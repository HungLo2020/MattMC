package mattmc.world.phys.shapes;

import mattmc.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a shape made up of one or more AABBs.
 * Similar to Minecraft's VoxelShape class.
 */
public class VoxelShape {
    private final List<AABB> boxes;
    
    private VoxelShape(List<AABB> boxes) {
        this.boxes = boxes;
    }
    
    /**
     * Create an empty voxel shape.
     */
    public static VoxelShape empty() {
        return new VoxelShape(new ArrayList<>());
    }
    
    /**
     * Create a voxel shape from a single box.
     */
    public static VoxelShape box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        // Pre-allocate with exact size (1 box)
        List<AABB> boxes = new ArrayList<>(1);
        boxes.add(new AABB(minX, minY, minZ, maxX, maxY, maxZ));
        return new VoxelShape(boxes);
    }
    
    /**
     * Create a full block voxel shape (1x1x1).
     */
    public static VoxelShape block() {
        return box(0, 0, 0, 1, 1, 1);
    }
    
    /**
     * Combine two voxel shapes into one.
     */
    public static VoxelShape or(VoxelShape shape1, VoxelShape shape2) {
        // Pre-allocate with exact combined size
        List<AABB> combined = new ArrayList<>(shape1.boxes.size() + shape2.boxes.size());
        combined.addAll(shape1.boxes);
        combined.addAll(shape2.boxes);
        return new VoxelShape(combined);
    }
    
    /**
     * Get all AABBs in this shape, offset by block position.
     */
    public List<AABB> toAabbs(int blockX, int blockY, int blockZ) {
        // Pre-allocate with exact size (number of boxes)
        List<AABB> result = new ArrayList<>(boxes.size());
        for (AABB box : boxes) {
            result.add(box.move(blockX, blockY, blockZ));
        }
        return result;
    }
    
    /**
     * Check if this shape is empty.
     */
    public boolean isEmpty() {
        return boxes.isEmpty();
    }
    
    /**
     * Get the collision boxes for rendering debug outlines.
     */
    public List<AABB> getBoxes() {
        return new ArrayList<>(boxes);
    }
}
