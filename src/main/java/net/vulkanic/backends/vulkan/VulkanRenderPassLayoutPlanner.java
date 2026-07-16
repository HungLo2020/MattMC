package net.vulkanic.backends.vulkan;

import net.vulkanic.VulkanicRenderPassDescriptor;
import net.vulkanic.VulkanicResourceUsage;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.vulkan.EXTAttachmentFeedbackLoopLayout;
import org.lwjgl.vulkan.KHRSwapchain;
import org.lwjgl.vulkan.VK10;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Pure render-pass attachment/layout planner for Vulkan backend render passes.
 *
 * <p>The planner owns policy for attachment layouts, load/store operations,
 * feedback-loop subpass layouts, and dependency intent. It deliberately does not
 * read backend state, allocate Vulkan structs, issue commands, transition
 * images, create framebuffers, or own resources.</p>
 */
final class VulkanRenderPassLayoutPlanner {
    private VulkanRenderPassLayoutPlanner() {
    }

    static RenderPassPlan planTextureView(AttachmentInput color, @Nullable AttachmentInput depth) {
        Objects.requireNonNull(color, "color");
        if (color.depth()) {
            throw new IllegalArgumentException("Texture-view color attachment must not be depth");
        }
        if (color.attachmentIndex() != 0) {
            throw new IllegalArgumentException("Texture-view render passes require color attachment index 0");
        }
        if (depth != null) {
            validateDepthInput(depth, 1);
        }

        AttachmentPlan colorPlan = planColorTextureView(color);
        AttachmentPlan depthPlan = depth == null ? null : planDepthTextureView(depth);
        boolean feedbackLoop = (!color.swapchain() && color.feedbackLoopCapable())
            || (depth != null && depth.feedbackLoopCapable());
        VulkanRenderPassCompatibilityKey compatibilityKey = VulkanRenderPassCompatibilityKey.textureView(
            List.of(colorPlan.format()),
            depthPlan == null ? VK10.VK_FORMAT_UNDEFINED : depthPlan.format(),
            feedbackLoop
        );
        return new RenderPassPlan(
            List.of(colorPlan),
            depthPlan,
            compatibilityKey,
            null,
            dependencyIntent(compatibilityKey)
        );
    }

    static RenderPassPlan planFramebuffer(List<AttachmentInput> colors, @Nullable AttachmentInput depth) {
        Objects.requireNonNull(colors, "colors");
        List<AttachmentPlan> colorPlans = new ArrayList<>(colors.size());
        List<Integer> colorFormats = new ArrayList<>(colors.size());
        for (int index = 0; index < colors.size(); index++) {
            AttachmentInput color = colors.get(index);
            validateColorInput(color, index);
            AttachmentPlan plan = planColorFramebuffer(color);
            colorPlans.add(plan);
            colorFormats.add(plan.format());
        }

        AttachmentPlan depthPlan = null;
        if (depth != null) {
            validateDepthInput(depth, colors.size());
            depthPlan = planDepthFramebuffer(depth);
        }

        boolean feedbackLoop = colors.stream().anyMatch(AttachmentInput::feedbackLoopCapable)
            || (depth != null && depth.feedbackLoopCapable());
        VulkanRenderPassCompatibilityKey compatibilityKey = VulkanRenderPassCompatibilityKey.framebuffer(
            colorFormats,
            depthPlan == null ? VK10.VK_FORMAT_UNDEFINED : depthPlan.format(),
            feedbackLoop
        );
        VulkanRenderPassKey renderPassKey = VulkanRenderPassKey.framebuffer(
            colorPlans.stream().map(AttachmentPlan::cacheKey).toList(),
            depthPlan == null ? null : depthPlan.cacheKey(),
            feedbackLoop
        );
        return new RenderPassPlan(
            colorPlans,
            depthPlan,
            compatibilityKey,
            renderPassKey,
            dependencyIntent(compatibilityKey)
        );
    }

    static VulkanRenderPassCompatibilityKey swapchainPresentCompatibility(int colorFormat) {
        return VulkanRenderPassCompatibilityKey.swapchainPresent(colorFormat);
    }

    static RenderPassPlan planPipelineCompatible(VulkanRenderPassCompatibilityKey compatibilityKey) {
        Objects.requireNonNull(compatibilityKey, "compatibilityKey");
        int colorLayout = compatibilityKey.feedbackLoop()
            ? EXTAttachmentFeedbackLoopLayout.VK_IMAGE_LAYOUT_ATTACHMENT_FEEDBACK_LOOP_OPTIMAL_EXT
            : VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
        int depthLayout = compatibilityKey.feedbackLoop()
            ? EXTAttachmentFeedbackLoopLayout.VK_IMAGE_LAYOUT_ATTACHMENT_FEEDBACK_LOOP_OPTIMAL_EXT
            : VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL;

        List<AttachmentPlan> colorPlans = new ArrayList<>(compatibilityKey.colorAttachmentCount());
        for (int colorIndex = 0; colorIndex < compatibilityKey.colorAttachmentCount(); colorIndex++) {
            colorPlans.add(AttachmentPlan.pipelineCompatibleColor(
                colorIndex,
                compatibilityKey.colorFormats().get(colorIndex),
                compatibilityKey.feedbackLoop(),
                colorLayout
            ));
        }
        AttachmentPlan depthPlan = compatibilityKey.hasDepthAttachment()
            ? AttachmentPlan.pipelineCompatibleDepth(
                compatibilityKey.colorAttachmentCount(),
                compatibilityKey.depthFormat(),
                compatibilityKey.feedbackLoop(),
                compatibilityKey.hasStencilAttachment(),
                depthLayout
            )
            : null;
        VulkanRenderPassKey key = VulkanRenderPassKey.pipelineCompatible(
            compatibilityKey.colorFormats(),
            compatibilityKey.hasDepthAttachment(),
            compatibilityKey.depthFormat(),
            colorLayout,
            depthLayout,
            compatibilityKey.feedbackLoop(),
            compatibilityKey.dependencyProfile() == VulkanRenderPassCompatibilityKey.DependencyProfile.SWAPCHAIN_PRESENT
        );
        return new RenderPassPlan(
            colorPlans,
            depthPlan,
            compatibilityKey,
            key,
            dependencyIntent(compatibilityKey)
        );
    }

    static int layoutForUsage(
        VulkanicResourceUsage usage,
        boolean depth,
        boolean feedbackLoopCapable,
        int inferredLayout
    ) {
        Objects.requireNonNull(usage, "usage must not be null");
        if (usage == VulkanicResourceUsage.INFERRED) {
            return inferredLayout;
        }

        if (feedbackLoopCapable && usage == VulkanicResourceUsage.ATTACHMENT_FEEDBACK_LOOP) {
            return EXTAttachmentFeedbackLoopLayout.VK_IMAGE_LAYOUT_ATTACHMENT_FEEDBACK_LOOP_OPTIMAL_EXT;
        }

        return switch (usage) {
            case COLOR_ATTACHMENT_WRITE -> feedbackLoopCapable
                ? EXTAttachmentFeedbackLoopLayout.VK_IMAGE_LAYOUT_ATTACHMENT_FEEDBACK_LOOP_OPTIMAL_EXT
                : VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
            case DEPTH_ATTACHMENT_WRITE -> feedbackLoopCapable
                ? EXTAttachmentFeedbackLoopLayout.VK_IMAGE_LAYOUT_ATTACHMENT_FEEDBACK_LOOP_OPTIMAL_EXT
                : VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL;
            case SAMPLED_READ -> depth
                ? VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL
                : VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
            case TRANSFER_SRC -> VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL;
            case TRANSFER_DST -> VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
            case STORAGE_READ_WRITE -> VK10.VK_IMAGE_LAYOUT_GENERAL;
            case PRESENT -> KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
            case ATTACHMENT_FEEDBACK_LOOP -> EXTAttachmentFeedbackLoopLayout.VK_IMAGE_LAYOUT_ATTACHMENT_FEEDBACK_LOOP_OPTIMAL_EXT;
            case INFERRED -> inferredLayout;
        };
    }

    static int toVkLoadOp(VulkanicRenderPassDescriptor.LoadOp loadOp) {
        Objects.requireNonNull(loadOp, "loadOp must not be null");
        return switch (loadOp) {
            case LOAD -> VK10.VK_ATTACHMENT_LOAD_OP_LOAD;
            case CLEAR -> VK10.VK_ATTACHMENT_LOAD_OP_CLEAR;
            case DONT_CARE -> VK10.VK_ATTACHMENT_LOAD_OP_DONT_CARE;
        };
    }

    static int toVkStoreOp(VulkanicRenderPassDescriptor.StoreOp storeOp) {
        Objects.requireNonNull(storeOp, "storeOp must not be null");
        return switch (storeOp) {
            case STORE -> VK10.VK_ATTACHMENT_STORE_OP_STORE;
            case DONT_CARE -> VK10.VK_ATTACHMENT_STORE_OP_DONT_CARE;
        };
    }

    static List<SubpassDependencyPlan> dependencyIntent(VulkanRenderPassCompatibilityKey compatibilityKey) {
        Objects.requireNonNull(compatibilityKey, "compatibilityKey");
        return switch (compatibilityKey.dependencyProfile()) {
            case SWAPCHAIN_PRESENT -> swapchainPresentDependencies();
            case TEXTURE_VIEW -> textureViewDependencies(compatibilityKey);
            case FRAMEBUFFER -> framebufferDependencies(compatibilityKey);
        };
    }

    private static AttachmentPlan planColorTextureView(AttachmentInput input) {
        int inferredInitialLayout = input.swapchain()
            ? input.trackedLayout()
            : input.trackedLayout() != VK10.VK_IMAGE_LAYOUT_UNDEFINED
                ? input.trackedLayout()
                : VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
        int initialLayout = input.swapchain()
            ? inferredInitialLayout
            : layoutForUsage(input.initialUsage(), false, input.feedbackLoopCapable(), inferredInitialLayout);
        int inferredPassLayout = (!input.swapchain() && input.feedbackLoopCapable())
            ? EXTAttachmentFeedbackLoopLayout.VK_IMAGE_LAYOUT_ATTACHMENT_FEEDBACK_LOOP_OPTIMAL_EXT
            : VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
        int passLayout = input.swapchain()
            ? inferredPassLayout
            : layoutForUsage(input.passUsage(), false, input.feedbackLoopCapable(), inferredPassLayout);
        int finalLayout = input.swapchain()
            ? KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR
            : layoutForUsage(
                input.finalUsage(),
                false,
                input.feedbackLoopCapable(),
                VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL
            );
        return AttachmentPlan.color(input, initialLayout, passLayout, finalLayout);
    }

    private static AttachmentPlan planDepthTextureView(AttachmentInput input) {
        int inferredPassLayout = input.feedbackLoopCapable()
            ? EXTAttachmentFeedbackLoopLayout.VK_IMAGE_LAYOUT_ATTACHMENT_FEEDBACK_LOOP_OPTIMAL_EXT
            : VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL;
        int passLayout = layoutForUsage(input.passUsage(), true, input.feedbackLoopCapable(), inferredPassLayout);
        int inferredInitialLayout = input.trackedLayout() != VK10.VK_IMAGE_LAYOUT_UNDEFINED
            ? input.trackedLayout()
            : inferredPassLayout;
        int initialLayout = layoutForUsage(input.initialUsage(), true, input.feedbackLoopCapable(), inferredInitialLayout);
        int finalLayout = layoutForUsage(
            input.finalUsage(),
            true,
            input.feedbackLoopCapable(),
            VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL
        );
        return AttachmentPlan.depth(input, initialLayout, passLayout, finalLayout);
    }

    private static AttachmentPlan planColorFramebuffer(AttachmentInput input) {
        int inferredPassLayout = input.feedbackLoopCapable()
            ? EXTAttachmentFeedbackLoopLayout.VK_IMAGE_LAYOUT_ATTACHMENT_FEEDBACK_LOOP_OPTIMAL_EXT
            : VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
        int passLayout = layoutForUsage(input.passUsage(), false, input.feedbackLoopCapable(), inferredPassLayout);
        int inferredInitialLayout = input.feedbackLoopCapable()
            ? passLayout
            : input.trackedLayout() != VK10.VK_IMAGE_LAYOUT_UNDEFINED
                ? input.trackedLayout()
                : passLayout;
        int initialLayout = layoutForUsage(input.initialUsage(), false, input.feedbackLoopCapable(), inferredInitialLayout);
        int finalLayout = layoutForUsage(
            input.finalUsage(),
            false,
            input.feedbackLoopCapable(),
            VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL
        );
        return AttachmentPlan.color(input, initialLayout, passLayout, finalLayout);
    }

    private static AttachmentPlan planDepthFramebuffer(AttachmentInput input) {
        int inferredPassLayout = input.feedbackLoopCapable()
            ? EXTAttachmentFeedbackLoopLayout.VK_IMAGE_LAYOUT_ATTACHMENT_FEEDBACK_LOOP_OPTIMAL_EXT
            : VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL;
        int passLayout = layoutForUsage(input.passUsage(), true, input.feedbackLoopCapable(), inferredPassLayout);
        int inferredInitialLayout = input.feedbackLoopCapable()
            ? passLayout
            : input.trackedLayout() != VK10.VK_IMAGE_LAYOUT_UNDEFINED
                ? input.trackedLayout()
                : passLayout;
        int initialLayout = layoutForUsage(input.initialUsage(), true, input.feedbackLoopCapable(), inferredInitialLayout);
        int finalLayout = layoutForUsage(
            input.finalUsage(),
            true,
            input.feedbackLoopCapable(),
            VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL
        );
        return AttachmentPlan.depth(input, initialLayout, passLayout, finalLayout);
    }

    private static void validateColorInput(AttachmentInput input, int expectedIndex) {
        Objects.requireNonNull(input, "color input");
        if (input.depth()) {
            throw new IllegalArgumentException("Color attachment input cannot be depth");
        }
        if (input.swapchain()) {
            throw new IllegalArgumentException("Framebuffer render passes cannot use swapchain attachment inputs");
        }
        if (input.attachmentIndex() != expectedIndex) {
            throw new IllegalArgumentException("Color attachment index " + input.attachmentIndex()
                + " did not match expected " + expectedIndex);
        }
    }

    private static void validateDepthInput(AttachmentInput input, int expectedIndex) {
        Objects.requireNonNull(input, "depth input");
        if (!input.depth()) {
            throw new IllegalArgumentException("Depth attachment input must be depth");
        }
        if (input.swapchain()) {
            throw new IllegalArgumentException("Depth attachment cannot be swapchain-backed");
        }
        if (input.attachmentIndex() != expectedIndex) {
            throw new IllegalArgumentException("Depth attachment index " + input.attachmentIndex()
                + " did not match expected " + expectedIndex);
        }
    }

    private static List<SubpassDependencyPlan> swapchainPresentDependencies() {
        return List.of(
            new SubpassDependencyPlan(
                VK10.VK_SUBPASS_EXTERNAL,
                0,
                VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT,
                VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT,
                0,
                VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT,
                0
            ),
            new SubpassDependencyPlan(
                0,
                VK10.VK_SUBPASS_EXTERNAL,
                VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT,
                VK10.VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT,
                VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT,
                0,
                0
            )
        );
    }

    private static List<SubpassDependencyPlan> textureViewDependencies(VulkanRenderPassCompatibilityKey compatibilityKey) {
        int entryStageMask = VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT
            | VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
        int entryAccessMask = VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT
            | VK10.VK_ACCESS_SHADER_READ_BIT;
        if (compatibilityKey.hasDepthAttachment()) {
            entryStageMask |= VK10.VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT
                | VK10.VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT;
            entryAccessMask |= VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT
                | VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT;
        }

        int exitSrcStageMask = VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
        int exitSrcAccessMask = VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
        if (compatibilityKey.hasDepthAttachment()) {
            exitSrcStageMask |= VK10.VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT
                | VK10.VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT;
            exitSrcAccessMask |= VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT
                | VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT;
        }

        List<SubpassDependencyPlan> dependencies = new ArrayList<>(compatibilityKey.feedbackLoop() ? 4 : 3);
        dependencies.add(new SubpassDependencyPlan(
            VK10.VK_SUBPASS_EXTERNAL,
            0,
            entryStageMask,
            entryStageMask,
            entryAccessMask,
            entryAccessMask,
            0
        ));
        dependencies.add(new SubpassDependencyPlan(
            0,
            VK10.VK_SUBPASS_EXTERNAL,
            exitSrcStageMask,
            VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
            exitSrcAccessMask,
            VK10.VK_ACCESS_SHADER_READ_BIT,
            0
        ));
        dependencies.add(graphicsBarrierSelfDependency());
        if (compatibilityKey.feedbackLoop()) {
            dependencies.add(new SubpassDependencyPlan(
                0,
                0,
                VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT,
                VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
                VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT,
                VK10.VK_ACCESS_SHADER_READ_BIT,
                EXTAttachmentFeedbackLoopLayout.VK_DEPENDENCY_FEEDBACK_LOOP_BIT_EXT
                    | VK10.VK_DEPENDENCY_BY_REGION_BIT
            ));
        }
        return List.copyOf(dependencies);
    }

    private static List<SubpassDependencyPlan> framebufferDependencies(VulkanRenderPassCompatibilityKey compatibilityKey) {
        int entryStageMask = VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
        int entryAccessMask = VK10.VK_ACCESS_SHADER_READ_BIT;
        if (compatibilityKey.colorAttachmentCount() > 0) {
            entryStageMask |= VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
            entryAccessMask |= VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
        }
        if (compatibilityKey.hasDepthAttachment()) {
            entryStageMask |= VK10.VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT
                | VK10.VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT;
            entryAccessMask |= VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT;
        }

        int exitSrcStageMask = 0;
        int exitSrcAccessMask = 0;
        if (compatibilityKey.colorAttachmentCount() > 0) {
            exitSrcStageMask |= VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
            exitSrcAccessMask |= VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
        }
        if (compatibilityKey.hasDepthAttachment()) {
            exitSrcStageMask |= VK10.VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT
                | VK10.VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT;
            exitSrcAccessMask |= VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT;
        }

        List<SubpassDependencyPlan> dependencies = new ArrayList<>(compatibilityKey.feedbackLoop() ? 4 : 3);
        dependencies.add(new SubpassDependencyPlan(
            VK10.VK_SUBPASS_EXTERNAL,
            0,
            entryStageMask,
            entryStageMask,
            entryAccessMask,
            entryAccessMask,
            0
        ));
        dependencies.add(new SubpassDependencyPlan(
            0,
            VK10.VK_SUBPASS_EXTERNAL,
            exitSrcStageMask,
            VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
            exitSrcAccessMask,
            VK10.VK_ACCESS_SHADER_READ_BIT,
            0
        ));
        dependencies.add(graphicsBarrierSelfDependency());
        if (compatibilityKey.feedbackLoop()) {
            int feedbackSrcStageMask = 0;
            int feedbackSrcAccessMask = 0;
            if (compatibilityKey.colorAttachmentCount() > 0) {
                feedbackSrcStageMask |= VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
                feedbackSrcAccessMask |= VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
            }
            if (compatibilityKey.hasDepthAttachment()) {
                feedbackSrcStageMask |= VK10.VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT
                    | VK10.VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT;
                feedbackSrcAccessMask |= VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT;
            }
            dependencies.add(new SubpassDependencyPlan(
                0,
                0,
                feedbackSrcStageMask,
                VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
                feedbackSrcAccessMask,
                VK10.VK_ACCESS_SHADER_READ_BIT,
                EXTAttachmentFeedbackLoopLayout.VK_DEPENDENCY_FEEDBACK_LOOP_BIT_EXT
                    | VK10.VK_DEPENDENCY_BY_REGION_BIT
            ));
        }
        return List.copyOf(dependencies);
    }

    private static SubpassDependencyPlan graphicsBarrierSelfDependency() {
        int attachmentStages = VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT
            | VK10.VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT
            | VK10.VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT;
        int attachmentAccess = VK10.VK_ACCESS_COLOR_ATTACHMENT_READ_BIT
            | VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT
            | VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT
            | VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT;
        return new SubpassDependencyPlan(
            0,
            0,
            attachmentStages,
            attachmentStages,
            attachmentAccess,
            attachmentAccess,
            VK10.VK_DEPENDENCY_BY_REGION_BIT
        );
    }

    record AttachmentInput(
        int attachmentIndex,
        int format,
        boolean depth,
        boolean feedbackLoopCapable,
        boolean swapchain,
        boolean stencilCapable,
        int trackedLayout,
        VulkanicRenderPassDescriptor.LoadOp loadOp,
        VulkanicRenderPassDescriptor.StoreOp storeOp,
        VulkanicResourceUsage initialUsage,
        VulkanicResourceUsage passUsage,
        VulkanicResourceUsage finalUsage
    ) {
        AttachmentInput {
            Objects.requireNonNull(loadOp, "loadOp");
            Objects.requireNonNull(storeOp, "storeOp");
            Objects.requireNonNull(initialUsage, "initialUsage");
            Objects.requireNonNull(passUsage, "passUsage");
            Objects.requireNonNull(finalUsage, "finalUsage");
            if (attachmentIndex < 0) {
                throw new IllegalArgumentException("attachmentIndex must be non-negative");
            }
            if (format == VK10.VK_FORMAT_UNDEFINED) {
                throw new IllegalArgumentException("attachment format must be defined");
            }
        }

        static AttachmentInput color(
            int attachmentIndex,
            int format,
            boolean feedbackLoopCapable,
            boolean swapchain,
            int trackedLayout,
            VulkanicRenderPassDescriptor.LoadOp loadOp,
            VulkanicRenderPassDescriptor.StoreOp storeOp,
            VulkanicResourceUsage initialUsage,
            VulkanicResourceUsage passUsage,
            VulkanicResourceUsage finalUsage
        ) {
            return new AttachmentInput(
                attachmentIndex,
                format,
                false,
                feedbackLoopCapable,
                swapchain,
                false,
                trackedLayout,
                loadOp,
                storeOp,
                initialUsage,
                passUsage,
                finalUsage
            );
        }

        static AttachmentInput depth(
            int attachmentIndex,
            int format,
            boolean feedbackLoopCapable,
            boolean stencilCapable,
            int trackedLayout,
            VulkanicRenderPassDescriptor.LoadOp loadOp,
            VulkanicRenderPassDescriptor.StoreOp storeOp,
            VulkanicResourceUsage initialUsage,
            VulkanicResourceUsage passUsage,
            VulkanicResourceUsage finalUsage
        ) {
            return new AttachmentInput(
                attachmentIndex,
                format,
                true,
                feedbackLoopCapable,
                false,
                stencilCapable,
                trackedLayout,
                loadOp,
                storeOp,
                initialUsage,
                passUsage,
                finalUsage
            );
        }
    }

    record AttachmentPlan(
        int attachmentIndex,
        int format,
        boolean depth,
        boolean feedbackLoopCapable,
        boolean stencilCapable,
        int loadOp,
        int storeOp,
        int stencilLoadOp,
        int stencilStoreOp,
        int initialLayout,
        int subpassLayout,
        int finalLayout,
        int activeLayout,
        boolean clear
    ) {
        private static AttachmentPlan color(
            AttachmentInput input,
            int initialLayout,
            int subpassLayout,
            int finalLayout
        ) {
            return new AttachmentPlan(
                input.attachmentIndex(),
                input.format(),
                false,
                input.feedbackLoopCapable(),
                false,
                toVkLoadOp(input.loadOp()),
                toVkStoreOp(input.storeOp()),
                VK10.VK_ATTACHMENT_LOAD_OP_DONT_CARE,
                VK10.VK_ATTACHMENT_STORE_OP_DONT_CARE,
                initialLayout,
                subpassLayout,
                finalLayout,
                subpassLayout,
                input.loadOp() == VulkanicRenderPassDescriptor.LoadOp.CLEAR
            );
        }

        private static AttachmentPlan depth(
            AttachmentInput input,
            int initialLayout,
            int subpassLayout,
            int finalLayout
        ) {
            int loadOp = toVkLoadOp(input.loadOp());
            int storeOp = toVkStoreOp(input.storeOp());
            return new AttachmentPlan(
                input.attachmentIndex(),
                input.format(),
                true,
                input.feedbackLoopCapable(),
                input.stencilCapable(),
                loadOp,
                storeOp,
                input.stencilCapable() ? loadOp : VK10.VK_ATTACHMENT_LOAD_OP_DONT_CARE,
                input.stencilCapable() ? storeOp : VK10.VK_ATTACHMENT_STORE_OP_DONT_CARE,
                initialLayout,
                subpassLayout,
                finalLayout,
                subpassLayout,
                input.loadOp() == VulkanicRenderPassDescriptor.LoadOp.CLEAR
            );
        }

        private static AttachmentPlan pipelineCompatibleColor(
            int attachmentIndex,
            int format,
            boolean feedbackLoop,
            int layout
        ) {
            return new AttachmentPlan(
                attachmentIndex,
                format,
                false,
                feedbackLoop,
                false,
                VK10.VK_ATTACHMENT_LOAD_OP_DONT_CARE,
                VK10.VK_ATTACHMENT_STORE_OP_STORE,
                VK10.VK_ATTACHMENT_LOAD_OP_DONT_CARE,
                VK10.VK_ATTACHMENT_STORE_OP_DONT_CARE,
                layout,
                layout,
                layout,
                layout,
                false
            );
        }

        private static AttachmentPlan pipelineCompatibleDepth(
            int attachmentIndex,
            int format,
            boolean feedbackLoop,
            boolean stencilCapable,
            int layout
        ) {
            return new AttachmentPlan(
                attachmentIndex,
                format,
                true,
                feedbackLoop,
                stencilCapable,
                VK10.VK_ATTACHMENT_LOAD_OP_DONT_CARE,
                VK10.VK_ATTACHMENT_STORE_OP_DONT_CARE,
                VK10.VK_ATTACHMENT_LOAD_OP_DONT_CARE,
                VK10.VK_ATTACHMENT_STORE_OP_DONT_CARE,
                layout,
                layout,
                layout,
                layout,
                false
            );
        }

        VulkanRenderPassKey.Attachment cacheKey() {
            return new VulkanRenderPassKey.Attachment(
                format,
                loadOp,
                storeOp,
                initialLayout,
                finalLayout,
                subpassLayout
            );
        }
    }

    record RenderPassPlan(
        List<AttachmentPlan> colorAttachments,
        @Nullable AttachmentPlan depthAttachment,
        VulkanRenderPassCompatibilityKey compatibilityKey,
        @Nullable VulkanRenderPassKey renderPassKey,
        List<SubpassDependencyPlan> dependencyIntent
    ) {
        RenderPassPlan {
            colorAttachments = List.copyOf(colorAttachments);
            Objects.requireNonNull(compatibilityKey, "compatibilityKey");
            dependencyIntent = List.copyOf(dependencyIntent);
        }

        AttachmentPlan colorAttachment(int index) {
            return colorAttachments.get(index);
        }

        boolean hasDepthAttachment() {
            return depthAttachment != null;
        }

        VulkanRenderPassKey requireRenderPassKey() {
            if (renderPassKey == null) {
                throw new IllegalStateException("Render pass plan has no framebuffer cache key");
            }
            return renderPassKey;
        }
    }

    record SubpassDependencyPlan(
        int srcSubpass,
        int dstSubpass,
        int srcStageMask,
        int dstStageMask,
        int srcAccessMask,
        int dstAccessMask,
        int dependencyFlags
    ) {
    }
}
