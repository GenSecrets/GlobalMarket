package com.survivorserver.globalMarket;

import com.survivorserver.globalMarket.lib.SortMethod;
import com.survivorserver.globalMarket.ui.IMenu;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.inventory.Inventory;

import java.util.Map;

@SuppressWarnings("unused")
public class InterfaceViewer {
    
    String name;
    String player;
    Map<Integer, Integer> boundSlots;
    int currentPage = 1;
    Inventory gui;
    InventoryAction lastAction;
    int lastActionSlot = -1;
    String search;
    int lastClicked;
    int clicks;
    String world;
    IMenu mInterface;
    int searchSize;
    SortMethod sort;
    int lastLowerSlot = -1;
    String createMessage;
    
    public InterfaceViewer(final String name, final String player, final Inventory gui, final IMenu mInterface, final String world) {
        this.name = name;
        this.player = player;
        this.gui = gui;
        this.world = world;
        this.mInterface = mInterface;
        sort = SortMethod.DEFAULT;
    }
    
    public Map<Integer, Integer> getBoundSlots() {
        return boundSlots;
    }
    
    public void setBoundSlots(final Map<Integer, Integer> boundSlots) {
        this.boundSlots = boundSlots;
    }
    
    public int getPage() {
        return currentPage;
    }
    
    public void setPage(final int page) {
        currentPage = page;
    }
    
    public String getName() {
        return name;
    }
    
    public String getViewer() {
        return player;
    }
    
    public Inventory getGui() {
        return gui;
    }
    
    public void setGui(final Inventory gui) {
        this.gui = gui;
    }
    
    public InventoryAction getLastAction() {
        return lastAction;
    }
    
    public void setLastAction(final InventoryAction action) {
        lastAction = action;
    }
    
    public int getLastActionSlot() {
        return lastActionSlot;
    }
    
    public void setLastActionSlot(final int slot) {
        lastActionSlot = slot;
    }
    
    public int getLastItem() {
        return lastClicked;
    }
    
    public void setLastItem(final int id) {
        lastClicked = id;
    }
    
    public String getSearch() {
        return search;
    }
    
    public void setSearch(final String search) {
        this.search = search;
    }
    
    public IMenu getInterface() {
        return mInterface;
    }
    
    public void resetActions() {
        setLastAction(null);
        setLastActionSlot(-1);
        setLastItem(-1);
        clicks = 0;
        lastLowerSlot = -1;
        createMessage = null;
    }
    
    public int getClicks() {
        return clicks;
    }
    
    public void incrementClicks() {
        clicks++;
    }
    
    public String getWorld() {
        return world;
    }
    
    public int getSearchSize() {
        return searchSize;
    }
    
    public void setSearchSize(final int searchSize) {
        this.searchSize = searchSize;
    }
    
    public SortMethod getSort() {
        return sort;
    }
    
    public void setSort(final SortMethod sort) {
        this.sort = sort;
    }
    
    public String getCreateMessage() {
        return createMessage;
    }
    
    public void setCreateMessage(final String createMessage) {
        this.createMessage = createMessage;
    }
    
    public void setLastLower(final int lastLowerSlot) {
        this.lastLowerSlot = lastLowerSlot;
    }
    
    public int getLastLowerSlot() {
        return lastLowerSlot;
    }
}
