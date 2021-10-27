package com.survivorserver.globalMarket.command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import org.bukkit.command.CommandSender;

@CommandAlias("market")
@CommandPermission("")
public class MarketCommandHandler extends BaseCommand {

    @Default
    public void onMarketCommand(final CommandSender commandSender) {

    }
}
