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
     * Stairs blocks are transparent (non-solid) so adjacent blocks render correctly,
     * but still have custom collision shapes.
     */
    public StairsBlock() {
        super(false);  // Stairs are transparent/non-solid for rendering
    }
    
    /**
     * Internal constructor used during registration to set the identifier.
     */
    StairsBlock(String identifier) {
        super(false, identifier);  // Stairs are transparent/non-solid for rendering
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
            // Clicked bottom face - always place as top half (upside down)
            half = Half.TOP;
        } else if (hitFace == 1) {
            // Clicked top face - always place as bottom half (right-side up)
            half = Half.BOTTOM;
        } else {
            // Clicked side face - determine based on where the player is looking (Y position)
            // Use the player's eye height for more accurate placement
            float eyeY = playerY + 1.62f; // Player eye height is at feet + 1.62 blocks
            float blockCenterY = blockY + 0.5f;
            
            // If looking at top half of block, place as top half (upside down)
            // If looking at bottom half, place as bottom half (right-side up)
            half = eyeY > blockCenterY ? Half.TOP : Half.BOTTOM;
        }
        
        state.setValue("facing", facing);
        state.setValue("half", half);
        
        return state;
    }
}
