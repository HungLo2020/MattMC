package mattmc.world.item;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Item class.
 * Validates item properties and behavior.
 */
public class ItemTest {
    
    @Test
    public void testDefaultConstructor() {
        Item item = new Item();
        assertEquals(64, item.getMaxStackSize(), "Default max stack size should be 64");
        assertTrue(item.isStackable(), "Item with stack size > 1 should be stackable");
    }
    
    @Test
    public void testCustomStackSize() {
        Item item = new Item(16);
        assertEquals(16, item.getMaxStackSize(), "Max stack size should match constructor argument");
        assertTrue(item.isStackable(), "Item with stack size > 1 should be stackable");
    }
    
    @Test
    public void testNonStackableItem() {
        Item item = new Item(1);
        assertEquals(1, item.getMaxStackSize(), "Max stack size should be 1");
        assertFalse(item.isStackable(), "Item with stack size of 1 should not be stackable");
    }
    
    @Test
    public void testFallbackColor() {
        Item item = new Item();
        assertEquals(0xFF00FF, item.getFallbackColor(), "Fallback color should be magenta (0xFF00FF)");
    }
    
    @Test
    public void testIdentifierNotSetBeforeRegistration() {
        Item item = new Item();
        assertNull(item.getIdentifier(), "Identifier should be null before registration");
    }
    
    @Test
    public void testHasTextureWithoutTexture() {
        Item item = new Item();
        // Since textures are lazily loaded and we haven't implemented ResourceManager.getItemTexturePaths yet,
        // this should return false
        assertFalse(item.hasTexture(), "Item without texture should return false for hasTexture()");
    }
    
    @Test
    public void testGetTexturePathWithoutTexture() {
        Item item = new Item();
        assertNull(item.getTexturePath(), "Item without texture should return null for getTexturePath()");
    }
}
