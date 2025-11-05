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
     * Samples key positions to quickly determine if the section is empty.
     * 
     * @param chunk The chunk to check
     * @param startY Start Y coordinate (chunk-local)
     * @param endY End Y coordinate (chunk-local, exclusive)
     * @return true if section appears empty
     */
    public static boolean isSectionEmpty(LevelChunk chunk, int startY, int endY) {
        int midY = (startY + endY) / 2;
        
        // Sample corners and center
        if (!chunk.getBlock(0, startY, 0).isAir()) return false;
        if (!chunk.getBlock(15, startY, 15).isAir()) return false;
        if (!chunk.getBlock(0, midY, 0).isAir()) return false;
        if (!chunk.getBlock(15, midY, 15).isAir()) return false;
        if (!chunk.getBlock(0, endY - 1, 0).isAir()) return false;
        if (!chunk.getBlock(15, endY - 1, 15).isAir()) return false;
        if (!chunk.getBlock(8, midY, 8).isAir()) return false;
        
        return true;
    }
}
