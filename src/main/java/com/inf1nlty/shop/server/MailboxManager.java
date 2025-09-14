package com.inf1nlty.shop.server;

import net.minecraft.src.*;

import java.io.*;
import java.util.*;

/**
 * Manages mailbox inventories per player for buy order fulfillment.
 * Mailbox is persisted per player.
 */
public class MailboxManager {

    private static final Map<UUID, InventoryBasic> MAILBOXES = new HashMap<>();
    private static File SHOP_DIR = null;
    private static File MAILBOX_DIR = null;
    private static boolean initialized = false;


    private MailboxManager() {}

    public static void init(File shopDir) {
        if (initialized) return;
        SHOP_DIR = shopDir;
        MAILBOX_DIR = new File(SHOP_DIR, "mailboxes");
        if (!MAILBOX_DIR.exists()) {
            boolean created = MAILBOX_DIR.mkdirs();
            if (!created) {
                System.err.println("Failed to create mailbox directory: " + MAILBOX_DIR.getAbsolutePath());
            }
        }
        initialized = true;
    }

    private static void ensureInitialized() {
        if (!initialized || SHOP_DIR == null || MAILBOX_DIR == null) {
            throw new IllegalStateException("MailboxManager is not initialized! Call MailboxManager.init(shopDir) after world load.");
        }
    }

    public static InventoryBasic getMailbox(UUID playerId) {
        ensureInitialized();
        InventoryBasic inv = MAILBOXES.get(playerId);
        if (inv == null) {
            inv = loadMailbox(playerId);
            MAILBOXES.put(playerId, inv);
        }
        return inv;
    }

    public static void deliver(UUID playerId, ItemStack stack) {
        ensureInitialized();
        InventoryBasic inv = getMailbox(playerId);
        addToInventory(inv, stack);
        saveMailbox(playerId, inv);
    }

    /**
     * Adds an item stack to an inventory, merging with existing stacks if possible.
     * Returns true if all items were added, false if some could not be added.
     */
    public static boolean addToInventory(IInventory inv, ItemStack stack) {
        if (stack == null || stack.stackSize <= 0) return false;
        int remaining = stack.stackSize;
        Item item = stack.getItem();
        int max = item.getItemStackLimit();
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack slot = inv.getStackInSlot(i);
            if (slot != null && slot.itemID == stack.itemID && slot.getItemDamage() == stack.getItemDamage()
                    && (Objects.equals(slot.stackTagCompound, stack.stackTagCompound))) {
                int space = max - slot.stackSize;
                if (space > 0) {
                    int toAdd = Math.min(space, remaining);
                    slot.stackSize += toAdd;
                    remaining -= toAdd;
                    if (remaining == 0) return true;
                }
            }
        }
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack slot = inv.getStackInSlot(i);
            if (slot == null) {
                int toAdd = Math.min(max, remaining);
                ItemStack copy = stack.copy();
                copy.stackSize = toAdd;
                inv.setInventorySlotContents(i, copy);
                remaining -= toAdd;
                if (remaining <= 0) return true;
            }
        }
        return remaining <= 0;
    }

    public static void flushAllMailboxesToNBT(List<EntityPlayerMP> players) {
        for (EntityPlayerMP p : players) {
            NBTTagCompound tag = new NBTTagCompound();
            p.writeEntityToNBT(tag);
        }
    }

    public static void saveMailbox(UUID playerId, InventoryBasic inv) {
        ensureInitialized();
        try {
            if (!MAILBOX_DIR.exists()) MAILBOX_DIR.mkdirs();
            File mailboxFile = new File(MAILBOX_DIR, playerId.toString() + ".dat");
            NBTTagList nbtList = new NBTTagList();
            for (int i = 0; i < inv.getSizeInventory(); i++) {
                ItemStack stack = inv.getStackInSlot(i);
                if (stack != null) {
                    NBTTagCompound c = new NBTTagCompound();
                    stack.writeToNBT(c);
                    c.setInteger("Slot", i);
                    nbtList.appendTag(c);
                }
            }
            NBTTagCompound root = new NBTTagCompound();
            root.setTag("mailbox", nbtList);
            CompressedStreamTools.writeCompressed(root, new FileOutputStream(mailboxFile));
        } catch (Exception ignored) {}
    }

    private static InventoryBasic loadMailbox(UUID playerId) {
        ensureInitialized();
        File mailboxFile = new File(MAILBOX_DIR, playerId.toString() + ".dat");
        InventoryBasic inv = new InventoryBasic("Mailbox", false, 133);
        if (!mailboxFile.exists()) return inv;
        try {
            NBTTagCompound root = CompressedStreamTools.readCompressed(new FileInputStream(mailboxFile));
            if (root != null && root.hasKey("mailbox")) {
                NBTTagList nbtList = root.getTagList("mailbox");
                for (int i = 0; i < nbtList.tagCount(); i++) {
                    NBTTagCompound c = (NBTTagCompound) nbtList.tagAt(i);
                    int slot = c.getInteger("Slot");
                    ItemStack stack = ItemStack.loadItemStackFromNBT(c);
                    if (slot >= 0 && slot < inv.getSizeInventory() && stack != null) {
                        inv.setInventorySlotContents(slot, stack);
                    }
                }
            }
        } catch (Exception ignored) {}
        return inv;
    }

}