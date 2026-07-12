package com.seapack.feature.item;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class ItemCatalogTest {
    @Test
    void indexesNestedCategoriesWithoutRepeatedTreeScans() {
        CustomItemDefinition summer = item(
                "pack:summer_chair",
                List.of("general", "furniture", "summer")
        );
        CustomItemDefinition winter = item(
                "pack:winter_chair",
                List.of("general", "furniture", "winter")
        );
        ItemCatalog catalog = new ItemCatalog(List.of(summer, winter), Map.of(), Map.of());

        assertEquals(List.of("general"), catalog.categories());
        assertEquals(List.of("furniture"), catalog.childCategories(List.of("GENERAL")));
        assertEquals(2, catalog.categoryTreeCount(List.of("general", "furniture")));
        assertEquals(List.of(summer), catalog.itemsDirectlyInCategory(
                List.of("general", "furniture", "summer")
        ));
    }

    @Test
    void groupsSetVariantsUsingTheCurrentAliasAlgorithm() {
        CustomItemDefinition base = item("pack:dragonsoul_sword", List.of("sets"));
        CustomItemDefinition variant = item("pack:dragonsoul_crystal_sword", List.of("sets"));
        Map<String, String> aliases = SetGroupResolver.aliases(List.of(base, variant));
        ItemCatalog catalog = new ItemCatalog(List.of(base, variant), Map.of(), aliases);

        assertEquals(List.of("dragonsoul"), catalog.setGroups());
        assertEquals(List.of(base, variant), catalog.itemsInSetGroup("DRAGONSOUL"));
    }

    private static CustomItemDefinition item(String id, List<String> path) {
        return new CustomItemDefinition(
                id,
                Material.PAPER,
                1,
                "",
                path,
                "<aqua>Item</aqua>",
                List.of(),
                Map.of(),
                ArmorRendering.NONE
        );
    }
}
