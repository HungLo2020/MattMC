package net.vulkanic.backends.vulkan;

import net.blaze3d.textures.TextureFormat;
import net.minecraft.resources.ResourceLocation;
import net.vulkanic.PipelineDescriptor;
import net.vulkanic.PipelineResourceBindings;
import net.vulkanic.VulkanicAPI;
import net.vulkanic.VulkanicBuffer;
import net.vulkanic.VulkanicBufferSlice;
import net.vulkanic.VulkanicPassResourceModel;
import net.vulkanic.VulkanicTexture;
import net.vulkanic.VulkanicTextureFormat;
import org.junit.jupiter.api.Test;
import org.lwjgl.vulkan.VK10;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
        VulkanImageResourceViewCoordinator.ImageStorageSnapshot texture = textureSnapshot(11, VK10.VK_IMAGE_ASPECT_COLOR_BIT, 2, 0x1100L);
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
        assertEquals(VulkanicPassResourceModel.ResourceKind.SAMPLED_TEXTURE, entry.resourceUse().kind());
        assertEquals(VulkanicPassResourceModel.Access.READ, entry.resourceUse().access());
        assertEquals("texture:11:view-mip:0+2", entry.resourceUse().resource().stableKey());
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
    void compactResourceBindingTableProducesEquivalentSamplerPlan() {
        PlanFixture fixture = new PlanFixture();
        VulkanImageResourceViewCoordinator.ImageStorageSnapshot texture = textureSnapshot(21, VK10.VK_IMAGE_ASPECT_COLOR_BIT, 2, 0x2100L);
        VulkanTextureView view = textureView(texture, 0x2200L, 0, 2);
        fixture.bindView(view, texture);
        fixture.unitSamplerStates.put(4, new VirtualSamplerStateSnapshot(
            VulkanicAPI.GL_LINEAR,
            VulkanicAPI.GL_LINEAR,
            VulkanicAPI.GL_CLAMP_TO_EDGE,
            VulkanicAPI.GL_CLAMP_TO_EDGE,
            VulkanicAPI.GL_CLAMP_TO_EDGE,
            0,
            VulkanicAPI.GL_LEQUAL,
            0.0f
        ));
        List<PipelineDescriptor.ResourceBinding> layout =
            List.of(binding("Sampler0", 0, PipelineDescriptor.ResourceType.SAMPLER));
        PipelineResourceBindings bindings = PipelineResourceBindings.builder()
            .bindSampler("Sampler0", view, 4)
            .build();

        VulkanDescriptorBindingPlanner.SamplerEntry oldEntry = samplerEntry(fixture.plan(layout, bindings), 0);
        VulkanDescriptorBindingPlanner.SamplerEntry compactEntry = samplerEntry(fixture.compactPlan(layout, bindings), 0);

        assertEquals(oldEntry.bindingIndex(), compactEntry.bindingIndex());
        assertEquals(oldEntry.descriptorType(), compactEntry.descriptorType());
        assertEquals(oldEntry.descriptorImageViewHandle(), compactEntry.descriptorImageViewHandle());
        assertEquals(oldEntry.imageLayout(), compactEntry.imageLayout());
        assertEquals(oldEntry.samplerKey(), compactEntry.samplerKey());
        assertEquals(oldEntry.resourceUse(), compactEntry.resourceUse());
    }

    @Test
    void compactResourceBindingTableTracksMissingAndRejectsInvalidSlots() {
        PipelineDescriptor.ResourceBinding sampler = binding("Sampler0", 0, PipelineDescriptor.ResourceType.SAMPLER);
        VulkanCompactResourceBindingTable table = new VulkanCompactResourceBindingTable(
            descriptor(layout(sampler)),
            new PipelineDescriptor.ResourceBinding[]{sampler},
            new Object[1],
            0,
            1
        );

        assertFalse(table.completeCoverage());
        assertEquals(List.of("Sampler0(SAMPLER)"), table.missingResources());
        assertThrows(IllegalArgumentException.class, () ->
            new VulkanCompactResourceBindingTable(
                descriptor(layout(sampler)),
                new PipelineDescriptor.ResourceBinding[]{sampler},
                new Object[]{new VulkanicBufferSlice(buffer(0x9900L, VulkanicBuffer.USAGE_UNIFORM), 0, 16)},
                1,
                0
            )
        );
    }

    @Test
    void storageImageAndSharedSamplerUseGeneralLayout() {
        PlanFixture fixture = new PlanFixture();
        VulkanImageResourceViewCoordinator.ImageStorageSnapshot texture = textureSnapshot(
            12,
            VK10.VK_IMAGE_ASPECT_COLOR_BIT,
            3,
            0x1200L,
            VK10.VK_IMAGE_USAGE_STORAGE_BIT
        );
        VulkanTextureView view = textureView(texture, 0x2201L, 0, 2);
        fixture.bindView(view, texture);
        fixture.textureLayouts.put(levelKey(texture.textureId(), 0), VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);

        VulkanDescriptorBindingPlanner.DescriptorBindingPlan plan = fixture.plan(
            List.of(
                binding("Sampler0", 0, PipelineDescriptor.ResourceType.SAMPLER),
                binding("Image0", 1, PipelineDescriptor.ResourceType.STORAGE_IMAGE)
            ),
            PipelineResourceBindings.builder()
                .bindSampler("Sampler0", view, 0)
                .bindStorageImage("Image0", new PipelineResourceBindings.StorageImageBinding(
                    0,
                    texture.textureId(),
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
        assertEquals(VulkanicPassResourceModel.ResourceKind.STORAGE_TEXTURE, storageEntry.resourceUse().kind());
        assertEquals(VulkanicPassResourceModel.Access.READ_WRITE, storageEntry.resourceUse().access());
        assertEquals("texture:12:storage-mip:1", storageEntry.resourceUse().resource().stableKey());
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
        assertEquals(VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, directEntry.descriptorType());
        assertEquals(0x3100L, directEntry.descriptorBufferHandle());
        assertEquals(256, directEntry.descriptorOffset());
        assertEquals(128, directEntry.descriptorRange());
        assertEquals(VulkanicPassResourceModel.ResourceKind.UNIFORM_BUFFER, directEntry.resourceUse().kind());
        assertEquals(VulkanicPassResourceModel.Access.READ, directEntry.resourceUse().access());
        assertEquals("uniform:Globals:offset:256:length:128", directEntry.resourceUse().resource().stableKey());
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
        assertEquals(VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, transientEntry.descriptorType());
        assertEquals(0x3200L, transientEntry.descriptorBufferHandle());
        assertFalse(transientPlan.cacheable());

        VulkanBuffer dynamicTransformsSource = buffer(0x3300L, VulkanicBuffer.USAGE_VERTEX);
        VulkanDescriptorBindingPlanner.DescriptorBindingPlan dynamicPlan = fixture.plan(
            List.of(binding("DynamicTransforms", 0, PipelineDescriptor.ResourceType.UNIFORM_BUFFER)),
            PipelineResourceBindings.builder()
                .bindUniformBuffer("DynamicTransforms", new VulkanicBufferSlice(dynamicTransformsSource, 4, 164))
                .build()
        );
        VulkanDescriptorBindingPlanner.UniformBufferEntry dynamicEntry =
            (VulkanDescriptorBindingPlanner.UniformBufferEntry) dynamicPlan.entries().get(0);
        assertFalse(dynamicEntry.requiresTransientCopy());
        assertEquals(VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC, dynamicEntry.descriptorType());
        assertEquals(0x3300L, dynamicEntry.descriptorBufferHandle());
        assertTrue(dynamicPlan.cacheable());

        VulkanBuffer standaloneSource = buffer(0x3400L, VulkanicBuffer.USAGE_VERTEX);
        VulkanDescriptorBindingPlanner.DescriptorBindingPlan standalonePlan = fixture.plan(
            List.of(binding("VulkanicStandaloneUniforms", 0, PipelineDescriptor.ResourceType.UNIFORM_BUFFER)),
            PipelineResourceBindings.builder()
                .bindUniformBuffer("VulkanicStandaloneUniforms", new VulkanicBufferSlice(standaloneSource, 8, 256))
                .build()
        );
        VulkanDescriptorBindingPlanner.UniformBufferEntry standaloneEntry =
            (VulkanDescriptorBindingPlanner.UniformBufferEntry) standalonePlan.entries().get(0);
        assertFalse(standaloneEntry.requiresTransientCopy());
        assertEquals(VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC, standaloneEntry.descriptorType());
        assertEquals(0x3400L, standaloneEntry.descriptorBufferHandle());
        assertTrue(standalonePlan.cacheable());
    }

    @Test
    void texelBufferUsesTextureBindingSnapshot() {
        PlanFixture fixture = new PlanFixture();
        fixture.textureBindings.put(5, new TextureBindingSnapshot(5, 44, 0, null));
        fixture.texelBindings.put(44, new VulkanImageResourceViewCoordinator.TexelBufferViewPlan(44, VulkanicAPI.GL_RGBA8, 77, 0x4400L));

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
        assertEquals(VulkanicPassResourceModel.ResourceKind.TEXEL_BUFFER, entry.resourceUse().kind());
        assertEquals("legacy-buffer:77:format:" + VulkanicAPI.GL_RGBA8, entry.resourceUse().resource().stableKey());
    }

    @Test
    void comparisonSamplerUsesDepthFallbackWhenPrimaryIsNotDepth() {
        PlanFixture fixture = new PlanFixture();
        VulkanImageResourceViewCoordinator.ImageStorageSnapshot color = textureSnapshot(21, VK10.VK_IMAGE_ASPECT_COLOR_BIT, 1, 0x2100L);
        VulkanImageResourceViewCoordinator.ImageStorageSnapshot depth = textureSnapshot(22, VK10.VK_IMAGE_ASPECT_DEPTH_BIT, 1, 0x2200L);
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
        VulkanImageResourceViewCoordinator.ImageStorageSnapshot texture = textureSnapshot(31, VK10.VK_IMAGE_ASPECT_COLOR_BIT, 1, 0x3100L);
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
        assertEquals(
            first.resourceUses().get(0).resource().stableKey(),
            second.resourceUses().get(0).resource().stableKey()
        );
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

    private static PipelineDescriptor.ResourceLayout layout(PipelineDescriptor.ResourceBinding... bindings) {
        return new PipelineDescriptor.ResourceLayout(List.of(bindings));
    }

    private static PipelineDescriptor descriptor(PipelineDescriptor.ResourceLayout layout) {
        PipelineDescriptor.PortableState state = new PipelineDescriptor.PortableState(
            ResourceLocation.withDefaultNamespace("test/pipeline"),
            ResourceLocation.withDefaultNamespace("test/pipeline/vertex"),
            ResourceLocation.withDefaultNamespace("test/pipeline/fragment"),
            Map.of(),
            Set.of(),
            List.of(),
            List.of(),
            Optional.empty(),
            net.blaze3d.platform.DepthTestFunction.NO_DEPTH_TEST,
            net.blaze3d.platform.PolygonMode.FILL,
            false,
            VulkanicAPI.GL_BACK,
            true,
            true,
            false,
            net.blaze3d.platform.LogicOp.NONE,
            net.blaze3d.vertex.DefaultVertexFormat.POSITION,
            net.blaze3d.vertex.VertexFormat.Mode.TRIANGLES,
            0.0F,
            0.0F
        );
        return PipelineDescriptor.fromPortableState(state).withResourceLayout(layout);
    }

    private static VulkanBuffer buffer(long handle, int usage) {
        return new VulkanBuffer(handle, 0x9000L + handle, usage, 1024, "planner-buffer", () -> {});
    }

    private static VulkanTextureView textureView(
        VulkanImageResourceViewCoordinator.ImageStorageSnapshot texture,
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
            "planner-texture-" + texture.textureId(),
            () -> {}
        );
        return new VulkanTextureView(vulkanTexture, imageViewHandle, baseMip, mipCount, texture.textureId(), () -> {});
    }

    private static VulkanImageResourceViewCoordinator.ImageStorageSnapshot textureSnapshot(
        int id,
        int aspect,
        int mipLevels,
        long defaultViewHandle
    ) {
        return textureSnapshot(id, aspect, mipLevels, defaultViewHandle, 0);
    }

    private static VulkanImageResourceViewCoordinator.ImageStorageSnapshot textureSnapshot(
        int id,
        int aspect,
        int mipLevels,
        long defaultViewHandle,
        int usageFlags
    ) {
        return new VulkanImageResourceViewCoordinator.ImageStorageSnapshot(
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

    private static VulkanImageResourceViewCoordinator.DescriptorImagePlan sampledImagePlan(
        VulkanTextureView view,
        VulkanImageResourceViewCoordinator.ImageStorageSnapshot storage,
        Set<Integer> storageImageTextureIds,
        VulkanDescriptorBindingPlanner.LayoutLookup layoutLookup,
        VulkanDescriptorBindingPlanner.RenderStateSnapshot renderState
    ) {
        long requestedImageViewHandle = view.getVkImageViewHandle();
        long descriptorImageViewHandle = requestedImageViewHandle;
        int baseMipLevel = Math.max(0, view.getBaseMipLevel());
        int mipLevelCount = Math.max(1, view.getMipLevelCount());
        boolean remappedToDefaultView = false;
        if (storage != null
            && storage.defaultViewHandle() != VK10.VK_NULL_HANDLE
            && view.getBaseMipLevel() == 0
            && view.getMipLevelCount() >= storage.mipLevels()) {
            descriptorImageViewHandle = storage.defaultViewHandle();
            mipLevelCount = Math.max(1, storage.mipLevels());
            remappedToDefaultView = requestedImageViewHandle != descriptorImageViewHandle;
        }
        boolean explicitStorage = storage != null && storageImageTextureIds.contains(storage.textureId());
        boolean storageCompatible = explicitStorage || storageImageCompatible(storage, baseMipLevel, mipLevelCount, layoutLookup);
        VulkanDescriptorBindingPlanner.DescriptorTransitionRequirement transitionRequirement = storage == null
            ? VulkanDescriptorBindingPlanner.DescriptorTransitionRequirement.NONE
            : explicitStorage
            ? VulkanDescriptorBindingPlanner.DescriptorTransitionRequirement.STORAGE_IMAGE
            : storageCompatible
            ? VulkanDescriptorBindingPlanner.DescriptorTransitionRequirement.NONE
            : VulkanDescriptorBindingPlanner.DescriptorTransitionRequirement.SAMPLE;
        boolean requiresDepthOnlyView = storage != null && storage.hasDepthAspect() && storage.hasStencilAspect();
        VulkanImageResourceViewCoordinator.ViewMaterializationRequest materializationRequest = requiresDepthOnlyView
            ? new VulkanImageResourceViewCoordinator.ViewMaterializationRequest(
                storage.textureId(),
                storage.imageHandle(),
                storage.vkFormat(),
                VK10.VK_IMAGE_ASPECT_DEPTH_BIT,
                baseMipLevel,
                mipLevelCount,
                VulkanImageResourceViewCoordinator.layerCount(storage),
                VulkanImageResourceViewCoordinator.isCubemapTarget(storage.target()),
                VulkanImageResourceViewCoordinator.is3DTexture(storage.target()),
                VulkanImageResourceViewCoordinator.ViewUsage.SAMPLED
            )
            : null;
        return new VulkanImageResourceViewCoordinator.DescriptorImagePlan(
            view,
            storage,
            requestedImageViewHandle,
            descriptorImageViewHandle,
            baseMipLevel,
            mipLevelCount,
            requiresDepthOnlyView,
            remappedToDefaultView,
            storageCompatible,
            transitionRequirement,
            imageLayoutFor(storage, storageCompatible, renderState),
            materializationRequest
        );
    }

    private static VulkanImageResourceViewCoordinator.DescriptorImagePlan storageImagePlan(
        VulkanImageResourceViewCoordinator.ImageStorageSnapshot storage,
        int mipLevel
    ) {
        int safeMipLevel = Math.max(0, mipLevel);
        return new VulkanImageResourceViewCoordinator.DescriptorImagePlan(
            null,
            storage,
            storage != null ? storage.defaultViewHandle() : VK10.VK_NULL_HANDLE,
            storage != null ? storage.defaultViewHandle() : VK10.VK_NULL_HANDLE,
            safeMipLevel,
            1,
            false,
            false,
            true,
            VulkanDescriptorBindingPlanner.DescriptorTransitionRequirement.STORAGE_IMAGE,
            VK10.VK_IMAGE_LAYOUT_GENERAL,
            null
        );
    }

    private static boolean storageImageCompatible(
        VulkanImageResourceViewCoordinator.ImageStorageSnapshot storage,
        int baseMipLevel,
        int mipLevelCount,
        VulkanDescriptorBindingPlanner.LayoutLookup layoutLookup
    ) {
        if (storage == null
            || storage.feedbackLoopCapable()
            || storage.aspectMask() != VK10.VK_IMAGE_ASPECT_COLOR_BIT
            || (storage.imageUsageFlags() & VK10.VK_IMAGE_USAGE_STORAGE_BIT) == 0) {
            return false;
        }
        int endExclusive = Math.min(Math.max(1, storage.mipLevels()), baseMipLevel + Math.max(1, mipLevelCount));
        for (int level = baseMipLevel; level < endExclusive; level++) {
            if (layoutLookup.trackedLayout(storage.textureId(), level) != VK10.VK_IMAGE_LAYOUT_GENERAL) {
                return false;
            }
        }
        return true;
    }

    private static int imageLayoutFor(
        VulkanImageResourceViewCoordinator.ImageStorageSnapshot storage,
        boolean storageCompatible,
        VulkanDescriptorBindingPlanner.RenderStateSnapshot renderState
    ) {
        if (storage == null) {
            return VulkanImageUse.SAMPLED_COLOR.vkLayout();
        }
        if (storageCompatible) {
            return VK10.VK_IMAGE_LAYOUT_GENERAL;
        }
        if (storage.feedbackLoopCapable()
            && renderState.renderPassRecording()
            && renderState.activeAttachmentTextureIds().contains(storage.textureId())) {
            return VulkanImageUse.FEEDBACK_LOOP.vkLayout();
        }
        return storage.hasDepthAspect()
            ? VulkanImageUse.SAMPLED_DEPTH.vkLayout()
            : VulkanImageUse.SAMPLED_COLOR.vkLayout();
    }

    private static final class PlanFixture {
        private final VulkanDescriptorBindingPlanner planner = new VulkanDescriptorBindingPlanner();
        private final Map<Integer, VulkanImageResourceViewCoordinator.ImageStorageSnapshot> textures = new HashMap<>();
        private final Map<VulkanTextureView, VulkanImageResourceViewCoordinator.ImageStorageSnapshot> views = new IdentityHashMap<>();
        private final Map<Integer, TextureBindingSnapshot> textureBindings = new HashMap<>();
        private final Map<Integer, VulkanImageResourceViewCoordinator.TexelBufferViewPlan> texelBindings = new HashMap<>();
        private final Map<Integer, VirtualSamplerStateSnapshot> samplerStates = new HashMap<>();
        private final Map<Integer, VirtualSamplerStateSnapshot> unitSamplerStates = new HashMap<>();
        private final Map<Long, Integer> textureLayouts = new HashMap<>();
        private final List<String> events = new ArrayList<>();

        private void bindView(VulkanTextureView view, VulkanImageResourceViewCoordinator.ImageStorageSnapshot texture) {
            views.put(view, texture);
            textures.put(texture.textureId(), texture);
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
                    public VulkanImageResourceViewCoordinator.ImageStorageSnapshot snapshotForView(VulkanTextureView view) {
                        return views.get(view);
                    }

                    @Override
                    public VulkanImageResourceViewCoordinator.ImageStorageSnapshot snapshotForTexture(int textureId) {
                        return textures.get(textureId);
                    }

                    @Override
                    public VulkanImageResourceViewCoordinator.DescriptorImagePlan descriptorSampledImagePlan(
                        VulkanTextureView view,
                        VulkanImageResourceViewCoordinator.ImageStorageSnapshot storage,
                        Set<Integer> storageImageTextureIds,
                        VulkanDescriptorBindingPlanner.LayoutLookup layoutLookup,
                        VulkanDescriptorBindingPlanner.RenderStateSnapshot renderState
                    ) {
                        return sampledImagePlan(view, storage, storageImageTextureIds, layoutLookup, renderState);
                    }

                    @Override
                    public VulkanImageResourceViewCoordinator.DescriptorImagePlan descriptorStorageImagePlan(
                        int textureId,
                        int mipLevel
                    ) {
                        return storageImagePlan(textures.get(textureId), mipLevel);
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
                        VulkanImageResourceViewCoordinator.ImageStorageSnapshot texture
                    ) {
                        events.add(bindingName + "->" + fallbackBindingName + ":" + (texture != null ? texture.textureId() : 0));
                    }

                    @Override
                    public void comparisonSamplerDowngraded(String bindingName, VulkanImageResourceViewCoordinator.ImageStorageSnapshot texture) {
                        events.add(bindingName + "->plain:" + (texture != null ? texture.textureId() : 0));
                    }
                }
            ));
        }

        private VulkanDescriptorBindingPlanner.DescriptorBindingPlan compactPlan(
            List<PipelineDescriptor.ResourceBinding> layout,
            PipelineResourceBindings bindings
        ) {
            int slotCount = layout.size();
            PipelineDescriptor.ResourceBinding[] layoutBindingSlots = new PipelineDescriptor.ResourceBinding[slotCount];
            Object[] resourceBindingSlots = new Object[slotCount];
            int bound = 0;
            int missing = 0;
            int index = 0;
            for (PipelineDescriptor.ResourceBinding binding : layout) {
                Object resourceBinding = switch (binding.type()) {
                    case SAMPLER, COMPARISON_SAMPLER -> bindings.getSamplerBindingOrNull(binding.name());
                    case UNIFORM_BUFFER -> bindings.getUniformBufferBindingOrNull(binding.name());
                    case STORAGE_IMAGE -> bindings.getStorageImageBindingOrNull(binding.name());
                    case TEXEL_BUFFER -> bindings.getTexelBufferBindingOrNull(binding.name());
                };
                if (resourceBinding != null) {
                    bound++;
                } else {
                    missing++;
                }
                layoutBindingSlots[index] = binding;
                resourceBindingSlots[index] = resourceBinding;
                index++;
            }
            VulkanCompactResourceBindingTable table =
                new VulkanCompactResourceBindingTable(
                    descriptor(new PipelineDescriptor.ResourceLayout(layout)),
                    layoutBindingSlots,
                    resourceBindingSlots,
                    bound,
                    missing
                );
            return planner.plan(new VulkanDescriptorBindingPlanner.CompactPlanRequest(
                table,
                new VulkanDescriptorBindingPlanner.TextureSnapshotLookup() {
                    @Override
                    public VulkanImageResourceViewCoordinator.ImageStorageSnapshot snapshotForView(VulkanTextureView view) {
                        return views.get(view);
                    }

                    @Override
                    public VulkanImageResourceViewCoordinator.ImageStorageSnapshot snapshotForTexture(int textureId) {
                        return textures.get(textureId);
                    }

                    @Override
                    public VulkanImageResourceViewCoordinator.DescriptorImagePlan descriptorSampledImagePlan(
                        VulkanTextureView view,
                        VulkanImageResourceViewCoordinator.ImageStorageSnapshot storage,
                        Set<Integer> storageImageTextureIds,
                        VulkanDescriptorBindingPlanner.LayoutLookup layoutLookup,
                        VulkanDescriptorBindingPlanner.RenderStateSnapshot renderState
                    ) {
                        return sampledImagePlan(view, storage, storageImageTextureIds, layoutLookup, renderState);
                    }

                    @Override
                    public VulkanImageResourceViewCoordinator.DescriptorImagePlan descriptorStorageImagePlan(
                        int textureId,
                        int mipLevel
                    ) {
                        return storageImagePlan(textures.get(textureId), mipLevel);
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
                        VulkanImageResourceViewCoordinator.ImageStorageSnapshot texture
                    ) {
                        events.add(bindingName + "->" + fallbackBindingName + ":" + (texture != null ? texture.textureId() : 0));
                    }

                    @Override
                    public void comparisonSamplerDowngraded(String bindingName, VulkanImageResourceViewCoordinator.ImageStorageSnapshot texture) {
                        events.add(bindingName + "->plain:" + (texture != null ? texture.textureId() : 0));
                    }
                }
            ));
        }
    }
}
