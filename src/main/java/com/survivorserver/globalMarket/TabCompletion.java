package com.survivorserver.globalMarket;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TabCompletion implements TabCompleter {
    private static final ArrayList<String> COMMANDS = new ArrayList<String>(){{
        add("listings");
        add("cancelall");
        add("mail");
        add("create");
        add("send");
        add("pricecheck");
        add("mailbox");
        add("stall");
        add("history");
        add("reload");}};

    private static final ArrayList<String> REMOVE = new ArrayList<String>(){{
        add("remove");}};

    private static final ArrayList<String> NUMBERS = new ArrayList<String>(){{
        for(int i = 1; i <=1000; i++) {
            add(String.valueOf(i));
        }
    }};

    private static final ArrayList<String> PLAYERS = new ArrayList<String>(){{
        for(Player p : Market.getMarket().getServer().getOnlinePlayers()) {
            add(p.getName());
        }
    }};

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        final List<String> completions = new ArrayList<>();
        if(args.length > 1 && (args[0].equalsIgnoreCase("create") || args[0].equalsIgnoreCase("history"))) {
            StringUtil.copyPartialMatches(args[0], NUMBERS, completions);
            Collections.sort(completions);
            return completions;
        } else if (args.length > 1 && (args[0].equalsIgnoreCase("mail") || args[0].equalsIgnoreCase("send"))) {
            StringUtil.copyPartialMatches(args[0], PLAYERS, completions);
            Collections.sort(completions);
            return completions;
        } else if (args.length > 1 && (args[0].equalsIgnoreCase("mailbox") || args[0].equalsIgnoreCase("stall"))) {
            StringUtil.copyPartialMatches(args[0], REMOVE, completions);
            Collections.sort(completions);
            return completions;
        } else {
            StringUtil.copyPartialMatches(args[0], COMMANDS, completions);
            Collections.sort(completions);
            return completions;
        }
    }
}
