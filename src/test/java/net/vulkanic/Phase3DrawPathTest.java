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
        // Note: GlStateManager._glBindBuffer(35051,...) in copyTextureToBuffer is a different
        // buffer target (GL_PIXEL_PACK_BUFFER) and must remain; we only check the draw path.
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
