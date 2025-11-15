package mattmc.client.resources;

import mattmc.client.Minecraft;

import mattmc.client.resources.model.BlockModel;
import mattmc.client.resources.model.BlockState;
import mattmc.client.resources.model.BlockStateVariant;
import mattmc.client.resources.model.ModelElement;
import mattmc.client.resources.model.ItemModelWrapper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        
        try (InputStream is = ResourceManager.class.getResourceAsStream(resourcePath);
             Reader reader = new InputStreamReader(is)) {
            BlockModel model = GSON.fromJson(reader, BlockModel.class);
            return model;
        } catch (Exception e) {
            logger.debug("Failed to load block model: {}", resourcePath);
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
     */
    private static void resolveTextureVariables(BlockModel model) {
        if (model.getTextures() == null || model.getElements() == null) {
            return;
        }
        
        Map<String, String> textures = model.getTextures();
        
        // First, resolve any texture variables in the textures map itself
        // (e.g., "particle": "#side" should become "particle": "block/planks" if "side": "block/planks")
        Map<String, String> resolvedTextures = new HashMap<>();
        for (Map.Entry<String, String> entry : textures.entrySet()) {
            String value = entry.getValue();
            resolvedTextures.put(entry.getKey(), resolveTextureVariable(value, textures));
        }
        model.setTextures(resolvedTextures);
        
        // Now resolve texture variables in element faces
        for (ModelElement element : model.getElements()) {
            if (element.getFaces() != null) {
                for (ModelElement.ElementFace face : element.getFaces().values()) {
                    if (face.getTexture() != null && face.getTexture().startsWith("#")) {
                        String varName = face.getTexture().substring(1);
                        String resolved = resolvedTextures.get(varName);
                        if (resolved != null) {
                            face.setTexture(resolved);
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Resolve a single texture variable reference.
     * Recursively follows variable chains (e.g., #particle -> #side -> block/planks).
     */
    private static String resolveTextureVariable(String texture, Map<String, String> textures) {
        if (texture == null || !texture.startsWith("#")) {
            return texture;
        }
        
        String varName = texture.substring(1);
        String resolved = textures.get(varName);
        
        if (resolved == null) {
            return texture;  // Can't resolve, return as-is
        }
        
        // Recursively resolve if the resolved value is also a variable
        if (resolved.startsWith("#")) {
            return resolveTextureVariable(resolved, textures);
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
        try (InputStream is = ResourceManager.class.getResourceAsStream(path);
             Reader reader = new InputStreamReader(is)) {
            BlockState blockState = GSON.fromJson(reader, BlockState.class);
            BLOCKSTATE_CACHE.put(name, blockState);
            return blockState;
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
     * This handles both standard Minecraft format and custom wrapper format.
     * 
     * @param name The item model name (e.g., "diamond")
     * @return The loaded BlockModel (raw), or null if not found
     */
    private static BlockModel loadItemModelRaw(String name) {
        String path = "/assets/models/item/" + name + ".json";
        try (InputStream is = ResourceManager.class.getResourceAsStream(path);
             Reader reader = new InputStreamReader(is)) {
            
            // First, try to parse as ItemModelWrapper (custom format)
            try {
                ItemModelWrapper wrapper = GSON.fromJson(new InputStreamReader(
                    ResourceManager.class.getResourceAsStream(path)), ItemModelWrapper.class);
                
                if (wrapper != null && wrapper.getModel() != null && wrapper.getModel().getModel() != null) {
                    // This is the custom format - create a BlockModel with the referenced model as parent
                    BlockModel model = new BlockModel();
                    model.setParent(wrapper.getModel().getModel());
                    return model;
                }
            } catch (Exception e) {
                // Not the custom format, try standard format
            }
            
            // Try standard Minecraft format
            BlockModel model = GSON.fromJson(new InputStreamReader(
                ResourceManager.class.getResourceAsStream(path)), BlockModel.class);
            return model;
        } catch (Exception e) {
            logger.debug("Failed to load item model: {}", path);
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
            } else {
                // Try as item model
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
