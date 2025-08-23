package com.inf1nlty.shop.mixin.client;

import com.inf1nlty.shop.client.input.ShopKeyBindings;
import com.inf1nlty.shop.network.HandshakeClient;
import com.inf1nlty.shop.network.ShopNet;
import net.minecraft.src.EntityClientPlayerMP;
import net.minecraft.src.GuiScreen;
import net.minecraft.src.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Polls custom keys each client tick.
 * B -> open system shop
 * G -> open global shop
 */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

    @Shadow public EntityClientPlayerMP thePlayer;
    @Shadow public GuiScreen currentScreen;

    @Inject(method = "runTick", at = @At("TAIL"))
    private void shop$handleHotkeys(CallbackInfo ci) {
        if (thePlayer == null || currentScreen != null) {
            return;
        }
        if (ShopKeyBindings.OPEN_SHOP.isPressed()) {
            ShopNet.sendOpenRequest();
        } else if (ShopKeyBindings.OPEN_GLOBAL_SHOP.isPressed()) {
            ShopNet.sendGlobalOpenRequest();
        }
        HandshakeClient.onClientTick();
    }
}