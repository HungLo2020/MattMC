package mattmc.world.level;

import mattmc.world.level.chunk.Region;

import mattmc.world.level.block.Block;

/**
 * Interface for world/region access.
 * Provides basic block getting and setting operations.
 * Used by Region and Level classes.
 */
public interface LevelAccessor {
    /**
     * Get a block at the specified coordinates.
     */
    Block getBlock(int x, int y, int z);
    
    /**
     * Set a block at the specified coordinates.
     */
    void setBlock(int x, int y, int z, Block block);
}
