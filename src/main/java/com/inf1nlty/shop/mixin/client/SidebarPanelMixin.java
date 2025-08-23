package com.inf1nlty.shop.mixin.client;

import emi.dev.emi.emi.screen.EmiScreenManager.SidebarPanel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Defensive null check to prevent crashes when third-party panels have a null space.
 */
@Mixin(value = SidebarPanel.class, remap = false)
public class SidebarPanelMixin {

    @Inject(method = "isVisible", at = @At("HEAD"), cancellable = true)
    private void safeIsVisible(CallbackInfoReturnable<Boolean> cir) {
        SidebarPanel self = (SidebarPanel) (Object) this;
        if (self.space == null) {
            cir.setReturnValue(false);
        }
    }
}