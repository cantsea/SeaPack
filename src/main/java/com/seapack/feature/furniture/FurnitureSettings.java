package com.seapack.feature.furniture;

import java.util.Locale;

public record FurnitureSettings(
        boolean enabled,
        boolean small,
        boolean gravity,
        boolean fixedRotation,
        int rotationSnap,
        FurnitureModelTransform modelTransform,
        boolean oppositeDirection,
        boolean floorPlacement,
        boolean wallPlacement,
        boolean ceilingPlacement,
        int lightLevel,
        double yOffset,
        boolean dropItem,
        String entityType,
        boolean solid,
        double hitboxHeight,
        double hitboxHeightOffset,
        double hitboxLength,
        double hitboxLengthOffset,
        double hitboxWidth,
        double hitboxWidthOffset
) {
    public static final FurnitureSettings DEFAULT = new FurnitureSettings(
            true,
            false,
            false,
            false,
            45,
            FurnitureModelTransform.IDENTITY,
            false,
            true,
            false,
            false,
            0,
            0.0,
            true,
            "armor_stand",
            false,
            1.0,
            0.0,
            1.0,
            0.0,
            1.0,
            0.0
    );

    public FurnitureSettings {
        rotationSnap = rotationSnap == 90 ? 90 : 45;
        modelTransform = modelTransform == null ? FurnitureModelTransform.IDENTITY : modelTransform;
        lightLevel = Math.max(0, Math.min(15, lightLevel));
        yOffset = finiteOr(yOffset, 0.0);
        entityType = entityType == null || entityType.isBlank()
                ? "armor_stand"
                : entityType.toLowerCase(Locale.ROOT).replace('-', '_');
        if (entityType.equals("armorstand")) {
            entityType = "armor_stand";
        }
        hitboxHeight = dimension(hitboxHeight);
        hitboxLength = dimension(hitboxLength);
        hitboxWidth = dimension(hitboxWidth);
        hitboxHeightOffset = offset(hitboxHeightOffset);
        hitboxLengthOffset = offset(hitboxLengthOffset);
        hitboxWidthOffset = offset(hitboxWidthOffset);
    }

    public boolean hasPlacementSurface() {
        return floorPlacement || wallPlacement || ceilingPlacement;
    }

    public FurnitureSettings withModelTransform(FurnitureModelTransform transform) {
        return new FurnitureSettings(
                enabled,
                small,
                gravity,
                fixedRotation,
                rotationSnap,
                transform,
                oppositeDirection,
                floorPlacement,
                wallPlacement,
                ceilingPlacement,
                lightLevel,
                yOffset,
                dropItem,
                entityType,
                solid,
                hitboxHeight,
                hitboxHeightOffset,
                hitboxLength,
                hitboxLengthOffset,
                hitboxWidth,
                hitboxWidthOffset
        );
    }

    private static double dimension(double value) {
        return Math.max(0.1, Math.min(3.0, finiteOr(value, 1.0)));
    }

    private static double offset(double value) {
        return Math.max(-3.0, Math.min(3.0, finiteOr(value, 0.0)));
    }

    private static double finiteOr(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }
}
