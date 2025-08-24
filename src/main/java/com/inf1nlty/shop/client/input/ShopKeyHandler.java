package com.inf1nlty.shop.client.input;

import com.inf1nlty.shop.network.ShopNet;
import net.minecraft.src.Minecraft;
import org.lwjgl.input.Keyboard;

public class ShopKeyHandler {

    private static boolean wasF3Down = false;
    private static int f3ReleaseDelay = 0;

    public static void onClientTick() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.currentScreen != null) {
            wasF3Down = Keyboard.isKeyDown(Keyboard.KEY_F3);
            f3ReleaseDelay = 0;
            return;
        }

        boolean isF3Down = Keyboard.isKeyDown(Keyboard.KEY_F3);

        // Block shop hotkeys while F3 is held or shortly after release
        if (isF3Down) {
            wasF3Down = true;
            f3ReleaseDelay = 2;
            clearShopKeys();
            return;
        }

        if (wasF3Down) {
            if (f3ReleaseDelay > 0) {
                f3ReleaseDelay--;
                clearShopKeys();
                if (f3ReleaseDelay == 0) wasF3Down = false;
                return;
            }
            wasF3Down = false;
        }

        if (ShopKeyBindings.OPEN_SHOP.isPressed()) {
            ShopNet.sendOpenRequest();
        } else if (ShopKeyBindings.OPEN_GLOBAL_SHOP.isPressed()) {
            ShopNet.sendGlobalOpenRequest();
        } else if (ShopKeyBindings.OPEN_MAILBOX.isPressed()) {
            ShopNet.sendMailboxOpen();
        }
    }

    // Clear pressed status and event queue for shop hotkeys
    private static void clearShopKeys() {
        ShopKeyBindings.OPEN_SHOP.pressed = false;
        ShopKeyBindings.OPEN_GLOBAL_SHOP.pressed = false;
        ShopKeyBindings.OPEN_MAILBOX.pressed = false;
        try {
            ShopKeyBindings.OPEN_SHOP.pressTime = 0;
            ShopKeyBindings.OPEN_GLOBAL_SHOP.pressTime = 0;
            ShopKeyBindings.OPEN_MAILBOX.pressTime = 0;
        } catch (Throwable ignore) {}
        while (Keyboard.next()) {
            int k = Keyboard.getEventKey();
            if (k == Keyboard.KEY_G || k == Keyboard.KEY_B || k == Keyboard.KEY_M) {
                // discard event
            }
        }
    }
}