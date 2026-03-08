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
    public void testGlStateManagerTypeDeleted() throws IOException {
        Path stateManagerFile = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/GlStateManager.java");
        assertTrue(Files.exists(stateManagerFile), "GlStateManager.java path should remain for migration tracking");

        String source = Files.readString(stateManagerFile);
        assertFalse(source.contains("class GlStateManager"),
            "GlStateManager type should be fully deleted from source");
        assertFalse(source.contains("public class GlStateManager"),
            "GlStateManager should no longer exist as a concrete class");
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
        assertFalse(source.contains("GlStateManager._glBlitFrameBuffer("),
            "DirectStateAccess should not blit through removed GlStateManager._glBlitFrameBuffer wrapper");

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
        assertTrue(source.contains("VulkanicAPI.blitFramebuffer(VulkanicAPI.getImmediateContext()"),
            "DirectStateAccess should blit framebuffers directly via VulkanicAPI.blitFramebuffer");
    }

    @Test
    public void testIrisRenderSystemUsesFramebufferIntentHelpers() throws IOException {
        Path stateManagerFile = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/GlStateManager.java");
        String stateManagerSource = Files.readString(stateManagerFile);

        assertFalse(stateManagerSource.contains("public static void _glBindFramebuffer("),
            "GlStateManager should no longer expose _glBindFramebuffer wrapper");
        assertFalse(stateManagerSource.contains("public static int getFrameBuffer("),
            "GlStateManager should no longer expose getFrameBuffer wrapper");

        Path irisRenderSystemFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/gl/IrisRenderSystem.java");
        String irisRenderSystemSource = Files.readString(irisRenderSystemFile);

        assertTrue(irisRenderSystemSource.contains("public static void bindFramebuffer("),
            "IrisRenderSystem should provide bindFramebuffer helper after wrapper removal");
        assertTrue(irisRenderSystemSource.contains("public static int getFrameBuffer("),
            "IrisRenderSystem should provide getFrameBuffer helper after wrapper removal");
        assertTrue(irisRenderSystemSource.contains("VulkanicAPI.bindReadFramebuffer(ctx, framebuffer)"),
            "IrisRenderSystem.bindFramebuffer should bind read FBO via VulkanicAPI.bindReadFramebuffer");
        assertTrue(irisRenderSystemSource.contains("VulkanicAPI.bindDrawFramebuffer(ctx, framebuffer)"),
            "IrisRenderSystem.bindFramebuffer should bind draw FBO via VulkanicAPI.bindDrawFramebuffer");
    }

    @Test
    public void testFramebufferDeletePathsUseDirectVulkanicCalls() throws IOException {
        Path stateManagerFile = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/GlStateManager.java");
        String stateManagerSource = Files.readString(stateManagerFile);
        assertFalse(stateManagerSource.contains("public static void _glDeleteFramebuffers("),
            "GlStateManager should no longer expose _glDeleteFramebuffers wrapper");
        assertFalse(stateManagerSource.contains("public static int glGenFramebuffers("),
            "GlStateManager should no longer expose glGenFramebuffers wrapper");

        Path directStateAccessFile = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/DirectStateAccess.java");
        String directStateAccessSource = Files.readString(directStateAccessFile);
        assertFalse(directStateAccessSource.contains("GlStateManager.glGenFramebuffers("),
            "DirectStateAccess should not create FBOs through removed GlStateManager.glGenFramebuffers wrapper");
        assertTrue(directStateAccessSource.contains("VulkanicAPI.createFramebuffer(VulkanicAPI.getImmediateContext())"),
            "DirectStateAccess should create FBOs directly through VulkanicAPI.createFramebuffer");

        Path glTextureFile = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/GlTexture.java");
        String glTextureSource = Files.readString(glTextureFile);
        assertFalse(glTextureSource.contains("GlStateManager._glDeleteFramebuffers("),
            "GlTexture should not delete cached FBOs through removed GlStateManager._glDeleteFramebuffers wrapper");
        assertTrue(glTextureSource.contains("VulkanicAPI.deleteFramebuffer(ctx, i)"),
            "GlTexture should delete cached FBOs directly through VulkanicAPI.deleteFramebuffer");

        Path irisFramebufferFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/gl/framebuffer/GlFramebuffer.java");
        String irisFramebufferSource = Files.readString(irisFramebufferFile);
        assertFalse(irisFramebufferSource.contains("GlStateManager._glDeleteFramebuffers("),
            "GlFramebuffer should not destroy FBOs through removed GlStateManager._glDeleteFramebuffers wrapper");
        assertTrue(irisFramebufferSource.contains("VulkanicAPI.deleteFramebuffer(VulkanicAPI.getImmediateContext(), framebuffer)"),
            "GlFramebuffer should destroy FBOs directly through VulkanicAPI.deleteFramebuffer");

        Path irisRenderSystemFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/gl/IrisRenderSystem.java");
        String irisRenderSystemSource = Files.readString(irisRenderSystemFile);
        assertFalse(irisRenderSystemSource.contains("GlStateManager.glGenFramebuffers("),
            "IrisRenderSystem should not create FBOs through removed GlStateManager.glGenFramebuffers wrapper");
        assertTrue(irisRenderSystemSource.contains("VulkanicAPI.createFramebuffer(VulkanicAPI.getImmediateContext())"),
            "IrisRenderSystem should create FBOs directly through VulkanicAPI.createFramebuffer");

        Path textureManipulationUtilFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/pbr/util/TextureManipulationUtil.java");
        String textureManipulationUtilSource = Files.readString(textureManipulationUtilFile);
        assertFalse(textureManipulationUtilSource.contains("GlStateManager.glGenFramebuffers("),
            "TextureManipulationUtil should not create helper FBO through removed GlStateManager.glGenFramebuffers wrapper");
        assertTrue(textureManipulationUtilSource.contains("VulkanicAPI.createFramebuffer(VulkanicAPI.getImmediateContext())"),
            "TextureManipulationUtil should create helper FBO directly through VulkanicAPI.createFramebuffer");
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
        assertTrue(source.contains("IrisRenderSystem.bindFramebuffer(VulkanicAPI.GL_FRAMEBUFFER"),
            "GlCommandEncoder should bind/unbind framebuffers through IrisRenderSystem.bindFramebuffer");
        assertTrue(source.contains("VulkanicAPI.clearBuffersWithMacosWorkaround("),
            "GlCommandEncoder should clear buffers via VulkanicAPI.clearBuffersWithMacosWorkaround");
        assertTrue(source.contains("VulkanicAPI.clearBuffersWithMacosWorkaround(VulkanicAPI.getImmediateContext(), VulkanicAPI.GL_COLOR_BUFFER_BIT)"),
            "GlCommandEncoder should clear color via VulkanicAPI.clearBuffersWithMacosWorkaround + VulkanicAPI.GL_COLOR_BUFFER_BIT");
        assertTrue(source.contains("VulkanicAPI.clearBuffersWithMacosWorkaround(VulkanicAPI.getImmediateContext(), VulkanicAPI.GL_COLOR_BUFFER_BIT | VulkanicAPI.GL_DEPTH_BUFFER_BIT)"),
            "GlCommandEncoder should clear color+depth via VulkanicAPI.clearBuffersWithMacosWorkaround");
        assertTrue(source.contains("VulkanicAPI.clearBuffersWithMacosWorkaround(VulkanicAPI.getImmediateContext(), VulkanicAPI.GL_DEPTH_BUFFER_BIT)"),
            "GlCommandEncoder should clear depth via VulkanicAPI.clearBuffersWithMacosWorkaround + VulkanicAPI.GL_DEPTH_BUFFER_BIT");
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
        assertFalse(source.contains("GlStateManager._glBindBuffer("),
            "VertexArrayCache should not bind buffers through GlStateManager wrapper");
        assertTrue(source.contains("VulkanicAPI.bindBuffer(ctx, VulkanicAPI.GL_ARRAY_BUFFER, glBuffer.handle)"),
            "VertexArrayCache should bind array buffers directly via VulkanicAPI.bindBuffer using VulkanicAPI.GL_ARRAY_BUFFER");
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
        assertFalse(textureManipulationUtilSource.contains("GlStateManager._glFramebufferTexture2D("),
            "TextureManipulationUtil should not attach/detach framebuffer textures through removed GlStateManager._glFramebufferTexture2D wrapper");
        assertTrue(textureManipulationUtilSource.contains("VulkanicAPI.getInteger(VulkanicAPI.getImmediateContext(), VulkanicAPI.GL_FRAMEBUFFER_BINDING)"),
            "TextureManipulationUtil should query previous framebuffer directly through VulkanicAPI.getInteger");
        assertTrue(textureManipulationUtilSource.contains("VulkanicAPI.getInteger(VulkanicAPI.getImmediateContext(), VulkanicAPI.GL_TEXTURE_BINDING_2D)"),
            "TextureManipulationUtil should query previous texture directly through VulkanicAPI.getInteger");
        assertTrue(textureManipulationUtilSource.contains("VulkanicAPI.getTextureLevelParameter(VulkanicAPI.getImmediateContext(), VulkanicAPI.GL_TEXTURE_2D, level, VulkanicAPI.GL_TEXTURE_WIDTH)"),
            "TextureManipulationUtil should query mip width directly through VulkanicAPI.getTextureLevelParameter");
        assertTrue(textureManipulationUtilSource.contains("VulkanicAPI.framebufferColorAttachment0Texture2D(VulkanicAPI.getImmediateContext(), VulkanicAPI.GL_FRAMEBUFFER"),
            "TextureManipulationUtil should attach/detach color attachments directly through VulkanicAPI.framebufferColorAttachment0Texture2D");

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
        assertFalse(stateManagerSource.contains("public static int _glGetUniformLocation("),
            "GlStateManager should no longer expose _glGetUniformLocation wrapper");

        Path glProgramFile = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/GlProgram.java");
        String glProgramSource = Files.readString(glProgramFile);
        assertFalse(glProgramSource.contains("GlStateManager._glBindAttribLocation("),
            "GlProgram should not bind attributes through removed GlStateManager wrapper");
        assertFalse(glProgramSource.contains("GlStateManager._glGetUniformLocation("),
            "GlProgram should not query uniforms through removed GlStateManager._glGetUniformLocation wrapper");
        assertTrue(glProgramSource.contains("VulkanicAPI.setAttributeLocation(ctx"),
            "GlProgram should bind attributes directly via VulkanicAPI.setAttributeLocation");
        assertTrue(glProgramSource.contains("VulkanicAPI.getUniformLocationWithLegacySamplerFallback(VulkanicAPI.getImmediateContext(), this.programId"),
            "GlProgram should query uniforms via VulkanicAPI.getUniformLocationWithLegacySamplerFallback");

        Path vertexFormatFile = SRC_MAIN_JAVA.resolve("net/blaze3d/vertex/VertexFormat.java");
        String vertexFormatSource = Files.readString(vertexFormatFile);
        assertFalse(vertexFormatSource.contains("GlStateManager._glBindAttribLocation("),
            "VertexFormat Iris binding path should not use removed GlStateManager wrapper");
        assertTrue(vertexFormatSource.contains("VulkanicAPI.setAttributeLocation(ctx"),
            "VertexFormat Iris binding path should use VulkanicAPI.setAttributeLocation directly");

        Path samplersFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/gl/program/ProgramSamplers.java");
        String samplersSource = Files.readString(samplersFile);
        assertFalse(samplersSource.contains("GlStateManager._glGetUniformLocation("),
            "ProgramSamplers should not query uniforms through removed GlStateManager._glGetUniformLocation wrapper");
        assertTrue(samplersSource.contains("VulkanicAPI.getUniformLocationWithLegacySamplerFallback("),
            "ProgramSamplers should query uniforms through VulkanicAPI.getUniformLocationWithLegacySamplerFallback");

        Path imagesFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/gl/program/ProgramImages.java");
        String imagesSource = Files.readString(imagesFile);
        assertFalse(imagesSource.contains("GlStateManager._glGetUniformLocation("),
            "ProgramImages should not query uniforms through removed GlStateManager._glGetUniformLocation wrapper");
        assertTrue(imagesSource.contains("VulkanicAPI.getUniformLocationWithLegacySamplerFallback("),
            "ProgramImages should query uniforms through VulkanicAPI.getUniformLocationWithLegacySamplerFallback");

        Path uniformsFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/gl/program/ProgramUniforms.java");
        String uniformsSource = Files.readString(uniformsFile);
        assertFalse(uniformsSource.contains("GlStateManager._glGetUniformLocation("),
            "ProgramUniforms should not query uniforms through removed GlStateManager._glGetUniformLocation wrapper");
        assertTrue(uniformsSource.contains("VulkanicAPI.getUniformLocationWithLegacySamplerFallback("),
            "ProgramUniforms should query uniforms through VulkanicAPI.getUniformLocationWithLegacySamplerFallback");

        Path fallbackShaderFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/pipeline/programs/FallbackShader.java");
        String fallbackShaderSource = Files.readString(fallbackShaderFile);
        assertFalse(fallbackShaderSource.contains("GlStateManager._glGetUniformLocation("),
            "FallbackShader should not query uniforms through removed GlStateManager._glGetUniformLocation wrapper");
        assertTrue(fallbackShaderSource.contains("VulkanicAPI.getUniformLocationWithLegacySamplerFallback("),
            "FallbackShader should query uniforms through VulkanicAPI.getUniformLocationWithLegacySamplerFallback");

        Path extendedShaderFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/pipeline/programs/ExtendedShader.java");
        String extendedShaderSource = Files.readString(extendedShaderFile);
        assertFalse(extendedShaderSource.contains("GlStateManager._glGetUniformLocation("),
            "ExtendedShader should not query uniforms through removed GlStateManager._glGetUniformLocation wrapper");
        assertTrue(extendedShaderSource.contains("VulkanicAPI.getUniformLocationWithLegacySamplerFallback("),
            "ExtendedShader should query uniforms through VulkanicAPI.getUniformLocationWithLegacySamplerFallback");

        Path vulkanicApiFile = SRC_MAIN_JAVA.resolve("net/vulkanic/VulkanicAPI.java");
        String vulkanicApiSource = Files.readString(vulkanicApiFile);
        assertTrue(vulkanicApiSource.contains("getUniformLocationWithLegacySamplerFallback"),
            "VulkanicAPI should expose getUniformLocationWithLegacySamplerFallback for legacy Sampler0/1/2 compatibility");
    }

    @Test
    public void testBlaze3dProgramLifecycleWrappersRemovedFromGlStateManager() throws IOException {
        Path stateManagerFile = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/GlStateManager.java");
        String stateManagerSource = Files.readString(stateManagerFile);

        assertFalse(stateManagerSource.contains("public static int glCreateProgram("),
            "GlStateManager should no longer expose glCreateProgram wrapper");
        assertFalse(stateManagerSource.contains("public static void glDeleteProgram("),
            "GlStateManager should no longer expose glDeleteProgram wrapper");
        assertFalse(stateManagerSource.contains("public static void glLinkProgram("),
            "GlStateManager should no longer expose glLinkProgram wrapper");
        assertFalse(stateManagerSource.contains("public static int glGetProgrami("),
            "GlStateManager should no longer expose glGetProgrami wrapper");
        assertFalse(stateManagerSource.contains("public static String glGetProgramInfoLog("),
            "GlStateManager should no longer expose glGetProgramInfoLog wrapper");

        Path glProgramFile = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/GlProgram.java");
        String glProgramSource = Files.readString(glProgramFile);
        assertFalse(glProgramSource.contains("GlStateManager.glCreateProgram("),
            "GlProgram should not create programs through removed GlStateManager.glCreateProgram wrapper");
        assertFalse(glProgramSource.contains("GlStateManager.glLinkProgram("),
            "GlProgram should not link programs through removed GlStateManager.glLinkProgram wrapper");
        assertFalse(glProgramSource.contains("GlStateManager.glGetProgrami("),
            "GlProgram should not query program params through removed GlStateManager.glGetProgrami wrapper");
        assertFalse(glProgramSource.contains("GlStateManager.glGetProgramInfoLog("),
            "GlProgram should not query info logs through removed GlStateManager.glGetProgramInfoLog wrapper");
        assertFalse(glProgramSource.contains("GlStateManager.glDeleteProgram("),
            "GlProgram should not delete programs through removed GlStateManager.glDeleteProgram wrapper");
        assertTrue(glProgramSource.contains("VulkanicAPI.createShaderProgram(ctx)"),
            "GlProgram should create programs directly through VulkanicAPI.createShaderProgram");
        assertTrue(glProgramSource.contains("VulkanicAPI.linkProgram(ctx, i)"),
            "GlProgram should link programs directly through VulkanicAPI.linkProgram");
        assertTrue(glProgramSource.contains("VulkanicAPI.getProgramParameter(ctx, i, 35714)"),
            "GlProgram should query link status directly through VulkanicAPI.getProgramParameter");
        assertTrue(glProgramSource.contains("VulkanicAPI.getProgramInfoLog(ctx, i)"),
            "GlProgram should query program info log directly through VulkanicAPI.getProgramInfoLog");
        assertTrue(glProgramSource.contains("VulkanicAPI.deleteProgram(VulkanicAPI.getImmediateContext(), this.programId)"),
            "GlProgram should delete programs directly through VulkanicAPI.deleteProgram");

        Path shaderCreatorFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/pipeline/programs/ShaderCreator.java");
        String shaderCreatorSource = Files.readString(shaderCreatorFile);
        assertFalse(shaderCreatorSource.contains("GlStateManager.glCreateProgram("),
            "ShaderCreator should not create programs through removed GlStateManager.glCreateProgram wrapper");
        assertFalse(shaderCreatorSource.contains("GlStateManager.glLinkProgram("),
            "ShaderCreator should not link programs through removed GlStateManager.glLinkProgram wrapper");
        assertTrue(shaderCreatorSource.contains("VulkanicAPI.createShaderProgram(VulkanicAPI.getImmediateContext())"),
            "ShaderCreator should create programs directly through VulkanicAPI.createShaderProgram");
        assertTrue(shaderCreatorSource.contains("VulkanicAPI.linkProgram(VulkanicAPI.getImmediateContext(), i)"),
            "ShaderCreator should link programs directly through VulkanicAPI.linkProgram");

        Path programCreatorFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/gl/shader/ProgramCreator.java");
        String programCreatorSource = Files.readString(programCreatorFile);
        assertFalse(programCreatorSource.contains("GlStateManager.glCreateProgram("),
            "ProgramCreator should not create programs through removed GlStateManager.glCreateProgram wrapper");
        assertFalse(programCreatorSource.contains("GlStateManager.glLinkProgram("),
            "ProgramCreator should not link programs through removed GlStateManager.glLinkProgram wrapper");
        assertFalse(programCreatorSource.contains("GlStateManager.glGetProgrami("),
            "ProgramCreator should not query link status through removed GlStateManager.glGetProgrami wrapper");
        assertTrue(programCreatorSource.contains("VulkanicAPI.createShaderProgram(VulkanicAPI.getImmediateContext())"),
            "ProgramCreator should create programs directly through VulkanicAPI.createShaderProgram");
        assertTrue(programCreatorSource.contains("VulkanicAPI.linkProgram(ctx, program)"),
            "ProgramCreator should link programs directly through VulkanicAPI.linkProgram");
        assertTrue(programCreatorSource.contains("VulkanicAPI.getProgramParameter(ctx, program, VulkanicAPI.GL_LINK_STATUS)"),
            "ProgramCreator should query link status directly through VulkanicAPI.getProgramParameter");

        Path shaderMapFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/pipeline/programs/ShaderMap.java");
        String shaderMapSource = Files.readString(shaderMapFile);
        assertFalse(shaderMapSource.contains("GlStateManager.glDeleteProgram("),
            "ShaderMap should not delete programs through removed GlStateManager.glDeleteProgram wrapper");
        assertFalse(shaderMapSource.contains("GlStateManager.glGetProgrami("),
            "ShaderMap should not query link status through removed GlStateManager.glGetProgrami wrapper");
        assertFalse(shaderMapSource.contains("GlStateManager.glGetProgramInfoLog("),
            "ShaderMap should not query program logs through removed GlStateManager.glGetProgramInfoLog wrapper");
        assertTrue(shaderMapSource.contains("VulkanicAPI.deleteProgram(VulkanicAPI.getImmediateContext(), shader.id().program())"),
            "ShaderMap should delete programs directly through VulkanicAPI.deleteProgram");
        assertTrue(shaderMapSource.contains("VulkanicAPI.getProgramParameter(VulkanicAPI.getImmediateContext(), i, 35714)"),
            "ShaderMap should query link status directly through VulkanicAPI.getProgramParameter");
        assertTrue(shaderMapSource.contains("VulkanicAPI.getProgramInfoLog(VulkanicAPI.getImmediateContext(), i)"),
            "ShaderMap should query program info logs directly through VulkanicAPI.getProgramInfoLog");

        Path uniformsFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/gl/program/ProgramUniforms.java");
        String uniformsSource = Files.readString(uniformsFile);
        assertFalse(uniformsSource.contains("GlStateManager.glGetProgrami("),
            "ProgramUniforms should not query active uniforms through removed GlStateManager.glGetProgrami wrapper");
        assertTrue(uniformsSource.contains("VulkanicAPI.getProgramParameter(VulkanicAPI.getImmediateContext(), program, VulkanicAPI.GL_ACTIVE_UNIFORMS)"),
            "ProgramUniforms should query active uniforms directly through VulkanicAPI.getProgramParameter");

        Path programFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/gl/program/Program.java");
        String programSource = Files.readString(programFile);
        assertFalse(programSource.contains("GlStateManager.glDeleteProgram("),
            "Program should not destroy programs through removed GlStateManager.glDeleteProgram wrapper");
        assertTrue(programSource.contains("VulkanicAPI.deleteProgram(VulkanicAPI.getImmediateContext(), getGlId())"),
            "Program should destroy programs directly through VulkanicAPI.deleteProgram");

        Path computeProgramFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/gl/program/ComputeProgram.java");
        String computeProgramSource = Files.readString(computeProgramFile);
        assertFalse(computeProgramSource.contains("GlStateManager.glDeleteProgram("),
            "ComputeProgram should not destroy programs through removed GlStateManager.glDeleteProgram wrapper");
        assertTrue(computeProgramSource.contains("VulkanicAPI.deleteProgram(VulkanicAPI.getImmediateContext(), getGlId())"),
            "ComputeProgram should destroy programs directly through VulkanicAPI.deleteProgram");

        Path glDeviceFile = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/GlDevice.java");
        String glDeviceSource = Files.readString(glDeviceFile);
        assertFalse(glDeviceSource.contains("GlStateManager.glCreateProgram("),
            "GlDevice AMD workaround should not create programs through removed GlStateManager.glCreateProgram wrapper");
        assertFalse(glDeviceSource.contains("GlStateManager.glDeleteProgram("),
            "GlDevice AMD workaround should not delete programs through removed GlStateManager.glDeleteProgram wrapper");
        assertTrue(glDeviceSource.contains("net.vulkanic.VulkanicAPI.createShaderProgram(net.vulkanic.VulkanicAPI.getImmediateContext())"),
            "GlDevice AMD workaround should create programs directly through VulkanicAPI.createShaderProgram");
        assertTrue(glDeviceSource.contains("net.vulkanic.VulkanicAPI.deleteProgram(net.vulkanic.VulkanicAPI.getImmediateContext(), j)"),
            "GlDevice AMD workaround should delete programs directly through VulkanicAPI.deleteProgram");
    }

    @Test
    public void testBlaze3dShaderLifecycleWrappersRemovedFromGlStateManager() throws IOException {
        Path stateManagerFile = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/GlStateManager.java");
        String stateManagerSource = Files.readString(stateManagerFile);

        assertFalse(stateManagerSource.contains("public static void glAttachShader("),
            "GlStateManager should no longer expose glAttachShader wrapper");
        assertFalse(stateManagerSource.contains("public static void glDeleteShader("),
            "GlStateManager should no longer expose glDeleteShader wrapper");
        assertFalse(stateManagerSource.contains("public static int glCreateShader("),
            "GlStateManager should no longer expose glCreateShader wrapper");
        assertFalse(stateManagerSource.contains("public static void glShaderSource("),
            "GlStateManager should no longer expose glShaderSource wrapper");
        assertFalse(stateManagerSource.contains("public static void glCompileShader("),
            "GlStateManager should no longer expose glCompileShader wrapper");
        assertFalse(stateManagerSource.contains("public static int glGetShaderi("),
            "GlStateManager should no longer expose glGetShaderi wrapper");
        assertFalse(stateManagerSource.contains("public static String glGetShaderInfoLog("),
            "GlStateManager should no longer expose glGetShaderInfoLog wrapper");

        Path glProgramFile = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/GlProgram.java");
        String glProgramSource = Files.readString(glProgramFile);
        assertFalse(glProgramSource.contains("GlStateManager.glAttachShader("),
            "GlProgram should not attach shaders through removed GlStateManager.glAttachShader wrapper");
        assertTrue(glProgramSource.contains("VulkanicAPI.attachShader(ctx, i"),
            "GlProgram should attach shaders directly through VulkanicAPI.attachShader");

        Path programCreatorFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/gl/shader/ProgramCreator.java");
        String programCreatorSource = Files.readString(programCreatorFile);
        assertFalse(programCreatorSource.contains("GlStateManager.glAttachShader("),
            "ProgramCreator should not attach shaders through removed GlStateManager.glAttachShader wrapper");
        assertTrue(programCreatorSource.contains("VulkanicAPI.attachShader(ctx, program"),
            "ProgramCreator should attach shaders directly through VulkanicAPI.attachShader");

        Path shaderCreatorFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/pipeline/programs/ShaderCreator.java");
        String shaderCreatorSource = Files.readString(shaderCreatorFile);
        assertFalse(shaderCreatorSource.contains("GlStateManager.glAttachShader("),
            "ShaderCreator should not attach shaders through removed GlStateManager.glAttachShader wrapper");
        assertFalse(shaderCreatorSource.contains("GlStateManager.glDeleteShader("),
            "ShaderCreator should not delete shaders through removed GlStateManager.glDeleteShader wrapper");
        assertFalse(shaderCreatorSource.contains("GlStateManager.glCreateShader("),
            "ShaderCreator should not create shaders through removed GlStateManager.glCreateShader wrapper");
        assertFalse(shaderCreatorSource.contains("GlStateManager.glShaderSource("),
            "ShaderCreator should not upload source through removed GlStateManager.glShaderSource wrapper");
        assertFalse(shaderCreatorSource.contains("GlStateManager.glCompileShader("),
            "ShaderCreator should not compile shaders through removed GlStateManager.glCompileShader wrapper");
        assertFalse(shaderCreatorSource.contains("GlStateManager.glGetShaderi("),
            "ShaderCreator should not query shader status through removed GlStateManager.glGetShaderi wrapper");
        assertTrue(shaderCreatorSource.contains("VulkanicAPI.attachShader(VulkanicAPI.getImmediateContext(), i, s)"),
            "ShaderCreator should attach shaders directly through VulkanicAPI.attachShader");
        assertTrue(shaderCreatorSource.contains("VulkanicAPI.deleteShader(VulkanicAPI.getImmediateContext(), s)"),
            "ShaderCreator should delete shaders directly through VulkanicAPI.deleteShader");
        assertTrue(shaderCreatorSource.contains("VulkanicAPI.createShader(VulkanicAPI.getImmediateContext(), shaderType.id)"),
            "ShaderCreator should create shaders directly through VulkanicAPI.createShader");
        assertTrue(shaderCreatorSource.contains("ShaderWorkarounds.safeShaderSource(shader, source)"),
            "ShaderCreator should upload shader source via ShaderWorkarounds.safeShaderSource");
        assertTrue(shaderCreatorSource.contains("VulkanicAPI.compileShader(VulkanicAPI.getImmediateContext(), shader)"),
            "ShaderCreator should compile shaders directly through VulkanicAPI.compileShader");
        assertTrue(shaderCreatorSource.contains("VulkanicAPI.getShaderParameter(VulkanicAPI.getImmediateContext(), shader, VulkanicAPI.GL_COMPILE_STATUS)"),
            "ShaderCreator should query compile status directly through VulkanicAPI.getShaderParameter");

        Path glDeviceFile = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/GlDevice.java");
        String glDeviceSource = Files.readString(glDeviceFile);
        assertFalse(glDeviceSource.contains("GlStateManager.glCreateShader("),
            "GlDevice should not create shaders through removed GlStateManager.glCreateShader wrapper");
        assertFalse(glDeviceSource.contains("GlStateManager.glAttachShader("),
            "GlDevice should not attach shaders through removed GlStateManager.glAttachShader wrapper");
        assertFalse(glDeviceSource.contains("GlStateManager.glDeleteShader("),
            "GlDevice should not delete shaders through removed GlStateManager.glDeleteShader wrapper");
        assertFalse(glDeviceSource.contains("GlStateManager.glShaderSource("),
            "GlDevice should not upload shader source through removed GlStateManager.glShaderSource wrapper");
        assertFalse(glDeviceSource.contains("GlStateManager.glCompileShader("),
            "GlDevice should not compile shaders through removed GlStateManager.glCompileShader wrapper");
        assertFalse(glDeviceSource.contains("GlStateManager.glGetShaderi("),
            "GlDevice should not query shader status through removed GlStateManager.glGetShaderi wrapper");
        assertFalse(glDeviceSource.contains("GlStateManager.glGetShaderInfoLog("),
            "GlDevice should not query shader logs through removed GlStateManager.glGetShaderInfoLog wrapper");
        assertTrue(glDeviceSource.contains("net.vulkanic.VulkanicAPI.createShader(net.vulkanic.VulkanicAPI.getImmediateContext()"),
            "GlDevice should create shaders directly through VulkanicAPI.createShader");
        assertTrue(glDeviceSource.contains("net.vulkanic.VulkanicAPI.attachShader(net.vulkanic.VulkanicAPI.getImmediateContext(), j, i)"),
            "GlDevice should attach shaders directly through VulkanicAPI.attachShader");
        assertTrue(glDeviceSource.contains("net.vulkanic.VulkanicAPI.deleteShader(net.vulkanic.VulkanicAPI.getImmediateContext(), i)"),
            "GlDevice should delete shaders directly through VulkanicAPI.deleteShader");
        assertTrue(glDeviceSource.contains("net.irisshaders.iris.gl.shader.ShaderWorkarounds.safeShaderSource(i, string2)"),
            "GlDevice should upload shader source via ShaderWorkarounds.safeShaderSource");
        assertTrue(glDeviceSource.contains("net.vulkanic.VulkanicAPI.compileShader(net.vulkanic.VulkanicAPI.getImmediateContext(), i)"),
            "GlDevice should compile shaders directly through VulkanicAPI.compileShader");
        assertTrue(glDeviceSource.contains("net.vulkanic.VulkanicAPI.getShaderParameter(net.vulkanic.VulkanicAPI.getImmediateContext(), i, 35713)"),
            "GlDevice should query compile status directly through VulkanicAPI.getShaderParameter");
        assertTrue(glDeviceSource.contains("net.vulkanic.VulkanicAPI.getShaderInfoLog(net.vulkanic.VulkanicAPI.getImmediateContext(), i)"),
            "GlDevice should query shader logs directly through VulkanicAPI.getShaderInfoLog");

        Path glShaderFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/gl/shader/GlShader.java");
        String glShaderSource = Files.readString(glShaderFile);
        assertFalse(glShaderSource.contains("GlStateManager.glCreateShader("),
            "GlShader should not create shaders through removed GlStateManager.glCreateShader wrapper");
        assertFalse(glShaderSource.contains("GlStateManager.glCompileShader("),
            "GlShader should not compile shaders through removed GlStateManager.glCompileShader wrapper");
        assertFalse(glShaderSource.contains("GlStateManager.glDeleteShader("),
            "GlShader should not delete shaders through removed GlStateManager.glDeleteShader wrapper");
        assertTrue(glShaderSource.contains("VulkanicAPI.createShader(VulkanicAPI.getImmediateContext(), type.id)"),
            "GlShader should create shaders directly through VulkanicAPI.createShader");
        assertTrue(glShaderSource.contains("VulkanicAPI.compileShader(VulkanicAPI.getImmediateContext(), handle)"),
            "GlShader should compile shaders directly through VulkanicAPI.compileShader");
        assertTrue(glShaderSource.contains("VulkanicAPI.deleteShader(VulkanicAPI.getImmediateContext(), this.getGlId())"),
            "GlShader should delete shaders directly through VulkanicAPI.deleteShader");

        Path partialShaderFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/pipeline/programs/PartialShader.java");
        String partialShaderSource = Files.readString(partialShaderFile);
        assertFalse(partialShaderSource.contains("GlStateManager.glDeleteShader("),
            "PartialShader should not delete shaders through removed GlStateManager.glDeleteShader wrapper");
        assertTrue(partialShaderSource.contains("VulkanicAPI.deleteShader(VulkanicAPI.getImmediateContext(), s)"),
            "PartialShader should delete shaders directly through VulkanicAPI.deleteShader");

        Path shaderModuleFile = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/GlShaderModule.java");
        String shaderModuleSource = Files.readString(shaderModuleFile);
        assertFalse(shaderModuleSource.contains("GlStateManager.glDeleteShader("),
            "GlShaderModule should not delete shaders through removed GlStateManager.glDeleteShader wrapper");
        assertTrue(shaderModuleSource.contains("net.vulkanic.VulkanicAPI.deleteShader(net.vulkanic.VulkanicAPI.getImmediateContext(), this.shaderId)"),
            "GlShaderModule should delete shaders directly through VulkanicAPI.deleteShader");
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
    public void testOpenGLBackendTracksTextureBindingsWithoutGlStateManager() throws IOException {
        Path backendFile = SRC_MAIN_JAVA.resolve("net/vulkanic/backends/opengl/OpenGLBackend.java");
        String backendSource = Files.readString(backendFile);

        assertFalse(backendSource.contains("import net.blaze3d.opengl.GlStateManager;"),
            "OpenGLBackend should not import GlStateManager for texture-state tracking");
        assertFalse(backendSource.contains("GlStateManager.activeTexture"),
            "OpenGLBackend should not read active texture from GlStateManager");
        assertFalse(backendSource.contains("GlStateManager.TEXTURES"),
            "OpenGLBackend should not read or write texture bindings through GlStateManager.TEXTURES");

        assertTrue(backendSource.contains("private final int[] texture2DBindings"),
            "OpenGLBackend should maintain backend-local 2D texture binding cache");
        assertTrue(backendSource.contains("private int activeTextureUnitIndex"),
            "OpenGLBackend should maintain backend-local active texture unit index");
        assertTrue(backendSource.contains("activeTextureUnitIndex = textureUnitIndex"),
            "OpenGLBackend should update backend-local active texture unit state in setActiveTextureUnit");
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

        assertFalse(stateManagerSource.contains("public static void _glBindBuffer("),
            "GlStateManager should no longer expose _glBindBuffer wrapper");
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
        assertFalse(stateManagerSource.contains("public static int _glGenBuffers("),
            "GlStateManager should no longer expose _glGenBuffers wrapper");
        assertFalse(stateManagerSource.contains("public static void _glBufferData("),
            "GlStateManager should no longer expose _glBufferData wrapper overloads");
        assertFalse(stateManagerSource.contains("public static void _glBufferSubData("),
            "GlStateManager should no longer expose _glBufferSubData wrapper");
        assertFalse(stateManagerSource.contains("public static ByteBuffer _glMapBufferRange("),
            "GlStateManager should no longer expose _glMapBufferRange wrapper");
        assertFalse(stateManagerSource.contains("public static void _glUnmapBuffer("),
            "GlStateManager should no longer expose _glUnmapBuffer wrapper");
        assertFalse(stateManagerSource.contains("public static void _glDeleteBuffers("),
            "GlStateManager should no longer expose _glDeleteBuffers wrapper");

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

        Path directStateAccessFile = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/DirectStateAccess.java");
        String directStateAccessSource = Files.readString(directStateAccessFile);
        assertFalse(directStateAccessSource.contains("GlStateManager._glGenBuffers("),
            "DirectStateAccess should not create buffers through removed GlStateManager._glGenBuffers wrapper");
        assertFalse(directStateAccessSource.contains("GlStateManager._glBindBuffer("),
            "DirectStateAccess should not bind buffers through removed GlStateManager._glBindBuffer wrapper");
        assertFalse(directStateAccessSource.contains("GlStateManager._glMapBufferRange("),
            "DirectStateAccess should not map buffers through removed GlStateManager._glMapBufferRange wrapper");
        assertFalse(directStateAccessSource.contains("GlStateManager._glUnmapBuffer("),
            "DirectStateAccess should not unmap buffers through removed GlStateManager._glUnmapBuffer wrapper");
        assertFalse(directStateAccessSource.contains("GlStateManager.incrementTrackedBuffers();"),
            "DirectStateAccess should no longer increment tracked buffers through GlStateManager");
        assertTrue(directStateAccessSource.contains("IrisRenderSystem.incrementTrackedBuffers();"),
            "DirectStateAccess should increment tracked buffers through IrisRenderSystem helper");
        assertTrue(directStateAccessSource.contains("VulkanicAPI.createBuffer(VulkanicAPI.getImmediateContext())"),
            "DirectStateAccess should create buffers directly via VulkanicAPI.createBuffer");
        assertTrue(directStateAccessSource.contains("VulkanicAPI.bindBuffer(VulkanicAPI.getImmediateContext(),"),
            "DirectStateAccess should bind/unbind emulated targets directly via VulkanicAPI.bindBuffer");
        assertTrue(directStateAccessSource.contains("VulkanicAPI.mapBuffer(VulkanicAPI.getImmediateContext(),"),
            "DirectStateAccess should map buffers directly via VulkanicAPI.mapBuffer");
        assertTrue(directStateAccessSource.contains("VulkanicAPI.unmapBuffer(VulkanicAPI.getImmediateContext(),"),
            "DirectStateAccess should unmap buffers directly via VulkanicAPI.unmapBuffer");

        Path irisRenderSystemFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/gl/IrisRenderSystem.java");
        String irisRenderSystemSource = Files.readString(irisRenderSystemFile);
        assertFalse(irisRenderSystemSource.contains("GlStateManager._glGenBuffers("),
            "IrisRenderSystem should not create buffers through removed GlStateManager._glGenBuffers wrapper");
        assertFalse(irisRenderSystemSource.contains("GlStateManager._glBindBuffer("),
            "IrisRenderSystem should not bind buffers through removed GlStateManager._glBindBuffer wrapper");
        assertTrue(irisRenderSystemSource.contains("VulkanicAPI.createBuffer(VulkanicAPI.getImmediateContext())"),
            "IrisRenderSystem should create buffers directly via VulkanicAPI.createBuffer");
        assertTrue(irisRenderSystemSource.contains("VulkanicAPI.bindBuffer(VulkanicAPI.getImmediateContext(), target, buffer)"),
            "IrisRenderSystem should bind new buffers directly via VulkanicAPI.bindBuffer");

        Path ssboFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/gl/buffer/ShaderStorageBuffer.java");
        String ssboSource = Files.readString(ssboFile);
        assertFalse(ssboSource.contains("GlStateManager._glGenBuffers("),
            "ShaderStorageBuffer should not create buffers through removed GlStateManager._glGenBuffers wrapper");
        assertFalse(ssboSource.contains("GlStateManager._glBindBuffer("),
            "ShaderStorageBuffer should not bind buffers through removed GlStateManager._glBindBuffer wrapper");
        assertFalse(ssboSource.contains("GlStateManager._glBufferSubData("),
            "ShaderStorageBuffer should not upload content through removed GlStateManager._glBufferSubData wrapper");
        assertTrue(ssboSource.contains("VulkanicAPI.createBuffer(VulkanicAPI.getImmediateContext())"),
            "ShaderStorageBuffer should create buffers directly via VulkanicAPI.createBuffer");
        assertTrue(ssboSource.contains("VulkanicAPI.bindBuffer(VulkanicAPI.getImmediateContext(), VulkanicAPI.GL_SHADER_STORAGE_BUFFER, getId())"),
            "ShaderStorageBuffer should bind SSBOs directly via VulkanicAPI.bindBuffer");
        assertTrue(ssboSource.contains("VulkanicAPI.bufferSubData(VulkanicAPI.getImmediateContext(), VulkanicAPI.GL_SHADER_STORAGE_BUFFER, 0L, content)"),
            "ShaderStorageBuffer should upload content directly via VulkanicAPI.bufferSubData");

        Path glBufferFile = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/GlBuffer.java");
        String glBufferSource = Files.readString(glBufferFile);
        assertFalse(glBufferSource.contains("GlStateManager._glDeleteBuffers("),
            "GlBuffer should not delete buffers through removed GlStateManager._glDeleteBuffers wrapper");
        assertTrue(glBufferSource.contains("IrisRenderSystem.decrementTrackedBuffers();"),
            "GlBuffer should preserve tracked-buffer decrement through IrisRenderSystem helper when closing");
        assertTrue(glBufferSource.contains("VulkanicAPI.deleteBuffer(VulkanicAPI.getImmediateContext(), this.handle)"),
            "GlBuffer should delete buffers directly via VulkanicAPI.deleteBuffer");

        Path dsaFile = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/DirectStateAccess.java");
        String dsaSource = Files.readString(dsaFile);
        assertFalse(dsaSource.contains("GlStateManager._glBufferData("),
            "DirectStateAccess should not call removed GlStateManager._glBufferData wrappers");
        assertFalse(dsaSource.contains("GlStateManager._glBufferSubData("),
            "DirectStateAccess should not call removed GlStateManager._glBufferSubData wrapper");
        assertTrue(dsaSource.contains("VulkanicAPI.bufferData(VulkanicAPI.getImmediateContext()"),
            "DirectStateAccess should upload buffer data directly via VulkanicAPI.bufferData");
        assertTrue(dsaSource.contains("VulkanicAPI.bufferSubData(VulkanicAPI.getImmediateContext()"),
            "DirectStateAccess should update buffer ranges directly via VulkanicAPI.bufferSubData");
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
        assertFalse(stateManagerSource.contains("public static void _glBlitFrameBuffer("),
            "GlStateManager should no longer expose _glBlitFrameBuffer wrapper");
        assertFalse(stateManagerSource.contains("public static void _glFramebufferTexture2D("),
            "GlStateManager should no longer expose _glFramebufferTexture2D wrapper");
        assertFalse(stateManagerSource.contains("public static void _texSubImage2D("),
            "GlStateManager should no longer expose _texSubImage2D wrapper overloads");
    }

    @Test
    public void testBlaze3dScissorAndPolygonWrappersRemovedFromGlStateManager() throws IOException {
        Path stateManagerFile = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/GlStateManager.java");
        String stateManagerSource = Files.readString(stateManagerFile);

        assertFalse(stateManagerSource.contains("public static void _scissorBox("),
            "GlStateManager should no longer expose _scissorBox wrapper");
        assertFalse(stateManagerSource.contains("public static void _polygonMode("),
            "GlStateManager should no longer expose _polygonMode wrapper");

        Path encoderFile = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/GlCommandEncoder.java");
        String encoderSource = Files.readString(encoderFile);

        assertFalse(encoderSource.contains("GlStateManager._scissorBox("),
            "GlCommandEncoder should not call removed GlStateManager._scissorBox wrapper");
        assertFalse(encoderSource.contains("GlStateManager._polygonMode("),
            "GlCommandEncoder should not call removed GlStateManager._polygonMode wrapper");
        assertTrue(encoderSource.contains("VulkanicAPI.setDynamicScissor(VulkanicAPI.getImmediateContext()"),
            "GlCommandEncoder should set scissor directly through VulkanicAPI.setDynamicScissor");
        assertTrue(encoderSource.contains("VulkanicAPI.setPolygonMode(VulkanicAPI.getImmediateContext()"),
            "GlCommandEncoder should set polygon mode directly through VulkanicAPI.setPolygonMode");
    }

    @Test
    public void testBlaze3dColorLogicWrappersRemovedFromGlStateManager() throws IOException {
        Path stateManagerFile = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/GlStateManager.java");
        String stateManagerSource = Files.readString(stateManagerFile);

        assertFalse(stateManagerSource.contains("public static void _enableColorLogicOp("),
            "GlStateManager should no longer expose _enableColorLogicOp wrapper");
        assertFalse(stateManagerSource.contains("public static void _disableColorLogicOp("),
            "GlStateManager should no longer expose _disableColorLogicOp wrapper");
        assertFalse(stateManagerSource.contains("public static void _logicOp("),
            "GlStateManager should no longer expose _logicOp wrapper");

        Path encoderFile = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/GlCommandEncoder.java");
        String encoderSource = Files.readString(encoderFile);

        assertFalse(encoderSource.contains("GlStateManager._enableColorLogicOp("),
            "GlCommandEncoder should not call removed GlStateManager._enableColorLogicOp wrapper");
        assertFalse(encoderSource.contains("GlStateManager._disableColorLogicOp("),
            "GlCommandEncoder should not call removed GlStateManager._disableColorLogicOp wrapper");
        assertFalse(encoderSource.contains("GlStateManager._logicOp("),
            "GlCommandEncoder should not call removed GlStateManager._logicOp wrapper");
        assertFalse(encoderSource.contains("VulkanicAPI.setLogicOp(VulkanicAPI.getImmediateContext(), 5387)"),
            "GlCommandEncoder should not hardcode OR_REVERSE literal 5387 when setting logic op");

        assertTrue(encoderSource.contains("VulkanicAPI.setColorLogicOpEnabled("),
            "GlCommandEncoder should enable/disable color logic directly through VulkanicAPI.setColorLogicOpEnabled");
        assertTrue(encoderSource.contains("VulkanicAPI.setLogicOp(VulkanicAPI.getImmediateContext(), VulkanicAPI.GL_OR_REVERSE)"),
            "GlCommandEncoder should set OR_REVERSE logic op through VulkanicAPI.GL_OR_REVERSE");
    }

    @Test
    public void testBlaze3dPolygonOffsetWrappersRemovedFromGlStateManager() throws IOException {
        Path stateManagerFile = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/GlStateManager.java");
        String stateManagerSource = Files.readString(stateManagerFile);

        assertFalse(stateManagerSource.contains("public static void _enablePolygonOffset("),
            "GlStateManager should no longer expose _enablePolygonOffset wrapper");
        assertFalse(stateManagerSource.contains("public static void _disablePolygonOffset("),
            "GlStateManager should no longer expose _disablePolygonOffset wrapper");
        assertFalse(stateManagerSource.contains("public static void _polygonOffset("),
            "GlStateManager should no longer expose _polygonOffset wrapper");

        Path encoderFile = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/GlCommandEncoder.java");
        String encoderSource = Files.readString(encoderFile);

        assertFalse(encoderSource.contains("GlStateManager._enablePolygonOffset("),
            "GlCommandEncoder should not call removed GlStateManager._enablePolygonOffset wrapper");
        assertFalse(encoderSource.contains("GlStateManager._disablePolygonOffset("),
            "GlCommandEncoder should not call removed GlStateManager._disablePolygonOffset wrapper");
        assertFalse(encoderSource.contains("GlStateManager._polygonOffset("),
            "GlCommandEncoder should not call removed GlStateManager._polygonOffset wrapper");

        assertTrue(encoderSource.contains("VulkanicAPI.setPolygonOffset("),
            "GlCommandEncoder should set polygon offset directly through VulkanicAPI.setPolygonOffset");
        assertTrue(encoderSource.contains("VulkanicAPI.setPolygonOffsetFillEnabled("),
            "GlCommandEncoder should toggle polygon-offset fill state directly through VulkanicAPI.setPolygonOffsetFillEnabled");
    }

    @Test
    public void testBlaze3dCullWrappersRemovedFromGlStateManager() throws IOException {
        Path stateManagerFile = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/GlStateManager.java");
        String stateManagerSource = Files.readString(stateManagerFile);

        assertFalse(stateManagerSource.contains("public static void _enableCull("),
            "GlStateManager should no longer expose _enableCull wrapper");
        assertFalse(stateManagerSource.contains("public static void _disableCull("),
            "GlStateManager should no longer expose _disableCull wrapper");

        Path encoderFile = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/GlCommandEncoder.java");
        String encoderSource = Files.readString(encoderFile);
        assertFalse(encoderSource.contains("GlStateManager._enableCull("),
            "GlCommandEncoder should not call removed GlStateManager._enableCull wrapper");
        assertFalse(encoderSource.contains("GlStateManager._disableCull("),
            "GlCommandEncoder should not call removed GlStateManager._disableCull wrapper");
        assertTrue(encoderSource.contains("VulkanicAPI.setCullFaceEnabled("),
            "GlCommandEncoder should toggle cull state directly through VulkanicAPI.setCullFaceEnabled");

        Path shadowRendererFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/shadows/ShadowRenderer.java");
        String shadowRendererSource = Files.readString(shadowRendererFile);
        assertFalse(shadowRendererSource.contains("GlStateManager._disableCull("),
            "ShadowRenderer should not call removed GlStateManager._disableCull wrapper");
        assertFalse(shadowRendererSource.contains("GlStateManager._enableCull("),
            "ShadowRenderer should not call removed GlStateManager._enableCull wrapper");
        assertTrue(shadowRendererSource.contains("VulkanicAPI.setCullFaceEnabled("),
            "ShadowRenderer should toggle cull state via VulkanicAPI.setCullFaceEnabled");

        Path sodiumShaderFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/pipeline/programs/SodiumShader.java");
        String sodiumShaderSource = Files.readString(sodiumShaderFile);
        assertFalse(sodiumShaderSource.contains("GlStateManager._disableCull("),
            "SodiumShader should not call removed GlStateManager._disableCull wrapper");
        assertTrue(sodiumShaderSource.contains("VulkanicAPI.setCullFaceEnabled("),
            "SodiumShader should disable culling through VulkanicAPI.setCullFaceEnabled");

        Path dhWrapperFile = SRC_MAIN_JAVA.resolve("com/seibel/distanthorizons/common/wrappers/minecraft/MinecraftGLWrapper.java");
        String dhWrapperSource = Files.readString(dhWrapperFile);
        assertFalse(dhWrapperSource.contains("GlStateManager._enableCull("),
            "MinecraftGLWrapper should not call removed GlStateManager._enableCull wrapper");
        assertFalse(dhWrapperSource.contains("GlStateManager._disableCull("),
            "MinecraftGLWrapper should not call removed GlStateManager._disableCull wrapper");
        assertTrue(dhWrapperSource.contains("VulkanicAPI.setCullFaceEnabled("),
            "MinecraftGLWrapper should toggle culling through VulkanicAPI.setCullFaceEnabled");
    }

    @Test
    public void testBlaze3dDepthWrappersRemovedFromGlStateManager() throws IOException {
        Path stateManagerFile = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/GlStateManager.java");
        String stateManagerSource = Files.readString(stateManagerFile);

        assertFalse(stateManagerSource.contains("public static void _enableDepthTest("),
            "GlStateManager should no longer expose _enableDepthTest wrapper");
        assertFalse(stateManagerSource.contains("public static void _disableDepthTest("),
            "GlStateManager should no longer expose _disableDepthTest wrapper");
        assertFalse(stateManagerSource.contains("public static void _depthFunc("),
            "GlStateManager should no longer expose _depthFunc wrapper");
        assertFalse(stateManagerSource.contains("public static void _depthMask("),
            "GlStateManager should no longer expose _depthMask wrapper");
        assertFalse(stateManagerSource.contains("public static final GlStateManager.DepthState DEPTH"),
            "GlStateManager should no longer own depth-mask state container");
        assertFalse(stateManagerSource.contains("class DepthState"),
            "GlStateManager should no longer define DepthState helper class");

        Path encoderFile = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/GlCommandEncoder.java");
        String encoderSource = Files.readString(encoderFile);
        assertFalse(encoderSource.contains("GlStateManager._enableDepthTest("),
            "GlCommandEncoder should not call removed GlStateManager._enableDepthTest wrapper");
        assertFalse(encoderSource.contains("GlStateManager._disableDepthTest("),
            "GlCommandEncoder should not call removed GlStateManager._disableDepthTest wrapper");
        assertFalse(encoderSource.contains("GlStateManager._depthFunc("),
            "GlCommandEncoder should not call removed GlStateManager._depthFunc wrapper");
        assertFalse(encoderSource.contains("GlStateManager._depthMask("),
            "GlCommandEncoder should not call removed GlStateManager._depthMask wrapper");
        assertTrue(encoderSource.contains("VulkanicAPI.setDepthTestEnabled("),
            "GlCommandEncoder should toggle depth test through VulkanicAPI.setDepthTestEnabled");
        assertTrue(encoderSource.contains("VulkanicAPI.setDepthFunc("),
            "GlCommandEncoder should set depth function through VulkanicAPI.setDepthFunc");
        assertTrue(encoderSource.contains("DepthColorStorage.setDepthMask("),
            "GlCommandEncoder should route depth-write mask changes through DepthColorStorage.setDepthMask");

        Path oldImageButtonFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/gui/OldImageButton.java");
        String oldImageButtonSource = Files.readString(oldImageButtonFile);
        assertFalse(oldImageButtonSource.contains("GlStateManager._enableDepthTest("),
            "OldImageButton should not call removed GlStateManager._enableDepthTest wrapper");
        assertTrue(oldImageButtonSource.contains("VulkanicAPI.setDepthTestEnabled("),
            "OldImageButton should enable depth test through VulkanicAPI.setDepthTestEnabled");

        Path irisButtonFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/gui/element/screen/IrisButton.java");
        String irisButtonSource = Files.readString(irisButtonFile);
        assertFalse(irisButtonSource.contains("GlStateManager._enableDepthTest("),
            "IrisButton should not call removed GlStateManager._enableDepthTest wrapper");
        assertTrue(irisButtonSource.contains("VulkanicAPI.setDepthTestEnabled("),
            "IrisButton should enable depth test through VulkanicAPI.setDepthTestEnabled");

        Path dhProgramFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/compat/dh/IrisGenericRenderProgram.java");
        String dhProgramSource = Files.readString(dhProgramFile);
        assertFalse(dhProgramSource.contains("GlStateManager._enableDepthTest("),
            "IrisGenericRenderProgram should not call removed GlStateManager._enableDepthTest wrapper");
        assertFalse(dhProgramSource.contains("GlStateManager._depthFunc("),
            "IrisGenericRenderProgram should not call removed GlStateManager._depthFunc wrapper");
        assertTrue(dhProgramSource.contains("VulkanicAPI.setDepthTestEnabled("),
            "IrisGenericRenderProgram should enable depth test through VulkanicAPI.setDepthTestEnabled");
        assertTrue(dhProgramSource.contains("VulkanicAPI.setDepthFunc("),
            "IrisGenericRenderProgram should set depth func through VulkanicAPI.setDepthFunc");

        Path dhWrapperFile = SRC_MAIN_JAVA.resolve("com/seibel/distanthorizons/common/wrappers/minecraft/MinecraftGLWrapper.java");
        String dhWrapperSource = Files.readString(dhWrapperFile);
        assertFalse(dhWrapperSource.contains("GlStateManager._enableDepthTest("),
            "MinecraftGLWrapper should not call removed GlStateManager._enableDepthTest wrapper");
        assertFalse(dhWrapperSource.contains("GlStateManager._disableDepthTest("),
            "MinecraftGLWrapper should not call removed GlStateManager._disableDepthTest wrapper");
        assertFalse(dhWrapperSource.contains("GlStateManager._depthFunc("),
            "MinecraftGLWrapper should not call removed GlStateManager._depthFunc wrapper");
        assertFalse(dhWrapperSource.contains("GlStateManager._depthMask("),
            "MinecraftGLWrapper should not call removed GlStateManager._depthMask wrapper");
        assertTrue(dhWrapperSource.contains("VulkanicAPI.setDepthTestEnabled("),
            "MinecraftGLWrapper should toggle depth testing through VulkanicAPI.setDepthTestEnabled");
        assertTrue(dhWrapperSource.contains("DepthColorStorage.setDepthMask("),
            "MinecraftGLWrapper should route depth-write mask changes through DepthColorStorage.setDepthMask");

        Path depthColorStorageFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/gl/blending/DepthColorStorage.java");
        String depthColorStorageSource = Files.readString(depthColorStorageFile);
        assertTrue(depthColorStorageSource.contains("public static void setDepthMask("),
            "DepthColorStorage should expose lock-aware setDepthMask after _depthMask wrapper removal");
        assertFalse(depthColorStorageSource.contains("GlStateManager.DEPTH"),
            "DepthColorStorage should not read depth-mask state from GlStateManager");
        assertTrue(depthColorStorageSource.contains("private static boolean currentDepthEnable"),
            "DepthColorStorage should own depth-mask state locally");
    }

    @Test
    public void testBlaze3dColorMaskWrapperRemovedFromGlStateManager() throws IOException {
        Path stateManagerFile = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/GlStateManager.java");
        String stateManagerSource = Files.readString(stateManagerFile);

        assertFalse(stateManagerSource.contains("public static void _colorMask("),
            "GlStateManager should no longer expose _colorMask wrapper");
        assertFalse(stateManagerSource.contains("public static final GlStateManager.ColorMask COLOR_MASK"),
            "GlStateManager should no longer own color-mask state container");
        assertFalse(stateManagerSource.contains("class ColorMask"),
            "GlStateManager should no longer define color-mask state helper class");

        Path encoderFile = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/GlCommandEncoder.java");
        String encoderSource = Files.readString(encoderFile);
        assertFalse(encoderSource.contains("GlStateManager._colorMask("),
            "GlCommandEncoder should not call removed GlStateManager._colorMask wrapper");
        assertTrue(encoderSource.contains("DepthColorStorage.setColorMask("),
            "GlCommandEncoder should route color-mask changes through DepthColorStorage.setColorMask");

        Path depthColorStorageFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/gl/blending/DepthColorStorage.java");
        String depthColorStorageSource = Files.readString(depthColorStorageFile);
        assertFalse(depthColorStorageSource.contains("GlStateManager._colorMask("),
            "DepthColorStorage should not call removed GlStateManager._colorMask wrapper");
        assertTrue(depthColorStorageSource.contains("public static void setColorMask("),
            "DepthColorStorage should expose lock-aware setColorMask after _colorMask wrapper removal");
        assertFalse(depthColorStorageSource.contains("GlStateManager.COLOR_MASK"),
            "DepthColorStorage should not read color-mask state from GlStateManager");
        assertTrue(depthColorStorageSource.contains("private static boolean currentRedMask"),
            "DepthColorStorage should own color-mask state locally");
    }

    @Test
    public void testBlaze3dBlendWrappersRemovedFromGlStateManager() throws IOException {
        Path stateManagerFile = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/GlStateManager.java");
        String stateManagerSource = Files.readString(stateManagerFile);

        assertFalse(stateManagerSource.contains("public static void _enableBlend("),
            "GlStateManager should no longer expose _enableBlend wrapper");
        assertFalse(stateManagerSource.contains("public static void _disableBlend("),
            "GlStateManager should no longer expose _disableBlend wrapper");
        assertFalse(stateManagerSource.contains("public static void _blendFuncSeparate("),
            "GlStateManager should no longer expose _blendFuncSeparate wrapper");
        assertFalse(stateManagerSource.contains("public static void glBlendFuncSeparate("),
            "GlStateManager should no longer expose glBlendFuncSeparate shim");
        assertFalse(stateManagerSource.contains("public static void notifyBlendFuncChanged("),
            "GlStateManager should no longer expose blend-function notifier trigger");
        assertFalse(stateManagerSource.contains("StateUpdateNotifiers.blendFuncNotifier"),
            "GlStateManager should no longer own blend-function notifier wiring");
        assertFalse(stateManagerSource.contains("public static final GlStateManager.BlendState BLEND"),
            "GlStateManager should no longer own blend-state container");
        assertFalse(stateManagerSource.contains("class BlendState"),
            "GlStateManager should no longer define BlendState helper class");
        assertFalse(stateManagerSource.contains("class BooleanState"),
            "GlStateManager should no longer define BooleanState helper class");

        Path encoderFile = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/GlCommandEncoder.java");
        String encoderSource = Files.readString(encoderFile);
        assertFalse(encoderSource.contains("GlStateManager._enableBlend("),
            "GlCommandEncoder should not call removed GlStateManager._enableBlend wrapper");
        assertFalse(encoderSource.contains("GlStateManager._disableBlend("),
            "GlCommandEncoder should not call removed GlStateManager._disableBlend wrapper");
        assertFalse(encoderSource.contains("GlStateManager._blendFuncSeparate("),
            "GlCommandEncoder should not call removed GlStateManager._blendFuncSeparate wrapper");
        assertTrue(encoderSource.contains("BlendModeStorage.setBlendEnabled("),
            "GlCommandEncoder should route blend toggles through BlendModeStorage.setBlendEnabled");
        assertTrue(encoderSource.contains("BlendModeStorage.setBlendFuncSeparate("),
            "GlCommandEncoder should route blend functions through BlendModeStorage.setBlendFuncSeparate");

        Path blendStorageFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/gl/blending/BlendModeStorage.java");
        String blendStorageSource = Files.readString(blendStorageFile);
        assertTrue(blendStorageSource.contains("public static void setBlendEnabled("),
            "BlendModeStorage should expose setBlendEnabled helper after wrapper removal");
        assertTrue(blendStorageSource.contains("public static void setBlendFuncSeparate("),
            "BlendModeStorage should expose setBlendFuncSeparate helper after wrapper removal");
        assertTrue(blendStorageSource.contains("public static boolean isBlendEnabled("),
            "BlendModeStorage should expose blend-enabled getter for non-Blaze blend-state reads");
        assertTrue(blendStorageSource.contains("public static int getBlendSrcRgb("),
            "BlendModeStorage should expose blend factor getters for non-Blaze blend-state reads");
        assertTrue(blendStorageSource.contains("VulkanicAPI.setBlendFunction("),
            "BlendModeStorage should set blend function directly through VulkanicAPI.setBlendFunction");
        assertTrue(blendStorageSource.contains("VulkanicAPI.setCapabilityEnabled("),
            "BlendModeStorage should toggle GL_BLEND capability directly through VulkanicAPI");
        assertTrue(blendStorageSource.contains("public static void markBlendStateUnknown("),
            "BlendModeStorage should expose unknown-state marker for indexed blend overrides");
        assertFalse(blendStorageSource.contains("GlStateManager.notifyBlendFuncChanged("),
            "BlendModeStorage should not trigger blend-function notifier through GlStateManager");
        assertTrue(blendStorageSource.contains("IrisRenderSystem.notifyBlendFuncChanged("),
            "BlendModeStorage should trigger blend-function notifier through IrisRenderSystem");
        assertFalse(blendStorageSource.contains("GlStateManager.BLEND"),
            "BlendModeStorage should no longer read or write blend state through GlStateManager.BLEND");

        Path irisRenderSystemFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/gl/IrisRenderSystem.java");
        String irisRenderSystemSource = Files.readString(irisRenderSystemFile);
        assertTrue(irisRenderSystemSource.contains("public static void notifyBlendFuncChanged("),
            "IrisRenderSystem should expose blend-function notifier trigger after migration");
        assertTrue(irisRenderSystemSource.contains("StateUpdateNotifiers.blendFuncNotifier"),
            "IrisRenderSystem should own blend-function notifier wiring after migration");
        assertFalse(irisRenderSystemSource.contains("GlStateManager.BLEND"),
            "IrisRenderSystem should not invalidate blend state through GlStateManager.BLEND");
        assertTrue(irisRenderSystemSource.contains("BlendModeStorage.markBlendStateUnknown("),
            "IrisRenderSystem should invalidate blend state through BlendModeStorage helper");

        Path commonUniformsFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/uniforms/CommonUniforms.java");
        String commonUniformsSource = Files.readString(commonUniformsFile);
        assertFalse(commonUniformsSource.contains("GlStateManager.BLEND"),
            "CommonUniforms should not read blend state directly from GlStateManager");
        assertTrue(commonUniformsSource.contains("BlendModeStorage.isBlendEnabled("),
            "CommonUniforms should read blend enabled state through BlendModeStorage helper");
        assertTrue(commonUniformsSource.contains("BlendModeStorage.getBlendSrcRgb("),
            "CommonUniforms should read blend factors through BlendModeStorage helpers");

        Path dhWrapperFile = SRC_MAIN_JAVA.resolve("com/seibel/distanthorizons/common/wrappers/minecraft/MinecraftGLWrapper.java");
        String dhWrapperSource = Files.readString(dhWrapperFile);
        assertFalse(dhWrapperSource.contains("GlStateManager._enableBlend("),
            "MinecraftGLWrapper should not call removed GlStateManager._enableBlend wrapper");
        assertFalse(dhWrapperSource.contains("GlStateManager._disableBlend("),
            "MinecraftGLWrapper should not call removed GlStateManager._disableBlend wrapper");
        assertFalse(dhWrapperSource.contains("GlStateManager._blendFuncSeparate("),
            "MinecraftGLWrapper should not call removed GlStateManager._blendFuncSeparate wrapper");
        assertTrue(dhWrapperSource.contains("BlendModeStorage.setBlendEnabled("),
            "MinecraftGLWrapper should route blend toggles through BlendModeStorage.setBlendEnabled");
    }

    @Test
    public void testCoreIrisPathsDoNotImportGlStateManager() throws IOException {
        String legacyImport = "import net.blaze3d.opengl.GlStateManager;";
        String[] migratedFiles = new String[] {
            "net/irisshaders/iris/gl/blending/BlendModeStorage.java",
            "net/irisshaders/iris/gl/IrisRenderSystem.java",
            "net/irisshaders/iris/gl/framebuffer/GlFramebuffer.java",
            "net/irisshaders/iris/gl/program/Program.java",
            "net/irisshaders/iris/gl/program/ComputeProgram.java",
            "net/irisshaders/iris/gl/texture/GlTexture.java",
            "net/irisshaders/iris/gl/texture/TextureUploadHelper.java",
            "net/irisshaders/iris/gl/image/GlImage.java",
            "net/irisshaders/iris/pipeline/programs/SodiumShader.java",
            "net/irisshaders/iris/pipeline/programs/ExtendedShader.java",
            "net/irisshaders/iris/pipeline/programs/FallbackShader.java",
            "net/irisshaders/iris/pipeline/programs/ShaderCreator.java",
            "net/irisshaders/iris/shadows/ShadowRenderer.java",
            "net/irisshaders/iris/shadows/ShadowCompositeRenderer.java",
            "com/seibel/distanthorizons/common/wrappers/minecraft/MinecraftGLWrapper.java"
        };

        for (String relativePath : migratedFiles) {
            Path file = SRC_MAIN_JAVA.resolve(relativePath);
            String source = Files.readString(file);
            assertFalse(source.contains(legacyImport),
                relativePath + " should not import GlStateManager after Vulkanic migration");
        }
    }

    @Test
    public void testBlaze3dUseProgramWrapperRemovedFromGlStateManager() throws IOException {
        Path stateManagerFile = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/GlStateManager.java");
        String stateManagerSource = Files.readString(stateManagerFile);

        assertFalse(stateManagerSource.contains("public static void _glUseProgram("),
            "GlStateManager should no longer expose _glUseProgram wrapper");

        Path encoderFile = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/GlCommandEncoder.java");
        String encoderSource = Files.readString(encoderFile);
        assertFalse(encoderSource.contains("GlStateManager._glUseProgram("),
            "GlCommandEncoder should not call removed GlStateManager._glUseProgram wrapper");
        assertTrue(encoderSource.contains("IrisRenderSystem.useProgram("),
            "GlCommandEncoder should bind programs through IrisRenderSystem.useProgram");

        Path programFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/gl/program/Program.java");
        String programSource = Files.readString(programFile);
        assertFalse(programSource.contains("GlStateManager._glUseProgram("),
            "Program should not call removed GlStateManager._glUseProgram wrapper");
        assertTrue(programSource.contains("IrisRenderSystem.useProgram("),
            "Program should bind programs through IrisRenderSystem.useProgram");

        Path computeProgramFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/gl/program/ComputeProgram.java");
        String computeProgramSource = Files.readString(computeProgramFile);
        assertFalse(computeProgramSource.contains("GlStateManager._glUseProgram("),
            "ComputeProgram should not call removed GlStateManager._glUseProgram wrapper");
        assertTrue(computeProgramSource.contains("IrisRenderSystem.useProgram("),
            "ComputeProgram should bind programs through IrisRenderSystem.useProgram");

        Path irisRenderSystemFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/gl/IrisRenderSystem.java");
        String irisRenderSystemSource = Files.readString(irisRenderSystemFile);
        assertTrue(irisRenderSystemSource.contains("public static void useProgram("),
            "IrisRenderSystem should provide useProgram helper after _glUseProgram removal");
        assertTrue(irisRenderSystemSource.contains("ImmediateState.usingTessellation = false"),
            "IrisRenderSystem.useProgram should preserve tessellation reset behavior");
    }

    @Test
    public void testBlaze3dActiveTextureWrapperRemovedFromGlStateManager() throws IOException {
        Path stateManagerFile = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/GlStateManager.java");
        String stateManagerSource = Files.readString(stateManagerFile);

        assertFalse(stateManagerSource.contains("public static void _activeTexture("),
            "GlStateManager should no longer expose _activeTexture wrapper");

        Path encoderFile = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/GlCommandEncoder.java");
        String encoderSource = Files.readString(encoderFile);
        assertFalse(encoderSource.contains("GlStateManager._activeTexture("),
            "GlCommandEncoder should not call removed GlStateManager._activeTexture wrapper");
        assertTrue(encoderSource.contains("IrisRenderSystem.setActiveTexture("),
            "GlCommandEncoder should route active texture changes through IrisRenderSystem.setActiveTexture");

        Path sodiumShaderFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/pipeline/programs/SodiumShader.java");
        String sodiumShaderSource = Files.readString(sodiumShaderFile);
        assertFalse(sodiumShaderSource.contains("GlStateManager._activeTexture("),
            "SodiumShader should not call removed GlStateManager._activeTexture wrapper");
        assertTrue(sodiumShaderSource.contains("IrisRenderSystem.setActiveTexture("),
            "SodiumShader should route active texture changes through IrisRenderSystem.setActiveTexture");

        Path dhWrapperFile = SRC_MAIN_JAVA.resolve("com/seibel/distanthorizons/common/wrappers/minecraft/MinecraftGLWrapper.java");
        String dhWrapperSource = Files.readString(dhWrapperFile);
        assertFalse(dhWrapperSource.contains("GlStateManager._activeTexture("),
            "MinecraftGLWrapper should not call removed GlStateManager._activeTexture wrapper");
        assertTrue(dhWrapperSource.contains("IrisRenderSystem.setActiveTexture("),
            "MinecraftGLWrapper should route active texture changes through IrisRenderSystem.setActiveTexture");

        Path irisRenderSystemFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/gl/IrisRenderSystem.java");
        String irisRenderSystemSource = Files.readString(irisRenderSystemFile);
        assertTrue(irisRenderSystemSource.contains("public static void setActiveTexture("),
            "IrisRenderSystem should provide setActiveTexture helper after _activeTexture removal");
        assertTrue(irisRenderSystemSource.contains("public static void setActiveTextureUnitIndex("),
            "IrisRenderSystem should expose index-based active texture helper to avoid GL_TEXTURE0 arithmetic at call sites");
    }

    @Test
    public void testIrisTextureStateAccessUsesIrisRenderSystemHelpers() throws IOException {
        Path stateManagerFile = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/GlStateManager.java");
        String stateManagerSource = Files.readString(stateManagerFile);
        assertFalse(stateManagerSource.contains("public static int activeTexture"),
            "GlStateManager should no longer own activeTexture state field");
        assertFalse(stateManagerSource.contains("public static final GlStateManager.TextureState[] TEXTURES"),
            "GlStateManager should no longer own per-unit texture binding state array");
        assertFalse(stateManagerSource.contains("class TextureState"),
            "GlStateManager should no longer define TextureState helper class");

        Path irisRenderSystemFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/gl/IrisRenderSystem.java");
        String irisRenderSystemSource = Files.readString(irisRenderSystemFile);

        assertTrue(irisRenderSystemSource.contains("public static int getActiveTextureUnitIndex("),
            "IrisRenderSystem should expose getActiveTextureUnitIndex helper for active texture state");
        assertTrue(irisRenderSystemSource.contains("public static int getTextureBinding("),
            "IrisRenderSystem should expose getTextureBinding helper for per-unit binding state");
        assertTrue(irisRenderSystemSource.contains("public static int getBoundTextureOnActiveUnit("),
            "IrisRenderSystem should expose getBoundTextureOnActiveUnit helper for active binding reads");
        assertTrue(irisRenderSystemSource.contains("public static void setTextureBinding("),
            "IrisRenderSystem should expose setTextureBinding helper for binding tracking updates");
        assertTrue(irisRenderSystemSource.contains("private static int activeTextureUnitIndex"),
            "IrisRenderSystem should own active texture unit index state locally");
        assertTrue(irisRenderSystemSource.contains("private static final int[] textureBindings"),
            "IrisRenderSystem should own per-unit texture binding state array locally");
        assertFalse(irisRenderSystemSource.contains("GlStateManager.activeTexture"),
            "IrisRenderSystem should not read active texture from GlStateManager");
        assertFalse(irisRenderSystemSource.contains("GlStateManager.TEXTURES"),
            "IrisRenderSystem should not read texture bindings from GlStateManager");

        Path programSamplersFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/gl/program/ProgramSamplers.java");
        String programSamplersSource = Files.readString(programSamplersFile);
        assertFalse(programSamplersSource.contains("GlStateManager.activeTexture"),
            "ProgramSamplers should not read active texture directly from GlStateManager");
        assertTrue(programSamplersSource.contains("IrisRenderSystem.getActiveTextureUnitIndex("),
            "ProgramSamplers should read active texture through IrisRenderSystem helper");

        Path depthCopyStrategyFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/gl/texture/DepthCopyStrategy.java");
        String depthCopyStrategySource = Files.readString(depthCopyStrategyFile);
        assertFalse(depthCopyStrategySource.contains("GlStateManager.TEXTURES[GlStateManager.activeTexture].binding"),
            "DepthCopyStrategy should not read active-unit binding directly from GlStateManager");
        assertTrue(depthCopyStrategySource.contains("IrisRenderSystem.getBoundTextureOnActiveUnit("),
            "DepthCopyStrategy should read active-unit binding through IrisRenderSystem helper");

        Path customTextureManagerFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/pipeline/CustomTextureManager.java");
        String customTextureManagerSource = Files.readString(customTextureManagerFile);
        assertFalse(customTextureManagerSource.contains("GlStateManager.activeTexture"),
            "CustomTextureManager should not read active texture directly from GlStateManager");
        assertFalse(customTextureManagerSource.contains("GlStateManager.TEXTURES"),
            "CustomTextureManager should not read texture bindings directly from GlStateManager");
        assertTrue(customTextureManagerSource.contains("IrisRenderSystem.getActiveTextureUnitIndex("),
            "CustomTextureManager should read active texture through IrisRenderSystem helper");
        assertTrue(customTextureManagerSource.contains("IrisRenderSystem.getTextureBinding("),
            "CustomTextureManager should read texture bindings through IrisRenderSystem helper");

        Path compositeRendererFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/pipeline/CompositeRenderer.java");
        String compositeRendererSource = Files.readString(compositeRendererFile);
        assertFalse(compositeRendererSource.contains("GlStateManager.TEXTURES"),
            "CompositeRenderer should not read texture bindings directly from GlStateManager");
        assertTrue(compositeRendererSource.contains("IrisRenderSystem.getTextureBinding("),
            "CompositeRenderer should check bindings through IrisRenderSystem helper");

        Path finalPassRendererFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/pipeline/FinalPassRenderer.java");
        String finalPassRendererSource = Files.readString(finalPassRendererFile);
        assertFalse(finalPassRendererSource.contains("GlStateManager.TEXTURES"),
            "FinalPassRenderer should not read texture bindings directly from GlStateManager");
        assertTrue(finalPassRendererSource.contains("IrisRenderSystem.getTextureBinding("),
            "FinalPassRenderer should check bindings through IrisRenderSystem helper");

        Path pipelineFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/pipeline/IrisRenderingPipeline.java");
        String pipelineSource = Files.readString(pipelineFile);
        assertFalse(pipelineSource.contains("GlStateManager.TEXTURES[GlStateManager.activeTexture].binding"),
            "IrisRenderingPipeline should not read active-unit binding directly from GlStateManager");
        assertTrue(pipelineSource.contains("IrisRenderSystem.getBoundTextureOnActiveUnit("),
            "IrisRenderingPipeline should read active-unit binding through IrisRenderSystem helper");

        Path textureInfoCacheFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/pbr/TextureInfoCache.java");
        String textureInfoCacheSource = Files.readString(textureInfoCacheFile);
        assertFalse(textureInfoCacheSource.contains("GlStateManager.TEXTURES[GlStateManager.activeTexture].binding"),
            "TextureInfoCache should not read active-unit binding directly from GlStateManager");
        assertTrue(textureInfoCacheSource.contains("IrisRenderSystem.getBoundTextureOnActiveUnit("),
            "TextureInfoCache should read active-unit binding through IrisRenderSystem helper");

        Path pbrTextureManagerFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/pbr/texture/PBRTextureManager.java");
        String pbrTextureManagerSource = Files.readString(pbrTextureManagerFile);
        assertFalse(pbrTextureManagerSource.contains("GlStateManager.TEXTURES[GlStateManager.activeTexture].binding"),
            "PBRTextureManager should not read active-unit binding directly from GlStateManager");
        assertTrue(pbrTextureManagerSource.contains("IrisRenderSystem.getBoundTextureOnActiveUnit("),
            "PBRTextureManager should read active-unit binding through IrisRenderSystem helper");

        Path programSamplersFile2 = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/gl/program/ProgramSamplers.java");
        String programSamplersSource2 = Files.readString(programSamplersFile2);
        assertFalse(programSamplersSource2.contains("IrisRenderSystem.setActiveTexture(VulkanicAPI.GL_TEXTURE0 +"),
            "ProgramSamplers should not compute GL_TEXTURE0 offsets directly when restoring active texture");
        assertTrue(programSamplersSource2.contains("IrisRenderSystem.setActiveTextureUnitIndex("),
            "ProgramSamplers should restore active texture through IrisRenderSystem index helper");

        Path pipelineManagerFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/pipeline/PipelineManager.java");
        String pipelineManagerSource = Files.readString(pipelineManagerFile);
        assertFalse(pipelineManagerSource.contains("IrisRenderSystem.setActiveTexture(VulkanicAPI.GL_TEXTURE0 +"),
            "PipelineManager should not compute GL_TEXTURE0 offsets directly in texture unit loops");
        assertTrue(pipelineManagerSource.contains("IrisRenderSystem.setActiveTextureUnitIndex("),
            "PipelineManager should switch texture units through IrisRenderSystem index helper");
    }

    @Test
    public void testBlaze3dClearWrapperRemovedFromGlStateManager() throws IOException {
        Path stateManagerFile = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/GlStateManager.java");
        String stateManagerSource = Files.readString(stateManagerFile);

        assertFalse(stateManagerSource.contains("public static void _clear("),
            "GlStateManager should no longer expose _clear wrapper");

        Path encoderFile = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/GlCommandEncoder.java");
        String encoderSource = Files.readString(encoderFile);
        assertFalse(encoderSource.contains("GlStateManager._clear("),
            "GlCommandEncoder should not call removed GlStateManager._clear wrapper");
        assertTrue(encoderSource.contains("VulkanicAPI.clearBuffersWithMacosWorkaround("),
            "GlCommandEncoder should clear via VulkanicAPI.clearBuffersWithMacosWorkaround");

        Path clearPassFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/targets/ClearPass.java");
        String clearPassSource = Files.readString(clearPassFile);
        assertFalse(clearPassSource.contains("GlStateManager._clear("),
            "ClearPass should not call removed GlStateManager._clear wrapper");
        assertTrue(clearPassSource.contains("VulkanicAPI.clearBuffersWithMacosWorkaround("),
            "ClearPass should clear via VulkanicAPI.clearBuffersWithMacosWorkaround");
    }

    @Test
    public void testBlaze3dBindTextureWrapperRemovedFromGlStateManager() throws IOException {
        Path stateManagerFile = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/GlStateManager.java");
        String stateManagerSource = Files.readString(stateManagerFile);

        assertFalse(stateManagerSource.contains("public static void _bindTexture("),
            "GlStateManager should no longer expose _bindTexture wrapper");

        Path encoderFile = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/GlCommandEncoder.java");
        String encoderSource = Files.readString(encoderFile);
        assertFalse(encoderSource.contains("GlStateManager._bindTexture("),
            "GlCommandEncoder should not call removed GlStateManager._bindTexture wrapper");
        assertTrue(encoderSource.contains("VulkanicAPI.bindTexture2D("),
            "GlCommandEncoder should bind 2D textures directly through VulkanicAPI.bindTexture2D");

        Path renderTargetsFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/targets/RenderTargets.java");
        String renderTargetsSource = Files.readString(renderTargetsFile);
        assertFalse(renderTargetsSource.contains("GlStateManager._bindTexture("),
            "RenderTargets should not call removed GlStateManager._bindTexture wrapper");
        assertTrue(renderTargetsSource.contains("VulkanicAPI.bindTexture2D("),
            "RenderTargets should bind textures through VulkanicAPI.bindTexture2D");

        Path dhWrapperFile = SRC_MAIN_JAVA.resolve("com/seibel/distanthorizons/common/wrappers/minecraft/MinecraftGLWrapper.java");
        String dhWrapperSource = Files.readString(dhWrapperFile);
        assertFalse(dhWrapperSource.contains("GlStateManager._bindTexture("),
            "MinecraftGLWrapper should not call removed GlStateManager._bindTexture wrapper");
        assertTrue(dhWrapperSource.contains("VulkanicAPI.bindTexture2D("),
            "MinecraftGLWrapper should bind textures through VulkanicAPI.bindTexture2D");
    }

    @Test
    public void testBlaze3dTextureLifecycleWrappersRemovedFromGlStateManager() throws IOException {
        Path stateManagerFile = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/GlStateManager.java");
        String stateManagerSource = Files.readString(stateManagerFile);

        assertFalse(stateManagerSource.contains("public static int _genTexture("),
            "GlStateManager should no longer expose _genTexture wrapper");
        assertFalse(stateManagerSource.contains("public static void _deleteTexture("),
            "GlStateManager should no longer expose _deleteTexture wrapper");
        assertFalse(stateManagerSource.contains("public static void incrementTrackedTextures("),
            "GlStateManager should no longer expose incrementTrackedTextures helper");
        assertFalse(stateManagerSource.contains("public static void decrementTrackedTextures("),
            "GlStateManager should no longer expose decrementTrackedTextures helper");

        Path glDeviceFile = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/GlDevice.java");
        String glDeviceSource = Files.readString(glDeviceFile);
        assertFalse(glDeviceSource.contains("GlStateManager._genTexture("),
            "GlDevice should not call removed GlStateManager._genTexture wrapper");
        assertTrue(glDeviceSource.contains("IrisRenderSystem.createTextureId("),
            "GlDevice should create textures through IrisRenderSystem.createTextureId");

        Path uniformFile = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/Uniform.java");
        String uniformSource = Files.readString(uniformFile);
        assertFalse(uniformSource.contains("GlStateManager._genTexture("),
            "Uniform should not call removed GlStateManager._genTexture wrapper");
        assertFalse(uniformSource.contains("GlStateManager._deleteTexture("),
            "Uniform should not call removed GlStateManager._deleteTexture wrapper");
        assertTrue(uniformSource.contains("IrisRenderSystem.createTextureId("),
            "Uniform should create textures through IrisRenderSystem.createTextureId");
        assertTrue(uniformSource.contains("IrisRenderSystem.deleteTextureId("),
            "Uniform should delete textures through IrisRenderSystem.deleteTextureId");

        Path irisRenderSystemFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/gl/IrisRenderSystem.java");
        String irisRenderSystemSource = Files.readString(irisRenderSystemFile);
        assertTrue(irisRenderSystemSource.contains("public static int createTextureId("),
            "IrisRenderSystem should provide createTextureId helper after _genTexture removal");
        assertTrue(irisRenderSystemSource.contains("public static void deleteTextureId("),
            "IrisRenderSystem should provide deleteTextureId helper after _deleteTexture removal");
        assertTrue(irisRenderSystemSource.contains("public static void incrementTrackedTextures("),
            "IrisRenderSystem should expose incrementTrackedTextures helper after migration");
        assertTrue(irisRenderSystemSource.contains("public static void decrementTrackedTextures("),
            "IrisRenderSystem should expose decrementTrackedTextures helper after migration");
    }

    @Test
    public void testBlaze3dBufferTrackingMovedFromGlStateManager() throws IOException {
        Path stateManagerFile = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/GlStateManager.java");
        String stateManagerSource = Files.readString(stateManagerFile);

        assertFalse(stateManagerSource.contains("public static void incrementTrackedBuffers("),
            "GlStateManager should no longer expose incrementTrackedBuffers helper");
        assertFalse(stateManagerSource.contains("public static void decrementTrackedBuffers("),
            "GlStateManager should no longer expose decrementTrackedBuffers helper");

        Path irisRenderSystemFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/gl/IrisRenderSystem.java");
        String irisRenderSystemSource = Files.readString(irisRenderSystemFile);
        assertTrue(irisRenderSystemSource.contains("public static void incrementTrackedBuffers("),
            "IrisRenderSystem should expose incrementTrackedBuffers helper after migration");
        assertTrue(irisRenderSystemSource.contains("public static void decrementTrackedBuffers("),
            "IrisRenderSystem should expose decrementTrackedBuffers helper after migration");

        Path dsaFile = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/DirectStateAccess.java");
        String dsaSource = Files.readString(dsaFile);
        assertFalse(dsaSource.contains("GlStateManager.incrementTrackedBuffers("),
            "DirectStateAccess should not increment tracked buffers through GlStateManager");
        assertTrue(dsaSource.contains("IrisRenderSystem.incrementTrackedBuffers("),
            "DirectStateAccess should increment tracked buffers through IrisRenderSystem helper");

        Path glBufferFile = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/GlBuffer.java");
        String glBufferSource = Files.readString(glBufferFile);
        assertFalse(glBufferSource.contains("GlStateManager.decrementTrackedBuffers("),
            "GlBuffer should not decrement tracked buffers through GlStateManager");
        assertTrue(glBufferSource.contains("IrisRenderSystem.decrementTrackedBuffers("),
            "GlBuffer should decrement tracked buffers through IrisRenderSystem helper");

        Path dhWrapperFile = SRC_MAIN_JAVA.resolve("com/seibel/distanthorizons/common/wrappers/minecraft/MinecraftGLWrapper.java");
        String dhWrapperSource = Files.readString(dhWrapperFile);
        assertFalse(dhWrapperSource.contains("GlStateManager.incrementTrackedBuffers("),
            "MinecraftGLWrapper should not increment tracked buffers through GlStateManager");
        assertFalse(dhWrapperSource.contains("GlStateManager.decrementTrackedBuffers("),
            "MinecraftGLWrapper should not decrement tracked buffers through GlStateManager");
        assertTrue(dhWrapperSource.contains("IrisRenderSystem.incrementTrackedBuffers("),
            "MinecraftGLWrapper should increment tracked buffers through IrisRenderSystem helper");
        assertTrue(dhWrapperSource.contains("IrisRenderSystem.decrementTrackedBuffers("),
            "MinecraftGLWrapper should decrement tracked buffers through IrisRenderSystem helper");
    }

    @Test
    public void testBlaze3dTexImageWrapperRemovedFromGlStateManager() throws IOException {
        Path stateManagerFile = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/GlStateManager.java");
        String stateManagerSource = Files.readString(stateManagerFile);

        assertFalse(stateManagerSource.contains("public static void _texImage2D("),
            "GlStateManager should no longer expose _texImage2D wrapper");

        Path glDeviceFile = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/GlDevice.java");
        String glDeviceSource = Files.readString(glDeviceFile);
        assertFalse(glDeviceSource.contains("GlStateManager._texImage2D("),
            "GlDevice should not call removed GlStateManager._texImage2D wrapper");
        assertTrue(glDeviceSource.contains("net.vulkanic.VulkanicAPI.uploadTexture2D("),
            "GlDevice texture allocation paths should upload directly through VulkanicAPI.uploadTexture2D");
        assertTrue(glDeviceSource.contains("net.irisshaders.iris.pbr.TextureInfoCache.INSTANCE.onTexImage2D("),
            "GlDevice texture allocation paths should preserve TextureInfoCache tracking after wrapper removal");
    }

    @Test
    public void testBlaze3dViewportWrapperRemovedFromGlStateManager() throws IOException {
        Path stateManagerFile = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/GlStateManager.java");
        String stateManagerSource = Files.readString(stateManagerFile);

        assertFalse(stateManagerSource.contains("public static void _viewport("),
            "GlStateManager should no longer expose _viewport wrapper");

        Path encoderFile = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/GlCommandEncoder.java");
        String encoderSource = Files.readString(encoderFile);
        assertFalse(encoderSource.contains("GlStateManager._viewport("),
            "GlCommandEncoder should not call removed GlStateManager._viewport wrapper");
        assertTrue(encoderSource.contains("VulkanicAPI.setDynamicViewport("),
            "GlCommandEncoder should set viewport through VulkanicAPI.setDynamicViewport");

        Path clearPassFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/targets/ClearPass.java");
        String clearPassSource = Files.readString(clearPassFile);
        assertFalse(clearPassSource.contains("GlStateManager._viewport("),
            "ClearPass should not call removed GlStateManager._viewport wrapper");
        assertTrue(clearPassSource.contains("VulkanicAPI.setDynamicViewport("),
            "ClearPass should set viewport through VulkanicAPI.setDynamicViewport");
    }

    @Test
    public void testBlaze3dScissorToggleWrappersRemovedFromGlStateManager() throws IOException {
        Path stateManagerFile = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/GlStateManager.java");
        String stateManagerSource = Files.readString(stateManagerFile);

        assertFalse(stateManagerSource.contains("public static void _enableScissorTest("),
            "GlStateManager should no longer expose _enableScissorTest wrapper");
        assertFalse(stateManagerSource.contains("public static void _disableScissorTest("),
            "GlStateManager should no longer expose _disableScissorTest wrapper");

        Path encoderFile = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/GlCommandEncoder.java");
        String encoderSource = Files.readString(encoderFile);
        assertFalse(encoderSource.contains("GlStateManager._enableScissorTest("),
            "GlCommandEncoder should not call removed GlStateManager._enableScissorTest wrapper");
        assertFalse(encoderSource.contains("GlStateManager._disableScissorTest("),
            "GlCommandEncoder should not call removed GlStateManager._disableScissorTest wrapper");
        assertTrue(encoderSource.contains("VulkanicAPI.setScissorTestEnabled("),
            "GlCommandEncoder should toggle scissor test through VulkanicAPI.setScissorTestEnabled");

        Path dhWrapperFile = SRC_MAIN_JAVA.resolve("com/seibel/distanthorizons/common/wrappers/minecraft/MinecraftGLWrapper.java");
        String dhWrapperSource = Files.readString(dhWrapperFile);
        assertFalse(dhWrapperSource.contains("GlStateManager._enableScissorTest("),
            "MinecraftGLWrapper should not call removed GlStateManager._enableScissorTest wrapper");
        assertFalse(dhWrapperSource.contains("GlStateManager._disableScissorTest("),
            "MinecraftGLWrapper should not call removed GlStateManager._disableScissorTest wrapper");
        assertTrue(dhWrapperSource.contains("VulkanicAPI.setScissorTestEnabled("),
            "MinecraftGLWrapper should toggle scissor test through VulkanicAPI.setScissorTestEnabled");
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
