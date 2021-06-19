package com.survivorserver.globalMarket;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;

public class TabCompletion implements TabCompleter {
    @Override
    public List<String> onTabComplete(CommandSender commandSender, Command command, String s, String[] strings) {
        return new ArrayList<String>(){{add("listings"); add("cancelall"); add("mail"); add("create"); add("send"); add("pricecheck"); add("mailbox"); add("stall"); add("history"); add("reload");}};
    }
}
