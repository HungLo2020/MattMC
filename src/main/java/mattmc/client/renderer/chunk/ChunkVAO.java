package mattmc.client.renderer.chunk;

import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Manages OpenGL VAO (Vertex Array Object), VBO (Vertex Buffer Object), 
 * and EBO (Element Buffer Object) for a single chunk.
 * Uses OpenGL 3.2+ VAO for proper state management and texture support.
 * 
 * Encapsulates all GPU resources needed to render a chunk mesh efficiently.
 */
public class ChunkVAO {
    private final int chunkX;
    private final int chunkZ;
    private final int vaoId;
    private final int vboId;
    private final int eboId;
    private final int indexCount;
    
    /**
     * Create and upload a chunk VAO/VBO/EBO from mesh buffer data.
     * Must be called on the OpenGL thread.
     * 
     * @param meshBuffer CPU-side mesh data to upload
     */
    public ChunkVAO(ChunkMeshBuffer meshBuffer) {
        this.chunkX = meshBuffer.getChunkX();
        this.chunkZ = meshBuffer.getChunkZ();
        this.indexCount = meshBuffer.getIndexCount();
        
        // Generate VAO (OpenGL 3.0+)
        this.vaoId = glGenVertexArrays();
        glBindVertexArray(vaoId);
        
        // Generate and upload VBO (vertex data)
        this.vboId = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        FloatBuffer vertexBuffer = meshBuffer.createVertexBuffer();
        glBufferData(GL_ARRAY_BUFFER, vertexBuffer, GL_STATIC_DRAW);
        
        // Generate and upload EBO (index data)
        this.eboId = glGenBuffers();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, eboId);
        IntBuffer indexBuffer = meshBuffer.createIndexBuffer();
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, indexBuffer, GL_STATIC_DRAW);
        
        // Configure vertex arrays using fixed-function pipeline (compatibility mode)
        int stride = ChunkMeshBuffer.VERTEX_SIZE_BYTES;
        
        // Set vertex pointers (fixed-function pipeline)
        // Note: glEnableClientState is NOT part of VAO state, so we enable it in render()
        glVertexPointer(3, GL_FLOAT, stride, ChunkMeshBuffer.POSITION_OFFSET * Float.BYTES);
        glTexCoordPointer(2, GL_FLOAT, stride, ChunkMeshBuffer.TEXCOORD_OFFSET * Float.BYTES);
        glColorPointer(4, GL_FLOAT, stride, ChunkMeshBuffer.COLOR_OFFSET * Float.BYTES);
        
        // Unbind VAO (good practice)
        glBindVertexArray(0);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
    }
    
    /**
     * Render this chunk using VAO with OpenGL 3.2+.
     * Simply binds the VAO and issues a single draw call.
     * Must be called on the OpenGL thread.
     * 
     * Now supports textures with proper VAO binding.
     * Texture atlas can be bound before calling this method.
     */
    public void render() {
        // Bind VAO
        glBindVertexArray(vaoId);
        
        // Enable client state (not part of VAO state in compatibility mode)
        glEnableClientState(GL_VERTEX_ARRAY);
        glEnableClientState(GL_TEXTURE_COORD_ARRAY);
        glEnableClientState(GL_COLOR_ARRAY);
        
        // Draw
        glDrawElements(GL_TRIANGLES, indexCount, GL_UNSIGNED_INT, 0);
        
        // Disable client state
        glDisableClientState(GL_VERTEX_ARRAY);
        glDisableClientState(GL_TEXTURE_COORD_ARRAY);
        glDisableClientState(GL_COLOR_ARRAY);
        
        // Unbind VAO
        glBindVertexArray(0);
    }
    
    /**
     * Delete all GPU resources.
     * Must be called on the OpenGL thread when chunk is unloaded.
     */
    public void delete() {
        glDeleteVertexArrays(vaoId);
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
