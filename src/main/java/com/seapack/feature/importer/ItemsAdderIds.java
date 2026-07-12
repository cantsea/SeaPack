package com.seapack.feature.importer;

import java.util.Locale;

final class ItemsAdderIds {
    private ItemsAdderIds() {
    }

    static String qualified(String namespace, String id) {
        String normalizedId = id.toLowerCase(Locale.ROOT);
        return normalizedId.contains(":")
                ? normalizedId
                : namespace.toLowerCase(Locale.ROOT) + ":" + normalizedId;
    }
}
