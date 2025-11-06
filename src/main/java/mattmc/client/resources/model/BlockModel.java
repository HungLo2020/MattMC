package mattmc.client.resources.model;

import mattmc.client.Minecraft;

import java.util.Map;

/**
 * Represents a block model JSON file.
 * Similar to Minecraft's block model format.
 * 
 * Example:
 * {
 *   "parent": "block/cube_all",
 *   "textures": {
 *     "all": "block/dirt"
 *   }
 * }
 */
public class BlockModel {
    private String parent;
    private Map<String, String> textures;
    
    public String getParent() {
        return parent;
    }
    
    public void setParent(String parent) {
        this.parent = parent;
    }
    
    public Map<String, String> getTextures() {
        return textures;
    }
    
    public void setTextures(Map<String, String> textures) {
        this.textures = textures;
    }
    
    /**
     * Get a texture for a given key (e.g., "all", "top", "side", etc.)
     */
    public String getTexture(String key) {
        return textures != null ? textures.get(key) : null;
    }
}
