package mattmc.client.renderer.chunk;

import mattmc.client.renderer.block.BlockFaceCollector;
import mattmc.client.renderer.texture.TextureCoordinateProvider;
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
 * This makes the rendering system compatible with MattMC's model format where
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
     * Reads the block's blockstate and model files and renders all defined elements with proper rotations.
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
        
        // Try to load blockstate JSON to get rotation information
        mattmc.client.resources.model.BlockState blockStateJson = ResourceManager.loadBlockState(blockName);
        int xRotation = 0;
        int yRotation = 0;
        boolean uvlock = false;
        String modelName = blockName;
        
        if (blockStateJson != null && state != null && !state.isEmpty()) {
            // Build the variant string from the block's state (e.g., "facing=north,half=bottom,shape=straight")
            String variantString = state.toVariantString();
            List<mattmc.client.resources.model.BlockStateVariant> variants = blockStateJson.getVariantsForState(variantString);
            
            if (variants != null && !variants.isEmpty()) {
                // Use the first variant (if multiple, one is chosen randomly, but we just use first)
                mattmc.client.resources.model.BlockStateVariant variant = variants.get(0);
                
                // Get rotation values from variant
                if (variant.getX() != null) {
                    xRotation = variant.getX();
                }
                if (variant.getY() != null) {
                    yRotation = variant.getY();
                }
                if (variant.getUvlock() != null) {
                    uvlock = variant.getUvlock();
                }
                
                // Get the model path from variant (e.g., "mattmc:block/stairs" or "block/stairs")
                if (variant.getModel() != null) {
                    modelName = variant.getModel();
                    // The model name might be a full path, use it as-is
                    // ResourceManager.loadBlockModel() will handle namespace prefixes and "block/" paths
                }
            }
        }
        
        // If no blockstate variant found, fall back to old rotation logic
        if (blockStateJson == null && state != null) {
            yRotation = getYRotationFromState(block, state);
        }
        
        // Load the model
        BlockModel model = ResourceManager.loadBlockModel(modelName);
        
        if (model == null || model.getElements() == null) {
            return currentVertex; // No model or elements, skip rendering
        }
        
        // Process each element in the model
        for (ModelElement element : model.getElements()) {
            currentVertex = renderElement(face, element, model, x, y, z, xRotation, yRotation, uvlock, vertices, indices, currentVertex);
        }
        
        return currentVertex;
    }
    
    /**
     * Get Y-axis rotation from blockstate for rotatable blocks.
     * Returns 0 if no rotation is needed.
     */
    private int getYRotationFromState(Block block, BlockState state) {
        return BlockRotationExtractor.getYRotationFromState(block, state);
    }
    
    /**
     * Render a single model element.
     * Converts from MattMC's 0-16 coordinate system to block-relative 0-1 coordinates.
     * Applies X and Y axis rotations if specified.
     */
    private int renderElement(BlockFaceCollector.FaceData face,
                              ModelElement element,
                              BlockModel model,
                              float blockX, float blockY, float blockZ,
                              int xRotation,
                              int yRotation,
                              boolean uvlock,
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
                                             xRotation, yRotation, uvlock, vertices, indices, currentVertex);
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
                                   int xRotation,
                                   int yRotation,
                                   boolean uvlock,
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
			if (texturePath == null) {
				// Failed to resolve texture - skip this face
				return currentVertex;
			}
		}
		
		// Strip namespace prefix if present (e.g., "mattmc:block/planks" -> "block/planks")
		if (texturePath.contains(":")) {
			texturePath = texturePath.substring(texturePath.indexOf(':') + 1);
		}
		
		// Convert to full atlas path format: "block/birch_planks" -> "assets/textures/block/birch_planks.png"
		String atlasPath = "assets/textures/" + texturePath + ".png";
		
		// TODO: Pre-resolve texture IDs in baked model structures to avoid string→int conversion here.
		// For now, resolve the texture ID once per face and use int-based UV lookup.
		int textureId = uvMapper.resolveTextureId(atlasPath);
		TextureCoordinateProvider.UVMapping uvMapping = (textureId >= 0) ? uvMapper.resolveUV(textureId) : null;
		
		// Get UV coordinates from element face (in 0-16 space)
		List<Float> uvList = elementFace.getUv();
		float[] uv = new float[]{0, 0, 16, 16}; // Default to full texture
		if (uvList != null && uvList.size() >= 4) {
			uv[0] = uvList.get(0);
			uv[1] = uvList.get(1);
			uv[2] = uvList.get(2);
			uv[3] = uvList.get(3);
		}
		
		// Get the per-face rotation from the model (0, 90, 180, or 270)
		Integer faceRotation = elementFace.getRotation();
		int faceRotDegrees = (faceRotation != null) ? faceRotation : 0;
		
		// When uvlock=true, transform UV coordinates to account for geometry rotation
        // This keeps textures aligned with world axes by rotating the UV rectangle
        // Note: Only horizontal faces (up/down) need UV transformation for Y-axis rotation
        // Vertical faces already have correct UVs in the JSON for their face direction
        if (uvlock && yRotation != 0 && (faceDirection.equals("up") || faceDirection.equals("down"))) {
            uv = UVTransformer.transformUVsForRotation(uv, faceDirection, yRotation);
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
        
        // Build vertices following MattMC's FaceBakery approach:
        // For each vertex index, get the corner position based on face direction,
        // then apply rotations. This maintains vertex-UV correspondence.
        float[][] faceVerts = new float[4][3];
        for (int i = 0; i < 4; i++) {
            // Get corner position for this vertex index based on face direction
            float[] corner = FaceVertexCalculator.getFaceVertexCorner(faceDirection, i, x0, y0, z0, x1, y1, z1);
            
            // Apply element rotations if needed
            if (xRotation != 0 || yRotation != 0) {
                corner = rotatePoint(corner[0], corner[1], corner[2], xRotation, yRotation);
            }
            
            // Add world position offset
            faceVerts[i][0] = corner[0] + blockX;
            faceVerts[i][1] = corner[1] + blockY;
            faceVerts[i][2] = corner[2] + blockZ;
        }
        
        // Get face normal and rotate it if needed
        float[] faceNormal = FaceVertexCalculator.getFaceNormal(faceDirection);
        if (xRotation != 0 || yRotation != 0) {
            faceNormal = rotatePoint(faceNormal[0], faceNormal[1], faceNormal[2], xRotation, yRotation);
        }
        
        // Apply per-face UV rotation as specified in the model JSON
        int adjustedFaceRotation = faceRotDegrees;
        
        // Render the face with rotated vertices and transformed UVs
        currentVertex = addFaceQuadWithVertices(face, faceVerts, faceNormal, u0, v0, u1, v1, 
                                                adjustedFaceRotation, vertices, indices, currentVertex);
        
        return currentVertex;
    }
    

    /**
     * Rotate a single point around X and Y axes (around center 0.5, 0.5, 0.5).
     */
    private float[] rotatePoint(float x, float y, float z, int xDegrees, int yDegrees) {
        return ElementRotationHelper.rotatePoint(x, y, z, xDegrees, yDegrees);
    }
    

    
    /**
     * Resolve texture variable reference to actual texture path.
     * Recursively resolves if the resolved value is also a variable.
     */
    private String resolveTexture(String textureRef, BlockModel model) {
        return TextureVariableResolver.resolveTexture(textureRef, model);
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
        int fIdx = FaceVertexCalculator.getFaceIndex(faceDirection);
        float[] light0 = lightSampler.sampleVertexLight(face, fIdx, 0);
        float[] light1 = lightSampler.sampleVertexLight(face, fIdx, 1);
        float[] light2 = lightSampler.sampleVertexLight(face, fIdx, 2);
        float[] light3 = lightSampler.sampleVertexLight(face, fIdx, 3);
        
        // Get face normal
        float[] normal = FaceVertexCalculator.getFaceNormal(faceDirection);
        
        // Get face vertices based on direction using new per-vertex approach
        float[][] faceVerts = new float[4][3];
        for (int i = 0; i < 4; i++) {
            faceVerts[i] = FaceVertexCalculator.getFaceVertexCorner(faceDirection, i, x0, y0, z0, x1, y1, z1);
        }
        
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
     * Add a face quad with pre-computed vertices and normal.
     * Applies per-face UV rotation as specified in the model.
     */
    private int addFaceQuadWithVertices(BlockFaceCollector.FaceData face,
                                        float[][] faceVerts,
                                        float[] normal,
                                        float u0, float v0, float u1, float v1,
                                        int faceRotation,
                                        FloatList vertices,
                                        IntList indices,
                                        int currentVertex) {
        // For lighting, we need to determine which face index to use
        // Use a default face index based on the normal direction
        int faceIndex = 0; // Default to up
        if (Math.abs(normal[1]) > 0.9f) {
            faceIndex = normal[1] > 0 ? 0 : 1; // up or down
        } else if (Math.abs(normal[2]) > 0.9f) {
            faceIndex = normal[2] < 0 ? 2 : 3; // north or south
        } else if (Math.abs(normal[0]) > 0.9f) {
            faceIndex = normal[0] < 0 ? 4 : 5; // west or east
        }
        
        // Sample lighting for vertices
        float[] light0 = lightSampler.sampleVertexLight(face, faceIndex, 0);
        float[] light1 = lightSampler.sampleVertexLight(face, faceIndex, 1);
        float[] light2 = lightSampler.sampleVertexLight(face, faceIndex, 2);
        float[] light3 = lightSampler.sampleVertexLight(face, faceIndex, 3);
        
        // Apply per-face UV rotation by shifting which vertex gets which UV coordinate
        // This follows MattMC's BlockFaceUV logic where rotation shifts the vertex index
        // Rotation is applied counter-clockwise: 0°, 90°, 180°, 270°
        float[][] uvCoords = UVTransformer.getRotatedUVCoordinates(u0, v0, u1, v1, faceRotation);
        
        // Add 4 vertices for the quad with rotated UV assignment
        int baseVertex = currentVertex;
        addVertex(vertices, faceVerts[0], uvCoords[0][0], uvCoords[0][1], normal, light0);
        addVertex(vertices, faceVerts[1], uvCoords[1][0], uvCoords[1][1], normal, light1);
        addVertex(vertices, faceVerts[2], uvCoords[2][0], uvCoords[2][1], normal, light2);
        addVertex(vertices, faceVerts[3], uvCoords[3][0], uvCoords[3][1], normal, light3);
        
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
    
}
