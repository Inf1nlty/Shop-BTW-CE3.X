package com.inf1nlty.shop.mixin.world;

import com.inf1nlty.shop.ShopConfig;
import net.minecraft.src.ServerConfigurationManager;
import net.minecraft.src.Entity;
import net.minecraft.src.WorldServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Makes Nether portals use a 1:1 coordinate ratio instead of 8:1.
 */
@Mixin(ServerConfigurationManager.class)
public abstract class ServerConfigurationManagerMixin {
    @Inject(method = "transferEntityToWorld", at = @At("HEAD"), cancellable = true)
    private void makePortal1to1(Entity entity, int fromDim, WorldServer oldWorld, WorldServer newWorld, CallbackInfo ci) {
        if (ShopConfig.IS_SKYBLOCK_MODE) {
            if ((entity.dimension == -1 && fromDim == 0) || (entity.dimension == 0 && fromDim == -1)) {
                entity.setLocationAndAngles(entity.posX, entity.posY, entity.posZ, entity.rotationYaw, entity.rotationPitch);
                if (entity.isEntityAlive()) {
                    oldWorld.updateEntityWithOptionalForce(entity, false);
                }
                entity.setWorld(newWorld);
                newWorld.getDefaultTeleporter().placeInPortal(entity, entity.posX, entity.posY, entity.posZ, entity.rotationYaw);
                newWorld.spawnEntityInWorld(entity);
                newWorld.updateEntityWithOptionalForce(entity, false);
                ci.cancel();
            }
        }
    }
}