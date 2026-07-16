package net.vulkanic.backends.vulkan;

import net.vulkanic.PipelineHandle;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VulkanPipelineLifecycleManagerTest {
    @Test
    void precompiledCacheReusesEntriesAndClearsThem() {
        VulkanPipelineLifecycleManager manager = new VulkanPipelineLifecycleManager();
        CloseableEntry first = manager.computePrecompiled("terrain", ignored -> new CloseableEntry());
        CloseableEntry second = manager.computePrecompiled("terrain", ignored -> new CloseableEntry());

        assertSame(first, second);
        assertEquals(1, manager.precompiledPipelineCount());

        manager.clearCachedPipelines();

        assertTrue(first.closed);
        assertEquals(0, manager.precompiledPipelineCount());
    }

    @Test
    void pipelineCacheReusesValidHandlesAndClosesRacedCreates() {
        VulkanPipelineLifecycleManager manager = new VulkanPipelineLifecycleManager();
        FakePipelineHandle first = new FakePipelineHandle(1);
        FakePipelineHandle raced = new FakePipelineHandle(2);

        PipelineHandle cached = manager.cachePipeline(
            VulkanPipelineLifecycleManager.CacheKind.LEGACY_PROGRAM,
            "program",
            first
        );
        PipelineHandle reused = manager.cachePipeline(
            VulkanPipelineLifecycleManager.CacheKind.LEGACY_PROGRAM,
            "program",
            raced
        );

        assertSame(first, cached);
        assertSame(first, reused);
        assertTrue(raced.closed);
        assertEquals(1, manager.cachedPipelineCount(VulkanPipelineLifecycleManager.CacheKind.LEGACY_PROGRAM));
    }

    @Test
    void invalidCachedPipelineIsRemovedAndCannotBeReused() {
        VulkanPipelineLifecycleManager manager = new VulkanPipelineLifecycleManager();
        FakePipelineHandle stale = new FakePipelineHandle(1);
        manager.cachePipeline(VulkanPipelineLifecycleManager.CacheKind.DESCRIPTOR_VARIANT, "descriptor", stale);
        stale.close();

        assertEquals(null, manager.getCachedPipeline(
            VulkanPipelineLifecycleManager.CacheKind.DESCRIPTOR_VARIANT,
            "descriptor"
        ));
        assertEquals(0, manager.cachedPipelineCount(VulkanPipelineLifecycleManager.CacheKind.DESCRIPTOR_VARIANT));
    }

    @Test
    void invalidatedCachedPipelineIsNotReturned() {
        VulkanPipelineLifecycleManager manager = new VulkanPipelineLifecycleManager();
        FakePipelineHandle handle = new FakePipelineHandle(1);
        manager.cachePipeline(VulkanPipelineLifecycleManager.CacheKind.RENDER_TARGET_VARIANT, "target", handle);

        manager.invalidateCachedPipeline(
            VulkanPipelineLifecycleManager.CacheKind.RENDER_TARGET_VARIANT,
            "target",
            handle
        );

        assertEquals(null, manager.getCachedPipeline(
            VulkanPipelineLifecycleManager.CacheKind.RENDER_TARGET_VARIANT,
            "target"
        ));
    }

    @Test
    void pipelineResourcesRetireThroughDeferredDestroyInVulkanOrder() {
        VulkanPipelineLifecycleManager manager = new VulkanPipelineLifecycleManager();
        List<String> destroyed = new ArrayList<>();
        List<Runnable> deferred = new ArrayList<>();
        manager.registerPipeline(10);
        manager.registerPipelineLayout(20);
        manager.registerDescriptorSetLayout(30);

        manager.retirePipelineResources(
            10,
            20,
            30,
            deferred::add,
            destroyActions(destroyed)
        );

        assertEquals(1, deferred.size());
        assertEquals(0, manager.trackedPipelineCount());
        assertEquals(0, manager.trackedPipelineLayoutCount());
        assertEquals(0, manager.trackedDescriptorSetLayoutCount());
        assertTrue(destroyed.isEmpty());

        deferred.getFirst().run();

        assertEquals(List.of("pipeline:10", "pipelineLayout:20", "descriptorSetLayout:30"), destroyed);
    }

    @Test
    void duplicatePipelineRetirementIsHarmless() {
        VulkanPipelineLifecycleManager manager = new VulkanPipelineLifecycleManager();
        List<Runnable> deferred = new ArrayList<>();
        manager.registerPipeline(10);
        manager.registerPipelineLayout(20);
        manager.registerDescriptorSetLayout(30);

        manager.retirePipelineResources(10, 20, 30, deferred::add, destroyActions(new ArrayList<>()));
        manager.retirePipelineResources(10, 20, 30, deferred::add, destroyActions(new ArrayList<>()));

        assertEquals(1, deferred.size());
    }

    @Test
    void shaderModulesAreTrackedAndDestroyedOnce() {
        VulkanPipelineLifecycleManager manager = new VulkanPipelineLifecycleManager();
        List<Long> destroyed = new ArrayList<>();
        manager.registerShaderModule(44);

        manager.destroyShaderModule(44, destroyed::add);
        manager.destroyShaderModule(44, destroyed::add);

        assertEquals(List.of(44L), destroyed);
        assertEquals(0, manager.trackedShaderModuleCount());
    }

    @Test
    void shutdownDrainsNativeHandlesAndCacheEntries() {
        VulkanPipelineLifecycleManager manager = new VulkanPipelineLifecycleManager();
        List<String> destroyed = new ArrayList<>();
        FakePipelineHandle cached = new FakePipelineHandle(1);
        manager.cachePipeline(VulkanPipelineLifecycleManager.CacheKind.LEGACY_COMPUTE, "compute", cached);
        manager.registerPipeline(10);
        manager.registerPipelineLayout(20);
        manager.registerDescriptorSetLayout(30);
        manager.registerShaderModule(40);

        manager.destroyAllNow(destroyActions(destroyed));

        assertEquals(List.of("pipeline:10", "pipelineLayout:20", "descriptorSetLayout:30", "shaderModule:40"), destroyed);
        assertTrue(cached.closed);
        assertEquals(0, manager.cachedPipelineCount(VulkanPipelineLifecycleManager.CacheKind.LEGACY_COMPUTE));
        assertEquals(0, manager.trackedPipelineCount());
        assertEquals(0, manager.trackedShaderModuleCount());
    }

    @Test
    void deferredLifetimeDestroysPipelineOnlyAfterFenceCompletion() {
        VulkanPipelineLifecycleManager manager = new VulkanPipelineLifecycleManager();
        VulkanDeferredResourceLifetime<Object, Object> lifetime = new VulkanDeferredResourceLifetime<>(2, 2);
        List<String> destroyed = new ArrayList<>();
        manager.registerPipeline(10);
        manager.registerPipelineLayout(20);
        manager.registerDescriptorSetLayout(30);
        long generation = lifetime.reserveFrameWorkGeneration(0);
        lifetime.registerSubmittedWork(777, generation);

        Consumer<Runnable> deferredDestroy = destroy -> lifetime.enqueueDestroy(
            true,
            true,
            true,
            0,
            false,
            -1,
            destroy
        );
        manager.retirePipelineResources(10, 20, 30, deferredDestroy, destroyActions(destroyed));

        assertTrue(destroyed.isEmpty());
        assertEquals(1, lifetime.pendingDestroyCountForTests());

        lifetime.markFenceComplete(777, true);

        assertEquals(List.of("pipeline:10", "pipelineLayout:20", "descriptorSetLayout:30"), destroyed);
        assertEquals(0, lifetime.pendingDestroyCountForTests());
    }

    private static VulkanPipelineLifecycleManager.NativeDestroyActions destroyActions(List<String> destroyed) {
        LongConsumer pipeline = handle -> destroyed.add("pipeline:" + handle);
        LongConsumer pipelineLayout = handle -> destroyed.add("pipelineLayout:" + handle);
        LongConsumer descriptorSetLayout = handle -> destroyed.add("descriptorSetLayout:" + handle);
        LongConsumer shaderModule = handle -> destroyed.add("shaderModule:" + handle);
        return new VulkanPipelineLifecycleManager.NativeDestroyActions(
            pipeline,
            pipelineLayout,
            descriptorSetLayout,
            shaderModule
        );
    }

    private static final class CloseableEntry implements AutoCloseable {
        boolean closed;

        @Override
        public void close() {
            closed = true;
        }
    }

    private static final class FakePipelineHandle implements PipelineHandle {
        private final long handle;
        private boolean closed;

        private FakePipelineHandle(long handle) {
            this.handle = handle;
        }

        @Override
        public boolean isValid() {
            return !closed && handle != 0L;
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
