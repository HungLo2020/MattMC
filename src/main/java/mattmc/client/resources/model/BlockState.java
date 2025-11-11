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
     * If no default state exists, returns the first available variant.
     */
    public List<BlockStateVariant> getDefaultVariants() {
        if (variants == null || variants.isEmpty()) {
            return null;
        }
        
        // Try to get default state (empty string key)
        List<BlockStateVariant> defaultVariants = variants.get("");
        if (defaultVariants != null) {
            return defaultVariants;
        }
        
        // If no default state, return first available variant
        // This is useful for blocks like stairs that only have property-based variants
        return variants.values().iterator().next();
    }
}
