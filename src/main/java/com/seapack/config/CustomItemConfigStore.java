package com.seapack.config;

import com.seapack.SeaPack;
import com.seapack.feature.item.CustomItemDefinition;
import com.seapack.feature.item.ItemCustomization;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CustomItemConfigStore {
    private final SeaPack plugin;
    private final CustomItemConfigPaths paths;
    private final ItemCustomizationReader reader;
    private final ItemCustomizationWriter writer;

    public CustomItemConfigStore(SeaPack plugin, Map<String, String> setGroupAliases) {
        this.plugin = plugin;
        this.paths = new CustomItemConfigPaths(plugin, setGroupAliases);
        this.reader = new ItemCustomizationReader(plugin, paths);
        this.writer = new ItemCustomizationWriter(plugin, paths);
    }

    public SyncResult syncAndLoad(List<CustomItemDefinition> items) {
        try {
            Files.createDirectories(paths.root());
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not create custom item config folder '" + paths.root() + "': "
                    + exception.getMessage());
            return new SyncResult(Map.of(), 0, 0, 0);
        }

        Map<String, ItemCustomization> legacyCustomizations = reader.legacyCustomizationsById();
        Map<String, ItemCustomization> customizations = new LinkedHashMap<>();
        int configCount = 0;
        int createdConfigCount = 0;
        int createdItemEntryCount = 0;
        Map<String, Path> itemConfigPaths = paths.uniqueItemConfigPaths(items);

        for (CustomItemDefinition item : items) {
            if (item.category().equalsIgnoreCase("sets")) {
                continue;
            }

            String normalizedId = CustomItemConfigPaths.normalizeId(item.id());
            Path path = itemConfigPaths.get(normalizedId);
            if (!Files.exists(path)
                    && writer.writeSingle(path, item, legacyCustomizations.get(normalizedId))) {
                createdConfigCount++;
            }

            if (Files.isRegularFile(path)) {
                configCount++;
                customizations.put(normalizedId, reader.readSingle(path, item));
            }
        }

        for (Map.Entry<String, List<CustomItemDefinition>> entry : paths.setItemsByGroup(items).entrySet()) {
            Path path = paths.setConfigPath(entry.getKey());
            Map<String, String> itemKeys = paths.uniqueSetItemKeys(entry.getValue());
            if (!Files.exists(path)) {
                if (writer.writeSet(path, entry.getKey(), entry.getValue(), itemKeys, legacyCustomizations)) {
                    createdConfigCount++;
                    createdItemEntryCount += entry.getValue().size();
                }
            } else {
                createdItemEntryCount += writer.ensureSetHasCurrentItems(
                        path,
                        entry.getKey(),
                        entry.getValue(),
                        itemKeys,
                        legacyCustomizations
                );
            }

            if (Files.isRegularFile(path)) {
                configCount++;
                customizations.putAll(reader.readSet(path, entry.getValue(), itemKeys));
            }
        }

        return new SyncResult(customizations, configCount, createdConfigCount, createdItemEntryCount);
    }

    public record SyncResult(
            Map<String, ItemCustomization> customizations,
            int configCount,
            int createdCount,
            int createdItemEntryCount
    ) {
    }
}
