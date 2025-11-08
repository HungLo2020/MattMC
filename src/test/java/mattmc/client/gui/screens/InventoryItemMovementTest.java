package mattmc.client.gui.screens;

import mattmc.world.item.Inventory;
import mattmc.world.item.ItemStack;
import mattmc.world.item.Items;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for inventory item movement logic.
 */
public class InventoryItemMovementTest {
    
    @Test
    public void testPickUpAndPlaceItem() {
        Inventory inventory = new Inventory();
        
        // Place an item in slot 0
        ItemStack diamond = new ItemStack(Items.DIAMOND, 5);
        inventory.setStack(0, diamond);
        
        // Simulate picking up the item
        ItemStack pickedUp = inventory.getStack(0);
        assertNotNull(pickedUp);
        assertEquals(Items.DIAMOND, pickedUp.getItem());
        assertEquals(5, pickedUp.getCount());
        
        // Remove from original slot
        inventory.setStack(0, null);
        assertNull(inventory.getStack(0));
        
        // Place in new slot
        inventory.setStack(5, pickedUp);
        assertNotNull(inventory.getStack(5));
        assertEquals(Items.DIAMOND, inventory.getStack(5).getItem());
        assertEquals(5, inventory.getStack(5).getCount());
    }
    
    @Test
    public void testShiftClickHotbarToInventory() {
        Inventory inventory = new Inventory();
        
        // Place an item in hotbar slot 0
        ItemStack stone = new ItemStack(Items.STONE, 10);
        inventory.setStack(0, stone);
        
        // Find first empty slot in main inventory (9-35)
        int targetSlot = -1;
        for (int i = 9; i < 36; i++) {
            if (inventory.getStack(i) == null) {
                targetSlot = i;
                break;
            }
        }
        
        assertEquals(9, targetSlot, "First empty main inventory slot should be 9");
        
        // Move item
        inventory.setStack(targetSlot, stone);
        inventory.setStack(0, null);
        
        // Verify
        assertNull(inventory.getStack(0));
        assertNotNull(inventory.getStack(9));
        assertEquals(Items.STONE, inventory.getStack(9).getItem());
    }
    
    @Test
    public void testShiftClickInventoryToHotbar() {
        Inventory inventory = new Inventory();
        
        // Place an item in main inventory slot 9
        ItemStack iron = new ItemStack(Items.IRON_INGOT, 20);
        inventory.setStack(9, iron);
        
        // Find first empty slot in hotbar (0-8)
        int targetSlot = -1;
        for (int i = 0; i < 9; i++) {
            if (inventory.getStack(i) == null) {
                targetSlot = i;
                break;
            }
        }
        
        assertEquals(0, targetSlot, "First empty hotbar slot should be 0");
        
        // Move item
        inventory.setStack(targetSlot, iron);
        inventory.setStack(9, null);
        
        // Verify
        assertNull(inventory.getStack(9));
        assertNotNull(inventory.getStack(0));
        assertEquals(Items.IRON_INGOT, inventory.getStack(0).getItem());
    }
    
    @Test
    public void testShiftClickWhenTargetFull() {
        Inventory inventory = new Inventory();
        
        // Fill all hotbar slots
        for (int i = 0; i < 9; i++) {
            inventory.setStack(i, new ItemStack(Items.DIRT, 64));
        }
        
        // Place an item in main inventory
        ItemStack gold = new ItemStack(Items.GOLD_INGOT, 5);
        inventory.setStack(9, gold);
        
        // Try to shift-click (should not move since hotbar is full)
        int emptySlot = -1;
        for (int i = 0; i < 9; i++) {
            if (inventory.getStack(i) == null) {
                emptySlot = i;
                break;
            }
        }
        
        assertEquals(-1, emptySlot, "No empty hotbar slots should be available");
        
        // Item should remain in slot 9
        assertNotNull(inventory.getStack(9));
        assertEquals(Items.GOLD_INGOT, inventory.getStack(9).getItem());
    }
    
    @Test
    public void testIsHotbarSlot() {
        // Hotbar slots are 0-8
        for (int i = 0; i < 9; i++) {
            assertTrue(Inventory.isHotbarSlot(i), "Slot " + i + " should be a hotbar slot");
        }
        
        // Main inventory slots are 9-35
        for (int i = 9; i < 36; i++) {
            assertFalse(Inventory.isHotbarSlot(i), "Slot " + i + " should not be a hotbar slot");
        }
    }
    
    @Test
    public void testIsMainInventorySlot() {
        // Hotbar slots are 0-8
        for (int i = 0; i < 9; i++) {
            assertFalse(Inventory.isMainInventorySlot(i), "Slot " + i + " should not be a main inventory slot");
        }
        
        // Main inventory slots are 9-35
        for (int i = 9; i < 36; i++) {
            assertTrue(Inventory.isMainInventorySlot(i), "Slot " + i + " should be a main inventory slot");
        }
    }
}
