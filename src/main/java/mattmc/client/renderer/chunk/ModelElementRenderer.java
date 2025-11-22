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
            System.out.println("DEBUG: Resolved " + textureRef + " -> " + texturePath);
            if (texturePath == null) {
                System.err.println("ERROR: Failed to resolve texture " + textureRef + " for block at " + 
                                   face.x + "," + face.y + "," + face.z);
                System.err.println("Model textures: " + (model.getTextures() != null ? model.getTextures() : "null"));
                return currentVertex;
            }
        }
        
        // Strip namespace prefix if present (e.g., "mattmc:block/planks" -> "block/planks")
        if (texturePath.contains(":")) {
            texturePath = texturePath.substring(texturePath.indexOf(':') + 1);
        }
        
        // Convert to full atlas path format: "block/birch_planks" -> "assets/textures/block/birch_planks.png"
        String atlasPath = "assets/textures/" + texturePath + ".png";
        
        // Get UV mapping
        TextureAtlas.UVMapping uvMapping = uvMapper.getUVMappingForTexture(atlasPath);
        
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
        
        // TODO: Implement proper uvlock UV transformation using transformation matrices
        // For now, disable uvlock UV rotation as it's causing incorrect texture scaling
        // The per-face rotation property should handle most cases correctly
        //if (uvlock && (xRotation != 0 || yRotation != 0)) {
        //    uv = rotateUVs(uv, faceDirection, xRotation, yRotation);
        //}
        
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
        
        // Get face vertices and normal BEFORE rotation
        // This ensures UVs are mapped correctly to the original face orientation
        float[][] faceVerts = getFaceVertices(faceDirection, x0, y0, z0, x1, y1, z1);
        float[] faceNormal = getFaceNormal(faceDirection);
        
        // Apply rotations to vertex positions if needed
        // Rotations are applied in order: X first, then Y (following Minecraft's convention)
        if (xRotation != 0 || yRotation != 0) {
            for (int i = 0; i < 4; i++) {
                float[] rotated = rotatePoint(faceVerts[i][0], faceVerts[i][1], faceVerts[i][2], xRotation, yRotation);
                faceVerts[i][0] = rotated[0];
                faceVerts[i][1] = rotated[1];
                faceVerts[i][2] = rotated[2];
            }
            // Also rotate the face normal
            float[] rotatedNormal = rotatePoint(faceNormal[0], faceNormal[1], faceNormal[2], xRotation, yRotation);
            faceNormal = rotatedNormal;
        }
        
        // Add world position offset to vertices
        for (int i = 0; i < 4; i++) {
            faceVerts[i][0] += blockX;
            faceVerts[i][1] += blockY;
            faceVerts[i][2] += blockZ;
        }
        
        // Apply per-face UV rotation as specified in the model JSON
        // Note: When uvlock=false (default), UVs should NOT counter-rotate when the block rotates
        // The texture rotates with the geometry naturally through vertex rotation
        // Only the per-face rotation property from the JSON should affect UV mapping
        int adjustedFaceRotation = faceRotDegrees;
        
        // Render the face with rotated vertices
        currentVertex = addFaceQuadWithVertices(face, faceVerts, faceNormal, u0, v0, u1, v1, 
                                                adjustedFaceRotation, vertices, indices, currentVertex);
        
        return currentVertex;
    }
    
    /**
     * Apply X and Y axis rotations to element coordinates.
     * Rotations are applied around the center (0.5, 0.5, 0.5).
     * X rotation is applied first, then Y rotation (following Minecraft's convention).
     * 
     * To correctly compute the bounding box after rotation, we rotate all 8 corners
     * of the original box and then find the min/max of all rotated corners.
     * 
     * @return Array of [x0, y0, z0, x1, y1, z1] after rotation, representing the axis-aligned bounding box
     */
    private float[] applyRotations(float x0, float y0, float z0, float x1, float y1, float z1, int xDegrees, int yDegrees) {
        // Generate all 8 corners of the original bounding box
        float[][] corners = new float[][] {
            {x0, y0, z0}, {x1, y0, z0}, {x0, y1, z0}, {x1, y1, z0},
            {x0, y0, z1}, {x1, y0, z1}, {x0, y1, z1}, {x1, y1, z1}
        };
        
        // Rotate each corner
        for (int i = 0; i < 8; i++) {
            // Center around (0.5, 0.5, 0.5)
            float cx = corners[i][0] - 0.5f;
            float cy = corners[i][1] - 0.5f;
            float cz = corners[i][2] - 0.5f;
            
            // Apply X rotation
            if (xDegrees != 0) {
                float[] rotated = rotateX(cx, cy, cz, xDegrees);
                cx = rotated[0]; cy = rotated[1]; cz = rotated[2];
            }
            
            // Apply Y rotation
            if (yDegrees != 0) {
                float[] rotated = rotateY(cx, cy, cz, yDegrees);
                cx = rotated[0]; cy = rotated[1]; cz = rotated[2];
            }
            
            // Un-center
            corners[i][0] = cx + 0.5f;
            corners[i][1] = cy + 0.5f;
            corners[i][2] = cz + 0.5f;
        }
        
        // Find min and max of all rotated corners to get the new axis-aligned bounding box
        float minX = corners[0][0], maxX = corners[0][0];
        float minY = corners[0][1], maxY = corners[0][1];
        float minZ = corners[0][2], maxZ = corners[0][2];
        
        for (int i = 1; i < 8; i++) {
            minX = Math.min(minX, corners[i][0]);
            maxX = Math.max(maxX, corners[i][0]);
            minY = Math.min(minY, corners[i][1]);
            maxY = Math.max(maxY, corners[i][1]);
            minZ = Math.min(minZ, corners[i][2]);
            maxZ = Math.max(maxZ, corners[i][2]);
        }
        
        return new float[]{minX, minY, minZ, maxX, maxY, maxZ};
    }
    
    /**
     * Rotate a single point around X and Y axes (around center 0.5, 0.5, 0.5).
     */
    private float[] rotatePoint(float x, float y, float z, int xDegrees, int yDegrees) {
        // Center around origin
        float cx = x - 0.5f, cy = y - 0.5f, cz = z - 0.5f;
        
        // Apply X rotation
        if (xDegrees != 0) {
            float[] rotated = rotateX(cx, cy, cz, xDegrees);
            cx = rotated[0]; cy = rotated[1]; cz = rotated[2];
        }
        
        // Apply Y rotation
        if (yDegrees != 0) {
            float[] rotated = rotateY(cx, cy, cz, yDegrees);
            cx = rotated[0]; cy = rotated[1]; cz = rotated[2];
        }
        
        // Un-center
        return new float[]{cx + 0.5f, cy + 0.5f, cz + 0.5f};
    }
    
    /**
     * Rotate a point around the X axis.
     */
    private float[] rotateX(float x, float y, float z, int degrees) {
        return switch (degrees) {
            case 90 -> new float[]{x, -z, y};
            case 180 -> new float[]{x, -y, -z};
            case 270 -> new float[]{x, z, -y};
            default -> new float[]{x, y, z};
        };
    }
    
    /**
     * Rotate a point around the Y axis.
     */
    private float[] rotateY(float x, float y, float z, int degrees) {
        return switch (degrees) {
            case 90 -> new float[]{-z, y, x};
            case 180 -> new float[]{-x, y, -z};
            case 270 -> new float[]{z, y, -x};
            default -> new float[]{x, y, z};
        };
    }
    
    /**
     * Rotate a face direction string based on X and Y rotations.
     * This ensures the face culling and normals are correct after rotation.
     */
    private String rotateFaceDirection(String face, int xDegrees, int yDegrees) {
        // Apply X rotation first
        if (xDegrees == 90) {
            face = switch (face) {
                case "up" -> "north";
                case "north" -> "down";
                case "down" -> "south";
                case "south" -> "up";
                default -> face;
            };
        } else if (xDegrees == 180) {
            face = switch (face) {
                case "up" -> "down";
                case "down" -> "up";
                case "north" -> "south";
                case "south" -> "north";
                default -> face;
            };
        } else if (xDegrees == 270) {
            face = switch (face) {
                case "up" -> "south";
                case "south" -> "down";
                case "down" -> "north";
                case "north" -> "up";
                default -> face;
            };
        }
        
        // Apply Y rotation second
        if (yDegrees == 90) {
            face = switch (face) {
                case "north" -> "east";
                case "east" -> "south";
                case "south" -> "west";
                case "west" -> "north";
                default -> face;
            };
        } else if (yDegrees == 180) {
            face = switch (face) {
                case "north" -> "south";
                case "south" -> "north";
                case "east" -> "west";
                case "west" -> "east";
                default -> face;
            };
        } else if (yDegrees == 270) {
            face = switch (face) {
                case "north" -> "west";
                case "west" -> "south";
                case "south" -> "east";
                case "east" -> "north";
                default -> face;
            };
        }
        
        return face;
    }
    
    /**
     * Rotate UV coordinates for uvlock.
     * When uvlock is true, UVs are counter-rotated to maintain texture orientation with world axes.
     * 
     * Key principle: rotation around an axis only affects faces perpendicular to that axis.
     * - Y-axis rotation: affects horizontal faces (up/down) - they see the texture rotate
     * - X-axis rotation: affects vertical north/south faces - they see the texture rotate  
     * - Z-axis rotation: affects vertical east/west faces - they see the texture rotate
     * 
     * @param uv Original UV coordinates [u0, v0, u1, v1] in 0-16 space
     * @param face Face direction (BEFORE any rotation is applied to geometry)
     * @param xDegrees X-axis rotation applied to block
     * @param yDegrees Y-axis rotation applied to block
     * @return Counter-rotated UV coordinates
     */
    private float[] rotateUVs(float[] uv, String face, int xDegrees, int yDegrees) {
        // With uvlock enabled, counter-rotate UVs to maintain world-axis alignment
        // This applies to all faces that are affected by the rotation
        
        // Y-axis rotation affects horizontal (up/down) AND vertical (N/S/E/W) faces
        // All faces need UV counter-rotation to keep textures aligned with world axes
        if (yDegrees != 0) {
            uv = rotateUVClockwise(uv, -yDegrees);
        }
        
        // X-axis rotation affects north/south faces (perpendicular to X axis)
        // For stairs, this is used for upside-down variants (half=top with x=180)
        if (xDegrees != 0 && (face.equals("north") || face.equals("south"))) {
            uv = rotateUVClockwise(uv, -xDegrees);
        }
        
        return uv;
    }
    
    /**
     * Rotate UV coordinates clockwise by the specified degrees.
     * UV coordinates are in texture space [u0, v0, u1, v1] from 0-16.
     * 
     * For 90-degree clockwise rotation: (u, v) → (v, 16-u)
     * This rotates the UV rectangle around the center of the 16x16 texture space.
     */
    private float[] rotateUVClockwise(float[] uv, int degrees) {
        float u0 = uv[0], v0 = uv[1], u1 = uv[2], v1 = uv[3];
        
        // Number of 90-degree rotations (handle negative degrees for counter-clockwise)
        int rotations = (degrees / 90) % 4;
        if (rotations < 0) rotations += 4;
        
        for (int i = 0; i < rotations; i++) {
            // Rotate 90 degrees clockwise: (u, v) → (v, 16-u)
            // Apply to both corners of the UV rectangle
            float newU0 = v0;
            float newV0 = 16 - u1;
            float newU1 = v1;
            float newV1 = 16 - u0;
            
            u0 = newU0;
            v0 = newV0;
            u1 = newU1;
            v1 = newV1;
        }
        
        return new float[]{u0, v0, u1, v1};
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
        // This follows Minecraft's BlockFaceUV logic where rotation shifts the vertex index
        // Rotation is applied counter-clockwise: 0°, 90°, 180°, 270°
        float[][] uvCoords = new float[4][2];
        uvCoords[0] = new float[]{u0, v0};
        uvCoords[1] = new float[]{u0, v1};
        uvCoords[2] = new float[]{u1, v1};
        uvCoords[3] = new float[]{u1, v0};
        
        // Shift UV assignment based on rotation (in 90-degree increments)
        int rotationSteps = (faceRotation / 90) % 4;
        
        // Add 4 vertices for the quad with rotated UV assignment
        int baseVertex = currentVertex;
        addVertex(vertices, faceVerts[0], uvCoords[(0 + rotationSteps) % 4][0], uvCoords[(0 + rotationSteps) % 4][1], normal, light0);
        addVertex(vertices, faceVerts[1], uvCoords[(1 + rotationSteps) % 4][0], uvCoords[(1 + rotationSteps) % 4][1], normal, light1);
        addVertex(vertices, faceVerts[2], uvCoords[(2 + rotationSteps) % 4][0], uvCoords[(2 + rotationSteps) % 4][1], normal, light2);
        addVertex(vertices, faceVerts[3], uvCoords[(3 + rotationSteps) % 4][0], uvCoords[(3 + rotationSteps) % 4][1], normal, light3);
        
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
