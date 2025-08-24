package com.inf1nlty.shop.server;

import net.minecraft.src.*;

import java.util.*;

/**
 * Manages mailbox inventories per player for buy order fulfillment.
 * Mailbox is persisted per player.
 */
public class MailboxManager {

    private static final Map<UUID, InventoryBasic> MAILBOXES = new HashMap<>();

    private MailboxManager() {}

    public static InventoryBasic getMailbox(UUID playerId) {
        return MAILBOXES.computeIfAbsent(playerId, id -> new InventoryBasic("Mailbox", false, 133));
    }

    public static void deliver(UUID playerId, ItemStack stack) {
        InventoryBasic inv = getMailbox(playerId);
        addToInventory(inv, stack);
    }

    public static void removeMailbox(UUID playerId) {
        MAILBOXES.remove(playerId);
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

}