package com.inf1nlty.shop.util;

import net.minecraft.src.EntityPlayer;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.HashMap;
import java.util.UUID;

/**
 * In-memory balances in tenths.
 * Persisted via EntityPlayerMixin in "shop_money" as int tenths.
 */
public class MoneyManager {

    private static final WeakHashMap<EntityPlayer, Integer> BALANCES = new WeakHashMap<>();

    // DOC: Global UUID balance table, supports offline player balance get/set
    private static final Map<UUID, Integer> BALANCES_BY_UUID = new HashMap<>();

    private MoneyManager() {}

    // DOC: Get balance by EntityPlayer (online), fallback to UUID storage
    public static int getBalanceTenths(EntityPlayer p) {
        Integer bal = BALANCES.get(p);
        if (bal != null) return bal;
        return getBalanceTenths(PlayerIdentityUtil.getOfflineUUID(p.username));
    }

    // DOC: Get balance by UUID (offline/online)
    public static int getBalanceTenths(UUID uuid) {
        return BALANCES_BY_UUID.getOrDefault(uuid, 0);
    }

    // DOC: Set balance for EntityPlayer and update UUID storage
    public static void setBalanceTenths(EntityPlayer p, int v) {
        BALANCES.put(p, v);
        setBalanceTenths(PlayerIdentityUtil.getOfflineUUID(p.username), v);
    }

    // DOC: Set balance for UUID (offline/online)
    public static void setBalanceTenths(UUID uuid, int v) {
        BALANCES_BY_UUID.put(uuid, v);
    }

    // DOC: Add delta to EntityPlayer and update UUID storage
    public static void addTenths(EntityPlayer p, int delta) {
        setBalanceTenths(p, getBalanceTenths(p) + delta);
    }

    // DOC: Add delta to UUID, support offline player
    public static void addTenths(UUID uuid, int delta) {
        setBalanceTenths(uuid, getBalanceTenths(uuid) + delta);
    }
}