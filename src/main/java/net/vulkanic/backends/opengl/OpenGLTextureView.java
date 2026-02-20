package net.vulkanic.backends.opengl;

import net.vulkanic.resources.VulkanicTexture;
import net.vulkanic.resources.VulkanicTextureView;

/**
 * OpenGL implementation of {@link VulkanicTextureView}.
 *
 * <p>In OpenGL there is no separate image-view object for the render-pass use-case:
 * the texture object name is bound directly to an FBO attachment. This class therefore
 * holds a reference back to the parent {@link OpenGLTexture} and exposes the mip-level
 * window that was requested.
 *
 * <p>Created exclusively by {@link OpenGLBackend#createVulkanicTextureView}.
 */
public class OpenGLTextureView implements VulkanicTextureView {

    private final OpenGLTexture texture;
    private final int baseMipLevel;
    private final int mipLevelCount;

    OpenGLTextureView(OpenGLTexture texture, int baseMipLevel, int mipLevelCount) {
        this.texture       = texture;
        this.baseMipLevel  = baseMipLevel;
        this.mipLevelCount = mipLevelCount;
    }

    @Override
    public long getNativeHandle() {
        // For OpenGL, the "view handle" is just the underlying texture name.
        return texture.getNativeHandle();
    }

    @Override
    public VulkanicTexture texture() {
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
        return texture.isClosed();
    }

    @Override
    public void close() {
        // The view does not own the texture; closing it is a no-op.
        // Callers must close the underlying VulkanicTexture separately.
    }

    @Override
    public String toString() {
        return "OpenGLTextureView{texture=" + texture.getNativeHandle()
                + ", baseMip=" + baseMipLevel + ", mipCount=" + mipLevelCount + "}";
    }
}
