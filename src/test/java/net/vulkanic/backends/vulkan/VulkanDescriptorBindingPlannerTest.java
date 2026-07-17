package net.vulkanic.backends.vulkan;

import net.blaze3d.textures.TextureFormat;
import net.vulkanic.PipelineDescriptor;
import net.vulkanic.PipelineResourceBindings;
import net.vulkanic.VulkanicAPI;
import net.vulkanic.VulkanicBuffer;
import net.vulkanic.VulkanicBufferSlice;
import net.vulkanic.VulkanicTexture;
import net.vulkanic.VulkanicTextureFormat;
import org.junit.jupiter.api.Test;
import org.lwjgl.vulkan.VK10;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class VulkanDescriptorBindingPlannerTest {

    @Test
    void sampledImageAndSamplerStateProduceSamplerEntry() {
        PlanFixture fixture = new PlanFixture();
        LegacyTextureStorageSnapshot texture = textureSnapshot(11, VK10.VK_IMAGE_ASPECT_COLOR_BIT, 2, 0x1100L);
        VulkanTextureView view = textureView(texture, 0x2200L, 0, 2);
        fixture.bindView(view, texture);
        fixture.unitSamplerStates.put(3, new VirtualSamplerStateSnapshot(
            VulkanicAPI.GL_LINEAR_MIPMAP_LINEAR,
            VulkanicAPI.GL_NEAREST,
            VulkanicAPI.GL_CLAMP_TO_EDGE,
            VulkanicAPI.GL_REPEAT,
            VulkanicAPI.GL_CLAMP_TO_EDGE,
            0,
            VulkanicAPI.GL_LEQUAL,
            7.0f
        ));

        VulkanDescriptorBindingPlanner.DescriptorBindingPlan plan = fixture.plan(
            List.of(binding("Sampler0", 0, PipelineDescriptor.ResourceType.SAMPLER)),
            PipelineResourceBindings.builder()
                .bindSampler("Sampler0", view, 3)
                .build()
        );

        VulkanDescriptorBindingPlanner.SamplerEntry entry = samplerEntry(plan, 0);
        assertEquals(0, entry.bindingIndex());
        assertEquals(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, entry.descriptorType());
        assertEquals(0x1100L, entry.descriptorImageViewHandle());
        assertEquals(VulkanDescriptorBindingPlanner.DescriptorTransitionRequirement.SAMPLE, entry.transitionRequirement());
        assertEquals(VulkanImageUse.SAMPLED_COLOR.vkLayout(), entry.imageLayout());
        assertFalse(entry.storageImageCompatible());
        assertTrue(entry.remappedToDefaultView());
        assertEquals(
            new VulkanDescriptorSamplerKey(
                VulkanicAPI.GL_LINEAR_MIPMAP_LINEAR,
                VulkanicAPI.GL_NEAREST,
                VulkanicAPI.GL_CLAMP_TO_EDGE,
                VulkanicAPI.GL_REPEAT,
                VulkanicAPI.GL_CLAMP_TO_EDGE,
                1,
                0,
                VulkanicAPI.GL_LEQUAL
            ),
            entry.samplerKey()
        );
        assertTrue(plan.cacheable());
    }

    @Test
    void storageImageAndSharedSamplerUseGeneralLayout() {
        PlanFixture fixture = new PlanFixture();
        LegacyTextureStorageSnapshot texture = textureSnapshot(
            12,
            VK10.VK_IMAGE_ASPECT_COLOR_BIT,
            3,
            0x1200L,
            VK10.VK_IMAGE_USAGE_STORAGE_BIT
        );
        VulkanTextureView view = textureView(texture, 0x2201L, 0, 2);
        fixture.bindView(view, texture);
        fixture.textureLayouts.put(levelKey(texture.id(), 0), VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);

        VulkanDescriptorBindingPlanner.DescriptorBindingPlan plan = fixture.plan(
            List.of(
                binding("Sampler0", 0, PipelineDescriptor.ResourceType.SAMPLER),
                binding("Image0", 1, PipelineDescriptor.ResourceType.STORAGE_IMAGE)
            ),
            PipelineResourceBindings.builder()
                .bindSampler("Sampler0", view, 0)
                .bindStorageImage("Image0", new PipelineResourceBindings.StorageImageBinding(
                    0,
                    texture.id(),
                    1,
                    false,
                    0,
                    0,
                    VulkanicAPI.GL_RGBA8
                ))
                .build()
        );

        VulkanDescriptorBindingPlanner.SamplerEntry samplerEntry = samplerEntry(plan, 0);
        assertEquals(VulkanDescriptorBindingPlanner.DescriptorTransitionRequirement.STORAGE_IMAGE, samplerEntry.transitionRequirement());
        assertEquals(VK10.VK_IMAGE_LAYOUT_GENERAL, samplerEntry.imageLayout());
        assertTrue(samplerEntry.storageImageCompatible());

        VulkanDescriptorBindingPlanner.StorageImageEntry storageEntry =
            (VulkanDescriptorBindingPlanner.StorageImageEntry) plan.entries().get(1);
        assertEquals(1, storageEntry.bindingIndex());
        assertEquals(VK10.VK_DESCRIPTOR_TYPE_STORAGE_IMAGE, storageEntry.descriptorType());
        assertEquals(texture.defaultViewHandle(), storageEntry.imageViewHandle());
        assertEquals(1, storageEntry.mipLevel());
        assertEquals(VK10.VK_IMAGE_LAYOUT_GENERAL, storageEntry.imageLayout());
    }

    @Test
    void uniformBuffersPlanDirectAndTransientCopy() {
        PlanFixture fixture = new PlanFixture();
        VulkanBuffer directBuffer = buffer(0x3100L, VulkanicBuffer.USAGE_UNIFORM);
        VulkanDescriptorBindingPlanner.DescriptorBindingPlan directPlan = fixture.plan(
            List.of(binding("Globals", 0, PipelineDescriptor.ResourceType.UNIFORM_BUFFER)),
            PipelineResourceBindings.builder()
                .bindUniformBuffer("Globals", new VulkanicBufferSlice(directBuffer, 256, 128))
                .build()
        );
        VulkanDescriptorBindingPlanner.UniformBufferEntry directEntry =
            (VulkanDescriptorBindingPlanner.UniformBufferEntry) directPlan.entries().get(0);
        assertFalse(directEntry.requiresTransientCopy());
        assertEquals(0x3100L, directEntry.descriptorBufferHandle());
        assertEquals(256, directEntry.descriptorOffset());
        assertEquals(128, directEntry.descriptorRange());
        assertTrue(directPlan.cacheable());

        VulkanBuffer transientBuffer = buffer(0x3200L, VulkanicBuffer.USAGE_VERTEX);
        VulkanDescriptorBindingPlanner.DescriptorBindingPlan transientPlan = fixture.plan(
            List.of(binding("Globals", 0, PipelineDescriptor.ResourceType.UNIFORM_BUFFER)),
            PipelineResourceBindings.builder()
                .bindUniformBuffer("Globals", new VulkanicBufferSlice(transientBuffer, 4, 64))
                .build()
        );
        VulkanDescriptorBindingPlanner.UniformBufferEntry transientEntry =
            (VulkanDescriptorBindingPlanner.UniformBufferEntry) transientPlan.entries().get(0);
        assertTrue(transientEntry.requiresTransientCopy());
        assertEquals(0x3200L, transientEntry.descriptorBufferHandle());
        assertFalse(transientPlan.cacheable());
    }

    @Test
    void texelBufferUsesTextureBindingSnapshot() {
        PlanFixture fixture = new PlanFixture();
        fixture.textureBindings.put(5, new TextureBindingSnapshot(5, 44, 0, null));
        fixture.texelBindings.put(44, new LegacyTexelBufferBinding(VulkanicAPI.GL_RGBA8, 77, 0x4400L));

        VulkanDescriptorBindingPlanner.DescriptorBindingPlan plan = fixture.plan(
            List.of(new PipelineDescriptor.ResourceBinding(
                0,
                2,
                "CloudFaces",
                PipelineDescriptor.ResourceType.TEXEL_BUFFER,
                TextureFormat.RGBA8
            )),
            PipelineResourceBindings.builder()
                .bindTexelBuffer("CloudFaces", 5)
                .build()
        );

        VulkanDescriptorBindingPlanner.TexelBufferEntry entry =
            (VulkanDescriptorBindingPlanner.TexelBufferEntry) plan.entries().get(0);
        assertEquals(2, entry.bindingIndex());
        assertEquals(VK10.VK_DESCRIPTOR_TYPE_UNIFORM_TEXEL_BUFFER, entry.descriptorType());
        assertEquals(44, entry.textureId());
        assertEquals(77, entry.legacyBufferId());
        assertEquals(0x4400L, entry.bufferViewHandle());
    }

    @Test
    void comparisonSamplerUsesDepthFallbackWhenPrimaryIsNotDepth() {
        PlanFixture fixture = new PlanFixture();
        LegacyTextureStorageSnapshot color = textureSnapshot(21, VK10.VK_IMAGE_ASPECT_COLOR_BIT, 1, 0x2100L);
        LegacyTextureStorageSnapshot depth = textureSnapshot(22, VK10.VK_IMAGE_ASPECT_DEPTH_BIT, 1, 0x2200L);
        VulkanTextureView colorView = textureView(color, 0x3100L, 0, 1);
        VulkanTextureView depthView = textureView(depth, 0x3200L, 0, 1);
        fixture.bindView(colorView, color);
        fixture.bindView(depthView, depth);

        VulkanDescriptorBindingPlanner.DescriptorBindingPlan plan = fixture.plan(
            List.of(binding("shadowtex0", 0, PipelineDescriptor.ResourceType.COMPARISON_SAMPLER)),
            PipelineResourceBindings.builder()
                .bindSampler("shadowtex0", colorView, 0)
                .bindSampler("depthtex0", depthView, 1)
                .build()
        );

        VulkanDescriptorBindingPlanner.SamplerEntry entry = samplerEntry(plan, 0);
        assertEquals(depth, entry.texture());
        assertEquals(depthView, entry.textureView());
        assertEquals(VulkanicAPI.GL_COMPARE_REF_TO_TEXTURE, entry.samplerKey().compareMode());
        assertEquals(List.of("shadowtex0->depthtex0:22"), fixture.events);
    }

    @Test
    void plansAreImmutableAndEquivalentStateProducesEquivalentPlans() {
        PlanFixture fixture = new PlanFixture();
        LegacyTextureStorageSnapshot texture = textureSnapshot(31, VK10.VK_IMAGE_ASPECT_COLOR_BIT, 1, 0x3100L);
        VulkanTextureView view = textureView(texture, 0x4100L, 0, 1);
        fixture.bindView(view, texture);
        List<PipelineDescriptor.ResourceBinding> layout =
            new ArrayList<>(List.of(binding("Sampler0", 0, PipelineDescriptor.ResourceType.SAMPLER)));
        PipelineResourceBindings bindings = PipelineResourceBindings.builder()
            .bindSampler("Sampler0", view, 0)
            .build();

        VulkanDescriptorBindingPlanner.DescriptorBindingPlan first = fixture.plan(layout, bindings);
        layout.clear();
        VulkanDescriptorBindingPlanner.DescriptorBindingPlan second = fixture.plan(
            List.of(binding("Sampler0", 0, PipelineDescriptor.ResourceType.SAMPLER)),
            bindings
        );

        assertEquals(first, second);
        assertThrows(UnsupportedOperationException.class, () -> first.entries().clear());
    }

    private static VulkanDescriptorBindingPlanner.SamplerEntry samplerEntry(
        VulkanDescriptorBindingPlanner.DescriptorBindingPlan plan,
        int index
    ) {
        assertNotNull(plan);
        return (VulkanDescriptorBindingPlanner.SamplerEntry) plan.entries().get(index);
    }

    private static PipelineDescriptor.ResourceBinding binding(
        String name,
        int binding,
        PipelineDescriptor.ResourceType type
    ) {
        return new PipelineDescriptor.ResourceBinding(0, binding, name, type, null);
    }

    private static VulkanBuffer buffer(long handle, int usage) {
        return new VulkanBuffer(handle, 0x9000L + handle, usage, 1024, "planner-buffer", () -> {});
    }

    private static VulkanTextureView textureView(
        LegacyTextureStorageSnapshot texture,
        long imageViewHandle,
        int baseMip,
        int mipCount
    ) {
        VulkanTexture vulkanTexture = new VulkanTexture(
            texture.imageHandle(),
            texture.memoryHandle(),
            texture.defaultViewHandle(),
            VulkanicTexture.USAGE_TEXTURE_BINDING,
            texture.hasDepthAspect() ? VulkanicTextureFormat.DEPTH32 : VulkanicTextureFormat.RGBA8,
            texture.width(),
            texture.height(),
            texture.depth(),
            texture.mipLevels(),
            "planner-texture-" + texture.id(),
            () -> {}
        );
        return new VulkanTextureView(vulkanTexture, imageViewHandle, baseMip, mipCount, texture.id(), () -> {});
    }

    private static LegacyTextureStorageSnapshot textureSnapshot(
        int id,
        int aspect,
        int mipLevels,
        long defaultViewHandle
    ) {
        return textureSnapshot(id, aspect, mipLevels, defaultViewHandle, 0);
    }

    private static LegacyTextureStorageSnapshot textureSnapshot(
        int id,
        int aspect,
        int mipLevels,
        long defaultViewHandle,
        int usageFlags
    ) {
        return new LegacyTextureStorageSnapshot(
            id,
            VulkanicAPI.GL_TEXTURE_2D,
            0x5000L + id,
            0x6000L + id,
            defaultViewHandle,
            aspect == VK10.VK_IMAGE_ASPECT_COLOR_BIT ? VK10.VK_FORMAT_R8G8B8A8_UNORM : VK10.VK_FORMAT_D32_SFLOAT,
            aspect,
            usageFlags,
            false,
            4,
            mipLevels,
            64,
            32,
            1,
            VulkanicAPI.GL_RGBA,
            VulkanicAPI.GL_UNSIGNED_BYTE,
            Map.of(
                VulkanicAPI.GL_TEXTURE_MIN_FILTER,
                VulkanicAPI.GL_NEAREST,
                VulkanicAPI.GL_TEXTURE_MAG_FILTER,
                VulkanicAPI.GL_LINEAR,
                VulkanicAPI.GL_TEXTURE_WRAP_S,
                VulkanicAPI.GL_REPEAT,
                VulkanicAPI.GL_TEXTURE_WRAP_T,
                VulkanicAPI.GL_REPEAT
            ),
            Map.of(0, new TextureLevelInfo(64, 32, VulkanicAPI.GL_RGBA8))
        );
    }

    private static long levelKey(int textureId, int level) {
        return (((long) textureId) << 32) ^ (level & 0xffffffffL);
    }

    private static final class PlanFixture {
        private final VulkanDescriptorBindingPlanner planner = new VulkanDescriptorBindingPlanner();
        private final Map<Integer, LegacyTextureStorageSnapshot> textures = new HashMap<>();
        private final Map<VulkanTextureView, LegacyTextureStorageSnapshot> views = new IdentityHashMap<>();
        private final Map<Integer, TextureBindingSnapshot> textureBindings = new HashMap<>();
        private final Map<Integer, LegacyTexelBufferBinding> texelBindings = new HashMap<>();
        private final Map<Integer, VirtualSamplerStateSnapshot> samplerStates = new HashMap<>();
        private final Map<Integer, VirtualSamplerStateSnapshot> unitSamplerStates = new HashMap<>();
        private final Map<Long, Integer> textureLayouts = new HashMap<>();
        private final List<String> events = new ArrayList<>();

        private void bindView(VulkanTextureView view, LegacyTextureStorageSnapshot texture) {
            views.put(view, texture);
            textures.put(texture.id(), texture);
        }

        private VulkanDescriptorBindingPlanner.DescriptorBindingPlan plan(
            List<PipelineDescriptor.ResourceBinding> layout,
            PipelineResourceBindings bindings
        ) {
            return planner.plan(new VulkanDescriptorBindingPlanner.PlanRequest(
                layout,
                bindings,
                new VulkanDescriptorBindingPlanner.TextureSnapshotLookup() {
                    @Override
                    public LegacyTextureStorageSnapshot snapshotForView(VulkanTextureView view) {
                        return views.get(view);
                    }

                    @Override
                    public LegacyTextureStorageSnapshot snapshotForTexture(int textureId) {
                        return textures.get(textureId);
                    }
                },
                textureBindings::get,
                texelBindings::get,
                new VulkanDescriptorBindingPlanner.SamplerStateLookup() {
                    @Override
                    public VirtualSamplerStateSnapshot samplerState(int sampler) {
                        return samplerStates.get(sampler);
                    }

                    @Override
                    public VirtualSamplerStateSnapshot samplerStateForTextureUnit(int unit) {
                        return unitSamplerStates.get(unit);
                    }
                },
                (textureId, level) -> textureLayouts.getOrDefault(levelKey(textureId, level), VK10.VK_IMAGE_LAYOUT_UNDEFINED),
                new VulkanDescriptorBindingPlanner.RenderStateSnapshot(false, Set.of()),
                256,
                false,
                false,
                "test:pipeline",
                0x1234L,
                new VulkanDescriptorBindingPlanner.PlannerEvents() {
                    @Override
                    public void comparisonSamplerRebound(
                        String bindingName,
                        String fallbackBindingName,
                        LegacyTextureStorageSnapshot texture
                    ) {
                        events.add(bindingName + "->" + fallbackBindingName + ":" + (texture != null ? texture.id() : 0));
                    }

                    @Override
                    public void comparisonSamplerDowngraded(String bindingName, LegacyTextureStorageSnapshot texture) {
                        events.add(bindingName + "->plain:" + (texture != null ? texture.id() : 0));
                    }
                }
            ));
        }
    }
}
