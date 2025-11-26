package mattmc.world.level.block;

import mattmc.world.level.block.state.BlockState;
import mattmc.world.level.block.state.properties.Axis;
import mattmc.world.level.block.state.properties.BlockStateProperties;

/**
 * Represents a rotated pillar block in the world.
 * Similar to MattMC's RotatedPillarBlock class.
 * 
 * Rotated pillar blocks can be placed along different axes (X, Y, Z).
 * Examples include logs, basalt pillars, and froglights.
 * The axis determines which direction the block is oriented.
 * 
 * @see Axis
 */
public class RotatedPillarBlock extends Block {
    
    /**
     * Create a new rotated pillar block.
     * 
     * @param solid Whether the block is solid (has collision)
     */
    public RotatedPillarBlock(boolean solid) {
        super(solid);
    }
    
    /**
     * Create a new rotated pillar block with white light emission (legacy).
     * Sets all RGB channels to the same value for white light.
     * 
     * @param solid Whether the block is solid (has collision)
     * @param lightEmission Light level emitted by this block (0-15)
     */
    public RotatedPillarBlock(boolean solid, int lightEmission) {
        super(solid, lightEmission, lightEmission, lightEmission, lightEmission);
    }
    
    /**
     * Create a new rotated pillar block with RGB light emission.
     * 
     * @param solid Whether the block is solid (has collision)
     * @param lightEmissionR Red channel light emission (0-15)
     * @param lightEmissionG Green channel light emission (0-15)
     * @param lightEmissionB Blue channel light emission (0-15)
     */
    public RotatedPillarBlock(boolean solid, int lightEmissionR, int lightEmissionG, int lightEmissionB) {
        super(solid, lightEmissionR, lightEmissionG, lightEmissionB);
    }
    
    /**
     * Create a new rotated pillar block with explicit emission level and RGB light values.
     * This is the most flexible constructor for colored lights.
     * 
     * @param solid Whether the block is solid (has collision)
     * @param lightEmission Overall light emission level (0-15), used for intensity
     * @param lightEmissionR Red channel light emission (0-15)
     * @param lightEmissionG Green channel light emission (0-15)
     * @param lightEmissionB Blue channel light emission (0-15)
     */
    public RotatedPillarBlock(boolean solid, int lightEmission, int lightEmissionR, int lightEmissionG, int lightEmissionB) {
        super(solid, lightEmission, lightEmissionR, lightEmissionG, lightEmissionB);
    }
    
    /**
     * Internal constructor used during registration to set the identifier.
     */
    RotatedPillarBlock(boolean solid, int lightEmission, int lightEmissionR, int lightEmissionG, int lightEmissionB, String identifier) {
        super(solid, lightEmission, lightEmissionR, lightEmissionG, lightEmissionB, identifier);
    }
    
    /**
     * Get the texture path for a specific face.
     * Rotated pillar blocks use "end" texture for top/bottom and "side" texture for horizontal faces.
     * 
     * @param face The face name (e.g., "top", "bottom", "side", "north", etc.)
     * @return The texture path, or null if no texture is available
     */
    @Override
    public String getTexturePath(String face) {
        java.util.Map<String, String> paths = getTexturePaths();
        if (paths == null || paths.isEmpty()) {
            return null;
        }
        
        // Map face names to texture variable names for pillar blocks
        // Pillar blocks use "end" for top/bottom and "side" for horizontal faces
        String textureKey;
        switch (face) {
            case "top":
            case "bottom":
                textureKey = "end";  // Top and bottom use "end" texture
                break;
            case "north":
            case "south":
            case "east":
            case "west":
            case "side":
                textureKey = "side";  // All horizontal faces use "side" texture
                break;
            default:
                textureKey = face;  // Use face name as-is for other cases
                break;
        }
        
        String path = paths.get(textureKey);
        if (path != null) {
            return path;
        }
        
        // Fall back to "all" texture (for simple cube_all models)
        return paths.get("all");
    }
    
    /**
     * Get the blockstate for pillar placement based on clicked face.
     * The axis is determined by the face that was clicked.
     */
    @Override
    public BlockState getPlacementState(
            float playerX, float playerY, float playerZ,
            int blockX, int blockY, int blockZ, int hitFace,
            float hitX, float hitY, float hitZ) {
        
        BlockState state = new BlockState();
        
        // Determine axis based on which face was clicked
        Axis axis;
        switch (hitFace) {
            case 0:  // Bottom face (Y-)
            case 1:  // Top face (Y+)
                axis = Axis.Y;  // Clicked top/bottom, orient vertically
                break;
            case 2:  // North face (Z-)
            case 3:  // South face (Z+)
                axis = Axis.Z;  // Clicked north/south, orient along Z axis
                break;
            case 4:  // West face (X-)
            case 5:  // East face (X+)
                axis = Axis.X;  // Clicked east/west, orient along X axis
                break;
            default:
                axis = Axis.Y;  // Default to vertical
                break;
        }
        
        state.setValue(BlockStateProperties.AXIS, axis);
        
        return state;
    }
}
