package net.minecraft.worldedit.region;

import net.minecraft.worldedit.math.BlockVector3;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * A cuboid (box-shaped) region.
 */
public class CuboidRegion implements Region {
    private BlockVector3 pos1;
    private BlockVector3 pos2;
    
    public CuboidRegion(BlockVector3 pos1, BlockVector3 pos2) {
        this.pos1 = pos1;
        this.pos2 = pos2;
    }
    
    public CuboidRegion(BlockVector3 position) {
        this.pos1 = position;
        this.pos2 = position;
    }
    
    /**
     * Set the first position.
     */
    public void setPos1(BlockVector3 pos1) {
        this.pos1 = pos1;
    }
    
    /**
     * Set the second position.
     */
    public void setPos2(BlockVector3 pos2) {
        this.pos2 = pos2;
    }
    
    /**
     * Get the first position.
     */
    public BlockVector3 getPos1() {
        return pos1;
    }
    
    /**
     * Get the second position.
     */
    public BlockVector3 getPos2() {
        return pos2;
    }
    
    @Override
    public BlockVector3 getMinimumPoint() {
        return pos1.getMinimum(pos2);
    }
    
    @Override
    public BlockVector3 getMaximumPoint() {
        return pos1.getMaximum(pos2);
    }
    
    @Override
    public BlockVector3 getCenter() {
        BlockVector3 min = getMinimumPoint();
        BlockVector3 max = getMaximumPoint();
        return BlockVector3.at(
            (min.getX() + max.getX()) / 2,
            (min.getY() + max.getY()) / 2,
            (min.getZ() + max.getZ()) / 2
        );
    }
    
    @Override
    public int getVolume() {
        return getWidth() * getHeight() * getLength();
    }
    
    @Override
    public int getWidth() {
        BlockVector3 min = getMinimumPoint();
        BlockVector3 max = getMaximumPoint();
        return max.getX() - min.getX() + 1;
    }
    
    @Override
    public int getHeight() {
        BlockVector3 min = getMinimumPoint();
        BlockVector3 max = getMaximumPoint();
        return max.getY() - min.getY() + 1;
    }
    
    @Override
    public int getLength() {
        BlockVector3 min = getMinimumPoint();
        BlockVector3 max = getMaximumPoint();
        return max.getZ() - min.getZ() + 1;
    }
    
    @Override
    public boolean contains(BlockVector3 position) {
        BlockVector3 min = getMinimumPoint();
        BlockVector3 max = getMaximumPoint();
        return position.getX() >= min.getX() && position.getX() <= max.getX()
            && position.getY() >= min.getY() && position.getY() <= max.getY()
            && position.getZ() >= min.getZ() && position.getZ() <= max.getZ();
    }
    
    @Override
    public Iterator<BlockVector3> iterator() {
        return new CuboidIterator();
    }
    
    /**
     * Iterator over all positions in the cuboid.
     */
    private class CuboidIterator implements Iterator<BlockVector3> {
        private final BlockVector3 min = getMinimumPoint();
        private final BlockVector3 max = getMaximumPoint();
        private int x = min.getX();
        private int y = min.getY();
        private int z = min.getZ();
        
        @Override
        public boolean hasNext() {
            return x <= max.getX() && y <= max.getY() && z <= max.getZ();
        }
        
        @Override
        public BlockVector3 next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            
            BlockVector3 current = BlockVector3.at(x, y, z);
            
            // Advance to next position
            x++;
            if (x > max.getX()) {
                x = min.getX();
                z++;
                if (z > max.getZ()) {
                    z = min.getZ();
                    y++;
                }
            }
            
            return current;
        }
    }
}
