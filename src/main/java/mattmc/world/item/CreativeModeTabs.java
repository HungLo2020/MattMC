package mattmc.world.item;

/**
 * Initializes all creative mode tabs and assigns items to them.
 * This class is separate from CreativeTabs (the registry) and handles
 * the actual population of items into tabs.
 * 
 * Similar to MattMC's CreativeModeTabs class which populates the tabs
 * defined in CreativeTabs.
 */
public class CreativeModeTabs {
    
    /**
     * Initialize all creative tabs and assign items to them.
     * This is called automatically when the class is loaded.
     */
    static {
        initializeTabs();
    }
    
    /**
     * Initialize creative tabs and assign items to them.
     * This method should be called after all items are registered.
     */
    private static void initializeTabs() {
        // Building Blocks tab
        CreativeTabs.BUILDING_BLOCKS.addItem(Items.STONE);
        CreativeTabs.BUILDING_BLOCKS.addItem(Items.GRASS_BLOCK);
        CreativeTabs.BUILDING_BLOCKS.addItem(Items.DIRT);
        CreativeTabs.BUILDING_BLOCKS.addItem(Items.COBBLESTONE);
        CreativeTabs.BUILDING_BLOCKS.addItem(Items.MOSSY_COBBLESTONE);
        CreativeTabs.BUILDING_BLOCKS.addItem(Items.OAK_LOG);
        CreativeTabs.BUILDING_BLOCKS.addItem(Items.SPRUCE_LOG);
        CreativeTabs.BUILDING_BLOCKS.addItem(Items.BIRCH_LOG);
        CreativeTabs.BUILDING_BLOCKS.addItem(Items.JUNGLE_LOG);
        CreativeTabs.BUILDING_BLOCKS.addItem(Items.ACACIA_LOG);
        CreativeTabs.BUILDING_BLOCKS.addItem(Items.DARK_OAK_LOG);
        CreativeTabs.BUILDING_BLOCKS.addItem(Items.MANGROVE_LOG);
        CreativeTabs.BUILDING_BLOCKS.addItem(Items.CHERRY_LOG);
        CreativeTabs.BUILDING_BLOCKS.addItem(Items.BAMBOO_BLOCK);
        CreativeTabs.BUILDING_BLOCKS.addItem(Items.CRIMSON_STEM);
        CreativeTabs.BUILDING_BLOCKS.addItem(Items.WARPED_STEM);
        CreativeTabs.BUILDING_BLOCKS.addItem(Items.SILVER_BIRCH_LOG);
        CreativeTabs.BUILDING_BLOCKS.addItem(Items.OAK_PLANKS);
        CreativeTabs.BUILDING_BLOCKS.addItem(Items.SPRUCE_PLANKS);
        CreativeTabs.BUILDING_BLOCKS.addItem(Items.BIRCH_PLANKS);
        CreativeTabs.BUILDING_BLOCKS.addItem(Items.SILVER_BIRCH_PLANKS);
        CreativeTabs.BUILDING_BLOCKS.addItem(Items.JUNGLE_PLANKS);
        CreativeTabs.BUILDING_BLOCKS.addItem(Items.ACACIA_PLANKS);
        CreativeTabs.BUILDING_BLOCKS.addItem(Items.DARK_OAK_PLANKS);
        CreativeTabs.BUILDING_BLOCKS.addItem(Items.MANGROVE_PLANKS);
        CreativeTabs.BUILDING_BLOCKS.addItem(Items.CHERRY_PLANKS);
        CreativeTabs.BUILDING_BLOCKS.addItem(Items.BAMBOO_PLANKS);
        CreativeTabs.BUILDING_BLOCKS.addItem(Items.CRIMSON_PLANKS);
        CreativeTabs.BUILDING_BLOCKS.addItem(Items.WARPED_PLANKS);
        // Stairs
        CreativeTabs.BUILDING_BLOCKS.addItem(Items.OAK_STAIRS);
        CreativeTabs.BUILDING_BLOCKS.addItem(Items.SPRUCE_STAIRS);
        CreativeTabs.BUILDING_BLOCKS.addItem(Items.BIRCH_STAIRS);
        CreativeTabs.BUILDING_BLOCKS.addItem(Items.JUNGLE_STAIRS);
        CreativeTabs.BUILDING_BLOCKS.addItem(Items.ACACIA_STAIRS);
        CreativeTabs.BUILDING_BLOCKS.addItem(Items.DARK_OAK_STAIRS);
        CreativeTabs.BUILDING_BLOCKS.addItem(Items.MANGROVE_STAIRS);
        CreativeTabs.BUILDING_BLOCKS.addItem(Items.CHERRY_STAIRS);
        CreativeTabs.BUILDING_BLOCKS.addItem(Items.BAMBOO_STAIRS);
        CreativeTabs.BUILDING_BLOCKS.addItem(Items.CRIMSON_STAIRS);
        CreativeTabs.BUILDING_BLOCKS.addItem(Items.WARPED_STAIRS);
        CreativeTabs.BUILDING_BLOCKS.addItem(Items.SILVER_BIRCH_STAIRS);
        // Slabs
        CreativeTabs.BUILDING_BLOCKS.addItem(Items.OAK_SLAB);
        CreativeTabs.BUILDING_BLOCKS.addItem(Items.SPRUCE_SLAB);
        CreativeTabs.BUILDING_BLOCKS.addItem(Items.BIRCH_SLAB);
        CreativeTabs.BUILDING_BLOCKS.addItem(Items.JUNGLE_SLAB);
        CreativeTabs.BUILDING_BLOCKS.addItem(Items.ACACIA_SLAB);
        CreativeTabs.BUILDING_BLOCKS.addItem(Items.DARK_OAK_SLAB);
        CreativeTabs.BUILDING_BLOCKS.addItem(Items.MANGROVE_SLAB);
        CreativeTabs.BUILDING_BLOCKS.addItem(Items.CHERRY_SLAB);
        CreativeTabs.BUILDING_BLOCKS.addItem(Items.BAMBOO_SLAB);
        CreativeTabs.BUILDING_BLOCKS.addItem(Items.CRIMSON_SLAB);
        CreativeTabs.BUILDING_BLOCKS.addItem(Items.WARPED_SLAB);
        CreativeTabs.BUILDING_BLOCKS.addItem(Items.SILVER_BIRCH_SLAB);
        CreativeTabs.BUILDING_BLOCKS.addItem(Items.TORCH);
        CreativeTabs.BUILDING_BLOCKS.addItem(Items.PEARLESCENT_FROGLIGHT);
        CreativeTabs.BUILDING_BLOCKS.addItem(Items.OCHRE_FROGLIGHT);
        CreativeTabs.BUILDING_BLOCKS.addItem(Items.VERDANT_FROGLIGHT);
        CreativeTabs.BUILDING_BLOCKS.addItem(Items.SEA_LANTERN);
        
        // Tools tab
        //CreativeTabs.TOOLS.addItem(Items.WOODEN_PICKAXE);
        //CreativeTabs.TOOLS.addItem(Items.STONE_PICKAXE);
        //CreativeTabs.TOOLS.addItem(Items.IRON_PICKAXE);
        //CreativeTabs.TOOLS.addItem(Items.DIAMOND_PICKAXE);
        //CreativeTabs.TOOLS.addItem(Items.WOODEN_AXE);
        //CreativeTabs.TOOLS.addItem(Items.STONE_AXE);
        //CreativeTabs.TOOLS.addItem(Items.IRON_AXE);
        //CreativeTabs.TOOLS.addItem(Items.DIAMOND_AXE);
        //CreativeTabs.TOOLS.addItem(Items.WOODEN_SHOVEL);
        //CreativeTabs.TOOLS.addItem(Items.STONE_SHOVEL);
        //CreativeTabs.TOOLS.addItem(Items.IRON_SHOVEL);
        //CreativeTabs.TOOLS.addItem(Items.DIAMOND_SHOVEL);
        
        // Ingredients tab
        //CreativeTabs.INGREDIENTS.addItem(Items.STICK);
        //CreativeTabs.INGREDIENTS.addItem(Items.COAL);
        //CreativeTabs.INGREDIENTS.addItem(Items.IRON_INGOT);
        //CreativeTabs.INGREDIENTS.addItem(Items.GOLD_INGOT);
        //CreativeTabs.INGREDIENTS.addItem(Items.DIAMOND);
    }
    
    /**
     * Force initialization of this class.
     * This ensures the static initializer runs and tabs are populated.
     */
    public static void init() {
        // Static initializer will run when this method is called
    }
}
