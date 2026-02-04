package net.vulkanic.backends.opengl;

import net.vulkanic.VulkanicBuffer;
import org.lwjgl.opengl.GL15;
import java.nio.ByteBuffer;

/**
 * OpenGL implementation of VulkanicBuffer.
 * 
 * Uses direct OpenGL buffer objects (VBOs) for GPU buffer management.
 */
public class OpenGLBuffer implements VulkanicBuffer {
    private final int bufferId;
    private final int sizeInBytes;
    
    /**
     * Creates a new OpenGL buffer with the specified size.
     * 
     * @param sizeInBytes the size of the buffer in bytes
     */
    public OpenGLBuffer(int sizeInBytes) {
        this.sizeInBytes = sizeInBytes;
        this.bufferId = GL15.glGenBuffers();
        
        // Allocate buffer storage
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, bufferId);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, sizeInBytes, GL15.GL_DYNAMIC_DRAW);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
    }
    
    @Override
    public void upload(ByteBuffer data) {
        if (data.remaining() > sizeInBytes) {
            throw new IllegalArgumentException("Data size (" + data.remaining() + ") exceeds buffer size (" + sizeInBytes + ")");
        }
        
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, bufferId);
        GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0, data);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
    }
    
    @Override
    public void uploadSubData(int offset, ByteBuffer data) {
        if (offset < 0 || offset >= sizeInBytes) {
            throw new IllegalArgumentException("Offset " + offset + " is out of bounds for buffer of size " + sizeInBytes);
        }
        if (offset + data.remaining() > sizeInBytes) {
            throw new IllegalArgumentException("Data at offset " + offset + " exceeds buffer size");
        }
        
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, bufferId);
        GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, offset, data);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
    }
    
    @Override
    public int getSize() {
        return sizeInBytes;
    }
    
    /**
     * Gets the OpenGL buffer ID.
     * Package-private for use by other OpenGL backend classes.
     * 
     * @return the buffer ID
     */
    int getBufferId() {
        return bufferId;
    }
    
    @Override
    public void close() {
        if (bufferId != 0) {
            GL15.glDeleteBuffers(bufferId);
        }
    }
}
