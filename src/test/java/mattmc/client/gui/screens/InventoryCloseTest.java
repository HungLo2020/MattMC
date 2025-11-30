package mattmc.client.gui.screens;

import mattmc.world.item.Inventory;
import mattmc.world.item.ItemStack;
import mattmc.registries.Items;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for inventory close behavior to ensure items are not lost.
 */
public class InventoryCloseTest {
    
    @Test
    public void testReturnHeldItemToSourceSlot() {
        Inventory inventory = new Inventory();
        
        // Simulate picking up an item from slot 5
        ItemStack diamond = new ItemStack(Items.STONE, 10);
        inventory.setStack(5, diamond);
        
        // Simulate picking up the item (source slot is 5)
        ItemStack heldItem = inventory.getStack(5);
        inventory.setStack(5, null);
        int heldItemSourceSlot = 5;
        
        assertNotNull(heldItem);
        assertNull(inventory.getStack(5));
        
        // Simulate closing inventory - item should return to source slot
        if (heldItem != null && heldItemSourceSlot >= 0 && heldItemSourceSlot < inventory.getSize()) {
            ItemStack slotItem = inventory.getStack(heldItemSourceSlot);
            if (slotItem == null) {
                inventory.setStack(heldItemSourceSlot, heldItem);
                heldItem = null;
            }
        }
        
        // Verify item returned to source slot
        assertNull(heldItem);
        assertNotNull(inventory.getStack(5));
        assertEquals(Items.STONE, inventory.getStack(5).getItem());
        assertEquals(10, inventory.getStack(5).getCount());
    }
    
    @Test
    public void testReturnHeldItemWhenSourceSlotInvalid() {
        Inventory inventory = new Inventory();
        
        // Simulate holding an item but source slot is -1 (e.g., after placing some items)
        ItemStack heldItem = new ItemStack(Items.STONE, 5);
        int heldItemSourceSlot = -1; // Source slot is invalid
        
        // Ensure inventory has space
        assertTrue(inventory.findFirstEmptySlot() >= 0);
        
        // Simulate closing inventory - item should be added to inventory
        if (heldItem != null) {
            if (heldItemSourceSlot >= 0 && heldItemSourceSlot < inventory.getSize()) {
                // This branch won't execute because source slot is -1
                fail("Should not try to return to invalid source slot");
            }
            
            // Try to add to any compatible slot or first empty slot
            boolean added = inventory.addItem(heldItem);
            if (added) {
                heldItem = null;
            }
        }
        
        // Verify item was added to inventory
        assertNull(heldItem, "Held item should be null after being added to inventory");
        
        // Find where the item was added
        boolean foundItem = false;
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (stack != null && stack.getItem() == Items.STONE && stack.getCount() == 5) {
                foundItem = true;
                break;
            }
        }
        assertTrue(foundItem, "Item should be found in inventory");
    }
    
    @Test
    public void testMergeHeldItemWithSourceSlot() {
        Inventory inventory = new Inventory();
        
        // Place 30 stones in slot 10
        ItemStack slotItem = new ItemStack(Items.STONE, 30);
        inventory.setStack(10, slotItem);
        
        // Simulate holding 20 stones from the same slot (after placing some elsewhere)
        ItemStack heldItem = new ItemStack(Items.STONE, 20);
        int heldItemSourceSlot = 10;
        
        // Simulate closing inventory - items should merge
        if (heldItem != null && heldItemSourceSlot >= 0 && heldItemSourceSlot < inventory.getSize()) {
            ItemStack sourceSlotItem = inventory.getStack(heldItemSourceSlot);
            if (sourceSlotItem != null && sourceSlotItem.canMergeWith(heldItem)) {
                int spaceLeft = sourceSlotItem.getItem().getMaxStackSize() - sourceSlotItem.getCount();
                int toAdd = Math.min(spaceLeft, heldItem.getCount());
                if (toAdd > 0) {
                    sourceSlotItem.grow(toAdd);
                    int remaining = heldItem.getCount() - toAdd;
                    if (remaining > 0) {
                        heldItem.setCount(remaining);
                    } else {
                        heldItem = null;
                    }
                }
            }
        }
        
        // Verify items merged: 30 + 20 = 50 in slot, 0 held
        assertNull(heldItem, "All held items should have merged");
        assertEquals(50, inventory.getStack(10).getCount());
    }
    
    @Test
    public void testPartialMergeWhenSourceSlotNearlyFull() {
        Inventory inventory = new Inventory();
        
        // Place 60 stones in slot 10 (max is 64)
        ItemStack slotItem = new ItemStack(Items.STONE, 60);
        inventory.setStack(10, slotItem);
        
        // Simulate holding 10 stones
        ItemStack heldItem = new ItemStack(Items.STONE, 10);
        int heldItemSourceSlot = 10;
        
        // Simulate closing inventory - should merge 4, then add remaining 6 elsewhere
        if (heldItem != null && heldItemSourceSlot >= 0 && heldItemSourceSlot < inventory.getSize()) {
            ItemStack sourceSlotItem = inventory.getStack(heldItemSourceSlot);
            if (sourceSlotItem != null && sourceSlotItem.canMergeWith(heldItem)) {
                int spaceLeft = sourceSlotItem.getItem().getMaxStackSize() - sourceSlotItem.getCount();
                int toAdd = Math.min(spaceLeft, heldItem.getCount());
                if (toAdd > 0) {
                    sourceSlotItem.grow(toAdd);
                    int remaining = heldItem.getCount() - toAdd;
                    if (remaining > 0) {
                        heldItem.setCount(remaining);
                    } else {
                        heldItem = null;
                    }
                }
            }
        }
        
        // If item still held, try to add to inventory
        if (heldItem != null) {
            boolean added = inventory.addItem(heldItem);
            if (added) {
                heldItem = null;
            }
        }
        
        // Verify: slot 10 has 64 (full), remaining 6 added elsewhere
        assertNull(heldItem, "All held items should be placed");
        assertEquals(64, inventory.getStack(10).getCount());
        
        // Find the remaining 6 stones
        boolean foundRemaining = false;
        for (int i = 0; i < inventory.getSize(); i++) {
            if (i == 10) continue; // Skip the source slot
            ItemStack stack = inventory.getStack(i);
            if (stack != null && stack.getItem() == Items.STONE && stack.getCount() == 6) {
                foundRemaining = true;
                break;
            }
        }
        assertTrue(foundRemaining, "Remaining 6 stones should be in another slot");
    }
    
    @Test
    public void testItemLossWhenInventoryFull() {
        Inventory inventory = new Inventory();
        
        // Fill entire inventory
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setStack(i, new ItemStack(Items.DIRT, 64));
        }
        
        // Simulate holding an item
        ItemStack heldItem = new ItemStack(Items.STONE, 5);
        int heldItemSourceSlot = -1; // Source slot invalid
        
        // Try to add to inventory (should fail - inventory is full)
        boolean added = inventory.addItem(heldItem);
        
        assertFalse(added, "Should not be able to add to full inventory");
        assertNotNull(heldItem, "Item should still be held (will be lost)");
        // Note: In the current implementation, the item would be lost.
        // A production system might drop it to the world instead.
    }
}
