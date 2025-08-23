package com.inf1nlty.shop.mixin.client;

import com.inf1nlty.shop.ShopConfig;
import com.inf1nlty.shop.ShopItem;
import com.inf1nlty.shop.client.state.ShopClientData;
import com.inf1nlty.shop.util.Money;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.Item;
import net.minecraft.src.ItemStack;
import net.minecraft.src.StatCollector;
import net.minecraft.src.NBTTagCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Tooltip augmentation for system/global shop contexts.
 * Prefers NBT-embedded price tags (server authoritative) before fallback to client cfg.
 */
@Mixin(Item.class)
public abstract class ItemMixin {

    @Unique private static final String ZERO = "0.0";

    @SuppressWarnings({"rawtypes","unchecked"})
    @Inject(method = "addInformation", at = @At("RETURN"))
    private void shop$append(ItemStack stack, EntityPlayer player, List lines, boolean advanced, CallbackInfo ci) {
        if (stack == null) return;

        // Global shop
        if (ShopClientData.inGlobalShop &&
                stack.stackTagCompound != null &&
                stack.stackTagCompound.hasKey("GShopPriceTenths")) {

            int priceTenths = stack.stackTagCompound.getInteger("GShopPriceTenths");
            int amount = stack.stackTagCompound.getInteger("GShopAmount");
            String seller = stack.stackTagCompound.getString("GShopSeller");

            lines.add("§e" + StatCollector.translateToLocal("gshop.price") + ": §f" + Money.format(priceTenths));
            lines.add("§a" + StatCollector.translateToLocal("gshop.amount") + ": §f" + amount);
            lines.add("§b" + StatCollector.translateToLocal("gshop.seller") + ": §f" + seller);
            return;
        }

        // System shop
        if (ShopClientData.inShop) {
            int buy = 0;
            int sell = 0;
            NBTTagCompound tag = stack.stackTagCompound;
            if (tag != null && tag.hasKey("ShopBuyPrice")) {
                buy = tag.getInteger("ShopBuyPrice");
                sell = tag.getInteger("ShopSellPrice");
            } else {
                ShopItem cfg = ShopConfig.get(stack.itemID, stack.getItemDamage());
                if (cfg != null) {
                    buy = cfg.buyPriceTenths;
                    sell = cfg.sellPriceTenths;
                }
            }
            String priceLbl = StatCollector.translateToLocal("shop.price");
            String sellLbl = StatCollector.translateToLocal("shop.sellprice");
            lines.add("§e" + priceLbl + ": §f" + (buy > 0 ? Money.format(buy) : ZERO));
            lines.add("§a" + sellLbl + ": §f" + (sell > 0 ? Money.format(sell) : ZERO));
        }
    }
}