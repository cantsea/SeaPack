package org.seapack;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class CustomItemRegistry {
    private final SeaPack plugin;
    private List<CustomItemDefinition> items = List.of();
    private Map<String, CustomItemDefinition> itemsById = Map.of();
    private Map<String, FurnitureSettings> furnitureSettings = Map.of();
    private Map<String, String> setGroupAliases = Map.of();
    private int armorRuleCount;
    private int armorSourceCount;
    private int armorDocumentCount;
    private int namespaceCount;
    private int customConfigCount;
    private int customConfigCreatedCount;
    private int customItemEntryCreatedCount;
    private boolean furnitureConfigCreated;
    private int furnitureConfigItemCreatedCount;

    public CustomItemRegistry(SeaPack plugin) {
        this.plugin = plugin;
    }

    public boolean reload() {
        try {
            ItemsAdderMetadataLoader metadataLoader = ItemsAdderMetadataLoader.load(plugin);
            List<CustomItemDefinition> loadedItems = new ArrayList<>(loadItemsAdderCache(metadataLoader));
            loadedItems.sort((left, right) -> left.id().compareToIgnoreCase(right.id()));
            List<CustomItemDefinition> canonicalItems = preferCanonicalItems(loadedItems, metadataLoader);
            if (canonicalItems.isEmpty()) {
                plugin.getLogger().warning("SeaPack reload rejected because no valid current items were found."
                        + " The previous registry remains active.");
                return false;
            }

            Map<String, String> newSetGroupAliases = buildSetGroupAliases(canonicalItems);
            CustomItemConfigStore.SyncResult customItemConfigs =
                    new CustomItemConfigStore(plugin, newSetGroupAliases).syncAndLoad(canonicalItems);
            List<CustomItemDefinition> newItems = Collections.unmodifiableList(
                    applyCustomizations(canonicalItems, customItemConfigs.customizations())
            );
            Map<String, CustomItemDefinition> indexedItems = new LinkedHashMap<>();
            newItems.forEach(item -> indexedItems.put(item.id().toLowerCase(Locale.ROOT), item));
            FurnitureConfigStore.SyncResult furnitureConfigs = new FurnitureConfigStore(plugin)
                    .syncAndLoad(newItems, metadataLoader.furnitureSettings());

            items = newItems;
            itemsById = Map.copyOf(indexedItems);
            furnitureSettings = furnitureConfigs.settings();
            setGroupAliases = newSetGroupAliases;
            armorRuleCount = metadataLoader.ruleCount();
            armorSourceCount = metadataLoader.sourceCount();
            armorDocumentCount = metadataLoader.documentCount();
            namespaceCount = metadataLoader.namespaceCount();
            customConfigCount = customItemConfigs.configCount();
            customConfigCreatedCount = customItemConfigs.createdCount();
            customItemEntryCreatedCount = customItemConfigs.createdItemEntryCount();
            furnitureConfigCreated = furnitureConfigs.created();
            furnitureConfigItemCreatedCount = furnitureConfigs.addedItemCount();
            return true;
        } catch (RuntimeException exception) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "SeaPack reload failed. The previous registry remains active.", exception);
            return false;
        }
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
                plugin.getLogger().warning("Skipping ItemsAdder cache material '" + materialName + "': invalid material.");
                continue;
            }

            ConfigurationSection materialSection = cache.getConfigurationSection(materialName);
            if (materialSection == null) {
                continue;
            }

            for (String id : materialSection.getKeys(false)) {
                if (shouldHide(id)) {
                    continue;
                }
                if (metadataLoader.hasItemIndex() && !metadataLoader.isCurrentItem(id)) {
                    continue;
                }

                int customModelData = materialSection.getInt(id, -1);
                if (customModelData < 0) {
                    plugin.getLogger().warning("Skipping ItemsAdder cache item '" + id + "': invalid custom model data.");
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

    public List<CustomItemDefinition> items() {
        return items;
    }

    public int armorRuleCount() {
        return armorRuleCount;
    }

    public int armorSourceCount() {
        return armorSourceCount;
    }

    public int armorDocumentCount() {
        return armorDocumentCount;
    }

    public int namespaceCount() {
        return namespaceCount;
    }

    public int customConfigCount() {
        return customConfigCount;
    }

    public int customConfigCreatedCount() {
        return customConfigCreatedCount;
    }

    public int customItemEntryCreatedCount() {
        return customItemEntryCreatedCount;
    }

    public int furnitureCount() {
        return (int) furnitureSettings.values().stream().filter(FurnitureSettings::enabled).count();
    }

    public boolean furnitureConfigCreated() {
        return furnitureConfigCreated;
    }

    public int furnitureConfigItemCreatedCount() {
        return furnitureConfigItemCreatedCount;
    }

    public CustomItemDefinition item(String itemId) {
        return itemId == null ? null : itemsById.get(itemId.toLowerCase(Locale.ROOT));
    }

    public FurnitureSettings furniture(String itemId) {
        return itemId == null ? null : furnitureSettings.get(itemId.toLowerCase(Locale.ROOT));
    }

    @SuppressWarnings("deprecation")
    public CustomItemDefinition identifyFurniture(org.bukkit.inventory.ItemStack itemStack, org.bukkit.NamespacedKey itemIdKey) {
        if (itemStack == null || itemStack.getType().isAir() || !itemStack.hasItemMeta()) {
            return null;
        }

        String taggedId = itemStack.getItemMeta().getPersistentDataContainer()
                .get(itemIdKey, org.bukkit.persistence.PersistentDataType.STRING);
        CustomItemDefinition taggedItem = item(taggedId);
        if (taggedItem != null && furniture(taggedItem.id()) != null) {
            return taggedItem;
        }

        if (!itemStack.getItemMeta().hasCustomModelData()) {
            return null;
        }
        int customModelData = itemStack.getItemMeta().getCustomModelData();
        return items.stream()
                .filter(candidate -> furniture(candidate.id()) != null)
                .filter(candidate -> candidate.material() == itemStack.getType())
                .filter(candidate -> candidate.customModelData() == customModelData)
                .findFirst()
                .orElse(null);
    }

    public List<String> categories() {
        return childCategories(List.of());
    }

    public List<String> childCategories(List<String> parentPath) {
        int depth = parentPath.size();
        return items.stream()
                .filter(item -> categoryStartsWith(item.categoryPath(), parentPath))
                .filter(item -> item.categoryPath().size() > depth)
                .map(item -> item.categoryPath().get(depth))
                .distinct()
                .sorted(String::compareToIgnoreCase)
                .toList();
    }

    public List<CustomItemDefinition> itemsInCategory(String category) {
        return itemsInCategoryTree(List.of(category));
    }

    public List<CustomItemDefinition> itemsDirectlyInCategory(List<String> categoryPath) {
        return items.stream()
                .filter(item -> categoryPathEquals(item.categoryPath(), categoryPath))
                .toList();
    }

    public List<CustomItemDefinition> itemsInCategoryTree(List<String> categoryPath) {
        return items.stream()
                .filter(item -> categoryStartsWith(item.categoryPath(), categoryPath))
                .toList();
    }

    public int categoryTreeCount(List<String> categoryPath) {
        return (int) items.stream()
                .filter(item -> categoryStartsWith(item.categoryPath(), categoryPath))
                .count();
    }

    public List<String> setGroups() {
        return itemsInCategory("sets").stream()
                .map(this::resolvedSetGroup)
                .filter(group -> !group.isBlank())
                .distinct()
                .sorted(String::compareToIgnoreCase)
                .toList();
    }

    public List<CustomItemDefinition> itemsInSetGroup(String setGroup) {
        return itemsInCategory("sets").stream()
                .filter(item -> resolvedSetGroup(item).equalsIgnoreCase(setGroup))
                .toList();
    }

    public Map<String, Integer> setGroupCounts() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String setGroup : setGroups()) {
            counts.put(setGroup, itemsInSetGroup(setGroup).size());
        }
        return counts;
    }

    public Map<String, Integer> categoryCounts() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String category : categories()) {
            counts.put(category, itemsInCategory(category).size());
        }
        return counts;
    }

    public List<CustomItemDefinition> search(String query) {
        String normalizedQuery = normalizeSearchText(query);
        if (normalizedQuery.isBlank()) {
            return items;
        }

        int maximumScore = Math.max(4, normalizedQuery.length() / 2 + 2);
        return items.stream()
                .map(item -> new SearchMatch(item, searchScore(item, normalizedQuery)))
                .filter(match -> match.score() <= maximumScore)
                .sorted(Comparator.comparingInt(SearchMatch::score)
                        .thenComparing(match -> match.item().id(), String.CASE_INSENSITIVE_ORDER))
                .map(SearchMatch::item)
                .toList();
    }

    private String resolvedSetGroup(CustomItemDefinition item) {
        String rawGroup = item.setGroup();
        return setGroupAliases.getOrDefault(rawGroup, rawGroup);
    }

    private static Map<String, String> buildSetGroupAliases(List<CustomItemDefinition> items) {
        Set<String> rawGroups = new HashSet<>();
        for (CustomItemDefinition item : items) {
            if (!item.category().equalsIgnoreCase("sets")) {
                continue;
            }
            String rawGroup = item.setGroup();
            if (!rawGroup.isBlank()) {
                rawGroups.add(rawGroup);
            }
        }

        Map<String, String> aliases = new HashMap<>();
        for (String rawGroup : rawGroups) {
            String bestParent = rawGroup;
            for (String candidate : rawGroups) {
                if (candidate.equals(rawGroup)) {
                    continue;
                }
                if (rawGroup.startsWith(candidate + "_") && candidate.length() > bestParent.length()) {
                    bestParent = candidate;
                }
                if (rawGroup.startsWith(candidate + "_") && bestParent.equals(rawGroup)) {
                    bestParent = candidate;
                }
            }
            aliases.put(rawGroup, bestParent);
        }
        return aliases;
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
            if (material.name().matches("(CHAINMAIL|DIAMOND|GOLDEN|IRON|LEATHER|NETHERITE)_(HELMET|CHESTPLATE|LEGGINGS|BOOTS)")
                    && itemKey.endsWith("_" + material.name())) {
                return material;
            }
        }
        if (itemKey.endsWith("_TURTLE_HELMET")) {
            return Material.TURTLE_HELMET;
        }
        return rendering.preferredMaterial();
    }

    private int searchScore(CustomItemDefinition item, String normalizedQuery) {
        List<String> targets = new ArrayList<>();
        targets.add(item.id());
        targets.add(item.itemKey());
        targets.add(SeaText.plain(item.displayName()));
        targets.add(item.category());
        targets.addAll(item.categoryPath());
        targets.add(resolvedSetGroup(item));
        targets.addAll(item.searchTokens());

        return targets.stream()
                .map(CustomItemRegistry::normalizeSearchText)
                .filter(target -> !target.isBlank())
                .mapToInt(target -> manhattanDistance(normalizedQuery, target))
                .min()
                .orElse(Integer.MAX_VALUE);
    }

    private static boolean categoryStartsWith(List<String> itemPath, List<String> parentPath) {
        if (itemPath.size() < parentPath.size()) {
            return false;
        }
        for (int index = 0; index < parentPath.size(); index++) {
            if (!itemPath.get(index).equalsIgnoreCase(parentPath.get(index))) {
                return false;
            }
        }
        return true;
    }

    private static boolean categoryPathEquals(List<String> left, List<String> right) {
        return left.size() == right.size() && categoryStartsWith(left, right);
    }

    private static String normalizeSearchText(String text) {
        if (text == null) {
            return "";
        }
        return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static int manhattanDistance(String query, String target) {
        if (target.contains(query)) {
            return 0;
        }

        int best = vectorManhattanDistance(query, target);
        if (target.length() > query.length()) {
            for (int start = 0; start <= target.length() - query.length(); start++) {
                String window = target.substring(start, start + query.length());
                best = Math.min(best, vectorManhattanDistance(query, window));
            }
        }
        return best;
    }

    private static int vectorManhattanDistance(String left, String right) {
        int[] counts = new int[36];
        countCharacters(left, counts, 1);
        countCharacters(right, counts, -1);

        int distance = 0;
        for (int count : counts) {
            distance += Math.abs(count);
        }
        return distance;
    }

    private static void countCharacters(String text, int[] counts, int multiplier) {
        for (char character : text.toCharArray()) {
            if (character >= 'a' && character <= 'z') {
                counts[character - 'a'] += multiplier;
            } else if (character >= '0' && character <= '9') {
                counts[26 + character - '0'] += multiplier;
            }
        }
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

    private record SearchMatch(CustomItemDefinition item, int score) {
    }
}
