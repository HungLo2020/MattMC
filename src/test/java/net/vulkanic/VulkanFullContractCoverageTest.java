package net.vulkanic;

import net.vulkanic.backends.vulkan.VulkanBackend;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the final Vulkan-side completion of the GraphicsBackend contract.
 *
 * <p>These tests focus on the last compatibility surfaces that were added to
 * eliminate fail-fast proxy gaps: typed handle wrappers, query/sync lifecycles,
 * integer state queries, capability probes, and compatibility no-op methods.</p>
 */
public class VulkanFullContractCoverageTest {

    private VulkanBackend vulkanBackend;
    private CommandContext stubCtx;

    @BeforeEach
    public void setUp() throws Exception {
        vulkanBackend = new VulkanBackend();
        stubCtx = makeStubVulkanContext();
    }

    @AfterEach
    public void tearDown() throws Exception {
        resetVulkanicAPIBackend();
    }

    @Test
    public void testTypedShaderAndProgramHandlesWrapVirtualHandles() {
        VulkanicShaderHandle shaderHandle = vulkanBackend.createShaderHandle(stubCtx, VulkanicAPI.GL_VERTEX_SHADER);
        VulkanicProgramHandle programHandle = vulkanBackend.createShaderProgramHandle(stubCtx);

        assertTrue(shaderHandle.isValid(), "createShaderHandle must wrap a non-zero virtual shader handle");
        assertTrue(programHandle.isValid(), "createShaderProgramHandle must wrap a non-zero virtual program handle");
        assertTrue(vulkanBackend.isProgram(stubCtx, programHandle.value()),
            "Program created through the typed wrapper must still be visible to isProgram()");
    }

    @Test
    public void testIntegerQueriesReflectCachedRenderState() {
        int program = vulkanBackend.createShaderProgram(stubCtx);
        int vao = vulkanBackend.createVertexArray(stubCtx);

        vulkanBackend.bindShaderProgram(stubCtx, program);
        vulkanBackend.bindVertexArray(stubCtx, vao);
        vulkanBackend.blendFunc(stubCtx, VulkanicBlendFactor.SRC_ALPHA, VulkanicBlendFactor.ONE_MINUS_SRC_ALPHA);
        vulkanBackend.setStencilFunc(stubCtx, VulkanicAPI.GL_EQUAL, 3, 0x7F);
        vulkanBackend.setStencilOp(stubCtx, VulkanicAPI.GL_KEEP, VulkanicAPI.GL_REPLACE, VulkanicAPI.GL_INCR);
        vulkanBackend.setStencilWriteMask(stubCtx, 0x3F);

        assertEquals(program, vulkanBackend.getInteger(stubCtx, VulkanicIntegerQuery.CURRENT_PROGRAM));
        assertEquals(vao, vulkanBackend.getInteger(stubCtx, VulkanicIntegerQuery.VERTEX_ARRAY_BINDING));
        assertEquals(VulkanicAPI.GL_SRC_ALPHA,
            vulkanBackend.getInteger(stubCtx, VulkanicIntegerQuery.BLEND_SRC_RGB));
        assertEquals(3, vulkanBackend.getInteger(stubCtx, VulkanicIntegerQuery.STENCIL_REF));
        assertEquals(0x7F, vulkanBackend.getInteger(stubCtx, VulkanicIntegerQuery.STENCIL_VALUE_MASK));
        assertEquals(0x3F, vulkanBackend.getInteger(stubCtx, VulkanicIntegerQuery.STENCIL_WRITEMASK));
    }

    @Test
    public void testVirtualVertexArrayLifecycleAndQueryState() {
        int vao = vulkanBackend.createVertexArray(stubCtx);
        assertTrue(vulkanBackend.isVertexArray(stubCtx, vao));

        vulkanBackend.bindVertexArray(stubCtx, vao);
        assertEquals(vao, vulkanBackend.getInteger(stubCtx, VulkanicIntegerQuery.VERTEX_ARRAY_BINDING));

        vulkanBackend.deleteVertexArrays(stubCtx, vao);
        assertFalse(vulkanBackend.isVertexArray(stubCtx, vao));
        assertEquals(0, vulkanBackend.getInteger(stubCtx, VulkanicIntegerQuery.VERTEX_ARRAY_BINDING));
    }

    @Test
    public void testVirtualQueryLifecycle() {
        int query = vulkanBackend.generateQueryObject(stubCtx);
        assertDoesNotThrow(() -> vulkanBackend.initiateQuery(stubCtx, VulkanicAPI.GL_TIME_ELAPSED, query));
        assertDoesNotThrow(() -> vulkanBackend.concludeQuery(stubCtx, VulkanicAPI.GL_TIME_ELAPSED));
        assertEquals(0, vulkanBackend.retrieveQueryObjectInt(stubCtx, query, VulkanicAPI.GL_QUERY_RESULT));
        assertEquals(0L, vulkanBackend.retrieveQueryObjectInt64(stubCtx, query, VulkanicAPI.GL_QUERY_RESULT));

        vulkanBackend.disposeQueryObject(stubCtx, query);
        assertThrows(IllegalArgumentException.class,
            () -> vulkanBackend.initiateQuery(stubCtx, VulkanicAPI.GL_TIME_ELAPSED, query));
    }

    @Test
    public void testVirtualFenceSyncLifecycle() {
        long sync = vulkanBackend.createFenceSync(stubCtx, VulkanicAPI.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
        IntBuffer length = IntBuffer.allocate(1);

        assertEquals(0x911A, vulkanBackend.waitForSync(stubCtx, sync, 0, Long.MAX_VALUE));
        assertEquals(1, vulkanBackend.getSynci(stubCtx, sync, 0, length));
        assertEquals(1, length.get(0));

        vulkanBackend.destroySync(stubCtx, sync);
        assertEquals(0x911B, vulkanBackend.waitForSync(stubCtx, sync, 0, Long.MAX_VALUE));
    }

    @Test
    public void testGraphicsCapabilitiesReportVulkanBackend() {
        GraphicsCapabilities initialized = vulkanBackend.initializeGraphicsCapabilities();
        GraphicsCapabilities queried = vulkanBackend.getGraphicsCapabilities();

        assertSame(initialized, queried, "Capability initialization should return the cached Vulkan capabilities object");
        assertEquals(GraphicsBackendType.VULKAN, queried.backendType());
        assertFalse(queried.OpenGL33, "Vulkan capability object must not claim OpenGL core versions");
        assertFalse(queried.GL_ARB_buffer_storage, "Vulkan capability object must not claim GL extensions");
        assertSame(queried, vulkanBackend.getGLCapabilities(), "Low-level capability accessor should expose the same object");
    }

    @Test
    public void testProbeMethodsReturnConservativeDefaults() {
        assertFalse(vulkanBackend.supportsKhrDebug());
        assertFalse(vulkanBackend.supportsArbDebugOutput());
        assertFalse(vulkanBackend.hasBufferStorageExtension());
        assertFalse(vulkanBackend.hasVertexAttribBindingExtension());
        assertFalse(vulkanBackend.checkOpenGL32Support());
        assertFalse(vulkanBackend.checkOpenGL33Support());
        assertFalse(vulkanBackend.checkARBInstancedArraysSupport());
        assertFalse(vulkanBackend.checkFunctionAvailable("glDispatchCompute"));
    }

    @Test
    public void testResolveHelpersReturnBackendNeutralDefaults() {
        VulkanicUniformLocation location = vulkanBackend.resolveUniformLocation(stubCtx, 123, "u_MVP");

        assertFalse(location.isValid(), "Vulkan legacy uniform locations should resolve to INVALID");
        assertEquals(0, vulkanBackend.resolveTextureHandle(stubCtx, null));
        assertEquals(0, vulkanBackend.resolveFramebufferForTextures(stubCtx, null, null));
        assertEquals(0xFFFFFFFF, vulkanBackend.getUniformBlockIndex(stubCtx, 1, "Globals"));
        assertEquals("", vulkanBackend.retrieveActiveUniformBlockName(stubCtx, 1, 0));
    }

    @Test
    public void testCompatibilityCompatibilityMethodsRespectStubAndReadinessContracts() {
        assertDoesNotThrow(() -> vulkanBackend.blitNamedFramebuffer(stubCtx, 0, 0, 0, 0, 1, 1, 0, 0, 1, 1, 0, 0));
        assertDoesNotThrow(() -> vulkanBackend.blitNamedFramebufferDSA(stubCtx, 0, 0, 0, 0, 1, 1, 0, 0, 1, 1, 0, 0));

        IllegalStateException copyException = assertThrows(IllegalStateException.class,
            () -> vulkanBackend.copyImageSubData(stubCtx, 1, 0, 0, 0, 0, 0, 2, 0, 0, 0, 0, 0, 1, 1, 1));
        assertTrue(copyException.getMessage().contains("copyImageSubData"),
            "Implemented native-backed copy paths should fail fast with operation context when Vulkan is not ready");

        assertDoesNotThrow(() -> vulkanBackend.dispatchCompute(stubCtx, 1, 1, 1));
        assertDoesNotThrow(() -> vulkanBackend.dispatchComputeIndirect(stubCtx, 0L));
        assertDoesNotThrow(() -> vulkanBackend.memoryBarrier(stubCtx, 0));
        assertDoesNotThrow(() -> vulkanBackend.texBuffer(stubCtx, VulkanicAPI.GL_TEXTURE_BUFFER, 0, 0));
        assertDoesNotThrow(() -> vulkanBackend.uploadTexture1D(stubCtx, VulkanicAPI.GL_TEXTURE_1D, 0, 0, 16, 0, 0, 0, (ByteBuffer) null));
        assertDoesNotThrow(() -> vulkanBackend.uploadTexture3D(stubCtx, VulkanicAPI.GL_TEXTURE_3D, 0, 0, 4, 4, 4, 0, 0, 0, (ByteBuffer) null));
        assertDoesNotThrow(() -> vulkanBackend.readPixels(stubCtx, 0, 0, 1, 1, VulkanicAPI.GL_RGBA, VulkanicAPI.GL_FLOAT, new float[4]));
    }

    @Test
    public void testDirectStateTextureHelpersAreSafeWithoutNativeSpine() {
        assertDoesNotThrow(() -> vulkanBackend.textureParameteri(stubCtx, 99, VulkanicAPI.GL_TEXTURE_MIN_FILTER, VulkanicAPI.GL_NEAREST));
        assertDoesNotThrow(() -> vulkanBackend.textureParameterf(stubCtx, 99, VulkanicAPI.GL_TEXTURE_LOD_BIAS, 0.0f));
        assertDoesNotThrow(() -> vulkanBackend.textureParameteriv(stubCtx, 99, VulkanicAPI.GL_TEXTURE_SWIZZLE_RGBA, new int[] {0, 1, 2, 3}));
        assertEquals(0, vulkanBackend.getTextureParameteri(stubCtx, 99, VulkanicAPI.GL_TEXTURE_MIN_FILTER));
    }

    @Test
    public void testStringAndVectorQueriesReturnStableDefaults() {
        assertEquals("Vulkanic", vulkanBackend.getString(stubCtx, VulkanicAPI.GL_VENDOR));
        assertEquals("VulkanBackend", vulkanBackend.getString(stubCtx, VulkanicAPI.GL_RENDERER));
        assertEquals("", vulkanBackend.getString(stubCtx, VulkanicAPI.GL_EXTENSIONS, 0));

        int[] ints = new int[4];
        float[] floats = new float[4];
        vulkanBackend.getIntegerv(stubCtx, VulkanicAPI.GL_MAX_DRAW_BUFFERS, ints);
        vulkanBackend.getFloatv(stubCtx, VulkanicAPI.GL_MAX_DRAW_BUFFERS, floats);

        assertEquals(8, ints[0]);
        assertEquals(8.0f, floats[0]);
        assertEquals(0, ints[1]);
        assertEquals(0.0f, floats[1]);
    }

    private static CommandContext makeStubVulkanContext() throws Exception {
        Class<?> vccClass = Class.forName("net.vulkanic.backends.vulkan.VulkanCommandContext");
        java.lang.reflect.Constructor<?> ctor = vccClass.getDeclaredConstructor(long.class, String.class);
        ctor.setAccessible(true);
        return (CommandContext) ctor.newInstance(1L, "test-stub-command-context");
    }

    private static void resetVulkanicAPIBackend() throws Exception {
        Field backendField = VulkanicAPI.class.getDeclaredField("backend");
        backendField.setAccessible(true);
        backendField.set(null, null);

        try {
            Field rawVulkanField = VulkanicAPI.class.getDeclaredField("rawVulkanBackend");
            rawVulkanField.setAccessible(true);
            rawVulkanField.set(null, null);
        } catch (NoSuchFieldException ignored) {
            // Field may not exist in some headless test configs.
        }
    }
}
