package com.inf1nlty.shop.global;

import net.minecraft.src.NBTTagCompound;

import java.util.UUID;

/**
 * Global shop listing record (server-side).
 * Includes optional NBT to preserve enchantments/custom data.
 * priceTenths is one-decimal currency stored as tenths.
 */
public class GlobalListing {

    public int listingId;
    public UUID ownerUUID;
    public String ownerName;
    public int itemId;
    public int meta;
    public int amount;
    public int priceTenths;
    public NBTTagCompound nbt; // may be null

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
        return g;
    }
}