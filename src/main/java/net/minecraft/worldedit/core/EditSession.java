package net.minecraft.worldedit.core;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.worldedit.extent.Extent;
import net.minecraft.worldedit.history.ChangeSet;
import net.minecraft.worldedit.history.ArrayListHistory;
import net.minecraft.worldedit.mask.Mask;
import net.minecraft.worldedit.math.BlockVector3;
import net.minecraft.worldedit.pattern.Pattern;
import net.minecraft.worldedit.region.Region;

/**
 * Handles block changes in the world with change tracking for undo/redo.
 * This is the main class for all WorldEdit block manipulation operations.
 */
public class EditSession implements Extent {
    private final ServerLevel world;
    private final ChangeSet changeSet;
    private final int maxBlocks;
    private int blocksChanged = 0;
    private boolean fastMode = false;
    private Mask mask;
    private boolean recordingChanges = true;
    
    public EditSession(ServerLevel world, int maxBlocks) {
        this.world = world;
        this.maxBlocks = maxBlocks;
        this.changeSet = new ArrayListHistory();
    }
    
    public EditSession(ServerLevel world) {
        this(world, -1); // Unlimited by default
    }
    
    @Override
    public ServerLevel getWorld() {
        return world;
    }
    
    @Override
    public BlockState getBlock(BlockVector3 position) {
        return world.getBlockState(position.toBlockPos());
    }
    
    @Override
    public boolean setBlock(BlockVector3 position, BlockState block) {
        if (mask != null && !mask.test(this, position)) {
            return false;
        }
        if (recordingChanges && maxBlocks >= 0 && blocksChanged >= maxBlocks) {
            return false;
        }
        
        BlockPos pos = position.toBlockPos();
        BlockState oldBlock = world.getBlockState(pos);
        
        if (oldBlock.equals(block)) {
            return false; // No change needed
        }
        
        // Record the change for undo/redo
        if (recordingChanges) {
            changeSet.add(position, oldBlock, block);
        }
        
        // Set the block in the world
        int flags = fastMode ? 2 : 3; // Fast mode skips updates
        world.setBlock(pos, block, flags);
        
        if (recordingChanges) {
            blocksChanged++;
        }
        return true;
    }
    
    /**
     * Set all blocks in a region to a specific block type.
     */
    public int setBlocks(Region region, BlockState block) {
        int count = 0;
        for (BlockVector3 pos : region) {
            if (setBlock(pos, block)) {
                count++;
            }
            if (maxBlocks >= 0 && count >= maxBlocks) {
                break;
            }
        }
        return count;
    }

    /**
     * Set all blocks in a region to blocks selected by a pattern.
     */
    public int setBlocks(Region region, Pattern pattern) {
        int count = 0;
        for (BlockVector3 pos : region) {
            BlockState block = pattern.apply(pos);
            if (block != null && setBlock(pos, block)) {
                count++;
            }
            if (maxBlocks >= 0 && count >= maxBlocks) {
                break;
            }
        }
        return count;
    }
    
    /**
     * Replace blocks in a region.
     */
    public int replaceBlocks(Region region, BlockState from, BlockState to) {
        int count = 0;
        for (BlockVector3 pos : region) {
            if (getBlock(pos).equals(from)) {
                if (setBlock(pos, to)) {
                    count++;
                }
            }
            if (maxBlocks >= 0 && count >= maxBlocks) {
                break;
            }
        }
        return count;
    }

    /**
     * Replace blocks in a region with blocks selected by a pattern.
     */
    public int replaceBlocks(Region region, BlockState from, Pattern to) {
        int count = 0;
        for (BlockVector3 pos : region) {
            if (getBlock(pos).equals(from)) {
                BlockState replacement = to.apply(pos);
                if (replacement != null && setBlock(pos, replacement)) {
                    count++;
                }
            }
            if (maxBlocks >= 0 && count >= maxBlocks) {
                break;
            }
        }
        return count;
    }

    /**
     * Replace blocks matching a mask with blocks selected by a pattern.
     */
    public int replaceBlocks(Region region, Mask from, Pattern to) {
        int count = 0;
        for (BlockVector3 pos : region) {
            if (from.test(this, pos)) {
                BlockState replacement = to.apply(pos);
                if (replacement != null && setBlock(pos, replacement)) {
                    count++;
                }
            }
            if (maxBlocks >= 0 && count >= maxBlocks) {
                break;
            }
        }
        return count;
    }
    
    /**
     * Get the number of blocks changed.
     */
    public int getBlockChangeCount() {
        return blocksChanged;
    }
    
    /**
     * Get the change set for this session.
     */
    public ChangeSet getChangeSet() {
        return changeSet;
    }
    
    /**
     * Set fast mode (disables block updates for performance).
     */
    public void setFastMode(boolean fastMode) {
        this.fastMode = fastMode;
    }
    
    /**
     * Check if fast mode is enabled.
     */
    public boolean isFastMode() {
        return fastMode;
    }

    /**
     * Get the mask applied to block changes in this edit session.
     */
    public Mask getMask() {
        return mask;
    }

    /**
     * Set the mask applied to block changes in this edit session.
     */
    public void setMask(Mask mask) {
        this.mask = mask;
    }
    
    /**
     * Undo all changes made in this session.
     */
    public void undo() {
        replayHistory(() -> changeSet.undo(this));
    }
    
    /**
     * Redo all changes made in this session.
     */
    public void redo() {
        replayHistory(() -> changeSet.redo(this));
    }

    private void replayHistory(Runnable action) {
        Mask previousMask = mask;
        boolean previousRecordingChanges = recordingChanges;
        mask = null;
        recordingChanges = false;
        try {
            action.run();
        } finally {
            mask = previousMask;
            recordingChanges = previousRecordingChanges;
        }
    }
}
