package com.seapack.feature.furniture;

import java.util.function.Function;
import java.util.function.Predicate;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

public final class FurnitureTargeting {
    private static final double DEFAULT_BREAK_REACH = 4.5;
    private static final double BREAK_AIM_RADIUS = 0.9;

    public ArmorStand findTarget(
            Player player,
            Predicate<ArmorStand> furniturePredicate,
            Function<ArmorStand, FurnitureSettings> settingsResolver
    ) {
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().normalize();
        double reach = interactionReach(player);
        double maximumDistance = blockingDistance(player, eye, direction, reach);
        ArmorStand aimedFurniture = null;
        double nearestDistance = Double.MAX_VALUE;

        for (ArmorStand armorStand : player.getWorld().getNearbyEntitiesByType(
                ArmorStand.class,
                eye,
                reach,
                reach,
                reach,
                furniturePredicate
        )) {
            double distance = aimDistance(
                    eye.toVector(),
                    direction,
                    armorStand,
                    reach,
                    settingsResolver.apply(armorStand)
            );
            if (distance < 0.0 || distance > maximumDistance + 0.75 || distance >= nearestDistance) {
                continue;
            }
            nearestDistance = distance;
            aimedFurniture = armorStand;
        }
        return aimedFurniture;
    }

    private static double interactionReach(Player player) {
        AttributeInstance attribute = player.getAttribute(Attribute.ENTITY_INTERACTION_RANGE);
        return attribute == null ? DEFAULT_BREAK_REACH : Math.max(0.0, attribute.getValue());
    }

    private static double blockingDistance(Player player, Location eye, Vector direction, double reach) {
        RayTraceResult result = player.getWorld().rayTraceBlocks(
                eye,
                direction,
                reach,
                FluidCollisionMode.NEVER,
                true
        );
        return result == null || result.getHitPosition() == null
                ? reach
                : eye.toVector().distance(result.getHitPosition());
    }

    private static double aimDistance(
            Vector origin,
            Vector direction,
            ArmorStand armorStand,
            double reach,
            FurnitureSettings settings
    ) {
        if (settings != null) {
            return FurnitureGeometry.rayDistance(
                    origin,
                    direction,
                    armorStand.getLocation(),
                    FurniturePlacement.modelVisualYaw(
                            armorStand.getLocation().getYaw(),
                            settings.modelTransform().rotation().y()
                    ),
                    settings,
                    reach
            );
        }

        Location standLocation = armorStand.getLocation();
        double[] heights = armorStand.isSmall()
                ? new double[]{0.2, 0.5, 0.85}
                : new double[]{0.25, 0.8, 1.45};
        double bestProjection = -1.0;
        for (double height : heights) {
            Vector target = standLocation.toVector().add(new Vector(0.0, height, 0.0));
            Vector offset = target.clone().subtract(origin);
            double projection = offset.dot(direction);
            if (projection < 0.0 || projection > reach) {
                continue;
            }
            Vector closestPoint = origin.clone().add(direction.clone().multiply(projection));
            if (closestPoint.distanceSquared(target) <= BREAK_AIM_RADIUS * BREAK_AIM_RADIUS
                    && (bestProjection < 0.0 || projection < bestProjection)) {
                bestProjection = projection;
            }
        }
        return bestProjection;
    }
}
