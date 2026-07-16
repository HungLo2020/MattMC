package net.vulkanic.backends.vulkan;

import org.lwjgl.vulkan.VK10;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Owns swapchain and presentation bookkeeping without issuing Vulkan commands.
 */
final class VulkanSwapchainStateManager {
    private final long[] imageAvailableSemaphores;
    private final long[] frameFences;
    private final boolean[] frameCommandBufferRecording;
    private long[] renderFinishedSemaphoresByImage = new long[0];
    private long[] imagesInFlight = new long[0];
    private int currentFrameSyncIndex;
    private int acquiredImageIndex = -1;
    private boolean frameInProgress;
    private boolean lastAcquireOutOfDate;
    private boolean lastPresentOutOfDate;
    private boolean lastPresentSuboptimal;

    private int imageFormat = VK10.VK_FORMAT_UNDEFINED;
    private int colorSpace = -1;
    private int presentMode = -1;
    private int width;
    private int height;
    private final List<Long> imageHandles = new ArrayList<>();
    private final List<Long> imageViewHandles = new ArrayList<>();
    private final List<Integer> imageLayouts = new ArrayList<>();
    private long presentRenderPass = VK10.VK_NULL_HANDLE;
    private final List<Long> presentFramebufferHandles = new ArrayList<>();

    VulkanSwapchainStateManager(int maxFramesInFlight) {
        if (maxFramesInFlight <= 0) {
            throw new IllegalArgumentException("maxFramesInFlight must be positive.");
        }
        this.imageAvailableSemaphores = new long[maxFramesInFlight];
        this.frameFences = new long[maxFramesInFlight];
        this.frameCommandBufferRecording = new boolean[maxFramesInFlight];
    }

    int maxFramesInFlight() {
        return imageAvailableSemaphores.length;
    }

    int currentFrameSyncIndex() {
        return currentFrameSyncIndex;
    }

    int acquiredImageIndex() {
        return acquiredImageIndex;
    }

    boolean frameInProgress() {
        return frameInProgress;
    }

    boolean isCurrentFrameCommandBufferRecording() {
        return frameCommandBufferRecording[currentFrameSyncIndex];
    }

    void setCurrentFrameCommandBufferRecording(boolean recording) {
        frameCommandBufferRecording[currentFrameSyncIndex] = recording;
    }

    void setFrameCommandBufferRecording(int frameIndex, boolean recording) {
        frameCommandBufferRecording[frameIndex] = recording;
    }

    void clearFrameCommandBufferRecordingState() {
        Arrays.fill(frameCommandBufferRecording, false);
    }

    boolean[] frameCommandBufferRecordingState() {
        return frameCommandBufferRecording;
    }

    void setImageAvailableSemaphore(int frameIndex, long semaphore) {
        imageAvailableSemaphores[frameIndex] = semaphore;
    }

    long imageAvailableSemaphore(int frameIndex) {
        return imageAvailableSemaphores[frameIndex];
    }

    long currentImageAvailableSemaphore() {
        return imageAvailableSemaphores[currentFrameSyncIndex];
    }

    void setFrameFence(int frameIndex, long fence) {
        frameFences[frameIndex] = fence;
    }

    long frameFence(int frameIndex) {
        return frameFences[frameIndex];
    }

    long currentFrameFence() {
        return frameFences[currentFrameSyncIndex];
    }

    boolean hasValidFrameSyncPrimitives() {
        for (int i = 0; i < imageAvailableSemaphores.length; i++) {
            if (imageAvailableSemaphores[i] == VK10.VK_NULL_HANDLE
                || frameFences[i] == VK10.VK_NULL_HANDLE) {
                return false;
            }
        }
        if (imageHandles.isEmpty()) {
            return false;
        }
        if (imagesInFlight.length != imageHandles.size()) {
            return false;
        }
        if (renderFinishedSemaphoresByImage.length != imageHandles.size()) {
            return false;
        }
        for (long semaphore : renderFinishedSemaphoresByImage) {
            if (semaphore == VK10.VK_NULL_HANDLE) {
                return false;
            }
        }
        return true;
    }

    long[] imageAvailableSemaphores() {
        return imageAvailableSemaphores;
    }

    long[] frameFences() {
        return frameFences;
    }

    long[] renderFinishedSemaphoresByImage() {
        return renderFinishedSemaphoresByImage;
    }

    long acquiredRenderFinishedSemaphore() {
        if (acquiredImageIndex < 0 || acquiredImageIndex >= renderFinishedSemaphoresByImage.length) {
            throw new IllegalStateException(
                "Render-finished semaphore is unavailable for acquired swapchain image "
                    + acquiredImageIndex + " (semaphores=" + renderFinishedSemaphoresByImage.length + ")."
            );
        }
        long semaphore = renderFinishedSemaphoresByImage[acquiredImageIndex];
        if (semaphore == VK10.VK_NULL_HANDLE) {
            throw new IllegalStateException(
                "Render-finished semaphore for acquired swapchain image " + acquiredImageIndex + " is unavailable."
            );
        }
        return semaphore;
    }

    void installSwapchain(
        int imageFormat,
        int colorSpace,
        int presentMode,
        int width,
        int height,
        List<Long> imageHandles,
        List<Long> imageViewHandles,
        long[] renderFinishedSemaphores
    ) {
        if (imageHandles.size() != imageViewHandles.size()) {
            throw new IllegalArgumentException("Swapchain image/view counts differ.");
        }
        this.imageFormat = imageFormat;
        this.colorSpace = colorSpace;
        this.presentMode = presentMode;
        this.width = width;
        this.height = height;
        this.imageHandles.clear();
        this.imageHandles.addAll(imageHandles);
        this.imageViewHandles.clear();
        this.imageViewHandles.addAll(imageViewHandles);
        this.imageLayouts.clear();
        for (int i = 0; i < imageHandles.size(); i++) {
            this.imageLayouts.add(VK10.VK_IMAGE_LAYOUT_UNDEFINED);
        }
        this.imagesInFlight = new long[imageHandles.size()];
        this.renderFinishedSemaphoresByImage = renderFinishedSemaphores;
    }

    void clearSwapchainImages() {
        imageHandles.clear();
        imageViewHandles.clear();
        imageLayouts.clear();
        imagesInFlight = new long[0];
        renderFinishedSemaphoresByImage = new long[0];
        imageFormat = VK10.VK_FORMAT_UNDEFINED;
        colorSpace = -1;
        presentMode = -1;
        width = 0;
        height = 0;
    }

    int imageFormat() {
        return imageFormat;
    }

    int colorSpace() {
        return colorSpace;
    }

    int presentMode() {
        return presentMode;
    }

    int imageCount() {
        return imageHandles.size();
    }

    int width() {
        return width;
    }

    int height() {
        return height;
    }

    List<Long> imageViewHandlesSnapshot() {
        return new ArrayList<>(imageViewHandles);
    }

    List<Long> presentFramebufferHandlesSnapshot() {
        return new ArrayList<>(presentFramebufferHandles);
    }

    long imageHandle(int imageIndex) {
        return imageHandles.get(imageIndex);
    }

    long imageViewHandle(int imageIndex) {
        return imageViewHandles.get(imageIndex);
    }

    int imageLayout(int imageIndex) {
        if (imageIndex < 0 || imageIndex >= imageLayouts.size()) {
            return VK10.VK_IMAGE_LAYOUT_UNDEFINED;
        }
        return imageLayouts.get(imageIndex);
    }

    void recordImageLayout(int imageIndex, int layout) {
        if (imageIndex < 0 || imageIndex >= imageLayouts.size()) {
            return;
        }
        imageLayouts.set(imageIndex, layout);
    }

    int imageIndexForViewHandle(long imageViewHandle) {
        if (imageViewHandle == VK10.VK_NULL_HANDLE) {
            return -1;
        }
        return imageViewHandles.indexOf(imageViewHandle);
    }

    long imageInFlightFence(int imageIndex) {
        return imagesInFlight[imageIndex];
    }

    void beginAcquiredFrame(int imageIndex, long frameFence) {
        imagesInFlight[imageIndex] = frameFence;
        acquiredImageIndex = imageIndex;
        frameInProgress = true;
    }

    void markAcquireOutOfDate() {
        lastAcquireOutOfDate = true;
    }

    boolean lastAcquireOutOfDate() {
        return lastAcquireOutOfDate;
    }

    void markPresentOutOfDate() {
        lastPresentOutOfDate = true;
        lastPresentSuboptimal = false;
    }

    void markPresentSuboptimal() {
        lastPresentOutOfDate = false;
        lastPresentSuboptimal = true;
    }

    boolean lastPresentOutOfDate() {
        return lastPresentOutOfDate;
    }

    boolean lastPresentSuboptimal() {
        return lastPresentSuboptimal;
    }

    void clearSwapchainStatusFlags() {
        lastAcquireOutOfDate = false;
        lastPresentOutOfDate = false;
        lastPresentSuboptimal = false;
    }

    void skipFrameAndAdvance() {
        acquiredImageIndex = -1;
        frameInProgress = false;
        advanceFrame();
    }

    void finishFrameAndAdvance() {
        acquiredImageIndex = -1;
        frameInProgress = false;
        advanceFrame();
    }

    void resetFrameState() {
        acquiredImageIndex = -1;
        frameInProgress = false;
        currentFrameSyncIndex = 0;
        clearFrameCommandBufferRecordingState();
    }

    private void advanceFrame() {
        currentFrameSyncIndex = (currentFrameSyncIndex + 1) % imageAvailableSemaphores.length;
    }

    void recordPresentTargets(long renderPass, List<Long> framebufferHandles) {
        this.presentRenderPass = renderPass;
        this.presentFramebufferHandles.clear();
        this.presentFramebufferHandles.addAll(framebufferHandles);
    }

    long presentRenderPass() {
        return presentRenderPass;
    }

    void clearPresentTargets() {
        presentRenderPass = VK10.VK_NULL_HANDLE;
        presentFramebufferHandles.clear();
    }

    long presentFramebufferHandle(int imageIndex) {
        return presentFramebufferHandles.get(imageIndex);
    }

    void clearForDeviceLossOrShutdown() {
        clearSwapchainImages();
        clearPresentTargets();
        Arrays.fill(imageAvailableSemaphores, VK10.VK_NULL_HANDLE);
        Arrays.fill(frameFences, VK10.VK_NULL_HANDLE);
        resetFrameState();
        clearSwapchainStatusFlags();
    }
}
