package mattmc.world.level.chunk;

import mattmc.world.level.block.Block;
import mattmc.world.level.block.Blocks;
import mattmc.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents a 16x16x384 chunk of blocks.
 * Similar to Minecraft's chunk system:
 * - 16 blocks wide (X)
 * - 384 blocks tall (Y: -64 to 319)
 * - 16 blocks deep (Z)
 */
public final class LevelChunk {
    public static final int WIDTH = 16;
    public static final int HEIGHT = 384;
    public static final int DEPTH = 16;
    public static final int MIN_Y = -64;
    public static final int MAX_Y = 319;
    
    // LevelChunk position in world coordinates (not block coordinates)
    private final int chunkX;
    private final int chunkZ;
    
    // 3D array to store blocks [x][y][z]
    // We store the full array for simplicity, but Minecraft uses sections
    private final Block[][][] blocks;
    
    // Block states for blocks that need them (sparse storage)
    private final Map<Long, BlockState> blockStates;
    
    // Light storage for each 16×16×16 section (HEIGHT / 16 sections)
    private final LightStorage[] lightSections;
    
    // Heightmap tracking topmost non-air block per column
    private final ColumnHeightmap heightmap;
    
    // Dirty flag: marks if chunk needs to be re-rendered (for display list caching)
    private boolean dirty = true;
    
    public LevelChunk(int chunkX, int chunkZ) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.blocks = new Block[WIDTH][HEIGHT][DEPTH];
        this.blockStates = new HashMap<>();
        
        // Initialize light storage for each section
        int numSections = HEIGHT / 16;
        this.lightSections = new LightStorage[numSections];
        for (int i = 0; i < numSections; i++) {
            this.lightSections[i] = new LightStorage();
        }
        
        // Initialize heightmap
        this.heightmap = new ColumnHeightmap();
        
        // Initialize all blocks to air using Arrays.fill for better performance
        for (int x = 0; x < WIDTH; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                java.util.Arrays.fill(blocks[x][y], Blocks.AIR);
            }
        }
    }
    
    /**
     * Get position key for blockstate map.
     */
    private long getPositionKey(int x, int y, int z) {
        return ((long)x << 32) | ((long)y << 16) | (long)z;
    }
    
    /**
     * Get a block at chunk-local coordinates.
     * @param x 0-15
     * @param y 0-383 (world Y = y + MIN_Y)
     * @param z 0-15
     */
    public Block getBlock(int x, int y, int z) {
        if (x < 0 || x >= WIDTH || y < 0 || y >= HEIGHT || z < 0 || z >= DEPTH) {
            return Blocks.AIR;
        }
        return blocks[x][y][z];
    }
    
    /**
     * Get a blockstate at chunk-local coordinates.
     * Returns null if no blockstate exists.
     */
    public BlockState getBlockState(int x, int y, int z) {
        if (x < 0 || x >= WIDTH || y < 0 || y >= HEIGHT || z < 0 || z >= DEPTH) {
            return null;
        }
        return blockStates.get(getPositionKey(x, y, z));
    }
    
    /**
     * Set a block at chunk-local coordinates.
     * @param x 0-15
     * @param y 0-383 (world Y = y + MIN_Y)
     * @param z 0-15
     */
    public void setBlock(int x, int y, int z, Block block) {
        setBlock(x, y, z, block, null);
    }
    
    /**
     * Set a block with blockstate at chunk-local coordinates.
     * @param x 0-15
     * @param y 0-383 (world Y = y + MIN_Y)
     * @param z 0-15
     * @param block The block to place
     * @param state The blockstate (can be null for stateless blocks)
     */
    public void setBlock(int x, int y, int z, Block block, BlockState state) {
        if (x < 0 || x >= WIDTH || y < 0 || y >= HEIGHT || z < 0 || z >= DEPTH) {
            return;
        }
        blocks[x][y][z] = block;
        
        // Store or remove blockstate
        long key = getPositionKey(x, y, z);
        if (state != null) {
            blockStates.put(key, state);
        } else {
            blockStates.remove(key);
        }
        
        this.dirty = true;  // Mark chunk as needing re-render
    }
    
    /**
     * Check if chunk has been modified and needs re-rendering.
     */
    public boolean isDirty() {
        return dirty;
    }
    
    /**
     * Mark chunk as clean (already rendered).
     */
    public void setDirty(boolean dirty) {
        this.dirty = dirty;
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
                    Block block;
                    int worldY = chunkYToWorldY(y);
                    
                    if (worldY == surfaceWorldY) {
                        block = Blocks.GRASS_BLOCK;
                    } else if (worldY >= surfaceWorldY - 3) {
                        block = Blocks.DIRT;
                    } else {
                        block = Blocks.STONE;
                    }
                    
                    blocks[x][y][z] = block;
                }
            }
        }
    }
    
    /**
     * Get sky light level at chunk-local coordinates.
     * @param x 0-15
     * @param y 0-383 (world Y = y + MIN_Y)
     * @param z 0-15
     * @return Sky light level (0-15)
     */
    public int getSkyLight(int x, int y, int z) {
        if (x < 0 || x >= WIDTH || y < 0 || y >= HEIGHT || z < 0 || z >= DEPTH) {
            return 0;
        }
        int sectionIndex = y / 16;
        int sectionY = y % 16;
        return lightSections[sectionIndex].getSky(x, sectionY, z);
    }
    
    /**
     * Get block light level at chunk-local coordinates.
     * @param x 0-15
     * @param y 0-383 (world Y = y + MIN_Y)
     * @param z 0-15
     * @return Block light level (0-15)
     */
    public int getBlockLight(int x, int y, int z) {
        if (x < 0 || x >= WIDTH || y < 0 || y >= HEIGHT || z < 0 || z >= DEPTH) {
            return 0;
        }
        int sectionIndex = y / 16;
        int sectionY = y % 16;
        return lightSections[sectionIndex].getBlock(x, sectionY, z);
    }
    
    /**
     * Set sky light level at chunk-local coordinates.
     * @param x 0-15
     * @param y 0-383 (world Y = y + MIN_Y)
     * @param z 0-15
     * @param level Sky light level (0-15)
     */
    public void setSkyLight(int x, int y, int z, int level) {
        if (x < 0 || x >= WIDTH || y < 0 || y >= HEIGHT || z < 0 || z >= DEPTH) {
            return;
        }
        int sectionIndex = y / 16;
        int sectionY = y % 16;
        lightSections[sectionIndex].setSky(x, sectionY, z, level);
    }
    
    /**
     * Set block light level at chunk-local coordinates.
     * @param x 0-15
     * @param y 0-383 (world Y = y + MIN_Y)
     * @param z 0-15
     * @param level Block light level (0-15)
     */
    public void setBlockLight(int x, int y, int z, int level) {
        if (x < 0 || x >= WIDTH || y < 0 || y >= HEIGHT || z < 0 || z >= DEPTH) {
            return;
        }
        int sectionIndex = y / 16;
        int sectionY = y % 16;
        lightSections[sectionIndex].setBlock(x, sectionY, z, level);
    }
    
    /**
     * Get the heightmap for this chunk.
     */
    public ColumnHeightmap getHeightmap() {
        return heightmap;
    }
    
    /**
     * Get the light storage for a specific section.
     * @param sectionIndex Section index (0 to HEIGHT/16 - 1)
     */
    public LightStorage getLightSection(int sectionIndex) {
        if (sectionIndex < 0 || sectionIndex >= lightSections.length) {
            return null;
        }
        return lightSections[sectionIndex];
    }
    
    /**
     * Set the light storage for a specific section.
     * @param sectionIndex Section index (0 to HEIGHT/16 - 1)
     * @param lightStorage The light storage to set
     */
    public void setLightSection(int sectionIndex, LightStorage lightStorage) {
        if (sectionIndex >= 0 && sectionIndex < lightSections.length) {
            lightSections[sectionIndex] = lightStorage;
        }
    }
    
    public int chunkX() { return chunkX; }
    public int chunkZ() { return chunkZ; }
}
