package net.vulkanic.backends.opengl;

import net.vulkanic.VulkanicTexture;
import net.vulkanic.VulkanicTextureFormat;
import org.lwjgl.opengl.GL11;

/**
 * OpenGL implementation of {@link VulkanicTexture}.
 *
 * <p>Wraps a GL texture object identified by a native integer handle.
 * Resources are freed when {@link #close()} is called (calls glDeleteTextures),
 * unless this is a <em>non-owning</em> instance created via {@link #nonOwning}.
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
    /** Whether this instance owns the GL texture object (responsible for deleting it). */
    private final boolean owned;
    private boolean closed;

    /**
     * Creates an owning OpenGLTexture. {@link #close()} will call {@code glDeleteTextures}.
     */
    public OpenGLTexture(int glHandle, int usage, VulkanicTextureFormat format,
                          int width, int height, int depthOrLayers, int mipLevels, String label) {
        this(glHandle, usage, format, width, height, depthOrLayers, mipLevels, label, true);
    }

    private OpenGLTexture(int glHandle, int usage, VulkanicTextureFormat format,
                           int width, int height, int depthOrLayers, int mipLevels,
                           String label, boolean owned) {
        this.glHandle = glHandle;
        this.usage = usage;
        this.format = format;
        this.width = width;
        this.height = height;
        this.depthOrLayers = depthOrLayers;
        this.mipLevels = mipLevels;
        this.label = label;
        this.owned = owned;
        this.closed = false;
    }

    /**
     * Creates a <em>non-owning</em> OpenGLTexture that wraps an existing GL texture handle.
     *
     * <p>Calling {@link #close()} on the returned instance is a no-op — the caller
     * remains responsible for the GL texture object's lifetime. This is used as a
     * bridge when handing existing Blaze3D {@code GlTexture}-backed views into the
     * Vulkanic render-pass API.
     *
     * @param glHandle      the GL texture object name (from glGenTextures / glCreateTextures)
     * @param usage         usage flags (VulkanicTexture.USAGE_* constants)
     * @param format        texture format
     * @param width         width at mip level 0
     * @param height        height at mip level 0
     * @param depthOrLayers depth (3D) or layer count (array); usually 1
     * @param mipLevels     total mip levels
     * @param label         debug label
     * @return a non-owning wrapper around the given GL texture handle
     */
    public static OpenGLTexture nonOwning(int glHandle, int usage, VulkanicTextureFormat format,
                                           int width, int height, int depthOrLayers,
                                           int mipLevels, String label) {
        return new OpenGLTexture(glHandle, usage, format, width, height, depthOrLayers,
                                  mipLevels, label, false);
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
            if (owned) {
                // Clear any accumulated GL errors before deletion to avoid false positives
                // when the caller later checks for errors from unrelated operations.
                GL11.glGetError();
                GL11.glDeleteTextures(glHandle);
            }
            // Non-owning instances: close() is a no-op (caller manages the GL texture lifetime).
        }
    }

    @Override
    public String toString() {
        return "OpenGLTexture{handle=" + glHandle + ", " + width + "x" + height +
               ", format=" + format + ", mips=" + mipLevels +
               ", owned=" + owned + ", closed=" + closed + "}";
    }
}
