package net.minecraft.worldedit.history;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.worldedit.extent.Extent;
import net.minecraft.worldedit.math.BlockVector3;
import java.util.ArrayList;
import java.util.List;

/**
 * Simple array-based change set implementation.
 */
public class ArrayListHistory implements ChangeSet {
    private final List<Change> changes = new ArrayList<>();
    
    private static class Change {
        final BlockVector3 position;
        final BlockState before;
        final BlockState after;
        
        Change(BlockVector3 position, BlockState before, BlockState after) {
            this.position = position;
            this.before = before;
            this.after = after;
        }
    }
    
    @Override
    public void add(BlockVector3 position, BlockState before, BlockState after) {
        changes.add(new Change(position, before, after));
    }
    
    @Override
    public void undo(Extent extent) {
        // Undo in reverse order
        for (int i = changes.size() - 1; i >= 0; i--) {
            Change change = changes.get(i);
            extent.setBlock(change.position, change.before);
        }
    }
    
    @Override
    public void redo(Extent extent) {
        // Redo in forward order
        for (Change change : changes) {
            extent.setBlock(change.position, change.after);
        }
    }
    
    @Override
    public int size() {
        return changes.size();
    }
    
    @Override
    public void clear() {
        changes.clear();
    }
}
