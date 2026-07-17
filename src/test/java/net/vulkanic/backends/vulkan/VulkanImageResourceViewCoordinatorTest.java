package net.vulkanic.backends.vulkan;

import net.vulkanic.VulkanicAPI;
import net.vulkanic.VulkanicRenderPassDescriptor;
import net.vulkanic.VulkanicResourceUsage;
import org.junit.jupiter.api.Test;
import org.lwjgl.vulkan.VK10;

import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class VulkanImageResourceViewCoordinatorTest {
    @Test
    void sampledColorTextureProducesImmutableDescriptorPlan() {
        Fixture fixture = new Fixture();
        LegacyTextureObject texture = fixture.createTexture(
            VulkanicAPI.GL_TEXTURE_2D,
            VK10.VK_IMAGE_ASPECT_COLOR_BIT,
            VK10.VK_IMAGE_USAGE_SAMPLED_BIT,
            2,
            0x1100L
        );
        VulkanTextureView requestedView = textureView(texture, 0x2200L, 0, 2);

        VulkanImageResourceViewCoordinator.ImageStorageSnapshot storage =
            fixture.coordinator.legacyStorageSnapshot(texture.id);
        VulkanImageResourceViewCoordinator.DescriptorImagePlan plan =
            fixture.coordinator.descriptorSampledImagePlan(
                requestedView,
                storage,
                Set.of(),
                (textureId, level) -> VK10.VK_IMAGE_LAYOUT_UNDEFINED,
                new VulkanDescriptorBindingPlanner.RenderStateSnapshot(false, Set.of())
            );

        texture.defaultViewHandle = 0x3300L;

        assertEquals(texture.id, storage.textureId());
        assertEquals(0x1100L, storage.defaultViewHandle());
        assertEquals(0x1100L, plan.descriptorImageViewHandle());
        assertEquals(VulkanDescriptorBindingPlanner.DescriptorTransitionRequirement.SAMPLE, plan.transitionRequirement());
        assertEquals(VulkanImageUse.SAMPLED_COLOR.vkLayout(), plan.imageLayout());
        assertTrue(plan.remappedToDefaultView());
        assertFalse(plan.storageImageCompatible());
    }

    @Test
    void storageImageAndSharedSamplerUseGeneralLayout() {
        Fixture fixture = new Fixture();
        LegacyTextureObject texture = fixture.createTexture(
            VulkanicAPI.GL_TEXTURE_2D,
            VK10.VK_IMAGE_ASPECT_COLOR_BIT,
            VK10.VK_IMAGE_USAGE_SAMPLED_BIT | VK10.VK_IMAGE_USAGE_STORAGE_BIT,
            3,
            0x1200L
        );
        fixture.tracker.recordLayout(texture.id, 0, VK10.VK_IMAGE_LAYOUT_GENERAL);
        VulkanImageResourceViewCoordinator.ImageStorageSnapshot storage =
            fixture.coordinator.legacyStorageSnapshot(texture.id);

        VulkanImageResourceViewCoordinator.DescriptorImagePlan samplerPlan =
            fixture.coordinator.descriptorSampledImagePlan(
                textureView(texture, 0x2201L, 0, 1),
                storage,
                Set.of(texture.id),
                (textureId, level) -> fixture.tracker.layoutFor(textureId, level, VK10.VK_IMAGE_LAYOUT_UNDEFINED),
                new VulkanDescriptorBindingPlanner.RenderStateSnapshot(false, Set.of())
            );
        VulkanImageResourceViewCoordinator.DescriptorImagePlan storagePlan =
            fixture.coordinator.descriptorStorageImagePlan(texture.id, 1);

        assertTrue(samplerPlan.storageImageCompatible());
        assertEquals(VulkanDescriptorBindingPlanner.DescriptorTransitionRequirement.STORAGE_IMAGE, samplerPlan.transitionRequirement());
        assertEquals(VK10.VK_IMAGE_LAYOUT_GENERAL, samplerPlan.imageLayout());
        assertEquals(storage, storagePlan.storage());
        assertEquals(0x1200L, storagePlan.descriptorImageViewHandle());
        assertEquals(1, storagePlan.baseMipLevel());
    }

    @Test
    void attachmentPlansCarryFramebufferVisibleSnapshotState() {
        Fixture fixture = new Fixture();
        LegacyTextureObject color = fixture.createTexture(
            VulkanicAPI.GL_TEXTURE_2D,
            VK10.VK_IMAGE_ASPECT_COLOR_BIT,
            VK10.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT,
            1,
            0x1300L
        );
        color.feedbackLoopCapable = true;
        VulkanImageResourceViewCoordinator.ImageStorageSnapshot storage =
            fixture.coordinator.legacyStorageSnapshot(color.id);

        VulkanImageResourceViewCoordinator.AttachmentViewPlan plan =
            fixture.coordinator.attachmentViewPlan(
                storage,
                storage.defaultViewHandle(),
                VulkanicResourceUsage.SAMPLED_READ,
                VulkanicResourceUsage.ATTACHMENT_FEEDBACK_LOOP,
                VulkanicResourceUsage.SAMPLED_READ,
                new VulkanImageResourceViewCoordinator.VulkanicRenderPassDescriptorParts(
                    VulkanicRenderPassDescriptor.LoadOp.CLEAR,
                    VulkanicRenderPassDescriptor.StoreOp.STORE,
                    OptionalInt.of(0xff336699),
                    OptionalDouble.empty()
                )
            );

        assertEquals(storage, plan.storage());
        assertEquals(color.width, plan.width());
        assertEquals(color.height, plan.height());
        assertTrue(plan.feedbackLoopCapable());
        assertEquals(VulkanicResourceUsage.ATTACHMENT_FEEDBACK_LOOP, plan.passUsage());
        assertEquals(OptionalInt.of(0xff336699), plan.descriptorParts().clearColor());
    }

    @Test
    void depthStencilSampledViewRequestsDepthOnlyMaterialization() {
        Fixture fixture = new Fixture();
        LegacyTextureObject depthStencil = fixture.createTexture(
            VulkanicAPI.GL_TEXTURE_2D,
            VK10.VK_IMAGE_ASPECT_DEPTH_BIT | VK10.VK_IMAGE_ASPECT_STENCIL_BIT,
            VK10.VK_IMAGE_USAGE_SAMPLED_BIT | VK10.VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT,
            2,
            0x1400L
        );
        depthStencil.vkFormat = VK10.VK_FORMAT_D24_UNORM_S8_UINT;
        VulkanImageResourceViewCoordinator.ImageStorageSnapshot storage =
            fixture.coordinator.legacyStorageSnapshot(depthStencil.id);

        VulkanImageResourceViewCoordinator.DescriptorImagePlan plan =
            fixture.coordinator.descriptorSampledImagePlan(
                textureView(depthStencil, 0x2400L, 1, 1),
                storage,
                Set.of(),
                (textureId, level) -> VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL,
                new VulkanDescriptorBindingPlanner.RenderStateSnapshot(false, Set.of())
            );

        assertTrue(plan.requiresDepthOnlyView());
        assertNotNull(plan.materializationRequest());
        assertEquals(VK10.VK_IMAGE_ASPECT_DEPTH_BIT, plan.materializationRequest().aspectMask());
        assertEquals(1, plan.materializationRequest().baseMipLevel());
        assertEquals(VulkanImageUse.SAMPLED_DEPTH.vkLayout(), plan.imageLayout());
    }

    @Test
    void transferTargetPlanUsesMipDimensionsAndTrackedLayout() {
        Fixture fixture = new Fixture();
        LegacyTextureObject texture = fixture.createTexture(
            VulkanicAPI.GL_TEXTURE_3D,
            VK10.VK_IMAGE_ASPECT_COLOR_BIT,
            VK10.VK_IMAGE_USAGE_TRANSFER_SRC_BIT | VK10.VK_IMAGE_USAGE_TRANSFER_DST_BIT,
            3,
            0x1500L
        );
        texture.depth = 4;
        texture.levels.put(1, new TextureLevelInfo(32, 16, 2, VulkanicAPI.GL_RGBA8));
        fixture.tracker.recordLayout(texture.id, 1, VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL);

        VulkanImageResourceViewCoordinator.TransferTargetPlan plan =
            fixture.coordinator.transferTargetPlan(
                texture.id,
                1,
                VulkanImageResourceViewCoordinator.ImageTransferUsage.READBACK
            );

        assertEquals(VulkanImageResourceViewCoordinator.ViewResolutionStatus.EXISTING_COMPATIBLE_VIEW, plan.status());
        assertEquals(32, plan.width());
        assertEquals(16, plan.height());
        assertEquals(2, plan.depth());
        assertEquals(VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, plan.trackedLayout());
        assertEquals(VulkanImageResourceViewCoordinator.ImageTransferUsage.READBACK, plan.usage());
    }

    @Test
    void texelBufferAndManagedSnapshotsAreResolvedWithoutMutableRecords() {
        Fixture fixture = new Fixture();
        LegacyTextureObject texture = fixture.createTexture(
            VulkanicAPI.GL_TEXTURE_2D,
            VK10.VK_IMAGE_ASPECT_COLOR_BIT,
            VK10.VK_IMAGE_USAGE_SAMPLED_BIT,
            1,
            0x1600L
        );
        fixture.textures.setLegacyTexelBufferBinding(
            texture.id,
            new LegacyTexelBufferBinding(VulkanicAPI.GL_RGBA8, 77, 0x7700L)
        );
        fixture.textures.registerManagedTexture(0x9000L, 0x9100L, 0x9200L);

        VulkanImageResourceViewCoordinator.TexelBufferViewPlan texelPlan =
            fixture.coordinator.texelBufferViewPlan(texture.id);
        ManagedImageSnapshot managed = fixture.coordinator.managedImageSnapshot(0x9000L);

        assertEquals(texture.id, texelPlan.textureId());
        assertEquals(VulkanicAPI.GL_RGBA8, texelPlan.internalFormat());
        assertEquals(77, texelPlan.legacyBufferId());
        assertEquals(0x7700L, texelPlan.bufferViewHandle());
        assertEquals(0x9200L, managed.defaultViewHandle());
    }

    @Test
    void incompatibleMissingAndLazyCreationFailuresAreExplicit() {
        Fixture fixture = new Fixture();
        LegacyTextureObject texture = fixture.createTexture(
            VulkanicAPI.GL_TEXTURE_2D,
            VK10.VK_IMAGE_ASPECT_COLOR_BIT,
            VK10.VK_IMAGE_USAGE_STORAGE_BIT,
            1,
            0x1700L
        );
        texture.defaultViewHandle = VK10.VK_NULL_HANDLE;

        VulkanImageResourceViewCoordinator.DescriptorImagePlan missingStorage =
            fixture.coordinator.descriptorStorageImagePlan(texture.id, 0);
        VulkanImageResourceViewCoordinator.TransferTargetPlan missingTransfer =
            fixture.coordinator.transferTargetPlan(9999, 0, VulkanImageResourceViewCoordinator.ImageTransferUsage.UPLOAD);

        assertNull(missingStorage.storage());
        assertEquals(VK10.VK_NULL_HANDLE, missingStorage.descriptorImageViewHandle());
        assertEquals(VulkanImageResourceViewCoordinator.ViewResolutionStatus.UNAVAILABLE, missingTransfer.status());
        assertNull(missingTransfer.storage());
    }

    @Test
    void invalidationReplacementDeletionAndShutdownDoNotExposeStaleLayoutOrHandles() {
        Fixture fixture = new Fixture();
        LegacyTextureObject texture = fixture.createTexture(
            VulkanicAPI.GL_TEXTURE_2D,
            VK10.VK_IMAGE_ASPECT_COLOR_BIT,
            VK10.VK_IMAGE_USAGE_SAMPLED_BIT,
            1,
            0x1800L
        );
        fixture.textures.setLegacyFallbackSamplerTextureId(texture.id);
        fixture.tracker.recordLayout(texture.id, 0, VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
        VulkanImageResourceViewCoordinator.ImageStorageSnapshot before =
            fixture.coordinator.legacyStorageSnapshot(texture.id);

        VulkanImageResourceViewCoordinator.InvalidationResult invalidation =
            fixture.coordinator.invalidateTexture(texture.id);
        texture.defaultViewHandle = 0x2800L;
        VulkanImageResourceViewCoordinator.ImageStorageSnapshot after =
            fixture.coordinator.legacyStorageSnapshot(texture.id);
        LegacyTextureObject removed = fixture.textures.removeLegacyTexture(texture.id);
        LegacyTextureObject recreated = fixture.textures.getOrCreateLegacyTexture(texture.id, VulkanicAPI.GL_TEXTURE_2D);
        fillTexture(recreated, VK10.VK_IMAGE_ASPECT_COLOR_BIT, VK10.VK_IMAGE_USAGE_SAMPLED_BIT, 1, 0x3800L);

        assertTrue(invalidation.layoutStateCleared());
        assertEquals(0, fixture.textures.legacyFallbackSamplerTextureId());
        assertEquals(VK10.VK_IMAGE_LAYOUT_UNDEFINED, fixture.tracker.layoutFor(texture.id, 0, VK10.VK_IMAGE_LAYOUT_UNDEFINED));
        assertEquals(0x1800L, before.defaultViewHandle());
        assertEquals(0x2800L, after.defaultViewHandle());
        assertSame(texture, removed);
        assertNotEquals(removed.defaultViewHandle, fixture.coordinator.legacyStorageSnapshot(texture.id).defaultViewHandle());

        fixture.textures.clearAll();
        fixture.tracker.reset();

        assertNull(fixture.coordinator.legacyStorageSnapshot(texture.id));
        assertEquals(VK10.VK_IMAGE_LAYOUT_UNDEFINED, fixture.tracker.layoutFor(texture.id, 0, VK10.VK_IMAGE_LAYOUT_UNDEFINED));
    }

    private static VulkanTextureView textureView(
        LegacyTextureObject texture,
        long imageViewHandle,
        int baseMip,
        int mipCount
    ) {
        VulkanTexture vulkanTexture = new VulkanTexture(
            texture.imageHandle,
            texture.memoryHandle,
            texture.defaultViewHandle,
            0,
            (texture.aspectMask & VK10.VK_IMAGE_ASPECT_DEPTH_BIT) != 0
                ? net.vulkanic.VulkanicTextureFormat.DEPTH32
                : net.vulkanic.VulkanicTextureFormat.RGBA8,
            texture.width,
            texture.height,
            texture.depth,
            texture.mipLevels,
            "coordinator-test-texture-" + texture.id,
            () -> {}
        );
        return new VulkanTextureView(vulkanTexture, imageViewHandle, baseMip, mipCount, texture.id, () -> {});
    }

    private static void fillTexture(
        LegacyTextureObject texture,
        int aspectMask,
        int usageFlags,
        int mipLevels,
        long defaultViewHandle
    ) {
        texture.imageHandle = 0x5000L + texture.id;
        texture.memoryHandle = 0x6000L + texture.id;
        texture.defaultViewHandle = defaultViewHandle;
        texture.vkFormat = (aspectMask & VK10.VK_IMAGE_ASPECT_DEPTH_BIT) != 0
            ? VK10.VK_FORMAT_D32_SFLOAT
            : VK10.VK_FORMAT_R8G8B8A8_UNORM;
        texture.aspectMask = aspectMask;
        texture.imageUsageFlags = usageFlags;
        texture.pixelBytes = 4;
        texture.mipLevels = mipLevels;
        texture.width = 64;
        texture.height = 32;
        texture.depth = 1;
        texture.sourceFormat = VulkanicAPI.GL_RGBA;
        texture.sourceType = VulkanicAPI.GL_UNSIGNED_BYTE;
        texture.integerParameters.putAll(Map.of(
            VulkanicAPI.GL_TEXTURE_MIN_FILTER,
            VulkanicAPI.GL_NEAREST,
            VulkanicAPI.GL_TEXTURE_MAG_FILTER,
            VulkanicAPI.GL_LINEAR,
            VulkanicAPI.GL_TEXTURE_WRAP_S,
            VulkanicAPI.GL_REPEAT,
            VulkanicAPI.GL_TEXTURE_WRAP_T,
            VulkanicAPI.GL_REPEAT
        ));
        texture.levels.put(0, new TextureLevelInfo(64, 32, VulkanicAPI.GL_RGBA8));
        if (mipLevels > 1) {
            texture.levels.put(1, new TextureLevelInfo(32, 16, VulkanicAPI.GL_RGBA8));
        }
    }

    private static final class Fixture {
        private final VulkanTextureResourceManager textures = new VulkanTextureResourceManager();
        private final VulkanImageStateTracker tracker = new VulkanImageStateTracker();
        private final VulkanImageResourceViewCoordinator coordinator =
            new VulkanImageResourceViewCoordinator(textures, tracker, new VulkanStagingTransferManager(2));

        private LegacyTextureObject createTexture(
            int target,
            int aspectMask,
            int usageFlags,
            int mipLevels,
            long defaultViewHandle
        ) {
            LegacyTextureObject texture = textures.getLegacyTexture(textures.createLegacyTexture(target));
            fillTexture(texture, aspectMask, usageFlags, mipLevels, defaultViewHandle);
            tracker.registerTexture(
                texture.id,
                texture.imageHandle,
                texture.aspectMask,
                texture.mipLevels,
                VulkanImageResourceViewCoordinator.layerCount(coordinator.legacyStorageSnapshot(texture.id)),
                texture.feedbackLoopCapable,
                VK10.VK_IMAGE_LAYOUT_UNDEFINED
            );
            return texture;
        }
    }
}
