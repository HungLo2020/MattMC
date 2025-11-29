package mattmc.world.level.block;

/**
 * Cherry leaves block that spawns falling cherry blossom particles.
 * 
 * <p>Cherry leaves have a 10% chance per tick to spawn a falling petal
 * particle below them if there's air below.
 * 
 * <p>Mirrors Minecraft's CherryLeavesBlock.
 */
public class CherryLeavesBlock extends LeavesBlock {
    
    /**
     * Create cherry leaves without tinting.
     */
    public CherryLeavesBlock() {
        super(-1);
    }
    
    /**
     * Internal constructor used during registration.
     */
    CherryLeavesBlock(String identifier) {
        super(-1, identifier);
    }
    
    /**
     * Cherry leaves spawn particles and should indicate this.
     */
    @Override
    public boolean hasRandomTick() {
        return true;
    }
}
