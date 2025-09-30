package com.inf1nlty.shop.inventory;

import com.inf1nlty.shop.server.MailboxManager;
import com.inf1nlty.shop.util.PlayerIdentityUtil;
import net.minecraft.src.*;

public class ContainerMailbox extends Container {

    private static final int MAILBOX_COLS = 19;
    private static final int MAILBOX_ROWS = 7;
    private static final int MAILBOX_START_X = 8;
    private static final int MAILBOX_START_Y = 18;
    private static final int SLOT_SIZE = 18;
    private static final int PLAYER_INV_X = 98;
    private static final int PLAYER_INV_Y = 158;
    private static final int HOTBAR_GAP = 4;

    public ContainerMailbox(InventoryPlayer playerInv, IInventory mailboxInv) {
        // Mail(19x7 = 133)
        for (int r = 0; r < MAILBOX_ROWS; r++) {
            for (int c = 0; c < MAILBOX_COLS; c++) {
                addSlotToContainer(new SlotMailbox(
                        mailboxInv,
                        c + r * MAILBOX_COLS,
                        MAILBOX_START_X + c * SLOT_SIZE,
                        MAILBOX_START_Y + r * SLOT_SIZE
                ));
            }
        }
        // Inventory(3*9)
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                addSlotToContainer(new Slot(
                        playerInv,
                        c + r * 9 + 9,
                        PLAYER_INV_X + c * 18,
                        PLAYER_INV_Y + r * 18
                ));
            }
        }
        // Hotbar(9)
        int hotbarY = PLAYER_INV_Y + 3 * 18 + HOTBAR_GAP;
        for (int c = 0; c < 9; c++) {
            addSlotToContainer(new Slot(
                    playerInv,
                    c,
                    PLAYER_INV_X + c * 18,
                    hotbarY
            ));
        }
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return true;
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        Slot slot = (Slot) inventorySlots.get(index);
        if (slot != null && slot.getHasStack()) {
            ItemStack stack = slot.getStack();
            ItemStack original = stack.copy();
            int mailboxSize = MAILBOX_COLS * MAILBOX_ROWS;
            if (index < mailboxSize) {
                if (!mergeItemStack(stack, mailboxSize, inventorySlots.size(), true)) {
                    return null;
                }
                slot.onSlotChange(stack, original);
            } else {
                return null;
            }
            if (stack.stackSize == 0) {
                slot.putStack(null);
            } else {
                slot.onSlotChanged();
            }
            if (!player.worldObj.isRemote) {
                MailboxManager.saveMailbox(PlayerIdentityUtil.getOfflineUUID(player.username),
                        (InventoryBasic) slot.inventory);
            }
            return original;
        }
        return null;
    }
}