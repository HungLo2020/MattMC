package mattmc.world.item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a creative mode inventory tab.
 * Similar to MattMC's CreativeModeTab system.
 * 
 * Each tab has a name, an icon item, and a list of items that belong to it.
 * Items can be added to tabs programmatically.
 * 
 * <h2>Usage Examples:</h2>
 * <pre>{@code
 * // Create a new creative tab
 * CreativeTab buildingBlocks = new CreativeTab("building_blocks", Items.STONE);
 * 
 * // Add items to the tab
 * buildingBlocks.addItem(Items.STONE);
 * buildingBlocks.addItem(Items.DIRT);
 * 
 * // Get all items in the tab
 * List<Item> items = buildingBlocks.getItems();
 * }</pre>
 */
public class CreativeTab {
    private final String identifier;
    private final Item icon;
    private final List<Item> items;
    private final String displayName;
    
    /**
     * Create a new creative tab.
     * 
     * @param identifier The tab identifier (e.g., "building_blocks")
     * @param icon The item to use as the tab icon
     */
    public CreativeTab(String identifier, Item icon) {
        this(identifier, icon, formatDisplayName(identifier));
    }
    
    /**
     * Create a new creative tab with a custom display name.
     * 
     * @param identifier The tab identifier (e.g., "building_blocks")
     * @param icon The item to use as the tab icon
     * @param displayName The display name for the tab (e.g., "Building Blocks")
     */
    public CreativeTab(String identifier, Item icon, String displayName) {
        if (identifier == null) {
            throw new NullPointerException("Tab identifier cannot be null");
        }
        if (icon == null) {
            throw new NullPointerException("Tab icon cannot be null");
        }
        this.identifier = identifier;
        this.icon = icon;
        this.displayName = displayName != null ? displayName : formatDisplayName(identifier);
        this.items = new ArrayList<>();
    }
    
    /**
     * Format a tab identifier into a display name.
     * Converts "building_blocks" to "Building Blocks", etc.
     */
    private static String formatDisplayName(String identifier) {
        if (identifier == null || identifier.isEmpty()) {
            return "";
        }
        
        // Split by underscores and capitalize each word
        String[] words = identifier.split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.length() > 0) {
                if (result.length() > 0) {
                    result.append(" ");
                }
                result.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    result.append(word.substring(1).toLowerCase());
                }
            }
        }
        return result.toString();
    }
    
    /**
     * Add an item to this creative tab.
     * 
     * @param item The item to add
     * @return This tab instance for method chaining
     */
    public CreativeTab addItem(Item item) {
        if (item != null && !items.contains(item)) {
            items.add(item);
        }
        return this;
    }
    
    /**
     * Remove an item from this creative tab.
     * 
     * @param item The item to remove
     * @return This tab instance for method chaining
     */
    public CreativeTab removeItem(Item item) {
        items.remove(item);
        return this;
    }
    
    /**
     * Get all items in this creative tab.
     * 
     * @return An unmodifiable list of items in this tab
     */
    public List<Item> getItems() {
        return Collections.unmodifiableList(items);
    }
    
    /**
     * Get the identifier for this tab.
     * 
     * @return The tab identifier
     */
    public String getIdentifier() {
        return identifier;
    }
    
    /**
     * Get the icon item for this tab.
     * 
     * @return The tab icon
     */
    public Item getIcon() {
        return icon;
    }
    
    /**
     * Get the display name for this tab.
     * 
     * @return The display name
     */
    public String getDisplayName() {
        return displayName;
    }
    
    /**
     * Get the number of items in this tab.
     * 
     * @return The number of items
     */
    public int getItemCount() {
        return items.size();
    }
    
    /**
     * Check if this tab contains an item.
     * 
     * @param item The item to check
     * @return true if the item is in this tab, false otherwise
     */
    public boolean containsItem(Item item) {
        return items.contains(item);
    }
    
    /**
     * Clear all items from this tab.
     */
    public void clear() {
        items.clear();
    }
}
