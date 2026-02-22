package net.vulkanic.backends.opengl;

import net.vulkanic.VulkanicTexture;
import net.vulkanic.VulkanicTextureFormat;
import org.lwjgl.opengl.GL11;

/**
 * OpenGL implementation of {@link VulkanicTexture}.
 *
 * <p>Wraps a GL texture object identified by a native integer handle.
 * Resources are freed when {@link #close()} is called (calls glDeleteTextures).
 */
public class OpenGLTexture extends VulkanicTexture {

    private final int glHandle;
    private final int usage;
    private final VulkanicTextureFormat format;
    private final int width;
    private final int height;
    private final int depthOrLayers;
    private final int mipLevels;
    private final String label;
    private boolean closed;

    /**
     * Creates an OpenGLTexture wrapping the given GL texture handle.
     */
    public OpenGLTexture(int glHandle, int usage, VulkanicTextureFormat format,
                          int width, int height, int depthOrLayers, int mipLevels, String label) {
        this.glHandle = glHandle;
        this.usage = usage;
        this.format = format;
        this.width = width;
        this.height = height;
        this.depthOrLayers = depthOrLayers;
        this.mipLevels = mipLevels;
        this.label = label;
        this.closed = false;
    }

    /**
     * Returns the native GL texture object name.
     */
    public int getGlHandle() {
        return glHandle;
    }

    @Override
    public int getWidth(int mipLevel) {
        return Math.max(1, width >> mipLevel);
    }

    @Override
    public int getHeight(int mipLevel) {
        return Math.max(1, height >> mipLevel);
    }

    @Override
    public int getMipLevels() {
        return mipLevels;
    }

    @Override
    public int getDepthOrLayers() {
        return depthOrLayers;
    }

    @Override
    public VulkanicTextureFormat getFormat() {
        return format;
    }

    @Override
    public int usage() {
        return usage;
    }

    @Override
    public String getLabel() {
        return label;
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            // Clear any accumulated GL errors before deletion to avoid false positives
            // when the caller later checks for errors from unrelated operations.
            GL11.glGetError();
            GL11.glDeleteTextures(glHandle);
        }
    }

    @Override
    public String toString() {
        return "OpenGLTexture{handle=" + glHandle + ", " + width + "x" + height +
               ", format=" + format + ", mips=" + mipLevels + ", closed=" + closed + "}";
    }
}
