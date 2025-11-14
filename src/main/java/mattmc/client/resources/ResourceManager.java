package mattmc.client.resources;

import mattmc.client.Minecraft;

import mattmc.client.resources.model.BlockModel;
import mattmc.client.resources.model.BlockState;
import mattmc.client.resources.model.BlockStateVariant;

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
 * Similar to Minecraft's ResourceManager.
 */
public class ResourceManager {
    private static final Logger logger = LoggerFactory.getLogger(ResourceManager.class);
    private static final Gson GSON = new GsonBuilder().create();
    private static final Map<String, BlockModel> MODEL_CACHE = new HashMap<>();
    private static final Map<String, BlockState> BLOCKSTATE_CACHE = new HashMap<>();
    
    /**
     * Load a block model from assets/models/block/{name}.json
     * Also tries minecraft/models/block/{name}.json for vanilla models.
     * 
     * @param name The model name (e.g., "dirt")
     * @return The loaded BlockModel, or null if not found
     */
    public static BlockModel loadBlockModel(String name) {
        if (MODEL_CACHE.containsKey(name)) {
            return MODEL_CACHE.get(name);
        }
        
        // Try loading from assets first
        String path = "/assets/models/block/" + name + ".json";
        try (InputStream is = ResourceManager.class.getResourceAsStream(path);
             Reader reader = new InputStreamReader(is)) {
            BlockModel model = GSON.fromJson(reader, BlockModel.class);
            MODEL_CACHE.put(name, model);
            return model;
        } catch (Exception e) {
            // Try minecraft namespace
            String minecraftPath = "/minecraft/models/block/" + name + ".json";
            try (InputStream is = ResourceManager.class.getResourceAsStream(minecraftPath);
                 Reader reader = new InputStreamReader(is)) {
                BlockModel model = GSON.fromJson(reader, BlockModel.class);
                MODEL_CACHE.put(name, model);
                return model;
            } catch (Exception e2) {
                logger.error("Failed to load block model: {} and {}", path, minecraftPath);
                return null;
            }
        }
    }
    
    /**
     * Load a blockstate from assets/mattmc/blockstates/{name}.json
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
        
        // Extract model name from path (e.g., "block/dirt" -> "dirt")
        String modelPath = variant.getModel();
        String modelName = modelPath.contains("/") ? modelPath.substring(modelPath.lastIndexOf('/') + 1) : modelPath;
        
        // Load model
        BlockModel model = loadBlockModel(modelName);
        if (model == null || model.getTextures() == null) {
            return null;
        }
        
        // Convert all texture paths to file paths
        Map<String, String> texturePaths = new HashMap<>();
        for (Map.Entry<String, String> entry : model.getTextures().entrySet()) {
            String key = entry.getKey();
            String texturePath = entry.getValue();
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
     * 
     * @param name The item model name (e.g., "diamond")
     * @return The loaded BlockModel, or null if not found
     */
    public static BlockModel loadItemModel(String name) {
        String cacheKey = "item:" + name;
        if (MODEL_CACHE.containsKey(cacheKey)) {
            return MODEL_CACHE.get(cacheKey);
        }
        
        String path = "/assets/models/item/" + name + ".json";
        try (InputStream is = ResourceManager.class.getResourceAsStream(path);
             Reader reader = new InputStreamReader(is)) {
            BlockModel model = GSON.fromJson(reader, BlockModel.class);
            MODEL_CACHE.put(cacheKey, model);
            return model;
        } catch (Exception e) {
            logger.error("Failed to load item model: {}", path, e);
            return null;
        }
    }
    
    /**
     * Resolve an item model by following parent references.
     * If the item model has a parent, load and merge with parent model.
     * 
     * @param itemName The item name (e.g., "grass_block")
     * @return The resolved BlockModel with all textures, or null if not found
     */
    public static BlockModel resolveItemModel(String itemName) {
        BlockModel itemModel = loadItemModel(itemName);
        if (itemModel == null) {
            return null;
        }
        
        // If the item model has a parent, resolve it
        if (itemModel.getParent() != null) {
            String parentPath = itemModel.getParent();
            
            // Check if parent is a block model (e.g., "block/grass_block")
            if (parentPath.startsWith("block/")) {
                String blockName = parentPath.substring(6); // Remove "block/" prefix
                BlockModel blockModel = loadBlockModel(blockName);
                
                // If block model has textures, use them
                if (blockModel != null && blockModel.getTextures() != null) {
                    // Merge textures (item model textures override block model textures)
                    Map<String, String> mergedTextures = new HashMap<>(blockModel.getTextures());
                    if (itemModel.getTextures() != null) {
                        mergedTextures.putAll(itemModel.getTextures());
                    }
                    
                    // Create a new model with merged data
                    BlockModel resolved = new BlockModel();
                    resolved.setParent(blockModel.getParent());
                    resolved.setTextures(mergedTextures);
                    // Preserve tints from item model
                    resolved.setTints(itemModel.getTints());
                    return resolved;
                }
            }
        }
        
        return itemModel;
    }
    
    /**
     * Get all texture paths for an item by loading its item model.
     * 
     * @param itemName The item name (e.g., "grass_block")
     * @return A map of texture keys to paths, or null if not found
     */
    public static Map<String, String> getItemTexturePaths(String itemName) {
        BlockModel model = resolveItemModel(itemName);
        if (model == null || model.getTextures() == null) {
            return null;
        }
        
        // Convert all texture paths to file paths
        Map<String, String> texturePaths = new HashMap<>();
        for (Map.Entry<String, String> entry : model.getTextures().entrySet()) {
            String key = entry.getKey();
            String texturePath = entry.getValue();
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
    
    /**
     * Resolve a block model by following parent references and merging elements.
     * This handles models that use "parent" to inherit geometry and textures.
     * 
     * @param modelPath The model path (e.g., "block/stairs" or "minecraft:block/stairs")
     * @return The resolved BlockModel with merged elements and textures, or null if not found
     */
    public static BlockModel resolveBlockModel(String modelPath) {
        // Extract model name from path
        String modelName;
        if (modelPath.contains(":")) {
            // Handle "minecraft:block/stairs" format
            modelName = modelPath.substring(modelPath.indexOf(':') + 1);
            if (modelName.startsWith("block/")) {
                modelName = modelName.substring(6);
            }
        } else if (modelPath.startsWith("block/")) {
            // Handle "block/stairs" format
            modelName = modelPath.substring(6);
        } else {
            modelName = modelPath;
        }
        
        BlockModel model = loadBlockModel(modelName);
        if (model == null) {
            return null;
        }
        
        // If model has a parent, resolve it and merge
        if (model.getParent() != null) {
            BlockModel parent = resolveBlockModel(model.getParent());
            if (parent != null) {
                // Merge parent and child
                return mergeModels(parent, model);
            }
        }
        
        return model;
    }
    
    /**
     * Merge a parent model with a child model.
     * Child properties override parent properties.
     */
    private static BlockModel mergeModels(BlockModel parent, BlockModel child) {
        BlockModel merged = new BlockModel();
        
        // Merge textures (child overrides parent)
        Map<String, String> mergedTextures = new HashMap<>();
        if (parent.getTextures() != null) {
            mergedTextures.putAll(parent.getTextures());
        }
        if (child.getTextures() != null) {
            mergedTextures.putAll(child.getTextures());
        }
        merged.setTextures(mergedTextures);
        
        // Use child elements if present, otherwise use parent elements
        if (child.hasElements()) {
            merged.setElements(child.getElements());
        } else if (parent.hasElements()) {
            merged.setElements(parent.getElements());
        }
        
        // Use child tints if present, otherwise use parent tints
        if (child.getTints() != null) {
            merged.setTints(child.getTints());
        } else if (parent.getTints() != null) {
            merged.setTints(parent.getTints());
        }
        
        // Preserve parent reference from child
        merged.setParent(child.getParent());
        
        return merged;
    }
}
