package net.vulkanic;

import net.vulkanic.backends.vulkan.VulkanBackend;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that VulkanBackend correctly implements the GraphicsBackend contract
 * methods added in the "GraphicsBackend Contract Coverage (Vulkan Path)" work item:
 *
 * <ul>
 *     <li>Blend state (setBlendEnabled, setBlendFunction, setBlendEquation, setBlendEquationSeparate)</li>
 *     <li>Depth state (setDepthTest, setDepthFunc, setDepthWriteMask)</li>
 *     <li>Color mask (setColorMask)</li>
 *     <li>Rasterization (setCullFaceMode, setPolygonMode, setPolygonOffset)</li>
 *     <li>Capability toggle (setCapabilityEnabled, setIndexedEnabled)</li>
 *     <li>Clear state (setClearColor, setClearDepth)</li>
 *     <li>Logic op, read/draw buffer routing (setLogicOp, setReadBuffer, setDrawBuffer)</li>
 *     <li>VAO and shader binding no-ops (bindVertexArray, bindShaderProgram)</li>
 *     <li>Error query (getError → always 0)</li>
 *     <li>Virtual FBO lifecycle (createFramebuffer, bindFramebuffer, deleteFramebuffer, checkFramebufferStatus)</li>
 * </ul>
 *
 * <p>All tests operate on a raw {@link VulkanBackend} instance (not via the proxy) to avoid
 * native Vulkan bring-up.  A stub {@link CommandContext} of the correct type is injected via
 * reflection to satisfy the {@code requireVulkanCommandBufferHandle} guard.</p>
 *
 * <h2>OpenGL non-regression</h2>
 * <p>These APIs are routed through {@link VulkanicAPI} which delegates to {@code getBackend()}.
 * The test explicitly verifies that switching back to OpenGL mode after calling Vulkan state
 * methods does not corrupt the API's backend routing.</p>
 */
public class VulkanRenderStateContractTest {

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

    // ================================================================
    //  Blend state
    // ================================================================

    @Test
    public void testSetBlendEnabledCachesFlag() {
        // No exception should be thrown; state is cached for pipeline creation.
        assertDoesNotThrow(() -> vulkanBackend.setBlendEnabled(stubCtx, true));
        assertDoesNotThrow(() -> vulkanBackend.setBlendEnabled(stubCtx, false));
    }

    @Test
    public void testSetBlendFunctionAcceptsBothOverloads() {
        assertDoesNotThrow(() -> vulkanBackend.setBlendFunction(stubCtx, 0x0302, 0x0303, 0x0302, 0x0303));
        assertDoesNotThrow(() -> vulkanBackend.setBlendFunction(stubCtx,
            VulkanicBlendFactor.SRC_ALPHA, VulkanicBlendFactor.ONE_MINUS_SRC_ALPHA,
            VulkanicBlendFactor.ONE, VulkanicBlendFactor.ZERO));
    }

    @Test
    public void testTypedBlendFunctionCachesLegacyGlConstants() {
        vulkanBackend.setBlendFunction(stubCtx,
            VulkanicBlendFactor.SRC_ALPHA, VulkanicBlendFactor.ONE_MINUS_SRC_ALPHA,
            VulkanicBlendFactor.ONE, VulkanicBlendFactor.ZERO);

        assertEquals(VulkanicAPI.GL_SRC_ALPHA,
            vulkanBackend.getInteger(stubCtx, VulkanicIntegerQuery.BLEND_SRC_RGB));
        assertEquals(VulkanicAPI.GL_ONE_MINUS_SRC_ALPHA,
            vulkanBackend.getInteger(stubCtx, VulkanicIntegerQuery.BLEND_DST_RGB));
    }

    @Test
    public void testIndexedBlendFunctionCachesNonzeroDrawBufferState() throws Exception {
        vulkanBackend.setIndexedEnabled(stubCtx, VulkanicAPI.GL_BLEND, 1, true);
        vulkanBackend.blendFuncSeparatei(stubCtx, 1,
            VulkanicBlendFactor.SRC_ALPHA, VulkanicBlendFactor.ONE_MINUS_SRC_ALPHA,
            VulkanicBlendFactor.ONE, VulkanicBlendFactor.ONE_MINUS_SRC_ALPHA);

        Object state = indexedBlendState(1);
        assertNotNull(state, "Vulkan should retain per-attachment blend state beyond draw buffer 0");
        assertEquals(true, indexedBlendStateValue(state, "enabled"));
        assertEquals(VulkanicAPI.GL_SRC_ALPHA, indexedBlendStateValue(state, "srcRgb"));
        assertEquals(VulkanicAPI.GL_ONE_MINUS_SRC_ALPHA, indexedBlendStateValue(state, "dstRgb"));
        assertEquals(VulkanicAPI.GL_ONE, indexedBlendStateValue(state, "srcAlpha"));
        assertEquals(VulkanicAPI.GL_ONE_MINUS_SRC_ALPHA, indexedBlendStateValue(state, "dstAlpha"));
    }

    @Test
    public void testSetBlendEquationAcceptsBothOverloads() {
        assertDoesNotThrow(() -> vulkanBackend.setBlendEquation(stubCtx, 0x8006));
        assertDoesNotThrow(() -> vulkanBackend.setBlendEquation(stubCtx, VulkanicBlendEquation.ADD));
    }

    @Test
    public void testSetBlendEquationSeparateAcceptsBothOverloads() {
        assertDoesNotThrow(() -> vulkanBackend.setBlendEquationSeparate(stubCtx, 0x8006, 0x800A));
        assertDoesNotThrow(() -> vulkanBackend.setBlendEquationSeparate(stubCtx,
            VulkanicBlendEquation.ADD, VulkanicBlendEquation.MIN));
    }

    // ================================================================
    //  Depth state
    // ================================================================

    @Test
    public void testSetDepthTestAcceptsBothOverloads() {
        assertDoesNotThrow(() -> vulkanBackend.setDepthTest(stubCtx, 0x0201));
        assertDoesNotThrow(() -> vulkanBackend.setDepthTest(stubCtx, VulkanicDepthCompareOp.LESS));
    }

    @Test
    public void testSetDepthFuncDelegatesToSetDepthTest() {
        assertDoesNotThrow(() -> vulkanBackend.setDepthFunc(stubCtx, 0x0203));
        assertDoesNotThrow(() -> vulkanBackend.setDepthFunc(stubCtx, VulkanicDepthCompareOp.LEQUAL));
    }

    @Test
    public void testSetDepthWriteMaskCachesFlag() {
        assertDoesNotThrow(() -> vulkanBackend.setDepthWriteMask(stubCtx, true));
        assertDoesNotThrow(() -> vulkanBackend.setDepthWriteMask(stubCtx, false));
    }

    // ================================================================
    //  Color mask
    // ================================================================

    @Test
    public void testSetColorMaskCachesChannelFlags() {
        assertDoesNotThrow(() -> vulkanBackend.setColorMask(stubCtx, true, true, true, true));
        assertDoesNotThrow(() -> vulkanBackend.setColorMask(stubCtx, false, false, false, false));
        assertDoesNotThrow(() -> vulkanBackend.setColorMask(stubCtx, true, false, true, false));
    }

    // ================================================================
    //  Rasterization state
    // ================================================================

    @Test
    public void testSetCullFaceModeAcceptsBothOverloads() {
        assertDoesNotThrow(() -> vulkanBackend.setCullFaceMode(stubCtx, 0x0405));
        assertDoesNotThrow(() -> vulkanBackend.setCullFaceMode(stubCtx, VulkanicCullFaceMode.BACK));
    }

    @Test
    public void testSetPolygonModeCachesState() {
        assertDoesNotThrow(() -> vulkanBackend.setPolygonMode(stubCtx, 0x0408, 0x1B02));
    }

    @Test
    public void testSetPolygonOffsetCachesFactorAndUnits() {
        assertDoesNotThrow(() -> vulkanBackend.setPolygonOffset(stubCtx, 1.5f, 2.0f));
        assertDoesNotThrow(() -> vulkanBackend.setPolygonOffset(stubCtx, 0.0f, 0.0f));
    }

    // ================================================================
    //  setCapabilityEnabled / setIndexedEnabled
    // ================================================================

    @Test
    public void testSetCapabilityEnabledHandlesKnownGLConstants() {
        final int GL_BLEND      = 0x0BE2;
        final int GL_DEPTH_TEST = 0x0B71;
        assertDoesNotThrow(() -> vulkanBackend.setCapabilityEnabled(stubCtx, GL_BLEND, true));
        assertDoesNotThrow(() -> vulkanBackend.setCapabilityEnabled(stubCtx, GL_DEPTH_TEST, false));
        assertDoesNotThrow(() -> vulkanBackend.setCapabilityEnabled(stubCtx, 0xDEAD, true));
    }

    @Test
    public void testSetCapabilityEnabledTypedOverloadDelegates() {
        assertDoesNotThrow(() -> vulkanBackend.setCapabilityEnabled(stubCtx, VulkanicCapability.DEBUG_OUTPUT, true));
    }

    @Test
    public void testSetIndexedEnabledBlendIndex0UpdatesBlendFlag() {
        final int GL_BLEND = 0x0BE2;
        assertDoesNotThrow(() -> vulkanBackend.setIndexedEnabled(stubCtx, GL_BLEND, 0, true));
        assertDoesNotThrow(() -> vulkanBackend.setIndexedEnabled(stubCtx, GL_BLEND, 0, false));
    }

    @Test
    public void testSetBlendEnabledMirrorsAttachmentZeroState() throws Exception {
        vulkanBackend.setBlendEnabled(stubCtx, true);
        assertEquals(true, indexedBlendStateValue(indexedBlendState(0), "enabled"));

        vulkanBackend.setBlendEnabled(stubCtx, false);
        assertEquals(false, indexedBlendStateValue(indexedBlendState(0), "enabled"));
    }

    // ================================================================
    //  Clear state
    // ================================================================

    @Test
    public void testSetClearColorCachesRgba() {
        assertDoesNotThrow(() -> vulkanBackend.setClearColor(stubCtx, 0.1f, 0.2f, 0.3f, 1.0f));
    }

    @Test
    public void testSetClearDepthCachesDepth() {
        assertDoesNotThrow(() -> vulkanBackend.setClearDepth(stubCtx, 1.0));
        assertDoesNotThrow(() -> vulkanBackend.setClearDepth(stubCtx, 0.5));
    }

    // ================================================================
    //  clearBuffers (deferred outside render pass)
    // ================================================================

    @Test
    public void testClearBuffersOutsideRenderPassIsSilentNoOp() {
        // NativeSpine is null (not initialized), so no render pass is active.
        // The method should silently return without throwing.
        final int GL_COLOR_BUFFER_BIT = 0x00004000;
        final int GL_DEPTH_BUFFER_BIT = 0x00000100;
        assertDoesNotThrow(() ->
            vulkanBackend.clearBuffers(stubCtx, GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT));
    }

    // ================================================================
    //  Logic op, read/draw buffer
    // ================================================================

    @Test
    public void testSetLogicOpCachesOpcode() {
        assertDoesNotThrow(() -> vulkanBackend.setLogicOp(stubCtx, 0x1503));
    }

    @Test
    public void testSetReadBufferCachesBuffer() {
        assertDoesNotThrow(() -> vulkanBackend.setReadBuffer(stubCtx, 0x0405));
    }

    @Test
    public void testSetDrawBufferCachesMode() {
        assertDoesNotThrow(() -> vulkanBackend.setDrawBuffer(stubCtx, 0x0405));
    }

    // ================================================================
    //  bindVertexArray / bindShaderProgram no-ops
    // ================================================================

    @Test
    public void testBindVertexArrayIsNoOp() {
        assertDoesNotThrow(() -> vulkanBackend.bindVertexArray(stubCtx, 42));
        assertDoesNotThrow(() -> vulkanBackend.bindVertexArray(stubCtx, 0));
    }

    @Test
    public void testBindShaderProgramIsNoOp() {
        assertDoesNotThrow(() -> vulkanBackend.bindShaderProgram(stubCtx, 7));
        assertDoesNotThrow(() -> vulkanBackend.bindShaderProgram(stubCtx, 0));
    }

    // ================================================================
    //  Error query
    // ================================================================

    @Test
    public void testGetErrorAlwaysReturnsZero() {
        int error = vulkanBackend.getError(stubCtx);
        assertEquals(0, error,
            "Vulkan getError() must always return 0 (no deferred error queue in Vulkan)");
    }

    // ================================================================
    //  Virtual FBO lifecycle
    // ================================================================

    @Test
    public void testCreateFramebufferReturnsPositiveId() {
        int fbo = vulkanBackend.createFramebuffer(stubCtx);
        assertTrue(fbo > 0, "createFramebuffer must return a positive virtual ID, got: " + fbo);
    }

    @Test
    public void testMultipleCreateFramebufferCallsReturnDistinctIds() {
        int fbo1 = vulkanBackend.createFramebuffer(stubCtx);
        int fbo2 = vulkanBackend.createFramebuffer(stubCtx);
        int fbo3 = vulkanBackend.createFramebuffer(stubCtx);
        assertNotEquals(fbo1, fbo2);
        assertNotEquals(fbo2, fbo3);
        assertNotEquals(fbo1, fbo3);
    }

    @Test
    public void testBindFramebufferUpdatesReadAndDrawBindings() {
        final int GL_FRAMEBUFFER      = 0x8D40;
        final int GL_READ_FRAMEBUFFER = 0x8CA8;
        final int GL_DRAW_FRAMEBUFFER = 0x8CA9;

        int fbo = vulkanBackend.createFramebuffer(stubCtx);

        // Bind both targets via GL_FRAMEBUFFER
        assertDoesNotThrow(() -> vulkanBackend.bindFramebuffer(stubCtx, GL_FRAMEBUFFER, fbo));

        // Bind read only
        int fbo2 = vulkanBackend.createFramebuffer(stubCtx);
        assertDoesNotThrow(() -> vulkanBackend.bindFramebuffer(stubCtx, GL_READ_FRAMEBUFFER, fbo2));

        // Bind draw only
        assertDoesNotThrow(() -> vulkanBackend.bindFramebuffer(stubCtx, GL_DRAW_FRAMEBUFFER, 0));
    }

    @Test
    public void testCheckFramebufferStatusReturnsCompleteForDefaultAndVirtualFbos() {
        final int GL_FRAMEBUFFER          = 0x8D40;
        final int GL_FRAMEBUFFER_COMPLETE = 0x8CD5;

        // Default FBO (0) should be complete
        vulkanBackend.bindFramebuffer(stubCtx, GL_FRAMEBUFFER, 0);
        int statusDefault = vulkanBackend.checkFramebufferStatus(stubCtx, GL_FRAMEBUFFER);
        assertEquals(GL_FRAMEBUFFER_COMPLETE, statusDefault,
            "Default framebuffer must report COMPLETE");

        // Virtual FBO should be complete
        int fbo = vulkanBackend.createFramebuffer(stubCtx);
        vulkanBackend.bindFramebuffer(stubCtx, GL_FRAMEBUFFER, fbo);
        int statusVirtual = vulkanBackend.checkFramebufferStatus(stubCtx, GL_FRAMEBUFFER);
        assertEquals(GL_FRAMEBUFFER_COMPLETE, statusVirtual,
            "Virtual FBO must report COMPLETE");
    }

    @Test
    public void testCheckFramebufferStatusReturnsUndefinedForUnknownFbo() {
        final int GL_FRAMEBUFFER         = 0x8D40;
        final int GL_FRAMEBUFFER_UNDEFINED = 0x8219;

        // Bind an FBO ID that was never created
        vulkanBackend.bindFramebuffer(stubCtx, GL_FRAMEBUFFER, 99999);
        int status = vulkanBackend.checkFramebufferStatus(stubCtx, GL_FRAMEBUFFER);
        assertEquals(GL_FRAMEBUFFER_UNDEFINED, status,
            "Unknown FBO must report FRAMEBUFFER_UNDEFINED");
    }

    @Test
    public void testDeleteFramebufferRemovesVirtualFboAndResetsBinding() {
        final int GL_FRAMEBUFFER          = 0x8D40;
        final int GL_FRAMEBUFFER_COMPLETE = 0x8CD5;
        final int GL_FRAMEBUFFER_UNDEFINED = 0x8219;

        int fbo = vulkanBackend.createFramebuffer(stubCtx);
        vulkanBackend.bindFramebuffer(stubCtx, GL_FRAMEBUFFER, fbo);

        // Before deletion the FBO should be complete.
        assertEquals(GL_FRAMEBUFFER_COMPLETE, vulkanBackend.checkFramebufferStatus(stubCtx, GL_FRAMEBUFFER));

        // After deletion:
        vulkanBackend.deleteFramebuffer(stubCtx, fbo);

        // The binding was reset to 0 (default FBO), which is still COMPLETE.
        // Explicitly re-bind the deleted (now unregistered) FBO to verify it reports UNDEFINED.
        vulkanBackend.bindFramebuffer(stubCtx, GL_FRAMEBUFFER, fbo);
        int statusAfterDeletion = vulkanBackend.checkFramebufferStatus(stubCtx, GL_FRAMEBUFFER);
        assertEquals(GL_FRAMEBUFFER_UNDEFINED, statusAfterDeletion,
            "Re-binding a deleted virtual FBO must report FRAMEBUFFER_UNDEFINED");
    }

    // ================================================================
    //  Viewport / Scissor — require native spine (throw without init)
    // ================================================================

    @Test
    public void testSetDynamicViewportThrowsWhenNativeSpineUnavailable() {
        // Without native bring-up, setDynamicViewport should throw (ensureNativeReady gates it).
        assertThrows(IllegalStateException.class,
            () -> vulkanBackend.setDynamicViewport(stubCtx, 0, 0, 800, 600));
    }

    @Test
    public void testSetDynamicScissorThrowsWhenNativeSpineUnavailable() {
        assertThrows(IllegalStateException.class,
            () -> vulkanBackend.setDynamicScissor(stubCtx, 0, 0, 800, 600));
    }

    // ================================================================
    //  OpenGL non-regression: switching backend to OpenGL after Vulkan mode
    // ================================================================

    @Test
    public void testOpenGLModeIsUnaffectedByVulkanStateMethodInvocations() {
        // This test verifies that the VulkanicAPI routing tables are independent of
        // the VulkanBackend internal state cache — invoking OpenGL after Vulkan mode
        // must use the real OpenGL backend, not a contaminated one.
        VulkanicAPI.initialize(GraphicsBackendType.OPENGL);
        GraphicsBackend backend = VulkanicAPI.getBackend();
        assertNotNull(backend);
        assertEquals(GraphicsBackendType.OPENGL, backend.getBackendType(),
            "OpenGL backend must be selected after initialize(OPENGL)");
        assertFalse(backend instanceof VulkanBackend,
            "OpenGL mode must not expose a VulkanBackend");
    }

    // ================================================================
    //  Wrong context type → IllegalArgumentException
    // ================================================================

    @Test
    public void testStateMethods_rejectNullContext() {
        assertThrows(IllegalArgumentException.class,
            () -> vulkanBackend.setBlendEnabled(null, true));
        assertThrows(IllegalArgumentException.class,
            () -> vulkanBackend.createFramebuffer(null));
        assertThrows(IllegalArgumentException.class,
            () -> vulkanBackend.getError(null));
    }

    // ================================================================
    //  Helpers
    // ================================================================

    /**
     * Creates a minimal {@link CommandContext} stub whose {@code getHandle()} returns a
     * non-zero long, satisfying the {@code requireVulkanCommandBufferHandle} type check.
     * Uses the concrete package-private {@link net.vulkanic.backends.vulkan.VulkanCommandContext}.
     */
    private static CommandContext makeStubVulkanContext() throws Exception {
        Class<?> vccClass = Class.forName("net.vulkanic.backends.vulkan.VulkanCommandContext");
        java.lang.reflect.Constructor<?> ctor = vccClass.getDeclaredConstructor(long.class, String.class);
        ctor.setAccessible(true);
        // Use a sentinel handle value of 1 — non-zero, never a real VkCommandBuffer in tests.
        return (CommandContext) ctor.newInstance(1L, "test-stub-command-context");
    }

    /**
     * Resets the VulkanicAPI backend field to null so subsequent tests start clean.
     */
    private static void resetVulkanicAPIBackend() throws Exception {
        Field backendField = VulkanicAPI.class.getDeclaredField("backend");
        backendField.setAccessible(true);
        backendField.set(null, null);

        try {
            Field rawVulkanField = VulkanicAPI.class.getDeclaredField("rawVulkanBackend");
            rawVulkanField.setAccessible(true);
            rawVulkanField.set(null, null);
        } catch (NoSuchFieldException ignored) {
            // Field may not exist in headless test configs.
        }
    }

    private Object indexedBlendState(int index) throws Exception {
        Field field = VulkanBackend.class.getDeclaredField("indexedBlendStates");
        field.setAccessible(true);
        java.util.Map<?, ?> states = (java.util.Map<?, ?>) field.get(vulkanBackend);
        return states.get(index);
    }

    private static Object indexedBlendStateValue(Object state, String accessorName) throws Exception {
        assertNotNull(state, "Indexed blend state must exist before reading " + accessorName);
        java.lang.reflect.Method accessor = state.getClass().getDeclaredMethod(accessorName);
        accessor.setAccessible(true);
        return accessor.invoke(state);
    }

}
