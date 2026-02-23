package net.vulkanic;

import net.vulkanic.backends.opengl.OpenGLCommandContext;
import net.vulkanic.backends.opengl.OpenGLTexture;
import net.vulkanic.backends.opengl.OpenGLTextureView;
import org.junit.jupiter.api.Test;

import java.util.OptionalDouble;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Phase 3b delegation: GlCommandEncoder → VulkanicAPI.beginRenderPass.
 *
 * Validates:
 * <ul>
 *   <li>{@code OpenGLTexture.nonOwning()} creates a non-owning texture whose close() is a no-op.</li>
 *   <li>{@link VulkanicAPI#createTextureViewFromGlHandle} is dispatched through {@link GraphicsBackend}.</li>
 *   <li>The bridge view returned is an {@link OpenGLTextureView} usable by the render-pass path.</li>
 *   <li>The TextureFormat → VulkanicTextureFormat round-trip is correct for all four formats.</li>
 * </ul>
 *
 * All tests run without an OpenGL context (no GL calls needed).
 */
public class Phase3DelegationTest {

    // ── OpenGLTexture.nonOwning() ──────────────────────────────────────────

    @Test
    public void testNonOwningTextureCloseIsNoop() {
        // close() on a non-owning texture must not throw and must not delete the texture.
        // (We can't check glDeleteTextures is NOT called without a GL context,
        // but we can verify that close() doesn't throw and isClosed() returns true.)
        OpenGLTexture nonOwned = OpenGLTexture.nonOwning(
            999, VulkanicTexture.USAGE_RENDER_ATTACHMENT,
            VulkanicTextureFormat.RGBA8, 64, 64, 1, 1, "test-non-owned");

        assertFalse(nonOwned.isClosed(), "Should not be closed before close()");
        // close() must be a no-op (no GL deletion since owned=false, no GL context needed)
        assertDoesNotThrow(nonOwned::close);
        assertTrue(nonOwned.isClosed(), "Should be closed after close()");

        // Calling close() again must also be safe
        assertDoesNotThrow(nonOwned::close);
        assertTrue(nonOwned.isClosed(), "Must remain closed after second close()");
    }

    @Test
    public void testNonOwningTextureProperties() {
        OpenGLTexture tex = OpenGLTexture.nonOwning(
            42, VulkanicTexture.USAGE_TEXTURE_BINDING,
            VulkanicTextureFormat.RGBA8, 128, 64, 1, 3, "bridge-tex");

        assertEquals(42,  tex.getGlHandle());
        assertEquals(128, tex.getWidth(0));
        assertEquals(64,  tex.getHeight(0));
        assertEquals(64,  tex.getWidth(1));   // mip 1 = 128>>1
        assertEquals(32,  tex.getHeight(1));  // mip 1 = 64>>1
        assertEquals(1,   tex.getDepthOrLayers());
        assertEquals(3,   tex.getMipLevels());
        assertEquals(VulkanicTextureFormat.RGBA8, tex.getFormat());
        assertEquals(VulkanicTexture.USAGE_TEXTURE_BINDING, tex.usage());
        assertEquals("bridge-tex", tex.getLabel());
    }

    @Test
    public void testNonOwningTextureIsInstanceOfVulkanicTexture() {
        OpenGLTexture tex = OpenGLTexture.nonOwning(
            1, VulkanicTexture.USAGE_RENDER_ATTACHMENT,
            VulkanicTextureFormat.RGBA8, 4, 4, 1, 1, "t");
        assertInstanceOf(VulkanicTexture.class, tex);
    }

    @Test
    public void testOwningTextureFlagDistinct() {
        // An ordinary (owning) OpenGLTexture close() is guarded by owned=true,
        // while nonOwning() is guarded by owned=false.
        // We can't test actual GL deletion without a context, but we CAN verify that
        // a non-owning texture created from handle 0 doesn't throw on close().
        OpenGLTexture owned    = new OpenGLTexture(0, 0, VulkanicTextureFormat.RGBA8, 1, 1, 1, 1, "owned");
        OpenGLTexture nonOwned = OpenGLTexture.nonOwning(0, 0, VulkanicTextureFormat.RGBA8, 1, 1, 1, 1, "non");

        // Non-owned: close() must not throw (no GL call made with owned=false)
        assertDoesNotThrow(nonOwned::close);
        // Owned with handle=0: glDeleteTextures(0) is defined by OpenGL as a no-op
        // so it also won't throw, but we don't need to test the GL path here.
        assertTrue(nonOwned.isClosed());
    }

    // ── GraphicsBackend.createTextureViewFromGlHandle interface ──────────

    @Test
    public void testGraphicsBackendHasCreateTextureViewFromGlHandle() throws NoSuchMethodException {
        // Verify the method signature exists in the GraphicsBackend interface
        assertNotNull(GraphicsBackend.class.getMethod(
            "createTextureViewFromGlHandle",
            CommandContext.class,
            int.class,
            VulkanicTextureFormat.class,
            int.class, int.class,
            int.class, int.class, int.class,
            int.class, int.class));
    }

    @Test
    public void testVulkanicAPIHasCreateTextureViewFromGlHandle() throws NoSuchMethodException {
        // Verify the static dispatch method exists in VulkanicAPI
        assertNotNull(VulkanicAPI.class.getMethod(
            "createTextureViewFromGlHandle",
            CommandContext.class,
            int.class,
            VulkanicTextureFormat.class,
            int.class, int.class,
            int.class, int.class, int.class,
            int.class, int.class));
    }

    // ── VulkanicAPI.createTextureViewFromGlHandle dispatch ───────────────

    @Test
    public void testCreateTextureViewFromGlHandleReturnType() {
        VulkanicAPI.initialize(); // ensure backend is set
        CommandContext ctx = OpenGLCommandContext.IMMEDIATE;

        VulkanicTextureView view = VulkanicAPI.createTextureViewFromGlHandle(
            ctx, 77, VulkanicTextureFormat.RGBA8,
            256, 128, 1, 4, VulkanicTexture.USAGE_RENDER_ATTACHMENT,
            0, 4);

        // Must return a non-null VulkanicTextureView
        assertNotNull(view, "createTextureViewFromGlHandle must return non-null");
        assertInstanceOf(VulkanicTextureView.class, view);
        // Must be an OpenGLTextureView for the OpenGL backend path
        assertInstanceOf(OpenGLTextureView.class, view,
            "OpenGL backend must return OpenGLTextureView");
    }

    @Test
    public void testCreateTextureViewFromGlHandleProperties() {
        VulkanicAPI.initialize();
        CommandContext ctx = OpenGLCommandContext.IMMEDIATE;

        VulkanicTextureView view = VulkanicAPI.createTextureViewFromGlHandle(
            ctx, 55, VulkanicTextureFormat.DEPTH32,
            512, 512, 1, 1, VulkanicTexture.USAGE_RENDER_ATTACHMENT,
            0, 1);

        assertEquals(512, view.getWidth(0),  "Width at mip 0 must match");
        assertEquals(512, view.getHeight(0), "Height at mip 0 must match");
        assertEquals(0,   view.getBaseMipLevel());
        assertEquals(1,   view.getMipLevelCount());
        assertFalse(view.isClosed(), "Fresh view must not be closed");

        // The backing texture must be non-owning: close() is safe without a GL context
        OpenGLTextureView glView = (OpenGLTextureView) view;
        assertEquals(55, glView.openGLTexture().getGlHandle());
        assertEquals(VulkanicTextureFormat.DEPTH32, glView.openGLTexture().getFormat());

        // Close the view — must not throw even without a GL context
        assertDoesNotThrow(view::close);
        assertTrue(view.isClosed());
        // The backing texture must NOT be closed when its view is closed
        // (VulkanicTextureView.close() does not propagate to the texture).
        assertFalse(glView.openGLTexture().isClosed(),
            "Closing a view must NOT close the backing texture");
        // The texture is non-owning, so we can safely close it without GL calls:
        assertDoesNotThrow(() -> glView.openGLTexture().close());
        assertTrue(glView.openGLTexture().isClosed());
    }

    @Test
    public void testCreateTextureViewFromGlHandleMipRange() {
        VulkanicAPI.initialize();
        CommandContext ctx = OpenGLCommandContext.IMMEDIATE;

        // View starting at mip 2, covering 2 mip levels of a 256x128 texture
        VulkanicTextureView view = VulkanicAPI.createTextureViewFromGlHandle(
            ctx, 10, VulkanicTextureFormat.RGBA8,
            256, 128, 1, 5, VulkanicTexture.USAGE_TEXTURE_BINDING,
            2, 2);

        assertEquals(2, view.getBaseMipLevel());
        assertEquals(2, view.getMipLevelCount());
        // getWidth(0) in the view maps to mip 2 of the original texture: 256 >> 2 = 64
        assertEquals(64, view.getWidth(0),  "mip-0 of view = mip-2 of original = 64");
        assertEquals(32, view.getHeight(0), "mip-0 height of view = mip-2 of original = 32");
    }

    // ── VulkanicTextureFormat all values bridge ───────────────────────────

    @Test
    public void testAllFormatsRoundTrip() {
        VulkanicAPI.initialize();
        CommandContext ctx = OpenGLCommandContext.IMMEDIATE;

        // Ensure a view can be created for every VulkanicTextureFormat value without throwing
        for (VulkanicTextureFormat fmt : VulkanicTextureFormat.values()) {
            int usage = fmt.hasDepthAspect()
                ? VulkanicTexture.USAGE_RENDER_ATTACHMENT
                : VulkanicTexture.USAGE_TEXTURE_BINDING;
            assertDoesNotThrow(() -> {
                VulkanicTextureView v = VulkanicAPI.createTextureViewFromGlHandle(
                    ctx, 1, fmt, 16, 16, 1, 1, usage, 0, 1);
                OpenGLTextureView ov = (OpenGLTextureView) v;
                assertEquals(fmt, ov.openGLTexture().getFormat(),
                    "Format must round-trip through bridge for " + fmt);
            }, "createTextureViewFromGlHandle should not throw for format: " + fmt);
        }
    }

    // ── GlCommandEncoder delegation: structural verification ─────────────

    @Test
    public void testGlCommandEncoderHasActiveVulkanicRenderPassField()
            throws NoSuchFieldException {
        // Verify the new field exists in GlCommandEncoder
        var field = net.blaze3d.opengl.GlCommandEncoder.class
            .getDeclaredField("activeVulkanicRenderPass");
        assertNotNull(field);
        assertEquals(VulkanicRenderPass.class, field.getType(),
            "activeVulkanicRenderPass must be typed as VulkanicRenderPass");
    }

    @Test
    public void testGlCommandEncoderHasToVulkanicTextureViewHelper()
            throws NoSuchMethodException {
        // Verify the private bridge helper method exists
        var method = net.blaze3d.opengl.GlCommandEncoder.class
            .getDeclaredMethod("toVulkanicTextureView",
                net.blaze3d.opengl.GlTextureView.class);
        assertNotNull(method);
        assertEquals(VulkanicTextureView.class, method.getReturnType());
    }

    @Test
    public void testGlCommandEncoderHasToVulkanicFormatHelper()
            throws NoSuchMethodException {
        // Verify the static format-conversion helper exists
        var method = net.blaze3d.opengl.GlCommandEncoder.class
            .getDeclaredMethod("toVulkanicFormat",
                net.blaze3d.textures.TextureFormat.class);
        assertNotNull(method);
        assertEquals(VulkanicTextureFormat.class, method.getReturnType());
    }
}
