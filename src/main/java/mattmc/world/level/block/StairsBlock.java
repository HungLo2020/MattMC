package mattmc.world.level.block;

/**
 * Represents a stairs block in the world.
 * Similar to Minecraft's StairsBlock class.
 * 
 * Stairs blocks are decorative blocks that form a staircase.
 * They have complex collision boxes and multiple variants based on:
 * - facing: The direction the stairs face (north, south, east, west)
 * - half: Whether the stairs are in the top or bottom half (top, bottom)
 * - shape: The shape of the stairs (straight, inner_left, inner_right, outer_left, outer_right)
 * 
 * Textures are inherited from the base block material (e.g., birch_planks for birch_stairs).
 */
public class StairsBlock extends Block {
    
    /**
     * Create a new stairs block.
     * Stairs blocks are always solid (have collision).
     */
    public StairsBlock() {
        super(true);
    }
    
    /**
     * Internal constructor used during registration to set the identifier.
     */
    StairsBlock(String identifier) {
        super(true, identifier);
    }
}
