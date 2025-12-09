package net.minecraft.client.renderer.shaders.pack;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringReader;
import java.util.*;

/**
 * Handles dimension-specific shader configurations.
 * Based on IRIS 1.21.9 dimension parsing logic.
 */
public class DimensionConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(DimensionConfig.class);
    
    private final Map<NamespacedId, String> dimensionMap = new HashMap<>();
    private final List<String> dimensionIds = new ArrayList<>();
    
    /**
     * Load dimension configuration from a shader pack.
     * Follows IRIS parsing logic exactly.
     */
    public static DimensionConfig load(ShaderPackSource source) {
        DimensionConfig config = new DimensionConfig();
        
        try {
            Optional<String> content = source.readFile("dimension.properties");
            if (content.isPresent()) {
                config.parseDimensionProperties(content.get());
            } else {
                // Use default mappings (IRIS fallback logic)
                config.detectDefaultDimensions(source);
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to load dimension.properties, using defaults", e);
            config.detectDefaultDimensions(source);
        }
        
        return config;
    }
    
    /**
     * Parse dimension.properties file.
     * Format matches IRIS exactly:
     *   dimension.overworld=world0
     *   dimension.nether=world-1 world_nether
     * 
     * In IRIS parseDimensionMap: key (after prefix) is the folder name,
     * value is space-separated list of dimension IDs.
     */
    private void parseDimensionProperties(String content) throws IOException {
        Properties props = new Properties();
        props.load(new StringReader(content));
        
        // Parse dimension mappings (IRIS parseDimensionMap logic)
        props.forEach((keyObject, valueObject) -> {
            String key = (String) keyObject;
            String value = (String) valueObject;
            
            if (!key.startsWith("dimension.")) {
                return;
            }
            
            // Key after "dimension." is the folder name
            String folderName = key.substring("dimension.".length());
            dimensionIds.add(folderName);
            
            // Value is space-separated list of dimension IDs that map to this folder
            for (String dimId : value.split("\\s+")) {
                if (dimId.equals("*")) {
                    dimensionMap.put(new NamespacedId("*", "*"), folderName);
                }
                dimensionMap.put(new NamespacedId(dimId), folderName);
            }
            
            LOGGER.debug("Dimension mapping: {} -> {}", value, folderName);
        });
    }
    
    /**
     * Detect standard dimension folders (IRIS default detection logic).
     * Checks for world0, world-1, world1 folders.
     */
    private void detectDefaultDimensions(ShaderPackSource source) {
        // Check for world0 (overworld)
        if (source.fileExists("world0/composite.fsh") || 
            source.fileExists("world0/gbuffers_terrain.fsh")) {
            dimensionIds.add("world0");
            dimensionMap.put(DimensionId.OVERWORLD, "world0");
            dimensionMap.put(new NamespacedId("*", "*"), "world0");
            LOGGER.info("Detected world0 folder for overworld (default fallback)");
        }
        
        // Check for world-1 (nether)
        if (source.fileExists("world-1/composite.fsh") || 
            source.fileExists("world-1/gbuffers_terrain.fsh")) {
            dimensionIds.add("world-1");
            dimensionMap.put(DimensionId.NETHER, "world-1");
            LOGGER.info("Detected world-1 folder for nether");
        }
        
        // Check for world1 (end)
        if (source.fileExists("world1/composite.fsh") || 
            source.fileExists("world1/gbuffers_terrain.fsh")) {
            dimensionIds.add("world1");
            dimensionMap.put(DimensionId.END, "world1");
            LOGGER.info("Detected world1 folder for end");
        }
    }
    
    /**
     * Get dimension folder for a dimension ID.
     * Returns empty string if no mapping exists (use root shaders folder).
     * Follows IRIS lookup logic exactly.
     */
    public String getDimensionFolder(NamespacedId dimension) {
        // Try exact match first
        if (dimensionMap.containsKey(dimension)) {
            return dimensionMap.get(dimension);
        }
        
        // Try wildcard/default (IRIS fallback)
        if (dimensionMap.containsKey(new NamespacedId("*", "*"))) {
            return dimensionMap.get(new NamespacedId("*", "*"));
        }
        
        // No specific mapping, use root shaders folder
        return "";
    }
    
    /**
     * Get dimension folder for a dimension string.
     * Convenience method for string-based dimension IDs.
     */
    public String getDimensionFolder(String dimension) {
        return getDimensionFolder(new NamespacedId(dimension));
    }
    
    /**
     * Get all discovered dimension folder IDs.
     */
    public List<String> getDimensionIds() {
        return new ArrayList<>(dimensionIds);
    }
    
    /**
     * Check if pack has dimension-specific shaders.
     */
    public boolean hasDimensionSpecificShaders() {
        return !dimensionIds.isEmpty();
    }
    
    /**
     * Get the dimension map (for debugging/inspection).
     */
    public Map<NamespacedId, String> getDimensionMap() {
        return new HashMap<>(dimensionMap);
    }
}
