package mattmc.client.renderer.chunk;

import mattmc.client.renderer.block.BlockFaceCollector;
import mattmc.client.renderer.texture.TextureAtlas;
import mattmc.client.resources.ResourceManager;
import mattmc.client.resources.model.BlockModel;
import mattmc.client.resources.model.ModelElement;
import mattmc.world.level.block.Block;

import java.util.List;
import java.util.Map;

/**
 * Handles geometry generation for torch blocks.
 * Torches use custom geometry defined in JSON model files (e.g., template_torch.json).
 * This builder reads the model elements and generates the appropriate vertices.
 * 
 * Created as part of fixing torch rendering (previously incorrectly rendered as stairs).
 */
public class TorchGeometryBuilder {
    
    private final VertexLightSampler lightSampler;
    private final UVMapper uvMapper;
    
    /**
     * Create a torch geometry builder.
     */
    public TorchGeometryBuilder(VertexLightSampler lightSampler, UVMapper uvMapper) {
        this.lightSampler = lightSampler;
        this.uvMapper = uvMapper;
    }
    
    /**
     * Add torch geometry to the provided vertex and index lists.
     * Reads the model from JSON and generates vertices based on model elements.
     * 
     * @param face The face data containing block, position, and state
     * @param vertices The vertex list to add to
     * @param indices The index list to add to
     * @param currentVertex The current vertex index (will be updated)
     * @return The new current vertex index after adding torch geometry
     */
    public int addTorchGeometry(BlockFaceCollector.FaceData face, 
                                  FloatList vertices, 
                                  IntList indices, 
                                  int currentVertex) {
        float x = face.x;
        float y = face.y;
        float z = face.z;
        Block block = face.block;
        
        // Load the block model for this torch
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
        
        // Determine Y-axis rotation for wall torches
        // Wall torches use rotation based on facing direction (from blockstate)
        int yRotation = 0;
        if (block instanceof mattmc.world.level.block.WallTorchBlock && face.blockState != null) {
            mattmc.world.level.block.state.properties.Direction facing = 
                face.blockState.getDirection("facing");
            if (facing != null) {
                // Rotation values from wall_torch.json blockstate
                // facing=east: 0 (default), facing=north: 270, facing=south: 90, facing=west: 180
                yRotation = switch (facing) {
                    case NORTH -> 270;
                    case SOUTH -> 90;
                    case WEST -> 180;
                    case EAST -> 0;
                    default -> 0;
                };
            }
        }
        
        // Process each element in the model
        for (ModelElement element : model.getElements()) {
            currentVertex = addModelElement(face, element, model, x, y, z, yRotation, vertices, indices, currentVertex);
        }
        
        return currentVertex;
    }
    
    /**
     * Add a single model element to the mesh.
     * Converts from Minecraft's 0-16 coordinate system to block-relative 0-1 coordinates.
     * Applies Y-axis rotation if specified.
     */
    private int addModelElement(BlockFaceCollector.FaceData face,
                                 ModelElement element,
                                 BlockModel model,
                                 float blockX, float blockY, float blockZ,
                                 int yRotation,
                                 FloatList vertices,
                                 IntList indices,
                                 int currentVertex) {
        List<Float> from = element.getFrom();
        List<Float> to = element.getTo();
        
        if (from == null || to == null || from.size() != 3 || to.size() != 3) {
            return currentVertex; // Invalid element
        }
        
        // Convert from Minecraft's 0-16 coordinate system to 0-1 block-relative coordinates
        // First in local block space (0-1)
        float localX0 = from.get(0) / 16.0f;
        float localY0 = from.get(1) / 16.0f;
        float localZ0 = from.get(2) / 16.0f;
        float localX1 = to.get(0) / 16.0f;
        float localY1 = to.get(1) / 16.0f;
        float localZ1 = to.get(2) / 16.0f;
        
        // Apply Y-axis rotation around block center (0.5, 0.5, 0.5)
        if (yRotation != 0) {
            float[] rotated0 = rotateY(localX0, localZ0, yRotation);
            float[] rotated1 = rotateY(localX1, localZ1, yRotation);
            localX0 = rotated0[0];
            localZ0 = rotated0[1];
            localX1 = rotated1[0];
            localZ1 = rotated1[1];
        }
        
        // Now add block position offset
        float x0 = blockX + localX0;
        float y0 = blockY + localY0;
        float z0 = blockZ + localZ0;
        float x1 = blockX + localX1;
        float y1 = blockY + localY1;
        float z1 = blockZ + localZ1;
        
        Map<String, ModelElement.ElementFace> faces = element.getFaces();
        if (faces == null) {
            return currentVertex;
        }
        
        // Get texture path for UV mapping
        String texturePath = resolveTexturePath(model);
        
        // Render each face of the element
        float[] color = new float[]{1.0f, 1.0f, 1.0f, 1.0f}; // White color
        
        // Process each face
        if (faces.containsKey("down")) {
            currentVertex = addElementFace(face, faces.get("down"), texturePath,
                x0, y0, z0, x1, y1, z1, "down", color, vertices, indices, currentVertex);
        }
        if (faces.containsKey("up")) {
            currentVertex = addElementFace(face, faces.get("up"), texturePath,
                x0, y0, z0, x1, y1, z1, "up", color, vertices, indices, currentVertex);
        }
        if (faces.containsKey("north")) {
            currentVertex = addElementFace(face, faces.get("north"), texturePath,
                x0, y0, z0, x1, y1, z1, "north", color, vertices, indices, currentVertex);
        }
        if (faces.containsKey("south")) {
            currentVertex = addElementFace(face, faces.get("south"), texturePath,
                x0, y0, z0, x1, y1, z1, "south", color, vertices, indices, currentVertex);
        }
        if (faces.containsKey("west")) {
            currentVertex = addElementFace(face, faces.get("west"), texturePath,
                x0, y0, z0, x1, y1, z1, "west", color, vertices, indices, currentVertex);
        }
        if (faces.containsKey("east")) {
            currentVertex = addElementFace(face, faces.get("east"), texturePath,
                x0, y0, z0, x1, y1, z1, "east", color, vertices, indices, currentVertex);
        }
        
        return currentVertex;
    }
    
    /**
     * Add a single face of a model element.
     */
    private int addElementFace(BlockFaceCollector.FaceData faceData,
                                ModelElement.ElementFace face,
                                String texturePath,
                                float x0, float y0, float z0,
                                float x1, float y1, float z1,
                                String direction,
                                float[] color,
                                FloatList vertices,
                                IntList indices,
                                int currentVertex) {
        // Get UV coordinates from the face
        List<Float> uv = face.getUv();
        float u0, v0, u1, v1;
        
        if (uv != null && uv.size() == 4) {
            // Convert from 0-16 texture coordinates to 0-1
            u0 = uv.get(0) / 16.0f;
            v0 = uv.get(1) / 16.0f;
            u1 = uv.get(2) / 16.0f;
            v1 = uv.get(3) / 16.0f;
        } else {
            // Default to full texture
            u0 = 0.0f;
            v0 = 0.0f;
            u1 = 1.0f;
            v1 = 1.0f;
        }
        
        // Get UV mapping from texture atlas
        TextureAtlas.UVMapping uvMapping = getUVMappingForTexture(texturePath);
        
        if (uvMapping != null) {
            // Map the model UVs to atlas UVs
            float atlasU0 = uvMapping.u0 + (uvMapping.u1 - uvMapping.u0) * u0;
            float atlasV0 = uvMapping.v0 + (uvMapping.v1 - uvMapping.v0) * v0;
            float atlasU1 = uvMapping.u0 + (uvMapping.u1 - uvMapping.u0) * u1;
            float atlasV1 = uvMapping.v0 + (uvMapping.v1 - uvMapping.v0) * v1;
            
            u0 = atlasU0;
            v0 = atlasV0;
            u1 = atlasU1;
            v1 = atlasV1;
        }
        
        // Sample lighting at corners
        float[] light0, light1, light2, light3;
        int faceIndex = getFaceIndex(direction);
        light0 = lightSampler.sampleVertexLight(faceData, faceIndex, 0);
        light1 = lightSampler.sampleVertexLight(faceData, faceIndex, 1);
        light2 = lightSampler.sampleVertexLight(faceData, faceIndex, 2);
        light3 = lightSampler.sampleVertexLight(faceData, faceIndex, 3);
        
        // Get normal vector for this face
        float nx = 0, ny = 0, nz = 0;
        switch (direction) {
            case "down" -> ny = -1;
            case "up" -> ny = 1;
            case "north" -> nz = -1;
            case "south" -> nz = 1;
            case "west" -> nx = -1;
            case "east" -> nx = 1;
        }
        
        // Add vertices for this face
        int base = currentVertex;
        
        switch (direction) {
            case "down" -> {
                addVertex(vertices, x0, y0, z0, u0, v0, color, nx, ny, nz, light0[0], light0[1], light0[2], light0[3], light0[4]);
                addVertex(vertices, x1, y0, z0, u1, v0, color, nx, ny, nz, light1[0], light1[1], light1[2], light1[3], light1[4]);
                addVertex(vertices, x1, y0, z1, u1, v1, color, nx, ny, nz, light2[0], light2[1], light2[2], light2[3], light2[4]);
                addVertex(vertices, x0, y0, z1, u0, v1, color, nx, ny, nz, light3[0], light3[1], light3[2], light3[3], light3[4]);
            }
            case "up" -> {
                addVertex(vertices, x0, y1, z0, u0, v0, color, nx, ny, nz, light0[0], light0[1], light0[2], light0[3], light0[4]);
                addVertex(vertices, x0, y1, z1, u0, v1, color, nx, ny, nz, light1[0], light1[1], light1[2], light1[3], light1[4]);
                addVertex(vertices, x1, y1, z1, u1, v1, color, nx, ny, nz, light2[0], light2[1], light2[2], light2[3], light2[4]);
                addVertex(vertices, x1, y1, z0, u1, v0, color, nx, ny, nz, light3[0], light3[1], light3[2], light3[3], light3[4]);
            }
            case "north" -> {
                addVertex(vertices, x1, y0, z0, u1, v1, color, nx, ny, nz, light0[0], light0[1], light0[2], light0[3], light0[4]);
                addVertex(vertices, x0, y0, z0, u0, v1, color, nx, ny, nz, light1[0], light1[1], light1[2], light1[3], light1[4]);
                addVertex(vertices, x0, y1, z0, u0, v0, color, nx, ny, nz, light2[0], light2[1], light2[2], light2[3], light2[4]);
                addVertex(vertices, x1, y1, z0, u1, v0, color, nx, ny, nz, light3[0], light3[1], light3[2], light3[3], light3[4]);
            }
            case "south" -> {
                addVertex(vertices, x0, y0, z1, u0, v1, color, nx, ny, nz, light0[0], light0[1], light0[2], light0[3], light0[4]);
                addVertex(vertices, x1, y0, z1, u1, v1, color, nx, ny, nz, light1[0], light1[1], light1[2], light1[3], light1[4]);
                addVertex(vertices, x1, y1, z1, u1, v0, color, nx, ny, nz, light2[0], light2[1], light2[2], light2[3], light2[4]);
                addVertex(vertices, x0, y1, z1, u0, v0, color, nx, ny, nz, light3[0], light3[1], light3[2], light3[3], light3[4]);
            }
            case "west" -> {
                addVertex(vertices, x0, y0, z0, u0, v1, color, nx, ny, nz, light0[0], light0[1], light0[2], light0[3], light0[4]);
                addVertex(vertices, x0, y0, z1, u1, v1, color, nx, ny, nz, light1[0], light1[1], light1[2], light1[3], light1[4]);
                addVertex(vertices, x0, y1, z1, u1, v0, color, nx, ny, nz, light2[0], light2[1], light2[2], light2[3], light2[4]);
                addVertex(vertices, x0, y1, z0, u0, v0, color, nx, ny, nz, light3[0], light3[1], light3[2], light3[3], light3[4]);
            }
            case "east" -> {
                addVertex(vertices, x1, y0, z1, u0, v1, color, nx, ny, nz, light0[0], light0[1], light0[2], light0[3], light0[4]);
                addVertex(vertices, x1, y0, z0, u1, v1, color, nx, ny, nz, light1[0], light1[1], light1[2], light1[3], light1[4]);
                addVertex(vertices, x1, y1, z0, u1, v0, color, nx, ny, nz, light2[0], light2[1], light2[2], light2[3], light2[4]);
                addVertex(vertices, x1, y1, z1, u0, v0, color, nx, ny, nz, light3[0], light3[1], light3[2], light3[3], light3[4]);
            }
        }
        
        addQuadIndices(indices, base);
        return currentVertex + 4;
    }
    
    /**
     * Resolve texture path from model textures.
     * Converts from model format (e.g., "mattmc:block/torch") to file path format
     * (e.g., "assets/textures/block/torch.png") for texture atlas lookup.
     */
    private String resolveTexturePath(BlockModel model) {
        if (model.getTextures() == null) {
            return null;
        }
        
        // Try to find the torch texture
        String texture = model.getTexture("torch");
        if (texture == null) {
            texture = model.getTexture("particle");
        }
        if (texture == null) {
            texture = model.getTexture("all");
        }
        
        if (texture == null) {
            return null;
        }
        
        // Convert from model format to file path format
        // "mattmc:block/torch" -> "assets/textures/block/torch.png"
        if (texture.contains(":")) {
            String[] parts = texture.split(":", 2);
            texture = parts[1];  // Use just the path part (e.g., "block/torch")
        }
        
        return "assets/textures/" + texture + ".png";
    }
    
    /**
     * Get UV mapping for a texture from the texture atlas.
     */
    private TextureAtlas.UVMapping getUVMappingForTexture(String texturePath) {
        if (texturePath == null) {
            return null;
        }
        
        // Create a temporary FaceData to use uvMapper
        // We'll use a simple approach: query the texture atlas directly
        return uvMapper.getTextureAtlas() != null ? 
               uvMapper.getTextureAtlas().getUVMapping(texturePath) : null;
    }
    
    /**
     * Rotate a point around the Y-axis (around block center at 0.5, 0.5).
     * @param x X coordinate in block-local space (0-1)
     * @param z Z coordinate in block-local space (0-1)
     * @param degrees Rotation angle in degrees (90, 180, 270)
     * @return Rotated [x, z] coordinates
     */
    private float[] rotateY(float x, float z, int degrees) {
        // Translate to origin (block center is at 0.5, 0.5)
        float cx = x - 0.5f;
        float cz = z - 0.5f;
        
        // Rotate around origin
        float rotatedX, rotatedZ;
        switch (degrees) {
            case 90 -> {
                rotatedX = -cz;
                rotatedZ = cx;
            }
            case 180 -> {
                rotatedX = -cx;
                rotatedZ = -cz;
            }
            case 270 -> {
                rotatedX = cz;
                rotatedZ = -cx;
            }
            default -> {
                rotatedX = cx;
                rotatedZ = cz;
            }
        }
        
        // Translate back
        return new float[]{rotatedX + 0.5f, rotatedZ + 0.5f};
    }
    
    /**
     * Get face index for light sampling.
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
    
    /**
     * Add a single vertex to the vertex list.
     */
    private void addVertex(FloatList vertices, float x, float y, float z, 
                          float u, float v, float[] color,
                          float nx, float ny, float nz,
                          float skyLight, float blockLightR, float blockLightG, float blockLightB, float ao) {
        vertices.add(x);
        vertices.add(y);
        vertices.add(z);
        vertices.add(u);
        vertices.add(v);
        vertices.add(color[0]);
        vertices.add(color[1]);
        vertices.add(color[2]);
        vertices.add(color[3]);
        vertices.add(nx);
        vertices.add(ny);
        vertices.add(nz);
        vertices.add(skyLight);
        vertices.add(blockLightR);
        vertices.add(blockLightG);
        vertices.add(blockLightB);
        vertices.add(ao);
    }
    
    /**
     * Add quad indices (two triangles).
     */
    private void addQuadIndices(IntList indices, int base) {
        // First triangle
        indices.add(base);
        indices.add(base + 1);
        indices.add(base + 2);
        
        // Second triangle
        indices.add(base);
        indices.add(base + 2);
        indices.add(base + 3);
    }
}
