package com.seapack.feature.furniture;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public final class FurniturePersistence {
    public static final String FURNITURE_ID = "furniture_id";
    public static final String BLOCK_X = "furniture_block_x";
    public static final String BLOCK_Y = "furniture_block_y";
    public static final String BLOCK_Z = "furniture_block_z";
    public static final String LIGHT = "furniture_light";
    public static final String LIGHT_LEVEL = "furniture_light_level";
    public static final String LIGHT_X = "furniture_light_x";
    public static final String LIGHT_Y = "furniture_light_y";
    public static final String LIGHT_Z = "furniture_light_z";
    public static final String BARRIERS = "furniture_barriers";

    private final NamespacedKey furnitureIdKey;
    private final NamespacedKey blockXKey;
    private final NamespacedKey blockYKey;
    private final NamespacedKey blockZKey;
    private final NamespacedKey lightKey;
    private final NamespacedKey lightLevelKey;
    private final NamespacedKey lightXKey;
    private final NamespacedKey lightYKey;
    private final NamespacedKey lightZKey;
    private final NamespacedKey barrierBlocksKey;

    public FurniturePersistence(Plugin plugin) {
        this.furnitureIdKey = new NamespacedKey(plugin, FURNITURE_ID);
        this.blockXKey = new NamespacedKey(plugin, BLOCK_X);
        this.blockYKey = new NamespacedKey(plugin, BLOCK_Y);
        this.blockZKey = new NamespacedKey(plugin, BLOCK_Z);
        this.lightKey = new NamespacedKey(plugin, LIGHT);
        this.lightLevelKey = new NamespacedKey(plugin, LIGHT_LEVEL);
        this.lightXKey = new NamespacedKey(plugin, LIGHT_X);
        this.lightYKey = new NamespacedKey(plugin, LIGHT_Y);
        this.lightZKey = new NamespacedKey(plugin, LIGHT_Z);
        this.barrierBlocksKey = new NamespacedKey(plugin, BARRIERS);
    }

    public void tag(
            ArmorStand armorStand,
            String itemId,
            Block anchorBlock,
            Block lightBlock,
            int lightLevel,
            List<FurnitureGeometry.BlockPosition> solidBlocks
    ) {
        PersistentDataContainer data = armorStand.getPersistentDataContainer();
        data.set(furnitureIdKey, PersistentDataType.STRING, itemId);
        data.set(blockXKey, PersistentDataType.INTEGER, anchorBlock.getX());
        data.set(blockYKey, PersistentDataType.INTEGER, anchorBlock.getY());
        data.set(blockZKey, PersistentDataType.INTEGER, anchorBlock.getZ());
        data.set(lightKey, PersistentDataType.BYTE, lightLevel > 0 ? (byte) 1 : (byte) 0);
        data.set(lightLevelKey, PersistentDataType.INTEGER, lightLevel);
        data.set(lightXKey, PersistentDataType.INTEGER, lightBlock.getX());
        data.set(lightYKey, PersistentDataType.INTEGER, lightBlock.getY());
        data.set(lightZKey, PersistentDataType.INTEGER, lightBlock.getZ());
        if (!solidBlocks.isEmpty()) {
            data.set(barrierBlocksKey, PersistentDataType.STRING, solidBlocks.stream()
                    .map(position -> position.x() + "," + position.y() + "," + position.z())
                    .collect(Collectors.joining(";")));
        }
    }

    public boolean isTagged(ArmorStand armorStand) {
        return armorStand.getPersistentDataContainer().has(furnitureIdKey, PersistentDataType.STRING);
    }

    public String itemId(ArmorStand armorStand) {
        return armorStand.getPersistentDataContainer().get(furnitureIdKey, PersistentDataType.STRING);
    }

    public Location anchor(ArmorStand armorStand) {
        Coordinates coordinates = coordinates(
                armorStand.getPersistentDataContainer(),
                blockXKey,
                blockYKey,
                blockZKey
        );
        return coordinates == null
                ? armorStand.getLocation()
                : new Location(armorStand.getWorld(), coordinates.x(), coordinates.y(), coordinates.z());
    }

    public boolean anchoredAt(ArmorStand armorStand, Block block) {
        Coordinates coordinates = coordinates(
                armorStand.getPersistentDataContainer(),
                blockXKey,
                blockYKey,
                blockZKey
        );
        if (coordinates != null) {
            return coordinates.x() == block.getX()
                    && coordinates.y() == block.getY()
                    && coordinates.z() == block.getZ();
        }
        return armorStand.getLocation().distanceSquared(block.getLocation().add(0.5, 0.0, 0.5)) <= 1.0;
    }

    public StoredLight storedLight(ArmorStand armorStand) {
        PersistentDataContainer data = armorStand.getPersistentDataContainer();
        Byte hasLight = data.get(lightKey, PersistentDataType.BYTE);
        if (hasLight == null || hasLight == 0) {
            return null;
        }

        Coordinates coordinates = coordinates(data, lightXKey, lightYKey, lightZKey);
        if (coordinates == null) {
            coordinates = coordinates(data, blockXKey, blockYKey, blockZKey);
        }
        if (coordinates == null) {
            return null;
        }
        return new StoredLight(
                coordinates,
                data.get(lightLevelKey, PersistentDataType.INTEGER)
        );
    }

    public List<FurnitureGeometry.BlockPosition> barrierBlocks(ArmorStand armorStand) {
        String serialized = armorStand.getPersistentDataContainer()
                .get(barrierBlocksKey, PersistentDataType.STRING);
        if (serialized == null || serialized.isBlank()) {
            return List.of();
        }

        List<FurnitureGeometry.BlockPosition> positions = new ArrayList<>();
        for (String encodedPosition : serialized.split(";")) {
            String[] coordinates = encodedPosition.split(",", -1);
            if (coordinates.length != 3) {
                continue;
            }
            try {
                positions.add(new FurnitureGeometry.BlockPosition(
                        Integer.parseInt(coordinates[0]),
                        Integer.parseInt(coordinates[1]),
                        Integer.parseInt(coordinates[2])
                ));
            } catch (NumberFormatException ignored) {
            }
        }
        return List.copyOf(positions);
    }

    private static Coordinates coordinates(
            PersistentDataContainer data,
            NamespacedKey xKey,
            NamespacedKey yKey,
            NamespacedKey zKey
    ) {
        Integer x = data.get(xKey, PersistentDataType.INTEGER);
        Integer y = data.get(yKey, PersistentDataType.INTEGER);
        Integer z = data.get(zKey, PersistentDataType.INTEGER);
        return x == null || y == null || z == null ? null : new Coordinates(x, y, z);
    }

    public record StoredLight(Coordinates coordinates, Integer expectedLevel) {
    }

    public record Coordinates(int x, int y, int z) {
    }
}
