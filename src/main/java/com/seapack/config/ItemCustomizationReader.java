package com.seapack.config;

import com.seapack.SeaPack;
import com.seapack.feature.item.CustomItemDefinition;
import com.seapack.feature.item.ItemCustomization;
import com.seapack.util.YamlFiles;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;

final class ItemCustomizationReader {
    static final int MAX_ENCHANTMENT_LEVEL = 255;

    private final SeaPack plugin;
    private final CustomItemConfigPaths paths;

    ItemCustomizationReader(SeaPack plugin, CustomItemConfigPaths paths) {
        this.plugin = plugin;
        this.paths = paths;
    }

    Map<String, ItemCustomization> legacyCustomizationsById() {
        Map<String, ItemCustomization> customizations = new HashMap<>();
        for (Path path : paths.configFiles()) {
            YamlConfiguration configuration = load(path);
            if (configuration == null) {
                continue;
            }
            String id = configuration.getString("id", "").trim();
            if (id.isBlank()) {
                continue;
            }
            customizations.putIfAbsent(
                    CustomItemConfigPaths.normalizeId(id),
                    readItemCustomization(configuration, path)
            );
        }
        return customizations;
    }

    ItemCustomization readSingle(Path path, CustomItemDefinition item) {
        YamlConfiguration configuration = load(path);
        return configuration == null
                ? ItemCustomization.EMPTY
                : renderPlaceholders(item, readItemCustomization(configuration, path));
    }

    Map<String, ItemCustomization> readSet(
            Path path,
            List<CustomItemDefinition> items,
            Map<String, String> itemKeys
    ) {
        YamlConfiguration configuration = load(path);
        if (configuration == null) {
            return Map.of();
        }
        DisplayNamePrefix displayNamePrefix = readDisplayNamePrefix(configuration);
        List<String> globalLore = readGlobalLore(configuration);
        List<EnchantSection> enchantSections = readEnchantSections(configuration, path);
        ConfigurationSection itemsSection = configuration.getConfigurationSection(CustomItemConfigSchema.ITEMS);

        Map<String, ItemCustomization> customizations = new LinkedHashMap<>();
        for (CustomItemDefinition item : items) {
            ConfigurationSection itemSection = itemsSection == null
                    ? null
                    : itemsSection.getConfigurationSection(itemKeys.get(CustomItemConfigPaths.normalizeId(item.id())));
            ItemCustomization itemCustomization = itemSection == null
                    ? ItemCustomization.EMPTY
                    : readItemCustomization(itemSection, path);

            ItemCustomization merged = mergeSetCustomization(
                    item,
                    itemCustomization,
                    displayNamePrefix,
                    globalLore,
                    enchantSections
            );
            customizations.put(
                    CustomItemConfigPaths.normalizeId(item.id()),
                    renderPlaceholders(item, merged)
            );
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
            displayName = Optional.of(displayNamePrefix.format());
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

    private ItemCustomization renderPlaceholders(
            CustomItemDefinition item,
            ItemCustomization customization
    ) {
        String suffix = CustomItemPlaceholders.render(
                customization.nameSuffix().orElse(paths.defaultNameSuffix(item)),
                item
        );
        return new ItemCustomization(
                customization.displayName().map(text -> renderText(text, item, suffix)),
                customization.nameSuffix().map(text -> renderText(text, item, suffix)),
                customization.lore().map(lines -> lines.stream()
                        .map(line -> renderText(line, item, suffix))
                        .toList()),
                customization.enchantments()
        );
    }

    private static String renderText(
            String text,
            CustomItemDefinition item,
            String suffix
    ) {
        return CustomItemPlaceholders.render(text, item).replace("{suffix}", suffix);
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
                configuration.getBoolean(CustomItemConfigSchema.DISPLAY_NAME_PREFIX + ".enabled", false),
                configuration.getString(
                        CustomItemConfigSchema.DISPLAY_NAME_PREFIX + ".format",
                        CustomItemConfigSchema.DEFAULT_PREFIX
                )
        );
    }

    private List<String> readGlobalLore(YamlConfiguration configuration) {
        if (!configuration.getBoolean(CustomItemConfigSchema.GLOBAL_LORE + ".enabled", false)) {
            return List.of();
        }
        return configuration.getStringList(CustomItemConfigSchema.GLOBAL_LORE + ".lines");
    }

    private List<EnchantSection> readEnchantSections(YamlConfiguration configuration, Path path) {
        ConfigurationSection sections = configuration.getConfigurationSection(
                CustomItemConfigSchema.ENCHANT_SECTIONS
        );
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
        Map<Enchantment, Integer> enchantments = new LinkedHashMap<>();
        Map<String, Integer> configuredEnchantments = configuredEnchantmentLevels(
                section,
                (configuredName, level) -> plugin.getLogger().warning(
                        "Ignoring enchantment '" + configuredName + "' with invalid level "
                                + level + " in " + path + ". Supported levels are 1 through "
                                + MAX_ENCHANTMENT_LEVEL + "."
                )
        );
        for (Map.Entry<String, Integer> entry : configuredEnchantments.entrySet()) {
            String configuredName = entry.getKey();
            Enchantment enchantment = parseEnchantment(configuredName);
            if (enchantment == null) {
                plugin.getLogger().warning("Ignoring unknown enchantment '" + configuredName + "' in " + path);
                continue;
            }
            enchantments.put(enchantment, entry.getValue());
        }
        return Map.copyOf(enchantments);
    }

    static Map<String, Integer> configuredEnchantmentLevels(
            ConfigurationSection section,
            BiConsumer<String, Integer> invalidLevelHandler
    ) {
        ConfigurationSection enchantSection = section.getConfigurationSection("enchants");
        if (enchantSection == null) {
            return Map.of();
        }

        Map<String, Integer> enchantments = new LinkedHashMap<>();
        for (String configuredName : enchantSection.getKeys(false)) {
            int level = enchantSection.getInt(configuredName, 0);
            if (level < 1 || level > MAX_ENCHANTMENT_LEVEL) {
                invalidLevelHandler.accept(configuredName, level);
                continue;
            }
            enchantments.put(configuredName, level);
        }
        return Map.copyOf(enchantments);
    }

    private YamlConfiguration load(Path path) {
        return YamlFiles.load(path, plugin.getLogger(), "custom item config").orElse(null);
    }

    private static Enchantment parseEnchantment(String configuredName) {
        String normalizedName = configuredName.toLowerCase(Locale.ROOT).replace(' ', '_');
        NamespacedKey key = normalizedName.contains(":")
                ? NamespacedKey.fromString(normalizedName)
                : NamespacedKey.minecraft(normalizedName);
        return key == null ? null : Registry.ENCHANTMENT.get(key);
    }

    private record EnchantSection(
            List<String> materialRules,
            Map<Enchantment, Integer> enchantments
    ) {
        private boolean matches(Material material) {
            for (String materialRule : materialRules) {
                if (MaterialRuleMatcher.matches(material, materialRule)) {
                    return true;
                }
            }
            return false;
        }
    }

    private record DisplayNamePrefix(boolean enabled, String format) {
    }
}
