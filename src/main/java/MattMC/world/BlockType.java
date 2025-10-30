package MattMC.world;

/**
 * Types of blocks available in the world.
 * Similar to Minecraft's block types.
 */
public enum BlockType {
    AIR(0x000000, false),      // Transparent
    GRASS(0x7CB342, true),     // Green grass block
    DIRT(0x8B5A3C, true),      // Brown dirt
    STONE(0x808080, true);     // Gray stone
    
    private final int color;
    private final boolean solid;
    
    BlockType(int color, boolean solid) {
        this.color = color;
        this.solid = solid;
    }
    
    public int color() {
        return color;
    }
    
    public boolean isSolid() {
        return solid;
    }
}
