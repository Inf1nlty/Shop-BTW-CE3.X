package com.inf1nlty.shop.util;

import net.minecraft.src.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.List;

public class ShopChatLocalizer {

    // Map for argument order, must match server-side ARG_ORDER and lang file!
    private static final Map<String, List<String>> ARG_ORDER = Map.ofEntries(
            Map.entry("shop.buy.success", List.of("item", "count", "cost")),
            Map.entry("shop.sell.success", List.of("item", "count", "gain")),
            Map.entry("shop.force.dispose", List.of("item", "count")),
            Map.entry("gshop.listing.add.success", List.of("item", "count", "price")),
            Map.entry("gshop.listing.remove.success", List.of("item", "count", "id")),
            Map.entry("gshop.listing.announce.line1.sell", List.of("player", "item", "count")),
            Map.entry("gshop.listing.announce.line1.buy", List.of("player", "item", "count")),
            Map.entry("gshop.buy.success", List.of("cost", "seller", "item", "count")),
            Map.entry("gshop.sale.success", List.of("buyer", "revenue", "item", "count")),
            Map.entry("gshop.buyorder.add.success", List.of("item", "count")),
            Map.entry("gshop.buyorder.remove.success", List.of("item", "count", "id")),
            Map.entry("gshop.buyorder.sell.success", List.of("item", "count", "buyer")),
            Map.entry("gshop.buyorder.sell.fail_zero", List.of("item")),
            Map.entry("gshop.unlist.item.invalid", List.of("id", "item", "count"))
    );

    /**
     * Try to handle a system/global shop chat message from a raw string.
     * @param raw The raw chat message string.
     * @return true if handled and localized, false otherwise.
     */
    public static boolean tryHandleSystemShopMessage(String raw) {
        System.out.println("[DEBUG] tryHandleSystemShopMessage raw=" + raw);
        if (raw == null || (!raw.contains("shop.") && !raw.contains("gshop.")) || !raw.contains("|")) return false;
        String[] parts = raw.split("\\|");
        if (parts.length < 2) return false;
        String key = parts[0];

        // Supported keys that require item name localization
        Set<String> supportedKeys = ARG_ORDER.keySet();
        if (!supportedKeys.contains(key)) return false;

        int itemId = -1, meta = 0, count = 1;
        String itemName = "unknown";

        Map<String, String> paramMap = new HashMap<>();
        for (String part : parts) {
            int idx = part.indexOf('=');
            if (idx > 0) paramMap.put(part.substring(0, idx), part.substring(idx + 1));
        }
        if (paramMap.containsKey("itemID")) itemId = parseIntSafe(paramMap.get("itemID"));
        if (paramMap.containsKey("meta")) meta = parseIntSafe(paramMap.get("meta"));
        if (paramMap.containsKey("count")) count = parseIntSafe(paramMap.get("count"));

        // Always resolve itemName if possible
        if (itemId >= 0 && itemId < Item.itemsList.length && Item.itemsList[itemId] != null) {
            ItemStack stack = new ItemStack(Item.itemsList[itemId], 1, meta);
            itemName = stack.getDisplayName();
            System.out.println("[DEBUG] itemId=" + itemId
                    + " meta=" + meta
                    + " itemClass=" + Item.itemsList[itemId].getClass().getName()
                    + " unloc=" + stack.getUnlocalizedName()
                    + " display=" + itemName);
        } else {
            System.out.println("[DEBUG] itemId=" + itemId
                    + " meta=" + meta
                    + " itemClass=" + (itemId >= 0 && itemId < Item.itemsList.length ? String.valueOf(Item.itemsList[itemId]) : "null"));
        }

        // Prepare arguments in correct order per key
        List<String> argOrder = ARG_ORDER.get(key);
        Object[] args = new Object[argOrder.size()];
        for (int i = 0; i < argOrder.size(); i++) {
            String arg = argOrder.get(i);
            if ("item".equals(arg)) {
                args[i] = itemName;
            } else if ("count".equals(arg)) {
                args[i] = count;
            } else {
                args[i] = paramMap.getOrDefault(arg, "");
            }
        }

        String localized = I18n.getStringParams(key, args);

        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        if (player != null) player.addChatMessage(localized);
        return true;
    }

    private static int parseIntSafe(String s) {
        try { return Integer.parseInt(s); } catch (Exception ignored) {}
        return 0;
    }
}