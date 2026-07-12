package com.seapack.feature.item;

public record ItemLoadStats(
        int armorRuleCount,
        int armorSourceCount,
        int armorDocumentCount,
        int namespaceCount,
        int customConfigCount,
        int customConfigCreatedCount,
        int customItemEntryCreatedCount,
        boolean furnitureConfigCreated,
        int furnitureConfigItemCreatedCount
) {
    static final ItemLoadStats EMPTY = new ItemLoadStats(0, 0, 0, 0, 0, 0, 0, false, 0);
}
