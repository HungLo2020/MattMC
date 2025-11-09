package mattmc.world.level.block;

import mattmc.world.phys.shapes.VoxelShape;

/**
 * Represents a stairs block in the world.
 * Similar to Minecraft's StairsBlock class.
 * 
 * Stairs blocks are decorative blocks that form a staircase.
 * They have complex collision boxes and multiple variants based on:
 * - facing: The direction the stairs face (north, south, east, west)
 * - half: Whether the stairs are in the top or bottom half (top, bottom)
 * - shape: The shape of the stairs (straight, inner_left, inner_right, outer_left, outer_right)
 * 
 * Textures are inherited from the base block material (e.g., birch_planks for birch_stairs).
 * 
 * Note: This simplified implementation uses a fixed north-facing bottom straight stair shape.
 * A full implementation would require block state storage in chunks.
 */
public class StairsBlock extends Block {
    
    // Collision shape for bottom stairs (north-facing) - full block with south-east quarter removed
    // This is more accurate to Minecraft - a single shape with a corner cut out
    private static final VoxelShape BOTTOM_SHAPE = VoxelShape.or(
        VoxelShape.box(0.0, 0.0, 0.0, 1.0, 0.5, 1.0),      // Bottom slab (full width/depth, half height)
        VoxelShape.box(0.0, 0.5, 0.0, 1.0, 1.0, 0.5)       // Top step (north half only, upper half)
    );
    
    /**
     * Create a new stairs block.
     * Stairs blocks are always solid (have collision).
     */
    public StairsBlock() {
        super(true);
    }
    
    /**
     * Internal constructor used during registration to set the identifier.
     */
    StairsBlock(String identifier) {
        super(true, identifier);
    }
    
    /**
     * Get the collision shape for stairs.
     * Returns a shape representing a full block with the south-east quarter removed.
     */
    @Override
    public VoxelShape getCollisionShape() {
        return BOTTOM_SHAPE;
    }
    
    /**
     * Stairs use custom rendering with VBO-generated geometry.
     */
    @Override
    public boolean hasCustomRendering() {
        return true;
    }
}
