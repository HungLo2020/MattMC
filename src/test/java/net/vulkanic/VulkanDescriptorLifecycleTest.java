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
        assertTrue(source.contains("descriptorSetCache"),
            "Vulkan descriptor lifecycle should cache descriptor sets for repeated resolved bindings instead of allocating every bind");
        assertTrue(source.contains("lastBoundGraphicsPipelineByCommandBuffer"),
            "Vulkan backend should track per-command-buffer pipeline binds so redundant vkCmdBindPipeline calls can be skipped safely");
        assertTrue(source.contains("vkUpdateDescriptorSets"),
            "Vulkan descriptor lifecycle should now issue vkUpdateDescriptorSets on descriptor binding path");
        assertTrue(source.contains("vkCmdBindDescriptorSets"),
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
