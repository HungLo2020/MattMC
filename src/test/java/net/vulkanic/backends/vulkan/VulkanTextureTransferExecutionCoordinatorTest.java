package net.vulkanic.backends.vulkan;

import net.vulkanic.VulkanicAPI;
import net.vulkanic.VulkanicPassResourceModel;
import org.junit.jupiter.api.Test;
import org.lwjgl.vulkan.VK10;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class VulkanTextureTransferExecutionCoordinatorTest {
    @Test
    void pixelStorePlanningAndMaterializationPreserveOpenGlUnpackSemantics() {
        Fixture fixture = new Fixture();
        VulkanTextureTransferExecutionCoordinator.TransferFormat rgbToRgba =
            new VulkanTextureTransferExecutionCoordinator.TransferFormat(
                VK10.VK_FORMAT_R8G8B8A8_UNORM,
                4,
                VK10.VK_IMAGE_ASPECT_COLOR_BIT,
                3,
                true
            );
        VulkanTextureTransferExecutionCoordinator.PixelStoreSnapshot pixelStore =
            new VulkanTextureTransferExecutionCoordinator.PixelStoreSnapshot(4, 1, 1, 4);
        ByteBuffer source = ByteBuffer.allocateDirect(40);
        for (int index = 0; index < 40; index++) {
            source.put((byte) index);
        }
        source.flip();

        VulkanTextureTransferExecutionCoordinator.PixelUnpackPlan plan =
            fixture.coordinator.planPixelUnpack2D(rgbToRgba, 2, 2, pixelStore, source.remaining());
        ByteBuffer packed = fixture.coordinator.materializePixelUnpack(plan, source);

        assertEquals(4, plan.rowLength());
        assertEquals(12, plan.rowBytes());
        assertEquals(12, plan.stride());
        assertEquals(15, plan.startOffset());
        assertEquals(33, plan.requiredSourceBytes());
        assertFalse(plan.directSlice());
        assertEquals(16, packed.remaining());
        assertEquals(15, packed.get(0) & 0xFF);
        assertEquals(16, packed.get(1) & 0xFF);
        assertEquals(17, packed.get(2) & 0xFF);
        assertEquals(255, packed.get(3) & 0xFF);
        assertEquals(18, packed.get(4) & 0xFF);
        assertEquals(27, packed.get(8) & 0xFF);
        assertEquals(30, packed.get(12) & 0xFF);
        assertEquals(255, packed.get(15) & 0xFF);
    }

    @Test
    void directPixelUnpackSliceUsesRequiredBytesFromOriginalBufferPosition() {
        Fixture fixture = new Fixture();
        VulkanTextureTransferExecutionCoordinator.PixelStoreSnapshot pixelStore =
            new VulkanTextureTransferExecutionCoordinator.PixelStoreSnapshot(0, 1, 0, 4);
        ByteBuffer source = ByteBuffer.allocateDirect(48);
        for (int index = 0; index < 48; index++) {
            source.put((byte) index);
        }
        source.flip();

        VulkanTextureTransferExecutionCoordinator.PixelUnpackPlan plan =
            fixture.coordinator.planPixelUnpack2D(rgbaFormat(), 4, 2, pixelStore, source.remaining());
        ByteBuffer direct = fixture.coordinator.materializePixelUnpack(plan, source);

        assertTrue(plan.directSlice());
        assertEquals(16, plan.startOffset());
        assertEquals(48, plan.requiredSourceBytes());
        assertEquals(32, direct.remaining());
        assertEquals(16, direct.get(0) & 0xFF);
        assertEquals(47, direct.get(31) & 0xFF);
        assertEquals(0, source.position());
        assertEquals(48, source.limit());
    }

    @Test
    void storageDefinitionPreservesMipMetadataAndClampsConfiguredMipCount() {
        Fixture fixture = new Fixture();
        LegacyTextureObject texture = fixture.createTexture(VulkanicAPI.GL_TEXTURE_2D, 64, 32, 1, 2, VK10.VK_FORMAT_R8G8B8A8_UNORM);
        texture.levels.put(1, new TextureLevelInfo(32, 16, VulkanicAPI.GL_RGBA8));

        VulkanTextureTransferExecutionCoordinator.StorageDefinitionPlan growPlan =
            fixture.coordinator.planStorageDefinition(new VulkanTextureTransferExecutionCoordinator.StorageDefinitionRequest(
                VulkanicAPI.GL_TEXTURE_2D,
                0,
                64,
                32,
                1,
                7,
                rgbaFormat(),
                false,
                false,
                fixture.imageViews.legacyStorageSnapshot(texture.id)
            ));

        assertTrue(growPlan.needsRecreate());
        assertTrue(growPlan.preserveExistingLevels());
        assertEquals(7, growPlan.requiredMipLevels());
        assertEquals(32, growPlan.preservedLevels().get(1).width);

        VulkanTextureTransferExecutionCoordinator.StorageDefinitionPlan levelPlan =
            fixture.coordinator.planStorageDefinition(new VulkanTextureTransferExecutionCoordinator.StorageDefinitionRequest(
                VulkanicAPI.GL_TEXTURE_2D,
                2,
                16,
                8,
                1,
                2,
                rgbaFormat(),
                false,
                false,
                null
            ));

        assertEquals(64, levelPlan.inferredBaseWidth());
        assertEquals(32, levelPlan.inferredBaseHeight());
        assertEquals(3, levelPlan.requiredMipLevels());
    }

    @Test
    void uploadCopyBlitReadbackAndMipmapPlansUseSnapshotsAndTrackedLayouts() {
        Fixture fixture = new Fixture();
        LegacyTextureObject color = fixture.createTexture(VulkanicAPI.GL_TEXTURE_2D, 64, 32, 1, 3, VK10.VK_FORMAT_R8G8B8A8_UNORM);
        LegacyTextureObject destination = fixture.createTexture(VulkanicAPI.GL_TEXTURE_2D, 64, 32, 1, 3, VK10.VK_FORMAT_R8G8B8A8_UNORM);
        fixture.tracker.recordLayout(color.id, 0, VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
        fixture.tracker.recordLayout(destination.id, 0, VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);

        VulkanTextureTransferExecutionCoordinator.TransferOperationPlan upload =
            fixture.coordinator.planUpload(color.id, 0, 0, 1);
        VulkanTextureTransferExecutionCoordinator.TransferOperationPlan readback =
            fixture.coordinator.planReadback(color.id, 0, 0, 1);
        VulkanTextureTransferExecutionCoordinator.CopyOperationPlan copy =
            fixture.coordinator.planCopy(color.id, 0, destination.id, 0, 1);
        VulkanTextureTransferExecutionCoordinator.BlitOperationPlan blit =
            fixture.coordinator.planBlit(
                color.id,
                0,
                destination.id,
                0,
                1,
                0x00004000,
                "testBlit"
            );
        VulkanTextureTransferExecutionCoordinator.MipmapPlan mipmap =
            fixture.coordinator.planMipmap(color.id, "testMipmap");

        assertEquals(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, upload.originalLayout());
        assertEquals(VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, upload.transferLayout());
        assertEquals(VulkanImageUse.SAMPLED_COLOR.vkLayout(), upload.finalLayout());
        assertEquals(VulkanicPassResourceModel.PassKind.TRANSFER, upload.resourcePlan().request().kind());
        assertEquals(VulkanicPassResourceModel.ResourceKind.TRANSFER_DESTINATION, upload.resourcePlan().orderedUses().get(0).kind());
        assertEquals("texture:" + color.id, upload.resourcePlan().orderedUses().get(0).resource().stableKey());
        assertEquals(VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, readback.transferLayout());
        assertEquals(VulkanicPassResourceModel.PassKind.READBACK, readback.resourcePlan().request().kind());
        assertEquals(VulkanicPassResourceModel.ResourceKind.READBACK_SOURCE, readback.resourcePlan().orderedUses().get(0).kind());
        assertEquals(color.id, copy.source().storage().textureId());
        assertEquals(destination.id, copy.destination().storage().textureId());
        assertEquals(VK10.VK_IMAGE_ASPECT_COLOR_BIT, blit.operationAspectMask());
        assertEquals(3, mipmap.storage().mipLevels());
        assertEquals(VulkanImageUse.SAMPLED_COLOR.vkLayout(), mipmap.finalLayout());
    }

    @Test
    void depthStencilBlitPlanningUsesCombinedDepthStencilTransitionWhenNeeded() {
        Fixture fixture = new Fixture();
        LegacyTextureObject source = fixture.createTexture(
            VulkanicAPI.GL_TEXTURE_2D,
            32,
            32,
            1,
            1,
            VK10.VK_FORMAT_D24_UNORM_S8_UINT,
            VK10.VK_IMAGE_ASPECT_DEPTH_BIT | VK10.VK_IMAGE_ASPECT_STENCIL_BIT,
            4
        );
        LegacyTextureObject destination = fixture.createTexture(
            VulkanicAPI.GL_TEXTURE_2D,
            32,
            32,
            1,
            1,
            VK10.VK_FORMAT_D24_UNORM_S8_UINT,
            VK10.VK_IMAGE_ASPECT_DEPTH_BIT | VK10.VK_IMAGE_ASPECT_STENCIL_BIT,
            4
        );

        VulkanTextureTransferExecutionCoordinator.BlitOperationPlan plan =
            fixture.coordinator.planBlit(
                source.id,
                0,
                destination.id,
                0,
                1,
                0x00000100,
                "depthBlit"
            );

        assertEquals(VK10.VK_IMAGE_ASPECT_DEPTH_BIT, plan.operationAspectMask());
        assertEquals(
            VK10.VK_IMAGE_ASPECT_DEPTH_BIT | VK10.VK_IMAGE_ASPECT_STENCIL_BIT,
            plan.sourceTransitionAspectMask()
        );
        assertEquals(
            VK10.VK_IMAGE_ASPECT_DEPTH_BIT | VK10.VK_IMAGE_ASPECT_STENCIL_BIT,
            plan.destinationTransitionAspectMask()
        );
    }

    @Test
    void coordinatorPublishesLayoutViewInvalidationAndStorageLifecycle() {
        Fixture fixture = new Fixture();
        LegacyTextureObject texture = fixture.createTexture(VulkanicAPI.GL_TEXTURE_2D, 16, 16, 1, 1, VK10.VK_FORMAT_R8G8B8A8_UNORM);
        fixture.textures.setLegacyFallbackSamplerTextureId(texture.id);
        VulkanTextureTransferExecutionCoordinator.TransferOperationPlan upload =
            fixture.coordinator.planUpload(texture.id, 0, 0, 1);

        fixture.coordinator.publishLayout(upload, upload.finalLayout());
        assertEquals(VulkanImageUse.SAMPLED_COLOR.vkLayout(), fixture.tracker.layoutFor(texture.id, 0, VK10.VK_IMAGE_LAYOUT_UNDEFINED));

        fixture.coordinator.publishTextureInvalidated(texture.id);
        assertEquals(0, fixture.textures.legacyFallbackSamplerTextureId());
        assertEquals(VK10.VK_IMAGE_LAYOUT_UNDEFINED, fixture.tracker.layoutFor(texture.id, 0, VK10.VK_IMAGE_LAYOUT_UNDEFINED));

        fixture.coordinator.publishStorageDestroyed(texture.id);
        assertEquals(VK10.VK_IMAGE_LAYOUT_UNDEFINED, fixture.tracker.layoutFor(texture.id, 0, VK10.VK_IMAGE_LAYOUT_UNDEFINED));

        fixture.coordinator.publishStorageCreated(texture.id, 0x7777L, VK10.VK_IMAGE_ASPECT_COLOR_BIT, 2, 1, false);
        assertEquals(VK10.VK_IMAGE_LAYOUT_UNDEFINED, fixture.tracker.layoutFor(texture.id, 1, VK10.VK_IMAGE_LAYOUT_UNDEFINED));
    }

    @Test
    void stagingAndReadbackLifecycleRemainBoundedAndSingleOwner() {
        Fixture fixture = new Fixture();
        List<Long> destroyed = new ArrayList<>();
        VulkanStagingTransferManager.StagingBufferRecord staging =
            fixture.coordinator.recordUploadStaging(0x100L, 0x200L, 64L);

        fixture.coordinator.markUploadStagingMapped(staging);
        fixture.coordinator.markUploadStagingUnmapped(staging);
        fixture.coordinator.associateStagingCommand(staging, 0x300L);
        fixture.coordinator.retireStagingAfterTransfer(staging, 1);
        assertEquals(1, fixture.staging.pendingRetirementCountForTests(1));

        fixture.staging.retireImmediateSlot(1, record -> destroyed.add(record.bufferHandle()));
        assertEquals(List.of(0x100L), destroyed);

        VulkanStagingTransferManager.StagingBufferRecord failed =
            fixture.staging.recordUploadAllocation(0x101L, 0x201L, 8L);
        fixture.coordinator.cleanupFailedStagingTransfer(failed, false, 0, true, record -> destroyed.add(record.bufferHandle()));
        assertEquals(List.of(0x100L, 0x101L), destroyed);

        VulkanStagingTransferManager.ReadbackTransferRecord readback =
            fixture.coordinator.recordReadbackStaging(12, 16L);
        fixture.coordinator.associateReadbackCommand(readback, 0x400L);
        try (VulkanStagingTransferManager.ReadbackResult result =
                 fixture.coordinator.mapReadbackResult(readback, ByteBuffer.allocateDirect(16), () -> {})) {
            assertEquals(16, result.data().remaining());
        }
        fixture.coordinator.discardReadback(readback);
        assertEquals(0, fixture.staging.liveReadbackCountForTests());
    }

    @Test
    void incompatibleTransfersFailBeforeNativeCommandsAreMaterialized() {
        Fixture fixture = new Fixture();
        LegacyTextureObject color = fixture.createTexture(VulkanicAPI.GL_TEXTURE_2D, 16, 16, 1, 1, VK10.VK_FORMAT_R8G8B8A8_UNORM);
        LegacyTextureObject depth = fixture.createTexture(
            VulkanicAPI.GL_TEXTURE_2D,
            16,
            16,
            1,
            1,
            VK10.VK_FORMAT_D32_SFLOAT,
            VK10.VK_IMAGE_ASPECT_DEPTH_BIT,
            4
        );

        assertThrows(IllegalArgumentException.class, () -> fixture.coordinator.planCopy(color.id, 0, depth.id, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new VulkanTextureTransferExecutionCoordinator.PixelStoreSnapshot(0, 0, 0, 3));
        assertThrows(IllegalStateException.class, () -> fixture.coordinator.planUpload(9999, 0, 0, 1));
    }

    private static VulkanTextureTransferExecutionCoordinator.TransferFormat rgbaFormat() {
        return new VulkanTextureTransferExecutionCoordinator.TransferFormat(
            VK10.VK_FORMAT_R8G8B8A8_UNORM,
            4,
            VK10.VK_IMAGE_ASPECT_COLOR_BIT,
            4,
            false
        );
    }

    private static final class Fixture {
        final VulkanTextureResourceManager textures = new VulkanTextureResourceManager();
        final VulkanImageStateTracker tracker = new VulkanImageStateTracker();
        final VulkanStagingTransferManager staging = new VulkanStagingTransferManager(3);
        final VulkanCommandSubmissionStateManager commands = new VulkanCommandSubmissionStateManager(3, 2);
        final VulkanSwapchainStateManager swapchain = new VulkanSwapchainStateManager(2);
        final VulkanDeferredResourceLifetime<String> lifetime = new VulkanDeferredResourceLifetime<>(2, 3);
        final VulkanFrameExecutionCoordinator<String> frameExecution =
            new VulkanFrameExecutionCoordinator<>(swapchain, commands, lifetime, staging);
        final VulkanImageResourceViewCoordinator imageViews =
            new VulkanImageResourceViewCoordinator(textures, tracker, staging);
        final VulkanTextureTransferExecutionCoordinator coordinator =
            new VulkanTextureTransferExecutionCoordinator(textures, imageViews, tracker, staging, frameExecution, lifetime);

        LegacyTextureObject createTexture(int target, int width, int height, int depth, int mipLevels, int vkFormat) {
            int aspectMask = vkFormat == VK10.VK_FORMAT_D32_SFLOAT
                ? VK10.VK_IMAGE_ASPECT_DEPTH_BIT
                : VK10.VK_IMAGE_ASPECT_COLOR_BIT;
            return createTexture(target, width, height, depth, mipLevels, vkFormat, aspectMask, 4);
        }

        LegacyTextureObject createTexture(
            int target,
            int width,
            int height,
            int depth,
            int mipLevels,
            int vkFormat,
            int aspectMask,
            int pixelBytes
        ) {
            LegacyTextureObject texture = textures.getLegacyTexture(textures.createLegacyTexture(target));
            texture.imageHandle = 0x5000L + texture.id;
            texture.memoryHandle = 0x6000L + texture.id;
            texture.defaultViewHandle = 0x7000L + texture.id;
            texture.vkFormat = vkFormat;
            texture.aspectMask = aspectMask;
            texture.imageUsageFlags = VK10.VK_IMAGE_USAGE_TRANSFER_SRC_BIT
                | VK10.VK_IMAGE_USAGE_TRANSFER_DST_BIT
                | VK10.VK_IMAGE_USAGE_SAMPLED_BIT;
            texture.pixelBytes = pixelBytes;
            texture.width = width;
            texture.height = height;
            texture.depth = depth;
            texture.mipLevels = mipLevels;
            texture.levels.put(0, new TextureLevelInfo(width, height, depth, VulkanicAPI.GL_RGBA8));
            for (int level = 1; level < mipLevels; level++) {
                texture.levels.put(level, new TextureLevelInfo(
                    Math.max(1, width >> level),
                    Math.max(1, height >> level),
                    Math.max(1, depth >> level),
                    VulkanicAPI.GL_RGBA8
                ));
            }
            tracker.registerTexture(texture.id, texture.imageHandle, aspectMask, mipLevels, depth, false, VK10.VK_IMAGE_LAYOUT_UNDEFINED);
            return texture;
        }
    }
}
