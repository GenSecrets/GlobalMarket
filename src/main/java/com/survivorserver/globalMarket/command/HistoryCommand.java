package com.survivorserver.globalMarket.command;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import com.survivorserver.globalMarket.LocaleHandler;
import com.survivorserver.globalMarket.Market;
import com.survivorserver.globalMarket.sql.Database;

public class HistoryCommand extends SubCommand {

    public HistoryCommand(final Market market, final LocaleHandler locale) {
        super(market, locale);
    }

    @Override
    public String getCommand() {
        return "history";
    }

    @Override
    public String[] getAliases() {
        return null;
    }

    @Override
    public String getPermissionNode() {
        return "globalmarket.history";
    }

    @Override
    public String getHelp() {
        return locale.get("cmd.prefix") + locale.get("cmd.history_syntax") + ' ' + locale.get("cmd.history_descr");
    }

    @Override
    public boolean allowConsoleSender() {
        return false;
    }

    @Override
    @SuppressWarnings("TypeMayBeWeakened")
    public boolean onCommand(final CommandSender sender, final String[] args) {
        if (!market.enableHistory()) {
            sender.sendMessage(ChatColor.RED + locale.get("history_not_enabled"));
            return true;
        }
        String name = sender.getName();
        OfflinePlayer oPlayer = (Player) sender;
        if(args.length != 0) {
            name = args[0];
            oPlayer = Bukkit.getPlayer(name);
        }
        if(oPlayer == null) {
            return false;
        }
        final String finalName = name;
        new BukkitRunnable() {
            public void run() {
                final Database db = market.getConfigHandler().createConnection();
                db.connect();
                sender.sendMessage(market.getHistory().buildHistory(finalName, 15, db));
                db.close();
            }
        }.runTaskAsynchronously(market);
        return true;
    }
}
