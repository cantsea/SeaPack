package com.seapack.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.seapack.feature.item.ArmorRendering;
import com.seapack.feature.item.CustomItemDefinition;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class CustomItemPlaceholdersTest {
    @Test
    void rendersReadableAndKeyMaterialValues() {
        CustomItemDefinition item = item("pack:diamond_pickaxe", Material.DIAMOND_PICKAXE, List.of("sets"));

        assertEquals(
                "Diamond Pickaxe|DIAMOND_PICKAXE|Pickaxe|PICKAXE",
                CustomItemPlaceholders.render(
                        "{material}|{material_key}|{tool_type}|{tool_type_key}",
                        item
                )
        );
    }

    @Test
    void infersPaperToolTypesFromTheItemKey() {
        CustomItemDefinition item = item(
                "pack:dragonsoul_crystal_pickaxe",
                Material.PAPER,
                List.of("sets")
        );

        assertEquals("Pickaxe", CustomItemPlaceholders.toolType(item));
        assertEquals("Battle Axe", CustomItemPlaceholders.toolType(
                item("pack:dragonsoul_battle_axe", Material.PAPER, List.of("sets"))
        ));
        assertEquals("Greatsword", CustomItemPlaceholders.toolType(
                item("pack:dragonsoul_greatsword", Material.PAPER, List.of("sets"))
        ));
    }

    @Test
    void infersFurnitureAndPlushieTypesFromCategories() {
        assertEquals("Furniture", CustomItemPlaceholders.toolType(
                item("pack:summer_shelf", Material.PAPER, List.of("general", "furniture"))
        ));
        assertEquals("Plushie", CustomItemPlaceholders.toolType(
                item("pack:otter", Material.PAPER, List.of("general", "plushies"))
        ));
    }

    private static CustomItemDefinition item(String id, Material material, List<String> categories) {
        return new CustomItemDefinition(
                id,
                material,
                1,
                "",
                categories,
                "<aqua>Item</aqua>",
                List.of(),
                Map.of(),
                ArmorRendering.NONE
        );
    }
}
