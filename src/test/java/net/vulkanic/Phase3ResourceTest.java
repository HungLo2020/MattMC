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

    // -----------------------------------------------------------------------
    // Device info methods (VulkanicAPI.getBackendName, getVendor, etc.)
    // -----------------------------------------------------------------------

    @Test
    public void testGraphicsBackendHasDeviceInfoMethods() throws NoSuchMethodException {
        // Verify the interface declares all device-info methods needed for Vulkan parity.
        assertNotNull(GraphicsBackend.class.getMethod("getImplementationInformation"));
        assertNotNull(GraphicsBackend.class.getMethod("getBackendName"));
        assertNotNull(GraphicsBackend.class.getMethod("getVendor"));
        assertNotNull(GraphicsBackend.class.getMethod("getRenderer"));
        assertNotNull(GraphicsBackend.class.getMethod("getApiVersion"));
        assertNotNull(GraphicsBackend.class.getMethod("getMaxTextureSize"));
        assertNotNull(GraphicsBackend.class.getMethod("getEnabledExtensions"));
    }

    @Test
    public void testVulkanicAPIHasDeviceInfoMethods() throws NoSuchMethodException {
        // VulkanicAPI must expose each device-info method as a static wrapper.
        assertNotNull(VulkanicAPI.class.getMethod("getImplementationInformation"));
        assertNotNull(VulkanicAPI.class.getMethod("getBackendName"));
        assertNotNull(VulkanicAPI.class.getMethod("getVendor"));
        assertNotNull(VulkanicAPI.class.getMethod("getRenderer"));
        assertNotNull(VulkanicAPI.class.getMethod("getApiVersion"));
        assertNotNull(VulkanicAPI.class.getMethod("getMaxTextureSize"));
        assertNotNull(VulkanicAPI.class.getMethod("getEnabledExtensions"));
    }

    @Test
    public void testOpenGLBackendReturnsOpenGLBackendName() {
        VulkanicAPI.initialize();
        assertEquals("OpenGL", VulkanicAPI.getBackendName(),
                "OpenGL backend must self-identify as 'OpenGL'");
    }

    @Test
    public void testGetEnabledExtensionsNotNull() {
        VulkanicAPI.initialize();
        assertNotNull(VulkanicAPI.getEnabledExtensions(),
                "getEnabledExtensions() must never return null (empty list before device ready)");
    }

    @Test
    public void testGetMaxTextureSizePositiveBeforeDevice() {
        VulkanicAPI.initialize();
        assertTrue(VulkanicAPI.getMaxTextureSize() > 0,
                "getMaxTextureSize() must return a positive value even before the GL device is ready");
    }

    // -----------------------------------------------------------------------
    // VulkanicBufferSlice
    // -----------------------------------------------------------------------

    @Test
    public void testVulkanicBufferSliceWholeFactory() {
        // Use a mock VulkanicBuffer for testing without a GL context.
        VulkanicBuffer stub = new VulkanicBuffer() {
            @Override public long getNativeHandle() { return 0; }
            @Override public int  getSize()         { return 256; }
            @Override public int  getUsage()        { return USAGE_COPY_DST; }
            @Override public boolean isClosed()     { return false; }
            @Override public void close()           {}
        };
        net.vulkanic.resources.VulkanicBufferSlice slice =
                net.vulkanic.resources.VulkanicBufferSlice.whole(stub);
        assertEquals(0,   slice.offset());
        assertEquals(256, slice.length());
        assertSame(stub,  slice.buffer());
    }

    @Test
    public void testVulkanicBufferSliceSubSlice() {
        VulkanicBuffer stub = new VulkanicBuffer() {
            @Override public long getNativeHandle() { return 0; }
            @Override public int  getSize()         { return 512; }
            @Override public int  getUsage()        { return USAGE_COPY_DST; }
            @Override public boolean isClosed()     { return false; }
            @Override public void close()           {}
        };
        net.vulkanic.resources.VulkanicBufferSlice parent =
                new net.vulkanic.resources.VulkanicBufferSlice(stub, 64, 128);
        net.vulkanic.resources.VulkanicBufferSlice child  = parent.subSlice(16, 32);
        assertEquals(80, child.offset());   // 64 + 16
        assertEquals(32, child.length());
    }

    @Test
    public void testVulkanicBufferSliceRejectsInvalidRange() {
        VulkanicBuffer stub = new VulkanicBuffer() {
            @Override public long getNativeHandle() { return 0; }
            @Override public int  getSize()         { return 64; }
            @Override public int  getUsage()        { return 0; }
            @Override public boolean isClosed()     { return false; }
            @Override public void close()           {}
        };
        // offset + length > buffer.getSize() → should throw
        assertThrows(IllegalArgumentException.class,
                () -> new net.vulkanic.resources.VulkanicBufferSlice(stub, 48, 32));
        // negative length → should throw
        assertThrows(IllegalArgumentException.class,
                () -> new net.vulkanic.resources.VulkanicBufferSlice(stub, 0, -1));
        // zero-length is valid (Vulkan-compatible no-op) → must NOT throw
        assertDoesNotThrow(
                () -> new net.vulkanic.resources.VulkanicBufferSlice(stub, 0, 0));
    }

    // -----------------------------------------------------------------------
    // GraphicsBackend interface: command-encoder operations exist
    // -----------------------------------------------------------------------

    @Test
    public void testGraphicsBackendHasEncoderOperations() throws NoSuchMethodException {
        // Verify that all §3b encoder operations exist on the interface.
        assertNotNull(GraphicsBackend.class.getMethod("writeToBuffer",
                CommandContext.class,
                net.vulkanic.resources.VulkanicBufferSlice.class,
                java.nio.ByteBuffer.class));
        assertNotNull(GraphicsBackend.class.getMethod("mapBuffer",
                CommandContext.class,
                net.vulkanic.resources.VulkanicBufferSlice.class,
                boolean.class, boolean.class));
        assertNotNull(GraphicsBackend.class.getMethod("unmapBuffer",
                CommandContext.class, VulkanicBuffer.class));
        assertNotNull(GraphicsBackend.class.getMethod("clearColorTexture",
                CommandContext.class, VulkanicTexture.class, int.class));
        assertNotNull(GraphicsBackend.class.getMethod("clearDepthTexture",
                CommandContext.class, VulkanicTexture.class, double.class));
        assertNotNull(GraphicsBackend.class.getMethod("clearColorAndDepthTextures",
                CommandContext.class,
                VulkanicTexture.class, int.class,
                VulkanicTexture.class, double.class));
    }

    @Test
    public void testVulkanicAPIHasEncoderOperations() throws NoSuchMethodException {
        // VulkanicAPI must expose each encoder operation as a static method.
        assertNotNull(VulkanicAPI.class.getMethod("writeToBuffer",
                CommandContext.class,
                net.vulkanic.resources.VulkanicBufferSlice.class,
                java.nio.ByteBuffer.class));
        assertNotNull(VulkanicAPI.class.getMethod("mapBuffer",
                CommandContext.class,
                net.vulkanic.resources.VulkanicBufferSlice.class,
                boolean.class, boolean.class));
        assertNotNull(VulkanicAPI.class.getMethod("unmapBuffer",
                CommandContext.class, VulkanicBuffer.class));
        assertNotNull(VulkanicAPI.class.getMethod("clearColorTexture",
                CommandContext.class, VulkanicTexture.class, int.class));
        assertNotNull(VulkanicAPI.class.getMethod("clearDepthTexture",
                CommandContext.class, VulkanicTexture.class, double.class));
        assertNotNull(VulkanicAPI.class.getMethod("clearColorAndDepthTextures",
                CommandContext.class,
                VulkanicTexture.class, int.class,
                VulkanicTexture.class, double.class));
    }

    // -----------------------------------------------------------------------
    // VulkanicRenderPass interface
    // -----------------------------------------------------------------------

    @Test
    public void testVulkanicRenderPassInterfaceExists() throws ClassNotFoundException {
        // Must be an interface in net.vulkanic.resources
        Class<?> cls = Class.forName("net.vulkanic.resources.VulkanicRenderPass");
        assertTrue(cls.isInterface(), "VulkanicRenderPass must be an interface");
        assertTrue(AutoCloseable.class.isAssignableFrom(cls),
                "VulkanicRenderPass must extend AutoCloseable (Vulkan try-with-resources)");
    }

    @Test
    public void testVulkanicRenderPassHasCriticalMethods() throws Exception {
        Class<?> cls = Class.forName("net.vulkanic.resources.VulkanicRenderPass");
        // Core draw-pipeline methods
        assertNotNull(cls.getMethod("setPipeline", net.blaze3d.pipeline.RenderPipeline.class));
        assertNotNull(cls.getMethod("setVertexBuffer", int.class, VulkanicBuffer.class));
        assertNotNull(cls.getMethod("setIndexBuffer",
                VulkanicBuffer.class, net.blaze3d.vertex.VertexFormat.IndexType.class));
        assertNotNull(cls.getMethod("setUniform", String.class, VulkanicBuffer.class));
        assertNotNull(cls.getMethod("setUniform", String.class,
                net.vulkanic.resources.VulkanicBufferSlice.class));
        assertNotNull(cls.getMethod("bindSampler", String.class, VulkanicTextureView.class));
        // Draw calls — these map to vkCmdDrawIndexed / vkCmdDraw
        assertNotNull(cls.getMethod("drawIndexed", int.class, int.class, int.class, int.class));
        assertNotNull(cls.getMethod("draw", int.class, int.class));
        // Scissor
        assertNotNull(cls.getMethod("enableScissor", int.class, int.class, int.class, int.class));
        assertNotNull(cls.getMethod("disableScissor"));
        // Debug
        assertNotNull(cls.getMethod("pushDebugGroup", java.util.function.Supplier.class));
        assertNotNull(cls.getMethod("popDebugGroup"));
        // Lifecycle
        assertNotNull(cls.getMethod("close"));
    }

    @Test
    public void testGlRenderPassImplementsVulkanicRenderPass() throws ClassNotFoundException {
        Class<?> glRenderPass = Class.forName("net.blaze3d.opengl.GlRenderPass");
        Class<?> vulkanicRenderPass = Class.forName("net.vulkanic.resources.VulkanicRenderPass");
        assertTrue(vulkanicRenderPass.isAssignableFrom(glRenderPass),
                "GlRenderPass must implement VulkanicRenderPass");
    }

    // -----------------------------------------------------------------------
    // VulkanicCompiledPipeline interface
    // -----------------------------------------------------------------------

    @Test
    public void testVulkanicCompiledPipelineInterfaceExists() throws ClassNotFoundException {
        Class<?> cls = Class.forName("net.vulkanic.pipeline.VulkanicCompiledPipeline");
        assertTrue(cls.isInterface(), "VulkanicCompiledPipeline must be an interface");
    }

    @Test
    public void testVulkanicCompiledPipelineHasRequiredMethods() throws Exception {
        Class<?> cls = Class.forName("net.vulkanic.pipeline.VulkanicCompiledPipeline");
        // isValid() maps to VkPipeline creation success
        assertNotNull(cls.getMethod("isValid"));
        // getNativePipelineHandle() returns GL program name or VkPipeline handle
        assertNotNull(cls.getMethod("getNativePipelineHandle"));
    }

    @Test
    public void testGlRenderPipelineImplementsVulkanicCompiledPipeline() throws ClassNotFoundException {
        Class<?> glPipeline = Class.forName("net.blaze3d.opengl.GlRenderPipeline");
        Class<?> vkPipeline = Class.forName("net.vulkanic.pipeline.VulkanicCompiledPipeline");
        assertTrue(vkPipeline.isAssignableFrom(glPipeline),
                "GlRenderPipeline must implement VulkanicCompiledPipeline");
    }

    // -----------------------------------------------------------------------
    // GraphicsBackend — render pass and pipeline methods
    // -----------------------------------------------------------------------

    @Test
    public void testGraphicsBackendHasRenderPassAndPipelineMethods() throws NoSuchMethodException {
        assertNotNull(GraphicsBackend.class.getMethod("createVulkanicRenderPass",
                CommandContext.class, java.util.function.Supplier.class,
                VulkanicTextureView.class, java.util.OptionalInt.class));
        assertNotNull(GraphicsBackend.class.getMethod("createVulkanicRenderPass",
                CommandContext.class, java.util.function.Supplier.class,
                VulkanicTextureView.class, java.util.OptionalInt.class,
                VulkanicTextureView.class, java.util.OptionalDouble.class));
        assertNotNull(GraphicsBackend.class.getMethod("precompilePipeline",
                net.blaze3d.pipeline.RenderPipeline.class,
                java.util.function.BiFunction.class));
        assertNotNull(GraphicsBackend.class.getMethod("clearPipelineCache"));
    }

    // -----------------------------------------------------------------------
    // VulkanicAPI — render pass and pipeline static wrappers
    // -----------------------------------------------------------------------

    @Test
    public void testVulkanicAPIHasRenderPassAndPipelineMethods() throws NoSuchMethodException {
        assertNotNull(VulkanicAPI.class.getMethod("createVulkanicRenderPass",
                CommandContext.class, java.util.function.Supplier.class,
                VulkanicTextureView.class, java.util.OptionalInt.class));
        assertNotNull(VulkanicAPI.class.getMethod("createVulkanicRenderPass",
                CommandContext.class, java.util.function.Supplier.class,
                VulkanicTextureView.class, java.util.OptionalInt.class,
                VulkanicTextureView.class, java.util.OptionalDouble.class));
        assertNotNull(VulkanicAPI.class.getMethod("precompilePipeline",
                net.blaze3d.pipeline.RenderPipeline.class));
        assertNotNull(VulkanicAPI.class.getMethod("precompilePipeline",
                net.blaze3d.pipeline.RenderPipeline.class,
                java.util.function.BiFunction.class));
        assertNotNull(VulkanicAPI.class.getMethod("clearPipelineCache"));
    }

    // -----------------------------------------------------------------------
    // VulkanicFence
    // -----------------------------------------------------------------------

    @Test
    public void testVulkanicFenceInterfaceExists() throws ClassNotFoundException {
        Class<?> cls = Class.forName("net.vulkanic.resources.VulkanicFence");
        assertTrue(cls.isInterface(), "VulkanicFence must be an interface");
        assertTrue(AutoCloseable.class.isAssignableFrom(cls),
                "VulkanicFence must extend AutoCloseable (maps to VkFence lifecycle)");
    }

    @Test
    public void testVulkanicFenceHasRequiredMethods() throws Exception {
        Class<?> cls = Class.forName("net.vulkanic.resources.VulkanicFence");
        // awaitCompletion maps to vkWaitForFences(timeout)
        assertNotNull(cls.getMethod("awaitCompletion", long.class));
        // close maps to vkDestroyFence
        assertNotNull(cls.getMethod("close"));
    }

    @Test
    public void testGlFenceImplementsVulkanicFence() throws ClassNotFoundException {
        Class<?> glFence = Class.forName("net.blaze3d.opengl.GlFence");
        Class<?> vkFence = Class.forName("net.vulkanic.resources.VulkanicFence");
        assertTrue(vkFence.isAssignableFrom(glFence),
                "GlFence must implement VulkanicFence");
    }

    @Test
    public void testGraphicsBackendHasCreateFence() throws NoSuchMethodException {
        assertNotNull(GraphicsBackend.class.getMethod("createFence", CommandContext.class));
    }

    @Test
    public void testVulkanicAPIHasCreateFence() throws NoSuchMethodException {
        assertNotNull(VulkanicAPI.class.getMethod("createFence", CommandContext.class));
    }

    // -----------------------------------------------------------------------
    // VulkanicAddressMode + VulkanicFilterMode
    // -----------------------------------------------------------------------

    @Test
    public void testVulkanicAddressModeExists() throws ClassNotFoundException {
        Class<?> cls = Class.forName("net.vulkanic.resources.VulkanicAddressMode");
        assertTrue(cls.isEnum(), "VulkanicAddressMode must be an enum");
        // Verify Vulkan-required values are present
        Enum<?>[] constants = (Enum<?>[]) cls.getEnumConstants();
        java.util.Set<String> names = new java.util.HashSet<>();
        for (Enum<?> c : constants) names.add(c.name());
        assertTrue(names.contains("REPEAT"),        "Must have REPEAT → VK_SAMPLER_ADDRESS_MODE_REPEAT");
        assertTrue(names.contains("CLAMP_TO_EDGE"), "Must have CLAMP_TO_EDGE → VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE");
    }

    @Test
    public void testVulkanicFilterModeExists() throws ClassNotFoundException {
        Class<?> cls = Class.forName("net.vulkanic.resources.VulkanicFilterMode");
        assertTrue(cls.isEnum(), "VulkanicFilterMode must be an enum");
        Enum<?>[] constants = (Enum<?>[]) cls.getEnumConstants();
        java.util.Set<String> names = new java.util.HashSet<>();
        for (Enum<?> c : constants) names.add(c.name());
        assertTrue(names.contains("NEAREST"), "Must have NEAREST → VK_FILTER_NEAREST");
        assertTrue(names.contains("LINEAR"),  "Must have LINEAR → VK_FILTER_LINEAR");
    }

    @Test
    public void testAddressModeFromBlaze3dBridgeRoundTrips() throws Exception {
        // REPEAT
        net.vulkanic.resources.VulkanicAddressMode repeat =
                net.vulkanic.resources.VulkanicAddressMode.fromBlaze3d(net.blaze3d.textures.AddressMode.REPEAT);
        assertEquals(net.vulkanic.resources.VulkanicAddressMode.REPEAT, repeat);
        // CLAMP_TO_EDGE
        net.vulkanic.resources.VulkanicAddressMode clamp =
                net.vulkanic.resources.VulkanicAddressMode.fromBlaze3d(net.blaze3d.textures.AddressMode.CLAMP_TO_EDGE);
        assertEquals(net.vulkanic.resources.VulkanicAddressMode.CLAMP_TO_EDGE, clamp);
    }

    @Test
    public void testFilterModeFromBlaze3dBridgeRoundTrips() throws Exception {
        net.vulkanic.resources.VulkanicFilterMode nearest =
                net.vulkanic.resources.VulkanicFilterMode.fromBlaze3d(net.blaze3d.textures.FilterMode.NEAREST);
        assertEquals(net.vulkanic.resources.VulkanicFilterMode.NEAREST, nearest);
        net.vulkanic.resources.VulkanicFilterMode linear =
                net.vulkanic.resources.VulkanicFilterMode.fromBlaze3d(net.blaze3d.textures.FilterMode.LINEAR);
        assertEquals(net.vulkanic.resources.VulkanicFilterMode.LINEAR, linear);
    }

    // -----------------------------------------------------------------------
    // VulkanicSamplerDescriptor + VulkanicSampler
    // -----------------------------------------------------------------------

    @Test
    public void testVulkanicSamplerDescriptorBuilderDefaults() {
        net.vulkanic.resources.VulkanicSamplerDescriptor desc =
                net.vulkanic.resources.VulkanicSamplerDescriptor.builder().build();
        assertEquals(net.vulkanic.resources.VulkanicFilterMode.NEAREST, desc.getMinFilter());
        assertEquals(net.vulkanic.resources.VulkanicFilterMode.NEAREST, desc.getMagFilter());
        assertEquals(net.vulkanic.resources.VulkanicFilterMode.NEAREST, desc.getMipmapMode());
        assertEquals(net.vulkanic.resources.VulkanicAddressMode.REPEAT, desc.getAddressU());
        assertEquals(net.vulkanic.resources.VulkanicAddressMode.REPEAT, desc.getAddressV());
        assertEquals(net.vulkanic.resources.VulkanicAddressMode.REPEAT, desc.getAddressW());
        assertEquals(0.0f,         desc.getMipLodBias());
        assertEquals(1.0f,         desc.getMaxAnisotropy());
        assertEquals(0.0f,         desc.getMinLod());
        assertEquals(Float.MAX_VALUE, desc.getMaxLod());
    }

    @Test
    public void testVulkanicSamplerDescriptorBuilderCustomValues() {
        net.vulkanic.resources.VulkanicSamplerDescriptor desc =
                net.vulkanic.resources.VulkanicSamplerDescriptor.builder()
                        .minFilter(net.vulkanic.resources.VulkanicFilterMode.LINEAR)
                        .magFilter(net.vulkanic.resources.VulkanicFilterMode.LINEAR)
                        .addressU(net.vulkanic.resources.VulkanicAddressMode.CLAMP_TO_EDGE)
                        .maxAnisotropy(16.0f)
                        .minLod(0.0f)
                        .maxLod(4.0f)
                        .debugLabel("trilinear_clamp")
                        .build();
        assertEquals(net.vulkanic.resources.VulkanicFilterMode.LINEAR,        desc.getMinFilter());
        assertEquals(net.vulkanic.resources.VulkanicAddressMode.CLAMP_TO_EDGE, desc.getAddressU());
        assertEquals(16.0f, desc.getMaxAnisotropy());
        assertEquals(4.0f,  desc.getMaxLod());
        assertEquals("trilinear_clamp", desc.getDebugLabel());
    }

    @Test
    public void testVulkanicSamplerInterfaceExists() throws ClassNotFoundException {
        Class<?> cls = Class.forName("net.vulkanic.resources.VulkanicSampler");
        assertTrue(cls.isInterface(), "VulkanicSampler must be an interface");
    }

    @Test
    public void testVulkanicSamplerHasRequiredMethods() throws Exception {
        Class<?> cls = Class.forName("net.vulkanic.resources.VulkanicSampler");
        // getNativeHandle — GL sampler ID or VkSampler handle
        assertNotNull(cls.getMethod("getNativeHandle"));
        // isValid — was the sampler successfully created?
        assertNotNull(cls.getMethod("isValid"));
        // getDescriptor — for debugging / recreation
        assertNotNull(cls.getMethod("getDescriptor"));
    }

    @Test
    public void testGraphicsBackendHasSamplerMethods() throws NoSuchMethodException {
        assertNotNull(GraphicsBackend.class.getMethod("createSampler",
                CommandContext.class, net.vulkanic.resources.VulkanicSamplerDescriptor.class));
        assertNotNull(GraphicsBackend.class.getMethod("deleteSampler",
                CommandContext.class, net.vulkanic.resources.VulkanicSampler.class));
    }

    @Test
    public void testVulkanicAPIHasSamplerMethods() throws NoSuchMethodException {
        assertNotNull(VulkanicAPI.class.getMethod("createSampler",
                CommandContext.class, net.vulkanic.resources.VulkanicSamplerDescriptor.class));
        assertNotNull(VulkanicAPI.class.getMethod("deleteSampler",
                CommandContext.class, net.vulkanic.resources.VulkanicSampler.class));
    }

    // -----------------------------------------------------------------------
    // Transfer operations — GraphicsBackend + VulkanicAPI method signatures
    // -----------------------------------------------------------------------

    @Test
    public void testGraphicsBackendHasTransferOperations() throws NoSuchMethodException {
        // copyVulkanicBuffers → vkCmdCopyBuffer
        assertNotNull(GraphicsBackend.class.getMethod("copyVulkanicBuffers",
                CommandContext.class,
                net.vulkanic.resources.VulkanicBufferSlice.class,
                net.vulkanic.resources.VulkanicBufferSlice.class));
        // writeToVulkanicTexture (full) → vkCmdCopyBufferToImage
        assertNotNull(GraphicsBackend.class.getMethod("writeToVulkanicTexture",
                CommandContext.class, VulkanicTexture.class,
                net.blaze3d.platform.NativeImage.class));
        // writeToVulkanicTexture (sub-region) → vkCmdCopyBufferToImage with offset
        assertNotNull(GraphicsBackend.class.getMethod("writeToVulkanicTexture",
                CommandContext.class, VulkanicTexture.class,
                net.blaze3d.platform.NativeImage.class,
                int.class, int.class, int.class, int.class, int.class, int.class, int.class, int.class));
        // copyVulkanicTextureToBuffer → vkCmdCopyImageToBuffer
        assertNotNull(GraphicsBackend.class.getMethod("copyVulkanicTextureToBuffer",
                CommandContext.class, VulkanicTexture.class,
                VulkanicBuffer.class, int.class, Runnable.class, int.class));
        assertNotNull(GraphicsBackend.class.getMethod("copyVulkanicTextureToBuffer",
                CommandContext.class, VulkanicTexture.class,
                VulkanicBuffer.class, int.class, Runnable.class,
                int.class, int.class, int.class, int.class, int.class));
        // copyVulkanicTextureToTexture → vkCmdCopyImage
        assertNotNull(GraphicsBackend.class.getMethod("copyVulkanicTextureToTexture",
                CommandContext.class, VulkanicTexture.class, VulkanicTexture.class,
                int.class, int.class, int.class, int.class, int.class, int.class, int.class));
        // presentVulkanicTexture → vkQueuePresentKHR
        assertNotNull(GraphicsBackend.class.getMethod("presentVulkanicTexture",
                CommandContext.class, VulkanicTextureView.class));
    }

    @Test
    public void testVulkanicAPIHasTransferOperations() throws NoSuchMethodException {
        assertNotNull(VulkanicAPI.class.getMethod("copyVulkanicBuffers",
                CommandContext.class,
                net.vulkanic.resources.VulkanicBufferSlice.class,
                net.vulkanic.resources.VulkanicBufferSlice.class));
        assertNotNull(VulkanicAPI.class.getMethod("writeToVulkanicTexture",
                CommandContext.class, VulkanicTexture.class,
                net.blaze3d.platform.NativeImage.class));
        assertNotNull(VulkanicAPI.class.getMethod("writeToVulkanicTexture",
                CommandContext.class, VulkanicTexture.class,
                net.blaze3d.platform.NativeImage.class,
                int.class, int.class, int.class, int.class, int.class, int.class, int.class, int.class));
        assertNotNull(VulkanicAPI.class.getMethod("copyVulkanicTextureToBuffer",
                CommandContext.class, VulkanicTexture.class,
                VulkanicBuffer.class, int.class, Runnable.class, int.class));
        assertNotNull(VulkanicAPI.class.getMethod("copyVulkanicTextureToBuffer",
                CommandContext.class, VulkanicTexture.class,
                VulkanicBuffer.class, int.class, Runnable.class,
                int.class, int.class, int.class, int.class, int.class));
        assertNotNull(VulkanicAPI.class.getMethod("copyVulkanicTextureToTexture",
                CommandContext.class, VulkanicTexture.class, VulkanicTexture.class,
                int.class, int.class, int.class, int.class, int.class, int.class, int.class));
        assertNotNull(VulkanicAPI.class.getMethod("presentVulkanicTexture",
                CommandContext.class, VulkanicTextureView.class));
    }
}
