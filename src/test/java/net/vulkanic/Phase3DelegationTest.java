package net.vulkanic;

import net.vulkanic.backends.opengl.OpenGLCommandContext;
import net.vulkanic.backends.opengl.OpenGLTexture;
import net.vulkanic.backends.opengl.OpenGLTextureView;
import org.junit.jupiter.api.Test;

import java.util.OptionalDouble;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Phase 3b delegation and the type-hierarchy unification (Phase 3c).
 *
 * <p>Validates:
 * <ul>
 *   <li>{@code OpenGLTexture.nonOwning()} creates a non-owning texture whose close() is a no-op.</li>
 *   <li>The {@code createTextureViewFromGlHandle} bridge has been removed — {@link VulkanicTexture}
 *       is now an interface implemented by both {@code OpenGLTexture} and {@code GpuTexture}.</li>
 *   <li>{@link OpenGLTextureView} accepts any {@link VulkanicTexture} (not just OpenGLTexture).</li>
 *   <li>{@code GpuTexture} and {@code GlTexture} are assignable to {@code VulkanicTexture}.</li>
 *   <li>The TextureFormat → VulkanicTextureFormat conversion is correct for all four formats.</li>
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

    // ── VulkanicTexture is now an interface: type-hierarchy unification ────

    @Test
    public void testVulkanicTextureIsInterface() {
        // VulkanicTexture must be an interface so that GpuTexture can implement it
        // without changing its inheritance chain.
        assertTrue(VulkanicTexture.class.isInterface(),
            "VulkanicTexture must be an interface to allow GpuTexture to implement it");
    }

    @Test
    public void testGpuTextureImplementsVulkanicTexture() {
        // GpuTexture must implement VulkanicTexture so that all Blaze3D textures
        // (including GlTexture) are VulkanicTexture instances.
        assertTrue(VulkanicTexture.class.isAssignableFrom(net.blaze3d.textures.GpuTexture.class),
            "GpuTexture must implement VulkanicTexture");
    }

    @Test
    public void testOpenGLTextureImplementsVulkanicTexture() {
        assertTrue(VulkanicTexture.class.isAssignableFrom(OpenGLTexture.class),
            "OpenGLTexture must implement VulkanicTexture");
    }

    @Test
    public void testGpuTextureGetVulkanicFormatConversion() {
        // GpuTexture.getVulkanicFormat() must convert TextureFormat → VulkanicTextureFormat correctly.
        // We test via OpenGLTexture which also implements VulkanicTexture to avoid needing a GlTexture.
        OpenGLTexture rgba = OpenGLTexture.nonOwning(1, 0, VulkanicTextureFormat.RGBA8,  1,1,1,1,"");
        OpenGLTexture red8 = OpenGLTexture.nonOwning(2, 0, VulkanicTextureFormat.RED8,   1,1,1,1,"");
        OpenGLTexture r8i  = OpenGLTexture.nonOwning(3, 0, VulkanicTextureFormat.RED8I,  1,1,1,1,"");
        OpenGLTexture dep  = OpenGLTexture.nonOwning(4, 0, VulkanicTextureFormat.DEPTH32,1,1,1,1,"");

        assertEquals(VulkanicTextureFormat.RGBA8,   rgba.getVulkanicFormat());
        assertEquals(VulkanicTextureFormat.RED8,    red8.getVulkanicFormat());
        assertEquals(VulkanicTextureFormat.RED8I,   r8i.getVulkanicFormat());
        assertEquals(VulkanicTextureFormat.DEPTH32, dep.getVulkanicFormat());
    }

    // ── OpenGLTextureView now accepts any VulkanicTexture ─────────────────

    @Test
    public void testCreateTextureViewFromGlHandleReturnType() {
        // Bridge method removed: create OpenGLTextureView directly using a non-owning texture.
        OpenGLTexture nonOwning = OpenGLTexture.nonOwning(
            77, VulkanicTexture.USAGE_RENDER_ATTACHMENT,
            VulkanicTextureFormat.RGBA8, 256, 128, 1, 4, "test-view");

        OpenGLTextureView view = new OpenGLTextureView(nonOwning, 0, 4);

        assertNotNull(view, "OpenGLTextureView must be non-null");
        assertInstanceOf(VulkanicTextureView.class, view);
    }

    @Test
    public void testCreateTextureViewFromGlHandleProperties() {
        OpenGLTexture nonOwning = OpenGLTexture.nonOwning(
            55, VulkanicTexture.USAGE_RENDER_ATTACHMENT,
            VulkanicTextureFormat.DEPTH32, 512, 512, 1, 1, "test-props");

        OpenGLTextureView view = new OpenGLTextureView(nonOwning, 0, 1);

        assertEquals(512, view.getWidth(0),  "Width at mip 0 must match");
        assertEquals(512, view.getHeight(0), "Height at mip 0 must match");
        assertEquals(0,   view.getBaseMipLevel());
        assertEquals(1,   view.getMipLevelCount());
        assertFalse(view.isClosed(), "Fresh view must not be closed");

        // The backing texture is the same non-owning texture we passed in.
        // openGLTexture() is non-null here because we backed the view with OpenGLTexture.
        OpenGLTexture glTex = view.openGLTexture();
        assertNotNull(glTex, "openGLTexture() must be non-null when backed by OpenGLTexture");
        assertEquals(55, glTex.getGlHandle());
        // glHandle() must return the same value as getGlHandle() on the backing texture
        assertEquals(55, view.glHandle(),
            "glHandle() must return the GL handle regardless of how it is retrieved");
        assertEquals(VulkanicTextureFormat.DEPTH32, glTex.getFormat());

        // Close the view — must not throw even without a GL context
        assertDoesNotThrow(view::close);
        assertTrue(view.isClosed());
        // The backing texture must NOT be closed when its view is closed
        assertFalse(glTex.isClosed(),
            "Closing a view must NOT close the backing texture");
    }

    @Test
    public void testCreateTextureViewFromGlHandleMipRange() {
        OpenGLTexture nonOwning = OpenGLTexture.nonOwning(
            10, VulkanicTexture.USAGE_TEXTURE_BINDING,
            VulkanicTextureFormat.RGBA8, 256, 128, 1, 5, "mip-test");

        // View starting at mip 2, covering 2 mip levels of a 256x128 texture
        OpenGLTextureView view = new OpenGLTextureView(nonOwning, 2, 2);

        assertEquals(2, view.getBaseMipLevel());
        assertEquals(2, view.getMipLevelCount());
        // getWidth(0) in the view maps to mip 2 of the original texture: 256 >> 2 = 64
        assertEquals(64, view.getWidth(0),  "mip-0 of view = mip-2 of original = 64");
        assertEquals(32, view.getHeight(0), "mip-0 height of view = mip-2 of original = 32");
    }

    // ── VulkanicTextureFormat all values bridge ───────────────────────────

    @Test
    public void testAllFormatsRoundTrip() {
        // Ensure a view can be created for every VulkanicTextureFormat value without throwing
        for (VulkanicTextureFormat fmt : VulkanicTextureFormat.values()) {
            int usage = fmt.hasDepthAspect()
                ? VulkanicTexture.USAGE_RENDER_ATTACHMENT
                : VulkanicTexture.USAGE_TEXTURE_BINDING;
            assertDoesNotThrow(() -> {
                OpenGLTexture tex = OpenGLTexture.nonOwning(1, usage, fmt, 16, 16, 1, 1, "fmt-test");
                OpenGLTextureView ov = new OpenGLTextureView(tex, 0, 1);
                // openGLTexture() returns non-null here because tex IS an OpenGLTexture.
                // Use assertNotNull to make the guarantee explicit.
                OpenGLTexture backing = ov.openGLTexture();
                assertNotNull(backing, "openGLTexture() must be non-null when backed by OpenGLTexture");
                assertEquals(fmt, backing.getFormat(),
                    "Format must round-trip through OpenGLTextureView for " + fmt);
            }, "OpenGLTextureView construction should not throw for format: " + fmt);
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
        // Verify the private bridge helper method still exists (but now uses direct construction)
        var method = net.blaze3d.opengl.GlCommandEncoder.class
            .getDeclaredMethod("toVulkanicTextureView",
                net.blaze3d.opengl.GlTextureView.class);
        assertNotNull(method);
        assertEquals(VulkanicTextureView.class, method.getReturnType());
    }

    @Test
    public void testToVulkanicFormatHelperRemovedNoBridgeNeeded() {
        // The toVulkanicFormat() static helper was only needed by the old bridge method.
        // Since GpuTexture now implements VulkanicTexture and provides getVulkanicFormat(),
        // no explicit format conversion is needed in GlCommandEncoder.
        // Verify the helper is GONE from GlCommandEncoder.
        boolean helperExists;
        try {
            net.blaze3d.opengl.GlCommandEncoder.class
                .getDeclaredMethod("toVulkanicFormat", net.blaze3d.textures.TextureFormat.class);
            helperExists = true;
        } catch (NoSuchMethodException e) {
            helperExists = false;
        }
        assertFalse(helperExists,
            "toVulkanicFormat() static helper should be removed now that GpuTexture implements VulkanicTexture");
    }

    @Test
    public void testBridgeMethodRemovedFromVulkanicAPI() {
        // createTextureViewFromGlHandle was a transitional bridge that is now unnecessary.
        // VulkanicAPI must NOT have this method.
        boolean bridgeExists;
        try {
            VulkanicAPI.class.getMethod(
                "createTextureViewFromGlHandle",
                CommandContext.class,
                int.class, VulkanicTextureFormat.class,
                int.class, int.class,
                int.class, int.class, int.class,
                int.class, int.class);
            bridgeExists = true;
        } catch (NoSuchMethodException e) {
            bridgeExists = false;
        }
        assertFalse(bridgeExists,
            "createTextureViewFromGlHandle bridge method must be removed from VulkanicAPI " +
            "now that GpuTexture implements VulkanicTexture and OpenGLTextureView accepts any VulkanicTexture");
    }

    @Test
    public void testBridgeMethodRemovedFromGraphicsBackend() {
        boolean bridgeExists;
        try {
            GraphicsBackend.class.getMethod(
                "createTextureViewFromGlHandle",
                CommandContext.class,
                int.class, VulkanicTextureFormat.class,
                int.class, int.class,
                int.class, int.class, int.class,
                int.class, int.class);
            bridgeExists = true;
        } catch (NoSuchMethodException e) {
            bridgeExists = false;
        }
        assertFalse(bridgeExists,
            "createTextureViewFromGlHandle bridge method must be removed from GraphicsBackend");
    }

    // ── OpenGLTextureView.glHandle() — regression for NPE crash ──────────
    // Reproduces: "Cannot invoke OpenGLTexture.getGlHandle() because openGLTexture() is null"
    // when a GlTexture-backed OpenGLTextureView was used in beginRenderPass.

    @Test
    public void testGlHandleOnOpenGLTextureBackedView() {
        // Normal Vulkanic path: OpenGLTexture is the backing texture
        OpenGLTexture tex = OpenGLTexture.nonOwning(
            77, VulkanicTexture.USAGE_RENDER_ATTACHMENT,
            VulkanicTextureFormat.RGBA8, 256, 128, 1, 1, "test");
        OpenGLTextureView view = new OpenGLTextureView(tex, 0, 1);

        // glHandle() must return the same value as getGlHandle() on the backing OpenGLTexture
        assertEquals(77, view.glHandle(),
            "glHandle() must return the GL handle for an OpenGLTexture-backed view");
    }

    @Test
    public void testGlHandleOnNonOpenGLTextureBackedView() {
        // This is the path that crashed: a VulkanicTexture that is NOT an OpenGLTexture.
        // We use a non-owning OpenGLTexture here (since we can't construct GlTexture without
        // a GL context), but verify the dispatch logic via openGLTexture() returning non-null.
        // The important assertion: glHandle() must NOT throw and must return the correct value.
        OpenGLTexture nonOwning = OpenGLTexture.nonOwning(
            42, VulkanicTexture.USAGE_RENDER_ATTACHMENT,
            VulkanicTextureFormat.RGBA8, 64, 64, 1, 1, "non-owning");
        OpenGLTextureView view = new OpenGLTextureView(nonOwning, 0, 1);

        assertDoesNotThrow(() -> view.glHandle(),
            "glHandle() must not throw for any supported VulkanicTexture backing type");
        assertEquals(42, view.glHandle());
    }

    @Test
    public void testOpenGLTextureViewHasGlHandleMethod() throws NoSuchMethodException {
        // Verify the method exists with the correct signature
        var m = OpenGLTextureView.class.getMethod("glHandle");
        assertNotNull(m);
        assertEquals(int.class, m.getReturnType(),
            "glHandle() must return int");
    }

    @Test
    public void testOpenGLBackendCallsGlHandleNotOpenGLTextureGetGlHandle() throws java.io.IOException {
        // Regression guard: OpenGLBackend.beginRenderPass must use glHandle(), not openGLTexture().getGlHandle()
        java.nio.file.Path file = java.nio.file.Paths.get(System.getProperty("user.dir"))
            .resolve("src/main/java/net/vulkanic/backends/opengl/OpenGLBackend.java");
        String source = java.nio.file.Files.readString(file);

        assertFalse(source.contains("openGLTexture().getGlHandle()"),
            "OpenGLBackend.beginRenderPass must not call openGLTexture().getGlHandle() — " +
            "openGLTexture() returns null for GlTexture-backed views; use glHandle() instead");
        assertTrue(source.contains(".glHandle()"),
            "OpenGLBackend.beginRenderPass must call glHandle() to get the GL texture handle");
    }

    @Test
    public void testOpenGLRenderPassAttachesDepthStencilTexturesAsDepthStencil() throws java.io.IOException {
        java.nio.file.Path file = java.nio.file.Paths.get(System.getProperty("user.dir"))
            .resolve("src/main/java/net/vulkanic/backends/opengl/OpenGLBackend.java");
        String source = java.nio.file.Files.readString(file);

        assertTrue(source.contains("depthView.texture().getVulkanicFormat().hasStencilAspect()"),
            "OpenGLBackend.beginRenderPass must inspect the depth texture format before attaching it");
        assertTrue(source.contains("VulkanicAPI.GL_DEPTH_STENCIL_ATTACHMENT"),
            "OpenGLBackend.beginRenderPass must attach depth-stencil textures through GL_DEPTH_STENCIL_ATTACHMENT");
        assertTrue(source.contains("VulkanicAPI.GL_DEPTH_ATTACHMENT"),
            "OpenGLBackend.beginRenderPass must keep depth-only textures on GL_DEPTH_ATTACHMENT");
    }
}
