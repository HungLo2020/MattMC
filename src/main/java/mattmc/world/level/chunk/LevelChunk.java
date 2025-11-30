package mattmc.world.level.chunk;

import mattmc.world.level.block.Block;
import mattmc.registries.Blocks;
import mattmc.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents a 16x16x384 chunk of blocks.
 * Similar to MattMC's chunk system:
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
    public static final int SECTION_HEIGHT = 16;
    public static final int NUM_SECTIONS = HEIGHT / SECTION_HEIGHT; // 24 sections
    
    // LevelChunk position in world coordinates (not block coordinates)
    private final int chunkX;
    private final int chunkZ;
    
    // 3D array to store blocks [x][y][z]
    // We store the full array for simplicity, but MattMC uses sections
    private final Block[][][] blocks;
    
    // Block states for blocks that need them (sparse storage)
    private final Map<Long, BlockState> blockStates;
    
    // Light storage per section (one LightStorage per 16x16x16 section)
    private final LightStorage[] lightSections;
    
    // Heightmap tracking topmost non-air block per column
    private final ColumnHeightmap heightmap;
    
    // Dirty flag: marks if chunk needs to be re-rendered (for display list caching)
    private boolean dirty = true;
    
    // Suppress light updates flag: set true during bulk operations like terrain generation
    private boolean suppressLightUpdates = false;
    
    // World light manager reference (optional, for automatic light updates)
    private mattmc.world.level.lighting.WorldLightManager worldLightManager = null;
    
    /**
     * Callback interface for marking neighbor chunks dirty when light changes at chunk edges.
     * This is needed for smooth lighting which samples a 3x3x3 grid around each block.
     */
    public interface NeighborDirtyCallback {
        /**
         * Mark the chunk at the given position dirty.
         * @param chunkX The chunk X coordinate
         * @param chunkZ The chunk Z coordinate
         */
        void markChunkDirty(int chunkX, int chunkZ);
    }
    
    // Callback for marking neighbor chunks dirty (optional)
    private NeighborDirtyCallback neighborDirtyCallback = null;
    
    /**
     * Set the neighbor dirty callback for smooth lighting updates.
     * When light changes at a chunk edge, this callback marks adjacent chunks dirty.
     */
    public void setNeighborDirtyCallback(NeighborDirtyCallback callback) {
        this.neighborDirtyCallback = callback;
    }
    
    public LevelChunk(int chunkX, int chunkZ) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.blocks = new Block[WIDTH][HEIGHT][DEPTH];
        this.blockStates = new HashMap<>();
        
        // Initialize light storage for all sections
        this.lightSections = new LightStorage[NUM_SECTIONS];
        for (int i = 0; i < NUM_SECTIONS; i++) {
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
        Block block = blocks[x][y][z];
        return block != null ? block : Blocks.AIR;
    }
    
    /**
     * Get a block at chunk-local coordinates WITHOUT bounds checking.
     * 
     * ISSUE-003 fix: For interior blocks where bounds are guaranteed valid,
     * this avoids redundant bounds checks that waste CPU cycles.
     * 
     * WARNING: Caller MUST ensure coordinates are within valid range:
     * - x: 0 to WIDTH-1 (0-15)
     * - y: 0 to HEIGHT-1 (0-383)
     * - z: 0 to DEPTH-1 (0-15)
     * 
     * @param x 0-15
     * @param y 0-383 (world Y = y + MIN_Y)
     * @param z 0-15
     * @return The block at the given position, or AIR if null
     */
    public Block getBlockUnchecked(int x, int y, int z) {
        Block block = blocks[x][y][z];
        return block != null ? block : Blocks.AIR;
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
        
        // Get the old block for light updates
        Block oldBlock = blocks[x][y][z];
        
        // Update the block
        blocks[x][y][z] = block;
        
        // Store or remove blockstate
        long key = getPositionKey(x, y, z);
        if (state != null) {
            blockStates.put(key, state);
        } else {
            blockStates.remove(key);
        }
        
        // Update block light if emission or opacity changed
        if (!suppressLightUpdates && worldLightManager != null &&
            (oldBlock.getLightEmissionR() != block.getLightEmissionR() ||
             oldBlock.getLightEmissionG() != block.getLightEmissionG() ||
             oldBlock.getLightEmissionB() != block.getLightEmissionB() ||
             oldBlock.getOpacity() != block.getOpacity())) {
            worldLightManager.updateBlockLight(this, x, y, z, block, oldBlock);
            
            // Update skylight if opacity changed (column update)
            worldLightManager.updateColumnSkylight(this, x, y, z, block, oldBlock);
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
     * Set whether to suppress light updates during bulk operations.
     * Should be true during terrain generation and world loading.
     */
    public void setSuppressLightUpdates(boolean suppress) {
        this.suppressLightUpdates = suppress;
    }
    
    /**
     * Set the world light manager for automatic light updates.
     * If not set, light updates will be skipped (useful for chunks that are being generated).
     */
    public void setWorldLightManager(mattmc.world.level.lighting.WorldLightManager worldLightManager) {
        this.worldLightManager = worldLightManager;
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
        int sectionIndex = y / SECTION_HEIGHT;
        int sectionY = y % SECTION_HEIGHT;
        return lightSections[sectionIndex].getSkyLight(x, sectionY, z);
    }
    
    /**
     * Set sky light level at chunk-local coordinates.
     * @param x 0-15
     * @param y 0-383 (world Y = y + MIN_Y)
     * @param z 0-15
     * @param level Light level (0-15)
     */
    public void setSkyLight(int x, int y, int z, int level) {
        if (x < 0 || x >= WIDTH || y < 0 || y >= HEIGHT || z < 0 || z >= DEPTH) {
            return;
        }
        int sectionIndex = y / SECTION_HEIGHT;
        int sectionY = y % SECTION_HEIGHT;
        lightSections[sectionIndex].setSkyLight(x, sectionY, z, level);
        // Mark chunk dirty to trigger mesh rebuild with new lighting
        setDirty(true);
        
        // Mark adjacent chunks dirty if light changed at chunk edge
        // This is needed for smooth lighting which samples a 3x3x3 grid
        markNeighborsDirtyIfOnEdge(x, z);
    }
    
    /**
     * Helper to mark neighboring chunks dirty if a light change happened at a chunk edge.
     * Smooth lighting samples from adjacent blocks, so if light changes at position (15, y, z),
     * the chunk at (chunkX+1, chunkZ) needs to rebuild its mesh to pick up the new light values.
     */
    private void markNeighborsDirtyIfOnEdge(int x, int z) {
        if (neighborDirtyCallback == null) {
            return;
        }
        
        // Check X edges (0 or 15)
        if (x == 0) {
            neighborDirtyCallback.markChunkDirty(chunkX - 1, chunkZ);
        } else if (x == WIDTH - 1) {
            neighborDirtyCallback.markChunkDirty(chunkX + 1, chunkZ);
        }
        
        // Check Z edges (0 or 15)
        if (z == 0) {
            neighborDirtyCallback.markChunkDirty(chunkX, chunkZ - 1);
        } else if (z == DEPTH - 1) {
            neighborDirtyCallback.markChunkDirty(chunkX, chunkZ + 1);
        }
    }
    
    /**
     * Get block light RED level at chunk-local coordinates.
     * @param x 0-15
     * @param y 0-383 (world Y = y + MIN_Y)
     * @param z 0-15
     * @return Block light red level (0-15)
     */
    public int getBlockLightR(int x, int y, int z) {
        if (x < 0 || x >= WIDTH || y < 0 || y >= HEIGHT || z < 0 || z >= DEPTH) {
            return 0;
        }
        int sectionIndex = y / SECTION_HEIGHT;
        int sectionY = y % SECTION_HEIGHT;
        return lightSections[sectionIndex].getBlockLightR(x, sectionY, z);
    }
    
    /**
     * Get block light GREEN level at chunk-local coordinates.
     * @param x 0-15
     * @param y 0-383 (world Y = y + MIN_Y)
     * @param z 0-15
     * @return Block light green level (0-15)
     */
    public int getBlockLightG(int x, int y, int z) {
        if (x < 0 || x >= WIDTH || y < 0 || y >= HEIGHT || z < 0 || z >= DEPTH) {
            return 0;
        }
        int sectionIndex = y / SECTION_HEIGHT;
        int sectionY = y % SECTION_HEIGHT;
        return lightSections[sectionIndex].getBlockLightG(x, sectionY, z);
    }
    
    /**
     * Get block light BLUE level at chunk-local coordinates.
     * @param x 0-15
     * @param y 0-383 (world Y = y + MIN_Y)
     * @param z 0-15
     * @return Block light blue level (0-15)
     */
    public int getBlockLightB(int x, int y, int z) {
        if (x < 0 || x >= WIDTH || y < 0 || y >= HEIGHT || z < 0 || z >= DEPTH) {
            return 0;
        }
        int sectionIndex = y / SECTION_HEIGHT;
        int sectionY = y % SECTION_HEIGHT;
        return lightSections[sectionIndex].getBlockLightB(x, sectionY, z);
    }
    
    /**
     * Get block light INTENSITY level at chunk-local coordinates.
     * @param x 0-15
     * @param y 0-383 (world Y = y + MIN_Y)
     * @param z 0-15
     * @return Block light intensity level (0-15)
     */
    public int getBlockLightI(int x, int y, int z) {
        if (x < 0 || x >= WIDTH || y < 0 || y >= HEIGHT || z < 0 || z >= DEPTH) {
            return 0;
        }
        int sectionIndex = y / SECTION_HEIGHT;
        int sectionY = y % SECTION_HEIGHT;
        return lightSections[sectionIndex].getBlockLightI(x, sectionY, z);
    }
    
    /**
     * Set block light RGBI levels at chunk-local coordinates.
     * @param x 0-15
     * @param y 0-383 (world Y = y + MIN_Y)
     * @param z 0-15
     * @param r Red level (0-15)
     * @param g Green level (0-15)
     * @param b Blue level (0-15)
     * @param i Intensity level (0-15)
     */
    public void setBlockLightRGBI(int x, int y, int z, int r, int g, int b, int i) {
        if (x < 0 || x >= WIDTH || y < 0 || y >= HEIGHT || z < 0 || z >= DEPTH) {
            return;
        }
        int sectionIndex = y / SECTION_HEIGHT;
        int sectionY = y % SECTION_HEIGHT;
        lightSections[sectionIndex].setBlockLightRGBI(x, sectionY, z, r, g, b, i);
        // Mark chunk dirty to trigger mesh rebuild with new lighting
        setDirty(true);
        
        // Mark adjacent chunks dirty if light changed at chunk edge
        markNeighborsDirtyIfOnEdge(x, z);
    }
    
    /**
     * Set block light RGB levels at chunk-local coordinates (intensity = max of RGB).
     * @param x 0-15
     * @param y 0-383 (world Y = y + MIN_Y)
     * @param z 0-15
     * @param r Red level (0-15)
     * @param g Green level (0-15)
     * @param b Blue level (0-15)
     */
    public void setBlockLightRGB(int x, int y, int z, int r, int g, int b) {
        if (x < 0 || x >= WIDTH || y < 0 || y >= HEIGHT || z < 0 || z >= DEPTH) {
            return;
        }
        int sectionIndex = y / SECTION_HEIGHT;
        int sectionY = y % SECTION_HEIGHT;
        lightSections[sectionIndex].setBlockLightRGB(x, sectionY, z, r, g, b);
        // Mark chunk dirty to trigger mesh rebuild with new lighting
        setDirty(true);
        
        // Mark adjacent chunks dirty if light changed at chunk edge
        markNeighborsDirtyIfOnEdge(x, z);
    }
    
    /**
     * Get the light storage for a specific section.
     * @param sectionIndex Section index (0-23)
     * @return LightStorage for the section, or null if out of bounds
     */
    public LightStorage getLightStorage(int sectionIndex) {
        if (sectionIndex < 0 || sectionIndex >= NUM_SECTIONS) {
            return null;
        }
        return lightSections[sectionIndex];
    }
    
    /**
     * Set the light storage for a specific section (used during deserialization).
     * @param sectionIndex Section index (0-23)
     * @param lightStorage Light storage to set
     */
    public void setLightStorage(int sectionIndex, LightStorage lightStorage) {
        if (sectionIndex >= 0 && sectionIndex < NUM_SECTIONS && lightStorage != null) {
            lightSections[sectionIndex] = lightStorage;
        }
    }
    
    /**
     * Get the heightmap for this chunk.
     */
    public ColumnHeightmap getHeightmap() {
        return heightmap;
    }
    
    /**
     * Recalculate block light for all emissive blocks in this chunk.
     * This is used after loading a chunk to ensure light values match current registry values.
     * If a block's light emission was changed in the registry, this will update the lighting.
     * Requires worldLightManager to be set.
     */
    public void recalculateBlockLight() {
        // Can only recalculate light if we have a world light manager
        if (worldLightManager == null) {
            return;
        }
        
        // Iterate through all blocks and update lighting for emissive blocks
        for (int x = 0; x < WIDTH; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                for (int z = 0; z < DEPTH; z++) {
                    Block block = blocks[x][y][z];
                    
                    // Skip air blocks
                    if (block.isAir()) {
                        continue;
                    }
                    
                    // Get current emission values from registry
                    int emissionR = block.getLightEmissionR();
                    int emissionG = block.getLightEmissionG();
                    int emissionB = block.getLightEmissionB();
                    
                    // Only process blocks that should emit light according to the registry
                    boolean hasEmission = (emissionR > 0 || emissionG > 0 || emissionB > 0);
                    if (!hasEmission) {
                        continue; // Skip non-emissive blocks
                    }
                    
                    // Get currently stored light at this position
                    int storedR = getBlockLightR(x, y, z);
                    int storedG = getBlockLightG(x, y, z);
                    int storedB = getBlockLightB(x, y, z);
                    
                    // Check if there's a mismatch between registry and stored values
                    boolean mismatch = (emissionR != storedR || emissionG != storedG || emissionB != storedB);
                    
                    if (mismatch) {
                        // Emission values changed - update lighting
                        // First remove the old light
                        worldLightManager.removeBlockLight(this, x, y, z);
                        
                        // Then add new light based on current registry values
                        worldLightManager.addBlockLightRGB(this, x, y, z, emissionR, emissionG, emissionB);
                    }
                }
            }
        }
    }
    
    public int chunkX() { return chunkX; }
    public int chunkZ() { return chunkZ; }
}
