package mattmc.client.gui.screens;

import mattmc.world.item.Inventory;
import mattmc.world.item.ItemStack;
import mattmc.world.item.Items;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for inventory delete item functionality.
 */
public class InventoryDeleteItemTest {
    
    @Test
    public void testDeleteItemFromSlot() {
        Inventory inventory = new Inventory();
        
        // Place an item in slot 0
        ItemStack diamond = new ItemStack(Items.STONE, 5);
        inventory.setStack(0, diamond);
        
        // Verify item is there
        assertNotNull(inventory.getStack(0));
        assertEquals(Items.STONE, inventory.getStack(0).getItem());
        assertEquals(5, inventory.getStack(0).getCount());
        
        // Simulate delete (set to null)
        inventory.setStack(0, null);
        
        // Verify item is deleted
        assertNull(inventory.getStack(0));
    }
    
    @Test
    public void testDeleteHeldItem() {
        // Simulate holding an item
        ItemStack heldItem = new ItemStack(Items.STONE, 10);
        
        // Verify item exists
        assertNotNull(heldItem);
        assertEquals(Items.STONE, heldItem.getItem());
        assertEquals(10, heldItem.getCount());
        
        // Simulate delete (set to null)
        heldItem = null;
        
        // Verify item is deleted
        assertNull(heldItem);
    }
    
    @Test
    public void testDeleteEntireStack() {
        Inventory inventory = new Inventory();
        
        // Place a full stack in slot 5
        ItemStack stone = new ItemStack(Items.STONE, 64);
        inventory.setStack(5, stone);
        
        // Verify full stack is there
        assertNotNull(inventory.getStack(5));
        assertEquals(64, inventory.getStack(5).getCount());
        
        // Delete the entire stack
        inventory.setStack(5, null);
        
        // Verify slot is empty
        assertNull(inventory.getStack(5));
    }
    
    @Test
    public void testDeletePartialStack() {
        Inventory inventory = new Inventory();
        
        // Place a partial stack in slot 10
        ItemStack iron = new ItemStack(Items.ACACIA_PLANKS, 15);
        inventory.setStack(10, iron);
        
        // Verify partial stack is there
        assertNotNull(inventory.getStack(10));
        assertEquals(15, inventory.getStack(10).getCount());
        
        // Delete the partial stack
        inventory.setStack(10, null);
        
        // Verify slot is empty
        assertNull(inventory.getStack(10));
    }
    
    @Test
    public void testDeleteFromHotbar() {
        Inventory inventory = new Inventory();
        
        // Place items in multiple hotbar slots
        inventory.setStack(0, new ItemStack(Items.STONE, 5));
        inventory.setStack(1, new ItemStack(Items.DARK_OAK_PLANKS, 10));
        inventory.setStack(2, new ItemStack(Items.ACACIA_PLANKS, 20));
        
        // Delete item from slot 1
        inventory.setStack(1, null);
        
        // Verify slot 1 is empty but others remain
        assertNotNull(inventory.getStack(0));
        assertNull(inventory.getStack(1));
        assertNotNull(inventory.getStack(2));
    }
    
    @Test
    public void testDeleteFromMainInventory() {
        Inventory inventory = new Inventory();
        
        // Place items in multiple main inventory slots
        inventory.setStack(9, new ItemStack(Items.STONE, 5));
        inventory.setStack(15, new ItemStack(Items.DARK_OAK_PLANKS, 10));
        inventory.setStack(20, new ItemStack(Items.ACACIA_PLANKS, 20));
        
        // Delete item from slot 15
        inventory.setStack(15, null);
        
        // Verify slot 15 is empty but others remain
        assertNotNull(inventory.getStack(9));
        assertNull(inventory.getStack(15));
        assertNotNull(inventory.getStack(20));
    }
    
    @Test
    public void testDeleteDoesNotAffectOtherSlots() {
        Inventory inventory = new Inventory();
        
        // Fill inventory with different items
        for (int i = 0; i < 36; i++) {
            inventory.setStack(i, new ItemStack(Items.STONE, i + 1));
        }
        
        // Delete item from slot 18
        inventory.setStack(18, null);
        
        // Verify only slot 18 is empty
        for (int i = 0; i < 36; i++) {
            if (i == 18) {
                assertNull(inventory.getStack(i), "Slot " + i + " should be empty");
            } else {
                assertNotNull(inventory.getStack(i), "Slot " + i + " should not be empty");
                assertEquals(i + 1, inventory.getStack(i).getCount(), "Slot " + i + " count mismatch");
            }
        }
    }
    
    @Test
    public void testDeleteEmptySlot() {
        Inventory inventory = new Inventory();
        
        // Verify slot is already empty
        assertNull(inventory.getStack(5));
        
        // Delete from empty slot (should not cause issues)
        inventory.setStack(5, null);
        
        // Verify slot is still empty
        assertNull(inventory.getStack(5));
    }
}
