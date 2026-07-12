package com.seapack.feature.importer;

import com.seapack.SeaPack;
import com.seapack.util.YamlFiles;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Stream;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class ItemsAdderImporter {
    private static final List<String> CACHE_FILE_NAMES = List.of(
            "items_ids_cache.yml",
            "items_ids_cache.yaml",
            "items_id_cache.yml",
            "items_id_cache.yaml"
    );

    private final SeaPack plugin;

    public ItemsAdderImporter(SeaPack plugin) {
        this.plugin = plugin;
    }

    public ImportResult importCurrentFiles() throws IOException {
        Path pluginsFolder = plugin.getDataFolder().toPath().toAbsolutePath().normalize().getParent();
        if (pluginsFolder == null || !Files.isDirectory(pluginsFolder)) {
            throw new IOException("Could not locate the server plugins folder.");
        }

        Path itemsAdderFolder = findItemsAdderFolder(pluginsFolder);
        Path sourceContents = itemsAdderFolder.resolve("contents");
        Path sourceCache = findCacheFile(itemsAdderFolder.resolve("storage"));
        if (!Files.isDirectory(sourceContents)) {
            throw new IOException("ItemsAdder contents folder was not found at " + sourceContents + ".");
        }
        if (sourceCache == null) {
            throw new IOException("ItemsAdder cache was not found in "
                    + itemsAdderFolder.resolve("storage") + ".");
        }

        Path seaPackFolder = plugin.getDataFolder().toPath().toAbsolutePath().normalize();
        Files.createDirectories(seaPackFolder);
        String operationId = UUID.randomUUID().toString();
        Path staging = seaPackFolder.resolve(".import-staging-" + operationId);
        Path backup = seaPackFolder.resolve("import-backup");
        Path stagedContents = staging.resolve("contents");
        Path stagedCache = staging.resolve("items_ids_cache.yml");
        Path targetContents = seaPackFolder.resolve("contents");
        Path targetCache = seaPackFolder.resolve("items_ids_cache.yml");
        long copiedFiles;

        try {
            copiedFiles = copyDirectory(sourceContents, stagedContents);
            Files.copy(sourceCache, stagedCache, StandardCopyOption.REPLACE_EXISTING);
            int cacheItemCount = validateCache(stagedCache);
            int configFileCount = countConfigFiles(stagedContents);
            if (configFileCount == 0) {
                throw new IOException("Imported contents contains no YAML files inside a configs folder.");
            }
            deleteRecursively(backup);
            replaceTargets(stagedContents, stagedCache, targetContents, targetCache, backup);
            return new ImportResult(itemsAdderFolder, sourceCache, copiedFiles + 1, cacheItemCount, configFileCount);
        } finally {
            cleanup(staging);
        }
    }

    public RollbackResult rollback() throws IOException {
        Path seaPackFolder = plugin.getDataFolder().toPath().toAbsolutePath().normalize();
        Path backup = seaPackFolder.resolve("import-backup");
        Path backupContents = backup.resolve("contents");
        Path backupCache = backup.resolve("items_ids_cache.yml");
        if (!Files.isDirectory(backupContents) || !Files.isRegularFile(backupCache)) {
            throw new IOException("No previous import backup is available.");
        }

        Path staging = seaPackFolder.resolve(".rollback-staging-" + UUID.randomUUID());
        Path replacedFiles = seaPackFolder.resolve("rollback-backup");
        Path currentContents = seaPackFolder.resolve("contents");
        Path currentCache = seaPackFolder.resolve("items_ids_cache.yml");
        try {
            copyDirectory(backupContents, staging.resolve("contents"));
            Files.createDirectories(staging);
            Files.copy(backupCache, staging.resolve("items_ids_cache.yml"), StandardCopyOption.REPLACE_EXISTING);
            validateCache(staging.resolve("items_ids_cache.yml"));
            deleteRecursively(replacedFiles);
            replaceTargets(
                    staging.resolve("contents"),
                    staging.resolve("items_ids_cache.yml"),
                    currentContents,
                    currentCache,
                    replacedFiles
            );
        } finally {
            cleanup(staging);
        }
        return new RollbackResult(Files.exists(replacedFiles.resolve("contents"))
                || Files.exists(replacedFiles.resolve("items_ids_cache.yml")));
    }

    public void recoverInterruptedImport() {
        Path seaPackFolder = plugin.getDataFolder().toPath().toAbsolutePath().normalize();
        Path backup = seaPackFolder.resolve("import-backup");
        Path targetContents = seaPackFolder.resolve("contents");
        Path targetCache = seaPackFolder.resolve("items_ids_cache.yml");
        try {
            boolean targetIncomplete = !Files.isDirectory(targetContents) || !Files.isRegularFile(targetCache);
            boolean backupComplete = Files.isDirectory(backup.resolve("contents"))
                    && Files.isRegularFile(backup.resolve("items_ids_cache.yml"));
            if (targetIncomplete && backupComplete) {
                deleteRecursively(targetContents);
                Files.deleteIfExists(targetCache);
                moveIfExists(backup.resolve("contents"), targetContents);
                moveIfExists(backup.resolve("items_ids_cache.yml"), targetCache);
                plugin.getLogger().warning("Recovered the previous SeaPack contents and cache after an interrupted import.");
            }
            try (Stream<Path> children = Files.isDirectory(seaPackFolder)
                    ? Files.list(seaPackFolder)
                    : Stream.empty()) {
                for (Path child : children
                        .filter(path -> path.getFileName().toString().startsWith(".import-staging-")
                                || path.getFileName().toString().startsWith(".rollback-staging-"))
                        .toList()) {
                    cleanup(child);
                }
            }
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not recover an interrupted ItemsAdder import: "
                    + exception.getMessage());
        }
    }

    private static Path findItemsAdderFolder(Path pluginsFolder) throws IOException {
        try (Stream<Path> children = Files.list(pluginsFolder)) {
            return children
                    .filter(Files::isDirectory)
                    .filter(path -> path.getFileName().toString().equalsIgnoreCase("ItemsAdder"))
                    .findFirst()
                    .orElseThrow(() -> new IOException(
                            "Could not find an ItemsAdder folder inside " + pluginsFolder + "."));
        }
    }

    static Path findCacheFile(Path storageFolder) throws IOException {
        if (!Files.isDirectory(storageFolder)) {
            return null;
        }
        for (String fileName : CACHE_FILE_NAMES) {
            Path candidate = storageFolder.resolve(fileName);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }

        try (Stream<Path> files = Files.list(storageFolder)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(ItemsAdderImporter::looksLikeCacheFile)
                    .max(Comparator.comparingLong(ItemsAdderImporter::lastModified))
                    .orElse(null);
        }
    }

    private static boolean looksLikeCacheFile(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return (name.endsWith(".yml") || name.endsWith(".yaml"))
                && (name.startsWith("items_ids_cache") || name.startsWith("items_id_cache"));
    }

    private static long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException ignored) {
            return Long.MIN_VALUE;
        }
    }

    private static long copyDirectory(Path source, Path destination) throws IOException {
        long[] copiedFiles = {0};
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) throws IOException {
                Files.createDirectories(destination.resolve(source.relativize(directory)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                if (attributes.isSymbolicLink()) {
                    return FileVisitResult.CONTINUE;
                }
                Files.copy(
                        file,
                        destination.resolve(source.relativize(file)),
                        StandardCopyOption.REPLACE_EXISTING
                );
                copiedFiles[0]++;
                return FileVisitResult.CONTINUE;
            }
        });
        return copiedFiles[0];
    }

    private int validateCache(Path cachePath) throws IOException {
        YamlConfiguration cache = YamlFiles.load(cachePath, plugin.getLogger(), "imported ItemsAdder cache")
                .orElseThrow(() -> new IOException("Imported ItemsAdder cache is invalid YAML."));
        int itemCount = 0;
        for (String materialName : cache.getKeys(false)) {
            Material material = Material.matchMaterial(materialName);
            ConfigurationSection materialSection = cache.getConfigurationSection(materialName);
            if (material == null || material.isAir() || materialSection == null) {
                continue;
            }
            for (String itemId : materialSection.getKeys(false)) {
                if (materialSection.isInt(itemId) && materialSection.getInt(itemId, -1) >= 0) {
                    itemCount++;
                }
            }
        }
        if (itemCount == 0) {
            throw new IOException("Imported ItemsAdder cache contains no valid item mappings.");
        }
        return itemCount;
    }

    private static int countConfigFiles(Path contents) throws IOException {
        try (Stream<Path> paths = Files.walk(contents)) {
            return Math.toIntExact(paths
                    .filter(Files::isRegularFile)
                    .filter(ItemsAdderImporter::isConfigYaml)
                    .count());
        }
    }

    private static boolean isConfigYaml(Path path) {
        String normalized = path.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
        return (normalized.endsWith(".yml") || normalized.endsWith(".yaml"))
                && normalized.contains("/configs/");
    }

    private static void replaceTargets(
            Path stagedContents,
            Path stagedCache,
            Path targetContents,
            Path targetCache,
            Path backup
    ) throws IOException {
        Path backupContents = backup.resolve("contents");
        Path backupCache = backup.resolve("items_ids_cache.yml");
        Files.createDirectories(backup);
        boolean contentsBackedUp = false;
        boolean cacheBackedUp = false;

        try {
            if (Files.exists(targetContents)) {
                Files.move(targetContents, backupContents);
                contentsBackedUp = true;
            }
            if (Files.exists(targetCache)) {
                Files.move(targetCache, backupCache);
                cacheBackedUp = true;
            }

            Files.move(stagedContents, targetContents);
            Files.move(stagedCache, targetCache);
        } catch (IOException exception) {
            deleteRecursively(targetContents);
            Files.deleteIfExists(targetCache);
            if (contentsBackedUp && Files.exists(backupContents)) {
                Files.move(backupContents, targetContents);
            }
            if (cacheBackedUp && Files.exists(backupCache)) {
                Files.move(backupCache, targetCache);
            }
            throw exception;
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (root == null || !Files.exists(root)) {
            return;
        }
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException exception) throws IOException {
                if (exception != null) {
                    throw exception;
                }
                Files.deleteIfExists(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void moveIfExists(Path source, Path destination) throws IOException {
        if (!Files.exists(source)) {
            return;
        }
        Path parent = destination.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
    }

    private void cleanup(Path root) {
        try {
            deleteRecursively(root);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not remove temporary import folder '" + root + "': "
                    + exception.getMessage());
        }
    }

    public record ImportResult(
            Path itemsAdderFolder,
            Path sourceCache,
            long copiedFileCount,
            int cacheItemCount,
            int configFileCount
    ) {
    }

    public record RollbackResult(
            boolean currentFilesSavedAsBackup
    ) {
    }
}
