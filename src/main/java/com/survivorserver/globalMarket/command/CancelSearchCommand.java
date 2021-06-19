package com.survivorserver.globalMarket.command;

import java.util.List;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import com.survivorserver.globalMarket.Listing;
import com.survivorserver.globalMarket.LocaleHandler;
import com.survivorserver.globalMarket.Market;
import com.survivorserver.globalMarket.lib.SearchResult;
import com.survivorserver.globalMarket.lib.SortMethod;

public class CancelSearchCommand extends SubCommand {

    public CancelSearchCommand(Market market, LocaleHandler locale) {
        super(market, locale);
    }

    @Override
    public String getCommand() {
        return "cancelsearch";
    }

    @Override
    public String[] getAliases() {
        return new String[] {"cancelall"};
    }

    @Override
    public String getPermissionNode() {
        return "globalmarket.admin";
    }

    @Override
    public String getHelp() {
        return locale.get("cmd.prefix") + locale.get("cmd.cs_syntax") + " " + locale.get("cmd.cs_descr");
    }

    @Override
    public boolean allowConsoleSender() {
        return true;
    }

    @Override
    public boolean onCommand(CommandSender sender, String[] args) {
        if (args.length == 2) {
            String search = args[1];
            if (search.equalsIgnoreCase("all")) {
                List<Listing> listings = market.getStorage().getAllListings();
                if (listings.size() > 0) {
                    for (Listing listing : listings) {
                        market.getCore().expireListing(listing);
                    }
                    sender.sendMessage(ChatColor.GREEN + "" + listings.size() + " listings cancelled");
                } else {
                    sender.sendMessage(ChatColor.YELLOW + "There are no listings to cancel");
                }
            } else {
                SearchResult res = market.getStorage().getListings(sender.getName(), SortMethod.DEFAULT, args[1], "");
                if (res.getTotalFound() > 0) {
                    for (Listing listing : res.getPage()) {
                        market.getCore().expireListing(listing);
                    }
                    sender.sendMessage(ChatColor.GREEN + "" + res.getTotalFound() + " listings cancelled");
                } else {
                    sender.sendMessage(ChatColor.YELLOW + "No results found for \"" + args[1] + "\"");
                }
            }
        } else {
            return false;
        }
        return true;
    }
}
