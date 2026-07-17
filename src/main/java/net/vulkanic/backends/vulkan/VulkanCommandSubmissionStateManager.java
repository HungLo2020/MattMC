package net.vulkanic.backends.vulkan;

import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkCommandBuffer;

import java.util.Arrays;

/**
 * Owns command-buffer, command-pool, fence, and submission-slot bookkeeping.
 * Vulkan commands and submission policy remain in {@link VulkanBackend.NativeSpine}.
 */
final class VulkanCommandSubmissionStateManager {
    private final long[] immediateCommandPools;
    private final long[] frameCommandPools;
    private final VkCommandBuffer[] immediateCommandBuffers;
    private final VkCommandBuffer[] frameCommandBuffers;
    private final long[] immediateSubmitFences;
    private final boolean[] immediateSubmitSlotsInFlight;

    private long currentCommandPool;
    private VkCommandBuffer primaryCommandBuffer;
    private long currentImmediateSubmitFence;
    private int currentImmediateSubmitSlot;
    private int recordingImmediateSubmitSlot = -1;
    private boolean commandBufferRecording;
    private boolean immediateSubmitInFlight;

    VulkanCommandSubmissionStateManager(int immediateSubmitSlots, int maxFramesInFlight) {
        if (immediateSubmitSlots <= 0) {
            throw new IllegalArgumentException("immediateSubmitSlots must be positive.");
        }
        if (maxFramesInFlight <= 0) {
            throw new IllegalArgumentException("maxFramesInFlight must be positive.");
        }
        this.immediateCommandPools = new long[immediateSubmitSlots];
        this.frameCommandPools = new long[maxFramesInFlight];
        this.immediateCommandBuffers = new VkCommandBuffer[immediateSubmitSlots];
        this.frameCommandBuffers = new VkCommandBuffer[maxFramesInFlight];
        this.immediateSubmitFences = new long[immediateSubmitSlots];
        this.immediateSubmitSlotsInFlight = new boolean[immediateSubmitSlots];
    }

    int immediateSlotCount() {
        return immediateCommandPools.length;
    }

    int frameSlotCount() {
        return frameCommandPools.length;
    }

    void installImmediateSlot(int slot, long commandPool, VkCommandBuffer commandBuffer, long submitFence) {
        immediateCommandPools[slot] = commandPool;
        immediateCommandBuffers[slot] = commandBuffer;
        immediateSubmitFences[slot] = submitFence;
    }

    void installImmediateCommandResources(int slot, long commandPool, VkCommandBuffer commandBuffer) {
        immediateCommandPools[slot] = commandPool;
        immediateCommandBuffers[slot] = commandBuffer;
    }

    void installFrameSlot(int slot, long commandPool, VkCommandBuffer commandBuffer) {
        frameCommandPools[slot] = commandPool;
        frameCommandBuffers[slot] = commandBuffer;
    }

    void setImmediateSubmitFence(int slot, long submitFence) {
        immediateSubmitFences[slot] = submitFence;
        if (currentImmediateSubmitSlot == slot) {
            currentImmediateSubmitFence = submitFence;
        }
    }

    void activateImmediateSlot(int slot) {
        currentImmediateSubmitSlot = slot;
        currentCommandPool = immediateCommandPools[slot];
        currentImmediateSubmitFence = immediateSubmitFences[slot];
        primaryCommandBuffer = immediateCommandBuffers[slot];
    }

    int currentImmediateSubmitSlot() {
        return currentImmediateSubmitSlot;
    }

    int recordingImmediateSubmitSlot() {
        return recordingImmediateSubmitSlot;
    }

    long currentCommandPool() {
        return currentCommandPool;
    }

    VkCommandBuffer primaryCommandBuffer() {
        return primaryCommandBuffer;
    }

    long primaryCommandBufferHandle() {
        return primaryCommandBuffer == null ? VK10.VK_NULL_HANDLE : primaryCommandBuffer.address();
    }

    long currentImmediateSubmitFence() {
        return currentImmediateSubmitFence;
    }

    long immediateSubmitFence(int slot) {
        return immediateSubmitFences[slot];
    }

    long immediateCommandPool(int slot) {
        return immediateCommandPools[slot];
    }

    VkCommandBuffer immediateCommandBuffer(int slot) {
        return immediateCommandBuffers[slot];
    }

    long frameCommandPool(int slot) {
        return frameCommandPools[slot];
    }

    VkCommandBuffer frameCommandBuffer(int slot) {
        return frameCommandBuffers[slot];
    }

    boolean commandBufferRecording() {
        return commandBufferRecording;
    }

    void markImmediateRecordingStarted() {
        commandBufferRecording = true;
        recordingImmediateSubmitSlot = currentImmediateSubmitSlot;
    }

    void markImmediateSubmitted(int slot) {
        immediateSubmitSlotsInFlight[slot] = true;
        immediateSubmitInFlight = true;
        commandBufferRecording = false;
        recordingImmediateSubmitSlot = -1;
    }

    void markImmediateSlotComplete(int slot) {
        immediateSubmitSlotsInFlight[slot] = false;
        immediateSubmitInFlight = anyImmediateSubmitSlotInFlight();
    }

    void markImmediateSubmitFailed(int slot) {
        if (recordingImmediateSubmitSlot == slot) {
            commandBufferRecording = false;
            recordingImmediateSubmitSlot = -1;
        }
    }

    boolean immediateSubmitInFlight() {
        return immediateSubmitInFlight;
    }

    boolean isImmediateSubmitSlotInFlight(int slot) {
        return immediateSubmitSlotsInFlight[slot];
    }

    boolean[] immediateSubmitSlotsInFlightState() {
        return immediateSubmitSlotsInFlight;
    }

    boolean anyImmediateSubmitSlotInFlight() {
        for (boolean inFlight : immediateSubmitSlotsInFlight) {
            if (inFlight) {
                return true;
            }
        }
        return false;
    }

    int immediateSubmitSlotForCommandBuffer(long commandBufferHandle) {
        for (int slot = 0; slot < immediateCommandBuffers.length; slot++) {
            VkCommandBuffer commandBuffer = immediateCommandBuffers[slot];
            if (commandBuffer != null && commandBuffer.address() == commandBufferHandle) {
                return slot;
            }
        }
        return -1;
    }

    void advanceImmediateSlotAfterSubmit(int submittedSlot) {
        activateImmediateSlot((submittedSlot + 1) % immediateCommandPools.length);
    }

    long reserveImmediateWorkGeneration(VulkanDeferredResourceLifetime<?, ?> lifetime) {
        return lifetime.reserveImmediateWorkGeneration(currentImmediateSubmitSlot);
    }

    long reserveFrameWorkGeneration(VulkanDeferredResourceLifetime<?, ?> lifetime, int frameSlot) {
        return lifetime.reserveFrameWorkGeneration(frameSlot);
    }

    long reservedImmediateWorkGeneration(VulkanDeferredResourceLifetime<?, ?> lifetime, int slot) {
        return lifetime.reservedImmediateWorkGeneration(slot);
    }

    long reservedFrameWorkGeneration(VulkanDeferredResourceLifetime<?, ?> lifetime, int frameSlot) {
        return lifetime.reservedFrameWorkGeneration(frameSlot);
    }

    void clearImmediateSlot(int slot) {
        clearImmediateCommandResources(slot);
        immediateSubmitFences[slot] = VK10.VK_NULL_HANDLE;
    }

    void clearImmediateCommandResources(int slot) {
        immediateCommandPools[slot] = VK10.VK_NULL_HANDLE;
        immediateCommandBuffers[slot] = null;
        immediateSubmitSlotsInFlight[slot] = false;
        if (currentImmediateSubmitSlot == slot) {
            currentCommandPool = VK10.VK_NULL_HANDLE;
            primaryCommandBuffer = null;
        }
        if (recordingImmediateSubmitSlot == slot) {
            recordingImmediateSubmitSlot = -1;
            commandBufferRecording = false;
        }
        immediateSubmitInFlight = anyImmediateSubmitSlotInFlight();
    }

    void clearFrameSlot(int slot) {
        frameCommandPools[slot] = VK10.VK_NULL_HANDLE;
        frameCommandBuffers[slot] = null;
    }

    void clearForDeviceLossOrShutdown() {
        Arrays.fill(immediateCommandPools, VK10.VK_NULL_HANDLE);
        Arrays.fill(frameCommandPools, VK10.VK_NULL_HANDLE);
        Arrays.fill(immediateCommandBuffers, null);
        Arrays.fill(frameCommandBuffers, null);
        Arrays.fill(immediateSubmitFences, VK10.VK_NULL_HANDLE);
        Arrays.fill(immediateSubmitSlotsInFlight, false);
        currentCommandPool = VK10.VK_NULL_HANDLE;
        primaryCommandBuffer = null;
        currentImmediateSubmitFence = VK10.VK_NULL_HANDLE;
        currentImmediateSubmitSlot = 0;
        recordingImmediateSubmitSlot = -1;
        commandBufferRecording = false;
        immediateSubmitInFlight = false;
    }
}
