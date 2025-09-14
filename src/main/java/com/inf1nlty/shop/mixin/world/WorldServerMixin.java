package com.inf1nlty.shop.mixin.world;

import com.inf1nlty.shop.server.MailboxManager;
import com.inf1nlty.shop.util.MoneyManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.src.ISaveHandler;
import net.minecraft.src.SaveHandler;
import net.minecraft.src.WorldSettings;
import net.minecraft.src.Profiler;
import net.minecraft.src.ILogAgent;
import net.minecraft.src.WorldServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;

@Mixin(WorldServer.class)
public class WorldServerMixin {

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onWorldServerConstructed(MinecraftServer server, ISaveHandler saveHandler, String worldName, int dimension, WorldSettings settings, Profiler profiler, ILogAgent logAgent, CallbackInfo ci) {

        File worldDir = ((SaveHandler)saveHandler).getWorldDirectory();

        File shopDir = new File(worldDir, "shop");
        MoneyManager.init(shopDir);
        MoneyManager.loadBalancesFromFile();

        MailboxManager.init(shopDir);
    }
}