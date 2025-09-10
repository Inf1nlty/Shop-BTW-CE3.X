package com.inf1nlty.shop.global;

import com.inf1nlty.shop.util.ListingIdGenerator;
import com.inf1nlty.shop.util.PlayerIdentityUtil;
import net.minecraft.src.CompressedStreamTools;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.Item;
import net.minecraft.src.ItemStack;
import net.minecraft.src.NBTTagCompound;

import java.io.*;
import java.util.*;
import java.util.Base64;

/**
 * Global listings persistence with a flat .cfg style file.
 * One line per listing:
 *   id;ownerUUID;ownerName;itemId;meta;amount;priceTenths;base64NBT;type
 * If no NBT, the field is empty.
 * type: "S" for sell, "B" for buy order
 */
public final class GlobalShopData {

    private static final List<GlobalListing> LIST = new ArrayList<>();
    private static final Map<Integer, GlobalListing> INDEX = new HashMap<>();
    private static final Map<UUID, List<GlobalListing>> BY_OWNER = new HashMap<>();

    private static final File FILE = new File("config/global_shop.cfg");

    private GlobalShopData() {}

    public static synchronized void load() {
        LIST.clear();
        INDEX.clear();
        BY_OWNER.clear();
        int maxId = 0;
        if (!FILE.exists()) {
            save();
            ListingIdGenerator.seed(0);
            return;
        }
        try (BufferedReader r = new BufferedReader(new FileReader(FILE))) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split(";", -1);
                if (parts.length < 9) continue;
                GlobalListing g = new GlobalListing();
                try {
                    g.listingId = Integer.parseInt(parts[0]);
                    g.ownerUUID = UUID.fromString(parts[1]);
                    g.ownerName = parts[2];
                    g.itemId = Integer.parseInt(parts[3]);
                    g.meta = Integer.parseInt(parts[4]);
                    g.amount = Integer.parseInt(parts[5]);
                    g.priceTenths = Integer.parseInt(parts[6]);
                    String b64 = parts[7];
                    if (!b64.isEmpty()) {
                        byte[] data = Base64.getDecoder().decode(b64);
                        g.nbt = CompressedStreamTools.readCompressed(new ByteArrayInputStream(data));
                    }
                    g.isBuyOrder = "B".equalsIgnoreCase(parts[8]);
                } catch (Exception ex) {
                    continue; // skip malformed
                }
                Item it = (g.itemId >= 0 && g.itemId < Item.itemsList.length) ? Item.itemsList[g.itemId] : null;
                if (it == null || (!g.isBuyOrder && g.amount <= 0)) continue;
                LIST.add(g);
                INDEX.put(g.listingId, g);
                BY_OWNER.computeIfAbsent(g.ownerUUID, k -> new ArrayList<>()).add(g);
                maxId = Math.max(maxId, g.listingId);
            }
            ListingIdGenerator.seed(maxId);
        } catch (Exception ignored) {
            ListingIdGenerator.seed(0);
        }
    }

    public static synchronized void save() {
        try {
            File dir = FILE.getParentFile();
            if (dir != null && !dir.exists()) dir.mkdirs();
            try (BufferedWriter w = new BufferedWriter(new FileWriter(FILE))) {
                w.write("# Global Shop Listings\n");
                for (GlobalListing g : LIST) {
                    String b64 = "";
                    if (g.nbt != null) {
                        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                            CompressedStreamTools.writeCompressed(g.nbt, bos);
                            b64 = Base64.getEncoder().encodeToString(bos.toByteArray());
                        } catch (Exception ignored) {}
                    }
                    String type = g.isBuyOrder ? "B" : "S";
                    w.write(g.listingId + ";" + g.ownerUUID + ";" + esc(g.ownerName) + ";"
                            + g.itemId + ";" + g.meta + ";" + g.amount + ";" + g.priceTenths + ";" + b64 + ";" + type + "\n");
                }
            }
        } catch (IOException ignored) {}
    }

    private static String esc(String s) {
        return s.replace(";", "_");
    }

    public static synchronized List<GlobalListing> all() {
        List<GlobalListing> out = new ArrayList<>(LIST.size());
        for (GlobalListing g : LIST) out.add(g.copyShallow());
        out.sort(Comparator.comparingInt((GlobalListing gl) -> gl.itemId)
                .thenComparingInt(gl -> gl.meta));
        return out;
    }

    public static synchronized List<GlobalListing> byOwner(UUID owner) {
        List<GlobalListing> src = BY_OWNER.get(owner);
        if (src == null) return List.of();
        List<GlobalListing> out = new ArrayList<>(src.size());
        for (GlobalListing g : src) out.add(g.copyShallow());
        return out;
    }

    public static synchronized GlobalListing get(int id) {
        GlobalListing g = INDEX.get(id);
        return g == null ? null : g;
    }

    public static synchronized GlobalListing addSellOrder(EntityPlayer player, int itemId, int meta, int amount, int priceTenths) {
        GlobalListing g = new GlobalListing();
        g.listingId = ListingIdGenerator.nextId();
        g.ownerName = player.username;
        g.ownerUUID = PlayerIdentityUtil.getOfflineUUID(player.username);
        g.itemId = itemId;
        g.meta = meta;
        g.amount = amount;
        g.priceTenths = priceTenths;
        ItemStack hand = player.inventory.getCurrentItem();
        if (hand != null && hand.stackTagCompound != null) {
            g.nbt = (NBTTagCompound) hand.stackTagCompound.copy();
        }
        g.isBuyOrder = false;
        LIST.add(g);
        INDEX.put(g.listingId, g);
        BY_OWNER.computeIfAbsent(g.ownerUUID, k -> new ArrayList<>()).add(g);
        save();
        return g.copyShallow();
    }

    public static synchronized GlobalListing addBuyOrder(EntityPlayer player, int itemId, int meta, int amount, int priceTenths) {
        GlobalListing g = new GlobalListing();
        g.listingId = ListingIdGenerator.nextId();
        g.ownerName = player.username;
        g.ownerUUID = PlayerIdentityUtil.getOfflineUUID(player.username);
        g.itemId = itemId;
        g.meta = meta;
        g.amount = amount; // -1 for unlimited
        g.priceTenths = priceTenths; // Buy order price set by seller when selling
        g.nbt = null;
        g.isBuyOrder = true;
        LIST.add(g);
        INDEX.put(g.listingId, g);
        BY_OWNER.computeIfAbsent(g.ownerUUID, k -> new ArrayList<>()).add(g);
        save();
        return g.copyShallow();
    }

    public static synchronized GlobalListing remove(int listingId, UUID actor) {
        GlobalListing g = INDEX.get(listingId);
        if (g == null) return null;
        if (!g.ownerUUID.equals(actor)) return null;
        LIST.remove(g);
        INDEX.remove(listingId);
        List<GlobalListing> owned = BY_OWNER.get(actor);
        if (owned != null) owned.remove(g);
        save();
        return g.copyShallow();
    }

    public static synchronized int buy(int listingId, int count) {
        GlobalListing g = INDEX.get(listingId);
        if (g == null) return -1;
        if (count <= 0) count = 1;
        int actual = Math.min(count, g.amount);
        g.amount -= actual;
        if (g.amount <= 0) {
            LIST.remove(g);
            INDEX.remove(listingId);
            List<GlobalListing> owned = BY_OWNER.get(g.ownerUUID);
            if (owned != null) owned.remove(g);
        }
        save();
        return actual;
    }
}