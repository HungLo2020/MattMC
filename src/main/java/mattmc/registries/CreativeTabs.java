package mattmc.registries;

import mattmc.world.item.*;

import java.util.*;

/**
 * Central registry for all creative mode tabs.
 * Similar to MattMC's CreativeModeTabs class.
 * 
 * This class provides predefined tabs for organizing items in the creative inventory,
 * similar to how MattMC organizes items (Building Blocks, Redstone, Tools, etc.).
 * 
 * <h2>Usage Examples:</h2>
 * <pre>{@code
 * // Get a tab
 * CreativeTab buildingTab = CreativeTabs.BUILDING_BLOCKS;
 * 
 * // Add an item to a tab
 * CreativeTabs.BUILDING_BLOCKS.addItem(Items.STONE);
 * 
 * // Get all tabs
 * List<CreativeTab> allTabs = CreativeTabs.getAllTabs();
 * 
 * // Get all items across all tabs
 * List<Item> allItems = CreativeTabs.getAllItems();
 * }</pre>
 */
public class CreativeTabs {
    
    // List of all registered tabs in order
    private static final List<CreativeTab> TABS = new ArrayList<>();
    
    // Map for quick tab lookup by identifier
    private static final Map<String, CreativeTab> TAB_MAP = new HashMap<>();
    
    // Predefined creative tabs - similar to MattMC's creative tabs
    // Note: Icons will be set to appropriate items when they are registered
    
    /**
     * Building Blocks tab - contains construction blocks like stone, planks, etc.
     */
    public static final CreativeTab BUILDING_BLOCKS = register("building_blocks", null, "Building Blocks");
    
    /**
     * Decoration Blocks tab - contains decorative blocks
     */
    public static final CreativeTab DECORATION_BLOCKS = register("decoration_blocks", null, "Decoration Blocks");
    
    /**
     * Redstone tab - contains redstone-related items
     */
    public static final CreativeTab REDSTONE = register("redstone", null, "Redstone");
    
    /**
     * Transportation tab - contains rails, minecarts, boats, etc.
     */
    public static final CreativeTab TRANSPORTATION = register("transportation", null, "Transportation");
    
    /**
     * Tools tab - contains tools like pickaxes, axes, shovels, etc.
     */
    public static final CreativeTab TOOLS = register("tools", null, "Tools & Utilities");
    
    /**
     * Combat tab - contains weapons and armor
     */
    public static final CreativeTab COMBAT = register("combat", null, "Combat");
    
    /**
     * Food tab - contains food items
     */
    public static final CreativeTab FOOD = register("food", null, "Foodstuffs");
    
    /**
     * Ingredients tab - contains crafting ingredients
     */
    public static final CreativeTab INGREDIENTS = register("ingredients", null, "Ingredients");
    
    /**
     * Miscellaneous tab - contains items that don't fit other categories
     */
    public static final CreativeTab MISCELLANEOUS = register("miscellaneous", null, "Miscellaneous");
    
    // Static initializer to ensure CreativeModeTabs is initialized
    static {
        // This will trigger the static initializer in CreativeModeTabs
        // which populates all the tabs with items
        CreativeModeTabs.init();
    }
    
    /**
     * Register a creative tab.
     * 
     * @param identifier The tab identifier
     * @param icon The tab icon item (can be null initially)
     * @param displayName The display name for the tab
     * @return The registered tab
     */
    private static CreativeTab register(String identifier, Item icon, String displayName) {
        // For now, we'll create a placeholder tab without an icon
        // The icon will be set later when items are registered
        CreativeTab tab = new CreativeTab(identifier, new Item(1, "placeholder:icon"), displayName);
        TABS.add(tab);
        TAB_MAP.put(identifier, tab);
        return tab;
    }
    
    /**
     * Register a custom creative tab.
     * Allows mods to add their own tabs.
     * 
     * @param identifier The tab identifier
     * @param icon The tab icon item
     * @return The registered tab
     * @throws IllegalArgumentException if a tab with this identifier already exists
     */
    public static CreativeTab registerTab(String identifier, Item icon) {
        return registerTab(identifier, icon, null);
    }
    
    /**
     * Register a custom creative tab with a display name.
     * Allows mods to add their own tabs.
     * 
     * @param identifier The tab identifier
     * @param icon The tab icon item
     * @param displayName The display name (null to auto-generate from identifier)
     * @return The registered tab
     * @throws IllegalArgumentException if a tab with this identifier already exists
     */
    public static CreativeTab registerTab(String identifier, Item icon, String displayName) {
        if (TAB_MAP.containsKey(identifier)) {
            throw new IllegalArgumentException("Creative tab with identifier '" + identifier + "' already exists!");
        }
        
        CreativeTab tab = new CreativeTab(identifier, icon, displayName);
        TABS.add(tab);
        TAB_MAP.put(identifier, tab);
        return tab;
    }
    
    /**
     * Get a creative tab by its identifier.
     * 
     * @param identifier The tab identifier
     * @return The tab, or null if not found
     */
    public static CreativeTab getTab(String identifier) {
        return TAB_MAP.get(identifier);
    }
    
    /**
     * Get all registered creative tabs.
     * 
     * @return An unmodifiable list of all tabs in registration order
     */
    public static List<CreativeTab> getAllTabs() {
        return Collections.unmodifiableList(TABS);
    }
    
    /**
     * Get all items from all creative tabs.
     * Items are returned in tab order, with each tab's items in the order they were added.
     * Duplicate items (items in multiple tabs) will appear multiple times.
     * 
     * @return A list of all items across all tabs
     */
    public static List<Item> getAllItems() {
        List<Item> allItems = new ArrayList<>();
        for (CreativeTab tab : TABS) {
            allItems.addAll(tab.getItems());
        }
        return allItems;
    }
    
    /**
     * Get all unique items from all creative tabs.
     * Each item appears only once, even if it's in multiple tabs.
     * 
     * @return A list of all unique items across all tabs
     */
    public static List<Item> getAllUniqueItems() {
        Set<Item> uniqueItems = new LinkedHashSet<>();
        for (CreativeTab tab : TABS) {
            uniqueItems.addAll(tab.getItems());
        }
        return new ArrayList<>(uniqueItems);
    }
    
    /**
     * Find which tab(s) contain a specific item.
     * 
     * @param item The item to search for
     * @return A list of tabs containing the item (may be empty)
     */
    public static List<CreativeTab> getTabsForItem(Item item) {
        List<CreativeTab> result = new ArrayList<>();
        for (CreativeTab tab : TABS) {
            if (tab.containsItem(item)) {
                result.add(tab);
            }
        }
        return result;
    }
    
    /**
     * Get the number of registered tabs.
     * 
     * @return The number of tabs
     */
    public static int getTabCount() {
        return TABS.size();
    }
    
    /**
     * Check if a tab is registered.
     * 
     * @param identifier The tab identifier
     * @return true if the tab exists, false otherwise
     */
    public static boolean isRegistered(String identifier) {
        return TAB_MAP.containsKey(identifier);
    }
}
