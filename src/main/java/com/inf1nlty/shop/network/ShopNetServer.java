package com.inf1nlty.shop.network;

import com.inf1nlty.shop.ShopConfig;
import com.inf1nlty.shop.ShopItem;
import com.inf1nlty.shop.global.GlobalListing;
import com.inf1nlty.shop.global.GlobalShopData;
import com.inf1nlty.shop.inventory.ContainerShopPlayer;
import com.inf1nlty.shop.util.Money;
import com.inf1nlty.shop.util.MoneyManager;
import com.inf1nlty.shop.util.PlayerIdentityUtil;
import net.minecraft.src.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * System & Global shop server logic.
 * Adds inventory capacity check before purchases.
 */
public class ShopNetServer {

    private static final List<EntityPlayerMP> GLOBAL_VIEWERS = new ArrayList<>();
    private static long lastConfigTouch = 0L;

    private ShopNetServer() {}

    public static void handlePacket(Packet250CustomPayload packet, EntityPlayerMP player) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(packet.data))) {
            byte a = in.readByte();
            switch (a) {
                case 2 -> { ensureConfig(true); int id = in.readInt(); int meta = in.readInt(); int count = in.readInt(); buySystem(player, id, meta, count); }
                case 3 -> { ensureConfig(true); int id = in.readInt(); int count = in.readInt(); int slot = in.readInt(); sellSystem(player, id, count, slot); }
                case 6 -> { ensureConfig(false); openSystemShop(player); }
                case 8 -> openGlobalShop(player);
                case 9 -> { int lid = in.readInt(); int cnt = in.readInt(); buyGlobal(player, lid, cnt); }
                case 10 -> { int item = in.readInt(); int meta = in.readInt(); int amt = in.readInt(); int price = in.readInt(); listGlobal(player, item, meta, amt, price); }
                case 11 -> { int lid = in.readInt(); unlistGlobal(player, lid); }
                default -> {}
            }
        } catch (Exception ignored) {}
    }

    public static void ensureConfig(boolean force) {
        long now = System.currentTimeMillis();
        if (force || now - lastConfigTouch > 4000 || ShopConfig.getItems().isEmpty()) {
            ShopConfig.forceReload();
            lastConfigTouch = now;
        }
    }

    // ===== System Shop =====

    public static void openSystemShop(EntityPlayerMP player) {
        player.currentWindowId = (player.currentWindowId % 100) + 1;
        ContainerShopPlayer c = new ContainerShopPlayer(player.inventory);
        c.windowId = player.currentWindowId;
        player.openContainer = c;
        sendSystemOpen(player);
    }

    private static void sendSystemOpen(EntityPlayerMP player) {
        try {
            int bal = MoneyManager.getBalanceTenths(player);
            List<ShopItem> items = ShopConfig.getItems();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bos);
            out.writeByte(4);
            out.writeInt(player.openContainer.windowId);
            out.writeInt(bal);
            out.writeInt(items.size());
            for (ShopItem si : items) {
                out.writeInt(si.itemID);
                out.writeInt(si.damage);
                out.writeInt(si.buyPriceTenths);
                out.writeInt(si.sellPriceTenths);
            }
            send(player, bos);
            syncInventory(player);
        } catch (Exception ignored) {}
    }

    private static void buySystem(EntityPlayerMP player, int itemID, int meta, int count) {
        if (count <= 0) count = 1;
        ShopItem si = ShopConfig.get(itemID, meta);
        if (si == null) { sendResult(player, "shop.item_not_supported"); return; }
        int cost = si.buyPriceTenths * count;
        if (cost <= 0) { sendResult(player, "shop.item_not_supported"); return; }
        if (MoneyManager.getBalanceTenths(player) < cost) { sendResult(player, "shop.not_enough_money"); return; }

        ItemStack purchase = new ItemStack(si.itemStack.getItem(), count, si.damage);
        if (!canFit(player, purchase)) {
            sendResult(player, "shop.inventory.full");
            return;
        }

        if (player.inventory.addItemStackToInventory(purchase)) {
            MoneyManager.addTenths(player, -cost);
            sendResult(player, "shop.buy.success|item=" + purchase.getDisplayName() + "|count=" + count + "|cost=" + Money.format(cost));
            syncInventory(player);
        } else {
            // Fallback (should not reach if canFit passed)
            sendResult(player, "shop.inventory.full");
        }
    }

    private static void sellSystem(EntityPlayerMP player, int itemID, int count, int slotIndex) {
        if (count <= 0) count = 1;
        if (slotIndex < 0 || slotIndex >= player.inventory.mainInventory.length) { sendResult(player, "shop.failed"); return; }
        ItemStack target = player.inventory.mainInventory[slotIndex];
        if (target == null || target.itemID != itemID) { sendResult(player, "shop.failed"); return; }
        ShopItem si = ShopConfig.get(itemID, target.getItemDamage());
        if (si == null) {
            if (ShopConfig.FORCE_SELL_UNLISTED) {
                int removed = removeFromSlot(player, slotIndex, count);
                if (removed > 0) {
                    sendResult(player, "shop.force.dispose|item=" + target.getDisplayName() + "|count=" + removed);
                    syncInventory(player);
                } else sendResult(player, "shop.failed");
            } else sendResult(player, "shop.item_not_supported");
            return;
        }
        int removed = removeFromSlot(player, slotIndex, count);
        if (removed > 0) {
            int gain = si.sellPriceTenths * removed;
            if (gain > 0) MoneyManager.addTenths(player, gain);
            sendResult(player, "shop.sell.success|item=" + target.getDisplayName() + "|count=" + removed + "|gain=" + Money.format(gain));
            syncInventory(player);
        } else sendResult(player, "shop.failed");
    }

    // ===== Global Shop =====

    public static void openGlobalShop(EntityPlayerMP player) {
        if (!GLOBAL_VIEWERS.contains(player)) GLOBAL_VIEWERS.add(player);
        player.currentWindowId = (player.currentWindowId % 100) + 1;
        ContainerShopPlayer c = new ContainerShopPlayer(player.inventory);
        c.windowId = player.currentWindowId;
        player.openContainer = c;
        sendGlobalSnapshot(player);
    }

    public static void broadcastGlobalSnapshot() {
        for (EntityPlayerMP v : new ArrayList<>(GLOBAL_VIEWERS)) {
            if (v.openContainer == null) continue;
            sendGlobalSnapshot(v);
        }
    }

    private static void sendGlobalSnapshot(EntityPlayerMP player) {
        try {
            int bal = MoneyManager.getBalanceTenths(player);
            List<GlobalListing> listings = GlobalShopData.all();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bos);
            out.writeByte(7);
            out.writeInt(player.openContainer.windowId);
            out.writeInt(bal);
            out.writeInt(listings.size());
            for (GlobalListing gl : listings) {
                out.writeInt(gl.listingId);
                out.writeInt(gl.itemId);
                out.writeInt(gl.meta);
                out.writeInt(gl.amount);
                out.writeInt(gl.priceTenths);
                out.writeUTF(gl.ownerName);
                if (gl.nbt != null) {
                    byte[] comp = compressNBT(gl.nbt);
                    out.writeBoolean(true);
                    out.writeShort(comp.length);
                    out.write(comp);
                } else out.writeBoolean(false);
            }
            send(player, bos);
        } catch (Exception ignored) {}
    }

    private static byte[] compressNBT(NBTTagCompound tag) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            CompressedStreamTools.writeCompressed(tag, bos);
            return bos.toByteArray();
        } catch (Exception e) { return new byte[0]; }
    }

    private static void buyGlobal(EntityPlayerMP buyer, int listingId, int requestedCount) {
        GlobalListing gl = GlobalShopData.get(listingId);
        if (gl == null) { sendResult(buyer, "gshop.buy.not_found"); return; }
        if (PlayerIdentityUtil.getOfflineUUID(buyer.username).equals(gl.ownerUUID)) { sendResult(buyer, "gshop.buy.self_not_allowed"); return; }
        if (requestedCount <= 0) requestedCount = 1;
        int tentativeCount = Math.min(requestedCount, gl.amount);
        if (tentativeCount <= 0) { sendResult(buyer, "gshop.buy.not_found"); return; }

        int tentativeCost = gl.priceTenths * tentativeCount;
        if (MoneyManager.getBalanceTenths(buyer) < tentativeCost) { sendResult(buyer, "gshop.buy.not_enough_money"); return; }

        Item item = (gl.itemId >= 0 && gl.itemId < Item.itemsList.length) ? Item.itemsList[gl.itemId] : null;
        if (item == null) { sendResult(buyer, "gshop.buy.not_found"); return; }

        ItemStack testStack = new ItemStack(item, tentativeCount, gl.meta);
        if (gl.nbt != null) testStack.stackTagCompound = (NBTTagCompound) gl.nbt.copy();
        if (!canFit(buyer, testStack)) {
            sendResult(buyer, "gshop.inventory.full");
            return;
        }

        // Remove from listing only after space & funds confirmed
        int bought = GlobalShopData.buy(listingId, tentativeCount);
        if (bought <= 0) { sendResult(buyer, "gshop.buy.not_found"); return; }

        int finalCost = gl.priceTenths * bought;
        if (MoneyManager.getBalanceTenths(buyer) < finalCost) {
            // Extremely rare race: balance changed between checks -> refund listing (not implemented)
            sendResult(buyer, "gshop.buy.not_enough_money");
            return;
        }

        ItemStack deliver = new ItemStack(item, bought, gl.meta);
        if (gl.nbt != null) deliver.stackTagCompound = (NBTTagCompound) gl.nbt.copy();

        if (!buyer.inventory.addItemStackToInventory(deliver)) {
            // Should not happen after canFit; fallback cancel purchase (listing already reduced)
            // Drop on ground as last resort
            buyer.dropPlayerItem(deliver);
        } else {
            MoneyManager.addTenths(buyer, -finalCost);
            int revenue = gl.priceTenths * bought;
            for (Object o : buyer.mcServer.getConfigurationManager().playerEntityList) {
                EntityPlayerMP online = (EntityPlayerMP) o;
                if (PlayerIdentityUtil.getOfflineUUID(online.username).equals(gl.ownerUUID)) {
                    MoneyManager.addTenths(online, revenue);
                    online.addChatMessage("gshop.sale.success|buyer=" + buyer.username
                            + "|item=" + deliver.getDisplayName()
                            + "|count=" + bought
                            + "|revenue=" + Money.format(revenue));
                }
            }
            buyer.addChatMessage("gshop.buy.success|item=" + deliver.getDisplayName()
                    + "|count=" + bought
                    + "|cost=" + Money.format(finalCost)
                    + "|seller=" + gl.ownerName);
        }

        broadcastGlobalSnapshot();
        syncInventory(buyer);
    }

    private static void listGlobal(EntityPlayerMP player, int itemId, int meta, int amount, int priceTenths) {
        if (priceTenths <= 0 || amount <= 0) { sendResult(player, "gshop.listing.add.fail_price"); return; }
        broadcastGlobalSnapshot();
    }

    private static void unlistGlobal(EntityPlayerMP player, int listingId) {
        GlobalListing gl = GlobalShopData.get(listingId); // 只获取，不先 remove
        if (gl == null) {
            sendResult(player, "gshop.listing.remove.not_found|id=" + listingId);
            return;
        }
        if (!PlayerIdentityUtil.getOfflineUUID(player.username).equals(gl.ownerUUID)) {
            sendResult(player, "gshop.listing.remove.not_owner|id=" + listingId);
            return;
        }
        Item item = (gl.itemId >= 0 && gl.itemId < Item.itemsList.length) ? Item.itemsList[gl.itemId] : null;
        if (item == null) {
            sendResult(player, "gshop.unlist.item.invalid|id=" + gl.listingId
                    + "|item=" + buildDisplayName(gl)
                    + "|count=" + gl.amount);
            broadcastGlobalSnapshot();
            return;
        }
        // 正式移除 listing
        GlobalListing removed = GlobalShopData.remove(listingId, PlayerIdentityUtil.getOfflineUUID(player.username));
        if (removed != null) {
            int remaining = removed.amount;
            int max = item.getItemStackLimit();
            while (remaining > 0) {
                int take = Math.min(max, remaining);
                ItemStack stack = new ItemStack(item, take, removed.meta);
                if (removed.nbt != null) stack.stackTagCompound = (NBTTagCompound) removed.nbt.copy();
                // 直接丢到玩家脚下，无需检测背包
                player.dropPlayerItem(stack);
                remaining -= take;
            }
            sendResult(player, "gshop.listing.remove.success|id=" + removed.listingId
                    + "|item=" + buildDisplayName(removed)
                    + "|count=" + removed.amount);
            broadcastGlobalSnapshot();
        }
    }

    private static String buildDisplayName(GlobalListing gl) {
        Item item = (gl.itemId >= 0 && gl.itemId < Item.itemsList.length) ? Item.itemsList[gl.itemId] : null;
        if (item == null) return "unknown";
        ItemStack one = new ItemStack(item, 1, gl.meta);
        if (gl.nbt != null) one.stackTagCompound = (NBTTagCompound) gl.nbt.copy();
        return one.getDisplayName();
    }

    // ===== Shared Utility =====

    private static int removeFromSlot(EntityPlayerMP player, int slotIndex, int want) {
        ItemStack st = player.inventory.mainInventory[slotIndex];
        if (st == null) return 0;
        int removed = Math.min(st.stackSize, want);
        st.stackSize -= removed;
        if (st.stackSize <= 0) player.inventory.mainInventory[slotIndex] = null;
        return removed;
    }

    private static void sendResult(EntityPlayerMP player, String keyWithParams) {
        try {
            int bal = MoneyManager.getBalanceTenths(player);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bos);
            out.writeByte(1);
            out.writeBoolean(true);
            out.writeUTF(keyWithParams);
            out.writeInt(bal);
            send(player, bos);
        } catch (Exception ignored) {}
    }

    private static void syncInventory(EntityPlayerMP player) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bos);
            out.writeByte(5);
            for (ItemStack st : player.inventory.mainInventory) {
                if (st == null) out.writeShort(-1);
                else {
                    out.writeShort((short) st.itemID);
                    out.writeByte(st.stackSize);
                    out.writeShort((short) st.getItemDamage());
                }
            }
            send(player, bos);
        } catch (Exception ignored) {}
    }

    private static void send(EntityPlayerMP player, ByteArrayOutputStream bos) {
        Packet250CustomPayload p = new Packet250CustomPayload(ShopNet.CHANNEL, bos.toByteArray());
        player.playerNetServerHandler.sendPacket(p);
    }

    /**
     * Simulates whether full stack can fit (merging with existing stacks and using empty slots).
     */
    private static boolean canFit(EntityPlayerMP player, ItemStack stack) {
        if (stack == null || stack.stackSize <= 0) return true;
        int remaining = stack.stackSize;
        Item item = stack.getItem();
        int maxStack = item.getItemStackLimit();
        ItemStack[] inv = player.inventory.mainInventory;

        // First pass: existing partial stacks
        for (ItemStack slot : inv) {
            if (slot == null) continue;
            if (slot.itemID == stack.itemID && slot.getItemDamage() == stack.getItemDamage() &&
                    nbtEqual(slot.stackTagCompound, stack.stackTagCompound)) {
                int space = Math.min(maxStack, slot.getMaxStackSize()) - slot.stackSize;
                if (space > 0) {
                    int used = Math.min(space, remaining);
                    remaining -= used;
                    if (remaining <= 0) return true;
                }
            }
        }
        // Second pass: empty slots
        for (ItemStack slot : inv) {
            if (slot == null) {
                int used = Math.min(maxStack, remaining);
                remaining -= used;
                if (remaining <= 0) return true;
            }
        }
        return remaining <= 0;
    }

    private static boolean nbtEqual(NBTTagCompound a, NBTTagCompound b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }
}