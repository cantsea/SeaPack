package com.seapack.listener;

import com.seapack.feature.menu.ItemMenuManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

public final class ItemMenuListener implements Listener {
    private final ItemMenuManager itemMenuManager;

    public ItemMenuListener(ItemMenuManager itemMenuManager) {
        this.itemMenuManager = itemMenuManager;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        itemMenuManager.handleClick(event);
    }
}
