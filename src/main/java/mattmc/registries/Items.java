package mattmc.registries;

import mattmc.world.item.*;

import java.util.*;

/**
 * Central registry for all items in the game.
 * Similar to MattMC's Items class where items are registered with resource location identifiers.
 * 
 * Each item is registered with a unique identifier in the format "namespace:name" (e.g., "mattmc:diamond").
 * This allows for modding and resource pack support in the future.
 * 
 * <h2>Usage Examples:</h2>
 * <pre>{@code
 * // Access items via static fields (recommended for vanilla items)
 * Item stick = Items.STICK;
 * Item diamond = Items.DIAMOND;
 * 
 * // Look up items by identifier
 * Item item = Items.getItem("mattmc:diamond");
 * 
 * // Get the identifier for an item
 * String id = Items.DIAMOND.getIdentifier(); // Returns "mattmc:diamond"
 * 
 * // Check if an item is registered
 * boolean exists = Items.isRegistered("mattmc:diamond"); // Returns true
 * 
 * // List all registered items
 * for (String identifier : Items.getRegisteredIdentifiers()) {
 *     System.out.println(identifier);
 * }
 * }</pre>
 */
public class Items {
    
    // Default namespace for vanilla items
    private static final String DEFAULT_NAMESPACE = "mattmc";
    
    // Registry maps item identifiers to Item instances
    private static final Map<String, Item> REGISTRY = new HashMap<>();

    // Static item instances - similar to MattMC's public static final Item fields
    // Each item is defined with its properties (max stack size)
    // Textures are loaded from item model JSON files
    // If texture loading fails, a magenta fallback color is automatically used
    
    // Block items (placeable blocks, stackable to 64)
    public static final BlockItem STONE = register("stone", new BlockItem(Blocks.STONE));
    public static final BlockItem DIRT = register("dirt", new BlockItem(Blocks.DIRT));
    public static final BlockItem GRASS_BLOCK = register("grass_block", new BlockItem(Blocks.GRASS_BLOCK));
    public static final BlockItem COBBLESTONE = register("cobblestone", new BlockItem(Blocks.COBBLESTONE));
    public static final BlockItem MOSSY_COBBLESTONE = register("mossy_cobblestone", new BlockItem(Blocks.MOSSY_COBBLESTONE));
    public static final BlockItem SILVER_BIRCH_PLANKS = register("silver_birch_planks", new BlockItem(Blocks.SILVER_BIRCH_PLANKS));
    public static final BlockItem OAK_PLANKS = register("oak_planks", new BlockItem(Blocks.OAK_PLANKS));
    public static final BlockItem SPRUCE_PLANKS = register("spruce_planks", new BlockItem(Blocks.SPRUCE_PLANKS));
    public static final BlockItem BIRCH_PLANKS = register("birch_planks", new BlockItem(Blocks.BIRCH_PLANKS));
    public static final BlockItem JUNGLE_PLANKS = register("jungle_planks", new BlockItem(Blocks.JUNGLE_PLANKS));
    public static final BlockItem ACACIA_PLANKS = register("acacia_planks", new BlockItem(Blocks.ACACIA_PLANKS));
    public static final BlockItem DARK_OAK_PLANKS = register("dark_oak_planks", new BlockItem(Blocks.DARK_OAK_PLANKS));
    public static final BlockItem MANGROVE_PLANKS = register("mangrove_planks", new BlockItem(Blocks.MANGROVE_PLANKS));
    public static final BlockItem CHERRY_PLANKS = register("cherry_planks", new BlockItem(Blocks.CHERRY_PLANKS));
    public static final BlockItem BAMBOO_PLANKS = register("bamboo_planks", new BlockItem(Blocks.BAMBOO_PLANKS));
    public static final BlockItem WARPED_PLANKS = register("warped_planks", new BlockItem(Blocks.WARPED_PLANKS));
    public static final BlockItem CRIMSON_PLANKS = register("crimson_planks", new BlockItem(Blocks.CRIMSON_PLANKS));
    public static final BlockItem BIRCH_STAIRS = register("birch_stairs", new BlockItem(Blocks.BIRCH_STAIRS));
    public static final BlockItem OAK_STAIRS = register("oak_stairs", new BlockItem(Blocks.OAK_STAIRS));
    public static final BlockItem SPRUCE_STAIRS = register("spruce_stairs", new BlockItem(Blocks.SPRUCE_STAIRS));
    public static final BlockItem JUNGLE_STAIRS = register("jungle_stairs", new BlockItem(Blocks.JUNGLE_STAIRS));
    public static final BlockItem ACACIA_STAIRS = register("acacia_stairs", new BlockItem(Blocks.ACACIA_STAIRS));
    public static final BlockItem DARK_OAK_STAIRS = register("dark_oak_stairs", new BlockItem(Blocks.DARK_OAK_STAIRS));
    public static final BlockItem MANGROVE_STAIRS = register("mangrove_stairs", new BlockItem(Blocks.MANGROVE_STAIRS));
    public static final BlockItem CHERRY_STAIRS = register("cherry_stairs", new BlockItem(Blocks.CHERRY_STAIRS));
    public static final BlockItem BAMBOO_STAIRS = register("bamboo_stairs", new BlockItem(Blocks.BAMBOO_STAIRS));
    public static final BlockItem CRIMSON_STAIRS = register("crimson_stairs", new BlockItem(Blocks.CRIMSON_STAIRS));
    public static final BlockItem WARPED_STAIRS = register("warped_stairs", new BlockItem(Blocks.WARPED_STAIRS));
    public static final BlockItem SILVER_BIRCH_STAIRS = register("silver_birch_stairs", new BlockItem(Blocks.SILVER_BIRCH_STAIRS));
    // Wood slabs
    public static final BlockItem OAK_SLAB = register("oak_slab", new BlockItem(Blocks.OAK_SLAB));
    public static final BlockItem SPRUCE_SLAB = register("spruce_slab", new BlockItem(Blocks.SPRUCE_SLAB));
    public static final BlockItem BIRCH_SLAB = register("birch_slab", new BlockItem(Blocks.BIRCH_SLAB));
    public static final BlockItem JUNGLE_SLAB = register("jungle_slab", new BlockItem(Blocks.JUNGLE_SLAB));
    public static final BlockItem ACACIA_SLAB = register("acacia_slab", new BlockItem(Blocks.ACACIA_SLAB));
    public static final BlockItem DARK_OAK_SLAB = register("dark_oak_slab", new BlockItem(Blocks.DARK_OAK_SLAB));
    public static final BlockItem MANGROVE_SLAB = register("mangrove_slab", new BlockItem(Blocks.MANGROVE_SLAB));
    public static final BlockItem CHERRY_SLAB = register("cherry_slab", new BlockItem(Blocks.CHERRY_SLAB));
    public static final BlockItem BAMBOO_SLAB = register("bamboo_slab", new BlockItem(Blocks.BAMBOO_SLAB));
    public static final BlockItem CRIMSON_SLAB = register("crimson_slab", new BlockItem(Blocks.CRIMSON_SLAB));
    public static final BlockItem WARPED_SLAB = register("warped_slab", new BlockItem(Blocks.WARPED_SLAB));
    public static final BlockItem SILVER_BIRCH_SLAB = register("silver_birch_slab", new BlockItem(Blocks.SILVER_BIRCH_SLAB));
    public static final Item TORCH = register("torch", new StandingAndWallBlockItem(Blocks.TORCH, Blocks.WALL_TORCH));
    public static final BlockItem PEARLESCENT_FROGLIGHT = register("pearlescent_froglight", new BlockItem(Blocks.PEARLESCENT_FROGLIGHT));
    public static final BlockItem OCHRE_FROGLIGHT = register("ochre_froglight", new BlockItem(Blocks.OCHRE_FROGLIGHT));
    public static final BlockItem VERDANT_FROGLIGHT = register("verdant_froglight", new BlockItem(Blocks.VERDANT_FROGLIGHT));
    public static final BlockItem SEA_LANTERN = register("sea_lantern", new BlockItem(Blocks.SEA_LANTERN));
    public static final BlockItem OAK_LOG = register("oak_log", new BlockItem(Blocks.OAK_LOG));
    public static final BlockItem SPRUCE_LOG = register("spruce_log", new BlockItem(Blocks.SPRUCE_LOG));
    public static final BlockItem BIRCH_LOG = register("birch_log", new BlockItem(Blocks.BIRCH_LOG));
    public static final BlockItem JUNGLE_LOG = register("jungle_log", new BlockItem(Blocks.JUNGLE_LOG));
    public static final BlockItem ACACIA_LOG = register("acacia_log", new BlockItem(Blocks.ACACIA_LOG));
    public static final BlockItem DARK_OAK_LOG = register("dark_oak_log", new BlockItem(Blocks.DARK_OAK_LOG));
    public static final BlockItem MANGROVE_LOG = register("mangrove_log", new BlockItem(Blocks.MANGROVE_LOG));
    public static final BlockItem CHERRY_LOG = register("cherry_log", new BlockItem(Blocks.CHERRY_LOG));
    public static final BlockItem BAMBOO_BLOCK = register("bamboo_block", new BlockItem(Blocks.BAMBOO_BLOCK));
    public static final BlockItem CRIMSON_STEM = register("crimson_stem", new BlockItem(Blocks.CRIMSON_STEM));
    public static final BlockItem WARPED_STEM = register("warped_stem", new BlockItem(Blocks.WARPED_STEM));
    public static final BlockItem SILVER_BIRCH_LOG = register("silver_birch_log", new BlockItem(Blocks.SILVER_BIRCH_LOG));
    // Stripped logs items
    public static final BlockItem STRIPPED_OAK_LOG = register("stripped_oak_log", new BlockItem(Blocks.STRIPPED_OAK_LOG));
    public static final BlockItem STRIPPED_SPRUCE_LOG = register("stripped_spruce_log", new BlockItem(Blocks.STRIPPED_SPRUCE_LOG));
    public static final BlockItem STRIPPED_BIRCH_LOG = register("stripped_birch_log", new BlockItem(Blocks.STRIPPED_BIRCH_LOG));
    public static final BlockItem STRIPPED_JUNGLE_LOG = register("stripped_jungle_log", new BlockItem(Blocks.STRIPPED_JUNGLE_LOG));
    public static final BlockItem STRIPPED_ACACIA_LOG = register("stripped_acacia_log", new BlockItem(Blocks.STRIPPED_ACACIA_LOG));
    public static final BlockItem STRIPPED_DARK_OAK_LOG = register("stripped_dark_oak_log", new BlockItem(Blocks.STRIPPED_DARK_OAK_LOG));
    public static final BlockItem STRIPPED_MANGROVE_LOG = register("stripped_mangrove_log", new BlockItem(Blocks.STRIPPED_MANGROVE_LOG));
    public static final BlockItem STRIPPED_CHERRY_LOG = register("stripped_cherry_log", new BlockItem(Blocks.STRIPPED_CHERRY_LOG));
    public static final BlockItem STRIPPED_BAMBOO_BLOCK = register("stripped_bamboo_block", new BlockItem(Blocks.STRIPPED_BAMBOO_BLOCK));
    public static final BlockItem STRIPPED_CRIMSON_STEM = register("stripped_crimson_stem", new BlockItem(Blocks.STRIPPED_CRIMSON_STEM));
    public static final BlockItem STRIPPED_WARPED_STEM = register("stripped_warped_stem", new BlockItem(Blocks.STRIPPED_WARPED_STEM));
    public static final BlockItem STRIPPED_PALE_OAK_LOG = register("stripped_pale_oak_log", new BlockItem(Blocks.STRIPPED_PALE_OAK_LOG));
    // Leaves items
    public static final BlockItem OAK_LEAVES = register("oak_leaves", new BlockItem(Blocks.OAK_LEAVES));
    public static final BlockItem SPRUCE_LEAVES = register("spruce_leaves", new BlockItem(Blocks.SPRUCE_LEAVES));
    public static final BlockItem BIRCH_LEAVES = register("birch_leaves", new BlockItem(Blocks.BIRCH_LEAVES));
    public static final BlockItem JUNGLE_LEAVES = register("jungle_leaves", new BlockItem(Blocks.JUNGLE_LEAVES));
    public static final BlockItem ACACIA_LEAVES = register("acacia_leaves", new BlockItem(Blocks.ACACIA_LEAVES));
    public static final BlockItem DARK_OAK_LEAVES = register("dark_oak_leaves", new BlockItem(Blocks.DARK_OAK_LEAVES));
    public static final BlockItem MANGROVE_LEAVES = register("mangrove_leaves", new BlockItem(Blocks.MANGROVE_LEAVES));
    public static final BlockItem CHERRY_LEAVES = register("cherry_leaves", new BlockItem(Blocks.CHERRY_LEAVES));
    public static final BlockItem AZALEA_LEAVES = register("azalea_leaves", new BlockItem(Blocks.AZALEA_LEAVES));
    public static final BlockItem FLOWERING_AZALEA_LEAVES = register("flowering_azalea_leaves", new BlockItem(Blocks.FLOWERING_AZALEA_LEAVES));
    public static final BlockItem PALE_OAK_LEAVES = register("pale_oak_leaves", new BlockItem(Blocks.PALE_OAK_LEAVES));

    // Basic materials (stackable, 64 max)
    //public static final Item STICK = register("stick", new Item(64));
    //public static final Item COAL = register("coal", new Item(64));
    //public static final Item IRON_INGOT = register("iron_ingot", new Item(64));
    //public static final Item GOLD_INGOT = register("gold_ingot", new Item(64));
    //public static final Item DIAMOND = register("diamond", new Item(64));
    
    // Tools (non-stackable, 1 max - similar to MattMC tools)
    //public static final Item WOODEN_PICKAXE = register("wooden_pickaxe", new Item(1));
    //public static final Item STONE_PICKAXE = register("stone_pickaxe", new Item(1));
    //public static final Item IRON_PICKAXE = register("iron_pickaxe", new Item(1));
    //public static final Item DIAMOND_PICKAXE = register("diamond_pickaxe", new Item(1));
    
    //public static final Item WOODEN_AXE = register("wooden_axe", new Item(1));
    //public static final Item STONE_AXE = register("stone_axe", new Item(1));
    //public static final Item IRON_AXE = register("iron_axe", new Item(1));
    //public static final Item DIAMOND_AXE = register("diamond_axe", new Item(1));
    
    //public static final Item WOODEN_SHOVEL = register("wooden_shovel", new Item(1));
    //public static final Item STONE_SHOVEL = register("stone_shovel", new Item(1));
    //public static final Item IRON_SHOVEL = register("iron_shovel", new Item(1));
    //public static final Item DIAMOND_SHOVEL = register("diamond_shovel", new Item(1));
    
    /**
     * Register an item with a given name (without namespace).
     * Automatically adds "mattmc:" namespace prefix.
     * 
     * @param name The item name (e.g., "diamond")
     * @param item The Item instance with properties
     * @return The registered Item with identifier set
     */
    @SuppressWarnings("unchecked") // Safe cast: we check instanceof before casting
    private static <T extends Item> T register(String name, T item) {
        if (name == null) {
            throw new NullPointerException("Item name cannot be null");
        }
        if (item == null) {
            throw new NullPointerException("Item cannot be null");
        }
        String identifier = DEFAULT_NAMESPACE + ":" + name;
        if (REGISTRY.containsKey(identifier)) {
            throw new IllegalStateException("Item with identifier '" + identifier + "' is already registered!");
        }
        
        // Create a new item instance with the identifier set
        T registeredItem;
        if (item instanceof StandingAndWallBlockItem) {
            StandingAndWallBlockItem standingAndWallItem = (StandingAndWallBlockItem) item;
            registeredItem = (T) new StandingAndWallBlockItem(
                standingAndWallItem.getStandingBlock(), 
                standingAndWallItem.getWallBlock(), 
                item.getMaxStackSize(), 
                identifier);
        } else if (item instanceof BlockItem) {
            BlockItem blockItem = (BlockItem) item;
            registeredItem = (T) new BlockItem(blockItem.getBlock(), item.getMaxStackSize(), identifier);
        } else {
            registeredItem = (T) new Item(item.getMaxStackSize(), identifier);
        }
        REGISTRY.put(identifier, registeredItem);
        return registeredItem;
    }
    
    /**
     * Register an item with a full identifier (including namespace).
     * Allows for future modding support where mods can use their own namespace.
     * 
     * @param identifier Full identifier (e.g., "mattmc:diamond" or "mymod:custom_item")
     * @param item The Item instance with properties
     * @return The registered Item with identifier set
     * @throws IllegalArgumentException if identifier is already registered
     * @throws NullPointerException if identifier or item is null
     */
    @SuppressWarnings("unchecked") // Safe cast: we check instanceof before casting
    public static <T extends Item> T registerItem(String identifier, T item) {
        if (identifier == null) {
            throw new NullPointerException("Item identifier cannot be null");
        }
        if (item == null) {
            throw new NullPointerException("Item cannot be null");
        }
        if (REGISTRY.containsKey(identifier)) {
            throw new IllegalArgumentException("Item with identifier '" + identifier + "' is already registered!");
        }
        
        // Create a new item instance with the identifier set
        T registeredItem;
        if (item instanceof StandingAndWallBlockItem) {
            StandingAndWallBlockItem standingAndWallItem = (StandingAndWallBlockItem) item;
            registeredItem = (T) new StandingAndWallBlockItem(
                standingAndWallItem.getStandingBlock(), 
                standingAndWallItem.getWallBlock(), 
                item.getMaxStackSize(), 
                identifier);
        } else if (item instanceof BlockItem) {
            BlockItem blockItem = (BlockItem) item;
            registeredItem = (T) new BlockItem(blockItem.getBlock(), item.getMaxStackSize(), identifier);
        } else {
            registeredItem = (T) new Item(item.getMaxStackSize(), identifier);
        }
        REGISTRY.put(identifier, registeredItem);
        return registeredItem;
    }
    
    /**
     * Get an item by its identifier.
     * 
     * @param identifier The item identifier (e.g., "mattmc:diamond")
     * @return The Item, or null if not found
     * @throws NullPointerException if identifier is null
     */
    public static Item getItem(String identifier) {
        if (identifier == null) {
            throw new NullPointerException("Item identifier cannot be null");
        }
        return REGISTRY.get(identifier);
    }
    
    /**
     * Check if an item identifier is registered.
     * 
     * @param identifier The item identifier
     * @return true if registered, false otherwise
     * @throws NullPointerException if identifier is null
     */
    public static boolean isRegistered(String identifier) {
        if (identifier == null) {
            throw new NullPointerException("Item identifier cannot be null");
        }
        return REGISTRY.containsKey(identifier);
    }
    
    /**
     * Get all registered item identifiers.
     * Useful for debugging and listing available items.
     * 
     * @return An unmodifiable set of all registered identifiers
     */
    public static Set<String> getRegisteredIdentifiers() {
        return Collections.unmodifiableSet(REGISTRY.keySet());
    }
}
