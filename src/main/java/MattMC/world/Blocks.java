package MattMC.world;

import java.util.HashMap;
import java.util.Map;

/**
 * Central registry for all blocks in the game.
 * Similar to Minecraft's Blocks class where blocks are registered with resource location identifiers.
 * 
 * Each block is registered with a unique identifier in the format "namespace:name" (e.g., "mattmc:dirt").
 * This allows for modding and resource pack support in the future.
 */
public class Blocks {
    
    // Registry maps block identifiers to BlockType instances
    private static final Map<String, BlockType> REGISTRY = new HashMap<>();
    
    // Reverse lookup: BlockType to identifier
    private static final Map<BlockType, String> REVERSE_REGISTRY = new HashMap<>();
    
    // Static block instances - similar to Minecraft's public static final Block fields
    public static final BlockType AIR;
    public static final BlockType GRASS;
    public static final BlockType DIRT;
    public static final BlockType STONE;
    
    // Static initializer block - registers all vanilla blocks
    static {
        // Register blocks with their identifiers
        AIR = register("air", BlockType.AIR);
        GRASS = register("grass", BlockType.GRASS);
        DIRT = register("dirt", BlockType.DIRT);
        STONE = register("stone", BlockType.STONE);
    }
    
    /**
     * Register a block with a given name (without namespace).
     * Automatically adds "mattmc:" namespace prefix.
     * 
     * @param name The block name (e.g., "dirt")
     * @param blockType The BlockType enum instance
     * @return The registered BlockType
     */
    private static BlockType register(String name, BlockType blockType) {
        String identifier = "mattmc:" + name;
        REGISTRY.put(identifier, blockType);
        REVERSE_REGISTRY.put(blockType, identifier);
        return blockType;
    }
    
    /**
     * Register a block with a full identifier (including namespace).
     * Allows for future modding support where mods can use their own namespace.
     * 
     * @param identifier Full identifier (e.g., "mattmc:dirt" or "mymod:custom_block")
     * @param blockType The BlockType enum instance
     * @return The registered BlockType
     */
    public static BlockType registerBlock(String identifier, BlockType blockType) {
        if (REGISTRY.containsKey(identifier)) {
            throw new IllegalArgumentException("Block with identifier '" + identifier + "' is already registered!");
        }
        REGISTRY.put(identifier, blockType);
        REVERSE_REGISTRY.put(blockType, identifier);
        return blockType;
    }
    
    /**
     * Get a block by its identifier.
     * 
     * @param identifier The block identifier (e.g., "mattmc:dirt")
     * @return The BlockType, or null if not found
     */
    public static BlockType getBlock(String identifier) {
        return REGISTRY.get(identifier);
    }
    
    /**
     * Get the identifier for a given BlockType.
     * 
     * @param blockType The BlockType
     * @return The identifier string (e.g., "mattmc:dirt"), or null if not registered
     */
    public static String getIdentifier(BlockType blockType) {
        return REVERSE_REGISTRY.get(blockType);
    }
    
    /**
     * Check if a block identifier is registered.
     * 
     * @param identifier The block identifier
     * @return true if registered, false otherwise
     */
    public static boolean isRegistered(String identifier) {
        return REGISTRY.containsKey(identifier);
    }
    
    /**
     * Get all registered block identifiers.
     * Useful for debugging and listing available blocks.
     * 
     * @return An iterable of all registered identifiers
     */
    public static Iterable<String> getRegisteredIdentifiers() {
        return REGISTRY.keySet();
    }
}
