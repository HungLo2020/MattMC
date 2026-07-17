package net.vulkanic.backends.vulkan;

import net.vulkanic.VulkanicAPI;
import net.vulkanic.VulkanicRenderPassDescriptor;
import net.vulkanic.VulkanicResourceUsage;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.vulkan.VK10;

import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Coordinates immutable image-storage and view-resolution decisions.
 *
 * <p>Ownership is deliberately split:</p>
 * <ul>
 *     <li>{@link VulkanTextureResourceManager} owns mutable legacy/managed texture records.</li>
 *     <li>{@link VulkanImageStateTracker} owns layout state.</li>
 *     <li>{@link VulkanStagingTransferManager} owns staging lifetime, not image storage.</li>
 *     <li>{@link VulkanRenderPassExecutionCoordinator} owns active-pass lifecycle.</li>
 *     <li>{@link VulkanDescriptorBindingPlanner} consumes immutable descriptor image plans.</li>
 *     <li>{@code NativeSpine} owns Vulkan image/view allocation, barriers, copies, and native destruction.</li>
 * </ul>
 */
final class VulkanImageResourceViewCoordinator {
    private final VulkanTextureResourceManager textures;
    private final VulkanImageStateTracker imageStateTracker;
    @SuppressWarnings("unused")
    private final VulkanStagingTransferManager stagingTransfers;

    VulkanImageResourceViewCoordinator(
        VulkanTextureResourceManager textures,
        VulkanImageStateTracker imageStateTracker,
        VulkanStagingTransferManager stagingTransfers
    ) {
        this.textures = Objects.requireNonNull(textures, "textures");
        this.imageStateTracker = Objects.requireNonNull(imageStateTracker, "imageStateTracker");
        this.stagingTransfers = Objects.requireNonNull(stagingTransfers, "stagingTransfers");
    }

    @Nullable
    ImageStorageSnapshot legacyStorageSnapshot(int textureId) {
        LegacyTextureStorageSnapshot snapshot = textures.legacyTextureSnapshot(textureId);
        return snapshot == null ? null : ImageStorageSnapshot.from(snapshot);
    }

    @Nullable
    ImageStorageSnapshot storageSnapshotForView(VulkanTextureView view) {
        if (view == null || view.getLegacyTextureHandle() <= 0) {
            return null;
        }
        return legacyStorageSnapshot(view.getLegacyTextureHandle());
    }

    @Nullable
    ManagedImageSnapshot managedImageSnapshot(long imageHandle) {
        return textures.managedImageSnapshot(imageHandle);
    }

    @Nullable
    TextureBindingSnapshot textureBindingSnapshot(int unit) {
        return textures.textureBindingSnapshot(unit);
    }

    @Nullable
    TexelBufferViewPlan texelBufferViewPlan(int textureId) {
        LegacyTexelBufferBinding binding = textures.legacyTexelBufferBinding(textureId);
        if (binding == null || binding.vkBufferViewHandle == VK10.VK_NULL_HANDLE) {
            return null;
        }
        return new TexelBufferViewPlan(
            textureId,
            binding.internalFormat,
            binding.legacyBufferId,
            binding.vkBufferViewHandle
        );
    }

    AttachmentViewPlan attachmentViewPlan(
        ImageStorageSnapshot storage,
        long imageViewHandle,
        VulkanicResourceUsage initialUsage,
        VulkanicResourceUsage passUsage,
        VulkanicResourceUsage finalUsage,
        VulkanicRenderPassDescriptorParts descriptorParts
    ) {
        Objects.requireNonNull(storage, "storage");
        if (storage.imageHandle() == VK10.VK_NULL_HANDLE || imageViewHandle == VK10.VK_NULL_HANDLE) {
            throw new IllegalStateException("Attachment texId=" + storage.textureId() + " is missing Vulkan image/view state");
        }
        return new AttachmentViewPlan(
            storage,
            imageViewHandle,
            storage.vkFormat(),
            storage.aspectMask(),
            storage.width(),
            storage.height(),
            storage.feedbackLoopCapable(),
            initialUsage,
            passUsage,
            finalUsage,
            descriptorParts
        );
    }

    DescriptorImagePlan descriptorSampledImagePlan(
        VulkanTextureView requestedView,
        ImageStorageSnapshot storage,
        Set<Integer> storageImageTextureIds,
        VulkanDescriptorBindingPlanner.LayoutLookup layoutLookup,
        VulkanDescriptorBindingPlanner.RenderStateSnapshot renderState
    ) {
        Objects.requireNonNull(requestedView, "requestedView");
        Objects.requireNonNull(storageImageTextureIds, "storageImageTextureIds");
        long requestedImageViewHandle = requestedView.getVkImageViewHandle();
        long descriptorImageViewHandle = requestedImageViewHandle;
        int descriptorBaseMipLevel = Math.max(0, requestedView.getBaseMipLevel());
        int descriptorMipLevelCount = Math.max(1, requestedView.getMipLevelCount());
        boolean remappedToDefaultView = false;

        if (storage != null
            && storage.defaultViewHandle() != VK10.VK_NULL_HANDLE
            && requestedView.getBaseMipLevel() == 0
            && requestedView.getMipLevelCount() >= storage.mipLevels()) {
            descriptorImageViewHandle = storage.defaultViewHandle();
            descriptorBaseMipLevel = 0;
            descriptorMipLevelCount = Math.max(1, storage.mipLevels());
            remappedToDefaultView = requestedImageViewHandle != descriptorImageViewHandle;
        }

        boolean requiresDepthOnlyView = storage != null && storage.hasDepthAspect() && storage.hasStencilAspect();
        boolean explicitlyStorageImageBound = storage != null && storageImageTextureIds.contains(storage.textureId());
        boolean storageImageCompatible = explicitlyStorageImageBound
            || isStorageImageLayoutCompatibleSampler(storage, descriptorBaseMipLevel, descriptorMipLevelCount, layoutLookup);
        VulkanDescriptorBindingPlanner.DescriptorTransitionRequirement transitionRequirement =
            VulkanDescriptorBindingPlanner.DescriptorTransitionRequirement.NONE;
        if (storage != null) {
            transitionRequirement = explicitlyStorageImageBound
                ? VulkanDescriptorBindingPlanner.DescriptorTransitionRequirement.STORAGE_IMAGE
                : storageImageCompatible
                ? VulkanDescriptorBindingPlanner.DescriptorTransitionRequirement.NONE
                : VulkanDescriptorBindingPlanner.DescriptorTransitionRequirement.SAMPLE;
        }

        int imageLayout = descriptorImageLayoutFor(storage, storageImageCompatible, renderState);
        ViewMaterializationRequest materializationRequest = requiresDepthOnlyView
            ? depthOnlyViewMaterializationRequest(storage, descriptorBaseMipLevel, descriptorMipLevelCount)
            : null;

        return new DescriptorImagePlan(
            requestedView,
            storage,
            requestedImageViewHandle,
            descriptorImageViewHandle,
            descriptorBaseMipLevel,
            descriptorMipLevelCount,
            requiresDepthOnlyView,
            remappedToDefaultView,
            storageImageCompatible,
            transitionRequirement,
            imageLayout,
            materializationRequest
        );
    }

    DescriptorImagePlan descriptorStorageImagePlan(int textureId, int mipLevel) {
        ImageStorageSnapshot storage = legacyStorageSnapshot(textureId);
        if (storage == null
            || storage.imageHandle() == VK10.VK_NULL_HANDLE
            || storage.defaultViewHandle() == VK10.VK_NULL_HANDLE) {
            return DescriptorImagePlan.unavailable(textureId);
        }
        int safeMipLevel = Math.max(0, mipLevel);
        if (safeMipLevel >= Math.max(1, storage.mipLevels())) {
            return DescriptorImagePlan.unavailable(textureId);
        }
        return new DescriptorImagePlan(
            null,
            storage,
            storage.defaultViewHandle(),
            storage.defaultViewHandle(),
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

    TransferTargetPlan transferTargetPlan(int textureId, int mipLevel, ImageTransferUsage usage) {
        ImageStorageSnapshot storage = legacyStorageSnapshot(textureId);
        if (storage == null || storage.imageHandle() == VK10.VK_NULL_HANDLE) {
            return TransferTargetPlan.unavailable(textureId, mipLevel, usage);
        }
        int safeMipLevel = Math.max(0, mipLevel);
        TextureLevelInfo level = storage.levelOrDefault(
            safeMipLevel,
            new TextureLevelInfo(storage.width(), storage.height(), storage.depth(), 0)
        );
        int trackedLayout = imageStateTracker.layoutFor(
            storage.textureId(),
            safeMipLevel,
            VK10.VK_IMAGE_LAYOUT_UNDEFINED
        );
        return new TransferTargetPlan(
            ViewResolutionStatus.EXISTING_COMPATIBLE_VIEW,
            storage,
            safeMipLevel,
            level.width,
            level.height,
            level.depth,
            Math.max(1, storage.pixelBytes()),
            trackedLayout,
            usage
        );
    }

    InvalidationResult invalidateTexture(int textureId) {
        imageStateTracker.unregisterTexture(textureId);
        textures.clearLegacyFallbackSamplerTextureIdIfMatches(textureId);
        return new InvalidationResult(textureId, true);
    }

    ViewMaterializationRequest depthOnlyViewMaterializationRequest(
        ImageStorageSnapshot storage,
        int baseMipLevel,
        int mipLevelCount
    ) {
        Objects.requireNonNull(storage, "storage");
        return new ViewMaterializationRequest(
            storage.textureId(),
            storage.imageHandle(),
            storage.vkFormat(),
            VK10.VK_IMAGE_ASPECT_DEPTH_BIT,
            Math.max(0, baseMipLevel),
            Math.max(1, mipLevelCount),
            layerCount(storage),
            isCubemapTarget(storage.target()),
            is3DTexture(storage.target()),
            ViewUsage.SAMPLED
        );
    }

    boolean isStorageImageLayoutCompatibleSampler(
        @Nullable ImageStorageSnapshot storage,
        int baseMip,
        int mipCount,
        VulkanDescriptorBindingPlanner.LayoutLookup layoutLookup
    ) {
        if (storage == null
            || storage.feedbackLoopCapable()
            || storage.aspectMask() != VK10.VK_IMAGE_ASPECT_COLOR_BIT
            || (storage.imageUsageFlags() & VK10.VK_IMAGE_USAGE_STORAGE_BIT) == 0) {
            return false;
        }

        int safeBaseMip = Math.max(0, baseMip);
        int safeMipCount = Math.max(1, mipCount);
        int maxMipLevels = Math.max(1, storage.mipLevels());
        if (safeBaseMip >= maxMipLevels) {
            return false;
        }
        int endMipExclusive = Math.min(maxMipLevels, safeBaseMip + safeMipCount);
        for (int level = safeBaseMip; level < endMipExclusive; level++) {
            if (layoutLookup.trackedLayout(storage.textureId(), level) != VK10.VK_IMAGE_LAYOUT_GENERAL) {
                return false;
            }
        }
        return true;
    }

    int descriptorImageLayoutFor(
        @Nullable ImageStorageSnapshot storage,
        boolean storageImageCompatible,
        VulkanDescriptorBindingPlanner.RenderStateSnapshot renderState
    ) {
        if (storage == null) {
            return VulkanImageUse.SAMPLED_COLOR.vkLayout();
        }
        if (storageImageCompatible) {
            return VK10.VK_IMAGE_LAYOUT_GENERAL;
        }
        if (storage.feedbackLoopCapable()
            && renderState.renderPassRecording()
            && renderState.activeAttachmentTextureIds().contains(storage.textureId())) {
            return VulkanImageUse.FEEDBACK_LOOP.vkLayout();
        }
        if (storage.hasDepthAspect()) {
            return VulkanImageUse.SAMPLED_DEPTH.vkLayout();
        }
        return VulkanImageUse.SAMPLED_COLOR.vkLayout();
    }

    static int layerCount(ImageStorageSnapshot storage) {
        return isCubemapTarget(storage.target()) ? 6 : 1;
    }

    static boolean is3DTexture(int target) {
        return target == VulkanicAPI.GL_TEXTURE_3D;
    }

    static boolean isCubemapTarget(int target) {
        return target == VulkanicAPI.GL_TEXTURE_CUBE_MAP
            || (target >= 0x8515 && target <= 0x851A);
    }

    enum ViewResolutionStatus {
        EXISTING_COMPATIBLE_VIEW,
        CREATE_REQUIRED,
        UNAVAILABLE,
        FALLBACK_REQUIRED
    }

    enum ViewUsage {
        SAMPLED,
        STORAGE,
        TRANSFER,
        FRAMEBUFFER,
        TEXEL_BUFFER
    }

    enum ImageTransferUsage {
        UPLOAD,
        READBACK,
        COPY_SRC,
        COPY_DST
    }

    record ImageStorageSnapshot(
        int textureId,
        int target,
        long imageHandle,
        long memoryHandle,
        long defaultViewHandle,
        int vkFormat,
        int aspectMask,
        int imageUsageFlags,
        boolean feedbackLoopCapable,
        int pixelBytes,
        int mipLevels,
        int width,
        int height,
        int depth,
        int sourceFormat,
        int sourceType,
        Map<Integer, Integer> integerParameters,
        Map<Integer, TextureLevelInfo> levels
    ) implements VulkanRenderPassExecutionCoordinator.TextureIdentity {
        ImageStorageSnapshot {
            integerParameters = Map.copyOf(integerParameters);
            levels = Map.copyOf(levels);
        }

        static ImageStorageSnapshot from(LegacyTextureStorageSnapshot texture) {
            return new ImageStorageSnapshot(
                texture.id(),
                texture.target(),
                texture.imageHandle(),
                texture.memoryHandle(),
                texture.defaultViewHandle(),
                texture.vkFormat(),
                texture.aspectMask(),
                texture.imageUsageFlags(),
                texture.feedbackLoopCapable(),
                texture.pixelBytes(),
                texture.mipLevels(),
                texture.width(),
                texture.height(),
                texture.depth(),
                texture.sourceFormat(),
                texture.sourceType(),
                texture.integerParameters(),
                texture.levels()
            );
        }

        @Override
        public int textureId() {
            return textureId;
        }

        boolean hasDepthAspect() {
            return (aspectMask & VK10.VK_IMAGE_ASPECT_DEPTH_BIT) != 0;
        }

        boolean hasStencilAspect() {
            return (aspectMask & VK10.VK_IMAGE_ASPECT_STENCIL_BIT) != 0;
        }

        int integerParameterOrDefault(int pname, int defaultValue) {
            return integerParameters.getOrDefault(pname, defaultValue);
        }

        TextureLevelInfo levelOrDefault(int level, TextureLevelInfo defaultValue) {
            return levels.getOrDefault(level, defaultValue);
        }
    }

    record VulkanicRenderPassDescriptorParts(
        VulkanicRenderPassDescriptor.LoadOp loadOp,
        VulkanicRenderPassDescriptor.StoreOp storeOp,
        OptionalInt clearColor,
        OptionalDouble clearDepth
    ) {
        VulkanicRenderPassDescriptorParts {
            Objects.requireNonNull(loadOp, "loadOp");
            Objects.requireNonNull(storeOp, "storeOp");
            Objects.requireNonNull(clearColor, "clearColor");
            Objects.requireNonNull(clearDepth, "clearDepth");
        }
    }

    record ImageViewRequest(
        int textureId,
        int aspectMask,
        int baseMipLevel,
        int mipLevelCount,
        ViewUsage usage
    ) {}

    record ViewMaterializationRequest(
        int textureId,
        long imageHandle,
        int vkFormat,
        int aspectMask,
        int baseMipLevel,
        int mipLevelCount,
        int layerCount,
        boolean cubemapCompatible,
        boolean texture3D,
        ViewUsage usage
    ) {}

    record ResolvedViewSnapshot(
        ViewResolutionStatus status,
        @Nullable ImageStorageSnapshot storage,
        long imageViewHandle,
        @Nullable ViewMaterializationRequest materializationRequest
    ) {}

    record AttachmentViewPlan(
        ImageStorageSnapshot storage,
        long imageViewHandle,
        int vkFormat,
        int aspectMask,
        int width,
        int height,
        boolean feedbackLoopCapable,
        VulkanicResourceUsage initialUsage,
        VulkanicResourceUsage passUsage,
        VulkanicResourceUsage finalUsage,
        VulkanicRenderPassDescriptorParts descriptorParts
    ) {
        AttachmentViewPlan {
            Objects.requireNonNull(storage, "storage");
            Objects.requireNonNull(initialUsage, "initialUsage");
            Objects.requireNonNull(passUsage, "passUsage");
            Objects.requireNonNull(finalUsage, "finalUsage");
            Objects.requireNonNull(descriptorParts, "descriptorParts");
        }
    }

    record DescriptorImagePlan(
        @Nullable VulkanTextureView textureView,
        @Nullable ImageStorageSnapshot storage,
        long requestedImageViewHandle,
        long descriptorImageViewHandle,
        int baseMipLevel,
        int mipLevelCount,
        boolean requiresDepthOnlyView,
        boolean remappedToDefaultView,
        boolean storageImageCompatible,
        VulkanDescriptorBindingPlanner.DescriptorTransitionRequirement transitionRequirement,
        int imageLayout,
        @Nullable ViewMaterializationRequest materializationRequest
    ) {
        static DescriptorImagePlan unavailable(int textureId) {
            return new DescriptorImagePlan(
                null,
                null,
                VK10.VK_NULL_HANDLE,
                VK10.VK_NULL_HANDLE,
                0,
                1,
                false,
                false,
                false,
                VulkanDescriptorBindingPlanner.DescriptorTransitionRequirement.NONE,
                VK10.VK_IMAGE_LAYOUT_UNDEFINED,
                null
            );
        }
    }

    record TransferTargetPlan(
        ViewResolutionStatus status,
        @Nullable ImageStorageSnapshot storage,
        int mipLevel,
        int width,
        int height,
        int depth,
        int pixelBytes,
        int trackedLayout,
        ImageTransferUsage usage
    ) {
        static TransferTargetPlan unavailable(int textureId, int mipLevel, ImageTransferUsage usage) {
            return new TransferTargetPlan(
                ViewResolutionStatus.UNAVAILABLE,
                null,
                Math.max(0, mipLevel),
                0,
                0,
                0,
                0,
                VK10.VK_IMAGE_LAYOUT_UNDEFINED,
                usage
            );
        }
    }

    record TexelBufferViewPlan(
        int textureId,
        int internalFormat,
        int legacyBufferId,
        long bufferViewHandle
    ) {}

    record InvalidationResult(
        int textureId,
        boolean layoutStateCleared
    ) {}
}
