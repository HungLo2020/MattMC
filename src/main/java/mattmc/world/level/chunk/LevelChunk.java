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
    
    // Light level storage (0-15 for each block position)
    // blockLight stores light emitted by blocks (torches, glowstone, etc.)
    // skyLight stores light from the sky (affected by day/night cycle)
    private final byte[][][] blockLight;  // [x][y][z] each value 0-15
    private final byte[][][] skyLight;    // [x][y][z] each value 0-15
    
    // Dirty flag: marks if chunk needs to be re-rendered (for display list caching)
    private boolean dirty = true;
    
    public LevelChunk(int chunkX, int chunkZ) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.blocks = new Block[WIDTH][HEIGHT][DEPTH];
        this.blockStates = new HashMap<>();
        this.blockLight = new byte[WIDTH][HEIGHT][DEPTH];
        this.skyLight = new byte[WIDTH][HEIGHT][DEPTH];
        
        // Initialize all blocks to air using Arrays.fill for better performance
        for (int x = 0; x < WIDTH; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                java.util.Arrays.fill(blocks[x][y], Blocks.AIR);
                // Initialize sky light to full brightness (15) at the top
                // This will be properly calculated by lighting engine later
                java.util.Arrays.fill(skyLight[x][y], (byte) 0);
                // Block light starts at 0 (no light emission)
                java.util.Arrays.fill(blockLight[x][y], (byte) 0);
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
        
        // Update block light based on the block's light emission
        // This is a simple implementation - a full lighting engine would propagate light to neighbors
        if (block != null) {
            setBlockLight(x, y, z, block.getLightLevel());
        } else {
            setBlockLight(x, y, z, 0);
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
     * Get the block light level at chunk-local coordinates (0-15).
     * Block light is light emitted by blocks like torches.
     * 
     * @param x 0-15
     * @param y 0-383 (world Y = y + MIN_Y)
     * @param z 0-15
     * @return Block light level (0-15), or 0 if out of bounds
     */
    public int getBlockLight(int x, int y, int z) {
        if (x < 0 || x >= WIDTH || y < 0 || y >= HEIGHT || z < 0 || z >= DEPTH) {
            return 0;
        }
        return blockLight[x][y][z] & 0x0F; // Mask to ensure 0-15 range
    }
    
    /**
     * Set the block light level at chunk-local coordinates (0-15).
     * 
     * @param x 0-15
     * @param y 0-383 (world Y = y + MIN_Y)
     * @param z 0-15
     * @param level Light level (0-15, will be clamped)
     */
    public void setBlockLight(int x, int y, int z, int level) {
        if (x < 0 || x >= WIDTH || y < 0 || y >= HEIGHT || z < 0 || z >= DEPTH) {
            return;
        }
        blockLight[x][y][z] = (byte) Math.max(0, Math.min(15, level));
        this.dirty = true;
    }
    
    /**
     * Get the sky light level at chunk-local coordinates (0-15).
     * Sky light is light from the sky, affected by day/night cycle.
     * 
     * @param x 0-15
     * @param y 0-383 (world Y = y + MIN_Y)
     * @param z 0-15
     * @return Sky light level (0-15), or 0 if out of bounds
     */
    public int getSkyLight(int x, int y, int z) {
        if (x < 0 || x >= WIDTH || y < 0 || y >= HEIGHT || z < 0 || z >= DEPTH) {
            return 0;
        }
        return skyLight[x][y][z] & 0x0F; // Mask to ensure 0-15 range
    }
    
    /**
     * Set the sky light level at chunk-local coordinates (0-15).
     * 
     * @param x 0-15
     * @param y 0-383 (world Y = y + MIN_Y)
     * @param z 0-15
     * @param level Light level (0-15, will be clamped)
     */
    public void setSkyLight(int x, int y, int z, int level) {
        if (x < 0 || x >= WIDTH || y < 0 || y >= HEIGHT || z < 0 || z >= DEPTH) {
            return;
        }
        skyLight[x][y][z] = (byte) Math.max(0, Math.min(15, level));
        this.dirty = true;
    }
    
    /**
     * Get the combined light level at chunk-local coordinates (0-15).
     * Returns the maximum of block light and sky light.
     * 
     * @param x 0-15
     * @param y 0-383 (world Y = y + MIN_Y)
     * @param z 0-15
     * @return Combined light level (0-15)
     */
    public int getLightLevel(int x, int y, int z) {
        return Math.max(getBlockLight(x, y, z), getSkyLight(x, y, z));
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
    
    public int chunkX() { return chunkX; }
    public int chunkZ() { return chunkZ; }
}
