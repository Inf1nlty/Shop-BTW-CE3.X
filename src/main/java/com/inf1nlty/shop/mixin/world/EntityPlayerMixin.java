package com.inf1nlty.shop.mixin.world;

import com.inf1nlty.shop.util.MoneyManager;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.NBTTagCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Persist currency tenths.
 */
@Mixin(EntityPlayer.class)
public abstract class EntityPlayerMixin {

    @Inject(method = "writeEntityToNBT", at = @At("TAIL"))
    private void shop$write(NBTTagCompound tag, CallbackInfo ci) {
        EntityPlayer p = (EntityPlayer)(Object)this;
        tag.setInteger("shop_money", MoneyManager.getBalanceTenths(p));
    }

    @Inject(method = "readEntityFromNBT", at = @At("TAIL"))
    private void shop$read(NBTTagCompound tag, CallbackInfo ci) {
        EntityPlayer p = (EntityPlayer)(Object)this;
        if (tag.hasKey("shop_money")) {
            MoneyManager.setBalanceTenths(p, tag.getInteger("shop_money"));
        }
    }
}