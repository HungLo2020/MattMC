package net.minecraft.client.renderer.sodium.util;

import net.minecraft.client.renderer.sodium.render.SodiumWorldRenderer;
import net.minecraft.client.renderer.chunk.advanced.ChunkRenderMatrices;

public interface SodiumChunkSection {
    void sodium$setRendering(SodiumWorldRenderer renderer, ChunkRenderMatrices matrices, double x, double y, double z);
}
