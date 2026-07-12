package com.seapack.command.subcommand;

import com.seapack.command.CommandContext;
import com.seapack.command.ItemsPermissions;
import com.seapack.command.ItemsSubCommand;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

public final class ImportSubCommand implements ItemsSubCommand {
    @Override
    public String name() {
        return "import";
    }

    @Override
    public String permission() {
        return ItemsPermissions.IMPORT;
    }

    @Override
    public boolean execute(CommandContext context, CommandSender sender, String[] args) {
        if (!sender.hasPermission(permission())) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to import ItemsAdder files.");
            return true;
        }
        return context.importManager().startImport(sender);
    }
}
