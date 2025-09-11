package com.inf1nlty.shop.mixin.world;

import com.inf1nlty.shop.server.MailboxManager;
import com.inf1nlty.shop.util.MoneyManager;
import com.inf1nlty.shop.util.PlayerIdentityUtil;
import net.minecraft.src.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;
import java.util.UUID;

/**
 * Persist currency tenths.
 */
@Mixin(EntityPlayer.class)
public abstract class EntityPlayerMixin {

    @Inject(method = "writeEntityToNBT", at = @At("TAIL"))
    private void shop$write(NBTTagCompound tag, CallbackInfo ci) {
        EntityPlayer player = (EntityPlayer)(Object)this;
        // Always write UUID-based balance for full offline/online sync
        tag.setInteger("shop_money", MoneyManager.getBalanceTenths(PlayerIdentityUtil.getOfflineUUID(player.username)));
        NBTTagList mailboxList = new NBTTagList();
        InventoryBasic inv = MailboxManager.getMailbox(PlayerIdentityUtil.getOfflineUUID(player.username));
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (stack != null) {
                NBTTagCompound c = new NBTTagCompound();
                stack.writeToNBT(c);
                c.setInteger("Slot", i);
                mailboxList.appendTag(c);
            }
        }
        tag.setTag("shop_mailbox", mailboxList);
    }

    @Inject(method = "readEntityFromNBT", at = @At("TAIL"))
    private void shop$read(NBTTagCompound tag, CallbackInfo ci) {
        EntityPlayer player = (EntityPlayer)(Object)this;
        int uuidBalance = MoneyManager.getBalanceTenths(PlayerIdentityUtil.getOfflineUUID(player.username));
        MoneyManager.setBalanceTenths(player, uuidBalance);

        UUID uuid = PlayerIdentityUtil.getOfflineUUID(player.username);
        InventoryBasic inv = MailboxManager.getMailbox(uuid);

        boolean mailboxFromDisk = false;
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            if (inv.getStackInSlot(i) != null) {
                mailboxFromDisk = true;
                break;
            }
        }
        if (!mailboxFromDisk && tag.hasKey("shop_mailbox")) {
            Arrays.fill(inv.inventoryContents, null);
            NBTTagList mailboxList = tag.getTagList("shop_mailbox");
            for (int i = 0; i < mailboxList.tagCount(); i++) {
                NBTTagCompound c = (NBTTagCompound) mailboxList.tagAt(i);
                int slot = c.getInteger("Slot");
                ItemStack stack = ItemStack.loadItemStackFromNBT(c);
                if (slot >= 0 && slot < inv.getSizeInventory() && stack != null) {
                    inv.setInventorySlotContents(slot, stack);
                }
            }
            MailboxManager.saveMailbox(uuid, inv);
        }
    }
}