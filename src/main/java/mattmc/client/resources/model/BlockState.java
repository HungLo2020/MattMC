package mattmc.client.resources.model;

import com.google.gson.*;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Represents a blockstate JSON file.
 * Similar to MattMC's blockstate format.
 * 
 * Example:
 * {
 *   "variants": {
 *     "": { "model": "block/dirt" }
 *   }
 * }
 * 
 * or:
 * 
 * {
 *   "variants": {
 *     "facing=north": [
 *       { "model": "block/door_bottom", "y": 180 }
 *     ]
 *   }
 * }
 */
public class BlockState {
    private Map<String, Object> variants;  // Can be either BlockStateVariant or List<BlockStateVariant>
    
    // Custom deserializer will be needed to handle both single objects and arrays
    private transient Map<String, List<BlockStateVariant>> parsedVariants;
    
    public Map<String, Object> getVariants() {
        return variants;
    }
    
    public void setVariants(Map<String, Object> variants) {
        this.variants = variants;
    }
    
    /**
     * Get parsed variants as a map of state -> list of variant options.
     * Handles both single variant objects and arrays of variants.
     */
    public Map<String, List<BlockStateVariant>> getParsedVariants() {
        if (parsedVariants == null && variants != null) {
            parsedVariants = new java.util.HashMap<>();
            Gson gson = new Gson();
            
            for (Map.Entry<String, Object> entry : variants.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                
                List<BlockStateVariant> variantList = new ArrayList<>();
                
                if (value instanceof List) {
                    // Already a list - convert each element
                    for (Object item : (List<?>) value) {
                        if (item instanceof Map) {
                            BlockStateVariant variant = gson.fromJson(gson.toJsonTree(item), BlockStateVariant.class);
                            variantList.add(variant);
                        }
                    }
                } else if (value instanceof Map) {
                    // Single object - wrap in a list
                    BlockStateVariant variant = gson.fromJson(gson.toJsonTree(value), BlockStateVariant.class);
                    variantList.add(variant);
                }
                
                parsedVariants.put(key, variantList);
            }
        }
        return parsedVariants;
    }
    
    /**
     * Get variants for the default state (empty string key).
     * If no default state exists, returns the first available variant.
     */
    public List<BlockStateVariant> getDefaultVariants() {
        Map<String, List<BlockStateVariant>> parsed = getParsedVariants();
        if (parsed == null || parsed.isEmpty()) {
            return null;
        }
        
        // Try to get default state (empty string key)
        List<BlockStateVariant> defaultVariants = parsed.get("");
        if (defaultVariants != null) {
            return defaultVariants;
        }
        
        // If no default state, return first available variant
        // This is useful for blocks like stairs that only have property-based variants
        return parsed.values().iterator().next();
    }
    
    /**
     * Get variants for a specific state string (e.g., "facing=north,half=bottom").
     */
    public List<BlockStateVariant> getVariantsForState(String state) {
        Map<String, List<BlockStateVariant>> parsed = getParsedVariants();
        return parsed != null ? parsed.get(state) : null;
    }
}
