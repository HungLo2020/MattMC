package net.vulkanic.backends.vulkan;

import net.vulkanic.VulkanicResourceBarriers;
import net.vulkanic.VulkanicPassResourceModel;
import net.vulkanic.VulkanicResourceUsage;
import org.lwjgl.vulkan.EXTAttachmentFeedbackLoopLayout;
import org.lwjgl.vulkan.KHRSwapchain;
import org.lwjgl.vulkan.VK10;

import java.util.Objects;
import java.util.Optional;

/**
 * Pure synchronization planner for Vulkan backend barriers.
 *
 * <p>The planner owns mask/layout/range policy only. It deliberately does not
 * look up resources, allocate Vulkan structs, record commands, submit work, or
 * mutate tracked resource state.</p>
 */
final class VulkanSynchronizationPlanner {
    private VulkanSynchronizationPlanner() {
    }

    static Optional<ImageBarrierPlan> planImageLayoutTransition(
        int aspectMask,
        int oldLayout,
        int newLayout,
        int baseMipLevel,
        int levelCount,
        int layerCount
    ) {
        return planImageLayoutTransition(
            aspectMask,
            oldLayout,
            newLayout,
            baseMipLevel,
            levelCount,
            0,
            layerCount,
            VK10.VK_QUEUE_FAMILY_IGNORED,
            VK10.VK_QUEUE_FAMILY_IGNORED
        );
    }

    static Optional<ImageBarrierPlan> planImageLayoutTransition(
        int aspectMask,
        int oldLayout,
        int newLayout,
        int baseMipLevel,
        int levelCount,
        int baseArrayLayer,
        int layerCount,
        int srcQueueFamilyIndex,
        int dstQueueFamilyIndex
    ) {
        if (baseMipLevel < 0 || levelCount <= 0 || baseArrayLayer < 0 || layerCount <= 0) {
            throw new IllegalArgumentException(
                "Image barrier planning requires non-negative base levels/layers and positive counts"
            );
        }
        if (oldLayout == newLayout && srcQueueFamilyIndex == dstQueueFamilyIndex) {
            return Optional.empty();
        }
        ImageSubresourceRange range = new ImageSubresourceRange(
            aspectMask,
            baseMipLevel,
            levelCount,
            baseArrayLayer,
            layerCount
        );
        return Optional.of(new ImageBarrierPlan(
            oldLayout,
            newLayout,
            accessMaskForLayout(oldLayout),
            accessMaskForLayout(newLayout),
            stageMaskForLayout(oldLayout),
            stageMaskForLayout(newLayout),
            srcQueueFamilyIndex,
            dstQueueFamilyIndex,
            range
        ));
    }

    static Optional<BufferBarrierPlan> planBufferTransferWriteVisibility(long offset, long size) {
        if (size <= 0L) {
            return Optional.empty();
        }
        return Optional.of(new BufferBarrierPlan(
            VK10.VK_ACCESS_TRANSFER_WRITE_BIT,
            VK10.VK_ACCESS_VERTEX_ATTRIBUTE_READ_BIT
                | VK10.VK_ACCESS_INDEX_READ_BIT
                | VK10.VK_ACCESS_UNIFORM_READ_BIT
                | VK10.VK_ACCESS_SHADER_READ_BIT
                | VK10.VK_ACCESS_SHADER_WRITE_BIT
                | VK10.VK_ACCESS_INDIRECT_COMMAND_READ_BIT
                | VK10.VK_ACCESS_TRANSFER_READ_BIT,
            VK10.VK_PIPELINE_STAGE_TRANSFER_BIT,
            VK10.VK_PIPELINE_STAGE_VERTEX_INPUT_BIT
                | VK10.VK_PIPELINE_STAGE_VERTEX_SHADER_BIT
                | VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT
                | VK10.VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT
                | VK10.VK_PIPELINE_STAGE_DRAW_INDIRECT_BIT
                | VK10.VK_PIPELINE_STAGE_TRANSFER_BIT,
            VK10.VK_QUEUE_FAMILY_IGNORED,
            VK10.VK_QUEUE_FAMILY_IGNORED,
            offset,
            size
        ));
    }

    static Optional<BufferBarrierPlan> planBufferVisibilityForUse(
        VulkanicPassResourceModel.ResourceUse priorWrite,
        VulkanicPassResourceModel.ResourceUse consumer
    ) {
        Objects.requireNonNull(priorWrite, "priorWrite");
        Objects.requireNonNull(consumer, "consumer");
        if (!priorWrite.writes() || !consumer.reads()) {
            return Optional.empty();
        }
        if (!priorWrite.subresource().overlaps(consumer.subresource())) {
            return Optional.empty();
        }
        if (!priorWrite.subresource().aspects().contains(VulkanicPassResourceModel.Aspect.BUFFER)
            || !consumer.subresource().aspects().contains(VulkanicPassResourceModel.Aspect.BUFFER)) {
            return Optional.empty();
        }
        int offset = Math.max(priorWrite.subresource().baseMipLevel(), consumer.subresource().baseMipLevel());
        int priorEnd = priorWrite.subresource().baseMipLevel() + priorWrite.subresource().levelCount();
        int consumerEnd = consumer.subresource().baseMipLevel() + consumer.subresource().levelCount();
        int size = Math.max(0, Math.min(priorEnd, consumerEnd) - offset);
        return planBufferTransferWriteVisibility(offset, size);
    }

    static Optional<ImageBarrierPlan> planImageLayoutTransitionForUse(
        int aspectMask,
        int oldLayout,
        VulkanicPassResourceModel.ResourceUse use,
        boolean depth,
        boolean feedbackLoopCapable,
        int inferredLayout
    ) {
        Objects.requireNonNull(use, "use");
        int newLayout = layoutForUse(use, depth, feedbackLoopCapable, inferredLayout);
        return planImageLayoutTransition(
            aspectMask,
            oldLayout,
            newLayout,
            use.subresource().baseMipLevel(),
            use.subresource().levelCount(),
            use.subresource().baseLayer(),
            use.subresource().layerCount(),
            VK10.VK_QUEUE_FAMILY_IGNORED,
            VK10.VK_QUEUE_FAMILY_IGNORED
        );
    }

    static int layoutForUse(
        VulkanicPassResourceModel.ResourceUse use,
        boolean depth,
        boolean feedbackLoopCapable,
        int inferredLayout
    ) {
        Objects.requireNonNull(use, "use");
        VulkanicResourceUsage usage = use.usage();
        if (usage == VulkanicResourceUsage.INFERRED) {
            usage = switch (use.kind()) {
                case COLOR_ATTACHMENT -> VulkanicResourceUsage.COLOR_ATTACHMENT_WRITE;
                case DEPTH_ATTACHMENT -> VulkanicResourceUsage.DEPTH_ATTACHMENT_WRITE;
                case SAMPLED_TEXTURE -> VulkanicResourceUsage.SAMPLED_READ;
                case STORAGE_TEXTURE, STORAGE_BUFFER -> VulkanicResourceUsage.STORAGE_READ_WRITE;
                case TRANSFER_SOURCE, READBACK_SOURCE -> VulkanicResourceUsage.TRANSFER_SRC;
                case TRANSFER_DESTINATION -> VulkanicResourceUsage.TRANSFER_DST;
                case UNIFORM_BUFFER, TEXEL_BUFFER, VERTEX_BUFFER, INDEX_BUFFER, INDIRECT_BUFFER -> VulkanicResourceUsage.SAMPLED_READ;
            };
        }
        return VulkanRenderPassLayoutPlanner.layoutForUsage(usage, depth, feedbackLoopCapable, inferredLayout);
    }

    static MemoryBarrierPlan planResourceBarrier(VulkanicResourceBarriers barriers, boolean renderPassRecording) {
        Objects.requireNonNull(barriers, "barriers");
        return renderPassRecording
            ? planRenderPassResourceBarrier(barriers)
            : planOutsideRenderPassResourceBarrier(barriers);
    }

    static MemoryBarrierPlan planConservativeMemoryBarrier(boolean renderPassRecording) {
        if (renderPassRecording) {
            return renderPassConservativeBarrier();
        }
        return new MemoryBarrierPlan(
            VK10.VK_PIPELINE_STAGE_ALL_COMMANDS_BIT,
            VK10.VK_PIPELINE_STAGE_ALL_COMMANDS_BIT,
            VK10.VK_ACCESS_MEMORY_WRITE_BIT,
            VK10.VK_ACCESS_MEMORY_READ_BIT | VK10.VK_ACCESS_MEMORY_WRITE_BIT,
            0
        );
    }

    static int accessMaskForLayout(int layout) {
        return switch (layout) {
            case VK10.VK_IMAGE_LAYOUT_UNDEFINED -> 0;
            case VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL -> VK10.VK_ACCESS_TRANSFER_READ_BIT;
            case VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL -> VK10.VK_ACCESS_TRANSFER_WRITE_BIT;
            case VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL ->
                VK10.VK_ACCESS_COLOR_ATTACHMENT_READ_BIT | VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
            case VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL ->
                VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT | VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT;
            case VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL -> VK10.VK_ACCESS_SHADER_READ_BIT;
            case VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL -> VK10.VK_ACCESS_SHADER_READ_BIT;
            case VK10.VK_IMAGE_LAYOUT_GENERAL -> VK10.VK_ACCESS_MEMORY_READ_BIT | VK10.VK_ACCESS_MEMORY_WRITE_BIT;
            case KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR -> 0;
            default -> {
                if (layout == EXTAttachmentFeedbackLoopLayout.VK_IMAGE_LAYOUT_ATTACHMENT_FEEDBACK_LOOP_OPTIMAL_EXT) {
                    yield VK10.VK_ACCESS_COLOR_ATTACHMENT_READ_BIT | VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT
                        | VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT | VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT
                        | VK10.VK_ACCESS_SHADER_READ_BIT;
                }
                yield VK10.VK_ACCESS_MEMORY_READ_BIT | VK10.VK_ACCESS_MEMORY_WRITE_BIT;
            }
        };
    }

    static int stageMaskForLayout(int layout) {
        return switch (layout) {
            case VK10.VK_IMAGE_LAYOUT_UNDEFINED -> VK10.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
            case VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL -> VK10.VK_PIPELINE_STAGE_TRANSFER_BIT;
            case VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL -> VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
            case VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL ->
                VK10.VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT | VK10.VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT;
            case VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL ->
                VK10.VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT
                    | VK10.VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT
                    | VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
            case VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL ->
                VK10.VK_PIPELINE_STAGE_ALL_GRAPHICS_BIT | VK10.VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT;
            case KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR -> VK10.VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT;
            default -> {
                if (layout == EXTAttachmentFeedbackLoopLayout.VK_IMAGE_LAYOUT_ATTACHMENT_FEEDBACK_LOOP_OPTIMAL_EXT) {
                    yield VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT
                        | VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT
                        | VK10.VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT
                        | VK10.VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT;
                }
                yield VK10.VK_PIPELINE_STAGE_ALL_COMMANDS_BIT;
            }
        };
    }

    private static MemoryBarrierPlan planOutsideRenderPassResourceBarrier(VulkanicResourceBarriers barriers) {
        int srcStageMask = 0;
        int dstStageMask = 0;
        int srcAccessMask = 0;
        int dstAccessMask = 0;

        int shaderStages = VK10.VK_PIPELINE_STAGE_VERTEX_SHADER_BIT
            | VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT
            | VK10.VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT;

        for (VulkanicResourceBarriers.Barrier barrier : barriers.barriers()) {
            switch (barrier) {
                case SHADER_IMAGE_ACCESS -> {
                    srcStageMask |= shaderStages;
                    dstStageMask |= shaderStages;
                    srcAccessMask |= VK10.VK_ACCESS_SHADER_WRITE_BIT;
                    dstAccessMask |= VK10.VK_ACCESS_SHADER_READ_BIT | VK10.VK_ACCESS_SHADER_WRITE_BIT;
                }
                case TEXTURE_FETCH -> {
                    srcStageMask |= shaderStages | VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT | VK10.VK_PIPELINE_STAGE_TRANSFER_BIT;
                    dstStageMask |= shaderStages;
                    srcAccessMask |= VK10.VK_ACCESS_SHADER_WRITE_BIT | VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT | VK10.VK_ACCESS_TRANSFER_WRITE_BIT;
                    dstAccessMask |= VK10.VK_ACCESS_SHADER_READ_BIT;
                }
                case SHADER_STORAGE -> {
                    srcStageMask |= shaderStages;
                    dstStageMask |= shaderStages;
                    srcAccessMask |= VK10.VK_ACCESS_SHADER_WRITE_BIT;
                    dstAccessMask |= VK10.VK_ACCESS_SHADER_READ_BIT | VK10.VK_ACCESS_SHADER_WRITE_BIT;
                }
                default -> throw new IllegalArgumentException("Unhandled VulkanicResourceBarriers.Barrier: " + barrier);
            }
        }

        if (srcStageMask == 0) {
            srcStageMask = VK10.VK_PIPELINE_STAGE_ALL_COMMANDS_BIT;
        }
        if (dstStageMask == 0) {
            dstStageMask = VK10.VK_PIPELINE_STAGE_ALL_COMMANDS_BIT;
        }
        if (srcAccessMask == 0) {
            srcAccessMask = VK10.VK_ACCESS_MEMORY_WRITE_BIT;
        }
        if (dstAccessMask == 0) {
            dstAccessMask = VK10.VK_ACCESS_MEMORY_READ_BIT;
        }

        return new MemoryBarrierPlan(srcStageMask, dstStageMask, srcAccessMask, dstAccessMask, 0);
    }

    private static MemoryBarrierPlan planRenderPassResourceBarrier(VulkanicResourceBarriers barriers) {
        int srcStageMask = 0;
        int dstStageMask = 0;
        int srcAccessMask = 0;
        int dstAccessMask = 0;

        int framebufferStages = VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT
            | VK10.VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT
            | VK10.VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT;

        for (VulkanicResourceBarriers.Barrier barrier : barriers.barriers()) {
            switch (barrier) {
                case SHADER_IMAGE_ACCESS -> {
                    srcStageMask |= framebufferStages;
                    dstStageMask |= framebufferStages;
                    srcAccessMask |= VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT
                        | VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT;
                    dstAccessMask |= VK10.VK_ACCESS_COLOR_ATTACHMENT_READ_BIT
                        | VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT
                        | VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT
                        | VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT;
                }
                case TEXTURE_FETCH -> {
                    srcStageMask |= framebufferStages;
                    dstStageMask |= framebufferStages;
                    srcAccessMask |= VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT
                        | VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT;
                    dstAccessMask |= VK10.VK_ACCESS_COLOR_ATTACHMENT_READ_BIT
                        | VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT;
                }
                case SHADER_STORAGE -> {
                    srcStageMask |= framebufferStages;
                    dstStageMask |= framebufferStages;
                    srcAccessMask |= VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT
                        | VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT;
                    dstAccessMask |= VK10.VK_ACCESS_COLOR_ATTACHMENT_READ_BIT
                        | VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT
                        | VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT
                        | VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT;
                }
                default -> throw new IllegalArgumentException("Unhandled VulkanicResourceBarriers.Barrier: " + barrier);
            }
        }

        if (srcStageMask == 0) {
            srcStageMask = framebufferStages;
        }
        if (dstStageMask == 0) {
            dstStageMask = framebufferStages;
        }
        if (srcAccessMask == 0) {
            srcAccessMask = VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT
                | VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT;
        }
        if (dstAccessMask == 0) {
            dstAccessMask = VK10.VK_ACCESS_COLOR_ATTACHMENT_READ_BIT
                | VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT
                | VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT
                | VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT;
        }

        return new MemoryBarrierPlan(
            srcStageMask,
            dstStageMask,
            srcAccessMask,
            dstAccessMask,
            VK10.VK_DEPENDENCY_BY_REGION_BIT
        );
    }

    private static MemoryBarrierPlan renderPassConservativeBarrier() {
        int sourceStages = VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT
            | VK10.VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT
            | VK10.VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT;
        int destinationStages = sourceStages;
        int sourceAccess = VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT
            | VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT;
        int destinationAccess = VK10.VK_ACCESS_COLOR_ATTACHMENT_READ_BIT
            | VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT
            | VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT
            | VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT;

        return new MemoryBarrierPlan(
            sourceStages,
            destinationStages,
            sourceAccess,
            destinationAccess,
            VK10.VK_DEPENDENCY_BY_REGION_BIT
        );
    }

    record ImageBarrierPlan(
        int oldLayout,
        int newLayout,
        int srcAccessMask,
        int dstAccessMask,
        int srcStageMask,
        int dstStageMask,
        int srcQueueFamilyIndex,
        int dstQueueFamilyIndex,
        ImageSubresourceRange range
    ) {
        ImageBarrierPlan {
            Objects.requireNonNull(range, "range");
        }
    }

    record ImageSubresourceRange(
        int aspectMask,
        int baseMipLevel,
        int levelCount,
        int baseArrayLayer,
        int layerCount
    ) {
    }

    record BufferBarrierPlan(
        int srcAccessMask,
        int dstAccessMask,
        int srcStageMask,
        int dstStageMask,
        int srcQueueFamilyIndex,
        int dstQueueFamilyIndex,
        long offset,
        long size
    ) {
    }

    record MemoryBarrierPlan(
        int srcStageMask,
        int dstStageMask,
        int srcAccessMask,
        int dstAccessMask,
        int dependencyFlags
    ) {
    }
}
