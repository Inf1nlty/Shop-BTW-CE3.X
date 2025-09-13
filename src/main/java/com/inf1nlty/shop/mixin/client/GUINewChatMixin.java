package com.inf1nlty.shop.mixin.client;

import com.inf1nlty.shop.util.ShopChatLocalizer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.src.GuiNewChat;

/**
 * Mixin for intercepting chat message display in GuiNewChat to handle system shop localization.
 */
@Mixin(GuiNewChat.class)
public class GUINewChatMixin {

    /**
     * Intercept printChatMessageWithOptionalDeletion to localize system shop messages.
     * @param string The raw chat message string.
     * @param id The chat line id.
     * @param ci The callback info.
     */
    @Inject(method = "printChatMessageWithOptionalDeletion", at = @At("HEAD"), cancellable = true)
    private void onPrintChatMessageWithOptionalDeletion(String string, int id, CallbackInfo ci) {
        if (ShopChatLocalizer.tryHandleSystemShopMessage(string)) {
            ci.cancel();
        }
    }
}