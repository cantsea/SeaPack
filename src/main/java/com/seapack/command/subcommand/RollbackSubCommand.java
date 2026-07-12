package com.seapack.command.subcommand;

import com.seapack.command.CommandContext;
import com.seapack.command.ItemsPermissions;
import com.seapack.command.ItemsSubCommand;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

public final class RollbackSubCommand implements ItemsSubCommand {
    @Override
    public String name() {
        return "rollback";
    }

    @Override
    public String permission() {
        return ItemsPermissions.IMPORT;
    }

    @Override
    public boolean execute(CommandContext context, CommandSender sender, String[] args) {
        if (!sender.hasPermission(permission())) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to roll back ItemsAdder files.");
            return true;
        }
        return context.importManager().startRollback(sender);
    }
}
