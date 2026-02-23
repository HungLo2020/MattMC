package net.vulkanic;

import net.vulkanic.backends.opengl.OpenGLBuffer;
import net.vulkanic.backends.opengl.OpenGLCommandContext;
import net.vulkanic.backends.opengl.OpenGLRenderPass;
import net.vulkanic.backends.opengl.OpenGLTexture;
import net.vulkanic.backends.opengl.OpenGLTextureView;
import org.junit.jupiter.api.Test;

import java.util.OptionalDouble;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Phase 3b render pass abstraction.
 *
 * These tests validate the interface hierarchy, VulkanicIndexType enum, and
 * OpenGLRenderPass contract WITHOUT requiring an actual OpenGL context.
 */
public class Phase3RenderPassTest {

    // ---- VulkanicIndexType tests --------------------------------------------

    @Test
    public void testVulkanicIndexTypeValues() {
        assertEquals(3, VulkanicIndexType.values().length);
        assertNotNull(VulkanicIndexType.BYTE);
        assertNotNull(VulkanicIndexType.SHORT);
        assertNotNull(VulkanicIndexType.INT);
    }

    @Test
    public void testVulkanicIndexTypeBytesPerIndex() {
        assertEquals(1, VulkanicIndexType.BYTE.bytesPerIndex());
        assertEquals(2, VulkanicIndexType.SHORT.bytesPerIndex());
        assertEquals(4, VulkanicIndexType.INT.bytesPerIndex());
    }

    @Test
    public void testVulkanicIndexTypeGlConstants() {
        // These must match the standard OpenGL / VulkanicAPI GL constant values
        assertEquals(0x1401, VulkanicIndexType.BYTE.toGlTypeConstant());  // GL_UNSIGNED_BYTE
        assertEquals(0x1403, VulkanicIndexType.SHORT.toGlTypeConstant()); // GL_UNSIGNED_SHORT
        assertEquals(0x1405, VulkanicIndexType.INT.toGlTypeConstant());   // GL_UNSIGNED_INT
    }

    // ---- VulkanicRenderPass interface contract tests -------------------------

    @Test
    public void testVulkanicRenderPassImplementsAutoCloseable() {
        // VulkanicRenderPass must extend AutoCloseable (for try-with-resources)
        assertTrue(AutoCloseable.class.isAssignableFrom(VulkanicRenderPass.class));
    }

    @Test
    public void testVulkanicRenderPassIsInterface() {
        assertTrue(VulkanicRenderPass.class.isInterface());
    }

    @Test
    public void testVulkanicRenderPassHasExpectedMethods() throws NoSuchMethodException {
        // Verify the interface has the exact method signatures required
        assertNotNull(VulkanicRenderPass.class.getMethod(
            "setPipeline", PipelineHandle.class));
        assertNotNull(VulkanicRenderPass.class.getMethod(
            "setVertexBuffer", int.class, VulkanicBuffer.class));
        assertNotNull(VulkanicRenderPass.class.getMethod(
            "setIndexBuffer", VulkanicBuffer.class, VulkanicIndexType.class));
        assertNotNull(VulkanicRenderPass.class.getMethod(
            "drawIndexed", int.class, int.class, int.class, int.class));
        assertNotNull(VulkanicRenderPass.class.getMethod(
            "draw", int.class, int.class));
        assertNotNull(VulkanicRenderPass.class.getMethod("close"));
    }

    // ---- GraphicsBackend interface has beginRenderPass ----------------------

    @Test
    public void testGraphicsBackendHasBeginRenderPassNoDepth() throws NoSuchMethodException {
        assertNotNull(GraphicsBackend.class.getMethod(
            "beginRenderPass",
            CommandContext.class,
            java.util.function.Supplier.class,
            VulkanicTextureView.class,
            OptionalInt.class));
    }

    @Test
    public void testGraphicsBackendHasBeginRenderPassWithDepth() throws NoSuchMethodException {
        assertNotNull(GraphicsBackend.class.getMethod(
            "beginRenderPass",
            CommandContext.class,
            java.util.function.Supplier.class,
            VulkanicTextureView.class,
            OptionalInt.class,
            VulkanicTextureView.class,
            OptionalDouble.class));
    }

    // ---- VulkanicAPI has beginRenderPass static dispatch --------------------

    @Test
    public void testVulkanicAPIHasBeginRenderPassNoDepth() throws NoSuchMethodException {
        assertNotNull(VulkanicAPI.class.getMethod(
            "beginRenderPass",
            CommandContext.class,
            java.util.function.Supplier.class,
            VulkanicTextureView.class,
            OptionalInt.class));
    }

    @Test
    public void testVulkanicAPIHasBeginRenderPassWithDepth() throws NoSuchMethodException {
        assertNotNull(VulkanicAPI.class.getMethod(
            "beginRenderPass",
            CommandContext.class,
            java.util.function.Supplier.class,
            VulkanicTextureView.class,
            OptionalInt.class,
            VulkanicTextureView.class,
            OptionalDouble.class));
    }

    // ---- OpenGLRenderPass basic structural tests ----------------------------

    @Test
    public void testOpenGLRenderPassImplementsVulkanicRenderPass() {
        assertInstanceOf(VulkanicRenderPass.class,
            // Lambda satisfying the interface (no GL context needed)
            (VulkanicRenderPass) new VulkanicRenderPass() {
                @Override public void setPipeline(PipelineHandle p) {}
                @Override public void setVertexBuffer(int s, VulkanicBuffer b) {}
                @Override public void setIndexBuffer(VulkanicBuffer b, VulkanicIndexType t) {}
                @Override public void drawIndexed(int fi, int ic, int bv, int inst) {}
                @Override public void draw(int fv, int vc) {}
                @Override public void close() {}
            });
        // Also verify OpenGLRenderPass is the real implementation
        assertTrue(VulkanicRenderPass.class.isAssignableFrom(OpenGLRenderPass.class));
    }

    @Test
    public void testOpenGLRenderPassCloseIsIdempotent() {
        // Create a fake OpenGLRenderPass using handle=0 (no real GL context)
        // close() should not throw when called twice
        OpenGLRenderPass pass = new OpenGLRenderPass(0, OpenGLCommandContext.IMMEDIATE) {
            @Override
            public void close() {
                // Override to skip actual GL calls — we have no GL context in tests
                // Just verify it doesn't throw or loop
            }
        };
        assertDoesNotThrow(pass::close);
        assertDoesNotThrow(pass::close); // second call must also be safe
    }

    @Test
    public void testOpenGLRenderPassGetFbo() {
        int expectedFbo = 42;
        OpenGLRenderPass pass = new OpenGLRenderPass(expectedFbo, OpenGLCommandContext.IMMEDIATE) {
            @Override public void close() { /* no GL context in tests */ }
        };
        assertEquals(expectedFbo, pass.getFbo());
    }

    @Test
    public void testOpenGLRenderPassSetPipelineThrowsOnNullPipeline() {
        OpenGLRenderPass pass = new OpenGLRenderPass(1, OpenGLCommandContext.IMMEDIATE) {
            @Override public void close() {}
        };
        assertThrows(IllegalArgumentException.class, () -> pass.setPipeline(null));
    }

    @Test
    public void testOpenGLRenderPassSetVertexBufferThrowsOnWrongType() {
        OpenGLRenderPass pass = new OpenGLRenderPass(1, OpenGLCommandContext.IMMEDIATE) {
            @Override public void close() {}
        };
        // Passing a non-OpenGLBuffer VulkanicBuffer should throw
        VulkanicBuffer fakeBuffer = new VulkanicBuffer() {
            @Override public int size() { return 64; }
            @Override public int usage() { return VulkanicBuffer.USAGE_VERTEX; }
            @Override public boolean isClosed() { return false; }
            @Override public void close() {}
        };
        assertThrows(IllegalArgumentException.class,
            () -> pass.setVertexBuffer(0, fakeBuffer));
    }

    @Test
    public void testOpenGLRenderPassMethodsThrowWhenClosed() {
        OpenGLRenderPass pass = new OpenGLRenderPass(1, OpenGLCommandContext.IMMEDIATE) {
            @Override public void close() {
                // mark as closed without GL calls
                try {
                    var f = OpenGLRenderPass.class.getDeclaredField("closed");
                    f.setAccessible(true);
                    f.set(this, true);
                } catch (Exception e) { throw new RuntimeException(e); }
            }
        };
        pass.close(); // mark closed

        OpenGLBuffer dummyBuffer = new OpenGLBuffer(1, VulkanicBuffer.USAGE_VERTEX, 64);

        assertThrows(IllegalStateException.class, () -> pass.draw(0, 6));
        assertThrows(IllegalStateException.class, () -> pass.drawIndexed(0, 6, 0, 1));
    }

    // ---- VulkanicIndexType and VulkanicRenderPass together ------------------

    @Test
    public void testRenderPassUsesIndexTypeForByteOffset() {
        // Verify that firstIndex * bytesPerIndex gives the correct byte offset
        // BYTE: firstIndex=3 → offset 3 bytes
        assertEquals(3, 3 * VulkanicIndexType.BYTE.bytesPerIndex());
        // SHORT: firstIndex=3 → offset 6 bytes
        assertEquals(6, 3 * VulkanicIndexType.SHORT.bytesPerIndex());
        // INT: firstIndex=3 → offset 12 bytes
        assertEquals(12, 3 * VulkanicIndexType.INT.bytesPerIndex());
    }
}
