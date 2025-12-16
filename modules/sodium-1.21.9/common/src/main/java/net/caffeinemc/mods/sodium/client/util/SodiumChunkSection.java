package net.caffeinemc.mods.sodium.client.util;

import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.minecraft.client.renderer.chunk.advanced.ChunkRenderMatrices;

public interface SodiumChunkSection {
    void sodium$setRendering(SodiumWorldRenderer renderer, ChunkRenderMatrices matrices, double x, double y, double z);
}
