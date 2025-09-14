package com.inf1nlty.shop.commands;

import com.inf1nlty.shop.ShopConfig;
import com.inf1nlty.shop.network.ShopNetServer;
import net.minecraft.src.ChatMessageComponent;
import net.minecraft.src.CommandBase;
import net.minecraft.src.EntityPlayerMP;
import net.minecraft.src.ICommandSender;
import net.minecraft.src.EnumChatFormatting;

/**
 * /shop [reload]
 * Reload only touches server config. No GUI is force-opened.
 */
public class ShopCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "shop";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/shop [reload|regen]";
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
        if (args.length == 1 && "reload".equalsIgnoreCase(args[0])) {
            ShopConfig.forceReload();           // server authoritative list
            ShopNetServer.ensureConfig(true);   // touch timestamp
            ChatMessageComponent msg = ChatMessageComponent.createFromTranslationKey("shop.config.reload").setColor(EnumChatFormatting.AQUA);
            if (sender instanceof EntityPlayerMP mp) {
                mp.sendChatToPlayer(msg);
            } else {
                sender.sendChatToPlayer(msg);
            }
            return;
        }

        if (args.length == 1 && "regen".equalsIgnoreCase(args[0])) {
            ShopConfig.regenerateDefault();
            ShopConfig.forceReload();
            ShopNetServer.ensureConfig(true);
            ChatMessageComponent msg = ChatMessageComponent.createFromTranslationKey("shop.config.regen").setColor(EnumChatFormatting.AQUA);
            if (sender instanceof EntityPlayerMP mp) {
                mp.sendChatToPlayer(msg);
            } else {
                sender.sendChatToPlayer(msg);
            }
            return;
        }

        if (sender instanceof EntityPlayerMP mp) {
            ShopNetServer.ensureConfig(false);
            ShopNetServer.openSystemShop(mp);
        }
    }
}