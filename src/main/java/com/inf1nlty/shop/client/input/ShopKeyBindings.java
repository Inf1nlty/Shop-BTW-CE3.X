package com.inf1nlty.shop.client.input;

import net.minecraft.src.KeyBinding;
import org.lwjgl.input.Keyboard;

/**
 * Holds custom shop key bindings (system + global + mailbox).
 * All are appended once to GameSettings via GameSettingsMixin.
 */
public final class ShopKeyBindings {

    public static KeyBinding OPEN_SHOP;
    public static KeyBinding OPEN_GLOBAL_SHOP;
    public static KeyBinding OPEN_MAILBOX;

    private static boolean registered;

    private ShopKeyBindings() {}

    /**
     * Returns true only the first time this is called (used to guard array expansion).
     */
    public static boolean markRegistered() {
        if (!registered) {
            registered = true;
            return true;
        }
        return false;
    }

    /**
     * All custom bindings to append.
     */
    public static KeyBinding[] all() {
        return new KeyBinding[]{ OPEN_SHOP, OPEN_GLOBAL_SHOP, OPEN_MAILBOX };
    }
}