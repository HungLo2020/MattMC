package mattmc.client.resources.model;

import mattmc.client.renderer.RenderType;

import java.util.List;
import java.util.Map;

/**
 * Represents a block model JSON file.
 * Similar to MattMC's block model format.
 * 
 * Example:
 * {
 *   "parent": "block/cube_all",
 *   "render_type": "cutout",
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
    
    // Render type for transparency handling (solid, cutout, cutout_mipped, translucent)
    // Uses Gson field name matching to parse "render_type" from JSON
    private String render_type;
    
    // Track the original parent before merging for special rendering detection (e.g., stairs)
    private transient String originalParent;
    
    // Cached render type enum (resolved from render_type string)
    private transient RenderType cachedRenderType;
    
    public String getParent() {
        return parent;
    }
    
    public void setParent(String parent) {
        this.parent = parent;
    }
    
    public String getOriginalParent() {
        return originalParent;
    }
    
    public void setOriginalParent(String originalParent) {
        this.originalParent = originalParent;
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
    
    /**
     * Get the raw render_type string from JSON.
     * Use {@link #getRenderType()} to get the parsed enum value.
     * 
     * @return the render type string (e.g., "cutout", "translucent"), or null if not specified
     */
    public String getRenderTypeString() {
        return render_type;
    }
    
    /**
     * Set the render_type string.
     * 
     * @param renderType the render type string (e.g., "cutout", "translucent")
     */
    public void setRenderTypeString(String renderType) {
        this.render_type = renderType;
        this.cachedRenderType = null; // Clear cache
    }
    
    /**
     * Get the render type for this model.
     * 
     * <p>Returns the parsed RenderType enum from the "render_type" JSON field.
     * If not specified, returns {@link RenderType#SOLID} as the default.
     * 
     * @return the render type, never null
     */
    public RenderType getRenderType() {
        if (cachedRenderType == null) {
            cachedRenderType = RenderType.fromJson(render_type);
        }
        return cachedRenderType;
    }
    
    /**
     * Set the render type for this model.
     * 
     * @param renderType the render type to set
     */
    public void setRenderType(RenderType renderType) {
        this.cachedRenderType = renderType;
        this.render_type = renderType != null ? renderType.getJsonName() : null;
    }
    
    /**
     * Check if this model has a render type explicitly specified.
     * 
     * @return true if a render type was specified in the JSON
     */
    public boolean hasRenderType() {
        return render_type != null && !render_type.isEmpty();
    }
}
