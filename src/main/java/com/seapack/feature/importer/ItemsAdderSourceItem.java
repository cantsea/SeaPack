package com.seapack.feature.importer;

import java.util.List;
import org.bukkit.configuration.ConfigurationSection;

record ItemsAdderSourceItem(
        String namespace,
        ConfigurationSection section,
        List<String> categoryPath
) {
}
