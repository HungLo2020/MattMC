package MattMC.world;

/**
 * Represents a 32x32 region of chunks (512x512 blocks horizontally).
 * Similar to Minecraft's region system:
 * - 32 chunks wide (X) = 512 blocks
 * - 384 blocks tall (Y: -64 to 319) - same as chunk
 * - 32 chunks deep (Z) = 512 blocks
 * - Total: 1024 chunks per region
 */
public class Region {
    public static final int REGION_SIZE = 32; // 32x32 chunks
    public static final int REGION_WIDTH_BLOCKS = REGION_SIZE * Chunk.WIDTH; // 512 blocks
    public static final int REGION_DEPTH_BLOCKS = REGION_SIZE * Chunk.DEPTH; // 512 blocks
    
    // Region position in region coordinates
    private final int regionX;
    private final int regionZ;
    
    // 2D array of chunks [chunkX][chunkZ]
    private final Chunk[][] chunks;
    
    public Region(int regionX, int regionZ) {
        this.regionX = regionX;
        this.regionZ = regionZ;
        this.chunks = new Chunk[REGION_SIZE][REGION_SIZE];
        
        // Initialize all chunks
        for (int cx = 0; cx < REGION_SIZE; cx++) {
            for (int cz = 0; cz < REGION_SIZE; cz++) {
                // Calculate absolute chunk coordinates
                int chunkX = regionX * REGION_SIZE + cx;
                int chunkZ = regionZ * REGION_SIZE + cz;
                chunks[cx][cz] = new Chunk(chunkX, chunkZ);
            }
        }
    }
    
    /**
     * Get a chunk at region-local coordinates.
     * @param cx 0-31 (chunk X within region)
     * @param cz 0-31 (chunk Z within region)
     */
    public Chunk getChunk(int cx, int cz) {
        if (cx < 0 || cx >= REGION_SIZE || cz < 0 || cz >= REGION_SIZE) {
            return null;
        }
        return chunks[cx][cz];
    }
    
    /**
     * Get a block at region-local block coordinates.
     * @param x 0-511 (block X within region)
     * @param y 0-383 (chunk-local Y, world Y = y + Chunk.MIN_Y)
     * @param z 0-511 (block Z within region)
     */
    public Block getBlock(int x, int y, int z) {
        if (x < 0 || x >= REGION_WIDTH_BLOCKS || z < 0 || z >= REGION_DEPTH_BLOCKS) {
            return Blocks.AIR;
        }
        
        int chunkX = x / Chunk.WIDTH;
        int chunkZ = z / Chunk.DEPTH;
        int blockX = x % Chunk.WIDTH;
        int blockZ = z % Chunk.DEPTH;
        
        Chunk chunk = getChunk(chunkX, chunkZ);
        if (chunk == null) return Blocks.AIR;
        
        return chunk.getBlock(blockX, y, blockZ);
    }
    
    /**
     * Set a block at region-local block coordinates.
     * @param x 0-511 (block X within region)
     * @param y 0-383 (chunk-local Y, world Y = y + Chunk.MIN_Y)
     * @param z 0-511 (block Z within region)
     */
    public void setBlock(int x, int y, int z, Block block) {
        if (x < 0 || x >= REGION_WIDTH_BLOCKS || z < 0 || z >= REGION_DEPTH_BLOCKS) {
            return;
        }
        
        int chunkX = x / Chunk.WIDTH;
        int chunkZ = z / Chunk.DEPTH;
        int blockX = x % Chunk.WIDTH;
        int blockZ = z % Chunk.DEPTH;
        
        Chunk chunk = getChunk(chunkX, chunkZ);
        if (chunk != null) {
            chunk.setBlock(blockX, y, blockZ, block);
        }
    }
    
    /**
     * Generate flat terrain across entire region at specified surface Y level.
     */
    public void generateFlatTerrain(int surfaceY) {
        for (int cx = 0; cx < REGION_SIZE; cx++) {
            for (int cz = 0; cz < REGION_SIZE; cz++) {
                chunks[cx][cz].generateFlatTerrain(surfaceY);
            }
        }
    }
    
    public int getRegionX() { return regionX; }
    public int getRegionZ() { return regionZ; }
    public int getRegionSize() { return REGION_SIZE; }
}
