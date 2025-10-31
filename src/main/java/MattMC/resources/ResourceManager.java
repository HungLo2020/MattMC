package MattMC.resources;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

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
    private static final Gson GSON = new GsonBuilder().create();
    private static final Map<String, BlockModel> MODEL_CACHE = new HashMap<>();
    private static final Map<String, BlockState> BLOCKSTATE_CACHE = new HashMap<>();
    
    /**
     * Load a block model from assets/mattmc/models/block/{name}.json
     * 
     * @param name The model name (e.g., "dirt")
     * @return The loaded BlockModel, or null if not found
     */
    public static BlockModel loadBlockModel(String name) {
        if (MODEL_CACHE.containsKey(name)) {
            return MODEL_CACHE.get(name);
        }
        
        String path = "/assets/mattmc/models/block/" + name + ".json";
        try (InputStream is = ResourceManager.class.getResourceAsStream(path);
             Reader reader = new InputStreamReader(is)) {
            BlockModel model = GSON.fromJson(reader, BlockModel.class);
            MODEL_CACHE.put(name, model);
            return model;
        } catch (Exception e) {
            System.err.println("Failed to load block model: " + path);
            e.printStackTrace();
            return null;
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
        
        String path = "/assets/mattmc/blockstates/" + name + ".json";
        try (InputStream is = ResourceManager.class.getResourceAsStream(path);
             Reader reader = new InputStreamReader(is)) {
            BlockState blockState = GSON.fromJson(reader, BlockState.class);
            BLOCKSTATE_CACHE.put(name, blockState);
            return blockState;
        } catch (Exception e) {
            System.err.println("Failed to load blockstate: " + path);
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Get the texture path for a block by loading its blockstate and model.
     * 
     * @param blockName The block name (e.g., "dirt")
     * @return The texture path (e.g., "assets/textures/block/dirt.png"), or null if not found
     */
    public static String getBlockTexturePath(String blockName) {
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
        
        // Get texture path (try "all" first, common for cube_all models)
        String texturePath = model.getTexture("all");
        if (texturePath == null) {
            // Try other common keys
            texturePath = model.getTexture("texture");
        }
        
        if (texturePath != null) {
            // Convert resource path to file path (e.g., "block/dirt" -> "assets/textures/block/dirt.png")
            return "assets/textures/" + texturePath + ".png";
        }
        
        return null;
    }
    
    /**
     * Clear all cached models and blockstates.
     */
    public static void clearCache() {
        MODEL_CACHE.clear();
        BLOCKSTATE_CACHE.clear();
    }
}
