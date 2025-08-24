package com.inf1nlty.shop.global;

import net.minecraft.src.NBTTagCompound;

import java.util.UUID;

/**
 * Global shop listing record (server-side).
 * Includes optional NBT to preserve enchantments/custom data.
 * priceTenths is one-decimal currency stored as tenths.
 * Supports both sell and buy orders.
 */
public class GlobalListing {

    public int listingId;
    public UUID ownerUUID;
    public String ownerName;
    public int itemId;
    public int meta;
    public int amount; // Sell: available count; Buy: desired count (-1 for unlimited)
    public int priceTenths;
    public NBTTagCompound nbt; // may be null
    public boolean isBuyOrder; // true for buy orders, false for sell orders

    public GlobalListing copyShallow() {
        GlobalListing g = new GlobalListing();
        g.listingId = listingId;
        g.ownerUUID = ownerUUID;
        g.ownerName = ownerName;
        g.itemId = itemId;
        g.meta = meta;
        g.amount = amount;
        g.priceTenths = priceTenths;
        g.nbt = nbt == null ? null : (NBTTagCompound) nbt.copy();
        g.isBuyOrder = isBuyOrder;
        return g;
    }
}