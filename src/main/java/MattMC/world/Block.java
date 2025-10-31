package MattMC.world;

import MattMC.resources.ResourceManager;

/**
 * Represents a single block in the world.
 * Similar to Minecraft's Block class.
 * 
 * Each block has properties like solidity and texture path.
 * Blocks are registered in the Blocks class with unique identifiers.
 * Texture paths are loaded from blockstate and model JSON files.
 * If texture loading fails, a fallback magenta color (0xFF00FF) is used.
 */
public final class Block {
    // Fallback color used when texture is missing or fails to load
    private static final int FALLBACK_COLOR = 0xFF00FF; // Magenta
    
    private final boolean solid;
    private final String identifier;
    private String texturePath; // Lazily loaded from JSON
    
    /**
     * Create a new block with the given properties.
     * Texture path will be loaded from blockstate/model JSON files.
     * 
     * @param solid Whether the block is solid (has collision)
     */
    public Block(boolean solid) {
        this.solid = solid;
        this.identifier = null; // Will be set during registration
    }
    
    /**
     * Internal constructor used during registration to set the identifier.
     */
    Block(boolean solid, String identifier) {
        this.solid = solid;
        this.identifier = identifier;
    }
    
    /**
     * Get the fallback color used when texture is missing.
     * This is a magenta color (0xFF00FF) to make missing textures obvious.
     * 
     * @return The fallback color (RGB hex value)
     */
    public int getFallbackColor() {
        return FALLBACK_COLOR;
    }
    
    public boolean isSolid() {
        return solid;
    }
    
    /**
     * Get the texture path for this block.
     * Lazily loads from blockstate/model JSON files on first access.
     * 
     * @return The texture path, or null if no texture is available
     */
    public String getTexturePath() {
        if (texturePath == null && identifier != null) {
            // Extract block name from identifier (e.g., "mattmc:dirt" -> "dirt")
            String blockName = identifier.contains(":") ? identifier.substring(identifier.indexOf(':') + 1) : identifier;
            texturePath = ResourceManager.getBlockTexturePath(blockName);
        }
        return texturePath;
    }
    
    public boolean hasTexture() {
        return getTexturePath() != null;
    }
    
    public String getIdentifier() {
        return identifier;
    }
    
    public boolean isAir() {
        return this == Blocks.AIR;
    }
}

