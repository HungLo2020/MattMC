package mattmc.world.item;

import java.util.Map;

/**
 * Represents a single item in the game.
 * Similar to MattMC's Item class.
 * 
 * Each item has properties like max stack size and texture paths.
 * Items are registered in the Items class with unique identifiers.
 * Texture paths are loaded from item model JSON files.
 * If texture loading fails, a fallback magenta color (0xFF00FF) is used.
 */
public class Item {
    // Fallback color used when texture is missing or fails to load
    private static final int FALLBACK_COLOR = 0xFF00FF; // Magenta
    
    // Default max stack size (similar to MattMC's default of 64)
    private static final int DEFAULT_MAX_STACK_SIZE = 64;
    
    private final int maxStackSize;
    private final String identifier;
    private Map<String, String> texturePaths; // Lazily loaded from JSON
    
    /**
     * Create a new item with default stack size (64).
     */
    public Item() {
        this(DEFAULT_MAX_STACK_SIZE);
    }
    
    /**
     * Create a new item with the given max stack size.
     * Texture path will be loaded from item model JSON files.
     * 
     * @param maxStackSize Maximum number of items that can be in a single stack
     */
    public Item(int maxStackSize) {
        this.maxStackSize = maxStackSize;
        this.identifier = null; // Will be set during registration
    }
    
    /**
     * Internal constructor used during registration to set the identifier.
     */
    public Item(int maxStackSize, String identifier) {
        this.maxStackSize = maxStackSize;
        this.identifier = identifier;
    }
    
    /**
     * Get the fallback color used when texture is missing.
     * This is a magenta color (0xFF00FF) to make missing textures obvious.
     * 
     * @return The fallback color (RGB hex value)
     */
    public int getFallbackColor() {
        return FALLBACK_COLOR;
    }
    
    /**
     * Get the maximum stack size for this item.
     * 
     * @return Maximum number of items in a single stack
     */
    public int getMaxStackSize() {
        return maxStackSize;
    }
    
    /**
     * Check if this item is stackable (max stack size > 1).
     * 
     * @return true if the item can stack, false otherwise
     */
    public boolean isStackable() {
        return maxStackSize > 1;
    }
    
    /**
     * Get the texture paths for this item.
     * Lazily loads from item model JSON files on first access.
     * 
     * @return A map of texture keys to paths (e.g., "layer0" -> "assets/textures/item/diamond.png")
     */
    public Map<String, String> getTexturePaths() {
        // Note: Item texture paths are currently not implemented
        // They should be set explicitly when registering items
        // or loaded from item model JSON files similar to how blocks work
        return texturePaths;
    }
    
    /**
     * Get the texture path for this item.
     * Returns the "layer0" texture (standard for items), or the first available texture.
     * 
     * @return The texture path, or null if no texture is available
     */
    public String getTexturePath() {
        Map<String, String> paths = getTexturePaths();
        if (paths == null || paths.isEmpty()) {
            return null;
        }
        
        // Try "layer0" first (standard for items)
        String path = paths.get("layer0");
        if (path != null) {
            return path;
        }
        
        // Return first available texture
        return paths.values().iterator().next();
    }
    
    /**
     * Check if this item has a texture.
     * 
     * @return true if texture is available, false otherwise
     */
    public boolean hasTexture() {
        Map<String, String> paths = getTexturePaths();
        return paths != null && !paths.isEmpty();
    }
    
    /**
     * Get the identifier for this item.
     * 
     * @return The item identifier (e.g., "mattmc:diamond")
     */
    public String getIdentifier() {
        return identifier;
    }
    
    /**
     * Called when the player uses (right-clicks with) this item.
     * This method should be overridden by subclasses to implement custom behavior.
     * 
     * @param blockInteraction The block interaction handler for the player
     * @return true if the item was used successfully, false otherwise
     */
    public boolean onUse(mattmc.world.entity.player.BlockInteraction blockInteraction) {
        // Base implementation does nothing
        return false;
    }
}
