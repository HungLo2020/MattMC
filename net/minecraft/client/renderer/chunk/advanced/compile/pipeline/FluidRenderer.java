package net.minecraft.client.renderer.chunk.advanced.compile.pipeline;

import net.caffeinemc.mods.sodium.client.model.color.ColorProviderRegistry;
import net.caffeinemc.mods.sodium.client.model.light.LightPipelineProvider;
import net.minecraft.client.renderer.chunk.advanced.compile.ChunkBuildBuffers;
import net.minecraft.client.renderer.chunk.advanced.translucent_sorting.TranslucentGeometryCollector;
import net.caffeinemc.mods.sodium.client.world.LevelSlice;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

public abstract class FluidRenderer {
    public abstract void render(LevelSlice level, BlockState blockState, FluidState fluidState, BlockPos blockPos, BlockPos offset, TranslucentGeometryCollector collector, ChunkBuildBuffers buffers);
}
