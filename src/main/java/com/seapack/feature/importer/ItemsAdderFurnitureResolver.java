package com.seapack.feature.importer;

import com.seapack.feature.furniture.FurnitureModelTransform;
import com.seapack.feature.furniture.FurnitureSettings;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;

final class ItemsAdderFurnitureResolver {
    private final Map<String, ItemsAdderSourceItem> sourceItems;
    private final Map<String, ItemsAdderModelDefinition> modelDefinitions;

    ItemsAdderFurnitureResolver(
            Map<String, ItemsAdderSourceItem> sourceItems,
            Map<String, ItemsAdderModelDefinition> modelDefinitions
    ) {
        this.sourceItems = sourceItems;
        this.modelDefinitions = modelDefinitions;
    }

    Map<String, FurnitureSettings> resolve() {
        Map<String, FurnitureSettings> furnitureSettings = new HashMap<>();
        Map<String, FurnitureSettings> resolved = new HashMap<>();
        for (String itemId : sourceItems.keySet()) {
            FurnitureSettings settings = resolveFurniture(itemId, resolved, new LinkedHashSet<>());
            if (settings != null && settings.enabled()) {
                furnitureSettings.put(itemId, settings);
            }
        }
        return Map.copyOf(furnitureSettings);
    }

    private FurnitureSettings resolveFurniture(
            String itemId,
            Map<String, FurnitureSettings> resolved,
            Set<String> resolving
    ) {
        if (resolved.containsKey(itemId)) {
            return resolved.get(itemId);
        }
        ItemsAdderSourceItem sourceItem = sourceItems.get(itemId);
        if (sourceItem == null || !resolving.add(itemId)) {
            return null;
        }

        ConfigurationSection itemSection = sourceItem.section();
        FurnitureSettings inherited = null;
        String variantOf = itemSection.getString("variant_of", "").trim();
        if (!variantOf.isBlank()) {
            String parentId = ItemsAdderIds.qualified(sourceItem.namespace(), variantOf);
            inherited = resolveFurniture(parentId, resolved, resolving);
        }

        FurnitureSettings settings = inherited;
        String furniturePath = "behaviours.furniture";
        if (itemSection.contains(furniturePath)) {
            ConfigurationSection furniture = itemSection.getConfigurationSection(furniturePath);
            if (furniture == null) {
                settings = itemSection.getBoolean(furniturePath, true) ? FurnitureSettings.DEFAULT : null;
            } else {
                settings = readSettings(furniture, inherited == null ? FurnitureSettings.DEFAULT : inherited);
            }
        }

        if (settings != null) {
            settings = settings.withModelTransform(modelTransformForItem(itemId));
        }

        resolving.remove(itemId);
        resolved.put(itemId, settings);
        return settings;
    }

    private FurnitureModelTransform modelTransformForItem(String itemId) {
        String modelId = resolveFurnitureModelId(itemId, new LinkedHashSet<>());
        FurnitureModelTransform transform = resolveModelTransform(modelId, new LinkedHashSet<>());
        return transform == null ? FurnitureModelTransform.IDENTITY : transform;
    }

    private String resolveFurnitureModelId(String itemId, Set<String> resolving) {
        ItemsAdderSourceItem sourceItem = sourceItems.get(itemId);
        if (sourceItem == null || !resolving.add(itemId)) {
            return null;
        }

        ConfigurationSection section = sourceItem.section();
        String configuredModel = firstConfiguredModel(section);
        String modelId = configuredModel.isBlank()
                ? null
                : normalizeConfiguredModelId(sourceItem.namespace(), configuredModel);
        if (modelId == null) {
            String variantOf = section.getString("variant_of", "").trim();
            if (!variantOf.isBlank()) {
                modelId = resolveFurnitureModelId(
                        ItemsAdderIds.qualified(sourceItem.namespace(), variantOf),
                        resolving
                );
            }
        }
        resolving.remove(itemId);
        return modelId;
    }

    private FurnitureModelTransform resolveModelTransform(String modelId, Set<String> resolving) {
        if (modelId == null || !resolving.add(modelId)) {
            return null;
        }
        ItemsAdderModelDefinition definition = modelDefinitions.get(modelId);
        if (definition == null) {
            resolving.remove(modelId);
            return null;
        }

        FurnitureModelTransform transform = definition.transform();
        if (transform == null && definition.parentId() != null) {
            transform = resolveModelTransform(definition.parentId(), resolving);
        }
        resolving.remove(modelId);
        return transform;
    }

    private static String firstConfiguredModel(ConfigurationSection section) {
        for (String path : List.of(
                "resource.model_path",
                "resource.model",
                "graphics.model_path",
                "graphics.model"
        )) {
            String model = section.getString(path, "").trim();
            if (!model.isBlank()) {
                return model;
            }
        }
        return "";
    }

    private static String normalizeConfiguredModelId(String namespace, String configuredModel) {
        String normalized = configuredModel.trim().replace('\\', '/').toLowerCase(Locale.ROOT);
        if (normalized.endsWith(".json")) {
            normalized = normalized.substring(0, normalized.length() - ".json".length());
        }
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.startsWith("models/")) {
            normalized = normalized.substring("models/".length());
        }
        return normalized.isBlank() ? null : ItemsAdderIds.qualified(namespace, normalized);
    }

    private static FurnitureSettings readSettings(
            ConfigurationSection section,
            FurnitureSettings defaults
    ) {
        return new FurnitureSettings(
                section.getBoolean("enabled", defaults.enabled()),
                section.getBoolean("small", defaults.small()),
                section.getBoolean("gravity", defaults.gravity()),
                section.getBoolean("fixed_rotation", defaults.fixedRotation()),
                defaults.rotationSnap(),
                defaults.modelTransform(),
                section.getBoolean("opposite_direction", defaults.oppositeDirection()),
                section.getBoolean("placeable_on.floor", defaults.floorPlacement()),
                section.getBoolean("placeable_on.walls", defaults.wallPlacement()),
                section.getBoolean("placeable_on.ceiling", defaults.ceilingPlacement()),
                section.getInt("light_level", defaults.lightLevel()),
                defaults.yOffset(),
                section.getBoolean("drop_when_mined", defaults.dropItem()),
                section.getString("entity", defaults.entityType()),
                section.getBoolean("solid", defaults.solid()),
                section.getDouble("hitbox.height", defaults.hitboxHeight()),
                section.getDouble("hitbox.height_offset", defaults.hitboxHeightOffset()),
                section.getDouble("hitbox.length", defaults.hitboxLength()),
                section.getDouble("hitbox.length_offset", defaults.hitboxLengthOffset()),
                section.getDouble("hitbox.width", defaults.hitboxWidth()),
                section.getDouble("hitbox.width_offset", defaults.hitboxWidthOffset())
        );
    }
}
