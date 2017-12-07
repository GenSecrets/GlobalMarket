package com.survivorserver.globalMarket.command;

import org.bukkit.command.CommandSender;

import com.survivorserver.globalMarket.LocaleHandler;
import com.survivorserver.globalMarket.Market;

public abstract class SubCommand {

    protected Market market;
    protected LocaleHandler locale;

    public SubCommand(Market market, LocaleHandler locale) {
        this.market = market;
        this.locale = locale;
    }

    public abstract String getCommand();

    public abstract String[] getAliases();

    public abstract String getPermissionNode();

    public abstract String getHelp();

    public abstract boolean allowConsoleSender();

    public abstract boolean onCommand(CommandSender sender, String[] args);
}
