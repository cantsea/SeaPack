package com.seapack.command;

import org.bukkit.command.CommandSender;

public interface ItemsSubCommand {
    String name();

    String permission();

    boolean execute(CommandContext context, CommandSender sender, String[] args);
}
