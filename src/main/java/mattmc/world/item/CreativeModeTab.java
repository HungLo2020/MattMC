package mattmc.world.item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a creative mode tab that groups items together.
 * Similar to Minecraft's CreativeModeTab (ItemGroup in older versions).
 * 
 * Each tab contains a list of items that belong to that category,
 * and has a display name and icon item for UI rendering.
 */
public class CreativeModeTab {
    private final String id;
    private final String displayName;
    private final Item iconItem;
    private final List<Item> items;
    
    /**
     * Create a new creative mode tab.
     * 
     * @param id Unique identifier for this tab (e.g., "building_blocks")
     * @param displayName Display name shown in UI (e.g., "Building Blocks")
     * @param iconItem Item to use as the tab icon
     */
    public CreativeModeTab(String id, String displayName, Item iconItem) {
        this.id = id;
        this.displayName = displayName;
        this.iconItem = iconItem;
        this.items = new ArrayList<>();
    }
    
    /**
     * Add an item to this tab.
     * 
     * @param item The item to add
     * @return This tab for chaining
     */
    public CreativeModeTab add(Item item) {
        if (item != null && !items.contains(item)) {
            items.add(item);
        }
        return this;
    }
    
    /**
     * Get all items in this tab.
     * 
     * @return Unmodifiable list of items
     */
    public List<Item> getItems() {
        return Collections.unmodifiableList(items);
    }
    
    /**
     * Get the tab's unique identifier.
     * 
     * @return The tab ID
     */
    public String getId() {
        return id;
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
     * Get the icon item for this tab.
     * 
     * @return The icon item
     */
    public Item getIconItem() {
        return iconItem;
    }
    
    /**
     * Get the number of items in this tab.
     * 
     * @return Item count
     */
    public int size() {
        return items.size();
    }
}
