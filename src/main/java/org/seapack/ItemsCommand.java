package org.seapack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class ItemsCommand implements CommandExecutor, TabCompleter {
    private final SeaPack plugin;
    private final CustomItemRegistry itemRegistry;
    private final ItemMenu itemMenu;
    private final ItemsAdderImporter itemsAdderImporter;
    private final AtomicBoolean importRunning = new AtomicBoolean();

    public ItemsCommand(SeaPack plugin, CustomItemRegistry itemRegistry, ItemMenu itemMenu) {
        this.plugin = plugin;
        this.itemRegistry = itemRegistry;
        this.itemMenu = itemMenu;
        this.itemsAdderImporter = new ItemsAdderImporter(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("import")) {
            return startImport(sender);
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("rollback")) {
            return startRollback(sender);
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("seapack.items.reload")) {
                sender.sendMessage(ChatColor.RED + "You do not have permission to reload SeaPack items.");
                return true;
            }
            if (importRunning.get()) {
                sender.sendMessage(ChatColor.YELLOW + "Wait for the ItemsAdder import to finish before reloading.");
                return true;
            }

            if (!itemRegistry.reload()) {
                sender.sendMessage(ChatColor.RED + "SeaPack reload failed validation. The previous items remain active.");
                sender.sendMessage(ChatColor.GRAY + "Check the console for the cache or contents error.");
                return true;
            }
            itemMenu.closeOpenMenus();
            sender.sendMessage(ChatColor.AQUA + "Reloaded " + ChatColor.WHITE + itemRegistry.items().size()
                    + ChatColor.AQUA + " SeaPack items and " + ChatColor.WHITE + itemRegistry.armorRuleCount()
                    + ChatColor.AQUA + " armor definitions.");
            sender.sendMessage(ChatColor.GRAY + "Armor scan: " + ChatColor.WHITE + itemRegistry.armorSourceCount()
                    + ChatColor.GRAY + " source(s), " + ChatColor.WHITE + itemRegistry.armorDocumentCount()
                    + ChatColor.GRAY + " config file(s).");
            sender.sendMessage(ChatColor.GRAY + "Detected " + ChatColor.WHITE + itemRegistry.namespaceCount()
                    + ChatColor.GRAY + " namespace(s).");
            sender.sendMessage(ChatColor.GRAY + "Custom item configs: " + ChatColor.WHITE
                    + itemRegistry.customConfigCount() + ChatColor.GRAY + " loaded, "
                    + ChatColor.WHITE + itemRegistry.customConfigCreatedCount() + ChatColor.GRAY + " created, "
                    + ChatColor.WHITE + itemRegistry.customItemEntryCreatedCount()
                    + ChatColor.GRAY + " set item entries added.");
            sender.sendMessage(ChatColor.GRAY + "Furniture: " + ChatColor.WHITE
                    + itemRegistry.furnitureCount() + ChatColor.GRAY + " placeable item(s), "
                    + ChatColor.WHITE + itemRegistry.furnitureConfigItemCreatedCount()
                    + ChatColor.GRAY + " new config entries added.");
            plugin.getLogger().info(sender.getName() + " reloaded SeaPack items.");
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can open the item menu.");
            return true;
        }

        if (!player.hasPermission("seapack.items.menu")) {
            player.sendMessage(ChatColor.RED + "You do not have permission to open this menu.");
            return true;
        }

        if (args.length == 0) {
            itemMenu.open(player);
            return true;
        }

        String query = String.join(" ", args);
        itemMenu.open(player, itemRegistry.search(query), 0, query);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> suggestions = new ArrayList<>();
        String input = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        if (args.length == 1 && sender.hasPermission("seapack.items.reload") && "reload".startsWith(input)) {
            suggestions.add("reload");
        }
        if (args.length == 1 && sender.hasPermission("seapack.items.import") && "import".startsWith(input)) {
            suggestions.add("import");
        }
        if (args.length == 1 && sender.hasPermission("seapack.items.import") && "rollback".startsWith(input)) {
            suggestions.add("rollback");
        }
        return suggestions;
    }

    private boolean startImport(CommandSender sender) {
        if (!sender.hasPermission("seapack.items.import")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to import ItemsAdder files.");
            return true;
        }
        if (!importRunning.compareAndSet(false, true)) {
            sender.sendMessage(ChatColor.YELLOW + "An ItemsAdder import is already running.");
            return true;
        }

        sender.sendMessage(ChatColor.AQUA + "Importing ItemsAdder contents and item cache...");
        String senderName = sender.getName();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                ItemsAdderImporter.ImportResult result = itemsAdderImporter.importCurrentFiles();
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    importRunning.set(false);
                    sender.sendMessage(ChatColor.AQUA + "Imported " + ChatColor.WHITE
                            + result.copiedFileCount() + ChatColor.AQUA + " file(s) from "
                            + ChatColor.WHITE + result.itemsAdderFolder() + ChatColor.AQUA + ".");
                    sender.sendMessage(ChatColor.GRAY + "Cache selected: " + ChatColor.WHITE
                            + result.sourceCache().getFileName());
                    sender.sendMessage(ChatColor.GRAY + "Validated " + ChatColor.WHITE
                            + result.cacheItemCount() + ChatColor.GRAY + " item mappings and "
                            + ChatColor.WHITE + result.configFileCount() + ChatColor.GRAY + " config files.");
                    sender.sendMessage(ChatColor.YELLOW + "Run " + ChatColor.WHITE
                            + "/sitems reload" + ChatColor.YELLOW + " to load the changes.");
                    plugin.getLogger().info(senderName + " imported ItemsAdder files into SeaPack.");
                });
            } catch (Exception exception) {
                plugin.getLogger().log(Level.SEVERE, senderName + " could not import ItemsAdder files.", exception);
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    importRunning.set(false);
                    sender.sendMessage(ChatColor.RED + "ItemsAdder import failed: " + exception.getMessage());
                    sender.sendMessage(ChatColor.GRAY + "SeaPack kept the previous imported files when possible. Check the console for details.");
                });
            }
        });
        return true;
    }

    private boolean startRollback(CommandSender sender) {
        if (!sender.hasPermission("seapack.items.import")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to roll back ItemsAdder files.");
            return true;
        }
        if (!importRunning.compareAndSet(false, true)) {
            sender.sendMessage(ChatColor.YELLOW + "An ItemsAdder file operation is already running.");
            return true;
        }

        sender.sendMessage(ChatColor.AQUA + "Restoring the previous imported contents and cache...");
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                itemsAdderImporter.rollback();
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    importRunning.set(false);
                    sender.sendMessage(ChatColor.AQUA + "Restored the previous ItemsAdder import.");
                    sender.sendMessage(ChatColor.YELLOW + "Run " + ChatColor.WHITE
                            + "/sitems reload" + ChatColor.YELLOW + " to load the restored files.");
                });
            } catch (Exception exception) {
                plugin.getLogger().log(Level.SEVERE, sender.getName() + " could not roll back ItemsAdder files.", exception);
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    importRunning.set(false);
                    sender.sendMessage(ChatColor.RED + "ItemsAdder rollback failed: " + exception.getMessage());
                });
            }
        });
        return true;
    }
}
