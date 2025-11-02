package MattMC.world;

import MattMC.resources.ResourceManager;

import java.util.Map;

/**
 * Represents a single block in the world.
 * Similar to Minecraft's Block class.
 * 
 * Each block has properties like solidity and texture paths.
 * Blocks are registered in the Blocks class with unique identifiers.
 * Texture paths are loaded from blockstate and model JSON files.
 * If texture loading fails, a fallback magenta color (0xFF00FF) is used.
 */
public final class Block {
    // Fallback color used when texture is missing or fails to load
    private static final int FALLBACK_COLOR = 0xFF00FF; // Magenta
    
    private final boolean solid;
    private final String identifier;
    private Map<String, String> texturePaths; // Lazily loaded from JSON (top, bottom, side, overlay, etc.)
    
    /**
     * Create a new block with the given properties.
     * Texture path will be loaded from blockstate/model JSON files.
     * 
     * @param solid Whether the block is solid (has collision)
     */
    public Block(boolean solid) {
        this.solid = solid;
        this.identifier = null; // Will be set during registration
    }
    
    /**
     * Internal constructor used during registration to set the identifier.
     */
    Block(boolean solid, String identifier) {
        this.solid = solid;
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
    
    public boolean isSolid() {
        return solid;
    }
    
    /**
     * Get the texture paths for this block.
     * Lazily loads from blockstate/model JSON files on first access.
     * 
     * @return A map of texture keys to paths (e.g., "top" -> "assets/textures/block/grass_block_top.png")
     */
    public Map<String, String> getTexturePaths() {
        if (texturePaths == null && identifier != null) {
            // Extract block name from identifier (e.g., "mattmc:dirt" -> "dirt")
            String blockName = identifier.contains(":") ? identifier.substring(identifier.indexOf(':') + 1) : identifier;
            texturePaths = ResourceManager.getBlockTexturePaths(blockName);
        }
        return texturePaths;
    }
    
    /**
     * Get the texture path for a specific face.
     * Falls back to "all" texture if face-specific texture is not available.
     * 
     * @param face The face name (e.g., "top", "bottom", "side", "north", etc.)
     * @return The texture path, or null if no texture is available
     */
    public String getTexturePath(String face) {
        Map<String, String> paths = getTexturePaths();
        if (paths == null || paths.isEmpty()) {
            return null;
        }
        
        // Try to get face-specific texture
        String path = paths.get(face);
        if (path != null) {
            return path;
        }
        
        // Fall back to "all" texture (for simple cube_all models)
        return paths.get("all");
    }
    
    /**
     * Get the texture path for this block (backward compatibility).
     * Returns the "all" texture, or the first available texture.
     * 
     * @return The texture path, or null if no texture is available
     */
    public String getTexturePath() {
        Map<String, String> paths = getTexturePaths();
        if (paths == null || paths.isEmpty()) {
            return null;
        }
        
        // Try "all" first (for simple blocks)
        String path = paths.get("all");
        if (path != null) {
            return path;
        }
        
        // Return first available texture
        return paths.values().iterator().next();
    }
    
    public boolean hasTexture() {
        Map<String, String> paths = getTexturePaths();
        return paths != null && !paths.isEmpty();
    }
    
    public String getIdentifier() {
        return identifier;
    }
    
    public boolean isAir() {
        return this == Blocks.AIR;
    }
}

