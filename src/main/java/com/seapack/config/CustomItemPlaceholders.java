package com.seapack.config;

import com.seapack.feature.item.CustomItemDefinition;
import java.util.List;
import java.util.Locale;

final class CustomItemPlaceholders {
    private static final List<String> TOOL_TYPES = List.of(
            "FISHING_ROD",
            "BATTLE_AXE",
            "GREAT_SWORD",
            "CHESTPLATE",
            "PICKAXE",
            "CROSSBOW",
            "GREATSWORD",
            "BIG_SWORD",
            "LEGGINGS",
            "BACKPACK",
            "GAUNTLET",
            "HELMET",
            "SHOVEL",
            "TRIDENT",
            "HALBERD",
            "ELYTRA",
            "SHIELD",
            "SICKLE",
            "SCYTHE",
            "HAMMER",
            "DAGGER",
            "KATANA",
            "SWORD",
            "BOOTS",
            "SHEARS",
            "SPEAR",
            "STAFF",
            "BLADE",
            "HOE",
            "AXE",
            "BOW",
            "ROD",
            "WAND",
            "MACE",
            "CLUB",
            "WINGS",
            "WING",
            "CAPE",
            "MASK",
            "HAT",
            "KEY"
    );

    private CustomItemPlaceholders() {
    }

    static String render(String text, CustomItemDefinition item) {
        if (text == null || text.isEmpty()) {
            return text == null ? "" : text;
        }
        return text
                .replace("{material}", prettify(item.material().name()))
                .replace("{material_key}", item.material().name())
                .replace("{tool_type}", toolType(item))
                .replace("{tool_type_key}", toolType(item).toUpperCase(Locale.ROOT).replace(' ', '_'));
    }

    static String toolType(CustomItemDefinition item) {
        String materialType = typeFrom(item.material().name());
        if (materialType != null) {
            return prettify(materialType);
        }

        String itemType = typeFrom(item.itemKey().toUpperCase(Locale.ROOT).replace('-', '_'));
        if (itemType != null) {
            return prettify(itemType);
        }

        for (String category : item.categoryPath()) {
            if (category.equalsIgnoreCase("plushies") || category.equalsIgnoreCase("plushie")) {
                return "Plushie";
            }
            if (category.equalsIgnoreCase("furniture")) {
                return "Furniture";
            }
        }
        return "Item";
    }

    private static String typeFrom(String value) {
        String padded = "_" + value + "_";
        for (String type : TOOL_TYPES) {
            if (padded.contains("_" + type + "_")) {
                return type;
            }
        }
        return null;
    }

    private static String prettify(String text) {
        String[] words = text.toLowerCase(Locale.ROOT).split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }
}
