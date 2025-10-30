package MattMC.world;

/**
 * Represents a 16x16x384 chunk of blocks.
 * Similar to Minecraft's chunk system:
 * - 16 blocks wide (X)
 * - 384 blocks tall (Y: -64 to 319)
 * - 16 blocks deep (Z)
 */
public final class Chunk {
    public static final int WIDTH = 16;
    public static final int HEIGHT = 384;
    public static final int DEPTH = 16;
    public static final int MIN_Y = -64;
    public static final int MAX_Y = 319;
    
    // Chunk position in world coordinates (not block coordinates)
    private final int chunkX;
    private final int chunkZ;
    
    // 3D array to store blocks [x][y][z]
    // We store the full array for simplicity, but Minecraft uses sections
    private final Block[][][] blocks;
    
    public Chunk(int chunkX, int chunkZ) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.blocks = new Block[WIDTH][HEIGHT][DEPTH];
        
        // Initialize all blocks to air by default
        for (int x = 0; x < WIDTH; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                for (int z = 0; z < DEPTH; z++) {
                    blocks[x][y][z] = new Block(BlockType.AIR);
                }
            }
        }
    }
    
    /**
     * Get a block at chunk-local coordinates.
     * @param x 0-15
     * @param y 0-383 (world Y = y + MIN_Y)
     * @param z 0-15
     */
    public Block getBlock(int x, int y, int z) {
        if (x < 0 || x >= WIDTH || y < 0 || y >= HEIGHT || z < 0 || z >= DEPTH) {
            return new Block(BlockType.AIR);
        }
        return blocks[x][y][z];
    }
    
    /**
     * Set a block at chunk-local coordinates.
     * @param x 0-15
     * @param y 0-383 (world Y = y + MIN_Y)
     * @param z 0-15
     */
    public void setBlock(int x, int y, int z, Block block) {
        if (x < 0 || x >= WIDTH || y < 0 || y >= HEIGHT || z < 0 || z >= DEPTH) {
            return;
        }
        blocks[x][y][z] = block;
    }
    
    /**
     * Convert world Y coordinate to chunk-local Y coordinate.
     */
    public static int worldYToChunkY(int worldY) {
        return worldY - MIN_Y;
    }
    
    /**
     * Convert chunk-local Y coordinate to world Y coordinate.
     */
    public static int chunkYToWorldY(int chunkY) {
        return chunkY + MIN_Y;
    }
    
    /**
     * Generate flat terrain at the specified Y level.
     * Fills from bottom to surfaceY with appropriate blocks.
     */
    public void generateFlatTerrain(int surfaceWorldY) {
        int surfaceY = worldYToChunkY(surfaceWorldY);
        
        for (int x = 0; x < WIDTH; x++) {
            for (int z = 0; z < DEPTH; z++) {
                // Fill from bottom to surface
                for (int y = 0; y <= surfaceY && y < HEIGHT; y++) {
                    BlockType type;
                    int worldY = chunkYToWorldY(y);
                    
                    if (worldY == surfaceWorldY) {
                        type = BlockType.GRASS;
                    } else if (worldY >= surfaceWorldY - 3) {
                        type = BlockType.DIRT;
                    } else {
                        type = BlockType.STONE;
                    }
                    
                    blocks[x][y][z] = new Block(type);
                }
            }
        }
    }
    
    public int chunkX() { return chunkX; }
    public int chunkZ() { return chunkZ; }
}
