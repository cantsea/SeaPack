package com.seapack.config;

import com.seapack.SeaPack;
import com.seapack.feature.item.CustomItemDefinition;
import com.seapack.feature.item.ItemCustomization;
import com.seapack.util.YamlFiles;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

final class ItemCustomizationWriter {
    private final SeaPack plugin;
    private final CustomItemConfigPaths paths;

    ItemCustomizationWriter(SeaPack plugin, CustomItemConfigPaths paths) {
        this.plugin = plugin;
        this.paths = paths;
    }

    boolean writeSingle(
            Path path,
            CustomItemDefinition item,
            ItemCustomization legacyCustomization
    ) {
        try {
            Files.createDirectories(path.getParent());
            YamlConfiguration configuration = new YamlConfiguration();
            writeItemCustomization(configuration, item, legacyCustomization);
            YamlFiles.saveAtomic(path, configuration);
            return true;
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not create custom item config '" + path + "': "
                    + exception.getMessage());
            return false;
        }
    }

    boolean writeSet(
            Path path,
            String setGroup,
            List<CustomItemDefinition> items,
            Map<String, String> itemKeys,
            Map<String, ItemCustomization> legacyCustomizations
    ) {
        try {
            Files.createDirectories(path.getParent());
            YamlConfiguration configuration = new YamlConfiguration();
            ensureSetTemplate(configuration, setGroup);
            for (CustomItemDefinition item : items) {
                ConfigurationSection itemSection = configuration.createSection(
                        CustomItemConfigSchema.ITEMS + "."
                                + itemKeys.get(CustomItemConfigPaths.normalizeId(item.id()))
                );
                writeItemCustomization(
                        itemSection,
                        item,
                        legacyCustomizations.get(CustomItemConfigPaths.normalizeId(item.id()))
                );
            }
            YamlFiles.saveAtomic(path, configuration);
            return true;
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not create set custom item config '" + path + "': "
                    + exception.getMessage());
            return false;
        }
    }

    int ensureSetHasCurrentItems(
            Path path,
            String setGroup,
            List<CustomItemDefinition> items,
            Map<String, String> itemKeys,
            Map<String, ItemCustomization> legacyCustomizations
    ) {
        YamlConfiguration configuration = load(path);
        if (configuration == null) {
            return 0;
        }
        boolean changed = ensureSetTemplate(configuration, setGroup);
        int addedItems = 0;

        for (CustomItemDefinition item : items) {
            String itemPath = CustomItemConfigSchema.ITEMS + "."
                    + itemKeys.get(CustomItemConfigPaths.normalizeId(item.id()));
            if (configuration.isConfigurationSection(itemPath)) {
                if (!configuration.contains(itemPath + ".name-suffix")) {
                    configuration.set(itemPath + ".name-suffix", paths.defaultNameSuffix(item));
                    changed = true;
                }
                continue;
            }
            ConfigurationSection itemSection = configuration.createSection(itemPath);
            writeItemCustomization(
                    itemSection,
                    item,
                    legacyCustomizations.get(CustomItemConfigPaths.normalizeId(item.id()))
            );
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
        if (!configuration.contains(CustomItemConfigSchema.DISPLAY_NAME_PREFIX + ".enabled")) {
            configuration.set(CustomItemConfigSchema.DISPLAY_NAME_PREFIX + ".enabled", false);
            changed = true;
        }
        if (!configuration.contains(CustomItemConfigSchema.DISPLAY_NAME_PREFIX + ".format")) {
            configuration.set(
                    CustomItemConfigSchema.DISPLAY_NAME_PREFIX + ".format",
                    CustomItemConfigSchema.DEFAULT_PREFIX
            );
            changed = true;
        }
        if (!configuration.contains(CustomItemConfigSchema.GLOBAL_LORE + ".enabled")) {
            configuration.set(CustomItemConfigSchema.GLOBAL_LORE + ".enabled", false);
            changed = true;
        }
        if (!configuration.contains(CustomItemConfigSchema.GLOBAL_LORE + ".lines")) {
            configuration.set(
                    CustomItemConfigSchema.GLOBAL_LORE + ".lines",
                    List.of("<gray>" + CustomItemConfigPaths.prettify(setGroup) + " set</gray>")
            );
            changed = true;
        }
        if (!configuration.isConfigurationSection(CustomItemConfigSchema.ENCHANT_SECTIONS + ".tools")) {
            configuration.set(CustomItemConfigSchema.ENCHANT_SECTIONS + ".tools.enabled", false);
            configuration.set(
                    CustomItemConfigSchema.ENCHANT_SECTIONS + ".tools.materials",
                    List.of("*_PICKAXE", "*_AXE", "*_SHOVEL")
            );
            configuration.set(CustomItemConfigSchema.ENCHANT_SECTIONS + ".tools.enchants.efficiency", 5);
            changed = true;
        }
        if (!configuration.isConfigurationSection(CustomItemConfigSchema.ITEMS)) {
            configuration.createSection(CustomItemConfigSchema.ITEMS);
            changed = true;
        }
        return changed;
    }

    private void writeItemCustomization(
            ConfigurationSection section,
            CustomItemDefinition item,
            ItemCustomization legacyCustomization
    ) {
        ItemCustomization customization = legacyCustomization == null
                ? ItemCustomization.EMPTY
                : legacyCustomization;
        if (item.category().equalsIgnoreCase("sets")) {
            section.set("name-suffix", customization.nameSuffix().orElse(paths.defaultNameSuffix(item)));
        }
        section.set("display-name", customization.displayName().orElse(item.displayName()));
        section.set("lore", customization.lore().orElse(item.lore()).stream().toList());
        section.createSection("enchants");
        customization.enchantments().forEach((enchantment, level) ->
                section.set("enchants." + enchantment.getKey().getKey(), level));
    }

    private YamlConfiguration load(Path path) {
        return YamlFiles.load(path, plugin.getLogger(), "custom item config").orElse(null);
    }
}
