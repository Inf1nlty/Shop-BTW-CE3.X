package com.inf1nlty.shop.mixin.world;

import com.inf1nlty.shop.ShopConfig;
import com.inf1nlty.shop.mixin.accessor.BiomeGenBaseAccessor;
import net.minecraft.src.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BiomeGenHell.class)
public abstract class BiomeGenHellMixin implements BiomeGenBaseAccessor {
    @Inject(method = "<init>", at = @At("TAIL"))
    public void addZombieVillagerToNetherSpawnList(int par1, CallbackInfo ci) {
        if (ShopConfig.IS_SKYBLOCK_MODE) {
            this.getSpawnableMonsterList().add(new SpawnListEntry(EntityZombie.class, 1, 1, 1));
        }
    }
}