package com.seapack.feature.importer;

import com.seapack.SeaPack;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

final class ItemsAdderItemResolver {
    private final SeaPack plugin;
    private final Map<String, ItemsAdderSourceItem> sourceItems;
    private final Map<String, Material> sourceMaterials = new HashMap<>();

    ItemsAdderItemResolver(SeaPack plugin, Map<String, ItemsAdderSourceItem> sourceItems) {
        this.plugin = plugin;
        this.sourceItems = sourceItems;
    }

    Result resolve() {
        Map<String, String> itemModels = new HashMap<>();
        Map<String, ItemModelRule> resolvedModels = new HashMap<>();
        for (String itemId : sourceItems.keySet()) {
            Material material = resolveSourceMaterial(itemId, new LinkedHashSet<>());
            if (material != null) {
                sourceMaterials.put(itemId, material);
            }
            ItemModelRule itemModel = resolveItemModel(itemId, resolvedModels, new LinkedHashSet<>());
            if (itemModel == null || itemModel.modelId().isBlank()) {
                continue;
            }
            try {
                Key.key(itemModel.modelId());
                itemModels.put(itemId, itemModel.modelId());
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("Ignoring invalid item_model '" + itemModel.modelId()
                        + "' for " + itemId + ".");
            }
        }
        return new Result(Map.copyOf(sourceMaterials), Map.copyOf(itemModels));
    }

    private Material resolveSourceMaterial(String itemId, Set<String> resolving) {
        if (sourceMaterials.containsKey(itemId)) {
            return sourceMaterials.get(itemId);
        }
        ItemsAdderSourceItem sourceItem = sourceItems.get(itemId);
        if (sourceItem == null || !resolving.add(itemId)) {
            return null;
        }
        ConfigurationSection section = sourceItem.section();
        Material material = readMaterial(section);
        if (material == null) {
            String variantOf = section.getString("variant_of", "").trim();
            if (!variantOf.isBlank()) {
                material = resolveSourceMaterial(ItemsAdderIds.qualified(sourceItem.namespace(), variantOf), resolving);
            }
        }
        if (material == null && (section.contains("graphics") || section.contains("item_model"))) {
            material = Material.PAPER;
        }
        resolving.remove(itemId);
        if (material != null) {
            sourceMaterials.put(itemId, material);
        }
        return material;
    }

    private ItemModelRule resolveItemModel(
            String itemId,
            Map<String, ItemModelRule> resolved,
            Set<String> resolving
    ) {
        if (resolved.containsKey(itemId)) {
            return resolved.get(itemId);
        }
        ItemsAdderSourceItem sourceItem = sourceItems.get(itemId);
        if (sourceItem == null || !resolving.add(itemId)) {
            return null;
        }
        ConfigurationSection section = sourceItem.section();
        ItemModelRule rule = null;
        String configuredModel = section.getString("item_model", "").trim();
        if (!configuredModel.isBlank()) {
            rule = new ItemModelRule(ItemsAdderIds.qualified(sourceItem.namespace(), configuredModel), false);
        } else if (section.contains("graphics")) {
            rule = new ItemModelRule(itemId, true);
        } else {
            String variantOf = section.getString("variant_of", "").trim();
            if (!variantOf.isBlank()) {
                ItemModelRule parent = resolveItemModel(
                        ItemsAdderIds.qualified(sourceItem.namespace(), variantOf),
                        resolved,
                        resolving
                );
                if (parent != null) {
                    rule = parent.generated() ? new ItemModelRule(itemId, true) : parent;
                }
            }
        }
        resolving.remove(itemId);
        resolved.put(itemId, rule);
        return rule;
    }

    static Material readMaterial(ConfigurationSection itemSection) {
        String materialName = itemSection.getString("material", "");
        if (materialName.isBlank()) {
            materialName = itemSection.getString("resource.material", "");
        }
        return materialName.isBlank() ? null : Material.matchMaterial(materialName);
    }

    record Result(Map<String, Material> sourceMaterials, Map<String, String> itemModels) {
    }

    private record ItemModelRule(String modelId, boolean generated) {
    }
}
