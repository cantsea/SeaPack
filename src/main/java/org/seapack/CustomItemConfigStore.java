package org.seapack;

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
import java.util.Optional;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;

public final class CustomItemConfigStore {
    private static final String DISPLAY_NAME_PREFIX_PATH = "display-name-prefix";
    private static final String GLOBAL_LORE_PATH = "global-lore";
    private static final String ENCHANT_SECTIONS_PATH = "enchant-sections";
    private static final String ITEMS_PATH = "items";

    private final SeaPack plugin;
    private final Map<String, String> setGroupAliases;
    private final Path root;

    public CustomItemConfigStore(SeaPack plugin, Map<String, String> setGroupAliases) {
        this.plugin = plugin;
        this.setGroupAliases = setGroupAliases;
        this.root = plugin.getDataFolder().toPath().resolve("customitems");
    }

    public SyncResult syncAndLoad(List<CustomItemDefinition> items) {
        try {
            Files.createDirectories(root);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not create custom item config folder '" + root + "': "
                    + exception.getMessage());
            return new SyncResult(Map.of(), 0, 0, 0);
        }

        Map<String, ItemCustomization> legacyCustomizations = legacyCustomizationsById();
        Map<String, ItemCustomization> customizations = new LinkedHashMap<>();
        int configCount = 0;
        int createdConfigCount = 0;
        int createdItemEntryCount = 0;
        Map<String, Path> itemConfigPaths = uniqueItemConfigPaths(items);

        for (CustomItemDefinition item : items) {
            if (item.category().equalsIgnoreCase("sets")) {
                continue;
            }

            Path path = itemConfigPaths.get(normalizeId(item.id()));
            if (!Files.exists(path)) {
                if (writeSingleItemConfig(path, item, legacyCustomizations.get(normalizeId(item.id())))) {
                    createdConfigCount++;
                }
            }

            if (Files.isRegularFile(path)) {
                configCount++;
                ItemCustomization customization = readSingleItemConfig(path);
                customizations.put(normalizeId(item.id()), customization);
            }
        }

        for (Map.Entry<String, List<CustomItemDefinition>> entry : setItemsByGroup(items).entrySet()) {
            Path path = setConfigPath(entry.getKey());
            Map<String, String> itemKeys = uniqueSetItemKeys(entry.getValue());
            if (!Files.exists(path)) {
                if (writeSetConfig(path, entry.getKey(), entry.getValue(), itemKeys, legacyCustomizations)) {
                    createdConfigCount++;
                    createdItemEntryCount += entry.getValue().size();
                }
            } else {
                createdItemEntryCount += ensureSetConfigHasCurrentItems(
                        path, entry.getKey(), entry.getValue(), itemKeys, legacyCustomizations
                );
            }

            if (Files.isRegularFile(path)) {
                configCount++;
                customizations.putAll(readSetConfig(path, entry.getValue(), itemKeys));
            }
        }

        return new SyncResult(customizations, configCount, createdConfigCount, createdItemEntryCount);
    }

    private Map<String, List<CustomItemDefinition>> setItemsByGroup(List<CustomItemDefinition> items) {
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

    private Map<String, ItemCustomization> legacyCustomizationsById() {
        Map<String, ItemCustomization> customizations = new HashMap<>();
        for (Path path : configFiles()) {
            YamlConfiguration configuration = loadConfiguration(path);
            if (configuration == null) {
                continue;
            }
            String id = configuration.getString("id", "").trim();
            if (id.isBlank()) {
                continue;
            }
            customizations.putIfAbsent(normalizeId(id), readItemCustomization(configuration, path));
        }
        return customizations;
    }

    private ItemCustomization readSingleItemConfig(Path path) {
        YamlConfiguration configuration = loadConfiguration(path);
        return configuration == null ? ItemCustomization.EMPTY : readItemCustomization(configuration, path);
    }

    private Map<String, ItemCustomization> readSetConfig(
            Path path,
            List<CustomItemDefinition> items,
            Map<String, String> itemKeys
    ) {
        YamlConfiguration configuration = loadConfiguration(path);
        if (configuration == null) {
            return Map.of();
        }
        DisplayNamePrefix displayNamePrefix = readDisplayNamePrefix(configuration);
        List<String> globalLore = readGlobalLore(configuration);
        List<EnchantSection> enchantSections = readEnchantSections(configuration, path);
        ConfigurationSection itemsSection = configuration.getConfigurationSection(ITEMS_PATH);

        Map<String, ItemCustomization> customizations = new LinkedHashMap<>();
        for (CustomItemDefinition item : items) {
            ConfigurationSection itemSection = itemsSection == null
                    ? null
                    : itemsSection.getConfigurationSection(itemKeys.get(normalizeId(item.id())));
            ItemCustomization itemCustomization = itemSection == null
                    ? ItemCustomization.EMPTY
                    : readItemCustomization(itemSection, path);

            customizations.put(normalizeId(item.id()), mergeSetCustomization(
                    item,
                    itemCustomization,
                    displayNamePrefix,
                    globalLore,
                    enchantSections
            ));
        }
        return customizations;
    }

    private ItemCustomization mergeSetCustomization(
            CustomItemDefinition item,
            ItemCustomization itemCustomization,
            DisplayNamePrefix displayNamePrefix,
            List<String> globalLore,
            List<EnchantSection> enchantSections
    ) {
        Optional<String> displayName = itemCustomization.displayName();
        if (displayNamePrefix.enabled()) {
            String suffix = itemCustomization.nameSuffix().orElse(defaultNameSuffix(item));
            displayName = Optional.of(displayNamePrefix.format().replace("{suffix}", suffix));
        }

        Optional<List<String>> lore = itemCustomization.lore();
        if (!globalLore.isEmpty()) {
            List<String> mergedLore = new ArrayList<>(lore.orElse(item.lore()));
            mergedLore.addAll(globalLore);
            lore = Optional.of(mergedLore);
        }

        Map<Enchantment, Integer> enchantments = new LinkedHashMap<>();
        for (EnchantSection section : enchantSections) {
            if (section.matches(item.material())) {
                enchantments.putAll(section.enchantments());
            }
        }
        enchantments.putAll(itemCustomization.enchantments());

        return new ItemCustomization(
                displayName,
                itemCustomization.nameSuffix(),
                lore,
                Map.copyOf(enchantments)
        );
    }

    private ItemCustomization readItemCustomization(ConfigurationSection section, Path path) {
        Optional<String> displayName = Optional.empty();
        if (section.contains("display-name")) {
            displayName = Optional.of(section.getString("display-name", ""));
        }

        Optional<String> nameSuffix = Optional.empty();
        if (section.contains("name-suffix")) {
            nameSuffix = Optional.of(section.getString("name-suffix", ""));
        }

        Optional<List<String>> lore = Optional.empty();
        if (section.contains("lore")) {
            lore = Optional.of(section.getStringList("lore"));
        }

        return new ItemCustomization(displayName, nameSuffix, lore, readEnchantments(section, path));
    }

    private DisplayNamePrefix readDisplayNamePrefix(YamlConfiguration configuration) {
        return new DisplayNamePrefix(
                configuration.getBoolean(DISPLAY_NAME_PREFIX_PATH + ".enabled", false),
                configuration.getString(
                        DISPLAY_NAME_PREFIX_PATH + ".format",
                        "<b><gradient:#FF30FF:#46FFFF>CUSTOM {suffix}</gradient></b>"
                )
        );
    }

    private List<String> readGlobalLore(YamlConfiguration configuration) {
        if (!configuration.getBoolean(GLOBAL_LORE_PATH + ".enabled", false)) {
            return List.of();
        }
        return configuration.getStringList(GLOBAL_LORE_PATH + ".lines");
    }

    private List<EnchantSection> readEnchantSections(YamlConfiguration configuration, Path path) {
        ConfigurationSection sections = configuration.getConfigurationSection(ENCHANT_SECTIONS_PATH);
        if (sections == null) {
            return List.of();
        }

        List<EnchantSection> enchantSections = new ArrayList<>();
        for (String sectionName : sections.getKeys(false)) {
            ConfigurationSection section = sections.getConfigurationSection(sectionName);
            if (section == null || !section.getBoolean("enabled", false)) {
                continue;
            }

            Map<Enchantment, Integer> enchantments = readEnchantments(section, path);
            if (enchantments.isEmpty()) {
                continue;
            }
            enchantSections.add(new EnchantSection(section.getStringList("materials"), enchantments));
        }
        return enchantSections;
    }

    private Map<Enchantment, Integer> readEnchantments(ConfigurationSection section, Path path) {
        ConfigurationSection enchantSection = section.getConfigurationSection("enchants");
        if (enchantSection == null) {
            return Map.of();
        }

        Map<Enchantment, Integer> enchantments = new LinkedHashMap<>();
        for (String configuredName : enchantSection.getKeys(false)) {
            Enchantment enchantment = parseEnchantment(configuredName);
            if (enchantment == null) {
                plugin.getLogger().warning("Ignoring unknown enchantment '" + configuredName + "' in " + path);
                continue;
            }

            int level = enchantSection.getInt(configuredName, 0);
            if (level <= 0) {
                plugin.getLogger().warning("Ignoring enchantment '" + configuredName + "' with invalid level "
                        + level + " in " + path);
                continue;
            }
            enchantments.put(enchantment, level);
        }
        return Map.copyOf(enchantments);
    }

    private static Enchantment parseEnchantment(String configuredName) {
        String normalizedName = configuredName.toLowerCase(Locale.ROOT).replace(' ', '_');
        NamespacedKey key = normalizedName.contains(":")
                ? NamespacedKey.fromString(normalizedName)
                : NamespacedKey.minecraft(normalizedName);
        return key == null ? null : Registry.ENCHANTMENT.get(key);
    }

    private boolean writeSingleItemConfig(
            Path path,
            CustomItemDefinition item,
            ItemCustomization legacyCustomization
    ) {
        try {
            Files.createDirectories(path.getParent());
            YamlConfiguration configuration = newConfiguration();
            writeItemCustomization(configuration, item, legacyCustomization);
            YamlFiles.saveAtomic(path, configuration);
            return true;
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not create custom item config '" + path + "': "
                    + exception.getMessage());
            return false;
        }
    }

    private boolean writeSetConfig(
            Path path,
            String setGroup,
            List<CustomItemDefinition> items,
            Map<String, String> itemKeys,
            Map<String, ItemCustomization> legacyCustomizations
    ) {
        try {
            Files.createDirectories(path.getParent());
            YamlConfiguration configuration = newConfiguration();
            ensureSetTemplate(configuration, setGroup);
            for (CustomItemDefinition item : items) {
                ConfigurationSection itemSection = configuration.createSection(
                        ITEMS_PATH + "." + itemKeys.get(normalizeId(item.id()))
                );
                writeItemCustomization(itemSection, item, legacyCustomizations.get(normalizeId(item.id())));
            }
            YamlFiles.saveAtomic(path, configuration);
            return true;
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not create set custom item config '" + path + "': "
                    + exception.getMessage());
            return false;
        }
    }

    private int ensureSetConfigHasCurrentItems(
            Path path,
            String setGroup,
            List<CustomItemDefinition> items,
            Map<String, String> itemKeys,
            Map<String, ItemCustomization> legacyCustomizations
    ) {
        YamlConfiguration configuration = loadConfiguration(path);
        if (configuration == null) {
            return 0;
        }
        boolean changed = ensureSetTemplate(configuration, setGroup);
        int addedItems = 0;

        for (CustomItemDefinition item : items) {
            String itemPath = ITEMS_PATH + "." + itemKeys.get(normalizeId(item.id()));
            if (configuration.isConfigurationSection(itemPath)) {
                if (!configuration.contains(itemPath + ".name-suffix")) {
                    configuration.set(itemPath + ".name-suffix", defaultNameSuffix(item));
                    changed = true;
                }
                continue;
            }
            ConfigurationSection itemSection = configuration.createSection(itemPath);
            writeItemCustomization(itemSection, item, legacyCustomizations.get(normalizeId(item.id())));
            addedItems++;
            changed = true;
        }

        if (changed) {
            try {
                YamlFiles.saveAtomic(path, configuration);
            } catch (IOException exception) {
                plugin.getLogger().warning("Could not update set custom item config '" + path + "': "
                        + exception.getMessage());
            }
        }

        return addedItems;
    }

    private boolean ensureSetTemplate(YamlConfiguration configuration, String setGroup) {
        boolean changed = false;
        if (!configuration.contains(DISPLAY_NAME_PREFIX_PATH + ".enabled")) {
            configuration.set(DISPLAY_NAME_PREFIX_PATH + ".enabled", false);
            changed = true;
        }
        if (!configuration.contains(DISPLAY_NAME_PREFIX_PATH + ".format")) {
            configuration.set(DISPLAY_NAME_PREFIX_PATH + ".format", "<b><gradient:#FF30FF:#46FFFF>CUSTOM {suffix}</gradient></b>");
            changed = true;
        }
        if (!configuration.contains(GLOBAL_LORE_PATH + ".enabled")) {
            configuration.set(GLOBAL_LORE_PATH + ".enabled", false);
            changed = true;
        }
        if (!configuration.contains(GLOBAL_LORE_PATH + ".lines")) {
            configuration.set(GLOBAL_LORE_PATH + ".lines", List.of("<gray>" + prettify(setGroup) + " set</gray>"));
            changed = true;
        }
        if (!configuration.isConfigurationSection(ENCHANT_SECTIONS_PATH + ".tools")) {
            configuration.set(ENCHANT_SECTIONS_PATH + ".tools.enabled", false);
            configuration.set(ENCHANT_SECTIONS_PATH + ".tools.materials", List.of("*_PICKAXE", "*_AXE", "*_SHOVEL"));
            configuration.set(ENCHANT_SECTIONS_PATH + ".tools.enchants.efficiency", 5);
            changed = true;
        }
        if (!configuration.isConfigurationSection(ITEMS_PATH)) {
            configuration.createSection(ITEMS_PATH);
            changed = true;
        }
        return changed;
    }

    private static YamlConfiguration newConfiguration() {
        return new YamlConfiguration();
    }

    private YamlConfiguration loadConfiguration(Path path) {
        return YamlFiles.load(path, plugin.getLogger(), "custom item config").orElse(null);
    }

    private void writeItemCustomization(
            ConfigurationSection section,
            CustomItemDefinition item,
            ItemCustomization legacyCustomization
    ) {
        ItemCustomization customization = legacyCustomization == null ? ItemCustomization.EMPTY : legacyCustomization;
        if (item.category().equalsIgnoreCase("sets")) {
            section.set("name-suffix", customization.nameSuffix().orElse(defaultNameSuffix(item)));
        }
        section.set("display-name", customization.displayName().orElse(item.displayName()));
        section.set("lore", customization.lore()
                .orElse(item.lore())
                .stream()
                .toList());
        section.createSection("enchants");
        customization.enchantments().forEach((enchantment, level) ->
                section.set("enchants." + enchantment.getKey().getKey(), level));
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

    private Map<String, Path> uniqueItemConfigPaths(List<CustomItemDefinition> items) {
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

    private Map<String, String> uniqueSetItemKeys(List<CustomItemDefinition> items) {
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

    private static String stripYamlExtension(String fileName) {
        return fileName.replaceFirst("(?i)\\.ya?ml$", "");
    }

    private static String stableToken(String text) {
        return Integer.toUnsignedString(text.hashCode(), 36);
    }

    private Path setConfigPath(String setGroup) {
        return root.resolve("sets").resolve(safePathName(setGroup) + ".yml");
    }

    private String resolvedSetGroup(CustomItemDefinition item) {
        String rawGroup = item.setGroup();
        return setGroupAliases.getOrDefault(rawGroup, rawGroup);
    }

    private static String itemConfigKey(CustomItemDefinition item) {
        return safePathName(item.itemKey());
    }

    private String defaultNameSuffix(CustomItemDefinition item) {
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

    private List<Path> configFiles() {
        if (!Files.isDirectory(root)) {
            return List.of();
        }

        try (var paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                    .filter(CustomItemConfigStore::isYaml)
                    .sorted(Comparator.comparing(path -> path.toString().toLowerCase(Locale.ROOT)))
                    .toList();
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not scan custom item configs in '" + root + "': "
                    + exception.getMessage());
            return List.of();
        }
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

    private static String normalizeId(String id) {
        return id.toLowerCase(Locale.ROOT);
    }

    private static String prettify(String text) {
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

    public record SyncResult(
            Map<String, ItemCustomization> customizations,
            int configCount,
            int createdCount,
            int createdItemEntryCount
    ) {
    }

    private record EnchantSection(
            List<String> materialRules,
            Map<Enchantment, Integer> enchantments
    ) {
        private boolean matches(Material material) {
            if (materialRules.isEmpty()) {
                return false;
            }

            for (String materialRule : materialRules) {
                if (matchesMaterialRule(material, materialRule)) {
                    return true;
                }
            }
            return false;
        }

        private static boolean matchesMaterialRule(Material material, String materialRule) {
            String rule = materialRule.toUpperCase(Locale.ROOT).replace(' ', '_');
            if (rule.equals("*") || rule.equals("ALL")) {
                return true;
            }
            if (rule.startsWith("*_")) {
                return material.name().endsWith(rule.substring(1));
            }

            Material exactMaterial = Material.matchMaterial(rule);
            if (exactMaterial != null) {
                return exactMaterial == material;
            }

            return switch (rule) {
                case "PICKAXES" -> material.name().endsWith("_PICKAXE");
                case "AXES" -> material.name().endsWith("_AXE");
                case "SHOVELS" -> material.name().endsWith("_SHOVEL");
                case "HOES" -> material.name().endsWith("_HOE");
                case "SWORDS" -> material.name().endsWith("_SWORD");
                case "HELMETS" -> material.name().endsWith("_HELMET") || material == Material.TURTLE_HELMET;
                case "CHESTPLATES" -> material.name().endsWith("_CHESTPLATE");
                case "LEGGINGS" -> material.name().endsWith("_LEGGINGS");
                case "BOOTS" -> material.name().endsWith("_BOOTS");
                case "ARMOR" -> material.name().matches(".*_(HELMET|CHESTPLATE|LEGGINGS|BOOTS)") || material == Material.TURTLE_HELMET;
                case "TOOLS" -> material.name().matches(".*_(PICKAXE|AXE|SHOVEL|HOE)");
                case "WEAPONS" -> material.name().matches(".*_(SWORD|AXE)") || material == Material.BOW || material == Material.CROSSBOW || material == Material.TRIDENT;
                default -> false;
            };
        }
    }

    private record DisplayNamePrefix(
            boolean enabled,
            String format
    ) {
    }
}
