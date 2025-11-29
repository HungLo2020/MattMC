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
    protected SlabBlock(String identifier) {
        super(false, 0, 0, 0, 0, identifier);  // Slabs are transparent/non-solid for rendering, no light emission
    }
    
    @Override
    public Block withIdentifier(String identifier) {
        return new SlabBlock(identifier);
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
     * Check if this slab can be replaced by another slab to form a double slab.
     * A slab can be replaced if:
     * - The existing slab is not already a double slab
     * - The placement would result in the opposite half being filled
     * 
     * @param existingState The current state of the existing slab
     * @param hitFace The face being clicked (0=bottom, 1=top, 2-5=sides)
     * @param hitY The exact Y position where the click occurred
     * @return true if the slab can be replaced to form a double slab
     */
    public boolean canBeReplacedBy(mattmc.world.level.block.state.BlockState existingState, int hitFace, float hitY) {
        if (existingState == null) {
            return false;
        }
        
        Object typeObj = existingState.getValue("type");
        if (typeObj == null) {
            return false;
        }
        
        SlabType existingType;
        if (typeObj instanceof SlabType) {
            existingType = (SlabType) typeObj;
        } else if (typeObj instanceof String) {
            try {
                existingType = SlabType.valueOf(((String) typeObj).toUpperCase());
            } catch (IllegalArgumentException e) {
                return false;
            }
        } else {
            return false;
        }
        
        // Can't replace a double slab
        if (existingType == SlabType.DOUBLE) {
            return false;
        }
        
        // Determine where the player is trying to place based on hit position
        float relativeY = hitY - (float) Math.floor(hitY);
        boolean clickedTopHalf = relativeY > 0.5f;
        
        // Check if the click would place in the opposite half
        if (hitFace == 0) {
            // Clicked bottom face - would place as top half
            // Can replace if existing is BOTTOM
            return existingType == SlabType.BOTTOM;
        } else if (hitFace == 1) {
            // Clicked top face - would place as bottom half  
            // Can replace if existing is TOP
            return existingType == SlabType.TOP;
        } else {
            // Clicked side face - check based on Y position
            if (clickedTopHalf) {
                // Clicking top half of side - would place as top half
                // Can replace if existing is BOTTOM
                return existingType == SlabType.BOTTOM;
            } else {
                // Clicking bottom half of side - would place as bottom half
                // Can replace if existing is TOP
                return existingType == SlabType.TOP;
            }
        }
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
    
    /**
     * Get the blockstate for a double slab.
     * Used when combining two half slabs.
     * 
     * @return A blockstate with type set to DOUBLE
     */
    public mattmc.world.level.block.state.BlockState getDoubleSlabState() {
        mattmc.world.level.block.state.BlockState state = new mattmc.world.level.block.state.BlockState();
        state.setValue("type", SlabType.DOUBLE);
        return state;
    }
}
