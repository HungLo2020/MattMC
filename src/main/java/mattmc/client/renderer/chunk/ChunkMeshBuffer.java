package mattmc.client.renderer.chunk;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * CPU-side container for chunk mesh vertex and index data.
 * Can be prepared on a background thread and then uploaded to GPU on the render thread.
 * 
 * Vertex format (interleaved, 17 floats per vertex):
 * - Position (x, y, z): 3 floats
 * - Texture coords (u, v): 2 floats  
 * - Color (r, g, b, a): 4 floats
 * - Normal (nx, ny, nz): 3 floats
 * - Light data (skyLight, blockLightR, blockLightG, blockLightB, ao): 5 floats
 */
public class ChunkMeshBuffer {
    private final int chunkX;
    private final int chunkZ;
    private final float[] vertices;
    private final int[] indices;
    private final int vertexCount;
    private final int indexCount;
    
    // Vertex attribute layout
    public static final int FLOATS_PER_VERTEX = 17;
    public static final int POSITION_OFFSET = 0;
    public static final int TEXCOORD_OFFSET = 3;
    public static final int COLOR_OFFSET = 5;
    public static final int NORMAL_OFFSET = 9;  // nx, ny, nz
    public static final int LIGHT_OFFSET = 12;  // skyLight, blockLightR, blockLightG, blockLightB, ao
    public static final int VERTEX_SIZE_BYTES = FLOATS_PER_VERTEX * Float.BYTES;
    
    /**
     * Create a mesh buffer with pre-allocated arrays.
     * 
     * @param chunkX Chunk X coordinate
     * @param chunkZ Chunk Z coordinate
     * @param vertices Vertex data (interleaved: x,y,z, u,v, r,g,b,a, nx,ny,nz, skyLight,blockLightR,blockLightG,blockLightB,ao)
     * @param indices Index data (triangle indices)
     */
    public ChunkMeshBuffer(int chunkX, int chunkZ, float[] vertices, int[] indices) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.vertices = vertices;
        this.indices = indices;
        this.vertexCount = vertices.length / FLOATS_PER_VERTEX;
        this.indexCount = indices.length;
    }
    
    public int getChunkX() {
        return chunkX;
    }
    
    public int getChunkZ() {
        return chunkZ;
    }
    
    public float[] getVertices() {
        return vertices;
    }
    
    public int[] getIndices() {
        return indices;
    }
    
    public int getVertexCount() {
        return vertexCount;
    }
    
    public int getIndexCount() {
        return indexCount;
    }
    
    /**
     * Create a direct FloatBuffer from vertex data for GPU upload.
     * Uses standard Java NIO to maintain backend-agnostic architecture.
     */
    public FloatBuffer createVertexBuffer() {
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(vertices.length * Float.BYTES);
        byteBuffer.order(ByteOrder.nativeOrder());
        FloatBuffer buffer = byteBuffer.asFloatBuffer();
        buffer.put(vertices);
        buffer.flip();
        return buffer;
    }
    
    /**
     * Create a direct IntBuffer from index data for GPU upload.
     * Uses standard Java NIO to maintain backend-agnostic architecture.
     */
    public IntBuffer createIndexBuffer() {
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(indices.length * Integer.BYTES);
        byteBuffer.order(ByteOrder.nativeOrder());
        IntBuffer buffer = byteBuffer.asIntBuffer();
        buffer.put(indices);
        buffer.flip();
        return buffer;
    }
    
    /**
     * Check if this mesh is empty (no geometry).
     */
    public boolean isEmpty() {
        return vertexCount == 0 || indexCount == 0;
    }
    
    /**
     * Get estimated memory size in bytes.
     */
    public int getMemorySize() {
        return (vertices.length * Float.BYTES) + (indices.length * Integer.BYTES);
    }
}
