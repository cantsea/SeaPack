package com.seapack.feature.furniture;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.ArmorStand;

public final class FurnitureBlocks {
    private final FurniturePersistence persistence;

    public FurnitureBlocks(FurniturePersistence persistence) {
        this.persistence = persistence;
    }

    public boolean canPlaceAt(
            Block anchorBlock,
            List<FurnitureGeometry.BlockPosition> solidBlocks
    ) {
        if (!canUsePlacementBlock(anchorBlock)) {
            return false;
        }
        return solidBlocks.stream()
                .map(position -> anchorBlock.getWorld().getBlockAt(position.x(), position.y(), position.z()))
                .allMatch(FurnitureBlocks::canUsePlacementBlock);
    }

    public Block chooseLightBlock(
            Block anchorBlock,
            List<FurnitureGeometry.BlockPosition> solidBlocks,
            int lightLevel
    ) {
        if (lightLevel <= 0) {
            return anchorBlock;
        }
        Set<FurnitureGeometry.BlockPosition> occupied = new HashSet<>(solidBlocks);
        FurnitureGeometry.BlockPosition anchorPosition = new FurnitureGeometry.BlockPosition(
                anchorBlock.getX(),
                anchorBlock.getY(),
                anchorBlock.getZ()
        );
        if (!occupied.contains(anchorPosition) && canUsePlacementBlock(anchorBlock)) {
            return anchorBlock;
        }
        for (int offset = 1; offset <= 4; offset++) {
            Block candidate = anchorBlock.getRelative(BlockFace.UP, offset);
            FurnitureGeometry.BlockPosition position = new FurnitureGeometry.BlockPosition(
                    candidate.getX(),
                    candidate.getY(),
                    candidate.getZ()
            );
            if (!occupied.contains(position) && canUsePlacementBlock(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    public void ensureSolidBlocks(
            Block anchorBlock,
            List<FurnitureGeometry.BlockPosition> solidBlocks
    ) {
        for (FurnitureGeometry.BlockPosition position : solidBlocks) {
            Block block = anchorBlock.getWorld().getBlockAt(position.x(), position.y(), position.z());
            if (canUsePlacementBlock(block)) {
                block.setType(Material.BARRIER, false);
            }
        }
    }

    public void ensureLight(Block block, int lightLevel) {
        if (lightLevel <= 0 || !canUsePlacementBlock(block)) {
            return;
        }
        if (block.getType() == Material.LIGHT && matchesLightLevel(block, lightLevel)) {
            return;
        }
        placeLight(block, lightLevel);
    }

    public void removeOwnedBlocks(ArmorStand armorStand) {
        removeLight(armorStand);
        removeSolidBlocks(armorStand);
    }

    public boolean isChunkLoaded(Block block) {
        return block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4);
    }

    private void removeLight(ArmorStand armorStand) {
        FurniturePersistence.StoredLight storedLight = persistence.storedLight(armorStand);
        if (storedLight == null) {
            return;
        }
        FurniturePersistence.Coordinates coordinates = storedLight.coordinates();
        Block block = armorStand.getWorld().getBlockAt(coordinates.x(), coordinates.y(), coordinates.z());
        if (block.getType() == Material.LIGHT && matchesLightLevel(block, storedLight.expectedLevel())) {
            block.setType(Material.AIR, false);
        }
    }

    private void removeSolidBlocks(ArmorStand armorStand) {
        for (FurnitureGeometry.BlockPosition position : persistence.barrierBlocks(armorStand)) {
            Block block = armorStand.getWorld().getBlockAt(position.x(), position.y(), position.z());
            if (block.getType() == Material.BARRIER) {
                block.setType(Material.AIR, false);
            }
        }
    }

    private static boolean canUsePlacementBlock(Block block) {
        return block.getType().isAir() || block.getType() == Material.LIGHT;
    }

    private static void placeLight(Block block, int lightLevel) {
        BlockData blockData = Material.LIGHT.createBlockData();
        if (blockData instanceof Levelled levelled) {
            levelled.setLevel(Math.max(levelled.getMinimumLevel(), Math.min(levelled.getMaximumLevel(), lightLevel)));
        }
        block.setBlockData(blockData, false);
    }

    private static boolean matchesLightLevel(Block block, Integer expectedLightLevel) {
        if (expectedLightLevel == null) {
            return true;
        }
        BlockData blockData = block.getBlockData();
        return blockData instanceof Levelled levelled && levelled.getLevel() == expectedLightLevel;
    }
}
