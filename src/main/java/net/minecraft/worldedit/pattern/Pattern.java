package net.minecraft.worldedit.pattern;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.worldedit.math.BlockVector3;

/**
 * Interface for block patterns.
 * Patterns determine what block to place at each position.
 */
public interface Pattern {
    
    /**
     * Get the block state to place at the given position.
     */
    BlockState apply(BlockVector3 position);
    
    /**
     * Get the block state to place at the given position.
     */
    default BlockState apply(BlockPos position) {
        return apply(BlockVector3.from(position));
    }
}
