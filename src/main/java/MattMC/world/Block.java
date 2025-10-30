package MattMC.world;

/**
 * Represents a single block in the world.
 * Similar to Minecraft's block system.
 */
public final class Block {
    private final BlockType type;
    
    public Block(BlockType type) {
        this.type = type;
    }
    
    public BlockType type() {
        return type;
    }
    
    public boolean isAir() {
        return type == BlockType.AIR;
    }
}
