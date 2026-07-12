package com.seapack.feature.importer;

import java.util.List;
import java.util.Locale;
import org.bukkit.configuration.file.YamlConfiguration;

record ItemsAdderSourceDocument(
        String name,
        String inferredNamespace,
        List<String> categoryFolders,
        YamlConfiguration configuration
) {
    String namespace() {
        String configuredNamespace = configuration.getString("info.namespace", "").trim();
        return configuredNamespace.isBlank()
                ? inferredNamespace
                : configuredNamespace.toLowerCase(Locale.ROOT);
    }
}
