package com.inf1nlty.shop;

import net.minecraft.src.EntityPlayer;
import net.minecraft.src.ItemStack;
import net.minecraft.src.NBTTagCompound;
import net.minecraft.src.StatCollector;

import java.util.List;

/**
 * System shop listing entry (id + damage variant).
 * buyPrice / sellPrice in tenths.
 */
public class ShopItem {

    public int itemID;
    public int damage;
    public String displayName;
    public int buyPriceTenths;
    public int sellPriceTenths;
    public ItemStack itemStack; // base exemplar (damage may be overridden)

    public ItemStack getShopStack() {
        ItemStack stack = new ItemStack(itemStack.getItem(), 1, damage);
        if (stack.stackTagCompound == null) {
            stack.stackTagCompound = new NBTTagCompound();
        }
        stack.stackTagCompound.setInteger("ShopBuyPrice", buyPriceTenths);
        stack.stackTagCompound.setInteger("ShopSellPrice", sellPriceTenths);
        stack.stackTagCompound.setString("ShopDisplay", displayName);
        return stack;
    }

    @SuppressWarnings("unchecked")
    public static void addShopInformation(ItemStack stack, EntityPlayer player, List info, boolean advanced) {
        if (stack == null) return;
        if (stack.hasTagCompound() && stack.stackTagCompound.hasKey("ShopBuyPrice")) {
            int buy = stack.stackTagCompound.getInteger("ShopBuyPrice");
            int sell = stack.stackTagCompound.getInteger("ShopSellPrice");
            String priceLbl = StatCollector.translateToLocal("shop.price");
            String sellLbl = StatCollector.translateToLocal("shop.sellprice");
            info.add("§e" + priceLbl + ": §f" + formatTenths(buy));
            info.add("§a" + sellLbl + ": §f" + formatTenths(sell));
        }
    }

    public static String formatTenths(int t) {
        int w = t / 10;
        int f = Math.abs(t % 10);
        return w + "." + f;
    }
}