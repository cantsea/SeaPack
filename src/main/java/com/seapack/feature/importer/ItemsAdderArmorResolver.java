package com.seapack.feature.importer;

import com.seapack.SeaPack;
import com.seapack.feature.item.ArmorRendering;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.EquipmentSlot;

final class ItemsAdderArmorResolver {
    private final SeaPack plugin;
    private final List<ItemsAdderSourceDocument> documents;
    private final Map<String, ItemsAdderSourceItem> sourceItems;
    private final Map<String, Material> sourceMaterials;
    private final Map<String, Color> legacyArmorColors = new HashMap<>();

    ItemsAdderArmorResolver(
            SeaPack plugin,
            List<ItemsAdderSourceDocument> documents,
            Map<String, ItemsAdderSourceItem> sourceItems,
            Map<String, Material> sourceMaterials
    ) {
        this.plugin = plugin;
        this.documents = documents;
        this.sourceItems = sourceItems;
        this.sourceMaterials = sourceMaterials;
    }

    List<Rule> resolve() {
        readArmorDefinitions();
        List<Rule> rules = new ArrayList<>();
        Map<String, ArmorRendering> resolved = new HashMap<>();
        for (String itemId : sourceItems.keySet()) {
            ArmorRendering rendering = resolveArmorRendering(itemId, resolved, new LinkedHashSet<>());
            if (rendering != ArmorRendering.NONE) {
                rules.add(new Rule(itemId, rendering));
            }
        }
        rules.sort(Comparator.comparingInt((Rule rule) -> rule.itemId().length()).reversed());
        return List.copyOf(rules);
    }

    private void readArmorDefinitions() {
        for (ItemsAdderSourceDocument document : documents) {
            String namespace = document.namespace();
            if (namespace.isBlank()) {
                continue;
            }
            readLegacyArmorColors(document, namespace, "legacy_armor_renderings");
            readLegacyArmorColors(document, namespace, "armors_rendering");
        }
    }

    private void readLegacyArmorColors(
            ItemsAdderSourceDocument document,
            String namespace,
            String sectionName
    ) {
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
            legacyArmorColors.put(ItemsAdderIds.qualified(namespace, armorId), color);
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
        ItemsAdderSourceItem sourceItem = sourceItems.get(itemId);
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
                        ItemsAdderIds.qualified(sourceItem.namespace(), variantOf),
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

    private ArmorRendering readModernEquipment(String namespace, ConfigurationSection itemSection) {
        String equipmentId = itemSection.getString("equipment.id", "");
        Material preferredMaterial = ItemsAdderItemResolver.readMaterial(itemSection);
        EquipmentSlot slot = parseSlot(itemSection.getString("equipment.slot", ""));
        if (slot == null) {
            slot = slotFromMaterial(preferredMaterial);
        }
        if (equipmentId.isBlank() || slot == null) {
            return ArmorRendering.NONE;
        }

        return new ArmorRendering(
                null,
                ItemsAdderIds.qualified(namespace, equipmentId),
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
                : legacyArmorColors.get(ItemsAdderIds.qualified(namespace, armorId));
        if (color == null) {
            return ArmorRendering.NONE;
        }

        Material preferredMaterial = ItemsAdderItemResolver.readMaterial(itemSection);
        if (preferredMaterial == null) {
            preferredMaterial = leatherMaterial(slot);
        }
        return new ArmorRendering(color, "", slot, preferredMaterial);
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

    record Rule(String itemId, ArmorRendering rendering) {
    }
}
