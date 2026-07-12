package org.seapack;

import java.util.logging.Level;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class SeaPack extends JavaPlugin {
    private CustomItemRegistry itemRegistry;
    private ItemMenu itemMenu;
    private NamespacedKey itemIdKey;

    @Override
    public void onEnable() {
        getDataFolder().mkdirs();
        saveResource("README.md", true);
        itemIdKey = new NamespacedKey(this, "item_id");
        new ItemsAdderImporter(this).recoverInterruptedImport();

        itemRegistry = new CustomItemRegistry(this);
        itemRegistry.reload();

        itemMenu = new ItemMenu(this, itemRegistry);
        getServer().getPluginManager().registerEvents(itemMenu, this);
        getServer().getPluginManager().registerEvents(
                new FurnitureListener(this, itemRegistry, createFurnitureProtection()),
                this
        );

        ItemsCommand command = new ItemsCommand(this, itemRegistry, itemMenu);
        getCommand("sitems").setExecutor(command);
        getCommand("sitems").setTabCompleter(command);

        getLogger().info("Loaded " + itemRegistry.items().size() + " custom items and "
                + itemRegistry.armorRuleCount() + " armor definitions from "
                + itemRegistry.armorDocumentCount() + " config files across "
                + itemRegistry.namespaceCount() + " namespaces. Custom item configs: "
                + itemRegistry.customConfigCount() + " loaded, "
                + itemRegistry.customConfigCreatedCount() + " created, "
                + itemRegistry.customItemEntryCreatedCount() + " set item entries added. Furniture: "
                + itemRegistry.furnitureCount() + " placeable definitions.");
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
