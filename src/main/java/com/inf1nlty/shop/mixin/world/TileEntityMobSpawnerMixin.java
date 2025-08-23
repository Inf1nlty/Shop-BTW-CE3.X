package com.inf1nlty.shop.mixin.world;

import com.inf1nlty.shop.ShopConfig;
import net.minecraft.src.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TileEntityMobSpawner.class)
public class TileEntityMobSpawnerMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    public void initEmptyMobSpawner(CallbackInfo ci) {
        if (ShopConfig.IS_SKYBLOCK_MODE) {
            MobSpawnerBaseLogic logic = ((TileEntityMobSpawner)(Object)this).getSpawnerLogic();
            logic.setMobID("");
        }
    }
}