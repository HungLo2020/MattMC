package net.minecraft.client.renderer.sodium.world;

import net.minecraft.client.renderer.sodium.render.SodiumWorldRenderer;
import net.minecraft.client.renderer.chunk.advanced.ChunkRenderMatrices;

public interface LevelRendererExtension {
    SodiumWorldRenderer sodium$getWorldRenderer();

    /**
     * Hook for mods to change the matrices.
     * @param matrices The new chunk matrices.
     */
    void sodium$setMatrices(ChunkRenderMatrices matrices);

    ChunkRenderMatrices sodium$getMatrices();
}
