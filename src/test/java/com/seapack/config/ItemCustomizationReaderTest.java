package com.seapack.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class ItemCustomizationReaderTest {
    @Test
    void acceptsLevelsAboveVanillaAndMutuallyExclusiveEnchantments() {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("enchants.sharpness", 100);
        configuration.set("enchants.infinity", 1);
        configuration.set("enchants.mending", 1);

        List<String> invalidEnchantments = new ArrayList<>();
        Map<String, Integer> enchantments = ItemCustomizationReader.configuredEnchantmentLevels(
                configuration,
                (name, level) -> invalidEnchantments.add(name)
        );

        assertEquals(100, enchantments.get("sharpness"));
        assertEquals(1, enchantments.get("infinity"));
        assertEquals(1, enchantments.get("mending"));
        assertTrue(invalidEnchantments.isEmpty());
    }

    @Test
    void rejectsLevelsOutsideMinecraftStorageRange() {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("enchants.unbreaking", 0);
        configuration.set("enchants.sharpness", 256);
        configuration.set("enchants.power", 255);

        List<String> invalidEnchantments = new ArrayList<>();
        Map<String, Integer> enchantments = ItemCustomizationReader.configuredEnchantmentLevels(
                configuration,
                (name, level) -> invalidEnchantments.add(name + ":" + level)
        );

        assertEquals(Map.of("power", 255), enchantments);
        assertEquals(List.of("unbreaking:0", "sharpness:256"), invalidEnchantments);
    }
}
