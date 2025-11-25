package mattmc.world.phys;

/**
 * Axis-Aligned Bounding Box for collision detection.
 * Similar to MattMC's AABB class.
 */
public class AABB {
    public final double minX;
    public final double minY;
    public final double minZ;
    public final double maxX;
    public final double maxY;
    public final double maxZ;
    
    public AABB(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
    }
    
    /**
     * Create an AABB from two corner points.
     */
    public static AABB fromPoints(double x1, double y1, double z1, double x2, double y2, double z2) {
        return new AABB(
            Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2),
            Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2)
        );
    }
    
    /**
     * Offset this AABB by the given amount.
     */
    public AABB move(double dx, double dy, double dz) {
        return new AABB(minX + dx, minY + dy, minZ + dz, maxX + dx, maxY + dy, maxZ + dz);
    }
    
    /**
     * Check if this AABB intersects with another AABB.
     */
    public boolean intersects(AABB other) {
        return this.minX < other.maxX && this.maxX > other.minX &&
               this.minY < other.maxY && this.maxY > other.minY &&
               this.minZ < other.maxZ && this.maxZ > other.minZ;
    }
    
    /**
     * Check if this AABB intersects with another AABB (with epsilon for floating point errors).
     */
    public boolean intersects(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        return this.minX < maxX && this.maxX > minX &&
               this.minY < maxY && this.maxY > minY &&
               this.minZ < maxZ && this.maxZ > minZ;
    }
}
