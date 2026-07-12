package com.seapack.command.subcommand;

import com.seapack.command.CommandContext;
import com.seapack.command.ItemsPermissions;
import com.seapack.command.ItemsSubCommand;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class SearchSubCommand implements ItemsSubCommand {
    @Override
    public String name() {
        return "search";
    }

    @Override
    public String permission() {
        return ItemsPermissions.MENU;
    }

    @Override
    public boolean execute(CommandContext context, CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can open the item menu.");
            return true;
        }
        if (!player.hasPermission(permission())) {
            player.sendMessage(ChatColor.RED + "You do not have permission to open this menu.");
            return true;
        }
        if (args.length == 0) {
            context.itemMenuManager().open(player);
            return true;
        }

        String query = String.join(" ", args);
        context.itemMenuManager().open(player, context.itemManager().search(query), 0, query);
        return true;
    }
}
