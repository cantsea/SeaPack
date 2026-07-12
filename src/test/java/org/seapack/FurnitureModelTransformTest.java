package org.seapack;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FurnitureModelTransformTest {
    @Test
    void configuredValuesKeepTheDiscoveredSourceForDeltaCalculations() {
        FurnitureModelTransform discovered = FurnitureModelTransform.discovered(
                new FurnitureModelTransform.Values(0.0, 180.0, 0.0),
                new FurnitureModelTransform.Values(0.0, -29.75, 7.0)
        );

        FurnitureModelTransform configured = discovered.withConfigured(
                new FurnitureModelTransform.Values(0.0, 0.0, 0.0),
                new FurnitureModelTransform.Values(0.0, -28.75, 7.0)
        );

        assertEquals(-180.0, configured.rotationDelta().y());
        assertEquals(1.0, configured.translationDelta().y());
        assertEquals(180.0, configured.sourceRotation().y());
        assertEquals(-29.75, configured.sourceTranslation().y());
    }
}
