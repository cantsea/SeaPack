package com.seapack.feature.furniture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

class FurnitureGeometryTest {
    @Test
    void oneBlockHitboxOccupiesOnlyItsAnchor() {
        Location origin = new Location(null, 10.5, 20.0, 30.5);

        List<FurnitureGeometry.BlockPosition> blocks = FurnitureGeometry.occupiedBlocks(
                origin,
                0.0f,
                settings(1.0, 1.0, 1.0)
        );

        assertEquals(List.of(new FurnitureGeometry.BlockPosition(10, 20, 30)), blocks);
    }

    @Test
    void configuredHitboxIsUsedForRayTargeting() {
        Location origin = new Location(null, 0.5, 0.0, 0.5);

        double distance = FurnitureGeometry.rayDistance(
                new Vector(0.5, 0.5, -3.0),
                new Vector(0.0, 0.0, 1.0),
                origin,
                0.0f,
                settings(1.0, 2.0, 1.0),
                5.0
        );

        assertTrue(distance >= 2.0 && distance <= 3.0);
    }

    private static FurnitureSettings settings(double width, double length, double height) {
        return new FurnitureSettings(
                true, false, false, false, 45, FurnitureModelTransform.IDENTITY, false,
                true, false, false,
                0, 0.0, true, "armor_stand", false,
                height, 0.0, length, 0.0, width, 0.0
        );
    }
}
