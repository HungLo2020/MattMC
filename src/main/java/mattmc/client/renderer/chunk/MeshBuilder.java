package mattmc.client.renderer.chunk;

import mattmc.client.renderer.ColorUtils;
import mattmc.client.renderer.block.BlockFaceCollector;
import mattmc.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds vertex and index arrays from collected block faces.
 * Converts BlockFaceCollector data into a format suitable for VBO/VAO rendering.
 */
public class MeshBuilder {
    
    // Vertex format: x, y, z, u, v, r, g, b, a (9 floats per vertex)
    private final List<Float> vertices = new ArrayList<>();
    private final List<Integer> indices = new ArrayList<>();
    private int currentVertex = 0;
    
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
            // Extract color components
            float[] color = extractColor(face);
            
            // Add the face with correct orientation
            switch (type) {
                case TOP -> addTopFace(face.x, face.y, face.z, color);
                case BOTTOM -> addBottomFace(face.x, face.y, face.z, color);
                case NORTH -> addNorthFace(face.x, face.y, face.z, color);
                case SOUTH -> addSouthFace(face.x, face.y, face.z, color);
                case WEST -> addWestFace(face.x, face.y, face.z, color);
                case EAST -> addEastFace(face.x, face.y, face.z, color);
            }
        }
    }
    
    /**
     * Extract RGBA color from face data.
     * Since VBO rendering doesn't support per-face texture switching,
     * we use the block's fallback color for all faces.
     */
    private float[] extractColor(BlockFaceCollector.FaceData face) {
        // Use fallback color since we don't have texture support in VBO rendering yet
        int renderColor = ColorUtils.adjustColorBrightness(
            face.block.getFallbackColor(), 
            face.colorBrightness
        );
        
        // Apply grass green tint for grass_block top face (vanilla Minecraft-like)
        // Note: faceType is never null in practice (always set to string literal)
        if (face.block == Blocks.GRASS_BLOCK && face.faceType != null && "top".equals(face.faceType)) {
            renderColor = ColorUtils.applyTint(renderColor, 0x5BB53B, face.colorBrightness);
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
    private void addTopFace(float x, float y, float z, float[] color) {
        float x0 = x, x1 = x + 1;
        float y1 = y + 1;
        float z0 = z, z1 = z + 1;
        
        int baseVertex = currentVertex;
        
        // 4 vertices for the quad
        addVertex(x0, y1, z0, 0, 0, color); // 0
        addVertex(x0, y1, z1, 0, 1, color); // 1
        addVertex(x1, y1, z1, 1, 1, color); // 2
        addVertex(x1, y1, z0, 1, 0, color); // 3
        
        // 2 triangles (6 indices)
        addQuadIndices(baseVertex);
        
        currentVertex += 4;
    }
    
    /**
     * Add bottom face vertices and indices.
     */
    private void addBottomFace(float x, float y, float z, float[] color) {
        float x0 = x, x1 = x + 1;
        float y0 = y;
        float z0 = z, z1 = z + 1;
        
        int baseVertex = currentVertex;
        
        // 4 vertices for the quad
        addVertex(x0, y0, z0, 0, 0, color); // 0
        addVertex(x1, y0, z0, 1, 0, color); // 1
        addVertex(x1, y0, z1, 1, 1, color); // 2
        addVertex(x0, y0, z1, 0, 1, color); // 3
        
        // 2 triangles (6 indices)
        addQuadIndices(baseVertex);
        
        currentVertex += 4;
    }
    
    /**
     * Add south face vertices and indices.
     */
    private void addSouthFace(float x, float y, float z, float[] color) {
        float x0 = x, x1 = x + 1;
        float y0 = y, y1 = y + 1;
        float z1 = z + 1;
        
        int baseVertex = currentVertex;
        
        // 4 vertices for the quad
        addVertex(x0, y0, z1, 0, 1, color); // 0
        addVertex(x1, y0, z1, 1, 1, color); // 1
        addVertex(x1, y1, z1, 1, 0, color); // 2
        addVertex(x0, y1, z1, 0, 0, color); // 3
        
        // 2 triangles (6 indices)
        addQuadIndices(baseVertex);
        
        currentVertex += 4;
    }
    
    /**
     * Add west face vertices and indices.
     */
    private void addWestFace(float x, float y, float z, float[] color) {
        float x0 = x;
        float y0 = y, y1 = y + 1;
        float z0 = z, z1 = z + 1;
        
        int baseVertex = currentVertex;
        
        // 4 vertices for the quad
        addVertex(x0, y0, z0, 0, 1, color); // 0
        addVertex(x0, y0, z1, 1, 1, color); // 1
        addVertex(x0, y1, z1, 1, 0, color); // 2
        addVertex(x0, y1, z0, 0, 0, color); // 3
        
        // 2 triangles (6 indices)
        addQuadIndices(baseVertex);
        
        currentVertex += 4;
    }
    
    /**
     * Add east face vertices and indices.
     */
    private void addEastFace(float x, float y, float z, float[] color) {
        float x1 = x + 1;
        float y0 = y, y1 = y + 1;
        float z0 = z, z1 = z + 1;
        
        int baseVertex = currentVertex;
        
        // 4 vertices for the quad
        addVertex(x1, y0, z1, 1, 1, color); // 0
        addVertex(x1, y0, z0, 0, 1, color); // 1
        addVertex(x1, y1, z0, 0, 0, color); // 2
        addVertex(x1, y1, z1, 1, 0, color); // 3
        
        // 2 triangles (6 indices)
        addQuadIndices(baseVertex);
        
        currentVertex += 4;
    }
    
    /**
     * Add north face vertices and indices.
     */
    private void addNorthFace(float x, float y, float z, float[] color) {
        float x0 = x, x1 = x + 1;
        float y0 = y, y1 = y + 1;
        float z0 = z;
        
        int baseVertex = currentVertex;
        
        // 4 vertices for the quad
        addVertex(x1, y0, z0, 1, 1, color); // 0
        addVertex(x0, y0, z0, 0, 1, color); // 1
        addVertex(x0, y1, z0, 0, 0, color); // 2
        addVertex(x1, y1, z0, 1, 0, color); // 3
        
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
}
