package com.seapack.listener;

import com.seapack.feature.furniture.FurnitureManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;

public final class FurniturePlacementListener implements Listener {
    private final FurnitureManager furnitureManager;

    public FurniturePlacementListener(FurnitureManager furnitureManager) {
        this.furnitureManager = furnitureManager;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlace(PlayerInteractEvent event) {
        furnitureManager.handlePlacement(event);
    }
}
