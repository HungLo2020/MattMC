package net.sodium.client.world;

import net.sodium.client.render.SodiumWorldRenderer;
import net.sodium.client.render.chunk.ChunkRenderMatrices;

public interface LevelRendererExtension {
    SodiumWorldRenderer sodium$getWorldRenderer();

    /**
     * Hook for mods to change the matrices.
     * @param matrices The new chunk matrices.
     */
    void sodium$setMatrices(ChunkRenderMatrices matrices);

    ChunkRenderMatrices sodium$getMatrices();
}
