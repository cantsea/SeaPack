package com.seapack.feature.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class ItemSearchTest {
    @Test
    void preservesCharacterFrequencyManhattanMatching() {
        assertEquals(0, ItemSearch.manhattanDistance("sowrd", "sword"));
        assertEquals("dragonsword", ItemSearch.normalize("Dragon Sword"));
    }

    @Test
    void exactAndAnagramMatchesSortByScoreThenId() {
        CustomItemDefinition alpha = item("pack:alpha_sword", "<aqua>Alpha Sword</aqua>");
        CustomItemDefinition beta = item("pack:beta_sword", "<aqua>Beta Sword</aqua>");
        ItemSearch search = new ItemSearch(List.of(beta, alpha), ignored -> "");

        List<CustomItemDefinition> results = search.search("sowrd");

        assertTrue(results.size() >= 2);
        assertEquals(List.of(alpha, beta), results.subList(0, 2));
    }

    private static CustomItemDefinition item(String id, String displayName) {
        return new CustomItemDefinition(
                id,
                Material.PAPER,
                1,
                "",
                List.of("general"),
                displayName,
                List.of(),
                Map.of(),
                ArmorRendering.NONE
        );
    }
}
