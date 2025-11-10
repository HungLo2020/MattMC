package mattmc.world.region;

/**
 * Manages region selection for WorldEdit-style commands.
 * Tracks two positions (pos1 and pos2) that define a cuboid region.
 */
public class RegionSelector {
    private int[] pos1 = null; // [x, y, z]
    private int[] pos2 = null; // [x, y, z]
    
    /**
     * Set the first position of the region.
     */
    public void setPos1(int x, int y, int z) {
        this.pos1 = new int[]{x, y, z};
    }
    
    /**
     * Set the second position of the region.
     */
    public void setPos2(int x, int y, int z) {
        this.pos2 = new int[]{x, y, z};
    }
    
    /**
     * Get the first position, or null if not set.
     */
    public int[] getPos1() {
        return pos1;
    }
    
    /**
     * Get the second position, or null if not set.
     */
    public int[] getPos2() {
        return pos2;
    }
    
    /**
     * Check if both positions are set.
     */
    public boolean hasSelection() {
        return pos1 != null && pos2 != null;
    }
    
    /**
     * Get the bounds of the selected region.
     * @return RegionBounds, or null if selection is incomplete
     */
    public RegionBounds getRegionBounds() {
        if (!hasSelection()) {
            return null;
        }
        
        int minX = Math.min(pos1[0], pos2[0]);
        int maxX = Math.max(pos1[0], pos2[0]);
        int minY = Math.min(pos1[1], pos2[1]);
        int maxY = Math.max(pos1[1], pos2[1]);
        int minZ = Math.min(pos1[2], pos2[2]);
        int maxZ = Math.max(pos1[2], pos2[2]);
        
        return new RegionBounds(minX, maxX, minY, maxY, minZ, maxZ);
    }
    
    /**
     * Get the total number of blocks in the selected region.
     * @return Block count, or 0 if selection is incomplete
     */
    public long getRegionSize() {
        RegionBounds bounds = getRegionBounds();
        if (bounds == null) {
            return 0;
        }
        
        long sizeX = (long)(bounds.maxX - bounds.minX + 1);
        long sizeY = (long)(bounds.maxY - bounds.minY + 1);
        long sizeZ = (long)(bounds.maxZ - bounds.minZ + 1);
        return sizeX * sizeY * sizeZ;
    }
    
    /**
     * Clear the selection.
     */
    public void clearSelection() {
        pos1 = null;
        pos2 = null;
    }
    
    /**
     * Represents the bounding box of a selected region.
     */
    public static class RegionBounds {
        public final int minX, maxX, minY, maxY, minZ, maxZ;
        
        public RegionBounds(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
            this.minX = minX;
            this.maxX = maxX;
            this.minY = minY;
            this.maxY = maxY;
            this.minZ = minZ;
            this.maxZ = maxZ;
        }
    }
}
