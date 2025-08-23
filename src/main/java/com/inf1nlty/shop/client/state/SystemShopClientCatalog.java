package com.inf1nlty.shop.client.state;

import net.minecraft.src.Item;
import net.minecraft.src.ItemStack;
import net.minecraft.src.NBTTagCompound;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds the server-provided system shop entries on the client.
 * Each entry includes pricing so client never reads local cfg.
 */
public final class SystemShopClientCatalog {

    public static class Entry {
        public int itemID;
        public int meta;
        public int buyTenths;
        public int sellTenths;

        public ItemStack toStack() {
            Item item = (itemID >= 0 && itemID < Item.itemsList.length) ? Item.itemsList[itemID] : null;
            if (item == null) return null;
            ItemStack st = new ItemStack(item, 1, meta);
            if (st.stackTagCompound == null) st.stackTagCompound = new NBTTagCompound();
            st.stackTagCompound.setInteger("ShopBuyPrice", buyTenths);
            st.stackTagCompound.setInteger("ShopSellPrice", sellTenths);
            return st;
        }
    }

    private static final List<Entry> ENTRIES = new ArrayList<>();

    private SystemShopClientCatalog() {}

    public static void set(List<Entry> fresh) {
        ENTRIES.clear();
        ENTRIES.addAll(fresh);
    }

    public static List<Entry> get() {
        return ENTRIES;
    }
}