package com.survivorserver.GlobalMarket.Lib;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;


public class MCPCPHelper {

    public static ItemStack wrapItemStack(Inventory inv, int slot) {
        me.dasfaust.GlobalMarket.MarketCompanion inst = me.dasfaust.GlobalMarket.MarketCompanion.getInstance();
        return inst.getWrappedForgeItemStack(getNMSInventory(inv), slot);
    }

    public static ItemStack wrapItemStack(ItemStack stack) {
        return new me.dasfaust.GlobalMarket.WrappedItemStack(getNMSStack(stack), true);
    }

    public static void addItemToInventory(ItemStack stack, Inventory inv, int slot) {
        if (!(stack instanceof me.dasfaust.GlobalMarket.WrappedItemStack)) {
            stack = wrapItemStack(stack);
        }
        me.dasfaust.GlobalMarket.MarketCompanion inst = me.dasfaust.GlobalMarket.MarketCompanion.getInstance();
        inst.setInventorySlot(getNMSInventory(inv), stack, slot);
    }

    public static void addItemToInventory(String playerName, ItemStack stack) {
        if (!(stack instanceof me.dasfaust.GlobalMarket.WrappedItemStack)) {
            stack = wrapItemStack(stack);
        }
        me.dasfaust.GlobalMarket.MarketCompanion inst = me.dasfaust.GlobalMarket.MarketCompanion.getInstance();
        inst.addToPlayerInventory(playerName, 0, stack);
    }

    public static void setInventoryContents(Inventory inv, ItemStack[] contents) {
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (stack != null) {
                if (!(stack instanceof me.dasfaust.GlobalMarket.WrappedItemStack)) {
                    contents[i] = new me.dasfaust.GlobalMarket.WrappedItemStack(getNMSStack(stack), true);
                }
            }
        }
        me.dasfaust.GlobalMarket.MarketCompanion inst = me.dasfaust.GlobalMarket.MarketCompanion.getInstance();
        inst.setInventoryContents(getNMSInventory(inv), contents);
    }

    // TODO: some type of abstraction to support 1.7+
    public static Object getNMSStack(ItemStack item) {
        return org.bukkit.craftbukkit.v1_6_R3.inventory.CraftItemStack.asNMSCopy(item);
    }

    // TODO: some type of abstraction to support 1.7+
    public static Object getNMSInventory(Inventory inv) {
        return ((org.bukkit.craftbukkit.v1_6_R3.inventory.CraftInventory) inv).getInventory();
    }
}
