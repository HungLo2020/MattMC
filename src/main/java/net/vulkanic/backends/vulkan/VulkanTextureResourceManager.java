package net.vulkanic.backends.vulkan;

import net.vulkanic.VulkanicAPI;
import org.lwjgl.vulkan.VK10;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

final class VulkanTextureResourceManager {
    private final AtomicInteger nextLegacyTextureId = new AtomicInteger(1);
    private final AtomicInteger nextVirtualSamplerId = new AtomicInteger(1);

    private final Map<Integer, LegacyTextureObject> legacyTextures = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> legacyTexture2DBindingsByUnit = new ConcurrentHashMap<>();
    private final Map<Integer, LegacyImageBinding> legacyImageBindingsByUnit = new ConcurrentHashMap<>();
    private final Map<Integer, LegacyTexelBufferBinding> legacyTexelBufferBindingsByTextureId = new ConcurrentHashMap<>();
    private final Map<Integer, TextureLevelInfo> proxyTexture2DLevels = new ConcurrentHashMap<>();

    private final Map<Long, Long> managedImageAllocations = new ConcurrentHashMap<>();
    private final Map<Long, Long> managedImageDefaultViews = new ConcurrentHashMap<>();
    private final Set<Long> managedExtraImageViews = ConcurrentHashMap.newKeySet();

    private final Set<Integer> virtualSamplers = ConcurrentHashMap.newKeySet();
    private final Map<Integer, VirtualSamplerState> virtualSamplerStates = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> boundSamplerPerUnit = new ConcurrentHashMap<>();

    private volatile int legacyFallbackSamplerTextureId;

    int createLegacyTexture(int target) {
        int id = nextLegacyTextureId.getAndIncrement();
        legacyTextures.put(id, new LegacyTextureObject(id, target));
        return id;
    }

    int allocateLegacyTextureId() {
        return nextLegacyTextureId.getAndIncrement();
    }

    LegacyTextureObject getLegacyTexture(int textureId) {
        return legacyTextures.get(textureId);
    }

    LegacyTextureStorageSnapshot legacyTextureSnapshot(int textureId) {
        LegacyTextureObject texture = legacyTextures.get(textureId);
        return texture == null ? null : LegacyTextureStorageSnapshot.from(texture);
    }

    LegacyTextureObject getOrCreateLegacyTexture(int textureId, int target) {
        return legacyTextures.computeIfAbsent(textureId, id -> new LegacyTextureObject(id, target));
    }

    LegacyTextureObject putLegacyTextureIfAbsent(int textureId, LegacyTextureObject texture) {
        return legacyTextures.putIfAbsent(textureId, texture);
    }

    LegacyTextureObject removeLegacyTexture(int textureId) {
        return legacyTextures.remove(textureId);
    }

    boolean hasLegacyTexture(int textureId) {
        return legacyTextures.containsKey(textureId);
    }

    boolean hasLegacyTextures() {
        return !legacyTextures.isEmpty();
    }

    int legacyTextureCountForTests() {
        return legacyTextures.size();
    }

    TextureBindingSnapshot textureBindingSnapshot(int unit) {
        int textureId = legacyTexture2DBindingsByUnit.getOrDefault(unit, 0);
        int samplerId = boundSamplerPerUnit.getOrDefault(unit, 0);
        return new TextureBindingSnapshot(unit, textureId, samplerId, samplerStateSnapshot(samplerId));
    }

    int boundLegacyTexture2D(int unit) {
        return legacyTexture2DBindingsByUnit.getOrDefault(unit, 0);
    }

    void bindLegacyTexture2D(int unit, int textureId) {
        if (textureId == 0) {
            legacyTexture2DBindingsByUnit.remove(unit);
        } else {
            legacyTexture2DBindingsByUnit.put(unit, textureId);
        }
    }

    void unbindLegacyTexture2D(int unit) {
        legacyTexture2DBindingsByUnit.remove(unit);
    }

    void unbindLegacyTextureFromAllUnits(int textureId) {
        for (Map.Entry<Integer, Integer> entry : new ArrayList<>(legacyTexture2DBindingsByUnit.entrySet())) {
            if (entry.getValue() == textureId) {
                legacyTexture2DBindingsByUnit.remove(entry.getKey());
            }
        }
    }

    void clearLegacyTexture2DBindings() {
        legacyTexture2DBindingsByUnit.clear();
    }

    int legacyTexture2DBindingCountForTests() {
        return legacyTexture2DBindingsByUnit.size();
    }

    int legacyFallbackSamplerTextureId() {
        return legacyFallbackSamplerTextureId;
    }

    LegacyTextureObject legacyFallbackSamplerTexture() {
        int textureId = legacyFallbackSamplerTextureId;
        return textureId > 0 ? legacyTextures.get(textureId) : null;
    }

    void setLegacyFallbackSamplerTextureId(int textureId) {
        legacyFallbackSamplerTextureId = textureId;
    }

    void clearLegacyFallbackSamplerTextureIdIfMatches(int textureId) {
        if (legacyFallbackSamplerTextureId == textureId) {
            legacyFallbackSamplerTextureId = 0;
        }
    }

    void invalidateVirtualFramebuffersForTexture(int textureId, IntConsumer invalidationHook) {
        invalidationHook.accept(textureId);
    }

    LegacyImageBinding legacyImageBinding(int imageUnit) {
        return legacyImageBindingsByUnit.get(imageUnit);
    }

    void bindLegacyImage(int imageUnit, LegacyImageBinding binding) {
        legacyImageBindingsByUnit.put(imageUnit, Objects.requireNonNull(binding, "binding"));
    }

    void unbindLegacyImage(int imageUnit) {
        legacyImageBindingsByUnit.remove(imageUnit);
    }

    int legacyImageBindingCountForTests() {
        return legacyImageBindingsByUnit.size();
    }

    LegacyTexelBufferBinding legacyTexelBufferBinding(int textureId) {
        return legacyTexelBufferBindingsByTextureId.get(textureId);
    }

    LegacyTexelBufferBinding removeLegacyTexelBufferBinding(int textureId) {
        return legacyTexelBufferBindingsByTextureId.remove(textureId);
    }

    void setLegacyTexelBufferBinding(int textureId, LegacyTexelBufferBinding binding) {
        legacyTexelBufferBindingsByTextureId.put(textureId, Objects.requireNonNull(binding, "binding"));
    }

    void removeTexelBufferBindingsForLegacyBuffer(int legacyBufferId, Consumer<LegacyTexelBufferBinding> removedBindingConsumer) {
        Objects.requireNonNull(removedBindingConsumer, "removedBindingConsumer");
        for (Map.Entry<Integer, LegacyTexelBufferBinding> entry :
            new ArrayList<>(legacyTexelBufferBindingsByTextureId.entrySet())) {
            LegacyTexelBufferBinding texelBinding = entry.getValue();
            if (texelBinding.legacyBufferId == legacyBufferId
                && legacyTexelBufferBindingsByTextureId.remove(entry.getKey(), texelBinding)) {
                removedBindingConsumer.accept(texelBinding);
            }
        }
    }

    List<LegacyTexelBufferBinding> drainLegacyTexelBufferBindings() {
        List<LegacyTexelBufferBinding> bindings = new ArrayList<>(legacyTexelBufferBindingsByTextureId.values());
        legacyTexelBufferBindingsByTextureId.clear();
        return bindings;
    }

    int legacyTexelBufferBindingCountForTests() {
        return legacyTexelBufferBindingsByTextureId.size();
    }

    TextureLevelInfo proxyTexture2DLevel(int level) {
        return proxyTexture2DLevels.get(level);
    }

    void setProxyTexture2DLevel(int level, TextureLevelInfo info) {
        proxyTexture2DLevels.put(level, Objects.requireNonNull(info, "info"));
    }

    void removeProxyTexture2DLevel(int level) {
        proxyTexture2DLevels.remove(level);
    }

    void clearProxyTexture2DLevels() {
        proxyTexture2DLevels.clear();
    }

    int proxyTexture2DLevelCountForTests() {
        return proxyTexture2DLevels.size();
    }

    void clearLegacyTextureStorage(LegacyTextureObject texture) {
        texture.imageHandle = VK10.VK_NULL_HANDLE;
        texture.memoryHandle = VK10.VK_NULL_HANDLE;
        texture.defaultViewHandle = VK10.VK_NULL_HANDLE;
        texture.imageUsageFlags = 0;
        texture.feedbackLoopCapable = false;
        texture.width = 0;
        texture.height = 0;
        texture.depth = 1;
        texture.mipLevels = 1;
        texture.levels.clear();
    }

    void registerManagedTexture(long imageHandle, long memoryHandle, long defaultViewHandle) {
        managedImageAllocations.put(imageHandle, memoryHandle);
        managedImageDefaultViews.put(imageHandle, defaultViewHandle);
    }

    ManagedImageSnapshot managedImageSnapshot(long imageHandle) {
        Long memoryHandle = managedImageAllocations.get(imageHandle);
        Long defaultViewHandle = managedImageDefaultViews.get(imageHandle);
        return memoryHandle == null
            ? null
            : new ManagedImageSnapshot(imageHandle, memoryHandle, defaultViewHandle != null ? defaultViewHandle : VK10.VK_NULL_HANDLE);
    }

    void unregisterManagedTexture(long imageHandle) {
        managedImageAllocations.remove(imageHandle);
        managedImageDefaultViews.remove(imageHandle);
    }

    void trackManagedImageView(long viewHandle) {
        managedExtraImageViews.add(viewHandle);
    }

    boolean untrackManagedImageView(long viewHandle) {
        return managedExtraImageViews.remove(viewHandle);
    }

    boolean isManagedExtraImageViewTracked(long viewHandle) {
        return managedExtraImageViews.contains(viewHandle);
    }

    List<Long> drainManagedExtraImageViews() {
        List<Long> views = new ArrayList<>(managedExtraImageViews);
        managedExtraImageViews.clear();
        return views;
    }

    List<ManagedImageSnapshot> drainManagedImages() {
        List<ManagedImageSnapshot> images = new ArrayList<>();
        for (Map.Entry<Long, Long> entry : new ArrayList<>(managedImageAllocations.entrySet())) {
            long imageHandle = entry.getKey();
            Long defaultView = managedImageDefaultViews.get(imageHandle);
            images.add(new ManagedImageSnapshot(
                imageHandle,
                entry.getValue(),
                defaultView != null ? defaultView : VK10.VK_NULL_HANDLE
            ));
        }
        managedImageAllocations.clear();
        managedImageDefaultViews.clear();
        return images;
    }

    int managedImageCountForTests() {
        return managedImageAllocations.size();
    }

    int managedExtraImageViewCountForTests() {
        return managedExtraImageViews.size();
    }

    List<LegacyTextureObject> drainLegacyTextures() {
        List<LegacyTextureObject> textures = new ArrayList<>(legacyTextures.values());
        legacyTextures.clear();
        return textures;
    }

    int createSampler() {
        int id = nextVirtualSamplerId.getAndIncrement();
        virtualSamplers.add(id);
        virtualSamplerStates.put(id, new VirtualSamplerState());
        return id;
    }

    void deleteSampler(int sampler) {
        virtualSamplers.remove(sampler);
        virtualSamplerStates.remove(sampler);
        boundSamplerPerUnit.values().removeIf(bound -> bound.equals(sampler));
    }

    void bindSampler(int unit, int sampler) {
        if (sampler == 0) {
            boundSamplerPerUnit.remove(unit);
        } else {
            boundSamplerPerUnit.put(unit, sampler);
        }
    }

    int boundSampler(int unit) {
        return boundSamplerPerUnit.getOrDefault(unit, 0);
    }

    VirtualSamplerState samplerState(int sampler) {
        return virtualSamplerStates.get(sampler);
    }

    VirtualSamplerState samplerStateForBoundUnit(int unit) {
        int sampler = boundSampler(unit);
        return sampler == 0 ? null : samplerState(sampler);
    }

    VirtualSamplerStateSnapshot samplerStateSnapshot(int sampler) {
        VirtualSamplerState state = virtualSamplerStates.get(sampler);
        return state == null ? null : state.snapshot();
    }

    VirtualSamplerStateSnapshot samplerStateSnapshotForBoundUnit(int unit) {
        int sampler = boundSampler(unit);
        return sampler == 0 ? null : samplerStateSnapshot(sampler);
    }

    void setSamplerParameteri(int sampler, int pname, int param) {
        VirtualSamplerState state = virtualSamplerStates.get(sampler);
        if (state != null) {
            state.setParameteri(pname, param);
        }
    }

    void setSamplerParameterf(int sampler, int pname, float param) {
        VirtualSamplerState state = virtualSamplerStates.get(sampler);
        if (state != null) {
            state.setParameterf(pname, param);
        }
    }

    boolean hasSampler(int sampler) {
        return virtualSamplers.contains(sampler);
    }

    int virtualSamplerCountForTests() {
        return virtualSamplers.size();
    }

    int boundSamplerCountForTests() {
        return boundSamplerPerUnit.size();
    }

    void clearAll() {
        legacyTextures.clear();
        legacyTexture2DBindingsByUnit.clear();
        legacyImageBindingsByUnit.clear();
        legacyTexelBufferBindingsByTextureId.clear();
        proxyTexture2DLevels.clear();
        managedImageAllocations.clear();
        managedImageDefaultViews.clear();
        managedExtraImageViews.clear();
        virtualSamplers.clear();
        virtualSamplerStates.clear();
        boundSamplerPerUnit.clear();
        legacyFallbackSamplerTextureId = 0;
    }
}

record TextureBindingSnapshot(
    int unit,
    int texture2D,
    int sampler,
    VirtualSamplerStateSnapshot samplerState
) {
}

record VirtualSamplerStateSnapshot(
    int minFilter,
    int magFilter,
    int wrapS,
    int wrapT,
    int wrapR,
    int compareMode,
    int compareFunc,
    float maxLod
) {
    int effectiveMaxLod(int textureViewMipCount) {
        int textureMaxLod = Math.max(0, textureViewMipCount - 1);
        int samplerMaxLod = Math.max(0, (int) Math.floor(maxLod));
        return Math.min(textureMaxLod, samplerMaxLod);
    }
}

record ManagedImageSnapshot(
    long imageHandle,
    long memoryHandle,
    long defaultViewHandle
) {
}

record LegacyTextureStorageSnapshot(
    int id,
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
) {
    LegacyTextureStorageSnapshot {
        integerParameters = Map.copyOf(integerParameters);
        levels = Map.copyOf(levels);
    }

    static LegacyTextureStorageSnapshot from(LegacyTextureObject texture) {
        return new LegacyTextureStorageSnapshot(
            texture.id,
            texture.target,
            texture.imageHandle,
            texture.memoryHandle,
            texture.defaultViewHandle,
            texture.vkFormat,
            texture.aspectMask,
            texture.imageUsageFlags,
            texture.feedbackLoopCapable,
            texture.pixelBytes,
            texture.mipLevels,
            texture.width,
            texture.height,
            texture.depth,
            texture.sourceFormat,
            texture.sourceType,
            texture.integerParameters,
            texture.levels
        );
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

final class VirtualSamplerState {
    volatile int minFilter = VulkanicAPI.GL_NEAREST_MIPMAP_LINEAR;
    volatile int magFilter = VulkanicAPI.GL_LINEAR;
    volatile int wrapS = VulkanicAPI.GL_REPEAT;
    volatile int wrapT = VulkanicAPI.GL_REPEAT;
    volatile int wrapR = VulkanicAPI.GL_REPEAT;
    volatile int compareMode = 0;
    volatile int compareFunc = VulkanicAPI.GL_LEQUAL;
    volatile float maxLod = 1000.0f;

    void setParameteri(int pname, int param) {
        switch (pname) {
            case VulkanicAPI.GL_TEXTURE_MIN_FILTER -> minFilter = param;
            case VulkanicAPI.GL_TEXTURE_MAG_FILTER -> magFilter = param;
            case VulkanicAPI.GL_TEXTURE_WRAP_S -> wrapS = param;
            case VulkanicAPI.GL_TEXTURE_WRAP_T -> wrapT = param;
            case VulkanicAPI.GL_TEXTURE_WRAP_R -> wrapR = param;
            case VulkanicAPI.GL_TEXTURE_COMPARE_MODE -> compareMode = param;
            case 0x884D -> compareFunc = param; // GL_TEXTURE_COMPARE_FUNC
            case VulkanicAPI.GL_TEXTURE_MAX_LOD -> maxLod = param;
            default -> {
                // Preserve compatibility behavior for currently-unused sampler params.
            }
        }
    }

    void setParameterf(int pname, float param) {
        if (pname == VulkanicAPI.GL_TEXTURE_MAX_LOD) {
            maxLod = param;
            return;
        }

        setParameteri(pname, Math.round(param));
    }

    int effectiveMaxLod(int textureViewMipCount) {
        int textureMaxLod = Math.max(0, textureViewMipCount - 1);
        int samplerMaxLod = Math.max(0, (int) Math.floor(maxLod));
        return Math.min(textureMaxLod, samplerMaxLod);
    }

    VirtualSamplerStateSnapshot snapshot() {
        return new VirtualSamplerStateSnapshot(
            minFilter,
            magFilter,
            wrapS,
            wrapT,
            wrapR,
            compareMode,
            compareFunc,
            maxLod
        );
    }
}

final class TextureLevelInfo {
    final int width;
    final int height;
    final int depth;
    final int internalFormat;

    TextureLevelInfo(int width, int height, int internalFormat) {
        this(width, height, 1, internalFormat);
    }

    TextureLevelInfo(int width, int height, int depth, int internalFormat) {
        this.width = width;
        this.height = height;
        this.depth = depth;
        this.internalFormat = internalFormat;
    }
}

record LegacySampledDepthViewKey(int baseMipLevel, int mipLevelCount) {
}

record LegacyImageBinding(
    int imageUnit,
    int texture,
    int level,
    boolean layered,
    int layer,
    int access,
    int format
) {
}

final class LegacyTextureObject {
    final int id;
    volatile int target;
    final Map<Integer, Integer> integerParameters = new ConcurrentHashMap<>();
    final Map<Integer, TextureLevelInfo> levels = new ConcurrentHashMap<>();
    final Set<Long> managedViewHandles = ConcurrentHashMap.newKeySet();
    final Map<LegacySampledDepthViewKey, Long> sampledDepthViewHandles = new ConcurrentHashMap<>();

    volatile long imageHandle;
    volatile long memoryHandle;
    volatile long defaultViewHandle;
    volatile int vkFormat = VK10.VK_FORMAT_UNDEFINED;
    volatile int aspectMask = VK10.VK_IMAGE_ASPECT_COLOR_BIT;
    volatile int imageUsageFlags;
    volatile boolean feedbackLoopCapable;
    volatile int pixelBytes;
    volatile int mipLevels = 1;
    volatile int width;
    volatile int height;
    volatile int depth = 1;
    volatile int sourceFormat;
    volatile int sourceType;

    LegacyTextureObject(int id, int target) {
        this.id = id;
        this.target = target;
    }

}

final class LegacyTexelBufferBinding {
    final int internalFormat;
    final int legacyBufferId;
    final long vkBufferViewHandle;

    LegacyTexelBufferBinding(int internalFormat, int legacyBufferId, long vkBufferViewHandle) {
        this.internalFormat = internalFormat;
        this.legacyBufferId = legacyBufferId;
        this.vkBufferViewHandle = vkBufferViewHandle;
    }
}
