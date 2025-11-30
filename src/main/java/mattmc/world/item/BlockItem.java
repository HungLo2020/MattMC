package mattmc.world.item;

import mattmc.world.level.block.Block;

/**
 * Represents an item that can place a block.
 * Similar to MattMC's BlockItem class.
 * 
 * BlockItems are used for placeable blocks like dirt, stone, grass, etc.
 * When used (right-clicked), they place the associated block in the world.
 */
public class BlockItem extends Item {
    private final Block block;
    
    /**
     * Create a new block item with default stack size (64).
     * 
     * @param block The block that this item places
     */
    public BlockItem(Block block) {
        this(block, 64);
    }
    
    /**
     * Create a new block item with a custom stack size.
     * 
     * @param block The block that this item places
     * @param maxStackSize Maximum number of items that can be in a single stack
     */
    public BlockItem(Block block, int maxStackSize) {
        super(maxStackSize);
        this.block = block;
    }
    
    /**
     * Internal constructor used during registration to set the identifier.
     * 
     * @param block The block that this item places
     * @param maxStackSize Maximum stack size
     * @param identifier The item identifier
     */
    public BlockItem(Block block, int maxStackSize, String identifier) {
        super(maxStackSize, identifier);
        this.block = block;
    }
    
    /**
     * Get the block that this item places.
     * 
     * @return The block associated with this item
     */
    public Block getBlock() {
        return block;
    }
    
    /**
     * Called when the player uses (right-clicks with) this block item.
     * Places the associated block where the player is looking.
     * 
     * @param blockInteraction The block interaction handler for the player
     * @return true if the block was placed successfully, false otherwise
     */
    @Override
    public boolean onUse(mattmc.world.entity.player.BlockInteraction blockInteraction) {
        // Use the BlockInteraction to place the block where the player is looking
        blockInteraction.placeBlock(this.block);
        // Return true to indicate we attempted to use the item
        // Note: Currently returns true even if placement failed due to no valid target
        // This prevents item consumption on failed placements
        return true;
    }
}
