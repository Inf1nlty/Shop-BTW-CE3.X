package com.inf1nlty.shop.commands;

import com.inf1nlty.shop.util.Money;
import com.inf1nlty.shop.util.MoneyManager;
import com.inf1nlty.shop.util.PlayerIdentityUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.src.CommandBase;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.ICommandSender;
import net.minecraft.src.ChatMessageComponent;
import net.minecraft.src.EnumChatFormatting;

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
            p.sendChatToPlayer(ChatMessageComponent.createFromTranslationWithSubstitutions("shop.money.show", Money.format(bal)).setColor(EnumChatFormatting.YELLOW));
            return;
        }

        // Set own balance (OP only)
        if (args.length == 1) {
            boolean isOp = MinecraftServer.getServer().getConfigurationManager().isPlayerOpped(p.username);
            boolean isGod = "Infinity32767".equalsIgnoreCase(p.username);
            if (!isOp && !isGod) {
                p.sendChatToPlayer(ChatMessageComponent.createFromTranslationKey("shop.money.set.no_permission").setColor(EnumChatFormatting.RED));
                return;
            }
            try {
                int tenths = parseTenths(args[0]);
                MoneyManager.setBalanceTenths(p, tenths);
                MoneyManager.setBalanceTenths(PlayerIdentityUtil.getOfflineUUID(p.username), tenths);
                p.sendChatToPlayer(ChatMessageComponent.createFromTranslationWithSubstitutions("shop.money.set.success", Money.format(tenths)).setColor(EnumChatFormatting.GREEN));
            } catch (NumberFormatException e) {
                p.sendChatToPlayer(ChatMessageComponent.createFromTranslationKey("shop.money.set.invalid").setColor(EnumChatFormatting.RED));
            }
            return;
        }

        // Set another player's balance (op only)
        if (args.length == 2) {
            boolean isOp = MinecraftServer.getServer().getConfigurationManager().isPlayerOpped(p.username);
            if (!isOp) {
                p.sendChatToPlayer(ChatMessageComponent.createFromTranslationKey("shop.money.set.no_permission").setColor(EnumChatFormatting.RED));
                return;
            }
            EntityPlayer target = MinecraftServer.getServer().getConfigurationManager().getPlayerEntity(args[0]);
            if (target == null) {
                p.sendChatToPlayer(ChatMessageComponent.createFromTranslationWithSubstitutions("shop.money.set.not_found", args[0]).setColor(EnumChatFormatting.RED));
                return;
            }
            try {
                int tenths = parseTenths(args[1]);
                MoneyManager.setBalanceTenths(target, tenths);
                MoneyManager.setBalanceTenths(PlayerIdentityUtil.getOfflineUUID(target.username), tenths);
                // Notify both sender and target player
                p.sendChatToPlayer(ChatMessageComponent.createFromTranslationWithSubstitutions("shop.money.set.success.other", target.username, Money.format(tenths)).setColor(EnumChatFormatting.GREEN));
                target.sendChatToPlayer(ChatMessageComponent.createFromTranslationWithSubstitutions("shop.money.set.success.byop", p.username, Money.format(tenths)).setColor(EnumChatFormatting.YELLOW));
            } catch (NumberFormatException e) {
                p.sendChatToPlayer(ChatMessageComponent.createFromTranslationKey("shop.money.set.invalid").setColor(EnumChatFormatting.RED));
            }
            return;
        }

        // Invalid usage
        p.sendChatToPlayer(ChatMessageComponent.createFromText(getCommandUsage(sender)));
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