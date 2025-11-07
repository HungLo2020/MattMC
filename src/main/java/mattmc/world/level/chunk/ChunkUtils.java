package mattmc.world.level.chunk;

/**
 * Utility methods for chunk operations shared across multiple classes.
 */
public final class ChunkUtils {
    
    private ChunkUtils() {
        // Utility class - no instantiation
    }
    
    /**
     * Convert chunk coordinates to a unique long key for storage.
     * Uses the same format as Minecraft: upper 32 bits for X, lower 32 bits for Z.
     */
    public static long chunkKey(int chunkX, int chunkZ) {
        return ((long)chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }
    
    /**
     * Check if a chunk section is empty (all air blocks).
     * Performs a thorough check of all blocks in the section.
     * 
     * @param chunk The chunk to check
     * @param startY Start Y coordinate (chunk-local)
     * @param endY End Y coordinate (chunk-local, exclusive)
     * @return true if section is completely empty
     */
    public static boolean isSectionEmpty(LevelChunk chunk, int startY, int endY) {
        // Check all blocks in the section to ensure accuracy
        // This is more thorough than sampling and prevents invisible block bugs
        for (int x = 0; x < LevelChunk.WIDTH; x++) {
            for (int y = startY; y < endY; y++) {
                for (int z = 0; z < LevelChunk.DEPTH; z++) {
                    if (!chunk.getBlock(x, y, z).isAir()) {
                        return false; // Found a non-air block, section is not empty
                    }
                }
            }
        }
        return true; // All blocks are air, section is empty
    }
}
