package mattmc.world.level.chunk;

/**
 * Utility methods for chunk coordinate conversions and operations.
 * Centralizes common chunk-related calculations to reduce magic numbers
 * and ensure consistency across the codebase.
 */
public final class ChunkUtils {
    
    // Note: These constants reference LevelChunk for single source of truth.
    // Alternative approach: Make LevelChunk constants public and use them directly
    // throughout the codebase instead of these aliases. However, having them here
    // provides better API discoverability and cleaner call sites.
    public static final int CHUNK_WIDTH = LevelChunk.WIDTH;
    public static final int CHUNK_DEPTH = LevelChunk.DEPTH;
    public static final int CHUNK_HEIGHT = LevelChunk.HEIGHT;
    public static final int SECTION_HEIGHT = LevelChunk.SECTION_HEIGHT;
    public static final int MIN_Y = LevelChunk.MIN_Y;
    public static final int MAX_Y = LevelChunk.MAX_Y;
    
    private ChunkUtils() {
        // Utility class - no instantiation
    }
    
    // === Coordinate Conversions ===
    
    /**
     * Convert world X coordinate to chunk X coordinate.
     * @param worldX World X coordinate
     * @return Chunk X coordinate
     */
    public static int worldToChunkX(int worldX) {
        return worldX >> 4; // Divide by 16
    }
    
    /**
     * Convert world Z coordinate to chunk Z coordinate.
     * @param worldZ World Z coordinate
     * @return Chunk Z coordinate
     */
    public static int worldToChunkZ(int worldZ) {
        return worldZ >> 4; // Divide by 16
    }
    
    /**
     * Convert chunk X coordinate to world X coordinate (western edge).
     * @param chunkX Chunk X coordinate
     * @return World X coordinate of chunk's western edge
     */
    public static int chunkToWorldX(int chunkX) {
        return chunkX << 4; // Multiply by 16
    }
    
    /**
     * Convert chunk Z coordinate to world Z coordinate (northern edge).
     * @param chunkZ Chunk Z coordinate
     * @return World Z coordinate of chunk's northern edge
     */
    public static int chunkToWorldZ(int chunkZ) {
        return chunkZ << 4; // Multiply by 16
    }
    
    /**
     * Get chunk-local X coordinate from world X coordinate.
     * Note: For negative coordinates, this produces Minecraft-style wrapping behavior.
     * For example, worldX=-1 returns 15, worldX=-16 returns 0, worldX=-17 returns 15.
     * This matches Minecraft's coordinate system where negative coordinates wrap around
     * within their chunk rather than producing negative local coordinates.
     * 
     * @param worldX World X coordinate
     * @return Chunk-local X (0-15)
     */
    public static int worldToLocalX(int worldX) {
        return worldX & 15; // Modulo 16 with proper negative handling
    }
    
    /**
     * Get chunk-local Z coordinate from world Z coordinate.
     * Note: For negative coordinates, this produces Minecraft-style wrapping behavior.
     * For example, worldZ=-1 returns 15, worldZ=-16 returns 0, worldZ=-17 returns 15.
     * This matches Minecraft's coordinate system where negative coordinates wrap around
     * within their chunk rather than producing negative local coordinates.
     * 
     * @param worldZ World Z coordinate
     * @return Chunk-local Z (0-15)
     */
    public static int worldToLocalZ(int worldZ) {
        return worldZ & 15; // Modulo 16 with proper negative handling
    }
    
    /**
     * Get chunk-local Y coordinate from world Y coordinate.
     * @param worldY World Y coordinate (-64 to 319)
     * @return Chunk-local Y (0-383), or -1 if out of bounds
     */
    public static int worldToLocalY(int worldY) {
        int localY = worldY - MIN_Y;
        return (localY >= 0 && localY < CHUNK_HEIGHT) ? localY : -1;
    }
    
    /**
     * Get world Y coordinate from chunk-local Y coordinate.
     * @param localY Chunk-local Y (0-383)
     * @return World Y coordinate (-64 to 319)
     */
    public static int localToWorldY(int localY) {
        return localY + MIN_Y;
    }
    
    // === Section Calculations ===
    
    /**
     * Get section index from chunk-local Y coordinate.
     * @param localY Chunk-local Y (0-383)
     * @return Section index (0-23)
     */
    public static int getSectionIndex(int localY) {
        return localY / SECTION_HEIGHT;
    }
    
    /**
     * Get section-local Y coordinate.
     * @param localY Chunk-local Y (0-383)
     * @return Y within section (0-15)
     */
    public static int getSectionLocalY(int localY) {
        return localY % SECTION_HEIGHT;
    }
    
    /**
     * Get section index from world Y coordinate.
     * @param worldY World Y coordinate (-64 to 319)
     * @return Section index (0-23), or -1 if out of bounds
     */
    public static int worldYToSectionIndex(int worldY) {
        int localY = worldToLocalY(worldY);
        return localY >= 0 ? getSectionIndex(localY) : -1;
    }
    
    // === Validation ===
    
    /**
     * Check if world Y coordinate is valid.
     * @param worldY World Y coordinate
     * @return true if within valid range (-64 to 319)
     */
    public static boolean isValidWorldY(int worldY) {
        return worldY >= MIN_Y && worldY <= MAX_Y;
    }
    
    /**
     * Check if chunk-local coordinates are valid.
     * @param localX Local X (should be 0-15)
     * @param localY Local Y (should be 0-383)
     * @param localZ Local Z (should be 0-15)
     * @return true if all coordinates are valid
     */
    public static boolean isValidLocalCoords(int localX, int localY, int localZ) {
        return localX >= 0 && localX < CHUNK_WIDTH &&
               localY >= 0 && localY < CHUNK_HEIGHT &&
               localZ >= 0 && localZ < CHUNK_DEPTH;
    }
    
    // === Existing Methods ===
    
    /**
     * Convert chunk coordinates to a unique long key for storage.
     * Uses the same format as Minecraft: upper 32 bits for X, lower 32 bits for Z.
     */
    public static long chunkKey(int chunkX, int chunkZ) {
        return ((long)chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }
    
    /**
     * Extract chunk X coordinate from chunk key.
     */
    public static int chunkXFromKey(long key) {
        return (int)(key >> 32);
    }
    
    /**
     * Extract chunk Z coordinate from chunk key.
     */
    public static int chunkZFromKey(long key) {
        return (int)key;
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
        for (int x = 0; x < CHUNK_WIDTH; x++) {
            for (int y = startY; y < endY; y++) {
                for (int z = 0; z < CHUNK_DEPTH; z++) {
                    if (!chunk.getBlock(x, y, z).isAir()) {
                        return false; // Found a non-air block, section is not empty
                    }
                }
            }
        }
        return true; // All blocks are air, section is empty
    }
}
