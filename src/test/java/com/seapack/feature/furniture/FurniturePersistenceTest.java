package com.seapack.feature.furniture;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class FurniturePersistenceTest {
    @Test
    void persistentKeysRemainCompatibleWithPlacedFurniture() {
        assertEquals(List.of(
                "furniture_id",
                "furniture_block_x",
                "furniture_block_y",
                "furniture_block_z",
                "furniture_light",
                "furniture_light_level",
                "furniture_light_x",
                "furniture_light_y",
                "furniture_light_z",
                "furniture_barriers"
        ), List.of(
                FurniturePersistence.FURNITURE_ID,
                FurniturePersistence.BLOCK_X,
                FurniturePersistence.BLOCK_Y,
                FurniturePersistence.BLOCK_Z,
                FurniturePersistence.LIGHT,
                FurniturePersistence.LIGHT_LEVEL,
                FurniturePersistence.LIGHT_X,
                FurniturePersistence.LIGHT_Y,
                FurniturePersistence.LIGHT_Z,
                FurniturePersistence.BARRIERS
        ));
    }
}
