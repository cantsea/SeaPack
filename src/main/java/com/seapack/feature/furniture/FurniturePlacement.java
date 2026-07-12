package com.seapack.feature.furniture;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.util.EulerAngle;

public final class FurniturePlacement {
    private FurniturePlacement() {
    }

    public static ResolvedPlacement resolve(
            PlayerInteractEvent event,
            Player player,
            FurnitureSettings settings
    ) {
        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) {
            return null;
        }
        BlockFace face = event.getBlockFace();
        boolean wall = face == BlockFace.NORTH || face == BlockFace.SOUTH
                || face == BlockFace.EAST || face == BlockFace.WEST;
        boolean allowed = face == BlockFace.UP && settings.floorPlacement()
                || face == BlockFace.DOWN && settings.ceilingPlacement()
                || wall && settings.wallPlacement();
        if (!allowed) {
            return null;
        }

        Block anchorBlock = clickedBlock.getRelative(face);
        Location location = anchorBlock.getLocation().add(0.5, settings.yOffset(), 0.5);
        float yaw = facingPositionYaw(location, player.getLocation(), player.getLocation().getYaw());
        if (settings.fixedRotation()) {
            yaw = snapYaw(yaw, settings.rotationSnap());
        }
        if (settings.oppositeDirection()) {
            yaw += 180.0f;
        }
        yaw = normalizeYaw(yaw);
        FurnitureModelTransform transform = settings.modelTransform();
        float entityYaw = modelEntityYaw(yaw, transform.sourceRotation().y());
        float visualYaw = modelVisualYaw(entityYaw, transform.rotation().y());
        applyModelTranslationOverride(location, visualYaw, transform.translationDelta());
        location.setYaw(entityYaw);
        return new ResolvedPlacement(anchorBlock, location, entityYaw, visualYaw);
    }

    public static float facingPositionYaw(Location anchor, Location player, float fallbackPlayerYaw) {
        double relativeX = player.getX() - anchor.getX();
        double relativeZ = player.getZ() - anchor.getZ();
        if (relativeX * relativeX + relativeZ * relativeZ < 1.0E-8) {
            return fallbackPlayerYaw + 180.0f;
        }
        float yaw = (float) Math.toDegrees(Math.atan2(-relativeX, relativeZ));
        return yaw == 0.0f ? 0.0f : yaw;
    }

    public static float snapYaw(float yaw, int rotationSnap) {
        int step = rotationSnap == 90 ? 90 : 45;
        return normalizeYaw(Math.round(yaw / step) * (float) step);
    }

    public static float modelEntityYaw(float desiredVisualYaw, double sourceModelYaw) {
        return normalizeYaw((float) (desiredVisualYaw - sourceModelYaw));
    }

    public static float modelVisualYaw(float entityYaw, double configuredModelYaw) {
        return normalizeYaw((float) (entityYaw + configuredModelYaw));
    }

    public static void applyModelTranslationOverride(
            Location location,
            float visualYaw,
            FurnitureModelTransform.Values translationDelta
    ) {
        double localX = translationDelta.x() / 16.0;
        double localY = translationDelta.y() / 16.0;
        double localZ = translationDelta.z() / 16.0;
        double radians = Math.toRadians(visualYaw);
        double sine = Math.sin(radians);
        double cosine = Math.cos(radians);
        location.add(
                localX * cosine - localZ * sine,
                localY,
                localX * sine + localZ * cosine
        );
    }

    public static EulerAngle headPose(FurnitureModelTransform transform) {
        FurnitureModelTransform.Values delta = transform.rotationDelta();
        return new EulerAngle(
                Math.toRadians(delta.x()),
                Math.toRadians(delta.y()),
                Math.toRadians(delta.z())
        );
    }

    private static float normalizeYaw(float yaw) {
        float normalized = yaw % 360.0f;
        if (normalized > 180.0f) {
            normalized -= 360.0f;
        } else if (normalized <= -180.0f) {
            normalized += 360.0f;
        }
        return normalized == 0.0f ? 0.0f : normalized;
    }

    public record ResolvedPlacement(
            Block anchorBlock,
            Location location,
            float entityYaw,
            float visualYaw
    ) {
    }
}
