package org.seapack;

public record FurnitureModelTransform(
        Values rotation,
        Values translation,
        Values sourceRotation,
        Values sourceTranslation
) {
    public static final FurnitureModelTransform IDENTITY = discovered(Values.ZERO, Values.ZERO);

    public FurnitureModelTransform {
        rotation = rotation == null ? Values.ZERO : rotation;
        translation = translation == null ? Values.ZERO : translation;
        sourceRotation = sourceRotation == null ? Values.ZERO : sourceRotation;
        sourceTranslation = sourceTranslation == null ? Values.ZERO : sourceTranslation;
    }

    public static FurnitureModelTransform discovered(Values rotation, Values translation) {
        Values safeRotation = rotation == null ? Values.ZERO : rotation;
        Values safeTranslation = translation == null ? Values.ZERO : translation;
        return new FurnitureModelTransform(safeRotation, safeTranslation, safeRotation, safeTranslation);
    }

    public FurnitureModelTransform withConfigured(Values configuredRotation, Values configuredTranslation) {
        return new FurnitureModelTransform(
                configuredRotation,
                configuredTranslation,
                sourceRotation,
                sourceTranslation
        );
    }

    public Values rotationDelta() {
        return rotation.subtract(sourceRotation);
    }

    public Values translationDelta() {
        return translation.subtract(sourceTranslation);
    }

    public record Values(double x, double y, double z) {
        public static final Values ZERO = new Values(0.0, 0.0, 0.0);

        public Values {
            x = finiteOrZero(x);
            y = finiteOrZero(y);
            z = finiteOrZero(z);
        }

        public Values subtract(Values other) {
            Values safeOther = other == null ? ZERO : other;
            return new Values(x - safeOther.x, y - safeOther.y, z - safeOther.z);
        }

        private static double finiteOrZero(double value) {
            return Double.isFinite(value) ? value : 0.0;
        }
    }
}
