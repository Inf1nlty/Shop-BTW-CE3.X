package com.inf1nlty.shop.util;

import net.minecraft.src.EntityPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory balances in tenths.
 * Persisted via EntityPlayerMixin in "shop_money" as int tenths.
 * Supports both online EntityPlayer and offline UUID for balances.
 */
public class MoneyManager {

    private static final WeakHashMap<EntityPlayer, Integer> BALANCES = new WeakHashMap<>();
    private static final Map<UUID, Integer> OFFLINE_BALANCES = new ConcurrentHashMap<>();

    private MoneyManager() {
    }

    // ----- EntityPlayer-based -----
    public static int getBalanceTenths(EntityPlayer p) {
        return BALANCES.getOrDefault(p, 0);
    }

    public static void setBalanceTenths(EntityPlayer p, int v) {
        BALANCES.put(p, v);
        syncPlayerToOffline(p);
    }

    public static void addTenths(EntityPlayer p, int delta) {
        setBalanceTenths(p, getBalanceTenths(p) + delta);
        syncPlayerToOffline(p);
    }

    // ----- UUID-based (offline support) -----
    public static int getBalanceTenths(UUID uuid) {
        for (EntityPlayer p : BALANCES.keySet()) {
            if (PlayerIdentityUtil.getOfflineUUID(p.username).equals(uuid)) {
                return BALANCES.getOrDefault(p, 0);
            }
        }
        return OFFLINE_BALANCES.getOrDefault(uuid, 0);
    }

    public static void setBalanceTenths(UUID uuid, int v) {
        OFFLINE_BALANCES.put(uuid, v);
    }

    public static void addTenths(UUID uuid, int delta) {
        setBalanceTenths(uuid, getBalanceTenths(uuid) + delta);
    }

    public static void syncPlayerToOffline(EntityPlayer p) {
        if (p != null) {
            setBalanceTenths(PlayerIdentityUtil.getOfflineUUID(p.username), getBalanceTenths(p));
        }
    }

    public static void syncOfflineToPlayer(EntityPlayer p) {
        if (p != null) {
            setBalanceTenths(p, getBalanceTenths(PlayerIdentityUtil.getOfflineUUID(p.username)));
        }
    }
}