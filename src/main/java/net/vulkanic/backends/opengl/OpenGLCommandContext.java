package net.vulkanic.backends.opengl;

import net.vulkanic.CommandContext;

/**
 * OpenGL implementation of CommandContext.
 * 
 * OpenGL is immediate-mode, so commands execute as soon as they're called.
 * This context is essentially a marker/validator rather than a true command buffer.
 * 
 * We use a singleton IMMEDIATE instance since OpenGL doesn't have multiple
 * command buffers - there's only one global state machine.
 */
public class OpenGLCommandContext implements CommandContext {
    
    /**
     * Singleton instance for immediate-mode OpenGL rendering.
     * All OpenGL commands use this single context.
     */
    public static final OpenGLCommandContext IMMEDIATE = new OpenGLCommandContext("OpenGL-Immediate");
    
    private final String debugName;
    
    /**
     * Private constructor - use IMMEDIATE singleton.
     */
    private OpenGLCommandContext(String debugName) {
        this.debugName = debugName;
    }
    
    @Override
    public boolean isImmediate() {
        return true; // OpenGL always executes immediately
    }
    
    @Override
    public long getHandle() {
        return 0; // OpenGL doesn't have command buffer handles
    }
    
    @Override
    public String getDebugName() {
        return debugName;
    }
    
    @Override
    public String toString() {
        return "OpenGLCommandContext{" + debugName + "}";
    }
}
