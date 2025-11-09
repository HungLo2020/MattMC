package mattmc.client.renderer.chunk;

import mattmc.client.renderer.ColorUtils;
import mattmc.client.renderer.block.BlockFaceCollector;
import mattmc.client.renderer.texture.TextureAtlas;
import mattmc.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds vertex and index arrays from collected block faces.
 * Converts BlockFaceCollector data into a format suitable for VBO/VAO rendering.
 * Supports texture atlas UV mapping for multi-texture VBO rendering.
 */
public class MeshBuilder {
    
    // Vertex format: x, y, z, u, v, r, g, b, a (9 floats per vertex)
    private final List<Float> vertices = new ArrayList<>();
    private final List<Integer> indices = new ArrayList<>();
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
     */
    public ChunkMeshBuffer build(int chunkX, int chunkZ, BlockFaceCollector collector) {
        vertices.clear();
        indices.clear();
        currentVertex = 0;
        
        // Process all face types - each list knows its orientation
        addFacesOfType(collector.getTopFaces(), FaceType.TOP);
        addFacesOfType(collector.getBottomFaces(), FaceType.BOTTOM);
        addFacesOfType(collector.getNorthFaces(), FaceType.NORTH);
        addFacesOfType(collector.getSouthFaces(), FaceType.SOUTH);
        addFacesOfType(collector.getWestFaces(), FaceType.WEST);
        addFacesOfType(collector.getEastFaces(), FaceType.EAST);
        
        // Convert lists to arrays
        float[] vertexArray = new float[vertices.size()];
        for (int i = 0; i < vertices.size(); i++) {
            vertexArray[i] = vertices.get(i);
        }
        
        int[] indexArray = new int[indices.size()];
        for (int i = 0; i < indices.size(); i++) {
            indexArray[i] = indices.get(i);
        }
        
        return new ChunkMeshBuffer(chunkX, chunkZ, vertexArray, indexArray);
    }
    
    /**
     * Face orientation enum.
     */
    private enum FaceType {
        TOP, BOTTOM, NORTH, SOUTH, WEST, EAST
    }
    
    /**
     * Add faces of a specific type to the mesh.
     */
    private void addFacesOfType(List<BlockFaceCollector.FaceData> faces, FaceType type) {
        for (BlockFaceCollector.FaceData face : faces) {
            // Check if this is a stairs block (special marker)
            if ("stairs".equals(face.faceType)) {
                // Add stairs geometry instead of regular face, passing blockstate
                addStairsGeometry(face.x, face.y, face.z, face.block, face.blockState);
                continue;
            }
            
            // Extract color components and UV mapping
            float[] color = extractColor(face);
            TextureAtlas.UVMapping uvMapping = getUVMapping(face);
            
            // Add the face with correct orientation
            switch (type) {
                case TOP -> addTopFace(face.x, face.y, face.z, color, uvMapping);
                case BOTTOM -> addBottomFace(face.x, face.y, face.z, color, uvMapping);
                case NORTH -> addNorthFace(face.x, face.y, face.z, color, uvMapping);
                case SOUTH -> addSouthFace(face.x, face.y, face.z, color, uvMapping);
                case WEST -> addWestFace(face.x, face.y, face.z, color, uvMapping);
                case EAST -> addEastFace(face.x, face.y, face.z, color, uvMapping);
            }
        }
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
     * Add top face vertices and indices.
     */
    private void addTopFace(float x, float y, float z, float[] color, TextureAtlas.UVMapping uvMapping) {
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
        
        // 4 vertices for the quad with atlas UVs
        addVertex(x0, y1, z0, u0, v0, color); // 0
        addVertex(x0, y1, z1, u0, v1, color); // 1
        addVertex(x1, y1, z1, u1, v1, color); // 2
        addVertex(x1, y1, z0, u1, v0, color); // 3
        
        // 2 triangles (6 indices)
        addQuadIndices(baseVertex);
        
        currentVertex += 4;
    }
    
    /**
     * Add bottom face vertices and indices.
     */
    private void addBottomFace(float x, float y, float z, float[] color, TextureAtlas.UVMapping uvMapping) {
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
        
        // 4 vertices for the quad with atlas UVs
        addVertex(x0, y0, z0, u0, v0, color); // 0
        addVertex(x1, y0, z0, u1, v0, color); // 1
        addVertex(x1, y0, z1, u1, v1, color); // 2
        addVertex(x0, y0, z1, u0, v1, color); // 3
        
        // 2 triangles (6 indices)
        addQuadIndices(baseVertex);
        
        currentVertex += 4;
    }
    
    /**
     * Add south face vertices and indices.
     */
    private void addSouthFace(float x, float y, float z, float[] color, TextureAtlas.UVMapping uvMapping) {
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
        
        // 4 vertices for the quad with atlas UVs
        addVertex(x0, y0, z1, u0, v1, color); // 0
        addVertex(x1, y0, z1, u1, v1, color); // 1
        addVertex(x1, y1, z1, u1, v0, color); // 2
        addVertex(x0, y1, z1, u0, v0, color); // 3
        
        // 2 triangles (6 indices)
        addQuadIndices(baseVertex);
        
        currentVertex += 4;
    }
    
    /**
     * Add west face vertices and indices.
     */
    private void addWestFace(float x, float y, float z, float[] color, TextureAtlas.UVMapping uvMapping) {
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
        
        // 4 vertices for the quad with atlas UVs
        addVertex(x0, y0, z0, u0, v1, color); // 0
        addVertex(x0, y0, z1, u1, v1, color); // 1
        addVertex(x0, y1, z1, u1, v0, color); // 2
        addVertex(x0, y1, z0, u0, v0, color); // 3
        
        // 2 triangles (6 indices)
        addQuadIndices(baseVertex);
        
        currentVertex += 4;
    }
    
    /**
     * Add east face vertices and indices.
     */
    private void addEastFace(float x, float y, float z, float[] color, TextureAtlas.UVMapping uvMapping) {
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
        
        // 4 vertices for the quad with atlas UVs
        addVertex(x1, y0, z1, u1, v1, color); // 0
        addVertex(x1, y0, z0, u0, v1, color); // 1
        addVertex(x1, y1, z0, u0, v0, color); // 2
        addVertex(x1, y1, z1, u1, v0, color); // 3
        
        // 2 triangles (6 indices)
        addQuadIndices(baseVertex);
        
        currentVertex += 4;
    }
    
    /**
     * Add north face vertices and indices.
     */
    private void addNorthFace(float x, float y, float z, float[] color, TextureAtlas.UVMapping uvMapping) {
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
        
        // 4 vertices for the quad with atlas UVs
        addVertex(x1, y0, z0, u1, v1, color); // 0
        addVertex(x0, y0, z0, u0, v1, color); // 1
        addVertex(x0, y1, z0, u0, v0, color); // 2
        addVertex(x1, y1, z0, u1, v0, color); // 3
        
        // 2 triangles (6 indices)
        addQuadIndices(baseVertex);
        
        currentVertex += 4;
    }
    
    /**
     * Add a single vertex to the vertex list.
     */
    private void addVertex(float x, float y, float z, float u, float v, float[] color) {
        vertices.add(x);
        vertices.add(y);
        vertices.add(z);
        vertices.add(u);
        vertices.add(v);
        vertices.add(color[0]); // r
        vertices.add(color[1]); // g
        vertices.add(color[2]); // b
        vertices.add(color[3]); // a
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
            addStairsTopStepFace(x, y + 0.5f, z, facing, colorTop, colorNorth, colorSouth, colorWest, colorEast, uvMapping);
        } else {
            // Top stairs: slab on top, step on bottom  
            addStairsBottomSlabFace(x, y + 0.5f, z, 0.5f, facing, colorTop, colorBottom, colorNorth, colorSouth, colorWest, colorEast, uvMapping);
            addStairsTopStepFace(x, y, z, facing, colorTop, colorNorth, colorSouth, colorWest, colorEast, uvMapping);
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
        addVertex(x0, y1, z0, u0, v0, colorTop);
        addVertex(x0, y1, z1, u0, v1, colorTop);
        addVertex(x1, y1, z1, u1, v1, colorTop);
        addVertex(x1, y1, z0, u1, v0, colorTop);
        addQuadIndices(base);
        currentVertex += 4;
        
        // Bottom face (full texture)
        base = currentVertex;
        addVertex(x0, y0, z0, u0, v0, colorBottom);
        addVertex(x1, y0, z0, u1, v0, colorBottom);
        addVertex(x1, y0, z1, u1, v1, colorBottom);
        addVertex(x0, y0, z1, u0, v1, colorBottom);
        addQuadIndices(base);
        currentVertex += 4;
        
        // North face (only use bottom half of texture since slab is half height)
        base = currentVertex;
        addVertex(x1, y0, z0, u1, v1, colorNorth);
        addVertex(x0, y0, z0, u0, v1, colorNorth);
        addVertex(x0, y1, z0, u0, v05, colorNorth);
        addVertex(x1, y1, z0, u1, v05, colorNorth);
        addQuadIndices(base);
        currentVertex += 4;
        
        // South face (only use bottom half of texture)
        base = currentVertex;
        addVertex(x0, y0, z1, u0, v1, colorSouth);
        addVertex(x1, y0, z1, u1, v1, colorSouth);
        addVertex(x1, y1, z1, u1, v05, colorSouth);
        addVertex(x0, y1, z1, u0, v05, colorSouth);
        addQuadIndices(base);
        currentVertex += 4;
        
        // West face (only use bottom half of texture)
        base = currentVertex;
        addVertex(x0, y0, z0, u0, v1, colorWest);
        addVertex(x0, y0, z1, u1, v1, colorWest);
        addVertex(x0, y1, z1, u1, v05, colorWest);
        addVertex(x0, y1, z0, u0, v05, colorWest);
        addQuadIndices(base);
        currentVertex += 4;
        
        // East face (only use bottom half of texture)
        base = currentVertex;
        addVertex(x1, y0, z1, u1, v1, colorEast);
        addVertex(x1, y0, z0, u0, v1, colorEast);
        addVertex(x1, y1, z0, u0, v05, colorEast);
        addVertex(x1, y1, z1, u1, v05, colorEast);
        addQuadIndices(base);
        currentVertex += 4;
    }
    
    /**
     * Add top step faces for stairs (based on facing direction).
     */
    private void addStairsTopStepFace(float x, float y, float z,
                                       mattmc.world.level.block.state.properties.Direction facing,
                                       float[] colorTop, float[] colorNorth, float[] colorSouth,
                                       float[] colorWest, float[] colorEast,
                                       TextureAtlas.UVMapping uvMapping) {
        float x0 = x, x1 = x + 1;
        float y0 = y, y1 = y + 0.5f;
        float z0 = z, z05 = z + 0.5f;
        
        float u0 = 0, v0 = 0, u05 = 0.5f, u1 = 1, v05 = 0.5f, v1 = 1;
        if (uvMapping != null) {
            u0 = uvMapping.u0;
            v0 = uvMapping.v0;
            u05 = (uvMapping.u0 + uvMapping.u1) / 2;
            u1 = uvMapping.u1;
            v05 = (uvMapping.v0 + uvMapping.v1) / 2;
            v1 = uvMapping.v1;
        }
        
        // Top face of step (north half only - use half depth of texture)
        int base = currentVertex;
        addVertex(x0, y1, z0, u0, v0, colorTop);
        addVertex(x0, y1, z05, u0, v05, colorTop);
        addVertex(x1, y1, z05, u1, v05, colorTop);
        addVertex(x1, y1, z0, u1, v0, colorTop);
        addQuadIndices(base);
        currentVertex += 4;
        
        // North face of step (full height - use top half of texture since this is upper step)
        base = currentVertex;
        addVertex(x1, y0, z0, u1, v05, colorNorth);
        addVertex(x0, y0, z0, u0, v05, colorNorth);
        addVertex(x0, y1, z0, u0, v0, colorNorth);
        addVertex(x1, y1, z0, u1, v0, colorNorth);
        addQuadIndices(base);
        currentVertex += 4;
        
        // South face of step (inner vertical face - use top half of texture)
        base = currentVertex;
        addVertex(x0, y0, z05, u0, v05, colorSouth);
        addVertex(x1, y0, z05, u1, v05, colorSouth);
        addVertex(x1, y1, z05, u1, v0, colorSouth);
        addVertex(x0, y1, z05, u0, v0, colorSouth);
        addQuadIndices(base);
        currentVertex += 4;
        
        // West face of step (half depth, use top half of texture vertically)
        base = currentVertex;
        addVertex(x0, y0, z0, u0, v05, colorWest);
        addVertex(x0, y0, z05, u05, v05, colorWest);
        addVertex(x0, y1, z05, u05, v0, colorWest);
        addVertex(x0, y1, z0, u0, v0, colorWest);
        addQuadIndices(base);
        currentVertex += 4;
        
        // East face of step (half depth, use top half of texture vertically)
        base = currentVertex;
        addVertex(x1, y0, z05, u05, v05, colorEast);
        addVertex(x1, y0, z0, u0, v05, colorEast);
        addVertex(x1, y1, z0, u0, v0, colorEast);
        addVertex(x1, y1, z05, u05, v0, colorEast);
        addQuadIndices(base);
        currentVertex += 4;
    }
}
