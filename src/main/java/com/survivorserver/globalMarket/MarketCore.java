package com.survivorserver.globalMarket;

import com.survivorserver.globalMarket.HistoryHandler.MarketAction;
import com.survivorserver.globalMarket.lib.cauldron.CauldronHelper;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import net.milkbowl.vault.economy.EconomyResponse.ResponseType;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Collections;

@SuppressWarnings({"deprecation", "unused"})
public class MarketCore {

    Market market;
    InterfaceHandler handler;
    MarketStorage storage;

    public MarketCore(final Market market, final InterfaceHandler handler, final MarketStorage storage) {
        this.market = market;
        this.handler = handler;
        this.storage = storage;
    }

    public boolean buyListing(final Listing listing, final Player player, final InterfaceViewer viewer, final boolean removeListing, final boolean mailItem, final boolean refreshInterface) {
        final double originalPrice = listing.getPrice();
        double cutPrice = originalPrice;
        final Economy econ = market.getEcon();
        final String seller = listing.getSeller();
        final String infAccount = market.getInfiniteAccount();
        final boolean isInfinite = listing.getSeller().equalsIgnoreCase(market.getInfiniteSeller());
        final String buyer = player.getName();
        final double cut = market.getCut(listing.getPrice(), listing.getSeller(), listing.getWorld());
        if (cut > 0) {
            cutPrice = originalPrice - cut;
        }
        final ItemStack item = viewer.getInterface().getItemStack(viewer, listing);
        // Make the transaction between buyer and seller
        EconomyResponse response = econ.withdrawPlayer(buyer, originalPrice);
        if (!response.transactionSuccess()) {
            if (response.type == ResponseType.NOT_IMPLEMENTED) {
                market.log.severe(econ.getName() + " may not be compatible with globalMarket. It does not support the withdrawPlayer() function.");
            } else {
                market.log.severe("Recieved failed economy response from " + econ.getName() + ": " + response.errorMessage);
            }
            return false;
        }
        if (isInfinite && infAccount.length() >= 1) {
            // Put the money earned in the infinite seller's account
            response = econ.depositPlayer(infAccount, cutPrice);
            if (!response.transactionSuccess()) {
                if (response.type == ResponseType.NOT_IMPLEMENTED) {
                    market.log.severe(econ.getName() + " may not be compatible with globalMarket. It does not support the depositPlayer() function.");
                } else {
                    market.log.severe("Recieved failed economy response from " + econ.getName() + ": " + response.errorMessage);
                }
                return false;
            }
        } else {
            // Direct deposit?
            if (market.autoPayment()) {
                response = econ.depositPlayer(seller, cutPrice);
                if (!response.transactionSuccess()) {
                    if (response.type == ResponseType.NOT_IMPLEMENTED) {
                        market.log.severe(econ.getName() + " may not be compatible with globalMarket. It does not support the depositPlayer() function.");
                    } else {
                        market.log.severe("Recieved failed economy response from " + econ.getName() + ": " + response.errorMessage);
                    }
                    return false;
                }
            } else {
                // Send a Transaction Log
                storage.storePayment(item, seller, buyer, originalPrice, cutPrice, cut, listing.getWorld());
            }
            // Seller's stats
            if (market.enableHistory()) {
                market.getHistory().storeHistory(seller, buyer, MarketAction.LISTING_SOLD, listing.getItemId(), listing.getAmount(), originalPrice);
                market.getHistory().incrementEarned(seller, cutPrice);
                market.getHistory().incrementSpent(buyer, originalPrice);
                market.getHistory().storeHistory(buyer, seller, MarketAction.LISTING_BOUGHT, listing.getItemId(), listing.getAmount(), originalPrice);
            }
        }
        // Transfer the item to where it belongs
        if (mailItem) {
            final int mailTime = market.getMailTime(player);
            if (mailTime > 0 && market.queueOnBuy() && !player.hasPermission("globalmarket.noqueue")) {
                storage.queueMail(buyer, null, listing.getItemId(), listing.getAmount(), listing.getWorld());
                player.sendMessage(ChatColor.GREEN + market.getLocale().get("item_will_send", mailTime));
            } else {
                storage.createMail(buyer, null, listing.getItemId(), listing.getAmount(), listing.getWorld());
            }
        }
        if (!isInfinite && removeListing) {
            storage.removeListing(listing.getId());
        }
        final String itemName = market.getItemName(item);
        market.notifyPlayer(seller, market.autoPayment() ? market.getLocale().get("you_sold_your_listing", itemName) :
            market.getLocale().get("listing_purchased_mailbox", itemName));
        market.notifyPlayer(buyer, market.getLocale().get("you_have_new_mail"));
        // Update viewers
        if (refreshInterface) {
            handler.updateAllViewers();
        }
        return true;
    }

    public synchronized boolean buyListing(final Listing listing, final String buyer, final boolean removeListing, final boolean refreshInterface) {
        final double originalPrice = listing.getPrice();
        double cutPrice = originalPrice;
        final Economy econ = market.getEcon();
        final String seller = listing.getSeller();
        final String infAccount = market.getInfiniteAccount();
        final boolean isInfinite = listing.getSeller().equalsIgnoreCase(market.getInfiniteSeller());
        final double cut = market.getCut(listing.getPrice(), listing.getSeller(), listing.getWorld());
        if (cut > 0) {
            cutPrice = originalPrice - cut;
        }
        final ItemStack item = storage.getItem(listing.getItemId(), listing.getAmount());
        if (!econ.has(buyer, listing.getPrice())) {
            return false;
        }
        // Make the transaction between buyer and seller
        EconomyResponse response = econ.withdrawPlayer(buyer, originalPrice);
        if (!response.transactionSuccess()) {
            if (response.type == ResponseType.NOT_IMPLEMENTED) {
                market.log.severe(econ.getName() + " may not be compatible with globalMarket. It does not support the withdrawPlayer() function.");
            }
            return false;
        }
        if (isInfinite && infAccount.length() >= 1) {
            // Put the money earned in the infinite seller's account
            response = econ.depositPlayer(infAccount, cutPrice);
            if (!response.transactionSuccess()) {
                if (response.type == ResponseType.NOT_IMPLEMENTED) {
                    market.log.severe(econ.getName() + " may not be compatible with globalMarket. It does not support the depositPlayer() function.");
                }
                return false;
            }
        } else {
            // Direct deposit?
            if (market.autoPayment()) {
                response = econ.depositPlayer(seller, cutPrice);
                if (!response.transactionSuccess()) {
                    if (response.type == ResponseType.NOT_IMPLEMENTED) {
                        market.log.severe(econ.getName() + " may not be compatible with globalMarket. It does not support the depositPlayer() function.");
                    }
                    return false;
                }
            } else {
                // Send a Transaction Log
                storage.storePayment(item, seller, buyer, originalPrice, cutPrice, cut, listing.getWorld());
            }
            // Seller's stats
            if (market.enableHistory()) {
                market.getHistory().storeHistory(seller, buyer, MarketAction.LISTING_SOLD, listing.getItemId(), listing.getAmount(), originalPrice);
                market.getHistory().incrementEarned(seller, cutPrice);
                market.getHistory().incrementSpent(buyer, originalPrice);
                market.getHistory().storeHistory(buyer, seller, MarketAction.LISTING_BOUGHT, listing.getItemId(), listing.getAmount(), originalPrice);
            }
        }
        // Transfer the item to where it belongs
        storage.createMail(buyer, null, listing.getItemId(), listing.getAmount(), listing.getWorld());
        if (!isInfinite && removeListing) {
            storage.removeListing(listing.getId());
        }
        final String itemName = market.getItemName(item);
        market.notifyPlayer(seller, market.autoPayment() ? market.getLocale().get("you_sold_your_listing_of", itemName) :
            market.getLocale().get("listing_purchased_mailbox", itemName));
        market.notifyPlayer(buyer, market.getLocale().get("you_have_new_mail"));
        // Update viewers
        if (refreshInterface) {
            handler.updateAllViewers();
        }
        return true;
    }

    public void removeListing(final Listing listing, final Player player) {
        if (!listing.getSeller().equalsIgnoreCase(market.getInfiniteSeller())) {
            final int mailTime = market.getMailTime(player);
            if (mailTime > 0 && market.queueOnBuy() && !player.hasPermission("globalmarket.noqueue")) {
                storage.queueMail(listing.getSeller(), null, listing.getItemId(), listing.getAmount(), listing.getWorld());
                player.sendMessage(ChatColor.GREEN + market.getLocale().get("item_will_send", mailTime));
            } else {
                storage.createMail(listing.getSeller(), null, listing.getItemId(), listing.getAmount(), listing.getWorld());
            }
        }
        storage.removeListing(listing.getId());
        handler.updateAllViewers();
        if (market.enableHistory()) {
            if (!listing.getSeller().equalsIgnoreCase(market.getInfiniteSeller())) {
                if (listing.getSeller().equalsIgnoreCase(player.getName())) {
                    market.getHistory().storeHistory(listing.getSeller(), "You", MarketAction.LISTING_REMOVED, listing.getItemId(), listing.getAmount(), 0);
                } else {
                    market.getHistory().storeHistory(listing.getSeller(), player.getName(), MarketAction.LISTING_REMOVED, listing.getItemId(), listing.getAmount(), 0);
                }
            }
        }
        market.notifyPlayer(listing.getSeller(), market.getLocale().get("you_have_new_mail"));
    }

    public synchronized void removeListing(final Listing listing, final String player) {
        if (!listing.getSeller().equalsIgnoreCase(market.getInfiniteSeller())) {
            storage.createMail(listing.getSeller(), null, listing.getItemId(), listing.getAmount(), listing.getWorld());
        }
        storage.removeListing(listing.getId());
        handler.updateAllViewers();
        if (market.enableHistory()) {
            if (!listing.getSeller().equalsIgnoreCase(market.getInfiniteSeller())) {
                if (listing.getSeller().equalsIgnoreCase(player)) {
                    market.getHistory().storeHistory(listing.getSeller(), "You", MarketAction.LISTING_REMOVED, listing.getItemId(), listing.getAmount(), 0);
                } else {
                    market.getHistory().storeHistory(listing.getSeller(), player, MarketAction.LISTING_REMOVED, listing.getItemId(), listing.getAmount(), 0);
                }
            }
        }
        market.notifyPlayer(listing.getSeller(), market.getLocale().get("you_have_new_mail"));
    }

    public synchronized void expireListing(final Listing listing) {
        if (!listing.getSeller().equalsIgnoreCase(market.getInfiniteSeller())) {
            storage.createMail(listing.getSeller(), "Expired", listing.getItemId(), listing.getAmount(), listing.getWorld());
        }
        storage.removeListing(listing.getId());
        handler.updateAllViewers();
        if (!listing.getSeller().equalsIgnoreCase(market.getInfiniteSeller())) {
            if (market.enableHistory()) {
                market.getHistory().storeHistory(listing.getSeller(), null, MarketAction.LISTING_EXPIRED, listing.getItemId(), listing.getAmount(), 0);
            }
        }
        market.notifyPlayer(listing.getSeller(), market.getLocale().get("you_have_new_mail"));
    }

    @SuppressWarnings("TypeMayBeWeakened")
    public void retrieveMail(final Mail mail, final InterfaceViewer viewer, final Player player, final boolean transactionLog) {
        final Inventory playerInv = player.getInventory();
        final ItemStack item = storage.getItem(mail.getItemId(), mail.getAmount());
        if (transactionLog) {
            final ItemMeta meta = item.getItemMeta();
            meta.setLore(Collections.singletonList(ChatColor.GRAY + market.getLocale().get("transaction_log.unsignable")));
            item.setItemMeta(meta);
        }
        if (market.mcpcpSupportEnabled()) {
            CauldronHelper.addItemToInventory(player.getName(), item);
        } else {
            playerInv.addItem(item);
        }
        storage.removeMail(mail.getId());
    }
}
