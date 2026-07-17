package net.vulkanic.backends.vulkan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

/**
 * Backend-internal lifetime coordinator for Vulkan resources that must be
 * retired only after submitted GPU work is known complete.
 *
 * <p>The manager owns generation bookkeeping, pending-destroy ordering, and
 * transient resource buckets. It does not own Vulkan handles semantically and it
 * never calls Vulkan commands directly; {@link VulkanBackend.NativeSpine}
 * supplies narrow destruction callbacks.</p>
 */
final class VulkanDeferredResourceLifetime<DescriptorResource> {
    private final int frameSlots;
    private final int immediateSubmitSlots;
    private final ConcurrentMap<Long, Long> submittedWorkGenerationByFence = new ConcurrentHashMap<>();
    private final List<PendingDestroy> pendingDestroys = Collections.synchronizedList(new ArrayList<>());
    private final long[] reservedFrameWorkGenerations;
    private final long[] reservedImmediateWorkGenerations;

    private final Set<Long> transientRenderPassHandles = ConcurrentHashMap.newKeySet();
    private final List<Set<Long>> transientRenderPassHandlesByImmediateSlot;
    private final Set<Long> transientFramebufferHandles = ConcurrentHashMap.newKeySet();
    private final List<Set<Long>> transientFramebufferHandlesByImmediateSlot;
    private final List<DescriptorResource> transientDescriptorResources = Collections.synchronizedList(new ArrayList<>());
    private final List<List<DescriptorResource>> transientDescriptorResourcesByImmediateSlot;
    private final List<List<DescriptorResource>> transientFrameDescriptorResources;

    private long submittedWorkGeneration;
    private long completedWorkGeneration;

    VulkanDeferredResourceLifetime(int frameSlots, int immediateSubmitSlots) {
        if (frameSlots <= 0) {
            throw new IllegalArgumentException("frameSlots must be positive");
        }
        if (immediateSubmitSlots <= 0) {
            throw new IllegalArgumentException("immediateSubmitSlots must be positive");
        }
        this.frameSlots = frameSlots;
        this.immediateSubmitSlots = immediateSubmitSlots;
        this.reservedFrameWorkGenerations = new long[frameSlots];
        this.reservedImmediateWorkGenerations = new long[immediateSubmitSlots];
        this.transientRenderPassHandlesByImmediateSlot = createTransientHandleBuckets(immediateSubmitSlots);
        this.transientFramebufferHandlesByImmediateSlot = createTransientHandleBuckets(immediateSubmitSlots);
        this.transientDescriptorResourcesByImmediateSlot = createTransientListBuckets(immediateSubmitSlots);
        this.transientFrameDescriptorResources = createTransientListBuckets(frameSlots);
    }

    boolean hasSubmittedWorkInFlight() {
        return submittedWorkGeneration > completedWorkGeneration;
    }

    boolean hasPotentiallyPendingGpuWork(
        boolean renderPassRecording,
        boolean commandBufferRecording,
        boolean immediateSubmitInFlight,
        boolean frameInProgress,
        boolean[] immediateSubmitSlotsInFlight,
        boolean[] frameCommandBufferRecording
    ) {
        if (hasSubmittedWorkInFlight()) {
            return true;
        }
        if (renderPassRecording || commandBufferRecording || immediateSubmitInFlight || frameInProgress) {
            return true;
        }
        for (boolean inFlight : immediateSubmitSlotsInFlight) {
            if (inFlight) {
                return true;
            }
        }
        for (boolean recording : frameCommandBufferRecording) {
            if (recording) {
                return true;
            }
        }
        return false;
    }

    long reserveWorkGeneration() {
        return ++submittedWorkGeneration;
    }

    long reserveFrameWorkGeneration(int frameIndex) {
        checkFrameIndex(frameIndex);
        long generation = reserveWorkGeneration();
        reservedFrameWorkGenerations[frameIndex] = generation;
        return generation;
    }

    long reservedFrameWorkGeneration(int frameIndex) {
        checkFrameIndex(frameIndex);
        return reservedFrameWorkGenerations[frameIndex];
    }

    void clearReservedFrameWorkGeneration(int frameIndex) {
        checkFrameIndex(frameIndex);
        reservedFrameWorkGenerations[frameIndex] = 0L;
    }

    long reserveImmediateWorkGeneration(int slot) {
        checkImmediateSlot(slot);
        long generation = reserveWorkGeneration();
        reservedImmediateWorkGenerations[slot] = generation;
        return generation;
    }

    long reservedImmediateWorkGeneration(int slot) {
        checkImmediateSlot(slot);
        return reservedImmediateWorkGenerations[slot];
    }

    void clearReservedImmediateWorkGeneration(int slot) {
        checkImmediateSlot(slot);
        reservedImmediateWorkGenerations[slot] = 0L;
    }

    void registerSubmittedWork(long fenceHandle, long generation) {
        if (fenceHandle == 0L) {
            return;
        }
        long submittedGeneration = generation <= 0L ? reserveWorkGeneration() : generation;
        submittedWorkGenerationByFence.put(fenceHandle, submittedGeneration);
    }

    void markFenceComplete(long fenceHandle, boolean deviceAvailable) {
        if (fenceHandle == 0L) {
            return;
        }
        Long generation = submittedWorkGenerationByFence.remove(fenceHandle);
        if (generation != null && generation > completedWorkGeneration) {
            completedWorkGeneration = generation;
        }
        flushPendingDestroys(deviceAvailable, false);
    }

    void markAllSubmittedWorkComplete(boolean deviceAvailable) {
        completedWorkGeneration = submittedWorkGeneration;
        submittedWorkGenerationByFence.clear();
        clearReservedGenerations();
        flushPendingDestroys(deviceAvailable, false);
    }

    void enqueueDestroy(
        boolean deviceAvailable,
        boolean hasPotentiallyPendingGpuWork,
        boolean frameInProgress,
        int currentFrameIndex,
        boolean commandBufferRecording,
        int recordingImmediateSubmitSlot,
        Runnable destroyAction
    ) {
        Objects.requireNonNull(destroyAction, "destroyAction");
        if (!deviceAvailable) {
            return;
        }
        if (!hasPotentiallyPendingGpuWork) {
            destroyAction.run();
            return;
        }
        pendingDestroys.add(new PendingDestroy(
            retireAfterGeneration(frameInProgress, currentFrameIndex, commandBufferRecording, recordingImmediateSubmitSlot),
            destroyAction
        ));
    }

    void flushPendingDestroys(boolean deviceAvailable, boolean force) {
        if (!deviceAvailable) {
            pendingDestroys.clear();
            return;
        }
        if (force) {
            completedWorkGeneration = submittedWorkGeneration;
            submittedWorkGenerationByFence.clear();
        }

        List<PendingDestroy> ready = new ArrayList<>();
        synchronized (pendingDestroys) {
            if (pendingDestroys.isEmpty()) {
                return;
            }
            var iterator = pendingDestroys.iterator();
            while (iterator.hasNext()) {
                PendingDestroy pending = iterator.next();
                if (force || pending.retireAfterGeneration <= completedWorkGeneration) {
                    ready.add(pending);
                    iterator.remove();
                }
            }
        }

        for (PendingDestroy pending : ready) {
            pending.destroyAction.run();
        }
    }

    void trackTransientRenderPassHandle(long renderPassHandle, int activeImmediateSlot) {
        if (renderPassHandle == 0L) {
            return;
        }
        if (isValidImmediateSlot(activeImmediateSlot)) {
            transientRenderPassHandlesByImmediateSlot.get(activeImmediateSlot).add(renderPassHandle);
        } else {
            transientRenderPassHandles.add(renderPassHandle);
        }
    }

    void trackTransientFramebufferHandle(long framebufferHandle, int activeImmediateSlot) {
        if (framebufferHandle == 0L) {
            return;
        }
        if (isValidImmediateSlot(activeImmediateSlot)) {
            transientFramebufferHandlesByImmediateSlot.get(activeImmediateSlot).add(framebufferHandle);
        } else {
            transientFramebufferHandles.add(framebufferHandle);
        }
    }

    void trackTransientDescriptorResource(DescriptorResource descriptorResource, int activeImmediateSlot) {
        if (descriptorResource == null) {
            return;
        }
        if (isValidImmediateSlot(activeImmediateSlot)) {
            transientDescriptorResourcesByImmediateSlot.get(activeImmediateSlot).add(descriptorResource);
        } else {
            transientDescriptorResources.add(descriptorResource);
        }
    }

    void trackFrameDescriptorResource(int frameIndex, DescriptorResource descriptorResource) {
        if (descriptorResource == null) {
            return;
        }
        checkFrameIndex(frameIndex);
        transientFrameDescriptorResources.get(frameIndex).add(descriptorResource);
    }

    void retireGlobalTransientResources(
        Consumer<DescriptorResource> descriptorRetire,
        LongConsumer framebufferDestroy,
        LongConsumer renderPassDestroy
    ) {
        drainList(transientDescriptorResources, descriptorRetire);
        drainHandles(transientFramebufferHandles, framebufferDestroy);
        drainHandles(transientRenderPassHandles, renderPassDestroy);
    }

    void retireImmediateTransientResources(
        int slot,
        Consumer<DescriptorResource> descriptorRetire,
        LongConsumer framebufferDestroy,
        LongConsumer renderPassDestroy
    ) {
        if (!isValidImmediateSlot(slot)) {
            return;
        }
        drainList(transientDescriptorResourcesByImmediateSlot.get(slot), descriptorRetire);
        drainHandles(transientFramebufferHandlesByImmediateSlot.get(slot), framebufferDestroy);
        drainHandles(transientRenderPassHandlesByImmediateSlot.get(slot), renderPassDestroy);
    }

    void retireFrameDescriptorResources(int frameIndex, Consumer<DescriptorResource> descriptorRetire) {
        if (frameIndex < 0 || frameIndex >= transientFrameDescriptorResources.size()) {
            return;
        }
        drainList(transientFrameDescriptorResources.get(frameIndex), descriptorRetire);
    }

    void retireAllFrameDescriptorResources(Consumer<DescriptorResource> descriptorRetire) {
        for (int frameIndex = 0; frameIndex < transientFrameDescriptorResources.size(); frameIndex++) {
            retireFrameDescriptorResources(frameIndex, descriptorRetire);
        }
    }

    int pendingDestroyCountForTests() {
        return pendingDestroys.size();
    }

    long submittedWorkGenerationForTests() {
        return submittedWorkGeneration;
    }

    long completedWorkGenerationForTests() {
        return completedWorkGeneration;
    }

    int transientRenderPassCountForTests() {
        return transientRenderPassHandles.size();
    }

    int transientRenderPassCountForTests(int slot) {
        return isValidImmediateSlot(slot) ? transientRenderPassHandlesByImmediateSlot.get(slot).size() : 0;
    }

    int transientFramebufferCountForTests() {
        return transientFramebufferHandles.size();
    }

    int transientDescriptorCountForTests() {
        return transientDescriptorResources.size();
    }

    int transientFrameDescriptorCountForTests(int frameIndex) {
        return frameIndex >= 0 && frameIndex < transientFrameDescriptorResources.size()
            ? transientFrameDescriptorResources.get(frameIndex).size()
            : 0;
    }

    private long retireAfterGeneration(
        boolean frameInProgress,
        int currentFrameIndex,
        boolean commandBufferRecording,
        int recordingImmediateSubmitSlot
    ) {
        long retireAfterGeneration = submittedWorkGeneration;
        if (frameInProgress) {
            checkFrameIndex(currentFrameIndex);
            retireAfterGeneration = Math.max(retireAfterGeneration, reservedFrameWorkGenerations[currentFrameIndex]);
        }
        if (commandBufferRecording && isValidImmediateSlot(recordingImmediateSubmitSlot)) {
            retireAfterGeneration = Math.max(
                retireAfterGeneration,
                reservedImmediateWorkGenerations[recordingImmediateSubmitSlot]
            );
        }
        return retireAfterGeneration;
    }

    private void clearReservedGenerations() {
        java.util.Arrays.fill(reservedFrameWorkGenerations, 0L);
        java.util.Arrays.fill(reservedImmediateWorkGenerations, 0L);
    }

    private boolean isValidImmediateSlot(int slot) {
        return slot >= 0 && slot < immediateSubmitSlots;
    }

    private void checkFrameIndex(int frameIndex) {
        if (frameIndex < 0 || frameIndex >= frameSlots) {
            throw new IndexOutOfBoundsException("frame index " + frameIndex + " outside 0.." + (frameSlots - 1));
        }
    }

    private void checkImmediateSlot(int slot) {
        if (!isValidImmediateSlot(slot)) {
            throw new IndexOutOfBoundsException("immediate slot " + slot + " outside 0.." + (immediateSubmitSlots - 1));
        }
    }

    private static <T> List<List<T>> createTransientListBuckets(int bucketCount) {
        List<List<T>> buckets = new ArrayList<>(bucketCount);
        for (int i = 0; i < bucketCount; i++) {
            buckets.add(Collections.synchronizedList(new ArrayList<>()));
        }
        return buckets;
    }

    private static List<Set<Long>> createTransientHandleBuckets(int bucketCount) {
        List<Set<Long>> buckets = new ArrayList<>(bucketCount);
        for (int i = 0; i < bucketCount; i++) {
            buckets.add(ConcurrentHashMap.newKeySet());
        }
        return buckets;
    }

    private static <T> void drainList(List<T> resources, Consumer<T> retire) {
        Objects.requireNonNull(retire, "retire");
        synchronized (resources) {
            if (resources.isEmpty()) {
                return;
            }
            new ArrayList<>(resources).forEach(retire);
            resources.clear();
        }
    }

    private static void drainHandles(Set<Long> handles, LongConsumer retire) {
        Objects.requireNonNull(retire, "retire");
        if (handles.isEmpty()) {
            return;
        }
        new ArrayList<>(handles).forEach(handle -> {
            handles.remove(handle);
            retire.accept(handle);
        });
    }

    private record PendingDestroy(long retireAfterGeneration, Runnable destroyAction) {}
}
