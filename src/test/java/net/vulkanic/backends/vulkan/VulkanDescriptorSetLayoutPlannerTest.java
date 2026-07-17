package net.vulkanic.backends.vulkan;

import net.blaze3d.textures.TextureFormat;
import net.vulkanic.PipelineDescriptor;
import net.vulkanic.PipelineResourceBindings;
import net.vulkanic.VulkanicAPI;
import net.vulkanic.VulkanicBuffer;
import net.vulkanic.VulkanicBufferSlice;
import net.vulkanic.VulkanicShaderStage;
import net.vulkanic.VulkanicTexture;
import net.vulkanic.VulkanicTextureFormat;
import org.junit.jupiter.api.Test;
import org.lwjgl.vulkan.VK10;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class VulkanDescriptorSetLayoutPlannerTest {
    private final VulkanDescriptorSetLayoutPlanner planner = new VulkanDescriptorSetLayoutPlanner();

    @Test
    void graphicsAndComputeStageVisibilityUseSharedClassifier() {
        VulkanDescriptorSetLayoutPlanner.DescriptorLayoutPlan graphicsPlan = planner.plan(List.of(
            binding("Sampler0", 0, 0, PipelineDescriptor.ResourceType.SAMPLER, null,
                Set.of(VulkanicShaderStage.VERTEX, VulkanicShaderStage.FRAGMENT))
        ));
        assertEquals(
            VK10.VK_SHADER_STAGE_VERTEX_BIT | VK10.VK_SHADER_STAGE_FRAGMENT_BIT,
            graphicsPlan.primarySet().bindings().getFirst().stageFlags()
        );

        VulkanDescriptorSetLayoutPlanner.DescriptorLayoutPlan computePlan = planner.plan(List.of(
            binding("Globals", 0, 0, PipelineDescriptor.ResourceType.UNIFORM_BUFFER, null,
                Set.of(VulkanicShaderStage.COMPUTE))
        ));
        assertEquals(VK10.VK_SHADER_STAGE_COMPUTE_BIT, computePlan.primarySet().bindings().getFirst().stageFlags());
    }

    @Test
    void multipleDescriptorSetsAreContiguousAndOrdered() {
        VulkanDescriptorSetLayoutPlanner.DescriptorLayoutPlan plan = planner.plan(List.of(
            binding("Sampler0", 0, 3, PipelineDescriptor.ResourceType.SAMPLER, null,
                Set.of(VulkanicShaderStage.FRAGMENT)),
            binding("Globals", 2, 1, PipelineDescriptor.ResourceType.UNIFORM_BUFFER, null,
                Set.of(VulkanicShaderStage.VERTEX))
        ));

        assertEquals(3, plan.sets().size());
        assertEquals(0, plan.sets().get(0).set());
        assertEquals(1, plan.sets().get(1).set());
        assertTrue(plan.sets().get(1).bindings().isEmpty());
        assertEquals(2, plan.sets().get(2).set());
        assertEquals("Sampler0", plan.sets().get(0).bindings().getFirst().name());
        assertEquals("Globals", plan.sets().get(2).bindings().getFirst().name());
    }

    @Test
    void descriptorTypesCountsFlagsAndImmutableSamplerSlotsArePlanned() {
        VulkanDescriptorSetLayoutPlanner.DescriptorLayoutPlan plan = planner.plan(List.of(
            binding("Sampler0", 0, 0, PipelineDescriptor.ResourceType.SAMPLER, null,
                Set.of(VulkanicShaderStage.FRAGMENT)),
            binding("Image0", 0, 1, PipelineDescriptor.ResourceType.STORAGE_IMAGE, null,
                Set.of(VulkanicShaderStage.COMPUTE)),
            binding("Globals", 0, 2, PipelineDescriptor.ResourceType.UNIFORM_BUFFER, null,
                Set.of(VulkanicShaderStage.VERTEX)),
            binding("CloudFaces", 0, 3, PipelineDescriptor.ResourceType.TEXEL_BUFFER, TextureFormat.RGBA8,
                Set.of(VulkanicShaderStage.FRAGMENT))
        ));

        Map<String, VulkanDescriptorSetLayoutPlanner.DescriptorLayoutBindingPlan> byName = byName(plan);
        assertEquals(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, byName.get("Sampler0").descriptorType());
        assertEquals(VK10.VK_DESCRIPTOR_TYPE_STORAGE_IMAGE, byName.get("Image0").descriptorType());
        assertEquals(VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, byName.get("Globals").descriptorType());
        assertEquals(VK10.VK_DESCRIPTOR_TYPE_UNIFORM_TEXEL_BUFFER, byName.get("CloudFaces").descriptorType());
        for (VulkanDescriptorSetLayoutPlanner.DescriptorLayoutBindingPlan binding : byName.values()) {
            assertEquals(1, binding.descriptorCount());
            assertEquals(0, binding.bindingFlags());
            assertEquals(null, binding.immutableSamplerRequirement());
        }
    }

    @Test
    void stableCompatibilityKeyIgnoresDeclarationOrderWithinSet() {
        VulkanDescriptorSetLayoutPlanner.DescriptorLayoutPlan first = planner.plan(List.of(
            binding("A", 0, 1, PipelineDescriptor.ResourceType.UNIFORM_BUFFER, null,
                Set.of(VulkanicShaderStage.VERTEX)),
            binding("B", 0, 0, PipelineDescriptor.ResourceType.SAMPLER, null,
                Set.of(VulkanicShaderStage.FRAGMENT))
        ));
        VulkanDescriptorSetLayoutPlanner.DescriptorLayoutPlan second = planner.plan(List.of(
            binding("B", 0, 0, PipelineDescriptor.ResourceType.SAMPLER, null,
                Set.of(VulkanicShaderStage.FRAGMENT)),
            binding("A", 0, 1, PipelineDescriptor.ResourceType.UNIFORM_BUFFER, null,
                Set.of(VulkanicShaderStage.VERTEX))
        ));

        assertEquals(first.compatibilityKey(), second.compatibilityKey());
        assertThrows(UnsupportedOperationException.class, () -> first.sets().clear());
        assertThrows(UnsupportedOperationException.class, () -> first.primarySet().bindings().clear());
    }

    @Test
    void runtimeBindingPlanDescriptorTypesMatchLayoutPlanForSetZero() {
        VulkanImageResourceViewCoordinator.ImageStorageSnapshot texture = textureSnapshot(7);
        VulkanTextureView view = textureView(texture);
        VulkanBuffer buffer = new VulkanBuffer(0x7000L, 0x8000L, VulkanicBuffer.USAGE_UNIFORM, 1024, "layout-runtime", () -> {});
        List<PipelineDescriptor.ResourceBinding> layout = List.of(
            binding("Sampler0", 0, 0, PipelineDescriptor.ResourceType.SAMPLER, null,
                Set.of(VulkanicShaderStage.FRAGMENT)),
            binding("Globals", 0, 1, PipelineDescriptor.ResourceType.UNIFORM_BUFFER, null,
                Set.of(VulkanicShaderStage.VERTEX)),
            binding("Image0", 0, 2, PipelineDescriptor.ResourceType.STORAGE_IMAGE, null,
                Set.of(VulkanicShaderStage.COMPUTE)),
            binding("CloudFaces", 0, 3, PipelineDescriptor.ResourceType.TEXEL_BUFFER, TextureFormat.RGBA8,
                Set.of(VulkanicShaderStage.FRAGMENT))
        );
        VulkanDescriptorSetLayoutPlanner.DescriptorLayoutPlan layoutPlan = planner.plan(layout);

        VulkanDescriptorBindingPlanner.DescriptorBindingPlan bindingPlan = new VulkanDescriptorBindingPlanner().plan(
            new VulkanDescriptorBindingPlanner.PlanRequest(
                layout,
                PipelineResourceBindings.builder()
                    .bindSampler("Sampler0", view, 0)
                    .bindUniformBuffer("Globals", new VulkanicBufferSlice(buffer, 0, 128))
                    .bindStorageImage("Image0", new PipelineResourceBindings.StorageImageBinding(
                        0,
                        texture.textureId(),
                        0,
                        false,
                        0,
                        0,
                        VulkanicAPI.GL_RGBA8
                    ))
                    .bindTexelBuffer("CloudFaces", 1)
                    .build(),
                new SingleTextureLookup(view, texture),
                unit -> unit == 1 ? new TextureBindingSnapshot(1, texture.textureId(), 0, null) : null,
                id -> id == texture.textureId()
                    ? new VulkanImageResourceViewCoordinator.TexelBufferViewPlan(texture.textureId(), VulkanicAPI.GL_RGBA8, 9, 0x9000L)
                    : null,
                NullSamplerStateLookup.INSTANCE,
                (textureId, level) -> VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                new VulkanDescriptorBindingPlanner.RenderStateSnapshot(false, Set.of()),
                256,
                false,
                false,
                "test:pipeline",
                0x1234L,
                VulkanDescriptorBindingPlanner.PlannerEvents.NOOP
            )
        );

        Map<Integer, Integer> layoutTypes = layoutPlan.primarySet().bindings().stream()
            .collect(Collectors.toMap(
                VulkanDescriptorSetLayoutPlanner.DescriptorLayoutBindingPlan::binding,
                VulkanDescriptorSetLayoutPlanner.DescriptorLayoutBindingPlan::descriptorType
            ));
        Map<Integer, Integer> runtimeTypes = bindingPlan.entries().stream()
            .collect(Collectors.toMap(
                VulkanDescriptorBindingPlanner.DescriptorPlanEntry::bindingIndex,
                VulkanDescriptorBindingPlanner.DescriptorPlanEntry::descriptorType
            ));
        assertEquals(layoutTypes, runtimeTypes);
    }

    @Test
    void conflictingSetBindingDeclarationsAreRejected() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> planner.plan(List.of(
            binding("A", 0, 0, PipelineDescriptor.ResourceType.SAMPLER, null,
                Set.of(VulkanicShaderStage.FRAGMENT)),
            binding("B", 0, 0, PipelineDescriptor.ResourceType.UNIFORM_BUFFER, null,
                Set.of(VulkanicShaderStage.VERTEX))
        )));
        assertTrue(exception.getMessage().contains("set 0 binding 0"));
    }

    private static Map<String, VulkanDescriptorSetLayoutPlanner.DescriptorLayoutBindingPlan> byName(
        VulkanDescriptorSetLayoutPlanner.DescriptorLayoutPlan plan
    ) {
        return plan.allBindings().stream()
            .collect(Collectors.toMap(
                VulkanDescriptorSetLayoutPlanner.DescriptorLayoutBindingPlan::name,
                Function.identity()
            ));
    }

    private static PipelineDescriptor.ResourceBinding binding(
        String name,
        int set,
        int binding,
        PipelineDescriptor.ResourceType type,
        TextureFormat textureFormat,
        Set<VulkanicShaderStage> stages
    ) {
        return new PipelineDescriptor.ResourceBinding(set, binding, name, type, textureFormat, stages);
    }

    private static VulkanImageResourceViewCoordinator.ImageStorageSnapshot textureSnapshot(int id) {
        return new VulkanImageResourceViewCoordinator.ImageStorageSnapshot(
            id,
            VulkanicAPI.GL_TEXTURE_2D,
            0x5000L + id,
            0x6000L + id,
            0x7000L + id,
            VK10.VK_FORMAT_R8G8B8A8_UNORM,
            VK10.VK_IMAGE_ASPECT_COLOR_BIT,
            VK10.VK_IMAGE_USAGE_STORAGE_BIT,
            false,
            4,
            1,
            64,
            32,
            1,
            VulkanicAPI.GL_RGBA,
            VulkanicAPI.GL_UNSIGNED_BYTE,
            Map.of(
                VulkanicAPI.GL_TEXTURE_MIN_FILTER,
                VulkanicAPI.GL_NEAREST,
                VulkanicAPI.GL_TEXTURE_MAG_FILTER,
                VulkanicAPI.GL_LINEAR
            ),
            Map.of(0, new TextureLevelInfo(64, 32, VulkanicAPI.GL_RGBA8))
        );
    }

    private static VulkanTextureView textureView(VulkanImageResourceViewCoordinator.ImageStorageSnapshot texture) {
        VulkanTexture vulkanTexture = new VulkanTexture(
            texture.imageHandle(),
            texture.memoryHandle(),
            texture.defaultViewHandle(),
            VulkanicTexture.USAGE_TEXTURE_BINDING,
            VulkanicTextureFormat.RGBA8,
            texture.width(),
            texture.height(),
            texture.depth(),
            texture.mipLevels(),
            "layout-planner-texture",
            () -> {}
        );
        return new VulkanTextureView(vulkanTexture, texture.defaultViewHandle(), 0, 1, texture.textureId(), () -> {});
    }

    private record SingleTextureLookup(
        VulkanTextureView view,
        VulkanImageResourceViewCoordinator.ImageStorageSnapshot texture
    ) implements VulkanDescriptorBindingPlanner.TextureSnapshotLookup {
        @Override
        public VulkanImageResourceViewCoordinator.ImageStorageSnapshot snapshotForView(VulkanTextureView view) {
            return this.view == view ? texture : null;
        }

        @Override
        public VulkanImageResourceViewCoordinator.ImageStorageSnapshot snapshotForTexture(int textureId) {
            return texture.textureId() == textureId ? texture : null;
        }

        @Override
        public VulkanImageResourceViewCoordinator.DescriptorImagePlan descriptorSampledImagePlan(
            VulkanTextureView view,
            VulkanImageResourceViewCoordinator.ImageStorageSnapshot storage,
            Set<Integer> storageImageTextureIds,
            VulkanDescriptorBindingPlanner.LayoutLookup layoutLookup,
            VulkanDescriptorBindingPlanner.RenderStateSnapshot renderState
        ) {
            return new VulkanImageResourceViewCoordinator.DescriptorImagePlan(
                view,
                storage,
                view.getVkImageViewHandle(),
                storage != null ? storage.defaultViewHandle() : view.getVkImageViewHandle(),
                Math.max(0, view.getBaseMipLevel()),
                Math.max(1, view.getMipLevelCount()),
                false,
                storage != null && view.getVkImageViewHandle() != storage.defaultViewHandle(),
                storage != null && storageImageTextureIds.contains(storage.textureId()),
                storage != null && storageImageTextureIds.contains(storage.textureId())
                    ? VulkanDescriptorBindingPlanner.DescriptorTransitionRequirement.STORAGE_IMAGE
                    : VulkanDescriptorBindingPlanner.DescriptorTransitionRequirement.SAMPLE,
                storage != null && storageImageTextureIds.contains(storage.textureId())
                    ? VK10.VK_IMAGE_LAYOUT_GENERAL
                    : VulkanImageUse.SAMPLED_COLOR.vkLayout(),
                null
            );
        }

        @Override
        public VulkanImageResourceViewCoordinator.DescriptorImagePlan descriptorStorageImagePlan(
            int textureId,
            int mipLevel
        ) {
            return new VulkanImageResourceViewCoordinator.DescriptorImagePlan(
                null,
                texture.textureId() == textureId ? texture : null,
                texture.defaultViewHandle(),
                texture.defaultViewHandle(),
                Math.max(0, mipLevel),
                1,
                false,
                false,
                true,
                VulkanDescriptorBindingPlanner.DescriptorTransitionRequirement.STORAGE_IMAGE,
                VK10.VK_IMAGE_LAYOUT_GENERAL,
                null
            );
        }
    }

    private enum NullSamplerStateLookup implements VulkanDescriptorBindingPlanner.SamplerStateLookup {
        INSTANCE;

        @Override
        public VirtualSamplerStateSnapshot samplerState(int sampler) {
            return null;
        }

        @Override
        public VirtualSamplerStateSnapshot samplerStateForTextureUnit(int unit) {
            return null;
        }
    }
}
