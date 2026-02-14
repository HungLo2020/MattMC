package net.vulkanic.backends.opengl;

import net.vulkanic.Format;
import net.vulkanic.Texture;

/**
 * OpenGL implementation of Texture interface.
 * Wraps a GL texture object.
 */
public class GLTexture implements Texture {
    
    private final long handle;  // GL texture ID
    private final int width;
    private final int height;
    private final Format format;
    
    public GLTexture(long handle, int width, int height, Format format) {
        this.handle = handle;
        this.width = width;
        this.height = height;
        this.format = format;
    }
    
    @Override
    public long getHandle() {
        return handle;
    }
    
    @Override
    public int getWidth() {
        return width;
    }
    
    @Override
    public int getHeight() {
        return height;
    }
    
    @Override
    public Format getFormat() {
        return format;
    }
}
