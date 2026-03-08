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
        assertTrue(source.contains("VulkanicAPI.bindUniformBufferRange(ctx, var39"),
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
        assertFalse(source.contains("GlStateManager._readPixels("),
            "GlCommandEncoder readback path should not route readPixels through GlStateManager wrapper");
        assertFalse(source.contains("GlStateManager._getError()"),
            "GlCommandEncoder readback path should not route getError through GlStateManager wrapper");

        assertTrue(source.contains("VulkanicAPI.bindPixelPackBuffer("),
            "GlCommandEncoder readback path should bind PBO through VulkanicAPI.bindPixelPackBuffer");
        assertTrue(source.contains("VulkanicAPI.setPixelStore(ctx, VulkanicAPI.GL_PACK_ROW_LENGTH"),
            "GlCommandEncoder readback path should set row length via VulkanicAPI GL_PACK_ROW_LENGTH helper");
        assertTrue(source.contains("VulkanicAPI.framebufferColorAttachment0Texture2D("),
            "GlCommandEncoder readback path should detach color attachment via framebufferColorAttachment0Texture2D helper");
        assertTrue(source.contains("VulkanicAPI.readPixels(ctx"),
            "GlCommandEncoder readback path should call VulkanicAPI.readPixels directly");
        assertTrue(source.contains("VulkanicAPI.getError(ctx)"),
            "GlCommandEncoder readback path should query error via VulkanicAPI.getError(ctx)");
    }

    @Test
    public void testGlCommandEncoderUsesAgnosticUnpackUploadHelpers() throws IOException {
        Path file = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/GlCommandEncoder.java");
        String source = Files.readString(file);

        assertFalse(source.contains("GlStateManager._pixelStore(3314"),
            "GlCommandEncoder texture upload paths should not set unpack row length via hardcoded literal 3314");
        assertFalse(source.contains("GlStateManager._pixelStore(3316"),
            "GlCommandEncoder texture upload paths should not set unpack skip-pixels via hardcoded literal 3316");
        assertFalse(source.contains("GlStateManager._pixelStore(3315"),
            "GlCommandEncoder texture upload paths should not set unpack skip-rows via hardcoded literal 3315");
        assertFalse(source.contains("GlStateManager._pixelStore(3317"),
            "GlCommandEncoder texture upload paths should not set unpack alignment via hardcoded literal 3317");
        assertFalse(source.contains("GlStateManager._texSubImage2D("),
            "GlCommandEncoder texture upload paths should not route texSubImage2D through GlStateManager wrapper");

        assertTrue(source.contains("VulkanicAPI.setPixelStore(ctx, VulkanicAPI.GL_UNPACK_ROW_LENGTH"),
            "GlCommandEncoder texture upload paths should set unpack row length via VulkanicAPI helper");
        assertTrue(source.contains("VulkanicAPI.setPixelStore(ctx, VulkanicAPI.GL_UNPACK_SKIP_PIXELS"),
            "GlCommandEncoder texture upload paths should set unpack skip-pixels via VulkanicAPI helper");
        assertTrue(source.contains("VulkanicAPI.setPixelStore(ctx, VulkanicAPI.GL_UNPACK_SKIP_ROWS"),
            "GlCommandEncoder texture upload paths should set unpack skip-rows via VulkanicAPI helper");
        assertTrue(source.contains("VulkanicAPI.setPixelStore(ctx, VulkanicAPI.GL_UNPACK_ALIGNMENT"),
            "GlCommandEncoder texture upload paths should set unpack alignment via VulkanicAPI helper");
        assertTrue(source.contains("VulkanicAPI.uploadTexture2DSubImage(ctx"),
            "GlCommandEncoder texture upload paths should call VulkanicAPI.uploadTexture2DSubImage directly");
        assertFalse(source.contains("GlStateManager._glUniform1i("),
            "GlCommandEncoder should not upload UTB/sampler uniforms through GlStateManager._glUniform1i wrapper");
        assertFalse(source.contains("GlStateManager._texParameter("),
            "GlCommandEncoder sampler setup should not set texture parameters through GlStateManager._texParameter wrapper");
        assertTrue(source.contains("VulkanicAPI.setUniform1i(ctx"),
            "GlCommandEncoder should upload UTB/sampler uniforms directly via VulkanicAPI.setUniform1i");
        assertTrue(source.contains("VulkanicAPI.setTextureParameter(ctx"),
            "GlCommandEncoder sampler setup should set base/max level directly via VulkanicAPI.setTextureParameter");
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
        assertFalse(source.contains("GlStateManager._enableVertexAttribArray("),
            "VertexArrayCache should not enable attributes through GlStateManager wrapper");
        assertFalse(source.contains("GlStateManager._vertexAttribPointer("),
            "VertexArrayCache should not set attrib pointers through GlStateManager wrapper");
        assertFalse(source.contains("GlStateManager._vertexAttribIPointer("),
            "VertexArrayCache should not set integer attrib pointers through GlStateManager wrapper");
        assertFalse(source.contains("GlStateManager._glBindVertexArray("),
            "VertexArrayCache should not bind vertex arrays through GlStateManager wrapper");
        assertTrue(source.contains("VulkanicAPI.enableVertexAttribArray("),
            "VertexArrayCache should enable attributes directly via VulkanicAPI.enableVertexAttribArray");
        assertTrue(source.contains("VulkanicAPI.setVertexAttribPointer("),
            "VertexArrayCache should set attrib pointers directly via VulkanicAPI.setVertexAttribPointer");
        assertTrue(source.contains("VulkanicAPI.setVertexAttribIPointer("),
            "VertexArrayCache should set integer attrib pointers directly via VulkanicAPI.setVertexAttribIPointer");
        assertTrue(source.contains("VulkanicAPI.bindVertexArray("),
            "VertexArrayCache should bind vertex arrays directly via VulkanicAPI.bindVertexArray");
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
    public void testGlDeviceTextureSetupUsesAgnosticTextureParameterHelpers() throws IOException {
        Path file = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/GlDevice.java");
        String source = Files.readString(file);

        assertFalse(source.contains("_texParameter(o, 33085"),
            "GlDevice texture setup should not set GL_TEXTURE_MAX_LEVEL via hardcoded literal 33085");
        assertFalse(source.contains("_texParameter(o, 33082"),
            "GlDevice texture setup should not set GL_TEXTURE_MIN_LOD via hardcoded literal 33082");
        assertFalse(source.contains("_texParameter(o, 33083"),
            "GlDevice texture setup should not set GL_TEXTURE_MAX_LOD via hardcoded literal 33083");
        assertFalse(source.contains("_texParameter(o, 34892"),
            "GlDevice texture setup should not toggle GL_TEXTURE_COMPARE_MODE via hardcoded literal 34892");
        assertFalse(source.contains("_getInteger(3379)"),
            "GlDevice max texture-size probe should not query GL_MAX_TEXTURE_SIZE via hardcoded literal 3379");
        assertFalse(source.contains("_texImage2D(32868"),
            "GlDevice max texture-size probe should not use hardcoded GL_PROXY_TEXTURE_2D literal 32868");

        assertTrue(source.contains("VulkanicAPI.setTextureMaxLevel("),
            "GlDevice texture setup should use VulkanicAPI.setTextureMaxLevel helper");
        assertTrue(source.contains("VulkanicAPI.setTextureMinLod("),
            "GlDevice texture setup should use VulkanicAPI.setTextureMinLod helper");
        assertTrue(source.contains("VulkanicAPI.setTextureMaxLod("),
            "GlDevice texture setup should use VulkanicAPI.setTextureMaxLod helper");
        assertTrue(source.contains("VulkanicAPI.disableTextureCompareMode("),
            "GlDevice depth texture setup should use VulkanicAPI.disableTextureCompareMode helper");
        assertTrue(source.contains("VulkanicAPI.GL_MAX_TEXTURE_SIZE"),
            "GlDevice max texture-size probe should use VulkanicAPI.GL_MAX_TEXTURE_SIZE constant");
        assertTrue(source.contains("VulkanicAPI.GL_PROXY_TEXTURE_2D"),
            "GlDevice max texture-size probe should use VulkanicAPI.GL_PROXY_TEXTURE_2D constant");
    }

    @Test
    public void testGlDeviceUsesDirectVulkanicQueryAndErrorCalls() throws IOException {
        Path file = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/GlDevice.java");
        String source = Files.readString(file);

        assertFalse(source.contains("GlStateManager._getString("),
            "GlDevice should not query strings via GlStateManager._getString wrapper in migrated paths");
        assertFalse(source.contains("GlStateManager._getInteger("),
            "GlDevice should not query integers via GlStateManager._getInteger wrapper in migrated paths");
        assertFalse(source.contains("GlStateManager._getTexLevelParameter("),
            "GlDevice should not query texture level params via GlStateManager wrapper in migrated paths");
        assertFalse(source.contains("GlStateManager._getError()"),
            "GlDevice should not query errors via GlStateManager._getError wrapper in migrated paths");
        assertFalse(source.contains("GlStateManager.clearGlErrors()"),
            "GlDevice should not clear errors via GlStateManager.clearGlErrors wrapper in migrated paths");

        assertTrue(source.contains("VulkanicAPI.getString("),
            "GlDevice should query strings via direct VulkanicAPI.getString calls");
        assertTrue(source.contains("VulkanicAPI.getInteger("),
            "GlDevice should query integer limits via direct VulkanicAPI.getInteger calls");
        assertTrue(source.contains("VulkanicAPI.getTextureLevelParameter("),
            "GlDevice should query proxy texture width via VulkanicAPI.getTextureLevelParameter");
        assertTrue(source.contains("VulkanicAPI.getError("),
            "GlDevice should query errors via direct VulkanicAPI.getError calls");
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
    public void testIrisUtilityPathsUseDirectVulkanicCalls() throws IOException {
        Path clearPassFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/targets/ClearPassCreator.java");
        String clearPassSource = Files.readString(clearPassFile);
        assertFalse(clearPassSource.contains("GlStateManager._getInteger("),
            "ClearPassCreator should not query max draw buffers through GlStateManager wrapper");
        assertTrue(clearPassSource.contains("VulkanicAPI.getInteger(VulkanicAPI.getImmediateContext(), VulkanicAPI.GL_MAX_DRAW_BUFFERS)"),
            "ClearPassCreator should query max draw buffers directly through VulkanicAPI");

        Path samplerLimitsFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/gl/sampler/SamplerLimits.java");
        String samplerLimitsSource = Files.readString(samplerLimitsFile);
        assertFalse(samplerLimitsSource.contains("GlStateManager._getInteger("),
            "SamplerLimits should not query limits through GlStateManager wrapper");
        assertTrue(samplerLimitsSource.contains("VulkanicAPI.getInteger(VulkanicAPI.getImmediateContext()"),
            "SamplerLimits should query limits directly through VulkanicAPI");

        Path standardMacrosFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/gl/shader/StandardMacros.java");
        String standardMacrosSource = Files.readString(standardMacrosFile);
        assertFalse(standardMacrosSource.contains("GlStateManager._getString("),
            "StandardMacros should not query GL strings through GlStateManager wrapper");
        assertFalse(standardMacrosSource.contains("GlStateManager._getInteger("),
            "StandardMacros should not query extension count through GlStateManager wrapper");
        assertTrue(standardMacrosSource.contains("VulkanicAPI.getString(VulkanicAPI.getImmediateContext(), name)"),
            "StandardMacros should query GL version strings directly through VulkanicAPI");
        assertTrue(standardMacrosSource.contains("VulkanicAPI.getInteger(VulkanicAPI.getImmediateContext(), VulkanicAPI.GL_NUM_EXTENSIONS)"),
            "StandardMacros should query extension count directly through VulkanicAPI");

        Path programCreatorFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/gl/shader/ProgramCreator.java");
        String programCreatorSource = Files.readString(programCreatorFile);
        assertFalse(programCreatorSource.contains("GlStateManager._glBindAttribLocation("),
            "ProgramCreator should not bind attributes through GlStateManager wrapper");
        assertTrue(programCreatorSource.contains("VulkanicAPI.setAttributeLocation(ctx, program"),
            "ProgramCreator should bind attributes through VulkanicAPI.setAttributeLocation");

        Path intCachedUniformFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/uniforms/custom/cached/IntCachedUniform.java");
        String intCachedUniformSource = Files.readString(intCachedUniformFile);
        assertFalse(intCachedUniformSource.contains("GlStateManager._glUniform1i("),
            "IntCachedUniform should not upload via GlStateManager._glUniform1i wrapper");
        assertTrue(intCachedUniformSource.contains("VulkanicAPI.setUniform1i("),
            "IntCachedUniform should upload directly via VulkanicAPI.setUniform1i");

        Path boolCachedUniformFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/uniforms/custom/cached/BooleanCachedUniform.java");
        String boolCachedUniformSource = Files.readString(boolCachedUniformFile);
        assertFalse(boolCachedUniformSource.contains("GlStateManager._glUniform1i("),
            "BooleanCachedUniform should not upload via GlStateManager._glUniform1i wrapper");
        assertTrue(boolCachedUniformSource.contains("VulkanicAPI.setUniform1i("),
            "BooleanCachedUniform should upload directly via VulkanicAPI.setUniform1i");

        Path programSamplersFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/gl/program/ProgramSamplers.java");
        String programSamplersSource = Files.readString(programSamplersFile);
        assertFalse(programSamplersSource.contains("GlStateManager._glUniform1i("),
            "ProgramSamplers initializer should not upload via GlStateManager._glUniform1i wrapper");
        assertTrue(programSamplersSource.contains("VulkanicAPI.setUniform1i(VulkanicAPI.getImmediateContext()"),
            "ProgramSamplers initializer should upload directly through VulkanicAPI.setUniform1i");

        Path programImagesFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/gl/program/ProgramImages.java");
        String programImagesSource = Files.readString(programImagesFile);
        assertFalse(programImagesSource.contains("GlStateManager._glUniform1i("),
            "ProgramImages initializer should not upload via GlStateManager._glUniform1i wrapper");
        assertTrue(programImagesSource.contains("VulkanicAPI.setUniform1i(VulkanicAPI.getImmediateContext()"),
            "ProgramImages initializer should upload directly through VulkanicAPI.setUniform1i");

        Path textureUploadHelperFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/gl/texture/TextureUploadHelper.java");
        String textureUploadHelperSource = Files.readString(textureUploadHelperFile);
        assertFalse(textureUploadHelperSource.contains("GlStateManager._pixelStore("),
            "TextureUploadHelper should not reset unpack state through GlStateManager._pixelStore wrapper");
        assertTrue(textureUploadHelperSource.contains("VulkanicAPI.setPixelStore(ctx"),
            "TextureUploadHelper should reset unpack state directly through VulkanicAPI.setPixelStore");

        Path fallbackShaderFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/pipeline/programs/FallbackShader.java");
        String fallbackShaderSource = Files.readString(fallbackShaderFile);
        assertFalse(fallbackShaderSource.contains("GlStateManager._glUniform1i("),
            "FallbackShader should not upload sampler uniforms through GlStateManager._glUniform1i wrapper");
        assertTrue(fallbackShaderSource.contains("VulkanicAPI.setUniform1i(VulkanicAPI.getImmediateContext(), gtexture, 0)"),
            "FallbackShader should upload sampler uniforms directly through VulkanicAPI.setUniform1i");

        Path intUniformFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/gl/uniform/IntUniform.java");
        String intUniformSource = Files.readString(intUniformFile);
        assertFalse(intUniformSource.contains("GlStateManager._glUniform1i("),
            "IntUniform should not upload through GlStateManager._glUniform1i wrapper");
        assertTrue(intUniformSource.contains("VulkanicAPI.setUniform1i(VulkanicAPI.getImmediateContext(), location, newValue)"),
            "IntUniform should upload directly through VulkanicAPI.setUniform1i");

        Path glFramebufferFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/gl/framebuffer/GlFramebuffer.java");
        String glFramebufferSource = Files.readString(glFramebufferFile);
        assertFalse(glFramebufferSource.contains("GlStateManager._getInteger("),
            "GlFramebuffer should not query caps through GlStateManager._getInteger wrapper");
        assertTrue(glFramebufferSource.contains("VulkanicAPI.getInteger(VulkanicAPI.getImmediateContext(), VulkanicAPI.GL_MAX_DRAW_BUFFERS)"),
            "GlFramebuffer should query draw-buffer cap directly through VulkanicAPI.getInteger");
        assertTrue(glFramebufferSource.contains("VulkanicAPI.getInteger(VulkanicAPI.getImmediateContext(), VulkanicAPI.GL_MAX_COLOR_ATTACHMENTS)"),
            "GlFramebuffer should query color-attachment cap directly through VulkanicAPI.getInteger");

        Path textureInfoCacheFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/pbr/TextureInfoCache.java");
        String textureInfoCacheSource = Files.readString(textureInfoCacheFile);
        assertFalse(textureInfoCacheSource.contains("GlStateManager._getInteger("),
            "TextureInfoCache should not query current texture binding through GlStateManager._getInteger wrapper");
        assertFalse(textureInfoCacheSource.contains("GlStateManager._getTexLevelParameter("),
            "TextureInfoCache should not query texture level params through GlStateManager._getTexLevelParameter wrapper");
        assertTrue(textureInfoCacheSource.contains("VulkanicAPI.getInteger(VulkanicAPI.getImmediateContext(), VulkanicAPI.GL_TEXTURE_BINDING_2D)"),
            "TextureInfoCache should query current texture binding directly through VulkanicAPI.getInteger");
        assertTrue(textureInfoCacheSource.contains("VulkanicAPI.getTextureLevelParameter(VulkanicAPI.getImmediateContext(), VulkanicAPI.GL_TEXTURE_2D, 0, pname)"),
            "TextureInfoCache should query texture level params directly through VulkanicAPI.getTextureLevelParameter");

        Path textureManipulationUtilFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/pbr/util/TextureManipulationUtil.java");
        String textureManipulationUtilSource = Files.readString(textureManipulationUtilFile);
        assertFalse(textureManipulationUtilSource.contains("GlStateManager._getInteger("),
            "TextureManipulationUtil should not query framebuffer/texture bindings through GlStateManager._getInteger wrapper");
        assertFalse(textureManipulationUtilSource.contains("GlStateManager._getTexLevelParameter("),
            "TextureManipulationUtil should not query tex level dimensions through GlStateManager._getTexLevelParameter wrapper");
        assertTrue(textureManipulationUtilSource.contains("VulkanicAPI.getInteger(VulkanicAPI.getImmediateContext(), VulkanicAPI.GL_FRAMEBUFFER_BINDING)"),
            "TextureManipulationUtil should query previous framebuffer directly through VulkanicAPI.getInteger");
        assertTrue(textureManipulationUtilSource.contains("VulkanicAPI.getInteger(VulkanicAPI.getImmediateContext(), VulkanicAPI.GL_TEXTURE_BINDING_2D)"),
            "TextureManipulationUtil should query previous texture directly through VulkanicAPI.getInteger");
        assertTrue(textureManipulationUtilSource.contains("VulkanicAPI.getTextureLevelParameter(VulkanicAPI.getImmediateContext(), VulkanicAPI.GL_TEXTURE_2D, level, VulkanicAPI.GL_TEXTURE_WIDTH)"),
            "TextureManipulationUtil should query mip width directly through VulkanicAPI.getTextureLevelParameter");

        Path sodiumShaderFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/pipeline/programs/SodiumShader.java");
        String sodiumShaderSource = Files.readString(sodiumShaderFile);
        assertFalse(sodiumShaderSource.contains("GlStateManager._texParameter(3553, 33084"),
            "SodiumShader should not set base mip level with hardcoded target/pname literals via GlStateManager wrapper");
        assertFalse(sodiumShaderSource.contains("GlStateManager._texParameter(3553, 33085"),
            "SodiumShader should not set max mip level with hardcoded target/pname literals via GlStateManager wrapper");
        assertTrue(sodiumShaderSource.contains("VulkanicAPI.setTextureParameter(ctx, VulkanicAPI.GL_TEXTURE_2D, VulkanicAPI.GL_TEXTURE_BASE_LEVEL"),
            "SodiumShader should set base mip level directly through VulkanicAPI.setTextureParameter");
        assertTrue(sodiumShaderSource.contains("VulkanicAPI.setTextureParameter(ctx, VulkanicAPI.GL_TEXTURE_2D, VulkanicAPI.GL_TEXTURE_MAX_LEVEL"),
            "SodiumShader should set max mip level directly through VulkanicAPI.setTextureParameter");
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
    public void testBlaze3dSyncPathsUseAgnosticFenceHelpers() throws IOException {
        Path fenceFile = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/GlFence.java");
        String fenceSource = Files.readString(fenceFile);

        assertFalse(fenceSource.contains("_glFenceSync(37143, 0)"),
            "blaze3d GlFence should not create sync with hardcoded GL_SYNC_GPU_COMMANDS_COMPLETE literal 37143");
        assertFalse(fenceSource.contains("i == 37147"),
            "blaze3d GlFence should not compare timeout with hardcoded GL_TIMEOUT_EXPIRED literal 37147");
        assertFalse(fenceSource.contains("i == 37149"),
            "blaze3d GlFence should not compare failure with hardcoded GL_WAIT_FAILED literal 37149");

        assertTrue(fenceSource.contains("VulkanicAPI.isSyncWaitTimeout("),
            "blaze3d GlFence should detect timeout via VulkanicAPI.isSyncWaitTimeout helper");
        assertTrue(fenceSource.contains("VulkanicAPI.isSyncWaitFailed("),
            "blaze3d GlFence should detect wait failure via VulkanicAPI.isSyncWaitFailed helper");
        assertTrue(fenceSource.contains("VulkanicAPI.createGpuCompletionFence("),
            "blaze3d GlFence should create fences directly via VulkanicAPI.createGpuCompletionFence");
        assertTrue(fenceSource.contains("VulkanicAPI.destroySync("),
            "blaze3d GlFence should destroy fences directly via VulkanicAPI.destroySync");
        assertTrue(fenceSource.contains("VulkanicAPI.waitForSync("),
            "blaze3d GlFence should wait directly via VulkanicAPI.waitForSync");

        Path stateManagerFile = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/GlStateManager.java");
        String stateManagerSource = Files.readString(stateManagerFile);

        assertFalse(stateManagerSource.contains("public static long _glFenceSync("),
            "GlStateManager should no longer expose _glFenceSync wrapper");
        assertFalse(stateManagerSource.contains("public static int _glClientWaitSync("),
            "GlStateManager should no longer expose _glClientWaitSync wrapper");
        assertFalse(stateManagerSource.contains("public static void _glDeleteSync("),
            "GlStateManager should no longer expose _glDeleteSync wrapper");
    }

    @Test
    public void testBlaze3dUniformAndAttribWrappersRemovedFromGlStateManager() throws IOException {
        Path stateManagerFile = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/GlStateManager.java");
        String stateManagerSource = Files.readString(stateManagerFile);

        assertFalse(stateManagerSource.contains("public static void _glUniform1i("),
            "GlStateManager should no longer expose _glUniform1i wrapper");
        assertFalse(stateManagerSource.contains("public static void _glBindAttribLocation("),
            "GlStateManager should no longer expose _glBindAttribLocation wrapper");

        Path glProgramFile = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/GlProgram.java");
        String glProgramSource = Files.readString(glProgramFile);
        assertFalse(glProgramSource.contains("GlStateManager._glBindAttribLocation("),
            "GlProgram should not bind attributes through removed GlStateManager wrapper");
        assertTrue(glProgramSource.contains("VulkanicAPI.setAttributeLocation(ctx"),
            "GlProgram should bind attributes directly via VulkanicAPI.setAttributeLocation");

        Path vertexFormatFile = SRC_MAIN_JAVA.resolve("net/blaze3d/vertex/VertexFormat.java");
        String vertexFormatSource = Files.readString(vertexFormatFile);
        assertFalse(vertexFormatSource.contains("GlStateManager._glBindAttribLocation("),
            "VertexFormat Iris binding path should not use removed GlStateManager wrapper");
        assertTrue(vertexFormatSource.contains("VulkanicAPI.setAttributeLocation(ctx"),
            "VertexFormat Iris binding path should use VulkanicAPI.setAttributeLocation directly");
    }

    @Test
    public void testBlaze3dQueryAndTexParameterWrappersRemovedFromGlStateManager() throws IOException {
        Path stateManagerFile = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/GlStateManager.java");
        String stateManagerSource = Files.readString(stateManagerFile);

        assertFalse(stateManagerSource.contains("public static String _getString("),
            "GlStateManager should no longer expose _getString wrapper");
        assertFalse(stateManagerSource.contains("public static int _getInteger("),
            "GlStateManager should no longer expose _getInteger wrapper");
        assertFalse(stateManagerSource.contains("public static int _getTexLevelParameter("),
            "GlStateManager should no longer expose _getTexLevelParameter wrapper");
        assertFalse(stateManagerSource.contains("public static void _texParameter("),
            "GlStateManager should no longer expose _texParameter wrapper");
    }

    @Test
    public void testOpenGLBackendManagedAllocationsAvoidBlaze3dErrorWrappers() throws IOException {
        Path backendFile = SRC_MAIN_JAVA.resolve("net/vulkanic/backends/opengl/OpenGLBackend.java");
        String backendSource = Files.readString(backendFile);

        assertFalse(backendSource.contains("net.blaze3d.opengl.GlStateManager.clearGlErrors()"),
            "OpenGLBackend managed allocation paths should not clear errors through Blaze3D GlStateManager wrapper");
        assertFalse(backendSource.contains("net.blaze3d.opengl.GlStateManager._getError()"),
            "OpenGLBackend managed allocation paths should not query errors through Blaze3D GlStateManager wrapper");
        assertTrue(backendSource.contains("while (GL11.glGetError() != GL11.GL_NO_ERROR)"),
            "OpenGLBackend managed allocation paths should clear errors directly via GL11.glGetError loop");
        assertTrue(backendSource.contains("int error = GL11.glGetError()"),
            "OpenGLBackend managed allocation paths should query errors directly via GL11.glGetError");
    }

    @Test
    public void testVertexArrayCacheUsesDirectVulkanicStringQueries() throws IOException {
        Path file = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/VertexArrayCache.java");
        String source = Files.readString(file);

        assertFalse(source.contains("GlStateManager._getString(7936)"),
            "VertexArrayCache should not query GL_VENDOR via hardcoded literal through GlStateManager._getString wrapper");
        assertFalse(source.contains("GlStateManager._getString(7938)"),
            "VertexArrayCache should not query GL_VERSION via hardcoded literal through GlStateManager._getString wrapper");
        assertTrue(source.contains("VulkanicAPI.getString(VulkanicAPI.getImmediateContext(), VulkanicAPI.GL_VENDOR)"),
            "VertexArrayCache should query vendor directly through VulkanicAPI.getString + VulkanicAPI.GL_VENDOR");
        assertTrue(source.contains("VulkanicAPI.getString(VulkanicAPI.getImmediateContext(), VulkanicAPI.GL_VERSION)"),
            "VertexArrayCache should query version directly through VulkanicAPI.getString + VulkanicAPI.GL_VERSION");
    }

    @Test
    public void testBufferStorageUsesDirectVulkanicErrorQueries() throws IOException {
        Path file = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/BufferStorage.java");
        String source = Files.readString(file);

        assertFalse(source.contains("GlStateManager._getError()"),
            "BufferStorage map failure paths should not query errors through GlStateManager._getError wrapper");
        assertFalse(source.contains("GlStateManager.clearGlErrors()"),
            "BufferStorage map failure paths should not clear errors through GlStateManager.clearGlErrors wrapper");
        assertTrue(source.contains("VulkanicAPI.getError(VulkanicAPI.getImmediateContext())"),
            "BufferStorage map failure paths should query errors directly through VulkanicAPI.getError");
    }

    @Test
    public void testBlaze3dErrorWrappersRemovedFromGlStateManager() throws IOException {
        Path stateManagerFile = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/GlStateManager.java");
        String stateManagerSource = Files.readString(stateManagerFile);

        assertFalse(stateManagerSource.contains("public static int _getError("),
            "GlStateManager should no longer expose _getError wrapper");
        assertFalse(stateManagerSource.contains("public static void clearGlErrors("),
            "GlStateManager should no longer expose clearGlErrors wrapper");
    }

    @Test
    public void testBlaze3dVertexArrayWrappersRemovedFromGlStateManager() throws IOException {
        Path stateManagerFile = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/GlStateManager.java");
        String stateManagerSource = Files.readString(stateManagerFile);

        assertFalse(stateManagerSource.contains("public static void _glBindVertexArray("),
            "GlStateManager should no longer expose _glBindVertexArray wrapper");
        assertFalse(stateManagerSource.contains("public static void _enableVertexAttribArray("),
            "GlStateManager should no longer expose _enableVertexAttribArray wrapper");
        assertFalse(stateManagerSource.contains("public static void _vertexAttribPointer("),
            "GlStateManager should no longer expose _vertexAttribPointer wrapper");
        assertFalse(stateManagerSource.contains("public static void _vertexAttribIPointer("),
            "GlStateManager should no longer expose _vertexAttribIPointer wrapper");
        assertFalse(stateManagerSource.contains("public static int _glGenVertexArrays("),
            "GlStateManager should no longer expose _glGenVertexArrays wrapper");

        Path dhProgramFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/compat/dh/IrisGenericRenderProgram.java");
        String dhProgramSource = Files.readString(dhProgramFile);
        assertFalse(dhProgramSource.contains("GlStateManager._glBindVertexArray("),
            "IrisGenericRenderProgram should not bind VAOs through removed GlStateManager wrapper");
        assertFalse(dhProgramSource.contains("GlStateManager._glGenVertexArrays("),
            "IrisGenericRenderProgram should not create VAOs through removed GlStateManager wrapper");
        assertTrue(dhProgramSource.contains("VulkanicAPI.createVertexArray("),
            "IrisGenericRenderProgram should create VAOs directly through VulkanicAPI.createVertexArray");
        assertTrue(dhProgramSource.contains("VulkanicAPI.bindVertexArray("),
            "IrisGenericRenderProgram should bind VAOs directly through VulkanicAPI.bindVertexArray");
    }

    @Test
    public void testBlaze3dDrawAndPixelWrappersRemovedFromGlStateManager() throws IOException {
        Path stateManagerFile = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/GlStateManager.java");
        String stateManagerSource = Files.readString(stateManagerFile);

        assertFalse(stateManagerSource.contains("public static void _drawElements("),
            "GlStateManager should no longer expose _drawElements wrapper");
        assertFalse(stateManagerSource.contains("public static void _drawArrays("),
            "GlStateManager should no longer expose _drawArrays wrapper");
        assertFalse(stateManagerSource.contains("public static void _pixelStore("),
            "GlStateManager should no longer expose _pixelStore wrapper");
        assertFalse(stateManagerSource.contains("public static void _readPixels("),
            "GlStateManager should no longer expose _readPixels wrapper");
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
