package com.seapack.feature.menu;

import com.seapack.feature.item.CustomItemDefinition;
import java.util.List;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

record MenuView(
        MenuMode mode,
        List<MenuCategory> categoryEntries,
        List<String> categoryPath,
        List<String> setGroups,
        List<CustomItemDefinition> items,
        int page,
        String query,
        String titleLabel,
        boolean hasCategoryBack,
        String setGroup
) implements InventoryHolder {
    static MenuView categories(
            List<MenuCategory> categoryEntries,
            List<String> categoryPath,
            int page
    ) {
        return new MenuView(
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

    static MenuView setGroups(List<String> setGroups, int page) {
        return new MenuView(
                MenuMode.SET_GROUPS,
                List.of(),
                List.of(),
                List.copyOf(setGroups),
                List.of(),
                page,
                "",
                "Sets",
                false,
                ""
        );
    }

    static MenuView items(
            List<CustomItemDefinition> items,
            int page,
            String query,
            String titleLabel,
            List<String> categoryPath,
            boolean hasCategoryBack,
            String setGroup
    ) {
        return new MenuView(
                MenuMode.ITEMS,
                List.of(),
                List.copyOf(categoryPath),
                List.of(),
                List.copyOf(items),
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
