package mattmc.client.renderer.chunk;

import mattmc.client.renderer.ColorUtils;
import mattmc.client.renderer.block.BlockFaceCollector;
import mattmc.client.renderer.texture.TextureAtlas;
import mattmc.world.level.block.Blocks;

import java.util.List;

/**
 * Builds vertex and index arrays from collected block faces.
 * Converts BlockFaceCollector data into a format suitable for VBO/VAO rendering.
 * Supports texture atlas UV mapping for multi-texture VBO rendering.
 * 
 * ISSUE-002 fix: Uses primitive FloatList and IntList instead of ArrayList<Float/Integer>
 * to eliminate boxing overhead and reduce GC pressure.
 */
public class MeshBuilder {
    
    // Vertex format: x, y, z, u, v, r, g, b, a, nx, ny, nz, unused1, unused2, unused3 (15 floats per vertex)
    // Using primitive arrays to avoid boxing/unboxing overhead
    private final FloatList vertices = new FloatList();
    private final IntList indices = new IntList();
    private int currentVertex = 0;
    private final TextureAtlas textureAtlas;
    
    /**
     * Create a mesh builder with optional texture atlas support.
     * 
     * @param textureAtlas Texture atlas for UV mapping, or null to use fallback colors
     */
    public MeshBuilder(TextureAtlas textureAtlas) {
        this.textureAtlas = textureAtlas;
    }
    
    /**
     * Build a ChunkMeshBuffer from collected face data.
     * ISSUE-015 fix: Optimized list iteration to reduce method call overhead.
     */
    public ChunkMeshBuffer build(int chunkX, int chunkZ, BlockFaceCollector collector) {
        vertices.clear();
        indices.clear();
        currentVertex = 0;
        
        // ISSUE-015 fix: Process all face types in a flattened structure
        // to reduce method call overhead and iterator allocations
        List<BlockFaceCollector.FaceData>[] allFaces = new List[] {
            collector.getTopFaces(),
            collector.getBottomFaces(),
            collector.getNorthFaces(),
            collector.getSouthFaces(),
            collector.getWestFaces(),
            collector.getEastFaces()
        };
        
        FaceType[] faceTypes = FaceType.values();
        
        // Use indexed loops to avoid iterator object allocations
        for (int i = 0; i < allFaces.length; i++) {
            List<BlockFaceCollector.FaceData> faces = allFaces[i];
            FaceType type = faceTypes[i];
            
            // Indexed loop instead of enhanced for to avoid iterator
            for (int j = 0; j < faces.size(); j++) {
                BlockFaceCollector.FaceData face = faces.get(j);
                
                // Check if this is a stairs block (special marker)
                if ("stairs".equals(face.faceType)) {
                    // Add stairs geometry instead of regular face, passing blockstate
                    addStairsGeometry(face.x, face.y, face.z, face.block, face.blockState);
                    continue;
                }
                
                // Extract color components and UV mapping
                float[] color = extractColor(face);
                TextureAtlas.UVMapping uvMapping = getUVMapping(face);
                
                // Add the face with correct orientation, passing face data for light sampling
                switch (type) {
                    case TOP -> addTopFace(face, color, uvMapping);
                    case BOTTOM -> addBottomFace(face, color, uvMapping);
                    case NORTH -> addNorthFace(face, color, uvMapping);
                    case SOUTH -> addSouthFace(face, color, uvMapping);
                    case WEST -> addWestFace(face, color, uvMapping);
                    case EAST -> addEastFace(face, color, uvMapping);
                }
            }
        }
        
        // Use efficient toArray() which just copies the used portion
        float[] vertexArray = vertices.toArray();
        int[] indexArray = indices.toArray();
        
        return new ChunkMeshBuffer(chunkX, chunkZ, vertexArray, indexArray);
    }
    
    /**
     * Face orientation enum.
     */
    private enum FaceType {
        TOP, BOTTOM, NORTH, SOUTH, WEST, EAST
    }
    
    /**
     * Get UV mapping from texture atlas for a face.
     * Returns null if no atlas or texture not found.
     */
    private TextureAtlas.UVMapping getUVMapping(BlockFaceCollector.FaceData face) {
        if (textureAtlas == null) {
            return null;
        }
        
        String texturePath = face.block.getTexturePath(face.faceType);
        if (texturePath == null) {
            return null;
        }
        
        return textureAtlas.getUVMapping(texturePath);
    }
    
    /**
     * Extract RGBA color from face data.
     * Uses white color with brightness when texture atlas is available,
     * otherwise uses fallback colors.
     */
    private float[] extractColor(BlockFaceCollector.FaceData face) {
        int renderColor;
        
        if (textureAtlas != null && face.block.hasTexture()) {
            // Use white color for texture modulation
            renderColor = 0xFFFFFF;
            
            // Apply grass green tint for grass_block top face (vanilla Minecraft-like)
            if (face.block == Blocks.GRASS_BLOCK && face.faceType != null && "top".equals(face.faceType)) {
                renderColor = 0x5BB53B; // Grass green
            }
        } else {
            // Use fallback color when no texture atlas
            renderColor = ColorUtils.adjustColorBrightness(
                face.block.getFallbackColor(), 
                face.colorBrightness
            );
            
            // Apply grass green tint for grass_block top face
            if (face.block == Blocks.GRASS_BLOCK && face.faceType != null && "top".equals(face.faceType)) {
                renderColor = ColorUtils.applyTint(renderColor, 0x5BB53B, face.colorBrightness);
            }
        }
        
        // Apply brightness
        float brightness = face.brightness;
        
        int r = (renderColor >> 16) & 0xFF;
        int g = (renderColor >> 8) & 0xFF;
        int b = renderColor & 0xFF;
        
        return new float[] {
            (r / 255.0f) * brightness,
            (g / 255.0f) * brightness,
            (b / 255.0f) * brightness,
            1.0f // alpha
        };
    }
    
    /**
     * Sample light for a vertex - removed, now returns empty values.
     * Returns [unused1, unused2, unused3] as floats.
     */
    private float[] sampleVertexLight(BlockFaceCollector.FaceData face, 
                                      int normalIndex,
                                      int cornerIndex) {
        // No light sampling in simplified version
        return new float[] {0.0f, 0.0f, 0.0f};
    }
    
    /**
     * Add top face vertices and indices.
     */
    private void addTopFace(BlockFaceCollector.FaceData face, float[] color, TextureAtlas.UVMapping uvMapping) {
        float x = face.x, y = face.y, z = face.z;
        float x0 = x, x1 = x + 1;
        float y1 = y + 1;
        float z0 = z, z1 = z + 1;
        
        // Get UV coordinates (0-1 if no atlas)
        float u0 = 0, v0 = 0, u1 = 1, v1 = 1;
        if (uvMapping != null) {
            u0 = uvMapping.u0;
            v0 = uvMapping.v0;
            u1 = uvMapping.u1;
            v1 = uvMapping.v1;
        }
        
        int baseVertex = currentVertex;
        
        // Sample light for each vertex (4 corners of top face)
        float[] light0 = sampleVertexLight(face, 0, 0);
        float[] light1 = sampleVertexLight(face, 0, 1);
        float[] light2 = sampleVertexLight(face, 0, 2);
        float[] light3 = sampleVertexLight(face, 0, 3);
        
        // 4 vertices for the quad with atlas UVs, normal (0,1,0) for top face, and light data
        addVertex(x0, y1, z0, u0, v0, color, 0, 1, 0, light0[0], light0[1], light0[2]); // 0
        addVertex(x0, y1, z1, u0, v1, color, 0, 1, 0, light1[0], light1[1], light1[2]); // 1
        addVertex(x1, y1, z1, u1, v1, color, 0, 1, 0, light2[0], light2[1], light2[2]); // 2
        addVertex(x1, y1, z0, u1, v0, color, 0, 1, 0, light3[0], light3[1], light3[2]); // 3
        
        // 2 triangles (6 indices)
        addQuadIndices(baseVertex);
        
        currentVertex += 4;
    }
    
    /**
     * Add bottom face vertices and indices.
     */
    private void addBottomFace(BlockFaceCollector.FaceData face, float[] color, TextureAtlas.UVMapping uvMapping) {
        float x = face.x, y = face.y, z = face.z;
        float x0 = x, x1 = x + 1;
        float y0 = y;
        float z0 = z, z1 = z + 1;
        
        // Get UV coordinates
        float u0 = 0, v0 = 0, u1 = 1, v1 = 1;
        if (uvMapping != null) {
            u0 = uvMapping.u0;
            v0 = uvMapping.v0;
            u1 = uvMapping.u1;
            v1 = uvMapping.v1;
        }
        
        int baseVertex = currentVertex;
        
        // Sample light for each vertex
        float[] light0 = sampleVertexLight(face, 1, 0);
        float[] light1 = sampleVertexLight(face, 1, 1);
        float[] light2 = sampleVertexLight(face, 1, 2);
        float[] light3 = sampleVertexLight(face, 1, 3);
        
        // 4 vertices for the quad with atlas UVs, normal (0,-1,0) for bottom face, and light data
        addVertex(x0, y0, z0, u0, v0, color, 0, -1, 0, light0[0], light0[1], light0[2]); // 0
        addVertex(x1, y0, z0, u1, v0, color, 0, -1, 0, light1[0], light1[1], light1[2]); // 1
        addVertex(x1, y0, z1, u1, v1, color, 0, -1, 0, light2[0], light2[1], light2[2]); // 2
        addVertex(x0, y0, z1, u0, v1, color, 0, -1, 0, light3[0], light3[1], light3[2]); // 3
        
        // 2 triangles (6 indices)
        addQuadIndices(baseVertex);
        
        currentVertex += 4;
    }
    
    /**
     * Add south face vertices and indices.
     */
    private void addSouthFace(BlockFaceCollector.FaceData face, float[] color, TextureAtlas.UVMapping uvMapping) {
        float x = face.x, y = face.y, z = face.z;
        float x0 = x, x1 = x + 1;
        float y0 = y, y1 = y + 1;
        float z1 = z + 1;
        
        // Get UV coordinates
        float u0 = 0, v0 = 0, u1 = 1, v1 = 1;
        if (uvMapping != null) {
            u0 = uvMapping.u0;
            v0 = uvMapping.v0;
            u1 = uvMapping.u1;
            v1 = uvMapping.v1;
        }
        
        int baseVertex = currentVertex;
        
        // Sample light for each vertex
        float[] light0 = sampleVertexLight(face, 3, 0);
        float[] light1 = sampleVertexLight(face, 3, 1);
        float[] light2 = sampleVertexLight(face, 3, 2);
        float[] light3 = sampleVertexLight(face, 3, 3);
        
        // 4 vertices for the quad with atlas UVs, normal (0,0,1) for south face, and light data
        addVertex(x0, y0, z1, u0, v1, color, 0, 0, 1, light0[0], light0[1], light0[2]); // 0
        addVertex(x1, y0, z1, u1, v1, color, 0, 0, 1, light1[0], light1[1], light1[2]); // 1
        addVertex(x1, y1, z1, u1, v0, color, 0, 0, 1, light2[0], light2[1], light2[2]); // 2
        addVertex(x0, y1, z1, u0, v0, color, 0, 0, 1, light3[0], light3[1], light3[2]); // 3
        
        // 2 triangles (6 indices)
        addQuadIndices(baseVertex);
        
        currentVertex += 4;
    }
    
    /**
     * Add west face vertices and indices.
     */
    private void addWestFace(BlockFaceCollector.FaceData face, float[] color, TextureAtlas.UVMapping uvMapping) {
        float x = face.x, y = face.y, z = face.z;
        float x0 = x;
        float y0 = y, y1 = y + 1;
        float z0 = z, z1 = z + 1;
        
        // Get UV coordinates
        float u0 = 0, v0 = 0, u1 = 1, v1 = 1;
        if (uvMapping != null) {
            u0 = uvMapping.u0;
            v0 = uvMapping.v0;
            u1 = uvMapping.u1;
            v1 = uvMapping.v1;
        }
        
        int baseVertex = currentVertex;
        
        // Sample light for each vertex
        float[] light0 = sampleVertexLight(face, 4, 0);
        float[] light1 = sampleVertexLight(face, 4, 1);
        float[] light2 = sampleVertexLight(face, 4, 2);
        float[] light3 = sampleVertexLight(face, 4, 3);
        
        // 4 vertices for the quad with atlas UVs, normal (-1,0,0) for west face, and light data
        addVertex(x0, y0, z0, u0, v1, color, -1, 0, 0, light0[0], light0[1], light0[2]); // 0
        addVertex(x0, y0, z1, u1, v1, color, -1, 0, 0, light1[0], light1[1], light1[2]); // 1
        addVertex(x0, y1, z1, u1, v0, color, -1, 0, 0, light2[0], light2[1], light2[2]); // 2
        addVertex(x0, y1, z0, u0, v0, color, -1, 0, 0, light3[0], light3[1], light3[2]); // 3
        
        // 2 triangles (6 indices)
        addQuadIndices(baseVertex);
        
        currentVertex += 4;
    }
    
    /**
     * Add east face vertices and indices.
     */
    private void addEastFace(BlockFaceCollector.FaceData face, float[] color, TextureAtlas.UVMapping uvMapping) {
        float x = face.x, y = face.y, z = face.z;
        float x1 = x + 1;
        float y0 = y, y1 = y + 1;
        float z0 = z, z1 = z + 1;
        
        // Get UV coordinates
        float u0 = 0, v0 = 0, u1 = 1, v1 = 1;
        if (uvMapping != null) {
            u0 = uvMapping.u0;
            v0 = uvMapping.v0;
            u1 = uvMapping.u1;
            v1 = uvMapping.v1;
        }
        
        int baseVertex = currentVertex;
        
        // Sample light for each vertex
        float[] light0 = sampleVertexLight(face, 5, 0);
        float[] light1 = sampleVertexLight(face, 5, 1);
        float[] light2 = sampleVertexLight(face, 5, 2);
        float[] light3 = sampleVertexLight(face, 5, 3);
        
        // 4 vertices for the quad with atlas UVs, normal (1,0,0) for east face, and light data
        addVertex(x1, y0, z1, u1, v1, color, 1, 0, 0, light0[0], light0[1], light0[2]); // 0
        addVertex(x1, y0, z0, u0, v1, color, 1, 0, 0, light1[0], light1[1], light1[2]); // 1
        addVertex(x1, y1, z0, u0, v0, color, 1, 0, 0, light2[0], light2[1], light2[2]); // 2
        addVertex(x1, y1, z1, u1, v0, color, 1, 0, 0, light3[0], light3[1], light3[2]); // 3
        
        // 2 triangles (6 indices)
        addQuadIndices(baseVertex);
        
        currentVertex += 4;
    }
    
    /**
     * Add north face vertices and indices.
     */
    private void addNorthFace(BlockFaceCollector.FaceData face, float[] color, TextureAtlas.UVMapping uvMapping) {
        float x = face.x, y = face.y, z = face.z;
        float x0 = x, x1 = x + 1;
        float y0 = y, y1 = y + 1;
        float z0 = z;
        
        // Get UV coordinates
        float u0 = 0, v0 = 0, u1 = 1, v1 = 1;
        if (uvMapping != null) {
            u0 = uvMapping.u0;
            v0 = uvMapping.v0;
            u1 = uvMapping.u1;
            v1 = uvMapping.v1;
        }
        
        int baseVertex = currentVertex;
        
        // Sample light for each vertex
        float[] light0 = sampleVertexLight(face, 2, 0);
        float[] light1 = sampleVertexLight(face, 2, 1);
        float[] light2 = sampleVertexLight(face, 2, 2);
        float[] light3 = sampleVertexLight(face, 2, 3);
        
        // 4 vertices for the quad with atlas UVs, normal (0,0,-1) for north face, and light data
        addVertex(x1, y0, z0, u1, v1, color, 0, 0, -1, light0[0], light0[1], light0[2]); // 0
        addVertex(x0, y0, z0, u0, v1, color, 0, 0, -1, light1[0], light1[1], light1[2]); // 1
        addVertex(x0, y1, z0, u0, v0, color, 0, 0, -1, light2[0], light2[1], light2[2]); // 2
        addVertex(x1, y1, z0, u1, v0, color, 0, 0, -1, light3[0], light3[1], light3[2]); // 3
        
        // 2 triangles (6 indices)
        addQuadIndices(baseVertex);
        
        currentVertex += 4;
    }
    
    /**
     * Add a single vertex to the vertex list with default unused values.
     */
    private void addVertex(float x, float y, float z, float u, float v, float[] color, float nx, float ny, float nz) {
        addVertex(x, y, z, u, v, color, nx, ny, nz, 0.0f, 0.0f, 0.0f);
    }
    
    /**
     * Add a single vertex to the vertex list with normal.
     * Does NOT apply lighting to the color - that's handled by the shader.
     * The color represents the albedo (base color) only.
     * The last three parameters are currently unused but kept for compatibility.
     */
    private void addVertex(float x, float y, float z, float u, float v, float[] color,
                          float nx, float ny, float nz, float unused1, float unused2, float unused3) {
        // Add vertex data (position, uv, color, normal, unused)
        vertices.add(x);
        vertices.add(y);
        vertices.add(z);
        vertices.add(u);
        vertices.add(v);
        vertices.add(color[0]); // r (albedo, no lighting applied)
        vertices.add(color[1]); // g
        vertices.add(color[2]); // b
        vertices.add(color[3]); // a
        vertices.add(nx);  // normal x
        vertices.add(ny);  // normal y
        vertices.add(nz);  // normal z
        vertices.add(unused1);  // unused
        vertices.add(unused2);  // unused
        vertices.add(unused3);  // unused
    }
    
    /**
     * Add indices for a quad (2 triangles).
     * Assumes vertices are in order: 0, 1, 2, 3 (counter-clockwise).
     */
    private void addQuadIndices(int baseVertex) {
        // Triangle 1: 0, 1, 2
        indices.add(baseVertex + 0);
        indices.add(baseVertex + 1);
        indices.add(baseVertex + 2);
        
        // Triangle 2: 0, 2, 3
        indices.add(baseVertex + 0);
        indices.add(baseVertex + 2);
        indices.add(baseVertex + 3);
    }
    
    /**
     * Add stairs geometry based on blockstate (facing and half).
     * Stairs consist of a bottom slab and a top step, rotated based on facing direction.
     */
    private void addStairsGeometry(float x, float y, float z, mattmc.world.level.block.Block block, 
                                    mattmc.world.level.block.state.BlockState state) {
        // Get texture path and UV mapping for stairs
        String texturePath = block.getTexturePath("side");
        if (texturePath == null) {
            texturePath = block.getTexturePath();
        }
        TextureAtlas.UVMapping uvMapping = null;
        if (textureAtlas != null && texturePath != null) {
            uvMapping = textureAtlas.getUVMapping(texturePath);
        }
        
        // White color with appropriate brightness for each face
        float[] colorTop = {1.0f, 1.0f, 1.0f, 1.0f};
        float[] colorBottom = {0.5f, 0.5f, 0.5f, 1.0f};
        float[] colorNorth = {0.8f, 0.8f, 0.8f, 1.0f};
        float[] colorSouth = {0.8f, 0.8f, 0.8f, 1.0f};
        float[] colorWest = {0.6f, 0.6f, 0.6f, 1.0f};
        float[] colorEast = {0.6f, 0.6f, 0.6f, 1.0f};
        
        // Get facing and half from blockstate (default to NORTH and BOTTOM if no state)
        mattmc.world.level.block.state.properties.Direction facing = 
            state != null ? state.getDirection("facing") : mattmc.world.level.block.state.properties.Direction.NORTH;
        mattmc.world.level.block.state.properties.Half half = 
            state != null ? state.getHalf("half") : mattmc.world.level.block.state.properties.Half.BOTTOM;
        
        // Render based on half (top or bottom)
        if (half == mattmc.world.level.block.state.properties.Half.BOTTOM) {
            // Bottom stairs: slab on bottom, step on top
            addStairsBottomSlabFace(x, y, z, 0.5f, facing, colorTop, colorBottom, colorNorth, colorSouth, colorWest, colorEast, uvMapping);
            addStairsTopStepFace(x, y + 0.5f, z, facing, colorTop, colorBottom, colorNorth, colorSouth, colorWest, colorEast, uvMapping);
        } else {
            // Top stairs: slab on top, step on bottom  
            addStairsBottomSlabFace(x, y + 0.5f, z, 0.5f, facing, colorTop, colorBottom, colorNorth, colorSouth, colorWest, colorEast, uvMapping);
            addStairsTopStepFace(x, y, z, facing, colorTop, colorBottom, colorNorth, colorSouth, colorWest, colorEast, uvMapping);
        }
    }
    
    /**
     * Add bottom slab faces for stairs.
     * The facing parameter determines which direction the step faces.
     */
    private void addStairsBottomSlabFace(float x, float y, float z, float height,
                                          mattmc.world.level.block.state.properties.Direction facing,
                                          float[] colorTop, float[] colorBottom, 
                                          float[] colorNorth, float[] colorSouth,
                                          float[] colorWest, float[] colorEast,
                                          TextureAtlas.UVMapping uvMapping) {
        float x0 = x, x1 = x + 1;
        float y0 = y, y1 = y + height;
        float z0 = z, z1 = z + 1;
        
        float u0 = 0, v0 = 0, u1 = 1, v1 = 1;
        if (uvMapping != null) {
            u0 = uvMapping.u0;
            v0 = uvMapping.v0;
            u1 = uvMapping.u1;
            v1 = uvMapping.v1;
        }
        
        // Calculate mid-point for half-height textures
        float v05 = (v0 + v1) / 2.0f;
        
        // Top face of slab (full texture)
        int base = currentVertex;
        addVertex(x0, y1, z0, u0, v0, colorTop, 0, 1, 0);
        addVertex(x0, y1, z1, u0, v1, colorTop, 0, 1, 0);
        addVertex(x1, y1, z1, u1, v1, colorTop, 0, 1, 0);
        addVertex(x1, y1, z0, u1, v0, colorTop, 0, 1, 0);
        addQuadIndices(base);
        currentVertex += 4;
        
        // Bottom face (full texture)
        base = currentVertex;
        addVertex(x0, y0, z0, u0, v0, colorBottom, 0, -1, 0);
        addVertex(x1, y0, z0, u1, v0, colorBottom, 0, -1, 0);
        addVertex(x1, y0, z1, u1, v1, colorBottom, 0, -1, 0);
        addVertex(x0, y0, z1, u0, v1, colorBottom, 0, -1, 0);
        addQuadIndices(base);
        currentVertex += 4;
        
        // Side faces - all full width/depth, half height
        // North face
        base = currentVertex;
        addVertex(x1, y0, z0, u1, v1, colorNorth, 0, 0, -1);
        addVertex(x0, y0, z0, u0, v1, colorNorth, 0, 0, -1);
        addVertex(x0, y1, z0, u0, v05, colorNorth, 0, 0, -1);
        addVertex(x1, y1, z0, u1, v05, colorNorth, 0, 0, -1);
        addQuadIndices(base);
        currentVertex += 4;
        
        // South face
        base = currentVertex;
        addVertex(x0, y0, z1, u0, v1, colorSouth, 0, 0, 1);
        addVertex(x1, y0, z1, u1, v1, colorSouth, 0, 0, 1);
        addVertex(x1, y1, z1, u1, v05, colorSouth, 0, 0, 1);
        addVertex(x0, y1, z1, u0, v05, colorSouth, 0, 0, 1);
        addQuadIndices(base);
        currentVertex += 4;
        
        // West face
        base = currentVertex;
        addVertex(x0, y0, z0, u0, v1, colorWest, -1, 0, 0);
        addVertex(x0, y0, z1, u1, v1, colorWest, -1, 0, 0);
        addVertex(x0, y1, z1, u1, v05, colorWest, -1, 0, 0);
        addVertex(x0, y1, z0, u0, v05, colorWest, -1, 0, 0);
        addQuadIndices(base);
        currentVertex += 4;
        
        // East face
        base = currentVertex;
        addVertex(x1, y0, z1, u1, v1, colorEast, 1, 0, 0);
        addVertex(x1, y0, z0, u0, v1, colorEast, 1, 0, 0);
        addVertex(x1, y1, z0, u0, v05, colorEast, 1, 0, 0);
        addVertex(x1, y1, z1, u1, v05, colorEast, 1, 0, 0);
        addQuadIndices(base);
        currentVertex += 4;
    }
    
    /**
     * Add top step faces for stairs (based on facing direction).
     */
    private void addStairsTopStepFace(float x, float y, float z,
                                       mattmc.world.level.block.state.properties.Direction facing,
                                       float[] colorTop, float[] colorBottom, float[] colorNorth, float[] colorSouth,
                                       float[] colorWest, float[] colorEast,
                                       TextureAtlas.UVMapping uvMapping) {
        float x0 = x, x05 = x + 0.5f, x1 = x + 1;
        float y0 = y, y1 = y + 0.5f;
        float z0 = z, z05 = z + 0.5f, z1 = z + 1;
        
        float u0 = 0, v0 = 0, u05 = 0.5f, u1 = 1, v05 = 0.5f, v1 = 1;
        if (uvMapping != null) {
            u0 = uvMapping.u0;
            v0 = uvMapping.v0;
            u05 = (uvMapping.u0 + uvMapping.u1) / 2;
            u1 = uvMapping.u1;
            v05 = (uvMapping.v0 + uvMapping.v1) / 2;
            v1 = uvMapping.v1;
        }
        
        // Render step based on facing direction
        // NORTH: step in north half (z0 to z05)
        // SOUTH: step in south half (z05 to z1)
        // WEST: step in west half (x0 to x05)
        // EAST: step in east half (x05 to x1)
        
        switch (facing) {
            case NORTH -> addStairsStepNorth(x0, x1, y0, y1, z0, z05, u0, u05, u1, v0, v05, v1, 
                                              colorTop, colorBottom, colorNorth, colorSouth, colorWest, colorEast);
            case SOUTH -> addStairsStepSouth(x0, x1, y0, y1, z05, z1, u0, u05, u1, v0, v05, v1,
                                              colorTop, colorBottom, colorNorth, colorSouth, colorWest, colorEast);
            case WEST -> addStairsStepWest(x0, x05, y0, y1, z0, z1, u0, u05, u1, v0, v05, v1,
                                            colorTop, colorBottom, colorNorth, colorSouth, colorWest, colorEast);
            case EAST -> addStairsStepEast(x05, x1, y0, y1, z0, z1, u0, u05, u1, v0, v05, v1,
                                            colorTop, colorBottom, colorNorth, colorSouth, colorWest, colorEast);
        }
    }
    
    /**
     * Add step geometry for north-facing stairs (step in north half).
     */
    private void addStairsStepNorth(float x0, float x1, float y0, float y1, float z0, float z05,
                                     float u0, float u05, float u1, float v0, float v05, float v1,
                                     float[] colorTop, float[] colorBottom, float[] colorNorth, float[] colorSouth,
                                     float[] colorWest, float[] colorEast) {
        // Top face (north half)
        int base = currentVertex;
        addVertex(x0, y1, z0, u0, v0, colorTop, 0, 1, 0);
        addVertex(x0, y1, z05, u0, v05, colorTop, 0, 1, 0);
        addVertex(x1, y1, z05, u1, v05, colorTop, 0, 1, 0);
        addVertex(x1, y1, z0, u1, v0, colorTop, 0, 1, 0);
        addQuadIndices(base);
        currentVertex += 4;
        
        // Bottom face (north half)
        base = currentVertex;
        addVertex(x0, y0, z0, u0, v0, colorBottom, 0, -1, 0);
        addVertex(x1, y0, z0, u1, v0, colorBottom, 0, -1, 0);
        addVertex(x1, y0, z05, u1, v05, colorBottom, 0, -1, 0);
        addVertex(x0, y0, z05, u0, v05, colorBottom, 0, -1, 0);
        addQuadIndices(base);
        currentVertex += 4;
        
        // North face (front of step)
        base = currentVertex;
        addVertex(x1, y0, z0, u1, v05, colorNorth, 0, -1, 0);
        addVertex(x0, y0, z0, u0, v05, colorNorth, 0, -1, 0);
        addVertex(x0, y1, z0, u0, v0, colorNorth, 0, 1, 0);
        addVertex(x1, y1, z0, u1, v0, colorNorth, 0, 1, 0);
        addQuadIndices(base);
        currentVertex += 4;
        
        // South face (inner vertical)
        base = currentVertex;
        addVertex(x0, y0, z05, u0, v05, colorSouth, 0, -1, 0);
        addVertex(x1, y0, z05, u1, v05, colorSouth, 0, -1, 0);
        addVertex(x1, y1, z05, u1, v0, colorSouth, 0, 1, 0);
        addVertex(x0, y1, z05, u0, v0, colorSouth, 0, 1, 0);
        addQuadIndices(base);
        currentVertex += 4;
        
        // West face (left side, half depth)
        base = currentVertex;
        addVertex(x0, y0, z0, u0, v05, colorWest, 0, -1, 0);
        addVertex(x0, y0, z05, u05, v05, colorWest, 0, -1, 0);
        addVertex(x0, y1, z05, u05, v0, colorWest, 0, 1, 0);
        addVertex(x0, y1, z0, u0, v0, colorWest, 0, 1, 0);
        addQuadIndices(base);
        currentVertex += 4;
        
        // East face (right side, half depth)
        base = currentVertex;
        addVertex(x1, y0, z05, u05, v05, colorEast, 0, -1, 0);
        addVertex(x1, y0, z0, u0, v05, colorEast, 0, -1, 0);
        addVertex(x1, y1, z0, u0, v0, colorEast, 0, 1, 0);
        addVertex(x1, y1, z05, u05, v0, colorEast, 0, 1, 0);
        addQuadIndices(base);
        currentVertex += 4;
    }
    
    /**
     * Add step geometry for south-facing stairs (step in south half).
     */
    private void addStairsStepSouth(float x0, float x1, float y0, float y1, float z05, float z1,
                                     float u0, float u05, float u1, float v0, float v05, float v1,
                                     float[] colorTop, float[] colorBottom, float[] colorNorth, float[] colorSouth,
                                     float[] colorWest, float[] colorEast) {
        // Top face (south half)
        int base = currentVertex;
        addVertex(x0, y1, z05, u0, v05, colorTop, 0, 1, 0);
        addVertex(x0, y1, z1, u0, v1, colorTop, 0, 1, 0);
        addVertex(x1, y1, z1, u1, v1, colorTop, 0, 1, 0);
        addVertex(x1, y1, z05, u1, v05, colorTop, 0, 1, 0);
        addQuadIndices(base);
        currentVertex += 4;
        
        // Bottom face (south half)
        base = currentVertex;
        addVertex(x0, y0, z05, u0, v05, colorBottom, 0, -1, 0);
        addVertex(x1, y0, z05, u1, v05, colorBottom, 0, -1, 0);
        addVertex(x1, y0, z1, u1, v1, colorBottom, 0, -1, 0);
        addVertex(x0, y0, z1, u0, v1, colorBottom, 0, -1, 0);
        addQuadIndices(base);
        currentVertex += 4;
        
        // North face (inner vertical)
        base = currentVertex;
        addVertex(x1, y0, z05, u1, v05, colorNorth, 0, -1, 0);
        addVertex(x0, y0, z05, u0, v05, colorNorth, 0, -1, 0);
        addVertex(x0, y1, z05, u0, v0, colorNorth, 0, 1, 0);
        addVertex(x1, y1, z05, u1, v0, colorNorth, 0, 1, 0);
        addQuadIndices(base);
        currentVertex += 4;
        
        // South face (front of step)
        base = currentVertex;
        addVertex(x0, y0, z1, u0, v05, colorSouth, 0, -1, 0);
        addVertex(x1, y0, z1, u1, v05, colorSouth, 0, -1, 0);
        addVertex(x1, y1, z1, u1, v0, colorSouth, 0, 1, 0);
        addVertex(x0, y1, z1, u0, v0, colorSouth, 0, 1, 0);
        addQuadIndices(base);
        currentVertex += 4;
        
        // West face (left side, half depth)
        base = currentVertex;
        addVertex(x0, y0, z05, u05, v05, colorWest, 0, -1, 0);
        addVertex(x0, y0, z1, u1, v05, colorWest, 0, -1, 0);
        addVertex(x0, y1, z1, u1, v0, colorWest, 0, 1, 0);
        addVertex(x0, y1, z05, u05, v0, colorWest, 0, 1, 0);
        addQuadIndices(base);
        currentVertex += 4;
        
        // East face (right side, half depth)
        base = currentVertex;
        addVertex(x1, y0, z1, u1, v05, colorEast, 0, -1, 0);
        addVertex(x1, y0, z05, u05, v05, colorEast, 0, -1, 0);
        addVertex(x1, y1, z05, u05, v0, colorEast, 0, 1, 0);
        addVertex(x1, y1, z1, u1, v0, colorEast, 0, 1, 0);
        addQuadIndices(base);
        currentVertex += 4;
    }
    
    /**
     * Add step geometry for west-facing stairs (step in west half).
     */
    private void addStairsStepWest(float x0, float x05, float y0, float y1, float z0, float z1,
                                    float u0, float u05, float u1, float v0, float v05, float v1,
                                    float[] colorTop, float[] colorBottom, float[] colorNorth, float[] colorSouth,
                                    float[] colorWest, float[] colorEast) {
        // Top face (west half)
        int base = currentVertex;
        addVertex(x0, y1, z0, u0, v0, colorTop, 0, 1, 0);
        addVertex(x0, y1, z1, u0, v1, colorTop, 0, 1, 0);
        addVertex(x05, y1, z1, u05, v1, colorTop, 0, 1, 0);
        addVertex(x05, y1, z0, u05, v0, colorTop, 0, 1, 0);
        addQuadIndices(base);
        currentVertex += 4;
        
        // Bottom face (west half)
        base = currentVertex;
        addVertex(x0, y0, z0, u0, v0, colorBottom, 0, -1, 0);
        addVertex(x05, y0, z0, u05, v0, colorBottom, 0, -1, 0);
        addVertex(x05, y0, z1, u05, v1, colorBottom, 0, -1, 0);
        addVertex(x0, y0, z1, u0, v1, colorBottom, 0, -1, 0);
        addQuadIndices(base);
        currentVertex += 4;
        
        // North face (left side, half width)
        base = currentVertex;
        addVertex(x05, y0, z0, u05, v05, colorNorth, 0, -1, 0);
        addVertex(x0, y0, z0, u0, v05, colorNorth, 0, -1, 0);
        addVertex(x0, y1, z0, u0, v0, colorNorth, 0, 1, 0);
        addVertex(x05, y1, z0, u05, v0, colorNorth, 0, 1, 0);
        addQuadIndices(base);
        currentVertex += 4;
        
        // South face (right side, half width)
        base = currentVertex;
        addVertex(x0, y0, z1, u0, v05, colorSouth, 0, -1, 0);
        addVertex(x05, y0, z1, u05, v05, colorSouth, 0, -1, 0);
        addVertex(x05, y1, z1, u05, v0, colorSouth, 0, 1, 0);
        addVertex(x0, y1, z1, u0, v0, colorSouth, 0, 1, 0);
        addQuadIndices(base);
        currentVertex += 4;
        
        // West face (front of step)
        base = currentVertex;
        addVertex(x0, y0, z0, u0, v05, colorWest, 0, -1, 0);
        addVertex(x0, y0, z1, u1, v05, colorWest, 0, -1, 0);
        addVertex(x0, y1, z1, u1, v0, colorWest, 0, 1, 0);
        addVertex(x0, y1, z0, u0, v0, colorWest, 0, 1, 0);
        addQuadIndices(base);
        currentVertex += 4;
        
        // East face (inner vertical)
        base = currentVertex;
        addVertex(x05, y0, z1, u1, v05, colorEast, 0, -1, 0);
        addVertex(x05, y0, z0, u0, v05, colorEast, 0, -1, 0);
        addVertex(x05, y1, z0, u0, v0, colorEast, 0, 1, 0);
        addVertex(x05, y1, z1, u1, v0, colorEast, 0, 1, 0);
        addQuadIndices(base);
        currentVertex += 4;
    }
    
    /**
     * Add step geometry for east-facing stairs (step in east half).
     */
    private void addStairsStepEast(float x05, float x1, float y0, float y1, float z0, float z1,
                                    float u0, float u05, float u1, float v0, float v05, float v1,
                                    float[] colorTop, float[] colorBottom, float[] colorNorth, float[] colorSouth,
                                    float[] colorWest, float[] colorEast) {
        // Top face (east half)
        int base = currentVertex;
        addVertex(x05, y1, z0, u05, v0, colorTop, 0, 1, 0);
        addVertex(x05, y1, z1, u05, v1, colorTop, 0, 1, 0);
        addVertex(x1, y1, z1, u1, v1, colorTop, 0, 1, 0);
        addVertex(x1, y1, z0, u1, v0, colorTop, 0, 1, 0);
        addQuadIndices(base);
        currentVertex += 4;
        
        // Bottom face (east half)
        base = currentVertex;
        addVertex(x05, y0, z0, u05, v0, colorBottom, 0, -1, 0);
        addVertex(x1, y0, z0, u1, v0, colorBottom, 0, -1, 0);
        addVertex(x1, y0, z1, u1, v1, colorBottom, 0, -1, 0);
        addVertex(x05, y0, z1, u05, v1, colorBottom, 0, -1, 0);
        addQuadIndices(base);
        currentVertex += 4;
        
        // North face (right side, half width)
        base = currentVertex;
        addVertex(x1, y0, z0, u1, v05, colorNorth, 0, -1, 0);
        addVertex(x05, y0, z0, u05, v05, colorNorth, 0, -1, 0);
        addVertex(x05, y1, z0, u05, v0, colorNorth, 0, 1, 0);
        addVertex(x1, y1, z0, u1, v0, colorNorth, 0, 1, 0);
        addQuadIndices(base);
        currentVertex += 4;
        
        // South face (left side, half width)
        base = currentVertex;
        addVertex(x05, y0, z1, u05, v05, colorSouth, 0, -1, 0);
        addVertex(x1, y0, z1, u1, v05, colorSouth, 0, -1, 0);
        addVertex(x1, y1, z1, u1, v0, colorSouth, 0, 1, 0);
        addVertex(x05, y1, z1, u05, v0, colorSouth, 0, 1, 0);
        addQuadIndices(base);
        currentVertex += 4;
        
        // West face (inner vertical)
        base = currentVertex;
        addVertex(x05, y0, z0, u0, v05, colorWest, 0, -1, 0);
        addVertex(x05, y0, z1, u1, v05, colorWest, 0, -1, 0);
        addVertex(x05, y1, z1, u1, v0, colorWest, 0, 1, 0);
        addVertex(x05, y1, z0, u0, v0, colorWest, 0, 1, 0);
        addQuadIndices(base);
        currentVertex += 4;
        
        // East face (front of step)
        base = currentVertex;
        addVertex(x1, y0, z1, u1, v05, colorEast, 0, -1, 0);
        addVertex(x1, y0, z0, u0, v05, colorEast, 0, -1, 0);
        addVertex(x1, y1, z0, u0, v0, colorEast, 0, 1, 0);
        addVertex(x1, y1, z1, u1, v0, colorEast, 0, 1, 0);
        addQuadIndices(base);
        currentVertex += 4;
    }
}
