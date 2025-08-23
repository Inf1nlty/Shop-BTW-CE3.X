package com.inf1nlty.shop.mixin.accessor;

import net.minecraft.src.SpawnerAnimals;
import net.minecraft.src.World;
import net.minecraft.src.ChunkPosition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(SpawnerAnimals.class)
public interface SpawnerAnimalsAccessor {
    @Invoker("getRandomSpawningPointInChunk")
    static ChunkPosition callGetRandomSpawningPointInChunk(World world, int chunkX, int chunkZ) {
        throw new AssertionError();
    }
}