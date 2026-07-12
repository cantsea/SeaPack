package org.seapack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class FurnitureConfigStore {
    private static final String ITEMS_PATH = "items";

    private final SeaPack plugin;
    private final Path path;

    public FurnitureConfigStore(SeaPack plugin) {
        this.plugin = plugin;
        this.path = plugin.getDataFolder().toPath().resolve("furniture.yml");
    }

    public SyncResult syncAndLoad(
            List<CustomItemDefinition> items,
            Map<String, FurnitureSettings> discoveredSettings
    ) {
        Map<String, CustomItemDefinition> currentItems = new LinkedHashMap<>();
        for (CustomItemDefinition item : items) {
            currentItems.put(item.id().toLowerCase(Locale.ROOT), item);
        }

        boolean created = !Files.isRegularFile(path);
        YamlConfiguration configuration;
        if (created) {
            configuration = newConfiguration();
        } else {
            var loadedConfiguration = YamlFiles.load(path, plugin.getLogger(), "furniture config");
            if (loadedConfiguration.isEmpty()) {
                return new SyncResult(currentFurnitureDefaults(currentItems, discoveredSettings), false, 0);
            }
            configuration = loadedConfiguration.get();
        }
        boolean changed = false;
        ConfigurationSection itemSection = configuration.getConfigurationSection(ITEMS_PATH);
        if (itemSection == null) {
            itemSection = configuration.createSection(ITEMS_PATH);
            changed = true;
        }

        int added = 0;
        for (Map.Entry<String, FurnitureSettings> entry : discoveredSettings.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER))
                .toList()) {
            String itemId = entry.getKey().toLowerCase(Locale.ROOT);
            if (!currentItems.containsKey(itemId)) {
                continue;
            }
            ConfigurationSection existing = itemSection.getConfigurationSection(itemId);
            if (existing != null) {
                changed |= backfillSettings(existing, entry.getValue());
                continue;
            }
            ConfigurationSection settingsSection = itemSection.createSection(itemId);
            writeSettings(settingsSection, entry.getValue());
            added++;
            changed = true;
        }

        if (changed) {
            try {
                Files.createDirectories(path.getParent());
                YamlFiles.saveAtomic(path, configuration);
            } catch (IOException exception) {
                plugin.getLogger().warning("Could not write furniture config '" + path + "': "
                        + exception.getMessage());
            }
        }

        Map<String, FurnitureSettings> loaded = new LinkedHashMap<>();
        for (String itemId : discoveredSettings.keySet()) {
            if (!currentItems.containsKey(itemId.toLowerCase(Locale.ROOT))) {
                continue;
            }
            ConfigurationSection settingsSection = itemSection.getConfigurationSection(itemId);
            FurnitureSettings defaults = discoveredSettings.get(itemId);
            loaded.put(itemId.toLowerCase(Locale.ROOT), settingsSection == null
                    ? defaults
                    : readSettings(settingsSection, defaults));
        }

        return new SyncResult(Map.copyOf(loaded), created, added);
    }

    private static YamlConfiguration newConfiguration() {
        return new YamlConfiguration();
    }

    private static Map<String, FurnitureSettings> currentFurnitureDefaults(
            Map<String, CustomItemDefinition> currentItems,
            Map<String, FurnitureSettings> discoveredSettings
    ) {
        Map<String, FurnitureSettings> defaults = new LinkedHashMap<>();
        discoveredSettings.forEach((itemId, settings) -> {
            String normalizedId = itemId.toLowerCase(Locale.ROOT);
            if (currentItems.containsKey(normalizedId)) {
                defaults.put(normalizedId, settings);
            }
        });
        return Map.copyOf(defaults);
    }

    private static void writeSettings(ConfigurationSection section, FurnitureSettings settings) {
        section.set("enabled", settings.enabled());
        section.set("small", settings.small());
        section.set("gravity", settings.gravity());
        section.set("fixed-rotation", settings.fixedRotation());
        section.set("rotation-snap", settings.rotationSnap());
        writeModelTransform(section, settings.modelTransform());
        section.set("opposite-direction", settings.oppositeDirection());
        section.set("placeable-on.floor", settings.floorPlacement());
        section.set("placeable-on.walls", settings.wallPlacement());
        section.set("placeable-on.ceiling", settings.ceilingPlacement());
        section.set("light-level", settings.lightLevel());
        section.set("y-offset", settings.yOffset());
        section.set("drop-item", settings.dropItem());
        section.set("entity", settings.entityType());
        section.set("solid", settings.solid());
        section.set("hitbox.height", settings.hitboxHeight());
        section.set("hitbox.height-offset", settings.hitboxHeightOffset());
        section.set("hitbox.length", settings.hitboxLength());
        section.set("hitbox.length-offset", settings.hitboxLengthOffset());
        section.set("hitbox.width", settings.hitboxWidth());
        section.set("hitbox.width-offset", settings.hitboxWidthOffset());
    }

    private static boolean backfillSettings(ConfigurationSection section, FurnitureSettings defaults) {
        boolean changed = false;
        changed |= setIfMissing(section, "enabled", defaults.enabled());
        changed |= setIfMissing(section, "small", defaults.small());
        changed |= setIfMissing(section, "gravity", defaults.gravity());
        changed |= setIfMissing(section, "fixed-rotation", defaults.fixedRotation());
        changed |= setIfMissing(section, "rotation-snap", defaults.rotationSnap());
        changed |= backfillModelTransform(section, defaults.modelTransform());
        changed |= setIfMissing(section, "opposite-direction", defaults.oppositeDirection());
        boolean legacyFloor = section.contains("floor-placement")
                ? section.getBoolean("floor-placement")
                : defaults.floorPlacement();
        changed |= setIfMissing(section, "placeable-on.floor", legacyFloor);
        changed |= setIfMissing(section, "placeable-on.walls", defaults.wallPlacement());
        changed |= setIfMissing(section, "placeable-on.ceiling", defaults.ceilingPlacement());
        changed |= setIfMissing(section, "light-level", defaults.lightLevel());
        changed |= setIfMissing(section, "y-offset", defaults.yOffset());
        changed |= setIfMissing(section, "drop-item", defaults.dropItem());
        changed |= setIfMissing(section, "entity", defaults.entityType());
        changed |= setIfMissing(section, "solid", defaults.solid());
        changed |= setIfMissing(section, "hitbox.height", defaults.hitboxHeight());
        changed |= setIfMissing(section, "hitbox.height-offset", defaults.hitboxHeightOffset());
        changed |= setIfMissing(section, "hitbox.length", defaults.hitboxLength());
        changed |= setIfMissing(section, "hitbox.length-offset", defaults.hitboxLengthOffset());
        changed |= setIfMissing(section, "hitbox.width", defaults.hitboxWidth());
        changed |= setIfMissing(section, "hitbox.width-offset", defaults.hitboxWidthOffset());
        return changed;
    }

    private static boolean setIfMissing(ConfigurationSection section, String path, Object value) {
        if (section.contains(path)) {
            return false;
        }
        section.set(path, value);
        return true;
    }

    private static void writeModelTransform(
            ConfigurationSection section,
            FurnitureModelTransform transform
    ) {
        writeValues(section, "model-transform.rotation", transform.rotation());
        writeValues(section, "model-transform.translation", transform.translation());
    }

    private static boolean backfillModelTransform(
            ConfigurationSection section,
            FurnitureModelTransform transform
    ) {
        boolean changed = false;
        changed |= backfillValues(section, "model-transform.rotation", transform.rotation());
        changed |= backfillValues(section, "model-transform.translation", transform.translation());
        return changed;
    }

    private static void writeValues(
            ConfigurationSection section,
            String path,
            FurnitureModelTransform.Values values
    ) {
        section.set(path + ".x", values.x());
        section.set(path + ".y", values.y());
        section.set(path + ".z", values.z());
    }

    private static boolean backfillValues(
            ConfigurationSection section,
            String path,
            FurnitureModelTransform.Values values
    ) {
        boolean changed = false;
        changed |= setIfMissing(section, path + ".x", values.x());
        changed |= setIfMissing(section, path + ".y", values.y());
        changed |= setIfMissing(section, path + ".z", values.z());
        return changed;
    }

    private static FurnitureModelTransform.Values readValues(
            ConfigurationSection section,
            String path,
            FurnitureModelTransform.Values defaults
    ) {
        return new FurnitureModelTransform.Values(
                section.getDouble(path + ".x", defaults.x()),
                section.getDouble(path + ".y", defaults.y()),
                section.getDouble(path + ".z", defaults.z())
        );
    }

    private static FurnitureSettings readSettings(
            ConfigurationSection section,
            FurnitureSettings defaults
    ) {
        FurnitureModelTransform sourceTransform = defaults.modelTransform();
        FurnitureModelTransform configuredTransform = sourceTransform.withConfigured(
                readValues(section, "model-transform.rotation", sourceTransform.rotation()),
                readValues(section, "model-transform.translation", sourceTransform.translation())
        );
        return new FurnitureSettings(
                section.getBoolean("enabled", defaults.enabled()),
                section.getBoolean("small", defaults.small()),
                section.getBoolean("gravity", defaults.gravity()),
                section.getBoolean("fixed-rotation", defaults.fixedRotation()),
                section.getInt("rotation-snap", defaults.rotationSnap()),
                configuredTransform,
                section.getBoolean("opposite-direction", defaults.oppositeDirection()),
                section.getBoolean("placeable-on.floor",
                        section.getBoolean("floor-placement", defaults.floorPlacement())),
                section.getBoolean("placeable-on.walls", defaults.wallPlacement()),
                section.getBoolean("placeable-on.ceiling", defaults.ceilingPlacement()),
                section.getInt("light-level", defaults.lightLevel()),
                section.getDouble("y-offset", defaults.yOffset()),
                section.getBoolean("drop-item", defaults.dropItem()),
                section.getString("entity", defaults.entityType()),
                section.getBoolean("solid", defaults.solid()),
                section.getDouble("hitbox.height", defaults.hitboxHeight()),
                section.getDouble("hitbox.height-offset", defaults.hitboxHeightOffset()),
                section.getDouble("hitbox.length", defaults.hitboxLength()),
                section.getDouble("hitbox.length-offset", defaults.hitboxLengthOffset()),
                section.getDouble("hitbox.width", defaults.hitboxWidth()),
                section.getDouble("hitbox.width-offset", defaults.hitboxWidthOffset())
        );
    }

    public record SyncResult(
            Map<String, FurnitureSettings> settings,
            boolean created,
            int addedItemCount
    ) {
    }
}
