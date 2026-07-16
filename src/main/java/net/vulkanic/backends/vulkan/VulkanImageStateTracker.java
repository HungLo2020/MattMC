package net.vulkanic.backends.vulkan;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.vulkan.VK10;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
        return layoutFor(textureId, mipLevel, 0, fallback);
    }

    int layoutFor(int textureId, int mipLevel, int layer, int fallback) {
        VulkanImageState state = this.images.get(textureId);
        if (state == null) {
            return fallback;
        }

        return state.layoutFor(mipLevel, layer);
    }

    void recordLayout(int textureId, int mipLevel, int layout) {
        VulkanImageState state = this.images.get(textureId);
        if (state != null) {
            state.recordLayout(mipLevel, layout);
        }
    }

    void recordLayoutRange(int textureId, int baseMipLevel, int levelCount, int baseLayer, int layerCount, int layout) {
        VulkanImageState state = this.images.get(textureId);
        if (state != null) {
            int safeBaseMip = Math.max(0, baseMipLevel);
            int safeLevelCount = Math.max(1, levelCount);
            int endMipExclusive = Math.min(state.mipLevels(), safeBaseMip + safeLevelCount);
            for (int mip = safeBaseMip; mip < endMipExclusive; mip++) {
                state.recordLayout(mip, baseLayer, layerCount, layout);
            }
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
            int layer = 0;
            while (layer < state.layerCount()) {
                int oldLayout = state.layoutFor(level, layer);
                int runStart = layer;
                layer++;
                while (layer < state.layerCount() && state.layoutFor(level, layer) == oldLayout) {
                    layer++;
                }
                if (oldLayout == targetLayout) {
                    continue;
                }
                transitions.add(new VulkanImageTransition(
                    textureId,
                    level,
                    1,
                    runStart,
                    layer - runStart,
                    oldLayout,
                    targetLayout
                ));
            }
        }

        return transitions;
    }

    void recordTransition(VulkanImageTransition transition) {
        Objects.requireNonNull(transition, "transition");
        recordLayoutRange(
            transition.textureId(),
            transition.baseMipLevel(),
            transition.levelCount(),
            transition.baseLayer(),
            transition.layerCount(),
            transition.newLayout()
        );
    }

    void reset() {
        this.images.clear();
    }

    record VulkanImageTransition(
        int textureId,
        int baseMipLevel,
        int levelCount,
        int baseLayer,
        int layerCount,
        int oldLayout,
        int newLayout
    ) {
    }
}
