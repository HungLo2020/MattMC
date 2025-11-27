package mattmc.world.level.block;

import mattmc.world.phys.shapes.VoxelShape;
import mattmc.world.level.block.state.properties.SlabType;

/**
 * Represents a slab block in the world.
 * Similar to MattMC's SlabBlock class.
 * 
 * Slab blocks are half-height blocks that can be placed in the top or bottom
 * half of a block space. They have three types:
 * - bottom: Slab is in the bottom half (y: 0-0.5)
 * - top: Slab is in the top half (y: 0.5-1.0)
 * - double: Two slabs combined into a full block
 * 
 * Textures are inherited from the base block material (e.g., birch_planks for birch_slab).
 */
public class SlabBlock extends Block {
    
    // Collision shape for bottom slab (bottom half of block)
    private static final VoxelShape BOTTOM_SHAPE = VoxelShape.box(0.0, 0.0, 0.0, 1.0, 0.5, 1.0);
    
    // Collision shape for top slab (top half of block)
    private static final VoxelShape TOP_SHAPE = VoxelShape.box(0.0, 0.5, 0.0, 1.0, 1.0, 1.0);
    
    /**
     * Create a new slab block.
     * Slab blocks are transparent (non-solid) so adjacent blocks render correctly,
     * but still have custom collision shapes.
     */
    public SlabBlock() {
        super(false);  // Slabs are transparent/non-solid for rendering
    }
    
    /**
     * Internal constructor used during registration to set the identifier.
     */
    SlabBlock(String identifier) {
        super(false, 0, 0, 0, 0, identifier);  // Slabs are transparent/non-solid for rendering, no light emission
    }
    
    /**
     * Get the collision shape for slabs.
     * Returns a shape representing a half-height block.
     * TODO: Check blockstate for type and return appropriate shape
     */
    @Override
    public VoxelShape getCollisionShape() {
        return BOTTOM_SHAPE;
    }
    
    /**
     * Slabs use custom rendering with VBO-generated geometry.
     */
    @Override
    public boolean hasCustomRendering() {
        return true;
    }
    
    /**
     * Get the blockstate for slab placement based on player position and clicked face.
     */
    @Override
    public mattmc.world.level.block.state.BlockState getPlacementState(
            float playerX, float playerY, float playerZ,
            int blockX, int blockY, int blockZ, int hitFace,
            float hitX, float hitY, float hitZ) {
        
        mattmc.world.level.block.state.BlockState state = new mattmc.world.level.block.state.BlockState();
        
        // Determine type based on which face was clicked and where on the face was hit
        SlabType type;
        if (hitFace == 0) {
            // Clicked bottom face - always place as top half
            type = SlabType.TOP;
        } else if (hitFace == 1) {
            // Clicked top face - always place as bottom half
            type = SlabType.BOTTOM;
        } else {
            // Clicked side face - determine based on where we hit the face (Y position)
            float relativeY = hitY - (float)Math.floor(hitY);
            
            // If we hit the top half of the face (relativeY > 0.5), place as top half
            // If we hit the bottom half of the face (relativeY <= 0.5), place as bottom half
            type = relativeY > 0.5f ? SlabType.TOP : SlabType.BOTTOM;
        }
        
        state.setValue("type", type);
        
        return state;
    }
}
