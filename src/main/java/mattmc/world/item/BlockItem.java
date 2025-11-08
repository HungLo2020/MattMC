package mattmc.world.item;

import mattmc.world.level.block.Block;

/**
 * Represents an item that can place a block.
 * Similar to Minecraft's BlockItem class.
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
    BlockItem(Block block, int maxStackSize, String identifier) {
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
}
