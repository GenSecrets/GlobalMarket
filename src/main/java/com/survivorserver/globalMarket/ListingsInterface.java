package com.survivorserver.globalMarket;

import com.survivorserver.globalMarket.HistoryHandler.MarketAction;
import com.survivorserver.globalMarket.lib.PacketManager;
import com.survivorserver.globalMarket.lib.SearchResult;
import com.survivorserver.globalMarket.lib.SortMethod;
import com.survivorserver.globalMarket.ui.IFunctionButton;
import com.survivorserver.globalMarket.ui.IMarketItem;
import com.survivorserver.globalMarket.ui.IMenu;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

@SuppressWarnings("deprecation")
public class ListingsInterface extends IMenu {
    
    private final Market market;
    
    public ListingsInterface(final Market market) {
        this.market = market;
        addDefaultButtons();
        addFunctionButton(49, new IFunctionButton("SortToggle", null, Material.COMPARATOR) {
            @Override
            public boolean showButton(final InterfaceHandler handler, final InterfaceViewer viewer, final boolean hasPrevPage, final boolean hasNextPage) {
                return true;
            }
            
            @Override
            public void preBuild(final InterfaceHandler handler, final InterfaceViewer viewer, final ItemStack stack, final ItemMeta meta, final List<String> lore) {
                final Market market = Market.getMarket();
                meta.setDisplayName(ChatColor.WHITE + market.getLocale().get("interface.sort_by"));
                lore.add(ChatColor.YELLOW + market.getLocale().get("interface.sorting_by", market.getLocale().get("interface.sort_methods." + viewer.getSort())));
            }
            
            @Override
            public void onClick(final Player player, final InterfaceHandler handler, final InterfaceViewer viewer, final int slot, final InventoryClickEvent event) {
                final SortMethod sort = viewer.getSort();
                if(sort == SortMethod.DEFAULT) {
                    viewer.setSort(SortMethod.PRICE_HIGHEST);
                } else if(sort == SortMethod.PRICE_HIGHEST) {
                    viewer.setSort(SortMethod.PRICE_LOWEST);
                } else if(sort == SortMethod.PRICE_LOWEST) {
                    viewer.setSort(SortMethod.AMOUNT_HIGHEST);
                } else {
                    viewer.setSort(SortMethod.DEFAULT);
                }
                final YamlConfiguration playerConf = Market.market.getConfigHandler().getPlayerConfig(viewer.getViewer());
                if(viewer.getSort() != SortMethod.DEFAULT) {
                    playerConf.set("listings.sort_method", viewer.getSort().toString());
                }
                handler.refreshViewer(viewer, viewer.getInterface().getName());
            }
        });

        addFunctionButton(46, new IFunctionButton("PLibCreate", null, Material.HOPPER) {
            @Override
            public boolean showButton(final InterfaceHandler handler, final InterfaceViewer viewer, final boolean hasPrevPage, final boolean hasNextPage) {
                return Market.getMarket().useProtocolLib() && Bukkit.getVersion().contains("1.7");
            }
            
            @Override
            public void preBuild(final InterfaceHandler handler, final InterfaceViewer viewer, final ItemStack stack, final ItemMeta meta, final List<String> lore) {
                final Market market = Market.getMarket();
                meta.setDisplayName(ChatColor.RESET + market.getLocale().get("interface.create"));
                if(viewer.getCreateMessage() != null) {
                    lore.add(ChatColor.RED + "<" + viewer.getCreateMessage() + '>');
                    viewer.resetActions();
                } else {
                    lore.add(ChatColor.GREEN + market.getLocale().get("interface.swap_to_create"));
                }
            }
            
            @Override
            public void onClick(final Player player, final InterfaceHandler handler, final InterfaceViewer viewer, final int slot, final InventoryClickEvent event) {
                if(event.getAction() == InventoryAction.SWAP_WITH_CURSOR) {
                    // Put the item back into the inv for safe keeping
                    final int last = viewer.getLastLowerSlot();
                    final Inventory inv = event.getWhoClicked().getInventory();
                    final ItemStack cursor = event.getCursor().clone();
                    event.getWhoClicked().setItemOnCursor(new ItemStack(Material.AIR));
                    if(last >= 0) {
                        final ItemStack lastSlot = inv.getItem(last);
                        if(lastSlot == null || lastSlot.getType() == Material.AIR) {
                            inv.setItem(last, cursor);
                        } else {
                            for(final Entry<Integer, ItemStack> ent : inv.addItem(cursor).entrySet()) {
                                player.getWorld().dropItem(player.getLocation(), ent.getValue());
                            }
                            final ItemStack ls = lastSlot.clone();
                            ls.setAmount(1);
                            final ItemStack is = cursor.clone();
                            is.setAmount(1);
                            if(!ls.equals(is)) {
                                return;
                            }
                        }
                        create((Player) event.getWhoClicked(), inv.getItem(last), viewer);
                    }
                } else {
                    viewer.resetActions();
                    handler.refreshFunctionBar(viewer);
                }
            }
        });

        /*addFunctionButton(47, new IFunctionButton("Search", null, Material.MAP) {
            @Override
            public void onClick(final Player player, final InterfaceHandler handler, final InterfaceViewer viewer, final int slot, final InventoryClickEvent event) {
                if(viewer.getSearch() == null) {
                    player.closeInventory();
                    Market.getMarket().startSearch(player, viewer.getInterface().getName());
                    handler.removeViewer(viewer);
                } else {
                    viewer.setSearch(null);
                    viewer.resetActions();
                    handler.refreshViewer(viewer, viewer.getInterface().getName());
                }
            }
            
            @Override
            public void preBuild(final InterfaceHandler handler, final InterfaceViewer viewer, final ItemStack stack, final ItemMeta meta, final List<String> lore) {
                if(viewer.getSearch() == null) {
                    meta.setDisplayName(ChatColor.WHITE + Market.getMarket().getLocale().get("interface.search"));
                    lore.add(ChatColor.YELLOW + Market.getMarket().getLocale().get("interface.start_search"));
                } else {
                    meta.setDisplayName(ChatColor.WHITE + Market.getMarket().getLocale().get("interface.cancel_search"));
                    lore.add(ChatColor.YELLOW + Market.getMarket().getLocale().get("interface.searching_for", viewer.getSearch()));
                }
            }
            
            @Override
            public boolean showButton(final InterfaceHandler handler, final InterfaceViewer viewer, final boolean hasPrevPage, final boolean hasNextPage) {
                return true;
            }
        });*/
    }
    
    private static void create(final Player player, final ItemStack item, final InterfaceViewer viewer) {
        final Market market = Market.getMarket();
        final MarketStorage storage = market.getStorage();
        // Not sure that this can happen, but better safe than sorry!
        if(player == null) {
            return;
        }
        if(item == null || item.getType() == Material.AIR) {
            return;
        }
        final LocaleHandler locale = market.getLocale();
        final PacketManager packet = market.getPacket();
        if(player.getGameMode() == GameMode.CREATIVE && !market.allowCreative(player)) {
            viewer.setCreateMessage(locale.get("not_allowed_while_in_creative"));
            market.getInterfaceHandler().refreshFunctionBar(viewer);
            return;
        }
        if(market.itemBlacklisted(item)) {
            viewer.setCreateMessage(locale.get("item_is_blacklisted"));
            market.getInterfaceHandler().refreshFunctionBar(viewer);
            return;
        }
        final int max = market.maxListings(player);
        if(max > 0 && storage.getNumListingsFor(player.getName(), player.getWorld().getName()) >= max) {
            viewer.setCreateMessage(locale.get("selling_too_many_items"));
            market.getInterfaceHandler().refreshFunctionBar(viewer);
            return;
        }
        final int maxMail = market.getMaxMail(player);
        if(maxMail > 0) {
            if(market.getStorage().getNumMail(player.getName(), player.getWorld().getName(), true) >= maxMail) {
                viewer.setCreateMessage(locale.get("full_mailbox"));
                market.getInterfaceHandler().refreshFunctionBar(viewer);
                return;
            }
        }
        // Suspend the viewer so we can unsuspend later
        market.getInterfaceHandler().suspendViewer(viewer);
        // Will disconnect client if you close their inventory on the same tick as the event. I think.
        new BukkitRunnable() {
            @Override
            public void run() {
                player.closeInventory();
                // Let's get their input
                final String[] placeholder = {ChatColor.AQUA + locale.get("interface.amount_sign") + ChatColor.RESET, Integer.toString(item.getAmount()), ChatColor.AQUA + locale.get("interface.price_sign") + ChatColor.RESET, ""};
                packet.getMessage().display(player, ChatColor.YELLOW + market.getLocale().get("interface.specify_amount_and_price"), 100);
                packet.getSignInput().create(player, placeholder, packet.getSignInput().new InputResult() {
                    @Override
                    public void finished(final Player player, final String[] input, final boolean cancelled) {
                        // This is async and we need to synchronize again...
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                if(cancelled) {
                                    // The player has disconnected
                                    market.getInterfaceHandler().purgeViewer(player);
                                    return;
                                }
                                market.getInterfaceHandler().unsuspendViewer(player, viewer);
                                final int ticks = 80;
                                final int amount;
                                try {
                                    amount = Integer.parseInt(input[1]);
                                } catch(final NumberFormatException e) {
                                    packet.getMessage().display(player, ChatColor.RED + locale.get("not_a_valid_number", input[1]), ticks);
                                    return;
                                }
                                if(amount <= 0) {
                                    packet.getMessage().display(player, ChatColor.RED + locale.get("not_a_valid_amount", input[1]), ticks);
                                    return;
                                }
                                final double price;
                                try {
                                    price = Double.parseDouble(input[3]);
                                } catch(final NumberFormatException e) {
                                    packet.getMessage().display(player, ChatColor.RED + locale.get("not_a_valid_number", input[3]), ticks);
                                    return;
                                }
                                if(price < 0.01) {
                                    packet.getMessage().display(player, ChatColor.RED + locale.get("price_too_low"), ticks);
                                    return;
                                }
                                final double maxPrice = market.getMaxPrice(player, player.getItemInHand());
                                if(maxPrice > 0 && price > maxPrice) {
                                    packet.getMessage().display(player, ChatColor.RED + locale.get("price_too_high"), ticks);
                                    return;
                                }
                                if(item.getAmount() < amount) {
                                    int carrying = 0;
                                    for(final ItemStack i : player.getInventory().getContents()) {
                                        if(i != null && i.equals(item)) {
                                            carrying += i.getAmount();
                                        }
                                    }
                                    if(amount > carrying) {
                                        packet.getMessage().display(player, ChatColor.RED + locale.get("you_dont_have_x_of_this_item", amount), ticks);
                                        return;
                                    }
                                }
                                final double fee = market.getCreationFee(player, price);
                                if(fee > 0) {
                                    if(!market.getEcon().has(player.getName(), fee)) {
                                        packet.getMessage().display(player, ChatColor.RED + locale.get("you_cant_pay_this_fee"), ticks);
                                        return;
                                    }
                                    market.getEcon().withdrawPlayer(player.getName(), fee);
                                }
                                final List<ItemStack> toList = new ArrayList<>();
                                if(amount < item.getAmount()) {
                                    item.setAmount(item.getAmount() - amount);
                                    final ItemStack toAdd = item.clone();
                                    toAdd.setAmount(amount);
                                    toList.add(toAdd);
                                } else if(amount == item.getAmount()) {
                                    toList.add(item.clone());
                                    player.getInventory().setItem(viewer.getLastLowerSlot(), new ItemStack(Material.AIR));
                                } else {
                                    int am = amount;
                                    final ItemStack[] contents = player.getInventory().getContents();
                                    for(int i = 0; i < contents.length; i++) {
                                        if(am == 0) {
                                            break;
                                        }
                                        final ItemStack c = contents[i];
                                        if(c != null && c.equals(item)) {
                                            if(am >= c.getAmount()) {
                                                final ItemStack a = c.clone();
                                                contents[i] = null;
                                                am -= a.getAmount();
                                                toList.add(a);
                                            } else {
                                                final ItemStack a = c.clone();
                                                c.setAmount(c.getAmount() - am);
                                                a.setAmount(am);
                                                toList.add(a);
                                                break;
                                            }
                                        }
                                    }
                                    player.getInventory().setContents(contents);
                                }
                                final String world = player.getWorld().getName();
                                if(toList.size() > 1) {
                                    final double pricePer = new BigDecimal(price / amount).setScale(2, RoundingMode.HALF_EVEN).doubleValue();
                                    final int tradeTime = market.getTradeTime(player);
                                    if(tradeTime > 0) {
                                        storage.queueListing(player.getName(), toList, pricePer, world);
                                        packet.getMessage().display(player, fee > 0 ? ChatColor.GREEN + locale.get("items_queued_with_fee", tradeTime, fee) : ChatColor.GREEN + locale.get("items_queued", tradeTime), ticks);
                                    } else {
                                        storage.createListing(player.getName(), toList, pricePer, world);
                                        packet.getMessage().display(player, fee > 0 ? ChatColor.GREEN + locale.get("items_listed_with_fee", fee) : ChatColor.GREEN + locale.get("items_listed"), ticks);
                                    }
                                    if(market.enableHistory()) {
                                        market.getHistory().storeHistory(player.getName(), "", MarketAction.LISTING_CREATED, toList, price);
                                    }
                                } else {
                                    final int tradeTime = market.getTradeTime(player);
                                    if(tradeTime > 0) {
                                        storage.queueListing(player.getName(), toList.get(0), price, world);
                                        packet.getMessage().display(player, fee > 0 ? ChatColor.GREEN + locale.get("item_queued_with_fee", tradeTime, fee) : ChatColor.GREEN + locale.get("item_queued", tradeTime), ticks);
                                    } else {
                                        storage.createListing(player.getName(), toList.get(0), price, world);
                                        packet.getMessage().display(player, fee > 0 ? ChatColor.GREEN + locale.get("item_listed_with_fee", fee) : ChatColor.GREEN + locale.get("item_listed"), ticks);
                                    }
                                    if(market.enableHistory()) {
                                        market.getHistory().storeHistory(player.getName(), "", MarketAction.LISTING_CREATED, toList.get(0), price);
                                    }
                                }
                            }
                        }.runTaskLater(market, 1);
                    }
                });
            }
        }.runTaskLater(market, 1);
    }
    
    @Override
    public String getName() {
        return "Listings";
    }
    
    @Override
    public String getTitle() {
        return market.getLocale().get("interface.listings_title");
    }
    
    @Override
    public int getSize() {
        return 54;
    }
    
    @Override
    public boolean doSingleClickActions() {
        return false;
    }
    
    @Override
    public ItemStack prepareItem(final IMarketItem marketItem, final InterfaceViewer viewer, final int page, final int slot, final boolean leftClick, final boolean shiftClick) {
        final Listing listing = (Listing) marketItem;
        final ItemStack item = market.getStorage().getItem(listing.getItemId(), listing.getAmount());
        final ItemMeta meta = item.getItemMeta();
        
        final boolean isSeller = viewer.getViewer().equalsIgnoreCase(listing.getSeller());
        final boolean isAdmin = market.getInterfaceHandler().isAdmin(viewer.getViewer());
        
        List<String> lore = meta.getLore();
        if(!meta.hasLore()) {
            lore = new ArrayList<>();
        }
        final String price = ChatColor.WHITE + market.getLocale().get("price") + (listing.getPrice() > 0 ? market.getEcon().format(listing.getPrice()) : market.getLocale().get("free"));
        final String seller = ChatColor.WHITE + market.getLocale().get("seller") + ChatColor.GRAY + ChatColor.ITALIC + listing.getSeller();
        lore.add(price);
        lore.add(seller);
        
        // Prevent things like hotbar swapping from triggering an action
        if(!shiftClick && !leftClick) {
            viewer.resetActions();
        }
        
        // Don't want people buying their own listings
        if(isSeller && leftClick) {
            viewer.resetActions();
        }
        
        // Or canceling listings they don't have permissions to
        if(!isSeller && shiftClick) {
            if(!isAdmin) {
                viewer.resetActions();
            }
        }
        
        if(!isSeller) {
            String buyMsg = ChatColor.YELLOW + market.getLocale().get("click_to_buy");
            if(leftClick) {
                if(market.getEcon().has(viewer.getViewer(), listing.getPrice())) {
                    if(viewer.getClicks() >= 2) {
                        if(!listing.getSeller().equalsIgnoreCase(market.getInfiniteSeller())) {
                            buyMsg = ChatColor.RED + market.getLocale().get("interface.transaction_error");
                        }
                    } else {
                        buyMsg = ChatColor.GREEN + market.getLocale().get("click_again_to_confirm");
                    }
                } else {
                    buyMsg = ChatColor.RED + market.getLocale().get("not_enough_money", market.getEcon().currencyNamePlural());
                    viewer.resetActions();
                }
            }
            lore.add(buyMsg);
        }
        
        if(isSeller || isAdmin) {
            String removeMsg = ChatColor.DARK_GRAY + market.getLocale().get("shift_click_to_remove");
            if(shiftClick) {
                removeMsg = ChatColor.GREEN + market.getLocale().get("shift_click_again_to_confirm");
            }
            lore.add(removeMsg);
        }
        
        if(listing.getSeller().equalsIgnoreCase(market.getInfiniteSeller())) {
            lore.add(ChatColor.LIGHT_PURPLE + market.getLocale().get("interface.infinite"));
        }
        
        final int siblings = listing.countStacked();
        if(siblings > 1) {
            int count = 0;
            if(siblings <= 15) {
                for(final Listing l : listing.getStacked()) {
                    count += l.getAmount();
                }
            }
            lore.add(ChatColor.AQUA + market.getLocale().get("interface.stacked", listing.getAmount(), (count > 0 ? count + listing.getAmount() : market.getLocale().get("interface.stacked_many"))));
        }
        
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }
    
    @Override
    public void handleLeftClickAction(final InterfaceViewer viewer, final IMarketItem item, final InventoryClickEvent event) {
        if(market.getCore().buyListing((Listing) item, (Player) event.getWhoClicked(), viewer, true, true, true)) {
            //event.getWhoClicked().sendMessage("HANDLE LEFT CLICK ACTION " + item);
            viewer.resetActions();
        }
    }
    
    @Override
    public void handleShiftClickAction(final InterfaceViewer viewer, final IMarketItem item, final InventoryClickEvent event) {
        viewer.resetActions();
        //event.getWhoClicked().sendMessage("HANDLE SHIFT LEFT CLICK ACTION " + item);
        market.getCore().removeListing((Listing) item, (Player) event.getWhoClicked());
    }
    
    @SuppressWarnings("unchecked")
    @Override
    public List<IMarketItem> getContents(final InterfaceViewer viewer) {
        return (List<IMarketItem>) (List<?>) market.getStorage().getListings(viewer.getViewer(), viewer.getSort(), viewer.getWorld());
    }
    
    @SuppressWarnings("unchecked")
    @Override
    public List<IMarketItem> doSearch(final InterfaceViewer viewer, final String search) {
        final SearchResult result = market.getStorage().getListings(viewer.getViewer(), viewer.getSort(), search, viewer.getWorld());
        viewer.setSearchSize(result.getTotalFound());
        return (List<IMarketItem>) (List<?>) result.getPage();
    }
    
    @Override
    public IMarketItem getItem(final InterfaceViewer viewer, final int id) {
        return market.getStorage().getListing(id);
    }
    
    @Override
    public void onInterfacePrepare(final InterfaceViewer viewer, final List<IMarketItem> contents, final ItemStack[] invContents, final Inventory inv) {
    }
    
    @Override
    public void onInterfaceClose(final InterfaceViewer viewer) {
        YamlConfiguration playerConf = market.getConfigHandler().getPlayerConfig(viewer.getViewer());
        if (!playerConf.isSet("listings.sort_method")) {
            market.getConfigHandler().savePlayerConfig(viewer.getViewer());
        }
        playerConf.set("listings.sort_method", viewer.getSort().toString());
    }
    
    @Override
    public void onInterfaceOpen(final InterfaceViewer viewer) {
        final YamlConfiguration playerConf = market.getConfigHandler().getPlayerConfig(viewer.getViewer());
        if(playerConf.isSet("listings.sort_method")) {
            viewer.setSort(SortMethod.valueOf(playerConf.getString("listings.sort_method").toUpperCase()));
        }
    }
    
    @Override
    public ItemStack getItemStack(final InterfaceViewer viewer, final IMarketItem item) {
        return market.getStorage().getItem(item.getItemId(), item.getAmount());
    }
}
