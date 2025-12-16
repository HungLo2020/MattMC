package net.minecraft.client.renderer.advanced.chunk;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.culling.Frustum;

/**
 * Abstraction for chunk rendering implementations.
 * 
 * <p>This interface allows switching between vanilla and Sodium-optimized rendering paths
 * at runtime based on configuration. Implementations should handle all aspects of chunk
 * rendering including culling, meshing, and draw call submission.</p>
 * 
 * <p><b>Implementation Note:</b> This is part of STEP7-8PLAN.md Step 2, creating the
 * abstraction layer needed for switchable rendering paths during Sodium integration.</p>
 * 
 * @since Step 7-8 Integration
 */
public interface ChunkRenderer {
    /**
     * Renders all visible chunks from the given camera perspective.
     * 
     * @param camera the camera viewpoint for rendering
     * @param frustum the view frustum for culling
     * @param spectator whether the camera is in spectator mode
     */
    void renderChunks(Camera camera, Frustum frustum, boolean spectator);
    
    /**
     * Schedules a chunk section for rebuild/remesh.
     * 
     * @param x chunk section X coordinate
     * @param y chunk section Y coordinate  
     * @param z chunk section Z coordinate
     * @param important whether this rebuild is high priority
     */
    void scheduleChunkRebuild(int x, int y, int z, boolean important);
    
    /**
     * Performs cleanup and resource release.
     * Called when the renderer is no longer needed.
     */
    void cleanup();
}
