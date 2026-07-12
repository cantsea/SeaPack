package com.seapack.feature.importer;

import java.util.List;
import java.util.Map;

record ItemsAdderSourceIndex(
        List<ItemsAdderSourceDocument> documents,
        Map<String, ItemsAdderModelDefinition> modelDefinitions,
        int sourceCount
) {
}
