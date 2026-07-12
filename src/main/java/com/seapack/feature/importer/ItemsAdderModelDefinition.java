package com.seapack.feature.importer;

import com.seapack.feature.furniture.FurnitureModelTransform;

record ItemsAdderModelDefinition(
        String modelId,
        String parentId,
        FurnitureModelTransform transform
) {
}
