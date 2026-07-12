package com.seapack.feature.item;

import com.seapack.util.TextUtils;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Equippable;
import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;

public record CustomItemDefinition(
        String id,
        Material material,
        int customModelData,
        String itemModel,
        List<String> categoryPath,
        String displayName,
        List<String> lore,
        Map<Enchantment, Integer> enchantments,
        ArmorRendering armorRendering
) {
    private static final List<String> BASE_MATERIAL_SUFFIXES = List.of(
            "chainmail_helmet", "diamond_helmet", "golden_helmet", "iron_helmet", "leather_helmet", "netherite_helmet", "turtle_helmet",
            "chainmail_chestplate", "diamond_chestplate", "golden_chestplate", "iron_chestplate", "leather_chestplate", "netherite_chestplate",
            "chainmail_leggings", "diamond_leggings", "golden_leggings", "iron_leggings", "leather_leggings", "netherite_leggings",
            "chainmail_boots", "diamond_boots", "golden_boots", "iron_boots", "leather_boots", "netherite_boots",
            "wooden_sword", "stone_sword", "iron_sword", "golden_sword", "diamond_sword", "netherite_sword",
            "wooden_axe", "stone_axe", "iron_axe", "golden_axe", "diamond_axe", "netherite_axe",
            "wooden_pickaxe", "stone_pickaxe", "iron_pickaxe", "golden_pickaxe", "diamond_pickaxe", "netherite_pickaxe",
            "wooden_shovel", "stone_shovel", "iron_shovel", "golden_shovel", "diamond_shovel", "netherite_shovel",
            "wooden_hoe", "stone_hoe", "iron_hoe", "golden_hoe", "diamond_hoe", "netherite_hoe"
    );

    private static final List<String> SET_PIECE_SUFFIXES = List.of(
            "cosmetics_wings_self", "cosmetics_cape_self", "cosmetics_backpack_self",
            "cosmeticscore_wings_self", "cosmeticscore_backpack_self",
            "chest_opening", "big_sword", "greatsword", "battle_axe",
            "chestplate", "leggings", "backpack", "gauntlet", "helmet",
            "pickaxe", "shovel", "sickle", "trident", "sword", "spear",
            "scythe", "staff", "hammer", "dagger", "halberd", "boots",
            "chest", "club", "key", "wings", "wing", "cape", "hat",
            "mask", "back", "blade", "fishing_rod", "crossbow", "shield",
            "pirate", "mace", "bow", "axe", "hoe",
            "cosmeticscore", "cosmetics"
    );

    public CustomItemDefinition {
        List<String> normalizedPath = categoryPath == null ? List.of() : categoryPath.stream()
                .filter(part -> part != null && !part.isBlank())
                .map(part -> part.toLowerCase(Locale.ROOT))
                .toList();
        if (normalizedPath.isEmpty()) {
            String namespace = id.contains(":") ? id.substring(0, id.indexOf(':')) : "other";
            normalizedPath = List.of(namespace.toLowerCase(Locale.ROOT));
        }
        categoryPath = List.copyOf(normalizedPath);
    }

    public String category() {
        return categoryPath.getFirst();
    }

    public String itemKey() {
        return id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
    }

    public String setGroup() {
        if (!category().equalsIgnoreCase("sets")) {
            return "";
        }

        String group = itemKey().toLowerCase().replace('-', '_');
        String previous;
        do {
            previous = group;
            group = stripVariants(group);
            group = stripSuffix(group, BASE_MATERIAL_SUFFIXES);
            group = stripVariants(group);
            group = stripSuffix(group, SET_PIECE_SUFFIXES);
            group = stripVariants(group);
        } while (!group.equals(previous));
        return group.replaceAll("^_+|_+$", "");
    }

    public List<String> searchTokens() {
        return Arrays.stream((id + " " + itemKey() + " " + String.join(" ", categoryPath)
                        + " " + TextUtils.plain(displayName)).split("[^A-Za-z0-9]+"))
                .filter(token -> !token.isBlank())
                .toList();
    }

    public CustomItemDefinition withCustomization(ItemCustomization customization) {
        return new CustomItemDefinition(
                id,
                material,
                customModelData,
                itemModel,
                categoryPath,
                customization.displayName().orElse(displayName),
                customization.lore().orElse(lore),
                customization.enchantments(),
                armorRendering
        );
    }

    @SuppressWarnings("UnstableApiUsage")
    public ItemStack createItem(int amount, NamespacedKey itemIdKey) {
        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(TextUtils.component(displayName));
        var modelData = meta.getCustomModelDataComponent();
        modelData.setFloats(List.of((float) customModelData));
        meta.setCustomModelDataComponent(modelData);
        meta.getPersistentDataContainer().set(itemIdKey, PersistentDataType.STRING, id);
        if (meta instanceof LeatherArmorMeta leatherArmorMeta && armorRendering.hasLeatherColor()) {
            leatherArmorMeta.setColor(armorRendering.leatherColor());
        }
        if (!lore.isEmpty()) {
            meta.lore(TextUtils.components(lore));
        }
        // Ignore vanilla level limits intentionally. ItemMeta also retains mutually exclusive
        // enchantments, allowing combinations such as Infinity and Mending.
        enchantments.forEach((enchantment, level) -> meta.addEnchant(enchantment, level, true));
        item.setItemMeta(meta);

        if (!itemModel.isBlank()) {
            item.setData(DataComponentTypes.ITEM_MODEL, Key.key(itemModel));
        }

        if (armorRendering.hasEquipmentAsset()) {
            Equippable current = item.getData(DataComponentTypes.EQUIPPABLE);
            Equippable.Builder builder = current == null
                    ? Equippable.equippable(armorRendering.slot())
                    : current.toBuilder();
            item.setData(
                    DataComponentTypes.EQUIPPABLE,
                    builder.assetId(Key.key(armorRendering.equipmentAssetId()))
            );
        }
        return item;
    }

    private static String stripVariants(String text) {
        String value = text;
        boolean changed = true;
        while (changed) {
            changed = false;
            for (String suffix : List.of("_animated", "_normal", "_self")) {
                if (value.endsWith(suffix)) {
                    value = value.substring(0, value.length() - suffix.length());
                    changed = true;
                }
            }
            if (value.matches(".*_(v\\d+|\\d+)$")) {
                value = value.substring(0, value.lastIndexOf('_'));
                changed = true;
            }
        }
        return value;
    }

    private static String stripSuffix(String text, List<String> suffixes) {
        for (String suffix : suffixes) {
            String delimitedSuffix = "_" + suffix;
            if (text.endsWith(delimitedSuffix)) {
                return text.substring(0, text.length() - delimitedSuffix.length());
            }
            if (text.endsWith(suffix) && text.length() > suffix.length()) {
                return text.substring(0, text.length() - suffix.length());
            }
        }
        return text;
    }
}
