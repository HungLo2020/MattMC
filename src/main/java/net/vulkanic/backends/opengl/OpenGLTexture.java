package net.vulkanic.backends.opengl;

import net.vulkanic.resources.VulkanicTexture;
import net.vulkanic.resources.VulkanicTextureFormat;
import org.lwjgl.opengl.GL11;

/**
 * OpenGL implementation of {@link VulkanicTexture}.
 *
 * <p>Holds a GL texture object name allocated with {@code glGenTextures} /
 * {@code glCreateTextures}. Created and destroyed exclusively by {@link OpenGLBackend}.
 */
public class OpenGLTexture implements VulkanicTexture {

    private final int glHandle;
    private final int usage;
    private final VulkanicTextureFormat format;
    private final int width;
    private final int height;
    private final int depthOrLayers;
    private final int mipLevels;
    private final String label;
    private boolean closed;

    OpenGLTexture(int glHandle, int usage, VulkanicTextureFormat format, String label,
                  int width, int height, int depthOrLayers, int mipLevels) {
        this.glHandle      = glHandle;
        this.usage         = usage;
        this.format        = format;
        this.label         = label != null ? label : String.valueOf(glHandle);
        this.width         = width;
        this.height        = height;
        this.depthOrLayers = depthOrLayers;
        this.mipLevels     = mipLevels;
        this.closed        = false;
    }

    @Override public long getNativeHandle()           { return glHandle; }
    @Override public int  getWidth()                  { return width; }
    @Override public int  getHeight()                 { return height; }
    @Override public int  getDepthOrLayers()          { return depthOrLayers; }
    @Override public int  getMipLevels()              { return mipLevels; }
    @Override public int  getUsage()                  { return usage; }
    @Override public String getLabel()                { return label; }
    @Override public boolean isClosed()               { return closed; }
    public VulkanicTextureFormat getFormat()          { return format; }

    /** Implements {@link net.vulkanic.resources.VulkanicTexture#getVulkanicFormat()}. */
    @Override
    public VulkanicTextureFormat getVulkanicFormat()  { return format; }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            GL11.glDeleteTextures(glHandle);
        }
    }

    @Override
    public String toString() {
        return "OpenGLTexture{handle=" + glHandle + ", " + format + ", " + width + "x" + height
                + ", mips=" + mipLevels + ", closed=" + closed + "}";
    }
}
