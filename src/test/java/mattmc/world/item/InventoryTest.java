package mattmc.world.item;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Inventory class.
 */
public class InventoryTest {
    
    private Inventory inventory;
    
    @BeforeEach
    public void setUp() {
        inventory = new Inventory();
    }
    
    @Test
    public void testInventorySize() {
        assertEquals(36, inventory.getSize());
    }
    
    @Test
    public void testInventoryStartsEmpty() {
        for (int i = 0; i < inventory.getSize(); i++) {
            assertNull(inventory.getStack(i));
        }
    }
    
    @Test
    public void testGetAndSetStack() {
        ItemStack stack = new ItemStack(Items.STONE, 10);
        inventory.setStack(5, stack);
        assertEquals(stack, inventory.getStack(5));
    }
    
    @Test
    public void testGetStackOutOfBounds() {
        assertNull(inventory.getStack(-1));
        assertNull(inventory.getStack(36));
        assertNull(inventory.getStack(100));
    }
    
    @Test
    public void testSetStackOutOfBounds() {
        ItemStack stack = new ItemStack(Items.STONE, 10);
        // Should not throw, just silently fail
        inventory.setStack(-1, stack);
        inventory.setStack(36, stack);
        inventory.setStack(100, stack);
    }
    
    @Test
    public void testSelectedSlot() {
        assertEquals(0, inventory.getSelectedSlot());
        
        inventory.setSelectedSlot(5);
        assertEquals(5, inventory.getSelectedSlot());
        
        inventory.setSelectedSlot(8);
        assertEquals(8, inventory.getSelectedSlot());
    }
    
    @Test
    public void testSelectedSlotOutOfBounds() {
        inventory.setSelectedSlot(10); // Should be ignored
        assertEquals(0, inventory.getSelectedSlot());
        
        inventory.setSelectedSlot(-1); // Should be ignored
        assertEquals(0, inventory.getSelectedSlot());
    }
    
    @Test
    public void testGetSelectedStack() {
        assertNull(inventory.getSelectedStack());
        
        ItemStack stack = new ItemStack(Items.STONE, 10);
        inventory.setStack(0, stack);
        assertEquals(stack, inventory.getSelectedStack());
        
        inventory.setSelectedSlot(3);
        assertNull(inventory.getSelectedStack());
        
        ItemStack stack2 = new ItemStack(Items.COBBLESTONE, 5);
        inventory.setStack(3, stack2);
        assertEquals(stack2, inventory.getSelectedStack());
    }
    
    @Test
    public void testAddItemToEmptyInventory() {
        ItemStack stack = new ItemStack(Items.STONE, 10);
        assertTrue(inventory.addItem(stack));
        
        // Should be in slot 0 (first slot)
        ItemStack inSlot = inventory.getStack(0);
        assertNotNull(inSlot);
        assertEquals(Items.STONE, inSlot.getItem());
        assertEquals(10, inSlot.getCount());
    }
    
    @Test
    public void testAddItemMergesWithExisting() {
        ItemStack stack1 = new ItemStack(Items.STONE, 30);
        inventory.setStack(0, stack1);
        
        ItemStack stack2 = new ItemStack(Items.STONE, 20);
        assertTrue(inventory.addItem(stack2));
        
        // Should merge into slot 0
        assertEquals(50, inventory.getStack(0).getCount());
    }
    
    @Test
    public void testAddItemMergesPartially() {
        ItemStack stack1 = new ItemStack(Items.STONE, 60);
        inventory.setStack(0, stack1);
        
        ItemStack stack2 = new ItemStack(Items.STONE, 10);
        assertTrue(inventory.addItem(stack2));
        
        // Should merge 4 into slot 0 (to reach 64), rest into new slot
        assertEquals(64, inventory.getStack(0).getCount());
        assertEquals(6, inventory.getStack(1).getCount());
    }
    
    @Test
    public void testAddItemToFullInventory() {
        // Fill inventory
        for (int i = 0; i < 36; i++) {
            inventory.setStack(i, new ItemStack(Items.COBBLESTONE, 64));
        }
        
        ItemStack stack = new ItemStack(Items.STONE, 10);
        assertFalse(inventory.addItem(stack));
    }
    
    @Test
    public void testAddNullItem() {
        assertFalse(inventory.addItem(null));
    }
    
    @Test
    public void testFindFirstEmptySlot() {
        assertEquals(0, inventory.findFirstEmptySlot());
        
        inventory.setStack(0, new ItemStack(Items.STONE, 1));
        assertEquals(1, inventory.findFirstEmptySlot());
        
        inventory.setStack(1, new ItemStack(Items.COBBLESTONE, 1));
        assertEquals(2, inventory.findFirstEmptySlot());
    }
    
    @Test
    public void testFindFirstEmptySlotWhenFull() {
        for (int i = 0; i < 36; i++) {
            inventory.setStack(i, new ItemStack(Items.STONE, 1));
        }
        assertEquals(-1, inventory.findFirstEmptySlot());
    }
    
    @Test
    public void testIsFull() {
        assertFalse(inventory.isFull());
        
        for (int i = 0; i < 36; i++) {
            inventory.setStack(i, new ItemStack(Items.STONE, 1));
        }
        assertTrue(inventory.isFull());
    }
    
    @Test
    public void testClear() {
        // Add some items
        inventory.setStack(0, new ItemStack(Items.STONE, 10));
        inventory.setStack(5, new ItemStack(Items.COBBLESTONE, 20));
        inventory.setStack(35, new ItemStack(Items.DIRT, 30));
        
        inventory.clear();
        
        // All slots should be null
        for (int i = 0; i < 36; i++) {
            assertNull(inventory.getStack(i));
        }
    }
    
    @Test
    public void testIsHotbarSlot() {
        assertTrue(Inventory.isHotbarSlot(0));
        assertTrue(Inventory.isHotbarSlot(4));
        assertTrue(Inventory.isHotbarSlot(8));
        assertFalse(Inventory.isHotbarSlot(9));
        assertFalse(Inventory.isHotbarSlot(35));
        assertFalse(Inventory.isHotbarSlot(-1));
        assertFalse(Inventory.isHotbarSlot(36));
    }
    
    @Test
    public void testIsMainInventorySlot() {
        assertFalse(Inventory.isMainInventorySlot(0));
        assertFalse(Inventory.isMainInventorySlot(8));
        assertTrue(Inventory.isMainInventorySlot(9));
        assertTrue(Inventory.isMainInventorySlot(20));
        assertTrue(Inventory.isMainInventorySlot(35));
        assertFalse(Inventory.isMainInventorySlot(36));
        assertFalse(Inventory.isMainInventorySlot(-1));
    }
}
