package net.vulkanic.backends.vulkan;

import net.vulkanic.PipelineHandle;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongConsumer;

/**
 * Backend-internal owner for Vulkan pipeline caches and native pipeline object
 * lifetime bookkeeping.
 *
 * <p>This class intentionally does not know how to build pipeline state,
 * descriptor layouts, shader modules, or render-pass compatibility. It only
 * owns cache containers and tracks the native handles that must be retired in
 * the correct Vulkan destruction order.</p>
 */
final class VulkanPipelineLifecycleManager {
    enum CacheKind {
        DESCRIPTOR_VARIANT,
        RENDER_TARGET_VARIANT,
        LEGACY_PROGRAM,
        LEGACY_COMPUTE
    }

    private final ConcurrentMap<Object, AutoCloseable> precompiledPipelineCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<Object, PipelineHandle> descriptorPipelineCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<Object, PipelineHandle> renderTargetPipelineCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<Object, PipelineHandle> legacyProgramPipelineCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<Object, PipelineHandle> legacyComputePipelineCache = new ConcurrentHashMap<>();

    private final Set<Long> pipelineHandles = ConcurrentHashMap.newKeySet();
    private final Set<Long> pipelineLayoutHandles = ConcurrentHashMap.newKeySet();
    private final Set<Long> descriptorSetLayoutHandles = ConcurrentHashMap.newKeySet();
    private final Set<Long> shaderModuleHandles = ConcurrentHashMap.newKeySet();

    @SuppressWarnings("unchecked")
    <T extends AutoCloseable> T getPrecompiled(Object key, Class<T> expectedType) {
        AutoCloseable cached = precompiledPipelineCache.get(key);
        if (cached == null || !expectedType.isInstance(cached)) {
            return null;
        }
        return (T) cached;
    }

    @SuppressWarnings("unchecked")
    <T extends AutoCloseable> T computePrecompiled(Object key, Function<Object, T> factory) {
        Objects.requireNonNull(factory, "factory");
        return (T) precompiledPipelineCache.computeIfAbsent(key, factory::apply);
    }

    void clearCachedPipelines() {
        closeAndClear(precompiledPipelineCache);
        closeAndClear(descriptorPipelineCache);
        closeAndClear(renderTargetPipelineCache);
        closeAndClear(legacyProgramPipelineCache);
        closeAndClear(legacyComputePipelineCache);
    }

    PipelineHandle getCachedPipeline(CacheKind kind, Object key) {
        ConcurrentMap<Object, PipelineHandle> cache = cache(kind);
        PipelineHandle cached = cache.get(key);
        if (cached == null) {
            return null;
        }
        if (cached.isValid()) {
            return cached;
        }
        cache.remove(key, cached);
        return null;
    }

    PipelineHandle cachePipeline(CacheKind kind, Object key, PipelineHandle created) {
        Objects.requireNonNull(created, "created");
        ConcurrentMap<Object, PipelineHandle> cache = cache(kind);
        PipelineHandle raced = cache.putIfAbsent(key, created);
        if (raced == null) {
            return created;
        }
        if (raced.isValid()) {
            created.close();
            return raced;
        }
        cache.put(key, created);
        raced.close();
        return created;
    }

    void invalidateCachedPipeline(CacheKind kind, Object key, PipelineHandle expected) {
        cache(kind).remove(key, expected);
    }

    void registerPipeline(long pipelineHandle) {
        if (pipelineHandle != 0L) {
            pipelineHandles.add(pipelineHandle);
        }
    }

    void registerPipelineLayout(long pipelineLayoutHandle) {
        if (pipelineLayoutHandle != 0L) {
            pipelineLayoutHandles.add(pipelineLayoutHandle);
        }
    }

    void registerDescriptorSetLayout(long descriptorSetLayoutHandle) {
        if (descriptorSetLayoutHandle != 0L) {
            descriptorSetLayoutHandles.add(descriptorSetLayoutHandle);
        }
    }

    void registerDescriptorSetLayouts(long[] descriptorSetLayoutHandles) {
        Objects.requireNonNull(descriptorSetLayoutHandles, "descriptorSetLayoutHandles");
        for (long descriptorSetLayoutHandle : descriptorSetLayoutHandles) {
            registerDescriptorSetLayout(descriptorSetLayoutHandle);
        }
    }

    void registerShaderModule(long shaderModuleHandle) {
        if (shaderModuleHandle != 0L) {
            shaderModuleHandles.add(shaderModuleHandle);
        }
    }

    void retirePipelineResources(
        long pipelineHandle,
        long pipelineLayoutHandle,
        long descriptorSetLayoutHandle,
        Consumer<Runnable> deferredDestroy,
        NativeDestroyActions destroyActions
    ) {
        retirePipelineResources(
            pipelineHandle,
            pipelineLayoutHandle,
            new long[]{descriptorSetLayoutHandle},
            deferredDestroy,
            destroyActions
        );
    }

    void retirePipelineResources(
        long pipelineHandle,
        long pipelineLayoutHandle,
        long[] descriptorSetLayoutHandles,
        Consumer<Runnable> deferredDestroy,
        NativeDestroyActions destroyActions
    ) {
        Objects.requireNonNull(deferredDestroy, "deferredDestroy");
        Objects.requireNonNull(destroyActions, "destroyActions");
        Objects.requireNonNull(descriptorSetLayoutHandles, "descriptorSetLayoutHandles");
        boolean pipelineTracked = pipelineHandle != 0L && pipelineHandles.remove(pipelineHandle);
        boolean layoutTracked = pipelineLayoutHandle != 0L && pipelineLayoutHandles.remove(pipelineLayoutHandle);
        List<Long> trackedDescriptorLayouts = new ArrayList<>();
        for (long descriptorSetLayoutHandle : descriptorSetLayoutHandles) {
            if (descriptorSetLayoutHandle != 0L && this.descriptorSetLayoutHandles.remove(descriptorSetLayoutHandle)) {
                trackedDescriptorLayouts.add(descriptorSetLayoutHandle);
            }
        }
        if (!pipelineTracked && !layoutTracked && trackedDescriptorLayouts.isEmpty()) {
            return;
        }
        deferredDestroy.accept(() -> {
            if (pipelineTracked) {
                destroyActions.destroyPipeline().accept(pipelineHandle);
            }
            if (layoutTracked) {
                destroyActions.destroyPipelineLayout().accept(pipelineLayoutHandle);
            }
            for (long descriptorSetLayoutHandle : trackedDescriptorLayouts) {
                destroyActions.destroyDescriptorSetLayout().accept(descriptorSetLayoutHandle);
            }
        });
    }

    void destroyShaderModule(long shaderModuleHandle, LongConsumer destroyShaderModule) {
        Objects.requireNonNull(destroyShaderModule, "destroyShaderModule");
        if (shaderModuleHandle == 0L || !shaderModuleHandles.remove(shaderModuleHandle)) {
            return;
        }
        destroyShaderModule.accept(shaderModuleHandle);
    }

    void destroyAllNow(NativeDestroyActions destroyActions) {
        Objects.requireNonNull(destroyActions, "destroyActions");
        drainHandles(pipelineHandles, destroyActions.destroyPipeline());
        drainHandles(pipelineLayoutHandles, destroyActions.destroyPipelineLayout());
        drainHandles(descriptorSetLayoutHandles, destroyActions.destroyDescriptorSetLayout());
        drainHandles(shaderModuleHandles, destroyActions.destroyShaderModule());
        clearCachedPipelines();
    }

    int cachedPipelineCount(CacheKind kind) {
        return cache(kind).size();
    }

    int precompiledPipelineCount() {
        return precompiledPipelineCache.size();
    }

    int trackedPipelineCount() {
        return pipelineHandles.size();
    }

    int trackedPipelineLayoutCount() {
        return pipelineLayoutHandles.size();
    }

    int trackedDescriptorSetLayoutCount() {
        return descriptorSetLayoutHandles.size();
    }

    int trackedShaderModuleCount() {
        return shaderModuleHandles.size();
    }

    private ConcurrentMap<Object, PipelineHandle> cache(CacheKind kind) {
        return switch (kind) {
            case DESCRIPTOR_VARIANT -> descriptorPipelineCache;
            case RENDER_TARGET_VARIANT -> renderTargetPipelineCache;
            case LEGACY_PROGRAM -> legacyProgramPipelineCache;
            case LEGACY_COMPUTE -> legacyComputePipelineCache;
        };
    }

    private static void closeAndClear(ConcurrentMap<?, ? extends AutoCloseable> cache) {
        if (cache.isEmpty()) {
            return;
        }
        new ArrayList<>(cache.values()).forEach(VulkanPipelineLifecycleManager::closeQuietly);
        cache.clear();
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to close Vulkan pipeline cache entry.", exception);
        }
    }

    private static void drainHandles(Set<Long> handles, LongConsumer destroyHandle) {
        if (handles.isEmpty()) {
            return;
        }
        new ArrayList<>(handles).forEach(handle -> {
            handles.remove(handle);
            if (handle != 0L) {
                destroyHandle.accept(handle);
            }
        });
    }

    record NativeDestroyActions(
        LongConsumer destroyPipeline,
        LongConsumer destroyPipelineLayout,
        LongConsumer destroyDescriptorSetLayout,
        LongConsumer destroyShaderModule
    ) {
        NativeDestroyActions {
            Objects.requireNonNull(destroyPipeline, "destroyPipeline");
            Objects.requireNonNull(destroyPipelineLayout, "destroyPipelineLayout");
            Objects.requireNonNull(destroyDescriptorSetLayout, "destroyDescriptorSetLayout");
            Objects.requireNonNull(destroyShaderModule, "destroyShaderModule");
        }
    }
}
