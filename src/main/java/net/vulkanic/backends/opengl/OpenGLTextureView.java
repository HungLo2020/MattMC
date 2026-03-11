package net.vulkanic.backends.opengl;

import net.blaze3d.opengl.GlTexture;
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

    private final VulkanicTexture texture;
    private final int baseMipLevel;
    private final int mipLevelCount;
    private boolean closed;

    /**
     * Creates a texture view over the given texture and mip range.
     *
     * <p>The texture may be any {@link VulkanicTexture} — including {@code OpenGLTexture}
     * (the normal Vulkanic path) or a Blaze3D {@code GlTexture} (which implements
     * {@code VulkanicTexture} via {@code GpuTexture}). This allows
     * {@code GlCommandEncoder.createRenderPass} to create views without the
     * {@code createTextureViewFromGlHandle} bridge.
     *
     * @param texture      the backing texture
     * @param baseMipLevel first mip level exposed by this view
     * @param mipLevelCount number of mip levels exposed by this view
     */
    public OpenGLTextureView(VulkanicTexture texture, int baseMipLevel, int mipLevelCount) {
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

    /**
     * Returns the native OpenGL texture object name (the integer handle) for this view.
     *
     * <p>Works for both {@link OpenGLTexture}-backed views (the normal Vulkanic-managed path)
     * and {@link GlTexture}-backed views (Blaze3D textures that implement
     * {@link VulkanicTexture} via {@code GpuTexture}).
     *
     * <p>This is the method callers should use when they need the raw GL handle — do NOT
     * call {@code openGLTexture().getGlHandle()} because {@link #openGLTexture()} returns
     * {@code null} when the backing texture is a {@code GlTexture}.
     *
     * @return the GL texture object name
     * @throws IllegalStateException if the backing texture type is not supported by
     *                               this OpenGL backend implementation
     */
    public int glHandle() {
        if (texture instanceof OpenGLTexture o) {
            return o.getGlHandle();
        }
        if (texture instanceof GlTexture t) {
            return t.getGlHandle();
        }
        throw new IllegalStateException(
            "OpenGLTextureView backed by unsupported texture type: " + texture.getClass().getName() +
            " — cannot extract a GL texture handle");
    }

    /**
     * Returns the backing texture cast to {@link OpenGLTexture}, or {@code null} if the
     * backing texture is a Blaze3D {@code GlTexture} rather than a Vulkanic-managed
     * {@code OpenGLTexture}.
     *
     * <p><strong>Do not call {@code openGLTexture().getGlHandle()} — use {@link #glHandle()}
     * instead,</strong> which handles both texture types correctly without a null-check.
     */
    @org.jetbrains.annotations.Nullable
    public OpenGLTexture openGLTexture() {
        return texture instanceof OpenGLTexture o ? o : null;
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
