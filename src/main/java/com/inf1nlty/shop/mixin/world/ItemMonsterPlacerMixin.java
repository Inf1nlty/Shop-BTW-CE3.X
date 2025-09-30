package com.inf1nlty.shop.mixin.world;

import net.minecraft.src.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@SuppressWarnings("unchecked")
@Mixin(ItemMonsterPlacer.class)
public class ItemMonsterPlacerMixin {

    @Inject(method = "onItemUse", at = @At("TAIL"))
    public void onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side, float hitX, float hitY, float hitZ, CallbackInfoReturnable<Boolean> cir) {
        if (world.isRemote) return;

        if (stack == null || stack.stackTagCompound == null || !stack.stackTagCompound.getBoolean("ShopBaby")) return;

        int id = stack.getItemDamage();

        if (id == 90 || id == 91 || id == 92 || id == 100 || id == 95) {

            List<Entity> entities = world.getEntitiesWithinAABB(EntityList.createEntityByID(id, world).getClass(),
                    AxisAlignedBB.getAABBPool().getAABB(x-2, y-2, z-2, x+2, y+2, z+2));

            for (Entity ent : entities) {

                if (ent instanceof EntityAgeable) {
                    ((EntityAgeable)ent).setGrowingAge(-24000);
                }
            }
        }
    }

    @Inject(method = "onItemRightClick", at = @At("TAIL"))
    public void onItemRightClick(ItemStack stack, World world, EntityPlayer player, CallbackInfoReturnable<ItemStack> cir) {
        if (world.isRemote) return;

        if (stack == null || stack.stackTagCompound == null || !stack.stackTagCompound.getBoolean("ShopBaby")) return;

        int id = stack.getItemDamage();

        if (id == 90 || id == 91 || id == 92 || id == 100 || id == 95) {

            List<Entity> entities = world.getEntitiesWithinAABB(EntityList.createEntityByID(id, world).getClass(),
                    AxisAlignedBB.getAABBPool().getAABB(player.posX-2, player.posY-2, player.posZ-2, player.posX+2, player.posY+2, player.posZ+2));

            for (Entity ent : entities) {
                if (ent instanceof EntityAgeable) {
                    ((EntityAgeable)ent).setGrowingAge(-24000);
                }
            }
        }
    }
}