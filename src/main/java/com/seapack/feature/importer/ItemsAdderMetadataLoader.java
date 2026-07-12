package com.seapack.feature.importer;

import com.seapack.SeaPack;
import com.seapack.feature.furniture.FurnitureSettings;
import com.seapack.feature.item.ArmorRendering;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

public final class ItemsAdderMetadataLoader {
    private final List<ItemsAdderArmorResolver.Rule> armorRules;
    private final Map<String, FurnitureSettings> furnitureSettings;
    private final Map<String, ItemsAdderSourceItem> sourceItems;
    private final Map<String, Material> sourceMaterials;
    private final Map<String, String> itemModels;
    private final int sourceCount;
    private final int documentCount;
    private final int namespaceCount;

    private ItemsAdderMetadataLoader(
            List<ItemsAdderArmorResolver.Rule> armorRules,
            Map<String, FurnitureSettings> furnitureSettings,
            Map<String, ItemsAdderSourceItem> sourceItems,
            Map<String, Material> sourceMaterials,
            Map<String, String> itemModels,
            int sourceCount,
            int documentCount,
            int namespaceCount
    ) {
        this.armorRules = armorRules;
        this.furnitureSettings = furnitureSettings;
        this.sourceItems = sourceItems;
        this.sourceMaterials = sourceMaterials;
        this.itemModels = itemModels;
        this.sourceCount = sourceCount;
        this.documentCount = documentCount;
        this.namespaceCount = namespaceCount;
    }

    public static ItemsAdderMetadataLoader load(SeaPack plugin) {
        ItemsAdderSourceIndex sourceIndex = new ItemsAdderSourceScanner(plugin).scan();
        IndexedItems indexedItems = indexItems(plugin, sourceIndex.documents());
        ItemsAdderItemResolver.Result items = new ItemsAdderItemResolver(plugin, indexedItems.items()).resolve();
        List<ItemsAdderArmorResolver.Rule> armorRules = new ItemsAdderArmorResolver(
                plugin,
                sourceIndex.documents(),
                indexedItems.items(),
                items.sourceMaterials()
        ).resolve();
        Map<String, FurnitureSettings> furnitureSettings = new ItemsAdderFurnitureResolver(
                indexedItems.items(),
                sourceIndex.modelDefinitions()
        ).resolve();

        if (sourceIndex.sourceCount() > 0 && sourceIndex.documents().isEmpty()) {
            plugin.getLogger().warning("ItemsAdder source was found, but no YAML files inside a configs folder were found.");
        } else if (!sourceIndex.documents().isEmpty() && armorRules.isEmpty()) {
            plugin.getLogger().warning("Scanned " + sourceIndex.documents().size()
                    + " ItemsAdder config files, but found no armor item definitions.");
        }

        return new ItemsAdderMetadataLoader(
                armorRules,
                furnitureSettings,
                indexedItems.items(),
                items.sourceMaterials(),
                items.itemModels(),
                sourceIndex.sourceCount(),
                sourceIndex.documents().size(),
                indexedItems.namespaces().size()
        );
    }

    public ArmorRendering armorRendering(String itemId) {
        String normalizedId = itemId.toLowerCase(Locale.ROOT);
        for (ItemsAdderArmorResolver.Rule rule : armorRules) {
            if (normalizedId.equals(rule.itemId()) || normalizedId.startsWith(rule.itemId() + "_")) {
                return rule.rendering();
            }
        }
        return ArmorRendering.NONE;
    }

    public int ruleCount() {
        return armorRules.size();
    }

    public int sourceCount() {
        return sourceCount;
    }

    public int documentCount() {
        return documentCount;
    }

    public int namespaceCount() {
        return namespaceCount;
    }

    public Map<String, FurnitureSettings> furnitureSettings() {
        return furnitureSettings;
    }

    public boolean hasItemIndex() {
        return !sourceItems.isEmpty();
    }

    public boolean isCurrentItem(String itemId) {
        ItemsAdderSourceItem sourceItem = sourceItems.get(itemId.toLowerCase(Locale.ROOT));
        return sourceItem != null
                && sourceItem.section().getBoolean("enabled", true)
                && !sourceItem.section().getBoolean("template", false);
    }

    public Material sourceMaterial(String itemId) {
        return sourceMaterials.get(itemId.toLowerCase(Locale.ROOT));
    }

    public String itemModel(String itemId) {
        return itemModels.getOrDefault(itemId.toLowerCase(Locale.ROOT), "");
    }

    public List<String> categoryPath(String itemId) {
        ItemsAdderSourceItem sourceItem = sourceItems.get(itemId.toLowerCase(Locale.ROOT));
        if (sourceItem != null) {
            return sourceItem.categoryPath();
        }
        String namespace = itemId.contains(":") ? itemId.substring(0, itemId.indexOf(':')) : "other";
        return List.of(namespace.toLowerCase(Locale.ROOT));
    }

    private static IndexedItems indexItems(
            SeaPack plugin,
            List<ItemsAdderSourceDocument> documents
    ) {
        Map<String, ItemsAdderSourceItem> sourceItems = new LinkedHashMap<>();
        Set<String> namespaces = new LinkedHashSet<>();
        for (ItemsAdderSourceDocument document : documents) {
            String namespace = document.namespace();
            ConfigurationSection itemsSection = document.configuration().getConfigurationSection("items");
            if (namespace.isBlank() || itemsSection == null) {
                continue;
            }
            namespaces.add(namespace);
            for (String itemKey : itemsSection.getKeys(false)) {
                ConfigurationSection itemSection = itemsSection.getConfigurationSection(itemKey);
                if (itemSection == null) {
                    continue;
                }
                String itemId = ItemsAdderIds.qualified(namespace, itemKey);
                List<String> categoryPath = new ArrayList<>();
                categoryPath.add(namespace);
                categoryPath.addAll(document.categoryFolders());
                ItemsAdderSourceItem previous = sourceItems.put(itemId, new ItemsAdderSourceItem(
                        namespace,
                        itemSection,
                        List.copyOf(categoryPath)
                ));
                if (previous != null) {
                    plugin.getLogger().warning("Duplicate ItemsAdder item definition '" + itemId
                            + "' found in contents; the later config file takes precedence.");
                }
            }
        }
        return new IndexedItems(Map.copyOf(sourceItems), Set.copyOf(namespaces));
    }

    private record IndexedItems(
            Map<String, ItemsAdderSourceItem> items,
            Set<String> namespaces
    ) {
    }
}
