package com.seapack.feature.menu;

import com.seapack.SeaPack;
import com.seapack.feature.item.CustomItemDefinition;
import com.seapack.feature.item.ItemManager;
import com.seapack.util.TextUtils;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

public final class ItemMenuManager {
    private final SeaPack plugin;
    private final ItemManager itemManager;
    private final ItemMenuRenderer renderer;

    public ItemMenuManager(SeaPack plugin, ItemManager itemManager) {
        this.plugin = plugin;
        this.itemManager = itemManager;
        this.renderer = new ItemMenuRenderer(plugin);
    }

    public void open(Player player) {
        openCategories(player, List.of(), 0);
    }

    public void open(Player player, List<CustomItemDefinition> items, int page, String query) {
        openItems(player, items, page, query, "SeaPack Items", List.of(), false, "");
    }

    public void closeOpenMenus() {
        Bukkit.getOnlinePlayers().stream()
                .filter(player -> player.getOpenInventory().getTopInventory().getHolder() instanceof MenuView)
                .forEach(Player::closeInventory);
    }

    public void handleClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof MenuView view)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= ItemMenuRenderer.MENU_SIZE) {
            return;
        }
        if (view.mode() == MenuMode.CATEGORIES) {
            handleCategoryClick(player, view, slot);
        } else if (view.mode() == MenuMode.SET_GROUPS) {
            handleSetGroupClick(player, view, slot);
        } else {
            handleItemClick(event, player, view, slot);
        }
    }

    private void openCategories(Player player, List<String> parentPath, int page) {
        List<MenuCategory> entries = new ArrayList<>();
        List<CustomItemDefinition> directItems = parentPath.isEmpty()
                ? List.of()
                : itemManager.itemsDirectlyInCategory(parentPath);
        if (!directItems.isEmpty()) {
            entries.add(MenuCategory.directItems(parentPath, directItems.size()));
        }
        for (String child : itemManager.childCategories(parentPath)) {
            List<String> childPath = appendPath(parentPath, child);
            entries.add(MenuCategory.folder(childPath, itemManager.categoryTreeCount(childPath)));
        }
        player.openInventory(renderer.renderCategories(entries, parentPath, page));
    }

    private void openSetGroups(Player player, int page) {
        player.openInventory(renderer.renderSetGroups(
                itemManager.setGroups(),
                itemManager.setGroupCounts(),
                page
        ));
    }

    private void openItems(
            Player player,
            List<CustomItemDefinition> items,
            int page,
            String query,
            String titleLabel,
            List<String> backCategoryPath,
            boolean hasCategoryBack,
            String setGroup
    ) {
        player.openInventory(renderer.renderItems(
                items,
                page,
                query,
                titleLabel,
                backCategoryPath,
                hasCategoryBack,
                setGroup
        ));
    }

    private void handleCategoryClick(Player player, MenuView view, int slot) {
        if (slot == ItemMenuRenderer.PREVIOUS_SLOT && view.page() > 0) {
            openCategories(player, view.categoryPath(), view.page() - 1);
            return;
        }
        if (slot == ItemMenuRenderer.BACK_SLOT && !view.categoryPath().isEmpty()) {
            openCategories(player, parentPath(view.categoryPath()), 0);
            return;
        }

        int maxPage = ItemMenuRenderer.maxPage(view.categoryEntries().size());
        if (slot == ItemMenuRenderer.NEXT_SLOT && view.page() < maxPage) {
            openCategories(player, view.categoryPath(), view.page() + 1);
            return;
        }

        int categoryIndex = view.page() * ItemMenuRenderer.ITEMS_PER_PAGE + slot;
        if (slot >= ItemMenuRenderer.ITEMS_PER_PAGE || categoryIndex >= view.categoryEntries().size()) {
            return;
        }
        MenuCategory entry = view.categoryEntries().get(categoryIndex);
        if (entry.directItems()) {
            openItems(
                    player,
                    itemManager.itemsDirectlyInCategory(entry.path()),
                    0,
                    "",
                    ItemMenuRenderer.prettify(entry.path().getLast()) + " Items",
                    view.categoryPath(),
                    true,
                    ""
            );
            return;
        }
        if (entry.path().size() == 1 && entry.path().getFirst().equalsIgnoreCase("sets")) {
            openSetGroups(player, 0);
            return;
        }
        if (!itemManager.childCategories(entry.path()).isEmpty()) {
            openCategories(player, entry.path(), 0);
            return;
        }
        openItems(
                player,
                itemManager.itemsDirectlyInCategory(entry.path()),
                0,
                "",
                ItemMenuRenderer.prettify(entry.path().getLast()),
                view.categoryPath(),
                true,
                ""
        );
    }

    private void handleSetGroupClick(Player player, MenuView view, int slot) {
        if (slot == ItemMenuRenderer.PREVIOUS_SLOT && view.page() > 0) {
            openSetGroups(player, view.page() - 1);
            return;
        }
        if (slot == ItemMenuRenderer.BACK_SLOT) {
            openCategories(player, List.of(), 0);
            return;
        }

        int maxPage = ItemMenuRenderer.maxPage(view.setGroups().size());
        if (slot == ItemMenuRenderer.NEXT_SLOT && view.page() < maxPage) {
            openSetGroups(player, view.page() + 1);
            return;
        }

        int setGroupIndex = view.page() * ItemMenuRenderer.ITEMS_PER_PAGE + slot;
        if (slot >= ItemMenuRenderer.ITEMS_PER_PAGE || setGroupIndex >= view.setGroups().size()) {
            return;
        }
        String setGroup = view.setGroups().get(setGroupIndex);
        openItems(
                player,
                itemManager.itemsInSetGroup(setGroup),
                0,
                "",
                ItemMenuRenderer.prettify(setGroup),
                List.of(),
                false,
                setGroup
        );
    }

    private void handleItemClick(InventoryClickEvent event, Player player, MenuView view, int slot) {
        if (slot == ItemMenuRenderer.PREVIOUS_SLOT && view.page() > 0) {
            reopenItems(player, view, view.page() - 1);
            return;
        }
        if (slot == ItemMenuRenderer.BACK_SLOT) {
            if (!view.setGroup().isBlank()) {
                openSetGroups(player, 0);
                return;
            }
            if (view.hasCategoryBack()) {
                openCategories(player, view.categoryPath(), 0);
                return;
            }
        }

        int maxPage = ItemMenuRenderer.maxPage(view.items().size());
        if (slot == ItemMenuRenderer.NEXT_SLOT && view.page() < maxPage) {
            reopenItems(player, view, view.page() + 1);
            return;
        }

        int itemIndex = view.page() * ItemMenuRenderer.ITEMS_PER_PAGE + slot;
        if (slot >= ItemMenuRenderer.ITEMS_PER_PAGE || itemIndex >= view.items().size()) {
            return;
        }
        CustomItemDefinition snapshot = view.items().get(itemIndex);
        CustomItemDefinition definition = itemManager.item(snapshot.id());
        if (definition == null) {
            player.sendMessage(Component.text(
                    "That item is no longer available. Reopen the menu.",
                    NamedTextColor.RED
            ));
            player.closeInventory();
            return;
        }

        int amount = event.isShiftClick() ? definition.material().getMaxStackSize() : 1;
        player.getInventory().addItem(definition.createItem(amount, plugin.itemIdKey())).values()
                .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        player.sendMessage(Component.text("Gave ", NamedTextColor.AQUA)
                .append(Component.text(amount + "x ", NamedTextColor.WHITE))
                .append(TextUtils.component(definition.displayName())));
    }

    private void reopenItems(Player player, MenuView view, int page) {
        openItems(
                player,
                view.items(),
                page,
                view.query(),
                view.titleLabel(),
                view.categoryPath(),
                view.hasCategoryBack(),
                view.setGroup()
        );
    }

    private static List<String> appendPath(List<String> parentPath, String child) {
        List<String> path = new ArrayList<>(parentPath);
        path.add(child);
        return List.copyOf(path);
    }

    private static List<String> parentPath(List<String> path) {
        return path.isEmpty() ? List.of() : List.copyOf(path.subList(0, path.size() - 1));
    }
}
