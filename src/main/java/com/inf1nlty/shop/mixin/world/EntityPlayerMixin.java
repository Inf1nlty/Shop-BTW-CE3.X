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
        if (tag.hasKey("shop_money")) {
            int money = tag.getInteger("shop_money");
            // Sync both EntityPlayer and UUID balance, always keep UUID as source of truth
            MoneyManager.setBalanceTenths(player, money);
        }
        InventoryBasic inv = MailboxManager.getMailbox(PlayerIdentityUtil.getOfflineUUID(player.username));
        Arrays.fill(inv.inventoryContents, null);
        if (tag.hasKey("shop_mailbox")) {
            NBTTagList mailboxList = tag.getTagList("shop_mailbox");
            for (int i = 0; i < mailboxList.tagCount(); i++) {
                NBTTagCompound c = (NBTTagCompound) mailboxList.tagAt(i);
                int slot = c.getInteger("Slot");
                ItemStack stack = ItemStack.loadItemStackFromNBT(c);
                if (slot >= 0 && slot < inv.getSizeInventory() && stack != null) {
                    inv.setInventorySlotContents(slot, stack);
                }
            }
        }
    }
}