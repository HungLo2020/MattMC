package MattMC.renderer;

import MattMC.player.Player;
import MattMC.world.Block;
import MattMC.world.BlockType;
import MattMC.world.Chunk;
import MattMC.world.Region;

import static org.lwjgl.opengl.GL11.*;

/**
 * Handles rendering of entire regions (32x32 chunks).
 * Similar to Minecraft's WorldRenderer with chunk batching.
 * 
 * Optimizations:
 * - Render distance: Only render chunks within RENDER_DISTANCE
 * - Frustum culling: Only render chunks visible to camera
 */
public class RegionRenderer {
    private final ChunkRenderer chunkRenderer;
    
    // Render distance in chunks (similar to Minecraft's render distance setting)
    // 8 chunks = 128 blocks radius, similar to Minecraft's render distance 8
    public static final int RENDER_DISTANCE = 8;
    
    public RegionRenderer() {
        this.chunkRenderer = new ChunkRenderer();
    }
    
    /**
     * Render all chunks in a region with optimizations.
     * @param region The region to render
     * @param player The player (for position-based culling)
     * @param viewMatrix Current view matrix for frustum culling
     */
    public void renderRegion(Region region, Player player, float[] viewMatrix) {
        // Get player position in chunk coordinates
        float playerChunkX = player.getX() / Chunk.WIDTH;
        float playerChunkZ = player.getZ() / Chunk.DEPTH;
        
        // Calculate frustum planes for frustum culling
        float[] frustumPlanes = calculateFrustumPlanes(viewMatrix);
        
        int chunksRendered = 0;
        int chunksCulled = 0;
        
        for (int cx = 0; cx < Region.REGION_SIZE; cx++) {
            for (int cz = 0; cz < Region.REGION_SIZE; cz++) {
                // Render distance check (Manhattan distance approximation)
                float dx = Math.abs(cx - playerChunkX);
                float dz = Math.abs(cz - playerChunkZ);
                if (dx + dz > RENDER_DISTANCE * 1.5f) {
                    chunksCulled++;
                    continue;  // Too far away
                }
                
                Chunk chunk = region.getChunk(cx, cz);
                if (chunk == null) {
                    continue;
                }
                
                // Calculate chunk bounding box in world coordinates
                float chunkWorldX = cx * Chunk.WIDTH;
                float chunkWorldZ = cz * Chunk.DEPTH;
                float chunkMinY = Chunk.MIN_Y;
                float chunkMaxY = Chunk.MAX_Y;
                
                // Frustum culling check
                if (!isChunkInFrustum(chunkWorldX, chunkMinY, chunkWorldZ, 
                                     chunkWorldX + Chunk.WIDTH, chunkMaxY, chunkWorldZ + Chunk.DEPTH,
                                     frustumPlanes)) {
                    chunksCulled++;
                    continue;  // Not visible
                }
                
                glPushMatrix();
                glTranslatef(chunkWorldX, 0, chunkWorldZ);
                chunkRenderer.renderChunk(chunk);
                glPopMatrix();
                
                chunksRendered++;
            }
        }
    }
    
    /**
     * Legacy method without optimizations (for compatibility).
     */
    public void renderRegion(Region region) {
        for (int cx = 0; cx < Region.REGION_SIZE; cx++) {
            for (int cz = 0; cz < Region.REGION_SIZE; cz++) {
                Chunk chunk = region.getChunk(cx, cz);
                if (chunk != null) {
                    glPushMatrix();
                    
                    float chunkWorldX = cx * Chunk.WIDTH;
                    float chunkWorldZ = cz * Chunk.DEPTH;
                    glTranslatef(chunkWorldX, 0, chunkWorldZ);
                    
                    chunkRenderer.renderChunk(chunk);
                    
                    glPopMatrix();
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
     * @param region The region containing the chunk
     * @param cx Chunk X coordinate (0-31)
     * @param cz Chunk Z coordinate (0-31)
     */
    public void renderChunk(Region region, int cx, int cz) {
        Chunk chunk = region.getChunk(cx, cz);
        if (chunk != null) {
            glPushMatrix();
            
            float chunkWorldX = cx * Chunk.WIDTH;
            float chunkWorldZ = cz * Chunk.DEPTH;
            glTranslatef(chunkWorldX, 0, chunkWorldZ);
            
            chunkRenderer.renderChunk(chunk);
            
            glPopMatrix();
        }
    }
}
