package mattmc.world.item;

/**
 * Represents a stack of items in an inventory slot.
 * Similar to Minecraft's ItemStack class.
 * 
 * An ItemStack contains an Item type and a count.
 * The count cannot exceed the item's maximum stack size.
 */
public class ItemStack {
    private final Item item;
    private int count;
    
    /**
     * Create a new item stack with count of 1.
     * 
     * @param item The item type
     */
    public ItemStack(Item item) {
        this(item, 1);
    }
    
    /**
     * Create a new item stack with a specific count.
     * 
     * @param item The item type
     * @param count Number of items in this stack
     * @throws IllegalArgumentException if count is invalid
     */
    public ItemStack(Item item, int count) {
        if (item == null) {
            throw new IllegalArgumentException("Item cannot be null");
        }
        if (count <= 0) {
            throw new IllegalArgumentException("Count must be positive");
        }
        if (count > item.getMaxStackSize()) {
            throw new IllegalArgumentException("Count " + count + " exceeds max stack size " + item.getMaxStackSize());
        }
        
        this.item = item;
        this.count = count;
    }
    
    /**
     * Get the item type in this stack.
     * 
     * @return The item
     */
    public Item getItem() {
        return item;
    }
    
    /**
     * Get the number of items in this stack.
     * 
     * @return The count
     */
    public int getCount() {
        return count;
    }
    
    /**
     * Set the number of items in this stack.
     * Count must be between 1 and the item's max stack size.
     * 
     * @param count New count
     * @throws IllegalArgumentException if count is invalid
     */
    public void setCount(int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("Count must be positive");
        }
        if (count > item.getMaxStackSize()) {
            throw new IllegalArgumentException("Count " + count + " exceeds max stack size " + item.getMaxStackSize());
        }
        this.count = count;
    }
    
    /**
     * Increase the count by the specified amount.
     * The count will not exceed the item's max stack size.
     * 
     * @param amount Amount to add
     * @return The actual amount added (may be less if we hit max stack size)
     */
    public int grow(int amount) {
        int newCount = Math.min(count + amount, item.getMaxStackSize());
        int actualAdded = newCount - count;
        count = newCount;
        return actualAdded;
    }
    
    /**
     * Decrease the count by the specified amount.
     * 
     * @param amount Amount to remove
     * @throws IllegalArgumentException if the amount would make count <= 0
     */
    public void shrink(int amount) {
        if (count - amount <= 0) {
            throw new IllegalArgumentException("Cannot shrink below 1. Use isEmpty() to check if stack should be removed.");
        }
        count -= amount;
    }
    
    /**
     * Check if this stack is empty (count is 0 or less).
     * This should not normally happen, but is here for safety.
     * 
     * @return true if the stack is empty
     */
    public boolean isEmpty() {
        return count <= 0;
    }
    
    /**
     * Check if this stack is full (count equals max stack size).
     * 
     * @return true if the stack is full
     */
    public boolean isFull() {
        return count >= item.getMaxStackSize();
    }
    
    /**
     * Check if this stack can merge with another stack.
     * Two stacks can merge if they have the same item type and this stack isn't full.
     * 
     * @param other The other stack
     * @return true if the stacks can be merged
     */
    public boolean canMergeWith(ItemStack other) {
        return other != null && this.item == other.item && !this.isFull();
    }
    
    /**
     * Get a copy of this item stack.
     * 
     * @return A new ItemStack with the same item and count
     */
    public ItemStack copy() {
        return new ItemStack(item, count);
    }
    
    /**
     * Get a copy of this item stack with a specific count.
     * 
     * @param count The count for the copy
     * @return A new ItemStack with the same item and specified count
     */
    public ItemStack copyWithCount(int count) {
        return new ItemStack(item, count);
    }
    
    @Override
    public String toString() {
        return count + "x " + (item.getIdentifier() != null ? item.getIdentifier() : "unknown");
    }
}
