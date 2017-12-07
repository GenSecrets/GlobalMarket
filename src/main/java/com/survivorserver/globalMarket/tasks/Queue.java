package com.survivorserver.globalMarket.tasks;

import java.util.Collection;

import org.bukkit.scheduler.BukkitRunnable;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.survivorserver.globalMarket.Listing;
import com.survivorserver.globalMarket.Mail;
import com.survivorserver.globalMarket.Market;
import com.survivorserver.globalMarket.MarketStorage;
import com.survivorserver.globalMarket.QueueItem;

public class Queue extends BukkitRunnable {

    Market market;
    MarketStorage storage;

    public Queue(Market market) {
        this.market = market;
        storage = market.getStorage();
    }

    @Override
    public void run() {
        Collection<QueueItem> expired = Collections2.filter(storage.getQueue(), new Predicate<QueueItem>() {
            @Override
            public boolean apply(QueueItem item) {
                if (item.getMail() != null) {
                    Mail mail = item.getMail();
                    if (((System.currentTimeMillis() - item.getTime()) / 1000) / 60 >= market.getMailTime(mail.getOwner(), mail.getWorld())) {
                        return true;
                    }
                } else {
                    Listing listing = item.getListing();
                    if (((System.currentTimeMillis() - item.getTime()) / 1000) / 60 >= market.getTradeTime(listing.getSeller(), listing.getWorld())
                            && !item.getListing().getSeller().equalsIgnoreCase(market.getInfiniteSeller())) {
                        return true;
                    }
                }
                return false;
            }
        });
        for (QueueItem item : expired) {
            storage.removeItemFromQueue(item.getId());
        }
    }
}
