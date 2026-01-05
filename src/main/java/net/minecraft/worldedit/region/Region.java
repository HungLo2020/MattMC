package net.minecraft.worldedit.region;

import net.minecraft.worldedit.math.BlockVector3;
import java.util.Iterator;

/**
 * Represents a physical region in the world.
 */
public interface Region extends Iterable<BlockVector3> {
    /**
     * Get the minimum point of the region.
     */
    BlockVector3 getMinimumPoint();
    
    /**
     * Get the maximum point of the region.
     */
    BlockVector3 getMaximumPoint();
    
    /**
     * Get the center point of the region.
     */
    BlockVector3 getCenter();
    
    /**
     * Get the volume of the region in blocks.
     */
    int getVolume();
    
    /**
     * Get the width of the region (X axis).
     */
    int getWidth();
    
    /**
     * Get the height of the region (Y axis).
     */
    int getHeight();
    
    /**
     * Get the length of the region (Z axis).
     */
    int getLength();
    
    /**
     * Check if a position is contained within this region.
     */
    boolean contains(BlockVector3 position);
    
    /**
     * Get an iterator over all block positions in this region.
     */
    @Override
    Iterator<BlockVector3> iterator();
}
