package mattmc.client.renderer.level;

import mattmc.world.entity.player.LocalPlayer;
import mattmc.world.level.chunk.Region;
import mattmc.world.level.chunk.LevelChunk;

/**
 * Handles rendering of entire regions (32x32 chunks).
 * 
 * <p>This class focuses on game logic (render distance, chunk iteration) and is mostly
 * backend-agnostic. It uses a callback pattern to delegate actual rendering to the caller.
 * 
 * <p><b>Refactored:</b> This class has been moved from backend/opengl to renderer/level
 * and refactored to use callbacks instead of direct OpenGL calls. The game logic of
 * determining which chunks to render is separated from the rendering implementation.
 * 
 * <p>Optimizations:
 * <ul>
 *   <li>Render distance: Only render chunks within RENDER_DISTANCE</li>
 *   <li>Frustum culling: Only render chunks visible to camera (future enhancement)</li>
 * </ul>
 */
public class RegionRenderer {
    // Render distance in chunks (similar to Minecraft's render distance setting)
    // 8 chunks = 128 blocks radius, similar to Minecraft's render distance 8
    public static final int RENDER_DISTANCE = 8;
    
    /**
     * Render all chunks in a region with optimizations.
     * 
     * @param region The region to render
     * @param player The player (for position-based culling)
     * @param renderCallback Callback for rendering each chunk (e.g., to handle transforms)
     */
    public void renderRegion(Region region, LocalPlayer player, ChunkRenderCallback renderCallback) {
        // Get player position in chunk coordinates
        float playerChunkX = player.getX() / LevelChunk.WIDTH;
        float playerChunkZ = player.getZ() / LevelChunk.DEPTH;
        
        for (int cx = 0; cx < Region.REGION_SIZE; cx++) {
            for (int cz = 0; cz < Region.REGION_SIZE; cz++) {
                LevelChunk chunk = region.getChunk(cx, cz);
                if (chunk == null) {
                    continue;
                }
                
                // Use actual chunk coordinates for distance calculation
                int chunkX = chunk.chunkX();
                int chunkZ = chunk.chunkZ();
                
                // Render distance check (Manhattan distance approximation)
                float dx = Math.abs(chunkX - playerChunkX);
                float dz = Math.abs(chunkZ - playerChunkZ);
                if (dx + dz > RENDER_DISTANCE * 1.5f) {
                    continue;  // Too far away
                }
                
                // Calculate chunk world position for transform
                float chunkWorldX = chunkX * LevelChunk.WIDTH;
                float chunkWorldZ = chunkZ * LevelChunk.DEPTH;
                
                // Render via callback (allows backend-specific transform handling)
                renderCallback.renderChunk(chunk, chunkWorldX, 0, chunkWorldZ);
            }
        }
    }
    
    /**
     * Legacy method without optimizations (for compatibility).
     * 
     * @param region The region to render
     * @param renderCallback Callback for rendering each chunk
     */
    public void renderRegion(Region region, ChunkRenderCallback renderCallback) {
        for (int cx = 0; cx < Region.REGION_SIZE; cx++) {
            for (int cz = 0; cz < Region.REGION_SIZE; cz++) {
                LevelChunk chunk = region.getChunk(cx, cz);
                if (chunk != null) {
                    // Use actual chunk coordinates, not region-local coordinates
                    float chunkWorldX = chunk.chunkX() * LevelChunk.WIDTH;
                    float chunkWorldZ = chunk.chunkZ() * LevelChunk.DEPTH;
                    
                    renderCallback.renderChunk(chunk, chunkWorldX, 0, chunkWorldZ);
                }
            }
        }
    }
    
    /**
     * Render a single chunk from the region for debugging.
     * 
     * @param region The region containing the chunk
     * @param cx Chunk X coordinate (0-31) - region-local coordinate
     * @param cz Chunk Z coordinate (0-31) - region-local coordinate
     * @param renderCallback Callback for rendering the chunk
     */
    public void renderChunk(Region region, int cx, int cz, ChunkRenderCallback renderCallback) {
        LevelChunk chunk = region.getChunk(cx, cz);
        if (chunk != null) {
            // Use actual chunk coordinates, not region-local coordinates
            float chunkWorldX = chunk.chunkX() * LevelChunk.WIDTH;
            float chunkWorldZ = chunk.chunkZ() * LevelChunk.DEPTH;
            
            renderCallback.renderChunk(chunk, chunkWorldX, 0, chunkWorldZ);
        }
    }
    
    /**
     * Callback interface for rendering chunks.
     * This allows the rendering backend to handle transforms and actual rendering.
     */
    @FunctionalInterface
    public interface ChunkRenderCallback {
        /**
         * Render a chunk at the given world position.
         * 
         * @param chunk The chunk to render
         * @param worldX World X coordinate
         * @param worldY World Y coordinate
         * @param worldZ World Z coordinate
         */
        void renderChunk(LevelChunk chunk, float worldX, float worldY, float worldZ);
    }
}
