package com.seapack.feature.item;

import com.seapack.SeaPack;
import com.seapack.feature.furniture.FurnitureSettings;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;

public final class ItemManager {
    private final SeaPack plugin;
    private final ItemLoader loader;
    private ItemCatalog catalog = ItemCatalog.empty();
    private ItemLoadStats stats = ItemLoadStats.EMPTY;

    public ItemManager(SeaPack plugin) {
        this.plugin = plugin;
        this.loader = new ItemLoader(plugin);
    }

    public boolean reload() {
        try {
            ItemLoader.Result replacement = loader.load();
            if (replacement == null) {
                plugin.getLogger().warning("SeaPack reload rejected because no valid current items were found."
                        + " The previous registry remains active.");
                return false;
            }
            catalog = replacement.catalog();
            stats = replacement.stats();
            return true;
        } catch (RuntimeException exception) {
            plugin.getLogger().log(
                    Level.SEVERE,
                    "SeaPack reload failed. The previous registry remains active.",
                    exception
            );
            return false;
        }
    }

    public List<CustomItemDefinition> items() {
        return catalog.items();
    }

    public CustomItemDefinition item(String itemId) {
        return catalog.item(itemId);
    }

    public FurnitureSettings furniture(String itemId) {
        return catalog.furniture(itemId);
    }

    public CustomItemDefinition identifyFurniture(ItemStack itemStack, NamespacedKey itemIdKey) {
        return catalog.identifyFurniture(itemStack, itemIdKey);
    }

    public List<String> categories() {
        return catalog.categories();
    }

    public List<String> childCategories(List<String> parentPath) {
        return catalog.childCategories(parentPath);
    }

    public List<CustomItemDefinition> itemsInCategory(String category) {
        return catalog.itemsInCategory(category);
    }

    public List<CustomItemDefinition> itemsDirectlyInCategory(List<String> categoryPath) {
        return catalog.itemsDirectlyInCategory(categoryPath);
    }

    public List<CustomItemDefinition> itemsInCategoryTree(List<String> categoryPath) {
        return catalog.itemsInCategoryTree(categoryPath);
    }

    public int categoryTreeCount(List<String> categoryPath) {
        return catalog.categoryTreeCount(categoryPath);
    }

    public List<String> setGroups() {
        return catalog.setGroups();
    }

    public List<CustomItemDefinition> itemsInSetGroup(String setGroup) {
        return catalog.itemsInSetGroup(setGroup);
    }

    public Map<String, Integer> setGroupCounts() {
        return catalog.setGroupCounts();
    }

    public Map<String, Integer> categoryCounts() {
        return catalog.categoryCounts();
    }

    public List<CustomItemDefinition> search(String query) {
        return catalog.search(query);
    }

    public int armorRuleCount() {
        return stats.armorRuleCount();
    }

    public int armorSourceCount() {
        return stats.armorSourceCount();
    }

    public int armorDocumentCount() {
        return stats.armorDocumentCount();
    }

    public int namespaceCount() {
        return stats.namespaceCount();
    }

    public int customConfigCount() {
        return stats.customConfigCount();
    }

    public int customConfigCreatedCount() {
        return stats.customConfigCreatedCount();
    }

    public int customItemEntryCreatedCount() {
        return stats.customItemEntryCreatedCount();
    }

    public int furnitureCount() {
        return catalog.furnitureCount();
    }

    public boolean furnitureConfigCreated() {
        return stats.furnitureConfigCreated();
    }

    public int furnitureConfigItemCreatedCount() {
        return stats.furnitureConfigItemCreatedCount();
    }
}
