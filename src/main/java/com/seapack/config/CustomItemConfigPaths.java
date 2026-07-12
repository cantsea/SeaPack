package com.seapack.config;

import com.seapack.SeaPack;
import com.seapack.feature.item.CustomItemDefinition;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class CustomItemConfigPaths {
    private final SeaPack plugin;
    private final Map<String, String> setGroupAliases;
    private final Path root;

    CustomItemConfigPaths(SeaPack plugin, Map<String, String> setGroupAliases) {
        this.plugin = plugin;
        this.setGroupAliases = setGroupAliases;
        this.root = plugin.getDataFolder().toPath().resolve("customitems");
    }

    Path root() {
        return root;
    }

    Map<String, List<CustomItemDefinition>> setItemsByGroup(List<CustomItemDefinition> items) {
        Map<String, List<CustomItemDefinition>> groupedItems = new LinkedHashMap<>();
        for (CustomItemDefinition item : items) {
            if (!item.category().equalsIgnoreCase("sets")) {
                continue;
            }
            String group = resolvedSetGroup(item);
            if (group.isBlank()) {
                group = "other";
            }
            groupedItems.computeIfAbsent(group, ignored -> new ArrayList<>()).add(item);
        }
        groupedItems.values().forEach(groupItems ->
                groupItems.sort(Comparator.comparing(CustomItemDefinition::itemKey, String.CASE_INSENSITIVE_ORDER)));
        return groupedItems;
    }

    Map<String, Path> uniqueItemConfigPaths(List<CustomItemDefinition> items) {
        Map<String, Path> paths = new LinkedHashMap<>();
        Map<String, String> owners = new HashMap<>();
        for (CustomItemDefinition item : items) {
            if (item.category().equalsIgnoreCase("sets")) {
                continue;
            }
            String normalizedId = normalizeId(item.id());
            Path path = itemConfigPath(item);
            String pathKey = path.toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT);
            String owner = owners.putIfAbsent(pathKey, normalizedId);
            if (owner != null && !owner.equals(normalizedId)) {
                path = path.resolveSibling(stripYamlExtension(path.getFileName().toString())
                        + "__" + stableToken(normalizedId) + ".yml");
                plugin.getLogger().warning("Custom config filename collision between '" + owner + "' and '"
                        + normalizedId + "'; using " + path.getFileName() + " for the latter.");
            }
            paths.put(normalizedId, path);
        }
        return paths;
    }

    Map<String, String> uniqueSetItemKeys(List<CustomItemDefinition> items) {
        Map<String, String> keys = new LinkedHashMap<>();
        Map<String, String> owners = new HashMap<>();
        for (CustomItemDefinition item : items) {
            String normalizedId = normalizeId(item.id());
            String key = itemConfigKey(item);
            String owner = owners.putIfAbsent(key.toLowerCase(Locale.ROOT), normalizedId);
            if (owner != null && !owner.equals(normalizedId)) {
                key = key + "__" + stableToken(normalizedId);
                plugin.getLogger().warning("Set config key collision between '" + owner + "' and '"
                        + normalizedId + "'; using " + key + " for the latter.");
            }
            keys.put(normalizedId, key);
        }
        return keys;
    }

    Path setConfigPath(String setGroup) {
        return root.resolve("sets").resolve(safePathName(setGroup) + ".yml");
    }

    String resolvedSetGroup(CustomItemDefinition item) {
        String rawGroup = item.setGroup();
        return setGroupAliases.getOrDefault(rawGroup, rawGroup);
    }

    String defaultNameSuffix(CustomItemDefinition item) {
        String itemKey = item.itemKey().toLowerCase(Locale.ROOT).replace('-', '_');
        List<String> groupCandidates = new ArrayList<>();
        groupCandidates.add(resolvedSetGroup(item));
        groupCandidates.add(item.setGroup());

        for (String groupCandidate : groupCandidates.stream()
                .filter(candidate -> candidate != null && !candidate.isBlank())
                .map(candidate -> candidate.toLowerCase(Locale.ROOT).replace('-', '_'))
                .distinct()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList()) {
            if (itemKey.equals(groupCandidate)) {
                return prettify(item.itemKey());
            }
            if (itemKey.startsWith(groupCandidate + "_")) {
                return prettify(cleanNameSuffix(itemKey.substring(groupCandidate.length() + 1)));
            }
        }

        return prettify(item.itemKey());
    }

    List<Path> configFiles() {
        if (!Files.isDirectory(root)) {
            return List.of();
        }

        try (var paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                    .filter(CustomItemConfigPaths::isYaml)
                    .sorted(Comparator.comparing(path -> path.toString().toLowerCase(Locale.ROOT)))
                    .toList();
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not scan custom item configs in '" + root + "': "
                    + exception.getMessage());
            return List.of();
        }
    }

    static String normalizeId(String id) {
        return id.toLowerCase(Locale.ROOT);
    }

    static String prettify(String text) {
        String name = text.replace('_', ' ').replace('-', ' ');
        StringBuilder prettyName = new StringBuilder();
        boolean capitalizeNext = true;
        for (char character : name.toCharArray()) {
            if (Character.isWhitespace(character)) {
                capitalizeNext = true;
                prettyName.append(character);
                continue;
            }

            prettyName.append(capitalizeNext ? Character.toUpperCase(character) : character);
            capitalizeNext = false;
        }
        return prettyName.toString();
    }

    private Path itemConfigPath(CustomItemDefinition item) {
        Path nestedFolder = root;
        for (String category : item.categoryPath()) {
            nestedFolder = nestedFolder.resolve(safePathName(category));
        }
        Path nestedPath = nestedFolder.resolve(itemConfigKey(item) + ".yml");
        Path legacyPath = root.resolve(safePathName(item.category())).resolve(itemConfigKey(item) + ".yml");
        if (Files.isRegularFile(nestedPath)) {
            return nestedPath;
        }
        return Files.isRegularFile(legacyPath) ? legacyPath : nestedPath;
    }

    private static String itemConfigKey(CustomItemDefinition item) {
        return safePathName(item.itemKey());
    }

    private static String stripYamlExtension(String fileName) {
        return fileName.replaceFirst("(?i)\\.ya?ml$", "");
    }

    private static String stableToken(String text) {
        return Integer.toUnsignedString(text.hashCode(), 36);
    }

    private static String cleanNameSuffix(String suffix) {
        String value = suffix.replaceAll("^_+|_+$", "");
        boolean changed = true;
        while (changed) {
            changed = false;
            for (String removable : List.of("_animated", "_normal", "_self")) {
                if (value.endsWith(removable)) {
                    value = value.substring(0, value.length() - removable.length());
                    changed = true;
                }
            }
        }
        return value.isBlank() ? suffix : value;
    }

    private static boolean isYaml(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".yml") || name.endsWith(".yaml");
    }

    private static String safePathName(String text) {
        String safeName = text.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]+", "_")
                .replaceAll("^_+|_+$", "");
        return safeName.isBlank() ? "other" : safeName;
    }
}
