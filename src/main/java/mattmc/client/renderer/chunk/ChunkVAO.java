package mattmc.client.renderer.chunk;

import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;

/**
 * Manages OpenGL VBO (Vertex Buffer Object) and EBO (Element Buffer Object) for a single chunk.
 * Uses fixed-function pipeline compatibility mode for rendering.
 * 
 * Encapsulates all GPU resources needed to render a chunk mesh efficiently.
 */
public class ChunkVAO {
    private final int chunkX;
    private final int chunkZ;
    private final int vboId;
    private final int eboId;
    private final int indexCount;
    
    /**
     * Create and upload a chunk VBO/EBO from mesh buffer data.
     * Must be called on the OpenGL thread.
     * 
     * @param meshBuffer CPU-side mesh data to upload
     */
    public ChunkVAO(ChunkMeshBuffer meshBuffer) {
        this.chunkX = meshBuffer.getChunkX();
        this.chunkZ = meshBuffer.getChunkZ();
        this.indexCount = meshBuffer.getIndexCount();
        
        // Generate and upload VBO (vertex data)
        this.vboId = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        FloatBuffer vertexBuffer = meshBuffer.createVertexBuffer();
        glBufferData(GL_ARRAY_BUFFER, vertexBuffer, GL_STATIC_DRAW);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        
        // Generate and upload EBO (index data)
        this.eboId = glGenBuffers();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, eboId);
        IntBuffer indexBuffer = meshBuffer.createIndexBuffer();
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, indexBuffer, GL_STATIC_DRAW);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
    }
    
    /**
     * Render this chunk using VBO/EBO with fixed-function pipeline.
     * Sets up vertex arrays and issues a single draw call.
     * Must be called on the OpenGL thread.
     * 
     * Note: Currently renders without textures (vertex colors only).
     * Future enhancement: implement texture atlas for multi-texture support in single VBO.
     */
    public void render() {
        int stride = ChunkMeshBuffer.VERTEX_SIZE_BYTES;
        
        // Disable texturing for VBO rendering (for now)
        // TODO: Implement texture atlas to support textures in VBO rendering
        boolean wasTextureEnabled = glIsEnabled(GL_TEXTURE_2D);
        if (wasTextureEnabled) {
            glDisable(GL_TEXTURE_2D);
        }
        
        // Bind VBO
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        
        // Enable client state arrays (fixed-function pipeline)
        glEnableClientState(GL_VERTEX_ARRAY);
        glEnableClientState(GL_COLOR_ARRAY);
        
        // Set vertex attribute pointers
        glVertexPointer(3, GL_FLOAT, stride, ChunkMeshBuffer.POSITION_OFFSET * Float.BYTES);
        glColorPointer(4, GL_FLOAT, stride, ChunkMeshBuffer.COLOR_OFFSET * Float.BYTES);
        
        // Bind EBO and draw
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, eboId);
        glDrawElements(GL_TRIANGLES, indexCount, GL_UNSIGNED_INT, 0);
        
        // Disable client state arrays
        glDisableClientState(GL_VERTEX_ARRAY);
        glDisableClientState(GL_COLOR_ARRAY);
        
        // Unbind buffers
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
        
        // Restore texture state
        if (wasTextureEnabled) {
            glEnable(GL_TEXTURE_2D);
        }
    }
    
    /**
     * Delete all GPU resources.
     * Must be called on the OpenGL thread when chunk is unloaded.
     */
    public void delete() {
        glDeleteBuffers(vboId);
        glDeleteBuffers(eboId);
    }
    
    public int getChunkX() {
        return chunkX;
    }
    
    public int getChunkZ() {
        return chunkZ;
    }
    
    public int getIndexCount() {
        return indexCount;
    }
    
    public boolean isEmpty() {
        return indexCount == 0;
    }
}
