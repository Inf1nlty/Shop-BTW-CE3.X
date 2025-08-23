package com.inf1nlty.shop.util;

import net.minecraft.src.EntityPlayer;

import java.util.WeakHashMap;

/**
 * In-memory balances in tenths.
 * Persisted via EntityPlayerMixin in "shop_money" as int tenths.
 */
public class MoneyManager {

    private static final WeakHashMap<EntityPlayer, Integer> BALANCES = new WeakHashMap<>();

    private MoneyManager() {}

    public static int getBalanceTenths(EntityPlayer p) {
        return BALANCES.getOrDefault(p, 0);
    }

    public static void setBalanceTenths(EntityPlayer p, int v) {
        BALANCES.put(p, v);
    }

    public static void addTenths(EntityPlayer p, int delta) {
        setBalanceTenths(p, getBalanceTenths(p) + delta);
    }
}