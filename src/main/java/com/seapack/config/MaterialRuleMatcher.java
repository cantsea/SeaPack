package com.seapack.config;

import java.util.Locale;
import org.bukkit.Material;

final class MaterialRuleMatcher {
    private MaterialRuleMatcher() {
    }

    static boolean matches(Material material, String materialRule) {
        String rule = materialRule.toUpperCase(Locale.ROOT).replace(' ', '_');
        if (rule.equals("*") || rule.equals("ALL")) {
            return true;
        }
        if (rule.startsWith("*_")) {
            return material.name().endsWith(rule.substring(1));
        }

        Material exactMaterial = Material.matchMaterial(rule);
        if (exactMaterial != null) {
            return exactMaterial == material;
        }

        return switch (rule) {
            case "PICKAXES" -> material.name().endsWith("_PICKAXE");
            case "AXES" -> material.name().endsWith("_AXE");
            case "SHOVELS" -> material.name().endsWith("_SHOVEL");
            case "HOES" -> material.name().endsWith("_HOE");
            case "SWORDS" -> material.name().endsWith("_SWORD");
            case "HELMETS" -> material.name().endsWith("_HELMET") || material == Material.TURTLE_HELMET;
            case "CHESTPLATES" -> material.name().endsWith("_CHESTPLATE");
            case "LEGGINGS" -> material.name().endsWith("_LEGGINGS");
            case "BOOTS" -> material.name().endsWith("_BOOTS");
            case "ARMOR" -> material.name().matches(".*_(HELMET|CHESTPLATE|LEGGINGS|BOOTS)")
                    || material == Material.TURTLE_HELMET;
            case "TOOLS" -> material.name().matches(".*_(PICKAXE|AXE|SHOVEL|HOE)");
            case "WEAPONS" -> material.name().matches(".*_(SWORD|AXE)")
                    || material == Material.BOW
                    || material == Material.CROSSBOW
                    || material == Material.TRIDENT;
            default -> false;
        };
    }
}
