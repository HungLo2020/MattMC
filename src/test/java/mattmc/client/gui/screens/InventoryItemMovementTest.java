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
    public void testRightClickPickUpHalf() {
        Inventory inventory = new Inventory();
        
        // Place 10 items in slot 0
        ItemStack stone = new ItemStack(Items.STONE, 10);
        inventory.setStack(0, stone);
        
        // Simulate right-click pick up (pick up half, rounded up)
        int totalCount = stone.getCount();
        int pickupCount = (totalCount + 1) / 2; // Should be 5
        int remainingCount = totalCount - pickupCount; // Should be 5
        
        assertEquals(5, pickupCount);
        assertEquals(5, remainingCount);
        
        // Update inventory
        ItemStack pickedUp = new ItemStack(stone.getItem(), pickupCount);
        stone.setCount(remainingCount);
        
        // Verify
        assertEquals(5, pickedUp.getCount());
        assertEquals(5, inventory.getStack(0).getCount());
    }
    
    @Test
    public void testRightClickPickUpHalfOddNumber() {
        Inventory inventory = new Inventory();
        
        // Place 9 items in slot 0 (odd number)
        ItemStack stone = new ItemStack(Items.STONE, 9);
        inventory.setStack(0, stone);
        
        // Simulate right-click pick up (pick up half, rounded up)
        int totalCount = stone.getCount();
        int pickupCount = (totalCount + 1) / 2; // Should be 5 (rounded up)
        int remainingCount = totalCount - pickupCount; // Should be 4
        
        assertEquals(5, pickupCount);
        assertEquals(4, remainingCount);
    }
    
    @Test
    public void testRightClickPlaceOne() {
        Inventory inventory = new Inventory();
        
        // Simulate holding 10 items
        ItemStack held = new ItemStack(Items.DIAMOND, 10);
        
        // Right-click on empty slot (place one)
        ItemStack placedStack = new ItemStack(held.getItem(), 1);
        inventory.setStack(0, placedStack);
        
        // Reduce held count
        held.setCount(held.getCount() - 1);
        
        // Verify
        assertEquals(1, inventory.getStack(0).getCount());
        assertEquals(9, held.getCount());
    }
    
    @Test
    public void testRightClickPlaceOneOnSameType() {
        Inventory inventory = new Inventory();
        
        // Place 5 items in slot 0
        ItemStack slotStack = new ItemStack(Items.STONE, 5);
        inventory.setStack(0, slotStack);
        
        // Simulate holding 10 of the same item
        ItemStack held = new ItemStack(Items.STONE, 10);
        
        // Right-click on slot with same item type (add one)
        slotStack.grow(1);
        held.setCount(held.getCount() - 1);
        
        // Verify
        assertEquals(6, inventory.getStack(0).getCount());
        assertEquals(9, held.getCount());
    }
    
    @Test
    public void testItemMerging() {
        Inventory inventory = new Inventory();
        
        // Place 10 items in slot 0
        ItemStack slotStack = new ItemStack(Items.STONE, 10);
        inventory.setStack(0, slotStack);
        
        // Simulate holding 5 of the same item
        ItemStack held = new ItemStack(Items.STONE, 5);
        
        // Merge items (add all 5 to the slot)
        int spaceLeft = slotStack.getItem().getMaxStackSize() - slotStack.getCount();
        int toAdd = Math.min(spaceLeft, held.getCount());
        slotStack.grow(toAdd);
        int remainingHeld = held.getCount() - toAdd;
        
        // Verify - all 5 should be added
        assertEquals(15, inventory.getStack(0).getCount());
        assertEquals(0, remainingHeld);
    }
    
    @Test
    public void testItemMergingPartial() {
        Inventory inventory = new Inventory();
        
        // Place 60 items in slot 0 (max is 64 for most items)
        ItemStack slotStack = new ItemStack(Items.STONE, 60);
        inventory.setStack(0, slotStack);
        
        // Simulate holding 10 of the same item
        ItemStack held = new ItemStack(Items.STONE, 10);
        
        // Merge items (can only add 4 due to max stack size)
        int spaceLeft = slotStack.getItem().getMaxStackSize() - slotStack.getCount();
        int toAdd = Math.min(spaceLeft, held.getCount());
        slotStack.grow(toAdd);
        int remainingHeld = held.getCount() - toAdd;
        
        // Verify - only 4 should be added (60 + 4 = 64 max)
        assertEquals(64, inventory.getStack(0).getCount());
        assertEquals(6, remainingHeld); // 10 - 4 = 6 remaining
    }
    
    @Test
    public void testItemSwapDifferentTypes() {
        Inventory inventory = new Inventory();
        
        // Place stone in slot 0
        ItemStack slotStack = new ItemStack(Items.STONE, 10);
        inventory.setStack(0, slotStack);
        
        // Simulate holding diamond
        ItemStack held = new ItemStack(Items.DIAMOND, 5);
        
        // Swap items (different types)
        ItemStack temp = slotStack;
        inventory.setStack(0, held);
        held = temp;
        
        // Verify swap
        assertEquals(Items.DIAMOND, inventory.getStack(0).getItem());
        assertEquals(5, inventory.getStack(0).getCount());
        assertEquals(Items.STONE, held.getItem());
        assertEquals(10, held.getCount());
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
