package mattmc.world.item;

import mattmc.world.level.block.Blocks;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Items registry class.
 * Validates item registration and lookup behavior.
 */
public class ItemsTest {
    
    @Test
    public void testVanillaItemsAreRegistered() {
        // Test that vanilla items are properly registered
        assertNotNull(Items.DIRT, "DIRT should be registered");
        assertNotNull(Items.STONE, "STONE should be registered");
        assertNotNull(Items.COBBLESTONE, "COBBLESTONE should be registered");
        assertNotNull(Items.ACACIA_PLANKS, "ACACIA_PLANKS should be registered");
        assertNotNull(Items.DARK_OAK_PLANKS, "DARK_OAK_PLANKS should be registered");
        
        // Test block items
        assertNotNull(Items.STONE, "STONE should be registered");
        assertNotNull(Items.DIRT, "DIRT should be registered");
        assertNotNull(Items.GRASS_BLOCK, "GRASS_BLOCK should be registered");
        
        // Test planks
        assertNotNull(Items.OAK_PLANKS, "OAK_PLANKS should be registered");
        assertNotNull(Items.SPRUCE_PLANKS, "SPRUCE_PLANKS should be registered");
        assertNotNull(Items.BAMBOO_PLANKS, "BAMBOO_PLANKS should be registered");
        assertNotNull(Items.MOSSY_COBBLESTONE, "MOSSY_COBBLESTONE should be registered");
    }
    
    @Test
    public void testItemIdentifiers() {
        assertEquals("mattmc:dirt", Items.DIRT.getIdentifier(), "DIRT identifier should be 'mattmc:dirt'");
        assertEquals("mattmc:stone", Items.STONE.getIdentifier(), "STONE identifier should be 'mattmc:stone'");
        assertEquals("mattmc:oak_planks", Items.OAK_PLANKS.getIdentifier(), 
            "OAK_PLANKS identifier should be 'mattmc:oak_planks'");
    }
    
    @Test
    public void testItemStackSizes() {
        // Materials should stack to 64
        assertEquals(64, Items.DIRT.getMaxStackSize(), "DIRT should stack to 64");
        assertEquals(64, Items.STONE.getMaxStackSize(), "STONE should stack to 64");
        assertEquals(64, Items.COBBLESTONE.getMaxStackSize(), "COBBLESTONE should stack to 64");
        
        // All block items stack to 64
        assertEquals(64, Items.OAK_PLANKS.getMaxStackSize(), "OAK_PLANKS should have max stack size of 64");
        assertEquals(64, Items.SPRUCE_PLANKS.getMaxStackSize(), "SPRUCE_PLANKS should have max stack size of 64");
        assertEquals(64, Items.BAMBOO_PLANKS.getMaxStackSize(), "BAMBOO_PLANKS should have max stack size of 64");
        assertEquals(64, Items.MOSSY_COBBLESTONE.getMaxStackSize(), "MOSSY_COBBLESTONE should have max stack size of 64");
    }
    
    @Test
    public void testGetItemByIdentifier() {
        Item dirt = Items.getItem("mattmc:dirt");
        assertNotNull(dirt, "getItem should return DIRT for 'mattmc:dirt'");
        assertSame(Items.DIRT, dirt, "getItem should return the same instance as Items.DIRT");
        
        Item stone = Items.getItem("mattmc:stone");
        assertNotNull(stone, "getItem should return STONE for 'mattmc:stone'");
        assertSame(Items.STONE, stone, "getItem should return the same instance as Items.STONE");
    }
    
    @Test
    public void testGetItemWithInvalidIdentifier() {
        Item item = Items.getItem("mattmc:nonexistent");
        assertNull(item, "getItem should return null for non-existent identifier");
    }
    
    @Test
    public void testIsRegistered() {
        assertTrue(Items.isRegistered("mattmc:dirt"), "DIRT should be registered");
        assertTrue(Items.isRegistered("mattmc:stone"), "STONE should be registered");
        assertFalse(Items.isRegistered("mattmc:nonexistent"), "Non-existent item should not be registered");
    }
    
    @Test
    public void testRegisterCustomItem() {
        // Register a custom item with a custom namespace
        Item customItem = Items.registerItem("mymod:custom_item", new Item(32));
        
        assertNotNull(customItem, "Registered item should not be null");
        assertEquals("mymod:custom_item", customItem.getIdentifier(), 
            "Custom item should have the correct identifier");
        assertEquals(32, customItem.getMaxStackSize(), 
            "Custom item should have the specified max stack size");
        
        // Verify it can be retrieved
        Item retrieved = Items.getItem("mymod:custom_item");
        assertSame(customItem, retrieved, "Retrieved item should be the same instance");
        
        assertTrue(Items.isRegistered("mymod:custom_item"), 
            "Custom item should be registered");
    }
    
    @Test
    public void testRegisterDuplicateItemThrowsException() {
        // Try to register an item with an identifier that's already registered
        assertThrows(IllegalArgumentException.class, () -> {
            Items.registerItem("mattmc:dirt", new Item(64));
        }, "Registering duplicate item should throw IllegalArgumentException");
    }
    
    @Test
    public void testRegisterItemWithNullIdentifierThrowsException() {
        assertThrows(NullPointerException.class, () -> {
            Items.registerItem(null, new Item(64));
        }, "Registering item with null identifier should throw NullPointerException");
    }
    
    @Test
    public void testRegisterItemWithNullItemThrowsException() {
        assertThrows(NullPointerException.class, () -> {
            Items.registerItem("test:item", null);
        }, "Registering null item should throw NullPointerException");
    }
    
    @Test
    public void testGetItemWithNullIdentifierThrowsException() {
        assertThrows(NullPointerException.class, () -> {
            Items.getItem(null);
        }, "Getting item with null identifier should throw NullPointerException");
    }
    
    @Test
    public void testIsRegisteredWithNullIdentifierThrowsException() {
        assertThrows(NullPointerException.class, () -> {
            Items.isRegistered(null);
        }, "Checking registration with null identifier should throw NullPointerException");
    }
    
    @Test
    public void testGetRegisteredIdentifiers() {
        var identifiers = Items.getRegisteredIdentifiers();
        
        assertNotNull(identifiers, "Registered identifiers set should not be null");
        assertFalse(identifiers.isEmpty(), "Registered identifiers set should not be empty");
        
        // Check that vanilla items are in the set
        assertTrue(identifiers.contains("mattmc:dirt"), "Identifiers should contain 'mattmc:dirt'");
        assertTrue(identifiers.contains("mattmc:stone"), "Identifiers should contain 'mattmc:stone'");
        assertTrue(identifiers.contains("mattmc:oak_planks"), 
            "Identifiers should contain 'mattmc:oak_planks'");
        
        // Verify the set is unmodifiable
        assertThrows(UnsupportedOperationException.class, () -> {
            identifiers.add("test:item");
        }, "Registered identifiers set should be unmodifiable");
    }
    
    @Test
    public void testItemsAreStackable() {
        assertTrue(Items.DIRT.isStackable(), "DIRT should be stackable");
        assertTrue(Items.STONE.isStackable(), "STONE should be stackable");
        assertTrue(Items.OAK_PLANKS.isStackable(), "OAK_PLANKS should be stackable");
        assertTrue(Items.CHERRY_PLANKS.isStackable(), "CHERRY_PLANKS should be stackable");
    }
    
    @Test
    public void testBlockItemsAreRegistered() {
        // Test that block items are BlockItem instances
        assertTrue(Items.STONE instanceof BlockItem, "STONE should be a BlockItem");
        assertTrue(Items.DIRT instanceof BlockItem, "DIRT should be a BlockItem");
        assertTrue(Items.GRASS_BLOCK instanceof BlockItem, "GRASS_BLOCK should be a BlockItem");
        
        // Test that block items have the correct blocks
        assertSame(Blocks.STONE, ((BlockItem) Items.STONE).getBlock(), "STONE item should have STONE block");
        assertSame(Blocks.DIRT, ((BlockItem) Items.DIRT).getBlock(), "DIRT item should have DIRT block");
        assertSame(Blocks.GRASS_BLOCK, ((BlockItem) Items.GRASS_BLOCK).getBlock(), "GRASS_BLOCK item should have GRASS_BLOCK block");
    }
    
    @Test
    public void testBlockItemsAreStackable() {
        assertTrue(Items.STONE.isStackable(), "STONE should be stackable");
        assertTrue(Items.DIRT.isStackable(), "DIRT should be stackable");
        assertTrue(Items.GRASS_BLOCK.isStackable(), "GRASS_BLOCK should be stackable");
        
        assertEquals(64, Items.STONE.getMaxStackSize(), "STONE should stack to 64");
        assertEquals(64, Items.DIRT.getMaxStackSize(), "DIRT should stack to 64");
        assertEquals(64, Items.GRASS_BLOCK.getMaxStackSize(), "GRASS_BLOCK should stack to 64");
    }
}
