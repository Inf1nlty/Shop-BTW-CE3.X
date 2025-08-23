package com.inf1nlty.shop.mixin.accessor;

import net.minecraft.src.Entity;
import net.minecraft.src.World;
import net.minecraft.src.AxisAlignedBB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Entity.class)
public interface EntityAccessor {
    @Accessor("posX") double getPosX();
    @Accessor("posZ") double getPosZ();
    @Accessor("worldObj") World getWorldObj();
    @Accessor("boundingBox") AxisAlignedBB getBoundingBox();
}