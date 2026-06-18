package net.vulkanic.backends.vulkan;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.vulkan.VK10;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class VulkanImageStateTracker {
    private final Map<Integer, VulkanImageState> images = new ConcurrentHashMap<>();

    VulkanImageState registerTexture(
        int textureId,
        long imageHandle,
        int aspectMask,
        int mipLevels,
        int layerCount,
        boolean feedbackLoopCapable,
        int initialLayout
    ) {
        VulkanImageState state = this.images.computeIfAbsent(textureId, VulkanImageState::new);
        state.configure(imageHandle, aspectMask, mipLevels, layerCount, feedbackLoopCapable, initialLayout);
        return state;
    }

    void unregisterTexture(int textureId) {
        this.images.remove(textureId);
    }

    void clearTextureStorage(int textureId) {
        VulkanImageState state = this.images.get(textureId);
        if (state != null) {
            state.clear();
        }
    }

    @Nullable
    VulkanImageState state(int textureId) {
        return this.images.get(textureId);
    }

    int layoutFor(int textureId, int mipLevel, int fallback) {
        VulkanImageState state = this.images.get(textureId);
        if (state == null) {
            return fallback;
        }

        return state.layoutFor(mipLevel);
    }

    void recordLayout(int textureId, int mipLevel, int layout) {
        VulkanImageState state = this.images.get(textureId);
        if (state != null) {
            state.recordLayout(mipLevel, layout);
        }
    }

    List<VulkanImageTransition> planTransitions(int textureId, int baseMipLevel, int levelCount, int targetLayout) {
        VulkanImageState state = this.images.get(textureId);
        if (state == null || state.imageHandle() == VK10.VK_NULL_HANDLE) {
            return List.of();
        }

        int safeBase = Math.max(0, baseMipLevel);
        int safeCount = Math.max(1, levelCount);
        int endExclusive = Math.min(state.mipLevels(), safeBase + safeCount);
        List<VulkanImageTransition> transitions = new ArrayList<>();
        for (int level = safeBase; level < endExclusive; level++) {
            int oldLayout = state.layoutFor(level);
            if (oldLayout == targetLayout) {
                continue;
            }
            transitions.add(new VulkanImageTransition(textureId, level, oldLayout, targetLayout));
        }

        return transitions;
    }

    void reset() {
        this.images.clear();
    }

    record VulkanImageTransition(int textureId, int mipLevel, int oldLayout, int newLayout) {
    }
}
