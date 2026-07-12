package com.seapack.command;

import com.seapack.command.subcommand.ImportSubCommand;
import com.seapack.command.subcommand.ReloadSubCommand;
import com.seapack.command.subcommand.RollbackSubCommand;
import com.seapack.command.subcommand.SearchSubCommand;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

public final class ItemsCommand implements CommandExecutor, TabCompleter {
    private final CommandContext context;
    private final Map<String, ItemsSubCommand> subcommands;
    private final SearchSubCommand searchSubCommand;

    public ItemsCommand(CommandContext context) {
        this.context = context;
        this.searchSubCommand = new SearchSubCommand();
        this.subcommands = new LinkedHashMap<>();
        register(new ReloadSubCommand());
        register(new ImportSubCommand());
        register(new RollbackSubCommand());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            ItemsSubCommand subcommand = subcommands.get(args[0].toLowerCase(Locale.ROOT));
            if (subcommand != null) {
                return subcommand.execute(context, sender, new String[0]);
            }
        }
        return searchSubCommand.execute(context, sender, args);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String input = args[0].toLowerCase(Locale.ROOT);
        return subcommands.values().stream()
                .filter(subcommand -> sender.hasPermission(subcommand.permission()))
                .map(ItemsSubCommand::name)
                .filter(name -> name.startsWith(input))
                .toList();
    }

    private void register(ItemsSubCommand subcommand) {
        subcommands.put(subcommand.name(), subcommand);
    }
}
