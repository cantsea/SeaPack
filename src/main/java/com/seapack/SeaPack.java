package com.seapack;

import com.seapack.command.ItemsCommand;
import com.seapack.command.CommandContext;
import com.seapack.feature.importer.ItemsAdderImportManager;
import com.seapack.feature.furniture.FurnitureManager;
import com.seapack.feature.item.ItemManager;
import com.seapack.feature.menu.ItemMenuManager;
import com.seapack.listener.FurnitureInteractionListener;
import com.seapack.listener.FurniturePlacementListener;
import com.seapack.listener.ItemMenuListener;
import com.seapack.service.FurnitureProtection;
import com.seapack.service.WorldGuardFurnitureProtection;
import java.util.logging.Level;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class SeaPack extends JavaPlugin {
    private ItemManager itemManager;
    private ItemMenuManager itemMenuManager;
    private NamespacedKey itemIdKey;

    @Override
    public void onEnable() {
        getDataFolder().mkdirs();
        saveResource("README.md", true);
        itemIdKey = new NamespacedKey(this, "item_id");
        ItemsAdderImportManager importManager = new ItemsAdderImportManager(this);
        importManager.recoverInterruptedImport();

        itemManager = new ItemManager(this);
        itemManager.reload();

        itemMenuManager = new ItemMenuManager(this, itemManager);
        getServer().getPluginManager().registerEvents(new ItemMenuListener(itemMenuManager), this);
        FurnitureManager furnitureManager = new FurnitureManager(this, itemManager, createFurnitureProtection());
        getServer().getPluginManager().registerEvents(new FurniturePlacementListener(furnitureManager), this);
        getServer().getPluginManager().registerEvents(new FurnitureInteractionListener(furnitureManager), this);

        ItemsCommand command = new ItemsCommand(new CommandContext(
                this,
                itemManager,
                itemMenuManager,
                importManager
        ));
        getCommand("sitems").setExecutor(command);
        getCommand("sitems").setTabCompleter(command);

        getLogger().info("Loaded " + itemManager.items().size() + " custom items and "
                + itemManager.armorRuleCount() + " armor definitions from "
                + itemManager.armorDocumentCount() + " config files across "
                + itemManager.namespaceCount() + " namespaces. Custom item configs: "
                + itemManager.customConfigCount() + " loaded, "
                + itemManager.customConfigCreatedCount() + " created, "
                + itemManager.customItemEntryCreatedCount() + " set item entries added. Furniture: "
                + itemManager.furnitureCount() + " placeable definitions.");
    }

    @Override
    public void onDisable() {
    }

    public NamespacedKey itemIdKey() {
        return itemIdKey;
    }

    private FurnitureProtection createFurnitureProtection() {
        Plugin worldGuard = getServer().getPluginManager().getPlugin("WorldGuard");
        if (worldGuard == null || !worldGuard.isEnabled()) {
            return FurnitureProtection.allowAll();
        }

        try {
            FurnitureProtection protection = new WorldGuardFurnitureProtection();
            getLogger().info("WorldGuard furniture place and break protection is active.");
            return protection;
        } catch (LinkageError | RuntimeException exception) {
            getLogger().log(
                    Level.SEVERE,
                    "WorldGuard was detected but its furniture protection hook could not start. "
                            + "Furniture placement and breaking will remain disabled.",
                    exception
            );
            return FurnitureProtection.denyAll();
        }
    }
}
