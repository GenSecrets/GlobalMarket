package com.survivorserver.globalMarket.ui;

import com.survivorserver.globalMarket.InterfaceHandler;
import com.survivorserver.globalMarket.InterfaceViewer;
import com.survivorserver.globalMarket.Market;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings({"UnusedParameters", "unused"})
public abstract class IMenu {
    
    private final Map<Integer, IFunctionButton> functionBar;
    
    public IMenu() {
        functionBar = new HashMap<>();
    }
    
    public abstract String getName();
    
    public abstract String getTitle();
    
    public abstract int getSize();
    
    public abstract boolean doSingleClickActions();
    
    public abstract ItemStack prepareItem(IMarketItem item, InterfaceViewer viewer, int page, int slot, boolean leftClick, boolean shiftClick);
    
    public abstract void handleLeftClickAction(InterfaceViewer viewer, IMarketItem item, InventoryClickEvent event);
    
    public abstract void handleShiftClickAction(InterfaceViewer viewer, IMarketItem item, InventoryClickEvent event);
    
    public abstract List<IMarketItem> getContents(InterfaceViewer viewer);
    
    public abstract List<IMarketItem> doSearch(InterfaceViewer viewer, String search);
    
    public abstract IMarketItem getItem(InterfaceViewer viewer, int id);
    
    public abstract ItemStack getItemStack(InterfaceViewer viewer, IMarketItem item);
    
    public abstract void onInterfacePrepare(InterfaceViewer viewer, List<IMarketItem> contents, ItemStack[] invContents, Inventory inv);
    
    public abstract void onInterfaceClose(InterfaceViewer viewer);
    
    public abstract void onInterfaceOpen(InterfaceViewer viewer);
    
    public void addDefaultButtons() {
        addFunctionButton(45, new IFunctionButton("PrevPage", null, Material.PAPER) {
            @Override
            public void onClick(final Player player, final InterfaceHandler handler, final InterfaceViewer viewer, final int slot, final InventoryClickEvent event) {
                viewer.setPage(viewer.getPage() - 1);
                viewer.resetActions();
                handler.refreshViewer(viewer, viewer.getInterface().getName());
            }
            
            @Override
            public void preBuild(final InterfaceHandler handler, final InterfaceViewer viewer, final ItemStack stack, final ItemMeta meta, final List<String> lore) {
                meta.setDisplayName(ChatColor.WHITE + Market.getMarket().getLocale().get("interface.page", viewer.getPage() - 1));
                lore.add(ChatColor.YELLOW + Market.getMarket().getLocale().get("interface.prev_page"));
            }
            
            @Override
            public boolean showButton(final InterfaceHandler handler, final InterfaceViewer viewer, final boolean hasPrevPage, final boolean hasNextPage) {
                return hasPrevPage;
            }
        });
        
        addFunctionButton(49, new IFunctionButton("CurPage", null, Material.PAPER) {
            @Override
            public void onClick(final Player player, final InterfaceHandler handler, final InterfaceViewer viewer, final int slot, final InventoryClickEvent event) {
                viewer.setPage(viewer.getPage() - 1);
                viewer.resetActions();
                handler.refreshViewer(viewer, viewer.getInterface().getName());
            }
            
            @Override
            public void preBuild(final InterfaceHandler handler, final InterfaceViewer viewer, final ItemStack stack, final ItemMeta meta, final List<String> lore) {
                meta.setDisplayName(ChatColor.WHITE + Market.getMarket().getLocale().get("interface.page", viewer.getPage()));
                lore.add(ChatColor.YELLOW + Market.getMarket().getLocale().get("interface.cur_page"));
            }
            
            @Override
            public boolean showButton(final InterfaceHandler handler, final InterfaceViewer viewer, final boolean hasPrevPage, final boolean hasNextPage) {
                return true;
            }
        });
        
        addFunctionButton(53, new IFunctionButton("NextPage", null, Material.PAPER) {
            @Override
            public void onClick(final Player player, final InterfaceHandler handler, final InterfaceViewer viewer, final int slot, final InventoryClickEvent event) {
                viewer.setPage(viewer.getPage() + 1);
                viewer.resetActions();
                handler.refreshViewer(viewer, viewer.getInterface().getName());
            }
            
            public void preBuild(final InterfaceHandler handler, final InterfaceViewer viewer, final ItemStack stack, final ItemMeta meta, final List<String> lore) {
                meta.setDisplayName(ChatColor.WHITE + Market.getMarket().getLocale().get("interface.page", viewer.getPage() + 1));
                lore.add(ChatColor.YELLOW + Market.getMarket().getLocale().get("interface.next_page"));
            }
            
            public boolean showButton(final InterfaceHandler handler, final InterfaceViewer viewer, final boolean hasPrevPage, final boolean hasNextPage) {
                return hasNextPage;
            }
        });
    }
    
    public void onUnboundClick(final Market market, final InterfaceHandler handler, final InterfaceViewer viewer, final int slot, final InventoryClickEvent event) {
        if(event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) {
            return;
        }
        final Player player = (Player) event.getWhoClicked();
        if(functionBar.containsKey(slot)) {
            functionBar.get(slot).onClick(player, handler, viewer, slot, event);
        }
    }
    
    public void buildFunctionBar(final InterfaceHandler handler, final InterfaceViewer viewer, final ItemStack[] contents, final boolean pPage, final boolean nPage) {
        final int invSize = viewer.getGui().getSize();
        for(int i = invSize - 9; i < invSize; i++) {
            if(functionBar.containsKey(i)) {
                final IFunctionButton button = functionBar.get(i);
                contents[i] = button.buildItem(handler, viewer, contents, pPage, nPage);
            }
        }
    }
    
    public void addFunctionButton(final int slot, final IFunctionButton button) {
        if(functionBar.containsKey(slot)) {
            functionBar.remove(slot);
        }
        functionBar.put(slot, button);
    }
    
    public void removeFunctionButton(final int slot) {
        functionBar.remove(slot);
    }
}
