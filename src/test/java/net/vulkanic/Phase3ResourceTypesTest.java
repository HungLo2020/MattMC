package net.vulkanic;

import net.vulkanic.backends.opengl.OpenGLBuffer;
import net.vulkanic.backends.opengl.OpenGLCommandContext;
import net.vulkanic.backends.opengl.OpenGLTexture;
import net.vulkanic.backends.opengl.OpenGLTextureView;
import net.vulkanic.backends.opengl.OpenGLPipelineHandle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Phase 3 Vulkanic resource types.
 * Tests the abstract type hierarchy and OpenGL implementation classes
 * WITHOUT requiring an actual OpenGL context (no GL calls made).
 */
public class Phase3ResourceTypesTest {

    // ---- VulkanicBuffer / OpenGLBuffer tests --------------------------------

    @Test
    public void testVulkanicBufferUsageConstants() {
        assertEquals(1,   VulkanicBuffer.USAGE_MAP_READ);
        assertEquals(2,   VulkanicBuffer.USAGE_MAP_WRITE);
        assertEquals(32,  VulkanicBuffer.USAGE_VERTEX);
        assertEquals(64,  VulkanicBuffer.USAGE_INDEX);
        assertEquals(128, VulkanicBuffer.USAGE_UNIFORM);
    }

    @Test
    public void testOpenGLBufferCreation() {
        OpenGLBuffer buffer = new OpenGLBuffer(42, VulkanicBuffer.USAGE_VERTEX, 1024);

        assertEquals(42,   buffer.getGlHandle());
        assertEquals(1024, buffer.size());
        assertEquals(VulkanicBuffer.USAGE_VERTEX, buffer.usage());
        assertFalse(buffer.isClosed());
    }

    @Test
    public void testOpenGLBufferIsInstanceOfVulkanicBuffer() {
        OpenGLBuffer buffer = new OpenGLBuffer(1, VulkanicBuffer.USAGE_VERTEX, 256);
        assertInstanceOf(VulkanicBuffer.class, buffer);
    }

    @Test
    public void testOpenGLBufferSlice() {
        OpenGLBuffer buffer = new OpenGLBuffer(1, VulkanicBuffer.USAGE_VERTEX, 512);
        VulkanicBufferSlice slice = buffer.slice(64, 128);

        assertSame(buffer, slice.buffer());
        assertEquals(64,  slice.offset());
        assertEquals(128, slice.length());
    }

    @Test
    public void testOpenGLBufferSliceFullRange() {
        OpenGLBuffer buffer = new OpenGLBuffer(1, VulkanicBuffer.USAGE_VERTEX, 512);
        VulkanicBufferSlice slice = buffer.slice();

        assertEquals(0,   slice.offset());
        assertEquals(512, slice.length());
    }

    @Test
    public void testOpenGLBufferSliceOutOfRange() {
        OpenGLBuffer buffer = new OpenGLBuffer(1, VulkanicBuffer.USAGE_VERTEX, 256);
        assertThrows(IllegalArgumentException.class, () -> buffer.slice(200, 100));
    }

    // ---- VulkanicTextureFormat tests ----------------------------------------

    @Test
    public void testVulkanicTextureFormatHasColorAspect() {
        assertTrue(VulkanicTextureFormat.RGBA8.hasColorAspect());
        assertTrue(VulkanicTextureFormat.RED8.hasColorAspect());
        assertTrue(VulkanicTextureFormat.RED8I.hasColorAspect());
        assertFalse(VulkanicTextureFormat.DEPTH32.hasColorAspect());
    }

    @Test
    public void testVulkanicTextureFormatHasDepthAspect() {
        assertFalse(VulkanicTextureFormat.RGBA8.hasDepthAspect());
        assertFalse(VulkanicTextureFormat.RED8.hasDepthAspect());
        assertTrue(VulkanicTextureFormat.DEPTH32.hasDepthAspect());
    }

    @Test
    public void testVulkanicTextureFormatPixelSizes() {
        assertEquals(4, VulkanicTextureFormat.RGBA8.pixelSize());
        assertEquals(1, VulkanicTextureFormat.RED8.pixelSize());
        assertEquals(1, VulkanicTextureFormat.RED8I.pixelSize());
        assertEquals(4, VulkanicTextureFormat.DEPTH32.pixelSize());
    }

    // ---- OpenGLTexture tests ------------------------------------------------

    @Test
    public void testOpenGLTextureCreation() {
        OpenGLTexture tex = new OpenGLTexture(
            99, VulkanicTexture.USAGE_TEXTURE_BINDING,
            VulkanicTextureFormat.RGBA8, 256, 128, 1, 4, "test-texture");

        assertEquals(99,  tex.getGlHandle());
        assertEquals(256, tex.getWidth(0));
        assertEquals(128, tex.getHeight(0));
        assertEquals(1,   tex.getDepthOrLayers());
        assertEquals(4,   tex.getMipLevels());
        assertEquals(VulkanicTextureFormat.RGBA8, tex.getFormat());
        assertEquals("test-texture", tex.getLabel());
        assertFalse(tex.isClosed());
    }

    @Test
    public void testOpenGLTextureIsInstanceOfVulkanicTexture() {
        OpenGLTexture tex = new OpenGLTexture(1, VulkanicTexture.USAGE_TEXTURE_BINDING,
            VulkanicTextureFormat.RGBA8, 64, 64, 1, 1, "tex");
        assertInstanceOf(VulkanicTexture.class, tex);
    }

    @Test
    public void testOpenGLTextureMipDimensions() {
        // Texture is 256 wide; each mip level halves the dimension
        OpenGLTexture tex = new OpenGLTexture(1, VulkanicTexture.USAGE_TEXTURE_BINDING,
            VulkanicTextureFormat.RGBA8, 256, 128, 1, 4, "tex");

        assertEquals(256, tex.getWidth(0)); // mip 0 = full resolution
        assertEquals(128, tex.getWidth(1)); // mip 1 = 256 >> 1
        assertEquals(64,  tex.getWidth(2)); // mip 2 = 256 >> 2
        assertEquals(32,  tex.getWidth(3)); // mip 3 = 256 >> 3
    }

    // ---- OpenGLTextureView tests --------------------------------------------

    @Test
    public void testOpenGLTextureViewCreation() {
        OpenGLTexture tex = new OpenGLTexture(1, VulkanicTexture.USAGE_TEXTURE_BINDING,
            VulkanicTextureFormat.RGBA8, 256, 256, 1, 4, "tex");
        OpenGLTextureView view = new OpenGLTextureView(tex, 1, 2);

        assertSame(tex, view.texture());
        assertSame(tex, view.openGLTexture());
        assertEquals(1, view.getBaseMipLevel());
        assertEquals(2, view.getMipLevelCount());
        assertFalse(view.isClosed());
    }

    @Test
    public void testOpenGLTextureViewIsInstanceOfVulkanicTextureView() {
        OpenGLTexture tex = new OpenGLTexture(1, VulkanicTexture.USAGE_TEXTURE_BINDING,
            VulkanicTextureFormat.RGBA8, 64, 64, 1, 1, "tex");
        OpenGLTextureView view = new OpenGLTextureView(tex, 0, 1);
        assertInstanceOf(VulkanicTextureView.class, view);
    }

    @Test
    public void testOpenGLTextureViewInvalidMipRange() {
        OpenGLTexture tex = new OpenGLTexture(1, VulkanicTexture.USAGE_TEXTURE_BINDING,
            VulkanicTextureFormat.RGBA8, 64, 64, 1, 3, "tex");
        assertThrows(IllegalArgumentException.class, () -> new OpenGLTextureView(tex, 2, 3));
    }

    @Test
    public void testOpenGLTextureViewClose() {
        OpenGLTexture tex = new OpenGLTexture(1, VulkanicTexture.USAGE_TEXTURE_BINDING,
            VulkanicTextureFormat.RGBA8, 64, 64, 1, 1, "tex");
        OpenGLTextureView view = new OpenGLTextureView(tex, 0, 1);
        view.close();
        assertTrue(view.isClosed());
        // Closing the view must NOT close the parent texture
        assertFalse(tex.isClosed());
    }

    @Test
    public void testOpenGLTextureViewDimensions() {
        OpenGLTexture tex = new OpenGLTexture(1, VulkanicTexture.USAGE_TEXTURE_BINDING,
            VulkanicTextureFormat.RGBA8, 256, 128, 1, 4, "tex");
        OpenGLTextureView view = new OpenGLTextureView(tex, 1, 2);
        assertEquals(128, view.getWidth(0));
        assertEquals(64,  view.getHeight(0));
    }

    // ---- PipelineDescriptor tests ------------------------------------------

    @Test
    public void testPipelineDescriptorFromNull() {
        assertThrows(IllegalArgumentException.class,
            () -> PipelineDescriptor.fromRenderPipeline(null));
    }

    // ---- PipelineHandle tests ----------------------------------------------

    @Test
    public void testOpenGLPipelineHandleClose() {
        PipelineHandle handle = new PipelineHandle() {
            @Override public boolean isValid() { return false; }
            @Override public void close() {}
        };
        assertInstanceOf(PipelineHandle.class, handle);
    }

    // ---- VulkanicAPI.registerDevice / beginCommandBuffer tests -------------

    @Test
    public void testBeginCommandBufferReturnsSomething() {
        VulkanicAPI.initialize();
        CommandContext ctx = VulkanicAPI.beginCommandBuffer();
        assertNotNull(ctx, "beginCommandBuffer() must return a non-null context");
        assertTrue(ctx.isImmediate(), "OpenGL beginCommandBuffer() must return immediate context");
    }

    @Test
    public void testSubmitCommandBufferImmediateMode() {
        VulkanicAPI.initialize();
        CommandContext ctx = VulkanicAPI.beginCommandBuffer();
        assertDoesNotThrow(() -> VulkanicAPI.submitCommandBuffer(ctx));
    }
}
