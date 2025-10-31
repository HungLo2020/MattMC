package MattMC.renderer;

import MattMC.world.Block;
import MattMC.world.BlockType;
import MattMC.world.Chunk;
import MattMC.world.Region;

import static org.lwjgl.opengl.GL11.*;

/**
 * Handles rendering of entire regions (32x32 chunks).
 * Similar to Minecraft's WorldRenderer with chunk batching.
 */
public class RegionRenderer {
    private final ChunkRenderer chunkRenderer;
    
    public RegionRenderer() {
        this.chunkRenderer = new ChunkRenderer();
    }
    
    /**
     * Render all chunks in a region.
     * @param region The region to render
     */
    public void renderRegion(Region region) {
        for (int cx = 0; cx < Region.REGION_SIZE; cx++) {
            for (int cz = 0; cz < Region.REGION_SIZE; cz++) {
                Chunk chunk = region.getChunk(cx, cz);
                if (chunk != null) {
                    glPushMatrix();
                    
                    // Translate to chunk's world position
                    // Each chunk is 16 blocks, so chunk at (cx, cz) starts at (cx*16, cz*16)
                    float chunkWorldX = cx * Chunk.WIDTH;
                    float chunkWorldZ = cz * Chunk.DEPTH;
                    glTranslatef(chunkWorldX, 0, chunkWorldZ);
                    
                    // Render the chunk
                    chunkRenderer.renderChunk(chunk);
                    
                    glPopMatrix();
                }
            }
        }
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
