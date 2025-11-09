package mattmc.world.item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Registry for all creative mode tabs.
 * Similar to Minecraft's CreativeModeTabs class.
 * 
 * Tabs organize items into categories for the creative inventory.
 * This class defines all the tabs and populates them with appropriate items.
 */
public class CreativeModeTabs {
    
    private static final List<CreativeModeTab> TABS = new ArrayList<>();
    
    // Define all creative mode tabs
    public static final CreativeModeTab BUILDING_BLOCKS = register(
        new CreativeModeTab("building_blocks", "Building Blocks", Items.STONE)
    );
    
    public static final CreativeModeTab DECORATIONS = register(
        new CreativeModeTab("decorations", "Decorations", Items.OAK_PLANKS)
    );
    
    public static final CreativeModeTab REDSTONE = register(
        new CreativeModeTab("redstone", "Redstone", Items.IRON_INGOT)
    );
    
    public static final CreativeModeTab TOOLS = register(
        new CreativeModeTab("tools", "Tools & Utilities", Items.IRON_PICKAXE)
    );
    
    public static final CreativeModeTab MATERIALS = register(
        new CreativeModeTab("materials", "Materials", Items.DIAMOND)
    );
    
    /**
     * Initialize all tabs with their respective items.
     * This is called automatically during class loading.
     */
    static {
        // Building Blocks tab - all block items that are primarily used for building
        BUILDING_BLOCKS
            .add(Items.STONE)
            .add(Items.COBBLESTONE)
            .add(Items.MOSSY_COBBLESTONE)
            .add(Items.DIRT)
            .add(Items.GRASS_BLOCK);
        
        // Decorations tab - planks and decorative blocks
        DECORATIONS
            .add(Items.OAK_PLANKS)
            .add(Items.SPRUCE_PLANKS)
            .add(Items.BIRCH_PLANKS)
            .add(Items.SILVER_BIRCH_PLANKS)
            .add(Items.JUNGLE_PLANKS)
            .add(Items.ACACIA_PLANKS)
            .add(Items.DARK_OAK_PLANKS)
            .add(Items.MANGROVE_PLANKS)
            .add(Items.CHERRY_PLANKS)
            .add(Items.BAMBOO_PLANKS)
            .add(Items.CRIMSON_PLANKS)
            .add(Items.WARPED_PLANKS);
        
        // Redstone tab - currently empty but ready for future redstone items
        // (Reserved for redstone dust, repeaters, comparators, etc.)
        
        // Tools tab - all tools and utilities
        TOOLS
            .add(Items.WOODEN_PICKAXE)
            .add(Items.STONE_PICKAXE)
            .add(Items.IRON_PICKAXE)
            .add(Items.DIAMOND_PICKAXE)
            .add(Items.WOODEN_AXE)
            .add(Items.STONE_AXE)
            .add(Items.IRON_AXE)
            .add(Items.DIAMOND_AXE)
            .add(Items.WOODEN_SHOVEL)
            .add(Items.STONE_SHOVEL)
            .add(Items.IRON_SHOVEL)
            .add(Items.DIAMOND_SHOVEL);
        
        // Materials tab - raw materials and ingredients
        MATERIALS
            .add(Items.STICK)
            .add(Items.COAL)
            .add(Items.IRON_INGOT)
            .add(Items.GOLD_INGOT)
            .add(Items.DIAMOND);
    }
    
    /**
     * Register a creative mode tab.
     * 
     * @param tab The tab to register
     * @return The registered tab
     */
    private static CreativeModeTab register(CreativeModeTab tab) {
        TABS.add(tab);
        return tab;
    }
    
    /**
     * Get all registered creative mode tabs.
     * 
     * @return Unmodifiable list of all tabs
     */
    public static List<CreativeModeTab> getTabs() {
        return Collections.unmodifiableList(TABS);
    }
    
    /**
     * Get a tab by its ID.
     * 
     * @param id The tab ID
     * @return The tab, or null if not found
     */
    public static CreativeModeTab getTab(String id) {
        for (CreativeModeTab tab : TABS) {
            if (tab.getId().equals(id)) {
                return tab;
            }
        }
        return null;
    }
    
    /**
     * Get the number of registered tabs.
     * 
     * @return Tab count
     */
    public static int getTabCount() {
        return TABS.size();
    }
    
    /**
     * Get all items from all tabs in order.
     * Items are returned in tab order (all items from first tab, then second tab, etc.).
     * 
     * @return List of all items across all tabs
     */
    public static List<Item> getAllItems() {
        List<Item> allItems = new ArrayList<>();
        for (CreativeModeTab tab : TABS) {
            allItems.addAll(tab.getItems());
        }
        return allItems;
    }
    
    /**
     * Get the tab that contains a specific item.
     * 
     * @param item The item to find
     * @return The tab containing the item, or null if not found
     */
    public static CreativeModeTab getTabForItem(Item item) {
        for (CreativeModeTab tab : TABS) {
            if (tab.getItems().contains(item)) {
                return tab;
            }
        }
        return null;
    }
}
