package com.seapack.feature.item;

import com.seapack.feature.furniture.FurnitureSettings;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public final class ItemCatalog {
    private final List<CustomItemDefinition> items;
    private final Map<String, CustomItemDefinition> itemsById;
    private final Map<String, FurnitureSettings> furnitureSettings;
    private final Map<String, String> setGroupAliases;
    private final Map<String, List<CustomItemDefinition>> directItemsByPath;
    private final Map<String, List<CustomItemDefinition>> treeItemsByPath;
    private final Map<String, List<String>> childrenByPath;
    private final Map<String, List<CustomItemDefinition>> itemsBySetGroup;
    private final Map<FurnitureItemKey, CustomItemDefinition> furnitureByMaterialAndModel;
    private final ItemSearch itemSearch;

    public ItemCatalog(
            List<CustomItemDefinition> items,
            Map<String, FurnitureSettings> furnitureSettings,
            Map<String, String> setGroupAliases
    ) {
        this.items = List.copyOf(items);
        this.furnitureSettings = normalizedMap(furnitureSettings);
        this.setGroupAliases = Map.copyOf(setGroupAliases);
        this.itemsById = indexItems(this.items);
        this.directItemsByPath = new LinkedHashMap<>();
        this.treeItemsByPath = new LinkedHashMap<>();
        this.childrenByPath = new LinkedHashMap<>();
        this.itemsBySetGroup = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        this.furnitureByMaterialAndModel = new LinkedHashMap<>();
        indexCategoriesAndFurniture();
        this.itemSearch = new ItemSearch(this.items, this::resolvedSetGroup);
    }

    public static ItemCatalog empty() {
        return new ItemCatalog(List.of(), Map.of(), Map.of());
    }

    public List<CustomItemDefinition> items() {
        return items;
    }

    public CustomItemDefinition item(String itemId) {
        return itemId == null ? null : itemsById.get(itemId.toLowerCase(Locale.ROOT));
    }

    public FurnitureSettings furniture(String itemId) {
        return itemId == null ? null : furnitureSettings.get(itemId.toLowerCase(Locale.ROOT));
    }

    public int furnitureCount() {
        return (int) furnitureSettings.values().stream().filter(FurnitureSettings::enabled).count();
    }

    @SuppressWarnings("deprecation")
    public CustomItemDefinition identifyFurniture(ItemStack itemStack, NamespacedKey itemIdKey) {
        if (itemStack == null || itemStack.getType().isAir() || !itemStack.hasItemMeta()) {
            return null;
        }

        String taggedId = itemStack.getItemMeta().getPersistentDataContainer()
                .get(itemIdKey, PersistentDataType.STRING);
        CustomItemDefinition taggedItem = item(taggedId);
        if (taggedItem != null && furniture(taggedItem.id()) != null) {
            return taggedItem;
        }
        if (!itemStack.getItemMeta().hasCustomModelData()) {
            return null;
        }
        return furnitureByMaterialAndModel.get(new FurnitureItemKey(
                itemStack.getType().name(),
                itemStack.getItemMeta().getCustomModelData()
        ));
    }

    public List<String> categories() {
        return childCategories(List.of());
    }

    public List<String> childCategories(List<String> parentPath) {
        return childrenByPath.getOrDefault(pathKey(parentPath), List.of());
    }

    public List<CustomItemDefinition> itemsInCategory(String category) {
        return itemsInCategoryTree(List.of(category));
    }

    public List<CustomItemDefinition> itemsDirectlyInCategory(List<String> categoryPath) {
        return directItemsByPath.getOrDefault(pathKey(categoryPath), List.of());
    }

    public List<CustomItemDefinition> itemsInCategoryTree(List<String> categoryPath) {
        return treeItemsByPath.getOrDefault(pathKey(categoryPath), List.of());
    }

    public int categoryTreeCount(List<String> categoryPath) {
        return itemsInCategoryTree(categoryPath).size();
    }

    public List<String> setGroups() {
        return List.copyOf(itemsBySetGroup.keySet());
    }

    public List<CustomItemDefinition> itemsInSetGroup(String setGroup) {
        return itemsBySetGroup.getOrDefault(setGroup, List.of());
    }

    public Map<String, Integer> setGroupCounts() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        itemsBySetGroup.forEach((group, groupItems) -> counts.put(group, groupItems.size()));
        return counts;
    }

    public Map<String, Integer> categoryCounts() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String category : categories()) {
            counts.put(category, itemsInCategory(category).size());
        }
        return counts;
    }

    public List<CustomItemDefinition> search(String query) {
        return itemSearch.search(query);
    }

    private void indexCategoriesAndFurniture() {
        Map<String, TreeMap<String, String>> childNames = new LinkedHashMap<>();
        Map<String, List<CustomItemDefinition>> mutableSetGroups = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        for (CustomItemDefinition item : items) {
            List<String> path = item.categoryPath();
            for (int depth = 0; depth <= path.size(); depth++) {
                List<String> prefix = path.subList(0, depth);
                treeItemsByPath.computeIfAbsent(pathKey(prefix), ignored -> new ArrayList<>()).add(item);
                if (depth < path.size()) {
                    childNames.computeIfAbsent(pathKey(prefix), ignored -> new TreeMap<>(String.CASE_INSENSITIVE_ORDER))
                            .putIfAbsent(path.get(depth), path.get(depth));
                }
            }
            directItemsByPath.computeIfAbsent(pathKey(path), ignored -> new ArrayList<>()).add(item);

            if (!path.isEmpty() && path.getFirst().equalsIgnoreCase("sets")) {
                String setGroup = resolvedSetGroup(item);
                if (!setGroup.isBlank()) {
                    mutableSetGroups.computeIfAbsent(setGroup, ignored -> new ArrayList<>()).add(item);
                }
            }

            FurnitureSettings settings = furniture(item.id());
            if (settings != null) {
                furnitureByMaterialAndModel.putIfAbsent(
                        new FurnitureItemKey(item.material().name(), item.customModelData()),
                        item
                );
            }
        }

        directItemsByPath.replaceAll((path, pathItems) -> List.copyOf(pathItems));
        treeItemsByPath.replaceAll((path, pathItems) -> List.copyOf(pathItems));
        childNames.forEach((path, names) -> childrenByPath.put(path, List.copyOf(names.values())));
        mutableSetGroups.forEach((group, groupItems) -> itemsBySetGroup.put(group, List.copyOf(groupItems)));
    }

    private String resolvedSetGroup(CustomItemDefinition item) {
        String rawGroup = item.setGroup();
        return setGroupAliases.getOrDefault(rawGroup, rawGroup);
    }

    private static Map<String, CustomItemDefinition> indexItems(List<CustomItemDefinition> items) {
        Map<String, CustomItemDefinition> indexed = new LinkedHashMap<>();
        items.forEach(item -> indexed.put(item.id().toLowerCase(Locale.ROOT), item));
        return Map.copyOf(indexed);
    }

    private static <T> Map<String, T> normalizedMap(Map<String, T> source) {
        Map<String, T> normalized = new LinkedHashMap<>();
        source.forEach((key, value) -> normalized.put(key.toLowerCase(Locale.ROOT), value));
        return Map.copyOf(normalized);
    }

    private static String pathKey(List<String> path) {
        return path.stream()
                .map(part -> part.toLowerCase(Locale.ROOT))
                .reduce((left, right) -> left + "\u0000" + right)
                .orElse("");
    }

    private record FurnitureItemKey(String material, int customModelData) {
    }
}
