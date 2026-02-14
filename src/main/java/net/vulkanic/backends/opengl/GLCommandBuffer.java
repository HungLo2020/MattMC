package net.vulkanic.backends.opengl;

import net.vulkanic.CommandBuffer;

/**
 * OpenGL implementation of CommandBuffer interface.
 * 
 * In OpenGL, this represents an immediate-mode execution context.
 * Commands execute immediately rather than being recorded and submitted later.
 */
public class GLCommandBuffer implements CommandBuffer {
    
    private final long handle;
    private boolean recording = false;
    
    public GLCommandBuffer(long handle) {
        this.handle = handle;
    }
    
    @Override
    public long getHandle() {
        return handle;
    }
    
    @Override
    public boolean isImmediate() {
        // OpenGL always uses immediate mode
        return true;
    }
    
    @Override
    public String getDebugName() {
        return "OpenGL Immediate CommandBuffer " + handle;
    }
    
    @Override
    public boolean isRecording() {
        return recording;
    }
    
    // Package-private for OpenGLBackend
    void setRecording(boolean recording) {
        this.recording = recording;
    }
}
