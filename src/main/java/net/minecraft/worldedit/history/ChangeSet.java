package net.minecraft.worldedit.history;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.worldedit.extent.Extent;
import net.minecraft.worldedit.math.BlockVector3;

/**
 * Tracks changes made during an edit session for undo/redo.
 */
public interface ChangeSet {
    /**
     * Add a block change to the history.
     */
    void add(BlockVector3 position, BlockState before, BlockState after);
    
    /**
     * Undo all changes.
     */
    void undo(Extent extent);
    
    /**
     * Redo all changes.
     */
    void redo(Extent extent);
    
    /**
     * Get the number of changes.
     */
    int size();
    
    /**
     * Clear all changes.
     */
    void clear();
}
