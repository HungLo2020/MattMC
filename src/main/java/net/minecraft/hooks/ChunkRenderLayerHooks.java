package net.minecraft.hooks;

import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;

/**
 * Hook interface for chunk section layer rendering.
 * Allows mods to inject custom rendering at specific chunk layer groups.
 */
public interface ChunkRenderLayerHooks {
    /**
     * Called before a chunk section layer group is rendered.
     * Allows mods to perform custom rendering for specific layers.
     * 
     * @param layerGroup The chunk section layer group being rendered
     */
    void onBeforeRenderLayer(ChunkSectionLayerGroup layerGroup);
}
