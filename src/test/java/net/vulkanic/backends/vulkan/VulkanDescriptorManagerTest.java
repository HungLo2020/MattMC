package net.vulkanic.backends.vulkan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.lwjgl.vulkan.VK10;

class VulkanDescriptorManagerTest {
    @Test
    void descriptorSetCacheReusesEquivalentSemanticKeys() {
        VulkanDescriptorManager<FakeUniformBuffer> manager = new VulkanDescriptorManager<>(3, 4, 1024);
        VulkanDescriptorManager.DescriptorSetCacheKey key = cacheKey(17L, 2, 99L);

        manager.cacheDescriptorSetForTests(key, 0xCAFE);

        assertEquals(0xCAFEL, manager.cachedDescriptorSetForTests(cacheKey(17L, 2, 99L)));
        assertEquals(1, manager.descriptorSetCacheSizeForTests());
    }

    @Test
    void descriptorSamplerCacheReusesOpaquePolicyKeysAndDestroysOnShutdown() {
        VulkanDescriptorManager<FakeUniformBuffer> manager = new VulkanDescriptorManager<>(3, 4, 1024);
        List<Long> destroyed = new ArrayList<>();

        long first = manager.resolveDescriptorSampler("sampler-key", 1L, ignored -> 42L, destroyed::add);
        long second = manager.resolveDescriptorSampler("sampler-key", 1L, ignored -> 43L, destroyed::add);
        long fallback = manager.resolveDescriptorSampler(null, 7L, ignored -> 99L, destroyed::add);

        assertEquals(42L, first);
        assertEquals(42L, second);
        assertEquals(7L, fallback);
        assertEquals(1, manager.descriptorSamplerCacheSizeForTests());
        assertTrue(destroyed.isEmpty(), "sequential cache hit should not create or destroy a duplicate sampler");

        manager.destroyDescriptorSamplers(destroyed::add);
        assertEquals(List.of(42L), destroyed);
        assertEquals(0, manager.descriptorSamplerCacheSizeForTests());
    }

    @Test
    void descriptorSetInvalidationClearsCache() {
        VulkanDescriptorManager<FakeUniformBuffer> manager = new VulkanDescriptorManager<>(3, 4, 1024);
        VulkanDescriptorManager.DescriptorSetCacheKey key = cacheKey(17L, 2, 99L);
        manager.cacheDescriptorSetForTests(key, 0xCAFE);

        manager.invalidateDescriptorSets();

        assertNull(manager.cachedDescriptorSetForTests(key));
        assertEquals(0, manager.descriptorSetCacheSizeForTests());
    }

    @Test
    void descriptorPoolConfigurationKeepsFixedImmediateSlotsAndLimits() {
        VulkanDescriptorManager<FakeUniformBuffer> manager = new VulkanDescriptorManager<>(3, 4, 1024);

        assertEquals(3, manager.immediateDescriptorPoolCountForTests());
        assertEquals(2048, VulkanDescriptorManager.DEFAULT_MAX_DESCRIPTOR_SETS);
        assertEquals(2048, VulkanDescriptorManager.DEFAULT_COMBINED_IMAGE_SAMPLER_DESCRIPTORS);
        assertEquals(2048, VulkanDescriptorManager.DEFAULT_UNIFORM_BUFFER_DESCRIPTORS);
        assertEquals(1024, VulkanDescriptorManager.DEFAULT_UNIFORM_TEXEL_BUFFER_DESCRIPTORS);
        assertEquals(1024, VulkanDescriptorManager.DEFAULT_STORAGE_IMAGE_DESCRIPTORS);
        assertThrows(IndexOutOfBoundsException.class, () -> manager.activateImmediateSlot(3));
    }

    @Test
    void recycledUniformBuffersHonorCountAndByteLimits() {
        VulkanDescriptorManager<FakeUniformBuffer> manager = new VulkanDescriptorManager<>(3, 1, 64);
        List<FakeUniformBuffer> closed = new ArrayList<>();
        FakeUniformBuffer retained = new FakeUniformBuffer(32);
        FakeUniformBuffer rejectedByCount = new FakeUniformBuffer(32);

        manager.recycleUniformBuffer(retained, retained.size(), FakeUniformBuffer::allocationSize, closed::add);
        manager.recycleUniformBuffer(rejectedByCount, rejectedByCount.size(), FakeUniformBuffer::allocationSize, closed::add);

        assertEquals(1, manager.recycledUniformBufferCountForTests());
        assertEquals(32, manager.recycledUniformBufferAllocationBytesForTests());
        assertEquals(List.of(rejectedByCount), closed);
        assertSame(
            retained,
            manager.takeRecycledUniformBuffer(32, buffer -> !buffer.closed(), FakeUniformBuffer::allocationSize)
        );
        assertEquals(0, manager.recycledUniformBufferCountForTests());

        VulkanDescriptorManager<FakeUniformBuffer> byteLimited = new VulkanDescriptorManager<>(3, 4, 48);
        FakeUniformBuffer tooLarge = new FakeUniformBuffer(64);
        byteLimited.recycleUniformBuffer(tooLarge, tooLarge.size(), FakeUniformBuffer::allocationSize, closed::add);
        assertTrue(closed.contains(tooLarge));
        assertEquals(0, byteLimited.recycledUniformBufferCountForTests());
    }

    @Test
    void deferredLifetimeRecyclesDescriptorBuffersOnlyAfterFenceCompletion() {
        VulkanDescriptorManager<FakeUniformBuffer> manager = new VulkanDescriptorManager<>(3, 4, 1024);
        VulkanDeferredResourceLifetime<FakeUniformBuffer> lifetime = new VulkanDeferredResourceLifetime<>(2, 3);
        FakeUniformBuffer descriptorBuffer = new FakeUniformBuffer(64);

        long generation = lifetime.reserveFrameWorkGeneration(0);
        lifetime.trackFrameDescriptorResource(0, descriptorBuffer);
        lifetime.registerSubmittedWork(501L, generation);

        lifetime.retireFrameDescriptorResources(
            0,
            buffer -> manager.recycleUniformBuffer(buffer, buffer.size(), FakeUniformBuffer::allocationSize, FakeUniformBuffer::close)
        );
        assertEquals(1, manager.recycledUniformBufferCountForTests());

        FakeUniformBuffer pendingDestroy = new FakeUniformBuffer(64);
        lifetime.enqueueDestroy(true, true, true, 0, false, -1, () -> pendingDestroy.close());
        lifetime.flushPendingDestroys(true, false);
        assertTrue(!pendingDestroy.closed(), "pending destroy should remain alive before the submitted generation completes");

        lifetime.markFenceComplete(501L, true);
        assertTrue(pendingDestroy.closed(), "pending destroy should run after the matching fence completes");
    }

    @Test
    void shutdownAndDeviceLossCleanupClearDescriptorStateSafely() {
        VulkanDescriptorManager<FakeUniformBuffer> manager = new VulkanDescriptorManager<>(3, 4, 1024);
        FakeUniformBuffer buffer = new FakeUniformBuffer(32);
        manager.cacheDescriptorSetForTests(cacheKey(17L, 2, 99L), 0xCAFE);
        manager.recycleUniformBuffer(buffer, buffer.size(), FakeUniformBuffer::allocationSize, FakeUniformBuffer::close);

        manager.destroyRecycledUniformBuffers(FakeUniformBuffer::close);
        manager.destroyDescriptorPools(null);

        assertTrue(buffer.closed());
        assertEquals(0, manager.recycledUniformBufferCountForTests());
        assertEquals(0, manager.descriptorSetCacheSizeForTests());
        assertEquals(VK10.VK_NULL_HANDLE, manager.activeDescriptorPool());
    }

    @Test
    void staleDescriptorCacheIsNotReusedAfterResourceInvalidation() {
        VulkanDescriptorManager<FakeUniformBuffer> manager = new VulkanDescriptorManager<>(3, 4, 1024);
        VulkanDescriptorManager.DescriptorSetCacheKey destroyedResourceKey = cacheKey(17L, 2, 99L);
        manager.cacheDescriptorSetForTests(destroyedResourceKey, 0xCAFE);

        manager.invalidateDescriptorSets();

        assertNull(manager.cachedDescriptorSetForTests(destroyedResourceKey));
    }

    private static VulkanDescriptorManager.DescriptorSetCacheKey cacheKey(long layout, int binding, long resourceHandle) {
        return new VulkanDescriptorManager.DescriptorSetCacheKey(
            layout,
            List.of(new VulkanDescriptorManager.DescriptorBindingCacheKey(
                binding,
                VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER,
                resourceHandle,
                0L,
                16L,
                0L
            ))
        );
    }

    private static final class FakeUniformBuffer {
        private final int size;
        private boolean closed;

        private FakeUniformBuffer(int size) {
            this.size = size;
        }

        int size() {
            return size;
        }

        long allocationSize() {
            return size;
        }

        boolean closed() {
            return closed;
        }

        void close() {
            closed = true;
        }
    }
}
