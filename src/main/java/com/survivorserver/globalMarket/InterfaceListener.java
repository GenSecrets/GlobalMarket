package com.survivorserver.globalMarket;

import com.survivorserver.globalMarket.ui.IMarketItem;
import com.survivorserver.globalMarket.ui.IMenu;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event.Result;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.inventory.InventoryType.SlotType;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BookMeta;

public class InterfaceListener implements Listener {

    Market market;
    InterfaceHandler handler;
    MarketStorage storage;
    MarketCore core;

    public InterfaceListener(final Market market, final InterfaceHandler handler, final MarketStorage storage, final MarketCore core) {
        this.market = market;
        this.handler = handler;
        this.storage = storage;
        this.core = core;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void clickEvent(final InventoryClickEvent event) {
        final InterfaceViewer viewer = handler.findViewer(event.getWhoClicked().getName());
        //ItemStack curItem = event.getCurrentItem();
        final int rawSlot = event.getRawSlot();
        final int slot = event.getSlot();

        final int lastTopSlot = event.getInventory().getSize() < 54 ? 26 : 53;
        if (viewer != null && event.getView().getTitle().equalsIgnoreCase(viewer.getInterface().getTitle())) {
            if (viewer.getGui() != null) {
                if (rawSlot <= lastTopSlot && slot > -1) {
                    // Determine if a click was within the top portion of the inventory

                    event.setCancelled(true);
                    event.setResult(Result.DENY);

                    if (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT && event.getClick() != ClickType.SHIFT_LEFT) {
                        return;
                    }

                    final IMenu inter = viewer.getInterface();
                    if (viewer.getBoundSlots().containsKey(slot)) {
                        // This item has an ID attached to it
                        final IMarketItem item = inter.getItem(viewer, viewer.getBoundSlots().get(event.getRawSlot()));
                        if (item == null) {
                            market.log.warning(String.format("Null IMarketItem in %s with position %s (raw: %s) in interface %s, should have an ID of %s.", event.getEventName(), slot, rawSlot, inter.getName(), viewer.getBoundSlots().get(rawSlot)));
                            return;
                        }
                        if (event.isRightClick()) {
                            // Drop everything and start over
                            viewer.resetActions();
                            handler.refreshSlot(viewer, slot, item);
                        } else {
                            // Reset their actions if clicking a different item than last time
                            final int lastSlot = viewer.getLastActionSlot();
                            if (lastSlot > -1 && lastSlot != slot) {
                                viewer.resetActions();
                                handler.refreshSlot(viewer, lastSlot, item);
                                return;
                            }

                            // Only increment clicks if both are of the same type
                            if (!inter.doSingleClickActions()) {
                                if (event.getAction() == InventoryAction.PICKUP_ALL
                                        && (viewer.getLastAction() == null || viewer.getLastAction() == InventoryAction.PICKUP_ALL)) {
                                    viewer.incrementClicks();
                                } else if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY
                                        && (viewer.getLastAction() == null || viewer.getLastAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY)) {
                                    viewer.incrementClicks();
                                } else {
                                    viewer.resetActions();
                                    handler.refreshSlot(viewer, lastSlot, item);
                                    return;
                                }
                            }

                            viewer.setLastAction(event.getAction());
                            viewer.setLastActionSlot(slot);

                            viewer.setLastItem(item.getId());

                            if (inter.doSingleClickActions()) {
                                if (event.isShiftClick()) {
                                    inter.handleShiftClickAction(viewer, item, event);
                                } else {
                                    inter.handleLeftClickAction(viewer, item, event);
                                }
                            } else {
                                handler.refreshSlot(viewer, slot, item);
                                if (viewer.getClicks() == 2) {
                                    if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                                        inter.handleShiftClickAction(viewer, item, event);
                                    } else {
                                        inter.handleLeftClickAction(viewer, item, event);
                                    }
                                }
                            }
                        }
                    } else {
                        if (event.isRightClick()) {
                            return;
                        }
                        inter.onUnboundClick(market, handler, viewer, rawSlot, event);
                    }
                } else if (isMarketItem(event.getCurrentItem())) {
                    event.setCancelled(true);
                    event.setResult(Result.DENY);
                    event.getCurrentItem().setType(Material.AIR);
                    event.getCursor().setType(Material.AIR);
                    event.getInventory().remove(event.getCurrentItem());
                } else if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY
                        || (event.getAction() == InventoryAction.PLACE_ALL
                        || event.getAction() == InventoryAction.PLACE_ONE
                        || event.getAction() == InventoryAction.PLACE_SOME
                        || event.getAction() == InventoryAction.SWAP_WITH_CURSOR)
                        && event.getRawSlot() == event.getSlot()) {
                    // They're trying to put an item from their inventory into the Market inventory. Not bad for us, but they will lose their item. Cancel it because we're nice :)
                    event.setCancelled(true);
                    event.setResult(Result.DENY);
                } else {
                    viewer.setLastLower(event.getSlot());
                }
            } else {
                event.setCancelled(true);
                event.setResult(Result.DENY);
                handler.removeViewer(viewer);
                cleanInventory(event.getWhoClicked().getInventory());
            }
        } else {
            if (isMarketItem(event.getCurrentItem())) {
                event.setCancelled(true);
                event.setResult(Result.DENY);
                event.getCurrentItem().setType(Material.AIR);
                event.getCursor().setType(Material.AIR);
                event.getInventory().remove(event.getCurrentItem());
            }
            if (event.getInventory().getType() == InventoryType.MERCHANT) {
                final ItemStack trading = event.getCursor();
                if (trading != null && trading.getType() == Material.WRITTEN_BOOK) {
                    if (trading.hasItemMeta()) {
                        if (trading.getItemMeta().hasLore()) {
                            if (trading.getItemMeta().getLore().contains(market.getLocale().get("not_tradable"))) {
                                if (event.getSlotType() == SlotType.CRAFTING) {
                                    event.setCancelled(true);
                                    event.setResult(Result.DENY);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    public void handleDrag(final InventoryDragEvent event) {
        final InterfaceViewer viewer = handler.findViewer(event.getWhoClicked().getName());
        if(viewer == null || viewer.getInterface() == null){
            return;
        }
        if (event.getView().getTitle().equalsIgnoreCase(viewer.getInterface().getTitle())) {
            final int lastTopSlot = event.getInventory().getSize() < 54 ? 26 : 53;
            event.getRawSlots().stream().filter(raw -> raw <= lastTopSlot).forEach(raw -> event.setCancelled(true));
        } else if (isMarketItem(event.getCursor())) {
            event.setCancelled(true);
            event.setResult(Result.DENY);
            event.setCursor(new ItemStack(Material.AIR));
        }
    }

    @SuppressWarnings("unused")
    public void handleSingleClick(final InventoryClickEvent event, final InterfaceViewer viewer, final IMenu gui, final IMarketItem item) {
        if (event.isShiftClick()) {
            gui.handleShiftClickAction(viewer, item, event);
        } else if (event.isLeftClick()) {
            gui.handleLeftClickAction(viewer, item, event);
        } else {
            viewer.resetActions();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void handleInventoryClose(final InventoryCloseEvent event) {
        final InterfaceViewer viewer = handler.findViewer(event.getPlayer().getName());
        if (viewer != null) {
            viewer.getGui().clear();
            handler.removeViewer(viewer);
            if (market.useProtocolLib()) {
                market.getPacket().getMessage().clearPlayer((Player) event.getPlayer());
            }
            cleanInventory(event.getPlayer().getInventory());
        }
    }

    public static void cleanInventory(final Inventory inv) {
        ItemStack[] items = inv.getContents();
        ItemStack[] clone = items.clone();
        for (int i = 0; i < items.length; i++) {
            if (items[i] != null) {
                if (isMarketItem(items[i])) {
                    clone[i] = null;
                }
            }
        }
        inv.setContents(clone);
        if (inv instanceof PlayerInventory) {
            items = ((PlayerInventory) inv).getArmorContents();
            clone = items.clone();
            for (int i = 0; i < items.length; i++) {
                if (items[i] != null) {
                    if (isMarketItem(items[i])) {
                        clone[i] = null;
                    }
                }
            }
            ((PlayerInventory) inv).setArmorContents(clone);
        }
    }

    @EventHandler
    public void handleItemPickup(final ItemSpawnEvent event) {
        // More fixing for item duping
        final ItemStack item = event.getEntity().getItemStack();
        if (item != null && isMarketItem(item)) {
            event.getEntity().remove();
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBookEvent(final PlayerEditBookEvent event) {
        if (event.isCancelled()) {
            return;
        }

        final BookMeta meta = event.getNewBookMeta();
        if (event.isSigning() && meta.hasLore()) {
            if (meta.getLore().contains(ChatColor.GRAY + market.getLocale().get("transaction_log.unsignable"))) {
                event.getPlayer().sendMessage(market.getLocale().get("cant_sign_book"));
                event.setCancelled(true);
            }
        }
    }

    public static boolean isMarketItem(final ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta().hasLore() && item.getItemMeta().getLore().contains(InterfaceHandler.ITEM_UUID);
    }
}
