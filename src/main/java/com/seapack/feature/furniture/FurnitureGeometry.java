package com.seapack.feature.furniture;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.util.Vector;

public final class FurnitureGeometry {
    private static final double EPSILON = 1.0e-7;

    private FurnitureGeometry() {
    }

    public static List<BlockPosition> occupiedBlocks(Location origin, float yaw, FurnitureSettings settings) {
        Bounds bounds = bounds(settings);
        double horizontalRadius = Math.max(settings.hitboxWidth(), settings.hitboxLength()) / 2.0
                + Math.max(Math.abs(settings.hitboxWidthOffset()), Math.abs(settings.hitboxLengthOffset()))
                + 1.5;
        int minimumX = (int) Math.floor(origin.getX() - horizontalRadius);
        int maximumX = (int) Math.floor(origin.getX() + horizontalRadius);
        int minimumZ = (int) Math.floor(origin.getZ() - horizontalRadius);
        int maximumZ = (int) Math.floor(origin.getZ() + horizontalRadius);
        int minimumY = (int) Math.floor(origin.getY() + bounds.minimumY());
        int maximumY = (int) Math.floor(Math.nextDown(origin.getY() + bounds.maximumY()));
        List<BlockPosition> blocks = new ArrayList<>();

        for (int x = minimumX; x <= maximumX; x++) {
            for (int y = minimumY; y <= maximumY; y++) {
                for (int z = minimumZ; z <= maximumZ; z++) {
                    if (containsPoint(
                            origin,
                            yaw,
                            settings,
                            x + 0.5,
                            y + 0.5,
                            z + 0.5
                    )) {
                        blocks.add(new BlockPosition(x, y, z));
                    }
                }
            }
        }
        return List.copyOf(blocks);
    }

    public static boolean containsBlockCenter(
            Location origin,
            float yaw,
            FurnitureSettings settings,
            int blockX,
            int blockY,
            int blockZ
    ) {
        return containsPoint(origin, yaw, settings, blockX + 0.5, blockY + 0.5, blockZ + 0.5);
    }

    public static double rayDistance(
            Vector worldOrigin,
            Vector worldDirection,
            Location furnitureOrigin,
            float yaw,
            FurnitureSettings settings,
            double reach
    ) {
        double radians = Math.toRadians(yaw);
        double cosine = Math.cos(radians);
        double sine = Math.sin(radians);
        double offsetX = worldOrigin.getX() - furnitureOrigin.getX();
        double offsetY = worldOrigin.getY() - furnitureOrigin.getY();
        double offsetZ = worldOrigin.getZ() - furnitureOrigin.getZ();
        Vector localOrigin = new Vector(
                offsetX * cosine + offsetZ * sine,
                offsetY,
                -offsetX * sine + offsetZ * cosine
        );
        Vector localDirection = new Vector(
                worldDirection.getX() * cosine + worldDirection.getZ() * sine,
                worldDirection.getY(),
                -worldDirection.getX() * sine + worldDirection.getZ() * cosine
        );
        Bounds bounds = bounds(settings);
        double[] interval = {0.0, reach};
        if (!clip(localOrigin.getX(), localDirection.getX(), bounds.minimumX(), bounds.maximumX(), interval)
                || !clip(localOrigin.getY(), localDirection.getY(), bounds.minimumY(), bounds.maximumY(), interval)
                || !clip(localOrigin.getZ(), localDirection.getZ(), bounds.minimumZ(), bounds.maximumZ(), interval)) {
            return -1.0;
        }
        return interval[0];
    }

    private static boolean containsPoint(
            Location origin,
            float yaw,
            FurnitureSettings settings,
            double worldX,
            double worldY,
            double worldZ
    ) {
        double radians = Math.toRadians(yaw);
        double cosine = Math.cos(radians);
        double sine = Math.sin(radians);
        double offsetX = worldX - origin.getX();
        double offsetZ = worldZ - origin.getZ();
        double localX = offsetX * cosine + offsetZ * sine;
        double localZ = -offsetX * sine + offsetZ * cosine;
        double localY = worldY - origin.getY();
        Bounds bounds = bounds(settings);
        return localX >= bounds.minimumX() - EPSILON && localX < bounds.maximumX() - EPSILON
                && localY >= bounds.minimumY() - EPSILON && localY < bounds.maximumY() - EPSILON
                && localZ >= bounds.minimumZ() - EPSILON && localZ < bounds.maximumZ() - EPSILON;
    }

    private static boolean clip(double origin, double direction, double minimum, double maximum, double[] interval) {
        if (Math.abs(direction) < EPSILON) {
            return origin >= minimum && origin <= maximum;
        }
        double first = (minimum - origin) / direction;
        double second = (maximum - origin) / direction;
        if (first > second) {
            double swap = first;
            first = second;
            second = swap;
        }
        interval[0] = Math.max(interval[0], first);
        interval[1] = Math.min(interval[1], second);
        return interval[0] <= interval[1] && interval[1] >= 0.0;
    }

    private static Bounds bounds(FurnitureSettings settings) {
        double halfWidth = settings.hitboxWidth() / 2.0;
        double halfLength = settings.hitboxLength() / 2.0;
        return new Bounds(
                settings.hitboxWidthOffset() - halfWidth,
                settings.hitboxWidthOffset() + halfWidth,
                settings.hitboxHeightOffset(),
                settings.hitboxHeightOffset() + settings.hitboxHeight(),
                settings.hitboxLengthOffset() - halfLength,
                settings.hitboxLengthOffset() + halfLength
        );
    }

    public record BlockPosition(int x, int y, int z) {
    }

    private record Bounds(
            double minimumX,
            double maximumX,
            double minimumY,
            double maximumY,
            double minimumZ,
            double maximumZ
    ) {
    }
}
