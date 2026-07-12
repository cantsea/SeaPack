package com.seapack.feature.importer;

import com.google.gson.JsonParseException;
import com.seapack.SeaPack;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.bukkit.configuration.file.YamlConfiguration;

final class ItemsAdderSourceScanner {
    private final SeaPack plugin;
    private final List<ItemsAdderSourceDocument> documents = new ArrayList<>();
    private final Map<String, ItemsAdderModelDefinition> modelDefinitions = new HashMap<>();
    private int sourceCount;

    ItemsAdderSourceScanner(SeaPack plugin) {
        this.plugin = plugin;
    }

    ItemsAdderSourceIndex scan() {
        Set<Path> sources = new LinkedHashSet<>();
        addSource(sources, new File(plugin.getDataFolder(), "contents"));
        addSource(sources, new File(plugin.getDataFolder(), "itemsadder-contents"));
        if (containsNamespaceConfigs(plugin.getDataFolder().toPath())) {
            addSource(sources, plugin.getDataFolder());
        }

        for (Path sourcePath : sources) {
            File source = sourcePath.toFile();
            if (source.isDirectory()) {
                sourceCount++;
                loadDirectory(sourcePath);
            } else if (source.isFile() && source.getName().toLowerCase(Locale.ROOT).endsWith(".zip")) {
                sourceCount++;
                loadZip(source);
            }
        }

        if (sourceCount == 0) {
            plugin.getLogger().warning("No ItemsAdder contents source found. Put the current contents folder at "
                    + new File(plugin.getDataFolder(), "contents").getPath());
        }
        return new ItemsAdderSourceIndex(
                List.copyOf(documents),
                Map.copyOf(modelDefinitions),
                sourceCount
        );
    }

    private static void addSource(Set<Path> sources, File source) {
        sources.add(source.toPath().toAbsolutePath().normalize());
    }

    private static boolean containsNamespaceConfigs(Path directory) {
        if (!Files.isDirectory(directory)) {
            return false;
        }
        try (var children = Files.list(directory)) {
            return children.filter(Files::isDirectory)
                    .anyMatch(child -> Files.isDirectory(child.resolve("configs")));
        } catch (IOException ignored) {
            return false;
        }
    }

    private void loadDirectory(Path directory) {
        try (var paths = Files.walk(directory)) {
            paths.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(path -> path.toString().toLowerCase(Locale.ROOT)))
                    .forEach(path -> loadDirectoryFile(directory, path));
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not scan ItemsAdder source directory '" + directory + "': "
                    + exception.getMessage());
        }
    }

    private void loadDirectoryFile(Path root, Path path) {
        if (isConfigYaml(root, path)) {
            documents.add(new ItemsAdderSourceDocument(
                    path.toString(),
                    inferNamespace(root, path),
                    categoryFolders(root, path),
                    YamlConfiguration.loadConfiguration(path.toFile())
            ));
        }

        String modelId = modelId(root, path);
        if (modelId == null) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            modelDefinitions.put(modelId, ItemsAdderModelReader.read(modelId, reader));
        } catch (IOException | JsonParseException exception) {
            plugin.getLogger().warning("Could not read item model '" + path + "': " + exception.getMessage());
        }
    }

    private void loadZip(File source) {
        try (ZipFile zip = new ZipFile(source)) {
            zip.stream()
                    .filter(entry -> !entry.isDirectory())
                    .sorted(Comparator.comparing(entry -> entry.getName().toLowerCase(Locale.ROOT)))
                    .forEach(entry -> loadZipEntry(zip, entry));
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not scan ItemsAdder source zip '" + source.getPath() + "': "
                    + exception.getMessage());
        }
    }

    private void loadZipEntry(ZipFile zip, ZipEntry entry) {
        if (isConfigYaml(entry)) {
            readZipDocument(zip, entry);
        }

        String modelId = modelId(entry.getName());
        if (modelId == null) {
            return;
        }
        try (Reader reader = new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8)) {
            modelDefinitions.put(modelId, ItemsAdderModelReader.read(modelId, reader));
        } catch (IOException | JsonParseException exception) {
            plugin.getLogger().warning("Could not read item model '" + entry.getName() + "': "
                    + exception.getMessage());
        }
    }

    private void readZipDocument(ZipFile zip, ZipEntry entry) {
        try (Reader reader = new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8)) {
            documents.add(new ItemsAdderSourceDocument(
                    entry.getName(),
                    inferNamespace(entry.getName()),
                    categoryFolders(entry.getName()),
                    YamlConfiguration.loadConfiguration(reader)
            ));
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not read ItemsAdder config '" + entry.getName() + "': "
                    + exception.getMessage());
        }
    }

    static String modelId(Path root, Path path) {
        Path relative = root.relativize(path);
        List<String> segments = new ArrayList<>();
        for (Path segment : relative) {
            segments.add(segment.toString());
        }
        return modelId(segments);
    }

    static String modelId(String entryName) {
        return modelId(List.of(entryName.replace('\\', '/').split("/")));
    }

    private static String modelId(List<String> pathSegments) {
        for (int index = 0; index + 3 < pathSegments.size(); index++) {
            if (!pathSegments.get(index).equalsIgnoreCase("assets")
                    || !pathSegments.get(index + 2).equalsIgnoreCase("models")) {
                continue;
            }
            String fileName = pathSegments.get(pathSegments.size() - 1);
            if (!fileName.toLowerCase(Locale.ROOT).endsWith(".json")) {
                return null;
            }
            String namespace = pathSegments.get(index + 1).toLowerCase(Locale.ROOT);
            String modelPath = String.join("/", pathSegments.subList(index + 3, pathSegments.size()));
            modelPath = modelPath.substring(0, modelPath.length() - ".json".length())
                    .toLowerCase(Locale.ROOT);
            return namespace.isBlank() || modelPath.isBlank() ? null : namespace + ":" + modelPath;
        }
        return null;
    }

    static List<String> categoryFolders(Path root, Path path) {
        Path relativePath = root.relativize(path);
        List<String> folders = new ArrayList<>();
        boolean afterConfigs = false;
        for (int index = 0; index < relativePath.getNameCount() - 1; index++) {
            String segment = relativePath.getName(index).toString();
            if (afterConfigs) {
                folders.add(normalizeCategory(segment));
            } else if (segment.equalsIgnoreCase("configs")) {
                afterConfigs = true;
            }
        }
        return folders.stream().filter(folder -> !folder.isBlank()).toList();
    }

    static List<String> categoryFolders(String entryName) {
        String[] segments = entryName.replace('\\', '/').split("/");
        List<String> folders = new ArrayList<>();
        boolean afterConfigs = false;
        for (int index = 0; index < segments.length - 1; index++) {
            if (afterConfigs) {
                folders.add(normalizeCategory(segments[index]));
            } else if (segments[index].equalsIgnoreCase("configs")) {
                afterConfigs = true;
            }
        }
        return folders.stream().filter(folder -> !folder.isBlank()).toList();
    }

    private static boolean isConfigYaml(Path root, Path path) {
        if (!isYaml(path)) {
            return false;
        }
        if (root.getFileName() != null && root.getFileName().toString().equalsIgnoreCase("configs")) {
            return true;
        }
        for (Path segment : root.relativize(path)) {
            if (segment.toString().equalsIgnoreCase("configs")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isConfigYaml(ZipEntry entry) {
        String normalizedName = entry.getName().replace('\\', '/').toLowerCase(Locale.ROOT);
        return (normalizedName.endsWith(".yml") || normalizedName.endsWith(".yaml"))
                && (normalizedName.startsWith("configs/") || normalizedName.contains("/configs/"));
    }

    private static String inferNamespace(Path root, Path path) {
        Path relativePath = root.relativize(path);
        for (int index = 0; index < relativePath.getNameCount(); index++) {
            if (!relativePath.getName(index).toString().equalsIgnoreCase("configs")) {
                continue;
            }
            if (index > 0) {
                return relativePath.getName(index - 1).toString().toLowerCase(Locale.ROOT);
            }
            Path parent = root.getParent();
            return parent != null && parent.getFileName() != null
                    ? parent.getFileName().toString().toLowerCase(Locale.ROOT)
                    : "";
        }
        return "";
    }

    private static String inferNamespace(String entryName) {
        String[] segments = entryName.replace('\\', '/').split("/");
        for (int index = 0; index < segments.length; index++) {
            if (segments[index].equalsIgnoreCase("configs")) {
                return index > 0 ? segments[index - 1].toLowerCase(Locale.ROOT) : "";
            }
        }
        return "";
    }

    private static boolean isYaml(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".yml") || name.endsWith(".yaml");
    }

    private static String normalizeCategory(String text) {
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]+", "_")
                .replaceAll("^_+|_+$", "");
    }
}
