package mattmc.client.renderer.model;

import mattmc.client.resources.ResourceManager;
import mattmc.client.resources.model.BlockModel;
import mattmc.client.resources.model.ModelDisplay;
import mattmc.client.resources.model.ModelElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bakes JSON block/item models into BakedModels ready for rendering.
 * Similar to Minecraft's ModelBakery class.
 * 
 * Converts model elements (boxes) into renderable quads with proper
 * UV mapping, normals, and colors.
 */
public class ModelBakery {
    private static final Logger logger = LoggerFactory.getLogger(ModelBakery.class);
    private static final Map<String, BakedModel> BAKED_MODEL_CACHE = new HashMap<>();
    
    /**
     * Bake a block model by name.
     * 
     * @param modelName The model name (e.g., "dirt", "cobblestone", "mattmc:block/stairs")
     * @return The baked model, or null if model not found
     */
    public static BakedModel bakeBlockModel(String modelName) {
        String cacheKey = "block:" + modelName;
        if (BAKED_MODEL_CACHE.containsKey(cacheKey)) {
            return BAKED_MODEL_CACHE.get(cacheKey);
        }
        
        // Load the resolved block model (with parent merging and texture resolution)
        BlockModel model = ResourceManager.loadBlockModel(modelName);
        if (model == null) {
            logger.warn("Failed to load block model: {}", modelName);
            return null;
        }
        
        BakedModel bakedModel = bakeModel(model);
        BAKED_MODEL_CACHE.put(cacheKey, bakedModel);
        return bakedModel;
    }
    
    /**
     * Bake an item model by name.
     * 
     * @param itemName The item name (e.g., "dirt", "diamond")
     * @return The baked model, or null if model not found
     */
    public static BakedModel bakeItemModel(String itemName) {
        String cacheKey = "item:" + itemName;
        if (BAKED_MODEL_CACHE.containsKey(cacheKey)) {
            return BAKED_MODEL_CACHE.get(cacheKey);
        }
        
        // Load the resolved item model
        BlockModel model = ResourceManager.resolveItemModel(itemName);
        if (model == null) {
            logger.warn("Failed to load item model: {}", itemName);
            return null;
        }
        
        BakedModel bakedModel = bakeModel(model);
        BAKED_MODEL_CACHE.put(cacheKey, bakedModel);
        return bakedModel;
    }
    
    /**
     * Bake a BlockModel into a BakedModel.
     */
    private static BakedModel bakeModel(BlockModel model) {
        List<BakedQuad> quads = new ArrayList<>();
        
        // Process each model element (cuboid)
        if (model.getElements() != null && !model.getElements().isEmpty()) {
            for (ModelElement element : model.getElements()) {
                quads.addAll(bakeElement(element, model));
            }
        } else if (model.getTextures() != null && model.getTextures().containsKey("layer0")) {
            // This is a generated 2D item (like torch) - create a flat quad from layer0 texture
            quads.add(generateFlatQuad(model));
        }
        
        // Get particle texture (for break particles)
        String particleTexture = model.getTexture("particle");
        if (particleTexture == null && model.getTextures() != null) {
            particleTexture = model.getTexture("layer0");
        }
        
        // Check ambient occlusion setting (default true)
        boolean hasAmbientOcclusion = model.getAmbientocclusion() == null || model.getAmbientocclusion();
        
        return new BakedModel(quads, model.getDisplay(), particleTexture, hasAmbientOcclusion);
    }
    
    /**
     * Bake a single model element into quads.
     */
    private static List<BakedQuad> bakeElement(ModelElement element, BlockModel model) {
        List<BakedQuad> quads = new ArrayList<>();
        
        if (element.getFaces() == null) {
            return quads;
        }
        
        // Get element bounds in 0-16 coordinate system
        List<Float> from = element.getFrom();
        List<Float> to = element.getTo();
        
        if (from == null || to == null || from.size() != 3 || to.size() != 3) {
            logger.warn("Invalid element bounds");
            return quads;
        }
        
        // Convert from 0-16 range to 0-1 range
        float x0 = from.get(0) / 16.0f;
        float y0 = from.get(1) / 16.0f;
        float z0 = from.get(2) / 16.0f;
        float x1 = to.get(0) / 16.0f;
        float y1 = to.get(1) / 16.0f;
        float z1 = to.get(2) / 16.0f;
        
        // Bake each face
        for (Map.Entry<String, ModelElement.ElementFace> entry : element.getFaces().entrySet()) {
            String faceDir = entry.getKey().toLowerCase();
            ModelElement.ElementFace face = entry.getValue();
            
            BakedQuad quad = bakeFace(faceDir, face, x0, y0, z0, x1, y1, z1, model);
            if (quad != null) {
                quads.add(quad);
            }
        }
        
        return quads;
    }
    
    /**
     * Bake a single face of an element into a quad.
     */
    private static BakedQuad bakeFace(String faceDir, ModelElement.ElementFace face, 
                                      float x0, float y0, float z0, float x1, float y1, float z1,
                                      BlockModel model) {
        // Get texture path
        String texturePath = face.getTexture();
        if (texturePath == null) {
            return null;
        }
        
        // Resolve texture path if needed
        texturePath = resolveTexturePath(texturePath, model);
        
        // Get UV coordinates (default to full texture if not specified)
        float u0 = 0, v0 = 0, u1 = 1, v1 = 1;
        if (face.getUv() != null && face.getUv().size() == 4) {
            u0 = face.getUv().get(0) / 16.0f;
            v0 = face.getUv().get(1) / 16.0f;
            u1 = face.getUv().get(2) / 16.0f;
            v1 = face.getUv().get(3) / 16.0f;
        }
        
        // Get tint index (-1 if not specified)
        int tintIndex = face.getTintindex() != null ? face.getTintindex() : -1;
        
        // Convert face direction to enum
        BakedQuad.Direction direction = parseFaceDirection(faceDir);
        if (direction == null) {
            logger.warn("Unknown face direction: {}", faceDir);
            return null;
        }
        
        // Build the quad vertices based on face direction
        float[] vertices = buildQuadVertices(direction, x0, y0, z0, x1, y1, z1, u0, v0, u1, v1);
        
        return new BakedQuad(vertices, tintIndex, direction, texturePath);
    }
    
    /**
     * Resolve a texture path from a variable reference or direct path.
     */
    private static String resolveTexturePath(String texturePath, BlockModel model) {
        // If it starts with #, it's a variable reference
        if (texturePath.startsWith("#") && model.getTextures() != null) {
            String varName = texturePath.substring(1);
            String resolved = model.getTextures().get(varName);
            if (resolved != null) {
                texturePath = resolved;
            }
        }
        
        // Convert resource location to file path
        // Format: "mattmc:block/dirt" or "block/dirt" -> "assets/textures/block/dirt.png"
        if (texturePath.contains(":")) {
            texturePath = texturePath.substring(texturePath.indexOf(':') + 1);
        }
        
        return "assets/textures/" + texturePath + ".png";
    }
    
    /**
     * Parse face direction string to enum.
     */
    private static BakedQuad.Direction parseFaceDirection(String faceDir) {
        return switch (faceDir) {
            case "down" -> BakedQuad.Direction.DOWN;
            case "up" -> BakedQuad.Direction.UP;
            case "north" -> BakedQuad.Direction.NORTH;
            case "south" -> BakedQuad.Direction.SOUTH;
            case "west" -> BakedQuad.Direction.WEST;
            case "east" -> BakedQuad.Direction.EAST;
            default -> null;
        };
    }
    
    /**
     * Build quad vertices for a face.
     * Vertex format: x, y, z, u, v, nx, ny, nz, r, g, b, a (12 floats per vertex, 4 vertices)
     */
    private static float[] buildQuadVertices(BakedQuad.Direction direction, 
                                             float x0, float y0, float z0, float x1, float y1, float z1,
                                             float u0, float v0, float u1, float v1) {
        float[] vertices = new float[48];  // 4 vertices * 12 floats
        
        // Get normal vector
        float nx = direction.getNormalX();
        float ny = direction.getNormalY();
        float nz = direction.getNormalZ();
        
        // Default color (white)
        float r = 1.0f, g = 1.0f, b = 1.0f, a = 1.0f;
        
        // Build vertices based on face direction
        // Vertices are ordered counter-clockwise when viewed from outside
        switch (direction) {
            case DOWN -> {
                // Bottom face (y = y0)
                addVertex(vertices, 0, x0, y0, z1, u0, v1, nx, ny, nz, r, g, b, a);
                addVertex(vertices, 12, x1, y0, z1, u1, v1, nx, ny, nz, r, g, b, a);
                addVertex(vertices, 24, x1, y0, z0, u1, v0, nx, ny, nz, r, g, b, a);
                addVertex(vertices, 36, x0, y0, z0, u0, v0, nx, ny, nz, r, g, b, a);
            }
            case UP -> {
                // Top face (y = y1)
                addVertex(vertices, 0, x0, y1, z0, u0, v0, nx, ny, nz, r, g, b, a);
                addVertex(vertices, 12, x1, y1, z0, u1, v0, nx, ny, nz, r, g, b, a);
                addVertex(vertices, 24, x1, y1, z1, u1, v1, nx, ny, nz, r, g, b, a);
                addVertex(vertices, 36, x0, y1, z1, u0, v1, nx, ny, nz, r, g, b, a);
            }
            case NORTH -> {
                // North face (z = z0)
                addVertex(vertices, 0, x1, y0, z0, u1, v1, nx, ny, nz, r, g, b, a);
                addVertex(vertices, 12, x0, y0, z0, u0, v1, nx, ny, nz, r, g, b, a);
                addVertex(vertices, 24, x0, y1, z0, u0, v0, nx, ny, nz, r, g, b, a);
                addVertex(vertices, 36, x1, y1, z0, u1, v0, nx, ny, nz, r, g, b, a);
            }
            case SOUTH -> {
                // South face (z = z1)
                addVertex(vertices, 0, x0, y0, z1, u0, v1, nx, ny, nz, r, g, b, a);
                addVertex(vertices, 12, x1, y0, z1, u1, v1, nx, ny, nz, r, g, b, a);
                addVertex(vertices, 24, x1, y1, z1, u1, v0, nx, ny, nz, r, g, b, a);
                addVertex(vertices, 36, x0, y1, z1, u0, v0, nx, ny, nz, r, g, b, a);
            }
            case WEST -> {
                // West face (x = x0)
                addVertex(vertices, 0, x0, y0, z0, u0, v1, nx, ny, nz, r, g, b, a);
                addVertex(vertices, 12, x0, y0, z1, u1, v1, nx, ny, nz, r, g, b, a);
                addVertex(vertices, 24, x0, y1, z1, u1, v0, nx, ny, nz, r, g, b, a);
                addVertex(vertices, 36, x0, y1, z0, u0, v0, nx, ny, nz, r, g, b, a);
            }
            case EAST -> {
                // East face (x = x1)
                addVertex(vertices, 0, x1, y0, z1, u0, v1, nx, ny, nz, r, g, b, a);
                addVertex(vertices, 12, x1, y0, z0, u1, v1, nx, ny, nz, r, g, b, a);
                addVertex(vertices, 24, x1, y1, z0, u1, v0, nx, ny, nz, r, g, b, a);
                addVertex(vertices, 36, x1, y1, z1, u0, v0, nx, ny, nz, r, g, b, a);
            }
        }
        
        return vertices;
    }
    
    /**
     * Add a vertex to the vertex array.
     */
    private static void addVertex(float[] vertices, int offset, float x, float y, float z, 
                                   float u, float v, float nx, float ny, float nz,
                                   float r, float g, float b, float a) {
        vertices[offset + 0] = x;
        vertices[offset + 1] = y;
        vertices[offset + 2] = z;
        vertices[offset + 3] = u;
        vertices[offset + 4] = v;
        vertices[offset + 5] = nx;
        vertices[offset + 6] = ny;
        vertices[offset + 7] = nz;
        vertices[offset + 8] = r;
        vertices[offset + 9] = g;
        vertices[offset + 10] = b;
        vertices[offset + 11] = a;
    }
    
    /**
     * Generate a flat quad for 2D items (like generated items from layer0 texture).
     * Creates a single facing-camera quad from 0,0 to 1,1 in model space.
     */
    private static BakedQuad generateFlatQuad(BlockModel model) {
        // Get the layer0 texture
        String texturePath = model.getTexture("layer0");
        if (texturePath == null) {
            texturePath = "missing";
        }
        
        // Resolve texture path
        texturePath = resolveTexturePath(texturePath, model);
        
        // Create a flat quad facing the camera (Z+ direction)
        float[] vertices = new float[48];  // 4 vertices * 12 floats
        
        // Normal pointing toward camera (0, 0, 1)
        float nx = 0, ny = 0, nz = 1;
        float r = 1, g = 1, b = 1, a = 1;
        
        // Vertex 0: bottom-left (0, 0, 0)
        addVertex(vertices, 0, 0, 0, 0, 0, 1, nx, ny, nz, r, g, b, a);
        // Vertex 1: bottom-right (1, 0, 0)
        addVertex(vertices, 12, 1, 0, 0, 1, 1, nx, ny, nz, r, g, b, a);
        // Vertex 2: top-right (1, 1, 0)
        addVertex(vertices, 24, 1, 1, 0, 1, 0, nx, ny, nz, r, g, b, a);
        // Vertex 3: top-left (0, 1, 0)
        addVertex(vertices, 36, 0, 1, 0, 0, 0, nx, ny, nz, r, g, b, a);
        
        return new BakedQuad(vertices, -1, BakedQuad.Direction.SOUTH, texturePath);
    }
    
    /**
     * Clear the baked model cache.
     */
    public static void clearCache() {
        BAKED_MODEL_CACHE.clear();
    }
}
