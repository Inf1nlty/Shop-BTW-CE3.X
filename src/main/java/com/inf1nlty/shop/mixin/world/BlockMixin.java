package com.inf1nlty.shop.mixin.world;

import com.inf1nlty.shop.ShopConfig;
import net.minecraft.src.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Block.class)
public class BlockMixin {
    @Inject(method = "onBlockActivated", at = @At("HEAD"), cancellable = true)
    public void onBlockActivated_SkyblockEgg(
            World world, int x, int y, int z,
            EntityPlayer player, int side, float hitX, float hitY, float hitZ,
            CallbackInfoReturnable<Boolean> cir) {
        if (!ShopConfig.IS_SKYBLOCK_MODE) return;
        Block block = Block.blocksList[world.getBlockId(x, y, z)];
        if (!(block instanceof BlockMobSpawner)) return;
        ItemStack held = player.getCurrentEquippedItem();
        if (held != null && held.getItem() instanceof ItemMonsterPlacer) {
            int meta = held.getItemDamage();
            String mobName = EntityList.getStringFromID(meta);
            if (mobName != null && !mobName.isEmpty()) {
                TileEntity tile = world.getBlockTileEntity(x, y, z);
                if (tile instanceof TileEntityMobSpawner) {
                    MobSpawnerBaseLogic logic = ((TileEntityMobSpawner) tile).getSpawnerLogic();
                    String currentMob = logic.getEntityNameToSpawn();
                    if (!mobName.equals(currentMob)) {
                        logic.setMobID(mobName);
                        world.markBlockForUpdate(x, y, z);
                        if (!player.capabilities.isCreativeMode) {
                            held.stackSize--;
                            if (held.stackSize <= 0) player.inventory.setInventorySlotContents(player.inventory.currentItem, null);
                        }
                    }
                    cir.setReturnValue(true);
                }
            }
        }
    }
}