package com.seapack.feature.menu;

import java.util.List;

record MenuCategory(
        String displayName,
        List<String> path,
        boolean directItems,
        int itemCount
) {
    static MenuCategory folder(List<String> path, int itemCount) {
        return new MenuCategory(ItemMenuRenderer.prettify(path.getLast()), List.copyOf(path), false, itemCount);
    }

    static MenuCategory directItems(List<String> path, int itemCount) {
        return new MenuCategory(
                ItemMenuRenderer.prettify(path.getLast()) + " Items",
                List.copyOf(path),
                true,
                itemCount
        );
    }
}
