package mattmc.world.item;

/**
 * Represents a player's inventory.
 * Similar to MattMC's Inventory class.
 * 
 * The inventory consists of:
 * - 9 hotbar slots (indices 0-8)
 * - 27 main inventory slots (indices 9-35)
 * Total: 36 slots
 */
public class Inventory {
    /** Size of the hotbar (9 slots). */
    public static final int HOTBAR_SIZE = 9;
    /** Size of the main inventory (27 slots). */
    public static final int MAIN_SIZE = 27;
    /** Total inventory size (36 slots = hotbar + main). */
    public static final int INVENTORY_SIZE = HOTBAR_SIZE + MAIN_SIZE; // 36 total
    
    private final ItemStack[] items;
    private int selectedHotbarSlot = 0; // 0-8, currently selected hotbar slot
    
    public Inventory() {
        this.items = new ItemStack[INVENTORY_SIZE];
    }
    
    /**
     * Get the item stack in a specific slot.
     * 
     * @param slot Slot index (0-35)
     * @return The item stack, or null if the slot is empty
     */
    public ItemStack getStack(int slot) {
        if (slot < 0 || slot >= INVENTORY_SIZE) {
            return null;
        }
        return items[slot];
    }
    
    /**
     * Set the item stack in a specific slot.
     * 
     * @param slot Slot index (0-35)
     * @param stack The item stack to set (can be null to clear the slot)
     */
    public void setStack(int slot, ItemStack stack) {
        if (slot >= 0 && slot < INVENTORY_SIZE) {
            items[slot] = stack;
        }
    }
    
    /**
     * Get the currently selected hotbar slot index.
     * 
     * @return Slot index (0-8)
     */
    public int getSelectedSlot() {
        return selectedHotbarSlot;
    }
    
    /**
     * Set the currently selected hotbar slot.
     * 
     * @param slot Slot index (0-8)
     */
    public void setSelectedSlot(int slot) {
        if (slot >= 0 && slot < HOTBAR_SIZE) {
            selectedHotbarSlot = slot;
        }
    }
    
    /**
     * Get the item stack in the currently selected hotbar slot.
     * 
     * @return The item stack, or null if empty
     */
    public ItemStack getSelectedStack() {
        return items[selectedHotbarSlot];
    }
    
    /**
     * Add an item stack to the first empty slot in the inventory.
     * Attempts to add to hotbar first, then main inventory.
     * 
     * @param stack The item stack to add
     * @return true if the item was added successfully
     */
    public boolean addItem(ItemStack stack) {
        if (stack == null) {
            return false;
        }
        
        // First, try to merge with existing stacks
        for (int i = 0; i < INVENTORY_SIZE; i++) {
            ItemStack existing = items[i];
            if (existing != null && existing.canMergeWith(stack)) {
                int toAdd = Math.min(stack.getCount(), existing.getItem().getMaxStackSize() - existing.getCount());
                existing.grow(toAdd);
                int remaining = stack.getCount() - toAdd;
                if (remaining <= 0) {
                    return true; // Fully merged
                }
                stack.setCount(remaining);
            }
        }
        
        // Then, try to add to first empty slot (hotbar first, then main inventory)
        for (int i = 0; i < INVENTORY_SIZE; i++) {
            if (items[i] == null) {
                items[i] = stack.copy();
                return true;
            }
        }
        
        return false; // Inventory full
    }
    
    /**
     * Find the first empty slot in the inventory.
     * Checks hotbar first (0-8), then main inventory (9-35).
     * 
     * @return The slot index, or -1 if inventory is full
     */
    public int findFirstEmptySlot() {
        for (int i = 0; i < INVENTORY_SIZE; i++) {
            if (items[i] == null) {
                return i;
            }
        }
        return -1;
    }
    
    /**
     * Check if the inventory is full.
     * 
     * @return true if all slots are occupied
     */
    public boolean isFull() {
        return findFirstEmptySlot() == -1;
    }
    
    /**
     * Clear the inventory (remove all items).
     */
    public void clear() {
        for (int i = 0; i < INVENTORY_SIZE; i++) {
            items[i] = null;
        }
    }
    
    /**
     * Get the total size of the inventory.
     * 
     * @return 36 (9 hotbar + 27 main inventory)
     */
    public int getSize() {
        return INVENTORY_SIZE;
    }
    
    /**
     * Check if a slot index is a hotbar slot.
     * 
     * @param slot Slot index
     * @return true if the slot is in the hotbar (0-8)
     */
    public static boolean isHotbarSlot(int slot) {
        return slot >= 0 && slot < HOTBAR_SIZE;
    }
    
    /**
     * Check if a slot index is a main inventory slot.
     * 
     * @param slot Slot index
     * @return true if the slot is in the main inventory (9-35)
     */
    public static boolean isMainInventorySlot(int slot) {
        return slot >= HOTBAR_SIZE && slot < INVENTORY_SIZE;
    }
    
    /**
     * Find the slot containing an item matching the given stack.
     * Matches by item type (identifier).
     * Searches all slots (hotbar first, then main inventory).
     * 
     * @param stack The item stack to match
     * @return The slot index containing the matching item, or -1 if not found
     */
    public int findSlotMatchingItem(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return -1;
        }
        String targetId = stack.getItem().getIdentifier();
        if (targetId == null) {
            return -1;
        }
        
        for (int i = 0; i < INVENTORY_SIZE; i++) {
            ItemStack existing = items[i];
            if (existing != null && existing.getItem() != null) {
                String existingId = existing.getItem().getIdentifier();
                if (targetId.equals(existingId)) {
                    return i;
                }
            }
        }
        return -1;
    }
    
    /**
     * Set the picked item similar to Minecraft's behavior.
     * If the item already exists in the hotbar, switch to that slot.
     * If not in hotbar but exists in inventory, swap with current slot.
     * If not in inventory and hotbar is full, move current slot to first empty main inventory slot.
     * If no empty slot exists, the pick is cancelled (item not added) to prevent item loss.
     * 
     * @param stack The item stack to set as picked
     * @return true if the item was picked successfully, false if operation was cancelled
     */
    public boolean setPickedItem(ItemStack stack) {
        int existingSlot = findSlotMatchingItem(stack);
        
        if (isHotbarSlot(existingSlot)) {
            // Item is already in hotbar - just select that slot
            selectedHotbarSlot = existingSlot;
            return true;
        } else if (existingSlot != -1) {
            // Item exists in main inventory - swap with current selected slot
            pickSlot(existingSlot);
            return true;
        } else {
            // Item not in inventory - add to current slot
            // If current slot is occupied, move its contents to first empty slot
            if (items[selectedHotbarSlot] != null) {
                int emptySlot = findFirstEmptySlotInMainInventory();
                if (emptySlot == -1) {
                    // No empty slot available - cancel pick to prevent item loss
                    // This matches Minecraft behavior where pick block does nothing when inventory is full
                    return false;
                }
                items[emptySlot] = items[selectedHotbarSlot];
            }
            items[selectedHotbarSlot] = stack.copy();
            return true;
        }
    }
    
    /**
     * Swap the contents of the given slot with the currently selected hotbar slot.
     * This is used when picking an item from the main inventory.
     * 
     * @param slot The slot to swap with
     */
    public void pickSlot(int slot) {
        ItemStack temp = items[selectedHotbarSlot];
        items[selectedHotbarSlot] = items[slot];
        items[slot] = temp;
    }
    
    /**
     * Find the first empty slot in the main inventory only (slots 9-35).
     * 
     * @return The slot index, or -1 if main inventory is full
     */
    public int findFirstEmptySlotInMainInventory() {
        for (int i = HOTBAR_SIZE; i < INVENTORY_SIZE; i++) {
            if (items[i] == null) {
                return i;
            }
        }
        return -1;
    }
    
    /**
     * Find the first empty slot in the hotbar only (slots 0-8).
     * 
     * @return The slot index, or -1 if hotbar is full
     */
    public int findFirstEmptySlotInHotbar() {
        for (int i = 0; i < HOTBAR_SIZE; i++) {
            if (items[i] == null) {
                return i;
            }
        }
        return -1;
    }
}
