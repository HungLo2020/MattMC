package net.minecraft.worldedit.mask;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.worldedit.extent.Extent;
import net.minecraft.worldedit.math.BlockVector3;

/**
 * Interface for block masks.
 * Masks determine which blocks can be affected by operations.
 */
public interface Mask {
    
    /**
     * Test if the block at the given position matches this mask.
     */
    boolean test(Extent extent, BlockVector3 position);
    
    /**
     * Test if the given block state matches this mask.
     */
    default boolean test(BlockState state) {
        return true;
    }
}
