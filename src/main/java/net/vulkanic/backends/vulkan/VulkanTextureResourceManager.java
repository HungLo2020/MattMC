package net.vulkanic.backends.vulkan;

import net.vulkanic.VulkanicAPI;
import org.lwjgl.vulkan.VK10;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;

final class VulkanTextureResourceManager {
    private final AtomicInteger nextLegacyTextureId = new AtomicInteger(1);
    private final AtomicInteger nextVirtualSamplerId = new AtomicInteger(1);

    final Map<Integer, LegacyTextureObject> legacyTextures = new ConcurrentHashMap<>();
    final Map<Integer, Integer> legacyTexture2DBindingsByUnit = new ConcurrentHashMap<>();
    final Map<Integer, LegacyImageBinding> legacyImageBindingsByUnit = new ConcurrentHashMap<>();
    final Map<Integer, LegacyTexelBufferBinding> legacyTexelBufferBindingsByTextureId = new ConcurrentHashMap<>();
    final Map<Integer, TextureLevelInfo> proxyTexture2DLevels = new ConcurrentHashMap<>();

    final Map<Long, Long> managedImageAllocations = new ConcurrentHashMap<>();
    final Map<Long, Long> managedImageDefaultViews = new ConcurrentHashMap<>();
    final Set<Long> managedExtraImageViews = ConcurrentHashMap.newKeySet();

    final Set<Integer> virtualSamplers = ConcurrentHashMap.newKeySet();
    final Map<Integer, VirtualSamplerState> virtualSamplerStates = new ConcurrentHashMap<>();
    final Map<Integer, Integer> boundSamplerPerUnit = new ConcurrentHashMap<>();

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

    int legacyFallbackSamplerTextureId() {
        return legacyFallbackSamplerTextureId;
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
