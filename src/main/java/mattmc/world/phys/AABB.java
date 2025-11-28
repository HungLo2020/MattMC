package mattmc.world.phys;

/**
 * Axis-Aligned Bounding Box for collision detection.
 * Similar to MattMC's AABB class.
 * 
 * Note: This class assumes callers provide properly ordered coordinates
 * (min <= max). Use fromPoints() if you need automatic ordering.
 */
public class AABB {
    public final double minX;
    public final double minY;
    public final double minZ;
    public final double maxX;
    public final double maxY;
    public final double maxZ;
    
    /**
     * Create an AABB with the specified bounds.
     * 
     * Note: This constructor assumes minX <= maxX, minY <= maxY, minZ <= maxZ.
     * For safety, consider using fromPoints() which handles any order.
     * 
     * @param minX Minimum X coordinate
     * @param minY Minimum Y coordinate
     * @param minZ Minimum Z coordinate
     * @param maxX Maximum X coordinate
     * @param maxY Maximum Y coordinate
     * @param maxZ Maximum Z coordinate
     */
    public AABB(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
    }
    
    /**
     * Create an AABB from two corner points, automatically handling any order.
     * This is the safest way to create an AABB when you're not sure about coordinate order.
     * 
     * @param x1 First X coordinate
     * @param y1 First Y coordinate
     * @param z1 First Z coordinate
     * @param x2 Second X coordinate
     * @param y2 Second Y coordinate
     * @param z2 Second Z coordinate
     * @return A new AABB with properly ordered min/max values
     */
    public static AABB fromPoints(double x1, double y1, double z1, double x2, double y2, double z2) {
        return new AABB(
            Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2),
            Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2)
        );
    }
    
    /**
     * Offset this AABB by the given amount.
     * 
     * @param dx X offset
     * @param dy Y offset
     * @param dz Z offset
     * @return A new AABB offset by the given amounts
     */
    public AABB move(double dx, double dy, double dz) {
        return new AABB(minX + dx, minY + dy, minZ + dz, maxX + dx, maxY + dy, maxZ + dz);
    }
    
    /**
     * Check if this AABB intersects with another AABB.
     * 
     * @param other The other AABB to check against
     * @return true if the AABBs intersect
     */
    public boolean intersects(AABB other) {
        return this.minX < other.maxX && this.maxX > other.minX &&
               this.minY < other.maxY && this.maxY > other.minY &&
               this.minZ < other.maxZ && this.maxZ > other.minZ;
    }
    
    /**
     * Check if this AABB intersects with a box defined by min/max coordinates.
     * 
     * @param minX Minimum X of the box
     * @param minY Minimum Y of the box
     * @param minZ Minimum Z of the box
     * @param maxX Maximum X of the box
     * @param maxY Maximum Y of the box
     * @param maxZ Maximum Z of the box
     * @return true if the AABBs intersect
     */
    public boolean intersects(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        return this.minX < maxX && this.maxX > minX &&
               this.minY < maxY && this.maxY > minY &&
               this.minZ < maxZ && this.maxZ > minZ;
    }
}
