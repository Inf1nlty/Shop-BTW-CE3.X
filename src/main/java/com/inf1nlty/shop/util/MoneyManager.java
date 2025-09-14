package com.inf1nlty.shop.util;

import net.minecraft.src.EntityPlayer;

import java.io.*;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.HashMap;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * In-memory balances in tenths.
 * Persisted via EntityPlayerMixin in "shop_money" as int tenths.
 * Also persisted for offline players in shop_balances.dat.
 */
public class MoneyManager {

    private static final WeakHashMap<EntityPlayer, Integer> BALANCES = new WeakHashMap<>();
    // Global UUID balance table, supports offline player balance get/set
    private static final Map<UUID, Integer> BALANCES_BY_UUID = new HashMap<>();

    private static final Logger LOGGER = Logger.getLogger(MoneyManager.class.getName());

    private static File SHOP_DIR = null;
    private static File BALANCES_FILE = null;
    private static boolean initialized = false;


    public static void init(File shopDir) {
        if (initialized) return;
        SHOP_DIR = shopDir;
        BALANCES_FILE = new File(SHOP_DIR, "shop_balances.dat");
        if (!SHOP_DIR.exists()) {
            boolean created = SHOP_DIR.mkdirs();
            if (!created) {
                LOGGER.warning("Failed to create shop directory: " + SHOP_DIR.getAbsolutePath());
            }
        }
        initialized = true;
    }

    private static void ensureInitialized() {
        if (!initialized || SHOP_DIR == null || BALANCES_FILE == null) {
            throw new IllegalStateException("MoneyManager is not initialized! Call MoneyManager.init(shopDir) after world load.");
        }
    }

    private MoneyManager() {}

    // Get balance by EntityPlayer (online), fallback to UUID storage
    public static int getBalanceTenths(EntityPlayer player) {
        ensureInitialized();
        Integer bal = BALANCES.get(player);
        if (bal != null) return bal;
        return getBalanceTenths(PlayerIdentityUtil.getOfflineUUID(player.username));
    }

    // Get balance by UUID (offline/online)
    public static int getBalanceTenths(UUID uuid) {
        ensureInitialized();
        return BALANCES_BY_UUID.getOrDefault(uuid, 0);
    }

    // Set balance for EntityPlayer (online memory only!)
    public static void setBalanceTenths(EntityPlayer player, int v) {
        ensureInitialized();
        BALANCES.put(player, v);
    }

    // Set balance for UUID (offline/online) and persist
    public static void setBalanceTenths(UUID uuid, int v) {
        ensureInitialized();
        BALANCES_BY_UUID.put(uuid, v);
        saveBalancesToFile();
    }

    // Add delta to EntityPlayer and update UUID storage (for online player)
    public static void addTenths(EntityPlayer player, int delta) {
        ensureInitialized();
        int newBal = getBalanceTenths(player) + delta;
        BALANCES.put(player, newBal);
        setBalanceTenths(PlayerIdentityUtil.getOfflineUUID(player.username), newBal);
    }

    // Add delta to UUID, support offline player, and persist
    public static void addTenths(UUID uuid, int delta) {
        ensureInitialized();
        int newBalance = getBalanceTenths(uuid) + delta;
        setBalanceTenths(uuid, newBalance); // This will save to file
        for (EntityPlayer player : BALANCES.keySet()) {
            if (PlayerIdentityUtil.getOfflineUUID(player.username).equals(uuid)) {
                BALANCES.put(player, newBalance);
            }
        }
    }

    public static void saveBalancesToFile() {
        ensureInitialized();
        try {
            if (!SHOP_DIR.exists()) {
                boolean created = SHOP_DIR.mkdirs();
                if (!created) {
                    LOGGER.warning("Failed to create shop directory: " + SHOP_DIR.getAbsolutePath());
                }
            }
            try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(BALANCES_FILE))) {
                out.writeObject(BALANCES_BY_UUID);
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to save balances", e);
        }
    }

    @SuppressWarnings("unchecked")
    public static void loadBalancesFromFile() {
        ensureInitialized();
        if (!BALANCES_FILE.exists()) return;
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(BALANCES_FILE))) {
            Map<UUID, Integer> loaded = (Map<UUID, Integer>) in.readObject();
            BALANCES_BY_UUID.clear();
            BALANCES_BY_UUID.putAll(loaded);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to load balances", e);
        }
    }

    public static void clearStatic() {
        BALANCES.clear();
        BALANCES_BY_UUID.clear();
        SHOP_DIR = null;
        BALANCES_FILE = null;
        initialized = false;
    }

}