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
    
    /**
     * Interface for sampling light values across chunk boundaries.
     */
    public interface ChunkLightAccessor {
        /**
         * Get skylight level at chunk-local coordinates, checking neighboring chunks if necessary.
         * @param chunk The current chunk
         * @param x Chunk-local X coordinate (can be outside 0-15 range)
         * @param y Chunk-local Y coordinate (0-383)
         * @param z Chunk-local Z coordinate (can be outside 0-15 range)
         * @return Skylight level (0-15)
         */
        int getSkyLightAcrossChunks(mattmc.world.level.chunk.LevelChunk chunk, int x, int y, int z);
        
        /**
         * Get blocklight level at chunk-local coordinates, checking neighboring chunks if necessary.
         * @param chunk The current chunk
         * @param x Chunk-local X coordinate (can be outside 0-15 range)
         * @param y Chunk-local Y coordinate (0-383)
         * @param z Chunk-local Z coordinate (can be outside 0-15 range)
         * @return Blocklight level (0-15)
         */
        int getBlockLightAcrossChunks(mattmc.world.level.chunk.LevelChunk chunk, int x, int y, int z);
    }
    
    // Vertex format: x, y, z, u, v, r, g, b, a, nx, ny, nz, unused1, unused2, unused3 (15 floats per vertex)
    // Using primitive arrays to avoid boxing/unboxing overhead
    private final FloatList vertices = new FloatList();
    private final IntList indices = new IntList();
    private int currentVertex = 0;
    private final TextureAtlas textureAtlas;
    private ChunkLightAccessor lightAccessor;
    
    /**
     * Create a mesh builder with optional texture atlas support.
     * 
     * @param textureAtlas Texture atlas for UV mapping, or null to use fallback colors
     */
    public MeshBuilder(TextureAtlas textureAtlas) {
        this.textureAtlas = textureAtlas;
    }
    
    /**
     * Set the light accessor for cross-chunk light sampling.
     */
    public void setLightAccessor(ChunkLightAccessor accessor) {
        this.lightAccessor = accessor;
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
     * Sample light for a vertex using 8-sample smooth lighting.
     * 
     * For each vertex, we sample light from the 3 adjacent faces + 1 diagonal corner.
     * This creates smooth lighting gradients across block edges without banding.
     * 
     * @param face The face data containing chunk reference and position
     * @param normalIndex Which face (0=top, 1=bottom, 2=north, 3=south, 4=west, 5=east)
     * @param cornerIndex Which corner of the face (0-3)
     * @return [skyLight, blockLight, ao] as floats (0-15 for light, 0-3 for ao)
     */
    private float[] sampleVertexLight(BlockFaceCollector.FaceData face, 
                                      int normalIndex,
                                      int cornerIndex) {
        // If no chunk reference, return default lighting
        if (face.chunk == null) {
            return new float[] {15.0f, 0.0f, 0.0f}; // Full skylight, no blocklight, no AO
        }
        
        // Get chunk-local coordinates of the block
        int cx = face.cx;
        int cy = face.cy;
        int cz = face.cz;
        
        // Sample light from 4 positions around the vertex (3 sides + 1 diagonal)
        // The positions depend on which face and which corner we're sampling
        int[] offsets = getVertexSampleOffsets(normalIndex, cornerIndex);
        
        float skyLightSum = 0;
        float blockLightSum = 0;
        int samples = 0;
        
        // Sample 4 positions (3 adjacent + 1 diagonal)
        for (int i = 0; i < 4; i++) {
            int dx = offsets[i * 3];
            int dy = offsets[i * 3 + 1];
            int dz = offsets[i * 3 + 2];
            
            int sx = cx + dx;
            int sy = cy + dy;
            int sz = cz + dz;
            
            // Sample light at this position
            int skyLight = getSkyLightSafe(face.chunk, sx, sy, sz);
            int blockLight = getBlockLightSafe(face.chunk, sx, sy, sz);
            
            skyLightSum += skyLight;
            blockLightSum += blockLight;
            samples++;
        }
        
        // Average the samples
        float avgSkyLight = skyLightSum / samples;
        float avgBlockLight = blockLightSum / samples;
        float ao = 0.0f; // No AO yet
        
        return new float[] {avgSkyLight, avgBlockLight, ao};
    }
    
    /**
     * Get offsets for the 4 sample positions for a vertex (3 sides + 1 diagonal).
     * Returns array of 12 ints: [dx0,dy0,dz0, dx1,dy1,dz1, dx2,dy2,dz2, dx3,dy3,dz3]
     */
    private int[] getVertexSampleOffsets(int normalIndex, int cornerIndex) {
        // For each face and corner, define the 4 sampling positions
        // Format: [dx,dy,dz, dx,dy,dz, dx,dy,dz, dx,dy,dz]
        
        // Top face (normal = 0,1,0)
        if (normalIndex == 0) {
            switch (cornerIndex) {
                case 0: return new int[] {0,1,0, -1,1,0, 0,1,-1, -1,1,-1}; // x0, z0
                case 1: return new int[] {0,1,0, -1,1,0, 0,1,1, -1,1,1};   // x0, z1
                case 2: return new int[] {0,1,0, 1,1,0, 0,1,1, 1,1,1};     // x1, z1
                case 3: return new int[] {0,1,0, 1,1,0, 0,1,-1, 1,1,-1};   // x1, z0
            }
        }
        // Bottom face (normal = 0,-1,0)
        else if (normalIndex == 1) {
            switch (cornerIndex) {
                case 0: return new int[] {0,0,0, -1,0,0, 0,0,-1, -1,0,-1}; // x0, z0
                case 1: return new int[] {0,0,0, 1,0,0, 0,0,-1, 1,0,-1};   // x1, z0
                case 2: return new int[] {0,0,0, 1,0,0, 0,0,1, 1,0,1};     // x1, z1
                case 3: return new int[] {0,0,0, -1,0,0, 0,0,1, -1,0,1};   // x0, z1
            }
        }
        // North face (normal = 0,0,-1)
        else if (normalIndex == 2) {
            switch (cornerIndex) {
                case 0: return new int[] {0,0,-1, 1,0,-1, 0,-1,-1, 1,-1,-1};   // x1, y0
                case 1: return new int[] {0,0,-1, -1,0,-1, 0,-1,-1, -1,-1,-1}; // x0, y0
                case 2: return new int[] {0,0,-1, -1,0,-1, 0,1,-1, -1,1,-1};   // x0, y1
                case 3: return new int[] {0,0,-1, 1,0,-1, 0,1,-1, 1,1,-1};     // x1, y1
            }
        }
        // South face (normal = 0,0,1)
        else if (normalIndex == 3) {
            switch (cornerIndex) {
                case 0: return new int[] {0,0,1, -1,0,1, 0,-1,1, -1,-1,1}; // x0, y0
                case 1: return new int[] {0,0,1, 1,0,1, 0,-1,1, 1,-1,1};   // x1, y0
                case 2: return new int[] {0,0,1, 1,0,1, 0,1,1, 1,1,1};     // x1, y1
                case 3: return new int[] {0,0,1, -1,0,1, 0,1,1, -1,1,1};   // x0, y1
            }
        }
        // West face (normal = -1,0,0)
        else if (normalIndex == 4) {
            switch (cornerIndex) {
                case 0: return new int[] {-1,0,0, -1,0,-1, -1,-1,0, -1,-1,-1}; // z0, y0
                case 1: return new int[] {-1,0,0, -1,0,1, -1,-1,0, -1,-1,1};   // z1, y0
                case 2: return new int[] {-1,0,0, -1,0,1, -1,1,0, -1,1,1};     // z1, y1
                case 3: return new int[] {-1,0,0, -1,0,-1, -1,1,0, -1,1,-1};   // z0, y1
            }
        }
        // East face (normal = 1,0,0)
        else if (normalIndex == 5) {
            switch (cornerIndex) {
                case 0: return new int[] {1,0,0, 1,0,1, 1,-1,0, 1,-1,1};   // z1, y0
                case 1: return new int[] {1,0,0, 1,0,-1, 1,-1,0, 1,-1,-1}; // z0, y0
                case 2: return new int[] {1,0,0, 1,0,-1, 1,1,0, 1,1,-1};   // z0, y1
                case 3: return new int[] {1,0,0, 1,0,1, 1,1,0, 1,1,1};     // z1, y1
            }
        }
        
        // Default: sample center position 4 times
        return new int[] {0,0,0, 0,0,0, 0,0,0, 0,0,0};
    }
    
    /**
     * Get skylight value safely, returning 15 if out of bounds.
     */
    private int getSkyLightSafe(mattmc.world.level.chunk.LevelChunk chunk, int x, int y, int z) {
        // Check bounds
        if (y < 0 || y >= mattmc.world.level.chunk.LevelChunk.HEIGHT) {
            return 15; // Out of bounds: full skylight
        }
        
        // If we have a light accessor and coordinates are out of chunk bounds, use cross-chunk sampling
        if (lightAccessor != null && 
            (x < 0 || x >= mattmc.world.level.chunk.LevelChunk.WIDTH ||
             z < 0 || z >= mattmc.world.level.chunk.LevelChunk.DEPTH)) {
            return lightAccessor.getSkyLightAcrossChunks(chunk, x, y, z);
        }
        
        // Within chunk bounds - use direct access
        if (x < 0 || x >= mattmc.world.level.chunk.LevelChunk.WIDTH ||
            z < 0 || z >= mattmc.world.level.chunk.LevelChunk.DEPTH) {
            return 15; // Out of chunk bounds without accessor: full skylight
        }
        
        return chunk.getSkyLight(x, y, z);
    }
    
    /**
     * Get blocklight value safely, returning 0 if out of bounds.
     */
    private int getBlockLightSafe(mattmc.world.level.chunk.LevelChunk chunk, int x, int y, int z) {
        // Check bounds
        if (y < 0 || y >= mattmc.world.level.chunk.LevelChunk.HEIGHT) {
            return 0; // Out of bounds: no blocklight
        }
        
        // If we have a light accessor and coordinates are out of chunk bounds, use cross-chunk sampling
        if (lightAccessor != null && 
            (x < 0 || x >= mattmc.world.level.chunk.LevelChunk.WIDTH ||
             z < 0 || z >= mattmc.world.level.chunk.LevelChunk.DEPTH)) {
            return lightAccessor.getBlockLightAcrossChunks(chunk, x, y, z);
        }
        
        // Within chunk bounds - use direct access
        if (x < 0 || x >= mattmc.world.level.chunk.LevelChunk.WIDTH ||
            z < 0 || z >= mattmc.world.level.chunk.LevelChunk.DEPTH) {
            return 0; // Out of chunk bounds without accessor: no blocklight
        }
        
        return chunk.getBlockLight(x, y, z);
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
     * Add stairs geometry based on blockstate (facing, half, and shape).
     * Uses JSON model elements instead of hardcoded geometry.
     */
    private void addStairsGeometry(float x, float y, float z, mattmc.world.level.block.Block block, 
                                    mattmc.world.level.block.state.BlockState blockState) {
        // Get the block name from identifier
        String blockName = block.getIdentifier();
        if (blockName == null) {
            return;
        }
        blockName = blockName.contains(":") ? blockName.substring(blockName.indexOf(':') + 1) : blockName;
        
        // Load blockstate definition
        mattmc.client.resources.model.BlockState blockStateDef = 
            mattmc.client.resources.ResourceManager.loadBlockState(blockName);
        if (blockStateDef == null) {
            return;
        }
        
        // Get variant for this blockstate
        java.util.List<mattmc.client.resources.model.BlockStateVariant> variants = 
            blockStateDef.getVariant(blockState);
        if (variants == null || variants.isEmpty()) {
            return;
        }
        
        // Use first variant
        mattmc.client.resources.model.BlockStateVariant variant = variants.get(0);
        if (variant.getModel() == null) {
            return;
        }
        
        // Resolve model with elements
        mattmc.client.resources.model.BlockModel model = 
            mattmc.client.resources.ResourceManager.resolveBlockModel(variant.getModel());
        if (model == null || !model.hasElements()) {
            return;
        }
        
        // Get textures
        java.util.Map<String, String> textures = model.getTextures();
        if (textures == null) {
            return;
        }
        
        // Render each element
        for (mattmc.client.resources.model.ModelElement element : model.getElements()) {
            renderModelElement(element, textures, x, y, z, variant);
        }
    }
    
    /**
     * Render a single model element from a block model.
     */
    private void renderModelElement(
            mattmc.client.resources.model.ModelElement element,
            java.util.Map<String, String> textures,
            float x, float y, float z,
            mattmc.client.resources.model.BlockStateVariant variant) {
        
        // Get element bounds (in pixels 0-16)
        float[] from = element.getFrom();
        float[] to = element.getTo();
        if (from == null || to == null || from.length < 3 || to.length < 3) {
            return;
        }
        
        // Convert from pixels to block coordinates (0-1)
        float x0 = from[0] / 16.0f;
        float y0 = from[1] / 16.0f;
        float z0 = from[2] / 16.0f;
        float x1 = to[0] / 16.0f;
        float y1 = to[1] / 16.0f;
        float z1 = to[2] / 16.0f;
        
        // Apply rotation from variant
        // Rotations are in degrees: x rotates around X axis, y rotates around Y axis
        int rotX = variant.getX() != null ? variant.getX() : 0;
        int rotY = variant.getY() != null ? variant.getY() : 0;
        
        // Render each face of the element
        java.util.Map<String, mattmc.client.resources.model.ModelElement.ElementFace> faces = element.getFaces();
        if (faces == null) {
            return;
        }
        
        // Process each face
        for (java.util.Map.Entry<String, mattmc.client.resources.model.ModelElement.ElementFace> entry : faces.entrySet()) {
            String direction = entry.getKey();
            mattmc.client.resources.model.ModelElement.ElementFace face = entry.getValue();
            
            // Resolve texture reference
            String textureRef = face.getTexture();
            String texturePath = resolveTexture(textureRef, textures);
            if (texturePath == null) {
                continue;
            }
            
            // Get UV mapping from texture atlas
            TextureAtlas.UVMapping uvMapping = null;
            if (textureAtlas != null) {
                // Convert texture path to full path
                String fullPath = "assets/textures/" + texturePath + ".png";
                uvMapping = textureAtlas.getUVMapping(fullPath);
            }
            
            // Get UV coordinates from face (or use defaults)
            float[] uv = face.getUv();
            float u0, v0, u1, v1;
            if (uv != null && uv.length >= 4) {
                // Convert from pixels to normalized (assuming 16x16 texture)
                u0 = uv[0] / 16.0f;
                v0 = uv[1] / 16.0f;
                u1 = uv[2] / 16.0f;
                v1 = uv[3] / 16.0f;
            } else {
                u0 = 0; v0 = 0; u1 = 1; v1 = 1;
            }
            
            // If we have atlas mapping, override the UVs
            if (uvMapping != null) {
                u0 = uvMapping.u0;
                v0 = uvMapping.v0;
                u1 = uvMapping.u1;
                v1 = uvMapping.v1;
            }
            
            // Render the face based on direction, with rotation applied
            renderElementFace(direction, x, y, z, x0, y0, z0, x1, y1, z1, 
                            u0, v0, u1, v1, rotX, rotY);
        }
    }
    
    /**
     * Resolve a texture reference (e.g., "#side" -> "block/birch_planks").
     */
    private String resolveTexture(String textureRef, java.util.Map<String, String> textures) {
        if (textureRef == null || textures == null) {
            return null;
        }
        
        // If reference starts with #, look it up in textures map
        if (textureRef.startsWith("#")) {
            String key = textureRef.substring(1);
            String resolved = textures.get(key);
            // May need to resolve recursively
            while (resolved != null && resolved.startsWith("#")) {
                key = resolved.substring(1);
                resolved = textures.get(key);
            }
            return resolved;
        }
        
        return textureRef;
    }
    
    /**
     * Render a face of a model element with rotation applied.
     * 
     * @param direction Face direction (up, down, north, south, east, west)
     * @param worldX World X position of the block
     * @param worldY World Y position of the block
     * @param worldZ World Z position of the block
     * @param x0 Local X minimum (0-1)
     * @param y0 Local Y minimum (0-1)
     * @param z0 Local Z minimum (0-1)
     * @param x1 Local X maximum (0-1)
     * @param y1 Local Y maximum (0-1)
     * @param z1 Local Z maximum (0-1)
     * @param u0 UV u minimum
     * @param v0 UV v minimum
     * @param u1 UV u maximum
     * @param v1 UV v maximum
     * @param rotX Rotation around X axis in degrees (0, 90, 180, 270)
     * @param rotY Rotation around Y axis in degrees (0, 90, 180, 270)
     */
    private void renderElementFace(String direction,
                                    float worldX, float worldY, float worldZ,
                                    float x0, float y0, float z0,
                                    float x1, float y1, float z1,
                                    float u0, float v0, float u1, float v1,
                                    int rotX, int rotY) {
        
        // Apply Y rotation (around vertical axis) to the coordinates and direction
        if (rotY != 0) {
            // Rotate the element bounds around Y axis
            float[] rotated = rotateY(x0, z0, rotY);
            float rx0 = rotated[0], rz0 = rotated[1];
            rotated = rotateY(x1, z1, rotY);
            float rx1 = rotated[0], rz1 = rotated[1];
            
            // Ensure min/max are correct after rotation
            x0 = Math.min(rx0, rx1);
            x1 = Math.max(rx0, rx1);
            z0 = Math.min(rz0, rz1);
            z1 = Math.max(rz0, rz1);
            
            // Rotate the face direction
            direction = rotateFaceDirection(direction, rotY, rotX);
        }
        
        // Apply X rotation (flip upside down for top/bottom stairs)
        if (rotX == 180) {
            // Flip Y coordinates
            float temp = y0;
            y0 = 1.0f - y1;
            y1 = 1.0f - temp;
            
            // Flip face direction for vertical faces
            if (direction.equals("up")) {
                direction = "down";
            } else if (direction.equals("down")) {
                direction = "up";
            }
        }
        
        // Add world offset
        x0 += worldX;
        x1 += worldX;
        y0 += worldY;
        y1 += worldY;
        z0 += worldZ;
        z1 += worldZ;
        
        // Color and brightness based on direction
        float[] color;
        switch (direction) {
            case "up" -> color = new float[]{1.0f, 1.0f, 1.0f, 1.0f};
            case "down" -> color = new float[]{0.5f, 0.5f, 0.5f, 1.0f};
            case "north", "south" -> color = new float[]{0.8f, 0.8f, 0.8f, 1.0f};
            case "west", "east" -> color = new float[]{0.6f, 0.6f, 0.6f, 1.0f};
            default -> color = new float[]{1.0f, 1.0f, 1.0f, 1.0f};
        }
        
        int base = currentVertex;
        
        switch (direction) {
            case "up" -> {
                // Top face
                addVertex(x0, y1, z0, u0, v0, color, 0, 1, 0);
                addVertex(x0, y1, z1, u0, v1, color, 0, 1, 0);
                addVertex(x1, y1, z1, u1, v1, color, 0, 1, 0);
                addVertex(x1, y1, z0, u1, v0, color, 0, 1, 0);
            }
            case "down" -> {
                // Bottom face
                addVertex(x0, y0, z0, u0, v0, color, 0, -1, 0);
                addVertex(x1, y0, z0, u1, v0, color, 0, -1, 0);
                addVertex(x1, y0, z1, u1, v1, color, 0, -1, 0);
                addVertex(x0, y0, z1, u0, v1, color, 0, -1, 0);
            }
            case "north" -> {
                // North face (z0)
                addVertex(x1, y0, z0, u1, v1, color, 0, 0, -1);
                addVertex(x0, y0, z0, u0, v1, color, 0, 0, -1);
                addVertex(x0, y1, z0, u0, v0, color, 0, 0, -1);
                addVertex(x1, y1, z0, u1, v0, color, 0, 0, -1);
            }
            case "south" -> {
                // South face (z1)
                addVertex(x0, y0, z1, u0, v1, color, 0, 0, 1);
                addVertex(x1, y0, z1, u1, v1, color, 0, 0, 1);
                addVertex(x1, y1, z1, u1, v0, color, 0, 0, 1);
                addVertex(x0, y1, z1, u0, v0, color, 0, 0, 1);
            }
            case "west" -> {
                // West face (x0)
                addVertex(x0, y0, z0, u0, v1, color, -1, 0, 0);
                addVertex(x0, y0, z1, u1, v1, color, -1, 0, 0);
                addVertex(x0, y1, z1, u1, v0, color, -1, 0, 0);
                addVertex(x0, y1, z0, u0, v0, color, -1, 0, 0);
            }
            case "east" -> {
                // East face (x1)
                addVertex(x1, y0, z1, u0, v1, color, 1, 0, 0);
                addVertex(x1, y0, z0, u1, v1, color, 1, 0, 0);
                addVertex(x1, y1, z0, u1, v0, color, 1, 0, 0);
                addVertex(x1, y1, z1, u0, v0, color, 1, 0, 0);
            }
        }
        
        addQuadIndices(base);
        currentVertex += 4;
    }
    
    /**
     * Rotate coordinates around Y axis (vertical).
     * Returns [newX, newZ].
     */
    private float[] rotateY(float x, float z, int degrees) {
        // Center at 0.5, 0.5
        x -= 0.5f;
        z -= 0.5f;
        
        float newX, newZ;
        switch (degrees) {
            case 90 -> {
                // 90 degrees clockwise: (x,z) -> (z, -x)
                newX = z;
                newZ = -x;
            }
            case 180 -> {
                // 180 degrees: (x,z) -> (-x, -z)
                newX = -x;
                newZ = -z;
            }
            case 270 -> {
                // 270 degrees clockwise: (x,z) -> (-z, x)
                newX = -z;
                newZ = x;
            }
            default -> {
                newX = x;
                newZ = z;
            }
        }
        
        // Offset back
        newX += 0.5f;
        newZ += 0.5f;
        
        return new float[]{newX, newZ};
    }
    
    /**
     * Rotate a face direction based on Y and X rotations.
     */
    private String rotateFaceDirection(String direction, int rotY, int rotX) {
        // Apply Y rotation (horizontal)
        if (rotY != 0) {
            direction = switch (direction) {
                case "north" -> {
                    if (rotY == 90) yield "east";
                    if (rotY == 180) yield "south";
                    if (rotY == 270) yield "west";
                    yield direction;
                }
                case "east" -> {
                    if (rotY == 90) yield "south";
                    if (rotY == 180) yield "west";
                    if (rotY == 270) yield "north";
                    yield direction;
                }
                case "south" -> {
                    if (rotY == 90) yield "west";
                    if (rotY == 180) yield "north";
                    if (rotY == 270) yield "east";
                    yield direction;
                }
                case "west" -> {
                    if (rotY == 90) yield "north";
                    if (rotY == 180) yield "east";
                    if (rotY == 270) yield "south";
                    yield direction;
                }
                default -> direction;
            };
        }
        
        return direction;
    }
    
}
