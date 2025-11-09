package mattmc.world.item;

import mattmc.world.level.block.Block;
import mattmc.world.level.block.Blocks;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Central registry for all items in the game.
 * Similar to Minecraft's Items class where items are registered with resource location identifiers.
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

    // Static item instances - similar to Minecraft's public static final Item fields
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
    
    // Basic materials (stackable, 64 max)
    public static final Item STICK = register("stick", new Item(64));
    public static final Item COAL = register("coal", new Item(64));
    public static final Item IRON_INGOT = register("iron_ingot", new Item(64));
    public static final Item GOLD_INGOT = register("gold_ingot", new Item(64));
    public static final Item DIAMOND = register("diamond", new Item(64));
    
    // Tools (non-stackable, 1 max - similar to Minecraft tools)
    public static final Item WOODEN_PICKAXE = register("wooden_pickaxe", new Item(1));
    public static final Item STONE_PICKAXE = register("stone_pickaxe", new Item(1));
    public static final Item IRON_PICKAXE = register("iron_pickaxe", new Item(1));
    public static final Item DIAMOND_PICKAXE = register("diamond_pickaxe", new Item(1));
    
    public static final Item WOODEN_AXE = register("wooden_axe", new Item(1));
    public static final Item STONE_AXE = register("stone_axe", new Item(1));
    public static final Item IRON_AXE = register("iron_axe", new Item(1));
    public static final Item DIAMOND_AXE = register("diamond_axe", new Item(1));
    
    public static final Item WOODEN_SHOVEL = register("wooden_shovel", new Item(1));
    public static final Item STONE_SHOVEL = register("stone_shovel", new Item(1));
    public static final Item IRON_SHOVEL = register("iron_shovel", new Item(1));
    public static final Item DIAMOND_SHOVEL = register("diamond_shovel", new Item(1));
    
    /**
     * Initialize creative tabs and assign items to them.
     * This method should be called after all items are registered.
     */
    static {
        // Building Blocks tab
        CreativeTabs.BUILDING_BLOCKS.addItem(STONE);
        CreativeTabs.BUILDING_BLOCKS.addItem(GRASS_BLOCK);
        CreativeTabs.BUILDING_BLOCKS.addItem(DIRT);
        CreativeTabs.BUILDING_BLOCKS.addItem(COBBLESTONE);
        CreativeTabs.BUILDING_BLOCKS.addItem(MOSSY_COBBLESTONE);
        CreativeTabs.BUILDING_BLOCKS.addItem(OAK_PLANKS);
        CreativeTabs.BUILDING_BLOCKS.addItem(SPRUCE_PLANKS);
        CreativeTabs.BUILDING_BLOCKS.addItem(BIRCH_PLANKS);
        CreativeTabs.BUILDING_BLOCKS.addItem(SILVER_BIRCH_PLANKS);
        CreativeTabs.BUILDING_BLOCKS.addItem(JUNGLE_PLANKS);
        CreativeTabs.BUILDING_BLOCKS.addItem(ACACIA_PLANKS);
        CreativeTabs.BUILDING_BLOCKS.addItem(DARK_OAK_PLANKS);
        CreativeTabs.BUILDING_BLOCKS.addItem(MANGROVE_PLANKS);
        CreativeTabs.BUILDING_BLOCKS.addItem(CHERRY_PLANKS);
        CreativeTabs.BUILDING_BLOCKS.addItem(BAMBOO_PLANKS);
        CreativeTabs.BUILDING_BLOCKS.addItem(CRIMSON_PLANKS);
        CreativeTabs.BUILDING_BLOCKS.addItem(WARPED_PLANKS);
        
        // Tools tab
        CreativeTabs.TOOLS.addItem(WOODEN_PICKAXE);
        CreativeTabs.TOOLS.addItem(STONE_PICKAXE);
        CreativeTabs.TOOLS.addItem(IRON_PICKAXE);
        CreativeTabs.TOOLS.addItem(DIAMOND_PICKAXE);
        CreativeTabs.TOOLS.addItem(WOODEN_AXE);
        CreativeTabs.TOOLS.addItem(STONE_AXE);
        CreativeTabs.TOOLS.addItem(IRON_AXE);
        CreativeTabs.TOOLS.addItem(DIAMOND_AXE);
        CreativeTabs.TOOLS.addItem(WOODEN_SHOVEL);
        CreativeTabs.TOOLS.addItem(STONE_SHOVEL);
        CreativeTabs.TOOLS.addItem(IRON_SHOVEL);
        CreativeTabs.TOOLS.addItem(DIAMOND_SHOVEL);
        
        // Ingredients tab
        CreativeTabs.INGREDIENTS.addItem(STICK);
        CreativeTabs.INGREDIENTS.addItem(COAL);
        CreativeTabs.INGREDIENTS.addItem(IRON_INGOT);
        CreativeTabs.INGREDIENTS.addItem(GOLD_INGOT);
        CreativeTabs.INGREDIENTS.addItem(DIAMOND);
    }
    
    /**
     * Register an item with a given name (without namespace).
     * Automatically adds "mattmc:" namespace prefix.
     * 
     * @param name The item name (e.g., "diamond")
     * @param item The Item instance with properties
     * @return The registered Item with identifier set
     */
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
        if (item instanceof BlockItem) {
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
        if (item instanceof BlockItem) {
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
