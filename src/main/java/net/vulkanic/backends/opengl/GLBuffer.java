package net.vulkanic.backends.opengl;

import net.vulkanic.Buffer;
import net.vulkanic.BufferUsage;

/**
 * OpenGL implementation of Buffer interface.
 * Wraps a GL buffer object.
 */
public class GLBuffer implements Buffer {
    
    private final long handle;  // GL buffer ID
    private final long size;
    private final BufferUsage usage;
    
    public GLBuffer(long handle, long size, BufferUsage usage) {
        this.handle = handle;
        this.size = size;
        this.usage = usage;
    }
    
    @Override
    public long getHandle() {
        return handle;
    }
    
    @Override
    public long getSize() {
        return size;
    }
    
    @Override
    public BufferUsage getUsage() {
        return usage;
    }
}
