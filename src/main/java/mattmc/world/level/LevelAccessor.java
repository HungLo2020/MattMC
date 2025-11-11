package mattmc.world.level;

import mattmc.world.level.chunk.Region;

import mattmc.world.level.block.Block;
import mattmc.world.level.block.state.BlockState;

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
     * Get a blockstate at the specified coordinates.
     * Returns null if no blockstate exists.
     */
    default BlockState getBlockState(int x, int y, int z) {
        return null;  // Default implementation for compatibility
    }
    
    /**
     * Set a block at the specified coordinates.
     */
    void setBlock(int x, int y, int z, Block block);
    
    /**
     * Set a block with blockstate at the specified coordinates.
     */
    default void setBlock(int x, int y, int z, Block block, BlockState state) {
        setBlock(x, y, z, block);  // Default implementation for compatibility
    }
}

