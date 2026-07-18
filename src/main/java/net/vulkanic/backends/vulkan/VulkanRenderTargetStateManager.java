package net.vulkanic.backends.vulkan;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * Backend-internal owner for active Vulkan render-target/attachment state.
 *
 * <p>The manager owns recorded state that results from policy decisions made by
 * {@link VulkanBackend.NativeSpine}. It does not decide layouts, load/store
 * behavior, feedback-loop rules, synchronization, or render-pass construction.</p>
 */
final class VulkanRenderTargetStateManager<ColorAttachment, DepthAttachment> {
    private final List<ColorAttachment> activeColorAttachments = new ArrayList<>();
    private final List<Integer> activeColorFinalLayouts = new ArrayList<>();
    @Nullable
    private DepthAttachment activeDepthAttachment;
    private int activeDepthFinalLayout;
    @Nullable
    private VulkanRenderPassCompatibilityKey activeCompatibilityKey;
    private int activeWidth;
    private int activeHeight;
    private boolean activeTargetsSwapchain;
    private int activeSwapchainImageIndex = -1;

    void beginPass(
        VulkanRenderPassCompatibilityKey compatibilityKey,
        int width,
        int height,
        boolean targetsSwapchain,
        int swapchainImageIndex,
        List<ColorAttachment> colorAttachments,
        List<Integer> colorFinalLayouts,
        @Nullable DepthAttachment depthAttachment,
        int depthFinalLayout
    ) {
        Objects.requireNonNull(compatibilityKey, "compatibilityKey");
        Objects.requireNonNull(colorAttachments, "colorAttachments");
        Objects.requireNonNull(colorFinalLayouts, "colorFinalLayouts");
        if (colorAttachments.size() != colorFinalLayouts.size()) {
            throw new IllegalArgumentException("Color attachment/final-layout counts must match");
        }

        activeCompatibilityKey = compatibilityKey;
        activeWidth = width;
        activeHeight = height;
        activeTargetsSwapchain = targetsSwapchain;
        activeSwapchainImageIndex = targetsSwapchain ? swapchainImageIndex : -1;
        activeColorAttachments.clear();
        activeColorAttachments.addAll(colorAttachments);
        activeColorFinalLayouts.clear();
        activeColorFinalLayouts.addAll(colorFinalLayouts);
        activeDepthAttachment = depthAttachment;
        activeDepthFinalLayout = depthFinalLayout;
    }

    void resetActivePass() {
        activeCompatibilityKey = null;
        activeWidth = 0;
        activeHeight = 0;
        activeTargetsSwapchain = false;
        activeSwapchainImageIndex = -1;
        activeColorAttachments.clear();
        activeColorFinalLayouts.clear();
        activeDepthAttachment = null;
        activeDepthFinalLayout = 0;
    }

    @Nullable
    VulkanRenderPassCompatibilityKey activeCompatibilityKey() {
        return activeCompatibilityKey;
    }

    int activeWidth() {
        return activeWidth;
    }

    int activeHeight() {
        return activeHeight;
    }

    boolean activeTargetsSwapchain() {
        return activeTargetsSwapchain;
    }

    int activeSwapchainImageIndex() {
        return activeSwapchainImageIndex;
    }

    int activeColorAttachmentCount() {
        if (!activeColorAttachments.isEmpty()) {
            return activeColorAttachments.size();
        }
        return activeTargetsSwapchain ? 1 : 0;
    }

    boolean hasActiveDepthAttachment() {
        return activeDepthAttachment != null;
    }

    boolean isActiveAttachment(Object attachment) {
        return activeColorAttachments.contains(attachment) || attachment == activeDepthAttachment;
    }

    @Nullable
    DepthAttachment activeDepthAttachment() {
        return activeDepthAttachment;
    }

    int activeDepthFinalLayout() {
        return activeDepthFinalLayout;
    }

    void forEachActiveColorAttachment(BiConsumer<ColorAttachment, Integer> consumer) {
        Objects.requireNonNull(consumer, "consumer");
        for (int index = 0; index < activeColorAttachments.size(); index++) {
            consumer.accept(activeColorAttachments.get(index), activeColorFinalLayouts.get(index));
        }
    }

}
