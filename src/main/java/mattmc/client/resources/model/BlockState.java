package mattmc.client.resources.model;

import mattmc.client.Minecraft;

import java.util.List;
import java.util.Map;

/**
 * Represents a blockstate JSON file.
 * Similar to Minecraft's blockstate format.
 * 
 * Example:
 * {
 *   "variants": {
 *     "": [ { "model": "block/dirt" } ]
 *   }
 * }
 */
public class BlockState {
    private Map<String, List<BlockStateVariant>> variants;
    
    public Map<String, List<BlockStateVariant>> getVariants() {
        return variants;
    }
    
    public void setVariants(Map<String, List<BlockStateVariant>> variants) {
        this.variants = variants;
    }
    
    /**
     * Get variants for the default state (empty string key).
     */
    public List<BlockStateVariant> getDefaultVariants() {
        return variants != null ? variants.get("") : null;
    }
}
