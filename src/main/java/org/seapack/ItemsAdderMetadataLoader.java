package org.seapack;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.kyori.adventure.key.Key;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.EquipmentSlot;

public final class ItemsAdderMetadataLoader {
    private final SeaPack plugin;
    private final List<SourceDocument> documents = new ArrayList<>();
    private final Map<String, Color> legacyArmorColors = new HashMap<>();
    private final List<ItemArmorRule> itemRules = new ArrayList<>();
    private final Map<String, FurnitureSettings> furnitureSettings = new HashMap<>();
    private final Map<String, SourceItem> sourceItems = new HashMap<>();
    private final Map<String, Material> sourceMaterials = new HashMap<>();
    private final Map<String, String> itemModels = new HashMap<>();
    private final Map<String, ModelDefinition> modelDefinitions = new HashMap<>();
    private final Set<String> namespaces = new LinkedHashSet<>();
    private int sourceCount;

    private ItemsAdderMetadataLoader(SeaPack plugin) {
        this.plugin = plugin;
    }

    public static ItemsAdderMetadataLoader load(SeaPack plugin) {
        ItemsAdderMetadataLoader loader = new ItemsAdderMetadataLoader(plugin);
        loader.loadSources();
        loader.indexSourceItems();
        loader.readArmorDefinitions();
        loader.readItemModelsAndMaterials();
        loader.readItemMetadata();
        loader.readFurnitureMetadata();
        loader.itemRules.sort(Comparator.comparingInt((ItemArmorRule rule) -> rule.itemId().length()).reversed());
        if (loader.sourceCount > 0 && loader.documents.isEmpty()) {
            plugin.getLogger().warning("ItemsAdder source was found, but no YAML files inside a configs folder were found.");
        } else if (!loader.documents.isEmpty() && loader.itemRules.isEmpty()) {
            plugin.getLogger().warning("Scanned " + loader.documents.size()
                    + " ItemsAdder config files, but found no armor item definitions.");
        }
        return loader;
    }

    public ArmorRendering armorRendering(String itemId) {
        String normalizedId = itemId.toLowerCase(Locale.ROOT);
        for (ItemArmorRule rule : itemRules) {
            if (normalizedId.equals(rule.itemId()) || normalizedId.startsWith(rule.itemId() + "_")) {
                return rule.rendering();
            }
        }
        return ArmorRendering.NONE;
    }

    public int ruleCount() {
        return itemRules.size();
    }

    public int sourceCount() {
        return sourceCount;
    }

    public int documentCount() {
        return documents.size();
    }

    public int namespaceCount() {
        return namespaces.size();
    }

    public Map<String, FurnitureSettings> furnitureSettings() {
        return Map.copyOf(furnitureSettings);
    }

    public boolean hasItemIndex() {
        return !sourceItems.isEmpty();
    }

    public boolean isCurrentItem(String itemId) {
        SourceItem sourceItem = sourceItems.get(itemId.toLowerCase(Locale.ROOT));
        return sourceItem != null
                && sourceItem.section().getBoolean("enabled", true)
                && !sourceItem.section().getBoolean("template", false);
    }

    public Material sourceMaterial(String itemId) {
        return sourceMaterials.get(itemId.toLowerCase(Locale.ROOT));
    }

    public String itemModel(String itemId) {
        return itemModels.getOrDefault(itemId.toLowerCase(Locale.ROOT), "");
    }

    public List<String> categoryPath(String itemId) {
        SourceItem sourceItem = sourceItems.get(itemId.toLowerCase(Locale.ROOT));
        if (sourceItem != null) {
            return sourceItem.categoryPath();
        }
        String namespace = itemId.contains(":") ? itemId.substring(0, itemId.indexOf(':')) : "other";
        return List.of(namespace.toLowerCase(Locale.ROOT));
    }

    private void loadSources() {
        Set<Path> sources = new LinkedHashSet<>();
        addSource(sources, new File(plugin.getDataFolder(), "contents"));
        addSource(sources, new File(plugin.getDataFolder(), "itemsadder-contents"));
        if (containsNamespaceConfigs(plugin.getDataFolder().toPath())) {
            addSource(sources, plugin.getDataFolder());
        }

        for (Path sourcePath : sources) {
            File source = sourcePath.toFile();
            if (source.isDirectory()) {
                sourceCount++;
                loadDirectory(sourcePath);
            } else if (source.isFile() && source.getName().toLowerCase(Locale.ROOT).endsWith(".zip")) {
                sourceCount++;
                loadZip(source);
            }
        }

        if (sourceCount == 0) {
            plugin.getLogger().warning("No ItemsAdder contents source found. Put the current contents folder at "
                    + new File(plugin.getDataFolder(), "contents").getPath());
        }
    }

    private static void addSource(Set<Path> sources, File source) {
        sources.add(source.toPath().toAbsolutePath().normalize());
    }

    private static boolean containsNamespaceConfigs(Path directory) {
        if (!Files.isDirectory(directory)) {
            return false;
        }
        try (var children = Files.list(directory)) {
            return children.filter(Files::isDirectory)
                    .anyMatch(child -> Files.isDirectory(child.resolve("configs")));
        } catch (IOException ignored) {
            return false;
        }
    }

    private void loadDirectory(Path directory) {
        try (var paths = Files.walk(directory)) {
            paths.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(path -> path.toString().toLowerCase(Locale.ROOT)))
                    .forEach(path -> loadDirectoryFile(directory, path));
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not scan ItemsAdder source directory '" + directory + "': "
                    + exception.getMessage());
        }
    }

    private void loadDirectoryFile(Path root, Path path) {
        if (isConfigYaml(root, path)) {
            documents.add(new SourceDocument(
                    path.toString(),
                    inferNamespace(root, path),
                    categoryFolders(root, path),
                    YamlConfiguration.loadConfiguration(path.toFile())
            ));
        }

        String modelId = modelId(root, path);
        if (modelId == null) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            modelDefinitions.put(modelId, readModelDefinition(modelId, reader));
        } catch (IOException | JsonParseException exception) {
            plugin.getLogger().warning("Could not read item model '" + path + "': " + exception.getMessage());
        }
    }

    private void loadZip(File source) {
        try (ZipFile zip = new ZipFile(source)) {
            zip.stream()
                    .filter(entry -> !entry.isDirectory())
                    .sorted(Comparator.comparing(entry -> entry.getName().toLowerCase(Locale.ROOT)))
                    .forEach(entry -> loadZipEntry(zip, entry));
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not scan ItemsAdder source zip '" + source.getPath() + "': "
                    + exception.getMessage());
        }
    }

    private void loadZipEntry(ZipFile zip, ZipEntry entry) {
        if (isConfigYaml(entry)) {
            readZipDocument(zip, entry);
        }

        String modelId = modelId(entry.getName());
        if (modelId == null) {
            return;
        }
        try (Reader reader = new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8)) {
            modelDefinitions.put(modelId, readModelDefinition(modelId, reader));
        } catch (IOException | JsonParseException exception) {
            plugin.getLogger().warning("Could not read item model '" + entry.getName() + "': "
                    + exception.getMessage());
        }
    }

    private void readZipDocument(ZipFile zip, ZipEntry entry) {
        try (Reader reader = new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8)) {
            documents.add(new SourceDocument(
                    entry.getName(),
                    inferNamespace(entry.getName()),
                    categoryFolders(entry.getName()),
                    YamlConfiguration.loadConfiguration(reader)
            ));
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not read ItemsAdder config '" + entry.getName() + "': "
                    + exception.getMessage());
        }
    }

    private void readArmorDefinitions() {
        for (SourceDocument document : documents) {
            String namespace = namespace(document);
            if (namespace.isBlank()) {
                continue;
            }
            readLegacyArmorColors(document, namespace, "legacy_armor_renderings");
            readLegacyArmorColors(document, namespace, "armors_rendering");
        }
    }

    private void indexSourceItems() {
        for (SourceDocument document : documents) {
            String namespace = namespace(document);
            ConfigurationSection itemsSection = document.configuration().getConfigurationSection("items");
            if (namespace.isBlank() || itemsSection == null) {
                continue;
            }
            namespaces.add(namespace);
            for (String itemKey : itemsSection.getKeys(false)) {
                ConfigurationSection itemSection = itemsSection.getConfigurationSection(itemKey);
                if (itemSection == null) {
                    continue;
                }
                String itemId = qualifiedId(namespace, itemKey);
                List<String> categoryPath = new ArrayList<>();
                categoryPath.add(namespace);
                categoryPath.addAll(document.categoryFolders());
                SourceItem previous = sourceItems.put(itemId, new SourceItem(
                        namespace,
                        itemSection,
                        List.copyOf(categoryPath)
                ));
                if (previous != null) {
                    plugin.getLogger().warning("Duplicate ItemsAdder item definition '" + itemId
                            + "' found in contents; the later config file takes precedence.");
                }
            }
        }
    }

    private void readLegacyArmorColors(SourceDocument document, String namespace, String sectionName) {
        ConfigurationSection renderingSection = document.configuration().getConfigurationSection(sectionName);
        if (renderingSection == null) {
            return;
        }

        for (String armorId : renderingSection.getKeys(false)) {
            String colorText = renderingSection.getString(armorId + ".color", "");
            Color color = parseColor(colorText);
            if (color == null) {
                plugin.getLogger().warning("Ignoring invalid custom armor color '" + colorText
                        + "' in " + document.name());
                continue;
            }
            legacyArmorColors.put(qualifiedId(namespace, armorId), color);
        }
    }

    private void readItemMetadata() {
        Map<String, ArmorRendering> resolved = new HashMap<>();
        for (String itemId : sourceItems.keySet()) {
            ArmorRendering rendering = resolveArmorRendering(itemId, resolved, new LinkedHashSet<>());
            if (rendering != ArmorRendering.NONE) {
                itemRules.add(new ItemArmorRule(itemId, rendering));
            }
        }
    }

    private ArmorRendering resolveArmorRendering(
            String itemId,
            Map<String, ArmorRendering> resolved,
            Set<String> resolving
    ) {
        if (resolved.containsKey(itemId)) {
            return resolved.get(itemId);
        }
        SourceItem sourceItem = sourceItems.get(itemId);
        if (sourceItem == null || !resolving.add(itemId)) {
            return ArmorRendering.NONE;
        }

        ArmorRendering rendering = readModernEquipment(sourceItem.namespace(), sourceItem.section());
        if (rendering == ArmorRendering.NONE) {
            rendering = readLegacyArmor(sourceItem.namespace(), sourceItem.section());
        }
        if (rendering == ArmorRendering.NONE) {
            String variantOf = sourceItem.section().getString("variant_of", "").trim();
            if (!variantOf.isBlank()) {
                ArmorRendering inherited = resolveArmorRendering(
                        qualifiedId(sourceItem.namespace(), variantOf),
                        resolved,
                        resolving
                );
                if (inherited != ArmorRendering.NONE) {
                    Material material = sourceMaterials.getOrDefault(itemId, inherited.preferredMaterial());
                    rendering = new ArmorRendering(
                            inherited.leatherColor(),
                            inherited.equipmentAssetId(),
                            inherited.slot(),
                            material
                    );
                }
            }
        }
        resolving.remove(itemId);
        resolved.put(itemId, rendering);
        return rendering;
    }

    private void readItemModelsAndMaterials() {
        Map<String, ItemModelRule> resolvedModels = new HashMap<>();
        for (String itemId : sourceItems.keySet()) {
            Material material = resolveSourceMaterial(itemId, new LinkedHashSet<>());
            if (material != null) {
                sourceMaterials.put(itemId, material);
            }
            ItemModelRule itemModel = resolveItemModel(itemId, resolvedModels, new LinkedHashSet<>());
            if (itemModel != null && !itemModel.modelId().isBlank()) {
                try {
                    Key.key(itemModel.modelId());
                    itemModels.put(itemId, itemModel.modelId());
                } catch (IllegalArgumentException exception) {
                    plugin.getLogger().warning("Ignoring invalid item_model '" + itemModel.modelId()
                            + "' for " + itemId + ".");
                }
            }
        }
    }

    private void readFurnitureMetadata() {
        Map<String, FurnitureSettings> resolved = new HashMap<>();
        for (String itemId : sourceItems.keySet()) {
            FurnitureSettings settings = resolveFurniture(itemId, sourceItems, resolved, new LinkedHashSet<>());
            if (settings != null && settings.enabled()) {
                furnitureSettings.put(itemId, settings);
            }
        }
    }

    private FurnitureSettings resolveFurniture(
            String itemId,
            Map<String, SourceItem> sourceItems,
            Map<String, FurnitureSettings> resolved,
            Set<String> resolving
    ) {
        if (resolved.containsKey(itemId)) {
            return resolved.get(itemId);
        }
        SourceItem sourceItem = sourceItems.get(itemId);
        if (sourceItem == null || !resolving.add(itemId)) {
            return null;
        }

        ConfigurationSection itemSection = sourceItem.section();
        FurnitureSettings inherited = null;
        String variantOf = itemSection.getString("variant_of", "").trim();
        if (!variantOf.isBlank()) {
            String parentId = qualifiedId(sourceItem.namespace(), variantOf);
            inherited = resolveFurniture(parentId, sourceItems, resolved, resolving);
        }

        FurnitureSettings settings = inherited;
        String furniturePath = "behaviours.furniture";
        if (itemSection.contains(furniturePath)) {
            ConfigurationSection furniture = itemSection.getConfigurationSection(furniturePath);
            if (furniture == null) {
                settings = itemSection.getBoolean(furniturePath, true) ? FurnitureSettings.DEFAULT : null;
            } else {
                settings = readFurnitureSettings(furniture, inherited == null ? FurnitureSettings.DEFAULT : inherited);
            }
        }

        if (settings != null) {
            settings = settings.withModelTransform(modelTransformForItem(itemId));
        }

        resolving.remove(itemId);
        resolved.put(itemId, settings);
        return settings;
    }

    private FurnitureModelTransform modelTransformForItem(String itemId) {
        String modelId = resolveFurnitureModelId(itemId, new LinkedHashSet<>());
        FurnitureModelTransform transform = resolveModelTransform(modelId, new LinkedHashSet<>());
        return transform == null ? FurnitureModelTransform.IDENTITY : transform;
    }

    private String resolveFurnitureModelId(String itemId, Set<String> resolving) {
        SourceItem sourceItem = sourceItems.get(itemId);
        if (sourceItem == null || !resolving.add(itemId)) {
            return null;
        }

        ConfigurationSection section = sourceItem.section();
        String configuredModel = firstConfiguredModel(section);
        String modelId = configuredModel.isBlank()
                ? null
                : normalizeConfiguredModelId(sourceItem.namespace(), configuredModel);
        if (modelId == null) {
            String variantOf = section.getString("variant_of", "").trim();
            if (!variantOf.isBlank()) {
                modelId = resolveFurnitureModelId(
                        qualifiedId(sourceItem.namespace(), variantOf),
                        resolving
                );
            }
        }
        resolving.remove(itemId);
        return modelId;
    }

    private FurnitureModelTransform resolveModelTransform(String modelId, Set<String> resolving) {
        if (modelId == null || !resolving.add(modelId)) {
            return null;
        }
        ModelDefinition definition = modelDefinitions.get(modelId);
        if (definition == null) {
            resolving.remove(modelId);
            return null;
        }

        FurnitureModelTransform transform = definition.transform();
        if (transform == null && definition.parentId() != null) {
            transform = resolveModelTransform(definition.parentId(), resolving);
        }
        resolving.remove(modelId);
        return transform;
    }

    private static String firstConfiguredModel(ConfigurationSection section) {
        for (String path : List.of(
                "resource.model_path",
                "resource.model",
                "graphics.model_path",
                "graphics.model"
        )) {
            String model = section.getString(path, "").trim();
            if (!model.isBlank()) {
                return model;
            }
        }
        return "";
    }

    private static String normalizeConfiguredModelId(String namespace, String configuredModel) {
        String normalized = configuredModel.trim().replace('\\', '/').toLowerCase(Locale.ROOT);
        if (normalized.endsWith(".json")) {
            normalized = normalized.substring(0, normalized.length() - ".json".length());
        }
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.startsWith("models/")) {
            normalized = normalized.substring("models/".length());
        }
        return normalized.isBlank() ? null : qualifiedId(namespace, normalized);
    }

    private Material resolveSourceMaterial(String itemId, Set<String> resolving) {
        if (sourceMaterials.containsKey(itemId)) {
            return sourceMaterials.get(itemId);
        }
        SourceItem sourceItem = sourceItems.get(itemId);
        if (sourceItem == null || !resolving.add(itemId)) {
            return null;
        }
        ConfigurationSection section = sourceItem.section();
        Material material = readMaterial(section);
        if (material == null) {
            String variantOf = section.getString("variant_of", "").trim();
            if (!variantOf.isBlank()) {
                material = resolveSourceMaterial(qualifiedId(sourceItem.namespace(), variantOf), resolving);
            }
        }
        if (material == null && (section.contains("graphics") || section.contains("item_model"))) {
            material = Material.PAPER;
        }
        resolving.remove(itemId);
        if (material != null) {
            sourceMaterials.put(itemId, material);
        }
        return material;
    }

    private ItemModelRule resolveItemModel(
            String itemId,
            Map<String, ItemModelRule> resolved,
            Set<String> resolving
    ) {
        if (resolved.containsKey(itemId)) {
            return resolved.get(itemId);
        }
        SourceItem sourceItem = sourceItems.get(itemId);
        if (sourceItem == null || !resolving.add(itemId)) {
            return null;
        }
        ConfigurationSection section = sourceItem.section();
        ItemModelRule rule = null;
        String configuredModel = section.getString("item_model", "").trim();
        if (!configuredModel.isBlank()) {
            rule = new ItemModelRule(qualifiedId(sourceItem.namespace(), configuredModel), false);
        } else if (section.contains("graphics")) {
            rule = new ItemModelRule(itemId, true);
        } else {
            String variantOf = section.getString("variant_of", "").trim();
            if (!variantOf.isBlank()) {
                ItemModelRule parent = resolveItemModel(
                        qualifiedId(sourceItem.namespace(), variantOf),
                        resolved,
                        resolving
                );
                if (parent != null) {
                    rule = parent.generated() ? new ItemModelRule(itemId, true) : parent;
                }
            }
        }
        resolving.remove(itemId);
        resolved.put(itemId, rule);
        return rule;
    }

    private static FurnitureSettings readFurnitureSettings(
            ConfigurationSection section,
            FurnitureSettings defaults
    ) {
        return new FurnitureSettings(
                section.getBoolean("enabled", defaults.enabled()),
                section.getBoolean("small", defaults.small()),
                section.getBoolean("gravity", defaults.gravity()),
                section.getBoolean("fixed_rotation", defaults.fixedRotation()),
                defaults.rotationSnap(),
                defaults.modelTransform(),
                section.getBoolean("opposite_direction", defaults.oppositeDirection()),
                section.getBoolean("placeable_on.floor", defaults.floorPlacement()),
                section.getBoolean("placeable_on.walls", defaults.wallPlacement()),
                section.getBoolean("placeable_on.ceiling", defaults.ceilingPlacement()),
                section.getInt("light_level", defaults.lightLevel()),
                defaults.yOffset(),
                section.getBoolean("drop_when_mined", defaults.dropItem()),
                section.getString("entity", defaults.entityType()),
                section.getBoolean("solid", defaults.solid()),
                section.getDouble("hitbox.height", defaults.hitboxHeight()),
                section.getDouble("hitbox.height_offset", defaults.hitboxHeightOffset()),
                section.getDouble("hitbox.length", defaults.hitboxLength()),
                section.getDouble("hitbox.length_offset", defaults.hitboxLengthOffset()),
                section.getDouble("hitbox.width", defaults.hitboxWidth()),
                section.getDouble("hitbox.width_offset", defaults.hitboxWidthOffset())
        );
    }

    private ArmorRendering readModernEquipment(String namespace, ConfigurationSection itemSection) {
        String equipmentId = itemSection.getString("equipment.id", "");
        Material preferredMaterial = readMaterial(itemSection);
        EquipmentSlot slot = parseSlot(itemSection.getString("equipment.slot", ""));
        if (slot == null) {
            slot = slotFromMaterial(preferredMaterial);
        }
        if (equipmentId.isBlank() || slot == null) {
            return ArmorRendering.NONE;
        }

        return new ArmorRendering(
                null,
                qualifiedId(namespace, equipmentId),
                slot,
                preferredMaterial
        );
    }

    private ArmorRendering readLegacyArmor(String namespace, ConfigurationSection itemSection) {
        String armorId = itemSection.getString("specific_properties.armor.custom_armor", "");
        EquipmentSlot slot = parseSlot(itemSection.getString("specific_properties.armor.slot", ""));
        if (slot == null) {
            return ArmorRendering.NONE;
        }

        Color color = armorId.isBlank()
                ? parseColor(itemSection.getString("specific_properties.armor.color", ""))
                : legacyArmorColors.get(qualifiedId(namespace, armorId));
        if (color == null) {
            return ArmorRendering.NONE;
        }

        Material preferredMaterial = readMaterial(itemSection);
        if (preferredMaterial == null) {
            preferredMaterial = leatherMaterial(slot);
        }
        return new ArmorRendering(color, "", slot, preferredMaterial);
    }

    private static Material readMaterial(ConfigurationSection itemSection) {
        String materialName = itemSection.getString("material", "");
        if (materialName.isBlank()) {
            materialName = itemSection.getString("resource.material", "");
        }
        return materialName.isBlank() ? null : Material.matchMaterial(materialName);
    }

    static ModelDefinition readModelDefinition(String modelId, Reader reader) {
        JsonElement parsed = JsonParser.parseReader(reader);
        if (!parsed.isJsonObject()) {
            throw new JsonParseException("Model root must be a JSON object");
        }
        JsonObject root = parsed.getAsJsonObject();
        String parentId = null;
        JsonElement parent = root.get("parent");
        if (parent != null && parent.isJsonPrimitive() && parent.getAsJsonPrimitive().isString()) {
            parentId = normalizeParentModelId(parent.getAsString());
        }

        FurnitureModelTransform transform = null;
        JsonElement displayElement = root.get("display");
        if (displayElement != null && displayElement.isJsonObject()) {
            JsonElement headElement = displayElement.getAsJsonObject().get("head");
            if (headElement != null && headElement.isJsonObject()) {
                JsonObject head = headElement.getAsJsonObject();
                transform = FurnitureModelTransform.discovered(
                        readTransformValues(head.get("rotation")),
                        readTransformValues(head.get("translation"))
                );
            }
        }
        return new ModelDefinition(modelId, parentId, transform);
    }

    private static FurnitureModelTransform.Values readTransformValues(JsonElement element) {
        if (element == null || !element.isJsonArray()) {
            return FurnitureModelTransform.Values.ZERO;
        }
        JsonArray values = element.getAsJsonArray();
        return new FurnitureModelTransform.Values(
                numberAt(values, 0),
                numberAt(values, 1),
                numberAt(values, 2)
        );
    }

    private static double numberAt(JsonArray values, int index) {
        if (index >= values.size()) {
            return 0.0;
        }
        JsonElement value = values.get(index);
        return value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()
                ? value.getAsDouble()
                : 0.0;
    }

    private static String normalizeParentModelId(String parent) {
        String normalized = parent.trim().replace('\\', '/').toLowerCase(Locale.ROOT);
        if (normalized.endsWith(".json")) {
            normalized = normalized.substring(0, normalized.length() - ".json".length());
        }
        return normalized.contains(":") ? normalized : "minecraft:" + normalized;
    }

    private static String namespace(SourceDocument document) {
        String configuredNamespace = document.configuration().getString("info.namespace", "").trim();
        if (!configuredNamespace.isBlank()) {
            return configuredNamespace.toLowerCase(Locale.ROOT);
        }
        return document.inferredNamespace();
    }

    private static String qualifiedId(String namespace, String id) {
        String normalizedId = id.toLowerCase(Locale.ROOT);
        return normalizedId.contains(":") ? normalizedId : namespace.toLowerCase(Locale.ROOT) + ":" + normalizedId;
    }

    private static Color parseColor(String text) {
        if (text == null || !text.matches("#?[0-9A-Fa-f]{6}")) {
            return null;
        }
        return Color.fromRGB(Integer.parseInt(text.replace("#", ""), 16));
    }

    private static EquipmentSlot parseSlot(String text) {
        return switch (text.toLowerCase(Locale.ROOT)) {
            case "head", "helmet" -> EquipmentSlot.HEAD;
            case "chest", "chestplate" -> EquipmentSlot.CHEST;
            case "legs", "leggings" -> EquipmentSlot.LEGS;
            case "feet", "boots" -> EquipmentSlot.FEET;
            case "body" -> EquipmentSlot.BODY;
            default -> null;
        };
    }

    private static Material leatherMaterial(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> Material.LEATHER_HELMET;
            case CHEST -> Material.LEATHER_CHESTPLATE;
            case LEGS -> Material.LEATHER_LEGGINGS;
            case FEET -> Material.LEATHER_BOOTS;
            default -> null;
        };
    }

    private static EquipmentSlot slotFromMaterial(Material material) {
        if (material == null) {
            return null;
        }
        String name = material.name();
        if (name.endsWith("_HELMET") || material == Material.TURTLE_HELMET) {
            return EquipmentSlot.HEAD;
        }
        if (name.endsWith("_CHESTPLATE")) {
            return EquipmentSlot.CHEST;
        }
        if (name.endsWith("_LEGGINGS")) {
            return EquipmentSlot.LEGS;
        }
        if (name.endsWith("_BOOTS")) {
            return EquipmentSlot.FEET;
        }
        return null;
    }

    private static boolean isYaml(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".yml") || name.endsWith(".yaml");
    }

    static String modelId(Path root, Path path) {
        Path relative = root.relativize(path);
        List<String> segments = new ArrayList<>();
        for (Path segment : relative) {
            segments.add(segment.toString());
        }
        return modelId(segments);
    }

    static String modelId(String entryName) {
        return modelId(List.of(entryName.replace('\\', '/').split("/")));
    }

    private static String modelId(List<String> pathSegments) {
        for (int index = 0; index + 3 < pathSegments.size(); index++) {
            if (!pathSegments.get(index).equalsIgnoreCase("assets")
                    || !pathSegments.get(index + 2).equalsIgnoreCase("models")) {
                continue;
            }
            String fileName = pathSegments.get(pathSegments.size() - 1);
            if (!fileName.toLowerCase(Locale.ROOT).endsWith(".json")) {
                return null;
            }
            String namespace = pathSegments.get(index + 1).toLowerCase(Locale.ROOT);
            String modelPath = String.join("/", pathSegments.subList(index + 3, pathSegments.size()));
            modelPath = modelPath.substring(0, modelPath.length() - ".json".length())
                    .toLowerCase(Locale.ROOT);
            return namespace.isBlank() || modelPath.isBlank() ? null : namespace + ":" + modelPath;
        }
        return null;
    }

    private static boolean isConfigYaml(Path root, Path path) {
        if (!isYaml(path)) {
            return false;
        }
        if (root.getFileName() != null && root.getFileName().toString().equalsIgnoreCase("configs")) {
            return true;
        }
        for (Path segment : root.relativize(path)) {
            if (segment.toString().equalsIgnoreCase("configs")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isConfigYaml(ZipEntry entry) {
        String normalizedName = entry.getName().replace('\\', '/').toLowerCase(Locale.ROOT);
        return (normalizedName.endsWith(".yml") || normalizedName.endsWith(".yaml"))
                && (normalizedName.startsWith("configs/") || normalizedName.contains("/configs/"));
    }

    private static String inferNamespace(Path root, Path path) {
        Path relativePath = root.relativize(path);
        for (int index = 0; index < relativePath.getNameCount(); index++) {
            if (!relativePath.getName(index).toString().equalsIgnoreCase("configs")) {
                continue;
            }
            if (index > 0) {
                return relativePath.getName(index - 1).toString().toLowerCase(Locale.ROOT);
            }
            Path parent = root.getParent();
            return parent != null && parent.getFileName() != null
                    ? parent.getFileName().toString().toLowerCase(Locale.ROOT)
                    : "";
        }
        return "";
    }

    private static String inferNamespace(String entryName) {
        String[] segments = entryName.replace('\\', '/').split("/");
        for (int index = 0; index < segments.length; index++) {
            if (segments[index].equalsIgnoreCase("configs")) {
                return index > 0 ? segments[index - 1].toLowerCase(Locale.ROOT) : "";
            }
        }
        return "";
    }

    static List<String> categoryFolders(Path root, Path path) {
        Path relativePath = root.relativize(path);
        List<String> folders = new ArrayList<>();
        boolean afterConfigs = false;
        for (int index = 0; index < relativePath.getNameCount() - 1; index++) {
            String segment = relativePath.getName(index).toString();
            if (afterConfigs) {
                folders.add(normalizeCategory(segment));
            } else if (segment.equalsIgnoreCase("configs")) {
                afterConfigs = true;
            }
        }
        return folders.stream().filter(folder -> !folder.isBlank()).toList();
    }

    static List<String> categoryFolders(String entryName) {
        String[] segments = entryName.replace('\\', '/').split("/");
        List<String> folders = new ArrayList<>();
        boolean afterConfigs = false;
        for (int index = 0; index < segments.length - 1; index++) {
            if (afterConfigs) {
                folders.add(normalizeCategory(segments[index]));
            } else if (segments[index].equalsIgnoreCase("configs")) {
                afterConfigs = true;
            }
        }
        return folders.stream().filter(folder -> !folder.isBlank()).toList();
    }

    private static String normalizeCategory(String text) {
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]+", "_")
                .replaceAll("^_+|_+$", "");
    }

    private record SourceDocument(
            String name,
            String inferredNamespace,
            List<String> categoryFolders,
            YamlConfiguration configuration
    ) {
    }

    private record ItemArmorRule(String itemId, ArmorRendering rendering) {
    }

    private record SourceItem(String namespace, ConfigurationSection section, List<String> categoryPath) {
    }

    private record ItemModelRule(String modelId, boolean generated) {
    }

    record ModelDefinition(
            String modelId,
            String parentId,
            FurnitureModelTransform transform
    ) {
    }

}
