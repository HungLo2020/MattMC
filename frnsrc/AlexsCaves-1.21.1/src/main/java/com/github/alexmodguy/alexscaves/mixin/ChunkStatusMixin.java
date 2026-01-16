package com.github.alexmodguy.alexscaves.mixin;

import com.github.alexmodguy.alexscaves.server.level.biome.MultiNoiseBiomeSourceAccessor;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.status.ChunkStatusTasks;
import net.minecraft.world.level.chunk.status.ChunkStep;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

@Mixin(ChunkStatusTasks.class)
public class ChunkStatusMixin {

    @Inject(at = @At("HEAD"),
            method = "generateBiomes")
    private static void ac_setWorldSeed(WorldGenContext worldGenContext, ChunkStep step, StaticCache2D<GenerationChunkHolder> cache, ChunkAccess chunk, CallbackInfoReturnable<CompletableFuture<ChunkAccess>> cir) {
        ServerLevel serverLevel = worldGenContext.level();
        ChunkGenerator chunkGenerator = worldGenContext.generator();
        if (chunkGenerator.getBiomeSource() instanceof MultiNoiseBiomeSource multiNoiseBiomeSource) {
            if (multiNoiseBiomeSource instanceof MultiNoiseBiomeSourceAccessor accessor) {
                accessor.setLastSampledSeed(serverLevel.getSeed());
                accessor.setLastSampledDimension(serverLevel.dimension());
            }
        }
    }
}
