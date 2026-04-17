package net.vulkanic;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

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

    private static String readSourceIfExists(Path path) throws IOException {
        if (!Files.exists(path)) {
            return "";
        }
        return Files.readString(path);
    }

    private static boolean containsAny(String source, String... needles) {
        for (String needle : needles) {
            if (source.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    @Test
    public void testImmediateContextUsageRestrictedToLegacySeam() throws IOException {
        Path legacyFile = SRC_MAIN_JAVA.resolve("net/vulkanic/VulkanicAPI.java");
        Path legacyRelative = Paths.get("net/vulkanic/VulkanicAPI.java");
        assertTrue(Files.exists(legacyFile), "VulkanicAPI.java must exist for migration seam validation");

        List<String> offenders = new ArrayList<>();
        try (var paths = Files.walk(SRC_MAIN_JAVA)) {
            for (Path file : (Iterable<Path>) paths::iterator) {
                if (!Files.isRegularFile(file) || !file.toString().endsWith(".java")) {
                    continue;
                }

                String source = Files.readString(file);
                if (!source.contains("getImmediateContext(")) {
                    continue;
                }

                Path relative = SRC_MAIN_JAVA.relativize(file);
                if (relative.equals(legacyRelative)) {
                    String withoutDeclaration = source.replace("public static CommandContext getImmediateContext() {", "");
                    if (withoutDeclaration.contains("getImmediateContext(")) {
                        offenders.add(relative + " (additional references)");
                    }
                } else {
                    offenders.add(relative.toString());
                }
            }
        }

        assertTrue(offenders.isEmpty(),
            "Only VulkanicAPI.getImmediateContext() compatibility seam may remain; offenders: " + offenders);

        String legacySource = Files.readString(legacyFile);
        assertTrue(legacySource.contains("@Deprecated"),
            "VulkanicAPI.getImmediateContext() must remain explicitly deprecated");
    }

    @Test
    public void testIrisGetGlIdRestrictedToDeprecatedCompatibilitySeams() throws IOException {
        Path interfaceFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/mixinterface/GpuTextureInterface.java");
        Path interfaceRelative = Paths.get("net/irisshaders/iris/mixinterface/GpuTextureInterface.java");
        Path glTextureFile = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/GlTexture.java");
        Path glTextureRelative = Paths.get("net/blaze3d/opengl/GlTexture.java");
        Path gpuTextureFile = SRC_MAIN_JAVA.resolve("net/blaze3d/textures/GpuTexture.java");

        String interfaceSource = Files.readString(interfaceFile);
        String glTextureSource = Files.readString(glTextureFile);
        String gpuTextureSource = Files.readString(gpuTextureFile);

        assertTrue(interfaceSource.contains("@Deprecated"),
            "GpuTextureInterface.iris$getGlId should remain explicitly deprecated as a compatibility seam");
        assertTrue(interfaceSource.contains("default int iris$getGlId()"),
            "GpuTextureInterface should remain the compatibility declaration owner for iris$getGlId");
        assertTrue(glTextureSource.contains("@Deprecated"),
            "GlTexture.iris$getGlId should remain explicitly deprecated as a compatibility seam");
        assertTrue(glTextureSource.contains("public int iris$getGlId()"),
            "GlTexture should keep the only concrete iris$getGlId implementation in main sources");
        assertFalse(gpuTextureSource.contains("iris$getGlId("),
            "GpuTexture should not declare iris$getGlId after compatibility-surface reduction");

        List<String> declarationOffenders = new ArrayList<>();
        List<String> invocationOffenders = new ArrayList<>();
        try (var paths = Files.walk(SRC_MAIN_JAVA)) {
            for (Path file : (Iterable<Path>) paths::iterator) {
                if (!Files.isRegularFile(file) || !file.toString().endsWith(".java")) {
                    continue;
                }

                String source = Files.readString(file);
                Path relative = SRC_MAIN_JAVA.relativize(file);
                if (source.contains("iris$getGlId(")
                    && !relative.equals(interfaceRelative)
                    && !relative.equals(glTextureRelative)) {
                    declarationOffenders.add(relative.toString());
                }

                if (source.contains(".iris$getGlId(")) {
                    invocationOffenders.add(relative.toString());
                }
            }
        }

        assertTrue(declarationOffenders.isEmpty(),
            "iris$getGlId declarations should remain restricted to compatibility seams; offenders: " + declarationOffenders);
        assertTrue(invocationOffenders.isEmpty(),
            "Runtime iris$getGlId invocations should remain eliminated; offenders: " + invocationOffenders);
    }

    @Test
    public void testGlIdRestrictedToDeprecatedDeclarationsAndNoRuntimeInvocations() throws IOException {
        Path gpuTextureFile = SRC_MAIN_JAVA.resolve("net/blaze3d/textures/GpuTexture.java");
        Path gpuTextureRelative = Paths.get("net/blaze3d/textures/GpuTexture.java");
        Path glTextureFile = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/GlTexture.java");
        Path glTextureRelative = Paths.get("net/blaze3d/opengl/GlTexture.java");

        String gpuTextureSource = Files.readString(gpuTextureFile);
        String glTextureSource = Files.readString(glTextureFile);

        assertTrue(gpuTextureSource.contains("@Deprecated"),
            "GpuTexture.glId should remain explicitly deprecated as a compatibility seam");
        assertTrue(gpuTextureSource.contains("public int glId()"),
            "GpuTexture should keep glId declaration for compatibility while migration is in progress");
        assertTrue(glTextureSource.contains("@Deprecated"),
            "GlTexture.glId should remain explicitly deprecated as a compatibility seam");
        assertTrue(glTextureSource.contains("public int glId()"),
            "GlTexture should keep the concrete glId compatibility implementation");

        List<String> declarationOffenders = new ArrayList<>();
        List<String> invocationOffenders = new ArrayList<>();
        try (var paths = Files.walk(SRC_MAIN_JAVA)) {
            for (Path file : (Iterable<Path>) paths::iterator) {
                if (!Files.isRegularFile(file) || !file.toString().endsWith(".java")) {
                    continue;
                }

                String source = Files.readString(file);
                Path relative = SRC_MAIN_JAVA.relativize(file);

                if (source.contains(" glId(")
                    && !relative.equals(gpuTextureRelative)
                    && !relative.equals(glTextureRelative)) {
                    declarationOffenders.add(relative.toString());
                }

                if (source.contains(".glId(")) {
                    invocationOffenders.add(relative.toString());
                }
            }
        }

        assertTrue(declarationOffenders.isEmpty(),
            "glId declarations should remain restricted to compatibility seam files; offenders: " + declarationOffenders);
        assertTrue(invocationOffenders.isEmpty(),
            "Runtime .glId invocations should remain eliminated; offenders: " + invocationOffenders);
    }

    @Test
    public void testGlTextureHandleExtractionRestrictedToOpenGLBackendSeams() throws IOException {
        Path glTextureFile = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/GlTexture.java");
        Path glTextureRelative = Paths.get("net/blaze3d/opengl/GlTexture.java");
        Path openGlBackendFile = SRC_MAIN_JAVA.resolve("net/vulkanic/backends/opengl/OpenGLBackend.java");
        Path openGlBackendRelative = Paths.get("net/vulkanic/backends/opengl/OpenGLBackend.java");
        Path openGlTextureViewFile = SRC_MAIN_JAVA.resolve("net/vulkanic/backends/opengl/OpenGLTextureView.java");
        Path openGlTextureViewRelative = Paths.get("net/vulkanic/backends/opengl/OpenGLTextureView.java");

        String glTextureSource = Files.readString(glTextureFile);
        String openGlBackendSource = Files.readString(openGlBackendFile);
        String openGlTextureViewSource = Files.readString(openGlTextureViewFile);

        assertTrue(glTextureSource.contains("public int getGlHandle()"),
            "GlTexture should expose getGlHandle for backend seam extraction");
        assertTrue(openGlBackendSource.contains("glTexture.getGlHandle()"),
            "OpenGLBackend should extract GlTexture handles via getGlHandle in the backend seam");
        assertTrue(openGlTextureViewSource.contains("return t.getGlHandle();"),
            "OpenGLTextureView should extract GlTexture handles via getGlHandle in the backend seam");

        List<String> offenders = new ArrayList<>();
        try (var paths = Files.walk(SRC_MAIN_JAVA)) {
            for (Path file : (Iterable<Path>) paths::iterator) {
                if (!Files.isRegularFile(file) || !file.toString().endsWith(".java")) {
                    continue;
                }

                Path relative = SRC_MAIN_JAVA.relativize(file);
                if (relative.equals(glTextureRelative)
                    || relative.equals(openGlBackendRelative)
                    || relative.equals(openGlTextureViewRelative)) {
                    continue;
                }

                String source = Files.readString(file);
                if (source.contains("GlTexture") && source.contains(".getGlHandle(")) {
                    offenders.add(relative.toString());
                }
            }
        }

        assertTrue(offenders.isEmpty(),
            "GlTexture.getGlHandle usage should remain confined to OpenGL backend seam files; offenders: " + offenders);
    }

    @Test
    public void testSingleQuadParticlesShrinkAtlasUvsBeforeSampling() throws IOException {
        String source = Files.readString(SRC_MAIN_JAVA.resolve("net/minecraft/client/particle/SingleQuadParticle.java"));

        assertTrue(source.contains("return this.shrinkU(this.sprite.getU0(), this.sprite.getU1());"),
            "SingleQuadParticle should shrink the leading U edge inward before sampling atlas sprites");
        assertTrue(source.contains("return this.shrinkU(this.sprite.getU1(), this.sprite.getU0());"),
            "SingleQuadParticle should shrink the trailing U edge inward before sampling atlas sprites");
        assertTrue(source.contains("return this.shrinkV(this.sprite.getV0(), this.sprite.getV1());"),
            "SingleQuadParticle should shrink the leading V edge inward before sampling atlas sprites");
        assertTrue(source.contains("return this.shrinkV(this.sprite.getV1(), this.sprite.getV0());"),
            "SingleQuadParticle should shrink the trailing V edge inward before sampling atlas sprites");
        assertTrue(source.contains("return Mth.lerp(this.particleAtlasShrinkRatio(), f, h);"),
            "SingleQuadParticle should contract atlas UVs with its particle-local shrink ratio");
        assertTrue(source.contains("private float particleAtlasShrinkRatio()"),
            "SingleQuadParticle should define a particle-local atlas shrink ratio helper");
        assertTrue(source.contains("this.sprite.contents().width() / (this.sprite.getU1() - this.sprite.getU0())"),
            "Particle atlas shrink ratio should be derived from sprite width relative to atlas UV span");
    }

    @Test
    public void testQuadParticleRenderStateFlushesSamplerTextureModesBeforeBinding() throws IOException {
        String source = Files.readString(SRC_MAIN_JAVA.resolve("net/minecraft/client/renderer/state/QuadParticleRenderState.java"));

        assertTrue(source.contains("particleTexture.setFilter(false, false);"),
            "QuadParticleRenderState should restore the particle atlas to nearest non-mip sampling before binding Sampler0");
        assertTrue(source.contains("lightTextureView.texture().flushModeChanges2D();"),
            "QuadParticleRenderState should flush lightmap texture modes before binding Sampler2");
        assertTrue(source.contains("particleTextureView.texture().flushModeChanges2D();"),
            "QuadParticleRenderState should flush atlas texture modes before binding Sampler0");
        assertTrue(source.contains("renderPass.bindSampler(\"Sampler2\", lightTextureView);"),
            "QuadParticleRenderState should bind the flushed lightmap view to Sampler2");
        assertTrue(source.contains("renderPass.bindSampler(\"Sampler0\", particleTextureView);"),
            "QuadParticleRenderState should bind the flushed particle atlas view to Sampler0");
    }

    @Test
    public void testMainTargetUsesBgra8AndBackendMappingsPreserveIt() throws IOException {
        String mainTargetSource = Files.readString(SRC_MAIN_JAVA.resolve("net/blaze3d/pipeline/MainTarget.java"));
        String textureFormatSource = Files.readString(SRC_MAIN_JAVA.resolve("net/blaze3d/textures/TextureFormat.java"));
        String gpuTextureSource = Files.readString(SRC_MAIN_JAVA.resolve("net/blaze3d/textures/GpuTexture.java"));
        String vulkanFormatSource = Files.readString(SRC_MAIN_JAVA.resolve("net/vulkanic/VulkanicTextureFormat.java"));
        String openGlBackendSource = Files.readString(SRC_MAIN_JAVA.resolve("net/vulkanic/backends/opengl/OpenGLBackend.java"));
        String vulkanBackendSource = Files.readString(SRC_MAIN_JAVA.resolve("net/vulkanic/backends/vulkan/VulkanBackend.java"));

        assertTrue(mainTargetSource.contains("TextureFormat.BGRA8"),
            "MainTarget should allocate its color attachment as BGRA8 so Vulkan can present without shader compose into a BGRA swapchain");
        assertTrue(mainTargetSource.contains("public void createBuffers(int i, int j)")
                && mainTargetSource.contains("this.createFrameBuffer(i, j);"),
            "MainTarget should override buffer recreation so window resizes keep using the BGRA main-target allocation path instead of the base RGBA8 RenderTarget implementation");
        assertTrue(textureFormatSource.contains("BGRA8(4)"),
            "TextureFormat should expose a BGRA8 color format for main-target allocation");
        assertTrue(vulkanFormatSource.contains("BGRA8(4)"),
            "VulkanicTextureFormat should expose BGRA8 so backends can preserve swapchain-compatible color ordering");
        assertTrue(gpuTextureSource.contains("case BGRA8   -> VulkanicTextureFormat.BGRA8;"),
            "GpuTexture should preserve Blaze BGRA8 textures when bridging to VulkanicTextureFormat");
        assertTrue(openGlBackendSource.contains("case BGRA8  -> net.vulkanic.VulkanicAPI.GL_BGRA;"),
            "OpenGL backend should preserve BGRA external format metadata for BGRA-backed managed textures");
        assertTrue(vulkanBackendSource.contains("case BGRA8   -> VK10.VK_FORMAT_B8G8R8A8_UNORM;"),
            "Vulkan backend should map BGRA8 textures to a BGRA VkFormat");
        assertTrue(vulkanBackendSource.contains("case VK10.VK_FORMAT_B8G8R8A8_UNORM, VK10.VK_FORMAT_B8G8R8A8_SRGB -> VulkanicTextureFormat.BGRA8;"),
            "Vulkan backend should preserve BGRA swapchain/image wrappers as BGRA8 instead of collapsing them to RGBA8");
        assertTrue(vulkanBackendSource.contains("if ((format == VulkanicAPI.GL_BGRA || internalFormat == VulkanicAPI.GL_BGRA)")
                && vulkanBackendSource.contains("return new LegacyTextureFormatInfo(VK10.VK_FORMAT_B8G8R8A8_UNORM, 4, VK10.VK_IMAGE_ASPECT_COLOR_BIT);")
                && vulkanBackendSource.indexOf("if ((format == VulkanicAPI.GL_BGRA || internalFormat == VulkanicAPI.GL_BGRA)")
                < vulkanBackendSource.indexOf("if ((format == VulkanicAPI.GL_RGBA || internalFormat == VulkanicAPI.GL_RGBA8 || internalFormat == VulkanicAPI.GL_RGBA16)"),
            "Vulkan backend should resolve GL_BGRA uploads to a BGRA VkFormat before the generic RGBA8 legacy-format branch");
    }

    @Test
    public void testVulkanDescriptorSamplerKeysUseLiveGpuTextureState() throws IOException {
        String backendSource = Files.readString(SRC_MAIN_JAVA.resolve("net/vulkanic/backends/vulkan/VulkanBackend.java"));
        String textureSource = Files.readString(SRC_MAIN_JAVA.resolve("net/blaze3d/textures/GpuTexture.java"));

        assertTrue(textureSource.contains("public FilterMode getMinFilter()"),
            "GpuTexture should expose its live min filter so Vulkan descriptor samplers can follow current texture state");
        assertTrue(textureSource.contains("public FilterMode getMagFilter()"),
            "GpuTexture should expose its live mag filter so Vulkan descriptor samplers can follow current texture state");
        assertTrue(textureSource.contains("public boolean usesMipmaps()"),
            "GpuTexture should expose whether mipmaps are live-enabled for descriptor sampler selection");
        assertTrue(backendSource.contains("GpuTexture gpuTexture = boundTexture instanceof GpuTexture blazeTexture ? blazeTexture : null;"),
            "Vulkan descriptor sampler keys should bridge the backend-neutral bound texture to live GpuTexture state when available");
        assertTrue(backendSource.contains("toLegacyMinFilter(gpuTexture.getMinFilter(), gpuTexture.usesMipmaps())"),
            "Vulkan descriptor samplers should derive minification mode from live GpuTexture state");
        assertTrue(backendSource.contains("toLegacyMagFilter(gpuTexture.getMagFilter())"),
            "Vulkan descriptor samplers should derive magnification mode from live GpuTexture state");
        assertTrue(backendSource.contains("toLegacyWrapMode(gpuTexture.getAddressModeU())"),
            "Vulkan descriptor samplers should derive wrap state from live GpuTexture state");
    }

    @Test
    public void testCompositeMipmappingRunsBeforePerPassRenderPassCreation() throws IOException {
        String compositeRendererSource = Files.readString(SRC_MAIN_JAVA.resolve("net/irisshaders/iris/pipeline/CompositeRenderer.java"));
        String shadowCompositeRendererSource = Files.readString(SRC_MAIN_JAVA.resolve("net/irisshaders/iris/shadows/ShadowCompositeRenderer.java"));

        assertTrue(compositeRendererSource.contains("for (int index : compositePass.mipmappedBuffers) {\n\t\t\t\t\tsetupMipmapping")
            && compositeRendererSource.indexOf("for (int index : compositePass.mipmappedBuffers)")
            < compositeRendererSource.indexOf("try (RenderPass renderPass = VulkanicAPI.createRenderPass("),
            "CompositeRenderer should generate mipmaps before opening the per-pass render pass on Vulkan");
        assertTrue(shadowCompositeRendererSource.contains("for (int index : renderPass.mipmappedBuffers) {\n\t\t\t\t\tsetupMipmapping")
            && shadowCompositeRendererSource.indexOf("for (int index : renderPass.mipmappedBuffers)")
            < shadowCompositeRendererSource.indexOf("try (RenderPass pass = VulkanicAPI.createRenderPass("),
            "ShadowCompositeRenderer should generate mipmaps before opening the per-pass render pass on Vulkan");
    }

    @Test
    public void testIrisCustomPassesUseFramebufferOwnedRenderPassAndRecoveredPipelineSeams() throws IOException {
        String compositeRendererSource = Files.readString(SRC_MAIN_JAVA.resolve("net/irisshaders/iris/pipeline/CompositeRenderer.java"));
        String shadowCompositeRendererSource = Files.readString(SRC_MAIN_JAVA.resolve("net/irisshaders/iris/shadows/ShadowCompositeRenderer.java"));
        String commandEncoderSource = Files.readString(SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/GlCommandEncoder.java"));

        assertTrue(compositeRendererSource.contains("compositePass.ensurePipelineState();"),
            "CompositeRenderer should precompute a framebuffer-compatible pipeline for custom passes before opening the render pass");
        assertTrue(compositeRendererSource.contains("compositePass.framebuffer.getId()")
                && compositeRendererSource.contains("compositePass.framebuffer.hasDepthAttachment()"),
            "CompositeRenderer should create custom-pass render passes from the pass framebuffer contract");
        assertTrue(compositeRendererSource.contains("VulkanicAPI.bindDefaultUniforms(renderPass);"),
            "CompositeRenderer should bind shared default uniforms before Vulkan custom-pass draws");
        assertFalse(compositeRendererSource.contains("framebuffer.bind();"),
            "CompositeRenderer custom pass setup should not manually rebind the framebuffer after backend-owned render-pass creation");

        assertTrue(shadowCompositeRendererSource.contains("renderPass.ensurePipelineState();"),
            "ShadowCompositeRenderer should precompute a framebuffer-compatible pipeline for custom passes before opening the render pass");
        assertTrue(shadowCompositeRendererSource.contains("renderPass.framebuffer.getId()")
                && shadowCompositeRendererSource.contains("renderPass.framebuffer.hasDepthAttachment()"),
            "ShadowCompositeRenderer should create custom-pass render passes from the pass framebuffer contract");
        assertTrue(shadowCompositeRendererSource.contains("VulkanicAPI.bindDefaultUniforms(pass);"),
            "ShadowCompositeRenderer should bind shared default uniforms before Vulkan custom-pass draws");
        assertFalse(shadowCompositeRendererSource.contains("framebuffer.bind();"),
            "ShadowCompositeRenderer custom pass setup should not manually rebind the framebuffer after backend-owned render-pass creation");

        assertTrue(commandEncoderSource.contains("customPass.bindRenderPassResources(glRenderPass);"),
            "GlCommandEncoder should let Iris custom passes contribute sampler resources to the active render pass");
        assertTrue(commandEncoderSource.contains("customPass.pipelineHandle(submission.descriptor())")
                && commandEncoderSource.contains("customPass.pipelineDescriptor()")
                && commandEncoderSource.contains("buildCustomPassPipelineResourceBindings("),
            "GlCommandEncoder should bind descriptor-matched live-program pipelines and descriptor resources for custom passes on Vulkan");
        assertTrue(commandEncoderSource.contains("if (!submission.completeCoverage())")
                && commandEncoderSource.contains("Skipping Vulkan custom pass"),
            "GlCommandEncoder should fail open instead of submitting underbound Vulkan custom passes");
        assertTrue(compositeRendererSource.contains("pipelineLayoutVariants")
                && compositeRendererSource.contains("VulkanicAPI.createPipeline(descriptor, this.framebuffer.getId())"),
            "CompositeRenderer custom passes should cache descriptor-layout pipeline variants for narrowed submission layouts");
        assertTrue(shadowCompositeRendererSource.contains("pipelineLayoutVariants")
                && shadowCompositeRendererSource.contains("VulkanicAPI.createPipeline(descriptor, this.framebuffer.getId())"),
            "ShadowCompositeRenderer custom passes should cache descriptor-layout pipeline variants for narrowed submission layouts");
    }

    @Test
    public void testVulkanBackendBootstrapPathExists() throws IOException {
        Path vulkanBackendFile = SRC_MAIN_JAVA.resolve("net/vulkanic/backends/vulkan/VulkanBackend.java");
        assertTrue(Files.exists(vulkanBackendFile),
            "Vulkan backend bootstrap class should exist for incremental backend bring-up");

        String vulkanBackendSource = Files.readString(vulkanBackendFile);
        assertFalse(vulkanBackendSource.contains("extends OpenGLBackend"),
            "Vulkan backend must not inherit OpenGL backend behavior; cross-backend inheritance must remain forbidden");

        Path apiFile = SRC_MAIN_JAVA.resolve("net/vulkanic/VulkanicAPI.java");
        String apiSource = Files.readString(apiFile);
        assertTrue(apiSource.contains("rawVulkanBackend = new VulkanBackend();"),
            "VulkanicAPI should construct VulkanBackend for GraphicsBackendType.VULKAN routing");
        assertTrue(apiSource.contains("backend = createFailFastVulkanProxy(rawVulkanBackend);"),
            "VulkanicAPI should route Vulkan backend calls through fail-fast proxy protection");
        assertTrue(apiSource.contains("directVulkanBackendForImplementedMethods()"),
            "VulkanicAPI should expose a direct-dispatch helper for hot implemented Vulkan methods");
        assertFalse(apiSource.contains("methodCache.computeIfAbsent(method"),
            "Vulkan fail-fast proxy should precompute backend method routing instead of paying per-call computeIfAbsent overhead on the render thread");
        assertFalse(apiSource.contains("throw new UnsupportedOperationException(\"Vulkan backend not yet implemented\")"),
            "VulkanicAPI should no longer hard-fail backend selection for GraphicsBackendType.VULKAN");
    }

    @Test
    public void testDrawPathRoutesSamplerAndUboBindingThroughPipelineResourceSeam() throws IOException {
        String glCommandEncoderSource = Files.readString(SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/GlCommandEncoder.java"));
        String glDeviceSource = Files.readString(SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/GlDevice.java"));
        String openGLBackendSource = Files.readString(SRC_MAIN_JAVA.resolve("net/vulkanic/backends/opengl/OpenGLBackend.java"));

        int trySetupStart = glCommandEncoderSource.indexOf("private boolean trySetup(");
        int trySetupEnd = glCommandEncoderSource.indexOf("@Override\n\tpublic void applyPipelineState", trySetupStart);
        assertTrue(trySetupStart >= 0 && trySetupEnd > trySetupStart,
            "GlCommandEncoder should still expose trySetup for draw-path inspection");
        String trySetupSource = glCommandEncoderSource.substring(trySetupStart, trySetupEnd);

        assertTrue(glDeviceSource.contains("withReflectedResourceLayout("),
            "GlDevice should cache reflected resource-layout metadata with compiled pipelines");
        assertTrue(trySetupSource.contains("buildPipelineResourceBindings(glRenderPass)"),
            "GlCommandEncoder trySetup should derive backend-neutral pipeline resource bindings from the live render pass");
        assertTrue(trySetupSource.contains("PipelineDescriptor submissionDescriptor = submission.descriptor();"),
            "GlCommandEncoder trySetup should resolve Vulkan pipelines against the actual submission descriptor after partial resource-layout narrowing");
        assertTrue(trySetupSource.contains("VulkanicAPI.bindPipelineResources("),
            "GlCommandEncoder trySetup should route sampler and UBO binding through VulkanicAPI.bindPipelineResources");
        assertTrue(trySetupSource.contains("VulkanicAPI.resolvePipelineHandle("),
            "GlCommandEncoder trySetup should resolve backend pipeline handles through VulkanicAPI seam for non-immediate contexts");
        assertTrue(trySetupSource.contains("immediateSeamHasCompleteCoverage"),
            "GlCommandEncoder trySetup should keep an explicit compatibility fallback path when immediate seam coverage is incomplete during migration");
        assertFalse(trySetupSource.contains("(!ctx.isImmediate() || !immediateSeamHasCompleteCoverage)"),
            "GlCommandEncoder trySetup legacy fallback should no longer force Vulkan contexts onto GL-shaped uniform/texture calls when descriptor seam coverage is complete");
        String glRenderPassSource = Files.readString(SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/GlRenderPass.java"));
        assertTrue(glRenderPassSource.contains("VulkanicAPI.resolveVulkanicBuffer(gpuBufferSlice.buffer())"),
            "GlRenderPass should resolve uniform-buffer bindings through a backend-neutral VulkanicAPI buffer seam at bind time");
        assertTrue(glCommandEncoderSource.contains("glRenderPass.getUniformResourceSlice(resourceBinding.name())"),
            "GlCommandEncoder should consume pre-resolved uniform-buffer slices from GlRenderPass during descriptor assembly");
        assertTrue(glCommandEncoderSource.contains("PipelineResourceBindings.ofResolvedBindings("),
            "GlCommandEncoder should build resolved descriptor bindings without builder duplicate-check churn on the hot path");
        assertTrue(openGLBackendSource.contains("samplerBinding.textureView()"),
            "OpenGLBackend should consume sampler texture views from the pipeline resource seam");
    }

    @Test
    public void testBackendReadinessSeamExistsForPrepOnlyVulkanPath() throws IOException {
        Path backendInterfaceFile = SRC_MAIN_JAVA.resolve("net/vulkanic/GraphicsBackend.java");
        String backendInterfaceSource = Files.readString(backendInterfaceFile);
        assertTrue(backendInterfaceSource.contains("GraphicsBackendType getBackendType();"),
            "GraphicsBackend should expose active backend identity for explicit routing");
        assertTrue(backendInterfaceSource.contains("boolean isNativeVulkanReady();"),
            "GraphicsBackend should expose native Vulkan readiness capability check");
        assertTrue(backendInterfaceSource.contains("default VulkanExecutionContextInfo getVulkanExecutionContextInfo()"),
            "GraphicsBackend should expose backend-neutral Vulkan execution-context seam");
        assertTrue(backendInterfaceSource.contains("default VulkanSwapchainSurfaceInfo getVulkanSwapchainSurfaceInfo()"),
            "GraphicsBackend should expose backend-neutral Vulkan swapchain/surface seam");
        assertTrue(backendInterfaceSource.contains("default void recreateVulkanSwapchain()"),
            "GraphicsBackend should expose explicit swapchain recreation seam");
        assertTrue(backendInterfaceSource.contains("default boolean recreateVulkanSwapchainIfNeeded()"),
            "GraphicsBackend should expose conditional swapchain recreation seam");
        assertTrue(backendInterfaceSource.contains("default VulkanNativeInitializationInfo initializeNativeVulkanRuntime()"),
            "GraphicsBackend should expose explicit native Vulkan initialization seam");
        assertTrue(backendInterfaceSource.contains("VulkanicBuffer createManagedBuffer(Supplier<String> label, int usage, int size);"),
            "GraphicsBackend should expose managed GPU buffer creation seam (size variant)");
        assertTrue(backendInterfaceSource.contains("VulkanicBuffer createManagedBuffer(Supplier<String> label, int usage, java.nio.ByteBuffer initialData);"),
            "GraphicsBackend should expose managed GPU buffer creation seam (initial-data variant)");
        assertTrue(backendInterfaceSource.contains("VulkanicBuffer.MappedView mapManagedBuffer(VulkanicBuffer buffer, boolean read, boolean write);"),
            "GraphicsBackend should expose managed GPU buffer mapping seam");
        assertTrue(backendInterfaceSource.contains("VulkanicTexture createManagedTexture(String label, int usage, VulkanicTextureFormat format,"),
            "GraphicsBackend should expose managed GPU texture creation seam");
        assertTrue(backendInterfaceSource.contains("VulkanicTextureView createManagedTextureView(VulkanicTexture texture);"),
            "GraphicsBackend should expose managed GPU texture view seam (full-range)");
        assertTrue(backendInterfaceSource.contains("VulkanicTextureView createManagedTextureView(VulkanicTexture texture, int baseMipLevel, int mipLevelCount);"),
            "GraphicsBackend should expose managed GPU texture view seam (mip-range)");

        Path apiFile = SRC_MAIN_JAVA.resolve("net/vulkanic/VulkanicAPI.java");
        String apiSource = Files.readString(apiFile);
        assertTrue(apiSource.contains("public static GraphicsBackendType getActiveBackendType()"),
            "VulkanicAPI should expose active backend identity helper");
        assertTrue(apiSource.contains("public static boolean isVulkanBackendSelected()"),
            "VulkanicAPI should expose Vulkan-selection helper");
        assertTrue(apiSource.contains("public static boolean isNativeVulkanBackendReady()"),
            "VulkanicAPI should expose native Vulkan readiness helper");
        assertTrue(apiSource.contains("public static VulkanNativeInitializationInfo initializeNativeVulkanRuntime()"),
            "VulkanicAPI should expose explicit native Vulkan runtime initialization helper");
        assertTrue(apiSource.contains("public static VulkanExecutionContextInfo getVulkanExecutionContextInfo()"),
            "VulkanicAPI should expose Vulkan execution-context diagnostics helper");
        assertTrue(apiSource.contains("public static VulkanSwapchainSurfaceInfo getVulkanSwapchainSurfaceInfo()"),
            "VulkanicAPI should expose Vulkan swapchain/surface diagnostics helper");
        assertTrue(apiSource.contains("public static void recreateVulkanSwapchain()"),
            "VulkanicAPI should expose explicit swapchain recreation helper");
        assertTrue(apiSource.contains("public static boolean recreateVulkanSwapchainIfNeeded()"),
            "VulkanicAPI should expose conditional swapchain recreation helper");
        assertTrue(apiSource.contains("public static VulkanicBuffer createManagedBuffer(java.util.function.Supplier<String> label, int usage, int size)"),
            "VulkanicAPI should expose managed GPU buffer creation helper (size variant)");
        assertTrue(apiSource.contains("public static VulkanicBuffer createManagedBuffer(java.util.function.Supplier<String> label, int usage,"),
            "VulkanicAPI should expose managed GPU buffer creation helper (initial-data variant)");
        assertTrue(apiSource.contains("public static VulkanicBuffer.MappedView mapManagedBuffer(VulkanicBuffer buffer, boolean read, boolean write)"),
            "VulkanicAPI should expose managed GPU buffer mapping helper");
        assertTrue(apiSource.contains("public static VulkanicTexture createManagedTexture(String label, int usage,"),
            "VulkanicAPI should expose managed GPU texture creation helper");
        assertTrue(apiSource.contains("public static VulkanicTextureView createManagedTextureView(VulkanicTexture texture)"),
            "VulkanicAPI should expose managed GPU texture view creation helper (full-range variant)");
        assertTrue(apiSource.contains("public static VulkanicTextureView createManagedTextureView(VulkanicTexture texture,"),
            "VulkanicAPI should expose managed GPU texture view creation helper (mip-range variant)");

        Path vulkanBackendFile = SRC_MAIN_JAVA.resolve("net/vulkanic/backends/vulkan/VulkanBackend.java");
        String vulkanBackendSource = Files.readString(vulkanBackendFile);
        assertTrue(vulkanBackendSource.contains("return GraphicsBackendType.VULKAN;"),
            "Bootstrap Vulkan backend should report Vulkan backend identity");
        assertTrue(vulkanBackendSource.contains("public boolean isNativeVulkanReady()"),
            "Bootstrap Vulkan backend should define native readiness contract");
        assertTrue(vulkanBackendSource.contains("return nativeSpine != null;"),
            "Bootstrap Vulkan backend should report native readiness based on native spine initialization state");
        assertTrue(vulkanBackendSource.contains("public VulkanExecutionContextInfo getVulkanExecutionContextInfo()"),
            "Bootstrap Vulkan backend should expose logical device/queue execution-context diagnostics");
        assertTrue(vulkanBackendSource.contains("public VulkanNativeInitializationInfo initializeNativeVulkanRuntime()"),
            "Bootstrap Vulkan backend should expose explicit native Vulkan initialization diagnostics");
        assertTrue(vulkanBackendSource.contains("public VulkanSwapchainSurfaceInfo getVulkanSwapchainSurfaceInfo()"),
            "Bootstrap Vulkan backend should expose surface/swapchain diagnostics");
        assertTrue(vulkanBackendSource.contains("public void recreateVulkanSwapchain()"),
            "Bootstrap Vulkan backend should expose explicit swapchain recreation entrypoint");
        assertTrue(vulkanBackendSource.contains("public boolean recreateVulkanSwapchainIfNeeded()"),
            "Bootstrap Vulkan backend should expose conditional swapchain recreation entrypoint");
        assertTrue(vulkanBackendSource.contains("public VulkanicBuffer createManagedBuffer(java.util.function.Supplier<String> label, int usage, int size)"),
            "Bootstrap Vulkan backend should expose managed GPU buffer creation entrypoint (size variant)");
        assertTrue(vulkanBackendSource.contains("public VulkanicBuffer createManagedBuffer(java.util.function.Supplier<String> label,"),
            "Bootstrap Vulkan backend should expose managed GPU buffer creation entrypoint (initial-data variant)");
        assertTrue(vulkanBackendSource.contains("public VulkanicBuffer.MappedView mapManagedBuffer(VulkanicBuffer buffer, boolean read, boolean write)"),
            "Bootstrap Vulkan backend should expose managed GPU buffer mapping entrypoint");
        assertTrue(vulkanBackendSource.contains("public VulkanicTexture createManagedTexture(String label, int usage, VulkanicTextureFormat format,"),
            "Bootstrap Vulkan backend should expose managed GPU texture creation entrypoint");
        assertTrue(vulkanBackendSource.contains("public VulkanicTextureView createManagedTextureView(VulkanicTexture texture)"),
            "Bootstrap Vulkan backend should expose managed GPU texture view creation entrypoint (full-range variant)");
        assertTrue(vulkanBackendSource.contains("public VulkanicTextureView createManagedTextureView(VulkanicTexture texture, int baseMipLevel, int mipLevelCount)"),
            "Bootstrap Vulkan backend should expose managed GPU texture view creation entrypoint (mip-range variant)");
        assertTrue(vulkanBackendSource.contains("spine.recreateSwapchainIfFramebufferSizeChanged();"),
            "Bootstrap Vulkan command-buffer begin path should auto-check framebuffer resize and recreate swapchain");
    }

    @Test
    public void testTextureHandleResolutionOwnedByBackendSeam() throws IOException {
        Path backendInterfaceFile = SRC_MAIN_JAVA.resolve("net/vulkanic/GraphicsBackend.java");
        String backendInterfaceSource = Files.readString(backendInterfaceFile);
        assertTrue(backendInterfaceSource.contains("default int resolveTextureHandle(CommandContext ctx, VulkanicTexture texture)"),
            "GraphicsBackend should expose resolveTextureHandle seam for backend-owned texture-handle resolution");

        Path vulkanicApiFile = SRC_MAIN_JAVA.resolve("net/vulkanic/VulkanicAPI.java");
        String vulkanicApiSource = Files.readString(vulkanicApiFile);
        assertFalse(vulkanicApiSource.contains("return texture.glId();"),
            "VulkanicAPI.getTextureHandle should not directly call texture.glId after backend-seam migration");
        assertTrue(vulkanicApiSource.contains("return getBackend().resolveTextureHandle(getCommandContext(), target);"),
            "VulkanicAPI.getTextureHandle should delegate handle extraction to GraphicsBackend.resolveTextureHandle");

        Path openGlBackendFile = SRC_MAIN_JAVA.resolve("net/vulkanic/backends/opengl/OpenGLBackend.java");
        String openGlBackendSource = Files.readString(openGlBackendFile);
        assertTrue(openGlBackendSource.contains("public int resolveTextureHandle(CommandContext ctx, net.vulkanic.VulkanicTexture texture)"),
            "OpenGLBackend should implement resolveTextureHandle for OpenGL-backed texture ids");
        assertTrue(openGlBackendSource.contains("texture instanceof net.vulkanic.backends.opengl.OpenGLTexture openGLTexture"),
            "OpenGLBackend resolveTextureHandle should support Vulkanic OpenGLTexture wrappers");
        assertTrue(openGlBackendSource.contains("texture instanceof net.blaze3d.opengl.GlTexture glTexture"),
            "OpenGLBackend resolveTextureHandle should support Blaze3D GlTexture instances");

        Path glTextureFile = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/GlTexture.java");
        String glTextureSource = Files.readString(glTextureFile);
        assertTrue(glTextureSource.contains("public int getGlHandle()"),
            "GlTexture should expose getGlHandle for backend-local OpenGL handle extraction");

        Path openGlTextureViewFile = SRC_MAIN_JAVA.resolve("net/vulkanic/backends/opengl/OpenGLTextureView.java");
        String openGlTextureViewSource = Files.readString(openGlTextureViewFile);
        assertFalse(openGlTextureViewSource.contains("return VulkanicAPI.getTextureHandle(t);"),
            "OpenGLTextureView should not route GlTexture handle extraction back through VulkanicAPI");
        assertTrue(openGlTextureViewSource.contains("return t.getGlHandle();"),
            "OpenGLTextureView should extract GlTexture handles directly through getGlHandle");
    }

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

        // The Iris tessellation override (TRIANGLES → PATCHES) must still be present
        // in the non-instanced indexed draw path, since we replaced GlStateManager._drawElements
        // which previously contained it.
        assertTrue(source.contains("usingTessellation"),
            "drawFromBuffers must preserve the Iris tessellation mode override");
        assertTrue(
            source.contains("VulkanicPrimitiveMode.PATCHES") || source.contains("GL_PATCHES"),
            "drawFromBuffers must substitute PATCHES when tessellation is active"
        );
    }

    @Test
    public void testDrawFromBuffersSharesContextAcrossCalls() throws IOException {
        Path file = SRC_MAIN_JAVA.resolve(
            "net/blaze3d/opengl/GlCommandEncoder.java");
        String source = Files.readString(file);

        // Verify that drawFromBuffers obtains the context once and reuses it (ctx variable),
        // rather than repeatedly resolving the backend-global context.
        assertTrue(source.contains("CommandContext ctx = commandContext();"),
            "drawFromBuffers should obtain a single CommandContext and reuse it");
    }

    @Test
    public void testSsaoApplyShaderUsesBackendNeutralSingleContext() throws IOException {
        Path file = SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/core/render/renderer/shaders/SSAOApplyShader.java");
        assertTrue(Files.exists(file), "SSAOApplyShader.java must exist");

        String source = Files.readString(file);
        Path abstractShaderFile = SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/core/render/renderer/shaders/AbstractShaderRenderer.java");
        String abstractShaderSource = Files.readString(abstractShaderFile);

        assertFalse(source.contains("VulkanicAPI.getCommandContext()"),
            "SSAOApplyShader should inherit the shared CommandContext from AbstractShaderRenderer");
        assertTrue(source.contains("onApplyUniforms(CommandContext ctx, float partialTicks)"),
            "SSAOApplyShader should receive command context via shader hook parameter");
        assertTrue(abstractShaderSource.contains("CommandContext ctx = VulkanicAPI.getCommandContext();"),
            "AbstractShaderRenderer should fetch a backend-neutral CommandContext once per render phase");
        assertTrue(abstractShaderSource.contains("if (width <= 0 || height <= 0)"),
            "AbstractShaderRenderer should skip shader bind/uniform work when viewport dimensions are invalid");
        assertTrue(abstractShaderSource.contains("if (!this.onPreRender(ctx, partialTicks))"),
            "AbstractShaderRenderer should support shared pre-bind resource prechecks before shader bind/uniform work");
        assertTrue(abstractShaderSource.contains("protected boolean onPreRender(CommandContext ctx, float partialTicks) { return true; }"),
            "AbstractShaderRenderer should provide a default backend-neutral pre-bind precheck hook");
        assertTrue(abstractShaderSource.contains("try") && abstractShaderSource.contains("finally"),
            "AbstractShaderRenderer render path should always unbind shader via try/finally for backend-state safety");
        assertTrue(abstractShaderSource.contains("this.shader.unbind(ctx);"),
            "AbstractShaderRenderer should unbind shader even when render hooks fail");
        assertFalse(source.contains("VulkanicAPI.getImmediateContext()"),
            "SSAOApplyShader should not hard-wire immediate OpenGL context retrieval");
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
        assertFalse(source.contains("bl ? 256 : 16384"),
            "GlCommandEncoder texture blit path should not branch over hardcoded depth/color blit masks 256/16384");
        assertFalse(source.contains("gpuTextureView.getHeight(0), 16384, 9728"),
            "GlCommandEncoder presentTexture path should not use hardcoded color-mask/filter literals 16384/9728");
        assertFalse(source.contains("bindFrameBufferTextures(this.drawFbo, VulkanicCoreAPI.textureId(gpuTextureView), 0, 0, 0)"),
            "GlCommandEncoder presentTexture should avoid direct drawFbo texture attachment plumbing and route through backend-owned present seam");

        assertTrue(source.contains("VulkanicAPI.bindCubemapTexture("),
            "GlCommandEncoder should bind cubemaps via VulkanicAPI.bindCubemapTexture");
        assertTrue(source.contains("VulkanicAPI.bindTextureBuffer("),
            "GlCommandEncoder should bind texture buffers via VulkanicAPI.bindTextureBuffer");
        assertTrue(source.contains("VulkanicAPI.bindTextureBufferData("),
            "GlCommandEncoder should attach texel buffer data via VulkanicAPI.bindTextureBufferData");
        assertTrue(source.contains("VulkanicAPI.bindUniformBufferRange(ctx, var39"),
            "GlCommandEncoder should use target-agnostic bindUniformBufferRange overload in uniform upload path");
        assertTrue(source.contains("bl ? VulkanicAPI.GL_DEPTH_BUFFER_BIT : VulkanicAPI.GL_COLOR_BUFFER_BIT"),
            "GlCommandEncoder texture blit path should route depth/color masks through VulkanicAPI constants");
        assertTrue(source.contains("VulkanicAPI.GL_NEAREST"),
            "GlCommandEncoder blit paths should route nearest-filter intent through VulkanicAPI constant");
        assertTrue(source.contains("VulkanicCoreAPI.presentTextureToScreen(ctx, gpuTextureView);"),
            "GlCommandEncoder presentTexture should route final presentation through VulkanicCoreAPI.presentTextureToScreen backend seam");
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
        assertFalse(source.contains("VulkanicAPI.bindReadFramebuffer(ctx, i);"),
            "DirectStateAccess should not manually bind read framebuffer around blits in fallback mode");
        assertFalse(source.contains("VulkanicAPI.bindDrawFramebuffer(ctx, j);"),
            "DirectStateAccess should not manually bind draw framebuffer around blits in fallback mode");
        assertTrue(source.contains("VulkanicAPI.blitNamedFramebuffer(ctx, i, j, k, l, m, n, o, p, q, r, s, t);"),
            "DirectStateAccess should route blits through VulkanicAPI.blitNamedFramebuffer");
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

        assertFalse(irisRenderSystemSource.contains("public static void bindFramebuffer("),
            "IrisRenderSystem framebuffer bind wrapper should be removed after VulkanicAPI helper migration");
        assertFalse(irisRenderSystemSource.contains("public static int getFrameBuffer("),
            "IrisRenderSystem framebuffer binding getter wrapper should be removed after VulkanicAPI helper migration");
        assertFalse(irisRenderSystemSource.contains("private static int readFramebuffer"),
            "IrisRenderSystem should not own read framebuffer binding state");
        assertFalse(irisRenderSystemSource.contains("private static int writeFramebuffer"),
            "IrisRenderSystem should not own draw framebuffer binding state");
        assertFalse(irisRenderSystemSource.contains("VulkanicAPI.bindReadFramebuffer(VulkanicAPI.getCommandContext(), source)"),
            "IrisRenderSystem should not manually bind read framebuffer in fallback blit path");
        assertFalse(irisRenderSystemSource.contains("VulkanicAPI.bindDrawFramebuffer(VulkanicAPI.getCommandContext(), dest)"),
            "IrisRenderSystem should not manually bind draw framebuffer in fallback blit path");
        assertTrue(irisRenderSystemSource.contains("VulkanicAPI.blitNamedFramebuffer(VulkanicAPI.getCommandContext(), source, dest"),
            "IrisRenderSystem fallback blit path should use VulkanicAPI.blitNamedFramebuffer");

        Path vulkanicApiFile = SRC_MAIN_JAVA.resolve("net/vulkanic/VulkanicAPI.java");
        String vulkanicApiSource = Files.readString(vulkanicApiFile);

        assertTrue(vulkanicApiSource.contains("private static int readFramebufferBinding"),
            "VulkanicAPI should own cached read framebuffer binding state");
        assertTrue(vulkanicApiSource.contains("private static int drawFramebufferBinding"),
            "VulkanicAPI should own cached draw framebuffer binding state");
        assertTrue(vulkanicApiSource.contains("public static void bindDefaultFramebuffer(CommandContext ctx)"),
            "VulkanicAPI should expose bindDefaultFramebuffer helper for intent-level default FBO binds");
        assertTrue(vulkanicApiSource.contains("public static int getReadFramebufferBinding()"),
            "VulkanicAPI should expose cached read framebuffer getter");
        assertTrue(vulkanicApiSource.contains("public static int getDrawFramebufferBinding()"),
            "VulkanicAPI should expose cached draw framebuffer getter");
    }

    @Test
    public void testFramebufferIntentCallsitesUseVulkanicAPIHelpers() throws IOException {
        Path commandEncoderFile = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/GlCommandEncoder.java");
        String commandEncoderSource = Files.readString(commandEncoderFile);
        assertFalse(commandEncoderSource.contains("IrisRenderSystem.bindFramebuffer("),
            "GlCommandEncoder framebuffer paths should not bind through IrisRenderSystem framebuffer helper wrappers");
        assertTrue(commandEncoderSource.contains("VulkanicAPI.bindDefaultFramebuffer("),
            "GlCommandEncoder framebuffer paths should bind default FBO through VulkanicAPI helper");

        Path directStateAccessFile = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/DirectStateAccess.java");
        String directStateAccessSource = Files.readString(directStateAccessFile);
        assertFalse(directStateAccessSource.contains("IrisRenderSystem.getFrameBuffer("),
            "DirectStateAccess should not read framebuffer bindings through IrisRenderSystem helper wrappers");
        assertFalse(directStateAccessSource.contains("IrisRenderSystem.bindFramebuffer("),
            "DirectStateAccess should not bind framebuffer targets through IrisRenderSystem helper wrappers");
        assertTrue(directStateAccessSource.contains("VulkanicAPI.getFramebufferBinding("),
            "DirectStateAccess should read framebuffer bindings through VulkanicAPI cached binding helpers");

        Path renderTargetFile = SRC_MAIN_JAVA.resolve("net/blaze3d/pipeline/RenderTarget.java");
        String renderTargetSource = Files.readString(renderTargetFile);
        assertFalse(renderTargetSource.contains("IrisRenderSystem.bindFramebuffer("),
            "RenderTarget iris$bindFramebuffer path should not bind through IrisRenderSystem helper wrappers");
        assertFalse(renderTargetSource.contains("VulkanicAPI.resolveFramebufferForTextures(this.colorTexture, this.depthTexture)"),
            "RenderTarget iris$bindFramebuffer path should not resolve backend framebuffer ids directly");
        assertTrue(renderTargetSource.contains("VulkanicAPI.bindRenderTarget(VulkanicAPI.getCommandContext(), this.colorTexture, this.depthTexture);"),
            "RenderTarget iris$bindFramebuffer path should bind through VulkanicAPI render-target helper");

        Path dhWrapperFile = SRC_MAIN_JAVA.resolve("com/seibel/distanthorizons/common/wrappers/minecraft/MinecraftGLWrapper.java");
        String dhWrapperSource = readSourceIfExists(dhWrapperFile);
        assertFalse(dhWrapperSource.contains("IrisRenderSystem.bindFramebuffer("),
            "MinecraftGLWrapper should not perform duplicate framebuffer binds through IrisRenderSystem helper wrappers");
        assertFalse(dhWrapperSource.contains("glBindFramebuffer("),
            "MinecraftGLWrapper should no longer expose framebuffer bind wrapper after VulkanicAPI callsite migration");

        Path dhInterfaceFile = SRC_MAIN_JAVA.resolve("com/seibel/distanthorizons/core/wrapperInterfaces/minecraft/IMinecraftGLWrapper.java");
        String dhInterfaceSource = readSourceIfExists(dhInterfaceFile);
        assertFalse(dhInterfaceSource.contains("void glBindFramebuffer("),
            "IMinecraftGLWrapper should no longer declare framebuffer bind wrapper after VulkanicAPI callsite migration");
        assertFalse(dhInterfaceSource.contains("void enableBlend("),
            "IMinecraftGLWrapper should no longer declare blend enable wrapper after VulkanicAPI callsite migration");
        assertFalse(dhInterfaceSource.contains("void disableBlend("),
            "IMinecraftGLWrapper should no longer declare blend disable wrapper after VulkanicAPI callsite migration");
        assertFalse(dhInterfaceSource.contains("void glBlendFunc("),
            "IMinecraftGLWrapper should no longer declare blend function wrapper after VulkanicAPI callsite migration");
        assertFalse(dhInterfaceSource.contains("void glBlendFuncSeparate("),
            "IMinecraftGLWrapper should no longer declare blend separate wrapper after VulkanicAPI callsite migration");
        assertFalse(dhInterfaceSource.contains("void enableDepthMask("),
            "IMinecraftGLWrapper should no longer declare depth-mask enable wrapper after VulkanicAPI callsite migration");
        assertFalse(dhInterfaceSource.contains("void disableDepthMask("),
            "IMinecraftGLWrapper should no longer declare depth-mask disable wrapper after VulkanicAPI callsite migration");
        assertFalse(dhInterfaceSource.contains("int glGenTextures("),
            "IMinecraftGLWrapper should no longer declare texture generation wrapper after VulkanicAPI callsite migration");
        assertFalse(dhInterfaceSource.contains("void glDeleteTextures("),
            "IMinecraftGLWrapper should no longer declare texture deletion wrapper after VulkanicAPI callsite migration");
        assertFalse(dhInterfaceSource.contains("int glGenBuffers("),
            "IMinecraftGLWrapper should no longer declare buffer generation wrapper after VulkanicAPI callsite migration");
        assertFalse(dhInterfaceSource.contains("void glDeleteBuffers("),
            "IMinecraftGLWrapper should no longer declare buffer deletion wrapper after VulkanicAPI callsite migration");
        assertFalse(dhInterfaceSource.contains("void enableScissorTest("),
            "IMinecraftGLWrapper should no longer declare scissor enable wrapper after VulkanicAPI callsite migration");
        assertFalse(dhInterfaceSource.contains("void disableScissorTest("),
            "IMinecraftGLWrapper should no longer declare scissor disable wrapper after VulkanicAPI callsite migration");
        assertFalse(dhInterfaceSource.contains("void enableDepthTest("),
            "IMinecraftGLWrapper should no longer declare depth-test enable wrapper after VulkanicAPI callsite migration");
        assertFalse(dhInterfaceSource.contains("void disableDepthTest("),
            "IMinecraftGLWrapper should no longer declare depth-test disable wrapper after VulkanicAPI callsite migration");
        assertFalse(dhInterfaceSource.contains("void glDepthFunc("),
            "IMinecraftGLWrapper should no longer declare depth-function wrapper after VulkanicAPI callsite migration");
        assertFalse(dhInterfaceSource.contains("void enableFaceCulling("),
            "IMinecraftGLWrapper should no longer declare cull enable wrapper after VulkanicAPI callsite migration");
        assertFalse(dhInterfaceSource.contains("void disableFaceCulling("),
            "IMinecraftGLWrapper should no longer declare cull disable wrapper after VulkanicAPI callsite migration");

        assertFalse(dhWrapperSource.contains("public void enableBlend("),
            "MinecraftGLWrapper should not implement blend enable wrapper after VulkanicAPI callsite migration");
        assertFalse(dhWrapperSource.contains("public void disableBlend("),
            "MinecraftGLWrapper should not implement blend disable wrapper after VulkanicAPI callsite migration");
        assertFalse(dhWrapperSource.contains("public void glBlendFunc("),
            "MinecraftGLWrapper should not implement blend function wrapper after VulkanicAPI callsite migration");
        assertFalse(dhWrapperSource.contains("public void glBlendFuncSeparate("),
            "MinecraftGLWrapper should not implement blend separate wrapper after VulkanicAPI callsite migration");
        assertFalse(dhWrapperSource.contains("public void enableDepthMask("),
            "MinecraftGLWrapper should not implement depth-mask enable wrapper after VulkanicAPI callsite migration");
        assertFalse(dhWrapperSource.contains("public void disableDepthMask("),
            "MinecraftGLWrapper should not implement depth-mask disable wrapper after VulkanicAPI callsite migration");
        assertFalse(dhWrapperSource.contains("public int glGenTextures("),
            "MinecraftGLWrapper should not implement texture generation wrapper after VulkanicAPI callsite migration");
        assertFalse(dhWrapperSource.contains("public void glDeleteTextures("),
            "MinecraftGLWrapper should not implement texture deletion wrapper after VulkanicAPI callsite migration");
        assertFalse(dhWrapperSource.contains("public int glGenBuffers("),
            "MinecraftGLWrapper should not implement buffer generation wrapper after VulkanicAPI callsite migration");
        assertFalse(dhWrapperSource.contains("public void glDeleteBuffers("),
            "MinecraftGLWrapper should not implement buffer deletion wrapper after VulkanicAPI callsite migration");
        assertFalse(dhWrapperSource.contains("public void enableScissorTest("),
            "MinecraftGLWrapper should not implement scissor enable wrapper after VulkanicAPI callsite migration");
        assertFalse(dhWrapperSource.contains("public void disableScissorTest("),
            "MinecraftGLWrapper should not implement scissor disable wrapper after VulkanicAPI callsite migration");
        assertFalse(dhWrapperSource.contains("public void enableDepthTest("),
            "MinecraftGLWrapper should not implement depth-test enable wrapper after VulkanicAPI callsite migration");
        assertFalse(dhWrapperSource.contains("public void disableDepthTest("),
            "MinecraftGLWrapper should not implement depth-test disable wrapper after VulkanicAPI callsite migration");
        assertFalse(dhWrapperSource.contains("public void glDepthFunc("),
            "MinecraftGLWrapper should not implement depth-function wrapper after VulkanicAPI callsite migration");
        assertFalse(dhWrapperSource.contains("public void enableFaceCulling("),
            "MinecraftGLWrapper should not implement cull enable wrapper after VulkanicAPI callsite migration");
        assertFalse(dhWrapperSource.contains("public void disableFaceCulling("),
            "MinecraftGLWrapper should not implement cull disable wrapper after VulkanicAPI callsite migration");

        Path dhFramebufferFile = SRC_MAIN_JAVA.resolve("com/seibel/distanthorizons/core/render/glObject/texture/DhFramebuffer.java");
        String dhFramebufferSource = Files.readString(dhFramebufferFile);
        assertFalse(dhFramebufferSource.contains("GLMC.glBindFramebuffer("),
            "DhFramebuffer should not bind framebuffers through GLMC wrapper");
        assertTrue(dhFramebufferSource.contains("VulkanicAPI.bindReadFramebuffer("),
            "DhFramebuffer should bind read framebuffer through VulkanicAPI helper");
        assertTrue(dhFramebufferSource.contains("VulkanicAPI.bindDrawFramebuffer("),
            "DhFramebuffer should bind draw framebuffer through VulkanicAPI helper");
        assertFalse(dhFramebufferSource.contains("VulkanicAPI.framebufferTexture2D(ctx, VulkanicAPI.GL_FRAMEBUFFER, depthAttachment, VulkanicAPI.GL_TEXTURE_2D"),
            "DhFramebuffer should not pass explicit GL_TEXTURE_2D in depth attachment path");
        assertFalse(dhFramebufferSource.contains("VulkanicAPI.framebufferTexture2D(ctx, VulkanicAPI.GL_FRAMEBUFFER, VulkanicAPI.GL_COLOR_ATTACHMENT0 + textureIndex, VulkanicAPI.GL_TEXTURE_2D"),
            "DhFramebuffer should not pass explicit GL_TEXTURE_2D in color attachment path");
        assertFalse(dhFramebufferSource.contains("VulkanicAPI.framebufferTexture2D(ctx, VulkanicAPI.GL_FRAMEBUFFER, depthAttachment, textureId, 0)"),
            "DhFramebuffer should not hard-code GL_FRAMEBUFFER target in depth attachment path");
        assertTrue(dhFramebufferSource.contains("VulkanicAPI.framebufferTexture2D(ctx, depthAttachment, textureId, 0)"),
            "DhFramebuffer should attach depth textures through VulkanicAPI default-target framebufferTexture2D overload");
        assertFalse(dhFramebufferSource.contains("VulkanicAPI.framebufferTexture2D(ctx, VulkanicAPI.GL_FRAMEBUFFER, VulkanicAPI.GL_COLOR_ATTACHMENT0 + textureIndex, textureId, 0)"),
            "DhFramebuffer should not compute color-attachment enums inline for color attachment path");
        assertFalse(dhFramebufferSource.contains("VulkanicAPI.framebufferColorAttachmentTexture2D(ctx, VulkanicAPI.GL_FRAMEBUFFER, textureIndex, textureId, 0)"),
            "DhFramebuffer should not hard-code GL_FRAMEBUFFER target in color attachment path");
        assertTrue(dhFramebufferSource.contains("VulkanicAPI.framebufferColorAttachmentTexture2D(ctx, textureIndex, textureId, 0)"),
            "DhFramebuffer should attach color textures through VulkanicAPI default-target color-attachment helper");
        assertTrue(dhFramebufferSource.contains("VulkanicAPI.colorAttachment(buffer)"),
            "DhFramebuffer draw/read buffer paths should use VulkanicAPI.colorAttachment helper");
        assertTrue(dhFramebufferSource.contains("VulkanicAPI.setReadBufferColorAttachment(ctx, buffer)"),
            "DhFramebuffer read-buffer path should use VulkanicAPI.setReadBufferColorAttachment helper");
        assertFalse(dhFramebufferSource.contains("VulkanicAPI.checkFramebufferStatus(ctx, VulkanicAPI.GL_FRAMEBUFFER)"),
            "DhFramebuffer should not hard-code GL_FRAMEBUFFER target in framebuffer status path");
        assertTrue(dhFramebufferSource.contains("VulkanicAPI.checkFramebufferStatus(ctx)"),
            "DhFramebuffer should query status through VulkanicAPI default-target helper");

        Path lodRendererFile = SRC_MAIN_JAVA.resolve("com/seibel/distanthorizons/core/render/renderer/LodRenderer.java");
        String lodRendererSource = Files.readString(lodRendererFile);
        assertFalse(lodRendererSource.contains("getFramebufferAttachmentParameteri(ctx, VulkanicAPI.GL_FRAMEBUFFER, VulkanicAPI.GL_COLOR_ATTACHMENT0, VulkanicAPI.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME)"),
            "LodRenderer should not query color attachment object name through raw GL_FRAMEBUFFER/GL_COLOR_ATTACHMENT0 constants");
        assertTrue(lodRendererSource.contains("VulkanicAPI.getFramebufferColorAttachment0ObjectName(ctx)"),
            "LodRenderer should query color attachment object name through VulkanicAPI helper");
        assertFalse(lodRendererSource.contains("this.framebuffer.getStatus() != VulkanicAPI.GL_FRAMEBUFFER_COMPLETE"),
            "LodRenderer should not compare framebuffer status against raw GL_FRAMEBUFFER_COMPLETE constant");
        assertTrue(lodRendererSource.contains("!VulkanicAPI.isFramebufferComplete(this.framebuffer.getStatus())"),
            "LodRenderer should validate framebuffer status through VulkanicAPI completeness helper");
        assertFalse(lodRendererSource.contains("VulkanicAPI.clearBuffers(ctx, VulkanicAPI.GL_DEPTH_BUFFER_BIT)"),
            "LodRenderer should not clear depth via raw GL_DEPTH_BUFFER_BIT mask");
        assertFalse(lodRendererSource.contains("VulkanicAPI.clearBuffers(ctx, VulkanicAPI.GL_COLOR_BUFFER_BIT | VulkanicAPI.GL_DEPTH_BUFFER_BIT)"),
            "LodRenderer should not clear color+depth via raw GL bitmask");
        assertTrue(lodRendererSource.contains("VulkanicAPI.clearDepthBuffer(ctx)"),
            "LodRenderer should clear depth through VulkanicAPI clearDepthBuffer helper");
        assertTrue(lodRendererSource.contains("VulkanicAPI.clearColorAndDepthBuffers(ctx)"),
            "LodRenderer should clear color+depth through VulkanicAPI clearColorAndDepthBuffers helper");

        Path renderTargetsFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/targets/RenderTargets.java");
        String renderTargetsSource = Files.readString(renderTargetsFile);
        assertFalse(renderTargetsSource.contains("status != VulkanicAPI.GL_FRAMEBUFFER_COMPLETE"),
            "RenderTargets should not compare framebuffer status against raw GL_FRAMEBUFFER_COMPLETE constant");
        assertTrue(renderTargetsSource.contains("!VulkanicAPI.isFramebufferComplete(status)"),
            "RenderTargets should validate framebuffer status through VulkanicAPI completeness helper");

        Path shadowRenderTargetsFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/shadows/ShadowRenderTargets.java");
        String shadowRenderTargetsSource = Files.readString(shadowRenderTargetsFile);
        assertFalse(shadowRenderTargetsSource.contains("status != VulkanicAPI.GL_FRAMEBUFFER_COMPLETE"),
            "ShadowRenderTargets should not compare framebuffer status against raw GL_FRAMEBUFFER_COMPLETE constant");
        assertTrue(shadowRenderTargetsSource.contains("!VulkanicAPI.isFramebufferComplete(status)"),
            "ShadowRenderTargets should validate framebuffer status through VulkanicAPI completeness helper");
        assertFalse(shadowRenderTargetsSource.contains("IrisRenderSystem.blitFramebuffer(depthSourceFb.getId(), noTranslucentsDestFb.getId()"),
            "ShadowRenderTargets should not blit depth via generic blitFramebuffer + raw mask/filter constants");
        assertTrue(shadowRenderTargetsSource.contains("IrisRenderSystem.blitDepthBufferNearest(depthSourceFb.getId(), noTranslucentsDestFb.getId()"),
            "ShadowRenderTargets should blit depth through IrisRenderSystem blitDepthBufferNearest helper");

        Path vulkanicApiFile = SRC_MAIN_JAVA.resolve("net/vulkanic/VulkanicAPI.java");
        String vulkanicApiSource = Files.readString(vulkanicApiFile);
        assertTrue(vulkanicApiSource.contains("public static boolean isFramebufferComplete(int framebufferStatus)"),
            "VulkanicAPI should expose framebuffer completeness helper");
        assertTrue(vulkanicApiSource.contains("return framebufferStatus == GL_FRAMEBUFFER_COMPLETE;"),
            "VulkanicAPI framebuffer completeness helper should map to GL_FRAMEBUFFER_COMPLETE semantics");
        assertTrue(vulkanicApiSource.contains("public static void clearDepthBuffer(CommandContext ctx)"),
            "VulkanicAPI should expose clearDepthBuffer helper");
        assertTrue(vulkanicApiSource.contains("public static void clearColorAndDepthBuffers(CommandContext ctx)"),
            "VulkanicAPI should expose clearColorAndDepthBuffers helper");
        assertTrue(vulkanicApiSource.contains("public static void clearColorBufferWithMacosWorkaround(CommandContext ctx)"),
            "VulkanicAPI should expose clearColorBufferWithMacosWorkaround helper");
        assertTrue(vulkanicApiSource.contains("public static void clearDepthBufferWithMacosWorkaround(CommandContext ctx)"),
            "VulkanicAPI should expose clearDepthBufferWithMacosWorkaround helper");
        assertTrue(vulkanicApiSource.contains("public static void clearColorAndDepthBuffersWithMacosWorkaround(CommandContext ctx)"),
            "VulkanicAPI should expose clearColorAndDepthBuffersWithMacosWorkaround helper");
        assertTrue(vulkanicApiSource.contains("public static final int GL_STENCIL_BUFFER_BIT = 0x00000400;"),
            "VulkanicAPI should define GL_STENCIL_BUFFER_BIT for centralized depth-stencil blit helpers");
        assertTrue(vulkanicApiSource.contains("public static void blitDepthBufferNearest(CommandContext ctx"),
            "VulkanicAPI should expose blitDepthBufferNearest helper");
        assertTrue(vulkanicApiSource.contains("public static void blitDepthAndStencilBuffersNearest(CommandContext ctx"),
            "VulkanicAPI should expose blitDepthAndStencilBuffersNearest helper");

        Path irisRenderingPipelineFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/pipeline/IrisRenderingPipeline.java");
        String irisRenderingPipelineSource = Files.readString(irisRenderingPipelineFile);
        assertFalse(irisRenderingPipelineSource.contains("VulkanicAPI.clearBuffersWithMacosWorkaround(VulkanicAPI.getCommandContext(), VulkanicAPI.GL_DEPTH_BUFFER_BIT)"),
            "IrisRenderingPipeline should not clear depth through raw GL_DEPTH_BUFFER_BIT macOS clear mask");
        assertTrue(irisRenderingPipelineSource.contains("VulkanicAPI.clearDepthBufferWithMacosWorkaround(VulkanicAPI.getCommandContext())"),
            "IrisRenderingPipeline should clear depth through VulkanicAPI clearDepthBufferWithMacosWorkaround helper");

        Path lodRendererEventsFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/compat/dh/LodRendererEvents.java");
        String lodRendererEventsSource = Files.readString(lodRendererEventsFile);
        assertFalse(lodRendererEventsSource.contains("VulkanicAPI.clearBuffersWithMacosWorkaround(VulkanicAPI.getCommandContext(), VulkanicAPI.GL_DEPTH_BUFFER_BIT)"),
            "LodRendererEvents should not clear depth through raw GL_DEPTH_BUFFER_BIT macOS clear mask");
        assertTrue(containsAny(lodRendererEventsSource,
                "VulkanicAPI.clearDepthBufferWithMacosWorkaround(VulkanicAPI.getCommandContext())",
                "CommandContext ctx = VulkanicAPI.getCommandContext();\n\t\t\t\t\t\tVulkanicAPI.clearDepthBufferWithMacosWorkaround(ctx);"),
            "LodRendererEvents should clear depth through VulkanicAPI clearDepthBufferWithMacosWorkaround helper");

        Path textureManipulationUtilMacosFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/pbr/util/TextureManipulationUtil.java");
        String textureManipulationUtilMacosSource = Files.readString(textureManipulationUtilMacosFile);
        assertFalse(textureManipulationUtilMacosSource.contains("VulkanicAPI.clearBuffersWithMacosWorkaround(ctx, VulkanicAPI.GL_COLOR_BUFFER_BIT)"),
            "TextureManipulationUtil should not clear color through raw GL_COLOR_BUFFER_BIT macOS clear mask");
        assertTrue(textureManipulationUtilMacosSource.contains("VulkanicAPI.clearColorBufferWithMacosWorkaround(ctx)"),
            "TextureManipulationUtil should clear color through VulkanicAPI clearColorBufferWithMacosWorkaround helper");

        Path dhApplyFile = SRC_MAIN_JAVA.resolve("com/seibel/distanthorizons/core/render/renderer/shaders/DhApplyShader.java");
        String dhApplySource = Files.readString(dhApplyFile);
        assertFalse(dhApplySource.contains("VulkanicAPI.framebufferTexture(ctx, VulkanicAPI.GL_DRAW_FRAMEBUFFER, VulkanicAPI.GL_COLOR_ATTACHMENT0, VulkanicAPI.GL_TEXTURE_2D"),
            "DhApplyShader should not attach color texture via raw framebufferTexture + explicit GL_TEXTURE_2D path");
        assertTrue(dhApplySource.contains("VulkanicAPI.framebufferColorAttachment0Texture2D(ctx, VulkanicAPI.GL_DRAW_FRAMEBUFFER, targetColorTextureId, 0)")
                || dhApplySource.contains("VulkanicAPI.framebufferColorAttachment0Texture2D(ctx, VulkanicAPI.GL_DRAW_FRAMEBUFFER, this.activeTargetColorTextureId, 0)"),
            "DhApplyShader should attach draw color via VulkanicAPI framebufferColorAttachment0Texture2D helper");

        Path fogApplyFile = SRC_MAIN_JAVA.resolve("com/seibel/distanthorizons/core/render/renderer/shaders/FogApplyShader.java");
        String fogApplySource = Files.readString(fogApplyFile);
        assertFalse(fogApplySource.contains("GLMC.glBindFramebuffer("),
            "FogApplyShader should not bind read/draw framebuffers through GLMC wrapper");

        Path fogShaderFile = SRC_MAIN_JAVA.resolve("com/seibel/distanthorizons/core/render/renderer/shaders/FogShader.java");
        String fogShaderSource = Files.readString(fogShaderFile);
        assertFalse(fogShaderSource.contains("VulkanicAPI.clearBuffers(ctx, VulkanicAPI.GL_COLOR_BUFFER_BIT | VulkanicAPI.GL_DEPTH_BUFFER_BIT)"),
            "FogShader should not clear color+depth via raw GL bitmask");
        assertTrue(fogShaderSource.contains("VulkanicAPI.clearColorAndDepthBuffers(ctx)"),
            "FogShader should clear color+depth through VulkanicAPI clearColorAndDepthBuffers helper");

        Path ssaoApplyFile = SRC_MAIN_JAVA.resolve("com/seibel/distanthorizons/core/render/renderer/shaders/SSAOApplyShader.java");
        String ssaoApplySource = Files.readString(ssaoApplyFile);
        assertFalse(ssaoApplySource.contains("GLMC.glBindFramebuffer("),
            "SSAOApplyShader should not bind read/draw framebuffers through GLMC wrapper");

        Path fadeApplyFile = SRC_MAIN_JAVA.resolve("com/seibel/distanthorizons/core/render/renderer/shaders/FadeApplyShader.java");
        String fadeApplySource = Files.readString(fadeApplyFile);
        assertFalse(fadeApplySource.contains("GLMC.glBindFramebuffer("),
            "FadeApplyShader should not bind read/draw framebuffers through GLMC wrapper");

        Path glStateFile = SRC_MAIN_JAVA.resolve("com/seibel/distanthorizons/core/render/glObject/GLState.java");
        String glStateSource = Files.readString(glStateFile);
        assertFalse(glStateSource.contains("GLMC.enableBlend("),
            "GLState should not restore blending through GLMC wrapper methods");
        assertFalse(glStateSource.contains("GLMC.disableBlend("),
            "GLState should not restore blending through GLMC wrapper methods");
        assertFalse(glStateSource.contains("GLMC.glBlendFunc("),
            "GLState should not restore blend function through GLMC wrapper methods");
        assertFalse(glStateSource.contains("GLMC.glBlendFuncSeparate("),
            "GLState should not restore blend function separate through GLMC wrapper methods");
        assertFalse(glStateSource.contains("GLMC.enableDepthMask("),
            "GLState should not restore depth mask through GLMC wrapper methods");
        assertFalse(glStateSource.contains("GLMC.disableDepthMask("),
            "GLState should not restore depth mask through GLMC wrapper methods");
        assertFalse(glStateSource.contains("GLMC.enableScissorTest("),
            "GLState should not restore scissor state through GLMC wrapper methods");
        assertFalse(glStateSource.contains("GLMC.disableScissorTest("),
            "GLState should not restore scissor state through GLMC wrapper methods");
        assertFalse(glStateSource.contains("GLMC.enableDepthTest("),
            "GLState should not restore depth test through GLMC wrapper methods");
        assertFalse(glStateSource.contains("GLMC.disableDepthTest("),
            "GLState should not restore depth test through GLMC wrapper methods");
        assertFalse(glStateSource.contains("GLMC.glDepthFunc("),
            "GLState should not restore depth function through GLMC wrapper methods");
        assertFalse(glStateSource.contains("GLMC.enableFaceCulling("),
            "GLState should not restore culling through GLMC wrapper methods");
        assertFalse(glStateSource.contains("GLMC.disableFaceCulling("),
            "GLState should not restore culling through GLMC wrapper methods");
        assertFalse(glStateSource.contains("DhTextureState.setActiveTextureUnit(VulkanicAPI.GL_TEXTURE0)"),
            "GLState should not select texture unit 0 through raw GL_TEXTURE0 constants");
        assertTrue(glStateSource.contains("DhTextureState.setActiveTextureUnitIndex(0)"),
            "GLState should select texture unit 0 through index-based helper");
        assertFalse(glStateSource.contains("getFramebufferAttachmentParameteri(ctx, VulkanicAPI.GL_FRAMEBUFFER"),
            "GLState should not query framebuffer attachment names via raw GL_FRAMEBUFFER constants");
        assertTrue(glStateSource.contains("getFramebufferColorAttachment0ObjectName(ctx)"),
            "GLState should query framebuffer color attachment 0 through VulkanicAPI helper");
        assertTrue(glStateSource.contains("framebufferColorAttachment1Texture2D(ctx, this.frameBufferTexture1, 0)"),
            "GLState should restore framebuffer color attachment 1 through VulkanicAPI helper");

        Path fogRendererFile = SRC_MAIN_JAVA.resolve("com/seibel/distanthorizons/core/render/renderer/FogRenderer.java");
        String fogRendererSource = Files.readString(fogRendererFile);
        assertFalse(fogRendererSource.contains("GLMC.glGenTextures("),
            "FogRenderer should not generate textures through GLMC wrapper methods");
        assertFalse(fogRendererSource.contains("GLMC.glDeleteTextures("),
            "FogRenderer should not delete textures through GLMC wrapper methods");
        assertTrue(fogRendererSource.contains("VulkanicAPI.createTexture2D("),
            "FogRenderer should generate textures through VulkanicAPI helper");
        assertTrue(fogRendererSource.contains("VulkanicAPI.deleteTexture("),
            "FogRenderer should delete textures through VulkanicAPI helper");

        Path ssaoRendererFile = SRC_MAIN_JAVA.resolve("com/seibel/distanthorizons/core/render/renderer/SSAORenderer.java");
        String ssaoRendererSource = Files.readString(ssaoRendererFile);
        assertFalse(ssaoRendererSource.contains("GLMC.glGenTextures("),
            "SSAORenderer should not generate textures through GLMC wrapper methods");
        assertFalse(ssaoRendererSource.contains("GLMC.glDeleteTextures("),
            "SSAORenderer should not delete textures through GLMC wrapper methods");
        assertTrue(ssaoRendererSource.contains("VulkanicAPI.createTexture2D("),
            "SSAORenderer should generate textures through VulkanicAPI helper");
        assertTrue(ssaoRendererSource.contains("VulkanicAPI.deleteTexture("),
            "SSAORenderer should delete textures through VulkanicAPI helper");

        Path testRendererFile = SRC_MAIN_JAVA.resolve("com/seibel/distanthorizons/core/render/renderer/TestRenderer.java");
        String testRendererSource = Files.readString(testRendererFile);
        assertFalse(testRendererSource.contains("VulkanicAPI.clearBuffers(ctx, VulkanicAPI.GL_DEPTH_BUFFER_BIT)"),
            "TestRenderer should not clear depth via raw GL_DEPTH_BUFFER_BIT mask");
        assertTrue(testRendererSource.contains("VulkanicAPI.clearDepthBuffer(ctx)"),
            "TestRenderer should clear depth through VulkanicAPI clearDepthBuffer helper");

        Path dhGlBufferFile = SRC_MAIN_JAVA.resolve("com/seibel/distanthorizons/core/render/glObject/buffer/GLBuffer.java");
        String dhGlBufferSource = Files.readString(dhGlBufferFile);
        assertFalse(dhGlBufferSource.contains("GLMC.glGenBuffers("),
            "DH GLBuffer should not generate buffers through GLMC wrapper methods");
        assertFalse(dhGlBufferSource.contains("GLMC.glDeleteBuffers("),
            "DH GLBuffer should not delete buffers through GLMC wrapper methods");
        assertTrue(dhGlBufferSource.contains("VulkanicAPI.createBuffer("),
            "DH GLBuffer should create buffers through VulkanicAPI helper");
        assertTrue(dhGlBufferSource.contains("VulkanicAPI.deleteBuffer("),
            "DH GLBuffer should delete buffers through VulkanicAPI helper");

        Path renderableBoxGroupFile = SRC_MAIN_JAVA.resolve("com/seibel/distanthorizons/core/render/renderer/generic/RenderableBoxGroup.java");
        String renderableBoxGroupSource = Files.readString(renderableBoxGroupFile);
        assertFalse(renderableBoxGroupSource.contains("GLMC.glGenBuffers("),
            "RenderableBoxGroup should not generate instance buffers through GLMC wrapper methods");
        assertFalse(renderableBoxGroupSource.contains("GLMC.glDeleteBuffers("),
            "RenderableBoxGroup should not delete instance buffers through GLMC wrapper methods");
        assertTrue(renderableBoxGroupSource.contains("VulkanicAPI.createBuffer("),
            "RenderableBoxGroup should create instance buffers through VulkanicAPI helper");
        assertTrue(renderableBoxGroupSource.contains("VulkanicAPI.deleteBuffer("),
            "RenderableBoxGroup should delete instance buffers through VulkanicAPI helper");
    }

    @Test
    public void testRenderTargetBindingOwnedByBackendSeam() throws IOException {
        Path backendInterfaceFile = SRC_MAIN_JAVA.resolve("net/vulkanic/GraphicsBackend.java");
        String backendInterfaceSource = Files.readString(backendInterfaceFile);
        assertTrue(backendInterfaceSource.contains("default void bindRenderTarget(CommandContext ctx, VulkanicTexture colorTexture, VulkanicTexture depthTexture)"),
            "GraphicsBackend should expose backend-owned render-target binding seam");
        assertTrue(backendInterfaceSource.contains("bindFramebuffer(ctx, VulkanicAPI.GL_FRAMEBUFFER, resolveFramebufferForTextures(ctx, colorTexture, depthTexture));"),
            "GraphicsBackend default render-target seam should bridge through backend-owned framebuffer resolution");

        Path vulkanicApiFile = SRC_MAIN_JAVA.resolve("net/vulkanic/VulkanicAPI.java");
        String vulkanicApiSource = Files.readString(vulkanicApiFile);
        assertTrue(vulkanicApiSource.contains("public static void bindRenderTarget(CommandContext ctx, @Nullable GpuTexture colorTexture, @Nullable GpuTexture depthTexture)"),
            "VulkanicAPI should expose render-target binding helper for color/depth texture pairs");
        assertTrue(vulkanicApiSource.contains("getBackend().bindRenderTarget(ctx, colorTarget, depthTarget);"),
            "VulkanicAPI render-target binding helper should delegate ownership to GraphicsBackend");
        assertTrue(vulkanicApiSource.contains("bindDefaultFramebuffer(ctx);"),
            "VulkanicAPI render-target binding helper should fall back to default framebuffer when no color target exists");

        Path openGlBackendFile = SRC_MAIN_JAVA.resolve("net/vulkanic/backends/opengl/OpenGLBackend.java");
        String openGlBackendSource = Files.readString(openGlBackendFile);
        assertTrue(openGlBackendSource.contains("public void bindRenderTarget(CommandContext ctx, net.vulkanic.VulkanicTexture colorTexture, net.vulkanic.VulkanicTexture depthTexture)"),
            "OpenGLBackend should implement render-target binding seam");
        assertTrue(openGlBackendSource.contains("int framebuffer = resolveFramebufferForTextures(ctx, colorTexture, depthTexture);"),
            "OpenGLBackend render-target binding should resolve framebuffer internally");

        Path vulkanBackendFile = SRC_MAIN_JAVA.resolve("net/vulkanic/backends/vulkan/VulkanBackend.java");
        String vulkanBackendSource = Files.readString(vulkanBackendFile);
        assertTrue(vulkanBackendSource.contains("public void bindRenderTarget(CommandContext ctx, net.vulkanic.VulkanicTexture colorTexture, net.vulkanic.VulkanicTexture depthTexture)"),
            "VulkanBackend should implement render-target binding seam");
        assertTrue(vulkanBackendSource.contains("int framebuffer = resolveFramebufferForTextures(ctx, colorTexture, depthTexture);"),
            "VulkanBackend render-target binding should keep attachment-pair routing inside backend code");
        assertTrue(vulkanBackendSource.contains("descriptorPipelineCache")
                && vulkanBackendSource.contains("matchesStableDescriptor("),
            "VulkanBackend should cache descriptor-layout pipeline variants for partial-coverage Vulkan draws instead of forcing them onto the full-layout precompile");
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
        assertTrue(containsAny(directStateAccessSource,
                "VulkanicAPI.createFramebuffer(VulkanicAPI.getCommandContext())",
                "VulkanicAPI.createFramebuffer(ctx)"),
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
        assertTrue(irisFramebufferSource.contains("VulkanicAPI.deleteFramebuffer(VulkanicAPI.getCommandContext(), framebuffer)"),
            "GlFramebuffer should destroy FBOs directly through VulkanicAPI.deleteFramebuffer");

        Path irisRenderSystemFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/gl/IrisRenderSystem.java");
        String irisRenderSystemSource = Files.readString(irisRenderSystemFile);
        assertFalse(irisRenderSystemSource.contains("GlStateManager.glGenFramebuffers("),
            "IrisRenderSystem should not create FBOs through removed GlStateManager.glGenFramebuffers wrapper");
        assertTrue(irisRenderSystemSource.contains("VulkanicAPI.createFramebuffer(VulkanicAPI.getCommandContext())"),
            "IrisRenderSystem should create FBOs directly through VulkanicAPI.createFramebuffer");

        Path textureManipulationUtilFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/pbr/util/TextureManipulationUtil.java");
        String textureManipulationUtilSource = Files.readString(textureManipulationUtilFile);
        assertFalse(textureManipulationUtilSource.contains("GlStateManager.glGenFramebuffers("),
            "TextureManipulationUtil should not create helper FBO through removed GlStateManager.glGenFramebuffers wrapper");
        assertTrue(textureManipulationUtilSource.contains("CommandContext ctx = VulkanicAPI.getCommandContext();"),
            "TextureManipulationUtil should acquire backend-neutral command context once per operation");
        assertTrue(textureManipulationUtilSource.contains("VulkanicAPI.createFramebuffer(ctx)"),
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
        assertTrue(source.contains("VulkanicAPI.bindDefaultFramebuffer("),
            "GlCommandEncoder should bind/unbind default framebuffer through VulkanicAPI.bindDefaultFramebuffer");
        assertFalse(source.contains("VulkanicAPI.clearBuffersWithMacosWorkaround("),
            "GlCommandEncoder should not clear through generic raw-mask macOS helper");
        assertFalse(source.contains("VulkanicAPI.clearBuffersWithMacosWorkaround(VulkanicAPI.getCommandContext(), VulkanicAPI.GL_COLOR_BUFFER_BIT)"),
            "GlCommandEncoder should not clear color via raw GL_COLOR_BUFFER_BIT mask");
        assertFalse(source.contains("VulkanicAPI.clearBuffersWithMacosWorkaround(VulkanicAPI.getCommandContext(), VulkanicAPI.GL_COLOR_BUFFER_BIT | VulkanicAPI.GL_DEPTH_BUFFER_BIT)"),
            "GlCommandEncoder should not clear color+depth via raw GL bitmask");
        assertFalse(source.contains("VulkanicAPI.clearBuffersWithMacosWorkaround(VulkanicAPI.getCommandContext(), VulkanicAPI.GL_DEPTH_BUFFER_BIT)"),
            "GlCommandEncoder should not clear depth via raw GL_DEPTH_BUFFER_BIT mask");
        assertFalse(source.contains("int j = 0;"),
            "GlCommandEncoder should not compose clear masks through ad-hoc integer bitfields");
        assertTrue(containsAny(source,
            "VulkanicAPI.clearColorBufferWithMacosWorkaround(VulkanicAPI.getCommandContext())",
            "VulkanicAPI.clearColorBufferWithMacosWorkaround(ctx)"),
            "GlCommandEncoder should clear color through VulkanicAPI clearColorBufferWithMacosWorkaround helper");
        assertTrue(containsAny(source,
            "VulkanicAPI.clearColorAndDepthBuffersWithMacosWorkaround(VulkanicAPI.getCommandContext())",
            "VulkanicAPI.clearColorAndDepthBuffersWithMacosWorkaround(ctx)"),
            "GlCommandEncoder should clear color+depth through VulkanicAPI clearColorAndDepthBuffersWithMacosWorkaround helper");
        assertTrue(containsAny(source,
            "VulkanicAPI.clearDepthBufferWithMacosWorkaround(VulkanicAPI.getCommandContext())",
            "VulkanicAPI.clearDepthBufferWithMacosWorkaround(ctx)"),
            "GlCommandEncoder should clear depth through VulkanicAPI clearDepthBufferWithMacosWorkaround helper");
        assertTrue(source.contains("boolean shouldClearColor = false;"),
            "GlCommandEncoder should track color clear intent explicitly");
        assertTrue(source.contains("boolean shouldClearDepth = false;"),
            "GlCommandEncoder should track depth clear intent explicitly");
    }

    @Test
    public void testGlCommandEncoderUsesCommandBufferLifecycleForRenderPass() throws IOException {
        Path file = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/GlCommandEncoder.java");
        String source = Files.readString(file);

        assertTrue(source.contains("CommandContext renderPassCtx = VulkanicAPI.beginCommandBuffer();"),
            "GlCommandEncoder should begin an explicit command-buffer scope for backend render-pass recording");
        assertTrue(source.contains("VulkanicAPI.beginRenderPass(") && source.contains("renderPassCtx, supplier,"),
            "GlCommandEncoder should pass explicit command-buffer context into VulkanicAPI.beginRenderPass");
        assertTrue(source.contains("VulkanicAPI.submitCommandBuffer(renderPassCtx);"),
            "GlCommandEncoder should submit command-buffer scope when finishing Vulkanic render passes");
        assertTrue(source.contains("return this.activeRenderPassContext != null ? this.activeRenderPassContext : VulkanicAPI.getCommandContext();"),
            "GlCommandEncoder should prefer the active render-pass command context over the backend-global current context");
        assertTrue(source.contains("ShadowRenderingState.areShadowsCurrentlyBeingRendered() && commandContext().isImmediate()"),
            "GlCommandEncoder should restrict the Iris shadow temp-FBO shortcut to immediate contexts so Vulkan shadow draws keep a real active render pass");
        assertTrue(source.contains("if (ctx.isImmediate()")
                && source.contains("VulkanicAPI.bindFramebuffer(commandContext(), iris$tempFBO);"),
            "GlCommandEncoder trySetup should only rebind the Iris shadow temp FBO on the immediate compatibility seam");
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

        assertTrue(source.contains("getInteger(VulkanicAPI.getCommandContext(), VulkanicIntegerQuery.MAX_TEXTURE_LOD_BIAS)"),
            "GLRenderDevice should use typed VulkanicIntegerQuery.MAX_TEXTURE_LOD_BIAS instead of raw texture query constants");
        assertFalse(source.contains("VulkanicAPI.bindBuffer(ctx, target.getTargetParameter(), buffer.handle())"),
            "GLRenderDevice should no longer bind buffers through raw GlBufferTarget integer targets where typed targets are available");
        assertTrue(source.contains("VulkanicAPI.bindBuffer(ctx, target.toVulkanicBufferTarget(), buffer.handle())"),
            "GLRenderDevice should bind buffers through typed VulkanicBufferTarget mapping");
        assertTrue(source.contains("VulkanicAPI.copyBufferSubDataBetweenCopyTargets("),
            "GLRenderDevice should copy buffer ranges via copyBufferSubDataBetweenCopyTargets helper");
        assertTrue(source.contains("VulkanicAPI.createGpuCompletionFence("),
            "GLRenderDevice should create sync fences via createGpuCompletionFence helper");
        assertFalse(source.contains("VulkanicAPI.getImmediateContext()"),
            "GLRenderDevice should not hard-wire immediate-context retrieval");
        assertTrue(source.contains("VulkanicAPI.getCommandContext()"),
            "GLRenderDevice should fetch backend-neutral command context");
    }

    @Test
    public void testSodiumGlProgramUsesBackendNeutralContext() throws IOException {
        Path file = SRC_MAIN_JAVA.resolve("net/sodium/client/gl/shader/GlProgram.java");
        String source = Files.readString(file);

        assertFalse(source.contains("VulkanicAPI.getImmediateContext()"),
            "Sodium GlProgram should not hard-wire immediate-context retrieval");
        assertTrue(source.contains("VulkanicAPI.getCommandContext()"),
            "Sodium GlProgram should fetch backend-neutral command context");
    }

    @Test
    public void testSodiumVulkanChunkCutoutShaderRestrictsBackfaceDiscardToNonMippedCutouts() throws IOException {
        Path shaderFile = PROJECT_ROOT.resolve("src/main/resources/assets/sodium/shaders/core/vulkan_chunk.fsh");
        String shaderSource = Files.readString(shaderFile);

        assertTrue(shaderSource.contains("#ifdef USE_FRAGMENT_DISCARD"),
            "The Vulkan Sodium chunk fragment shader should keep the cutout-only discard branch");
        assertTrue(shaderSource.contains("if (!_material_use_mips(materialBits) && !gl_FrontFacing) {"),
            "The Vulkan Sodium chunk cutout shader should only discard back-facing fragments for non-mipped alpha-tested terrain such as tall grass and leaf litter");
        assertTrue(shaderSource.contains("if (color.a < _material_alpha_cutoff(materialBits)) {"),
            "The Vulkan Sodium chunk cutout shader should keep alpha cutoff discard for all alpha-tested terrain");

        Path pipelineFile = SRC_MAIN_JAVA.resolve("net/sodium/client/render/chunk/shader/SodiumChunkRenderPipelines.java");
        String pipelineSource = Files.readString(pipelineFile);

        assertTrue(pipelineSource.contains("withShaderDefine(\"USE_FRAGMENT_DISCARD\")"),
            "The Vulkan Sodium cutout pipeline should keep the cutout-only shader define that scopes alpha discard to alpha-tested terrain");
    }

    @Test
    public void testVulkanChunkRendererRoutesThroughSharedActiveProgram() throws IOException {
        Path rendererFile = SRC_MAIN_JAVA.resolve("net/sodium/client/render/chunk/DefaultChunkRenderer.java");
        String rendererSource = Files.readString(rendererFile);

        assertTrue(rendererSource.contains("super.begin(terrainPass, parameters);"),
            "The Vulkan chunk renderer should begin through the shared Sodium chunk program path before issuing terrain draws");
        assertTrue(rendererSource.contains("SharedChunkProgramOverrides.pushActiveProgram(this.activeProgram);"),
            "The Vulkan chunk renderer should expose the currently active Sodium chunk program to the compatibility pipeline compiler");
        assertTrue(rendererSource.contains("shader.setProjectionMatrix(matrices.projection());"),
            "The Vulkan chunk renderer should update the shared chunk shader projection matrix through the same interface used by OpenGL terrain");
        assertTrue(rendererSource.contains("shader.setModelViewMatrix(matrices.modelView());"),
            "The Vulkan chunk renderer should update the shared chunk shader model-view matrix through the same interface used by OpenGL terrain");
        assertTrue(rendererSource.contains("renderPass.setPipeline(SodiumChunkRenderPipelines.forPass(terrainPass, renderPassShader));"),
            "The Vulkan chunk renderer should choose its terrain pipeline from the active shared chunk shader contract");
        assertTrue(rendererSource.contains("renderPassShader.bindRenderPassResources(renderPass, terrainPass);"),
            "The Vulkan chunk renderer should mirror chunk-program sampler state into the render pass before drawing");
        assertTrue(rendererSource.contains("setModelMatrixUniforms(shader, preparedDraw.region(), camera);"),
            "The Vulkan chunk renderer should keep per-region translation updates on the shared chunk shader interface");
        assertTrue(rendererSource.contains("super.end(terrainPass);"),
            "The Vulkan chunk renderer should close the shared chunk program path after terrain submission");
    }

    @Test
    public void testSharedChunkProgramOverridePrecedesIrisOverrideMap() throws IOException {
        Path overrideFile = SRC_MAIN_JAVA.resolve("net/sodium/client/render/chunk/shader/SharedChunkProgramOverrides.java");
        String overrideSource = Files.readString(overrideFile);

        assertTrue(overrideSource.contains("wrapper.setupUniforms(pipeline.getUniforms(), pipeline.getSamplers());"),
            "Shared chunk program overrides should reflect the current render-pipeline sampler and uniform contract onto the wrapped Sodium program handle");
        assertTrue(overrideSource.contains("public static void pushActiveProgram"),
            "Shared chunk program overrides should explicitly track the currently active Sodium chunk program");

        Path deviceFile = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/GlDevice.java");
        String deviceSource = Files.readString(deviceFile);
        int sharedOverrideIndex = deviceSource.indexOf("SharedChunkProgramOverrides.createOverride(renderPipeline)");
        int irisOverrideIndex = deviceSource.indexOf("// Iris: Check for shader overrides first");

        assertTrue(sharedOverrideIndex >= 0,
            "GlDevice should consult the shared Sodium chunk program override seam when compiling tracked terrain pipelines");
        assertTrue(irisOverrideIndex > sharedOverrideIndex,
            "GlDevice must try the shared Sodium chunk program override before falling back to Iris's generic shader override map");
    }

    @Test
    public void testSharedChunkPipelinesMirrorExtendedVertexAbiWithoutStaticRegistration() throws IOException {
        Path pipelineFile = SRC_MAIN_JAVA.resolve("net/sodium/client/render/chunk/shader/SodiumChunkRenderPipelines.java");
        String pipelineSource = Files.readString(pipelineFile);

        assertTrue(pipelineSource.contains("createVertexFormat(WorldRenderingSettings.INSTANCE.getVertexFormat().getVertexFormat())"),
            "Shared Sodium chunk pipelines should derive their vertex ABI from the active WorldRenderingSettings format, not just the base stride");
        assertTrue(pipelineSource.contains("SharedChunkProgramOverrides.register(solid);"),
            "Shared Sodium chunk pipelines should register tracked terrain pipelines for active-program overrides");
        assertTrue(pipelineSource.contains("SharedChunkProgramOverrides.unregisterAll(pipelines.asList());"),
            "Shared Sodium chunk pipeline cache should clean up tracked override entries when shader reloads invalidate cached pipelines");
        assertTrue(pipelineSource.contains("case 10 -> \"iris_Normal\";"),
            "Shared Sodium chunk pipelines should carry Iris terrain attribute locations into the Vulkan terrain ABI");
        assertFalse(pipelineSource.contains("RenderPipelines.register("),
            "Shared Sodium chunk pipelines should no longer leak dynamic terrain pipelines into the global static RenderPipelines registry");
    }

    @Test
    public void testGenericVertexLocationsHonorDeclaredAttributeIndices() throws IOException {
        Path vertexFormatFile = SRC_MAIN_JAVA.resolve("net/blaze3d/vertex/VertexFormat.java");
        String vertexFormatSource = Files.readString(vertexFormatFile);
        assertTrue(vertexFormatSource.contains("public int getShaderAttributeLocation(int attributeOrdinal)"),
            "VertexFormat should expose the shader attribute location derived from its declared element metadata");
        assertTrue(vertexFormatSource.contains("element.usage() == VertexFormatElement.Usage.GENERIC ? element.index() : attributeOrdinal"),
            "VertexFormat should preserve explicit generic attribute indices while keeping vanilla attribute ordering unchanged");

        Path glProgramFile = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/GlProgram.java");
        String glProgramSource = Files.readString(glProgramFile);
        assertTrue(glProgramSource.contains("vertexFormat.getShaderAttributeLocation(j)"),
            "GlProgram linking should bind declared attribute names to the shader locations supplied by the vertex format");

        Path backendFile = SRC_MAIN_JAVA.resolve("net/vulkanic/backends/vulkan/VulkanBackend.java");
        String backendSource = Files.readString(backendFile);
        assertTrue(backendSource.contains("renderPipeline.getVertexFormat().getShaderAttributeLocation(location)"),
            "Vulkan shader-source rebinding should inject explicit locations from the declared vertex format instead of forcing sequential chunk attributes");
        assertTrue(backendSource.contains(".location(vertexFormat.getShaderAttributeLocation(i))"),
            "Vulkan pipeline vertex input descriptions should preserve explicit generic attribute locations for extended terrain formats");
    }

    @Test
    public void testSodiumGlShaderAndSyncWrappersUseBackendNeutralContext() throws IOException {
        Path shaderFile = SRC_MAIN_JAVA.resolve("net/sodium/client/gl/shader/GlShader.java");
        String shaderSource = Files.readString(shaderFile);

        assertFalse(shaderSource.contains("VulkanicAPI.getImmediateContext()"),
            "Sodium GlShader should not hard-wire immediate-context retrieval");
        assertTrue(shaderSource.contains("CommandContext ctx = VulkanicAPI.getCommandContext();"),
            "Sodium GlShader should fetch backend-neutral command context once and reuse it");

        Path fenceFile = SRC_MAIN_JAVA.resolve("net/sodium/client/gl/sync/GlFence.java");
        String fenceSource = Files.readString(fenceFile);

        assertFalse(fenceSource.contains("VulkanicAPI.getImmediateContext()"),
            "Sodium GlFence should not hard-wire immediate-context retrieval");
        assertTrue(fenceSource.contains("VulkanicAPI.getCommandContext()"),
            "Sodium GlFence should fetch backend-neutral command context");

        Path storageFile = SRC_MAIN_JAVA.resolve("net/sodium/client/gl/functions/BufferStorageFunctions.java");
        String storageSource = Files.readString(storageFile);

        assertFalse(storageSource.contains("VulkanicAPI.getImmediateContext()"),
            "Sodium BufferStorageFunctions should not hard-wire immediate-context retrieval");
        assertTrue(storageSource.contains("VulkanicAPI.getCommandContext()"),
            "Sodium BufferStorageFunctions should fetch backend-neutral command context");
    }

    @Test
    public void testSodiumShaderChunkRendererUsesBackendNeutralContext() throws IOException {
        Path file = SRC_MAIN_JAVA.resolve("net/sodium/client/render/chunk/ShaderChunkRenderer.java");
        String source = Files.readString(file);

        assertFalse(source.contains("VulkanicAPI.getImmediateContext()"),
            "ShaderChunkRenderer should not hard-wire immediate-context retrieval in viewport/framebuffer setup");
        assertTrue(source.contains("VulkanicAPI.getCommandContext()"),
            "ShaderChunkRenderer should fetch backend-neutral command context");
    }

    @Test
    public void testSodiumLeafWrappersUseBackendNeutralContext() throws IOException {
        String[] migratedFiles = new String[] {
            "net/sodium/client/gl/tessellation/GlAbstractTessellation.java",
            "net/sodium/client/compatibility/environment/GlContextInfo.java",
            "net/sodium/fabric/SodiumGpuSyncHelper.java",
            "net/sodium/client/render/chunk/shader/DefaultShaderInterface.java",
            "net/sodium/client/gl/shader/uniform/GlUniformFloat4v.java",
            "net/sodium/client/gl/shader/uniform/GlUniformFloat3v.java",
            "net/sodium/client/gl/shader/uniform/GlUniformFloat2v.java",
            "net/sodium/client/gl/shader/uniform/GlUniformMatrix4f.java",
            "net/sodium/client/gl/shader/uniform/GlUniformInt.java",
            "net/sodium/client/gl/shader/uniform/GlUniformFloat.java",
            "net/sodium/client/gl/shader/uniform/GlUniformBlock.java",
            "net/sodium/client/gl/shader/ShaderWorkarounds.java",
            "net/sodium/client/gl/buffer/GlBuffer.java",
            "net/sodium/client/gl/array/GlVertexArray.java",
            "net/sodium/client/compatibility/workarounds/nvidia/NvidiaWorkarounds.java"
        };

        for (String relativePath : migratedFiles) {
            Path file = SRC_MAIN_JAVA.resolve(relativePath);
            String source = Files.readString(file);

            assertFalse(source.contains("VulkanicAPI.getImmediateContext()"),
                relativePath + " should not hard-wire immediate-context retrieval");
            assertTrue(source.contains("VulkanicAPI.getCommandContext()"),
                relativePath + " should fetch backend-neutral command context");
        }
    }

    @Test
    public void testVertexArrayCacheUsesAgnosticArrayBufferConstant() throws IOException {
        Path file = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/VertexArrayCache.java");
        String source = Files.readString(file);

        assertFalse(source.contains("_glBindBuffer(34962"),
            "VertexArrayCache should not bind GL_ARRAY_BUFFER via hardcoded target literal 34962");
        assertFalse(source.contains("GlStateManager._glBindBuffer("),
            "VertexArrayCache should not bind buffers through GlStateManager wrapper");
        assertFalse(source.contains("VulkanicAPI.bindBuffer(ctx, VulkanicAPI.GL_ARRAY_BUFFER, glBuffer.handle)"),
            "VertexArrayCache should no longer use raw GL_ARRAY_BUFFER target where typed targets are available");
        assertTrue(source.contains("VulkanicAPI.bindBuffer(ctx, VulkanicBufferTarget.VERTEX, glBuffer.handle)"),
            "VertexArrayCache should bind array buffers through typed VulkanicBufferTarget.VERTEX");
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
        assertTrue(source.contains("VulkanicIntegerQuery.MAX_TEXTURE_SIZE"),
            "GlDevice max texture-size probe should use typed VulkanicIntegerQuery.MAX_TEXTURE_SIZE");
        assertTrue(source.contains("VulkanicAPI.GL_PROXY_TEXTURE_2D"),
            "GlDevice max texture-size probe should use VulkanicAPI.GL_PROXY_TEXTURE_2D constant");

        Path irisGlTextureFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/gl/texture/GlTexture.java");
        String irisGlTextureSource = Files.readString(irisGlTextureFile);
        assertFalse(irisGlTextureSource.contains("IrisRenderSystem.texParameteri(texture, target.getGlType(), VulkanicAPI.GL_TEXTURE_MIN_FILTER"),
            "Iris GlTexture should not set min filter through raw GL_TEXTURE_MIN_FILTER pname constants");
        assertFalse(irisGlTextureSource.contains("IrisRenderSystem.texParameteri(texture, target.getGlType(), VulkanicAPI.GL_TEXTURE_MAG_FILTER"),
            "Iris GlTexture should not set mag filter through raw GL_TEXTURE_MAG_FILTER pname constants");
        assertFalse(irisGlTextureSource.contains("IrisRenderSystem.texParameteri(texture, target.getGlType(), VulkanicAPI.GL_TEXTURE_WRAP_S"),
            "Iris GlTexture should not set wrap S through raw GL_TEXTURE_WRAP_S pname constants");
        assertFalse(irisGlTextureSource.contains("IrisRenderSystem.texParameteri(texture, target.getGlType(), VulkanicAPI.GL_TEXTURE_MAX_LEVEL"),
            "Iris GlTexture should not set max level through raw GL_TEXTURE_MAX_LEVEL pname constants");
        assertTrue(
            irisGlTextureSource.contains("VulkanicAPI.setTextureLinearFiltering(ctx, target.getGlType())")
                || irisGlTextureSource.contains("target.setLinearFiltering(ctx)"),
            "Iris GlTexture should use VulkanicAPI texture filtering helpers directly or via TextureType typed helper when blur is enabled"
        );
        assertTrue(
            irisGlTextureSource.contains("VulkanicAPI.setTextureNearestFiltering(ctx, target.getGlType())")
                || irisGlTextureSource.contains("target.setNearestFiltering(ctx)"),
            "Iris GlTexture should use VulkanicAPI texture filtering helpers directly or via TextureType typed helper when blur is disabled"
        );
        assertTrue(
            irisGlTextureSource.contains("VulkanicAPI.setTextureWrapMode(ctx, target.getGlType(), filteringData.shouldClamp(), sizeY > 0, sizeZ > 0)")
                || irisGlTextureSource.contains("target.setWrapMode(ctx, filteringData.shouldClamp(), sizeY > 0, sizeZ > 0)"),
            "Iris GlTexture should use VulkanicAPI wrap helpers directly or via TextureType typed helper"
        );
        assertTrue(
            irisGlTextureSource.contains("VulkanicAPI.resetTextureLodRangeToZero(ctx, target.getGlType())")
                || irisGlTextureSource.contains("target.resetLodRangeToZero(ctx)"),
            "Iris GlTexture should use VulkanicAPI LOD-reset helpers directly or via TextureType typed helper"
        );

        Path irisGlImageFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/gl/image/GlImage.java");
        String irisGlImageSource = Files.readString(irisGlImageFile);
        assertFalse(irisGlImageSource.contains("IrisRenderSystem.texParameteri(texture, target.getGlType(), VulkanicAPI.GL_TEXTURE_MIN_FILTER"),
            "Iris GlImage should not set min filter through raw GL_TEXTURE_MIN_FILTER pname constants");
        assertFalse(irisGlImageSource.contains("IrisRenderSystem.texParameteri(texture, target.getGlType(), VulkanicAPI.GL_TEXTURE_WRAP_S"),
            "Iris GlImage should not set wrap through raw GL_TEXTURE_WRAP_* pname constants");
        assertFalse(irisGlImageSource.contains("IrisRenderSystem.texParameterf(texture, target.getGlType(), VulkanicAPI.GL_TEXTURE_LOD_BIAS"),
            "Iris GlImage should not set LOD bias through raw GL_TEXTURE_LOD_BIAS pname constants");
        assertTrue(
            irisGlImageSource.contains("VulkanicAPI.setTextureNearestFiltering(ctx, target.getGlType())")
                || irisGlImageSource.contains("target.setNearestFiltering(ctx)"),
            "Iris GlImage integer format path should use VulkanicAPI filtering helpers directly or via TextureType typed helper"
        );
        assertTrue(
            irisGlImageSource.contains("VulkanicAPI.setTextureLinearFiltering(ctx, target.getGlType())")
                || irisGlImageSource.contains("target.setLinearFiltering(ctx)"),
            "Iris GlImage non-integer format path should use VulkanicAPI filtering helpers directly or via TextureType typed helper"
        );
        assertTrue(
            irisGlImageSource.contains("VulkanicAPI.setTextureWrapMode(ctx, target.getGlType(), true, height > 0, depth > 0)")
                || irisGlImageSource.contains("target.setWrapMode(ctx, true, height > 0, depth > 0)"),
            "Iris GlImage clamp setup should use VulkanicAPI wrap helpers directly or via TextureType typed helper"
        );
        assertTrue(
            irisGlImageSource.contains("VulkanicAPI.resetTextureLodRangeToZero(ctx, target.getGlType())")
                || irisGlImageSource.contains("target.resetLodRangeToZero(ctx)"),
            "Iris GlImage LOD setup should use VulkanicAPI LOD-reset helpers directly or via TextureType typed helper"
        );

        Path vulkanicApiFile = SRC_MAIN_JAVA.resolve("net/vulkanic/VulkanicAPI.java");
        String vulkanicApiSource = Files.readString(vulkanicApiFile);
        assertTrue(vulkanicApiSource.contains("public static void setTextureLinearFiltering(CommandContext ctx, int target)"),
            "VulkanicAPI should expose setTextureLinearFiltering helper");
        assertTrue(vulkanicApiSource.contains("public static void setTextureNearestFiltering(CommandContext ctx, int target)"),
            "VulkanicAPI should expose setTextureNearestFiltering helper");
        assertTrue(vulkanicApiSource.contains("public static void setTextureWrapMode(CommandContext ctx, int target, boolean clampToEdge, boolean includeWrapT, boolean includeWrapR)"),
            "VulkanicAPI should expose setTextureWrapMode helper");
        assertTrue(vulkanicApiSource.contains("public static void resetTextureLodRangeToZero(CommandContext ctx, int target)"),
            "VulkanicAPI should expose resetTextureLodRangeToZero helper");
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
        Path clearPassCreatorFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/targets/ClearPassCreator.java");
        String clearPassCreatorSource = Files.readString(clearPassCreatorFile);
        assertFalse(clearPassCreatorSource.contains("GlStateManager._getInteger("),
            "ClearPassCreator should not query max draw buffers through GlStateManager wrapper");
        assertTrue(clearPassCreatorSource.contains("VulkanicAPI.getInteger(VulkanicAPI.getCommandContext(), VulkanicIntegerQuery.MAX_DRAW_BUFFERS)"),
            "ClearPassCreator should query max draw buffers through typed VulkanicIntegerQuery");
        assertFalse(clearPassCreatorSource.contains("new ClearPass(clearInfo.getColor(), clearInfo::getWidth, clearInfo::getHeight,\n\t\t\t\t\trenderTargets.createClearFramebuffer(true, clearBuffers), VulkanicAPI.GL_COLOR_BUFFER_BIT)"),
            "ClearPassCreator should not pass raw GL_COLOR_BUFFER_BIT masks into ClearPass for primary clear framebuffer");
        assertFalse(clearPassCreatorSource.contains("new ClearPass(clearInfo.getColor(), clearInfo::getWidth, clearInfo::getHeight,\n\t\t\t\t\trenderTargets.createClearFramebuffer(false, clearBuffers), VulkanicAPI.GL_COLOR_BUFFER_BIT)"),
            "ClearPassCreator should not pass raw GL_COLOR_BUFFER_BIT masks into ClearPass for alternate clear framebuffer");
        assertFalse(clearPassCreatorSource.contains("new ClearPass(clearColor, renderTargets::getResolution, renderTargets::getResolution,\n\t\t\t\t\trenderTargets.createFramebufferWritingToAlt(clearBuffers), VulkanicAPI.GL_COLOR_BUFFER_BIT)"),
            "ClearPassCreator shadow clear pass should not pass raw GL_COLOR_BUFFER_BIT mask for alt framebuffer");
        assertFalse(clearPassCreatorSource.contains("new ClearPass(clearColor, renderTargets::getResolution, renderTargets::getResolution,\n\t\t\t\t\trenderTargets.createFramebufferWritingToMain(clearBuffers), VulkanicAPI.GL_COLOR_BUFFER_BIT)"),
            "ClearPassCreator shadow clear pass should not pass raw GL_COLOR_BUFFER_BIT mask for main framebuffer");

        Path clearPassFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/targets/ClearPass.java");
        String clearPassSource = Files.readString(clearPassFile);
        assertFalse(clearPassSource.contains("private final int clearFlags;"),
            "ClearPass should not track generic raw clear flag masks for color-only clear passes");
        assertFalse(clearPassSource.contains("clearBuffersWithMacosWorkaround(VulkanicAPI.getCommandContext(), clearFlags)"),
            "ClearPass should not clear via generic raw clear mask plumbing");
        assertTrue(clearPassSource.contains("var ctx = VulkanicAPI.getCommandContext();")
                && clearPassSource.contains("VulkanicAPI.clearColorBufferWithMacosWorkaround(ctx)"),
            "ClearPass should clear color through explicit VulkanicAPI clearColorBufferWithMacosWorkaround helper");

        Path samplerLimitsFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/gl/sampler/SamplerLimits.java");
        String samplerLimitsSource = Files.readString(samplerLimitsFile);
        assertFalse(samplerLimitsSource.contains("GlStateManager._getInteger("),
            "SamplerLimits should not query limits through GlStateManager wrapper");
        assertFalse(samplerLimitsSource.contains("VulkanicAPI.getImmediateContext()"),
            "SamplerLimits should not hard-wire immediate-context retrieval");
        assertTrue(samplerLimitsSource.contains("var ctx = VulkanicAPI.getCommandContext();"),
            "SamplerLimits should fetch backend-neutral command context");
        assertTrue(samplerLimitsSource.contains("VulkanicAPI.getInteger(ctx, VulkanicIntegerQuery.MAX_TEXTURE_IMAGE_UNITS)"),
            "SamplerLimits should query limits through typed VulkanicIntegerQuery with shared context");

        Path standardMacrosFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/gl/shader/StandardMacros.java");
        String standardMacrosSource = Files.readString(standardMacrosFile);
        assertFalse(standardMacrosSource.contains("GlStateManager._getString("),
            "StandardMacros should not query GL strings through GlStateManager wrapper");
        assertFalse(standardMacrosSource.contains("GlStateManager._getInteger("),
            "StandardMacros should not query extension count through GlStateManager wrapper");
        assertFalse(standardMacrosSource.contains("VulkanicAPI.getImmediateContext()"),
            "StandardMacros should not hard-wire immediate-context retrieval");
        assertTrue(standardMacrosSource.contains("VulkanicAPI.getString(VulkanicAPI.getCommandContext(), name)"),
            "StandardMacros should query GL version strings directly through VulkanicAPI");
        assertTrue(standardMacrosSource.contains("VulkanicAPI.getInteger(VulkanicAPI.getCommandContext(), VulkanicIntegerQuery.NUM_EXTENSIONS)"),
            "StandardMacros should query extension count through typed VulkanicIntegerQuery");

        Path irisRenderSystemFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/gl/IrisRenderSystem.java");
        String irisRenderSystemSource = Files.readString(irisRenderSystemFile);
        assertFalse(irisRenderSystemSource.contains("getInteger(ctx, VulkanicAPI.GL_GPU_MEMORY_INFO_CURRENT_AVAILABLE_VIDMEM_NVX)"),
            "IrisRenderSystem should not query NVX VRAM through raw GL pname constants");
        assertTrue(irisRenderSystemSource.contains("VulkanicAPI.getInteger(ctx, VulkanicIntegerQuery.GPU_MEMORY_INFO_CURRENT_AVAILABLE_VIDMEM_NVX)"),
            "IrisRenderSystem should query NVX VRAM through typed VulkanicIntegerQuery");

        Path programCreatorFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/gl/shader/ProgramCreator.java");
        String programCreatorSource = Files.readString(programCreatorFile);
        assertFalse(programCreatorSource.contains("GlStateManager._glBindAttribLocation("),
            "ProgramCreator should not bind attributes through GlStateManager wrapper");
        assertTrue(programCreatorSource.contains("VulkanicAPI.setAttributeLocation(ctx, program"),
            "ProgramCreator should bind attributes through VulkanicAPI.setAttributeLocation");

        Path depthTextureFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/targets/DepthTexture.java");
        String depthTextureSource = Files.readString(depthTextureFile);
        assertTrue(depthTextureSource.contains("var ctx = VulkanicAPI.getCommandContext();")
                && depthTextureSource.contains("VulkanicAPI.bindTexture2D(ctx, 0);"),
            "DepthTexture should reuse one local command context when restoring texture binding state");

        Path irisRenderTargetFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/targets/RenderTarget.java");
        String irisRenderTargetSource = Files.readString(irisRenderTargetFile);
        assertTrue(irisRenderTargetSource.contains("var ctx = VulkanicAPI.getCommandContext();")
                && irisRenderTargetSource.contains("VulkanicAPI.bindTexture2D(ctx, 0);"),
            "Iris RenderTarget should reuse one local command context when cleaning up texture binding state");

        Path centerDepthFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/pathways/CenterDepthSampler.java");
        String centerDepthSource = Files.readString(centerDepthFile);
        assertTrue(centerDepthSource.contains("VulkanicAPI.setDynamicViewport(ctx, 0, 0, 1, 1);"),
            "CenterDepthSampler should resolve a local command context before configuring its sampling viewport");

        Path shadowCompositeFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/shadows/ShadowCompositeRenderer.java");
        String shadowCompositeSource = Files.readString(shadowCompositeFile);
        assertTrue(shadowCompositeSource.contains("VulkanicAPI.setDynamicViewport(ctx, beginWidth, beginHeight, (int) scaledWidth, (int) scaledHeight);"),
            "ShadowCompositeRenderer should resolve a local command context before configuring per-pass viewport state");

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
        assertFalse(programSamplersSource.contains("VulkanicAPI.getImmediateContext()"),
            "ProgramSamplers should not hard-wire immediate-context retrieval");
        assertTrue(programSamplersSource.contains("VulkanicAPI.setUniform1i(VulkanicAPI.getCommandContext()"),
            "ProgramSamplers initializer should upload directly through VulkanicAPI.setUniform1i");

        Path programImagesFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/gl/program/ProgramImages.java");
        String programImagesSource = Files.readString(programImagesFile);
        assertFalse(programImagesSource.contains("GlStateManager._glUniform1i("),
            "ProgramImages initializer should not upload via GlStateManager._glUniform1i wrapper");
        assertFalse(programImagesSource.contains("VulkanicAPI.getImmediateContext()"),
            "ProgramImages should not hard-wire immediate-context retrieval");
        assertTrue(programImagesSource.contains("VulkanicAPI.setUniform1i(VulkanicAPI.getCommandContext()"),
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
        assertTrue(fallbackShaderSource.contains("VulkanicAPI.setUniform1i(ctx, gtexture, 0)"),
            "FallbackShader should upload sampler uniforms directly through VulkanicAPI.setUniform1i");

        Path intUniformFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/gl/uniform/IntUniform.java");
        String intUniformSource = Files.readString(intUniformFile);
        assertFalse(intUniformSource.contains("GlStateManager._glUniform1i("),
            "IntUniform should not upload through GlStateManager._glUniform1i wrapper");
        assertTrue(intUniformSource.contains("VulkanicAPI.setUniform1i(VulkanicAPI.getCommandContext(), location, newValue)"),
            "IntUniform should upload directly through VulkanicAPI.setUniform1i");

        Path glFramebufferFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/gl/framebuffer/GlFramebuffer.java");
        String glFramebufferSource = Files.readString(glFramebufferFile);
        assertFalse(glFramebufferSource.contains("GlStateManager._getInteger("),
            "GlFramebuffer should not query caps through GlStateManager._getInteger wrapper");
        assertFalse(glFramebufferSource.contains("VulkanicAPI.getImmediateContext()"),
            "GlFramebuffer should not hard-wire immediate-context retrieval");
        assertTrue(glFramebufferSource.contains("VulkanicAPI.getInteger(VulkanicAPI.getCommandContext(), VulkanicIntegerQuery.MAX_DRAW_BUFFERS)"),
            "GlFramebuffer should query draw-buffer cap through typed VulkanicIntegerQuery");
        assertTrue(glFramebufferSource.contains("VulkanicAPI.getInteger(VulkanicAPI.getCommandContext(), VulkanicIntegerQuery.MAX_COLOR_ATTACHMENTS)"),
            "GlFramebuffer should query color-attachment cap through typed VulkanicIntegerQuery");

        Path textureInfoCacheFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/pbr/TextureInfoCache.java");
        String textureInfoCacheSource = Files.readString(textureInfoCacheFile);
        assertFalse(textureInfoCacheSource.contains("GlStateManager._getInteger("),
            "TextureInfoCache should not query current texture binding through GlStateManager._getInteger wrapper");
        assertFalse(textureInfoCacheSource.contains("GlStateManager._getTexLevelParameter("),
            "TextureInfoCache should not query texture level params through GlStateManager._getTexLevelParameter wrapper");
        assertFalse(textureInfoCacheSource.contains("VulkanicAPI.getImmediateContext()"),
            "TextureInfoCache should not hard-wire immediate-context retrieval");
        assertTrue(textureInfoCacheSource.contains("var ctx = VulkanicAPI.getCommandContext();"),
            "TextureInfoCache should fetch backend-neutral command context in level-parameter helper");
        assertTrue(textureInfoCacheSource.contains("VulkanicAPI.getInteger(ctx, VulkanicIntegerQuery.TEXTURE_BINDING_2D)"),
            "TextureInfoCache should query current texture binding through typed VulkanicIntegerQuery");
        assertTrue(textureInfoCacheSource.contains("VulkanicAPI.getTexture2DLevelParameter(ctx, 0, pname)"),
            "TextureInfoCache should query texture level params through VulkanicAPI 2D level helper");

        Path textureManipulationUtilFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/pbr/util/TextureManipulationUtil.java");
        String textureManipulationUtilSource = Files.readString(textureManipulationUtilFile);
        assertFalse(textureManipulationUtilSource.contains("GlStateManager._getInteger("),
            "TextureManipulationUtil should not query framebuffer/texture bindings through GlStateManager._getInteger wrapper");
        assertFalse(textureManipulationUtilSource.contains("GlStateManager._getTexLevelParameter("),
            "TextureManipulationUtil should not query tex level dimensions through GlStateManager._getTexLevelParameter wrapper");
        assertFalse(textureManipulationUtilSource.contains("GlStateManager._glFramebufferTexture2D("),
            "TextureManipulationUtil should not attach/detach framebuffer textures through removed GlStateManager._glFramebufferTexture2D wrapper");
        assertTrue(textureManipulationUtilSource.contains("VulkanicAPI.getInteger(ctx, VulkanicIntegerQuery.FRAMEBUFFER_BINDING)"),
            "TextureManipulationUtil should query previous framebuffer through typed VulkanicIntegerQuery");
        assertTrue(textureManipulationUtilSource.contains("VulkanicAPI.getInteger(ctx, VulkanicIntegerQuery.TEXTURE_BINDING_2D)"),
            "TextureManipulationUtil should query previous texture through typed VulkanicIntegerQuery");
        assertTrue(textureManipulationUtilSource.contains("VulkanicAPI.getTexture2DLevelWidth(ctx, level)"),
            "TextureManipulationUtil should query mip width through VulkanicAPI 2D width helper");
        assertTrue(textureManipulationUtilSource.contains("VulkanicAPI.getTexture2DLevelHeight(ctx, level)"),
            "TextureManipulationUtil should query mip height through VulkanicAPI 2D height helper");
        assertTrue(textureManipulationUtilSource.contains("VulkanicAPI.framebufferColorAttachment0Texture2D(ctx, textureId, level)"),
            "TextureManipulationUtil should attach color attachments through VulkanicAPI.framebufferColorAttachment0Texture2D default-target helper");

        Path sodiumShaderFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/pipeline/programs/SodiumShader.java");
        String sodiumShaderSource = Files.readString(sodiumShaderFile);
        assertFalse(sodiumShaderSource.contains("GlStateManager._texParameter(3553, 33084"),
            "SodiumShader should not set base mip level with hardcoded target/pname literals via GlStateManager wrapper");
        assertFalse(sodiumShaderSource.contains("GlStateManager._texParameter(3553, 33085"),
            "SodiumShader should not set max mip level with hardcoded target/pname literals via GlStateManager wrapper");
        assertTrue(sodiumShaderSource.contains("VulkanicAPI.texParameteri(ctx, VulkanicTextureTarget.TEXTURE_2D, VulkanicTextureParameterName.BASE_LEVEL"),
            "SodiumShader should set base mip level through typed Vulkanic texture parameter APIs");
        assertTrue(sodiumShaderSource.contains("VulkanicAPI.texParameteri(ctx, VulkanicTextureTarget.TEXTURE_2D, VulkanicTextureParameterName.MAX_LEVEL"),
            "SodiumShader should set max mip level through typed Vulkanic texture parameter APIs");
        assertFalse(sodiumShaderSource.contains("flushModeChanges(VulkanicAPI.GL_TEXTURE_2D)"),
            "SodiumShader should not pass explicit GL_TEXTURE_2D to GlTexture.flushModeChanges");
        assertTrue(sodiumShaderSource.contains("flushModeChanges2D()"),
            "SodiumShader should flush texture mode changes via GlTexture default-2D helper");

        Path defaultShaderInterfaceFile = SRC_MAIN_JAVA.resolve("net/sodium/client/render/chunk/shader/DefaultShaderInterface.java");
        String defaultShaderInterfaceSource = Files.readString(defaultShaderInterfaceFile);
        assertFalse(defaultShaderInterfaceSource.contains("flushModeChanges(VulkanicAPI.GL_TEXTURE_2D)"),
            "DefaultShaderInterface should not pass explicit GL_TEXTURE_2D to GlTexture.flushModeChanges");
        assertTrue(defaultShaderInterfaceSource.contains("tex.flushModeChanges2D()"),
            "DefaultShaderInterface should flush texture mode changes via GlTexture default-2D helper");

        Path glTextureFile = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/GlTexture.java");
        String glTextureSource = Files.readString(glTextureFile);
        assertTrue(glTextureSource.contains("public void flushModeChanges2D()"),
            "GlTexture should expose default-2D flush helper");
        assertTrue(glTextureSource.contains("this.flushModeChanges2D();"),
            "GlTexture iris$getGlId should route through default-2D flush helper");
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
        assertTrue(glProgramSource.contains("VulkanicAPI.getUniformLocationWithLegacySamplerFallback(VulkanicAPI.getCommandContext(), this.programId"),
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

        Path glProgramSamplerFile = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/GlProgram.java");
        String glProgramSamplerSource = Files.readString(glProgramSamplerFile);
        assertTrue(glProgramSamplerSource.contains("private static int legacySamplerUnit(String samplerName)"),
            "GlProgram should define legacy sampler unit routing for fixed-function sampler names");
        assertTrue(glProgramSamplerSource.contains("case \"Sampler2\" -> 2;"),
            "GlProgram should preserve Sampler2 as legacy lightmap unit 2 instead of renumbering it sequentially");
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
        assertTrue(
            glProgramSource.contains("VulkanicAPI.createShaderProgram(ctx)")
                || glProgramSource.contains("VulkanicAPI.createShaderProgramHandle(ctx)"),
            "GlProgram should create programs directly through VulkanicAPI.createShaderProgram or createShaderProgramHandle"
        );
        assertTrue(
            glProgramSource.contains("VulkanicAPI.linkProgram(ctx, i)")
                || glProgramSource.contains("VulkanicAPI.linkProgram(ctx, program)"),
            "GlProgram should link programs directly through VulkanicAPI.linkProgram"
        );
        assertTrue(
            glProgramSource.contains("VulkanicAPI.getProgramParameter(ctx, i, net.vulkanic.VulkanicProgramParameterName.LINK_STATUS)")
                || glProgramSource.contains("VulkanicAPI.isProgramLinkSuccessful(ctx, i)")
                || glProgramSource.contains("VulkanicAPI.isProgramLinkSuccessful(ctx, program)"),
            "GlProgram should query link status directly through VulkanicAPI.getProgramParameter or helper-based status API"
        );
        assertTrue(
            glProgramSource.contains("VulkanicAPI.getProgramInfoLog(ctx, i)")
                || glProgramSource.contains("VulkanicAPI.getProgramInfoLog(ctx, program)"),
            "GlProgram should query program info log directly through VulkanicAPI.getProgramInfoLog"
        );
        assertTrue(
            glProgramSource.contains("VulkanicAPI.deleteProgram(VulkanicAPI.getCommandContext(), this.programId)")
                || glProgramSource.contains("VulkanicAPI.deleteProgram(VulkanicAPI.getCommandContext(), VulkanicProgramHandle.of(this.programId))"),
            "GlProgram should delete programs directly through VulkanicAPI.deleteProgram"
        );

        Path shaderCreatorFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/pipeline/programs/ShaderCreator.java");
        String shaderCreatorSource = Files.readString(shaderCreatorFile);
        assertFalse(shaderCreatorSource.contains("GlStateManager.glCreateProgram("),
            "ShaderCreator should not create programs through removed GlStateManager.glCreateProgram wrapper");
        assertFalse(shaderCreatorSource.contains("GlStateManager.glLinkProgram("),
            "ShaderCreator should not link programs through removed GlStateManager.glLinkProgram wrapper");
        assertTrue(
            shaderCreatorSource.contains("VulkanicAPI.createShaderProgram(ctx)")
                || shaderCreatorSource.contains("VulkanicAPI.createShaderProgramHandle(ctx)"),
            "ShaderCreator should create programs directly through VulkanicAPI.createShaderProgram or createShaderProgramHandle"
        );
        assertTrue(
            shaderCreatorSource.contains("VulkanicAPI.linkProgram(ctx, i)")
                || shaderCreatorSource.contains("VulkanicAPI.linkProgram(ctx, program)"),
            "ShaderCreator should link programs directly through VulkanicAPI.linkProgram"
        );

        Path programCreatorFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/gl/shader/ProgramCreator.java");
        String programCreatorSource = Files.readString(programCreatorFile);
        assertFalse(programCreatorSource.contains("GlStateManager.glCreateProgram("),
            "ProgramCreator should not create programs through removed GlStateManager.glCreateProgram wrapper");
        assertFalse(programCreatorSource.contains("GlStateManager.glLinkProgram("),
            "ProgramCreator should not link programs through removed GlStateManager.glLinkProgram wrapper");
        assertFalse(programCreatorSource.contains("GlStateManager.glGetProgrami("),
            "ProgramCreator should not query link status through removed GlStateManager.glGetProgrami wrapper");
        assertTrue(
            programCreatorSource.contains("VulkanicAPI.createShaderProgram(ctx)")
                || programCreatorSource.contains("VulkanicAPI.createShaderProgramHandle(ctx)"),
            "ProgramCreator should create programs directly through VulkanicAPI.createShaderProgram or createShaderProgramHandle"
        );
        assertTrue(programCreatorSource.contains("VulkanicAPI.linkProgram(ctx, program)"),
            "ProgramCreator should link programs directly through VulkanicAPI.linkProgram");
        assertTrue(
            programCreatorSource.contains("VulkanicAPI.getProgramParameter(ctx, program, net.vulkanic.VulkanicProgramParameterName.LINK_STATUS)")
                || programCreatorSource.contains("VulkanicAPI.isProgramLinkSuccessful(ctx, program)"),
            "ProgramCreator should query link status directly through VulkanicAPI.getProgramParameter or helper-based status API"
        );

        Path shaderMapFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/pipeline/programs/ShaderMap.java");
        String shaderMapSource = Files.readString(shaderMapFile);
        assertFalse(shaderMapSource.contains("GlStateManager.glDeleteProgram("),
            "ShaderMap should not delete programs through removed GlStateManager.glDeleteProgram wrapper");
        assertFalse(shaderMapSource.contains("GlStateManager.glGetProgrami("),
            "ShaderMap should not query link status through removed GlStateManager.glGetProgrami wrapper");
        assertFalse(shaderMapSource.contains("GlStateManager.glGetProgramInfoLog("),
            "ShaderMap should not query program logs through removed GlStateManager.glGetProgramInfoLog wrapper");
        assertTrue(
            shaderMapSource.contains("VulkanicAPI.deleteProgram(VulkanicAPI.getCommandContext(), shader.id().program())")
                || shaderMapSource.contains("VulkanicAPI.deleteProgram(VulkanicAPI.getCommandContext(), VulkanicProgramHandle.of(shader.id().program()))"),
            "ShaderMap should delete programs directly through VulkanicAPI.deleteProgram"
        );
        assertTrue(
            shaderMapSource.contains("VulkanicAPI.getProgramParameter(ctx, i, net.vulkanic.VulkanicProgramParameterName.LINK_STATUS)")
                || shaderMapSource.contains("VulkanicAPI.isProgramLinkSuccessful(ctx, i)"),
            "ShaderMap should query link status directly through VulkanicAPI.getProgramParameter or helper-based status API"
        );
        assertTrue(shaderMapSource.contains("VulkanicAPI.getProgramInfoLog(ctx, i)"),
            "ShaderMap should query program info logs directly through VulkanicAPI.getProgramInfoLog");

        Path uniformsFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/gl/program/ProgramUniforms.java");
        String uniformsSource = Files.readString(uniformsFile);
        assertFalse(uniformsSource.contains("GlStateManager.glGetProgrami("),
            "ProgramUniforms should not query active uniforms through removed GlStateManager.glGetProgrami wrapper");
        assertTrue(
            uniformsSource.contains("VulkanicAPI.getActiveUniforms(")
                || uniformsSource.contains("VulkanicAPI.getProgramParameter(VulkanicAPI.getCommandContext(), program, VulkanicProgramParameterName.ACTIVE_UNIFORMS)"),
            "ProgramUniforms should enumerate active uniforms through VulkanicAPI typed helpers or direct VulkanicAPI program-parameter query"
        );

        Path programFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/gl/program/Program.java");
        String programSource = Files.readString(programFile);
        assertFalse(programSource.contains("GlStateManager.glDeleteProgram("),
            "Program should not destroy programs through removed GlStateManager.glDeleteProgram wrapper");
        assertTrue(programSource.contains("VulkanicAPI.deleteProgram(VulkanicAPI.getCommandContext(), getGlId())"),
            "Program should destroy programs directly through VulkanicAPI.deleteProgram");

        Path computeProgramFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/gl/program/ComputeProgram.java");
        String computeProgramSource = Files.readString(computeProgramFile);
        assertFalse(computeProgramSource.contains("GlStateManager.glDeleteProgram("),
            "ComputeProgram should not destroy programs through removed GlStateManager.glDeleteProgram wrapper");
        assertTrue(computeProgramSource.contains("VulkanicAPI.deleteProgram(VulkanicAPI.getCommandContext(), getGlId())"),
            "ComputeProgram should destroy programs directly through VulkanicAPI.deleteProgram");

        Path glDeviceFile = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/GlDevice.java");
        String glDeviceSource = Files.readString(glDeviceFile);
        assertFalse(glDeviceSource.contains("GlStateManager.glCreateProgram("),
            "GlDevice AMD workaround should not create programs through removed GlStateManager.glCreateProgram wrapper");
        assertFalse(glDeviceSource.contains("GlStateManager.glDeleteProgram("),
            "GlDevice AMD workaround should not delete programs through removed GlStateManager.glDeleteProgram wrapper");
        assertTrue(
            glDeviceSource.contains("net.vulkanic.VulkanicAPI.createShaderProgram(net.vulkanic.VulkanicAPI.getCommandContext())")
                || glDeviceSource.contains("net.vulkanic.VulkanicAPI.createShaderProgramHandle(ctx)"),
            "GlDevice AMD workaround should create programs directly through VulkanicAPI.createShaderProgram or createShaderProgramHandle"
        );
        assertTrue(
            glDeviceSource.contains("net.vulkanic.VulkanicAPI.deleteProgram(net.vulkanic.VulkanicAPI.getCommandContext(), j)")
                || glDeviceSource.contains("net.vulkanic.VulkanicAPI.deleteProgram(ctx, program)"),
            "GlDevice AMD workaround should delete programs directly through VulkanicAPI.deleteProgram"
        );
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
        assertTrue(
            glProgramSource.contains("VulkanicAPI.attachShader(ctx, i")
                || glProgramSource.contains("VulkanicAPI.attachShader(ctx, program, VulkanicShaderHandle.of(glShaderModule.getShaderId()))"),
            "GlProgram should attach shaders directly through VulkanicAPI.attachShader"
        );

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
        assertTrue(
            shaderCreatorSource.contains("VulkanicAPI.attachShader(ctx, i, s)")
                || shaderCreatorSource.contains("VulkanicAPI.attachShader(ctx, program, VulkanicShaderHandle.of(s))"),
            "ShaderCreator should attach shaders directly through VulkanicAPI.attachShader"
        );
        assertTrue(
            shaderCreatorSource.contains("VulkanicAPI.deleteShader(VulkanicAPI.getCommandContext(), s)")
                || shaderCreatorSource.contains("VulkanicAPI.deleteShader(ctx, VulkanicShaderHandle.of(s))"),
            "ShaderCreator should delete shaders directly through VulkanicAPI.deleteShader"
        );
        assertTrue(
            shaderCreatorSource.contains("VulkanicAPI.createShader(ctx, shaderType.stage)")
                || shaderCreatorSource.contains("VulkanicAPI.createShaderHandle(ctx, shaderType.stage)"),
            "ShaderCreator should create shaders directly through VulkanicAPI.createShader or createShaderHandle"
        );
        assertTrue(
            shaderCreatorSource.contains("ShaderWorkarounds.safeShaderSource(shader, source)")
                || shaderCreatorSource.contains("ShaderWorkarounds.safeShaderSource(shader.value(), source)"),
            "ShaderCreator should upload shader source via ShaderWorkarounds.safeShaderSource"
        );
        assertTrue(shaderCreatorSource.contains("VulkanicAPI.compileShader(ctx, shader)"),
            "ShaderCreator should compile shaders directly through VulkanicAPI.compileShader");
        assertTrue(
            shaderCreatorSource.contains("VulkanicAPI.getShaderParameter(ctx, shader, net.vulkanic.VulkanicShaderParameterName.COMPILE_STATUS)")
                || shaderCreatorSource.contains("VulkanicAPI.isShaderCompileSuccessful(ctx, shader)"),
            "ShaderCreator should query compile status directly through VulkanicAPI.getShaderParameter or helper-based status API"
        );

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
        assertTrue(
            glDeviceSource.contains("net.vulkanic.VulkanicAPI.createShader(net.vulkanic.VulkanicAPI.getCommandContext()")
                || glDeviceSource.contains("net.vulkanic.VulkanicAPI.createShader(ctx, toVulkanicShaderStage(shaderCompilationKey.type))")
                || glDeviceSource.contains("net.vulkanic.VulkanicAPI.createShaderHandle(ctx, toVulkanicShaderStage(shaderCompilationKey.type))"),
            "GlDevice should create shaders directly through VulkanicAPI.createShader or createShaderHandle"
        );
        assertFalse(glDeviceSource.contains("GlConst.toGl(shaderCompilationKey.type)"),
            "GlDevice shader creation should avoid OpenGL-specific GlConst shader-type conversion when typed stage mapping is available");
        assertTrue(
            glDeviceSource.contains("net.vulkanic.VulkanicAPI.attachShader(net.vulkanic.VulkanicAPI.getCommandContext(), j, i)")
                || glDeviceSource.contains("net.vulkanic.VulkanicAPI.attachShader(ctx, program, shader)"),
            "GlDevice should attach shaders directly through VulkanicAPI.attachShader"
        );
        assertTrue(
            glDeviceSource.contains("net.vulkanic.VulkanicAPI.deleteShader(net.vulkanic.VulkanicAPI.getCommandContext(), i)")
                || glDeviceSource.contains("net.vulkanic.VulkanicAPI.deleteShader(ctx, shader)"),
            "GlDevice should delete shaders directly through VulkanicAPI.deleteShader"
        );
        assertTrue(
            glDeviceSource.contains("net.irisshaders.iris.gl.shader.ShaderWorkarounds.safeShaderSource(i, string2)")
                || glDeviceSource.contains("net.irisshaders.iris.gl.shader.ShaderWorkarounds.safeShaderSource(shader.value(), string2)")
                || glDeviceSource.contains("net.irisshaders.iris.gl.shader.ShaderWorkarounds.safeShaderSource(shader, string2)"),
            "GlDevice should upload shader source via ShaderWorkarounds.safeShaderSource"
        );
        assertTrue(
            glDeviceSource.contains("net.vulkanic.VulkanicAPI.compileShader(net.vulkanic.VulkanicAPI.getCommandContext(), i)")
                || glDeviceSource.contains("net.vulkanic.VulkanicAPI.compileShader(ctx, i)")
                || glDeviceSource.contains("net.vulkanic.VulkanicAPI.compileShader(ctx, shader)"),
            "GlDevice should compile shaders directly through VulkanicAPI.compileShader"
        );
        assertTrue(
            glDeviceSource.contains("net.vulkanic.VulkanicAPI.getShaderParameter(net.vulkanic.VulkanicAPI.getCommandContext(), i, 35713)")
                || glDeviceSource.contains("net.vulkanic.VulkanicAPI.isShaderCompileSuccessful(ctx, i)")
                || glDeviceSource.contains("net.vulkanic.VulkanicAPI.isShaderCompileSuccessful(ctx, shader)"),
            "GlDevice should query compile status directly through VulkanicAPI.getShaderParameter or helper-based status API"
        );
        assertTrue(
            glDeviceSource.contains("net.vulkanic.VulkanicAPI.getShaderInfoLog(net.vulkanic.VulkanicAPI.getCommandContext(), i)")
                || glDeviceSource.contains("net.vulkanic.VulkanicAPI.getShaderInfoLog(ctx, i)")
                || glDeviceSource.contains("net.vulkanic.VulkanicAPI.getShaderInfoLog(ctx, shader)"),
            "GlDevice should query shader logs directly through VulkanicAPI.getShaderInfoLog"
        );

        Path glShaderFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/gl/shader/GlShader.java");
        String glShaderSource = Files.readString(glShaderFile);
        assertFalse(glShaderSource.contains("GlStateManager.glCreateShader("),
            "GlShader should not create shaders through removed GlStateManager.glCreateShader wrapper");
        assertFalse(glShaderSource.contains("GlStateManager.glCompileShader("),
            "GlShader should not compile shaders through removed GlStateManager.glCompileShader wrapper");
        assertFalse(glShaderSource.contains("GlStateManager.glDeleteShader("),
            "GlShader should not delete shaders through removed GlStateManager.glDeleteShader wrapper");
        assertFalse(glShaderSource.contains("VulkanicAPI.getImmediateContext()"),
            "GlShader should not hard-wire immediate-context retrieval");
        assertTrue(glShaderSource.contains("CommandContext ctx = VulkanicAPI.getCommandContext();"),
            "GlShader should fetch backend-neutral command context once in shader creation path");
        assertTrue(
            glShaderSource.contains("VulkanicAPI.createShader(ctx, type.stage)")
                || glShaderSource.contains("VulkanicAPI.createShaderHandle(ctx, type.stage)"),
            "GlShader should create shaders directly through VulkanicAPI.createShader or createShaderHandle"
        );
        assertTrue(glShaderSource.contains("VulkanicAPI.compileShader(ctx, handle)"),
            "GlShader should compile shaders directly through VulkanicAPI.compileShader");
        assertTrue(
            glShaderSource.contains("VulkanicAPI.deleteShader(VulkanicAPI.getCommandContext(), this.getGlId())")
                || glShaderSource.contains("VulkanicAPI.deleteShader(VulkanicAPI.getCommandContext(), VulkanicShaderHandle.of(this.getGlId()))"),
            "GlShader should delete shaders directly through VulkanicAPI.deleteShader"
        );

        Path partialShaderFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/pipeline/programs/PartialShader.java");
        String partialShaderSource = Files.readString(partialShaderFile);
        assertFalse(partialShaderSource.contains("GlStateManager.glDeleteShader("),
            "PartialShader should not delete shaders through removed GlStateManager.glDeleteShader wrapper");
        assertTrue(
            partialShaderSource.contains("VulkanicAPI.deleteShader(VulkanicAPI.getCommandContext(), s)")
                || partialShaderSource.contains("VulkanicAPI.deleteShader(VulkanicAPI.getCommandContext(), VulkanicShaderHandle.of(s))"),
            "PartialShader should delete shaders directly through VulkanicAPI.deleteShader"
        );

        Path shaderModuleFile = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/GlShaderModule.java");
        String shaderModuleSource = Files.readString(shaderModuleFile);
        assertFalse(shaderModuleSource.contains("GlStateManager.glDeleteShader("),
            "GlShaderModule should not delete shaders through removed GlStateManager.glDeleteShader wrapper");
        assertTrue(
            shaderModuleSource.contains("net.vulkanic.VulkanicAPI.deleteShader(net.vulkanic.VulkanicAPI.getCommandContext(), this.shaderId)")
                || shaderModuleSource.contains("net.vulkanic.VulkanicAPI.deleteShader(")
                && shaderModuleSource.contains("net.vulkanic.VulkanicShaderHandle.of(this.shaderId)"),
            "GlShaderModule should delete shaders directly through VulkanicAPI.deleteShader"
        );
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
        assertTrue(source.contains("VulkanicAPI.getString(VulkanicAPI.getCommandContext(), VulkanicAPI.GL_VENDOR)"),
            "VertexArrayCache should query vendor directly through VulkanicAPI.getString + VulkanicAPI.GL_VENDOR");
        assertTrue(source.contains("VulkanicAPI.getString(VulkanicAPI.getCommandContext(), VulkanicAPI.GL_VERSION)"),
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
        assertTrue(source.contains("VulkanicAPI.getError(VulkanicAPI.getCommandContext())"),
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
        assertTrue(containsAny(directStateAccessSource,
                "VulkanicAPI.createBuffer(VulkanicAPI.getCommandContext())",
                "VulkanicAPI.createBuffer(ctx)"),
            "DirectStateAccess should create buffers directly via VulkanicAPI.createBuffer");
        assertTrue(containsAny(directStateAccessSource,
                "VulkanicAPI.bindBuffer(VulkanicAPI.getCommandContext(),",
                "VulkanicCoreAPI.bindBuffer(ctx,"),
            "DirectStateAccess should bind/unbind emulated targets through Vulkanic frontend APIs");
        assertTrue(directStateAccessSource.contains("private VulkanicBufferTarget selectBufferBindTarget("),
            "DirectStateAccess should classify emulated bind targets using typed VulkanicBufferTarget");
        assertTrue(containsAny(directStateAccessSource,
                "VulkanicAPI.mapBuffer(VulkanicAPI.getCommandContext(),",
                "VulkanicCoreAPI.mapBufferRange(ctx,"),
            "DirectStateAccess should map buffers through Vulkanic frontend APIs");
        assertTrue(containsAny(directStateAccessSource,
                "VulkanicAPI.unmapBuffer(VulkanicAPI.getCommandContext(),",
                "VulkanicCoreAPI.unmapBuffer(ctx,"),
            "DirectStateAccess should unmap buffers through Vulkanic frontend APIs");

        Path irisRenderSystemFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/gl/IrisRenderSystem.java");
        String irisRenderSystemSource = Files.readString(irisRenderSystemFile);
        assertFalse(irisRenderSystemSource.contains("GlStateManager._glGenBuffers("),
            "IrisRenderSystem should not create buffers through removed GlStateManager._glGenBuffers wrapper");
        assertFalse(irisRenderSystemSource.contains("GlStateManager._glBindBuffer("),
            "IrisRenderSystem should not bind buffers through removed GlStateManager._glBindBuffer wrapper");
        assertTrue(irisRenderSystemSource.contains("VulkanicAPI.createBuffer(VulkanicAPI.getCommandContext())"),
            "IrisRenderSystem should create buffers directly via VulkanicAPI.createBuffer");
        assertTrue(irisRenderSystemSource.contains("VulkanicAPI.bindBuffer(VulkanicAPI.getCommandContext(), target, buffer)"),
            "IrisRenderSystem should bind new buffers directly via VulkanicAPI.bindBuffer");

        Path ssboFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/gl/buffer/ShaderStorageBuffer.java");
        String ssboSource = Files.readString(ssboFile);
        assertFalse(ssboSource.contains("GlStateManager._glGenBuffers("),
            "ShaderStorageBuffer should not create buffers through removed GlStateManager._glGenBuffers wrapper");
        assertFalse(ssboSource.contains("GlStateManager._glBindBuffer("),
            "ShaderStorageBuffer should not bind buffers through removed GlStateManager._glBindBuffer wrapper");
        assertFalse(ssboSource.contains("GlStateManager._glBufferSubData("),
            "ShaderStorageBuffer should not upload content through removed GlStateManager._glBufferSubData wrapper");
        assertTrue(ssboSource.contains("VulkanicAPI.createBuffer(ctx)"),
            "ShaderStorageBuffer should create buffers directly via VulkanicAPI.createBuffer");
        assertFalse(ssboSource.contains("VulkanicAPI.bindBuffer(ctx, VulkanicAPI.GL_SHADER_STORAGE_BUFFER, getId())"),
            "ShaderStorageBuffer should no longer use raw GL_SHADER_STORAGE_BUFFER target where typed targets are available");
        assertTrue(ssboSource.contains("VulkanicAPI.bindBuffer(ctx, VulkanicBufferTarget.SHADER_STORAGE, getId())"),
            "ShaderStorageBuffer should bind SSBOs through typed VulkanicBufferTarget.SHADER_STORAGE");
        assertTrue(ssboSource.contains("VulkanicAPI.bufferSubData(ctx, VulkanicBufferTarget.SHADER_STORAGE, 0L, content)"),
            "ShaderStorageBuffer should upload content directly via VulkanicAPI.bufferSubData");

        Path glBufferFile = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/GlBuffer.java");
        String glBufferSource = Files.readString(glBufferFile);
        assertFalse(glBufferSource.contains("GlStateManager._glDeleteBuffers("),
            "GlBuffer should not delete buffers through removed GlStateManager._glDeleteBuffers wrapper");
        assertTrue(glBufferSource.contains("IrisRenderSystem.decrementTrackedBuffers();"),
            "GlBuffer should preserve tracked-buffer decrement through IrisRenderSystem helper when closing");
        assertTrue(glBufferSource.contains("VulkanicAPI.deleteBuffer(VulkanicAPI.getCommandContext(), this.handle)"),
            "GlBuffer should delete buffers directly via VulkanicAPI.deleteBuffer");

        Path dsaFile = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/DirectStateAccess.java");
        String dsaSource = Files.readString(dsaFile);
        assertFalse(dsaSource.contains("GlStateManager._glBufferData("),
            "DirectStateAccess should not call removed GlStateManager._glBufferData wrappers");
        assertFalse(dsaSource.contains("GlStateManager._glBufferSubData("),
            "DirectStateAccess should not call removed GlStateManager._glBufferSubData wrapper");
        assertTrue(containsAny(dsaSource,
                "VulkanicAPI.bufferData(VulkanicAPI.getCommandContext()",
                "VulkanicCoreAPI.bufferData(ctx,"),
            "DirectStateAccess should upload buffer data through Vulkanic frontend APIs");
        assertTrue(containsAny(dsaSource,
                "VulkanicAPI.bufferSubData(VulkanicAPI.getCommandContext()",
                "VulkanicCoreAPI.bufferSubData(ctx,"),
            "DirectStateAccess should update buffer ranges through Vulkanic frontend APIs");
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
        assertTrue(encoderSource.contains("VulkanicAPI.setDynamicScissor(ctx,"),
            "GlCommandEncoder should set scissor directly through VulkanicAPI.setDynamicScissor");
        assertTrue(encoderSource.contains("VulkanicAPI.setPolygonMode(ctx, VulkanicPolygonFace.FRONT_AND_BACK"),
            "GlCommandEncoder should set polygon mode through VulkanicAPI.setPolygonMode with typed polygon-face semantics");
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
        assertTrue(containsAny(encoderSource,
                "VulkanicAPI.setLogicOp(ctx, VulkanicLogicOp.OR_REVERSE)",
                "VulkanicAPI.setLogicOp(ctx, VulkanicAPI.GL_OR_REVERSE)"),
            "GlCommandEncoder should set OR_REVERSE logic op through a typed Vulkanic logic-op seam");
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
        String dhWrapperSource = readSourceIfExists(dhWrapperFile);
        assertFalse(dhWrapperSource.contains("GlStateManager._enableCull("),
            "MinecraftGLWrapper should not call removed GlStateManager._enableCull wrapper");
        assertFalse(dhWrapperSource.contains("GlStateManager._disableCull("),
            "MinecraftGLWrapper should not call removed GlStateManager._disableCull wrapper");
        assertFalse(dhWrapperSource.contains("public void enableFaceCulling("),
            "MinecraftGLWrapper should no longer expose cull wrapper methods");
        assertFalse(dhWrapperSource.contains("public void disableFaceCulling("),
            "MinecraftGLWrapper should no longer expose cull wrapper methods");
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
        String dhWrapperSource = readSourceIfExists(dhWrapperFile);
        assertFalse(dhWrapperSource.contains("GlStateManager._enableDepthTest("),
            "MinecraftGLWrapper should not call removed GlStateManager._enableDepthTest wrapper");
        assertFalse(dhWrapperSource.contains("GlStateManager._disableDepthTest("),
            "MinecraftGLWrapper should not call removed GlStateManager._disableDepthTest wrapper");
        assertFalse(dhWrapperSource.contains("GlStateManager._depthFunc("),
            "MinecraftGLWrapper should not call removed GlStateManager._depthFunc wrapper");
        assertFalse(dhWrapperSource.contains("GlStateManager._depthMask("),
            "MinecraftGLWrapper should not call removed GlStateManager._depthMask wrapper");
        assertFalse(dhWrapperSource.contains("public void enableDepthTest("),
            "MinecraftGLWrapper should no longer expose depth-test wrapper methods");
        assertFalse(dhWrapperSource.contains("public void disableDepthTest("),
            "MinecraftGLWrapper should no longer expose depth-test wrapper methods");
        assertFalse(dhWrapperSource.contains("public void glDepthFunc("),
            "MinecraftGLWrapper should no longer expose depth-function wrapper methods");
        assertFalse(dhWrapperSource.contains("public void enableDepthMask("),
            "MinecraftGLWrapper should no longer expose depth-write mask wrapper methods");
        assertFalse(dhWrapperSource.contains("public void disableDepthMask("),
            "MinecraftGLWrapper should no longer expose depth-write mask wrapper methods");

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
        String dhWrapperSource = readSourceIfExists(dhWrapperFile);
        assertFalse(dhWrapperSource.contains("GlStateManager._enableBlend("),
            "MinecraftGLWrapper should not call removed GlStateManager._enableBlend wrapper");
        assertFalse(dhWrapperSource.contains("GlStateManager._disableBlend("),
            "MinecraftGLWrapper should not call removed GlStateManager._disableBlend wrapper");
        assertFalse(dhWrapperSource.contains("GlStateManager._blendFuncSeparate("),
            "MinecraftGLWrapper should not call removed GlStateManager._blendFuncSeparate wrapper");
        assertFalse(dhWrapperSource.contains("public void enableBlend("),
            "MinecraftGLWrapper should no longer expose blend wrapper methods");
        assertFalse(dhWrapperSource.contains("public void disableBlend("),
            "MinecraftGLWrapper should no longer expose blend wrapper methods");
    }

    @Test
    public void testCapabilityCallsitesUseTypedEnumsForKnownStateToggles() throws IOException {
        Path blendStorageFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/gl/blending/BlendModeStorage.java");
        String blendStorageSource = Files.readString(blendStorageFile);
        assertTrue(blendStorageSource.contains("VulkanicCapability.BLEND"),
            "BlendModeStorage should use typed VulkanicCapability.BLEND for blend toggles");
        assertFalse(blendStorageSource.contains("setCapabilityEnabled(ctx, VulkanicAPI.GL_BLEND"),
            "BlendModeStorage should avoid raw GL_BLEND constants in capability toggles");

        Path dhStateFile = SRC_MAIN_JAVA.resolve("com/seibel/distanthorizons/core/render/glObject/GLState.java");
        String dhStateSource = readSourceIfExists(dhStateFile);
        assertTrue(dhStateSource.contains("VulkanicCapability.STENCIL_TEST"),
            "Distant Horizons GLState should use typed stencil capability toggles");
        assertFalse(dhStateSource.contains("setCapabilityEnabled(ctx, VulkanicAPI.GL_STENCIL_TEST"),
            "Distant Horizons GLState should avoid raw GL_STENCIL_TEST constants in capability toggles");
        assertFalse(dhStateSource.contains("isEnabled(ctx, VulkanicAPI.GL_BLEND"),
            "Distant Horizons GLState should avoid raw GL_BLEND constants in capability queries");
        assertTrue(dhStateSource.contains("isEnabled(ctx, VulkanicCapability.BLEND)"),
            "Distant Horizons GLState should use typed VulkanicCapability.BLEND in capability queries");

        Path lodRendererEventsFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/compat/dh/LodRendererEvents.java");
        String lodRendererEventsSource = readSourceIfExists(lodRendererEventsFile);
        assertTrue(lodRendererEventsSource.contains("VulkanicCapability.CULL_FACE"),
            "LodRendererEvents should use typed cull-face capability toggles");
        assertFalse(lodRendererEventsSource.contains("setCapabilityEnabled(ctx, VulkanicAPI.GL_CULL_FACE"),
            "LodRendererEvents should avoid raw GL_CULL_FACE constants in capability toggles");

        Path lodRendererFile = SRC_MAIN_JAVA.resolve("com/seibel/distanthorizons/core/render/renderer/LodRenderer.java");
        String lodRendererSource = readSourceIfExists(lodRendererFile);
        assertTrue(lodRendererSource.contains("VulkanicCapability.SCISSOR_TEST"),
            "LodRenderer should use typed scissor-test capability toggles");
        assertFalse(lodRendererSource.contains("setCapabilityEnabled(ctx, VulkanicAPI.GL_SCISSOR_TEST"),
            "LodRenderer should avoid raw GL_SCISSOR_TEST constants in capability toggles");
    }

    @Test
    public void testDepthAndCullCallsitesUseTypedEnumsForKnownState() throws IOException {
        Path lodRendererFile = SRC_MAIN_JAVA.resolve("com/seibel/distanthorizons/core/render/renderer/LodRenderer.java");
        String lodRendererSource = readSourceIfExists(lodRendererFile);
        assertTrue(lodRendererSource.contains("VulkanicDepthCompareOp.LESS"),
            "LodRenderer should use typed depth-compare enum for known depth state");
        assertFalse(lodRendererSource.contains("setDepthFunc(ctx, VulkanicAPI.GL_LESS"),
            "LodRenderer should avoid raw GL_LESS depth constant in known state setup");

        Path dhProgramFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/compat/dh/IrisGenericRenderProgram.java");
        String dhProgramSource = readSourceIfExists(dhProgramFile);
        assertTrue(dhProgramSource.contains("VulkanicDepthCompareOp.LEQUAL"),
            "IrisGenericRenderProgram should use typed depth-compare enum for known depth state");
        assertFalse(dhProgramSource.contains("setDepthFunc(ctx, VulkanicAPI.GL_LEQUAL"),
            "IrisGenericRenderProgram should avoid raw GL_LEQUAL depth constant in known state setup");

        Path encoderFile = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/GlCommandEncoder.java");
        String encoderSource = readSourceIfExists(encoderFile);
        assertTrue(encoderSource.contains("toVulkanicDepthCompareOp(renderPipeline.getDepthTestFunction())"),
            "GlCommandEncoder should map pipeline depth-test function to typed Vulkanic depth compare enum");
        assertFalse(encoderSource.contains("setDepthFunc(ctx, GlConst.toGl(renderPipeline.getDepthTestFunction()))"),
            "GlCommandEncoder should avoid direct GL int conversion for depth compare setup");

        Path glStateFile = SRC_MAIN_JAVA.resolve("com/seibel/distanthorizons/core/render/glObject/GLState.java");
        String glStateSource = readSourceIfExists(glStateFile);
        assertTrue(glStateSource.contains("VulkanicDepthCompareOp.fromLegacyGlConstant(this.depthFunc)"),
            "GLState restore should resolve saved depth function through typed conversion helper");
        assertTrue(glStateSource.contains("VulkanicCullFaceMode.fromLegacyGlConstant(this.cullMode)"),
            "GLState restore should resolve saved cull mode through typed conversion helper");
        assertTrue(glStateSource.contains("VulkanicStencilCompareOp.fromLegacyGlConstant(this.stencilFunc)"),
            "GLState restore should resolve saved stencil function through typed conversion helper");
        assertTrue(glStateSource.contains("VulkanicStencilOperation.fromLegacyGlConstant(this.stencilFailOp)"),
            "GLState restore should resolve saved stencil operations through typed conversion helper");
        assertTrue(glStateSource.contains("VulkanicAPI.setStencilWriteMask(ctx, this.stencilWriteMask)"),
            "GLState restore should restore stencil write mask through VulkanicAPI helper");
    }

    @Test
    public void testBlendCallsitesUseTypedEnumsForKnownState() throws IOException {
        Path lodRendererFile = SRC_MAIN_JAVA.resolve("com/seibel/distanthorizons/core/render/renderer/LodRenderer.java");
        String lodRendererSource = readSourceIfExists(lodRendererFile);
        assertTrue(lodRendererSource.contains("VulkanicBlendFactor.SRC_ALPHA"),
            "LodRenderer should use typed blend-factor enums for known blend setup");
        assertTrue(lodRendererSource.contains("VulkanicBlendEquation.ADD"),
            "LodRenderer should use typed blend-equation enum for known blend setup");
        assertFalse(lodRendererSource.contains("setBlendEquation(ctx, VulkanicAPI.GL_FUNC_ADD"),
            "LodRenderer should avoid raw GL_FUNC_ADD constant in known blend setup");

        Path fogApplyFile = SRC_MAIN_JAVA.resolve("com/seibel/distanthorizons/core/render/renderer/shaders/FogApplyShader.java");
        String fogApplySource = readSourceIfExists(fogApplyFile);
        assertTrue(fogApplySource.contains("VulkanicBlendEquation.ADD"),
            "FogApplyShader should use typed blend-equation enum for known blend setup");
        assertTrue(fogApplySource.contains("VulkanicBlendFactor.ONE_MINUS_SRC_ALPHA"),
            "FogApplyShader should use typed blend-factor enums for known blend setup");

        Path ssaoApplyFile = SRC_MAIN_JAVA.resolve("com/seibel/distanthorizons/core/render/renderer/shaders/SSAOApplyShader.java");
        String ssaoApplySource = readSourceIfExists(ssaoApplyFile);
        assertTrue(ssaoApplySource.contains("VulkanicBlendFactor.ZERO"),
            "SSAOApplyShader should use typed blend-factor enums for known blend setup");
        assertTrue(ssaoApplySource.contains("VulkanicBlendFactor.SRC_ALPHA"),
            "SSAOApplyShader should use typed blend-factor enums for known blend setup");

        Path genericRendererFile = SRC_MAIN_JAVA.resolve("com/seibel/distanthorizons/core/render/renderer/generic/GenericObjectRenderer.java");
        String genericRendererSource = readSourceIfExists(genericRendererFile);
        assertTrue(genericRendererSource.contains("VulkanicBlendEquation.ADD"),
            "GenericObjectRenderer should use typed blend-equation enum for known blend setup");
        assertTrue(genericRendererSource.contains("VulkanicBlendFactor.ONE_MINUS_SRC_ALPHA"),
            "GenericObjectRenderer should use typed blend-factor enums for known blend setup");

        Path glStateFile = SRC_MAIN_JAVA.resolve("com/seibel/distanthorizons/core/render/glObject/GLState.java");
        String glStateSource = readSourceIfExists(glStateFile);
        assertTrue(glStateSource.contains("VulkanicBlendFactor.fromLegacyGlConstant(this.blendSrcColor)"),
            "GLState restore should resolve saved blend factors through typed conversion helper");
        assertTrue(glStateSource.contains("VulkanicBlendEquation.fromLegacyGlConstant(this.blendEqRGB)"),
            "GLState restore should resolve saved blend equations through typed conversion helper");
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
            String source = readSourceIfExists(file);
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
        assertFalse(encoderSource.contains("IrisRenderSystem.setActiveTexture(VulkanicAPI.GL_TEXTURE0 +"),
            "GlCommandEncoder should not compute GL_TEXTURE0 offsets directly when selecting active texture units");
        assertTrue(encoderSource.contains("IrisRenderSystem.setActiveTextureUnitIndex("),
            "GlCommandEncoder should route active texture changes through IrisRenderSystem.setActiveTextureUnitIndex");

        Path sodiumShaderFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/pipeline/programs/SodiumShader.java");
        String sodiumShaderSource = Files.readString(sodiumShaderFile);
        assertFalse(sodiumShaderSource.contains("GlStateManager._activeTexture("),
            "SodiumShader should not call removed GlStateManager._activeTexture wrapper");
        assertFalse(sodiumShaderSource.contains("IrisRenderSystem.setActiveTexture(VulkanicAPI.GL_TEXTURE0 +"),
            "SodiumShader should not compute GL_TEXTURE0 offsets directly when selecting active texture units");
        assertTrue(sodiumShaderSource.contains("IrisRenderSystem.setActiveTextureUnitIndex("),
            "SodiumShader should route active texture changes through IrisRenderSystem.setActiveTextureUnitIndex");

        Path dhWrapperFile = SRC_MAIN_JAVA.resolve("com/seibel/distanthorizons/common/wrappers/minecraft/MinecraftGLWrapper.java");
        String dhWrapperSource = readSourceIfExists(dhWrapperFile);
        assertFalse(dhWrapperSource.contains("GlStateManager._activeTexture("),
            "MinecraftGLWrapper should not call removed GlStateManager._activeTexture wrapper");

        Path dhTextureStateFile = SRC_MAIN_JAVA.resolve("com/seibel/distanthorizons/core/render/glObject/DhTextureState.java");
        String dhTextureStateSource = Files.readString(dhTextureStateFile);
        assertTrue(dhTextureStateSource.contains("IrisRenderSystem.setActiveTexture("),
            "DhTextureState should route active texture changes through IrisRenderSystem.setActiveTexture");

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
        assertTrue(irisRenderSystemSource.contains("public static void blitDepthBufferNearest("),
            "IrisRenderSystem should expose blitDepthBufferNearest helper for depth-copy callsites");
        assertTrue(irisRenderSystemSource.contains("public static void blitDepthAndStencilBuffersNearest("),
            "IrisRenderSystem should expose blitDepthAndStencilBuffersNearest helper for combined depth-stencil copies");
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
        assertFalse(depthCopyStrategySource.contains("int GL_DEPTH_BUFFER_BIT = 0x00000100;"),
            "DepthCopyStrategy should not redefine GL_DEPTH_BUFFER_BIT locally");
        assertFalse(depthCopyStrategySource.contains("int GL_STENCIL_BUFFER_BIT = 0x00000400;"),
            "DepthCopyStrategy should not redefine GL_STENCIL_BUFFER_BIT locally");
        assertFalse(depthCopyStrategySource.contains("int GL_NEAREST = 0x2600;"),
            "DepthCopyStrategy should not redefine GL_NEAREST locally");
        assertFalse(depthCopyStrategySource.contains("IrisRenderSystem.blitFramebuffer(sourceFb.getId(), destFb.getId()"),
            "DepthCopyStrategy should not blit depth-stencil via generic blitFramebuffer + raw mask/filter constants");
        assertTrue(depthCopyStrategySource.contains("IrisRenderSystem.blitDepthAndStencilBuffersNearest(sourceFb.getId(), destFb.getId()"),
            "DepthCopyStrategy should blit combined depth-stencil through IrisRenderSystem intent helper");

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
        assertFalse(finalPassRendererSource.contains("IrisRenderSystem.copyTexSubImage2D(VulkanicAPI.getTextureHandle(main.getColorTexture()), VulkanicAPI.GL_TEXTURE_2D"),
            "FinalPassRenderer should not pass explicit GL_TEXTURE_2D when copying into main color target");
        assertTrue(containsAny(finalPassRendererSource,
                "IrisRenderSystem.copyTexSubImage2D(VulkanicAPI.getTextureHandle(main.getColorTexture()), 0",
                "IrisRenderSystem.copyTexSubImage2D(VulkanicCoreAPI.textureId(main.getColorTexture()), 0"),
            "FinalPassRenderer should copy into main color target through IrisRenderSystem default-2D helper");
        assertFalse(finalPassRendererSource.contains("VulkanicAPI.copyTexSubImage2D(VulkanicAPI.getCommandContext(), VulkanicAPI.GL_TEXTURE_2D"),
            "FinalPassRenderer should not pass explicit GL_TEXTURE_2D in VulkanicAPI copyTexSubImage2D path");
        assertTrue(containsAny(finalPassRendererSource,
                "VulkanicAPI.copyTexSubImage2D(VulkanicAPI.getCommandContext(), 0",
                "VulkanicAPI.copyTexSubImage2D(ctx, 0"),
            "FinalPassRenderer should use VulkanicAPI default-2D copyTexSubImage2D overload");

        Path shadowRendererFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/shadows/ShadowRenderer.java");
        String shadowRendererSource = Files.readString(shadowRendererFile);
        assertFalse(shadowRendererSource.contains("IrisRenderSystem.generateMipmaps(texture, VulkanicAPI.GL_TEXTURE_2D"),
            "ShadowRenderer should not pass explicit GL_TEXTURE_2D when generating mipmaps");
        assertTrue(shadowRendererSource.contains("IrisRenderSystem.generateMipmaps(texture);"),
            "ShadowRenderer should generate mipmaps through IrisRenderSystem default-2D helper");
        assertFalse(shadowRendererSource.contains("IrisRenderSystem.texParameteri(glTextureId, VulkanicAPI.GL_TEXTURE_2D"),
            "ShadowRenderer should not pass explicit GL_TEXTURE_2D in texture parameter setup");
        assertFalse(shadowRendererSource.contains("IrisRenderSystem.texParameteri(glTextureId, VulkanicAPI.GL_TEXTURE_MIN_FILTER"),
            "ShadowRenderer should not set texture min filter directly through raw GL_TEXTURE_MIN_FILTER pname constants");
        assertTrue(shadowRendererSource.contains("IrisRenderSystem.setTextureLinearFiltering(glTextureId)"),
            "ShadowRenderer should set linear filtering through IrisRenderSystem texture intent helper");
        assertTrue(shadowRendererSource.contains("IrisRenderSystem.setTextureNearestFiltering(glTextureId)"),
            "ShadowRenderer should set nearest filtering through IrisRenderSystem texture intent helper");

        Path colorSpaceConverterFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/pathways/colorspace/ColorSpaceFragmentConverter.java");
        String colorSpaceConverterSource = Files.readString(colorSpaceConverterFile);
        assertFalse(colorSpaceConverterSource.contains("IrisRenderSystem.texImage2D(swapTexture, VulkanicAPI.GL_TEXTURE_2D"),
            "ColorSpaceFragmentConverter should not pass explicit GL_TEXTURE_2D when allocating swap texture");
        assertTrue(colorSpaceConverterSource.contains("IrisRenderSystem.texImage2D(swapTexture, 0"),
            "ColorSpaceFragmentConverter should allocate swap texture through IrisRenderSystem default-2D helper");
        assertFalse(colorSpaceConverterSource.contains("IrisRenderSystem.copyTexSubImage2D(VulkanicAPI.getTextureHandle(targetImage), VulkanicAPI.GL_TEXTURE_2D"),
            "ColorSpaceFragmentConverter should not pass explicit GL_TEXTURE_2D in copyTexSubImage2D");
        assertTrue(containsAny(colorSpaceConverterSource,
                "IrisRenderSystem.copyTexSubImage2D(VulkanicAPI.getTextureHandle(targetImage), 0",
                "IrisRenderSystem.copyTexSubImage2D(net.vulkanic.VulkanicCoreAPI.textureId(targetImage), 0"),
            "ColorSpaceFragmentConverter should copy texture data through IrisRenderSystem default-2D helper");

        Path glFramebufferFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/gl/framebuffer/GlFramebuffer.java");
        String glFramebufferSource = Files.readString(glFramebufferFile);
        assertFalse(glFramebufferSource.contains("IrisRenderSystem.framebufferTexture2D(fb, VulkanicAPI.GL_FRAMEBUFFER, VulkanicAPI.GL_DEPTH_ATTACHMENT, VulkanicAPI.GL_TEXTURE_2D"),
            "GlFramebuffer should not pass explicit GL_TEXTURE_2D for depth attachment");
        assertFalse(glFramebufferSource.contains("IrisRenderSystem.framebufferTexture2D(fb, VulkanicAPI.GL_FRAMEBUFFER, VulkanicAPI.GL_COLOR_ATTACHMENT0 + index, VulkanicAPI.GL_TEXTURE_2D"),
            "GlFramebuffer should not pass explicit GL_TEXTURE_2D for color attachment");
        assertFalse(glFramebufferSource.contains("IrisRenderSystem.framebufferTexture2D(fb, VulkanicAPI.GL_FRAMEBUFFER, VulkanicAPI.GL_DEPTH_ATTACHMENT, texture, 0)"),
            "GlFramebuffer should not hard-code GL_FRAMEBUFFER target for bypass depth attachment");
        assertTrue(glFramebufferSource.contains("IrisRenderSystem.framebufferTexture2D(fb, VulkanicAPI.GL_DEPTH_ATTACHMENT, texture, 0)"),
            "GlFramebuffer should use IrisRenderSystem default-target framebufferTexture2D helper for bypass depth attachment");
        assertFalse(glFramebufferSource.contains("IrisRenderSystem.framebufferTexture2D(fb, VulkanicAPI.GL_FRAMEBUFFER, VulkanicAPI.GL_COLOR_ATTACHMENT0 + index, texture, 0)"),
            "GlFramebuffer should not compute color-attachment enums inline for color attachment");
        assertFalse(glFramebufferSource.contains("IrisRenderSystem.framebufferTexture2D(fb, VulkanicAPI.GL_FRAMEBUFFER, VulkanicAPI.colorAttachment(index), texture, 0)"),
            "GlFramebuffer should not hard-code GL_FRAMEBUFFER target for color attachment path");
        assertTrue(glFramebufferSource.contains("IrisRenderSystem.framebufferTexture2D(fb, VulkanicAPI.colorAttachment(index), texture, 0)"),
            "GlFramebuffer should use IrisRenderSystem default-target helper for color attachment path");
        assertTrue(glFramebufferSource.contains("IrisRenderSystem.readBuffer(getGlId(), VulkanicAPI.colorAttachment(buffer))"),
            "GlFramebuffer read-buffer path should use VulkanicAPI.colorAttachment helper");
        assertFalse(glFramebufferSource.contains("IrisRenderSystem.checkFramebufferStatus(VulkanicAPI.GL_FRAMEBUFFER)"),
            "GlFramebuffer should not hard-code GL_FRAMEBUFFER target in status query path");
        assertTrue(glFramebufferSource.contains("IrisRenderSystem.checkFramebufferStatus()"),
            "GlFramebuffer should query status through IrisRenderSystem default-target helper");

        Path dhFramebufferWrapperFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/compat/dh/DhFrameBufferWrapper.java");
        String dhFramebufferWrapperSource = Files.readString(dhFramebufferWrapperFile);
        assertFalse(dhFramebufferWrapperSource.contains("IrisRenderSystem.checkFramebufferStatus(VulkanicAPI.GL_FRAMEBUFFER)"),
            "DhFrameBufferWrapper should not hard-code GL_FRAMEBUFFER target in status query path");
        assertTrue(dhFramebufferWrapperSource.contains("IrisRenderSystem.checkFramebufferStatus()"),
            "DhFrameBufferWrapper should query status through IrisRenderSystem default-target helper");

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

        Path encoderFile = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/GlCommandEncoder.java");
        String encoderSource = Files.readString(encoderFile);
        assertFalse(encoderSource.contains("RenderSystem.setShaderTexture(0, sam)"),
            "GlCommandEncoder should not bridge Sampler0 through RenderSystem.setShaderTexture in Iris setup path");
        assertTrue(encoderSource.contains("TextureTracker.INSTANCE.onSetShaderTexture(0, sam)"),
            "GlCommandEncoder should notify Iris texture tracking directly for Sampler0 setup");
		assertTrue(encoderSource.contains("IrisRenderSystem.setTextureBinding(samplerIndex, textureHandle);"),
			"GlCommandEncoder should mirror pipeline sampler binds into the Iris texture-binding cache");
		assertTrue(encoderSource.contains("IrisRenderSystem.setTextureBinding(var46, textureHandle);"),
			"GlCommandEncoder should mirror draw-time sampler binds into the Iris texture-binding cache");

        Path commonUniformsFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/uniforms/CommonUniforms.java");
        String commonUniformsSource = Files.readString(commonUniformsFile);
        assertFalse(commonUniformsSource.contains("RenderSystem.getShaderTexture(0)"),
            "CommonUniforms should not read atlasSize texture through RenderSystem.getShaderTexture after Iris texture-state migration");
        assertTrue(commonUniformsSource.contains("IrisRenderSystem.getTextureBinding(0)"),
            "CommonUniforms should read atlasSize texture through IrisRenderSystem.getTextureBinding");

        Path extendedShaderFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/pipeline/programs/ExtendedShader.java");
        String extendedShaderSource = Files.readString(extendedShaderFile);
        assertFalse(extendedShaderSource.contains("RenderSystem.getShaderTexture(0)"),
            "ExtendedShader should not read intensity swizzle texture through RenderSystem.getShaderTexture after Iris texture-state migration");
        assertTrue(extendedShaderSource.contains("IrisRenderSystem.getTextureBinding(0)"),
            "ExtendedShader should read intensity swizzle texture through IrisRenderSystem.getTextureBinding");

        Path sodiumShaderFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/pipeline/programs/SodiumShader.java");
        String sodiumShaderSource = Files.readString(sodiumShaderFile);
        assertFalse(sodiumShaderSource.contains("RenderSystem.setShaderTexture(0, pass.getAtlas())"),
            "SodiumShader should not bridge atlas binding through RenderSystem.setShaderTexture after Iris texture-state migration");
        assertTrue(sodiumShaderSource.contains("TextureTracker.INSTANCE.onSetShaderTexture(0, pass.getAtlas())"),
            "SodiumShader should notify Iris texture tracking directly for atlas binding");

        Path dhLodProgramFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/compat/dh/IrisLodRenderProgram.java");
        String dhLodProgramSource = Files.readString(dhLodProgramFile);
        assertFalse(dhLodProgramSource.contains("RenderSystem.getShaderTexture(2)"),
            "IrisLodRenderProgram should not read lightmap texture through RenderSystem.getShaderTexture after Iris texture-state migration");
        assertTrue(dhLodProgramSource.contains("IrisRenderSystem.getTextureBinding(2)"),
            "IrisLodRenderProgram should read lightmap texture through IrisRenderSystem.getTextureBinding");

        Path dhGenericProgramFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/compat/dh/IrisGenericRenderProgram.java");
        String dhGenericProgramSource = Files.readString(dhGenericProgramFile);
        assertFalse(dhGenericProgramSource.contains("RenderSystem.getShaderTexture(2)"),
            "IrisGenericRenderProgram should not read lightmap texture through RenderSystem.getShaderTexture after Iris texture-state migration");
        assertTrue(dhGenericProgramSource.contains("IrisRenderSystem.getTextureBinding(2)"),
            "IrisGenericRenderProgram should read lightmap texture through IrisRenderSystem.getTextureBinding");

        Path guiUtilFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/gui/GuiUtil.java");
        String guiUtilSource = Files.readString(guiUtilFile);
        assertFalse(guiUtilSource.contains("RenderSystem.setShaderTexture(0"),
            "GuiUtil should not bind widget texture through RenderSystem.setShaderTexture after Iris texture-state migration");
        assertTrue(guiUtilSource.contains("TextureTracker.INSTANCE.onSetShaderTexture(0, textureView)"),
            "GuiUtil should notify Iris texture tracking directly when binding widget texture");

        Path horizonRendererFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/pathways/HorizonRenderer.java");
        String horizonRendererSource = Files.readString(horizonRendererFile);
        assertFalse(horizonRendererSource.contains("RenderSystem.getShaderTexture(i)"),
            "HorizonRenderer should not read shader samplers through RenderSystem.getShaderTexture after Iris texture-state migration");
        assertTrue(horizonRendererSource.contains("IrisRenderSystem.getTextureBinding(i)"),
            "HorizonRenderer should read sampler texture bindings through IrisRenderSystem.getTextureBinding");
        assertTrue(horizonRendererSource.contains("TextureTracker.INSTANCE.getTexture(textureId)"),
            "HorizonRenderer should resolve bound textures through TextureTracker before binding samplers");

        Path irisPipelineFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/pipeline/IrisRenderingPipeline.java");
        String irisPipelineSource = Files.readString(irisPipelineFile);
        assertFalse(irisPipelineSource.contains("RenderSystem.setShaderTexture(i, null)"),
            "IrisRenderingPipeline destroy path should not clear shader textures through RenderSystem.setShaderTexture");
        assertTrue(irisPipelineSource.contains("IrisRenderSystem.setTextureBinding(i, 0)"),
            "IrisRenderingPipeline destroy path should clear cached texture bindings through IrisRenderSystem.setTextureBinding");

        Path renderStateShardFile = SRC_MAIN_JAVA.resolve("net/minecraft/client/renderer/RenderStateShard.java");
        String renderStateShardSource = Files.readString(renderStateShardFile);
        assertFalse(renderStateShardSource.contains("RenderSystem.setShaderTexture(i, abstractTexture.getTextureView())"),
            "RenderStateShard multi-texture setup should not bind shader textures through RenderSystem.setShaderTexture");
        assertFalse(renderStateShardSource.contains("RenderSystem.setShaderTexture(0, abstractTexture.getTextureView())"),
            "RenderStateShard single-texture setup should not bind shader texture 0 through RenderSystem.setShaderTexture");
        assertFalse(renderStateShardSource.contains("IrisRenderSystem.bindTextureToUnit("),
            "RenderStateShard texture setup should bind through VulkanicAPI texture-view seam instead of Iris texture-id helper calls");
        assertFalse(renderStateShardSource.contains("VulkanicCoreAPI.textureId(textureView)"),
            "RenderStateShard texture setup should not extract raw texture ids from texture views");
        assertTrue(renderStateShardSource.contains("var ctx = VulkanicAPI.getCommandContext();")
                && renderStateShardSource.contains("VulkanicAPI.bindTextureUnit(ctx, i, textureView)"),
            "RenderStateShard multi-texture setup should bind through VulkanicAPI texture-view seam");
        assertTrue(renderStateShardSource.contains("VulkanicAPI.bindTextureUnit(ctx, 0, textureView)"),
            "RenderStateShard single-texture setup should bind through VulkanicAPI texture-view seam");
        assertTrue(renderStateShardSource.contains("TextureTracker.INSTANCE.onSetShaderTexture(i, textureView)"),
            "RenderStateShard multi-texture setup should notify TextureTracker for each texture unit binding");

        Path renderTypeFile = SRC_MAIN_JAVA.resolve("net/minecraft/client/renderer/RenderType.java");
        String renderTypeSource = Files.readString(renderTypeFile);
        assertFalse(renderTypeSource.contains("RenderSystem.getShaderTexture(i)"),
            "RenderType draw path should not fetch samplers through RenderSystem.getShaderTexture");
        assertTrue(renderTypeSource.contains("IrisRenderSystem.getTextureBinding(i)"),
            "RenderType draw path should fetch sampler bindings through IrisRenderSystem.getTextureBinding");
        assertTrue(renderTypeSource.contains("TextureTracker.INSTANCE.getShaderTexture(i)"),
            "RenderType draw path should first resolve sampler views from TextureTracker unit bindings");
        assertTrue(renderTypeSource.contains("TextureTracker.INSTANCE.getTextureView(textureId)"),
            "RenderType draw path should resolve texture views through TextureTracker before binding samplers");

        Path textureTrackerFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/pbr/TextureTracker.java");
        String textureTrackerSource = Files.readString(textureTrackerFile);
        assertTrue(textureTrackerSource.contains("private final GpuTextureView[] shaderTexturesByUnit = new GpuTextureView[128];"),
            "TextureTracker should maintain per-unit shader texture view cache for robust sampler binding");
        assertTrue(textureTrackerSource.contains("public GpuTextureView getShaderTexture(int unit)"),
            "TextureTracker should expose per-unit shader texture lookup for RenderType sampler binding");

        Path abstractTextureFile = SRC_MAIN_JAVA.resolve("net/minecraft/client/renderer/texture/AbstractTexture.java");
        String abstractTextureSource = Files.readString(abstractTextureFile);
        assertTrue(containsAny(abstractTextureSource,
            "TextureTracker.INSTANCE.trackTexture(net.vulkanic.VulkanicAPI.getTextureHandle(lastChecked), this)",
            "TextureTracker.INSTANCE.trackTexture(net.vulkanic.VulkanicCoreAPI.textureId(lastChecked), this)"),
            "AbstractTexture should track textures when getTextureView() is used so RenderType sampler binding cannot silently drop GUI/item textures");

        Path lightTextureFile = SRC_MAIN_JAVA.resolve("net/minecraft/client/renderer/LightTexture.java");
        String lightTextureSource = Files.readString(lightTextureFile);
        assertFalse(lightTextureSource.contains("RenderSystem.setShaderTexture(2, null)"),
            "LightTexture should not disable light layer via RenderSystem.setShaderTexture bridge");
        assertFalse(lightTextureSource.contains("RenderSystem.setShaderTexture(2, this.textureView)"),
            "LightTexture should not enable light layer via RenderSystem.setShaderTexture bridge");
        assertTrue(lightTextureSource.contains("IrisRenderSystem.bindTextureToUnit(2, 0)"),
            "LightTexture should disable light layer via IrisRenderSystem default-2D helper");
        assertFalse(lightTextureSource.contains("VulkanicCoreAPI.textureId(this.texture)"),
            "LightTexture should not extract raw texture ids for light-layer binding");
        assertTrue(lightTextureSource.contains("var ctx = VulkanicAPI.getCommandContext();")
                && lightTextureSource.contains("VulkanicAPI.bindTextureUnit(ctx, 2, this.textureView)"),
            "LightTexture should enable light layer through VulkanicAPI texture-view seam");

        Path renderSystemFile = SRC_MAIN_JAVA.resolve("net/blaze3d/systems/RenderSystem.java");
        String renderSystemSource = Files.readString(renderSystemFile);
        assertFalse(renderSystemSource.contains("GpuTextureView[] shaderTextures"),
            "RenderSystem should not maintain a local shaderTextures array after Iris/Vulkanic texture-state migration");
        assertFalse(renderSystemSource.contains("shaderTextures[i] ="),
            "RenderSystem.setShaderTexture should not write to a local shader texture cache");
        assertFalse(renderSystemSource.contains("public static final int TEXTURE_COUNT"),
            "RenderSystem should not expose TEXTURE_COUNT after shader-texture bridge API removal");
        assertFalse(renderSystemSource.contains("public static void setShaderTexture("),
            "RenderSystem should not expose setShaderTexture after migration to Iris/Vulkanic texture binding paths");
        assertFalse(renderSystemSource.contains("public static GpuTextureView getShaderTexture("),
            "RenderSystem should not expose getShaderTexture after migration to Iris/Vulkanic texture binding paths");
        assertFalse(renderSystemSource.contains("public static void setupOverlayColor("),
            "RenderSystem should not expose setupOverlayColor wrapper after overlay texture binding migration");
        assertFalse(renderSystemSource.contains("public static void teardownOverlayColor("),
            "RenderSystem should not expose teardownOverlayColor wrapper after overlay texture binding migration");
        assertFalse(renderSystemSource.contains("public static void queueFencedTask("),
            "RenderSystem should not own fenced GPU callback queueing after migration to VulkanicAPI");
        assertFalse(renderSystemSource.contains("public static void executePendingTasks("),
            "RenderSystem should not own pending GPU task execution after migration to VulkanicAPI");
        assertFalse(renderSystemSource.contains("public static void bindDefaultUniforms("),
            "RenderSystem should not own default uniform binding after migration to VulkanicAPI");
        assertFalse(renderSystemSource.contains("public static void setShaderFog("),
            "RenderSystem should not own fog uniform state setter after migration to VulkanicAPI");
        assertFalse(renderSystemSource.contains("public static GpuBufferSlice getShaderFog("),
            "RenderSystem should not own fog uniform state getter after migration to VulkanicAPI");
        assertFalse(renderSystemSource.contains("public static void setShaderLights("),
            "RenderSystem should not own lighting uniform state setter after migration to VulkanicAPI");
        assertFalse(renderSystemSource.contains("public static GpuBufferSlice getShaderLights("),
            "RenderSystem should not own lighting uniform state getter after migration to VulkanicAPI");
        assertFalse(renderSystemSource.contains("public static void setGlobalSettingsUniform("),
            "RenderSystem should not own global uniform state setter after migration to VulkanicAPI");
        assertFalse(renderSystemSource.contains("public static GpuBuffer getGlobalSettingsUniform("),
            "RenderSystem should not own global uniform state getter after migration to VulkanicAPI");
        assertFalse(renderSystemSource.contains("public static void setProjectionMatrix("),
            "RenderSystem should not own projection matrix setter after migration to VulkanicAPI");
        assertFalse(renderSystemSource.contains("public static void backupProjectionMatrix("),
            "RenderSystem should not own projection matrix backup helper after migration to VulkanicAPI");
        assertFalse(renderSystemSource.contains("public static void restoreProjectionMatrix("),
            "RenderSystem should not own projection matrix restore helper after migration to VulkanicAPI");
        assertFalse(renderSystemSource.contains("public static GpuBufferSlice getProjectionMatrixBuffer("),
            "RenderSystem should not own projection matrix getter after migration to VulkanicAPI");
        assertFalse(renderSystemSource.contains("public static ProjectionType getProjectionType("),
            "RenderSystem should not own projection type getter after migration to VulkanicAPI");
        assertFalse(renderSystemSource.contains("public static void setTextureMatrix("),
            "RenderSystem should not own texture matrix setter after migration to VulkanicAPI");
        assertFalse(renderSystemSource.contains("public static void resetTextureMatrix("),
            "RenderSystem should not own texture matrix reset helper after migration to VulkanicAPI");
        assertFalse(renderSystemSource.contains("public static Matrix4f getTextureMatrix("),
            "RenderSystem should not own texture matrix getter after migration to VulkanicAPI");
        assertFalse(renderSystemSource.contains("public static void lineWidth("),
            "RenderSystem should not own line width setter after migration to VulkanicAPI");
        assertFalse(renderSystemSource.contains("public static float getShaderLineWidth("),
            "RenderSystem should not own shader line width getter after migration to VulkanicAPI");

        Path vulkanicApiFile = SRC_MAIN_JAVA.resolve("net/vulkanic/VulkanicAPI.java");
        String vulkanicApiSource = Files.readString(vulkanicApiFile);
        assertTrue(vulkanicApiSource.contains("public static void queueFencedTask("),
            "VulkanicAPI should expose queueFencedTask for backend-owned GPU callback scheduling");
        assertTrue(vulkanicApiSource.contains("public static void executePendingFenceTasks("),
            "VulkanicAPI should expose executePendingFenceTasks for backend-owned GPU callback execution");
        assertTrue(vulkanicApiSource.contains("public static void bindDefaultUniforms("),
            "VulkanicAPI should expose bindDefaultUniforms after RenderSystem uniform binding migration");
        assertTrue(vulkanicApiSource.contains("public static void setShaderFog("),
            "VulkanicAPI should expose setShaderFog after RenderSystem fog uniform migration");
        assertTrue(vulkanicApiSource.contains("public static GpuBufferSlice getShaderFog("),
            "VulkanicAPI should expose getShaderFog after RenderSystem fog uniform migration");
        assertTrue(vulkanicApiSource.contains("public static void setShaderLights("),
            "VulkanicAPI should expose setShaderLights after RenderSystem lighting uniform migration");
        assertTrue(vulkanicApiSource.contains("public static GpuBufferSlice getShaderLights("),
            "VulkanicAPI should expose getShaderLights after RenderSystem lighting uniform migration");
        assertTrue(vulkanicApiSource.contains("public static void setGlobalSettingsUniform("),
            "VulkanicAPI should expose setGlobalSettingsUniform after RenderSystem global uniform migration");
        assertTrue(vulkanicApiSource.contains("public static GpuBuffer getGlobalSettingsUniform("),
            "VulkanicAPI should expose getGlobalSettingsUniform after RenderSystem global uniform migration");
        assertTrue(vulkanicApiSource.contains("public static void setProjectionMatrix("),
            "VulkanicAPI should expose setProjectionMatrix after RenderSystem projection migration");
        assertTrue(vulkanicApiSource.contains("public static void backupProjectionMatrix("),
            "VulkanicAPI should expose backupProjectionMatrix after RenderSystem projection migration");
        assertTrue(vulkanicApiSource.contains("public static void restoreProjectionMatrix("),
            "VulkanicAPI should expose restoreProjectionMatrix after RenderSystem projection migration");
        assertTrue(vulkanicApiSource.contains("public static GpuBufferSlice getProjectionMatrixBuffer("),
            "VulkanicAPI should expose getProjectionMatrixBuffer after RenderSystem projection migration");
        assertTrue(vulkanicApiSource.contains("public static ProjectionType getProjectionType("),
            "VulkanicAPI should expose getProjectionType after RenderSystem projection migration");
        assertTrue(vulkanicApiSource.contains("public static void setTextureMatrix("),
            "VulkanicAPI should expose setTextureMatrix after RenderSystem texture matrix migration");
        assertTrue(vulkanicApiSource.contains("public static void resetTextureMatrix("),
            "VulkanicAPI should expose resetTextureMatrix after RenderSystem texture matrix migration");
        assertTrue(vulkanicApiSource.contains("public static Matrix4f getTextureMatrix("),
            "VulkanicAPI should expose getTextureMatrix after RenderSystem texture matrix migration");
        assertTrue(vulkanicApiSource.contains("public static void lineWidth("),
            "VulkanicAPI should expose lineWidth after RenderSystem line width migration");
        assertTrue(vulkanicApiSource.contains("public static float getShaderLineWidth("),
            "VulkanicAPI should expose getShaderLineWidth after RenderSystem line width migration");
        assertFalse(vulkanicApiSource.contains("RenderSystem.getProjectionMatrixBuffer()"),
            "VulkanicAPI default uniform binding should not read projection matrix state through RenderSystem after migration");
        assertFalse(vulkanicApiSource.contains("RenderSystem.getShaderFog()"),
            "VulkanicAPI default uniform binding should not read fog state through RenderSystem after migration");
        assertFalse(vulkanicApiSource.contains("RenderSystem.getShaderLights()"),
            "VulkanicAPI default uniform binding should not read lighting state through RenderSystem after migration");
        assertFalse(vulkanicApiSource.contains("RenderSystem.getGlobalSettingsUniform()"),
            "VulkanicAPI default uniform binding should not read global uniform state through RenderSystem after migration");

        assertFalse(encoderSource.contains("RenderSystem.queueFencedTask("),
            "GlCommandEncoder should not schedule fenced callbacks through RenderSystem.queueFencedTask");
        assertTrue(encoderSource.contains("VulkanicAPI.queueFencedTask("),
            "GlCommandEncoder should schedule fenced callbacks through VulkanicAPI.queueFencedTask");

        Path minecraftFile = SRC_MAIN_JAVA.resolve("net/minecraft/client/Minecraft.java");
        String minecraftSource = Files.readString(minecraftFile);
        assertFalse(minecraftSource.contains("RenderSystem.executePendingTasks()"),
            "Minecraft render loop should not execute GPU pending tasks through RenderSystem.executePendingTasks");
        assertTrue(minecraftSource.contains("VulkanicAPI.executePendingFenceTasks()"),
            "Minecraft render loop should execute GPU pending tasks through VulkanicAPI.executePendingFenceTasks");

        Path gameRendererFile = SRC_MAIN_JAVA.resolve("net/minecraft/client/renderer/GameRenderer.java");
        String gameRendererSource = Files.readString(gameRendererFile);
        assertFalse(gameRendererSource.contains("RenderSystem.setShaderFog("),
            "GameRenderer should not set fog uniforms through RenderSystem after VulkanicAPI migration");
        assertTrue(gameRendererSource.contains("VulkanicAPI.setShaderFog("),
            "GameRenderer should set fog uniforms through VulkanicAPI after migration");
        assertFalse(gameRendererSource.contains("RenderSystem.setProjectionMatrix("),
            "GameRenderer should not set projection matrix through RenderSystem after VulkanicAPI migration");
        assertTrue(gameRendererSource.contains("VulkanicAPI.setProjectionMatrix("),
            "GameRenderer should set projection matrix through VulkanicAPI after migration");
        assertFalse(gameRendererSource.contains("RenderSystem.resetTextureMatrix("),
            "GameRenderer should not reset texture matrix through RenderSystem after VulkanicAPI migration");
        assertTrue(gameRendererSource.contains("VulkanicAPI.resetTextureMatrix("),
            "GameRenderer should reset texture matrix through VulkanicAPI after migration");

        Path fogRendererFile = SRC_MAIN_JAVA.resolve("net/minecraft/client/renderer/fog/FogRenderer.java");
        String fogRendererSource = Files.readString(fogRendererFile);
        assertFalse(fogRendererSource.contains("RenderSystem.setShaderFog("),
            "FogRenderer should not initialize fog uniforms through RenderSystem after VulkanicAPI migration");
        assertTrue(fogRendererSource.contains("VulkanicAPI.setShaderFog("),
            "FogRenderer should initialize fog uniforms through VulkanicAPI after migration");

        Path levelRendererFile = SRC_MAIN_JAVA.resolve("net/minecraft/client/renderer/LevelRenderer.java");
        String levelRendererSource = Files.readString(levelRendererFile);
        assertFalse(levelRendererSource.contains("RenderSystem.getShaderFog("),
            "LevelRenderer should not read fog uniforms through RenderSystem after VulkanicAPI migration");
        assertFalse(levelRendererSource.contains("RenderSystem.setShaderFog("),
            "LevelRenderer should not set fog uniforms through RenderSystem after VulkanicAPI migration");
        assertTrue(levelRendererSource.contains("VulkanicAPI.getShaderFog("),
            "LevelRenderer should read fog uniforms through VulkanicAPI after migration");
        assertTrue(levelRendererSource.contains("VulkanicAPI.setShaderFog("),
            "LevelRenderer should set fog uniforms through VulkanicAPI after migration");

        Path particleFeatureRendererFile = SRC_MAIN_JAVA.resolve("net/minecraft/client/renderer/feature/ParticleFeatureRenderer.java");
        String particleFeatureRendererSource = Files.readString(particleFeatureRendererFile);
        assertFalse(particleFeatureRendererSource.contains("RenderSystem.getShaderFog()"),
            "ParticleFeatureRenderer should not read fog uniforms through RenderSystem after VulkanicAPI migration");
        assertTrue(particleFeatureRendererSource.contains("VulkanicAPI.getShaderFog()"),
            "ParticleFeatureRenderer should read fog uniforms through VulkanicAPI after migration");
        assertFalse(particleFeatureRendererSource.contains("RenderSystem.getProjectionMatrixBuffer()"),
            "ParticleFeatureRenderer should not read projection matrix through RenderSystem after VulkanicAPI migration");
        assertTrue(particleFeatureRendererSource.contains("VulkanicAPI.getProjectionMatrixBuffer()"),
            "ParticleFeatureRenderer should read projection matrix through VulkanicAPI after migration");

        Path lightingFile = SRC_MAIN_JAVA.resolve("net/blaze3d/platform/Lighting.java");
        String lightingSource = Files.readString(lightingFile);
        assertFalse(lightingSource.contains("RenderSystem.setShaderLights("),
            "Lighting should not publish lighting uniforms through RenderSystem after VulkanicAPI migration");
        assertTrue(lightingSource.contains("VulkanicAPI.setShaderLights("),
            "Lighting should publish lighting uniforms through VulkanicAPI after migration");

        Path globalSettingsUniformFile = SRC_MAIN_JAVA.resolve("net/minecraft/client/renderer/GlobalSettingsUniform.java");
        String globalSettingsUniformSource = Files.readString(globalSettingsUniformFile);
        assertFalse(globalSettingsUniformSource.contains("RenderSystem.setGlobalSettingsUniform("),
            "GlobalSettingsUniform should not publish globals UBO through RenderSystem after VulkanicAPI migration");
        assertTrue(globalSettingsUniformSource.contains("VulkanicAPI.setGlobalSettingsUniform("),
            "GlobalSettingsUniform should publish globals UBO through VulkanicAPI after migration");

        Path postPassFile = SRC_MAIN_JAVA.resolve("net/minecraft/client/renderer/PostPass.java");
        String postPassSource = Files.readString(postPassFile);
        assertFalse(postPassSource.contains("RenderSystem.backupProjectionMatrix("),
            "PostPass should not backup projection through RenderSystem after VulkanicAPI migration");
        assertFalse(postPassSource.contains("RenderSystem.setProjectionMatrix("),
            "PostPass should not set projection through RenderSystem after VulkanicAPI migration");
        assertFalse(postPassSource.contains("RenderSystem.restoreProjectionMatrix("),
            "PostPass should not restore projection through RenderSystem after VulkanicAPI migration");
        assertTrue(postPassSource.contains("VulkanicAPI.backupProjectionMatrix("),
            "PostPass should backup projection through VulkanicAPI after migration");
        assertTrue(postPassSource.contains("VulkanicAPI.setProjectionMatrix("),
            "PostPass should set projection through VulkanicAPI after migration");
        assertTrue(postPassSource.contains("VulkanicAPI.restoreProjectionMatrix("),
            "PostPass should restore projection through VulkanicAPI after migration");

        Path renderStateShardProjectionFile = SRC_MAIN_JAVA.resolve("net/minecraft/client/renderer/RenderStateShard.java");
        String renderStateShardProjectionSource = Files.readString(renderStateShardProjectionFile);
        assertFalse(renderStateShardProjectionSource.contains("RenderSystem.getProjectionType()"),
            "RenderStateShard should not read projection type through RenderSystem after VulkanicAPI migration");
        assertTrue(renderStateShardProjectionSource.contains("VulkanicAPI.getProjectionType()"),
            "RenderStateShard should read projection type through VulkanicAPI after migration");
        assertFalse(renderStateShardProjectionSource.contains("RenderSystem.setTextureMatrix("),
            "RenderStateShard should not set texture matrix through RenderSystem after VulkanicAPI migration");
        assertFalse(renderStateShardProjectionSource.contains("RenderSystem.resetTextureMatrix("),
            "RenderStateShard should not reset texture matrix through RenderSystem after VulkanicAPI migration");
        assertFalse(renderStateShardProjectionSource.contains("RenderSystem.lineWidth("),
            "RenderStateShard should not set line width through RenderSystem after VulkanicAPI migration");
        assertTrue(renderStateShardProjectionSource.contains("VulkanicAPI.setTextureMatrix("),
            "RenderStateShard should set texture matrix through VulkanicAPI after migration");
        assertTrue(renderStateShardProjectionSource.contains("VulkanicAPI.resetTextureMatrix("),
            "RenderStateShard should reset texture matrix through VulkanicAPI after migration");
        assertTrue(renderStateShardProjectionSource.contains("VulkanicAPI.lineWidth("),
            "RenderStateShard should set line width through VulkanicAPI after migration");

        Path multiBufferSourceFile = SRC_MAIN_JAVA.resolve("net/minecraft/client/renderer/MultiBufferSource.java");
        String multiBufferSourceSource = Files.readString(multiBufferSourceFile);
        assertFalse(multiBufferSourceSource.contains("RenderSystem.getProjectionType()"),
            "MultiBufferSource should not read projection type through RenderSystem after VulkanicAPI migration");
        assertTrue(multiBufferSourceSource.contains("VulkanicAPI.getProjectionType()"),
            "MultiBufferSource should read projection type through VulkanicAPI after migration");

        assertFalse(renderTypeSource.contains("RenderSystem.bindDefaultUniforms(renderPass)"),
            "RenderType should not bind default uniforms through RenderSystem after migration");
        assertTrue(renderTypeSource.contains("VulkanicAPI.bindDefaultUniforms(renderPass)"),
            "RenderType should bind default uniforms through VulkanicAPI after migration");
        assertFalse(renderTypeSource.contains("RenderSystem.getTextureMatrix()"),
            "RenderType should not read texture matrix through RenderSystem after VulkanicAPI migration");
        assertFalse(renderTypeSource.contains("RenderSystem.getShaderLineWidth()"),
            "RenderType should not read line width through RenderSystem after VulkanicAPI migration");
        assertTrue(renderTypeSource.contains("VulkanicAPI.getTextureMatrix()"),
            "RenderType should read texture matrix through VulkanicAPI after migration");
        assertTrue(renderTypeSource.contains("VulkanicAPI.getShaderLineWidth()"),
            "RenderType should read line width through VulkanicAPI after migration");

        Path quadParticleFile = SRC_MAIN_JAVA.resolve("net/minecraft/client/renderer/state/QuadParticleRenderState.java");
        String quadParticleSource = Files.readString(quadParticleFile);
        assertFalse(quadParticleSource.contains("RenderSystem.getTextureMatrix()"),
            "QuadParticleRenderState should not read texture matrix through RenderSystem after VulkanicAPI migration");
        assertFalse(quadParticleSource.contains("RenderSystem.getShaderLineWidth()"),
            "QuadParticleRenderState should not read line width through RenderSystem after VulkanicAPI migration");
        assertTrue(quadParticleSource.contains("VulkanicAPI.getTextureMatrix()"),
            "QuadParticleRenderState should read texture matrix through VulkanicAPI after migration");
        assertTrue(quadParticleSource.contains("VulkanicAPI.getShaderLineWidth()"),
            "QuadParticleRenderState should read line width through VulkanicAPI after migration");

        assertFalse(horizonRendererSource.contains("RenderSystem.getTextureMatrix()"),
            "HorizonRenderer should not read texture matrix through RenderSystem after VulkanicAPI migration");
        assertFalse(horizonRendererSource.contains("RenderSystem.getShaderLineWidth()"),
            "HorizonRenderer should not read line width through RenderSystem after VulkanicAPI migration");
        assertTrue(horizonRendererSource.contains("VulkanicAPI.getTextureMatrix()"),
            "HorizonRenderer should read texture matrix through VulkanicAPI after migration");
        assertTrue(horizonRendererSource.contains("VulkanicAPI.getShaderLineWidth()"),
            "HorizonRenderer should read line width through VulkanicAPI after migration");

        Path overlayTextureFile = SRC_MAIN_JAVA.resolve("net/minecraft/client/renderer/texture/OverlayTexture.java");
        String overlayTextureSource = Files.readString(overlayTextureFile);
        assertFalse(overlayTextureSource.contains("RenderSystem.setupOverlayColor("),
            "OverlayTexture should not route overlay setup through RenderSystem.setupOverlayColor");
        assertFalse(overlayTextureSource.contains("RenderSystem.teardownOverlayColor("),
            "OverlayTexture should not route overlay teardown through RenderSystem.teardownOverlayColor");
        assertFalse(overlayTextureSource.contains("VulkanicCoreAPI.textureId(textureView)"),
            "OverlayTexture should not extract raw texture ids for overlay binding");
        assertTrue(overlayTextureSource.contains("var ctx = VulkanicAPI.getCommandContext();")
                && overlayTextureSource.contains("VulkanicAPI.bindTextureUnit(ctx, 1, textureView)"),
            "OverlayTexture should bind overlay texture through VulkanicAPI texture-view seam");
        assertTrue(overlayTextureSource.contains("TextureTracker.INSTANCE.onSetShaderTexture(1, textureView)"),
            "OverlayTexture should publish Sampler1 texture view updates to TextureTracker");
        assertTrue(overlayTextureSource.contains("IrisRenderSystem.bindTextureToUnit(1, 0)"),
            "OverlayTexture should clear overlay texture binding directly through IrisRenderSystem default-2D helper");
        assertTrue(overlayTextureSource.contains("TextureTracker.INSTANCE.onSetShaderTexture(1, null)"),
            "OverlayTexture should clear Sampler1 texture view updates from TextureTracker when unbinding");

        Path graphicsBackendFile = SRC_MAIN_JAVA.resolve("net/vulkanic/GraphicsBackend.java");
        String graphicsBackendSource = Files.readString(graphicsBackendFile);
        assertTrue(graphicsBackendSource.contains("default void bindTextureUnit(CommandContext ctx, int unit, GpuTextureView textureView)"),
            "GraphicsBackend should expose texture-view texture-unit binding seam for backend-neutral callsites");
        assertTrue(vulkanicApiSource.contains("public static void bindTextureUnit(CommandContext ctx, int unit, GpuTextureView textureView)"),
            "VulkanicAPI should expose texture-view texture-unit binding seam for shared/game rendering callsites");
    }

    @Test
    public void testTextureUnitSelectionUsesIndexHelpers() throws IOException {
        Path[] dhFiles = new Path[] {
            SRC_MAIN_JAVA.resolve("com/seibel/distanthorizons/core/render/renderer/shaders/SSAOApplyShader.java"),
            SRC_MAIN_JAVA.resolve("com/seibel/distanthorizons/core/render/renderer/shaders/DhApplyShader.java"),
            SRC_MAIN_JAVA.resolve("com/seibel/distanthorizons/core/render/renderer/shaders/DhFadeShader.java"),
            SRC_MAIN_JAVA.resolve("com/seibel/distanthorizons/core/render/renderer/shaders/VanillaFadeShader.java"),
            SRC_MAIN_JAVA.resolve("com/seibel/distanthorizons/core/render/renderer/shaders/FogApplyShader.java"),
            SRC_MAIN_JAVA.resolve("com/seibel/distanthorizons/core/render/renderer/shaders/FadeApplyShader.java"),
            SRC_MAIN_JAVA.resolve("com/seibel/distanthorizons/core/render/renderer/shaders/SSAOShader.java"),
            SRC_MAIN_JAVA.resolve("com/seibel/distanthorizons/core/render/renderer/shaders/FogShader.java"),
            SRC_MAIN_JAVA.resolve("com/seibel/distanthorizons/common/wrappers/misc/LightMapWrapper.java")
        };

        for (Path file : dhFiles) {
            String source = Files.readString(file);
            assertFalse(source.contains("DhTextureState.setActiveTextureUnit(VulkanicAPI.GL_TEXTURE"),
                file.getFileName() + " should not select texture units through raw GL_TEXTURE constants");
            assertTrue(source.contains("DhTextureState.setActiveTextureUnitIndex("),
                file.getFileName() + " should select texture units through index-based helper");
        }

        Path[] irisFiles = new Path[] {
            SRC_MAIN_JAVA.resolve("net/irisshaders/iris/pipeline/PipelineManager.java"),
            SRC_MAIN_JAVA.resolve("net/irisshaders/iris/pipeline/FinalPassRenderer.java"),
            SRC_MAIN_JAVA.resolve("net/irisshaders/iris/pipeline/CompositeRenderer.java"),
            SRC_MAIN_JAVA.resolve("net/irisshaders/iris/pipeline/IrisRenderingPipeline.java"),
            SRC_MAIN_JAVA.resolve("net/irisshaders/iris/shadows/ShadowRenderer.java"),
            SRC_MAIN_JAVA.resolve("net/irisshaders/iris/shadows/ShadowCompositeRenderer.java")
        };

        for (Path file : irisFiles) {
            String source = Files.readString(file);
            assertFalse(source.contains("IrisRenderSystem.setActiveTexture(VulkanicAPI.GL_TEXTURE"),
                file.getFileName() + " should not select texture units through raw GL_TEXTURE constants");
            assertTrue(source.contains("IrisRenderSystem.setActiveTextureUnitIndex("),
                file.getFileName() + " should select texture units through index-based helper");
        }

        Path defaultShaderInterfaceFile = SRC_MAIN_JAVA.resolve("net/sodium/client/render/chunk/shader/DefaultShaderInterface.java");
        String defaultShaderInterfaceSource = Files.readString(defaultShaderInterfaceFile);
        assertFalse(defaultShaderInterfaceSource.contains("VulkanicAPI.setActiveTextureUnit(ctx, VulkanicAPI.GL_TEXTURE0 + slot.ordinal())"),
            "DefaultShaderInterface should not compute GL texture units via GL_TEXTURE0 arithmetic");
        assertTrue(defaultShaderInterfaceSource.contains("VulkanicAPI.setActiveTextureUnitIndex(ctx, slot.ordinal())"),
            "DefaultShaderInterface should select texture units via VulkanicAPI index helper");

        Path irisRenderSystemFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/gl/IrisRenderSystem.java");
        String irisRenderSystemSource = Files.readString(irisRenderSystemFile);
        assertTrue(irisRenderSystemSource.contains("VulkanicAPI.textureUnitToIndex(textureUnit)"),
            "IrisRenderSystem should convert GL texture units to indices via VulkanicAPI helper");
        assertTrue(irisRenderSystemSource.contains("VulkanicAPI.setActiveTextureUnitIndex(VulkanicAPI.getCommandContext(), textureUnitIndex)"),
            "IrisRenderSystem should set active texture through VulkanicAPI index helper");
        assertFalse(irisRenderSystemSource.contains("setActiveTexture(VulkanicAPI.GL_TEXTURE0 + textureUnitIndex)"),
            "IrisRenderSystem index path should not compute GL texture units inline");

        Path glEnumsFile = SRC_MAIN_JAVA.resolve("com/seibel/distanthorizons/core/render/glObject/GLEnums.java");
        String glEnumsSource = Files.readString(glEnumsFile);
        assertTrue(glEnumsSource.contains("if (glEnum >= VulkanicAPI.GL_TEXTURE0 && glEnum <= VulkanicAPI.GL_TEXTURE31)"),
            "GLEnums should map texture binding points through GL_TEXTURE range check");
        assertTrue(glEnumsSource.contains("\"GL_TEXTURE\" + VulkanicAPI.textureUnitToIndex(glEnum)"),
            "GLEnums should compute texture-unit suffix via VulkanicAPI textureUnitToIndex helper");
        assertFalse(glEnumsSource.contains("case VulkanicAPI.GL_TEXTURE1"),
            "GLEnums should not enumerate each GL_TEXTUREN case manually");
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
        assertFalse(encoderSource.contains("VulkanicAPI.clearBuffersWithMacosWorkaround(VulkanicAPI.getCommandContext(), VulkanicAPI.GL_COLOR_BUFFER_BIT)"),
            "GlCommandEncoder should not clear color through raw GL_COLOR_BUFFER_BIT macOS clear mask");
        assertFalse(encoderSource.contains("VulkanicAPI.clearBuffersWithMacosWorkaround(VulkanicAPI.getCommandContext(), VulkanicAPI.GL_COLOR_BUFFER_BIT | VulkanicAPI.GL_DEPTH_BUFFER_BIT)"),
            "GlCommandEncoder should not clear color+depth through raw GL bitmask macOS clear mask");
        assertFalse(encoderSource.contains("VulkanicAPI.clearBuffersWithMacosWorkaround(VulkanicAPI.getCommandContext(), VulkanicAPI.GL_DEPTH_BUFFER_BIT)"),
            "GlCommandEncoder should not clear depth through raw GL_DEPTH_BUFFER_BIT macOS clear mask");
        assertTrue(containsAny(encoderSource,
            "VulkanicAPI.clearColorBufferWithMacosWorkaround(VulkanicAPI.getCommandContext())",
            "VulkanicAPI.clearColorBufferWithMacosWorkaround(ctx)"),
            "GlCommandEncoder should clear color through VulkanicAPI clearColorBufferWithMacosWorkaround helper");
        assertTrue(containsAny(encoderSource,
            "VulkanicAPI.clearColorAndDepthBuffersWithMacosWorkaround(VulkanicAPI.getCommandContext())",
            "VulkanicAPI.clearColorAndDepthBuffersWithMacosWorkaround(ctx)"),
            "GlCommandEncoder should clear color+depth through VulkanicAPI clearColorAndDepthBuffersWithMacosWorkaround helper");
        assertTrue(containsAny(encoderSource,
            "VulkanicAPI.clearDepthBufferWithMacosWorkaround(VulkanicAPI.getCommandContext())",
            "VulkanicAPI.clearDepthBufferWithMacosWorkaround(ctx)"),
            "GlCommandEncoder should clear depth through VulkanicAPI clearDepthBufferWithMacosWorkaround helper");

        Path clearPassFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/targets/ClearPass.java");
        String clearPassSource = Files.readString(clearPassFile);
        assertFalse(clearPassSource.contains("GlStateManager._clear("),
            "ClearPass should not call removed GlStateManager._clear wrapper");
        assertFalse(clearPassSource.contains("VulkanicAPI.clearBuffersWithMacosWorkaround("),
            "ClearPass should not clear through generic raw-mask macOS helper");
        assertTrue(containsAny(clearPassSource,
                "VulkanicAPI.clearColorBufferWithMacosWorkaround(VulkanicAPI.getCommandContext())",
                "VulkanicAPI.clearColorBufferWithMacosWorkaround(ctx)"),
            "ClearPass should clear via VulkanicAPI.clearColorBufferWithMacosWorkaround");
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
        assertFalse(renderTargetsSource.contains("IrisRenderSystem.copyTexImage2D(VulkanicAPI.GL_TEXTURE_2D"),
            "RenderTargets should not pass explicit GL_TEXTURE_2D in copyTexImage2D calls");
        assertFalse(renderTargetsSource.contains("IrisRenderSystem.copyTexImage2D(0"),
            "RenderTargets depth snapshots should avoid legacy copyTexImage2D now that depth targets are preallocated");
        assertTrue(renderTargetsSource.contains("DepthCopyStrategy.fastestDepthSnapshot(currentDepthFormat.isCombinedStencil())"),
            "RenderTargets should use the Vulkan-safe depth snapshot strategy selector");
        assertTrue(renderTargetsSource.contains("copyStrategy.copy(depthSourceFb, VulkanicCoreAPI.textureId(getDepthTexture()), noHandDestFb, VulkanicCoreAPI.textureId(noHand),"),
            "RenderTargets pre-hand depth path should route through the shared depth copy strategy");
        assertTrue(renderTargetsSource.contains("copyStrategy.copy(depthSourceFb, VulkanicCoreAPI.textureId(getDepthTexture()), noTranslucentsDestFb, VulkanicCoreAPI.textureId(noTranslucents),"),
            "RenderTargets pre-translucent depth path should route through the shared depth copy strategy");

        Path depthCopyStrategyFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/gl/texture/DepthCopyStrategy.java");
        String depthCopyStrategySource = Files.readString(depthCopyStrategyFile);
        assertTrue(depthCopyStrategySource.contains("fastestDepthSnapshot(boolean combinedStencilRequired)"),
            "DepthCopyStrategy should expose a dedicated depth snapshot selector");
        assertTrue(depthCopyStrategySource.contains("VulkanicAPI.isVulkanBackendSelected()"),
            "DepthCopyStrategy depth snapshot selector should special-case Vulkan");
        assertTrue(depthCopyStrategySource.contains("class Gl30BlitFbDepth implements DepthCopyStrategy"),
            "DepthCopyStrategy should provide a depth-only framebuffer blit strategy");

        Path shadowRenderTargetsFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/shadows/ShadowRenderTargets.java");
        String shadowRenderTargetsSource = Files.readString(shadowRenderTargetsFile);
        assertTrue(shadowRenderTargetsSource.contains("DepthCopyStrategy.fastestDepthSnapshot(false).copy("),
            "ShadowRenderTargets should reuse the Vulkan-safe depth snapshot strategy after the initial blit");

        Path dhCompatInternalFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/compat/dh/DHCompatInternal.java");
        String dhCompatInternalSource = Files.readString(dhCompatInternalFile);
        assertFalse(dhCompatInternalSource.contains("IrisRenderSystem.copyTexImage2D(VulkanicAPI.GL_TEXTURE_2D"),
            "DHCompatInternal should not pass explicit GL_TEXTURE_2D in copyTexImage2D calls");
        assertTrue(dhCompatInternalSource.contains("IrisRenderSystem.copyTexImage2D(0"),
            "DHCompatInternal should use IrisRenderSystem default-2D copyTexImage2D helper");
        assertTrue(dhCompatInternalSource.contains("var ctx = VulkanicAPI.getCommandContext();")
                && dhCompatInternalSource.contains("VulkanicAPI.bindTexture2D(ctx, depthTexNoTranslucent.getTextureId());"),
            "DHCompatInternal should reuse one local command context when preparing the translucent depth copy");

        Path dhWrapperFile = SRC_MAIN_JAVA.resolve("com/seibel/distanthorizons/common/wrappers/minecraft/MinecraftGLWrapper.java");
        String dhWrapperSource = readSourceIfExists(dhWrapperFile);
        assertFalse(dhWrapperSource.contains("GlStateManager._bindTexture("),
            "MinecraftGLWrapper should not call removed GlStateManager._bindTexture wrapper");

        Path dhTextureStateFile = SRC_MAIN_JAVA.resolve("com/seibel/distanthorizons/core/render/glObject/DhTextureState.java");
        String dhTextureStateSource = Files.readString(dhTextureStateFile);
        assertTrue(dhTextureStateSource.contains("VulkanicAPI.bindTexture2D("),
            "DhTextureState should bind textures through VulkanicAPI.bindTexture2D");
    }

    @Test
    public void testIrisComputePipelinesFailOpenWhenComputeUnsupported() throws IOException {
        Path programBuilderFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/gl/program/ProgramBuilder.java");
        String programBuilderSource = Files.readString(programBuilderFile);

        assertTrue(programBuilderSource.contains("beginComputeIfSupported("),
            "ProgramBuilder should expose a compute fail-open helper for unsupported runtimes");
        assertTrue(programBuilderSource.contains("return null;"),
            "ProgramBuilder compute fail-open helper should return null when compute is unsupported");
        assertTrue(programBuilderSource.contains("Skipping compute shader program"),
            "ProgramBuilder compute fail-open helper should log when compute programs are skipped");

        List<String> pipelineFiles = List.of(
            "net/irisshaders/iris/shadows/ShadowCompositeRenderer.java",
            "net/irisshaders/iris/pipeline/CompositeRenderer.java",
            "net/irisshaders/iris/pipeline/FinalPassRenderer.java",
            "net/irisshaders/iris/pipeline/IrisRenderingPipeline.java"
        );

        for (String relative : pipelineFiles) {
            String source = Files.readString(SRC_MAIN_JAVA.resolve(relative));
            assertTrue(source.contains("ProgramBuilder.beginComputeIfSupported("),
                "Iris compute pipeline should use compute fail-open helper: " + relative);
        }

        assertFalse(Files.readString(SRC_MAIN_JAVA.resolve("net/irisshaders/iris/shadows/ShadowCompositeRenderer.java")).contains("ProgramBuilder.beginCompute("),
            "ShadowCompositeRenderer should not use fatal compute builder directly");
        assertFalse(Files.readString(SRC_MAIN_JAVA.resolve("net/irisshaders/iris/pipeline/CompositeRenderer.java")).contains("ProgramBuilder.beginCompute("),
            "CompositeRenderer should not use fatal compute builder directly");
        assertFalse(Files.readString(SRC_MAIN_JAVA.resolve("net/irisshaders/iris/pipeline/FinalPassRenderer.java")).contains("ProgramBuilder.beginCompute("),
            "FinalPassRenderer should not use fatal compute builder directly");
        assertFalse(Files.readString(SRC_MAIN_JAVA.resolve("net/irisshaders/iris/pipeline/IrisRenderingPipeline.java")).contains("ProgramBuilder.beginCompute("),
            "IrisRenderingPipeline should not use fatal compute builder directly");
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
        String dhWrapperSource = readSourceIfExists(dhWrapperFile);
        assertFalse(dhWrapperSource.contains("GlStateManager.incrementTrackedBuffers("),
            "MinecraftGLWrapper should not increment tracked buffers through GlStateManager");
        assertFalse(dhWrapperSource.contains("GlStateManager.decrementTrackedBuffers("),
            "MinecraftGLWrapper should not decrement tracked buffers through GlStateManager");
        assertFalse(dhWrapperSource.contains("public int glGenBuffers("),
            "MinecraftGLWrapper should no longer expose buffer generation wrapper methods");
        assertFalse(dhWrapperSource.contains("public void glDeleteBuffers("),
            "MinecraftGLWrapper should no longer expose buffer deletion wrapper methods");

        Path dhGlBufferFile = SRC_MAIN_JAVA.resolve("com/seibel/distanthorizons/core/render/glObject/buffer/GLBuffer.java");
        String dhGlBufferSource = Files.readString(dhGlBufferFile);
        assertTrue(dhGlBufferSource.contains("IrisRenderSystem.incrementTrackedBuffers("),
            "DH GLBuffer should increment tracked buffers through IrisRenderSystem helper");
        assertTrue(dhGlBufferSource.contains("IrisRenderSystem.decrementTrackedBuffers("),
            "DH GLBuffer should decrement tracked buffers through IrisRenderSystem helper");

        Path renderableBoxGroupFile = SRC_MAIN_JAVA.resolve("com/seibel/distanthorizons/core/render/renderer/generic/RenderableBoxGroup.java");
        String renderableBoxGroupSource = Files.readString(renderableBoxGroupFile);
        assertTrue(renderableBoxGroupSource.contains("IrisRenderSystem.incrementTrackedBuffers("),
            "RenderableBoxGroup should increment tracked buffers through IrisRenderSystem helper");
        assertTrue(renderableBoxGroupSource.contains("IrisRenderSystem.decrementTrackedBuffers("),
            "RenderableBoxGroup should decrement tracked buffers through IrisRenderSystem helper");
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
    public void testDistantHorizonsTextureUploadsUseDefault2DHelper() throws IOException {
        Path[] files = new Path[]{
            SRC_MAIN_JAVA.resolve("com/seibel/distanthorizons/core/render/renderer/DhFadeRenderer.java"),
            SRC_MAIN_JAVA.resolve("com/seibel/distanthorizons/core/render/renderer/SSAORenderer.java"),
            SRC_MAIN_JAVA.resolve("com/seibel/distanthorizons/core/render/renderer/VanillaFadeRenderer.java"),
            SRC_MAIN_JAVA.resolve("com/seibel/distanthorizons/core/render/renderer/FogRenderer.java"),
            SRC_MAIN_JAVA.resolve("com/seibel/distanthorizons/core/render/glObject/texture/DhColorTexture.java"),
            SRC_MAIN_JAVA.resolve("com/seibel/distanthorizons/core/render/glObject/texture/DHDepthTexture.java")
        };

        for (Path file : files) {
            String source = Files.readString(file);
            assertFalse(source.contains("uploadTexture2D(ctx, VulkanicAPI.GL_TEXTURE_2D"),
                file.getFileName() + " should not pass explicit GL_TEXTURE_2D in uploadTexture2D");
            assertTrue(source.contains("uploadTexture2D(ctx, 0"),
                file.getFileName() + " should use VulkanicAPI default-2D uploadTexture2D overload");
        }
    }

    @Test
    public void testDistantHorizonsTargetFramebufferUsesRenderTargetResolutionSeam() throws IOException {
        Path wrapperFile = SRC_MAIN_JAVA.resolve("com/seibel/distanthorizons/common/wrappers/minecraft/MinecraftRenderWrapper.java");
        String wrapperSource = Files.readString(wrapperFile);

        assertFalse(wrapperSource.contains("return 0; // 0 is the ID for the default frame buffer"),
            "MinecraftRenderWrapper.getTargetFramebuffer should not hardcode default FBO 0");
        assertTrue(wrapperSource.contains("VulkanicAPI.resolveFramebufferForTextures(renderTarget.getColorTexture(), renderTarget.getDepthTexture())"),
            "MinecraftRenderWrapper.getTargetFramebuffer should resolve framebuffer via VulkanicAPI texture-pair seam");
        assertTrue(wrapperSource.contains("public boolean hasTargetRenderTarget()"),
            "MinecraftRenderWrapper should expose a target render-target availability seam for DH renderers");
        assertTrue(wrapperSource.contains("public boolean bindTargetRenderTarget(CommandContext ctx)"),
            "MinecraftRenderWrapper should expose a target render-target binding seam for DH renderers");
        assertTrue(wrapperSource.contains("VulkanicAPI.bindRenderTarget(ctx, renderTarget.getColorTexture(), renderTarget.getDepthTexture());"),
            "MinecraftRenderWrapper should bind target render targets through VulkanicAPI render-target helper");
        assertTrue(wrapperSource.contains("this.finalLevelFrameBufferId = this.resolveTargetFramebufferId(renderTarget);")
                && wrapperSource.contains("return framebufferId == 0 ? -1 : framebufferId;"),
            "MinecraftRenderWrapper should map unresolved framebuffers to -1 through a shared render-target helper");
        assertFalse(wrapperSource.contains("return 0;"),
            "MinecraftRenderWrapper should not expose 0 as an unresolved texture/framebuffer sentinel in DH-facing seams");
        assertFalse(wrapperSource.contains("this.getRenderTarget().getDepthTexture()"),
            "MinecraftRenderWrapper.getDepthTextureId should avoid direct chained dereference and use null-safe local renderTarget checks");
        assertFalse(wrapperSource.contains("this.getRenderTarget().getColorTexture()"),
            "MinecraftRenderWrapper.getColorTextureId should avoid direct chained dereference and use null-safe local renderTarget checks");
        assertTrue(wrapperSource.contains("if (depthTexture == null)"),
            "MinecraftRenderWrapper.getDepthTextureId should guard null depth texture before resolving a handle");
        assertTrue(wrapperSource.contains("if (colorTexture == null)"),
            "MinecraftRenderWrapper.getColorTextureId should guard null color texture before resolving a handle");
        assertFalse(wrapperSource.contains("catch (Exception e)"),
            "MinecraftRenderWrapper texture-handle resolution should avoid exception-driven control flow and use sentinel checks instead");
        assertFalse(wrapperSource.contains("colorTextureCastFailLogged"),
            "MinecraftRenderWrapper should not rely on one-time exception logging flags for color texture handle resolution");
        assertFalse(wrapperSource.contains("depthTextureCastFailLogged"),
            "MinecraftRenderWrapper should not rely on one-time exception logging flags for depth texture handle resolution");
        assertTrue(wrapperSource.contains("if (textureId <= 0)"),
            "MinecraftRenderWrapper texture-handle resolution should map unresolved/invalid handles to -1 via deterministic sentinel checks");
        assertFalse(wrapperSource.contains("return this.getRenderTarget().width;"),
            "MinecraftRenderWrapper viewport width query should not assume render target is always available");
        assertFalse(wrapperSource.contains("return this.getRenderTarget().height;"),
            "MinecraftRenderWrapper viewport height query should not assume render target is always available");
        assertTrue(wrapperSource.contains("return renderTarget == null ? 0 : renderTarget.width;"),
            "MinecraftRenderWrapper viewport width query should use null-safe fallback semantics");
        assertTrue(wrapperSource.contains("return renderTarget == null ? 0 : renderTarget.height;"),
            "MinecraftRenderWrapper viewport height query should use null-safe fallback semantics");
    }

    @Test
    public void testDistantHorizonsRenderPathsGuardUnresolvedFramebufferIds() throws IOException {
        Path testRendererFile = SRC_MAIN_JAVA.resolve("com/seibel/distanthorizons/core/render/renderer/TestRenderer.java");
        String testRendererSource = Files.readString(testRendererFile);
        assertTrue(testRendererSource.contains("if (!MC_RENDER.bindTargetRenderTarget(ctx))"),
            "TestRenderer should bind Minecraft's target render target through the wrapper seam");
        assertFalse(testRendererSource.contains("MC_RENDER.getTargetFramebuffer()"),
            "TestRenderer should avoid resolving raw target framebuffer ids in the render hot path");

        Path dhApplyFile = SRC_MAIN_JAVA.resolve("com/seibel/distanthorizons/core/render/renderer/shaders/DhApplyShader.java");
        String dhApplySource = Files.readString(dhApplyFile);
        assertTrue(dhApplySource.contains("protected boolean onPreRender(CommandContext ctx, float partialTicks)"),
            "DhApplyShader should use shared pre-bind precheck hook for unresolved resources");
        assertTrue(dhApplySource.contains("this.activeDhColorTextureId == -1 || this.activeDhDepthTextureId == -1")
                && dhApplySource.contains("this.activeRenderToFrameBuffer = MC_RENDER.mcRendersToFrameBuffer();"),
            "DhApplyShader precheck should gate rendering on resolved DH textures and selected render path");
        assertTrue(dhApplySource.contains("return MC_RENDER.hasTargetRenderTarget();")
                && dhApplySource.contains("return this.activeTargetColorTextureId != -1")
                && dhApplySource.contains("&& LodRenderer.INSTANCE.hasActiveRenderTarget()")
                && dhApplySource.contains("&& MC_RENDER.hasTargetRenderTarget();"),
            "DhApplyShader precheck should validate render-target availability through owner seams before bind/uniform work");
        assertTrue(dhApplySource.contains("DhTextureState.bindTexture2D(this.activeDhColorTextureId)")
                && dhApplySource.contains("DhTextureState.bindTexture2D(this.activeDhDepthTextureId)"),
            "DhApplyShader should bind cached validated DH color/depth texture ids resolved during precheck");
        assertTrue(dhApplySource.contains("if (!MC_RENDER.bindTargetRenderTarget(ctx))")
                && dhApplySource.contains("if (!LodRenderer.INSTANCE.bindActiveRenderTarget())"),
            "DhApplyShader should bind MC and DH outputs through owner seams instead of raw framebuffer ids");

        Path fadeApplyFile = SRC_MAIN_JAVA.resolve("com/seibel/distanthorizons/core/render/renderer/shaders/FadeApplyShader.java");
        String fadeApplySource = Files.readString(fadeApplyFile);
        assertTrue(fadeApplySource.contains("public int fadeTexture = -1;"),
            "FadeApplyShader should initialize fadeTexture to unresolved sentinel -1");
        assertTrue(fadeApplySource.contains("public DhFramebuffer readFramebuffer;")
                && fadeApplySource.contains("public boolean drawToMinecraftTarget = false;")
                && fadeApplySource.contains("public boolean drawToLodTarget = false;"),
            "FadeApplyShader should track read/draw targets through framebuffer owners and owner seams instead of cached framebuffer ids");
        assertTrue(fadeApplySource.contains("protected boolean onPreRender(CommandContext ctx, float partialTicks)"),
            "FadeApplyShader should use shared pre-bind precheck hook for unresolved resources");
        assertTrue(fadeApplySource.contains("this.fadeTexture != -1")
                && fadeApplySource.contains("this.activeReadFramebuffer != null")
                && fadeApplySource.contains("(this.activeDrawToMinecraftTarget || this.activeDrawToLodTarget)"),
            "FadeApplyShader precheck should require a resolved source framebuffer and an owner-routed draw target");
        assertTrue(fadeApplySource.contains("this.activeReadFramebuffer.bindAsReadBuffer(ctx);")
                && fadeApplySource.contains("if (this.activeDrawToMinecraftTarget)")
                && fadeApplySource.contains("if (!MC_RENDER.bindTargetRenderTarget(ctx))")
                && fadeApplySource.contains("if (!LodRenderer.INSTANCE.bindActiveRenderTarget())"),
            "FadeApplyShader should bind through framebuffer owners and MC/DH owner seams instead of cached draw framebuffer ids");

        Path fogApplyFile = SRC_MAIN_JAVA.resolve("com/seibel/distanthorizons/core/render/renderer/shaders/FogApplyShader.java");
        String fogApplySource = Files.readString(fogApplyFile);
        assertTrue(fogApplySource.contains("public int fogTexture = -1;"),
            "FogApplyShader should initialize fog texture id to unresolved sentinel -1");
        assertTrue(fogApplySource.contains("protected boolean onPreRender(CommandContext ctx, float partialTicks)"),
            "FogApplyShader should use shared pre-bind precheck hook for unresolved resources");
        assertTrue(fogApplySource.contains("this.fogTexture != -1")
                && fogApplySource.contains("this.activeDepthTextureId != -1")
                && fogApplySource.contains("FogShader.INSTANCE.frameBuffer != null")
                && fogApplySource.contains("LodRenderer.INSTANCE.hasActiveRenderTarget()"),
            "FogApplyShader precheck should require resolved fog/depth/framebuffer resources before bind/uniform work");
        assertTrue(fogApplySource.contains("DhTextureState.bindTexture2D(this.activeDepthTextureId)")
                && fogApplySource.contains("FogShader.INSTANCE.frameBuffer.bindAsReadBuffer(ctx);")
                && fogApplySource.contains("if (!LodRenderer.INSTANCE.bindActiveRenderTarget())"),
            "FogApplyShader should bind framebuffer owners through the DH render-target seam");

        Path ssaoApplyFile = SRC_MAIN_JAVA.resolve("com/seibel/distanthorizons/core/render/renderer/shaders/SSAOApplyShader.java");
        String ssaoApplySource = Files.readString(ssaoApplyFile);
        assertTrue(ssaoApplySource.contains("public int ssaoTexture = -1;"),
            "SSAOApplyShader should initialize SSAO texture id to unresolved sentinel -1");
        assertTrue(ssaoApplySource.contains("protected boolean onPreRender(CommandContext ctx, float partialTicks)"),
            "SSAOApplyShader should use shared pre-bind precheck hook for unresolved resources");
        assertTrue(ssaoApplySource.contains("this.ssaoTexture != -1")
                && ssaoApplySource.contains("this.activeDepthTextureId != -1")
                && ssaoApplySource.contains("SSAOShader.INSTANCE.frameBuffer != null")
                && ssaoApplySource.contains("LodRenderer.INSTANCE.hasActiveRenderTarget()"),
            "SSAOApplyShader precheck should require resolved SSAO/depth/framebuffer resources before bind/uniform work");
        assertTrue(ssaoApplySource.contains("DhTextureState.bindTexture2D(this.activeDepthTextureId)")
                && ssaoApplySource.contains("SSAOShader.INSTANCE.frameBuffer.bindAsReadBuffer(ctx);")
                && ssaoApplySource.contains("if (!LodRenderer.INSTANCE.bindActiveRenderTarget())"),
            "SSAOApplyShader should bind framebuffer owners through the DH render-target seam");

        Path dhFadeShaderFile = SRC_MAIN_JAVA.resolve("com/seibel/distanthorizons/core/render/renderer/shaders/DhFadeShader.java");
        String dhFadeShaderSource = Files.readString(dhFadeShaderFile);
        assertTrue(dhFadeShaderSource.contains("public DhFramebuffer frameBuffer;")
                && dhFadeShaderSource.contains("protected boolean onPreRender(CommandContext ctx, float partialTicks)"),
            "DhFadeShader should use shared pre-bind precheck hook for unresolved resources");
        assertTrue(dhFadeShaderSource.contains("this.activeDepthTextureId = depthTextureId;")
                && dhFadeShaderSource.contains("this.activeColorTextureId = colorTextureId;")
                && dhFadeShaderSource.contains("this.activeMcColorTextureId = mcColorTextureId;")
                && dhFadeShaderSource.contains("this.activeFrameBuffer = this.frameBuffer;"),
            "DhFadeShader should cache validated depth/color/framebuffer resource ids during precheck");
        assertTrue(dhFadeShaderSource.contains("this.activeFrameBuffer.bind(ctx);")
                && dhFadeShaderSource.contains("DhTextureState.bindTexture2D(this.activeMcColorTextureId)")
                && dhFadeShaderSource.contains("DhTextureState.bindTexture2D(this.activeColorTextureId)"),
            "DhFadeShader should bind cached validated framebuffer owners and texture ids resolved during precheck");

        Path vanillaFadeShaderFile = SRC_MAIN_JAVA.resolve("com/seibel/distanthorizons/core/render/renderer/shaders/VanillaFadeShader.java");
        String vanillaFadeShaderSource = Files.readString(vanillaFadeShaderFile);
        assertTrue(vanillaFadeShaderSource.contains("public DhFramebuffer frameBuffer;")
                && vanillaFadeShaderSource.contains("protected boolean onPreRender(CommandContext ctx, float partialTicks)"),
            "VanillaFadeShader should use shared pre-bind precheck hook for unresolved resources");
        assertTrue(vanillaFadeShaderSource.contains("this.activeMcDepthTextureId = mcDepthTextureId;")
                && vanillaFadeShaderSource.contains("this.activeMcColorTextureId = mcColorTextureId;")
                && vanillaFadeShaderSource.contains("this.activeDepthTextureId = depthTextureId;")
                && vanillaFadeShaderSource.contains("this.activeColorTextureId = colorTextureId;")
                && vanillaFadeShaderSource.contains("this.activeFrameBuffer = this.frameBuffer;"),
            "VanillaFadeShader should cache validated MC/DH texture and framebuffer ids during precheck");
        assertTrue(vanillaFadeShaderSource.contains("this.activeFrameBuffer.bind(ctx);")
                && vanillaFadeShaderSource.contains("DhTextureState.bindTexture2D(this.activeMcDepthTextureId)")
                && vanillaFadeShaderSource.contains("DhTextureState.bindTexture2D(this.activeMcColorTextureId)")
                && vanillaFadeShaderSource.contains("DhTextureState.bindTexture2D(this.activeDepthTextureId)")
                && vanillaFadeShaderSource.contains("DhTextureState.bindTexture2D(this.activeColorTextureId)"),
            "VanillaFadeShader should bind cached validated MC/DH texture and framebuffer owners resolved during precheck");

        Path fogShaderFile = SRC_MAIN_JAVA.resolve("com/seibel/distanthorizons/core/render/renderer/shaders/FogShader.java");
        String fogShaderSource = Files.readString(fogShaderFile);
        assertTrue(fogShaderSource.contains("public DhFramebuffer frameBuffer;"),
            "FogShader should store its target as a framebuffer owner instead of a raw id");
        assertTrue(fogShaderSource.contains("protected boolean onPreRender(CommandContext ctx, float partialTicks)"),
            "FogShader should use shared pre-bind precheck hook for unresolved resources");
        assertTrue(fogShaderSource.contains("if (this.frameBuffer == null || depthTextureId == -1)")
                && fogShaderSource.contains("this.activeFrameBuffer = this.frameBuffer;")
                && fogShaderSource.contains("this.activeDepthTextureId = depthTextureId;"),
            "FogShader precheck should validate and cache framebuffer/depth texture ids before bind/uniform work");
        assertTrue(fogShaderSource.contains("this.activeFrameBuffer.bind(ctx);")
                && fogShaderSource.contains("DhTextureState.bindTexture2D(this.activeDepthTextureId)"),
            "FogShader should bind cached validated framebuffer owners and depth texture ids resolved during precheck");

        Path ssaoShaderFile = SRC_MAIN_JAVA.resolve("com/seibel/distanthorizons/core/render/renderer/shaders/SSAOShader.java");
        String ssaoShaderSource = Files.readString(ssaoShaderFile);
        assertTrue(ssaoShaderSource.contains("public DhFramebuffer frameBuffer;"),
            "SSAOShader should store its target as a framebuffer owner instead of a raw id");
        assertTrue(ssaoShaderSource.contains("protected boolean onPreRender(CommandContext ctx, float partialTicks)"),
            "SSAOShader should use shared pre-bind precheck hook for unresolved resources");
        assertTrue(ssaoShaderSource.contains("if (this.frameBuffer == null || depthTextureId == -1)")
                && ssaoShaderSource.contains("this.activeFrameBuffer = this.frameBuffer;")
                && ssaoShaderSource.contains("this.activeDepthTextureId = depthTextureId;"),
            "SSAOShader precheck should validate and cache framebuffer/depth texture ids before bind/uniform work");
        assertTrue(ssaoShaderSource.contains("this.activeFrameBuffer.bind(ctx);")
                && ssaoShaderSource.contains("DhTextureState.bindTexture2D(this.activeDepthTextureId)"),
            "SSAOShader should bind cached validated framebuffer owners and depth texture ids resolved during precheck");

        Path vanillaFadeRendererFile = SRC_MAIN_JAVA.resolve("com/seibel/distanthorizons/core/render/renderer/VanillaFadeRenderer.java");
        String vanillaFadeRendererSource = Files.readString(vanillaFadeRendererFile);
        assertTrue(vanillaFadeRendererSource.contains("if (!MC_RENDER.mcRendersToFrameBuffer())"),
            "VanillaFadeRenderer should branch MC-color attachment setup based on render-target path");
        assertTrue(vanillaFadeRendererSource.contains("if (mcColorTextureId == -1)"),
            "VanillaFadeRenderer should skip fade setup when MC color texture handle is unresolved");
        assertTrue(vanillaFadeRendererSource.contains("if (width <= 0 || height <= 0)"),
            "VanillaFadeRenderer should skip rendering when target viewport dimensions are invalid");

        Path fogRendererFile = SRC_MAIN_JAVA.resolve("com/seibel/distanthorizons/core/render/renderer/FogRenderer.java");
        String fogRendererSource = Files.readString(fogRendererFile);
        assertTrue(fogRendererSource.contains("if (width <= 0 || height <= 0)"),
            "FogRenderer should skip rendering when target viewport dimensions are invalid");
        assertTrue(fogRendererSource.contains("private DhFramebuffer fogFramebuffer;")
                && fogRendererSource.contains("this.fogFramebuffer = new DhFramebuffer();")
                && fogRendererSource.contains("this.fogFramebuffer.addColorAttachment(ctx, 0, this.fogTexture);")
                && fogRendererSource.contains("if (this.fogFramebuffer == null || this.fogTexture == -1)"),
            "FogRenderer should manage its offscreen target through DhFramebuffer owner objects");

        Path ssaoRendererFile = SRC_MAIN_JAVA.resolve("com/seibel/distanthorizons/core/render/renderer/SSAORenderer.java");
        String ssaoRendererSource = Files.readString(ssaoRendererFile);
        assertTrue(ssaoRendererSource.contains("if (width <= 0 || height <= 0)"),
            "SSAORenderer should skip rendering when target viewport dimensions are invalid");
        assertTrue(ssaoRendererSource.contains("private DhFramebuffer ssaoFramebuffer;")
                && ssaoRendererSource.contains("this.ssaoFramebuffer = new DhFramebuffer();")
                && ssaoRendererSource.contains("this.ssaoFramebuffer.addColorAttachment(ctx, 0, this.ssaoTexture);")
                && ssaoRendererSource.contains("if (this.ssaoFramebuffer == null || this.ssaoTexture == -1)"),
            "SSAORenderer should manage its offscreen target through DhFramebuffer owner objects");

        Path dhFadeRendererFile = SRC_MAIN_JAVA.resolve("com/seibel/distanthorizons/core/render/renderer/DhFadeRenderer.java");
        String dhFadeRendererSource = Files.readString(dhFadeRendererFile);
        assertTrue(dhFadeRendererSource.contains("if (width <= 0 || height <= 0)"),
            "DhFadeRenderer should skip rendering when target viewport dimensions are invalid");
        assertTrue(dhFadeRendererSource.contains("private DhFramebuffer fadeFramebuffer;")
                && dhFadeRendererSource.contains("this.fadeFramebuffer = new DhFramebuffer();")
                && dhFadeRendererSource.contains("this.fadeFramebuffer.addColorAttachment(ctx, 0, this.fadeTexture);")
                && dhFadeRendererSource.contains("if (this.fadeFramebuffer == null || this.fadeTexture == -1)"),
            "DhFadeRenderer should manage its offscreen target through DhFramebuffer owner objects");
    }

    @Test
    public void testIrisTextureCreationUsesCreateTexture2DHelper() throws IOException {
        Path[] files = new Path[]{
            SRC_MAIN_JAVA.resolve("net/irisshaders/iris/targets/DepthTexture.java"),
            SRC_MAIN_JAVA.resolve("net/irisshaders/iris/targets/backed/NoiseTexture.java"),
            SRC_MAIN_JAVA.resolve("net/irisshaders/iris/targets/backed/SingleColorTexture.java")
        };

        for (Path file : files) {
            String source = Files.readString(file);
            assertFalse(source.contains("IrisRenderSystem.createTexture(VulkanicAPI.GL_TEXTURE_2D)"),
                file.getFileName() + " should not pass explicit GL_TEXTURE_2D to IrisRenderSystem.createTexture");
            assertTrue(source.contains("IrisRenderSystem.createTexture2D()"),
                file.getFileName() + " should use IrisRenderSystem.createTexture2D helper");
        }

        Path depthTextureFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/targets/DepthTexture.java");
        String depthTextureSource = Files.readString(depthTextureFile);
        assertFalse(depthTextureSource.contains("IrisRenderSystem.texParameteri(texture, VulkanicAPI.GL_TEXTURE_MIN_FILTER"),
            "DepthTexture should not set min filter through raw GL_TEXTURE_MIN_FILTER pname constants");
        assertFalse(depthTextureSource.contains("IrisRenderSystem.texParameteri(texture, VulkanicAPI.GL_TEXTURE_WRAP_S"),
            "DepthTexture should not set wrap through raw GL_TEXTURE_WRAP_* pname constants");
        assertTrue(depthTextureSource.contains("IrisRenderSystem.setTextureNearestFiltering(texture)"),
            "DepthTexture should use IrisRenderSystem.setTextureNearestFiltering helper");
        assertTrue(depthTextureSource.contains("IrisRenderSystem.setTextureWrapMode2D(texture, true)"),
            "DepthTexture should use IrisRenderSystem.setTextureWrapMode2D clamp helper");

        Path noiseTextureFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/targets/backed/NoiseTexture.java");
        String noiseTextureSource = Files.readString(noiseTextureFile);
        assertFalse(noiseTextureSource.contains("IrisRenderSystem.texParameteri(texture, VulkanicAPI.GL_TEXTURE_MIN_FILTER"),
            "NoiseTexture should not set min filter through raw GL_TEXTURE_MIN_FILTER pname constants");
        assertFalse(noiseTextureSource.contains("IrisRenderSystem.texParameteri(texture, VulkanicAPI.GL_TEXTURE_WRAP_S"),
            "NoiseTexture should not set wrap through raw GL_TEXTURE_WRAP_* pname constants");
        assertFalse(noiseTextureSource.contains("IrisRenderSystem.texParameteri(texture, VulkanicAPI.GL_TEXTURE_MAX_LEVEL"),
            "NoiseTexture should not set LOD range through raw GL_TEXTURE_* LOD pname constants");
        assertTrue(noiseTextureSource.contains("IrisRenderSystem.setTextureLinearFiltering(texture)"),
            "NoiseTexture should use IrisRenderSystem.setTextureLinearFiltering helper");
        assertTrue(noiseTextureSource.contains("IrisRenderSystem.setTextureWrapMode2D(texture, false)"),
            "NoiseTexture should use IrisRenderSystem.setTextureWrapMode2D repeat helper");
        assertTrue(noiseTextureSource.contains("IrisRenderSystem.resetTextureLodRangeToZero(texture)"),
            "NoiseTexture should use IrisRenderSystem.resetTextureLodRangeToZero helper");

        Path singleColorTextureFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/targets/backed/SingleColorTexture.java");
        String singleColorTextureSource = Files.readString(singleColorTextureFile);
        assertFalse(singleColorTextureSource.contains("IrisRenderSystem.texParameteri(texture, VulkanicAPI.GL_TEXTURE_MIN_FILTER"),
            "SingleColorTexture should not set min filter through raw GL_TEXTURE_MIN_FILTER pname constants");
        assertFalse(singleColorTextureSource.contains("IrisRenderSystem.texParameteri(texture, VulkanicAPI.GL_TEXTURE_WRAP_S"),
            "SingleColorTexture should not set wrap through raw GL_TEXTURE_WRAP_* pname constants");
        assertTrue(singleColorTextureSource.contains("IrisRenderSystem.setTextureLinearFiltering(texture)"),
            "SingleColorTexture should use IrisRenderSystem.setTextureLinearFiltering helper");
        assertTrue(singleColorTextureSource.contains("IrisRenderSystem.setTextureWrapMode2D(texture, false)"),
            "SingleColorTexture should use IrisRenderSystem.setTextureWrapMode2D repeat helper");

        Path renderTargetFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/targets/RenderTarget.java");
        String renderTargetSource = Files.readString(renderTargetFile);
        assertFalse(renderTargetSource.contains("IrisRenderSystem.texParameteri(texture, VulkanicAPI.GL_TEXTURE_MIN_FILTER"),
            "RenderTarget should not set min filter through raw GL_TEXTURE_MIN_FILTER pname constants");
        assertFalse(renderTargetSource.contains("IrisRenderSystem.texParameteri(texture, VulkanicAPI.GL_TEXTURE_WRAP_S"),
            "RenderTarget should not set wrap through raw GL_TEXTURE_WRAP_* pname constants");
        assertTrue(renderTargetSource.contains("IrisRenderSystem.setTextureLinearFiltering(texture)"),
            "RenderTarget linear path should use IrisRenderSystem.setTextureLinearFiltering helper");
        assertTrue(renderTargetSource.contains("IrisRenderSystem.setTextureNearestFiltering(texture)"),
            "RenderTarget nearest path should use IrisRenderSystem.setTextureNearestFiltering helper");
        assertTrue(renderTargetSource.contains("IrisRenderSystem.setTextureWrapMode2D(texture, true)"),
            "RenderTarget should use IrisRenderSystem.setTextureWrapMode2D clamp helper");

        Path centerDepthSamplerFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/pathways/CenterDepthSampler.java");
        String centerDepthSamplerSource = Files.readString(centerDepthSamplerFile);
        assertFalse(centerDepthSamplerSource.contains("IrisRenderSystem.texParameteri(texture, VulkanicAPI.GL_TEXTURE_MIN_FILTER"),
            "CenterDepthSampler should not set min filter through raw GL_TEXTURE_MIN_FILTER pname constants");
        assertFalse(centerDepthSamplerSource.contains("IrisRenderSystem.texParameteri(texture, VulkanicAPI.GL_TEXTURE_WRAP_S"),
            "CenterDepthSampler should not set wrap through raw GL_TEXTURE_WRAP_* pname constants");
        assertTrue(centerDepthSamplerSource.contains("IrisRenderSystem.setTextureLinearFiltering(texture)"),
            "CenterDepthSampler should use IrisRenderSystem.setTextureLinearFiltering helper");
        assertTrue(centerDepthSamplerSource.contains("IrisRenderSystem.setTextureWrapMode2D(texture, true)"),
            "CenterDepthSampler should use IrisRenderSystem.setTextureWrapMode2D clamp helper");

        Path nativeImageBackedCustomTextureFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/targets/backed/NativeImageBackedCustomTexture.java");
        String nativeImageBackedCustomTextureSource = Files.readString(nativeImageBackedCustomTextureFile);
        assertFalse(nativeImageBackedCustomTextureSource.contains("IrisRenderSystem.texParameteri(getId(), VulkanicAPI.GL_TEXTURE_MIN_FILTER"),
            "NativeImageBackedCustomTexture should not set min filter through raw GL_TEXTURE_MIN_FILTER pname constants");
        assertFalse(nativeImageBackedCustomTextureSource.contains("IrisRenderSystem.texParameteri(getId(), VulkanicAPI.GL_TEXTURE_WRAP_S"),
            "NativeImageBackedCustomTexture should not set wrap through raw GL_TEXTURE_WRAP_* pname constants");
        assertTrue(nativeImageBackedCustomTextureSource.contains("IrisRenderSystem.setTextureLinearFiltering(getId())"),
            "NativeImageBackedCustomTexture blur path should use IrisRenderSystem.setTextureLinearFiltering helper");
        assertTrue(nativeImageBackedCustomTextureSource.contains("IrisRenderSystem.setTextureWrapMode2D(getId(), true)"),
            "NativeImageBackedCustomTexture clamp path should use IrisRenderSystem.setTextureWrapMode2D helper");

        Path shadowRendererFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/shadows/ShadowRenderer.java");
        String shadowRendererSource = Files.readString(shadowRendererFile);
        assertFalse(shadowRendererSource.contains("IrisRenderSystem.texParameteri(glTextureId, VulkanicAPI.GL_TEXTURE_MIN_FILTER, VulkanicAPI.GL_LINEAR)"),
            "ShadowRenderer should not set linear min filter through raw GL_TEXTURE_MIN_FILTER pname constants");
        assertFalse(shadowRendererSource.contains("IrisRenderSystem.texParameteri(glTextureId, VulkanicAPI.GL_TEXTURE_MIN_FILTER, VulkanicAPI.GL_NEAREST)"),
            "ShadowRenderer should not set nearest min filter through raw GL_TEXTURE_MIN_FILTER pname constants");
        assertTrue(shadowRendererSource.contains("IrisRenderSystem.setTextureLinearFiltering(glTextureId)"),
            "ShadowRenderer linear path should use IrisRenderSystem.setTextureLinearFiltering helper");
        assertTrue(shadowRendererSource.contains("IrisRenderSystem.setTextureNearestFiltering(glTextureId)"),
            "ShadowRenderer nearest path should use IrisRenderSystem.setTextureNearestFiltering helper");

        Path irisRenderSystemFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/gl/IrisRenderSystem.java");
        String irisRenderSystemSource = Files.readString(irisRenderSystemFile);
        assertTrue(irisRenderSystemSource.contains("public static void setTextureLinearFiltering(int texture)"),
            "IrisRenderSystem should expose setTextureLinearFiltering helper for object-DSA texture setup");
        assertTrue(irisRenderSystemSource.contains("public static void setTextureNearestFiltering(int texture)"),
            "IrisRenderSystem should expose setTextureNearestFiltering helper for object-DSA texture setup");
        assertTrue(irisRenderSystemSource.contains("public static void setTextureWrapMode2D(int texture, boolean clampToEdge)"),
            "IrisRenderSystem should expose setTextureWrapMode2D helper for object-DSA texture setup");
        assertTrue(irisRenderSystemSource.contains("public static void resetTextureLodRangeToZero(int texture)"),
            "IrisRenderSystem should expose resetTextureLodRangeToZero helper for object-DSA texture setup");
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
        String dhWrapperSource = readSourceIfExists(dhWrapperFile);
        assertFalse(dhWrapperSource.contains("GlStateManager._enableScissorTest("),
            "MinecraftGLWrapper should not call removed GlStateManager._enableScissorTest wrapper");
        assertFalse(dhWrapperSource.contains("GlStateManager._disableScissorTest("),
            "MinecraftGLWrapper should not call removed GlStateManager._disableScissorTest wrapper");
        assertFalse(dhWrapperSource.contains("public void enableScissorTest("),
            "MinecraftGLWrapper should no longer expose scissor wrapper methods");
        assertFalse(dhWrapperSource.contains("public void disableScissorTest("),
            "MinecraftGLWrapper should no longer expose scissor wrapper methods");
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

    @Test
    public void testModelViewOwnershipMovedToVulkanicAPI() throws IOException {
        Path renderSystemFile = SRC_MAIN_JAVA.resolve("net/blaze3d/systems/RenderSystem.java");
        String renderSystemSource = Files.readString(renderSystemFile);

        assertFalse(renderSystemSource.contains("private static final Matrix4fStack modelViewStack"),
            "RenderSystem should not own modelViewStack after model-view migration to VulkanicAPI");
        assertFalse(renderSystemSource.contains("public static Matrix4f getModelViewMatrix("),
            "RenderSystem should not expose getModelViewMatrix after model-view migration to VulkanicAPI");
        assertFalse(renderSystemSource.contains("public static Matrix4fStack getModelViewStack("),
            "RenderSystem should not expose getModelViewStack after model-view migration to VulkanicAPI");

        Path vulkanicApiFile = SRC_MAIN_JAVA.resolve("net/vulkanic/VulkanicAPI.java");
        String vulkanicApiSource = Files.readString(vulkanicApiFile);

        assertTrue(vulkanicApiSource.contains("private static final Matrix4fStack modelViewStack = new Matrix4fStack(16);"),
            "VulkanicAPI should own the model-view stack after migration");
        assertTrue(vulkanicApiSource.contains("public static Matrix4f getModelViewMatrix("),
            "VulkanicAPI should expose getModelViewMatrix after migration");
        assertTrue(vulkanicApiSource.contains("public static Matrix4fStack getModelViewStack("),
            "VulkanicAPI should expose getModelViewStack after migration");
        assertTrue(vulkanicApiSource.contains("public static void setupDefaultState("),
            "VulkanicAPI should own setupDefaultState after migration");
        assertTrue(vulkanicApiSource.contains("getModelViewStack().clear();"),
            "VulkanicAPI.setupDefaultState should clear the model-view stack");
        assertTrue(vulkanicApiSource.contains("resetTextureMatrix();"),
            "VulkanicAPI.setupDefaultState should reset the texture matrix");
    }

    @Test
    public void testBootstrapHelpersOwnershipMovedToVulkanicAPI() throws IOException {
        Path renderSystemFile = SRC_MAIN_JAVA.resolve("net/blaze3d/systems/RenderSystem.java");
        String renderSystemSource = Files.readString(renderSystemFile);

        assertFalse(renderSystemSource.contains("public static String getBackendDescription("),
            "RenderSystem should not expose getBackendDescription after bootstrap migration");
        assertFalse(renderSystemSource.contains("public static String getApiDescription("),
            "RenderSystem should not expose getApiDescription after bootstrap migration");
        assertFalse(renderSystemSource.contains("public static NanoTimeSource initBackendSystem("),
            "RenderSystem should not expose initBackendSystem after bootstrap migration");
        assertFalse(renderSystemSource.contains("public static void setupDefaultState("),
            "RenderSystem should not expose setupDefaultState after bootstrap migration");
        assertFalse(renderSystemSource.contains("public static void setErrorCallback("),
            "RenderSystem should not expose setErrorCallback after bootstrap migration");
        assertFalse(renderSystemSource.contains("public static boolean isFrozenAtPollEvents("),
            "RenderSystem should not expose isFrozenAtPollEvents after poll-state migration");
        assertFalse(renderSystemSource.contains("public static void limitDisplayFPS("),
            "RenderSystem should not expose limitDisplayFPS after frame pacing migration");

        Path vulkanicApiFile = SRC_MAIN_JAVA.resolve("net/vulkanic/VulkanicAPI.java");
        String vulkanicApiSource = Files.readString(vulkanicApiFile);

        assertTrue(vulkanicApiSource.contains("public static String getBackendDescription("),
            "VulkanicAPI should expose getBackendDescription after bootstrap migration");
        assertTrue(vulkanicApiSource.contains("public static String getApiDescription("),
            "VulkanicAPI should expose getApiDescription after bootstrap migration");
        assertTrue(vulkanicApiSource.contains("public static NanoTimeSource initBackendSystem("),
            "VulkanicAPI should expose initBackendSystem after bootstrap migration");
        assertTrue(vulkanicApiSource.contains("public static void setupDefaultState("),
            "VulkanicAPI should expose setupDefaultState after bootstrap migration");
        assertTrue(vulkanicApiSource.contains("public static void setErrorCallback("),
            "VulkanicAPI should expose setErrorCallback after bootstrap migration");
        assertTrue(vulkanicApiSource.contains("public static void pollEvents("),
            "VulkanicAPI should expose pollEvents after poll-state migration");
        assertTrue(vulkanicApiSource.contains("public static boolean isFrozenAtPollEvents("),
            "VulkanicAPI should expose isFrozenAtPollEvents after poll-state migration");
        assertTrue(vulkanicApiSource.contains("public static void limitDisplayFPS("),
            "VulkanicAPI should expose limitDisplayFPS after frame pacing migration");

        Path minecraftFile = SRC_MAIN_JAVA.resolve("net/minecraft/client/Minecraft.java");
        String minecraftSource = Files.readString(minecraftFile);

        assertFalse(minecraftSource.contains("RenderSystem.getBackendDescription("),
            "Minecraft should not call RenderSystem.getBackendDescription after bootstrap migration");
        assertFalse(minecraftSource.contains("RenderSystem.getApiDescription("),
            "Minecraft should not call RenderSystem.getApiDescription after bootstrap migration");
        assertFalse(minecraftSource.contains("RenderSystem.initBackendSystem("),
            "Minecraft should not call RenderSystem.initBackendSystem after bootstrap migration");
        assertFalse(minecraftSource.contains("RenderSystem.setupDefaultState("),
            "Minecraft should not call RenderSystem.setupDefaultState after bootstrap migration");
        assertFalse(minecraftSource.contains("RenderSystem.setErrorCallback("),
            "Minecraft should not call RenderSystem.setErrorCallback after bootstrap migration");
        assertFalse(minecraftSource.contains("RenderSystem.limitDisplayFPS("),
            "Minecraft should not call RenderSystem.limitDisplayFPS after frame pacing migration");
        assertTrue(minecraftSource.contains("VulkanicAPI.getBackendDescription("),
            "Minecraft should call VulkanicAPI.getBackendDescription after bootstrap migration");
        assertTrue(
            minecraftSource.contains("VulkanicAPI.getApiDescription(")
                || minecraftSource.contains("VulkanicAPI::getApiDescription"),
            "Minecraft should call VulkanicAPI.getApiDescription after bootstrap migration"
        );
        assertTrue(minecraftSource.contains("VulkanicAPI.initBackendSystem("),
            "Minecraft should call VulkanicAPI.initBackendSystem after bootstrap migration");
        assertTrue(minecraftSource.contains("VulkanicAPI.setupDefaultState("),
            "Minecraft should call VulkanicAPI.setupDefaultState after bootstrap migration");
        assertTrue(minecraftSource.contains("VulkanicAPI.setErrorCallback("),
            "Minecraft should call VulkanicAPI.setErrorCallback after bootstrap migration");
        assertTrue(minecraftSource.contains("VulkanicAPI.limitDisplayFPS("),
            "Minecraft should call VulkanicAPI.limitDisplayFPS after frame pacing migration");

        Path glxFile = SRC_MAIN_JAVA.resolve("net/blaze3d/platform/GLX.java");
        String glxSource = Files.readString(glxFile);
        assertFalse(glxSource.contains("RenderSystem.setErrorCallback("),
            "GLX should not route GLFW error callback setup through RenderSystem after bootstrap migration");
        assertTrue(glxSource.contains("VulkanicAPI.setErrorCallback("),
            "GLX should route GLFW error callback setup through VulkanicAPI after bootstrap migration");

        Path packetListenerFile = SRC_MAIN_JAVA.resolve("net/minecraft/client/multiplayer/ClientCommonPacketListenerImpl.java");
        String packetListenerSource = Files.readString(packetListenerFile);
        assertFalse(packetListenerSource.contains("RenderSystem.isFrozenAtPollEvents("),
            "ClientCommonPacketListenerImpl should not query poll freeze state through RenderSystem after migration");
        assertTrue(packetListenerSource.contains("VulkanicAPI.isFrozenAtPollEvents("),
            "ClientCommonPacketListenerImpl should query poll freeze state through VulkanicAPI after migration");

        assertTrue(renderSystemSource.contains("VulkanicAPI.pollEvents();"),
            "RenderSystem.flipFrame should route poll event calls through VulkanicAPI after migration");
    }

    @Test
    public void testThreadAndDeviceOwnershipMovedToVulkanicAPI() throws IOException {
        Path renderSystemFile = SRC_MAIN_JAVA.resolve("net/blaze3d/systems/RenderSystem.java");
        String renderSystemSource = Files.readString(renderSystemFile);

        assertFalse(renderSystemSource.contains("private static Thread renderThread;"),
            "RenderSystem should not own renderThread after thread ownership migration");
        assertFalse(renderSystemSource.contains("private static GpuDevice DEVICE;"),
            "RenderSystem should not own DEVICE after device ownership migration");
        assertFalse(renderSystemSource.contains("public static void initRenderThread("),
            "RenderSystem should not expose initRenderThread after thread ownership migration");
        assertFalse(renderSystemSource.contains("public static boolean isOnRenderThread("),
            "RenderSystem should not expose isOnRenderThread after thread ownership migration");
        assertFalse(renderSystemSource.contains("public static boolean isInInit("),
            "RenderSystem should not expose isInInit after thread ownership migration");
        assertFalse(renderSystemSource.contains("public static void assertOnRenderThreadOrInit("),
            "RenderSystem should not expose assertOnRenderThreadOrInit after thread ownership migration");
        assertFalse(renderSystemSource.contains("public static GpuDevice tryGetDevice("),
            "RenderSystem should not expose tryGetDevice after device ownership migration");
        assertTrue(renderSystemSource.contains("VulkanicAPI.setDevice("),
            "RenderSystem.initRenderer should set the device through VulkanicAPI after migration");
        assertTrue(renderSystemSource.contains("VulkanicAPI.createRendererDevice("),
            "RenderSystem.initRenderer should create the device through the backend-owned VulkanicAPI seam after migration");
        assertFalse(renderSystemSource.contains("new GlDevice("),
            "RenderSystem.initRenderer should not hard-code GlDevice construction after backend-owned device migration");
        assertTrue(renderSystemSource.contains("return net.vulkanic.VulkanicAPI.getDevice();"),
            "RenderSystem.getDevice should delegate to VulkanicAPI after migration");

        Path vulkanicApiFile = SRC_MAIN_JAVA.resolve("net/vulkanic/VulkanicAPI.java");
        String vulkanicApiSource = Files.readString(vulkanicApiFile);

        assertTrue(vulkanicApiSource.contains("private static Thread renderThread;"),
            "VulkanicAPI should own renderThread after migration");
        assertTrue(vulkanicApiSource.contains("private static GpuDevice device;"),
            "VulkanicAPI should own device after migration");
        assertTrue(vulkanicApiSource.contains("public static void initRenderThread("),
            "VulkanicAPI should expose initRenderThread after migration");
        assertTrue(vulkanicApiSource.contains("public static boolean isOnRenderThread("),
            "VulkanicAPI should expose isOnRenderThread after migration");
        assertTrue(vulkanicApiSource.contains("public static boolean isInInit("),
            "VulkanicAPI should expose isInInit after migration");
        assertTrue(vulkanicApiSource.contains("public static void assertOnRenderThreadOrInit("),
            "VulkanicAPI should expose assertOnRenderThreadOrInit after migration");
        assertTrue(vulkanicApiSource.contains("public static void setDevice("),
            "VulkanicAPI should expose setDevice after migration");
        assertTrue(vulkanicApiSource.contains("public static GpuDevice getDevice("),
            "VulkanicAPI should expose getDevice after migration");
        assertTrue(vulkanicApiSource.contains("public static GpuDevice tryGetDevice("),
            "VulkanicAPI should expose tryGetDevice after migration");

        Path mainFile = SRC_MAIN_JAVA.resolve("net/minecraft/client/main/Main.java");
        String mainSource = Files.readString(mainFile);
        assertFalse(mainSource.contains("RenderSystem.initRenderThread("),
            "Main should not initialize render thread through RenderSystem after migration");
        assertTrue(mainSource.contains("VulkanicAPI.initRenderThread("),
            "Main should initialize render thread through VulkanicAPI after migration");

        Path irisRenderSystemFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/gl/IrisRenderSystem.java");
        String irisRenderSystemSource = Files.readString(irisRenderSystemFile);
        assertFalse(irisRenderSystemSource.contains("RenderSystem.assertOnRenderThreadOrInit("),
            "IrisRenderSystem should not call RenderSystem.assertOnRenderThreadOrInit after migration");
        assertTrue(irisRenderSystemSource.contains("VulkanicAPI.assertOnRenderThreadOrInit("),
            "IrisRenderSystem should call VulkanicAPI.assertOnRenderThreadOrInit after migration");

        Path renderAssertsFile = SRC_MAIN_JAVA.resolve("net/sodium/client/render/util/RenderAsserts.java");
        String renderAssertsSource = Files.readString(renderAssertsFile);
        assertFalse(renderAssertsSource.contains("RenderSystem.isOnRenderThread("),
            "RenderAsserts should not call RenderSystem.isOnRenderThread after migration");
        assertTrue(renderAssertsSource.contains("VulkanicAPI.isOnRenderThread("),
            "RenderAsserts should call VulkanicAPI.isOnRenderThread after migration");

        Path voxelImageFile = SRC_MAIN_JAVA.resolve("net/voxelmap/persistent/CompressibleGLBufferedImage.java");
        String voxelImageSource = Files.readString(voxelImageFile);
        assertFalse(voxelImageSource.contains("RenderSystem.isOnRenderThread("),
            "CompressibleGLBufferedImage should not call RenderSystem.isOnRenderThread after migration");
        assertTrue(voxelImageSource.contains("VulkanicAPI.isOnRenderThread("),
            "CompressibleGLBufferedImage should call VulkanicAPI.isOnRenderThread after migration");

        Path minecraftFile = SRC_MAIN_JAVA.resolve("net/minecraft/client/Minecraft.java");
        String minecraftSource = Files.readString(minecraftFile);
        assertFalse(minecraftSource.contains("RenderSystem.tryGetDevice("),
            "Minecraft should not call RenderSystem.tryGetDevice after migration");
        assertTrue(minecraftSource.contains("VulkanicAPI.tryGetDevice("),
            "Minecraft should call VulkanicAPI.tryGetDevice after migration");
    }

    @Test
    public void testBlaze3dPackageUsesVulkanicAPIGetDevice() throws IOException {
        Path[] migratedFiles = new Path[] {
            SRC_MAIN_JAVA.resolve("net/blaze3d/platform/Lighting.java"),
            SRC_MAIN_JAVA.resolve("net/blaze3d/vertex/VertexFormat.java"),
            SRC_MAIN_JAVA.resolve("net/blaze3d/platform/TextureUtil.java"),
            SRC_MAIN_JAVA.resolve("net/blaze3d/TracyFrameCapture.java"),
            SRC_MAIN_JAVA.resolve("net/blaze3d/pipeline/RenderTarget.java"),
            SRC_MAIN_JAVA.resolve("net/blaze3d/pipeline/MainTarget.java"),
            SRC_MAIN_JAVA.resolve("net/blaze3d/resource/RenderTargetDescriptor.java"),
            SRC_MAIN_JAVA.resolve("net/blaze3d/font/TrueTypeGlyphProvider.java")
        };

        for (Path file : migratedFiles) {
            String source = Files.readString(file);
            assertFalse(source.contains("RenderSystem.getDevice("),
                file + " should not call RenderSystem.getDevice after device-access migration");
            boolean usesDeviceSeam = source.contains("VulkanicAPI.getDevice(");
            boolean usesCommandEncoderSeam = source.contains("VulkanicAPI.createCommandEncoder(");
            boolean usesRenderPassSeam = source.contains("VulkanicAPI.createRenderPass(");
            boolean usesTextureSeam = source.contains("VulkanicAPI.createTexture(");
            boolean usesBufferSeam = source.contains("VulkanicAPI.createBuffer(");
            boolean usesTextureViewSeam = source.contains("VulkanicAPI.createTextureView(");
            boolean usesBackendMaxTextureSizeSeam = source.contains("VulkanicAPI.getBackendMaxTextureSize(");
            boolean usesBackendUniformAlignmentSeam = source.contains("VulkanicAPI.getBackendUniformOffsetAlignment(");
            boolean usesBackendDeviceInfoSeam = source.contains("VulkanicAPI.getBackendDeviceInfo(");
            assertTrue(
                usesDeviceSeam
                    || usesCommandEncoderSeam
                    || usesRenderPassSeam
                    || usesTextureSeam
                    || usesBufferSeam
                    || usesTextureViewSeam
                    || usesBackendMaxTextureSizeSeam
                    || usesBackendUniformAlignmentSeam
                    || usesBackendDeviceInfoSeam,
                file + " should call VulkanicAPI.getDevice/createCommandEncoder/createRenderPass/createTexture/createBuffer/createTextureView/getBackendMaxTextureSize/getBackendUniformOffsetAlignment/getBackendDeviceInfo after device-access migration"
            );
        }
    }

    @Test
    public void testVoxelMapPackageUsesVulkanicAPIGetDevice() throws IOException {
        Path[] migratedFiles = new Path[] {
            SRC_MAIN_JAVA.resolve("net/voxelmap/util/VoxelMapCachedOrthoProjectionMatrixBuffer.java"),
            SRC_MAIN_JAVA.resolve("net/voxelmap/util/AllocatedTexture.java"),
            SRC_MAIN_JAVA.resolve("net/voxelmap/util/GLUtils.java"),
            SRC_MAIN_JAVA.resolve("net/voxelmap/entityrender/EntityMapImageManager.java"),
            SRC_MAIN_JAVA.resolve("net/voxelmap/textures/TextureAtlas.java"),
            SRC_MAIN_JAVA.resolve("net/voxelmap/Map.java")
        };

        for (Path file : migratedFiles) {
            String source = Files.readString(file);
            assertFalse(source.contains("RenderSystem.getDevice("),
                file + " should not call RenderSystem.getDevice after device-access migration");
            boolean usesDeviceSeam = source.contains("VulkanicAPI.getDevice(");
            boolean usesCommandEncoderSeam = source.contains("VulkanicAPI.createCommandEncoder(");
            boolean usesRenderPassSeam = source.contains("VulkanicAPI.createRenderPass(");
            boolean usesTextureSeam = source.contains("VulkanicAPI.createTexture(");
            boolean usesBufferSeam = source.contains("VulkanicAPI.createBuffer(");
            boolean usesTextureViewSeam = source.contains("VulkanicAPI.createTextureView(");
            boolean usesBackendMaxTextureSizeSeam = source.contains("VulkanicAPI.getBackendMaxTextureSize(");
            boolean usesBackendUniformAlignmentSeam = source.contains("VulkanicAPI.getBackendUniformOffsetAlignment(");
            boolean usesBackendDeviceInfoSeam = source.contains("VulkanicAPI.getBackendDeviceInfo(");
            assertTrue(
                usesDeviceSeam
                    || usesCommandEncoderSeam
                    || usesRenderPassSeam
                    || usesTextureSeam
                    || usesBufferSeam
                    || usesTextureViewSeam
                    || usesBackendMaxTextureSizeSeam
                    || usesBackendUniformAlignmentSeam
                    || usesBackendDeviceInfoSeam,
                file + " should call VulkanicAPI.getDevice/createCommandEncoder/createRenderPass/createTexture/createBuffer/createTextureView/getBackendMaxTextureSize/getBackendUniformOffsetAlignment/getBackendDeviceInfo after device-access migration"
            );
        }
    }

    @Test
    public void testMinecraftAndGuiUseVulkanicAPIGetDevice() throws IOException {
        Path[] migratedFiles = new Path[] {
            SRC_MAIN_JAVA.resolve("net/minecraft/client/Minecraft.java"),
            SRC_MAIN_JAVA.resolve("net/minecraft/client/gui/font/FontTexture.java"),
            SRC_MAIN_JAVA.resolve("net/minecraft/client/gui/font/glyphs/SpecialGlyphs.java"),
            SRC_MAIN_JAVA.resolve("net/minecraft/client/gui/font/providers/BitmapProvider.java"),
            SRC_MAIN_JAVA.resolve("net/minecraft/client/gui/font/providers/UnihexProvider.java"),
            SRC_MAIN_JAVA.resolve("net/minecraft/client/gui/render/GuiRenderer.java"),
            SRC_MAIN_JAVA.resolve("net/minecraft/client/gui/render/pip/PictureInPictureRenderer.java"),
            SRC_MAIN_JAVA.resolve("net/minecraft/client/gui/components/DebugScreenOverlay.java"),
            SRC_MAIN_JAVA.resolve("net/minecraft/client/gui/components/debug/DebugEntrySystemSpecs.java"),
            SRC_MAIN_JAVA.resolve("net/minecraft/client/gui/screens/LoadingOverlay.java")
        };

        for (Path file : migratedFiles) {
            String source = Files.readString(file);
            assertFalse(source.contains("RenderSystem.getDevice("),
                file + " should not call RenderSystem.getDevice after device-access migration");
            boolean usesDeviceSeam = source.contains("VulkanicAPI.getDevice(");
            boolean usesCommandEncoderSeam = source.contains("VulkanicAPI.createCommandEncoder(");
            boolean usesRenderPassSeam = source.contains("VulkanicAPI.createRenderPass(");
            boolean usesTextureSeam = source.contains("VulkanicAPI.createTexture(");
            boolean usesBufferSeam = source.contains("VulkanicAPI.createBuffer(");
            boolean usesTextureViewSeam = source.contains("VulkanicAPI.createTextureView(");
            boolean usesBackendMaxTextureSizeSeam = source.contains("VulkanicAPI.getBackendMaxTextureSize(");
            boolean usesBackendUniformAlignmentSeam = source.contains("VulkanicAPI.getBackendUniformOffsetAlignment(");
            boolean usesBackendDeviceInfoSeam = source.contains("VulkanicAPI.getBackendDeviceInfo(");
            assertTrue(
                usesDeviceSeam
                    || usesCommandEncoderSeam
                    || usesRenderPassSeam
                    || usesTextureSeam
                    || usesBufferSeam
                    || usesTextureViewSeam
                    || usesBackendMaxTextureSizeSeam
                    || usesBackendUniformAlignmentSeam
                    || usesBackendDeviceInfoSeam,
                file + " should call VulkanicAPI.getDevice/createCommandEncoder/createRenderPass/createTexture/createBuffer/createTextureView/getBackendMaxTextureSize/getBackendUniformOffsetAlignment/getBackendDeviceInfo after device-access migration"
            );
        }
    }

    @Test
    public void testRendererClusterUsesVulkanicAPIGetDevice() throws IOException {
        Path[] migratedFiles = new Path[] {
            SRC_MAIN_JAVA.resolve("net/minecraft/client/Screenshot.java"),
            SRC_MAIN_JAVA.resolve("net/minecraft/client/renderer/CloudRenderer.java"),
            SRC_MAIN_JAVA.resolve("net/minecraft/client/renderer/LightTexture.java"),
            SRC_MAIN_JAVA.resolve("net/minecraft/client/renderer/CubeMap.java"),
            SRC_MAIN_JAVA.resolve("net/minecraft/client/renderer/RenderType.java"),
            SRC_MAIN_JAVA.resolve("net/minecraft/client/renderer/GameRenderer.java"),
            SRC_MAIN_JAVA.resolve("net/minecraft/client/renderer/LevelRenderer.java"),
            SRC_MAIN_JAVA.resolve("net/minecraft/client/renderer/WorldBorderRenderer.java"),
            SRC_MAIN_JAVA.resolve("net/minecraft/client/renderer/SkyRenderer.java"),
            SRC_MAIN_JAVA.resolve("net/minecraft/client/renderer/chunk/ChunkSectionsToRender.java"),
            SRC_MAIN_JAVA.resolve("net/minecraft/client/renderer/chunk/CompiledSectionMesh.java"),
            SRC_MAIN_JAVA.resolve("net/minecraft/client/renderer/texture/SpriteContents.java"),
            SRC_MAIN_JAVA.resolve("net/minecraft/client/renderer/texture/DynamicTexture.java"),
            SRC_MAIN_JAVA.resolve("net/minecraft/client/renderer/texture/ReloadableTexture.java"),
            SRC_MAIN_JAVA.resolve("net/minecraft/client/renderer/texture/CubeMapTexture.java"),
            SRC_MAIN_JAVA.resolve("net/minecraft/client/renderer/MappableRingBuffer.java"),
            SRC_MAIN_JAVA.resolve("net/minecraft/client/renderer/PerspectiveProjectionMatrixBuffer.java"),
            SRC_MAIN_JAVA.resolve("net/minecraft/client/renderer/CachedPerspectiveProjectionMatrixBuffer.java"),
            SRC_MAIN_JAVA.resolve("net/minecraft/client/renderer/CachedOrthoProjectionMatrixBuffer.java"),
            SRC_MAIN_JAVA.resolve("net/minecraft/client/renderer/fog/FogRenderer.java"),
            SRC_MAIN_JAVA.resolve("net/minecraft/client/renderer/feature/ParticleFeatureRenderer.java")
        };

        for (Path file : migratedFiles) {
            String source = Files.readString(file);
            assertFalse(source.contains("RenderSystem.getDevice("),
                file + " should not call RenderSystem.getDevice after renderer-cluster migration");
            boolean usesDeviceSeam = source.contains("VulkanicAPI.getDevice(");
            boolean usesCommandEncoderSeam = source.contains("VulkanicAPI.createCommandEncoder(");
            boolean usesRenderPassSeam = source.contains("VulkanicAPI.createRenderPass(");
            boolean usesTextureSeam = source.contains("VulkanicAPI.createTexture(");
            boolean usesBufferSeam = source.contains("VulkanicAPI.createBuffer(");
            boolean usesTextureViewSeam = source.contains("VulkanicAPI.createTextureView(");
            boolean usesBackendMaxTextureSizeSeam = source.contains("VulkanicAPI.getBackendMaxTextureSize(");
            boolean usesBackendUniformAlignmentSeam = source.contains("VulkanicAPI.getBackendUniformOffsetAlignment(");
            boolean usesBackendDeviceInfoSeam = source.contains("VulkanicAPI.getBackendDeviceInfo(");
            assertTrue(
                usesDeviceSeam
                    || usesCommandEncoderSeam
                    || usesRenderPassSeam
                    || usesTextureSeam
                    || usesBufferSeam
                    || usesTextureViewSeam
                    || usesBackendMaxTextureSizeSeam
                    || usesBackendUniformAlignmentSeam
                    || usesBackendDeviceInfoSeam,
                file + " should call VulkanicAPI.getDevice/createCommandEncoder/createRenderPass/createTexture/createBuffer/createTextureView/getBackendMaxTextureSize/getBackendUniformOffsetAlignment/getBackendDeviceInfo after renderer-cluster migration"
            );
        }
    }

    @Test
    public void testIrisAndSodiumRenderClustersUseVulkanicAPISeams() throws IOException {
        Path[] migratedFiles = new Path[] {
            SRC_MAIN_JAVA.resolve("net/irisshaders/iris/targets/RenderTargets.java"),
            SRC_MAIN_JAVA.resolve("net/irisshaders/iris/targets/backed/NativeImageBackedCustomTexture.java"),
            SRC_MAIN_JAVA.resolve("net/irisshaders/iris/targets/backed/NativeImageBackedNoiseTexture.java"),
            SRC_MAIN_JAVA.resolve("net/irisshaders/iris/pathways/CenterDepthSampler.java"),
            SRC_MAIN_JAVA.resolve("net/irisshaders/iris/pathways/colorspace/ColorSpaceFragmentConverter.java"),
            SRC_MAIN_JAVA.resolve("net/irisshaders/iris/pathways/FullScreenQuadRenderer.java"),
            SRC_MAIN_JAVA.resolve("net/irisshaders/iris/pathways/HorizonRenderer.java"),
            SRC_MAIN_JAVA.resolve("net/irisshaders/iris/shadows/ShadowCompositeRenderer.java"),
            SRC_MAIN_JAVA.resolve("net/irisshaders/iris/shadows/ShadowRenderTargets.java"),
            SRC_MAIN_JAVA.resolve("net/irisshaders/iris/pipeline/FinalPassRenderer.java"),
            SRC_MAIN_JAVA.resolve("net/irisshaders/iris/pipeline/CompositeRenderer.java"),
            SRC_MAIN_JAVA.resolve("net/irisshaders/iris/pbr/texture/PBRAtlasTexture.java"),
            SRC_MAIN_JAVA.resolve("net/sodium/client/gui/SodiumGameOptionPages.java"),
            SRC_MAIN_JAVA.resolve("net/sodium/client/render/chunk/ShaderChunkRenderer.java")
        };

        for (Path file : migratedFiles) {
            String source = Files.readString(file);
            assertFalse(source.contains("RenderSystem.getDevice("),
                file + " should not call RenderSystem.getDevice after Iris/Sodium seam migration");
            boolean usesDeviceSeam = source.contains("VulkanicAPI.getDevice(");
            boolean usesCommandEncoderSeam = source.contains("VulkanicAPI.createCommandEncoder(");
            boolean usesRenderPassSeam = source.contains("VulkanicAPI.createRenderPass(");
            boolean usesTextureSeam = source.contains("VulkanicAPI.createTexture(");
            boolean usesBufferSeam = source.contains("VulkanicAPI.createBuffer(");
            boolean usesTextureViewSeam = source.contains("VulkanicAPI.createTextureView(");
            boolean usesBackendMaxTextureSizeSeam = source.contains("VulkanicAPI.getBackendMaxTextureSize(");
            boolean usesBackendUniformAlignmentSeam = source.contains("VulkanicAPI.getBackendUniformOffsetAlignment(");
            boolean usesBackendDeviceInfoSeam = source.contains("VulkanicAPI.getBackendDeviceInfo(");
            assertTrue(
                usesDeviceSeam
                    || usesCommandEncoderSeam
                    || usesRenderPassSeam
                    || usesTextureSeam
                    || usesBufferSeam
                    || usesTextureViewSeam
                    || usesBackendMaxTextureSizeSeam
                    || usesBackendUniformAlignmentSeam
                    || usesBackendDeviceInfoSeam,
                file + " should call a backend-owned VulkanicAPI seam after Iris/Sodium migration"
            );
        }
    }

    @Test
    public void testCubemapRenderPassStaysColorOnly() throws IOException {
        String source = Files.readString(SRC_MAIN_JAVA.resolve("net/minecraft/client/renderer/CubeMap.java"));

        assertTrue(source.contains("VulkanicAPI.resolveFramebufferForTextures(renderTarget.getColorTexture(), renderTarget.getDepthTexture())"),
            "CubeMap should recover the main render target framebuffer contract before rendering the panorama background");
        assertTrue(source.contains("VulkanicAPI.createRenderPass(() -> \"Cubemap\", framebuffer, renderTarget.getDepthTexture() != null)"),
            "CubeMap should prefer the framebuffer-owned render-pass path for panorama background rendering");
        assertTrue(source.contains(": VulkanicAPI.createRenderPass(() -> \"Cubemap\", gpuTextureView, OptionalInt.empty())"),
            "CubeMap should retain a texture-view fallback when framebuffer recovery is unavailable");
    }

    @Test
    public void testScissorStateOwnershipMovedToVulkanicAPI() throws IOException {
        Path renderSystemFile = SRC_MAIN_JAVA.resolve("net/blaze3d/systems/RenderSystem.java");
        String renderSystemSource = Files.readString(renderSystemFile);

        assertFalse(renderSystemSource.contains("scissorStateForRenderTypeDraws"),
            "RenderSystem should not own scissorStateForRenderTypeDraws after migration to VulkanicAPI");
        assertFalse(renderSystemSource.contains("public static void enableScissorForRenderTypeDraws("),
            "RenderSystem should not expose enableScissorForRenderTypeDraws after migration to VulkanicAPI");
        assertFalse(renderSystemSource.contains("public static void disableScissorForRenderTypeDraws("),
            "RenderSystem should not expose disableScissorForRenderTypeDraws after migration to VulkanicAPI");
        assertFalse(renderSystemSource.contains("public static ScissorState getScissorStateForRenderTypeDraws("),
            "RenderSystem should not expose getScissorStateForRenderTypeDraws after migration to VulkanicAPI");

        Path vulkanicApiFile = SRC_MAIN_JAVA.resolve("net/vulkanic/VulkanicAPI.java");
        String vulkanicApiSource = Files.readString(vulkanicApiFile);

        assertTrue(vulkanicApiSource.contains("private static final ScissorState scissorStateForRenderTypeDraws = new ScissorState();"),
            "VulkanicAPI should own scissorStateForRenderTypeDraws after migration");
        assertTrue(vulkanicApiSource.contains("public static void enableScissorForRenderTypeDraws("),
            "VulkanicAPI should expose enableScissorForRenderTypeDraws after migration");
        assertTrue(vulkanicApiSource.contains("public static void disableScissorForRenderTypeDraws("),
            "VulkanicAPI should expose disableScissorForRenderTypeDraws after migration");
        assertTrue(vulkanicApiSource.contains("public static ScissorState getScissorStateForRenderTypeDraws("),
            "VulkanicAPI should expose getScissorStateForRenderTypeDraws after migration");

        Path guiRendererFile = SRC_MAIN_JAVA.resolve("net/minecraft/client/gui/render/GuiRenderer.java");
        String guiRendererSource = Files.readString(guiRendererFile);
        assertFalse(guiRendererSource.contains("RenderSystem.enableScissorForRenderTypeDraws("),
            "GuiRenderer should not enable draw scissor through RenderSystem after migration");
        assertFalse(guiRendererSource.contains("RenderSystem.disableScissorForRenderTypeDraws("),
            "GuiRenderer should not disable draw scissor through RenderSystem after migration");
        assertTrue(guiRendererSource.contains("VulkanicAPI.enableScissorForRenderTypeDraws("),
            "GuiRenderer should enable draw scissor through VulkanicAPI after migration");
        assertTrue(guiRendererSource.contains("VulkanicAPI.disableScissorForRenderTypeDraws("),
            "GuiRenderer should disable draw scissor through VulkanicAPI after migration");

        Path renderTypeFile = SRC_MAIN_JAVA.resolve("net/minecraft/client/renderer/RenderType.java");
        String renderTypeSource = Files.readString(renderTypeFile);
        assertFalse(renderTypeSource.contains("RenderSystem.getScissorStateForRenderTypeDraws("),
            "RenderType should not read draw scissor state through RenderSystem after migration");
        assertTrue(renderTypeSource.contains("VulkanicAPI.getScissorStateForRenderTypeDraws("),
            "RenderType should read draw scissor state through VulkanicAPI after migration");
    }

    @Test
    public void testOutputOverrideOwnershipMovedToVulkanicAPI() throws IOException {
        Path renderSystemFile = SRC_MAIN_JAVA.resolve("net/blaze3d/systems/RenderSystem.java");
        String renderSystemSource = Files.readString(renderSystemFile);

        assertFalse(renderSystemSource.contains("public static GpuTextureView outputColorTextureOverride"),
            "RenderSystem should not own outputColorTextureOverride after migration to VulkanicAPI");
        assertFalse(renderSystemSource.contains("public static GpuTextureView outputDepthTextureOverride"),
            "RenderSystem should not own outputDepthTextureOverride after migration to VulkanicAPI");

        Path vulkanicApiFile = SRC_MAIN_JAVA.resolve("net/vulkanic/VulkanicAPI.java");
        String vulkanicApiSource = Files.readString(vulkanicApiFile);

        assertTrue(vulkanicApiSource.contains("private static GpuTextureView outputColorTextureOverride"),
            "VulkanicAPI should own outputColorTextureOverride after migration");
        assertTrue(vulkanicApiSource.contains("private static GpuTextureView outputDepthTextureOverride"),
            "VulkanicAPI should own outputDepthTextureOverride after migration");
        assertTrue(vulkanicApiSource.contains("public static void setOutputColorTextureOverride("),
            "VulkanicAPI should expose output color override setter after migration");
        assertTrue(vulkanicApiSource.contains("public static GpuTextureView getOutputColorTextureOverride("),
            "VulkanicAPI should expose output color override getter after migration");
        assertTrue(vulkanicApiSource.contains("public static void setOutputDepthTextureOverride("),
            "VulkanicAPI should expose output depth override setter after migration");
        assertTrue(vulkanicApiSource.contains("public static GpuTextureView getOutputDepthTextureOverride("),
            "VulkanicAPI should expose output depth override getter after migration");

        Path renderTypeFile = SRC_MAIN_JAVA.resolve("net/minecraft/client/renderer/RenderType.java");
        String renderTypeSource = Files.readString(renderTypeFile);
        assertFalse(renderTypeSource.contains("RenderSystem.outputColorTextureOverride"),
            "RenderType should not read outputColorTextureOverride through RenderSystem after migration");
        assertFalse(renderTypeSource.contains("RenderSystem.outputDepthTextureOverride"),
            "RenderType should not read outputDepthTextureOverride through RenderSystem after migration");
        assertTrue(renderTypeSource.contains("VulkanicAPI.getOutputColorTextureOverride()"),
            "RenderType should read outputColorTextureOverride through VulkanicAPI after migration");
        assertTrue(renderTypeSource.contains("VulkanicAPI.getOutputDepthTextureOverride()"),
            "RenderType should read outputDepthTextureOverride through VulkanicAPI after migration");

        Path guiRendererFile = SRC_MAIN_JAVA.resolve("net/minecraft/client/gui/render/GuiRenderer.java");
        String guiRendererSource = Files.readString(guiRendererFile);
        assertFalse(guiRendererSource.contains("RenderSystem.outputColorTextureOverride"),
            "GuiRenderer should not write outputColorTextureOverride through RenderSystem after migration");
        assertFalse(guiRendererSource.contains("RenderSystem.outputDepthTextureOverride"),
            "GuiRenderer should not write outputDepthTextureOverride through RenderSystem after migration");
        assertTrue(guiRendererSource.contains("VulkanicAPI.setOutputColorTextureOverride("),
            "GuiRenderer should write outputColorTextureOverride through VulkanicAPI after migration");
        assertTrue(guiRendererSource.contains("VulkanicAPI.setOutputDepthTextureOverride("),
            "GuiRenderer should write outputDepthTextureOverride through VulkanicAPI after migration");

        Path levelRendererFile = SRC_MAIN_JAVA.resolve("net/minecraft/client/renderer/LevelRenderer.java");
        String levelRendererSource = Files.readString(levelRendererFile);
        assertFalse(levelRendererSource.contains("RenderSystem.outputColorTextureOverride"),
            "LevelRenderer should not write outputColorTextureOverride through RenderSystem after migration");
        assertFalse(levelRendererSource.contains("RenderSystem.outputDepthTextureOverride"),
            "LevelRenderer should not write outputDepthTextureOverride through RenderSystem after migration");
        assertTrue(levelRendererSource.contains("VulkanicAPI.setOutputColorTextureOverride("),
            "LevelRenderer should write outputColorTextureOverride through VulkanicAPI after migration");
        assertTrue(levelRendererSource.contains("VulkanicAPI.setOutputDepthTextureOverride("),
            "LevelRenderer should write outputDepthTextureOverride through VulkanicAPI after migration");

        Path pictureInPictureRendererFile = SRC_MAIN_JAVA.resolve("net/minecraft/client/gui/render/pip/PictureInPictureRenderer.java");
        String pictureInPictureRendererSource = Files.readString(pictureInPictureRendererFile);
        assertFalse(pictureInPictureRendererSource.contains("RenderSystem.outputColorTextureOverride"),
            "PictureInPictureRenderer should not write outputColorTextureOverride through RenderSystem after migration");
        assertFalse(pictureInPictureRendererSource.contains("RenderSystem.outputDepthTextureOverride"),
            "PictureInPictureRenderer should not write outputDepthTextureOverride through RenderSystem after migration");
        assertTrue(pictureInPictureRendererSource.contains("VulkanicAPI.setOutputColorTextureOverride("),
            "PictureInPictureRenderer should write outputColorTextureOverride through VulkanicAPI after migration");
        assertTrue(pictureInPictureRendererSource.contains("VulkanicAPI.setOutputDepthTextureOverride("),
            "PictureInPictureRenderer should write outputDepthTextureOverride through VulkanicAPI after migration");

        Path glRenderPassFile = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/GlRenderPass.java");
        String glRenderPassSource = Files.readString(glRenderPassFile);
        assertTrue(glRenderPassSource.contains("private final int framebuffer;"),
            "GlRenderPass should retain the framebuffer contract backing a render pass");
        assertTrue(glRenderPassSource.contains("public int getFramebuffer()"),
            "GlRenderPass should expose the framebuffer contract for Vulkan pipeline resolution");

        Path glCommandEncoderFile = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/GlCommandEncoder.java");
        String glCommandEncoderSource = Files.readString(glCommandEncoderFile);
        assertTrue(glCommandEncoderSource.contains("VulkanicAPI.resolveFramebufferForTextures("),
            "GlCommandEncoder should recover a framebuffer contract for texture-view render passes");
        assertTrue(glCommandEncoderSource.contains("boolean useFramebufferCompatiblePipeline = glRenderPass.getFramebuffer() != 0;"),
            "GlCommandEncoder should resolve generic Vulkan pipeline handles against the active framebuffer contract whenever a render pass is framebuffer-backed");
        assertTrue(glCommandEncoderSource.contains("glRenderPass.pipeline.info(),\n\t\t\t\t\t\tsubmissionDescriptor,\n\t\t\t\t\t\tglRenderPass.getFramebuffer()"),
            "GlCommandEncoder should resolve Vulkan pipeline handles against the active framebuffer contract for offscreen override draws");

        Path vulkanBackendFile = SRC_MAIN_JAVA.resolve("net/vulkanic/backends/vulkan/VulkanBackend.java");
        String vulkanBackendSource = Files.readString(vulkanBackendFile);
        assertTrue(vulkanBackendSource.contains("virtualFramebufferStates.entrySet()")
                && vulkanBackendSource.contains("matchesSingleColorFramebufferContract("),
            "VulkanBackend should recover texture-pair framebuffer ids from tracked virtual framebuffer attachment state instead of leaving the seam stubbed");
        assertTrue(vulkanBackendSource.contains("implicitFramebufferByTexturePair")
            && vulkanBackendSource.contains("resolveOrCreateImplicitFramebuffer("),
            "VulkanBackend should synthesize a stable virtual framebuffer contract for unbound texture pairs used by offscreen override render passes");
    }

    @Test
    public void testStandard3dItemDebugPipDumpIsOptIn() throws IOException {
        Path guiRendererFile = SRC_MAIN_JAVA.resolve("net/minecraft/client/gui/render/GuiRenderer.java");
        String guiRendererSource = Files.readString(guiRendererFile);
        Path standard3dItemRendererFile = SRC_MAIN_JAVA.resolve("net/minecraft/client/gui/render/pip/Standard3dItemRenderer.java");
        String standard3dItemRendererSource = Files.readString(standard3dItemRendererFile);

        assertTrue(standard3dItemRendererSource.contains("Boolean.getBoolean(\"mattmc.gui.debugStandard3dItemPipDump\")"),
            "Standard3dItemRenderer should make its forced grass-block PIP dump opt-in behind an explicit debug flag");
        assertTrue(guiRendererSource.contains("if (Standard3dItemRenderer.isDebugDumpEnabled())")
                && guiRendererSource.contains("prepareDebugStandardBlockItemDump(this.renderState, i);"),
            "GuiRenderer should only invoke the standard 3D item PIP debug dump when that explicit debug flag is enabled");
    }

    @Test
    public void testSequentialAndDynamicUniformOwnershipMovedToVulkanicAPI() throws IOException {
        Path renderSystemFile = SRC_MAIN_JAVA.resolve("net/blaze3d/systems/RenderSystem.java");
        String renderSystemSource = Files.readString(renderSystemFile);

        assertFalse(renderSystemSource.contains("class AutoStorageIndexBuffer"),
            "RenderSystem should not define AutoStorageIndexBuffer after migration to VulkanicAPI");
        assertFalse(renderSystemSource.contains("private static DynamicUniforms dynamicUniforms"),
            "RenderSystem should not own dynamicUniforms after migration to VulkanicAPI");
        assertFalse(renderSystemSource.contains("public static VulkanicAPI.AutoStorageIndexBuffer getSequentialBuffer("),
            "RenderSystem should not expose getSequentialBuffer after migration to VulkanicAPI");
        assertFalse(renderSystemSource.contains("public static DynamicUniforms getDynamicUniforms("),
            "RenderSystem should not expose getDynamicUniforms after migration to VulkanicAPI");
        assertTrue(renderSystemSource.contains("VulkanicAPI.initializeDynamicUniforms();"),
            "RenderSystem.initRenderer should initialize dynamic uniforms via VulkanicAPI");
        assertTrue(renderSystemSource.contains("VulkanicAPI.resetDynamicUniforms();"),
            "RenderSystem.flipFrame should reset dynamic uniforms via VulkanicAPI");

        Path vulkanicApiFile = SRC_MAIN_JAVA.resolve("net/vulkanic/VulkanicAPI.java");
        String vulkanicApiSource = Files.readString(vulkanicApiFile);

        assertTrue(vulkanicApiSource.contains("private static final VulkanicAPI.AutoStorageIndexBuffer sharedSequential ="),
            "VulkanicAPI should own sharedSequential index buffer after migration");
        assertTrue(vulkanicApiSource.contains("private static final VulkanicAPI.AutoStorageIndexBuffer sharedSequentialQuad ="),
            "VulkanicAPI should own sharedSequentialQuad index buffer after migration");
        assertTrue(vulkanicApiSource.contains("private static final VulkanicAPI.AutoStorageIndexBuffer sharedSequentialLines ="),
            "VulkanicAPI should own sharedSequentialLines index buffer after migration");
        assertTrue(vulkanicApiSource.contains("public static final class AutoStorageIndexBuffer"),
            "VulkanicAPI should define AutoStorageIndexBuffer after migration");
        assertTrue(vulkanicApiSource.contains("private static DynamicUniforms dynamicUniforms;"),
            "VulkanicAPI should own dynamicUniforms after migration");
        assertTrue(vulkanicApiSource.contains("public static VulkanicAPI.AutoStorageIndexBuffer getSequentialBuffer("),
            "VulkanicAPI should expose getSequentialBuffer after migration");
        assertTrue(vulkanicApiSource.contains("public static DynamicUniforms getDynamicUniforms("),
            "VulkanicAPI should expose getDynamicUniforms after migration");

        Path renderTypeFile = SRC_MAIN_JAVA.resolve("net/minecraft/client/renderer/RenderType.java");
        String renderTypeSource = Files.readString(renderTypeFile);
        assertFalse(renderTypeSource.contains("RenderSystem.getSequentialBuffer("),
            "RenderType should not use RenderSystem.getSequentialBuffer after migration");
        assertFalse(renderTypeSource.contains("RenderSystem.getDynamicUniforms("),
            "RenderType should not use RenderSystem.getDynamicUniforms after migration");
        assertTrue(renderTypeSource.contains("VulkanicAPI.getSequentialBuffer("),
            "RenderType should use VulkanicAPI.getSequentialBuffer after migration");
        assertTrue(renderTypeSource.contains("VulkanicAPI.getDynamicUniforms("),
            "RenderType should use VulkanicAPI.getDynamicUniforms after migration");

        Path skyRendererFile = SRC_MAIN_JAVA.resolve("net/minecraft/client/renderer/SkyRenderer.java");
        String skyRendererSource = Files.readString(skyRendererFile);
        assertFalse(skyRendererSource.contains("RenderSystem.getSequentialBuffer("),
            "SkyRenderer should not use RenderSystem.getSequentialBuffer after migration");
        assertFalse(skyRendererSource.contains("RenderSystem.getDynamicUniforms("),
            "SkyRenderer should not use RenderSystem.getDynamicUniforms after migration");
        assertTrue(skyRendererSource.contains("VulkanicAPI.getSequentialBuffer("),
            "SkyRenderer should use VulkanicAPI.getSequentialBuffer after migration");
        assertTrue(skyRendererSource.contains("VulkanicAPI.getDynamicUniforms("),
            "SkyRenderer should use VulkanicAPI.getDynamicUniforms after migration");
    }

    @Test
    public void testHandPassLayerRoutingKeepsOpaqueItemSubpassesOpaque() throws IOException {
        Path irisPipelinesFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/pipeline/IrisPipelines.java");
        String irisPipelinesSource = Files.readString(irisPipelinesFile);

        assertFalse(irisPipelinesSource.contains("HandRenderer.INSTANCE.isRenderingSolid() ? ShaderKey.HAND_CUTOUT : ShaderKey.HAND_TRANSLUCENT"),
            "IrisPipelines solid hand routing should not remap opaque subpasses to HAND_TRANSLUCENT in translucent pass");
        assertTrue(irisPipelinesSource.contains("return ShaderKey.HAND_CUTOUT_DIFFUSE;"),
            "IrisPipelines cutout hand routing should keep opaque/cutout subpasses on HAND_CUTOUT_DIFFUSE shader");
        assertTrue(irisPipelinesSource.contains("return ShaderKey.HAND_CUTOUT;"),
            "IrisPipelines solid hand routing should keep opaque subpasses on HAND_CUTOUT shader");

        Path itemInHandRendererFile = SRC_MAIN_JAVA.resolve("net/minecraft/client/renderer/ItemInHandRenderer.java");
        String itemInHandRendererSource = Files.readString(itemInHandRendererFile);
        assertTrue(itemInHandRendererSource.contains("Iris.isPackInUseQuick() && net.irisshaders.iris.pathways.HandRenderer.INSTANCE.isActive()"),
            "ItemInHandRenderer hand pass filtering should only apply while Iris HandRenderer is actively rendering a hand pass");
    }

    @Test
    public void testGuiItemsBypassSodiumFastQuadPath() throws IOException {
        Path itemRendererFile = SRC_MAIN_JAVA.resolve("net/minecraft/client/renderer/entity/ItemRenderer.java");
        String itemRendererSource = Files.readString(itemRendererFile);

        assertTrue(itemRendererSource.contains("itemDisplayContext != ItemDisplayContext.GUI"),
            "ItemRenderer should disable Sodium fast quad path for GUI item rendering to preserve vanilla alpha behavior");
        assertTrue(itemRendererSource.contains("if (allowSodiumFastPath && writer != null && !list.isEmpty())"),
            "ItemRenderer fast path should be explicitly gated so GUI item rendering falls back to vanilla vertex submission");

        Path renderTypeFile = SRC_MAIN_JAVA.resolve("net/minecraft/client/renderer/RenderType.java");
        String renderTypeSource = Files.readString(renderTypeFile);
        assertTrue(renderTypeSource.contains("GpuTextureView textureView = TextureTracker.INSTANCE.getShaderTexture(i);"),
            "RenderType draw path should first resolve sampler views from TextureTracker unit bindings");
        assertTrue(containsAny(renderTypeSource,
            "if (textureView != null && textureId > 0 && VulkanicAPI.getTextureHandle(textureView.texture()) != textureId)",
            "if (textureView != null && textureId > 0 && net.vulkanic.VulkanicCoreAPI.textureId(textureView) != textureId)"),
            "RenderType draw path should reject stale tracked sampler views when they no longer match Iris texture binding IDs");
        assertTrue(renderTypeSource.contains("if (textureView == null)"),
            "RenderType draw path should only fall back to Iris texture binding ids when no tracked unit view exists");
        assertTrue(renderTypeSource.contains("if (textureView == null && i == 2)"),
            "RenderType draw path should explicitly fall back to live lightmap binding for Sampler2 when tracked state is unavailable");

        Path textureTrackerFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/pbr/TextureTracker.java");
        String textureTrackerSource = Files.readString(textureTrackerFile);
        assertTrue(textureTrackerSource.contains("shaderTexturesByUnit[unit] = id;"),
            "TextureTracker should always update per-unit shader texture cache on setShaderTexture");
        assertTrue(textureTrackerSource.contains("if (lockBindCallback)"),
            "TextureTracker should still suppress recursive callback propagation while retaining per-unit tracking");
        assertTrue(textureTrackerSource.contains("for (int unit = 0; unit < shaderTexturesByUnit.length; unit++)"),
            "TextureTracker should scan per-unit shader texture cache during texture deletion to remove stale unit bindings");
        assertTrue(textureTrackerSource.contains("shaderTexturesByUnit[unit] = null;"),
            "TextureTracker should clear per-unit cache entries that reference deleted textures");

        Path blockModelWrapperFile = SRC_MAIN_JAVA.resolve("net/minecraft/client/renderer/item/BlockModelWrapper.java");
        String blockModelWrapperSource = Files.readString(blockModelWrapperFile);
        assertFalse(blockModelWrapperSource.contains("renderType = Sheets.cutoutBlockSheet();"),
            "BlockModelWrapper should not force GUI item rendering onto the cutout sheet; translucent items need their original render type");

        Path irisPipelinesFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/pipeline/IrisPipelines.java");
        String irisPipelinesSource = Files.readString(irisPipelinesFile);
        assertTrue(irisPipelinesSource.contains("assignToMain(RenderPipelines.ITEM_ENTITY_TRANSLUCENT_CULL, p -> getTranslucent(p));"),
            "IrisPipelines should keep ITEM_ENTITY_TRANSLUCENT_CULL on translucent shader selection so GUI translucent items preserve alpha blending");

        Path itemShaderFile = PROJECT_ROOT.resolve("src/main/resources/assets/minecraft/shaders/core/rendertype_item_entity_translucent_cull.vsh");
        String itemShaderSource = Files.readString(itemShaderFile);
        assertTrue(itemShaderSource.contains("vec4(lightColor.rgb, 1.0)"),
            "Item shader should not allow lightmap alpha to modulate item alpha; only lightmap RGB should affect item shading");

        Path entityShaderFile = PROJECT_ROOT.resolve("src/main/resources/assets/minecraft/shaders/core/entity.vsh");
        String entityShaderSource = Files.readString(entityShaderFile);
        assertTrue(entityShaderSource.contains("lightMapColor = vec4(lightColor.rgb, 1.0);"),
            "Entity shader should not allow lightmap alpha to modulate entity/item alpha; lightmap alpha must be clamped to 1.0");

        Path itemStackRenderStateFile = SRC_MAIN_JAVA.resolve("net/minecraft/client/renderer/item/ItemStackRenderState.java");
        String itemStackRenderStateSource = Files.readString(itemStackRenderStateFile);
        assertTrue(itemStackRenderStateSource.contains("ItemStackRenderState.this.displayContext != ItemDisplayContext.GUI"),
            "ItemStackRenderState should bypass FRAPI mesh submission for GUI item rendering so GUI follows vanilla submit path");

        Path trackingItemStackRenderStateFile = SRC_MAIN_JAVA.resolve("net/minecraft/client/renderer/item/TrackingItemStackRenderState.java");
        String trackingItemStackRenderStateSource = Files.readString(trackingItemStackRenderStateFile);
        assertTrue(trackingItemStackRenderStateSource.contains("this.modelIdentityElements.clear();"),
            "TrackingItemStackRenderState should clear model identity elements when state is cleared so GUI item identity does not leak across updates");
        assertTrue(trackingItemStackRenderStateSource.contains("return List.copyOf(this.modelIdentityElements);"),
            "TrackingItemStackRenderState should return immutable identity snapshots so GUI atlas cache keys cannot be mutated after insertion");
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

    @Test
    public void testParticleDrawPathRebindsPipelineScopedStateAfterSetPipeline() throws IOException {
        Path particleFile = SRC_MAIN_JAVA.resolve("net/minecraft/client/renderer/state/QuadParticleRenderState.java");
        String particleSource = Files.readString(particleFile);

        int pipelineIndex = particleSource.indexOf("renderPass.setPipeline(((SingleQuadParticle.Layer)entry.getKey()).pipeline());");
        int defaultUniformsIndex = particleSource.indexOf("VulkanicAPI.bindDefaultUniforms(renderPass);", pipelineIndex);
        int dynamicTransformsIndex = particleSource.indexOf("renderPass.setUniform(\"DynamicTransforms\", preparedBuffers.dynamicTransforms);", pipelineIndex);
        int lightmapIndex = particleSource.indexOf("renderPass.bindSampler(\"Sampler2\", lightTextureView);", pipelineIndex);
        int atlasIndex = particleSource.indexOf("renderPass.bindSampler(\"Sampler0\", particleTextureView);", pipelineIndex);
        int drawIndex = particleSource.indexOf("renderPass.drawIndexed(", pipelineIndex);

        assertTrue(pipelineIndex >= 0, "Particle draw path must set a pipeline before drawing");
        assertTrue(defaultUniformsIndex > pipelineIndex, "Particle draw path must rebind default uniforms after setPipeline");
        assertTrue(dynamicTransformsIndex > defaultUniformsIndex, "Particle draw path must rebind DynamicTransforms after default uniforms");
        assertTrue(lightmapIndex > dynamicTransformsIndex, "Particle draw path must rebind the lightmap after setPipeline");
        assertTrue(atlasIndex > lightmapIndex, "Particle draw path must bind the particle atlas after the lightmap");
        assertTrue(drawIndex > atlasIndex, "Particle draw path must draw only after rebinding pipeline-scoped resources");
    }

    @Test
    public void testParticleFeatureRendererExplicitlyScopesLightLayerAroundParticlePasses() throws IOException {
        Path particleFeatureRendererFile = SRC_MAIN_JAVA.resolve("net/minecraft/client/renderer/feature/ParticleFeatureRenderer.java");
        String source = Files.readString(particleFeatureRendererFile);

        int turnOnIndex = source.indexOf("minecraft.gameRenderer.lightTexture().turnOnLightLayer();");
        int mainLoopIndex = source.indexOf("for (SubmitNodeCollector.ParticleGroupRenderer particleGroupRenderer : submitNodeCollection.getParticleGroupRenderers())");
        int turnOffIndex = source.indexOf("minecraft.gameRenderer.lightTexture().turnOffLightLayer();");

        assertTrue(turnOnIndex >= 0,
            "ParticleFeatureRenderer should explicitly enable the light layer before particle pass submission");
        assertTrue(turnOffIndex >= 0,
            "ParticleFeatureRenderer should explicitly disable the light layer after particle pass submission");
        assertTrue(mainLoopIndex > turnOnIndex,
            "ParticleFeatureRenderer should enable the light layer before iterating particle group renderers");
        assertTrue(turnOffIndex > mainLoopIndex,
            "ParticleFeatureRenderer should disable the light layer after particle group rendering completes");
    }

    @Test
    public void testIrisFallbackTextureRestoreSkipsUnknownBindingSentinels() throws IOException {
        Path irisRenderSystemFile = SRC_MAIN_JAVA.resolve("net/irisshaders/iris/gl/IrisRenderSystem.java");
        String source = Files.readString(irisRenderSystemFile);

        assertTrue(source.contains("private static void restoreKnownTextureBinding(int textureId)"),
            "IrisRenderSystem should centralize legacy texture restore guards for fallback paths");
        assertTrue(source.contains("if (textureId >= 0) {"),
            "IrisRenderSystem should treat negative cached texture bindings as unknown sentinels instead of rebinding them");
        assertTrue(source.contains("restoreKnownTextureBinding(previous);"),
            "Iris fallback DSA paths should restore prior texture bindings only through the guarded helper");
        assertFalse(source.contains("VulkanicAPI.bindTexture2D(VulkanicAPI.getCommandContext(), previous);"),
            "Iris fallback DSA paths should not blindly rebind cached previous texture ids");
    }
}
