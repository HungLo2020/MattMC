package mattmc.world.item;

import mattmc.world.level.block.Block;

/**
 * Represents an item that can place two different blocks depending on placement context.
 * Based on MattMC's StandingAndWallBlockItem class (used for torches, signs, etc.).
 * 
 * This item places the standing block when placing on horizontal surfaces (floor/ceiling)
 * and the wall block when placing on vertical surfaces (walls).
 */
public class StandingAndWallBlockItem extends BlockItem {
    private final Block standingBlock;
    private final Block wallBlock;
    
    /**
     * Create a new standing and wall block item.
     * 
     * @param standingBlock The block to place on floors/ceilings
     * @param wallBlock The block to place on walls
     */
    public StandingAndWallBlockItem(Block standingBlock, Block wallBlock) {
        super(standingBlock, 64);
        this.standingBlock = standingBlock;
        this.wallBlock = wallBlock;
    }
    
    /**
     * Create a new standing and wall block item with custom stack size.
     * 
     * @param standingBlock The block to place on floors/ceilings
     * @param wallBlock The block to place on walls
     * @param maxStackSize Maximum number of items that can be in a single stack
     */
    public StandingAndWallBlockItem(Block standingBlock, Block wallBlock, int maxStackSize) {
        super(standingBlock, maxStackSize);
        this.standingBlock = standingBlock;
        this.wallBlock = wallBlock;
    }
    
    /**
     * Internal constructor used during registration to set the identifier.
     * 
     * @param standingBlock The block to place on floors/ceilings
     * @param wallBlock The block to place on walls
     * @param maxStackSize Maximum stack size
     * @param identifier The item identifier
     */
    StandingAndWallBlockItem(Block standingBlock, Block wallBlock, int maxStackSize, String identifier) {
        super(standingBlock, maxStackSize, identifier);
        this.standingBlock = standingBlock;
        this.wallBlock = wallBlock;
    }
    
    /**
     * Get the standing block (used on floors/ceilings).
     * 
     * @return The standing block
     */
    public Block getStandingBlock() {
        return standingBlock;
    }
    
    /**
     * Get the wall block (used on walls).
     * 
     * @return The wall block
     */
    public Block getWallBlock() {
        return wallBlock;
    }
    
    /**
     * Called when the player uses (right-clicks with) this item.
     * Places either the standing or wall block depending on which face was clicked.
     * 
     * @param blockInteraction The block interaction handler for the player
     * @return true if a block was placed successfully, false otherwise
     */
    @Override
    public boolean onUse(mattmc.world.entity.player.BlockInteraction blockInteraction) {
        // Get the targeted block and hit result
        mattmc.world.entity.player.BlockInteraction.BlockHitResult hit = blockInteraction.getTargetedBlock();
        
        if (hit == null) {
            return false;
        }
        
        // Determine which block to place based on hit face
        // hitFace: 0=bottom, 1=top, 2=north, 3=south, 4=west, 5=east
        Block blockToPlace;
        if (hit.hitFace == 0 || hit.hitFace == 1) {
            // Placing on floor (top face) or ceiling (bottom face) - use standing block
            blockToPlace = standingBlock;
        } else {
            // Placing on a wall (side face) - use wall block
            blockToPlace = wallBlock;
        }
        
        // Use the BlockInteraction to place the appropriate block
        blockInteraction.placeBlock(blockToPlace);
        return true;
    }
}
