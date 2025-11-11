package mattmc.client.gui.screens.inventory;

import mattmc.world.item.Inventory;
import mattmc.world.item.ItemStack;

import static org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT;

/**
 * Handles all mouse input and inventory interactions.
 */
public class InventoryInputHandler {
    private final InventorySlotManager slotManager;
    private ItemStack heldItem = null;
    private int heldItemSourceSlot = -1;
    
    public InventoryInputHandler(InventorySlotManager slotManager) {
        this.slotManager = slotManager;
    }
    
    public void handleLeftClick(Inventory inventory, int slotIndex, int mods) {
        if (slotIndex < 0) return;
        
        boolean isShiftClick = (mods & GLFW_MOD_SHIFT) != 0;
        if (isShiftClick) {
            handleShiftClick(inventory, slotIndex);
        } else {
            handleNormalClick(inventory, slotIndex);
        }
    }
    
    public void handleRightClick(Inventory inventory, int slotIndex) {
        if (slotIndex < 0) return;
        handleRightClickSlot(inventory, slotIndex);
    }
    
    /**
     * Handle normal (non-shift) click on an inventory slot.
     */
    private void handleNormalClick(Inventory inventory, int slotIndex) {
        ItemStack slotItem = inventory.getStack(slotIndex);
        
        if (heldItem == null) {
            // Pick up item from slot
            if (slotItem != null) {
                heldItem = slotItem;
                heldItemSourceSlot = slotIndex;
                inventory.setStack(slotIndex, null);
            }
        } else {
            // Placing held item in slot
            if (slotItem == null) {
                // Empty slot - place held item
                inventory.setStack(slotIndex, heldItem);
                heldItem = null;
                heldItemSourceSlot = -1;
            } else if (slotItem.getItem() == heldItem.getItem()) {
                // Same item type - try to merge
                int spaceLeft = slotItem.getItem().getMaxStackSize() - slotItem.getCount();
                if (spaceLeft > 0) {
                    int toAdd = Math.min(spaceLeft, heldItem.getCount());
                    slotItem.grow(toAdd);
                    
                    int remainingHeld = heldItem.getCount() - toAdd;
                    if (remainingHeld > 0) {
                        heldItem.setCount(remainingHeld);
                    } else {
                        heldItem = null;
                        heldItemSourceSlot = -1;
                    }
                } else {
                    // Stack is full - swap items
                    ItemStack temp = slotItem;
                    inventory.setStack(slotIndex, heldItem);
                    heldItem = temp;
                    heldItemSourceSlot = -1; // Source slot no longer valid after swap
                }
            } else {
                // Different item type - swap
                ItemStack temp = slotItem;
                inventory.setStack(slotIndex, heldItem);
                heldItem = temp;
                heldItemSourceSlot = -1; // Source slot no longer valid after swap
            }
        }
    }
    
    /**
     * Handle right-click on an inventory slot.
     * If cursor is empty and slot has items: pick up half (rounded up)
     * If cursor has items and slot is empty: place one item
     */
    private void handleRightClickSlot(Inventory inventory, int slotIndex) {
        ItemStack slotItem = inventory.getStack(slotIndex);
        
        if (heldItem == null) {
            // Empty cursor - pick up half of the stack
            if (slotItem != null) {
                int totalCount = slotItem.getCount();
                int pickupCount = (totalCount + 1) / 2; // Round up
                int remainingCount = totalCount - pickupCount;
                
                // Create held item with pickup count
                heldItem = new ItemStack(slotItem.getItem(), pickupCount);
                heldItemSourceSlot = slotIndex;
                
                // Update slot with remaining count, or clear if none left
                if (remainingCount > 0) {
                    slotItem.setCount(remainingCount);
                } else {
                    inventory.setStack(slotIndex, null);
                }
            }
        } else {
            // Cursor has items - place one item
            if (slotItem == null) {
                // Empty slot - place one item
                ItemStack newStack = new ItemStack(heldItem.getItem(), 1);
                inventory.setStack(slotIndex, newStack);
                
                // Reduce held item count
                int newHeldCount = heldItem.getCount() - 1;
                if (newHeldCount > 0) {
                    heldItem.setCount(newHeldCount);
                } else {
                    heldItem = null;
                    heldItemSourceSlot = -1;
                }
            } else if (slotItem.getItem() == heldItem.getItem() && !slotItem.isFull()) {
                // Same item type and not full - add one item
                slotItem.grow(1);
                
                // Reduce held item count
                int newHeldCount = heldItem.getCount() - 1;
                if (newHeldCount > 0) {
                    heldItem.setCount(newHeldCount);
                } else {
                    heldItem = null;
                    heldItemSourceSlot = -1;
                }
            }
        }
    }
    
    /**
     * Handle shift-click on an inventory slot.
     * Moves item from hotbar to inventory or vice versa, with stack merging support.
     */
    private void handleShiftClick(Inventory inventory, int slotIndex) {
        ItemStack slotItem = inventory.getStack(slotIndex);
        
        if (slotItem == null) {
            return; // Nothing to move
        }
        
        // Create a copy to track remaining items to move
        ItemStack itemsToMove = slotItem.copy();
        
        if (slotManager.isHotbarSlot(slotIndex)) {
            // Move from hotbar to main inventory (slots 9-35)
            itemsToMove = moveItemsToRange(inventory, itemsToMove, 9, 36);
        } else if (slotManager.isMainInventorySlot(slotIndex)) {
            // Move from main inventory to hotbar (slots 0-8)
            itemsToMove = moveItemsToRange(inventory, itemsToMove, 0, 9);
        }
        
        // Update source slot
        if (itemsToMove == null || itemsToMove.getCount() == 0) {
            // All items moved
            inventory.setStack(slotIndex, null);
        } else {
            // Some items couldn't be moved
            inventory.setStack(slotIndex, itemsToMove);
        }
    }
    
    /**
     * Helper method to move items to a range of slots with merging support.
     * First tries to merge with existing stacks, then places in empty slots.
     * 
     * @param inventory The inventory
     * @param itemsToMove The items to move
     * @param startSlot Start of the slot range (inclusive)
     * @param endSlot End of the slot range (exclusive)
     * @return Remaining items that couldn't be moved, or null if all moved
     */
    private ItemStack moveItemsToRange(Inventory inventory, ItemStack itemsToMove, int startSlot, int endSlot) {
        if (itemsToMove == null || itemsToMove.getCount() == 0) {
            return null;
        }
        
        // First pass: try to merge with existing stacks of the same type
        for (int i = startSlot; i < endSlot; i++) {
            ItemStack targetStack = inventory.getStack(i);
            if (targetStack != null && targetStack.canMergeWith(itemsToMove)) {
                int spaceLeft = targetStack.getItem().getMaxStackSize() - targetStack.getCount();
                if (spaceLeft > 0) {
                    int toAdd = Math.min(spaceLeft, itemsToMove.getCount());
                    targetStack.grow(toAdd);
                    int remaining = itemsToMove.getCount() - toAdd;
                    
                    if (remaining <= 0) {
                        return null; // All items merged
                    }
                    itemsToMove.setCount(remaining);
                }
            }
        }
        
        // Second pass: place remaining items in empty slots
        for (int i = startSlot; i < endSlot; i++) {
            if (inventory.getStack(i) == null) {
                inventory.setStack(i, itemsToMove.copy());
                return null; // All items placed
            }
        }
        
        // If we get here, there wasn't enough space for all items
        return itemsToMove;
    }
    
    /**
     * Handle delete key press to delete held item or item under cursor.
     */
    public void handleDeleteItem(Inventory inventory, int slotIndex) {
        // If holding an item, delete it
        if (heldItem != null) {
            heldItem = null;
            heldItemSourceSlot = -1;
            return;
        }
        
        // Otherwise, delete the item in the slot under the cursor
        if (slotIndex >= 0) {
            ItemStack slotItem = inventory.getStack(slotIndex);
            if (slotItem != null) {
                // Delete the item by setting the slot to null
                inventory.setStack(slotIndex, null);
            }
        }
    }
    
    public ItemStack getHeldItem() {
        return heldItem;
    }
    
    public void setHeldItem(ItemStack item) {
        this.heldItem = item;
    }
    
    public void clearHeldItem() {
        this.heldItem = null;
        this.heldItemSourceSlot = -1;
    }
}
