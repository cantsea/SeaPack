package com.seapack.feature.item;

import com.seapack.SeaPack;
import com.seapack.config.CustomItemConfigStore;
import com.seapack.config.FurnitureConfigStore;
import com.seapack.feature.importer.ItemsAdderMetadataLoader;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

final class ItemLoader {
    private final SeaPack plugin;

    ItemLoader(SeaPack plugin) {
        this.plugin = plugin;
    }

    Result load() {
        ItemsAdderMetadataLoader metadataLoader = ItemsAdderMetadataLoader.load(plugin);
        List<CustomItemDefinition> loadedItems = new ArrayList<>(loadItemsAdderCache(metadataLoader));
        loadedItems.sort((left, right) -> left.id().compareToIgnoreCase(right.id()));
        List<CustomItemDefinition> canonicalItems = preferCanonicalItems(loadedItems, metadataLoader);
        if (canonicalItems.isEmpty()) {
            return null;
        }

        Map<String, String> setGroupAliases = SetGroupResolver.aliases(canonicalItems);
        CustomItemConfigStore.SyncResult customItemConfigs =
                new CustomItemConfigStore(plugin, setGroupAliases).syncAndLoad(canonicalItems);
        List<CustomItemDefinition> items = Collections.unmodifiableList(
                applyCustomizations(canonicalItems, customItemConfigs.customizations())
        );
        FurnitureConfigStore.SyncResult furnitureConfigs = new FurnitureConfigStore(plugin)
                .syncAndLoad(items, metadataLoader.furnitureSettings());

        ItemCatalog catalog = new ItemCatalog(items, furnitureConfigs.settings(), setGroupAliases);
        ItemLoadStats stats = new ItemLoadStats(
                metadataLoader.ruleCount(),
                metadataLoader.sourceCount(),
                metadataLoader.documentCount(),
                metadataLoader.namespaceCount(),
                customItemConfigs.configCount(),
                customItemConfigs.createdCount(),
                customItemConfigs.createdItemEntryCount(),
                furnitureConfigs.created(),
                furnitureConfigs.addedItemCount()
        );
        return new Result(catalog, stats);
    }

    private List<CustomItemDefinition> loadItemsAdderCache(ItemsAdderMetadataLoader metadataLoader) {
        File cacheFile = findCacheFile();
        if (cacheFile == null) {
            plugin.getLogger().warning("ItemsAdder cache file not found. Put items_ids_cache.yml in "
                    + plugin.getDataFolder().getPath());
            return List.of();
        }

        YamlConfiguration cache = YamlConfiguration.loadConfiguration(cacheFile);
        List<CustomItemDefinition> loadedItems = new ArrayList<>();
        for (String materialName : cache.getKeys(false)) {
            Material material = Material.matchMaterial(materialName);
            if (material == null || material.isAir()) {
                plugin.getLogger().warning("Skipping ItemsAdder cache material '" + materialName
                        + "': invalid material.");
                continue;
            }

            ConfigurationSection materialSection = cache.getConfigurationSection(materialName);
            if (materialSection == null) {
                continue;
            }
            for (String id : materialSection.getKeys(false)) {
                if (shouldHide(id) || metadataLoader.hasItemIndex() && !metadataLoader.isCurrentItem(id)) {
                    continue;
                }

                int customModelData = materialSection.getInt(id, -1);
                if (customModelData < 0) {
                    plugin.getLogger().warning("Skipping ItemsAdder cache item '" + id
                            + "': invalid custom model data.");
                    continue;
                }
                loadedItems.add(new CustomItemDefinition(
                        id,
                        material,
                        customModelData,
                        metadataLoader.itemModel(id),
                        metadataLoader.categoryPath(id),
                        prettifyName(id),
                        List.of(),
                        Map.of(),
                        metadataLoader.armorRendering(id)
                ));
            }
        }
        return loadedItems;
    }

    private List<CustomItemDefinition> preferCanonicalItems(
            List<CustomItemDefinition> loadedItems,
            ItemsAdderMetadataLoader metadataLoader
    ) {
        Map<String, List<CustomItemDefinition>> byId = new LinkedHashMap<>();
        for (CustomItemDefinition item : loadedItems) {
            byId.computeIfAbsent(item.id().toLowerCase(Locale.ROOT), ignored -> new ArrayList<>()).add(item);
        }

        List<CustomItemDefinition> canonicalItems = new ArrayList<>();
        for (List<CustomItemDefinition> candidates : byId.values()) {
            if (candidates.size() == 1) {
                canonicalItems.add(candidates.getFirst());
                continue;
            }

            Material sourceMaterial = metadataLoader.sourceMaterial(candidates.getFirst().id());
            CustomItemDefinition preferred = candidates.stream()
                    .filter(candidate -> sourceMaterial != null && candidate.material() == sourceMaterial)
                    .findFirst()
                    .orElseGet(() -> candidates.stream()
                            .filter(candidate -> preferredCacheMaterial(candidate) == candidate.material())
                            .findFirst()
                            .orElseGet(() -> candidates.stream()
                                    .sorted(Comparator.comparing((CustomItemDefinition item) -> item.material().name())
                                            .thenComparingInt(CustomItemDefinition::customModelData))
                                    .findFirst()
                                    .orElseThrow()));
            canonicalItems.add(preferred);
            plugin.getLogger().warning("Duplicate cache mappings found for '" + preferred.id()
                    + "'; using " + preferred.material() + " CMD " + preferred.customModelData() + ".");
        }
        canonicalItems.sort((left, right) -> left.id().compareToIgnoreCase(right.id()));
        return canonicalItems;
    }

    private File findCacheFile() {
        File preferred = new File(plugin.getDataFolder(), "items_ids_cache.yml");
        if (preferred.isFile()) {
            return preferred;
        }

        File[] candidates = plugin.getDataFolder().listFiles(file -> {
            String name = file.getName().toLowerCase(Locale.ROOT);
            return file.isFile()
                    && name.startsWith("items_ids_cache")
                    && (name.endsWith(".yml") || name.endsWith(".yaml"));
        });
        if (candidates == null || candidates.length == 0) {
            return null;
        }
        return java.util.Arrays.stream(candidates)
                .max(Comparator.comparingLong(File::lastModified))
                .orElse(null);
    }

    private static List<CustomItemDefinition> applyCustomizations(
            List<CustomItemDefinition> items,
            Map<String, ItemCustomization> customizations
    ) {
        return items.stream()
                .map(item -> item.withCustomization(customizations.getOrDefault(
                        item.id().toLowerCase(Locale.ROOT),
                        ItemCustomization.EMPTY
                )))
                .toList();
    }

    private static Material preferredCacheMaterial(CustomItemDefinition item) {
        ArmorRendering rendering = item.armorRendering();
        if (rendering.hasLeatherColor()) {
            return rendering.preferredMaterial();
        }

        String itemKey = item.itemKey().toUpperCase(Locale.ROOT);
        for (Material material : Material.values()) {
            if (material.name().matches("(CHAINMAIL|DIAMOND|GOLDEN|IRON|LEATHER|NETHERITE)_"
                    + "(HELMET|CHESTPLATE|LEGGINGS|BOOTS)")
                    && itemKey.endsWith("_" + material.name())) {
                return material;
            }
        }
        if (itemKey.endsWith("_TURTLE_HELMET")) {
            return Material.TURTLE_HELMET;
        }
        return rendering.preferredMaterial();
    }

    private static boolean shouldHide(String id) {
        String namespace = id.contains(":") ? id.substring(0, id.indexOf(':')) : "";
        return namespace.startsWith("_");
    }

    private static String prettifyName(String id) {
        String name = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        name = name.replace('_', ' ').replace('-', ' ');

        StringBuilder prettyName = new StringBuilder("<aqua>");
        boolean capitalizeNext = true;
        for (char character : name.toCharArray()) {
            if (Character.isWhitespace(character)) {
                capitalizeNext = true;
                prettyName.append(character);
                continue;
            }
            prettyName.append(capitalizeNext ? Character.toUpperCase(character) : character);
            capitalizeNext = false;
        }
        prettyName.append("</aqua>");
        return prettyName.toString();
    }

    record Result(ItemCatalog catalog, ItemLoadStats stats) {
    }
}
