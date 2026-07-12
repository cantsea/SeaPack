package com.seapack.command.subcommand;

import com.seapack.command.CommandContext;
import com.seapack.command.ItemsPermissions;
import com.seapack.command.ItemsSubCommand;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

public final class ReloadSubCommand implements ItemsSubCommand {
    @Override
    public String name() {
        return "reload";
    }

    @Override
    public String permission() {
        return ItemsPermissions.RELOAD;
    }

    @Override
    public boolean execute(CommandContext context, CommandSender sender, String[] args) {
        if (!sender.hasPermission(permission())) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to reload SeaPack items.");
            return true;
        }
        if (context.importManager().isRunning()) {
            sender.sendMessage(ChatColor.YELLOW + "Wait for the ItemsAdder import to finish before reloading.");
            return true;
        }
        if (!context.itemManager().reload()) {
            sender.sendMessage(ChatColor.RED + "SeaPack reload failed validation. The previous items remain active.");
            sender.sendMessage(ChatColor.GRAY + "Check the console for the cache or contents error.");
            return true;
        }

        context.itemMenuManager().closeOpenMenus();
        sender.sendMessage(ChatColor.AQUA + "Reloaded " + ChatColor.WHITE + context.itemManager().items().size()
                + ChatColor.AQUA + " SeaPack items and " + ChatColor.WHITE + context.itemManager().armorRuleCount()
                + ChatColor.AQUA + " armor definitions.");
        sender.sendMessage(ChatColor.GRAY + "Armor scan: " + ChatColor.WHITE + context.itemManager().armorSourceCount()
                + ChatColor.GRAY + " source(s), " + ChatColor.WHITE + context.itemManager().armorDocumentCount()
                + ChatColor.GRAY + " config file(s).");
        sender.sendMessage(ChatColor.GRAY + "Detected " + ChatColor.WHITE + context.itemManager().namespaceCount()
                + ChatColor.GRAY + " namespace(s).");
        sender.sendMessage(ChatColor.GRAY + "Custom item configs: " + ChatColor.WHITE
                + context.itemManager().customConfigCount() + ChatColor.GRAY + " loaded, "
                + ChatColor.WHITE + context.itemManager().customConfigCreatedCount() + ChatColor.GRAY + " created, "
                + ChatColor.WHITE + context.itemManager().customItemEntryCreatedCount()
                + ChatColor.GRAY + " set item entries added.");
        sender.sendMessage(ChatColor.GRAY + "Furniture: " + ChatColor.WHITE
                + context.itemManager().furnitureCount() + ChatColor.GRAY + " placeable item(s), "
                + ChatColor.WHITE + context.itemManager().furnitureConfigItemCreatedCount()
                + ChatColor.GRAY + " new config entries added.");
        context.plugin().getLogger().info(sender.getName() + " reloaded SeaPack items.");
        return true;
    }
}
