package com.inf1nlty.shop.commands;

import com.inf1nlty.shop.util.Money;
import com.inf1nlty.shop.util.MoneyManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.src.CommandBase;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.ICommandSender;

/**
 * /money [amount]
 * amount can have one decimal; stores tenths.
 * Extended: /money [player] [amount] for ops to set another player's balance.
 */
public class MoneyCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "money";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/money [amount]\n/money [player] [amount]";
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
        if (!(sender instanceof EntityPlayer p)) return;

        // Show own balance
        if (args.length == 0) {
            int bal = MoneyManager.getBalanceTenths(p);
            p.addChatMessage("shop.money.show|balance=" + Money.format(bal));
            return;
        }

        // Set own balance (op only)
        if (args.length == 1) {
            boolean isOp = MinecraftServer.getServer().getConfigurationManager().isPlayerOpped(p.username);
            if (!isOp) {
                p.addChatMessage("shop.money.set.no_permission");
                return;
            }
            try {
                int tenths = parseTenths(args[0]);
                MoneyManager.setBalanceTenths(p, tenths);
                p.addChatMessage("shop.money.set.success|balance=" + Money.format(tenths));
            } catch (NumberFormatException e) {
                p.addChatMessage("shop.money.set.invalid");
            }
            return;
        }

        // Set another player's balance (op only)
        if (args.length == 2) {
            boolean isOp = MinecraftServer.getServer().getConfigurationManager().isPlayerOpped(p.username);
            if (!isOp) {
                p.addChatMessage("shop.money.set.no_permission");
                return;
            }
            EntityPlayer target = MinecraftServer.getServer().getConfigurationManager().getPlayerEntity(args[0]);
            if (target == null) {
                p.addChatMessage("shop.money.set.not_found|player=" + args[0]);
                return;
            }
            try {
                int tenths = parseTenths(args[1]);
                MoneyManager.setBalanceTenths(target, tenths);
                // Notify both sender and target player
                p.addChatMessage("shop.money.set.success.other|player=" + target.username + "|balance=" + Money.format(tenths));
                target.addChatMessage("shop.money.set.success.byop|player=" + p.username + "|balance=" + Money.format(tenths));
            } catch (NumberFormatException e) {
                p.addChatMessage("shop.money.set.invalid");
            }
            return;
        }

        // Invalid usage
        p.addChatMessage(getCommandUsage(sender));
    }

    /**
     * Parses a decimal string into tenths as integer.
     * Valid formats: "12", "12.3", "-4.5"
     */
    private int parseTenths(String raw) {
        raw = raw.trim();
        if (raw.contains(".")) {
            String[] p = raw.split("\\.");
            if (p.length != 2) throw new NumberFormatException();
            int w = Integer.parseInt(p[0]);
            String frac = p[1];
            if (frac.length() > 1) frac = frac.substring(0,1);
            int f = Integer.parseInt(frac);
            if (w < 0) return w*10 - f;
            return w*10 + f;
        }
        return Integer.parseInt(raw) * 10;
    }
}