package net.vulkanic;

import net.blaze3d.pipeline.RenderPipeline;
import net.blaze3d.platform.DepthTestFunction;
import net.blaze3d.platform.LogicOp;
import net.blaze3d.platform.PolygonMode;
import net.blaze3d.shaders.UniformType;
import net.blaze3d.textures.TextureFormat;
import net.blaze3d.vertex.DefaultVertexFormat;
import net.blaze3d.vertex.VertexFormat;
import net.minecraft.resources.ResourceLocation;
import net.vulkanic.backends.opengl.OpenGLBuffer;
import net.vulkanic.backends.opengl.OpenGLCommandContext;
import net.vulkanic.backends.vulkan.VulkanBackend;
import net.vulkanic.backends.vulkan.VulkanBuffer;
import net.vulkanic.backends.vulkan.VulkanCommandContext;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VulkanDescriptorLifecycleTest {

    private static final Path PROJECT_ROOT = Paths.get(System.getProperty("user.dir"));

    @Test
    public void testVulkanDescriptorPoolAllocateUpdateResetLifecycle() {
        VulkanBackend backend = new VulkanBackend();
        PipelineDescriptor descriptor = PipelineDescriptor.fromRenderPipeline(buildTestPipeline());

        DescriptorPoolHandle pool = backend.createDescriptorPool(new DescriptorPoolDescriptor(1));
        DescriptorSetHandle descriptorSet = backend.allocateDescriptorSet(pool, descriptor);

        assertTrue(pool.isValid());
        assertTrue(descriptorSet.isValid());
        assertEquals(1, pool.allocatedSetCount());
        assertEquals(descriptor.getStableCacheKey(), descriptorSet.layoutKey());

        VulkanBuffer uniformBuffer = new VulkanBuffer(
            0x11L,
            0x22L,
            VulkanicBuffer.USAGE_UNIFORM,
            256,
            "descriptor-test-ubo",
            () -> {}
        );

        PipelineResourceBindings bindings = PipelineResourceBindings.builder()
            .bindSampler("Sampler0", 0)
            .bindUniformBuffer("Globals", new VulkanicBufferSlice(uniformBuffer, 0, 256))
            .bindTexelBuffer("CloudFaces", 3)
            .build();

        assertDoesNotThrow(() -> backend.updateDescriptorSet(descriptorSet, bindings));

        IllegalStateException exhausted = assertThrows(IllegalStateException.class,
            () -> backend.allocateDescriptorSet(pool, descriptor));
        assertTrue(exhausted.getMessage().contains("exhausted"));

        assertDoesNotThrow(() -> backend.resetDescriptorPool(pool));
        assertEquals(0, pool.allocatedSetCount());
        assertFalse(descriptorSet.isValid());

        DescriptorSetHandle secondSet = backend.allocateDescriptorSet(pool, descriptor);
        assertTrue(secondSet.isValid());

        secondSet.close();
        pool.close();
        uniformBuffer.close();
        assertFalse(pool.isValid());
    }

    @Test
    public void testBindPipelineResourcesRejectsNonVulkanContextBeforeFurtherChecks() {
        VulkanBackend backend = new VulkanBackend();
        PipelineDescriptor descriptor = PipelineDescriptor.fromRenderPipeline(buildTestPipeline());
        VulkanicTextureView samplerView = createSamplerTextureView();

        VulkanBuffer uniformBuffer = new VulkanBuffer(
            0x31L,
            0x32L,
            VulkanicBuffer.USAGE_UNIFORM,
            256,
            "ctx-check-ubo",
            () -> {}
        );

        PipelineResourceBindings bindings = PipelineResourceBindings.builder()
            .bindSampler("Sampler0", samplerView, 0)
            .bindUniformBuffer("Globals", new VulkanicBufferSlice(uniformBuffer, 0, 256))
            .bindTexelBuffer("CloudFaces", 3)
            .build();

        IllegalArgumentException mismatch = assertThrows(IllegalArgumentException.class,
            () -> backend.bindPipelineResources(
                OpenGLCommandContext.IMMEDIATE,
                new StubPipelineHandle(true),
                descriptor,
                bindings));

        assertTrue(mismatch.getMessage().contains("bindPipelineResources requires VulkanCommandContext"));
        uniformBuffer.close();
    }

    @Test
    public void testBindPipelineResourcesRejectsNonVulkanUniformBufferBinding() {
        VulkanBackend backend = new VulkanBackend();
        PipelineDescriptor descriptor = PipelineDescriptor.fromRenderPipeline(buildTestPipeline());
        VulkanicTextureView samplerView = createSamplerTextureView();

        PipelineResourceBindings bindings = PipelineResourceBindings.builder()
            .bindSampler("Sampler0", samplerView, 0)
            .bindUniformBuffer("Globals", new VulkanicBufferSlice(
                new OpenGLBuffer(77, VulkanicBuffer.USAGE_UNIFORM, 256),
                0,
                256))
            .bindTexelBuffer("CloudFaces", 3)
            .build();

        IllegalArgumentException mismatch = assertThrows(IllegalArgumentException.class,
            () -> backend.bindPipelineResources(
                new VulkanCommandContext(1L, "descriptor-test"),
                new StubPipelineHandle(true),
                descriptor,
                bindings));

        assertTrue(mismatch.getMessage().contains("must use VulkanBuffer"));
    }

    @Test
    public void testBindPipelineResourcesRejectsSamplerWithoutTextureView() {
        VulkanBackend backend = new VulkanBackend();
        PipelineDescriptor descriptor = PipelineDescriptor.fromRenderPipeline(buildTestPipeline());

        VulkanBuffer uniformBuffer = new VulkanBuffer(
            0x41L,
            0x42L,
            VulkanicBuffer.USAGE_UNIFORM,
            256,
            "sampler-view-check-ubo",
            () -> {}
        );

        PipelineResourceBindings bindings = PipelineResourceBindings.builder()
            .bindSampler("Sampler0", 0)
            .bindUniformBuffer("Globals", new VulkanicBufferSlice(uniformBuffer, 0, 256))
            .bindTexelBuffer("CloudFaces", 3)
            .build();

        IllegalArgumentException mismatch = assertThrows(IllegalArgumentException.class,
            () -> backend.bindPipelineResources(
                new VulkanCommandContext(1L, "sampler-view-test"),
                new StubPipelineHandle(true),
                descriptor,
                bindings));

        assertTrue(mismatch.getMessage().contains("must provide a VulkanicTextureView"));
        uniformBuffer.close();
    }

    @Test
    public void testBindDescriptorSetRejectsLayoutMismatch() {
        VulkanBackend backend = new VulkanBackend();

        PipelineDescriptor descriptorA = PipelineDescriptor.fromRenderPipeline(buildTestPipeline());
        PipelineDescriptor descriptorB = PipelineDescriptor.fromRenderPipeline(buildOtherPipeline());

        DescriptorPoolHandle pool = backend.createDescriptorPool(new DescriptorPoolDescriptor(1));
        DescriptorSetHandle descriptorSet = backend.allocateDescriptorSet(pool, descriptorA);

        IllegalArgumentException mismatch = assertThrows(IllegalArgumentException.class,
            () -> backend.bindDescriptorSet(
                new VulkanCommandContext(1L, "descriptor-bind"),
                new StubPipelineHandle(true),
                descriptorB,
                descriptorSet));

        assertTrue(mismatch.getMessage().toLowerCase().contains("layout key mismatch"));
    }

    @Test
    public void testVulkanDescriptorSourceNoLongerUsesUnsupportedStubs() throws Exception {
        String source = Files.readString(PROJECT_ROOT
            .resolve("src/main/java/net/vulkanic/backends/vulkan/VulkanBackend.java"));
        String descriptorManagerSource = Files.readString(PROJECT_ROOT
            .resolve("src/main/java/net/vulkanic/backends/vulkan/VulkanDescriptorManager.java"));

        assertTrue(source.contains("new VulkanDescriptorPoolHandle"),
            "Vulkan descriptor lifecycle should allocate VulkanDescriptorPoolHandle");
        assertTrue(source.contains("instanceof VulkanDescriptorSetHandle"),
            "Vulkan descriptor lifecycle should route through VulkanDescriptorSetHandle");
        assertTrue(source.contains("bindPipelineResources("),
            "Vulkan descriptor lifecycle should expose resource-binding entrypoint");
        assertTrue(source.contains("public net.vulkanic.VulkanicBuffer resolveVulkanicBuffer"),
            "Vulkan backend should expose backend-neutral buffer resolution for descriptor callsites");
        assertTrue(source.contains("resolveLegacyVulkanBuffer("),
            "Vulkan backend should resolve legacy integer buffer handles to real VulkanBuffer storage");
        assertTrue(source.contains("updateAndBindDescriptorSet("),
            "Vulkan descriptor lifecycle should route binding requests through a native descriptor update+bind helper");
        assertTrue(source.contains("descriptorManager.updateAndBindDescriptorSet(")
                && descriptorManagerSource.contains("descriptorSetCache"),
            "Vulkan descriptor lifecycle should cache descriptor sets in the backend-internal descriptor manager");
        assertTrue(source.contains("lastBoundGraphicsPipelineByCommandBuffer"),
            "Vulkan backend should track per-command-buffer pipeline binds so redundant vkCmdBindPipeline calls can be skipped safely");
        assertTrue(descriptorManagerSource.contains("vkUpdateDescriptorSets"),
            "Vulkan descriptor lifecycle should now issue vkUpdateDescriptorSets on descriptor binding path");
        assertTrue(descriptorManagerSource.contains("vkCmdBindDescriptorSets"),
            "Vulkan descriptor lifecycle should now issue vkCmdBindDescriptorSets on descriptor binding path");
        assertTrue(source.contains("bindLegacyTexelBufferForActiveUnit("),
            "Vulkan texel-buffer bindings should route through native VkBufferView-backed helper");
        assertTrue(source.contains("vkCreateBufferView(texBuffer)"),
            "Vulkan texel-buffer path should create VkBufferView objects for descriptor writes");

        assertFalse(source.contains("Vulkan-native descriptor pool lifecycle is not implemented yet."),
            "Descriptor pool lifecycle should no longer be marked unsupported");
        assertFalse(source.contains("Vulkan-native descriptor set allocation is not implemented yet."),
            "Descriptor set allocation should no longer be marked unsupported");
        assertFalse(source.contains("Vulkan-native descriptor set updates are not implemented yet."),
            "Descriptor set update should no longer be marked unsupported");
    }

    @Test
    public void testVulkanNativeRenderPassDirtyTracksDescriptorSubmissions() throws Exception {
        String source = Files.readString(PROJECT_ROOT
            .resolve("src/main/java/net/vulkanic/backends/vulkan/VulkanNativeCommandEncoder.java"));

        assertTrue(source.contains("resourceStateGeneration"),
            "Native Vulkan render pass should track descriptor-affecting state changes");
        assertTrue(source.contains("cachedResourceSubmission"),
            "Native Vulkan render pass should remember the last resolved descriptor submission");
        assertTrue(source.contains("isCachedResourceSubmission"),
            "Native Vulkan render pass should skip redundant pipeline/resource submissions only after comparing resolved state");
        assertTrue(source.contains("markResourceBindingsDirty"),
            "Native Vulkan render pass should invalidate descriptor cache when resource bindings change");
        assertTrue(source.contains("cacheSubmittedResources"),
            "Native Vulkan render pass should refresh its descriptor submission cache after binding resources");
    }

    @Test
    public void testNativeRenderPassUsesIrisOverrideProgramDescriptors() throws Exception {
        String encoderSource = Files.readString(PROJECT_ROOT
            .resolve("src/main/java/net/vulkanic/backends/vulkan/VulkanNativeCommandEncoder.java"));
        String backendSource = Files.readString(PROJECT_ROOT
            .resolve("src/main/java/net/vulkanic/backends/vulkan/VulkanBackend.java"));

        assertTrue(encoderSource.contains("resolveIrisOverrideProgram"),
            "Native Vulkan render passes must resolve the same Iris RenderPipeline overrides used by OpenGL");
        assertTrue(encoderSource.contains("createIrisProgramLiveDescriptor"),
            "Native Vulkan render passes must build pipeline descriptors from the linked Iris program SPIR-V");
        assertTrue(encoderSource.contains("setupIrisProgramStateIfNeeded"),
            "Native Vulkan render passes must let Iris populate shader uniforms and sampler units before descriptor binding");
        assertTrue(encoderSource.contains("clearIrisProgramState"),
            "Native Vulkan render passes must clear Iris override state after the pass");
        assertTrue(encoderSource.contains("irisProgramResourceLookup"),
            "Native Vulkan render passes must resolve Iris sampler names rather than only vanilla Sampler0-style names");
        assertTrue(backendSource.contains("resolveLegacySamplerUnitForProgram"),
            "Vulkan Iris resource lookup must use reflected opaque sampler uniform values to find the intended texture unit");
        assertTrue(backendSource.contains("resolveLegacySamplerViewForProgram"),
            "Vulkan Iris resource lookup must bind the texture actually visible through the reflected sampler unit");
    }

    @Test
    public void testSharedStorageImageSamplerDescriptorsUseGeneralLayout() throws Exception {
        String source = Files.readString(PROJECT_ROOT
            .resolve("src/main/java/net/vulkanic/backends/vulkan/VulkanBackend.java"))
            .replace("\r\n", "\n")
            .replace('\r', '\n');

        assertTrue(source.contains("collectStorageImageTextureIds"),
            "Vulkan descriptor planning should detect textures that are also bound as storage images");
        assertTrue(source.contains("storageImageCompatibleSample"),
            "Sampler descriptor resolution should classify sampled textures that also have storage-image bindings");
        assertTrue(source.contains("isStorageImageLayoutCompatibleSampler"),
            "Sampler descriptor resolution should preserve GENERAL layout for storage-capable sampled textures already in GENERAL");
        assertTrue(source.contains("trackedLayoutForLevel(texture, level) != VK10.VK_IMAGE_LAYOUT_GENERAL"),
            "Storage-compatible sampler detection should be based on the tracked per-mip image layout");
        assertTrue(source.contains("descriptorImageLayoutFor(sampledLegacyTexture, storageImageCompatibleSample)"),
            "Sampler descriptor writes should choose their image layout with storage-image compatibility in mind");
        assertTrue(source.contains("if (storageImageCompatible) {\n                return VK10.VK_IMAGE_LAYOUT_GENERAL;\n            }"),
            "A sampled texture that is also bound as a storage image must be described with GENERAL layout");
        assertTrue(source.contains("transitionLegacyTextureToStorageImageLayout(\n                    sampledLegacyTexture,"),
            "Shared storage/sampled textures should be transitioned to the storage-compatible layout instead of shader-read-only");
        assertTrue(source.contains("} else if (!storageImageCompatibleSample) {\n                transitionLegacyTextureToSampleLayout("),
            "Storage/general-compatible sampled textures should not be forced back to shader-read-only during descriptor writes");
    }

    @Test
    public void testFeedbackLoopSamplerLayoutIsOnlyUsedForActiveAttachments() throws Exception {
        String source = Files.readString(PROJECT_ROOT
            .resolve("src/main/java/net/vulkanic/backends/vulkan/VulkanBackend.java"));
        String normalized = source.replaceAll("\\s+", " ");
        String descriptorSelection = source.substring(source.indexOf("private int descriptorImageLayoutFor"),
            source.indexOf("private boolean isStorageImageLayoutCompatibleSampler"));
        String sampleTransition = source.substring(source.indexOf("private void transitionLegacyTextureToSampleLayout(@Nullable LegacyTextureObject texture,"),
            source.indexOf("private void transitionLegacyTextureToStorageImageLayout"));
        String preferredIdleLayout = source.substring(source.indexOf("private int preferredIdleLayout"),
            source.indexOf("private void clearLegacyColorTexture"));
        String normalizedDescriptorSelection = descriptorSelection.replaceAll("\\s+", " ");
        String normalizedSampleTransition = sampleTransition.replaceAll("\\s+", " ");
        String normalizedPreferredIdleLayout = preferredIdleLayout.replaceAll("\\s+", " ");

        assertTrue(normalized.contains("private boolean shouldUseFeedbackLoopLayoutForSampling(@Nullable LegacyTextureObject texture)"),
            "Feedback-loop-capable images should not be described as feedback-loop layout for ordinary sampled reads");
        assertTrue(normalized.contains("texture.feedbackLoopCapable && renderPassRecording && (activeRenderPassColorTextures.contains(texture) || texture == activeRenderPassDepthTexture)"),
            "Feedback-loop layout should be limited to textures that are actively bound as attachments in the current render pass");
        assertTrue(normalized.contains("if (shouldUseFeedbackLoopLayoutForSampling(texture)) { return VulkanImageUse.FEEDBACK_LOOP.vkLayout(); }"),
            "Descriptor image layout selection should route feedback-loop layout through the active-attachment predicate");
        assertTrue(normalized.contains("int targetLayout = shouldUseFeedbackLoopLayoutForSampling(texture) ? EXTAttachmentFeedbackLoopLayout.VK_IMAGE_LAYOUT_ATTACHMENT_FEEDBACK_LOOP_OPTIMAL_EXT"),
            "Sampler transitions should route feedback-loop layout through the active-attachment predicate");
        assertTrue(normalized.contains("if (feedbackLoopCapable && usage == VulkanicResourceUsage.ATTACHMENT_FEEDBACK_LOOP)"),
            "Resource usage mapping should select feedback-loop layout only for explicit attachment feedback");
        assertFalse(normalized.contains("|| usage == VulkanicResourceUsage.SAMPLED_READ"),
            "SAMPLED_READ must resolve to shader/depth read-only layouts even for feedback-loop-capable images");
        assertFalse(normalizedDescriptorSelection.contains("if (texture.feedbackLoopCapable) { return VulkanImageUse.FEEDBACK_LOOP.vkLayout(); }"),
            "Descriptor image layout selection should not permanently force every feedback-capable texture into feedback-loop layout");
        assertFalse(normalizedSampleTransition.contains("int targetLayout = texture.feedbackLoopCapable ? EXTAttachmentFeedbackLoopLayout.VK_IMAGE_LAYOUT_ATTACHMENT_FEEDBACK_LOOP_OPTIMAL_EXT"),
            "Sampler transitions should use feedback-loop layout only for active attachment feedback, not all feedback-capable textures");
        assertTrue(normalizedPreferredIdleLayout.contains("Feedback-loop layout is a render-pass usage, not an idle layout"),
            "Idle layout policy should document why feedback-loop-capable textures still idle as sampled reads");
        assertFalse(normalizedPreferredIdleLayout.contains("if (texture.feedbackLoopCapable) { return VulkanImageUse.FEEDBACK_LOOP.vkLayout(); }"),
            "Feedback-loop-capable textures should not permanently idle in feedback-loop layout");
        assertTrue(normalizedPreferredIdleLayout.contains("? VulkanImageUse.SAMPLED_COLOR.vkLayout() : VulkanImageUse.SAMPLED_DEPTH.vkLayout()"),
            "Idle layout should match ordinary descriptor sampled-read layouts");
    }

    @Test
    public void testIllegalRenderPassLayoutTransitionsDoNotAdvanceTrackerWithoutBarrier() throws Exception {
        String source = Files.readString(PROJECT_ROOT
            .resolve("src/main/java/net/vulkanic/backends/vulkan/VulkanBackend.java"));

        int samplerWarning = source.indexOf("Illegal sampler layout transition inside render pass");
        int samplerContinue = source.indexOf("continue;", samplerWarning);
        int samplerTrack = source.indexOf("trackLayoutForLevel(texture, level, targetLayout)", samplerWarning);
        assertTrue(samplerWarning >= 0 && samplerContinue > samplerWarning,
            "Sampler transition guard should keep rejecting render-pass-time barriers");
        assertTrue(samplerTrack < 0 || samplerTrack > samplerContinue,
            "Sampler layout tracking must not advance when no vkCmdPipelineBarrier was emitted");

        int storageWarning = source.indexOf("Illegal storage-image layout transition inside render pass");
        int storageContinue = source.indexOf("continue;", storageWarning);
        int storageTrack = source.indexOf("trackLayoutForLevel(texture, level, VK10.VK_IMAGE_LAYOUT_GENERAL)", storageWarning);
        assertTrue(storageWarning >= 0 && storageContinue > storageWarning,
            "Storage-image transition guard should keep rejecting render-pass-time barriers");
        assertTrue(storageTrack < 0 || storageTrack > storageContinue,
            "Storage-image layout tracking must not advance when no vkCmdPipelineBarrier was emitted");
    }

    private static RenderPipeline buildTestPipeline() {
        return RenderPipeline.builder()
            .withLocation(ResourceLocation.withDefaultNamespace("vulkanic/test_pipeline_vulkan_desc"))
            .withVertexShader(ResourceLocation.withDefaultNamespace("core/test_vertex"))
            .withFragmentShader(ResourceLocation.withDefaultNamespace("core/test_fragment"))
            .withSampler("Sampler0")
            .withUniform("Globals", UniformType.UNIFORM_BUFFER)
            .withUniform("CloudFaces", UniformType.TEXEL_BUFFER, TextureFormat.RED8I)
            .withDepthTestFunction(DepthTestFunction.GREATER_DEPTH_TEST)
            .withPolygonMode(PolygonMode.WIREFRAME)
            .withCull(false)
            .withoutBlend()
            .withColorWrite(true, false)
            .withDepthWrite(false)
            .withColorLogic(LogicOp.OR_REVERSE)
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.QUADS)
            .withDepthBias(2.5f, 1.25f)
            .build();
    }

    private static RenderPipeline buildOtherPipeline() {
        return RenderPipeline.builder()
            .withLocation(ResourceLocation.withDefaultNamespace("vulkanic/test_pipeline_vulkan_desc_other"))
            .withVertexShader(ResourceLocation.withDefaultNamespace("core/test_vertex"))
            .withFragmentShader(ResourceLocation.withDefaultNamespace("core/test_fragment"))
            .withSampler("Sampler0")
            .withUniform("Globals", UniformType.UNIFORM_BUFFER)
            .withUniform("CloudFaces", UniformType.TEXEL_BUFFER, TextureFormat.RED8I)
            .withDepthTestFunction(DepthTestFunction.LESS_DEPTH_TEST)
            .withPolygonMode(PolygonMode.FILL)
            .withCull(true)
            .withoutBlend()
            .withColorWrite(true, true)
            .withDepthWrite(true)
            .withColorLogic(LogicOp.OR_REVERSE)
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.TRIANGLES)
            .withDepthBias(0.0f, 0.0f)
            .build();
    }

    private static final class StubPipelineHandle implements PipelineHandle {
        private boolean valid;

        private StubPipelineHandle(boolean valid) {
            this.valid = valid;
        }

        @Override
        public boolean isValid() {
            return valid;
        }

        @Override
        public void close() {
            valid = false;
        }
    }

    private static VulkanicTextureView createSamplerTextureView() {
        VulkanicTexture texture = new VulkanicTexture() {
            @Override public int getWidth(int mipLevel) { return 16; }
            @Override public int getHeight(int mipLevel) { return 16; }
            @Override public int getMipLevels() { return 1; }
            @Override public int getDepthOrLayers() { return 1; }
            @Override public VulkanicTextureFormat getVulkanicFormat() { return VulkanicTextureFormat.RGBA8; }
            @Override public int usage() { return VulkanicTexture.USAGE_TEXTURE_BINDING; }
            @Override public String getLabel() { return "sampler-view-texture"; }
            @Override public boolean isClosed() { return false; }
            @Override public void close() {}
        };

        return new VulkanicTextureView() {
            @Override public VulkanicTexture texture() { return texture; }
            @Override public int getBaseMipLevel() { return 0; }
            @Override public int getMipLevelCount() { return 1; }
            @Override public boolean isClosed() { return false; }
            @Override public void close() {}
        };
    }
}
