package net.vulkanic.backends.vulkan;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongConsumer;
import java.util.function.Predicate;
import java.util.function.ToLongFunction;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo;
import org.lwjgl.vulkan.VkDescriptorPoolSize;
import org.lwjgl.vulkan.VkDescriptorSetAllocateInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkWriteDescriptorSet;

import static org.lwjgl.system.MemoryStack.stackPush;

/**
 * Backend-internal owner for descriptor pools, descriptor set reuse, and
 * descriptor uniform-buffer recycling.
 *
 * <p>The manager intentionally does not decide what resources a shader should
 * see. VulkanBackend still resolves sampler, image, UBO, texel-buffer, and
 * pipeline layout policy before handing a normalized write plan here.</p>
 */
final class VulkanDescriptorManager<DescriptorUniformBuffer> {
    static final int DEFAULT_MAX_DESCRIPTOR_SETS = 2048;
    static final int DEFAULT_COMBINED_IMAGE_SAMPLER_DESCRIPTORS = 2048;
    static final int DEFAULT_UNIFORM_BUFFER_DESCRIPTORS = 2048;
    static final int DEFAULT_UNIFORM_TEXEL_BUFFER_DESCRIPTORS = 1024;
    static final int DEFAULT_STORAGE_IMAGE_DESCRIPTORS = 1024;

    private final long[] immediateDescriptorPools;
    private final Map<Object, Long> descriptorSamplerCache = new ConcurrentHashMap<>();
    private final Map<DescriptorSetCacheKey, Long> descriptorSetCache = new HashMap<>();
    private final Map<Long, BoundDescriptorSetState> lastBoundDescriptorSetByCommandBuffer = new HashMap<>();
    private final Map<Integer, Deque<DescriptorUniformBuffer>> recycledDescriptorUniformBuffers = new HashMap<>();
    private final int maxRecycledDescriptorUniformBuffers;
    private final long maxRecycledDescriptorUniformBufferBytes;

    private long activeDescriptorPool;
    private int recycledDescriptorUniformBufferCount;
    private long recycledDescriptorUniformBufferAllocationBytes;
    private long descriptorSetCacheHitCount;
    private long descriptorSetCacheStoreCount;
    private long skippedRedundantDescriptorSetBindCount;

    VulkanDescriptorManager(
        int immediateSubmitSlots,
        int maxRecycledDescriptorUniformBuffers,
        long maxRecycledDescriptorUniformBufferBytes
    ) {
        if (immediateSubmitSlots <= 0) {
            throw new IllegalArgumentException("immediateSubmitSlots must be positive");
        }
        if (maxRecycledDescriptorUniformBuffers < 0) {
            throw new IllegalArgumentException("maxRecycledDescriptorUniformBuffers must be non-negative");
        }
        if (maxRecycledDescriptorUniformBufferBytes < 0L) {
            throw new IllegalArgumentException("maxRecycledDescriptorUniformBufferBytes must be non-negative");
        }
        this.immediateDescriptorPools = new long[immediateSubmitSlots];
        this.maxRecycledDescriptorUniformBuffers = maxRecycledDescriptorUniformBuffers;
        this.maxRecycledDescriptorUniformBufferBytes = maxRecycledDescriptorUniformBufferBytes;
    }

    void createDescriptorPools(VkDevice device, VkResultChecker checkVk) {
        Objects.requireNonNull(device, "device");
        Objects.requireNonNull(checkVk, "checkVk");
        try (MemoryStack stack = stackPush()) {
            VkDescriptorPoolSize.Buffer poolSizes = VkDescriptorPoolSize.calloc(4, stack);
            poolSizes.get(0)
                .type(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                .descriptorCount(DEFAULT_COMBINED_IMAGE_SAMPLER_DESCRIPTORS);
            poolSizes.get(1)
                .type(VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                .descriptorCount(DEFAULT_UNIFORM_BUFFER_DESCRIPTORS);
            poolSizes.get(2)
                .type(VK10.VK_DESCRIPTOR_TYPE_UNIFORM_TEXEL_BUFFER)
                .descriptorCount(DEFAULT_UNIFORM_TEXEL_BUFFER_DESCRIPTORS);
            poolSizes.get(3)
                .type(VK10.VK_DESCRIPTOR_TYPE_STORAGE_IMAGE)
                .descriptorCount(DEFAULT_STORAGE_IMAGE_DESCRIPTORS);

            VkDescriptorPoolCreateInfo poolInfo = VkDescriptorPoolCreateInfo.calloc(stack)
                .sType$Default()
                .flags(VK10.VK_DESCRIPTOR_POOL_CREATE_FREE_DESCRIPTOR_SET_BIT)
                .maxSets(DEFAULT_MAX_DESCRIPTOR_SETS)
                .pPoolSizes(poolSizes);

            java.nio.LongBuffer pPool = stack.mallocLong(1);
            for (int slot = 0; slot < immediateDescriptorPools.length; slot++) {
                checkVk.check(
                    "vkCreateDescriptorPool(immediate[" + slot + "])",
                    VK10.vkCreateDescriptorPool(device, poolInfo, null, pPool)
                );
                immediateDescriptorPools[slot] = pPool.get(0);
            }
            activeDescriptorPool = immediateDescriptorPools[0];
        }
    }

    void activateImmediateSlot(int slot) {
        checkImmediateSlot(slot);
        activeDescriptorPool = immediateDescriptorPools[slot];
    }

    long activeDescriptorPool() {
        return activeDescriptorPool;
    }

    long resolveDescriptorSampler(
        Object samplerKey,
        long defaultSampler,
        Function<Object, Long> createSampler,
        LongConsumer destroySampler
    ) {
        if (samplerKey == null) {
            return defaultSampler;
        }
        Objects.requireNonNull(createSampler, "createSampler");
        Objects.requireNonNull(destroySampler, "destroySampler");
        Long cachedSampler = descriptorSamplerCache.get(samplerKey);
        if (cachedSampler != null) {
            return cachedSampler;
        }

        long createdSampler = createSampler.apply(samplerKey);
        Long existingSampler = descriptorSamplerCache.putIfAbsent(samplerKey, createdSampler);
        if (existingSampler != null) {
            destroySampler.accept(createdSampler);
            return existingSampler;
        }
        return createdSampler;
    }

    void destroyDescriptorSamplers(LongConsumer destroySampler) {
        Objects.requireNonNull(destroySampler, "destroySampler");
        if (descriptorSamplerCache.isEmpty()) {
            return;
        }
        new ArrayList<>(descriptorSamplerCache.values()).forEach(samplerHandle -> {
            if (samplerHandle != null && samplerHandle != VK10.VK_NULL_HANDLE) {
                destroySampler.accept(samplerHandle);
            }
        });
        descriptorSamplerCache.clear();
    }

    void resetActiveDescriptorPool(VkDevice device, VkResultChecker checkVk) {
        Objects.requireNonNull(device, "device");
        Objects.requireNonNull(checkVk, "checkVk");
        if (activeDescriptorPool != VK10.VK_NULL_HANDLE) {
            checkVk.check(
                "vkResetDescriptorPool",
                VK10.vkResetDescriptorPool(device, activeDescriptorPool, 0)
            );
        }
        invalidateDescriptorSets();
    }

    void invalidateDescriptorSets() {
        descriptorSetCache.clear();
        lastBoundDescriptorSetByCommandBuffer.clear();
    }

    void clearCommandBufferState(long commandBufferHandle) {
        lastBoundDescriptorSetByCommandBuffer.remove(commandBufferHandle);
    }

    void updateAndBindDescriptorSet(
        VkDevice device,
        long commandBufferHandle,
        VkCommandBuffer activeCommandBuffer,
        long pipelineHandle,
        long pipelineLayoutHandle,
        long descriptorSetLayoutHandle,
        int bindPoint,
        List<? extends DescriptorWriteBinding> bindings,
        DescriptorSetCacheKey cacheKey,
        VkResultChecker checkVk
    ) {
        Objects.requireNonNull(device, "device");
        Objects.requireNonNull(activeCommandBuffer, "activeCommandBuffer");
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(checkVk, "checkVk");
        if (activeDescriptorPool == VK10.VK_NULL_HANDLE) {
            throw new IllegalStateException("Descriptor pool is unavailable for Vulkan descriptor updates");
        }

        if (cacheKey != null) {
            Long cachedDescriptorSetHandle = descriptorSetCache.get(cacheKey);
            if (cachedDescriptorSetHandle != null) {
                descriptorSetCacheHitCount++;
                bindDescriptorSetIfNeeded(
                    commandBufferHandle,
                    activeCommandBuffer,
                    pipelineHandle,
                    pipelineLayoutHandle,
                    cachedDescriptorSetHandle,
                    bindPoint
                );
                return;
            }
        }

        try (MemoryStack stack = stackPush()) {
            VkDescriptorSetAllocateInfo allocInfo = VkDescriptorSetAllocateInfo.calloc(stack)
                .sType$Default()
                .descriptorPool(activeDescriptorPool)
                .pSetLayouts(stack.longs(descriptorSetLayoutHandle));

            java.nio.LongBuffer pDescriptorSet = stack.mallocLong(1);
            checkVk.check(
                "vkAllocateDescriptorSets(bindPipelineResources)",
                VK10.vkAllocateDescriptorSets(device, allocInfo, pDescriptorSet)
            );
            long descriptorSetHandle = pDescriptorSet.get(0);

            VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(bindings.size(), stack);
            for (int i = 0; i < bindings.size(); i++) {
                DescriptorWriteBinding resolvedBinding = bindings.get(i);
                VkWriteDescriptorSet write = writes.get(i)
                    .sType$Default()
                    .dstSet(descriptorSetHandle)
                    .dstBinding(resolvedBinding.bindingIndex())
                    .dstArrayElement(0)
                    .descriptorCount(1)
                    .descriptorType(resolvedBinding.descriptorType());
                resolvedBinding.populateWrite(write, stack);
            }

            VK10.vkUpdateDescriptorSets(device, writes, null);
            if (cacheKey != null) {
                descriptorSetCache.put(cacheKey, descriptorSetHandle);
                descriptorSetCacheStoreCount++;
            }
            bindDescriptorSetIfNeeded(
                commandBufferHandle,
                activeCommandBuffer,
                pipelineHandle,
                pipelineLayoutHandle,
                descriptorSetHandle,
                bindPoint
            );
        }
    }

    DescriptorUniformBuffer takeRecycledUniformBuffer(
        int requestedSize,
        Predicate<DescriptorUniformBuffer> usable,
        ToLongFunction<DescriptorUniformBuffer> allocationSize
    ) {
        Objects.requireNonNull(usable, "usable");
        Objects.requireNonNull(allocationSize, "allocationSize");
        int bucketSize = descriptorUniformBufferBucketSize(requestedSize);
        synchronized (recycledDescriptorUniformBuffers) {
            Deque<DescriptorUniformBuffer> bucket = recycledDescriptorUniformBuffers.get(bucketSize);
            while (bucket != null && !bucket.isEmpty()) {
                DescriptorUniformBuffer candidate = bucket.removeFirst();
                recycledDescriptorUniformBufferCount--;
                recycledDescriptorUniformBufferAllocationBytes = Math.max(
                    0L,
                    recycledDescriptorUniformBufferAllocationBytes - Math.max(0L, allocationSize.applyAsLong(candidate))
                );
                if (usable.test(candidate)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    void recycleUniformBuffer(
        DescriptorUniformBuffer buffer,
        int bufferSize,
        ToLongFunction<DescriptorUniformBuffer> allocationSize,
        Consumer<DescriptorUniformBuffer> close
    ) {
        if (buffer == null) {
            return;
        }
        Objects.requireNonNull(allocationSize, "allocationSize");
        Objects.requireNonNull(close, "close");
        int bucketSize = descriptorUniformBufferBucketSize(bufferSize);
        long allocationSizeBytes = Math.max(0L, allocationSize.applyAsLong(buffer));
        synchronized (recycledDescriptorUniformBuffers) {
            if (recycledDescriptorUniformBufferCount >= maxRecycledDescriptorUniformBuffers
                || recycledDescriptorUniformBufferAllocationBytes + allocationSizeBytes > maxRecycledDescriptorUniformBufferBytes) {
                close.accept(buffer);
                return;
            }
            recycledDescriptorUniformBuffers
                .computeIfAbsent(bucketSize, ignored -> new ArrayDeque<>())
                .addLast(buffer);
            recycledDescriptorUniformBufferCount++;
            recycledDescriptorUniformBufferAllocationBytes += allocationSizeBytes;
        }
    }

    void destroyRecycledUniformBuffers(Consumer<DescriptorUniformBuffer> close) {
        Objects.requireNonNull(close, "close");
        synchronized (recycledDescriptorUniformBuffers) {
            for (Deque<DescriptorUniformBuffer> bucket : recycledDescriptorUniformBuffers.values()) {
                while (!bucket.isEmpty()) {
                    close.accept(bucket.removeFirst());
                }
            }
            recycledDescriptorUniformBuffers.clear();
            recycledDescriptorUniformBufferCount = 0;
            recycledDescriptorUniformBufferAllocationBytes = 0L;
        }
    }

    void destroyDescriptorPools(VkDevice device) {
        if (device == null) {
            clearDescriptorPoolState();
            return;
        }
        for (int slot = 0; slot < immediateDescriptorPools.length; slot++) {
            if (immediateDescriptorPools[slot] != VK10.VK_NULL_HANDLE) {
                VK10.vkDestroyDescriptorPool(device, immediateDescriptorPools[slot], null);
                immediateDescriptorPools[slot] = VK10.VK_NULL_HANDLE;
            }
        }
        clearDescriptorPoolState();
    }

    DescriptorReuseSummary descriptorReuseSummary(long skippedRedundantPipelineBindCount) {
        return new DescriptorReuseSummary(
            descriptorSetCacheHitCount,
            descriptorSetCacheStoreCount,
            skippedRedundantPipelineBindCount,
            skippedRedundantDescriptorSetBindCount
        );
    }

    int descriptorSetCacheSizeForTests() {
        return descriptorSetCache.size();
    }

    void cacheDescriptorSamplerForTests(Object samplerKey, long samplerHandle) {
        descriptorSamplerCache.put(samplerKey, samplerHandle);
    }

    int descriptorSamplerCacheSizeForTests() {
        return descriptorSamplerCache.size();
    }

    void cacheDescriptorSetForTests(DescriptorSetCacheKey key, long descriptorSetHandle) {
        descriptorSetCache.put(key, descriptorSetHandle);
    }

    Long cachedDescriptorSetForTests(DescriptorSetCacheKey key) {
        return descriptorSetCache.get(key);
    }

    void recordBoundDescriptorSetForTests(
        long commandBufferHandle,
        long pipelineHandle,
        long descriptorSetHandle,
        int bindPoint
    ) {
        lastBoundDescriptorSetByCommandBuffer.put(
            commandBufferHandle,
            new BoundDescriptorSetState(pipelineHandle, descriptorSetHandle, bindPoint)
        );
    }

    int lastBoundDescriptorSetCountForTests() {
        return lastBoundDescriptorSetByCommandBuffer.size();
    }

    int recycledUniformBufferCountForTests() {
        return recycledDescriptorUniformBufferCount;
    }

    long recycledUniformBufferAllocationBytesForTests() {
        return recycledDescriptorUniformBufferAllocationBytes;
    }

    long descriptorSetCacheHitCountForTests() {
        return descriptorSetCacheHitCount;
    }

    long descriptorSetCacheStoreCountForTests() {
        return descriptorSetCacheStoreCount;
    }

    int immediateDescriptorPoolCountForTests() {
        return immediateDescriptorPools.length;
    }

    static int descriptorUniformBufferBucketSize(int requestedSize) {
        int size = Math.max(1, requestedSize);
        int bucketSize = 1;
        while (bucketSize < size && bucketSize < (1 << 30)) {
            bucketSize <<= 1;
        }
        return bucketSize;
    }

    private void bindDescriptorSetIfNeeded(
        long commandBufferHandle,
        VkCommandBuffer activeCommandBuffer,
        long pipelineHandle,
        long pipelineLayoutHandle,
        long descriptorSetHandle,
        int bindPoint
    ) {
        BoundDescriptorSetState currentState = lastBoundDescriptorSetByCommandBuffer.get(commandBufferHandle);
        if (currentState != null
            && currentState.pipelineHandle() == pipelineHandle
            && currentState.descriptorSetHandle() == descriptorSetHandle
            && currentState.bindPoint() == bindPoint) {
            skippedRedundantDescriptorSetBindCount++;
            return;
        }

        try (MemoryStack stack = stackPush()) {
            VK10.vkCmdBindDescriptorSets(
                activeCommandBuffer,
                bindPoint,
                pipelineLayoutHandle,
                0,
                stack.longs(descriptorSetHandle),
                null
            );
        }
        lastBoundDescriptorSetByCommandBuffer.put(
            commandBufferHandle,
            new BoundDescriptorSetState(pipelineHandle, descriptorSetHandle, bindPoint)
        );
    }

    private void clearDescriptorPoolState() {
        activeDescriptorPool = VK10.VK_NULL_HANDLE;
        invalidateDescriptorSets();
    }

    private void checkImmediateSlot(int slot) {
        if (slot < 0 || slot >= immediateDescriptorPools.length) {
            throw new IndexOutOfBoundsException("immediate slot " + slot + " outside 0.." + (immediateDescriptorPools.length - 1));
        }
    }

    private record BoundDescriptorSetState(long pipelineHandle, long descriptorSetHandle, int bindPoint) {
    }

    record DescriptorBindingCacheKey(
        int bindingIndex,
        int descriptorType,
        long primaryHandle,
        long secondaryHandle,
        long tertiaryHandle,
        long quaternaryHandle
    ) {
    }

    record DescriptorSetCacheKey(
        long descriptorSetLayoutHandle,
        List<DescriptorBindingCacheKey> bindings
    ) {
        DescriptorSetCacheKey {
            bindings = List.copyOf(bindings);
        }
    }

    interface DescriptorWriteBinding {
        int bindingIndex();

        int descriptorType();

        DescriptorBindingCacheKey cacheKey();

        void populateWrite(VkWriteDescriptorSet write, MemoryStack stack);
    }

    record DescriptorReuseSummary(
        long cacheHits,
        long cacheStores,
        long skippedPipelineBinds,
        long skippedDescriptorSetBinds
    ) {
        boolean shouldLog() {
            return cacheHits > 0L || cacheStores > 0L || skippedPipelineBinds > 0L || skippedDescriptorSetBinds > 0L;
        }
    }

    @FunctionalInterface
    interface VkResultChecker {
        void check(String operation, int result);
    }
}
