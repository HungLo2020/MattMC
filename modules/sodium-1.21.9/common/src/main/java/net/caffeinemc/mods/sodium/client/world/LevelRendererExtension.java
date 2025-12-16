package net.caffeinemc.mods.sodium.client.world;

import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
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
