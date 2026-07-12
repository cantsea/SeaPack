package com.seapack.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class MaterialRuleMatcherTest {
    @Test
    void supportsExactMaterialsAndSuffixWildcards() {
        assertTrue(MaterialRuleMatcher.matches(Material.DIAMOND_PICKAXE, "diamond_pickaxe"));
        assertTrue(MaterialRuleMatcher.matches(Material.NETHERITE_PICKAXE, "*_pickaxe"));
        assertFalse(MaterialRuleMatcher.matches(Material.DIAMOND_SHOVEL, "*_pickaxe"));
    }

    @Test
    void supportsNamedToolArmorAndWeaponGroups() {
        assertTrue(MaterialRuleMatcher.matches(Material.IRON_SHOVEL, "tools"));
        assertTrue(MaterialRuleMatcher.matches(Material.TURTLE_HELMET, "armor"));
        assertTrue(MaterialRuleMatcher.matches(Material.CROSSBOW, "weapons"));
        assertFalse(MaterialRuleMatcher.matches(Material.PAPER, "weapons"));
    }

    @Test
    void supportsGlobalRules() {
        assertTrue(MaterialRuleMatcher.matches(Material.PAPER, "*"));
        assertTrue(MaterialRuleMatcher.matches(Material.PAPER, "all"));
    }
}
