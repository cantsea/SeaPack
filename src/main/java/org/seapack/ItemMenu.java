package org.seapack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class ItemMenu implements Listener {
    private static final int MENU_SIZE = 54;
    private static final int ITEMS_PER_PAGE = 45;
    private static final int PREVIOUS_SLOT = 45;
    private static final int BACK_SLOT = 49;
    private static final int NEXT_SLOT = 53;

    private final SeaPack plugin;
    private final CustomItemRegistry itemRegistry;

    public ItemMenu(SeaPack plugin, CustomItemRegistry itemRegistry) {
        this.plugin = plugin;
        this.itemRegistry = itemRegistry;
    }

    public void open(Player player) {
        openCategories(player, 0);
    }

    public void closeOpenMenus() {
        Bukkit.getOnlinePlayers().stream()
                .filter(player -> player.getOpenInventory().getTopInventory().getHolder() instanceof MenuHolder)
                .forEach(Player::closeInventory);
    }

    public void openCategories(Player player, int page) {
        openCategories(player, List.of(), page);
    }

    private void openCategories(Player player, List<String> parentPath, int page) {
        List<CategoryEntry> entries = new ArrayList<>();
        List<CustomItemDefinition> directItems = parentPath.isEmpty()
                ? List.of()
                : itemRegistry.itemsDirectlyInCategory(parentPath);
        if (!directItems.isEmpty()) {
            entries.add(CategoryEntry.directItems(parentPath, directItems.size()));
        }
        for (String child : itemRegistry.childCategories(parentPath)) {
            List<String> childPath = appendPath(parentPath, child);
            entries.add(CategoryEntry.folder(childPath, itemRegistry.categoryTreeCount(childPath)));
        }

        int maxPage = Math.max(0, (entries.size() - 1) / ITEMS_PER_PAGE);
        int currentPage = Math.max(0, Math.min(page, maxPage));
        String categoryTitle = parentPath.isEmpty()
                ? "SeaPack Categories "
                : prettify(parentPath.getLast()) + " ";

        Inventory inventory = Bukkit.createInventory(
                MenuHolder.categories(entries, parentPath, currentPage),
                MENU_SIZE,
                ChatColor.DARK_AQUA + categoryTitle + ChatColor.GRAY
                        + "(" + (currentPage + 1) + "/" + (maxPage + 1) + ")"
        );

        int start = currentPage * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, entries.size());
        for (int index = start; index < end; index++) {
            CategoryEntry entry = entries.get(index);
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

        if (currentPage > 0) {
            inventory.setItem(PREVIOUS_SLOT, navigationItem(Material.ARROW, ChatColor.YELLOW + "Previous Page"));
        }
        if (!parentPath.isEmpty()) {
            inventory.setItem(BACK_SLOT, navigationItem(Material.BARRIER, ChatColor.YELLOW + "Back"));
        }
        if (currentPage < maxPage) {
            inventory.setItem(NEXT_SLOT, navigationItem(Material.ARROW, ChatColor.YELLOW + "Next Page"));
        }

        player.openInventory(inventory);
    }

    public void open(Player player, List<CustomItemDefinition> items, int page, String query) {
        openItems(player, items, page, query, "SeaPack Items", List.of(), false, "");
    }

    private void openSetGroups(Player player, int page) {
        List<String> setGroups = itemRegistry.setGroups();
        Map<String, Integer> setGroupCounts = itemRegistry.setGroupCounts();
        int maxPage = Math.max(0, (setGroups.size() - 1) / ITEMS_PER_PAGE);
        int currentPage = Math.max(0, Math.min(page, maxPage));

        Inventory inventory = Bukkit.createInventory(
                MenuHolder.setGroups(setGroups, currentPage),
                MENU_SIZE,
                ChatColor.DARK_AQUA + "Sets " + ChatColor.GRAY + "(" + (currentPage + 1) + "/" + (maxPage + 1) + ")"
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

        if (currentPage > 0) {
            inventory.setItem(PREVIOUS_SLOT, navigationItem(Material.ARROW, ChatColor.YELLOW + "Previous Page"));
        }
        inventory.setItem(BACK_SLOT, navigationItem(Material.BARRIER, ChatColor.YELLOW + "Back to Categories"));
        if (currentPage < maxPage) {
            inventory.setItem(NEXT_SLOT, navigationItem(Material.ARROW, ChatColor.YELLOW + "Next Page"));
        }

        player.openInventory(inventory);
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
        int maxPage = Math.max(0, (items.size() - 1) / ITEMS_PER_PAGE);
        int currentPage = Math.max(0, Math.min(page, maxPage));
        String title = !setGroup.isBlank()
                ? ChatColor.DARK_AQUA + prettify(setGroup) + " "
                : ChatColor.DARK_AQUA + titleLabel + " ";

        Inventory inventory = Bukkit.createInventory(
                MenuHolder.items(
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
            List<Component> lore = meta.lore() == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(meta.lore());
            lore.add(Component.empty());
            lore.add(Component.text("ID: ", NamedTextColor.GRAY).append(Component.text(definition.id(), NamedTextColor.WHITE)));
            lore.add(Component.text("Material: ", NamedTextColor.GRAY).append(Component.text(definition.material().name(), NamedTextColor.WHITE)));
            lore.add(Component.text("CMD: ", NamedTextColor.GRAY).append(Component.text(definition.customModelData(), NamedTextColor.WHITE)));
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

        if (currentPage > 0) {
            inventory.setItem(PREVIOUS_SLOT, navigationItem(Material.ARROW, ChatColor.YELLOW + "Previous Page"));
        }
        if (hasCategoryBack || !setGroup.isBlank()) {
            String backName = setGroup.isBlank() ? "Back" : "Back to Sets";
            inventory.setItem(BACK_SLOT, navigationItem(Material.BARRIER, ChatColor.YELLOW + backName));
        }
        if (currentPage < maxPage) {
            inventory.setItem(NEXT_SLOT, navigationItem(Material.ARROW, ChatColor.YELLOW + "Next Page"));
        }

        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof MenuHolder holder)) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= MENU_SIZE) {
            return;
        }

        if (holder.mode() == MenuMode.CATEGORIES) {
            handleCategoryClick(player, holder, slot);
            return;
        }

        if (holder.mode() == MenuMode.SET_GROUPS) {
            handleSetGroupClick(player, holder, slot);
            return;
        }

        handleItemClick(event, player, holder, slot);
    }

    private void handleCategoryClick(Player player, MenuHolder holder, int slot) {
        if (slot == PREVIOUS_SLOT && holder.page() > 0) {
            openCategories(player, holder.categoryPath(), holder.page() - 1);
            return;
        }

        if (slot == BACK_SLOT && !holder.categoryPath().isEmpty()) {
            openCategories(player, parentPath(holder.categoryPath()), 0);
            return;
        }

        int maxPage = Math.max(0, (holder.categoryEntries().size() - 1) / ITEMS_PER_PAGE);
        if (slot == NEXT_SLOT && holder.page() < maxPage) {
            openCategories(player, holder.categoryPath(), holder.page() + 1);
            return;
        }

        int categoryIndex = holder.page() * ITEMS_PER_PAGE + slot;
        if (slot >= ITEMS_PER_PAGE || categoryIndex >= holder.categoryEntries().size()) {
            return;
        }

        CategoryEntry entry = holder.categoryEntries().get(categoryIndex);
        if (entry.directItems()) {
            openItems(
                    player,
                    itemRegistry.itemsDirectlyInCategory(entry.path()),
                    0,
                    "",
                    prettify(entry.path().getLast()) + " Items",
                    holder.categoryPath(),
                    true,
                    ""
            );
            return;
        }
        if (entry.path().size() == 1 && entry.path().getFirst().equalsIgnoreCase("sets")) {
            openSetGroups(player, 0);
            return;
        }

        List<String> children = itemRegistry.childCategories(entry.path());
        if (!children.isEmpty()) {
            openCategories(player, entry.path(), 0);
            return;
        }
        openItems(
                player,
                itemRegistry.itemsDirectlyInCategory(entry.path()),
                0,
                "",
                prettify(entry.path().getLast()),
                holder.categoryPath(),
                true,
                ""
        );
    }

    private void handleSetGroupClick(Player player, MenuHolder holder, int slot) {
        if (slot == PREVIOUS_SLOT && holder.page() > 0) {
            openSetGroups(player, holder.page() - 1);
            return;
        }

        if (slot == BACK_SLOT) {
            openCategories(player, 0);
            return;
        }

        int maxPage = Math.max(0, (holder.setGroups().size() - 1) / ITEMS_PER_PAGE);
        if (slot == NEXT_SLOT && holder.page() < maxPage) {
            openSetGroups(player, holder.page() + 1);
            return;
        }

        int setGroupIndex = holder.page() * ITEMS_PER_PAGE + slot;
        if (slot >= ITEMS_PER_PAGE || setGroupIndex >= holder.setGroups().size()) {
            return;
        }

        String setGroup = holder.setGroups().get(setGroupIndex);
        openItems(
                player,
                itemRegistry.itemsInSetGroup(setGroup),
                0,
                "",
                prettify(setGroup),
                List.of(),
                false,
                setGroup
        );
    }

    private void handleItemClick(InventoryClickEvent event, Player player, MenuHolder holder, int slot) {
        if (slot == PREVIOUS_SLOT && holder.page() > 0) {
            openItems(
                    player,
                    holder.items(),
                    holder.page() - 1,
                    holder.query(),
                    holder.titleLabel(),
                    holder.categoryPath(),
                    holder.hasCategoryBack(),
                    holder.setGroup()
            );
            return;
        }

        if (slot == BACK_SLOT) {
            if (!holder.setGroup().isBlank()) {
                openSetGroups(player, 0);
                return;
            }
            if (holder.hasCategoryBack()) {
                openCategories(player, holder.categoryPath(), 0);
                return;
            }
        }

        int maxPage = Math.max(0, (holder.items().size() - 1) / ITEMS_PER_PAGE);
        if (slot == NEXT_SLOT && holder.page() < maxPage) {
            openItems(
                    player,
                    holder.items(),
                    holder.page() + 1,
                    holder.query(),
                    holder.titleLabel(),
                    holder.categoryPath(),
                    holder.hasCategoryBack(),
                    holder.setGroup()
            );
            return;
        }

        int itemIndex = holder.page() * ITEMS_PER_PAGE + slot;
        if (slot >= ITEMS_PER_PAGE || itemIndex >= holder.items().size()) {
            return;
        }

        CustomItemDefinition snapshot = holder.items().get(itemIndex);
        CustomItemDefinition definition = itemRegistry.item(snapshot.id());
        if (definition == null) {
            player.sendMessage(Component.text("That item is no longer available. Reopen the menu.", NamedTextColor.RED));
            player.closeInventory();
            return;
        }
        int amount = event.isShiftClick() ? definition.material().getMaxStackSize() : 1;
        player.getInventory().addItem(definition.createItem(amount, plugin.itemIdKey())).values()
                .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        player.sendMessage(Component.text("Gave ", NamedTextColor.AQUA)
                .append(Component.text(amount + "x ", NamedTextColor.WHITE))
                .append(SeaText.component(definition.displayName())));
    }

    private static ItemStack navigationItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(SeaText.component(name));
        item.setItemMeta(meta);
        return item;
    }

    private static String prettify(String text) {
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

    private static List<String> appendPath(List<String> parentPath, String child) {
        List<String> path = new ArrayList<>(parentPath);
        path.add(child);
        return List.copyOf(path);
    }

    private static List<String> parentPath(List<String> path) {
        return path.isEmpty() ? List.of() : List.copyOf(path.subList(0, path.size() - 1));
    }

    private enum MenuMode {
        CATEGORIES,
        SET_GROUPS,
        ITEMS
    }

    private record MenuHolder(
            MenuMode mode,
            List<CategoryEntry> categoryEntries,
            List<String> categoryPath,
            List<String> setGroups,
            List<CustomItemDefinition> items,
            int page,
            String query,
            String titleLabel,
            boolean hasCategoryBack,
            String setGroup
    ) implements InventoryHolder {
        private static MenuHolder categories(
                List<CategoryEntry> categoryEntries,
                List<String> categoryPath,
                int page
        ) {
            return new MenuHolder(
                    MenuMode.CATEGORIES,
                    List.copyOf(categoryEntries),
                    List.copyOf(categoryPath),
                    List.of(),
                    List.of(),
                    page,
                    "",
                    "",
                    false,
                    ""
            );
        }

        private static MenuHolder setGroups(List<String> setGroups, int page) {
            return new MenuHolder(
                    MenuMode.SET_GROUPS,
                    List.of(),
                    List.of(),
                    setGroups,
                    List.of(),
                    page,
                    "",
                    "Sets",
                    false,
                    ""
            );
        }

        private static MenuHolder items(
                List<CustomItemDefinition> items,
                int page,
                String query,
                String titleLabel,
                List<String> categoryPath,
                boolean hasCategoryBack,
                String setGroup
        ) {
            return new MenuHolder(
                    MenuMode.ITEMS,
                    List.of(),
                    List.copyOf(categoryPath),
                    List.of(),
                    items,
                    page,
                    query,
                    titleLabel,
                    hasCategoryBack,
                    setGroup
            );
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record CategoryEntry(
            String displayName,
            List<String> path,
            boolean directItems,
            int itemCount
    ) {
        private static CategoryEntry folder(List<String> path, int itemCount) {
            return new CategoryEntry(prettify(path.getLast()), List.copyOf(path), false, itemCount);
        }

        private static CategoryEntry directItems(List<String> path, int itemCount) {
            return new CategoryEntry(prettify(path.getLast()) + " Items", List.copyOf(path), true, itemCount);
        }
    }
}
