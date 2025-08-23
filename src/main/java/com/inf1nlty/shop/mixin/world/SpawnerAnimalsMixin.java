package com.inf1nlty.shop.mixin.world;

import btw.block.BTWBlocks;
import com.inf1nlty.shop.ShopConfig;
import com.inf1nlty.shop.mixin.accessor.SpawnerAnimalsAccessor;
import net.minecraft.src.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.LinkedList;
import java.util.List;

@Mixin(SpawnerAnimals.class)
public class SpawnerAnimalsMixin {

    @Unique
    private static int animalSpawnCooldown = 0;

    @Inject(method = "findChunksForSpawning", at = @At("HEAD"))
    public void onFindChunksForSpawning(WorldServer world, boolean spawnHostile, boolean spawnPeaceful, boolean spawnAnimals, CallbackInfoReturnable<Integer> cir) {
        if (ShopConfig.IS_SKYBLOCK_MODE && spawnAnimals) {
            if (animalSpawnCooldown-- <= 0) {
                spawnSkyblockAnimals(world);
                animalSpawnCooldown = 20;
            }
        }
    }

    @Unique
    @SuppressWarnings("unchecked")
    private void spawnSkyblockAnimals(WorldServer world) {
        LinkedList<ChunkCoordIntPair> activeChunks = world.getActiveChunksCoordsList();
        int maxAnimals = EnumCreatureType.creature.getMaxNumberOfCreature() * activeChunks.size() / 256;
        int currentAnimals = world.countEntitiesThatApplyToSpawnCap(EntityAnimal.class);
        if (currentAnimals >= maxAnimals) return;
        for (ChunkCoordIntPair chunkCoord : activeChunks) {
            for (int attempt = 0; attempt < 3; attempt++) {
                ChunkPosition pos = SpawnerAnimalsAccessor.callGetRandomSpawningPointInChunk(world, chunkCoord.chunkXPos, chunkCoord.chunkZPos);
                int x = pos.x;
                int y = pos.y;
                int z = pos.z;
                if (world.getBlockId(x, y - 1, z) != BTWBlocks.grassSlab.blockID || world.getFullBlockLightValue(x, y, z) <= 8) continue;
                if (world.getClosestPlayer(x + 0.5, y, z + 0.5, 24.0D) != null) continue;
                BiomeGenBase biome = world.getBiomeGenForCoords(x, z);
                List<SpawnListEntry> spawnList = biome.getSpawnableList(EnumCreatureType.creature);
                if (spawnList == null || spawnList.isEmpty()) continue;
                SpawnListEntry entry = (SpawnListEntry) WeightedRandom.getRandomItem(world.rand, spawnList);
                int groupSize = entry.minGroupCount + world.rand.nextInt(entry.maxGroupCount - entry.minGroupCount + 1);
                for (int g = 0; g < groupSize; g++) {
                    int spawnX = x + world.rand.nextInt(6) - world.rand.nextInt(6);
                    int spawnZ = z + world.rand.nextInt(6) - world.rand.nextInt(6);
                    try {
                        EntityLiving animal = (EntityLiving) entry.entityClass.getConstructor(World.class).newInstance(world);
                        animal.setLocationAndAngles(spawnX + 0.5, y, spawnZ + 0.5, world.rand.nextFloat() * 360.0F, 0.0F);
                        if (animal.getCanSpawnHere()) {
                            world.spawnEntityInWorld(animal);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
}