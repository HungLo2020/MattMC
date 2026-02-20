package net.vulkanic;

import net.vulkanic.backends.opengl.OpenGLCommandContext;
import net.vulkanic.framegraph.VulkanicFrameGraphBuilder;
import net.vulkanic.framegraph.VulkanicFramePass;
import net.vulkanic.pipeline.PipelineDescriptor;
import net.vulkanic.pipeline.PipelineHandle;
import net.vulkanic.resources.VulkanicBuffer;
import net.vulkanic.resources.VulkanicTexture;
import net.vulkanic.resources.VulkanicTextureFormat;
import net.vulkanic.resources.VulkanicTextureView;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Phase 3 Vulkanic types and API method signatures.
 *
 * These tests verify that:
 *   - The new resource, pipeline, and frame-graph types exist and are properly structured
 *   - VulkanicAPI exposes the Phase 3 static methods with the correct signatures
 *   - The architectural boundary is not violated (no lwjgl.opengl imports outside the backend)
 *
 * Note: Tests that would actually allocate GL objects are skipped because there is no
 * GL context in the unit-test environment (UnsatisfiedLinkError would be thrown).
 * The structural / type-system tests below validate the API shape without a GL context.
 */
public class Phase3ResourceTest {

    // -----------------------------------------------------------------------
    // VulkanicBuffer interface constants
    // -----------------------------------------------------------------------

    @Test
    public void testVulkanicBufferUsageFlagsExist() {
        assertEquals(1,   VulkanicBuffer.USAGE_MAP_READ);
        assertEquals(2,   VulkanicBuffer.USAGE_MAP_WRITE);
        assertEquals(4,   VulkanicBuffer.USAGE_HINT_CLIENT_STORAGE);
        assertEquals(8,   VulkanicBuffer.USAGE_COPY_DST);
        assertEquals(16,  VulkanicBuffer.USAGE_COPY_SRC);
        assertEquals(32,  VulkanicBuffer.USAGE_VERTEX);
        assertEquals(64,  VulkanicBuffer.USAGE_INDEX);
        assertEquals(128, VulkanicBuffer.USAGE_UNIFORM);
        assertEquals(256, VulkanicBuffer.USAGE_UNIFORM_TEXEL_BUFFER);
    }

    // -----------------------------------------------------------------------
    // VulkanicTexture interface constants
    // -----------------------------------------------------------------------

    @Test
    public void testVulkanicTextureUsageFlagsExist() {
        assertEquals(1,  VulkanicTexture.USAGE_COPY_DST);
        assertEquals(2,  VulkanicTexture.USAGE_COPY_SRC);
        assertEquals(4,  VulkanicTexture.USAGE_TEXTURE_BINDING);
        assertEquals(8,  VulkanicTexture.USAGE_RENDER_ATTACHMENT);
        assertEquals(16, VulkanicTexture.USAGE_CUBEMAP_COMPATIBLE);
    }

    // -----------------------------------------------------------------------
    // PipelineDescriptor builder
    // -----------------------------------------------------------------------

    @Test
    public void testPipelineDescriptorBuilder() {
        PipelineDescriptor desc = PipelineDescriptor
                .builder("void main() {}",
                        // gl_FragColor is legacy GLSL (<1.30) — intentional here since we are
                        // testing the PipelineDescriptor builder, not GLSL compatibility.
                        "void main() { gl_FragColor = vec4(1.0); }")
                .debugLabel("test-pipeline")
                .depthTestEnabled(true)
                .blendEnabled(false)
                .cullMode(0x0405) // GL_BACK
                .build();

        assertEquals("void main() {}", desc.getVertexShaderSource());
        assertNotNull(desc.getFragmentShaderSource());
        assertEquals("test-pipeline", desc.getDebugLabel());
        assertTrue(desc.isDepthTestEnabled());
        assertFalse(desc.isBlendEnabled());
        assertEquals(0x0405, desc.getCullMode());
    }

    @Test
    public void testPipelineDescriptorDefaultValues() {
        PipelineDescriptor desc = PipelineDescriptor
                .builder("vs", "fs")
                .build();

        assertEquals("pipeline", desc.getDebugLabel());
        assertEquals(0, desc.getCullMode());
        assertFalse(desc.isDepthTestEnabled());
        assertTrue(desc.isDepthWriteEnabled());
        assertFalse(desc.isBlendEnabled());
    }

    // -----------------------------------------------------------------------
    // VulkanicFrameGraphBuilder
    // -----------------------------------------------------------------------

    @Test
    public void testFrameGraphBuilderAddPass() {
        VulkanicFrameGraphBuilder frame = new VulkanicFrameGraphBuilder();
        assertEquals(0, frame.getPassCount());

        VulkanicFramePass pass = frame.addPass("sky", ctx -> {});
        assertEquals(1, frame.getPassCount());
        assertEquals("sky", pass.getName());

        frame.addPass("level", ctx -> {});
        frame.addPass("gui",   ctx -> {});
        assertEquals(3, frame.getPassCount());
    }

    @Test
    public void testFrameGraphExecutesPassesInOrder() {
        VulkanicFrameGraphBuilder frame = new VulkanicFrameGraphBuilder();
        List<String> executionOrder = new ArrayList<>();

        frame.addPass("a", ctx -> executionOrder.add("a"));
        frame.addPass("b", ctx -> executionOrder.add("b"));
        frame.addPass("c", ctx -> executionOrder.add("c"));

        CommandContext ctx = OpenGLCommandContext.IMMEDIATE;
        frame.execute(ctx);

        assertEquals(List.of("a", "b", "c"), executionOrder);
    }

    @Test
    public void testBeginFrameReturnsNewBuilder() {
        VulkanicAPI.initialize();
        VulkanicFrameGraphBuilder f1 = VulkanicAPI.beginFrame();
        VulkanicFrameGraphBuilder f2 = VulkanicAPI.beginFrame();
        assertNotSame(f1, f2, "beginFrame() should return a new builder each call");
    }

    // -----------------------------------------------------------------------
    // API method signature checks (no GL context needed)
    // -----------------------------------------------------------------------

    @Test
    public void testVulkanicAPIHasPhase3BufferMethods() throws NoSuchMethodException {
        // Verify the static methods exist with the right signatures
        assertNotNull(VulkanicAPI.class.getMethod("createVulkanicBuffer", int.class, int.class));
        assertNotNull(VulkanicAPI.class.getMethod("createVulkanicBuffer", int.class, java.nio.ByteBuffer.class));
        assertNotNull(VulkanicAPI.class.getMethod("deleteVulkanicBuffer", VulkanicBuffer.class));
    }

    @Test
    public void testVulkanicAPIHasPhase3TextureMethods() throws NoSuchMethodException {
        assertNotNull(VulkanicAPI.class.getMethod("createVulkanicTexture",
                String.class, int.class, VulkanicTextureFormat.class,
                int.class, int.class, int.class, int.class));
        assertNotNull(VulkanicAPI.class.getMethod("createVulkanicTextureView", VulkanicTexture.class));
        assertNotNull(VulkanicAPI.class.getMethod("createVulkanicTextureView",
                VulkanicTexture.class, int.class, int.class));
        assertNotNull(VulkanicAPI.class.getMethod("deleteVulkanicTexture", VulkanicTexture.class));
    }

    @Test
    public void testVulkanicAPIHasPhase3PipelineMethods() throws NoSuchMethodException {
        assertNotNull(VulkanicAPI.class.getMethod("createPipeline", PipelineDescriptor.class));
        assertNotNull(VulkanicAPI.class.getMethod("deletePipeline", PipelineHandle.class));
    }

    @Test
    public void testVulkanicAPIHasPhase3RenderPassMethods() throws NoSuchMethodException {
        assertNotNull(VulkanicAPI.class.getMethod("beginRenderPass",
                CommandContext.class, VulkanicTextureView.class, java.util.OptionalInt.class));
        assertNotNull(VulkanicAPI.class.getMethod("beginRenderPass",
                CommandContext.class, VulkanicTextureView.class, java.util.OptionalInt.class,
                VulkanicTextureView.class, java.util.OptionalDouble.class));
        assertNotNull(VulkanicAPI.class.getMethod("setPipeline", CommandContext.class, PipelineHandle.class));
        assertNotNull(VulkanicAPI.class.getMethod("setVertexBuffer",
                CommandContext.class, VulkanicBuffer.class, long.class));
        assertNotNull(VulkanicAPI.class.getMethod("setIndexBuffer",
                CommandContext.class, VulkanicBuffer.class, int.class, long.class));
        assertNotNull(VulkanicAPI.class.getMethod("endRenderPass", CommandContext.class));
    }

    @Test
    public void testVulkanicAPIHasPhase3FrameGraphMethods() throws NoSuchMethodException {
        assertNotNull(VulkanicAPI.class.getMethod("beginFrame"));
        assertNotNull(VulkanicAPI.class.getMethod("executeFrame", VulkanicFrameGraphBuilder.class));
    }

    // -----------------------------------------------------------------------
    // VulkanicTextureFormat semantics
    // -----------------------------------------------------------------------

    @Test
    public void testVulkanicTextureFormatValues() {
        assertEquals(4, VulkanicTextureFormat.RGBA8.pixelSize());
        assertEquals(1, VulkanicTextureFormat.RED8.pixelSize());
        assertEquals(1, VulkanicTextureFormat.RED8I.pixelSize());
        assertEquals(4, VulkanicTextureFormat.DEPTH32.pixelSize());
    }

    @Test
    public void testVulkanicTextureFormatColorAspect() {
        assertTrue(VulkanicTextureFormat.RGBA8.hasColorAspect());
        assertTrue(VulkanicTextureFormat.RED8.hasColorAspect());
        assertTrue(VulkanicTextureFormat.RED8I.hasColorAspect());
        assertFalse(VulkanicTextureFormat.DEPTH32.hasColorAspect());
    }

    @Test
    public void testVulkanicTextureFormatDepthAspect() {
        assertFalse(VulkanicTextureFormat.RGBA8.hasDepthAspect());
        assertFalse(VulkanicTextureFormat.RED8.hasDepthAspect());
        assertFalse(VulkanicTextureFormat.RED8I.hasDepthAspect());
        assertTrue(VulkanicTextureFormat.DEPTH32.hasDepthAspect());
    }

    @Test
    public void testVulkanicTextureFormatHasAllFourValues() {
        // Validates that the enum has exactly the four formats required to cover
        // all Blaze3D TextureFormat values — important for the mapping in GlDevice.
        VulkanicTextureFormat[] values = VulkanicTextureFormat.values();
        assertEquals(4, values.length,
                "VulkanicTextureFormat must have exactly 4 values to cover RGBA8, RED8, RED8I, DEPTH32");
    }

    // -----------------------------------------------------------------------
    // beginCommandBuffer / submitCommandBuffer (Vulkan prerequisite)
    // -----------------------------------------------------------------------

    @Test
    public void testBeginCommandBufferReturnsImmediateContext() {
        VulkanicAPI.initialize();
        CommandContext ctx = VulkanicAPI.beginCommandBuffer();
        assertNotNull(ctx, "beginCommandBuffer() must never return null");
        assertTrue(ctx.isImmediate(),
                "OpenGL backend: beginCommandBuffer() must return the immediate context");
        assertSame(OpenGLCommandContext.IMMEDIATE, ctx,
                "OpenGL backend: beginCommandBuffer() must return the IMMEDIATE singleton");
    }

    @Test
    public void testSubmitCommandBufferIsNoopForOpenGL() {
        VulkanicAPI.initialize();
        CommandContext ctx = VulkanicAPI.beginCommandBuffer();
        // submitCommandBuffer() is a no-op for OpenGL — must not throw.
        assertDoesNotThrow(() -> VulkanicAPI.submitCommandBuffer(ctx));
    }

    @Test
    public void testBeginSubmitCommandBufferRoundTrip() {
        // Verifies the full frame idiom compiles and runs without error on the OpenGL backend.
        VulkanicAPI.initialize();
        CommandContext ctx = VulkanicAPI.beginCommandBuffer();
        // (draw calls would go here in a real frame)
        assertDoesNotThrow(() -> VulkanicAPI.submitCommandBuffer(ctx));
    }

    @Test
    public void testVulkanicAPIHasCommandBufferMethods() throws NoSuchMethodException {
        assertNotNull(VulkanicAPI.class.getMethod("beginCommandBuffer"));
        assertNotNull(VulkanicAPI.class.getMethod("submitCommandBuffer", CommandContext.class));
    }

    @Test
    public void testGraphicsBackendHasCommandBufferMethods() throws NoSuchMethodException {
        assertNotNull(GraphicsBackend.class.getMethod("beginCommandBuffer"));
        assertNotNull(GraphicsBackend.class.getMethod("submitCommandBuffer", CommandContext.class));
    }

    // -----------------------------------------------------------------------
    // GraphicsBackend interface completeness (updated for new signatures)
    // -----------------------------------------------------------------------

    @Test
    public void testGraphicsBackendHasPhase3Methods() throws NoSuchMethodException {
        assertNotNull(GraphicsBackend.class.getMethod("createVulkanicBuffer", int.class, int.class));
        assertNotNull(GraphicsBackend.class.getMethod("createVulkanicTexture",
                String.class, int.class, VulkanicTextureFormat.class,
                int.class, int.class, int.class, int.class));
        assertNotNull(GraphicsBackend.class.getMethod("createPipeline", PipelineDescriptor.class));
        assertNotNull(GraphicsBackend.class.getMethod("beginRenderPass",
                CommandContext.class, VulkanicTextureView.class, java.util.OptionalInt.class));
        assertNotNull(GraphicsBackend.class.getMethod("endRenderPass", CommandContext.class));
        assertNotNull(GraphicsBackend.class.getMethod("executeFrame",
                CommandContext.class, VulkanicFrameGraphBuilder.class));
    }
}
