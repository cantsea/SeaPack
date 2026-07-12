package com.seapack.listener;

import com.seapack.feature.furniture.FurnitureManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;

public final class FurnitureInteractionListener implements Listener {
    private final FurnitureManager furnitureManager;

    public FurnitureInteractionListener(FurnitureManager furnitureManager) {
        this.furnitureManager = furnitureManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        furnitureManager.handleDamage(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onManipulate(PlayerArmorStandManipulateEvent event) {
        furnitureManager.handleManipulation(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwing(PlayerAnimationEvent event) {
        furnitureManager.handleSwing(event);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent event) {
        furnitureManager.handleDeath(event);
    }
}
