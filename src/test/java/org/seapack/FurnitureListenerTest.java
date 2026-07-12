package org.seapack;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;

class FurnitureListenerTest {
    @Test
    void furnitureFacesThePlayersRelativePosition() {
        Location anchor = new Location(null, 10.5, 64.0, 10.5);

        assertEquals(-180.0f, FurnitureListener.facingPositionYaw(
                anchor,
                new Location(null, 10.5, 64.0, 5.5),
                0.0f
        ));
        assertEquals(0.0f, FurnitureListener.facingPositionYaw(
                anchor,
                new Location(null, 10.5, 64.0, 15.5),
                0.0f
        ));
        assertEquals(-90.0f, FurnitureListener.facingPositionYaw(
                anchor,
                new Location(null, 15.5, 64.0, 10.5),
                0.0f
        ));
        assertEquals(90.0f, FurnitureListener.facingPositionYaw(
                anchor,
                new Location(null, 5.5, 64.0, 10.5),
                0.0f
        ));
    }

    @Test
    void relativeFacingFallsBackWhenPlayerIsDirectlyAboveTheAnchor() {
        Location anchor = new Location(null, 10.5, 64.0, 10.5);
        assertEquals(253.0f, FurnitureListener.facingPositionYaw(
                anchor,
                new Location(null, 10.5, 70.0, 10.5),
                73.0f
        ));
    }

    @Test
    void modelDefaultsAndConfiguredYawComposePredictably() {
        float entityYaw = FurnitureListener.modelEntityYaw(90.0f, 180.0);

        assertEquals(-90.0f, entityYaw);
        assertEquals(90.0f, FurnitureListener.modelVisualYaw(entityYaw, 180.0));
        assertEquals(-90.0f, FurnitureListener.modelVisualYaw(entityYaw, 0.0));
    }

    @Test
    void rotationSnapsToTheNearestConfiguredStep() {
        assertEquals(45.0f, FurnitureListener.snapYaw(46.0f, 45));
        assertEquals(90.0f, FurnitureListener.snapYaw(68.0f, 45));
        assertEquals(90.0f, FurnitureListener.snapYaw(46.0f, 90));
        assertEquals(0.0f, FurnitureListener.snapYaw(44.0f, 90));
        assertEquals(-90.0f, FurnitureListener.snapYaw(-68.0f, 45));
    }

    @Test
    void editedModelTranslationMovesRelativeToFacing() {
        Location location = new Location(null, 10.0, 20.0, 30.0);

        FurnitureListener.applyModelTranslationOverride(
                location,
                90.0f,
                new FurnitureModelTransform.Values(0.0, 16.0, 16.0)
        );

        assertEquals(9.0, location.getX(), 1.0E-8);
        assertEquals(21.0, location.getY(), 1.0E-8);
        assertEquals(30.0, location.getZ(), 1.0E-8);
    }
}
