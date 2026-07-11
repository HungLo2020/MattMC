package net.sodium.client.render.chunk.compile.tasks;

import net.sodium.client.render.chunk.compile.ChunkBuildBuffers;
import net.sodium.client.render.chunk.compile.pipeline.BlockRenderCache;
import net.sodium.client.render.chunk.compile.pipeline.BlockRenderer;
import net.sodium.client.render.chunk.terrain.material.DefaultMaterials;
import net.sodium.client.render.chunk.translucent_sorting.TranslucentGeometryCollector;
import net.sodium.client.services.PlatformLevelRenderHooks;
import net.sodium.client.world.LevelSlice;
import net.sodium.client.world.cloned.ChunkRenderContext;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

final class NativeMeshingCompatibilityFallback {
    private NativeMeshingCompatibilityFallback() {
    }

    static int renderModel(BlockRenderCache cache, BlockRenderer blockRenderer, BlockState blockState,
            BlockPos blockPos, BlockPos modelOffset, NativeMeshingDiagnostics.FallbackStats fallbackStats) {
        BlockStateModel model = cache.getBlockModels().getBlockModel(blockState);
        int quadStart = blockRenderer.getEmittedQuadCount();
        beginIrisBlock(blockRenderer, blockState, blockPos);
        blockRenderer.renderModel(model, blockState, blockPos, modelOffset);
        int emittedQuads = blockRenderer.getEmittedQuadCount() - quadStart;
        fallbackStats.recordModelFallback(blockState, emittedQuads);
        return emittedQuads;
    }

    static int renderFluid(BlockRenderCache cache, ChunkBuildBuffers buffers, LevelSlice slice,
            BlockState blockState, FluidState fluidState, BlockPos blockPos, BlockPos modelOffset,
            TranslucentGeometryCollector collector, NativeMeshingDiagnostics.FallbackStats fallbackStats) {
        int quadStart = cache.getFluidRenderer().getEmittedQuadCount();
        beginIrisFluid(cache, blockState, fluidState, blockPos);
        cache.getFluidRenderer().render(slice, blockState, fluidState, blockPos, modelOffset, collector, buffers);
        int emittedQuads = cache.getFluidRenderer().getEmittedQuadCount() - quadStart;
        fallbackStats.recordFluidFallback(blockState, fluidState, emittedQuads);
        return emittedQuads;
    }

    static int runMeshAppenders(ChunkRenderContext renderContext, ChunkBuildBuffers buffers, LevelSlice slice,
            TranslucentGeometryCollector collector, NativeMeshingDiagnostics.FallbackStats fallbackStats) {
        int quadStart = buffers.getFallbackConsumerEmittedQuadCount();
        PlatformLevelRenderHooks.INSTANCE.runChunkMeshAppenders(renderContext.getRenderers(),
                type -> buffers.get(DefaultMaterials.forChunkLayer(type))
                        .asFallbackVertexConsumer(DefaultMaterials.forChunkLayer(type), collector),
                slice);
        int emittedQuads = buffers.getFallbackConsumerEmittedQuadCount() - quadStart;
        fallbackStats.recordAppenderFallbackQuads(emittedQuads);
        return emittedQuads;
    }

    private static void beginIrisBlock(BlockRenderer blockRenderer, BlockState blockState, BlockPos blockPos) {
        var ids = net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings.INSTANCE.getBlockStateIds();
        if (ids != null) {
            ((net.irisshaders.iris.vertices.sodium.terrain.VertexEncoderInterface) blockRenderer).beginBlock(
                    ids.getOrDefault(blockState, -1), (byte) 0, (byte) blockState.getLightEmission(),
                    blockPos.getX(), blockPos.getY(), blockPos.getZ());
        }
    }

    private static void beginIrisFluid(BlockRenderCache cache, BlockState blockState, FluidState fluidState,
            BlockPos blockPos) {
        var ids = net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings.INSTANCE.getBlockStateIds();
        if (ids != null) {
            ((net.irisshaders.iris.vertices.sodium.terrain.VertexEncoderInterface) cache.getFluidRenderer()).beginBlock(
                    ids.getInt(fluidState.createLegacyBlock()), (byte) 1, (byte) blockState.getLightEmission(),
                    blockPos.getX(), blockPos.getY(), blockPos.getZ());
        }
    }
}
