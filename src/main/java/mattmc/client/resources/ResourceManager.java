package mattmc.client.resources;

import mattmc.client.Minecraft;

import mattmc.client.resources.model.BlockModel;
import mattmc.client.resources.model.BlockState;
import mattmc.client.resources.model.BlockStateVariant;
import mattmc.client.resources.model.ModelElement;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages loading of block models and blockstates from JSON files.
 * Similar to Minecraft's ModelBakery and ResourceManager.
 * 
 * Implements proper model resolution with:
 * - Parent model inheritance
 * - Texture variable substitution (#variable references)
 * - Namespace-aware model loading (mattmc:block/cobblestone)
 * - Recursive parent chain resolution
 */
public class ResourceManager {
    private static final Logger logger = LoggerFactory.getLogger(ResourceManager.class);
    private static final Gson GSON = new GsonBuilder().create();
    private static final Map<String, BlockModel> MODEL_CACHE = new HashMap<>();
    private static final Map<String, BlockState> BLOCKSTATE_CACHE = new HashMap<>();
    
    // Default namespace for resources
    private static final String DEFAULT_NAMESPACE = "mattmc";
    
    /**
     * Load a block model from assets/models/block/{name}.json
     * This is the raw model without parent resolution.
     * 
     * @param name The model name (e.g., "dirt" or "mattmc:block/dirt")
     * @return The loaded BlockModel (raw, without parent resolution), or null if not found
     */
    private static BlockModel loadBlockModelRaw(String name) {
        // Parse namespace and path
        String namespace = DEFAULT_NAMESPACE;
        String path = name;
        
        if (name.contains(":")) {
            String[] parts = name.split(":", 2);
            namespace = parts[0];
            path = parts[1];
        }
        
        // Remove "block/" prefix if present for file lookup
        if (path.startsWith("block/")) {
            path = path.substring(6);
        }
        
        String resourcePath = "/assets/models/block/" + path + ".json";
        
        try {
            InputStream is = ResourceManager.class.getResourceAsStream(resourcePath);
            if (is == null) {
                logger.error("Block model not found: {}", resourcePath);
                return null;
            }
            try (Reader reader = new InputStreamReader(is)) {
                BlockModel model = GSON.fromJson(reader, BlockModel.class);
                return model;
            }
        } catch (Exception e) {
            logger.error("Failed to load block model: {}", resourcePath, e);
            return null;
        }
    }
    
    /**
     * Load and resolve a block model with full parent inheritance.
     * Follows parent chain and merges textures, elements, and display properties.
     * 
     * @param name The model name (e.g., "dirt", "cobblestone", "mattmc:block/stairs")
     * @return The fully resolved BlockModel, or null if not found
     */
    public static BlockModel loadBlockModel(String name) {
        String cacheKey = "block:" + name;
        if (MODEL_CACHE.containsKey(cacheKey)) {
            return MODEL_CACHE.get(cacheKey);
        }
        
        BlockModel resolved = resolveBlockModel(name);
        MODEL_CACHE.put(cacheKey, resolved);
        return resolved;
    }
    
    /**
     * Recursively resolve a block model by following its parent chain.
     * Merges properties from parent to child (child properties override parent).
     */
    private static BlockModel resolveBlockModel(String name) {
        BlockModel model = loadBlockModelRaw(name);
        if (model == null) {
            return null;
        }
        
        // If model has a parent, resolve it and merge
        if (model.getParent() != null) {
            BlockModel parent = resolveBlockModel(model.getParent());
            if (parent != null) {
                model = mergeModels(parent, model);
            }
        }
        
        // Resolve texture variables (e.g., #side -> block/planks)
        resolveTextureVariables(model);
        
        return model;
    }
    
    /**
     * Merge parent and child models.
     * Child properties override parent properties.
     */
    private static BlockModel mergeModels(BlockModel parent, BlockModel child) {
        BlockModel merged = new BlockModel();
        
        // Preserve the child's original parent for special rendering detection
        if (child.getParent() != null) {
            merged.setOriginalParent(child.getParent());
        }
        
        // Merge textures (child textures override parent)
        Map<String, String> mergedTextures = new HashMap<>();
        if (parent.getTextures() != null) {
            mergedTextures.putAll(parent.getTextures());
        }
        if (child.getTextures() != null) {
            mergedTextures.putAll(child.getTextures());
        }
        merged.setTextures(mergedTextures.isEmpty() ? null : mergedTextures);
        
        // Child elements override parent elements
        merged.setElements(child.getElements() != null ? child.getElements() : parent.getElements());
        
        // Child display overrides parent display
        merged.setDisplay(child.getDisplay() != null ? child.getDisplay() : parent.getDisplay());
        
        // Child ambientocclusion overrides parent
        merged.setAmbientocclusion(child.getAmbientocclusion() != null ? child.getAmbientocclusion() : parent.getAmbientocclusion());
        
        // Keep child's tints
        merged.setTints(child.getTints());
        
        // Parent is now resolved, so we don't set it on merged model
        merged.setParent(null);
        
        return merged;
    }
    
    /**
     * Resolve texture variables in a model.
     * Replaces #variable references with actual texture paths.
     * 
     * Example: texture "#side" with textures map {"side": "block/planks"} becomes "block/planks"
     * 
     * NOTE: We DON'T resolve texture variables in element faces here because those elements
     * might be shared from a cached parent model. Instead, ModelElementRenderer resolves
     * texture variables on-the-fly during rendering using the model's texture map.
     */
    private static void resolveTextureVariables(BlockModel model) {
        if (model.getTextures() == null) {
            return;
        }
        
        Map<String, String> textures = model.getTextures();
        
        // Resolve any texture variables in the textures map itself
        // (e.g., "particle": "#side" should become "particle": "block/planks" if "side": "block/planks")
        Map<String, String> resolvedTextures = new HashMap<>();
        for (Map.Entry<String, String> entry : textures.entrySet()) {
            String value = entry.getValue();
            resolvedTextures.put(entry.getKey(), resolveTextureVariable(value, textures));
        }
        model.setTextures(resolvedTextures);
        
        // DO NOT modify element faces here! They might be shared from a cached parent model.
        // ModelElementRenderer will resolve texture variables on-the-fly using the model's texture map.
    }
    
    /**
     * Resolve a single texture variable reference.
     * Recursively follows variable chains (e.g., #particle -> #side -> block/planks).
     */
    private static String resolveTextureVariable(String texture, Map<String, String> textures) {
        return resolveTextureVariable(texture, textures, new HashSet<>());
    }
    
    /**
     * Resolve a single texture variable reference with circular reference protection.
     */
    private static String resolveTextureVariable(String texture, Map<String, String> textures, Set<String> visited) {
        if (texture == null || !texture.startsWith("#")) {
            return texture;
        }
        
        String varName = texture.substring(1);
        
        // Check for circular reference
        if (visited.contains(varName)) {
            System.err.println("Warning: Circular texture variable reference detected for: " + varName);
            return texture;  // Return as-is to avoid infinite recursion
        }
        
        String resolved = textures.get(varName);
        
        if (resolved == null) {
            return texture;  // Can't resolve, return as-is
        }
        
        // Add to visited set before recursing
        visited.add(varName);
        
        // Recursively resolve if the resolved value is also a variable
        if (resolved.startsWith("#")) {
            return resolveTextureVariable(resolved, textures, visited);
        }
        
        return resolved;
    }
    
    /**
     * Load a blockstate from assets/blockstates/{name}.json
     * 
     * @param name The blockstate name (e.g., "dirt")
     * @return The loaded BlockState, or null if not found
     */
    public static BlockState loadBlockState(String name) {
        if (BLOCKSTATE_CACHE.containsKey(name)) {
            return BLOCKSTATE_CACHE.get(name);
        }
        
        String path = "/assets/blockstates/" + name + ".json";
        try {
            InputStream is = ResourceManager.class.getResourceAsStream(path);
            if (is == null) {
                logger.error("Blockstate not found: {}", path);
                return null;
            }
            try (Reader reader = new InputStreamReader(is)) {
                BlockState blockState = GSON.fromJson(reader, BlockState.class);
                BLOCKSTATE_CACHE.put(name, blockState);
                return blockState;
            }
        } catch (Exception e) {
            logger.error("Failed to load blockstate: {}", path, e);
            return null;
        }
    }
    
    /**
     * Get all texture paths for a block by loading its blockstate and model.
     * 
     * @param blockName The block name (e.g., "dirt")
     * @return A map of texture keys to paths (e.g., "top" -> "assets/textures/block/grass_block_top.png"), or null if not found
     */
    public static Map<String, String> getBlockTexturePaths(String blockName) {
        // Load blockstate
        BlockState blockState = loadBlockState(blockName);
        if (blockState == null || blockState.getDefaultVariants() == null || blockState.getDefaultVariants().isEmpty()) {
            return null;
        }
        
        // Get first variant's model
        BlockStateVariant variant = blockState.getDefaultVariants().get(0);
        if (variant == null || variant.getModel() == null) {
            return null;
        }
        
        // Load and resolve the model
        String modelPath = variant.getModel();
        BlockModel model = loadBlockModel(modelPath);
        
        if (model == null || model.getTextures() == null) {
            return null;
        }
        
        // Convert all texture paths to file paths
        Map<String, String> texturePaths = new HashMap<>();
        for (Map.Entry<String, String> entry : model.getTextures().entrySet()) {
            String key = entry.getKey();
            String texturePath = entry.getValue();
            
            // Handle namespace in texture path
            if (texturePath.contains(":")) {
                // Format: "mattmc:block/cobblestone"
                String[] parts = texturePath.split(":", 2);
                texturePath = parts[1];  // Use just the path part
            }
            
            // Convert resource path to file path (e.g., "block/dirt" -> "assets/textures/block/dirt.png")
            texturePaths.put(key, "assets/textures/" + texturePath + ".png");
        }
        
        return texturePaths;
    }
    
    /**
     * Get the texture path for a block by loading its blockstate and model.
     * 
     * @param blockName The block name (e.g., "dirt")
     * @return The texture path (e.g., "assets/textures/block/dirt.png"), or null if not found
     */
    public static String getBlockTexturePath(String blockName) {
        Map<String, String> paths = getBlockTexturePaths(blockName);
        if (paths == null || paths.isEmpty()) {
            return null;
        }
        
        // Get texture path (try "all" first, common for cube_all models)
        String texturePath = paths.get("all");
        if (texturePath == null) {
            // Try other common keys
            texturePath = paths.get("texture");
        }
        
        if (texturePath == null) {
            // Return first available texture
            texturePath = paths.values().iterator().next();
        }
        
        return texturePath;
    }
    
    /**
     * Load an item model from assets/models/item/{name}.json
     * Uses standard Minecraft format with "parent" property.
     * 
     * @param name The item model name (e.g., "diamond")
     * @return The loaded BlockModel (raw), or null if not found
     */
    private static BlockModel loadItemModelRaw(String name) {
        String path = "/assets/models/item/" + name + ".json";
        try {
            InputStream is = ResourceManager.class.getResourceAsStream(path);
            if (is == null) {
                logger.error("Item model not found: {}", path);
                return null;
            }
            try (Reader reader = new InputStreamReader(is)) {
                BlockModel model = GSON.fromJson(reader, BlockModel.class);
                return model;
            }
        } catch (Exception e) {
            logger.error("Failed to load item model: {}", path, e);
            return null;
        }
    }
    
    /**
     * Load and resolve an item model with full parent inheritance.
     * 
     * @param name The item model name (e.g., "diamond")
     * @return The fully resolved BlockModel, or null if not found
     */
    public static BlockModel loadItemModel(String name) {
        String cacheKey = "item:" + name;
        if (MODEL_CACHE.containsKey(cacheKey)) {
            return MODEL_CACHE.get(cacheKey);
        }
        
        BlockModel resolved = resolveItemModel(name);
        MODEL_CACHE.put(cacheKey, resolved);
        return resolved;
    }
    
    /**
     * Resolve an item model by following parent references.
     * If the item model has a parent, load and merge with parent model.
     * 
     * @param itemName The item name (e.g., "grass_block")
     * @return The resolved BlockModel with all textures, or null if not found
     */
    public static BlockModel resolveItemModel(String itemName) {
        BlockModel itemModel = loadItemModelRaw(itemName);
        if (itemModel == null) {
            return null;
        }
        
        // If the item model has a parent, resolve it
        if (itemModel.getParent() != null) {
            String parentPath = itemModel.getParent();
            BlockModel parentModel = null;
            
            // Check if parent is a block model (e.g., "block/grass_block" or "mattmc:block/stairs")
            if (parentPath.startsWith("block/") || parentPath.contains(":block/")) {
                parentModel = loadBlockModel(parentPath);
            } else if (parentPath.startsWith("item/")) {
                // Parent is an item model with "item/" prefix (e.g., "item/generated")
                // Strip the "item/" prefix since loadItemModelRaw already adds it
                String itemModelName = parentPath.substring(5); // Remove "item/" prefix
                parentModel = resolveItemModel(itemModelName);
            } else if (parentPath.contains(":item/")) {
                // Parent is an item model with namespace (e.g., "mattmc:item/generated")
                // Strip the namespace and "item/" prefix
                String[] parts = parentPath.split(":item/", 2);
                if (parts.length == 2) {
                    parentModel = resolveItemModel(parts[1]);
                }
            } else {
                // Try as item model without prefix
                parentModel = resolveItemModel(parentPath);
            }
            
            if (parentModel != null) {
                itemModel = mergeModels(parentModel, itemModel);
            }
        }
        
        // Resolve texture variables
        resolveTextureVariables(itemModel);
        
        return itemModel;
    }
    
    /**
     * Get all texture paths for an item by loading its item model.
     * 
     * @param itemName The item name (e.g., "grass_block")
     * @return A map of texture keys to paths, or null if not found
     */
    public static Map<String, String> getItemTexturePaths(String itemName) {
        BlockModel model = loadItemModel(itemName);
        if (model == null || model.getTextures() == null) {
            return null;
        }
        
        // Convert all texture paths to file paths
        Map<String, String> texturePaths = new HashMap<>();
        for (Map.Entry<String, String> entry : model.getTextures().entrySet()) {
            String key = entry.getKey();
            String texturePath = entry.getValue();
            
            // Handle namespace in texture path
            if (texturePath.contains(":")) {
                // Format: "mattmc:block/cobblestone"
                String[] parts = texturePath.split(":", 2);
                texturePath = parts[1];  // Use just the path part
            }
            
            // Convert resource path to file path (e.g., "block/dirt" -> "assets/textures/block/dirt.png")
            texturePaths.put(key, "assets/textures/" + texturePath + ".png");
        }
        
        return texturePaths;
    }
    
    /**
     * Clear all cached models and blockstates.
     */
    public static void clearCache() {
        MODEL_CACHE.clear();
        BLOCKSTATE_CACHE.clear();
    }
}
