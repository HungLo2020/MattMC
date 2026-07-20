package net.vulkanic.backends.vulkan;

import net.vulkanic.VulkanicPassResourceModel;
import net.vulkanic.VulkanicPassResourcePlanner;
import net.vulkanic.VulkanicResourceBarriers;
import net.vulkanic.VulkanicResourceUsage;
import org.lwjgl.vulkan.VK10;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Vulkan-side lowering of the backend-neutral {@link VulkanicPassResourceModel}.
 *
 * <p>This class is intentionally policy-only. It consumes immutable GAL resource
 * plans, validates that they are internally consistent, and derives the Vulkan
 * layout/barrier intent using the existing synchronization planner. It does not
 * look up native resources, allocate structs, record commands, submit work, or
 * publish tracked state.</p>
 */
final class VulkanResourceUsageExecutionPlanner {
    private VulkanResourceUsageExecutionPlanner() {
    }

    static ExecutionPlan plan(VulkanicPassResourceModel.PassExecutionPlan resourcePlan) {
        Objects.requireNonNull(resourcePlan, "resourcePlan");
        VulkanicPassResourceModel.PassExecutionPlan canonical =
            VulkanicPassResourcePlanner.plan(resourcePlan.request());
        if (!canonical.orderedUses().equals(resourcePlan.orderedUses())
            || !canonical.finalResourceUsages().equals(resourcePlan.finalResourceUsages())) {
            throw new IllegalArgumentException(
                "GAL resource execution plan is not canonical for pass " + resourcePlan.request().label()
            );
        }

        List<ResourceUsageIntent> uses = new ArrayList<>(resourcePlan.orderedUses().size());
        for (VulkanicPassResourceModel.ResourceUse use : resourcePlan.orderedUses()) {
            uses.add(new ResourceUsageIntent(
                use.resource().stableKey(),
                use.resource().logicalName(),
                use.kind(),
                use.access(),
                use.usage(),
                use.subresource(),
                use.role(),
                use.feedbackLoop(),
                use.order()
            ));
        }
        return new ExecutionPlan(
            resourcePlan.request().kind(),
            resourcePlan.request().label(),
            uses,
            resourcePlan.finalResourceUsages()
        );
    }

    static Optional<VulkanSynchronizationPlanner.ImageBarrierPlan> planImageTransitionForUse(
        int aspectMask,
        int oldLayout,
        VulkanicPassResourceModel.ResourceUse use,
        boolean depth,
        boolean feedbackLoopCapable,
        int inferredLayout
    ) {
        return VulkanSynchronizationPlanner.planImageLayoutTransitionForUse(
            aspectMask,
            oldLayout,
            use,
            depth,
            feedbackLoopCapable,
            inferredLayout
        );
    }

    static Optional<VulkanSynchronizationPlanner.ImageBarrierPlan> planExplicitImageTransition(
        int aspectMask,
        int oldLayout,
        int newLayout,
        int baseMipLevel,
        int levelCount,
        int baseLayer,
        int layerCount
    ) {
        VulkanicPassResourceModel.ResourceUse semanticUse = imageTransitionUse(
            aspectMask,
            oldLayout,
            newLayout,
            baseMipLevel,
            levelCount,
            baseLayer,
            layerCount
        );
        return planImageTransitionForUse(
            aspectMask,
            oldLayout,
            semanticUse,
            (aspectMask & (VK10.VK_IMAGE_ASPECT_DEPTH_BIT | VK10.VK_IMAGE_ASPECT_STENCIL_BIT)) != 0,
            newLayout == org.lwjgl.vulkan.EXTAttachmentFeedbackLoopLayout.VK_IMAGE_LAYOUT_ATTACHMENT_FEEDBACK_LOOP_OPTIMAL_EXT,
            newLayout
        );
    }

    static Optional<VulkanSynchronizationPlanner.BufferBarrierPlan> planBufferTransferWriteVisibility(
        long offset,
        long size
    ) {
        if (size <= 0L || offset > Integer.MAX_VALUE || size > Integer.MAX_VALUE) {
            return VulkanSynchronizationPlanner.planBufferTransferWriteVisibility(offset, size);
        }
        VulkanicPassResourceModel.ResourceUse write = VulkanicPassResourceModel.ResourceUse.of(
            VulkanicPassResourceModel.ResourceIdentity.of(
                "buffer-transfer-write",
                VulkanicPassResourceModel.ResourceKind.TRANSFER_DESTINATION,
                "buffer-transfer-write"
            ),
            VulkanicPassResourceModel.Access.WRITE,
            VulkanicPassResourceModel.Subresource.bufferRange(offset, size),
            VulkanicResourceUsage.TRANSFER_DST,
            "buffer-transfer-write",
            false,
            0
        );
        VulkanicPassResourceModel.ResourceUse read = VulkanicPassResourceModel.ResourceUse.of(
            VulkanicPassResourceModel.ResourceIdentity.of(
                "buffer-transfer-write",
                VulkanicPassResourceModel.ResourceKind.TRANSFER_DESTINATION,
                "buffer-transfer-write"
            ),
            VulkanicPassResourceModel.Access.READ,
            VulkanicPassResourceModel.Subresource.bufferRange(offset, size),
            VulkanicResourceUsage.INFERRED,
            "buffer-visible-after-transfer",
            false,
            1
        );
        return VulkanSynchronizationPlanner.planBufferVisibilityForUse(write, read);
    }

    static VulkanSynchronizationPlanner.MemoryBarrierPlan planResourceBarrier(
        VulkanicResourceBarriers barriers,
        boolean renderPassRecording
    ) {
        return VulkanSynchronizationPlanner.planResourceBarrier(barriers, renderPassRecording);
    }

    static VulkanSynchronizationPlanner.MemoryBarrierPlan planConservativeMemoryBarrier(boolean renderPassRecording) {
        return VulkanSynchronizationPlanner.planConservativeMemoryBarrier(renderPassRecording);
    }

    private static VulkanicPassResourceModel.ResourceUse imageTransitionUse(
        int aspectMask,
        int oldLayout,
        int newLayout,
        int baseMipLevel,
        int levelCount,
        int baseLayer,
        int layerCount
    ) {
        VulkanicResourceUsage usage = usageForLayout(newLayout);
        VulkanicPassResourceModel.ResourceKind kind = switch (usage) {
            case COLOR_ATTACHMENT_WRITE, ATTACHMENT_FEEDBACK_LOOP ->
                VulkanicPassResourceModel.ResourceKind.COLOR_ATTACHMENT;
            case DEPTH_ATTACHMENT_WRITE -> VulkanicPassResourceModel.ResourceKind.DEPTH_ATTACHMENT;
            case TRANSFER_SRC -> VulkanicPassResourceModel.ResourceKind.TRANSFER_SOURCE;
            case TRANSFER_DST -> VulkanicPassResourceModel.ResourceKind.TRANSFER_DESTINATION;
            case STORAGE_READ_WRITE -> VulkanicPassResourceModel.ResourceKind.STORAGE_TEXTURE;
            case PRESENT, SAMPLED_READ, INFERRED -> VulkanicPassResourceModel.ResourceKind.SAMPLED_TEXTURE;
        };
        VulkanicPassResourceModel.Access access = switch (usage) {
            case SAMPLED_READ, TRANSFER_SRC, PRESENT -> VulkanicPassResourceModel.Access.READ;
            case STORAGE_READ_WRITE, ATTACHMENT_FEEDBACK_LOOP -> VulkanicPassResourceModel.Access.READ_WRITE;
            case INFERRED, COLOR_ATTACHMENT_WRITE, DEPTH_ATTACHMENT_WRITE, TRANSFER_DST -> VulkanicPassResourceModel.Access.WRITE;
        };
        VulkanicPassResourceModel.Subresource subresource = subresourceForAspectMask(
            aspectMask,
            baseMipLevel,
            levelCount,
            baseLayer,
            layerCount
        );
        return VulkanicPassResourceModel.ResourceUse.of(
            VulkanicPassResourceModel.ResourceIdentity.of(
                "explicit-image-transition",
                kind,
                "layout:" + oldLayout + "->" + newLayout
            ),
            access,
            subresource,
            usage,
            "explicit-image-transition",
            usage == VulkanicResourceUsage.ATTACHMENT_FEEDBACK_LOOP,
            0
        );
    }

    private static VulkanicPassResourceModel.Subresource subresourceForAspectMask(
        int aspectMask,
        int baseMipLevel,
        int levelCount,
        int baseLayer,
        int layerCount
    ) {
        int depthStencil = VK10.VK_IMAGE_ASPECT_DEPTH_BIT | VK10.VK_IMAGE_ASPECT_STENCIL_BIT;
        if ((aspectMask & depthStencil) == depthStencil) {
            return VulkanicPassResourceModel.Subresource.depthStencil(baseMipLevel, levelCount, baseLayer, layerCount);
        }
        if ((aspectMask & VK10.VK_IMAGE_ASPECT_DEPTH_BIT) != 0) {
            return VulkanicPassResourceModel.Subresource.depth(baseMipLevel, levelCount, baseLayer, layerCount);
        }
        return VulkanicPassResourceModel.Subresource.color(baseMipLevel, levelCount, baseLayer, layerCount);
    }

    private static VulkanicResourceUsage usageForLayout(int layout) {
        return switch (layout) {
            case VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL -> VulkanicResourceUsage.COLOR_ATTACHMENT_WRITE;
            case VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL -> VulkanicResourceUsage.DEPTH_ATTACHMENT_WRITE;
            case VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL -> VulkanicResourceUsage.SAMPLED_READ;
            case VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL -> VulkanicResourceUsage.TRANSFER_SRC;
            case VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL -> VulkanicResourceUsage.TRANSFER_DST;
            case VK10.VK_IMAGE_LAYOUT_GENERAL -> VulkanicResourceUsage.STORAGE_READ_WRITE;
            case org.lwjgl.vulkan.KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR -> VulkanicResourceUsage.PRESENT;
            default -> {
                if (layout == org.lwjgl.vulkan.EXTAttachmentFeedbackLoopLayout.VK_IMAGE_LAYOUT_ATTACHMENT_FEEDBACK_LOOP_OPTIMAL_EXT) {
                    yield VulkanicResourceUsage.ATTACHMENT_FEEDBACK_LOOP;
                }
                yield VulkanicResourceUsage.INFERRED;
            }
        };
    }

    record ExecutionPlan(
        VulkanicPassResourceModel.PassKind kind,
        String label,
        List<ResourceUsageIntent> orderedUses,
        List<VulkanicPassResourceModel.ResourceUse> finalResourceUsages
    ) {
        ExecutionPlan {
            kind = Objects.requireNonNull(kind, "kind");
            label = Objects.requireNonNull(label, "label");
            orderedUses = List.copyOf(Objects.requireNonNull(orderedUses, "orderedUses"));
            finalResourceUsages = List.copyOf(Objects.requireNonNull(finalResourceUsages, "finalResourceUsages"));
        }
    }

    record ResourceUsageIntent(
        String stableKey,
        String logicalName,
        VulkanicPassResourceModel.ResourceKind kind,
        VulkanicPassResourceModel.Access access,
        VulkanicResourceUsage usage,
        VulkanicPassResourceModel.Subresource subresource,
        String role,
        boolean feedbackLoop,
        int order
    ) {
        ResourceUsageIntent {
            stableKey = Objects.requireNonNull(stableKey, "stableKey");
            logicalName = Objects.requireNonNull(logicalName, "logicalName");
            kind = Objects.requireNonNull(kind, "kind");
            access = Objects.requireNonNull(access, "access");
            usage = Objects.requireNonNull(usage, "usage");
            subresource = Objects.requireNonNull(subresource, "subresource");
            role = Objects.requireNonNull(role, "role");
        }
    }
}
