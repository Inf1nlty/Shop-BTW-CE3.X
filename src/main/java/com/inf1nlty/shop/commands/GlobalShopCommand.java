package com.inf1nlty.shop.commands;

import com.inf1nlty.shop.ShopConfig;
import com.inf1nlty.shop.global.GlobalListing;
import com.inf1nlty.shop.global.GlobalShopData;
import com.inf1nlty.shop.util.Money;
import com.inf1nlty.shop.util.MoneyManager;
import com.inf1nlty.shop.util.PlayerIdentityUtil;
import com.inf1nlty.shop.network.ShopNetServer;
import net.minecraft.src.*;

import java.util.List;
import java.util.UUID;

/**
 * /gshop main command:
 *  - /gshop                open global shop GUI
 *  - /gshop sell <price> [amount]
 *  - /gshop buy <id[:meta]> <price> [amount]
 *  - /gshop my
 *  - /gshop unlist <id>
 *  - /gshop mailbox        open mailbox GUI
 * Prices expressed with one decimal (tenths).
 * Also supports simplified commands:
 *  - /gs                    open global shop GUI
 *  - /gs s <price> [amount] sell
 *  - /gs b <id[:meta]> <price> [amount]
 *  - /gs m                  my listings
 *  - /gs u <id>             unlist
 *  - /gs mb                 open mailbox
 */
public class GlobalShopCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "gshop";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/gshop | /gshop sell <price> [amount] | /gshop buy <id[:meta]> [amount] | /gshop my | /gshop unlist <id> | /gshop mailbox\n"
                + "/gs | /gs s <price> [amount] | /gs b <id[:meta]> [amount] | /gs m | /gs u <id> | /gs mb";
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
            case "buy":
            case "b":
                handleBuyOrder(player, args);
                break;
            case "my":
            case "m":
                handleMy(player);
                break;
            case "unlist":
            case "u":
                handleUnlist(player, args);
                break;
            case "mailbox":
            case "mb":
                ShopNetServer.openMailbox(player);
                break;
            default:
                player.addChatMessage("gshop.sell.usage");
        }
    }

    private void handleSell(EntityPlayerMP player, String[] args) {
        if (args.length < 2) { ShopNetServer.sendResult(player, "gshop.sell.usage"); return; }
        ItemStack hand = player.inventory.getCurrentItem();
        if (hand == null) { ShopNetServer.sendResult(player, "gshop.listing.add.fail_no_item"); return; }

        int priceTenths;
        try { priceTenths = parseTenths(args[1]); }
        catch (NumberFormatException e) { ShopNetServer.sendResult(player, "gshop.listing.add.fail_price"); return; }
        if (priceTenths <= 0) { ShopNetServer.sendResult(player, "gshop.listing.add.fail_price"); return; }

        int desired = hand.stackSize;
        if (args.length >= 3) {
            try { desired = Integer.parseInt(args[2]); }
            catch (NumberFormatException e) { ShopNetServer.sendResult(player, "gshop.listing.add.fail_amount"); return; }
        }
        if (desired <= 0 || desired > hand.stackSize) { ShopNetServer.sendResult(player, "gshop.listing.add.fail_stack"); return; }

        GlobalListing listing = GlobalShopData.addSellOrder(player, hand.itemID, hand.getItemDamage(), desired, priceTenths);

        hand.stackSize -= desired;
        if (hand.stackSize <= 0) player.inventory.mainInventory[player.inventory.currentItem] = null;

        ShopNetServer.sendResult(player, "gshop.listing.add.success|itemID=" + listing.itemId + "|meta=" + listing.meta + "|count=" + listing.amount + "|price=" + Money.format(listing.priceTenths));
        if (ShopConfig.ANNOUNCE_GLOBAL_LISTING) {
            broadcastResultAll(
                    "gshop.listing.announce.line1.sell|player=" + player.username + "|itemID=" + listing.itemId + "|meta=" + listing.meta + "|count=" + listing.amount,
                    "gshop.listing.announce.line2|price=" + Money.format(listing.priceTenths) + "|type=Sell"
            );
        }
    }


    private void handleBuyOrder(EntityPlayerMP player, String[] args) {
        if (args.length < 3) { ShopNetServer.sendResult(player, "gshop.buy.usage"); return; }
        int itemId; int meta = 0;
        String idMeta = args[1];
        if (idMeta.contains(":")) {
            String[] parts = idMeta.split(":");
            try {
                itemId = Integer.parseInt(parts[0]);
                meta = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) { ShopNetServer.sendResult(player, "gshop.buy.usage"); return; }
        } else {
            try { itemId = Integer.parseInt(idMeta); }
            catch (NumberFormatException e) { ShopNetServer.sendResult(player, "gshop.buy.usage"); return; }
        }

        int priceTenths;
        try { priceTenths = parseTenths(args[2]); }
        catch (NumberFormatException e) { ShopNetServer.sendResult(player, "gshop.buy.usage"); return; }
        if (priceTenths <= 0) { ShopNetServer.sendResult(player, "gshop.buy.usage"); return; }

        int amount = 1;
        if (args.length >= 4) {
            try { amount = Integer.parseInt(args[3]); }
            catch (NumberFormatException e) { ShopNetServer.sendResult(player, "gshop.buy.usage"); return; }
        }
        if (amount <= 0) { ShopNetServer.sendResult(player, "gshop.buy.usage"); return; }

        int totalCost = priceTenths * amount;
        if (MoneyManager.getBalanceTenths(player) < totalCost) {
            ShopNetServer.sendResult(player, "gshop.buyorder.not_enough_money_for_post");
            return;
        }
        MoneyManager.addTenths(player, -totalCost);
        ShopNetServer.syncBalance(player);

        GlobalListing listing = GlobalShopData.addBuyOrder(player, itemId, meta, amount, priceTenths);

        ShopNetServer.sendResult(player, "gshop.buyorder.add.success|itemID=" + listing.itemId + "|meta=" + listing.meta + "|count=" + listing.amount + "|price=" + Money.format(listing.priceTenths));
        if (ShopConfig.ANNOUNCE_GLOBAL_LISTING) {
            broadcastResultAll(
                    "gshop.listing.announce.line1.buy|player=" + player.username + "|itemID=" + listing.itemId + "|meta=" + listing.meta + "|count=" + listing.amount,
                    "gshop.listing.announce.line2|price=" + Money.format(listing.priceTenths) + "|type=Buy"
            );
        }
    }

    private void broadcastResultAll(String... messages) {
        for (Object o : net.minecraft.server.MinecraftServer.getServer().getConfigurationManager().playerEntityList) {
            EntityPlayerMP online = (EntityPlayerMP) o;
            for (String msg : messages) {
                ShopNetServer.sendResult(online, msg);
            }
        }
    }

    private void handleMy(EntityPlayerMP player) {
        UUID id = PlayerIdentityUtil.getOfflineUUID(player.username);
        List<GlobalListing> mine = GlobalShopData.byOwner(id);
        ShopNetServer.sendResult(player, "gshop.list.mine.header");
        for (GlobalListing g : mine) {
            ShopNetServer.sendResult(player, "gshop.list.mine.line|id=" + g.listingId
                    + "|itemID=" + g.itemId
                    + "|meta=" + g.meta
                    + "|amount=" + (g.amount == -1 ? "∞" : g.amount)
                    + "|price=" + Money.format(g.priceTenths)
                    + (g.isBuyOrder ? "|type=Buy" : "|type=Sell"));
        }
    }

    private void handleUnlist(EntityPlayerMP player, String[] args) {
        if (args.length < 2) { ShopNetServer.sendResult(player, "gshop.unlist.usage"); return; }
        int id;
        try { id = Integer.parseInt(args[1]); }
        catch (NumberFormatException e) { ShopNetServer.sendResult(player, "gshop.unlist.usage"); return; }

        GlobalListing removed = GlobalShopData.remove(id, PlayerIdentityUtil.getOfflineUUID(player.username));
        if (removed != null) {
            if (removed.isBuyOrder) {
                ShopNetServer.sendResult(player, "gshop.buyorder.remove.success|id=" + removed.listingId
                        + "|itemID=" + removed.itemId
                        + "|meta=" + removed.meta
                        + "|count=" + (removed.amount == -1 ? "unlimited" : removed.amount));
            } else {
                refund(player, removed);
                ShopNetServer.sendResult(player, "gshop.listing.remove.success|id=" + removed.listingId
                        + "|itemID=" + removed.itemId
                        + "|meta=" + removed.meta
                        + "|count=" + removed.amount);
            }
        } else {
            GlobalListing gl = GlobalShopData.get(id);
            if (gl == null) ShopNetServer.sendResult(player, "gshop.listing.remove.not_found|id=" + id);
            else ShopNetServer.sendResult(player, "gshop.listing.remove.not_owner|id=" + id);
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