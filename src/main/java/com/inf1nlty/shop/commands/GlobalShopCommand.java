package com.inf1nlty.shop.commands;

import com.inf1nlty.shop.global.GlobalListing;
import com.inf1nlty.shop.global.GlobalShopData;
import com.inf1nlty.shop.util.Money;
import com.inf1nlty.shop.util.PlayerIdentityUtil;
import com.inf1nlty.shop.network.ShopNetServer;
import net.minecraft.src.*;

import java.util.List;
import java.util.UUID;

/**
 * /gshop main command:
 *  - /gshop                open global shop GUI
 *  - /gshop sell <price> [amount]
 *  - /gshop my
 *  - /gshop unlist <id>
 * Prices expressed with one decimal (tenths).
 * Also supports simplified commands:
 *  - /g                    open global shop GUI
 *  - /g s <price> [amount] sell
 *  - /g u <id>             unlist
 *  - /g m                  my listings
 */
public class GlobalShopCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "gshop";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/gshop | /gshop sell <price> [amount] | /gshop my | /gshop unlist <id>\n"
                + "/g | /g s <price> [amount] | /g m | /g u <id>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (!(sender instanceof EntityPlayerMP player)) {
            sender.sendChatToPlayer(ChatMessageComponent.createFromText("Only players"));
            return;
        }
        if (args.length == 0) {
            ShopNetServer.openGlobalShop(player);
            return;
        }
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "sell":
            case "s":
                handleSell(player, args);
                break;
            case "my":
            case "m":
                handleMy(player);
                break;
            case "unlist":
            case "u":
                handleUnlist(player, args);
                break;
            default:
                player.addChatMessage("gshop.sell.usage");
        }
    }

    private void handleSell(EntityPlayerMP player, String[] args) {
        if (args.length < 2) { player.addChatMessage("gshop.sell.usage"); return; }
        ItemStack hand = player.inventory.getCurrentItem();
        if (hand == null) { player.addChatMessage("gshop.listing.add.fail_no_item"); return; }

        int priceTenths;
        try { priceTenths = parseTenths(args[1]); }
        catch (NumberFormatException e) { player.addChatMessage("gshop.listing.add.fail_price"); return; }
        if (priceTenths <= 0) { player.addChatMessage("gshop.listing.add.fail_price"); return; }

        int desired = hand.stackSize;
        if (args.length >= 3) {
            try { desired = Integer.parseInt(args[2]); }
            catch (NumberFormatException e) { player.addChatMessage("gshop.listing.add.fail_amount"); return; }
        }
        if (desired <= 0 || desired > hand.stackSize) { player.addChatMessage("gshop.listing.add.fail_stack"); return; }

        GlobalListing listing = GlobalShopData.add(player, hand.itemID, hand.getItemDamage(), desired, priceTenths);

        hand.stackSize -= desired;
        if (hand.stackSize <= 0) player.inventory.mainInventory[player.inventory.currentItem] = null;

        String display = buildDisplayName(listing);
        player.addChatMessage("gshop.listing.add.success|item=" + display
                + "|count=" + listing.amount
                + "|price=" + Money.format(listing.priceTenths));
    }

    private void handleMy(EntityPlayerMP player) {
        UUID id = PlayerIdentityUtil.getOfflineUUID(player.username);
        List<GlobalListing> mine = GlobalShopData.byOwner(id);
        player.addChatMessage("gshop.list.mine.header");
        for (GlobalListing g : mine) {
            player.addChatMessage("gshop.list.mine.line|id=" + g.listingId
                    + "|item=" + buildDisplayName(g)
                    + "|amount=" + g.amount
                    + "|price=" + Money.format(g.priceTenths));
        }
    }

    private void handleUnlist(EntityPlayerMP player, String[] args) {
        if (args.length < 2) { player.addChatMessage("gshop.unlist.usage"); return; }
        int id;
        try { id = Integer.parseInt(args[1]); }
        catch (NumberFormatException e) { player.addChatMessage("gshop.unlist.usage"); return; }

        GlobalListing removed = GlobalShopData.remove(id, PlayerIdentityUtil.getOfflineUUID(player.username));
        if (removed != null) {
            refund(player, removed);
            player.addChatMessage("gshop.listing.remove.success|id=" + removed.listingId
                    + "|item=" + buildDisplayName(removed)
                    + "|count=" + removed.amount);
        } else {
            GlobalListing gl = GlobalShopData.get(id);
            if (gl == null) player.addChatMessage("gshop.listing.remove.not_found|id=" + id);
            else player.addChatMessage("gshop.listing.remove.not_owner|id=" + id);
        }
    }

    private void refund(EntityPlayerMP player, GlobalListing g) {
        Item item = (g.itemId >= 0 && g.itemId < Item.itemsList.length) ? Item.itemsList[g.itemId] : null;
        if (item == null) return;
        int remaining = g.amount;
        int max = item.getItemStackLimit();
        while (remaining > 0) {
            int take = Math.min(max, remaining);
            ItemStack stack = new ItemStack(item, take, g.meta);
            if (g.nbt != null) stack.stackTagCompound = (net.minecraft.src.NBTTagCompound) g.nbt.copy();
            if (!player.inventory.addItemStackToInventory(stack)) player.dropPlayerItem(stack);
            remaining -= take;
        }
    }

    private String buildDisplayName(GlobalListing g) {
        Item item = (g.itemId >= 0 && g.itemId < Item.itemsList.length) ? Item.itemsList[g.itemId] : null;
        if (item == null) return "unknown";
        ItemStack single = new ItemStack(item, 1, g.meta);
        if (g.nbt != null) single.stackTagCompound = (net.minecraft.src.NBTTagCompound) g.nbt.copy();
        return single.getDisplayName();
    }

    private int parseTenths(String raw) {
        raw = raw.trim();
        if (raw.contains(".")) {
            String[] p = raw.split("\\.");
            if (p.length != 2) throw new NumberFormatException();
            int w = Integer.parseInt(p[0]);
            String f = p[1];
            if (f.length() > 1) f = f.substring(0,1);
            int fr = Integer.parseInt(f);
            if (w < 0) return w * 10 - fr;
            return w * 10 + fr;
        }
        return Integer.parseInt(raw) * 10;
    }
}