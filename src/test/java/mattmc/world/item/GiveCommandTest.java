package mattmc.world.item;
import mattmc.registries.Items;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the /give command functionality.
 * These tests verify the logic for adding items to the player's inventory.
 */
public class GiveCommandTest {
    
    @Test
    public void testItemLookupWithNamespace() {
        // Test looking up items with full namespace
        Item stone = Items.getItem("mattmc:stone");
        assertNotNull(stone, "Item 'mattmc:stone' should exist");
        assertEquals("mattmc:stone", stone.getIdentifier());
        
        Item dirt = Items.getItem("mattmc:dirt");
        assertNotNull(dirt, "Item 'mattmc:dirt' should exist");
        assertEquals("mattmc:dirt", dirt.getIdentifier());
        
        Item cobblestone = Items.getItem("mattmc:cobblestone");
        assertNotNull(cobblestone, "Item 'mattmc:cobblestone' should exist");
        assertEquals("mattmc:cobblestone", cobblestone.getIdentifier());
    }
    
    @Test
    public void testItemLookupWithoutNamespace() {
        // Test the logic for adding namespace automatically (used in /give command)
        String itemName = "stone";
        Item item = Items.getItem("mattmc:" + itemName);
        assertNotNull(item, "Item should be found with added namespace");
        assertEquals("mattmc:stone", item.getIdentifier());
    }
    
    @Test
    public void testItemLookupInvalidItem() {
        // Test looking up an item that doesn't exist
        Item invalid = Items.getItem("mattmc:invalid_item");
        assertNull(invalid, "Invalid item should return null");
    }
    
    @Test
    public void testAddSingleItem() {
        // Test adding a single item to an empty inventory
        Inventory inventory = new Inventory();
        Item stone = Items.STONE;
        ItemStack stack = new ItemStack(stone, 1);
        
        boolean added = inventory.addItem(stack);
        assertTrue(added, "Item should be added to empty inventory");
        
        // Verify item is in first hotbar slot (index 0)
        ItemStack retrievedStack = inventory.getStack(0);
        assertNotNull(retrievedStack, "Stack should be in first slot");
        assertEquals(stone, retrievedStack.getItem());
        assertEquals(1, retrievedStack.getCount());
    }
    
    @Test
    public void testAddMultipleItems() {
        // Test adding multiple items (more than max stack size)
        Inventory inventory = new Inventory();
        Item stone = Items.STONE;
        int maxStackSize = stone.getMaxStackSize();
        
        // Add first stack (max size)
        ItemStack stack1 = new ItemStack(stone, maxStackSize);
        boolean added1 = inventory.addItem(stack1);
        assertTrue(added1, "First stack should be added");
        
        // Add second stack (max size)
        ItemStack stack2 = new ItemStack(stone, maxStackSize);
        boolean added2 = inventory.addItem(stack2);
        assertTrue(added2, "Second stack should be added");
        
        // Verify both stacks are in inventory
        ItemStack retrievedStack1 = inventory.getStack(0);
        assertNotNull(retrievedStack1);
        assertEquals(maxStackSize, retrievedStack1.getCount());
        
        ItemStack retrievedStack2 = inventory.getStack(1);
        assertNotNull(retrievedStack2);
        assertEquals(maxStackSize, retrievedStack2.getCount());
    }
    
    @Test
    public void testAddItemWithMerging() {
        // Test that items merge with existing stacks
        Inventory inventory = new Inventory();
        Item stone = Items.STONE;
        
        // Add first stack with 32 items
        ItemStack stack1 = new ItemStack(stone, 32);
        inventory.addItem(stack1);
        
        // Add second stack with 16 items - should merge with first
        ItemStack stack2 = new ItemStack(stone, 16);
        inventory.addItem(stack2);
        
        // First slot should have 48 items (32 + 16)
        ItemStack retrievedStack = inventory.getStack(0);
        assertNotNull(retrievedStack);
        assertEquals(48, retrievedStack.getCount());
        
        // Second slot should be empty
        assertNull(inventory.getStack(1));
    }
    
    @Test
    public void testAddItemWithMergingOverflow() {
        // Test merging when overflow occurs
        Inventory inventory = new Inventory();
        Item stone = Items.STONE;
        int maxStackSize = stone.getMaxStackSize();
        
        // Add first stack with 60 items
        ItemStack stack1 = new ItemStack(stone, 60);
        inventory.addItem(stack1);
        
        // Add second stack with 10 items - should partially merge (4 items) and create new stack (6 items)
        ItemStack stack2 = new ItemStack(stone, 10);
        inventory.addItem(stack2);
        
        // First slot should be full (64 items)
        ItemStack retrievedStack1 = inventory.getStack(0);
        assertNotNull(retrievedStack1);
        assertEquals(maxStackSize, retrievedStack1.getCount());
        
        // Second slot should have remaining items (6 items)
        ItemStack retrievedStack2 = inventory.getStack(1);
        assertNotNull(retrievedStack2);
        assertEquals(6, retrievedStack2.getCount());
    }
    
    @Test
    public void testAddItemToFullInventory() {
        // Test that adding to a full inventory fails gracefully
        Inventory inventory = new Inventory();
        Item stone = Items.STONE;
        int maxStackSize = stone.getMaxStackSize();
        
        // Fill all 36 slots with max stacks
        for (int i = 0; i < 36; i++) {
            inventory.setStack(i, new ItemStack(stone, maxStackSize));
        }
        
        // Try to add another item
        ItemStack stack = new ItemStack(stone, 1);
        boolean added = inventory.addItem(stack);
        
        assertFalse(added, "Should not be able to add to full inventory");
    }
    
    @Test
    public void testGiveMultipleStacks() {
        // Simulate giving 200 stone (requires multiple stacks)
        Inventory inventory = new Inventory();
        Item stone = Items.STONE;
        int maxStackSize = stone.getMaxStackSize(); // 64
        int totalToGive = 200;
        int itemsGiven = 0;
        int remainingCount = totalToGive;
        
        while (remainingCount > 0) {
            int stackSize = Math.min(remainingCount, maxStackSize);
            ItemStack stack = new ItemStack(stone, stackSize);
            
            if (inventory.addItem(stack)) {
                itemsGiven += stackSize;
                remainingCount -= stackSize;
            } else {
                break;
            }
        }
        
        // Should have given all 200 items (4 full stacks of 64, one partial stack of 8)
        assertEquals(200, itemsGiven, "Should give all 200 items");
        
        // Verify stacks in inventory
        assertEquals(64, inventory.getStack(0).getCount());
        assertEquals(64, inventory.getStack(1).getCount());
        assertEquals(64, inventory.getStack(2).getCount());
        assertEquals(8, inventory.getStack(3).getCount());
    }
    
    @Test
    public void testHotbarPrioritization() {
        // Test that items are added to hotbar first (slots 0-8), then main inventory
        Inventory inventory = new Inventory();
        Item stone = Items.STONE;
        int maxStackSize = stone.getMaxStackSize();
        
        // Fill hotbar with max stacks (slots 0-8)
        for (int i = 0; i < 9; i++) {
            ItemStack stack = new ItemStack(stone, maxStackSize);
            inventory.addItem(stack);
        }
        
        // Verify all hotbar slots are full
        for (int i = 0; i < 9; i++) {
            ItemStack slot = inventory.getStack(i);
            assertNotNull(slot, "Hotbar slot " + i + " should have an item");
            assertEquals(maxStackSize, slot.getCount(), "Hotbar slot should be full");
        }
        
        // Add one more max stack - should go to main inventory (slot 9)
        ItemStack stack = new ItemStack(stone, maxStackSize);
        inventory.addItem(stack);
        
        ItemStack mainSlot = inventory.getStack(9);
        assertNotNull(mainSlot, "Item should be in main inventory slot 9");
        assertEquals(maxStackSize, mainSlot.getCount(), "Main inventory slot should be full");
    }
    
    @Test
    public void testStackableBlockItem() {
        // Test giving a stackable block item (like planks)
        Inventory inventory = new Inventory();
        Item planks = Items.SPRUCE_PLANKS;
        
        assertEquals(64, planks.getMaxStackSize(), "Planks should have max stack size of 64");
        
        // Add planks
        ItemStack stack = new ItemStack(planks, 16);
        boolean added = inventory.addItem(stack);
        assertTrue(added, "Planks should be added");
        
        // Verify planks are in first slot
        ItemStack retrievedStack = inventory.getStack(0);
        assertNotNull(retrievedStack);
        assertEquals(planks, retrievedStack.getItem());
        assertEquals(16, retrievedStack.getCount());
    }
    
    @Test
    public void testGiveCommandDefaultQuantity() {
        // Test that /give command defaults to quantity of 1 when not specified
        // This simulates the command: "/give stone" (without a count)
        Inventory inventory = new Inventory();
        Item stone = Items.STONE;
        
        // Default count should be 1
        int defaultCount = 1;
        ItemStack stack = new ItemStack(stone, defaultCount);
        boolean added = inventory.addItem(stack);
        
        assertTrue(added, "Item should be added with default count");
        
        // Verify item is in first slot with count of 1
        ItemStack retrievedStack = inventory.getStack(0);
        assertNotNull(retrievedStack, "Stack should be in first slot");
        assertEquals(stone, retrievedStack.getItem());
        assertEquals(1, retrievedStack.getCount(), "Default count should be 1");
    }
}
