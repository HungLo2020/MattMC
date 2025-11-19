package mattmc.world.level.block;

import mattmc.world.phys.shapes.VoxelShape;
import mattmc.world.level.block.state.BlockState;
import mattmc.world.level.block.state.properties.Direction;

/**
 * Represents a wall-mounted torch block.
 * Based on Minecraft's WallTorchBlock class.
 * 
 * Wall torches attach to the side of blocks and have a directional facing property.
 * They have different collision shapes based on which direction they face.
 */
public class WallTorchBlock extends TorchBlock {
    // Collision shapes for each direction
    // North: torch extends from block face at Z=1.0 towards Z=0.6875
    private static final VoxelShape NORTH_SHAPE = VoxelShape.box(0.34375, 0.1875, 0.6875, 0.65625, 0.8125, 1.0);
    // South: torch extends from block face at Z=0.0 towards Z=0.3125
    private static final VoxelShape SOUTH_SHAPE = VoxelShape.box(0.34375, 0.1875, 0.0, 0.65625, 0.8125, 0.3125);
    // West: torch extends from block face at X=1.0 towards X=0.6875
    private static final VoxelShape WEST_SHAPE = VoxelShape.box(0.6875, 0.1875, 0.34375, 1.0, 0.8125, 0.65625);
    // East: torch extends from block face at X=0.0 towards X=0.3125
    private static final VoxelShape EAST_SHAPE = VoxelShape.box(0.0, 0.1875, 0.34375, 0.3125, 0.8125, 0.65625);
    
    /**
     * Create a new wall torch block with full light emission parameters.
     * 
     * @param solid Whether the block is solid (has collision)
     * @param lightEmission Overall light emission level (0-15), used for intensity
     * @param lightEmissionR Red channel light emission (0-15)
     * @param lightEmissionG Green channel light emission (0-15)
     * @param lightEmissionB Blue channel light emission (0-15)
     */
    public WallTorchBlock(boolean solid, int lightEmission, int lightEmissionR, int lightEmissionG, int lightEmissionB) {
        super(solid, lightEmission, lightEmissionR, lightEmissionG, lightEmissionB);
    }
    
    /**
     * Internal constructor used during registration to set the identifier.
     */
    WallTorchBlock(boolean solid, int lightEmission, int lightEmissionR, int lightEmissionG, int lightEmissionB, String identifier) {
        super(solid, lightEmission, lightEmissionR, lightEmissionG, lightEmissionB, identifier);
    }
    
    /**
     * Get the collision shape for wall torch based on facing direction.
     */
    @Override
    public VoxelShape getCollisionShape() {
        // Default to north shape if no state available
        return NORTH_SHAPE;
    }
    
    /**
     * Get the collision shape for wall torch with blockstate.
     * This method can be called by the renderer with the blockstate.
     */
    public VoxelShape getCollisionShape(BlockState state) {
        if (state != null && state.hasProperty("facing")) {
            Direction facing = state.getDirection("facing");
            return switch (facing) {
                case NORTH -> NORTH_SHAPE;
                case SOUTH -> SOUTH_SHAPE;
                case WEST -> WEST_SHAPE;
                case EAST -> EAST_SHAPE;
            };
        }
        return NORTH_SHAPE;
    }
    
    /**
     * Wall torches use custom rendering with VBO-generated geometry.
     */
    @Override
    public boolean hasCustomRendering() {
        return true;
    }
    
    /**
     * Get the blockstate for wall torch placement based on hit face.
     * Wall torches should be placed on walls, not on the floor or ceiling.
     */
    @Override
    public BlockState getPlacementState(
            float playerX, float playerY, float playerZ,
            int blockX, int blockY, int blockZ, int hitFace,
            float hitX, float hitY, float hitZ) {
        
        // hitFace: 0=bottom, 1=top, 2=north, 3=south, 4=west, 5=east
        // Only allow placement on side faces (2, 3, 4, 5)
        if (hitFace < 2) {
            return null; // Cannot place on floor or ceiling
        }
        
        BlockState state = new BlockState();
        
        // Set facing based on which face was clicked
        Direction facing = switch (hitFace) {
            case 2 -> Direction.SOUTH; // Clicked north face, torch faces south
            case 3 -> Direction.NORTH; // Clicked south face, torch faces north
            case 4 -> Direction.EAST;  // Clicked west face, torch faces east
            case 5 -> Direction.WEST;  // Clicked east face, torch faces west
            default -> Direction.NORTH;
        };
        
        state.setValue("facing", facing);
        return state;
    }
}
