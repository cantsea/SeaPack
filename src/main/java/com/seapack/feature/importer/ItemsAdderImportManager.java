package com.seapack.feature.importer;

import com.seapack.SeaPack;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

public final class ItemsAdderImportManager {
    private final SeaPack plugin;
    private final ItemsAdderImporter importer;
    private final AtomicBoolean running = new AtomicBoolean();

    public ItemsAdderImportManager(SeaPack plugin) {
        this.plugin = plugin;
        this.importer = new ItemsAdderImporter(plugin);
    }

    public boolean isRunning() {
        return running.get();
    }

    public void recoverInterruptedImport() {
        importer.recoverInterruptedImport();
    }

    public boolean startImport(CommandSender sender) {
        if (!running.compareAndSet(false, true)) {
            sender.sendMessage(ChatColor.YELLOW + "An ItemsAdder import is already running.");
            return true;
        }

        sender.sendMessage(ChatColor.AQUA + "Importing ItemsAdder contents and item cache...");
        String senderName = sender.getName();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                ItemsAdderImporter.ImportResult result = importer.importCurrentFiles();
                plugin.getServer().getScheduler().runTask(plugin, () -> completeImport(sender, senderName, result));
            } catch (Exception exception) {
                plugin.getLogger().log(Level.SEVERE, senderName + " could not import ItemsAdder files.", exception);
                plugin.getServer().getScheduler().runTask(plugin, () -> failImport(sender, exception));
            }
        });
        return true;
    }

    public boolean startRollback(CommandSender sender) {
        if (!running.compareAndSet(false, true)) {
            sender.sendMessage(ChatColor.YELLOW + "An ItemsAdder file operation is already running.");
            return true;
        }

        sender.sendMessage(ChatColor.AQUA + "Restoring the previous imported contents and cache...");
        String senderName = sender.getName();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                importer.rollback();
                plugin.getServer().getScheduler().runTask(plugin, () -> completeRollback(sender));
            } catch (Exception exception) {
                plugin.getLogger().log(Level.SEVERE, senderName + " could not roll back ItemsAdder files.", exception);
                plugin.getServer().getScheduler().runTask(plugin, () -> failRollback(sender, exception));
            }
        });
        return true;
    }

    private void completeImport(
            CommandSender sender,
            String senderName,
            ItemsAdderImporter.ImportResult result
    ) {
        running.set(false);
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
    }

    private void failImport(CommandSender sender, Exception exception) {
        running.set(false);
        sender.sendMessage(ChatColor.RED + "ItemsAdder import failed: " + exception.getMessage());
        sender.sendMessage(ChatColor.GRAY
                + "SeaPack kept the previous imported files when possible. Check the console for details.");
    }

    private void completeRollback(CommandSender sender) {
        running.set(false);
        sender.sendMessage(ChatColor.AQUA + "Restored the previous ItemsAdder import.");
        sender.sendMessage(ChatColor.YELLOW + "Run " + ChatColor.WHITE
                + "/sitems reload" + ChatColor.YELLOW + " to load the restored files.");
    }

    private void failRollback(CommandSender sender, Exception exception) {
        running.set(false);
        sender.sendMessage(ChatColor.RED + "ItemsAdder rollback failed: " + exception.getMessage());
    }
}
