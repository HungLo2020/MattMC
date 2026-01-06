package net.minecraft.worldedit.extent;

import net.minecraft.worldedit.math.BlockVector3;
import net.minecraft.worldedit.region.Region;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Interface for objects that can get and set blocks.
 * This is the foundation of WorldEdit's block manipulation system.
 */
public interface Extent {
    /**
     * Get the block at a position.
     */
    BlockState getBlock(BlockVector3 position);
    
    /**
     * Set a block at a position.
     * @return true if the block was changed
     */
    boolean setBlock(BlockVector3 position, BlockState block);
    
    /**
     * Get the world this extent operates on.
     */
    ServerLevel getWorld();
}
