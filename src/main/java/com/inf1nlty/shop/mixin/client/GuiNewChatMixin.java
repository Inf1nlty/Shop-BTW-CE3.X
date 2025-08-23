package com.inf1nlty.shop.mixin.client;

import net.minecraft.src.GuiNewChat;
import net.minecraft.src.Minecraft;
import net.minecraft.src.StatCollector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Localizes key|k=v lines and additionally translates raw unlocalized item names inside parameter values
 * when connected to a remote server (server-side display names may be unlocalized).
 */
@Mixin(GuiNewChat.class)
public class GuiNewChatMixin {

    @Unique private static final String[] PREFIXES = { "shop.", "gshop.", "globalshop." };

    @ModifyVariable(method = "drawChat", at = @At(value = "STORE", ordinal = 0), name = "var17")
    private String shop$localize(String original) {
        if (original == null) return null;
        if (!startsWithAny(original)) return original;

        String[] parts = original.split("\\|");
        if (parts.length == 0) return original;
        String key = parts[0];
        String template = StatCollector.translateToLocal(key);
        if (template.equals(key)) return original; // no translation entry

        boolean remote = !Minecraft.getMinecraft().isSingleplayer();

        for (int i = 1; i < parts.length; i++) {
            String seg = parts[i];
            int eq = seg.indexOf('=');
            if (eq <= 0) continue;
            String k = seg.substring(0, eq);
            String v = seg.substring(eq + 1);
            if (remote && (v.startsWith("tile.") || v.startsWith("item.")) && v.endsWith(".name")) {
                String vLoc = StatCollector.translateToLocal(v);
                if (!vLoc.equals(v)) v = vLoc;
            }
            template = template.replace("{" + k + "}", v);
        }
        return template;
    }

    @Unique
    private boolean startsWithAny(String s) {
        for (String p : PREFIXES) if (s.startsWith(p)) return true;
        return false;
    }
}