package net.vulkanic.backends.vulkan;

import net.vulkanic.VulkanicAPI;
import net.vulkanic.VulkanicLegacyCompatibilityAdapter;
import net.vulkanic.VulkanicPassResourceModel;
import net.vulkanic.VulkanicResourceUsage;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.vulkan.VK10;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.function.Consumer;

/**
 * Coordinates texture-storage and transfer lifecycle decisions.
 *
 * <p>Vulkan side effects remain in {@link VulkanBackend.NativeSpine}: image,
 * memory, staging, view, command, barrier, copy, blit, map, and destroy calls
 * are only materialized there. This coordinator owns the semantic state machine
 * around those calls: storage sizing/replacement decisions, pixel-store
 * arithmetic, transfer layout intent, staging retirement publication, and
 * layout/view invalidation publication.</p>
 */
final class VulkanTextureTransferExecutionCoordinator {
    private static final int GL_COLOR_BUFFER_BIT = 0x00004000;
    private static final int GL_DEPTH_BUFFER_BIT = 0x00000100;
    private static final int GL_STENCIL_BUFFER_BIT = 0x00000400;

    @SuppressWarnings("unused")
    private final VulkanTextureResourceManager textures;
    private final VulkanImageResourceViewCoordinator imageViews;
    private final VulkanImageStateTracker imageStateTracker;
    private final VulkanStagingTransferManager stagingTransfers;
    @SuppressWarnings("unused")
    private final VulkanFrameExecutionCoordinator<?> frameExecution;
    @SuppressWarnings("unused")
    private final VulkanDeferredResourceLifetime<?> deferredLifetime;

    VulkanTextureTransferExecutionCoordinator(
        VulkanTextureResourceManager textures,
        VulkanImageResourceViewCoordinator imageViews,
        VulkanImageStateTracker imageStateTracker,
        VulkanStagingTransferManager stagingTransfers,
        VulkanFrameExecutionCoordinator<?> frameExecution,
        VulkanDeferredResourceLifetime<?> deferredLifetime
    ) {
        this.textures = Objects.requireNonNull(textures, "textures");
        this.imageViews = Objects.requireNonNull(imageViews, "imageViews");
        this.imageStateTracker = Objects.requireNonNull(imageStateTracker, "imageStateTracker");
        this.stagingTransfers = Objects.requireNonNull(stagingTransfers, "stagingTransfers");
        this.frameExecution = Objects.requireNonNull(frameExecution, "frameExecution");
        this.deferredLifetime = Objects.requireNonNull(deferredLifetime, "deferredLifetime");
    }

    PixelUnpackPlan planPixelUnpack2D(
        ByteBuffer pixels,
        TransferFormat format,
        int width,
        int height,
        PixelStoreSnapshot pixelStore
    ) {
        Objects.requireNonNull(pixels, "pixels");
        PixelUnpackPlan plan = planPixelUnpack2D(format, width, height, pixelStore, pixels.remaining());
        if (pixels.remaining() < plan.requiredSourceBytes()) {
            throw new IllegalArgumentException(
                "Pixel upload buffer too small. Required=" + plan.requiredSourceBytes() + ", remaining=" + pixels.remaining());
        }
        return plan;
    }

    PixelUnpackPlan planPixelUnpack2D(
        TransferFormat format,
        int width,
        int height,
        PixelStoreSnapshot pixelStore,
        int availableBytes
    ) {
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(pixelStore, "pixelStore");
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("width/height must be > 0");
        }

        int rowLength = pixelStore.unpackRowLength() > 0 ? pixelStore.unpackRowLength() : width;
        int rowBytes = Math.multiplyExact(rowLength, format.unpackPixelBytes());
        int stride = align(rowBytes, pixelStore.unpackAlignment());
        int startOffset = Math.addExact(
            Math.multiplyExact(pixelStore.unpackSkipRows(), stride),
            Math.multiplyExact(pixelStore.unpackSkipPixels(), format.unpackPixelBytes())
        );
        long requiredLong = (long) startOffset
            + (long) (height - 1) * stride
            + (long) width * format.unpackPixelBytes();
        if (requiredLong > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Pixel upload source size exceeds int range: " + requiredLong);
        }
        int required = (int) requiredLong;
        if (availableBytes >= 0 && availableBytes < required) {
            throw new IllegalArgumentException(
                "Pixel upload buffer too small. Required=" + required + ", remaining=" + availableBytes);
        }

        int sourceRowBytes = Math.multiplyExact(width, format.unpackPixelBytes());
        int destinationRowBytes = Math.multiplyExact(width, format.pixelBytes());
        boolean directSlice = !format.expandRgbToRgba() && stride == sourceRowBytes;
        return new PixelUnpackPlan(
            width,
            height,
            1,
            rowLength,
            rowBytes,
            stride,
            startOffset,
            required,
            sourceRowBytes,
            destinationRowBytes,
            directSlice,
            format
        );
    }

    PixelUnpackPlan planPixelUnpack3D(ByteBuffer pixels, TransferFormat format, int width, int height, int depth) {
        Objects.requireNonNull(pixels, "pixels");
        Objects.requireNonNull(format, "format");
        if (width <= 0 || height <= 0 || depth <= 0) {
            throw new IllegalArgumentException("width/height/depth must be > 0");
        }

        long requiredLong = (long) width * height * depth * format.unpackPixelBytes();
        if (requiredLong > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("3D pixel upload source size exceeds int range: " + requiredLong);
        }
        int required = (int) requiredLong;
        if (pixels.remaining() < required) {
            throw new IllegalArgumentException(
                "3D pixel upload buffer too small. Required=" + required + ", remaining=" + pixels.remaining());
        }
        int rowBytes = Math.multiplyExact(width, format.unpackPixelBytes());
        return new PixelUnpackPlan(
            width,
            height,
            depth,
            width,
            rowBytes,
            rowBytes,
            0,
            required,
            rowBytes,
            Math.multiplyExact(width, format.pixelBytes()),
            !format.expandRgbToRgba(),
            format
        );
    }

    ByteBuffer materializePixelUnpack(PixelUnpackPlan plan, ByteBuffer pixels) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(pixels, "pixels");
        TransferFormat format = plan.format();
        if (plan.directSlice()) {
            ByteBuffer source = pixels.duplicate();
            int basePosition = source.position();
            source.position(basePosition + plan.startOffset());
            source.limit(basePosition + plan.requiredSourceBytes());
            return source.slice();
        }

        int destinationBytes = Math.multiplyExact(
            Math.multiplyExact(plan.destinationRowBytes(), plan.height()),
            plan.depth()
        );
        ByteBuffer packed = ByteBuffer.allocateDirect(destinationBytes).order(ByteOrder.nativeOrder());
        ByteBuffer source = pixels.duplicate();
        int sourceBase = source.position() + plan.startOffset();
        if (format.expandRgbToRgba()) {
            for (int slice = 0; slice < plan.depth(); slice++) {
                int sliceStart = sourceBase + slice * plan.height() * plan.stride();
                for (int row = 0; row < plan.height(); row++) {
                    int rowStart = sliceStart + row * plan.stride();
                    for (int column = 0; column < plan.width(); column++) {
                        int sourcePixelStart = rowStart + column * format.unpackPixelBytes();
                        packed.put(source.get(sourcePixelStart));
                        packed.put(source.get(sourcePixelStart + 1));
                        packed.put(source.get(sourcePixelStart + 2));
                        packed.put((byte) 0xFF);
                    }
                }
            }
        } else {
            for (int slice = 0; slice < plan.depth(); slice++) {
                int sliceStart = sourceBase + slice * plan.height() * plan.stride();
                for (int row = 0; row < plan.height(); row++) {
                    int rowStart = sliceStart + row * plan.stride();
                    ByteBuffer rowSlice = source.duplicate();
                    rowSlice.position(rowStart);
                    rowSlice.limit(rowStart + plan.sourceRowBytes());
                    packed.put(rowSlice);
                }
            }
        }

        packed.flip();
        return packed;
    }

    StorageDefinitionPlan planStorageDefinition(StorageDefinitionRequest request) {
        Objects.requireNonNull(request, "request");
        if (request.width() <= 0 || request.height() <= 0 || request.depth() <= 0) {
            throw new IllegalArgumentException("texture storage dimensions must be positive");
        }
        if (request.level() < 0) {
            throw new IllegalArgumentException("texture mip level must be non-negative");
        }

        int inferredBaseWidth = request.level() == 0
            ? request.width()
            : Math.max(1, request.width() << request.level());
        int inferredBaseHeight = request.level() == 0
            ? request.height()
            : Math.max(1, request.height() << request.level());
        int inferredBaseDepth = request.level() == 0
            ? request.depth()
            : Math.max(1, request.depth() << request.level());
        int configuredMipLevels = Math.max(1, request.maxConfiguredLevel() + 1);
        int maxMipExtentWidth = request.texture3D()
            ? Math.max(inferredBaseWidth, inferredBaseDepth)
            : inferredBaseWidth;
        int maxPossibleMipLevels = maxMipLevelsForExtent(maxMipExtentWidth, inferredBaseHeight);
        int requiredMipLevels = Math.max(
            1,
            Math.max(request.level() + 1, Math.min(configuredMipLevels, maxPossibleMipLevels))
        );

        VulkanImageResourceViewCoordinator.ImageStorageSnapshot current = request.currentStorage();
        boolean hasExistingImage = current != null && current.imageHandle() != VK10.VK_NULL_HANDLE;
        boolean sameStorageShape = hasExistingImage
            && current.vkFormat() == request.format().vkFormat()
            && current.width() == inferredBaseWidth
            && current.height() == inferredBaseHeight
            && current.depth() == (request.texture3D() ? inferredBaseDepth : 1);
        boolean preserveExistingLevels = sameStorageShape && current.mipLevels() < requiredMipLevels;
        boolean needsRecreate = !sameStorageShape
            || current.mipLevels() < requiredMipLevels
            || request.require3DTargetChange();

        Map<Integer, TextureLevelInfo> preservedLevels = preserveExistingLevels
            ? current.levels()
            : Map.of();
        return new StorageDefinitionPlan(
            needsRecreate,
            preserveExistingLevels,
            inferredBaseWidth,
            inferredBaseHeight,
            inferredBaseDepth,
            requiredMipLevels,
            preservedLevels
        );
    }

    TransferOperationPlan planUpload(int textureId, int mipLevel, int baseLayer, int layerCount) {
        return planTransferOperation(
            textureId,
            mipLevel,
            baseLayer,
            layerCount,
            VulkanImageResourceViewCoordinator.ImageTransferUsage.UPLOAD,
            VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
            true,
            false
        );
    }

    TransferOperationPlan planReadback(int textureId, int mipLevel, int baseLayer, int layerCount) {
        return planTransferOperation(
            textureId,
            mipLevel,
            baseLayer,
            layerCount,
            VulkanImageResourceViewCoordinator.ImageTransferUsage.READBACK,
            VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
            false,
            true
        );
    }

    TransferOperationPlan planClear(int textureId, int mipLevel, int baseLayer, int layerCount) {
        return planTransferOperation(
            textureId,
            mipLevel,
            baseLayer,
            layerCount,
            VulkanImageResourceViewCoordinator.ImageTransferUsage.COPY_DST,
            VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
            true,
            true
        );
    }

    CopyOperationPlan planCopy(
        int sourceTextureId,
        int sourceLevel,
        int destTextureId,
        int destLevel,
        int layerCount
    ) {
        TransferOperationPlan source = planTransferOperation(
            sourceTextureId,
            sourceLevel,
            0,
            layerCount,
            VulkanImageResourceViewCoordinator.ImageTransferUsage.COPY_SRC,
            VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
            false,
            true
        );
        TransferOperationPlan dest = planTransferOperation(
            destTextureId,
            destLevel,
            0,
            layerCount,
            VulkanImageResourceViewCoordinator.ImageTransferUsage.COPY_DST,
            VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
            false,
            true
        );
        if (source.storage().aspectMask() != dest.storage().aspectMask()) {
            throw new IllegalArgumentException("copy requires matching source and destination texture aspects");
        }
        return new CopyOperationPlan(source, dest);
    }

    BlitOperationPlan planBlit(
        int sourceTextureId,
        int sourceLevel,
        int destTextureId,
        int destLevel,
        int layerCount,
        int mask,
        String operation
    ) {
        VulkanImageResourceViewCoordinator.ImageStorageSnapshot sourceStorage = requireStorage(sourceTextureId, sourceLevel,
            VulkanImageResourceViewCoordinator.ImageTransferUsage.COPY_SRC);
        VulkanImageResourceViewCoordinator.ImageStorageSnapshot destStorage = requireStorage(destTextureId, destLevel,
            VulkanImageResourceViewCoordinator.ImageTransferUsage.COPY_DST);
        int operationAspectMask = blitOperationAspectMask(
            mask,
            sourceStorage.aspectMask(),
            destStorage.aspectMask(),
            operation
        );
        int sourceTransitionAspectMask = blitTransitionAspectMask(operationAspectMask, sourceStorage.aspectMask());
        int destTransitionAspectMask = blitTransitionAspectMask(operationAspectMask, destStorage.aspectMask());
        TransferOperationPlan source = planTransferOperation(
            sourceTextureId,
            sourceLevel,
            0,
            layerCount,
            VulkanImageResourceViewCoordinator.ImageTransferUsage.COPY_SRC,
            VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
            sourceTransitionAspectMask,
            false,
            true
        );
        TransferOperationPlan dest = planTransferOperation(
            destTextureId,
            destLevel,
            0,
            layerCount,
            VulkanImageResourceViewCoordinator.ImageTransferUsage.COPY_DST,
            VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
            destTransitionAspectMask,
            false,
            true
        );
        return new BlitOperationPlan(source, dest, operationAspectMask, sourceTransitionAspectMask, destTransitionAspectMask);
    }

    MipmapPlan planMipmap(int textureId, String operation) {
        VulkanImageResourceViewCoordinator.ImageStorageSnapshot storage = requireStorage(
            textureId,
            0,
            VulkanImageResourceViewCoordinator.ImageTransferUsage.COPY_DST
        );
        if (storage.aspectMask() != VK10.VK_IMAGE_ASPECT_COLOR_BIT) {
            throw new UnsupportedOperationException(operation + " currently supports only color textures");
        }
        return new MipmapPlan(
            storage,
            VulkanImageResourceViewCoordinator.layerCount(storage),
            preferredIdleLayout(storage)
        );
    }

    int preferredIdleLayout(VulkanImageResourceViewCoordinator.ImageStorageSnapshot storage) {
        Objects.requireNonNull(storage, "storage");
        return storage.hasDepthAspect()
            ? VulkanImageUse.SAMPLED_DEPTH.vkLayout()
            : VulkanImageUse.SAMPLED_COLOR.vkLayout();
    }

    int preferredIdleLayout(int textureId) {
        return preferredIdleLayout(requireStorage(textureId, 0, VulkanImageResourceViewCoordinator.ImageTransferUsage.UPLOAD));
    }

    int trackedLayoutOrIdle(VulkanImageResourceViewCoordinator.ImageStorageSnapshot storage, int mipLevel) {
        int trackedLayout = imageStateTracker.layoutFor(storage.textureId(), mipLevel, VK10.VK_IMAGE_LAYOUT_UNDEFINED);
        return trackedLayout == VK10.VK_IMAGE_LAYOUT_UNDEFINED ? preferredIdleLayout(storage) : trackedLayout;
    }

    void publishStorageCreated(
        int textureId,
        long imageHandle,
        int aspectMask,
        int mipLevels,
        int layerCount,
        boolean feedbackLoopCapable
    ) {
        imageStateTracker.registerTexture(
            textureId,
            imageHandle,
            aspectMask,
            mipLevels,
            layerCount,
            feedbackLoopCapable,
            VK10.VK_IMAGE_LAYOUT_UNDEFINED
        );
    }

    void publishStorageDestroyed(int textureId) {
        imageStateTracker.clearTextureStorage(textureId);
    }

    void publishTextureInvalidated(int textureId) {
        imageViews.invalidateTexture(textureId);
    }

    void publishLayout(TransferOperationPlan plan, int layout) {
        Objects.requireNonNull(plan, "plan");
        imageStateTracker.recordLayoutRange(
            plan.storage().textureId(),
            plan.mipLevel(),
            1,
            plan.baseLayer(),
            plan.layerCount(),
            layout
        );
    }

    void publishLayout(int textureId, int mipLevel, int layout) {
        imageStateTracker.recordLayout(textureId, mipLevel, layout);
    }

    void publishMipmapLevelLayout(int textureId, int mipLevel, int baseLayer, int layerCount, int layout) {
        imageStateTracker.recordLayoutRange(textureId, mipLevel, 1, baseLayer, layerCount, layout);
    }

    void associateStagingCommand(VulkanStagingTransferManager.StagingBufferRecord staging, long commandBufferHandle) {
        stagingTransfers.associateTransferCommand(staging, commandBufferHandle);
    }

    VulkanStagingTransferManager.StagingBufferRecord recordUploadStaging(long bufferHandle, long memoryHandle, long byteCount) {
        return stagingTransfers.recordUploadAllocation(bufferHandle, memoryHandle, byteCount);
    }

    void markUploadStagingMapped(VulkanStagingTransferManager.StagingBufferRecord staging) {
        stagingTransfers.markMapped(staging);
    }

    void markUploadStagingUnmapped(VulkanStagingTransferManager.StagingBufferRecord staging) {
        stagingTransfers.markUnmapped(staging);
    }

    void retireStagingAfterTransfer(VulkanStagingTransferManager.StagingBufferRecord staging, int activeImmediateSlot) {
        if (staging != null) {
            stagingTransfers.retireAfterTransfer(staging, activeImmediateSlot);
        }
    }

    void cleanupFailedStagingTransfer(
        VulkanStagingTransferManager.StagingBufferRecord staging,
        boolean commandMayReferenceRecord,
        int activeImmediateSlot,
        boolean deviceAvailable,
        Consumer<VulkanStagingTransferManager.StagingBufferRecord> destroy
    ) {
        if (staging != null) {
            stagingTransfers.cleanupFailedTransfer(
                staging,
                commandMayReferenceRecord,
                activeImmediateSlot,
                deviceAvailable,
                destroy
            );
        }
    }

    VulkanStagingTransferManager.ReadbackTransferRecord recordReadbackStaging(int bufferId, long byteCount) {
        return stagingTransfers.recordReadbackStaging(bufferId, byteCount);
    }

    void associateReadbackCommand(VulkanStagingTransferManager.ReadbackTransferRecord readback, long commandBufferHandle) {
        stagingTransfers.associateReadbackCommand(readback, commandBufferHandle);
    }

    VulkanStagingTransferManager.ReadbackResult mapReadbackResult(
        VulkanStagingTransferManager.ReadbackTransferRecord readback,
        ByteBuffer mappedData,
        Runnable unmapAction
    ) {
        return stagingTransfers.mapReadbackResult(readback, mappedData, unmapAction);
    }

    void discardReadback(VulkanStagingTransferManager.ReadbackTransferRecord readback) {
        stagingTransfers.discardReadback(readback);
    }

    private TransferOperationPlan planTransferOperation(
        int textureId,
        int mipLevel,
        int baseLayer,
        int layerCount,
        VulkanImageResourceViewCoordinator.ImageTransferUsage usage,
        int transferLayout,
        boolean finalLayoutIsIdle,
        boolean undefinedFallsBackToIdle
    ) {
        VulkanImageResourceViewCoordinator.ImageStorageSnapshot storage = requireStorage(textureId, mipLevel, usage);
        return planTransferOperation(
            textureId,
            mipLevel,
            baseLayer,
            layerCount,
            usage,
            transferLayout,
            storage.aspectMask(),
            finalLayoutIsIdle,
            undefinedFallsBackToIdle
        );
    }

    private TransferOperationPlan planTransferOperation(
        int textureId,
        int mipLevel,
        int baseLayer,
        int layerCount,
        VulkanImageResourceViewCoordinator.ImageTransferUsage usage,
        int transferLayout,
        int transitionAspectMask,
        boolean finalLayoutIsIdle,
        boolean undefinedFallsBackToIdle
    ) {
        VulkanImageResourceViewCoordinator.TransferTargetPlan target =
            imageViews.transferTargetPlan(textureId, mipLevel, usage);
        if (target.status() == VulkanImageResourceViewCoordinator.ViewResolutionStatus.UNAVAILABLE
            || target.storage() == null) {
            throw new IllegalStateException("Texture transfer target is unavailable for texture " + textureId);
        }
        VulkanImageResourceViewCoordinator.ImageStorageSnapshot storage = target.storage();
        int originalLayout = undefinedFallsBackToIdle && target.trackedLayout() == VK10.VK_IMAGE_LAYOUT_UNDEFINED
            ? preferredIdleLayout(storage)
            : target.trackedLayout();
        int finalLayout = finalLayoutIsIdle ? preferredIdleLayout(storage) : originalLayout;
        VulkanicPassResourceModel.PassExecutionPlan resourcePlan =
            explicitTransferPlan(storage, target.mipLevel(), Math.max(0, baseLayer), Math.max(1, layerCount), usage);
        VulkanResourceUsageExecutionPlanner.plan(resourcePlan);
        return new TransferOperationPlan(
            storage,
            target.mipLevel(),
            Math.max(0, baseLayer),
            Math.max(1, layerCount),
            transitionAspectMask,
            originalLayout,
            transferLayout,
            finalLayout,
            target.width(),
            target.height(),
            target.depth(),
            target.pixelBytes(),
            usage,
            resourcePlan
        );
    }

    private VulkanicPassResourceModel.PassExecutionPlan explicitTransferPlan(
        VulkanImageResourceViewCoordinator.ImageStorageSnapshot storage,
        int mipLevel,
        int baseLayer,
        int layerCount,
        VulkanImageResourceViewCoordinator.ImageTransferUsage usage
    ) {
        VulkanicPassResourceModel.ResourceKind kind = switch (usage) {
            case UPLOAD, COPY_DST -> VulkanicPassResourceModel.ResourceKind.TRANSFER_DESTINATION;
            case COPY_SRC -> VulkanicPassResourceModel.ResourceKind.TRANSFER_SOURCE;
            case READBACK -> VulkanicPassResourceModel.ResourceKind.READBACK_SOURCE;
        };
        VulkanicPassResourceModel.Access access = switch (usage) {
            case UPLOAD, COPY_DST -> VulkanicPassResourceModel.Access.WRITE;
            case COPY_SRC, READBACK -> VulkanicPassResourceModel.Access.READ;
        };
        VulkanicResourceUsage usageIntent = switch (usage) {
            case UPLOAD, COPY_DST -> VulkanicResourceUsage.TRANSFER_DST;
            case COPY_SRC, READBACK -> VulkanicResourceUsage.TRANSFER_SRC;
        };
        VulkanicPassResourceModel.PassKind passKind = usage == VulkanImageResourceViewCoordinator.ImageTransferUsage.READBACK
            ? VulkanicPassResourceModel.PassKind.READBACK
            : VulkanicPassResourceModel.PassKind.TRANSFER;
        return VulkanicLegacyCompatibilityAdapter.planTransfer(new VulkanicLegacyCompatibilityAdapter.TransferSnapshot(
            passKind,
            "texture-transfer:" + usage.name().toLowerCase(java.util.Locale.ROOT),
            "texture-transfer",
            "texture-transfer-" + storage.textureId(),
            kind,
            "texture:" + storage.textureId(),
            access,
            subresourceForAspectMask(storage.aspectMask(), mipLevel, 1, baseLayer, layerCount),
            usageIntent,
            "texture-transfer:" + usage.name().toLowerCase(java.util.Locale.ROOT),
            List.of("transition-before-copy", "publish-layout-after-copy"),
            false,
            false
        ));
    }

    private VulkanicPassResourceModel.Subresource subresourceForAspectMask(
        int aspectMask,
        int mipLevel,
        int levelCount,
        int baseLayer,
        int layerCount
    ) {
        int depthStencil = VK10.VK_IMAGE_ASPECT_DEPTH_BIT | VK10.VK_IMAGE_ASPECT_STENCIL_BIT;
        if ((aspectMask & depthStencil) == depthStencil) {
            return VulkanicPassResourceModel.Subresource.depthStencil(mipLevel, levelCount, baseLayer, layerCount);
        }
        if ((aspectMask & VK10.VK_IMAGE_ASPECT_DEPTH_BIT) != 0) {
            return VulkanicPassResourceModel.Subresource.depth(mipLevel, levelCount, baseLayer, layerCount);
        }
        return VulkanicPassResourceModel.Subresource.color(mipLevel, levelCount, baseLayer, layerCount);
    }

    private VulkanImageResourceViewCoordinator.ImageStorageSnapshot requireStorage(
        int textureId,
        int mipLevel,
        VulkanImageResourceViewCoordinator.ImageTransferUsage usage
    ) {
        VulkanImageResourceViewCoordinator.TransferTargetPlan target =
            imageViews.transferTargetPlan(textureId, mipLevel, usage);
        if (target.status() == VulkanImageResourceViewCoordinator.ViewResolutionStatus.UNAVAILABLE
            || target.storage() == null
            || target.storage().imageHandle() == VK10.VK_NULL_HANDLE) {
            throw new IllegalStateException("Texture transfer target is unavailable for texture " + textureId);
        }
        return target.storage();
    }

    static int textureLevelHeight(VulkanImageResourceViewCoordinator.ImageStorageSnapshot storage, int level) {
        TextureLevelInfo levelInfo = storage.levels().get(level);
        if (levelInfo != null) {
            return Math.max(1, levelInfo.height);
        }
        return Math.max(1, storage.height() >> Math.max(0, level));
    }

    static int toVulkanImageY(VulkanImageResourceViewCoordinator.ImageStorageSnapshot storage, int level, int glY) {
        return textureLevelHeight(storage, level) - glY;
    }

    static int toVulkanImageRegionY(
        VulkanImageResourceViewCoordinator.ImageStorageSnapshot storage,
        int level,
        int glY,
        int regionHeight
    ) {
        return textureLevelHeight(storage, level) - glY - regionHeight;
    }

    static int align(int value, int alignment) {
        if (alignment <= 1) {
            return value;
        }
        int mask = alignment - 1;
        return (value + mask) & ~mask;
    }

    static int maxMipLevelsForExtent(int width, int height) {
        int maxDimension = Math.max(width, height);
        if (maxDimension <= 0) {
            return 1;
        }
        return 32 - Integer.numberOfLeadingZeros(maxDimension);
    }

    private static int blitOperationAspectMask(int mask, int sourceAspectMask, int destAspectMask, String operation) {
        int requestedAspectMask = 0;
        if ((mask & GL_COLOR_BUFFER_BIT) != 0) {
            requestedAspectMask |= VK10.VK_IMAGE_ASPECT_COLOR_BIT;
        }
        if ((mask & GL_DEPTH_BUFFER_BIT) != 0) {
            requestedAspectMask |= VK10.VK_IMAGE_ASPECT_DEPTH_BIT;
        }
        if ((mask & GL_STENCIL_BUFFER_BIT) != 0) {
            requestedAspectMask |= VK10.VK_IMAGE_ASPECT_STENCIL_BIT;
        }
        if (requestedAspectMask == 0) {
            return 0;
        }
        if ((requestedAspectMask & VK10.VK_IMAGE_ASPECT_COLOR_BIT) != 0
            && requestedAspectMask != VK10.VK_IMAGE_ASPECT_COLOR_BIT) {
            throw new IllegalArgumentException(
                operation + " cannot blit color and depth/stencil aspects through a single legacy texture operation"
            );
        }

        int supportedAspectMask = sourceAspectMask & destAspectMask & requestedAspectMask;
        if (supportedAspectMask != requestedAspectMask) {
            throw new IllegalArgumentException(
                operation + " requires source and destination textures to support requested blit aspects"
            );
        }
        return requestedAspectMask;
    }

    private static int blitTransitionAspectMask(int operationAspectMask, int textureAspectMask) {
        int depthStencilMask = VK10.VK_IMAGE_ASPECT_DEPTH_BIT | VK10.VK_IMAGE_ASPECT_STENCIL_BIT;
        if ((operationAspectMask & depthStencilMask) != 0
            && (textureAspectMask & depthStencilMask) == depthStencilMask) {
            return depthStencilMask;
        }
        return operationAspectMask;
    }

    record PixelStoreSnapshot(
        int unpackRowLength,
        int unpackSkipRows,
        int unpackSkipPixels,
        int unpackAlignment
    ) {
        PixelStoreSnapshot {
            if (unpackRowLength < 0 || unpackSkipRows < 0 || unpackSkipPixels < 0) {
                throw new IllegalArgumentException("pixel-store row length and skip values must be non-negative");
            }
            if (unpackAlignment != 1 && unpackAlignment != 2 && unpackAlignment != 4 && unpackAlignment != 8) {
                throw new IllegalArgumentException("unpackAlignment must be one of {1,2,4,8}");
            }
        }
    }

    record TransferFormat(
        int vkFormat,
        int pixelBytes,
        int aspectMask,
        int unpackPixelBytes,
        boolean expandRgbToRgba
    ) {
        TransferFormat {
            if (pixelBytes <= 0 || unpackPixelBytes <= 0) {
                throw new IllegalArgumentException("pixel byte counts must be positive");
            }
        }
    }

    record PixelUnpackPlan(
        int width,
        int height,
        int depth,
        int rowLength,
        int rowBytes,
        int stride,
        int startOffset,
        int requiredSourceBytes,
        int sourceRowBytes,
        int destinationRowBytes,
        boolean directSlice,
        TransferFormat format
    ) {
        PixelUnpackPlan {
            Objects.requireNonNull(format, "format");
        }
    }

    record StorageDefinitionRequest(
        int target,
        int level,
        int width,
        int height,
        int depth,
        int maxConfiguredLevel,
        TransferFormat format,
        boolean texture3D,
        boolean require3DTargetChange,
        @Nullable VulkanImageResourceViewCoordinator.ImageStorageSnapshot currentStorage
    ) {
        StorageDefinitionRequest {
            Objects.requireNonNull(format, "format");
            maxConfiguredLevel = Math.max(0, maxConfiguredLevel);
        }
    }

    record StorageDefinitionPlan(
        boolean needsRecreate,
        boolean preserveExistingLevels,
        int inferredBaseWidth,
        int inferredBaseHeight,
        int inferredBaseDepth,
        int requiredMipLevels,
        Map<Integer, TextureLevelInfo> preservedLevels
    ) {
        StorageDefinitionPlan {
            preservedLevels = Map.copyOf(preservedLevels);
        }
    }

    record TransferOperationPlan(
        VulkanImageResourceViewCoordinator.ImageStorageSnapshot storage,
        int mipLevel,
        int baseLayer,
        int layerCount,
        int transitionAspectMask,
        int originalLayout,
        int transferLayout,
        int finalLayout,
        int width,
        int height,
        int depth,
        int pixelBytes,
        VulkanImageResourceViewCoordinator.ImageTransferUsage usage,
        VulkanicPassResourceModel.PassExecutionPlan resourcePlan
    ) {
        TransferOperationPlan {
            Objects.requireNonNull(storage, "storage");
            Objects.requireNonNull(usage, "usage");
            Objects.requireNonNull(resourcePlan, "resourcePlan");
        }

        long requiredByteCount(int width, int height) {
            return (long) width * height * Math.max(1, pixelBytes);
        }
    }

    record CopyOperationPlan(TransferOperationPlan source, TransferOperationPlan destination) {
        CopyOperationPlan {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(destination, "destination");
        }
    }

    record BlitOperationPlan(
        TransferOperationPlan source,
        TransferOperationPlan destination,
        int operationAspectMask,
        int sourceTransitionAspectMask,
        int destinationTransitionAspectMask
    ) {
        BlitOperationPlan {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(destination, "destination");
        }
    }

    record MipmapPlan(
        VulkanImageResourceViewCoordinator.ImageStorageSnapshot storage,
        int layerCount,
        int finalLayout
    ) {
        MipmapPlan {
            Objects.requireNonNull(storage, "storage");
        }
    }
}
