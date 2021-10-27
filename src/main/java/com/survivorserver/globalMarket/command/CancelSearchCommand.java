package com.survivorserver.globalMarket.command;

import java.util.List;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import com.survivorserver.globalMarket.Listing;
import com.survivorserver.globalMarket.LocaleHandler;
import com.survivorserver.globalMarket.Market;
import com.survivorserver.globalMarket.lib.SearchResult;
import com.survivorserver.globalMarket.lib.SortMethod;

public class CancelSearchCommand {
    private final Market market;
    private final LocaleHandler locale;

    public CancelSearchCommand(Market market, LocaleHandler locale) {
        this.market = market;
        this.locale = locale;
    }

    public String getHelp() {
        return locale.get("cmd.prefix") + locale.get("cmd.cs_syntax") + " " + locale.get("cmd.cs_descr");
    }

    public boolean onCommand(CommandSender sender, String[] args) {
        if (args.length == 1) {
            String search = args[0];
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
                SearchResult res = market.getStorage().getListings(sender.getName(), SortMethod.DEFAULT, args[0], "");
                if (res.getTotalFound() > 0) {
                    for (Listing listing : res.getPage()) {
                        market.getCore().expireListing(listing);
                    }
                    sender.sendMessage(ChatColor.GREEN + "" + res.getTotalFound() + " listings cancelled");
                } else {
                    sender.sendMessage(ChatColor.YELLOW + "No results found for \"" + args[0] + "\"");
                }
            }
        } else {
            return false;
        }
        return true;
    }
}
