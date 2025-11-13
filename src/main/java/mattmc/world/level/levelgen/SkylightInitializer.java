package mattmc.world.level.levelgen;

import mattmc.world.level.block.Block;
import mattmc.world.level.chunk.LevelChunk;

/**
 * Initializes skylight for a generated chunk column without neighbors.
 * 
 * This performs a top-down fill:
 * - For each (x,z) column, iterate from world max Y down to 0
 * - Set skylight=15 above heightmap until hitting first opaque block
 * - Opaque blocks stop skylight propagation
 * - All block light is zeroed
 */
public class SkylightInitializer {
    
    /**
     * Initialize skylight for a chunk column.
     * This method assumes the chunk has no neighbors and performs a simple top-down fill.
     * 
     * Algorithm:
     * 1. For each (x,z) column in the chunk
     * 2. Iterate from the top (MAX_Y) down to the bottom (MIN_Y)
     * 3. Set skylight=15 until encountering the first opaque block
     * 4. Once an opaque block is found, stop and set remaining blocks to skylight=0
     * 5. Zero all block light values
     * 
     * @param chunk The chunk to initialize lighting for
     */
    public static void initializeChunk(LevelChunk chunk) {
        // Zero all block light first
        for (int x = 0; x < LevelChunk.WIDTH; x++) {
            for (int z = 0; z < LevelChunk.DEPTH; z++) {
                for (int y = 0; y < LevelChunk.HEIGHT; y++) {
                    chunk.setBlockLight(x, y, z, 0);
                }
            }
        }
        
        // Initialize skylight for each column
        for (int x = 0; x < LevelChunk.WIDTH; x++) {
            for (int z = 0; z < LevelChunk.DEPTH; z++) {
                initializeColumn(chunk, x, z);
            }
        }
    }
    
    /**
     * Initialize skylight for a single column.
     * Iterates from top to bottom, setting skylight=15 until hitting an opaque block.
     * 
     * @param chunk The chunk
     * @param x Column X coordinate (0-15)
     * @param z Column Z coordinate (0-15)
     */
    private static void initializeColumn(LevelChunk chunk, int x, int z) {
        boolean hitOpaque = false;
        
        // Iterate from top to bottom
        for (int y = LevelChunk.HEIGHT - 1; y >= 0; y--) {
            Block block = chunk.getBlock(x, y, z);
            
            // Check if this block is opaque
            if (block.isOpaque()) {
                hitOpaque = true;
                chunk.setSkyLight(x, y, z, 0); // Opaque blocks have no skylight
            } else if (!hitOpaque) {
                // Still in open sky - set full brightness
                chunk.setSkyLight(x, y, z, 15);
            } else {
                // Below opaque blocks - no skylight
                chunk.setSkyLight(x, y, z, 0);
            }
        }
    }
}
