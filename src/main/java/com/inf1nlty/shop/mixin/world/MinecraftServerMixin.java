package com.inf1nlty.shop.mixin.world;

import com.inf1nlty.shop.network.HandshakeServer;
import com.inf1nlty.shop.server.MailboxManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.src.EntityPlayerMP;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
    @Inject(method = "tick", at = @At("RETURN"))
    private void shop$onServerTick(CallbackInfo ci) {
        HandshakeServer.onServerTick();
    }

    @Inject(method = "stopServer", at = @At("RETURN"))
    private void shop$onServerShutdown(CallbackInfo ci) {
        List rawList = ((MinecraftServer)(Object)this).getConfigurationManager().playerEntityList;
        List<EntityPlayerMP> playerList = new ArrayList<>();
        for (Object o : rawList) {
            if (o instanceof EntityPlayerMP p) {
                playerList.add(p);
            }
        }
        MailboxManager.flushAllMailboxesToNBT(playerList);
    }
}