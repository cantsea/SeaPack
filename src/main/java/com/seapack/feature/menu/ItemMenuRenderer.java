package com.seapack.feature.menu;

import com.seapack.SeaPack;
import com.seapack.feature.item.CustomItemDefinition;
import com.seapack.util.TextUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class ItemMenuRenderer {
    static final int MENU_SIZE = 54;
    static final int ITEMS_PER_PAGE = 45;
    static final int PREVIOUS_SLOT = 45;
    static final int BACK_SLOT = 49;
    static final int NEXT_SLOT = 53;

    private final SeaPack plugin;

    public ItemMenuRenderer(SeaPack plugin) {
        this.plugin = plugin;
    }

    public Inventory renderCategories(
            List<MenuCategory> entries,
            List<String> parentPath,
            int page
    ) {
        int maxPage = maxPage(entries.size());
        int currentPage = currentPage(page, maxPage);
        String categoryTitle = parentPath.isEmpty()
                ? "SeaPack Categories "
                : prettify(parentPath.getLast()) + " ";
        Inventory inventory = Bukkit.createInventory(
                MenuView.categories(entries, parentPath, currentPage),
                MENU_SIZE,
                ChatColor.DARK_AQUA + categoryTitle + ChatColor.GRAY
                        + "(" + (currentPage + 1) + "/" + (maxPage + 1) + ")"
        );

        int start = currentPage * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, entries.size());
        for (int index = start; index < end; index++) {
            MenuCategory entry = entries.get(index);
            Material icon = entry.directItems() ? Material.ENDER_CHEST : Material.CHEST;
            ItemStack categoryItem = navigationItem(icon, ChatColor.AQUA + entry.displayName());
            ItemMeta meta = categoryItem.getItemMeta();
            meta.lore(List.of(
                    Component.text("Items: ", NamedTextColor.GRAY)
                            .append(Component.text(entry.itemCount(), NamedTextColor.WHITE)),
                    Component.empty(),
                    Component.text("Click to open", NamedTextColor.GRAY)
            ));
            categoryItem.setItemMeta(meta);
            inventory.setItem(index - start, categoryItem);
        }

        addNavigation(inventory, currentPage, maxPage, !parentPath.isEmpty(), "Back");
        return inventory;
    }

    public Inventory renderSetGroups(
            List<String> setGroups,
            Map<String, Integer> setGroupCounts,
            int page
    ) {
        int maxPage = maxPage(setGroups.size());
        int currentPage = currentPage(page, maxPage);
        Inventory inventory = Bukkit.createInventory(
                MenuView.setGroups(setGroups, currentPage),
                MENU_SIZE,
                ChatColor.DARK_AQUA + "Sets " + ChatColor.GRAY
                        + "(" + (currentPage + 1) + "/" + (maxPage + 1) + ")"
        );

        int start = currentPage * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, setGroups.size());
        for (int index = start; index < end; index++) {
            String setGroup = setGroups.get(index);
            ItemStack setItem = navigationItem(Material.ENDER_CHEST, ChatColor.AQUA + prettify(setGroup));
            ItemMeta meta = setItem.getItemMeta();
            meta.lore(List.of(
                    Component.text("Items: ", NamedTextColor.GRAY)
                            .append(Component.text(setGroupCounts.getOrDefault(setGroup, 0), NamedTextColor.WHITE)),
                    Component.empty(),
                    Component.text("Click to open", NamedTextColor.GRAY)
            ));
            setItem.setItemMeta(meta);
            inventory.setItem(index - start, setItem);
        }

        addNavigation(inventory, currentPage, maxPage, true, "Back to Categories");
        return inventory;
    }

    public Inventory renderItems(
            List<CustomItemDefinition> items,
            int page,
            String query,
            String titleLabel,
            List<String> backCategoryPath,
            boolean hasCategoryBack,
            String setGroup
    ) {
        int maxPage = maxPage(items.size());
        int currentPage = currentPage(page, maxPage);
        String title = !setGroup.isBlank()
                ? ChatColor.DARK_AQUA + prettify(setGroup) + " "
                : ChatColor.DARK_AQUA + titleLabel + " ";
        Inventory inventory = Bukkit.createInventory(
                MenuView.items(
                        items,
                        currentPage,
                        query,
                        titleLabel,
                        backCategoryPath,
                        hasCategoryBack,
                        setGroup
                ),
                MENU_SIZE,
                title + ChatColor.GRAY + "(" + (currentPage + 1) + "/" + (maxPage + 1) + ")"
        );

        int start = currentPage * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, items.size());
        for (int index = start; index < end; index++) {
            CustomItemDefinition definition = items.get(index);
            ItemStack menuItem = definition.createItem(1, plugin.itemIdKey());
            ItemMeta meta = menuItem.getItemMeta();
            List<Component> lore = meta.lore() == null
                    ? new ArrayList<>()
                    : new ArrayList<>(meta.lore());
            lore.add(Component.empty());
            lore.add(Component.text("ID: ", NamedTextColor.GRAY)
                    .append(Component.text(definition.id(), NamedTextColor.WHITE)));
            lore.add(Component.text("Material: ", NamedTextColor.GRAY)
                    .append(Component.text(definition.material().name(), NamedTextColor.WHITE)));
            lore.add(Component.text("CMD: ", NamedTextColor.GRAY)
                    .append(Component.text(definition.customModelData(), NamedTextColor.WHITE)));
            if (!definition.itemModel().isBlank()) {
                lore.add(Component.text("Item model: ", NamedTextColor.GRAY)
                        .append(Component.text(definition.itemModel(), NamedTextColor.WHITE)));
            }
            lore.add(Component.text("Left click: 1", NamedTextColor.GRAY));
            lore.add(Component.text("Shift click: " + definition.material().getMaxStackSize(), NamedTextColor.GRAY));
            meta.lore(lore);
            menuItem.setItemMeta(meta);
            inventory.setItem(index - start, menuItem);
        }

        String backName = setGroup.isBlank() ? "Back" : "Back to Sets";
        addNavigation(inventory, currentPage, maxPage, hasCategoryBack || !setGroup.isBlank(), backName);
        return inventory;
    }

    static int maxPage(int itemCount) {
        return Math.max(0, (itemCount - 1) / ITEMS_PER_PAGE);
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

    private static int currentPage(int page, int maxPage) {
        return Math.max(0, Math.min(page, maxPage));
    }

    private static void addNavigation(
            Inventory inventory,
            int currentPage,
            int maxPage,
            boolean showBack,
            String backName
    ) {
        if (currentPage > 0) {
            inventory.setItem(PREVIOUS_SLOT, navigationItem(Material.ARROW, ChatColor.YELLOW + "Previous Page"));
        }
        if (showBack) {
            inventory.setItem(BACK_SLOT, navigationItem(Material.BARRIER, ChatColor.YELLOW + backName));
        }
        if (currentPage < maxPage) {
            inventory.setItem(NEXT_SLOT, navigationItem(Material.ARROW, ChatColor.YELLOW + "Next Page"));
        }
    }

    private static ItemStack navigationItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(TextUtils.component(name));
        item.setItemMeta(meta);
        return item;
    }
}
