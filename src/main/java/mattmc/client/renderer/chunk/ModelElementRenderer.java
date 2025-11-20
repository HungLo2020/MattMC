package mattmc.client.renderer.chunk;

import mattmc.client.renderer.block.BlockFaceCollector;
import mattmc.client.renderer.texture.TextureAtlas;
import mattmc.client.resources.ResourceManager;
import mattmc.client.resources.model.BlockModel;
import mattmc.client.resources.model.ModelElement;
import mattmc.world.level.block.Block;
import mattmc.world.level.block.state.BlockState;
import mattmc.world.level.block.state.properties.Direction;

import java.util.List;
import java.util.Map;

/**
 * Generic renderer for block geometry defined in JSON model files.
 * This replaces specialized geometry builders (StairsGeometryBuilder, TorchGeometryBuilder)
 * with a fully data-driven approach that reads element geometry from JSON models.
 * 
 * This makes the rendering system compatible with Minecraft's model format where
 * all block geometry is defined in JSON "elements" arrays rather than hardcoded in Java.
 */
public class ModelElementRenderer {
    
    private final VertexLightSampler lightSampler;
    private final UVMapper uvMapper;
    
    /**
     * Create a model element renderer.
     */
    public ModelElementRenderer(VertexLightSampler lightSampler, UVMapper uvMapper) {
        this.lightSampler = lightSampler;
        this.uvMapper = uvMapper;
    }
    
    /**
     * Render block geometry from JSON model elements.
     * Reads the block's model file and renders all defined elements.
     * 
     * @param face The face data containing block, position, and state
     * @param vertices The vertex list to add to
     * @param indices The index list to add to
     * @param currentVertex The current vertex index (will be updated)
     * @return The new current vertex index after adding geometry
     */
    public int renderModelElements(BlockFaceCollector.FaceData face, 
                                    FloatList vertices, 
                                    IntList indices, 
                                    int currentVertex) {
        float x = face.x;
        float y = face.y;
        float z = face.z;
        Block block = face.block;
        BlockState state = face.blockState;
        
        // Load the block model from JSON
        String identifier = block.getIdentifier();
        if (identifier == null) {
            return currentVertex; // Can't load model without identifier
        }
        
        // Extract the block name from identifier (e.g., "mattmc:torch" -> "torch")
        String blockName = identifier.contains(":") ? identifier.split(":")[1] : identifier;
        BlockModel model = ResourceManager.loadBlockModel(blockName);
        
        if (model == null || model.getElements() == null) {
            return currentVertex; // No model or elements, skip rendering
        }
        
        // Get rotation from blockstate if present
        int yRotation = getYRotationFromState(block, state);
        
        // Process each element in the model
        for (ModelElement element : model.getElements()) {
            currentVertex = renderElement(face, element, model, x, y, z, yRotation, vertices, indices, currentVertex);
        }
        
        return currentVertex;
    }
    
    /**
     * Get Y-axis rotation from blockstate for rotatable blocks.
     * Returns 0 if no rotation is needed.
     */
    private int getYRotationFromState(Block block, BlockState state) {
        if (state == null) {
            return 0;
        }
        
        // Handle wall torches and similar blocks that rotate based on facing
        if (block instanceof mattmc.world.level.block.WallTorchBlock) {
            Direction facing = state.getDirection("facing");
            if (facing != null) {
                return switch (facing) {
                    case NORTH -> 270;
                    case SOUTH -> 90;
                    case WEST -> 180;
                    case EAST -> 0;
                    default -> 0;
                };
            }
        }
        
        // Handle stairs rotation
        if (block instanceof mattmc.world.level.block.StairsBlock) {
            Direction facing = state.getDirection("facing");
            if (facing != null) {
                return switch (facing) {
                    case NORTH -> 0;
                    case SOUTH -> 180;
                    case WEST -> 90;
                    case EAST -> 270;
                    default -> 0;
                };
            }
        }
        
        return 0;
    }
    
    /**
     * Render a single model element.
     * Converts from Minecraft's 0-16 coordinate system to block-relative 0-1 coordinates.
     * Applies Y-axis rotation if specified.
     */
    private int renderElement(BlockFaceCollector.FaceData face,
                              ModelElement element,
                              BlockModel model,
                              float blockX, float blockY, float blockZ,
                              int yRotation,
                              FloatList vertices,
                              IntList indices,
                              int currentVertex) {
        
        // Get element bounds in 0-16 space
        List<Float> fromList = element.getFrom();
        List<Float> toList = element.getTo();
        
        if (fromList == null || toList == null || fromList.size() < 3 || toList.size() < 3) {
            return currentVertex; // Invalid element
        }
        
        // Convert to 0-1 space (divide by 16)
        float x0 = fromList.get(0) / 16.0f;
        float y0 = fromList.get(1) / 16.0f;
        float z0 = fromList.get(2) / 16.0f;
        float x1 = toList.get(0) / 16.0f;
        float y1 = toList.get(1) / 16.0f;
        float z1 = toList.get(2) / 16.0f;
        
        // Get element faces
        Map<String, ModelElement.ElementFace> faces = element.getFaces();
        if (faces == null || faces.isEmpty()) {
            return currentVertex; // No faces to render
        }
        
        // Render each face of the element
        for (Map.Entry<String, ModelElement.ElementFace> entry : faces.entrySet()) {
            String faceDir = entry.getKey();
            ModelElement.ElementFace elementFace = entry.getValue();
            
            currentVertex = renderElementFace(face, faceDir, elementFace, model,
                                             blockX, blockY, blockZ,
                                             x0, y0, z0, x1, y1, z1,
                                             yRotation, vertices, indices, currentVertex);
        }
        
        return currentVertex;
    }
    
    /**
     * Render a single face of an element.
     */
    private int renderElementFace(BlockFaceCollector.FaceData face,
                                   String faceDirection,
                                   ModelElement.ElementFace elementFace,
                                   BlockModel model,
                                   float blockX, float blockY, float blockZ,
                                   float x0, float y0, float z0,
                                   float x1, float y1, float z1,
                                   int yRotation,
                                   FloatList vertices,
                                   IntList indices,
                                   int currentVertex) {
        
        // Get texture for this face
        String textureRef = elementFace.getTexture();
        if (textureRef == null) {
            return currentVertex; // No texture, skip
        }
        
        // Resolve texture variable (e.g., "#torch" -> "block/torch")
        // The texture might still be a variable reference, so we need to resolve it
        String texturePath = textureRef;
        if (texturePath.startsWith("#")) {
            texturePath = resolveTexture(texturePath, model);
            System.out.println("DEBUG: Resolved " + textureRef + " -> " + texturePath);
            if (texturePath == null) {
                System.err.println("ERROR: Failed to resolve texture " + textureRef + " for block at " + 
                                   face.x + "," + face.y + "," + face.z);
                System.err.println("Model textures: " + (model.getTextures() != null ? model.getTextures() : "null"));
                return currentVertex;
            }
        }
        
        // Strip namespace prefix if present (e.g., "mattmc:block/planks" -> "block/planks")
        // The texture atlas stores paths without the namespace
        if (texturePath.contains(":")) {
            String before = texturePath;
            texturePath = texturePath.substring(texturePath.indexOf(':') + 1);
            System.out.println("DEBUG: Stripped namespace " + before + " -> " + texturePath);
        }
        
        // Get UV mapping
        System.out.println("DEBUG: Looking up UV mapping for: " + texturePath);
        TextureAtlas.UVMapping uvMapping = uvMapper.getUVMappingForTexture(texturePath);
        System.out.println("DEBUG: UV mapping result: " + (uvMapping != null ? "found" : "NULL"));
        
        // Get UV coordinates from element face (in 0-16 space)
        List<Float> uvList = elementFace.getUv();
        float[] uv = new float[]{0, 0, 16, 16}; // Default to full texture
        if (uvList != null && uvList.size() >= 4) {
            uv[0] = uvList.get(0);
            uv[1] = uvList.get(1);
            uv[2] = uvList.get(2);
            uv[3] = uvList.get(3);
        }
        
        // Convert UV to 0-1 space and apply atlas mapping
        float u0, v0, u1, v1;
        if (uvMapping != null) {
            float uvWidth = uv[2] - uv[0];
            float uvHeight = uv[3] - uv[1];
            u0 = uvMapping.u0 + (uvMapping.u1 - uvMapping.u0) * (uv[0] / 16.0f);
            v0 = uvMapping.v0 + (uvMapping.v1 - uvMapping.v0) * (uv[1] / 16.0f);
            u1 = uvMapping.u0 + (uvMapping.u1 - uvMapping.u0) * (uv[2] / 16.0f);
            v1 = uvMapping.v0 + (uvMapping.v1 - uvMapping.v0) * (uv[3] / 16.0f);
        } else {
            u0 = uv[0] / 16.0f;
            v0 = uv[1] / 16.0f;
            u1 = uv[2] / 16.0f;
            v1 = uv[3] / 16.0f;
        }
        
        // Apply Y rotation to coordinates if needed
        if (yRotation != 0) {
            float[] rotated = applyYRotation(x0, z0, x1, z1, yRotation);
            x0 = rotated[0]; z0 = rotated[1]; x1 = rotated[2]; z1 = rotated[3];
        }
        
        // Add world position offset
        x0 += blockX; x1 += blockX;
        y0 += blockY; y1 += blockY;
        z0 += blockZ; z1 += blockZ;
        
        // Render the face based on direction
        currentVertex = addFaceQuad(face, faceDirection, x0, y0, z0, x1, y1, z1,
                                    u0, v0, u1, v1, vertices, indices, currentVertex);
        
        return currentVertex;
    }
    
    /**
     * Apply Y-axis rotation to element coordinates.
     */
    private float[] applyYRotation(float x0, float z0, float x1, float z1, int degrees) {
        // Rotate around center (0.5, 0.5)
        float cx0 = x0 - 0.5f, cz0 = z0 - 0.5f;
        float cx1 = x1 - 0.5f, cz1 = z1 - 0.5f;
        
        float rx0, rz0, rx1, rz1;
        switch (degrees) {
            case 90 -> {
                rx0 = -cz0; rz0 = cx0;
                rx1 = -cz1; rz1 = cx1;
            }
            case 180 -> {
                rx0 = -cx0; rz0 = -cz0;
                rx1 = -cx1; rz1 = -cz1;
            }
            case 270 -> {
                rx0 = cz0; rz0 = -cx0;
                rx1 = cz1; rz1 = -cx1;
            }
            default -> {
                return new float[]{x0, z0, x1, z1};
            }
        }
        
        return new float[]{rx0 + 0.5f, rz0 + 0.5f, rx1 + 0.5f, rz1 + 0.5f};
    }
    
    /**
     * Resolve texture variable reference to actual texture path.
     * Recursively resolves if the resolved value is also a variable.
     */
    private String resolveTexture(String textureRef, BlockModel model) {
        return resolveTexture(textureRef, model, new java.util.HashSet<>());
    }
    
    /**
     * Resolve a texture reference with circular reference protection.
     */
    private String resolveTexture(String textureRef, BlockModel model, java.util.Set<String> visited) {
        if (!textureRef.startsWith("#")) {
            return textureRef; // Already a direct reference
        }
        
        // Resolve variable (e.g., "#torch" -> actual path)
        String varName = textureRef.substring(1);
        
        Map<String, String> textures = model.getTextures();
        if (textures == null || !textures.containsKey(varName)) {
            // Variable doesn't exist in this model - return textureRef as-is
            return textureRef;
        }
        
        String resolved = textures.get(varName);
        if (resolved == null) {
            // Resolved to null - return textureRef as-is
            return textureRef;
        }
        
        // Check for circular reference
        if (visited.contains(varName)) {
            // Circular reference - return textureRef as-is
            return textureRef;
        }
        
        // Add to visited set before recursing
        visited.add(varName);
        
        // Recursively resolve if the resolved value is also a variable
        if (resolved.startsWith("#")) {
            return resolveTexture(resolved, model, visited);
        }
        
        return resolved;
    }
    
    /**
     * Add a face quad to the vertex buffers.
     */
    private int addFaceQuad(BlockFaceCollector.FaceData face,
                            String faceDirection,
                            float x0, float y0, float z0,
                            float x1, float y1, float z1,
                            float u0, float v0, float u1, float v1,
                            FloatList vertices,
                            IntList indices,
                            int currentVertex) {
        
        // Sample lighting for vertices
        float[] light0 = lightSampler.sampleVertexLight(face, getFaceIndex(faceDirection), 0);
        float[] light1 = lightSampler.sampleVertexLight(face, getFaceIndex(faceDirection), 1);
        float[] light2 = lightSampler.sampleVertexLight(face, getFaceIndex(faceDirection), 2);
        float[] light3 = lightSampler.sampleVertexLight(face, getFaceIndex(faceDirection), 3);
        
        // Get face normal
        float[] normal = getFaceNormal(faceDirection);
        
        // Get face vertices based on direction
        float[][] faceVerts = getFaceVertices(faceDirection, x0, y0, z0, x1, y1, z1);
        
        // Add 4 vertices for the quad
        int baseVertex = currentVertex;
        addVertex(vertices, faceVerts[0], u0, v0, normal, light0);
        addVertex(vertices, faceVerts[1], u0, v1, normal, light1);
        addVertex(vertices, faceVerts[2], u1, v1, normal, light2);
        addVertex(vertices, faceVerts[3], u1, v0, normal, light3);
        
        // Add 2 triangles (6 indices)
        indices.add(baseVertex);
        indices.add(baseVertex + 1);
        indices.add(baseVertex + 2);
        indices.add(baseVertex);
        indices.add(baseVertex + 2);
        indices.add(baseVertex + 3);
        
        return currentVertex + 4;
    }
    
    /**
     * Add a single vertex to the buffer.
     */
    private void addVertex(FloatList vertices, float[] pos, float u, float v,
                          float[] normal, float[] light) {
        // Position
        vertices.add(pos[0]);
        vertices.add(pos[1]);
        vertices.add(pos[2]);
        
        // UV
        vertices.add(u);
        vertices.add(v);
        
        // Color (white)
        vertices.add(1.0f);
        vertices.add(1.0f);
        vertices.add(1.0f);
        vertices.add(1.0f);
        
        // Normal
        vertices.add(normal[0]);
        vertices.add(normal[1]);
        vertices.add(normal[2]);
        
        // Lighting (skyLight, blockLightR, blockLightG, blockLightB, ao)
        vertices.add(light[0]);
        vertices.add(light[1]);
        vertices.add(light[2]);
        vertices.add(light[3]);
        vertices.add(light[4]);
    }
    
    /**
     * Get vertices for a face in CCW order.
     */
    private float[][] getFaceVertices(String direction, float x0, float y0, float z0,
                                      float x1, float y1, float z1) {
        return switch (direction) {
            case "up" -> new float[][]{
                {x0, y1, z0}, {x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}
            };
            case "down" -> new float[][]{
                {x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1}
            };
            case "north" -> new float[][]{
                {x0, y0, z0}, {x0, y1, z0}, {x1, y1, z0}, {x1, y0, z0}
            };
            case "south" -> new float[][]{
                {x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}
            };
            case "west" -> new float[][]{
                {x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}, {x0, y1, z0}
            };
            case "east" -> new float[][]{
                {x1, y0, z0}, {x1, y1, z0}, {x1, y1, z1}, {x1, y0, z1}
            };
            default -> new float[][]{
                {x0, y0, z0}, {x0, y1, z0}, {x1, y1, z0}, {x1, y0, z0}
            };
        };
    }
    
    /**
     * Get face normal vector.
     */
    private float[] getFaceNormal(String direction) {
        return switch (direction) {
            case "up" -> new float[]{0, 1, 0};
            case "down" -> new float[]{0, -1, 0};
            case "north" -> new float[]{0, 0, -1};
            case "south" -> new float[]{0, 0, 1};
            case "west" -> new float[]{-1, 0, 0};
            case "east" -> new float[]{1, 0, 0};
            default -> new float[]{0, 0, -1};
        };
    }
    
    /**
     * Get face index for lighting calculations.
     */
    private int getFaceIndex(String direction) {
        return switch (direction) {
            case "up" -> 0;
            case "down" -> 1;
            case "north" -> 2;
            case "south" -> 3;
            case "west" -> 4;
            case "east" -> 5;
            default -> 0;
        };
    }
}
