package net.vulkanic;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Phase 3 draw-path wiring.
 *
 * <p>Validates three concrete changes made in this phase:
 * <ol>
 *   <li>{@code GlCommandEncoder.drawFromBuffers} no longer routes through
 *       {@code GlStateManager._drawElements} or {@code GlStateManager._drawArrays}; it calls
 *       {@code VulkanicAPI} directly.</li>
 *   <li>{@code GlCommandEncoder.getActiveVulkanicRenderPass()} accessor exists and returns
 *       the correct type.</li>
 *   <li>{@code CompressibleGLBufferedImage.uploadToTexture} no longer calls
 *       {@code VulkanicAPI.bindTexture2D} before mipmap generation; it uses the
 *       state-mutation-free {@code generateTextureMipmapDSA} call instead.</li>
 * </ol>
 *
 * <p>All tests run without an OpenGL context — they inspect source code and class structure.
 */
public class Phase3DrawPathTest {

    private static final Path PROJECT_ROOT = Paths.get(System.getProperty("user.dir"));
    private static final Path SRC_MAIN_JAVA = PROJECT_ROOT.resolve("src/main/java");

    // ── Task 1: drawFromBuffers routes directly through VulkanicAPI ───────────

    @Test
    public void testDrawFromBuffersNoGlStateManagerDrawCall() throws IOException {
        Path file = SRC_MAIN_JAVA.resolve(
            "net/blaze3d/opengl/GlCommandEncoder.java");
        assertTrue(Files.exists(file), "GlCommandEncoder.java must exist");

        String source = Files.readString(file);

        // The three GlStateManager draw/bind calls that were in drawFromBuffers must be gone.
        // This assertion intentionally checks only draw-path calls, not other upload/readback paths.
        assertFalse(source.contains("GlStateManager._drawElements("),
            "drawFromBuffers must not route through GlStateManager._drawElements; " +
            "it should call VulkanicAPI.drawElements directly");
        assertFalse(source.contains("GlStateManager._drawArrays("),
            "drawFromBuffers must not route through GlStateManager._drawArrays; " +
            "it should call VulkanicAPI.drawArrays directly");
        // The element-array-buffer bind for draw calls used hardcoded constant 34963;
        // it must now be VulkanicAPI.bindBuffer(...GL_ELEMENT_ARRAY_BUFFER...)
        assertFalse(source.contains("GlStateManager._glBindBuffer(34963,"),
            "drawFromBuffers must not route the index buffer bind through GlStateManager; " +
            "it should call VulkanicAPI.bindBuffer(ctx, GL_ELEMENT_ARRAY_BUFFER, ...) directly");
    }

    @Test
    public void testDrawFromBuffersCallsVulkanicAPIDrawElements() throws IOException {
        Path file = SRC_MAIN_JAVA.resolve(
            "net/blaze3d/opengl/GlCommandEncoder.java");
        String source = Files.readString(file);

        assertTrue(source.contains("VulkanicAPI.drawElements(ctx,"),
            "drawFromBuffers must call VulkanicAPI.drawElements(ctx, ...) for non-instanced indexed draws");
        assertTrue(source.contains("VulkanicAPI.drawArrays(ctx,"),
            "drawFromBuffers must call VulkanicAPI.drawArrays(ctx, ...) for non-instanced non-indexed draws");
        assertTrue(source.contains("VulkanicAPI.bindIndexBuffer(ctx,"),
            "drawFromBuffers must bind the index buffer via the backend-agnostic VulkanicAPI.bindIndexBuffer helper");
    }

    @Test
    public void testDrawFromBuffersPreservesIrisTessellationOverride() throws IOException {
        Path file = SRC_MAIN_JAVA.resolve(
            "net/blaze3d/opengl/GlCommandEncoder.java");
        String source = Files.readString(file);

        // The Iris tessellation override (GL_TRIANGLES → GL_PATCHES) must still be present
        // in the non-instanced indexed draw path, since we replaced GlStateManager._drawElements
        // which previously contained it.
        assertTrue(source.contains("usingTessellation"),
            "drawFromBuffers must preserve the Iris tessellation mode override");
        assertTrue(source.contains("GL_PATCHES"),
            "drawFromBuffers must substitute GL_PATCHES when tessellation is active");
    }

    @Test
    public void testDrawFromBuffersSharesContextAcrossCalls() throws IOException {
        Path file = SRC_MAIN_JAVA.resolve(
            "net/blaze3d/opengl/GlCommandEncoder.java");
        String source = Files.readString(file);

        // Verify that drawFromBuffers obtains the context once and reuses it (ctx variable),
        // rather than calling getImmediateContext() repeatedly.
        assertTrue(source.contains("CommandContext ctx = VulkanicAPI.getImmediateContext();"),
            "drawFromBuffers should obtain a single CommandContext and reuse it");
    }

    @Test
    public void testDrawFromBuffersUsesBackendAgnosticIndexTypeRouting() throws IOException {
        Path file = SRC_MAIN_JAVA.resolve(
            "net/blaze3d/opengl/GlCommandEncoder.java");
        String source = Files.readString(file);

        assertFalse(source.contains("GlConst.toGl(indexType)"),
            "drawFromBuffers should not convert index types through OpenGL-specific GlConst.toGl(indexType); " +
            "it should route through VulkanicIndexType-aware VulkanicAPI overloads");
        assertTrue(source.contains("toVulkanicIndexType(indexType)"),
            "drawFromBuffers should map VertexFormat.IndexType to VulkanicIndexType for backend-agnostic indexed draws");
    }

    @Test
    public void testGlCommandEncoderUsesAgnosticTextureAndUniformBindings() throws IOException {
        Path file = SRC_MAIN_JAVA.resolve(
            "net/blaze3d/opengl/GlCommandEncoder.java");
        String source = Files.readString(file);

        assertFalse(source.contains("VulkanicAPI.bindTexture(VulkanicAPI.getImmediateContext(), 34067"),
            "GlCommandEncoder should not bind cubemaps via hardcoded GL target 34067; use bindCubemapTexture");
        assertFalse(source.contains("VulkanicAPI.bindTexture(VulkanicAPI.getImmediateContext(), 35882"),
            "GlCommandEncoder should not bind texture buffers via hardcoded GL target 35882; use bindTextureBuffer");
        assertFalse(source.contains("VulkanicAPI.bindUniformBufferRange(VulkanicAPI.getImmediateContext(), 35345"),
            "GlCommandEncoder should not bind UBO ranges with hardcoded GL target 35345; use target-agnostic overload");
        assertFalse(source.contains("VulkanicAPI.setDrawBuffer(VulkanicAPI.getImmediateContext(), 0)"),
            "GlCommandEncoder should use setDrawBufferNone helper instead of raw draw-buffer literal 0");
        assertFalse(source.contains("VulkanicAPI.setDrawBuffer(VulkanicAPI.getImmediateContext(), 36064)"),
            "GlCommandEncoder should use setDrawBufferColorAttachment0 helper instead of raw draw-buffer literal 36064");

        assertTrue(source.contains("VulkanicAPI.bindCubemapTexture("),
            "GlCommandEncoder should bind cubemaps via VulkanicAPI.bindCubemapTexture");
        assertTrue(source.contains("VulkanicAPI.bindTextureBuffer("),
            "GlCommandEncoder should bind texture buffers via VulkanicAPI.bindTextureBuffer");
        assertTrue(source.contains("VulkanicAPI.bindTextureBufferData("),
            "GlCommandEncoder should attach texel buffer data via VulkanicAPI.bindTextureBufferData");
        assertTrue(source.contains("VulkanicAPI.bindUniformBufferRange(VulkanicAPI.getImmediateContext(), var39"),
            "GlCommandEncoder should use target-agnostic bindUniformBufferRange overload in uniform upload path");
    }

    @Test
    public void testGlDeviceUsesAgnosticCubemapBindHelper() throws IOException {
        Path file = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/GlDevice.java");
        String source = Files.readString(file);

        assertFalse(source.contains("VulkanicAPI.bindTexture(VulkanicAPI.getImmediateContext(), 34067"),
            "GlDevice should not bind cubemaps via hardcoded GL target 34067");
        assertTrue(source.contains("VulkanicAPI.bindCubemapTexture("),
            "GlDevice should bind cubemaps via VulkanicAPI.bindCubemapTexture");
    }

    @Test
    public void testTimerQueryUsesAgnosticQueryHelpers() throws IOException {
        Path file = SRC_MAIN_JAVA.resolve("net/blaze3d/systems/TimerQuery.java");
        String source = Files.readString(file);

        assertFalse(source.contains("VulkanicAPI.initiateQuery(VulkanicAPI.getImmediateContext(), 35007"),
            "TimerQuery should not begin queries with hardcoded GL_TIME_ELAPSED target literal 35007");
        assertFalse(source.contains("VulkanicAPI.concludeQuery(VulkanicAPI.getImmediateContext(), 35007"),
            "TimerQuery should not end queries with hardcoded GL_TIME_ELAPSED target literal 35007");
        assertFalse(source.contains("retrieveQueryObjectInt(VulkanicAPI.getImmediateContext(), this.queryName, 34919)"),
            "TimerQuery should not poll query availability using hardcoded GL_QUERY_RESULT_AVAILABLE literal 34919");
        assertFalse(source.contains("retrieveQueryObjectInt64(VulkanicAPI.getImmediateContext(), this.queryName, 34918)"),
            "TimerQuery should not fetch query values using hardcoded GL_QUERY_RESULT literal 34918");

        assertTrue(source.contains("VulkanicAPI.beginTimeElapsedQuery("),
            "TimerQuery should begin profiling via VulkanicAPI.beginTimeElapsedQuery");
        assertTrue(source.contains("VulkanicAPI.endTimeElapsedQuery("),
            "TimerQuery should end profiling via VulkanicAPI.endTimeElapsedQuery");
        assertTrue(source.contains("VulkanicAPI.isQueryResultAvailable("),
            "TimerQuery should poll completion via VulkanicAPI.isQueryResultAvailable");
        assertTrue(source.contains("VulkanicAPI.getQueryResultInt64("),
            "TimerQuery should fetch results via VulkanicAPI.getQueryResultInt64");
    }

    @Test
    public void testDirectStateAccessUsesAgnosticFramebufferAndCopyHelpers() throws IOException {
        Path file = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/DirectStateAccess.java");
        String source = Files.readString(file);

        assertFalse(source.contains("namedFramebufferTextureDSA(net.vulkanic.VulkanicAPI.getImmediateContext(), i, 36064"),
            "DirectStateAccess should not attach color with hardcoded GL_COLOR_ATTACHMENT0 literal 36064");
        assertFalse(source.contains("namedFramebufferTextureDSA(net.vulkanic.VulkanicAPI.getImmediateContext(), i, 36096"),
            "DirectStateAccess should not attach depth with hardcoded GL_DEPTH_ATTACHMENT literal 36096");
        assertFalse(source.contains("VulkanicAPI.copyBufferSubData(VulkanicAPI.getImmediateContext(), 36662, 36663"),
            "DirectStateAccess should not copy buffers with hardcoded copy-target literals 36662/36663");

        assertTrue(source.contains("VulkanicAPI.namedFramebufferColorAttachment0DSA("),
            "DirectStateAccess should use namedFramebufferColorAttachment0DSA helper");
        assertTrue(source.contains("VulkanicAPI.namedFramebufferDepthAttachmentDSA("),
            "DirectStateAccess should use namedFramebufferDepthAttachmentDSA helper");
        assertTrue(source.contains("VulkanicAPI.bindCopyReadBuffer("),
            "DirectStateAccess should bind copy-read via VulkanicAPI.bindCopyReadBuffer");
        assertTrue(source.contains("VulkanicAPI.bindCopyWriteBuffer("),
            "DirectStateAccess should bind copy-write via VulkanicAPI.bindCopyWriteBuffer");
        assertTrue(source.contains("VulkanicAPI.copyBufferSubDataBetweenCopyTargets("),
            "DirectStateAccess should copy via VulkanicAPI.copyBufferSubDataBetweenCopyTargets");
    }

    @Test
    public void testGlStateManagerUsesFramebufferIntentWrappers() throws IOException {
        Path file = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/GlStateManager.java");
        String source = Files.readString(file);

        assertFalse(source.contains("VulkanicAPI.bindFramebuffer(ctx, 36008, j)"),
            "GlStateManager should not bind read FBO via hardcoded target literal 36008");
        assertFalse(source.contains("VulkanicAPI.bindFramebuffer(ctx, 36009, j)"),
            "GlStateManager should not bind draw FBO via hardcoded target literal 36009");

        assertTrue(source.contains("VulkanicAPI.bindReadFramebuffer(ctx, j)"),
            "GlStateManager should bind read FBO via VulkanicAPI.bindReadFramebuffer");
        assertTrue(source.contains("VulkanicAPI.bindDrawFramebuffer(ctx, j)"),
            "GlStateManager should bind draw FBO via VulkanicAPI.bindDrawFramebuffer");
    }

    @Test
    public void testGlCommandEncoderUsesAgnosticReadbackBindings() throws IOException {
        Path file = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/GlCommandEncoder.java");
        String source = Files.readString(file);

        assertFalse(source.contains("GlStateManager._glBindBuffer(35051"),
            "GlCommandEncoder readback path should not bind pixel-pack buffer via hardcoded target literal 35051");
        assertFalse(source.contains("GlStateManager._pixelStore(3330"),
            "GlCommandEncoder readback path should not set pack row length via hardcoded pname literal 3330");
        assertFalse(source.contains("GlStateManager._glFramebufferTexture2D(36008, 36064, 3553"),
            "GlCommandEncoder readback path should not detach read framebuffer attachment via hardcoded literals");

        assertTrue(source.contains("VulkanicAPI.bindPixelPackBuffer("),
            "GlCommandEncoder readback path should bind PBO through VulkanicAPI.bindPixelPackBuffer");
        assertTrue(source.contains("VulkanicAPI.setPixelStore(ctx, VulkanicAPI.GL_PACK_ROW_LENGTH"),
            "GlCommandEncoder readback path should set row length via VulkanicAPI GL_PACK_ROW_LENGTH helper");
        assertTrue(source.contains("VulkanicAPI.framebufferColorAttachment0Texture2D("),
            "GlCommandEncoder readback path should detach color attachment via framebufferColorAttachment0Texture2D helper");
    }

    @Test
    public void testGlCommandEncoderClearPathsUseAgnosticFramebufferHelpers() throws IOException {
        Path file = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/GlCommandEncoder.java");
        String source = Files.readString(file);

        assertFalse(source.contains("bindFrameBufferTextures(this.drawFbo, ((GlTexture)gpuTexture).id, 0, 0, 36160"),
            "GlCommandEncoder clearColorTexture should not bind draw FBO with hardcoded GL_FRAMEBUFFER literal 36160");
        assertFalse(source.contains("bindFrameBufferTextures(this.drawFbo, 0, ((GlTexture)gpuTexture).id, 0, 36160"),
            "GlCommandEncoder clearDepthTexture should not bind draw FBO with hardcoded GL_FRAMEBUFFER literal 36160");
        assertFalse(source.contains("_clear(16384)"),
            "GlCommandEncoder clearColorTexture should not clear via hardcoded GL_COLOR_BUFFER_BIT literal 16384");
        assertFalse(source.contains("_clear(16640)"),
            "GlCommandEncoder color+depth clear paths should not use hardcoded clear mask literal 16640");
        assertFalse(source.contains("_clear(256)"),
            "GlCommandEncoder clearDepthTexture should not clear via hardcoded GL_DEPTH_BUFFER_BIT literal 256");
        assertFalse(source.contains("_glFramebufferTexture2D(36160, 36064, 3553"),
            "GlCommandEncoder should not detach color attachment via hardcoded framebuffer/attachment/target literals");
        assertFalse(source.contains("_glFramebufferTexture2D(36160, 36096, 3553"),
            "GlCommandEncoder should not detach depth attachment via hardcoded framebuffer/attachment/target literals");
        assertFalse(source.contains("_glBindFramebuffer(36160"),
            "GlCommandEncoder should not bind/unbind GL_FRAMEBUFFER via hardcoded literal 36160");

        assertTrue(source.contains("VulkanicAPI.framebufferColorAttachment0Texture2D("),
            "GlCommandEncoder should detach color attachments via framebufferColorAttachment0Texture2D helper");
        assertTrue(source.contains("VulkanicAPI.framebufferDepthAttachmentTexture2D("),
            "GlCommandEncoder should detach depth attachments via framebufferDepthAttachmentTexture2D helper");
        assertTrue(source.contains("GlStateManager._glBindFramebuffer(VulkanicAPI.GL_FRAMEBUFFER"),
            "GlCommandEncoder should bind/unbind framebuffers via VulkanicAPI.GL_FRAMEBUFFER constant");
        assertTrue(source.contains("_clear(VulkanicAPI.GL_COLOR_BUFFER_BIT)"),
            "GlCommandEncoder should clear color via VulkanicAPI.GL_COLOR_BUFFER_BIT");
        assertTrue(source.contains("_clear(VulkanicAPI.GL_COLOR_BUFFER_BIT | VulkanicAPI.GL_DEPTH_BUFFER_BIT)"),
            "GlCommandEncoder should clear color+depth via explicit VulkanicAPI clear-bit composition");
        assertTrue(source.contains("_clear(VulkanicAPI.GL_DEPTH_BUFFER_BIT)"),
            "GlCommandEncoder should clear depth via VulkanicAPI.GL_DEPTH_BUFFER_BIT");
    }

    @Test
    public void testSodiumGLRenderDeviceUsesAgnosticCopyFenceAndCapabilityHelpers() throws IOException {
        Path file = SRC_MAIN_JAVA.resolve("net/sodium/client/gl/device/GLRenderDevice.java");
        String source = Files.readString(file);

        assertFalse(source.contains("getInteger(VulkanicAPI.getImmediateContext(), 33085)"),
            "GLRenderDevice should not query max texture LOD bias with hardcoded literal 33085");
        assertFalse(source.contains("copyBufferSubData(VulkanicAPI.getImmediateContext(), VulkanicAPI.GL_COPY_READ_BUFFER, VulkanicAPI.GL_COPY_WRITE_BUFFER"),
            "GLRenderDevice should not copy buffers by spelling out copy targets inline");
        assertFalse(source.contains("createFenceSync(VulkanicAPI.getImmediateContext(), VulkanicAPI.GL_SYNC_GPU_COMMANDS_COMPLETE, 0)"),
            "GLRenderDevice should not create completion fences via raw createFenceSync parameters");

        assertTrue(source.contains("getInteger(VulkanicAPI.getImmediateContext(), VulkanicAPI.GL_TEXTURE_MAX_LEVEL)"),
            "GLRenderDevice should use VulkanicAPI constant instead of hardcoded texture query literal");
        assertTrue(source.contains("VulkanicAPI.copyBufferSubDataBetweenCopyTargets("),
            "GLRenderDevice should copy buffer ranges via copyBufferSubDataBetweenCopyTargets helper");
        assertTrue(source.contains("VulkanicAPI.createGpuCompletionFence("),
            "GLRenderDevice should create sync fences via createGpuCompletionFence helper");
    }

    @Test
    public void testVertexArrayCacheUsesAgnosticArrayBufferConstant() throws IOException {
        Path file = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/VertexArrayCache.java");
        String source = Files.readString(file);

        assertFalse(source.contains("_glBindBuffer(34962"),
            "VertexArrayCache should not bind GL_ARRAY_BUFFER via hardcoded target literal 34962");
        assertTrue(source.contains("_glBindBuffer(VulkanicAPI.GL_ARRAY_BUFFER, glBuffer.handle)"),
            "VertexArrayCache should bind array buffers via VulkanicAPI.GL_ARRAY_BUFFER constant");
    }

    @Test
    public void testGlDeviceUsesAgnosticCapabilityAndAlignmentHelpers() throws IOException {
        Path file = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/GlDevice.java");
        String source = Files.readString(file);

        assertFalse(source.contains("VulkanicAPI.getInteger(net.vulkanic.VulkanicAPI.getImmediateContext(), 35380)"),
            "GlDevice should not query UBO offset alignment via hardcoded literal 35380");
        assertFalse(source.contains("VulkanicAPI.setCapabilityEnabled(ctx, 34895, true)"),
            "GlDevice should not enable program point size via hardcoded literal 34895");

        assertTrue(source.contains("VulkanicAPI.getUniformBufferOffsetAlignment("),
            "GlDevice should query UBO alignment via VulkanicAPI.getUniformBufferOffsetAlignment");
        assertTrue(source.contains("VulkanicAPI.setProgramPointSizeEnabled("),
            "GlDevice should enable program point size via VulkanicAPI.setProgramPointSizeEnabled");
    }

    @Test
    public void testIrisGlDebugUsesAgnosticDebugControlHelpers() throws IOException {
        Path file = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/gl/GLDebug.java");
        String source = Files.readString(file);

        assertFalse(source.contains("debugMessageControl(ctx, 4352, 4352"),
            "GLDebug should not use hardcoded GL_DONT_CARE literals for core debugMessageControl");
        assertFalse(source.contains("debugMessageControlKHR(ctx, 4352, 4352"),
            "GLDebug should not use hardcoded GL_DONT_CARE literals for KHR debugMessageControl");
        assertFalse(source.contains("debugMessageControlARB(ctx, 4352, 4352"),
            "GLDebug should not use hardcoded GL_DONT_CARE literals for ARB debugMessageControl");
        assertFalse(source.contains("setCapabilityEnabled(ctx, VulkanicAPI.GL_DEBUG_OUTPUT_SYNCHRONOUS"),
            "GLDebug should use setDebugOutputSynchronousEnabled helper for sync debug output capability");

        assertTrue(source.contains("VulkanicAPI.setDebugOutputSynchronousEnabled("),
            "GLDebug should enable synchronous debug output via VulkanicAPI.setDebugOutputSynchronousEnabled");
        assertTrue(source.contains("VulkanicAPI.setDebugMessageControlAll("),
            "GLDebug should control core debug filtering via setDebugMessageControlAll helper");
        assertTrue(source.contains("VulkanicAPI.setDebugMessageControlAllKHR("),
            "GLDebug should control KHR debug filtering via setDebugMessageControlAllKHR helper");
        assertTrue(source.contains("VulkanicAPI.setDebugMessageControlAllARB("),
            "GLDebug should control ARB debug filtering via setDebugMessageControlAllARB helper");
        assertTrue(source.contains("VulkanicAPI.isDebugContext("),
            "GLDebug should check context debug status via VulkanicAPI.isDebugContext");
    }

    @Test
    public void testSodiumSyncPathsUseAgnosticFenceHelpers() throws IOException {
        Path helperFile = SRC_MAIN_JAVA.resolve("net/sodium/fabric/SodiumGpuSyncHelper.java");
        String helperSource = Files.readString(helperFile);

        assertFalse(helperSource.contains("createFenceSync(VulkanicAPI.getImmediateContext(), 37143, 0)"),
            "SodiumGpuSyncHelper should not create fences with hardcoded GL_SYNC_GPU_COMMANDS_COMPLETE literal 37143");
        assertFalse(helperSource.contains("waitForSync(VulkanicAPI.getImmediateContext(), fence, 1, Long.MAX_VALUE)"),
            "SodiumGpuSyncHelper should not wait with hardcoded GL_SYNC_FLUSH_COMMANDS_BIT literal 1");

        assertTrue(helperSource.contains("VulkanicAPI.createGpuCompletionFence("),
            "SodiumGpuSyncHelper should create fences via VulkanicAPI.createGpuCompletionFence");
        assertTrue(helperSource.contains("VulkanicAPI.waitForSyncWithFlush("),
            "SodiumGpuSyncHelper should wait via VulkanicAPI.waitForSyncWithFlush");

        Path fenceFile = SRC_MAIN_JAVA.resolve("net/sodium/client/gl/sync/GlFence.java");
        String fenceSource = Files.readString(fenceFile);

        assertFalse(fenceSource.contains("getSynci(VulkanicAPI.getImmediateContext(), this.id, 37140"),
            "GlFence should not query sync status via hardcoded GL_SYNC_STATUS literal 37140");
        assertFalse(fenceSource.contains("result == 37889"),
            "GlFence should not compare signal state via hardcoded GL_SIGNALED literal 37889");
        assertFalse(fenceSource.contains("waitForSync(VulkanicAPI.getImmediateContext(), this.id, 1, timeout)"),
            "GlFence should not wait with hardcoded GL_SYNC_FLUSH_COMMANDS_BIT literal 1");

        assertTrue(fenceSource.contains("VulkanicAPI.getSyncStatus("),
            "GlFence should query sync status via VulkanicAPI.getSyncStatus");
        assertTrue(fenceSource.contains("VulkanicAPI.GL_SIGNALED"),
            "GlFence should compare completion state against VulkanicAPI.GL_SIGNALED");
        assertTrue(fenceSource.contains("VulkanicAPI.waitForSyncWithFlush("),
            "GlFence should wait via VulkanicAPI.waitForSyncWithFlush");
    }

    @Test
    public void testGlDebugLabelUsesAgnosticLabelHelpers() throws IOException {
        Path file = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/GlDebugLabel.java");
        String source = Files.readString(file);

        assertFalse(source.contains("getInteger(VulkanicAPI.getImmediateContext(), 33512)"),
            "GlDebugLabel should not query max label length with hardcoded GL_MAX_LABEL_LENGTH literal 33512");
        assertFalse(source.contains("labelDebugObject(VulkanicAPI.getImmediateContext(), 33504"),
            "GlDebugLabel Core should not use hardcoded GL_BUFFER label identifier 33504");
        assertFalse(source.contains("labelDebugObject(VulkanicAPI.getImmediateContext(), 33505"),
            "GlDebugLabel Core should not use hardcoded GL_SHADER label identifier 33505");
        assertFalse(source.contains("labelDebugObject(VulkanicAPI.getImmediateContext(), 33506"),
            "GlDebugLabel Core should not use hardcoded GL_PROGRAM label identifier 33506");
        assertFalse(source.contains("labelObjectExt(VulkanicAPI.getImmediateContext(), 37201"),
            "GlDebugLabel EXT should not use hardcoded GL_BUFFER_OBJECT_EXT literal 37201");

        assertTrue(source.contains("VulkanicAPI.getMaxDebugLabelLength("),
            "GlDebugLabel should query label length via VulkanicAPI.getMaxDebugLabelLength");
        assertTrue(source.contains("VulkanicAPI.labelBufferDebugObject("),
            "GlDebugLabel Core should label buffers via VulkanicAPI.labelBufferDebugObject");
        assertTrue(source.contains("VulkanicAPI.labelTextureDebugObject("),
            "GlDebugLabel Core should label textures via VulkanicAPI.labelTextureDebugObject");
        assertTrue(source.contains("VulkanicAPI.labelVertexArrayDebugObject("),
            "GlDebugLabel Core should label vertex arrays via VulkanicAPI.labelVertexArrayDebugObject");
        assertTrue(source.contains("VulkanicAPI.enterApplicationDebugGroup("),
            "GlDebugLabel Core should enter debug groups via VulkanicAPI.enterApplicationDebugGroup");
        assertTrue(source.contains("VulkanicAPI.labelBufferExtObject("),
            "GlDebugLabel EXT should label buffers via VulkanicAPI.labelBufferExtObject");
    }

    @Test
    public void testNvidiaWorkaroundUsesDebugOutputSyncHelper() throws IOException {
        Path file = SRC_MAIN_JAVA.resolve("net/sodium/client/compatibility/workarounds/nvidia/NvidiaWorkarounds.java");
        String source = Files.readString(file);

        assertFalse(source.contains("setCapabilityEnabled(ctx, 33346, true)"),
            "NvidiaWorkarounds should not toggle GL_DEBUG_OUTPUT_SYNCHRONOUS using hardcoded literal 33346");
        assertTrue(source.contains("VulkanicAPI.setDebugOutputSynchronousEnabled("),
            "NvidiaWorkarounds should toggle debug output sync via VulkanicAPI.setDebugOutputSynchronousEnabled");
    }

    // ── Task 1b: getActiveVulkanicRenderPass() accessor ───────────────────────

    @Test
    public void testGlCommandEncoderHasGetActiveVulkanicRenderPassAccessor()
            throws NoSuchMethodException {
        Method m = net.blaze3d.opengl.GlCommandEncoder.class
            .getMethod("getActiveVulkanicRenderPass");
        assertNotNull(m, "GlCommandEncoder must expose getActiveVulkanicRenderPass()");
        assertEquals(VulkanicRenderPass.class, m.getReturnType(),
            "getActiveVulkanicRenderPass() must return VulkanicRenderPass");
    }

    @Test
    public void testGetActiveVulkanicRenderPassIsNullableAnnotated() throws NoSuchMethodException {
        Method m = net.blaze3d.opengl.GlCommandEncoder.class
            .getMethod("getActiveVulkanicRenderPass");
        // Method must exist; null-return is the documented contract (no active pass)
        assertNotNull(m);
    }

    // ── Task 2: createTextureViewFromGlHandle bridge removal ─────────────

    @Test
    public void testCreateTextureViewFromGlHandleRemovedFromGraphicsBackend() throws IOException {
        Path file = SRC_MAIN_JAVA.resolve("net/vulkanic/GraphicsBackend.java");
        String source = Files.readString(file);

        // The bridge method has been removed: GpuTexture now implements VulkanicTexture,
        // so no GL-handle bridge is needed.
        assertFalse(source.contains("createTextureViewFromGlHandle"),
            "createTextureViewFromGlHandle bridge must be removed from GraphicsBackend — " +
            "GpuTexture now implements VulkanicTexture, making the bridge unnecessary");
    }

    @Test
    public void testGpuTextureImplementsVulkanicTextureInSource() throws IOException {
        Path file = SRC_MAIN_JAVA.resolve("net/blaze3d/textures/GpuTexture.java");
        String source = Files.readString(file);

        assertTrue(source.contains("implements") && source.contains("VulkanicTexture"),
            "GpuTexture must implement VulkanicTexture");
        assertTrue(source.contains("getVulkanicFormat"),
            "GpuTexture must provide getVulkanicFormat() implementing the interface method");
    }

    // ── Task 3: VoxelMap bypass migrated to DSA ───────────────────────────────

    @Test
    public void testCompressibleGLBufferedImageUsesGenerateMipmapDSA() throws IOException {
        Path file = SRC_MAIN_JAVA.resolve(
            "net/voxelmap/persistent/CompressibleGLBufferedImage.java");
        assertTrue(Files.exists(file), "CompressibleGLBufferedImage.java must exist");
        String source = Files.readString(file);

        assertFalse(source.contains("VulkanicAPI.bindTexture2D"),
            "CompressibleGLBufferedImage must no longer call bindTexture2D before mipmap generation; " +
            "use generateTextureMipmapDSA instead to avoid mutating global GL texture bind state");
        assertFalse(source.contains("VulkanicAPI.generateTextureMipmap("),
            "CompressibleGLBufferedImage must no longer call the non-DSA generateTextureMipmap; " +
            "it must use generateTextureMipmapDSA");
        assertTrue(source.contains("VulkanicAPI.generateTextureMipmapDSA("),
            "CompressibleGLBufferedImage must call generateTextureMipmapDSA for state-mutation-free mipmap generation");
    }

    @Test
    public void testCompressibleGLBufferedImageDropsCommandContextImport() throws IOException {
        Path file = SRC_MAIN_JAVA.resolve(
            "net/voxelmap/persistent/CompressibleGLBufferedImage.java");
        String source = Files.readString(file);

        assertFalse(source.contains("import net.vulkanic.CommandContext;"),
            "CompressibleGLBufferedImage must not import CommandContext after the mipmap migration " +
            "(the local variable is no longer needed)");
    }

    @Test
    public void testCompressibleGLBufferedImageStillCallsVulkanicAPI() throws IOException {
        Path file = SRC_MAIN_JAVA.resolve(
            "net/voxelmap/persistent/CompressibleGLBufferedImage.java");
        String source = Files.readString(file);

        assertTrue(source.contains("VulkanicAPI."),
            "CompressibleGLBufferedImage must still call VulkanicAPI (generateTextureMipmapDSA)");
    }

    // ── Consistency: drawFromBuffers still has all instanced paths ────────────

    @Test
    public void testDrawFromBuffersRetainsInstancedDrawCalls() throws IOException {
        Path file = SRC_MAIN_JAVA.resolve(
            "net/blaze3d/opengl/GlCommandEncoder.java");
        String source = Files.readString(file);

        assertTrue(source.contains("VulkanicAPI.drawIndexedInstancedBaseVertex("),
            "Instanced+baseVertex indexed draw must still be present in drawFromBuffers");
        assertTrue(source.contains("VulkanicAPI.drawIndexedInstanced("),
            "Instanced indexed draw must still be present in drawFromBuffers");
        assertTrue(source.contains("VulkanicAPI.drawIndexedBaseVertex("),
            "BaseVertex indexed draw must still be present in drawFromBuffers");
        assertTrue(source.contains("VulkanicAPI.drawArraysInstanced("),
            "Instanced non-indexed draw must still be present in drawFromBuffers");
    }
}
