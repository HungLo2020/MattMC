package com.github.alexthe666.citadel.server.generation;

import com.github.alexthe666.citadel.config.ServerConfig;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;

// TODO: Implement with Fabric Biome API - NeoForge BiomeModifier doesn't exist
// This class modifies spawn probabilities in biomes during worldgen
// For now, this functionality is disabled until Fabric biome modification is implemented
public class SpawnProbabilityModifier {
    
    /* Original NeoForge implementation - commented out
    @Override
    public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        float probability = (float) (ServerConfig.chunkGenSpawnModifierVal) * builder.getMobSpawnSettings().getProbability();
        if (phase == Phase.MODIFY) {
            builder.getMobSpawnSettings().creatureGenerationProbability(Mth.clamp(probability, 0F, 1F));
        }
    }

    @Override
    public MapCodec<? extends BiomeModifier> codec() {
        return makeCodec();
    }
    */

    public static MapCodec<SpawnProbabilityModifier> makeCodec() {
        return MapCodec.unit(SpawnProbabilityModifier::new);
    }
}
