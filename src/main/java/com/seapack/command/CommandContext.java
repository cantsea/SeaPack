package com.seapack.command;

import com.seapack.SeaPack;
import com.seapack.feature.importer.ItemsAdderImportManager;
import com.seapack.feature.item.ItemManager;
import com.seapack.feature.menu.ItemMenuManager;

public record CommandContext(
        SeaPack plugin,
        ItemManager itemManager,
        ItemMenuManager itemMenuManager,
        ItemsAdderImportManager importManager
) {
}
