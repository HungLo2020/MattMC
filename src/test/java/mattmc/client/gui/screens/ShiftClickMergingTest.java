package mattmc.client.gui.screens;

import mattmc.world.item.Inventory;
import mattmc.world.item.ItemStack;
import mattmc.registries.Items;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for shift-click stack merging functionality.
 */
public class ShiftClickMergingTest {
    
    @Test
    public void testShiftClickMergeFromHotbarToInventory() {
        Inventory inventory = new Inventory();
        
        // Place 30 stones in hotbar slot 0
        inventory.setStack(0, new ItemStack(Items.STONE, 30));
        
        // Place 20 stones in main inventory slot 9 (has space for 34 more)
        inventory.setStack(9, new ItemStack(Items.STONE, 20));
        
        // Simulate shift-click from hotbar slot 0
        // Should merge all 30 stones into slot 9 (20 + 30 = 50)
        ItemStack sourceStack = inventory.getStack(0);
        ItemStack itemsToMove = sourceStack.copy();
        
        // Try to merge with existing stacks in main inventory (9-35)
        for (int i = 9; i < 36; i++) {
            ItemStack targetStack = inventory.getStack(i);
            if (targetStack != null && targetStack.canMergeWith(itemsToMove)) {
                int spaceLeft = targetStack.getItem().getMaxStackSize() - targetStack.getCount();
                if (spaceLeft > 0) {
                    int toAdd = Math.min(spaceLeft, itemsToMove.getCount());
                    targetStack.grow(toAdd);
                    int remaining = itemsToMove.getCount() - toAdd;
                    
                    if (remaining <= 0) {
                        itemsToMove = null;
                        break;
                    }
                    itemsToMove.setCount(remaining);
                }
            }
        }
        
        // Update source slot
        if (itemsToMove == null) {
            inventory.setStack(0, null);
        }
        
        // Verify: slot 0 should be empty, slot 9 should have 50 stones
        assertNull(inventory.getStack(0), "Source slot should be empty");
        assertNotNull(inventory.getStack(9), "Target slot should have items");
        assertEquals(50, inventory.getStack(9).getCount(), "Should have merged to 50 stones");
    }
    
    @Test
    public void testShiftClickPartialMergeThenPlaceRemainder() {
        Inventory inventory = new Inventory();
        
        // Place 40 stones in hotbar slot 0
        inventory.setStack(0, new ItemStack(Items.STONE, 40));
        
        // Place 60 stones in main inventory slot 9 (can only take 4 more)
        inventory.setStack(9, new ItemStack(Items.STONE, 60));
        
        // Simulate shift-click from hotbar slot 0
        ItemStack itemsToMove = inventory.getStack(0).copy();
        
        // First pass: try to merge with existing stacks
        for (int i = 9; i < 36; i++) {
            ItemStack targetStack = inventory.getStack(i);
            if (targetStack != null && targetStack.canMergeWith(itemsToMove)) {
                int spaceLeft = targetStack.getItem().getMaxStackSize() - targetStack.getCount();
                if (spaceLeft > 0) {
                    int toAdd = Math.min(spaceLeft, itemsToMove.getCount());
                    targetStack.grow(toAdd);
                    int remaining = itemsToMove.getCount() - toAdd;
                    
                    if (remaining <= 0) {
                        itemsToMove = null;
                        break;
                    }
                    itemsToMove.setCount(remaining);
                }
            }
        }
        
        // Second pass: place remaining in empty slots
        if (itemsToMove != null) {
            for (int i = 9; i < 36; i++) {
                if (inventory.getStack(i) == null) {
                    inventory.setStack(i, itemsToMove.copy());
                    itemsToMove = null;
                    break;
                }
            }
        }
        
        // Update source
        inventory.setStack(0, itemsToMove);
        
        // Verify: slot 9 should have 64 (full), slot 10 should have 36
        assertNull(inventory.getStack(0), "Source slot should be empty");
        assertEquals(64, inventory.getStack(9).getCount(), "Slot 9 should be full");
        assertNotNull(inventory.getStack(10), "Slot 10 should have remaining items");
        assertEquals(36, inventory.getStack(10).getCount(), "Slot 10 should have 36 stones");
    }
    
    @Test
    public void testShiftClickMergeFromInventoryToHotbar() {
        Inventory inventory = new Inventory();
        
        // Place 25 diamonds in main inventory slot 10
        inventory.setStack(10, new ItemStack(Items.STONE, 25));
        
        // Place 15 diamonds in hotbar slot 3
        inventory.setStack(3, new ItemStack(Items.STONE, 15));
        
        // Simulate shift-click from main inventory slot 10
        ItemStack itemsToMove = inventory.getStack(10).copy();
        
        // Try to merge with existing stacks in hotbar (0-8)
        for (int i = 0; i < 9; i++) {
            ItemStack targetStack = inventory.getStack(i);
            if (targetStack != null && targetStack.canMergeWith(itemsToMove)) {
                int spaceLeft = targetStack.getItem().getMaxStackSize() - targetStack.getCount();
                if (spaceLeft > 0) {
                    int toAdd = Math.min(spaceLeft, itemsToMove.getCount());
                    targetStack.grow(toAdd);
                    int remaining = itemsToMove.getCount() - toAdd;
                    
                    if (remaining <= 0) {
                        itemsToMove = null;
                        break;
                    }
                    itemsToMove.setCount(remaining);
                }
            }
        }
        
        // Update source
        inventory.setStack(10, itemsToMove);
        
        // Verify: slot 3 should have 40 (15+25), slot 10 should be empty
        assertNull(inventory.getStack(10), "Source slot should be empty");
        assertEquals(40, inventory.getStack(3).getCount(), "Hotbar slot 3 should have 40 diamonds");
    }
    
    @Test
    public void testShiftClickWithMultiplePartialMerges() {
        Inventory inventory = new Inventory();
        
        // Place 100 stones in hotbar slot 0
        inventory.setStack(0, new ItemStack(Items.STONE, 64));
        
        // Place partial stacks in main inventory
        inventory.setStack(9, new ItemStack(Items.STONE, 50));  // Can take 14
        inventory.setStack(10, new ItemStack(Items.STONE, 60)); // Can take 4
        
        // Simulate shift-click from hotbar slot 0
        ItemStack itemsToMove = inventory.getStack(0).copy();
        
        // Merge pass
        for (int i = 9; i < 36; i++) {
            ItemStack targetStack = inventory.getStack(i);
            if (targetStack != null && targetStack.canMergeWith(itemsToMove)) {
                int spaceLeft = targetStack.getItem().getMaxStackSize() - targetStack.getCount();
                if (spaceLeft > 0) {
                    int toAdd = Math.min(spaceLeft, itemsToMove.getCount());
                    targetStack.grow(toAdd);
                    int remaining = itemsToMove.getCount() - toAdd;
                    
                    if (remaining <= 0) {
                        itemsToMove = null;
                        break;
                    }
                    itemsToMove.setCount(remaining);
                }
            }
        }
        
        // Place remaining in empty slots
        if (itemsToMove != null) {
            for (int i = 9; i < 36; i++) {
                if (inventory.getStack(i) == null) {
                    inventory.setStack(i, itemsToMove.copy());
                    itemsToMove = null;
                    break;
                }
            }
        }
        
        // Update source
        inventory.setStack(0, itemsToMove);
        
        // Verify: 
        // - Slot 0 should be empty
        // - Slot 9 should have 64 (50 + 14)
        // - Slot 10 should have 64 (60 + 4)
        // - Slot 11 should have 46 (remaining: 64 - 14 - 4 = 46)
        assertNull(inventory.getStack(0), "Source slot should be empty");
        assertEquals(64, inventory.getStack(9).getCount(), "Slot 9 should be full");
        assertEquals(64, inventory.getStack(10).getCount(), "Slot 10 should be full");
        assertNotNull(inventory.getStack(11), "Slot 11 should have remaining items");
        assertEquals(46, inventory.getStack(11).getCount(), "Slot 11 should have 46 stones");
    }
    
    @Test
    public void testShiftClickWhenNoSpaceInTarget() {
        Inventory inventory = new Inventory();
        
        // Place stones in hotbar slot 0
        inventory.setStack(0, new ItemStack(Items.STONE, 20));
        
        // Fill all main inventory slots with different items
        for (int i = 9; i < 36; i++) {
            inventory.setStack(i, new ItemStack(Items.DIRT, 64));
        }
        
        // Simulate shift-click from hotbar slot 0 (should do nothing)
        ItemStack itemsToMove = inventory.getStack(0).copy();
        
        // Try merge (won't work - different items)
        for (int i = 9; i < 36; i++) {
            ItemStack targetStack = inventory.getStack(i);
            if (targetStack != null && targetStack.canMergeWith(itemsToMove)) {
                int spaceLeft = targetStack.getItem().getMaxStackSize() - targetStack.getCount();
                if (spaceLeft > 0) {
                    int toAdd = Math.min(spaceLeft, itemsToMove.getCount());
                    targetStack.grow(toAdd);
                    itemsToMove.setCount(itemsToMove.getCount() - toAdd);
                }
            }
        }
        
        // Try empty slots (none available)
        if (itemsToMove != null && itemsToMove.getCount() > 0) {
            boolean placed = false;
            for (int i = 9; i < 36; i++) {
                if (inventory.getStack(i) == null) {
                    inventory.setStack(i, itemsToMove);
                    itemsToMove = null;
                    placed = true;
                    break;
                }
            }
            
            // If couldn't place, items remain in source
            if (!placed) {
                // Do nothing - keep items in source slot
            }
        }
        
        // Verify: source slot should still have the items
        assertNotNull(inventory.getStack(0), "Source slot should still have items");
        assertEquals(20, inventory.getStack(0).getCount(), "Items should not have moved");
    }
}
