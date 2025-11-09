package mattmc.world.level.block;

import mattmc.world.phys.shapes.VoxelShape;
import mattmc.world.level.block.state.properties.Direction;
import mattmc.world.level.block.state.properties.Half;

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
     * TODO: Rotate based on blockstate facing/half
     */
    @Override
    public VoxelShape getCollisionShape() {
        return BOTTOM_SHAPE;
    }
    
    /**
     * Stairs are not opaque - they don't fully cover adjacent faces.
     * This prevents adjacent blocks from having their faces culled.
     */
    @Override
    public boolean isOpaque() {
        return false;
    }
    
    /**
     * Stairs use custom rendering with VBO-generated geometry.
     */
    @Override
    public boolean hasCustomRendering() {
        return true;
    }
    
    /**
     * Get the blockstate for stairs placement based on player position and clicked face.
     */
    @Override
    public mattmc.world.level.block.state.BlockState getPlacementState(
            float playerX, float playerY, float playerZ,
            int blockX, int blockY, int blockZ, int hitFace) {
        
        mattmc.world.level.block.state.BlockState state = new mattmc.world.level.block.state.BlockState();
        
        // Determine facing based on player's horizontal direction
        float dx = playerX - (blockX + 0.5f);
        float dz = playerZ - (blockZ + 0.5f);
        
        Direction facing;
        if (Math.abs(dx) > Math.abs(dz)) {
            // Player is more to the east or west
            facing = dx > 0 ? Direction.WEST : Direction.EAST;
        } else {
            // Player is more to the north or south
            facing = dz > 0 ? Direction.NORTH : Direction.SOUTH;
        }
        
        // Determine half based on which face was clicked and player's Y position
        Half half;
        if (hitFace == 0) {
            // Clicked bottom face - always place as top half
            half = Half.TOP;
        } else if (hitFace == 1) {
            // Clicked top face - always place as bottom half
            half = Half.BOTTOM;
        } else {
            // Clicked side face - determine based on where on the block was clicked
            float relativeY = playerY - blockY;
            half = relativeY > 0.5 ? Half.TOP : Half.BOTTOM;
        }
        
        state.setValue("facing", facing);
        state.setValue("half", half);
        
        return state;
    }
}
