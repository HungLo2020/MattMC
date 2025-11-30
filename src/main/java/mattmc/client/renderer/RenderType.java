package mattmc.client.renderer;

import java.util.HashMap;
import java.util.Map;

/**
 * Defines how a block or item should be rendered.
 * 
 * <p>Similar to Minecraft's RenderType, this enum determines the rendering pipeline
 * used for blocks and items:
 * 
 * <ul>
 *   <li><b>SOLID:</b> Standard opaque rendering with no transparency. Used for most blocks like
 *       stone, dirt, planks, etc. Most efficient rendering mode.</li>
 *   <li><b>CUTOUT:</b> Binary transparency using alpha testing. Fragments with alpha below a
 *       threshold (typically 0.5 or 0.1) are discarded entirely. Used for blocks like saplings,
 *       flowers, tall grass, torches, rails. No texture mipmap filtering.</li>
 *   <li><b>CUTOUT_MIPPED:</b> Same as CUTOUT but with mipmap filtering enabled. Used for blocks
 *       like leaves that benefit from mipmapping at distance. Prevents "sparkling" artifacts.</li>
 *   <li><b>TRANSLUCENT:</b> Full alpha blending for smooth transparency. Used for blocks like
 *       stained glass, ice, water. Requires depth sorting for correct rendering.</li>
 * </ul>
 * 
 * <p><b>Cutout vs Translucent:</b>
 * <ul>
 *   <li>Cutout uses alpha testing (discard if alpha &lt; threshold) - no blending, no sorting needed</li>
 *   <li>Translucent uses alpha blending - requires back-to-front sorting for correct results</li>
 *   <li>Cutout is more performant and produces correct results for binary transparency</li>
 * </ul>
 * 
 * <p>The render type can be specified in block model JSON files using the "render_type" key:
 * <pre>
 * {
 *   "parent": "block/cross",
 *   "render_type": "cutout",
 *   "textures": {
 *     "cross": "block/grass"
 *   }
 * }
 * </pre>
 * 
 * @see mattmc.client.resources.model.BlockModel#getRenderType()
 */
public enum RenderType {
    /**
     * Standard opaque rendering. No transparency handling.
     * This is the default render type for most blocks.
     */
    SOLID("solid"),
    
    /**
     * Binary transparency using alpha testing without mipmapping.
     * Fragments with alpha below threshold are discarded.
     * Used for: saplings, flowers, grass, rails, torches, doors.
     */
    CUTOUT("cutout"),
    
    /**
     * Binary transparency using alpha testing with mipmapping.
     * Same as CUTOUT but texture filtering is enabled for better visuals at distance.
     * Used for: leaves, glass panes, iron bars.
     */
    CUTOUT_MIPPED("cutout_mipped"),
    
    /**
     * Full alpha blending for smooth transparency.
     * Requires back-to-front sorting for correct rendering.
     * Used for: stained glass, ice, water, slime blocks.
     */
    TRANSLUCENT("translucent");
    
    private final String jsonName;
    
    // Static lookup map for O(1) JSON name to enum conversion
    private static final Map<String, RenderType> JSON_NAME_MAP = new HashMap<>();
    
    static {
        for (RenderType type : values()) {
            JSON_NAME_MAP.put(type.jsonName, type);
        }
    }
    
    RenderType(String jsonName) {
        this.jsonName = jsonName;
    }
    
    /**
     * Get the JSON key name for this render type.
     * This is used when parsing model JSON files.
     * 
     * @return the JSON key (e.g., "solid", "cutout", "cutout_mipped", "translucent")
     */
    public String getJsonName() {
        return jsonName;
    }
    
    /**
     * Parse a render type from a JSON string value.
     * Uses O(1) HashMap lookup for performance.
     * 
     * @param value the JSON value (e.g., "cutout", "translucent")
     * @return the corresponding RenderType, or SOLID if not recognized
     */
    public static RenderType fromJson(String value) {
        if (value == null || value.isEmpty()) {
            return SOLID;
        }
        
        String normalized = value.toLowerCase();
        
        // Fast O(1) lookup using static map
        RenderType result = JSON_NAME_MAP.get(normalized);
        if (result != null) {
            return result;
        }
        
        // Handle Minecraft namespace prefix (e.g., "minecraft:cutout")
        if (normalized.contains(":")) {
            String[] parts = normalized.split(":", 2);
            if (parts.length == 2) {
                result = JSON_NAME_MAP.get(parts[1]);
                if (result != null) {
                    return result;
                }
            }
        }
        
        return SOLID;
    }
    
    /**
     * Check if this render type requires alpha testing (cutout-style rendering).
     * 
     * @return true if fragments should be discarded based on alpha threshold
     */
    public boolean isCutout() {
        return this == CUTOUT || this == CUTOUT_MIPPED;
    }
    
    /**
     * Check if this render type supports mipmapping.
     * 
     * @return true if texture mipmapping should be enabled
     */
    public boolean usesMipmaps() {
        return this != CUTOUT;  // All types except plain CUTOUT use mipmaps
    }
    
    /**
     * Check if this render type requires alpha blending.
     * 
     * @return true if full alpha blending is needed (requires depth sorting)
     */
    public boolean isTranslucent() {
        return this == TRANSLUCENT;
    }
    
    /**
     * Check if this render type has any transparency (cutout or translucent).
     * 
     * @return true if transparency handling is needed
     */
    public boolean hasTransparency() {
        return this != SOLID;
    }
    
    /**
     * Get the corresponding RenderPass for this render type.
     * 
     * <p>Maps render types to their appropriate render passes:
     * <ul>
     *   <li>SOLID → OPAQUE</li>
     *   <li>CUTOUT → CUTOUT</li>
     *   <li>CUTOUT_MIPPED → CUTOUT_MIPPED</li>
     *   <li>TRANSLUCENT → TRANSPARENT</li>
     * </ul>
     * 
     * @return the appropriate render pass
     */
    public mattmc.client.renderer.backend.RenderPass toRenderPass() {
        return switch (this) {
            case SOLID -> mattmc.client.renderer.backend.RenderPass.OPAQUE;
            case CUTOUT -> mattmc.client.renderer.backend.RenderPass.CUTOUT;
            case CUTOUT_MIPPED -> mattmc.client.renderer.backend.RenderPass.CUTOUT_MIPPED;
            case TRANSLUCENT -> mattmc.client.renderer.backend.RenderPass.TRANSPARENT;
        };
    }
}
