package com.seapack.service;

import org.bukkit.Location;
import org.bukkit.entity.Player;

public interface FurnitureProtection {
    boolean canPlace(Player player, Location location);

    boolean canBreak(Player player, Location location);

    static FurnitureProtection allowAll() {
        return FixedFurnitureProtection.ALLOW_ALL;
    }

    static FurnitureProtection denyAll() {
        return FixedFurnitureProtection.DENY_ALL;
    }

    enum FixedFurnitureProtection implements FurnitureProtection {
        ALLOW_ALL(true),
        DENY_ALL(false);

        private final boolean allowed;

        FixedFurnitureProtection(boolean allowed) {
            this.allowed = allowed;
        }

        @Override
        public boolean canPlace(Player player, Location location) {
            return allowed;
        }

        @Override
        public boolean canBreak(Player player, Location location) {
            return allowed;
        }
    }
}
