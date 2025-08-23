package com.inf1nlty.shop.mixin.world;

import com.inf1nlty.shop.ShopConfig;
import net.minecraft.src.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(WorldServer.class)
public class WorldServerMixin {

    @ModifyArg(method= "tick", at=@At(value="INVOKE", target="Lnet/minecraft/src/SpawnerAnimals;findChunksForSpawning(Lnet/minecraft/src/WorldServer;ZZZ)I"), index=3)
    public boolean allowSpawnAnimal(boolean spawnAnimal) {
        return ShopConfig.IS_SKYBLOCK_MODE || spawnAnimal;
    }
}