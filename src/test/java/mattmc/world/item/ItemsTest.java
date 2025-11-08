package mattmc.world.item;

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
        assertNotNull(Items.STICK, "STICK should be registered");
        assertNotNull(Items.DIAMOND, "DIAMOND should be registered");
        assertNotNull(Items.COAL, "COAL should be registered");
        assertNotNull(Items.IRON_INGOT, "IRON_INGOT should be registered");
        assertNotNull(Items.GOLD_INGOT, "GOLD_INGOT should be registered");
        
        // Test tools
        assertNotNull(Items.WOODEN_PICKAXE, "WOODEN_PICKAXE should be registered");
        assertNotNull(Items.DIAMOND_PICKAXE, "DIAMOND_PICKAXE should be registered");
        assertNotNull(Items.IRON_AXE, "IRON_AXE should be registered");
        assertNotNull(Items.STONE_SHOVEL, "STONE_SHOVEL should be registered");
    }
    
    @Test
    public void testItemIdentifiers() {
        assertEquals("mattmc:stick", Items.STICK.getIdentifier(), "STICK identifier should be 'mattmc:stick'");
        assertEquals("mattmc:diamond", Items.DIAMOND.getIdentifier(), "DIAMOND identifier should be 'mattmc:diamond'");
        assertEquals("mattmc:wooden_pickaxe", Items.WOODEN_PICKAXE.getIdentifier(), 
            "WOODEN_PICKAXE identifier should be 'mattmc:wooden_pickaxe'");
    }
    
    @Test
    public void testItemStackSizes() {
        // Materials should stack to 64
        assertEquals(64, Items.STICK.getMaxStackSize(), "STICK should stack to 64");
        assertEquals(64, Items.DIAMOND.getMaxStackSize(), "DIAMOND should stack to 64");
        assertEquals(64, Items.COAL.getMaxStackSize(), "COAL should stack to 64");
        
        // Tools should not stack (max 1)
        assertEquals(1, Items.WOODEN_PICKAXE.getMaxStackSize(), "WOODEN_PICKAXE should have max stack size of 1");
        assertEquals(1, Items.DIAMOND_PICKAXE.getMaxStackSize(), "DIAMOND_PICKAXE should have max stack size of 1");
        assertEquals(1, Items.IRON_AXE.getMaxStackSize(), "IRON_AXE should have max stack size of 1");
        assertEquals(1, Items.STONE_SHOVEL.getMaxStackSize(), "STONE_SHOVEL should have max stack size of 1");
    }
    
    @Test
    public void testGetItemByIdentifier() {
        Item stick = Items.getItem("mattmc:stick");
        assertNotNull(stick, "getItem should return STICK for 'mattmc:stick'");
        assertSame(Items.STICK, stick, "getItem should return the same instance as Items.STICK");
        
        Item diamond = Items.getItem("mattmc:diamond");
        assertNotNull(diamond, "getItem should return DIAMOND for 'mattmc:diamond'");
        assertSame(Items.DIAMOND, diamond, "getItem should return the same instance as Items.DIAMOND");
    }
    
    @Test
    public void testGetItemWithInvalidIdentifier() {
        Item item = Items.getItem("mattmc:nonexistent");
        assertNull(item, "getItem should return null for non-existent identifier");
    }
    
    @Test
    public void testIsRegistered() {
        assertTrue(Items.isRegistered("mattmc:stick"), "STICK should be registered");
        assertTrue(Items.isRegistered("mattmc:diamond"), "DIAMOND should be registered");
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
            Items.registerItem("mattmc:stick", new Item(64));
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
        assertTrue(identifiers.contains("mattmc:stick"), "Identifiers should contain 'mattmc:stick'");
        assertTrue(identifiers.contains("mattmc:diamond"), "Identifiers should contain 'mattmc:diamond'");
        assertTrue(identifiers.contains("mattmc:wooden_pickaxe"), 
            "Identifiers should contain 'mattmc:wooden_pickaxe'");
        
        // Verify the set is unmodifiable
        assertThrows(UnsupportedOperationException.class, () -> {
            identifiers.add("test:item");
        }, "Registered identifiers set should be unmodifiable");
    }
    
    @Test
    public void testItemsAreStackable() {
        assertTrue(Items.STICK.isStackable(), "STICK should be stackable");
        assertTrue(Items.DIAMOND.isStackable(), "DIAMOND should be stackable");
        assertFalse(Items.WOODEN_PICKAXE.isStackable(), "WOODEN_PICKAXE should not be stackable");
        assertFalse(Items.DIAMOND_AXE.isStackable(), "DIAMOND_AXE should not be stackable");
    }
}
