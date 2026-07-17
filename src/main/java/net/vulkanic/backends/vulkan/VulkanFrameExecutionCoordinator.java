package net.vulkanic.backends.vulkan;

import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkCommandBuffer;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

/**
 * Coordinates Vulkan frame-execution lifecycle state across the swapchain,
 * command-submission, deferred-lifetime, and staging-transfer managers.
 *
 * <p>The state machine is:</p>
 * <pre>
 * IDLE
 *   -> prepare begin
 *   -> frame fence complete or skipped
 *   -> image acquired
 *   -> optional frame command-buffer recording
 *   -> frame submit or semaphore bridge submit
 *   -> present completed/skipped/out-of-date/suboptimal
 *   -> IDLE with the frame slot advanced
 * </pre>
 *
 * <p>Immediate submissions use the same submitted-work generation accounting as
 * frame submissions, but keep their independent ring slots. This coordinator
 * never issues Vulkan commands; NativeSpine performs waits, resets, acquire,
 * command-buffer begin/end, queue submit, present, and native destruction.</p>
 */
final class VulkanFrameExecutionCoordinator<DescriptorResource> {
    private final VulkanSwapchainStateManager swapchainState;
    private final VulkanCommandSubmissionStateManager commandSubmissionState;
    private final VulkanDeferredResourceLifetime<DescriptorResource> lifetime;
    private final VulkanStagingTransferManager stagingTransfers;

    VulkanFrameExecutionCoordinator(
        VulkanSwapchainStateManager swapchainState,
        VulkanCommandSubmissionStateManager commandSubmissionState,
        VulkanDeferredResourceLifetime<DescriptorResource> lifetime,
        VulkanStagingTransferManager stagingTransfers
    ) {
        this.swapchainState = Objects.requireNonNull(swapchainState, "swapchainState");
        this.commandSubmissionState = Objects.requireNonNull(commandSubmissionState, "commandSubmissionState");
        this.lifetime = Objects.requireNonNull(lifetime, "lifetime");
        this.stagingTransfers = Objects.requireNonNull(stagingTransfers, "stagingTransfers");
    }

    FrameBeginPlan planFrameBegin(boolean renderPassRecording) {
        if (!swapchainState.hasValidFrameSyncPrimitives()) {
            throw new IllegalStateException("Cannot begin frame: Vulkan swapchain frame sync primitives are unavailable.");
        }
        if (swapchainState.frameInProgress()) {
            throw new IllegalStateException("beginFrame called while a Vulkan frame is already in progress.");
        }
        long pendingImmediateCommandBufferHandle = VK10.VK_NULL_HANDLE;
        if (commandSubmissionState.commandBufferRecording()) {
            if (renderPassRecording) {
                throw new IllegalStateException("beginFrame cannot proceed while a render pass is active.");
            }
            pendingImmediateCommandBufferHandle = commandSubmissionState.primaryCommandBufferHandle();
        }
        int frameSlot = swapchainState.currentFrameSyncIndex();
        return new FrameBeginPlan(
            frameSlot,
            swapchainState.currentFrameFence(),
            swapchainState.currentImageAvailableSemaphore(),
            pendingImmediateCommandBufferHandle
        );
    }

    void completeFrameFenceWait(FrameBeginPlan plan, boolean deviceAvailable, RetirementHooks<DescriptorResource> hooks) {
        requireFrameSlot(plan.frameSlot());
        lifetime.markFenceComplete(plan.frameFence(), deviceAvailable);
        lifetime.clearReservedFrameWorkGeneration(plan.frameSlot());
        retireFrameDescriptorResources(plan.frameSlot(), hooks);
    }

    void skipFrameAndAdvance() {
        swapchainState.skipFrameAndAdvance();
    }

    void markAcquireOutOfDate() {
        swapchainState.markAcquireOutOfDate();
    }

    AcquiredImagePlan planAcquiredImage(int imageIndex) {
        if (imageIndex < 0) {
            throw new IllegalStateException("vkAcquireNextImageKHR returned invalid image index: " + imageIndex);
        }
        if (imageIndex >= swapchainState.imageCount()) {
            throw new IllegalStateException(
                "vkAcquireNextImageKHR returned image index " + imageIndex
                    + " outside tracked swapchain image/view range (images="
                    + swapchainState.imageCount() + ", views=" + swapchainState.imageCount() + ").");
        }
        int frameSlot = swapchainState.currentFrameSyncIndex();
        long frameFence = swapchainState.currentFrameFence();
        return new AcquiredImagePlan(
            frameSlot,
            imageIndex,
            frameFence,
            swapchainState.imageInFlightFence(imageIndex)
        );
    }

    void completeFrameAcquire(AcquiredImagePlan plan) {
        requireFrameSlot(plan.frameSlot());
        swapchainState.beginAcquiredFrame(plan.imageIndex(), plan.frameFence());
        commandSubmissionState.reserveFrameWorkGeneration(lifetime, plan.frameSlot());
    }

    FrameCommandBufferBeginPlan planFrameCommandBufferBegin() {
        int frameSlot = swapchainState.currentFrameSyncIndex();
        VkCommandBuffer commandBuffer = commandSubmissionState.frameCommandBuffer(frameSlot);
        if (commandBuffer == null) {
            throw new IllegalStateException(
                "Frame Vulkan command buffer for sync slot " + frameSlot + " has not been allocated."
            );
        }
        return new FrameCommandBufferBeginPlan(
            frameSlot,
            swapchainState.currentFrameFence(),
            commandSubmissionState.frameCommandPool(frameSlot),
            commandBuffer
        );
    }

    void completeFrameCommandBufferBegin(FrameCommandBufferBeginPlan plan) {
        requireFrameSlot(plan.frameSlot());
        swapchainState.setCurrentFrameCommandBufferRecording(true);
    }

    void failFrameCommandBufferBegin(FrameCommandBufferBeginPlan plan) {
        if (isCurrentFrameSlot(plan.frameSlot())) {
            swapchainState.setCurrentFrameCommandBufferRecording(false);
        }
    }

    FrameSubmitPlan planFrameCommandBufferSubmit() {
        int frameSlot = swapchainState.currentFrameSyncIndex();
        if (!swapchainState.isCurrentFrameCommandBufferRecording()) {
            throw new IllegalStateException(
                "Cannot submit Vulkan frame command buffer for sync slot " + frameSlot + " because it is not recording."
            );
        }
        VkCommandBuffer commandBuffer = commandSubmissionState.frameCommandBuffer(frameSlot);
        if (commandBuffer == null) {
            throw new IllegalStateException(
                "Frame Vulkan command buffer for sync slot " + frameSlot + " has not been allocated."
            );
        }
        return frameSubmitPlan(frameSlot, commandBuffer, true);
    }

    FrameSubmitPlan planFrameSemaphoreBridgeSubmit() {
        return frameSubmitPlan(swapchainState.currentFrameSyncIndex(), null, false);
    }

    void completeFrameSubmit(FrameSubmitPlan plan) {
        requireFrameSlot(plan.frameSlot());
        lifetime.registerSubmittedWork(plan.frameFence(), plan.reservedGeneration());
        if (plan.recordedCommandBuffer()) {
            swapchainState.setCurrentFrameCommandBufferRecording(false);
        }
    }

    void failFrameSubmit(FrameSubmitPlan plan, boolean queueMayOwnWork) {
        requireFrameSlot(plan.frameSlot());
        if (queueMayOwnWork) {
            lifetime.registerSubmittedWork(plan.frameFence(), plan.reservedGeneration());
            if (plan.recordedCommandBuffer()) {
                swapchainState.setCurrentFrameCommandBufferRecording(false);
            }
        }
    }

    PresentPlan planPresent() {
        if (!swapchainState.frameInProgress() || swapchainState.acquiredImageIndex() < 0) {
            throw new IllegalStateException("Cannot present without an acquired Vulkan swapchain image.");
        }
        return new PresentPlan(
            swapchainState.currentFrameSyncIndex(),
            swapchainState.acquiredImageIndex(),
            swapchainState.acquiredRenderFinishedSemaphore()
        );
    }

    void completePresent(PresentPlan plan, PresentResult result) {
        switch (result) {
            case OUT_OF_DATE -> swapchainState.markPresentOutOfDate();
            case SUBOPTIMAL -> swapchainState.markPresentSuboptimal();
            case SUCCESS -> {
            }
        }
        swapchainState.finishFrameAndAdvance();
    }

    void abandonFrame() {
        if (swapchainState.frameInProgress()) {
            swapchainState.finishFrameAndAdvance();
        }
    }

    ImmediateSubmitPlan planImmediateSubmit(long commandBufferHandle) {
        if (!commandSubmissionState.commandBufferRecording()) {
            return null;
        }
        int submitSlot = commandSubmissionState.immediateSubmitSlotForCommandBuffer(commandBufferHandle);
        if (submitSlot != commandSubmissionState.recordingImmediateSubmitSlot() || submitSlot < 0) {
            return null;
        }
        VkCommandBuffer commandBuffer = commandSubmissionState.immediateCommandBuffer(submitSlot);
        if (commandBuffer == null) {
            throw new IllegalStateException("Immediate Vulkan command buffer is unavailable for slot " + submitSlot + ".");
        }
        long submitFence = commandSubmissionState.immediateSubmitFence(submitSlot);
        if (submitFence == VK10.VK_NULL_HANDLE) {
            throw new IllegalStateException("Immediate Vulkan submit fence is unavailable.");
        }
        return new ImmediateSubmitPlan(
            submitSlot,
            commandSubmissionState.immediateCommandPool(submitSlot),
            commandBuffer,
            submitFence,
            commandSubmissionState.reservedImmediateWorkGeneration(lifetime, submitSlot)
        );
    }

    void completeImmediateSubmitQueued(ImmediateSubmitPlan plan) {
        lifetime.registerSubmittedWork(plan.submitFence(), plan.reservedGeneration());
        commandSubmissionState.markImmediateSubmitted(plan.slot());
    }

    void completeImmediateSubmitFence(
        ImmediateSubmitPlan plan,
        boolean deviceAvailable,
        RetirementHooks<DescriptorResource> hooks
    ) {
        completeImmediateSlotWait(plan.slot(), deviceAvailable, hooks);
    }

    void completeImmediateSlotWait(
        int slot,
        boolean deviceAvailable,
        RetirementHooks<DescriptorResource> hooks
    ) {
        long fence = commandSubmissionState.immediateSubmitFence(slot);
        lifetime.markFenceComplete(fence, deviceAvailable);
        lifetime.clearReservedImmediateWorkGeneration(slot);
        commandSubmissionState.markImmediateSlotComplete(slot);
        retireImmediateTransientResources(slot, hooks);
    }

    void failImmediateSubmitBeforeQueue(ImmediateSubmitPlan plan) {
        commandSubmissionState.markImmediateSubmitFailed(plan.slot());
    }

    void advanceImmediateAfterSubmit(ImmediateSubmitPlan plan) {
        commandSubmissionState.advanceImmediateSlotAfterSubmit(plan.slot());
    }

    void completeAllSwapchainFrameFences(boolean deviceAvailable, RetirementHooks<DescriptorResource> hooks) {
        lifetime.markAllSubmittedWorkComplete(deviceAvailable);
        retireAllFrameDescriptorResources(hooks);
    }

    boolean hasPotentiallyPendingGpuWork(boolean renderPassRecording) {
        return lifetime.hasPotentiallyPendingGpuWork(
            renderPassRecording,
            commandSubmissionState.commandBufferRecording(),
            commandSubmissionState.immediateSubmitInFlight(),
            swapchainState.frameInProgress(),
            commandSubmissionState.immediateSubmitSlotsInFlightState(),
            swapchainState.frameCommandBufferRecordingState()
        );
    }

    void enqueueDestroy(boolean deviceAvailable, boolean renderPassRecording, Runnable destroyAction) {
        lifetime.enqueueDestroy(
            deviceAvailable,
            hasPotentiallyPendingGpuWork(renderPassRecording),
            swapchainState.frameInProgress(),
            swapchainState.currentFrameSyncIndex(),
            commandSubmissionState.commandBufferRecording(),
            commandSubmissionState.recordingImmediateSubmitSlot(),
            destroyAction
        );
    }

    void markFenceComplete(long fenceHandle, boolean deviceAvailable) {
        lifetime.markFenceComplete(fenceHandle, deviceAvailable);
    }

    void flushPendingDestroys(boolean deviceAvailable, boolean force) {
        lifetime.flushPendingDestroys(deviceAvailable, force);
    }

    void retireGlobalTransientResources(RetirementHooks<DescriptorResource> hooks) {
        stagingTransfers.retireGlobal(hooks.stagingDestroy());
        lifetime.retireGlobalTransientResources(
            hooks.descriptorRetire(),
            hooks.framebufferDestroy(),
            hooks.renderPassDestroy()
        );
    }

    void retireImmediateTransientResources(int slot, RetirementHooks<DescriptorResource> hooks) {
        stagingTransfers.retireImmediateSlot(slot, hooks.stagingDestroy());
        lifetime.retireImmediateTransientResources(
            slot,
            hooks.descriptorRetire(),
            hooks.framebufferDestroy(),
            hooks.renderPassDestroy()
        );
    }

    void retireFrameDescriptorResources(int frameIndex, RetirementHooks<DescriptorResource> hooks) {
        lifetime.retireFrameDescriptorResources(frameIndex, hooks.descriptorRetire());
    }

    void retireAllFrameDescriptorResources(RetirementHooks<DescriptorResource> hooks) {
        lifetime.retireAllFrameDescriptorResources(hooks.descriptorRetire());
    }

    void cleanupForShutdownOrDeviceLoss(boolean deviceAvailable, RetirementHooks<DescriptorResource> hooks) {
        RetirementHooks<DescriptorResource> effectiveHooks = deviceAvailable
            ? hooks
            : new RetirementHooks<>(record -> {}, resource -> {}, handle -> {}, handle -> {});
        stagingTransfers.cleanupForShutdownOrDeviceLoss(deviceAvailable, hooks.stagingDestroy());
        retireGlobalTransientResources(effectiveHooks);
        for (int slot = 0; slot < commandSubmissionState.immediateSlotCount(); slot++) {
            retireImmediateTransientResources(slot, effectiveHooks);
        }
        retireAllFrameDescriptorResources(effectiveHooks);
        lifetime.markAllSubmittedWorkComplete(deviceAvailable);
    }

    void resetForDeviceLossOrShutdown() {
        swapchainState.resetFrameState();
        commandSubmissionState.clearForDeviceLossOrShutdown();
    }

    void clearFrameCommandBufferRecording(int frameIndex) {
        swapchainState.setFrameCommandBufferRecording(frameIndex, false);
    }

    private FrameSubmitPlan frameSubmitPlan(int frameSlot, VkCommandBuffer commandBuffer, boolean recordedCommandBuffer) {
        if (!swapchainState.frameInProgress() || swapchainState.acquiredImageIndex() < 0) {
            throw new IllegalStateException("Cannot submit a Vulkan frame without an acquired swapchain image.");
        }
        return new FrameSubmitPlan(
            frameSlot,
            swapchainState.acquiredImageIndex(),
            swapchainState.currentFrameFence(),
            swapchainState.currentImageAvailableSemaphore(),
            swapchainState.acquiredRenderFinishedSemaphore(),
            commandBuffer,
            recordedCommandBuffer,
            commandSubmissionState.reservedFrameWorkGeneration(lifetime, frameSlot)
        );
    }

    private void requireFrameSlot(int frameSlot) {
        if (!isCurrentFrameSlot(frameSlot)) {
            throw new IllegalStateException(
                "Frame lifecycle operation targeted sync slot " + frameSlot
                    + " while current slot is " + swapchainState.currentFrameSyncIndex()
            );
        }
    }

    private boolean isCurrentFrameSlot(int frameSlot) {
        return frameSlot == swapchainState.currentFrameSyncIndex();
    }

    record FrameBeginPlan(
        int frameSlot,
        long frameFence,
        long imageAvailableSemaphore,
        long pendingImmediateCommandBufferHandle
    ) {}

    record AcquiredImagePlan(
        int frameSlot,
        int imageIndex,
        long frameFence,
        long imageInFlightFence
    ) {}

    record FrameCommandBufferBeginPlan(
        int frameSlot,
        long frameFence,
        long commandPool,
        VkCommandBuffer commandBuffer
    ) {}

    record FrameSubmitPlan(
        int frameSlot,
        int imageIndex,
        long frameFence,
        long imageAvailableSemaphore,
        long renderFinishedSemaphore,
        VkCommandBuffer commandBuffer,
        boolean recordedCommandBuffer,
        long reservedGeneration
    ) {}

    record PresentPlan(
        int frameSlot,
        int imageIndex,
        long renderFinishedSemaphore
    ) {}

    record ImmediateSubmitPlan(
        int slot,
        long commandPool,
        VkCommandBuffer commandBuffer,
        long submitFence,
        long reservedGeneration
    ) {}

    enum PresentResult {
        SUCCESS,
        OUT_OF_DATE,
        SUBOPTIMAL
    }

    record RetirementHooks<DescriptorResource>(
        Consumer<VulkanStagingTransferManager.StagingBufferRecord> stagingDestroy,
        Consumer<DescriptorResource> descriptorRetire,
        LongConsumer framebufferDestroy,
        LongConsumer renderPassDestroy
    ) {
        RetirementHooks {
            Objects.requireNonNull(stagingDestroy, "stagingDestroy");
            Objects.requireNonNull(descriptorRetire, "descriptorRetire");
            Objects.requireNonNull(framebufferDestroy, "framebufferDestroy");
            Objects.requireNonNull(renderPassDestroy, "renderPassDestroy");
        }
    }
}
