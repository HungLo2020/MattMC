package MattMC.world;

import MattMC.resources.ResourceManager;

/**
 * Represents a single block in the world.
 * Similar to Minecraft's Block class.
 * 
 * Each block has properties like color, solidity, and texture path.
 * Blocks are registered in the Blocks class with unique identifiers.
 * Texture paths are loaded from blockstate and model JSON files.
 */
public final class Block {
    private final int color;
    private final boolean solid;
    private final String identifier;
    private String texturePath; // Lazily loaded from JSON
    
    /**
     * Create a new block with the given properties.
     * Texture path will be loaded from blockstate/model JSON files.
     * 
     * @param color The color of the block (RGB hex value)
     * @param solid Whether the block is solid (has collision)
     */
    public Block(int color, boolean solid) {
        this.color = color;
        this.solid = solid;
        this.identifier = null; // Will be set during registration
    }
    
    /**
     * Internal constructor used during registration to set the identifier.
     */
    Block(int color, boolean solid, String identifier) {
        this.color = color;
        this.solid = solid;
        this.identifier = identifier;
    }
    
    public int color() {
        return color;
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

