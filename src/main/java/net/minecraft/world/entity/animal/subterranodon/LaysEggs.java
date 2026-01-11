package net.minecraft.world.entity.animal.subterranodon;

import net.minecraft.world.level.block.state.BlockState;

/**
 * Interface for entities that can lay eggs.
 * Based on AlexsCaves LaysEggs interface.
 */
public interface LaysEggs {
    
    /**
     * Returns the block state for the egg block this entity lays.
     */
    BlockState createEggBlockState();
    
    /**
     * Returns whether this entity currently has an egg ready to lay.
     */
    boolean hasEgg();
    
    /**
     * Sets whether this entity has an egg ready to lay.
     */
    void setHasEgg(boolean hasEgg);
}
