package com.inf1nlty.shop.inventory;

import net.minecraft.src.*;

/**
 * Container exposing only the player's 36 inventory slots.
 * Shift-click transfer is disabled to avoid recursive vanilla transferStack loops
 * when no secondary slot ranges exist (prevents crash in custom shop GUIs).
 */
public class ContainerShopPlayer extends Container {

    private static final int PLAYER_INV_X = 98;
    private static final int PLAYER_INV_Y = 158;
    private static final int HOTBAR_GAP = 4;

    public ContainerShopPlayer(InventoryPlayer inv) {
        // Main inventory (3 * 9)
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                addSlotToContainer(new Slot(
                        inv,
                        c + r * 9 + 9,
                        PLAYER_INV_X + c * 18,
                        PLAYER_INV_Y + r * 18
                ));
            }
        }
        // Hotbar
        int hotbarY = PLAYER_INV_Y + 3 * 18 + HOTBAR_GAP;
        for (int c = 0; c < 9; c++) {
            addSlotToContainer(new Slot(
                    inv,
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

    /**
     * Disable shift-click transfers (returns null immediately).
     */
    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        return null;
    }
}