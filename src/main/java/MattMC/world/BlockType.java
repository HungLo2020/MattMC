package MattMC.world;

/**
 * Types of blocks available in the world.
 * Similar to Minecraft's block types.
 */
public enum BlockType {
    AIR(0x000000, false, null),      // Transparent
    GRASS(0x7CB342, true, null),     // Green grass block (no texture yet)
    DIRT(0x8B5A3C, true, "assets/textures/block/dirt.png"),      // Brown dirt with texture
    STONE(0x808080, true, "assets/textures/block/stone.png");     // Gray stone with texture
    
    private final int color;
    private final boolean solid;
    private final String texturePath;
    
    BlockType(int color, boolean solid, String texturePath) {
        this.color = color;
        this.solid = solid;
        this.texturePath = texturePath;
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
}
