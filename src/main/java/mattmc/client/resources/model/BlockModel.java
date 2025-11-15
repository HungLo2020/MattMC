package mattmc.client.resources.model;

import mattmc.client.Minecraft;

import java.util.List;
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
 *   },
 *   "elements": [...]
 * }
 */
public class BlockModel {
    private String parent;
    private Map<String, String> textures;
    private List<TintInfo> tints;
    private List<ModelElement> elements;
    private Map<String, ModelDisplay.Transform> display;
    private Boolean ambientocclusion;
    
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
    
    public List<TintInfo> getTints() {
        return tints;
    }
    
    public void setTints(List<TintInfo> tints) {
        this.tints = tints;
    }
    
    public List<ModelElement> getElements() {
        return elements;
    }
    
    public void setElements(List<ModelElement> elements) {
        this.elements = elements;
    }
    
    public Map<String, ModelDisplay.Transform> getDisplay() {
        return display;
    }
    
    public void setDisplay(Map<String, ModelDisplay.Transform> display) {
        this.display = display;
    }
    
    public Boolean getAmbientocclusion() {
        return ambientocclusion;
    }
    
    public void setAmbientocclusion(Boolean ambientocclusion) {
        this.ambientocclusion = ambientocclusion;
    }
}
