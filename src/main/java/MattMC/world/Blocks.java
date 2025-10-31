package MattMC.world;

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
 * BlockType dirt = Blocks.DIRT;
 * BlockType stone = Blocks.STONE;
 * 
 * // Look up blocks by identifier
 * BlockType block = Blocks.getBlock("mattmc:dirt");
 * 
 * // Get the identifier for a block
 * String id = Blocks.getIdentifier(BlockType.DIRT); // Returns "mattmc:dirt"
 * 
 * // Check if a block is registered
 * boolean exists = Blocks.isRegistered("mattmc:diamond"); // Returns false
 * 
 * // List all registered blocks
 * for (String identifier : Blocks.getRegisteredIdentifiers()) {
 *     System.out.println(identifier);
 * }
 * }</pre>
 * 
 * <p>Note: Currently, blocks are based on the BlockType enum. Future versions may support
 * dynamic block registration for modding support.</p>
 */
public class Blocks {
    
    // Default namespace for vanilla blocks
    private static final String DEFAULT_NAMESPACE = "mattmc";
    
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
     * This method is used during static initialization and assumes valid inputs.
     * 
     * @param name The block name (e.g., "dirt")
     * @param blockType The BlockType enum instance
     * @return The registered BlockType
     */
    private static BlockType register(String name, BlockType blockType) {
        if (name == null) {
            throw new NullPointerException("Block name cannot be null");
        }
        if (blockType == null) {
            throw new NullPointerException("BlockType cannot be null");
        }
        String identifier = DEFAULT_NAMESPACE + ":" + name;
        if (REGISTRY.containsKey(identifier)) {
            throw new IllegalStateException("Block with identifier '" + identifier + "' is already registered!");
        }
        if (REVERSE_REGISTRY.containsKey(blockType)) {
            throw new IllegalStateException("BlockType " + blockType + " is already registered!");
        }
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
     * @throws IllegalArgumentException if identifier is already registered or if blockType is already mapped
     * @throws NullPointerException if identifier or blockType is null
     */
    public static BlockType registerBlock(String identifier, BlockType blockType) {
        if (identifier == null) {
            throw new NullPointerException("Block identifier cannot be null");
        }
        if (blockType == null) {
            throw new NullPointerException("BlockType cannot be null");
        }
        if (REGISTRY.containsKey(identifier)) {
            throw new IllegalArgumentException("Block with identifier '" + identifier + "' is already registered!");
        }
        if (REVERSE_REGISTRY.containsKey(blockType)) {
            throw new IllegalArgumentException("BlockType " + blockType + " is already registered with identifier '" 
                + REVERSE_REGISTRY.get(blockType) + "'");
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
     * @throws NullPointerException if identifier is null
     */
    public static BlockType getBlock(String identifier) {
        if (identifier == null) {
            throw new NullPointerException("Block identifier cannot be null");
        }
        return REGISTRY.get(identifier);
    }
    
    /**
     * Get the identifier for a given BlockType.
     * 
     * @param blockType The BlockType
     * @return The identifier string (e.g., "mattmc:dirt"), or null if not registered
     * @throws NullPointerException if blockType is null
     */
    public static String getIdentifier(BlockType blockType) {
        if (blockType == null) {
            throw new NullPointerException("BlockType cannot be null");
        }
        return REVERSE_REGISTRY.get(blockType);
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
}
