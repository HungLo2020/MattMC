package mattmc.world.level.block;

import mattmc.client.Minecraft;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Central registry for all blocks in the game.
 * Similar to Minecraft's Blocks class where blocks are registered with resource location identifiers.
 * 
 * Each block is registered with a unique identifier in the format "namespace:name" (e.g., "mattmc:dirt").
 * This allows for modding and resource pack support in the future.
 * 
 * <h2>Usage Examples:</h2>
 * <pre>{@code
 * // Access blocks via static fields (recommended for vanilla blocks)
 * Block dirt = Blocks.DIRT;
 * Block stone = Blocks.STONE;
 * 
 * // Look up blocks by identifier
 * Block block = Blocks.getBlock("mattmc:dirt");
 * 
 * // Get the identifier for a block
 * String id = Blocks.DIRT.getIdentifier(); // Returns "mattmc:dirt"
 * 
 * // Check if a block is registered
 * boolean exists = Blocks.isRegistered("mattmc:diamond"); // Returns false
 * 
 * // List all registered blocks
 * for (String identifier : Blocks.getRegisteredIdentifiers()) {
 *     System.out.println(identifier);
 * }
 * }</pre>
 */
public class Blocks {
    
    // Default namespace for vanilla blocks
    private static final String DEFAULT_NAMESPACE = "mattmc";
    
    // Registry maps block identifiers to Block instances
    private static final Map<String, Block> REGISTRY = new HashMap<>();

    // Static block instances - similar to Minecraft's public static final Block fields
    // Each block is defined in one line with its properties
    // Textures are loaded from blockstate and model JSON files
    // If texture loading fails, a magenta fallback color is automatically used
    public static final Block AIR = register("air", new Block(false));
    public static final Block GRASS_BLOCK = register("grass_block", new Block(true));
    public static final Block DIRT = register("dirt", new Block(true));
    public static final Block STONE = register("stone", new Block(true));
    public static final Block COBBLESTONE = register("cobblestone", new Block(true));
    public static final Block MOSSY_COBBLESTONE = register("mossy_cobblestone", new Block(true));
    public static final Block SILVER_BIRCH_PLANKS = register("silver_birch_planks", new Block(true));
    public static final Block OAK_PLANKS = register("oak_planks", new Block(true));
    public static final Block SPRUCE_PLANKS = register("spruce_planks", new Block(true));
    public static final Block BIRCH_PLANKS = register("birch_planks", new Block(true));
    public static final Block JUNGLE_PLANKS = register("jungle_planks", new Block(true));
    public static final Block ACACIA_PLANKS = register("acacia_planks", new Block(true));
    public static final Block DARK_OAK_PLANKS = register("dark_oak_planks", new Block(true));
    public static final Block MANGROVE_PLANKS = register("mangrove_planks", new Block(true));
    public static final Block CHERRY_PLANKS = register("cherry_planks", new Block(true, 0, 0, 0, 0));
    public static final Block BAMBOO_PLANKS = register("bamboo_planks", new Block(true, 0, 0, 0, 0));
    public static final Block CRIMSON_PLANKS = register("crimson_planks", new Block(true, 0, 0, 0, 0));
    public static final Block WARPED_PLANKS = register("warped_planks", new Block(true, 0, 0, 0, 0));
    public static final Block BIRCH_STAIRS = register("birch_stairs", new StairsBlock());
    public static final Block TORCH = register("torch", new Block(false, 14, 14, 11, 0));
    public static final Block PEARLESCENT_FROGLIGHT = register("pearlescent_froglight", new Block(false, 14, 14, 9, 12));
    public static final Block VERDANT_FROGLIGHT = register("verdant_froglight", new Block(false, 14, 11, 13, 11));
    public static final Block OCHRE_FROGLIGHT = register("ochre_froglight", new Block(false, 14, 14, 12, 7));
    public static final Block SEA_LANTERN = register("sea_lantern", new Block(false, 14, 10, 14, 14));
    
    /**
     * Register a block with a given name (without namespace).
     * Automatically adds "mattmc:" namespace prefix.
     * 
     * @param name The block name (e.g., "dirt")
     * @param block The Block instance with properties
     * @return The registered Block with identifier set
     */
    private static Block register(String name, Block block) {
        if (name == null) {
            throw new NullPointerException("Block name cannot be null");
        }
        if (block == null) {
            throw new NullPointerException("Block cannot be null");
        }
        String identifier = DEFAULT_NAMESPACE + ":" + name;
        if (REGISTRY.containsKey(identifier)) {
            throw new IllegalStateException("Block with identifier '" + identifier + "' is already registered!");
        }
        
        // Create a new block instance with the identifier set
        Block registeredBlock;
        if (block instanceof StairsBlock) {
            registeredBlock = new StairsBlock(identifier);
        } else {
            registeredBlock = new Block(block.isSolid(), 
                block.getLightEmission(),
                block.getLightEmissionR(), block.getLightEmissionG(), block.getLightEmissionB(), 
                identifier);
        }
        REGISTRY.put(identifier, registeredBlock);
        return registeredBlock;
    }
    
    /**
     * Register a block with a full identifier (including namespace).
     * Allows for future modding support where mods can use their own namespace.
     * 
     * @param identifier Full identifier (e.g., "mattmc:dirt" or "mymod:custom_block")
     * @param block The Block instance with properties
     * @return The registered Block with identifier set
     * @throws IllegalArgumentException if identifier is already registered
     * @throws NullPointerException if identifier or block is null
     */
    public static Block registerBlock(String identifier, Block block) {
        if (identifier == null) {
            throw new NullPointerException("Block identifier cannot be null");
        }
        if (block == null) {
            throw new NullPointerException("Block cannot be null");
        }
        if (REGISTRY.containsKey(identifier)) {
            throw new IllegalArgumentException("Block with identifier '" + identifier + "' is already registered!");
        }
        
        // Create a new block instance with the identifier set
        Block registeredBlock;
        if (block instanceof StairsBlock) {
            registeredBlock = new StairsBlock(identifier);
        } else {
            registeredBlock = new Block(block.isSolid(), 
                block.getLightEmission(),
                block.getLightEmissionR(), block.getLightEmissionG(), block.getLightEmissionB(), 
                identifier);
        }
        REGISTRY.put(identifier, registeredBlock);
        return registeredBlock;
    }
    
    /**
     * Get a block by its identifier.
     * 
     * @param identifier The block identifier (e.g., "mattmc:dirt")
     * @return The Block, or null if not found
     * @throws NullPointerException if identifier is null
     */
    public static Block getBlock(String identifier) {
        if (identifier == null) {
            throw new NullPointerException("Block identifier cannot be null");
        }
        return REGISTRY.get(identifier);
    }
    
    /**
     * Check if a block identifier is registered.
     * 
     * @param identifier The block identifier
     * @return true if registered, false otherwise
     * @throws NullPointerException if identifier is null
     */
    public static boolean isRegistered(String identifier) {
        if (identifier == null) {
            throw new NullPointerException("Block identifier cannot be null");
        }
        return REGISTRY.containsKey(identifier);
    }
    
    /**
     * Get all registered block identifiers.
     * Useful for debugging and listing available blocks.
     * 
     * @return An unmodifiable set of all registered identifiers
     */
    public static Set<String> getRegisteredIdentifiers() {
        return Collections.unmodifiableSet(REGISTRY.keySet());
    }
    
    /**
     * Get a block by its identifier, returning AIR if not found.
     * This is useful for deserialization where we want to handle missing blocks gracefully.
     * 
     * @param identifier The block identifier (e.g., "mattmc:dirt")
     * @return The Block, or AIR if not found
     */
    public static Block getBlockOrAir(String identifier) {
        if (identifier == null) {
            return AIR;
        }
        Block block = REGISTRY.get(identifier);
        return block != null ? block : AIR;
    }
}
