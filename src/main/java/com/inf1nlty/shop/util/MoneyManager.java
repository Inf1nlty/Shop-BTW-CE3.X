package com.inf1nlty.shop.util;

import net.minecraft.src.EntityPlayer;

import java.io.*;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.HashMap;
import java.util.UUID;

/**
 * In-memory balances in tenths.
 * Persisted via EntityPlayerMixin in "shop_money" as int tenths.
 * Also persisted for offline players in shop_balances.dat.
 */
public class MoneyManager {

    private static final WeakHashMap<EntityPlayer, Integer> BALANCES = new WeakHashMap<>();
    // Global UUID balance table, supports offline player balance get/set
    private static final Map<UUID, Integer> BALANCES_BY_UUID = new HashMap<>();

    private static final String BALANCES_FILE = "shop_balances.dat";

    private MoneyManager() {}

    // Get balance by EntityPlayer (online), fallback to UUID storage
    public static int getBalanceTenths(EntityPlayer player) {
        Integer bal = BALANCES.get(player);
        if (bal != null) return bal;
        return getBalanceTenths(PlayerIdentityUtil.getOfflineUUID(player.username));
    }

    // Get balance by UUID (offline/online)
    public static int getBalanceTenths(UUID uuid) {
        return BALANCES_BY_UUID.getOrDefault(uuid, 0);
    }

    // Set balance for EntityPlayer (online memory only!)
    public static void setBalanceTenths(EntityPlayer player, int v) {
        BALANCES.put(player, v);
    }

    // Set balance for UUID (offline/online) and persist
    public static void setBalanceTenths(UUID uuid, int v) {
        BALANCES_BY_UUID.put(uuid, v);
        saveBalancesToFile();
    }

    // Add delta to EntityPlayer and update UUID storage (for online player)
    public static void addTenths(EntityPlayer player, int delta) {
        int newBal = getBalanceTenths(player) + delta;
        BALANCES.put(player, newBal);
        setBalanceTenths(PlayerIdentityUtil.getOfflineUUID(player.username), newBal);
    }

    // Add delta to UUID, support offline player, and persist
    public static void addTenths(UUID uuid, int delta) {
        int newBalance = getBalanceTenths(uuid) + delta;
        setBalanceTenths(uuid, newBalance); // This will save to file
        for (EntityPlayer player : BALANCES.keySet()) {
            if (PlayerIdentityUtil.getOfflineUUID(player.username).equals(uuid)) {
                BALANCES.put(player, newBalance);
            }
        }
    }

    public static void saveBalancesToFile() {
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(BALANCES_FILE))) {
            out.writeObject(BALANCES_BY_UUID);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings("unchecked")
    public static void loadBalancesFromFile() {
        File file = new File(BALANCES_FILE);
        if (!file.exists()) return;
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(file))) {
            Map<UUID, Integer> loaded = (Map<UUID, Integer>) in.readObject();
            BALANCES_BY_UUID.clear();
            BALANCES_BY_UUID.putAll(loaded);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}