package net.minecraft.client.renderer.sodium.render.chunk.compile.pipeline;

import net.minecraft.client.renderer.sodium.model.color.ColorProviderRegistry;
import net.minecraft.client.renderer.sodium.model.light.LightPipelineProvider;
import net.minecraft.client.renderer.chunk.advanced.compile.ChunkBuildBuffers;
import net.minecraft.client.renderer.chunk.advanced.translucent_sorting.TranslucentGeometryCollector;
import net.minecraft.client.renderer.sodium.world.LevelSlice;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

public abstract class FluidRenderer {
    public abstract void render(LevelSlice level, BlockState blockState, FluidState fluidState, BlockPos blockPos, BlockPos offset, TranslucentGeometryCollector collector, ChunkBuildBuffers buffers);
}
