package net.vulkanic.backends.opengl;

import net.vulkanic.VulkanicTexture;
import net.vulkanic.VulkanicTextureView;

/**
 * OpenGL implementation of {@link VulkanicTextureView}.
 *
 * <p>In OpenGL, a texture view for a single texture and mip-range is a
 * lightweight descriptor — no separate GL object is required for the common case.
 * The underlying texture's GL handle is used directly.
 */
public class OpenGLTextureView extends VulkanicTextureView {

    private final OpenGLTexture texture;
    private final int baseMipLevel;
    private final int mipLevelCount;
    private boolean closed;

    /**
     * Creates a texture view over the given texture and mip range.
     *
     * @param texture      the backing texture
     * @param baseMipLevel first mip level exposed by this view
     * @param mipLevelCount number of mip levels exposed by this view
     */
    public OpenGLTextureView(OpenGLTexture texture, int baseMipLevel, int mipLevelCount) {
        if (baseMipLevel < 0 || mipLevelCount < 1 || baseMipLevel + mipLevelCount > texture.getMipLevels()) {
            throw new IllegalArgumentException(
                "Invalid mip range [" + baseMipLevel + ", " + (baseMipLevel + mipLevelCount) +
                ") for texture with " + texture.getMipLevels() + " mip levels");
        }
        this.texture = texture;
        this.baseMipLevel = baseMipLevel;
        this.mipLevelCount = mipLevelCount;
        this.closed = false;
    }

    @Override
    public VulkanicTexture texture() {
        return texture;
    }

    /** Returns the backing OpenGL texture (with the GL handle). */
    public OpenGLTexture openGLTexture() {
        return texture;
    }

    @Override
    public int getBaseMipLevel() {
        return baseMipLevel;
    }

    @Override
    public int getMipLevelCount() {
        return mipLevelCount;
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        // Views don't own the parent texture — closing a view does NOT close the texture.
        closed = true;
    }

    @Override
    public String toString() {
        return "OpenGLTextureView{texture=" + texture + ", baseMip=" + baseMipLevel +
               ", mipCount=" + mipLevelCount + "}";
    }
}
