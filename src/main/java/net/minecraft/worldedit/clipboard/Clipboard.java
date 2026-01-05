package net.minecraft.worldedit.clipboard;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.worldedit.math.BlockVector3;
import net.minecraft.worldedit.region.Region;
import java.util.HashMap;
import java.util.Map;

/**
 * Stores a copy of blocks for copy/paste operations.
 */
public class Clipboard {
    private final Map<BlockVector3, BlockState> blocks;
    private final BlockVector3 origin;
    private final BlockVector3 minimumPoint;
    private final BlockVector3 maximumPoint;
    private final BlockVector3 dimensions;
    private final BlockVector3 offset;
    
    public Clipboard(Region region, BlockVector3 origin) {
        this.blocks = new HashMap<>();
        this.origin = origin;
        
        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();
        
        this.minimumPoint = min;
        this.maximumPoint = max;
        
        this.dimensions = BlockVector3.at(
            max.getX() - min.getX() + 1,
            max.getY() - min.getY() + 1,
            max.getZ() - min.getZ() + 1
        );
        
        this.offset = origin.subtract(min);
    }
    
    /**
     * Store a block in the clipboard.
     */
    public void setBlock(BlockVector3 position, BlockState block) {
        blocks.put(position, block);
    }
    
    /**
     * Get a block from the clipboard.
     */
    public BlockState getBlock(BlockVector3 position) {
        return blocks.get(position);
    }
    
    /**
     * Get all stored blocks.
     */
    public Map<BlockVector3, BlockState> getBlocks() {
        return new HashMap<>(blocks);
    }
    
    /**
     * Get the origin point of the clipboard.
     */
    public BlockVector3 getOrigin() {
        return origin;
    }
    
    /**
     * Get the minimum point.
     */
    public BlockVector3 getMinimumPoint() {
        return minimumPoint;
    }
    
    /**
     * Get the maximum point.
     */
    public BlockVector3 getMaximumPoint() {
        return maximumPoint;
    }
    
    /**
     * Get the dimensions of the clipboard.
     */
    public BlockVector3 getDimensions() {
        return dimensions;
    }
    
    /**
     * Get the offset from minimum point to origin.
     */
    public BlockVector3 getOffset() {
        return offset;
    }
    
    /**
     * Get the number of blocks in the clipboard.
     */
    public int getVolume() {
        return blocks.size();
    }
    
    /**
     * Check if the clipboard is empty.
     */
    public boolean isEmpty() {
        return blocks.isEmpty();
    }
}
