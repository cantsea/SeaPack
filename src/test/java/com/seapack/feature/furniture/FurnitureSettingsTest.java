package com.seapack.feature.furniture;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FurnitureSettingsTest {
    @Test
    void normalizesEntityTypeAndClampsLightLevel() {
        FurnitureModelTransform modelTransform = FurnitureModelTransform.discovered(
                new FurnitureModelTransform.Values(0.0, 180.0, 0.0),
                new FurnitureModelTransform.Values(0.0, -29.75, 7.0)
        );
        FurnitureSettings settings = new FurnitureSettings(
                true, false, false, false, 12, modelTransform, false,
                true, false, false,
                40, 0.0, true, "ArmorStand", false,
                9.0, -9.0, 0.0, 9.0, Double.NaN, 0.0
        );

        assertEquals(15, settings.lightLevel());
        assertEquals("armor_stand", settings.entityType());
        assertEquals(3.0, settings.hitboxHeight());
        assertEquals(-3.0, settings.hitboxHeightOffset());
        assertEquals(0.1, settings.hitboxLength());
        assertEquals(3.0, settings.hitboxLengthOffset());
        assertEquals(1.0, settings.hitboxWidth());
        assertEquals(180.0, settings.modelTransform().rotation().y());
        assertEquals(45, settings.rotationSnap());
    }
}
