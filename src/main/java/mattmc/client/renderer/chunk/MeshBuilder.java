package mattmc.client.renderer.chunk;

import mattmc.util.ColorUtils;
import mattmc.client.renderer.block.BlockFaceCollector;
import mattmc.client.renderer.texture.TextureCoordinateProvider;
import mattmc.registries.Blocks;

import java.util.List;

/**
 * Builds vertex and index arrays from collected block faces.
 * Converts BlockFaceCollector data into a format suitable for VBO/VAO rendering.
 * Supports texture atlas UV mapping for multi-texture VBO rendering.
 * 
 * <p><b>Texture ID Performance:</b> The TextureAtlas uses integer texture IDs internally.
 * String→int conversion happens once per texture lookup, then int-based UV lookup is used.
 * All UVMapper methods now use int-based lookups internally for optimal performance.
 * 
 * <p><b>Future Optimization:</b> Pre-resolve texture IDs in baked face data (e.g., FaceData, BakedQuad) 
 * during model/face collection time so that string lookup can be eliminated entirely from inner loops.
 * This would require passing the TextureAtlas to BlockFaceCollector during chunk mesh collection.
 * 
 * ISSUE-002 fix: Uses primitive FloatList and IntList instead of ArrayList<Float/Integer>
 * to eliminate boxing overhead and reduce GC pressure.
 * 
 * Refactored to use extracted classes for light sampling and UV mapping.
 */
public class MeshBuilder {
    
    // Vertex format: x, y, z, u, v, r, g, b, a, nx, ny, nz, skyLight, blockLightR, blockLightG, blockLightB, ao (17 floats per vertex)
    // Using primitive arrays to avoid boxing/unboxing overhead
    private final FloatList vertices = new FloatList();
    private final IntList indices = new IntList();
    private int currentVertex = 0;
    private final VertexLightSampler lightSampler;
    private final UVMapper uvMapper;
    private final ModelElementRenderer modelElementRenderer;
    
    /**
     * Create a mesh builder with optional texture atlas support.
     * 
     * @param textureAtlas Texture atlas for UV mapping, or null to use fallback colors
     */
    public MeshBuilder(TextureCoordinateProvider textureAtlas) {
        this.lightSampler = new VertexLightSampler();
        this.uvMapper = new UVMapper(textureAtlas);
        this.modelElementRenderer = new ModelElementRenderer(lightSampler, uvMapper);
    }
    
    /**
     * Set the light accessor for cross-chunk light sampling.
     */
    public void setLightAccessor(VertexLightSampler.ChunkLightAccessor accessor) {
        this.lightSampler.setLightAccessor(accessor);
    }
    
    /**
     * Build a ChunkMeshBuffer from collected face data.
     * ISSUE-015 fix: Optimized list iteration to reduce method call overhead.
     */
    public ChunkMeshBuffer build(int chunkX, int chunkZ, BlockFaceCollector collector) {
        vertices.clear();
        indices.clear();
        currentVertex = 0;
        
        // Invalidate lighting cache to ensure fresh light data for rebuild
        lightSampler.invalidateCache();
        
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
                
                // Check if this block uses model elements (data-driven geometry)
                if ("model_elements".equals(face.faceType)) {
                    // Render geometry from JSON model elements (data-driven)
                    currentVertex = modelElementRenderer.renderModelElements(face, vertices, indices, currentVertex);
                    continue;
                }
                
                // Extract color components and UV mapping
                float[] color = uvMapper.extractColor(face);
                TextureCoordinateProvider.UVMapping uvMapping = uvMapper.getUVMapping(face);
                
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
     * Add top face vertices and indices.
     */
    private void addTopFace(BlockFaceCollector.FaceData face, float[] color, TextureCoordinateProvider.UVMapping uvMapping) {
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
        float[] light0 = lightSampler.sampleVertexLight(face, 0, 0);
        float[] light1 = lightSampler.sampleVertexLight(face, 0, 1);
        float[] light2 = lightSampler.sampleVertexLight(face, 0, 2);
        float[] light3 = lightSampler.sampleVertexLight(face, 0, 3);
        
        // 4 vertices for the quad with atlas UVs, normal (0,1,0) for top face, and light data
        addVertex(x0, y1, z0, u0, v0, color, 0, 1, 0, light0[0], light0[1], light0[2], light0[3], light0[4]); // 0
        addVertex(x0, y1, z1, u0, v1, color, 0, 1, 0, light1[0], light1[1], light1[2], light1[3], light1[4]); // 1
        addVertex(x1, y1, z1, u1, v1, color, 0, 1, 0, light2[0], light2[1], light2[2], light2[3], light2[4]); // 2
        addVertex(x1, y1, z0, u1, v0, color, 0, 1, 0, light3[0], light3[1], light3[2], light3[3], light3[4]); // 3
        
        // 2 triangles (6 indices)
        addQuadIndices(baseVertex);
        
        currentVertex += 4;
    }
    
    /**
     * Add bottom face vertices and indices.
     */
    private void addBottomFace(BlockFaceCollector.FaceData face, float[] color, TextureCoordinateProvider.UVMapping uvMapping) {
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
        float[] light0 = lightSampler.sampleVertexLight(face, 1, 0);
        float[] light1 = lightSampler.sampleVertexLight(face, 1, 1);
        float[] light2 = lightSampler.sampleVertexLight(face, 1, 2);
        float[] light3 = lightSampler.sampleVertexLight(face, 1, 3);
        
        // 4 vertices for the quad with atlas UVs, normal (0,-1,0) for bottom face, and light data
        addVertex(x0, y0, z0, u0, v0, color, 0, -1, 0, light0[0], light0[1], light0[2], light0[3], light0[4]); // 0
        addVertex(x1, y0, z0, u1, v0, color, 0, -1, 0, light1[0], light1[1], light1[2], light1[3], light1[4]); // 1
        addVertex(x1, y0, z1, u1, v1, color, 0, -1, 0, light2[0], light2[1], light2[2], light2[3], light2[4]); // 2
        addVertex(x0, y0, z1, u0, v1, color, 0, -1, 0, light3[0], light3[1], light3[2], light3[3], light3[4]); // 3
        
        // 2 triangles (6 indices)
        addQuadIndices(baseVertex);
        
        currentVertex += 4;
    }
    
    /**
     * Add south face vertices and indices.
     */
    private void addSouthFace(BlockFaceCollector.FaceData face, float[] color, TextureCoordinateProvider.UVMapping uvMapping) {
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
        float[] light0 = lightSampler.sampleVertexLight(face, 3, 0);
        float[] light1 = lightSampler.sampleVertexLight(face, 3, 1);
        float[] light2 = lightSampler.sampleVertexLight(face, 3, 2);
        float[] light3 = lightSampler.sampleVertexLight(face, 3, 3);
        
        // 4 vertices for the quad with atlas UVs, normal (0,0,1) for south face, and light data
        addVertex(x0, y0, z1, u0, v1, color, 0, 0, 1, light0[0], light0[1], light0[2], light0[3], light0[4]); // 0
        addVertex(x1, y0, z1, u1, v1, color, 0, 0, 1, light1[0], light1[1], light1[2], light1[3], light1[4]); // 1
        addVertex(x1, y1, z1, u1, v0, color, 0, 0, 1, light2[0], light2[1], light2[2], light2[3], light2[4]); // 2
        addVertex(x0, y1, z1, u0, v0, color, 0, 0, 1, light3[0], light3[1], light3[2], light3[3], light3[4]); // 3
        
        // 2 triangles (6 indices)
        addQuadIndices(baseVertex);
        
        currentVertex += 4;
    }
    
    /**
     * Add west face vertices and indices.
     */
    private void addWestFace(BlockFaceCollector.FaceData face, float[] color, TextureCoordinateProvider.UVMapping uvMapping) {
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
        float[] light0 = lightSampler.sampleVertexLight(face, 4, 0);
        float[] light1 = lightSampler.sampleVertexLight(face, 4, 1);
        float[] light2 = lightSampler.sampleVertexLight(face, 4, 2);
        float[] light3 = lightSampler.sampleVertexLight(face, 4, 3);
        
        // 4 vertices for the quad with atlas UVs, normal (-1,0,0) for west face, and light data
        addVertex(x0, y0, z0, u0, v1, color, -1, 0, 0, light0[0], light0[1], light0[2], light0[3], light0[4]); // 0
        addVertex(x0, y0, z1, u1, v1, color, -1, 0, 0, light1[0], light1[1], light1[2], light1[3], light1[4]); // 1
        addVertex(x0, y1, z1, u1, v0, color, -1, 0, 0, light2[0], light2[1], light2[2], light2[3], light2[4]); // 2
        addVertex(x0, y1, z0, u0, v0, color, -1, 0, 0, light3[0], light3[1], light3[2], light3[3], light3[4]); // 3
        
        // 2 triangles (6 indices)
        addQuadIndices(baseVertex);
        
        currentVertex += 4;
    }
    
    /**
     * Add east face vertices and indices.
     */
    private void addEastFace(BlockFaceCollector.FaceData face, float[] color, TextureCoordinateProvider.UVMapping uvMapping) {
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
        float[] light0 = lightSampler.sampleVertexLight(face, 5, 0);
        float[] light1 = lightSampler.sampleVertexLight(face, 5, 1);
        float[] light2 = lightSampler.sampleVertexLight(face, 5, 2);
        float[] light3 = lightSampler.sampleVertexLight(face, 5, 3);
        
        // 4 vertices for the quad with atlas UVs, normal (1,0,0) for east face, and light data
        addVertex(x1, y0, z1, u1, v1, color, 1, 0, 0, light0[0], light0[1], light0[2], light0[3], light0[4]); // 0
        addVertex(x1, y0, z0, u0, v1, color, 1, 0, 0, light1[0], light1[1], light1[2], light1[3], light1[4]); // 1
        addVertex(x1, y1, z0, u0, v0, color, 1, 0, 0, light2[0], light2[1], light2[2], light2[3], light2[4]); // 2
        addVertex(x1, y1, z1, u1, v0, color, 1, 0, 0, light3[0], light3[1], light3[2], light3[3], light3[4]); // 3
        
        // 2 triangles (6 indices)
        addQuadIndices(baseVertex);
        
        currentVertex += 4;
    }
    
    /**
     * Add north face vertices and indices.
     */
    private void addNorthFace(BlockFaceCollector.FaceData face, float[] color, TextureCoordinateProvider.UVMapping uvMapping) {
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
        float[] light0 = lightSampler.sampleVertexLight(face, 2, 0);
        float[] light1 = lightSampler.sampleVertexLight(face, 2, 1);
        float[] light2 = lightSampler.sampleVertexLight(face, 2, 2);
        float[] light3 = lightSampler.sampleVertexLight(face, 2, 3);
        
        // 4 vertices for the quad with atlas UVs, normal (0,0,-1) for north face, and light data
        addVertex(x1, y0, z0, u1, v1, color, 0, 0, -1, light0[0], light0[1], light0[2], light0[3], light0[4]); // 0
        addVertex(x0, y0, z0, u0, v1, color, 0, 0, -1, light1[0], light1[1], light1[2], light1[3], light1[4]); // 1
        addVertex(x0, y1, z0, u0, v0, color, 0, 0, -1, light2[0], light2[1], light2[2], light2[3], light2[4]); // 2
        addVertex(x1, y1, z0, u1, v0, color, 0, 0, -1, light3[0], light3[1], light3[2], light3[3], light3[4]); // 3
        
        // 2 triangles (6 indices)
        addQuadIndices(baseVertex);
        
        currentVertex += 4;
    }
    
    /**
     * Add a single vertex to the vertex list with default light values.
     */
    private void addVertex(float x, float y, float z, float u, float v, float[] color, float nx, float ny, float nz) {
        addVertex(x, y, z, u, v, color, nx, ny, nz, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
    }
    
    /**
     * Add a single vertex to the vertex list with normal and light data.
     * Does NOT apply lighting to the color - that's handled by the shader.
     * The color represents the albedo (base color) only.
     * 
     * @param x, y, z Position
     * @param u, v Texture coordinates
     * @param color Albedo color [r, g, b, a]
     * @param nx, ny, nz Normal vector
     * @param skyLight Sky light level (0-15)
     * @param blockLightR Block light red channel (0-15)
     * @param blockLightG Block light green channel (0-15)
     * @param blockLightB Block light blue channel (0-15)
     * @param ao Ambient occlusion value (0-3)
     */
    private void addVertex(float x, float y, float z, float u, float v, float[] color,
                          float nx, float ny, float nz, 
                          float skyLight, float blockLightR, float blockLightG, float blockLightB, float ao) {
        // Add vertex data (position, uv, color, normal, light)
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
        vertices.add(skyLight);     // sky light level
        vertices.add(blockLightR);  // block light red
        vertices.add(blockLightG);  // block light green
        vertices.add(blockLightB);  // block light blue
        vertices.add(ao);           // ambient occlusion
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
}
