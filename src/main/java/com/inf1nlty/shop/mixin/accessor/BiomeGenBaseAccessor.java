package com.inf1nlty.shop.mixin.accessor;

import net.minecraft.src.BiomeGenBase;
import net.minecraft.src.SpawnListEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(BiomeGenBase.class)
public interface BiomeGenBaseAccessor {
    @Accessor("spawnableMonsterList")
    List<SpawnListEntry> getSpawnableMonsterList();
}