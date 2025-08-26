package com.inf1nlty.shop.mixin.world;

import com.inf1nlty.shop.ShopConfig;
import net.minecraft.src.MathHelper;
import net.minecraft.src.ServerConfigurationManager;
import net.minecraft.src.Entity;
import net.minecraft.src.WorldServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Makes Nether portals use a 1:1 coordinate ratio instead of 8:1.
 */
@Mixin(ServerConfigurationManager.class)

public abstract class ServerConfigurationManagerMixin {

    @Invoker("flagChunksAroundTeleportingEntityForCheckForUnload")

    public abstract void invokeFlagChunksAroundTeleportingEntityForCheckForUnload(WorldServer world, Entity entity);

    @Inject(method = "transferEntityToWorld", at = @At("HEAD"), cancellable = true)
    private void makePortal1to1(Entity entity, int fromDim, WorldServer oldWorld, WorldServer newWorld, CallbackInfo ci) {
        if (ShopConfig.IS_SKYBLOCK_MODE) {
            if ((entity.dimension == -1 && fromDim == 0) || (entity.dimension == 0 && fromDim == -1)) {
                oldWorld.theProfiler.startSection("moving");
                if (entity.isEntityAlive()) {
                    oldWorld.updateEntityWithOptionalForce(entity, false);
                }
                oldWorld.theProfiler.endSection();
                oldWorld.theProfiler.startSection("placing");
                double x = MathHelper.clamp_int((int)entity.posX, -29999872, 29999872);
                double z = MathHelper.clamp_int((int)entity.posZ, -29999872, 29999872);
                if (entity.isEntityAlive()) {
                    entity.setLocationAndAngles(x, entity.posY, z, entity.rotationYaw, entity.rotationPitch);
                    this.invokeFlagChunksAroundTeleportingEntityForCheckForUnload(newWorld, entity);
                    newWorld.getDefaultTeleporter().placeInPortal(entity, entity.posX, entity.posY, entity.posZ, entity.rotationYaw);
                    newWorld.spawnEntityInWorld(entity);
                    newWorld.updateEntityWithOptionalForce(entity, false);
                }
                oldWorld.theProfiler.endSection();
                entity.setWorld(newWorld);
                ci.cancel();
            }
        }
    }
}