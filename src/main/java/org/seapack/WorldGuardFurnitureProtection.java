package org.seapack;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.Location;
import org.bukkit.entity.Player;

final class WorldGuardFurnitureProtection implements FurnitureProtection {
    @Override
    public boolean canPlace(Player player, Location location) {
        return testBuild(player, location, Flags.BLOCK_PLACE);
    }

    @Override
    public boolean canBreak(Player player, Location location) {
        return testBuild(player, location, Flags.BLOCK_BREAK);
    }

    private static boolean testBuild(Player player, Location location, StateFlag flag) {
        if (location.getWorld() == null) {
            return false;
        }

        WorldGuard worldGuard = WorldGuard.getInstance();
        LocalPlayer localPlayer = WorldGuardPlugin.inst().wrapPlayer(player);
        com.sk89q.worldedit.world.World world = BukkitAdapter.adapt(location.getWorld());
        if (worldGuard.getPlatform().getSessionManager().hasBypass(localPlayer, world)) {
            return true;
        }

        RegionQuery query = worldGuard.getPlatform().getRegionContainer().createQuery();
        return query.testBuild(BukkitAdapter.adapt(location), localPlayer, flag);
    }
}
