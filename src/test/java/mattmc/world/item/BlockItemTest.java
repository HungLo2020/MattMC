package mattmc.world.item;

import mattmc.world.level.block.Block;
import mattmc.registries.Blocks;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the BlockItem class.
 * Validates block item properties and behavior.
 */
public class BlockItemTest {
    
    @Test
    public void testBlockItemWithDefaultConstructor() {
        BlockItem item = new BlockItem(Blocks.STONE);
        assertEquals(64, item.getMaxStackSize(), "Default max stack size should be 64");
        assertTrue(item.isStackable(), "BlockItem with stack size > 1 should be stackable");
        assertSame(Blocks.STONE, item.getBlock(), "BlockItem should return the correct block");
    }
    
    @Test
    public void testBlockItemWithCustomStackSize() {
        BlockItem item = new BlockItem(Blocks.DIRT, 16);
        assertEquals(16, item.getMaxStackSize(), "Max stack size should match constructor argument");
        assertTrue(item.isStackable(), "BlockItem with stack size > 1 should be stackable");
        assertSame(Blocks.DIRT, item.getBlock(), "BlockItem should return the correct block");
    }
    
    @Test
    public void testBlockItemGetBlock() {
        BlockItem stoneItem = new BlockItem(Blocks.STONE);
        BlockItem dirtItem = new BlockItem(Blocks.DIRT);
        BlockItem grassItem = new BlockItem(Blocks.GRASS_BLOCK);
        
        assertSame(Blocks.STONE, stoneItem.getBlock(), "Stone item should return STONE block");
        assertSame(Blocks.DIRT, dirtItem.getBlock(), "Dirt item should return DIRT block");
        assertSame(Blocks.GRASS_BLOCK, grassItem.getBlock(), "Grass item should return GRASS_BLOCK block");
    }
    
    @Test
    public void testBlockItemIsInstanceOfItem() {
        BlockItem item = new BlockItem(Blocks.STONE);
        assertTrue(item instanceof Item, "BlockItem should be an instance of Item");
    }
}
