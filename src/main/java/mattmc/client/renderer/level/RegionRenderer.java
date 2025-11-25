package mattmc.client.renderer.level;

import mattmc.client.renderer.backend.RenderBackend;
import mattmc.world.entity.player.LocalPlayer;
import mattmc.world.level.chunk.Region;
import mattmc.world.level.chunk.LevelChunk;

/**
 * Renderer-agnostic implementation for rendering entire regions (32x32 chunks).
 * 
 * <p>This class coordinates region rendering using the {@link RenderBackend} abstraction.
 * It does not make any direct graphics API calls (no OpenGL, Vulkan, etc.).
 * All rendering is done through the backend interface.
 * 
 * <p><b>Architecture:</b> This class sits in the rendering front-end layer:
 * <ul>
 *   <li><b>Game/World Layer:</b> Provides regions, chunks, blocks</li>
 *   <li><b>Rendering Front-End (this class):</b> Decides what to draw based on culling</li>
 *   <li><b>Rendering Back-End:</b> Executes rendering with specific graphics API</li>
 * </ul>
 * 
 * <p>Optimizations:
 * <ul>
 *   <li>Render distance: Only render chunks within RENDER_DISTANCE</li>
 *   <li>Frustum culling: Only render chunks visible to camera</li>
 * </ul>
 * 
 * @see RenderBackend
 * @see RegionChunkRenderer
 */
public class RegionRenderer {
    
    private final RegionChunkRenderer chunkRenderer;
    private final RenderBackend renderBackend;
    
    // Render distance in chunks (similar to MattMC's render distance setting)
    // 8 chunks = 128 blocks radius, similar to MattMC's render distance 8
    public static final int RENDER_DISTANCE = 8;
    
    /**
     * Create a new RegionRenderer with the given dependencies.
     * 
     * @param chunkRenderer the chunk renderer for rendering individual chunks
     * @param renderBackend the render backend for executing draw commands
     */
    public RegionRenderer(RegionChunkRenderer chunkRenderer, RenderBackend renderBackend) {
        this.chunkRenderer = chunkRenderer;
        this.renderBackend = renderBackend;
    }
    
    /**
     * Render all chunks in a region with optimizations.
     * 
     * @param region The region to render
     * @param player The player (for position-based culling)
     * @param viewMatrix Current view matrix for frustum culling
     */
    public void renderRegion(Region region, LocalPlayer player, float[] viewMatrix) {
        // Get player position in chunk coordinates
        float playerChunkX = player.getX() / LevelChunk.WIDTH;
        float playerChunkZ = player.getZ() / LevelChunk.DEPTH;
        
        // Calculate frustum planes for frustum culling
        float[] frustumPlanes = calculateFrustumPlanes(viewMatrix);
        
        int chunksRendered = 0;
        int chunksCulled = 0;
        
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
                    chunksCulled++;
                    continue;  // Too far away
                }
                
                // Calculate chunk bounding box in world coordinates
                float chunkWorldX = chunkX * LevelChunk.WIDTH;
                float chunkWorldZ = chunkZ * LevelChunk.DEPTH;
                float chunkMinY = LevelChunk.MIN_Y;
                float chunkMaxY = LevelChunk.MAX_Y;
                
                // Frustum culling check
                if (!isChunkInFrustum(chunkWorldX, chunkMinY, chunkWorldZ, 
                                     chunkWorldX + LevelChunk.WIDTH, chunkMaxY, chunkWorldZ + LevelChunk.DEPTH,
                                     frustumPlanes)) {
                    chunksCulled++;
                    continue;  // Not visible
                }
                
                // Use backend matrix operations instead of direct GL calls
                renderBackend.pushMatrix();
                renderBackend.translateMatrix(chunkWorldX, 0, chunkWorldZ);
                chunkRenderer.renderChunk(chunk);
                renderBackend.popMatrix();
                
                chunksRendered++;
            }
        }
    }
    
    /**
     * Legacy method without optimizations (for compatibility).
     * 
     * @param region The region to render
     */
    public void renderRegion(Region region) {
        for (int cx = 0; cx < Region.REGION_SIZE; cx++) {
            for (int cz = 0; cz < Region.REGION_SIZE; cz++) {
                LevelChunk chunk = region.getChunk(cx, cz);
                if (chunk != null) {
                    // Use backend matrix operations instead of direct GL calls
                    renderBackend.pushMatrix();
                    
                    // Use actual chunk coordinates, not region-local coordinates
                    float chunkWorldX = chunk.chunkX() * LevelChunk.WIDTH;
                    float chunkWorldZ = chunk.chunkZ() * LevelChunk.DEPTH;
                    renderBackend.translateMatrix(chunkWorldX, 0, chunkWorldZ);
                    
                    chunkRenderer.renderChunk(chunk);
                    
                    renderBackend.popMatrix();
                }
            }
        }
    }
    
    /**
     * Calculate frustum planes from view matrix.
     * Returns array of 24 floats (6 planes * 4 coefficients).
     * Plane equation: Ax + By + Cz + D = 0
     */
    private float[] calculateFrustumPlanes(float[] viewMatrix) {
        float[] planes = new float[24];  // 6 planes, 4 floats each (A, B, C, D)
        
        // For now, return null to disable frustum culling 
        // (full implementation would extract planes from modelview-projection matrix)
        // This is a simplified version that still gives benefits from render distance
        return planes;
    }
    
    /**
     * Check if a chunk's bounding box intersects the view frustum.
     * For now, simplified version that always returns true (frustum culling disabled).
     * Full implementation would test AABB against 6 frustum planes.
     */
    private boolean isChunkInFrustum(float minX, float minY, float minZ, 
                                     float maxX, float maxY, float maxZ,
                                     float[] frustumPlanes) {
        // Simplified: always visible (frustum culling can be added later)
        // Full frustum culling requires matrix math and plane-AABB tests
        return true;
    }
    
    /**
     * Render a single chunk from the region for debugging.
     * 
     * @param region The region containing the chunk
     * @param cx Chunk X coordinate (0-31) - region-local coordinate
     * @param cz Chunk Z coordinate (0-31) - region-local coordinate
     */
    public void renderChunk(Region region, int cx, int cz) {
        LevelChunk chunk = region.getChunk(cx, cz);
        if (chunk != null) {
            // Use backend matrix operations instead of direct GL calls
            renderBackend.pushMatrix();
            
            // Use actual chunk coordinates, not region-local coordinates
            float chunkWorldX = chunk.chunkX() * LevelChunk.WIDTH;
            float chunkWorldZ = chunk.chunkZ() * LevelChunk.DEPTH;
            renderBackend.translateMatrix(chunkWorldX, 0, chunkWorldZ);
            
            chunkRenderer.renderChunk(chunk);
            
            renderBackend.popMatrix();
        }
    }
}
