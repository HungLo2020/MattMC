package MattMC.world;

/**
 * Represents a single block in the world.
 * Similar to Minecraft's Block class.
 * 
 * Each block has properties like color, solidity, and texture path.
 * Blocks are registered in the Blocks class with unique identifiers.
 */
public final class Block {
    private final int color;
    private final boolean solid;
    private final String texturePath;
    private final String identifier;
    
    /**
     * Create a new block with the given properties.
     * 
     * @param color The color of the block (RGB hex value)
     * @param solid Whether the block is solid (has collision)
     * @param texturePath Path to the texture file, or null if no texture
     */
    public Block(int color, boolean solid, String texturePath) {
        this.color = color;
        this.solid = solid;
        this.texturePath = texturePath;
        this.identifier = null; // Will be set during registration
    }
    
    /**
     * Internal constructor used during registration to set the identifier.
     */
    Block(int color, boolean solid, String texturePath, String identifier) {
        this.color = color;
        this.solid = solid;
        this.texturePath = texturePath;
        this.identifier = identifier;
    }
    
    public int color() {
        return color;
    }
    
    public boolean isSolid() {
        return solid;
    }
    
    public String getTexturePath() {
        return texturePath;
    }
    
    public boolean hasTexture() {
        return texturePath != null;
    }
    
    public String getIdentifier() {
        return identifier;
    }
    
    public boolean isAir() {
        return this == Blocks.AIR;
    }
}
