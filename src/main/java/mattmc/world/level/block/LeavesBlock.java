package mattmc.world.level.block;

import mattmc.util.MathUtils;

/**
 * Represents a leaves block.
 * Similar to Minecraft's LeavesBlock class.
 * 
 * Leaves blocks are transparent, non-solid blocks with tinting support.
 * Some leaves (like oak, birch) have biome-based or constant color tinting,
 * while others (like cherry, azalea) do not have tinting.
 * 
 * In Minecraft, leaves also have decay mechanics and persistent property,
 * but those are not implemented yet.
 */
public class LeavesBlock extends Block {
    
    /** The tint color for this leaves block, or -1 if no tinting */
    private final int tintColor;
    
    /**
     * Create a leaves block without tinting.
     * Used for leaves like cherry, azalea, etc.
     */
    public LeavesBlock() {
        this(-1);
    }
    
    /**
     * Create a leaves block with a specific tint color.
     * 
     * @param tintColor The tint color in ARGB format (negative value from MC item JSON),
     *                  or -1 for no tinting
     */
    public LeavesBlock(int tintColor) {
        // Leaves are not solid (they don't block movement or light fully)
        // but for rendering purposes we treat them as partially solid
        super(true, 0, 0, 0, 0);
        this.tintColor = tintColor;
    }
    
    /**
     * Internal constructor used during registration to set the identifier.
     */
    LeavesBlock(int tintColor, String identifier) {
        super(true, 0, 0, 0, 0, identifier);
        this.tintColor = tintColor;
    }
    
    /**
     * Check if this leaves block has tinting.
     * 
     * @return true if tinting should be applied
     */
    public boolean hasTinting() {
        return tintColor != -1;
    }
    
    /**
     * Get the tint color for this leaves block.
     * Returns 0xFFFFFF (white) if no tinting.
     * 
     * @return The tint color as a positive RGB value
     */
    public int getTintColor() {
        if (tintColor == -1) {
            return 0xFFFFFF;
        }
        // Convert negative ARGB from MC format to positive RGB
        // MC uses negative integers like -12012264 which is 0xFF491408 in ARGB
        // We need to extract just the RGB portion
        return tintColor & 0xFFFFFF;
    }
    
    /**
     * Get the raw tint color value (may be negative from MC format).
     * 
     * @return The raw tint color value, or -1 if no tinting
     */
    public int getRawTintColor() {
        return tintColor;
    }
    
    @Override
    public boolean isOpaque() {
        // Leaves are not fully opaque - they allow some light through
        return false;
    }
    
    @Override
    public boolean canOcclude() {
        // Leaves are transparent and should not occlude neighboring block faces.
        // This means when you place leaves on top of grass_block, the grass_block's
        // top face should still be rendered (visible through the leaves).
        // This matches Minecraft's behavior where leaves have noOcclusion() set.
        return false;
    }
    
    @Override
    public int getOpacity() {
        // Leaves block some light but not all (value of 1 means slight light reduction)
        return 1;
    }
}
