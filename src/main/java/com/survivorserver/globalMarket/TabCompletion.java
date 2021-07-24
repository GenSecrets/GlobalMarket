package com.survivorserver.globalMarket;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class TabCompletion implements TabCompleter {
    @Override
    public List<String> onTabComplete(CommandSender commandSender, Command command, String s, String[] strings) {
        if(strings.length == 1){
            return new ArrayList<String>(){{
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
        } else {
            ArrayList<String> numbers = new ArrayList<>();
            ArrayList<String> players = new ArrayList<>();
            for (int i = 1; i <= 5; i++){
                numbers.add(String.valueOf(i));
            }
            for (int i = 10; i <= 100; i+=10){
                numbers.add(String.valueOf(i));
            }
            for (Player p : Market.getMarket().getServer().getOnlinePlayers()){
                players.add(p.getName());
            }

            switch (strings[0]){
                case "listings":
                case "cancelall":
                case "reload":
                case "pricecheck":
                default:
                    return new ArrayList<>();
                case "send":
                case "mail":
                    return players;
                case "history":
                case "create":
                    return numbers;
                case "mailbox":
                case "stall":
                    return new ArrayList<String>(){{ add("remove"); }};
            }
        }
    }
}
