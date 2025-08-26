package com.inf1nlty.shop.mixin.world;

import com.inf1nlty.shop.ShopConfig;
import net.minecraft.src.Teleporter;
import net.minecraft.src.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Makes Nether portal creation/search use 1:1 entity coordinates, not 8:1.
 */
@Mixin(Teleporter.class)
public abstract class TeleporterMixin {
    @Inject(method = "placeInPortal", at = @At("HEAD"), cancellable = true)
    private void forceOneToOnePortal(Entity entity, double d, double e, double f, float g, CallbackInfo ci) {
        if (ShopConfig.IS_SKYBLOCK_MODE) {
            Teleporter self = (Teleporter)(Object)this;
            if (!self.placeInExistingPortal(entity, entity.posX, entity.posY, entity.posZ, g)) {
                self.makePortal(entity);
                self.placeInExistingPortal(entity, entity.posX, entity.posY, entity.posZ, g);
            }
            ci.cancel();
        }
    }
}