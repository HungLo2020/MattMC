package net.vulkanic.backends.vulkan;

import net.vulkanic.VulkanicRenderPassDescriptor;
import net.vulkanic.VulkanicLegacyCompatibilityAdapter;
import net.vulkanic.VulkanicPassResourceModel;
import net.vulkanic.VulkanicResourceUsage;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.vulkan.KHRSwapchain;
import org.lwjgl.vulkan.VK10;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Coordinates Vulkan render-pass execution state and policy inputs without
 * issuing Vulkan commands or owning native handles.
 *
 * <p>The lifecycle is:</p>
 * <pre>
 * idle
 *   -> resolve semantic target state
 *   -> plan attachments/layouts/dependencies/cache keys
 *   -> NativeSpine materializes render pass, framebuffer, clear values, begin
 *   -> complete begin publishes active layouts and active pass state
 *   -> NativeSpine records draw/clear commands
 *   -> NativeSpine ends the native pass
 *   -> complete end publishes final layouts and resets active pass state
 *   -> idle
 * </pre>
 *
 * <p>NativeSpine still owns image/view lookup, render-pass/framebuffer creation,
 * command-buffer recording, barriers, vkCmdBeginRenderPass/vkCmdEndRenderPass,
 * and native handle retirement. This coordinator owns the Java-side decision
 * and lifecycle handoff that spans virtual framebuffers, render-target state,
 * layout planning, image-state publication, and synchronization intent.</p>
 */
final class VulkanRenderPassExecutionCoordinator<ColorAttachment, DepthAttachment> {
    private final VulkanRenderTargetStateManager<ColorAttachment, DepthAttachment> renderTargetState;
    private final VulkanImageStateTracker imageStateTracker;
    private boolean renderPassActive;

    VulkanRenderPassExecutionCoordinator(
        VulkanRenderTargetStateManager<ColorAttachment, DepthAttachment> renderTargetState,
        VulkanImageStateTracker imageStateTracker
    ) {
        this.renderTargetState = Objects.requireNonNull(renderTargetState, "renderTargetState");
        this.imageStateTracker = Objects.requireNonNull(imageStateTracker, "imageStateTracker");
    }

    BeginPassPlan<ColorAttachment, DepthAttachment> planTextureViewPass(
        String label,
        int width,
        int height,
        AttachmentRequest<ColorAttachment> color,
        @Nullable AttachmentRequest<DepthAttachment> depth,
        boolean persistentSwapchainPass
    ) {
        requireNoActivePass();
        Objects.requireNonNull(color, "color");
        if (color.depth()) {
            throw new IllegalArgumentException("Texture-view color attachment cannot be depth.");
        }
        if (depth != null && !depth.depth()) {
            throw new IllegalArgumentException("Texture-view depth attachment must be depth.");
        }
        VulkanRenderPassLayoutPlanner.RenderPassPlan layoutPlan = persistentSwapchainPass
            ? VulkanRenderPassLayoutPlanner.planPipelineCompatible(
                VulkanRenderPassCompatibilityKey.swapchainPresent(color.format())
            )
            : VulkanRenderPassLayoutPlanner.planTextureView(
                color.toLayoutInput(0),
                depth == null ? null : depth.toLayoutInput(1)
            );
        return beginPassPlan(
            RenderPassKind.TEXTURE_VIEW,
            label,
            width,
            height,
            List.of(color),
            depth,
            layoutPlan,
            persistentSwapchainPass,
            false
        );
    }

    BeginPassPlan<ColorAttachment, DepthAttachment> planFramebufferPass(
        String label,
        int width,
        int height,
        List<AttachmentRequest<ColorAttachment>> colors,
        @Nullable AttachmentRequest<DepthAttachment> depth
    ) {
        requireNoActivePass();
        Objects.requireNonNull(colors, "colors");
        List<VulkanRenderPassLayoutPlanner.AttachmentInput> colorInputs = new ArrayList<>(colors.size());
        for (int colorIndex = 0; colorIndex < colors.size(); colorIndex++) {
            colorInputs.add(colors.get(colorIndex).toLayoutInput(colorIndex));
        }
        VulkanRenderPassLayoutPlanner.RenderPassPlan layoutPlan =
            VulkanRenderPassLayoutPlanner.planFramebuffer(
                colorInputs,
                depth == null ? null : depth.toLayoutInput(colors.size())
            );
        return beginPassPlan(RenderPassKind.FRAMEBUFFER, label, width, height, colors, depth, layoutPlan, false, true);
    }

    void completeBegin(
        BeginPassPlan<ColorAttachment, DepthAttachment> plan,
        long renderPassHandle,
        long framebufferHandle
    ) {
        if (renderPassActive) {
            throw new IllegalStateException("Nested Vulkan render passes are not supported yet.");
        }
        renderPassActive = true;
        for (AttachmentExecution<ColorAttachment> color : plan.colors()) {
            color.publishActiveLayout(imageStateTracker);
        }
        if (plan.depth() != null) {
            plan.depth().publishActiveLayout(imageStateTracker);
        }
        renderTargetState.beginPass(
            plan.compatibilityKey(),
            plan.width(),
            plan.height(),
            plan.targetsSwapchain(),
            plan.swapchainImageIndex(),
            plan.colorAttachmentsForActiveState(),
            plan.colorFinalLayouts(),
            plan.depthAttachmentForActiveState(),
            plan.depthFinalLayout()
        );
    }

    EndPassResult<ColorAttachment, DepthAttachment> completeEnd() {
        if (!renderPassActive) {
            throw new IllegalStateException("No active Vulkan render pass to end");
        }
        List<FinalLayout<ColorAttachment>> colorFinalLayouts = new ArrayList<>();
        renderTargetState.forEachActiveColorAttachment((texture, finalLayout) -> {
            if (texture != null) {
                int textureId = texture instanceof TextureIdentity identity ? identity.textureId() : -1;
                if (textureId >= 0) {
                    imageStateTracker.recordLayout(textureId, 0, finalLayout);
                }
            }
            colorFinalLayouts.add(new FinalLayout<>(texture, finalLayout));
        });

        FinalLayout<DepthAttachment> depthFinalLayout = null;
        if (renderTargetState.hasActiveDepthAttachment()) {
            DepthAttachment depth = renderTargetState.activeDepthAttachment();
            int layout = renderTargetState.activeDepthFinalLayout() != VK10.VK_IMAGE_LAYOUT_UNDEFINED
                ? renderTargetState.activeDepthFinalLayout()
                : VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL;
            if (depth instanceof TextureIdentity identity) {
                imageStateTracker.recordLayout(identity.textureId(), 0, layout);
            }
            depthFinalLayout = new FinalLayout<>(depth, layout);
        }
        int swapchainImageIndex = renderTargetState.activeSwapchainImageIndex();
        if (swapchainImageIndex >= 0) {
            // NativeSpine owns swapchain image-state storage; report this result
            // so it can publish through the swapchain tracker after vkCmdEnd.
        }

        renderPassActive = false;
        renderTargetState.resetActivePass();
        return new EndPassResult<>(
            colorFinalLayouts,
            depthFinalLayout,
            swapchainImageIndex,
            swapchainImageIndex >= 0 ? KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR : VK10.VK_IMAGE_LAYOUT_UNDEFINED
        );
    }

    void abandonActivePass() {
        renderPassActive = false;
        renderTargetState.resetActivePass();
    }

    void resetForResizeDeviceLossOrShutdown() {
        abandonActivePass();
    }

    PresentComposeTargetPlan planPresentComposeTarget(
        int swapchainImageIndex,
        long imageHandle,
        long imageViewHandle,
        int width,
        int height,
        int format
    ) {
        if (swapchainImageIndex < 0) {
            throw new IllegalArgumentException("swapchain image index must be non-negative");
        }
        if (imageHandle == VK10.VK_NULL_HANDLE || imageViewHandle == VK10.VK_NULL_HANDLE) {
            throw new IllegalStateException("Swapchain present-compose target requires image and view handles.");
        }
        return new PresentComposeTargetPlan(swapchainImageIndex, imageHandle, imageViewHandle, width, height, format);
    }

    boolean isRenderPassActive() {
        return renderPassActive;
    }

    @Nullable
    VulkanRenderPassCompatibilityKey activeCompatibilityKey() {
        return renderPassActive ? renderTargetState.activeCompatibilityKey() : null;
    }

    int activeWidth() {
        return renderTargetState.activeWidth();
    }

    int activeHeight() {
        return renderTargetState.activeHeight();
    }

    boolean activeTargetsSwapchain() {
        return renderPassActive && renderTargetState.activeTargetsSwapchain();
    }

    int activeSwapchainImageIndex() {
        return renderTargetState.activeSwapchainImageIndex();
    }

    int activeColorAttachmentCount() {
        return renderTargetState.activeColorAttachmentCount();
    }

    boolean hasActiveDepthAttachment() {
        return renderTargetState.hasActiveDepthAttachment();
    }

    Set<Integer> activeAttachmentTextureIds() {
        Set<Integer> textureIds = new HashSet<>();
        renderTargetState.forEachActiveColorAttachment((texture, ignoredLayout) -> {
            if (texture instanceof TextureIdentity identity) {
                if (identity.textureId() >= 0) {
                    textureIds.add(identity.textureId());
                }
            }
        });
        Object depthAttachment = renderTargetState.activeDepthAttachment();
        if (depthAttachment instanceof TextureIdentity identity) {
            if (identity.textureId() >= 0) {
                textureIds.add(identity.textureId());
            }
        }
        return Set.copyOf(textureIds);
    }

    boolean isActiveAttachment(Object attachment) {
        return renderTargetState.isActiveAttachment(attachment);
    }

    private void requireNoActivePass() {
        if (renderPassActive) {
            throw new IllegalStateException("Nested Vulkan render passes are not supported yet.");
        }
    }

    private BeginPassPlan<ColorAttachment, DepthAttachment> beginPassPlan(
        RenderPassKind kind,
        String label,
        int width,
        int height,
        List<AttachmentRequest<ColorAttachment>> colors,
        @Nullable AttachmentRequest<DepthAttachment> depth,
        VulkanRenderPassLayoutPlanner.RenderPassPlan layoutPlan,
        boolean persistentSwapchainPass,
        boolean cacheableRenderPass
    ) {
        List<AttachmentExecution<ColorAttachment>> colorExecutions = new ArrayList<>(colors.size());
        for (int colorIndex = 0; colorIndex < colors.size(); colorIndex++) {
            colorExecutions.add(new AttachmentExecution<>(colors.get(colorIndex), layoutPlan.colorAttachment(colorIndex)));
        }
        AttachmentExecution<DepthAttachment> depthExecution = depth == null
            ? null
            : new AttachmentExecution<>(depth, layoutPlan.depthAttachment());
        boolean targetsSwapchain = colorExecutions.stream().anyMatch(color -> color.request().swapchain());
        int swapchainImageIndex = colorExecutions.stream()
            .filter(color -> color.request().swapchain())
            .mapToInt(color -> color.request().swapchainImageIndex())
            .findFirst()
            .orElse(-1);
        VulkanRenderPassKey renderPassKey = cacheableRenderPass ? layoutPlan.requireRenderPassKey() : null;
        VulkanicPassResourceModel.PassExecutionPlan resourcePlan = VulkanicLegacyCompatibilityAdapter.planRenderPass(
            explicitRenderPassSnapshot(kind, label, colors, depth)
        );
        return new BeginPassPlan<>(
            kind,
            Objects.requireNonNull(label, "label"),
            width,
            height,
            colorExecutions,
            depthExecution,
            layoutPlan,
            layoutPlan.compatibilityKey(),
            renderPassKey,
            persistentSwapchainPass,
            cacheableRenderPass,
            targetsSwapchain,
            swapchainImageIndex,
            resourcePlan
        );
    }

    private VulkanicLegacyCompatibilityAdapter.RenderPassSnapshot explicitRenderPassSnapshot(
        RenderPassKind kind,
        String label,
        List<AttachmentRequest<ColorAttachment>> colors,
        @Nullable AttachmentRequest<DepthAttachment> depth
    ) {
        List<VulkanicLegacyCompatibilityAdapter.AttachmentSnapshot> attachments = new ArrayList<>();
        for (int colorIndex = 0; colorIndex < colors.size(); colorIndex++) {
            attachments.add(explicitAttachmentSnapshot(colors.get(colorIndex), colorIndex));
        }
        if (depth != null) {
            attachments.add(explicitAttachmentSnapshot(depth, colors.size()));
        }
        return new VulkanicLegacyCompatibilityAdapter.RenderPassSnapshot(
            kind.name().toLowerCase(java.util.Locale.ROOT) + ":" + label,
            attachments,
            List.of(),
            List.of(),
            List.of(new VulkanicPassResourceModel.Command("render-pass-body", OptionalInt.empty(), OptionalInt.empty())),
            List.of("attachments-ready-before-pass", "final-layout-published-after-pass"),
            false,
            false
        );
    }

    private VulkanicLegacyCompatibilityAdapter.AttachmentSnapshot explicitAttachmentSnapshot(
        AttachmentRequest<?> request,
        int attachmentIndex
    ) {
        VulkanicPassResourceModel.ResourceKind kind = request.depth()
            ? VulkanicPassResourceModel.ResourceKind.DEPTH_ATTACHMENT
            : VulkanicPassResourceModel.ResourceKind.COLOR_ATTACHMENT;
        VulkanicPassResourceModel.Subresource subresource = request.depth()
            ? request.stencilCapable()
                ? VulkanicPassResourceModel.Subresource.depthStencil(0, 1, 0, 1)
                : VulkanicPassResourceModel.Subresource.depth(0, 1, 0, 1)
            : VulkanicPassResourceModel.Subresource.color(0, 1, 0, 1);
        return new VulkanicLegacyCompatibilityAdapter.AttachmentSnapshot(
            attachmentIndex,
            request.depth() ? "depth" : "color" + attachmentIndex,
            kind,
            request.swapchain()
                ? "swapchain:" + request.swapchainImageIndex()
                : "texture:" + request.textureId(),
            subresource,
            request.loadOp(),
            request.storeOp(),
            request.clearColor(),
            request.clearDepth(),
            request.initialUsage(),
            request.passUsage(),
            request.finalUsage(),
            request.feedbackLoopCapable() || request.passUsage() == VulkanicResourceUsage.ATTACHMENT_FEEDBACK_LOOP
        );
    }

    interface TextureIdentity {
        int textureId();
    }

    enum RenderPassKind {
        TEXTURE_VIEW,
        FRAMEBUFFER
    }

    record AttachmentRequest<Attachment>(
        Attachment attachment,
        int textureId,
        long imageViewHandle,
        int format,
        boolean depth,
        boolean feedbackLoopCapable,
        boolean swapchain,
        int swapchainImageIndex,
        boolean stencilCapable,
        int trackedLayout,
        VulkanicRenderPassDescriptor.LoadOp loadOp,
        VulkanicRenderPassDescriptor.StoreOp storeOp,
        OptionalInt clearColor,
        OptionalDouble clearDepth,
        VulkanicResourceUsage initialUsage,
        VulkanicResourceUsage passUsage,
        VulkanicResourceUsage finalUsage
    ) {
        AttachmentRequest {
            Objects.requireNonNull(loadOp, "loadOp");
            Objects.requireNonNull(storeOp, "storeOp");
            Objects.requireNonNull(clearColor, "clearColor");
            Objects.requireNonNull(clearDepth, "clearDepth");
            Objects.requireNonNull(initialUsage, "initialUsage");
            Objects.requireNonNull(passUsage, "passUsage");
            Objects.requireNonNull(finalUsage, "finalUsage");
        }

        static <Attachment> AttachmentRequest<Attachment> color(
            Attachment attachment,
            int textureId,
            long imageViewHandle,
            int format,
            boolean feedbackLoopCapable,
            boolean swapchain,
            int swapchainImageIndex,
            int trackedLayout,
            VulkanicRenderPassDescriptor.LoadOp loadOp,
            VulkanicRenderPassDescriptor.StoreOp storeOp,
            OptionalInt clearColor,
            VulkanicResourceUsage initialUsage,
            VulkanicResourceUsage passUsage,
            VulkanicResourceUsage finalUsage
        ) {
            return new AttachmentRequest<>(
                attachment,
                textureId,
                imageViewHandle,
                format,
                false,
                feedbackLoopCapable,
                swapchain,
                swapchainImageIndex,
                false,
                trackedLayout,
                loadOp,
                storeOp,
                clearColor,
                OptionalDouble.empty(),
                initialUsage,
                passUsage,
                finalUsage
            );
        }

        static <Attachment> AttachmentRequest<Attachment> depth(
            Attachment attachment,
            int textureId,
            long imageViewHandle,
            int format,
            boolean feedbackLoopCapable,
            boolean stencilCapable,
            int trackedLayout,
            VulkanicRenderPassDescriptor.LoadOp loadOp,
            VulkanicRenderPassDescriptor.StoreOp storeOp,
            OptionalDouble clearDepth,
            VulkanicResourceUsage initialUsage,
            VulkanicResourceUsage passUsage,
            VulkanicResourceUsage finalUsage
        ) {
            return new AttachmentRequest<>(
                attachment,
                textureId,
                imageViewHandle,
                format,
                true,
                feedbackLoopCapable,
                false,
                -1,
                stencilCapable,
                trackedLayout,
                loadOp,
                storeOp,
                OptionalInt.empty(),
                clearDepth,
                initialUsage,
                passUsage,
                finalUsage
            );
        }

        VulkanRenderPassLayoutPlanner.AttachmentInput toLayoutInput(int attachmentIndex) {
            return depth
                ? VulkanRenderPassLayoutPlanner.AttachmentInput.depth(
                    attachmentIndex,
                    format,
                    feedbackLoopCapable,
                    stencilCapable,
                    trackedLayout,
                    loadOp,
                    storeOp,
                    initialUsage,
                    passUsage,
                    finalUsage
                )
                : VulkanRenderPassLayoutPlanner.AttachmentInput.color(
                    attachmentIndex,
                    format,
                    feedbackLoopCapable,
                    swapchain,
                    trackedLayout,
                    loadOp,
                    storeOp,
                    initialUsage,
                    passUsage,
                    finalUsage
                );
        }
    }

    record AttachmentExecution<Attachment>(
        AttachmentRequest<Attachment> request,
        VulkanRenderPassLayoutPlanner.AttachmentPlan plan
    ) {
        void publishActiveLayout(VulkanImageStateTracker imageStateTracker) {
            if (!request.swapchain() && request.textureId() >= 0) {
                imageStateTracker.recordLayout(request.textureId(), 0, plan.activeLayout());
            }
        }

        @Nullable
        Attachment attachmentForActiveState() {
            return request.swapchain() ? null : request.attachment();
        }
    }

    record BeginPassPlan<ColorAttachment, DepthAttachment>(
        RenderPassKind kind,
        String label,
        int width,
        int height,
        List<AttachmentExecution<ColorAttachment>> colors,
        @Nullable AttachmentExecution<DepthAttachment> depth,
        VulkanRenderPassLayoutPlanner.RenderPassPlan layoutPlan,
        VulkanRenderPassCompatibilityKey compatibilityKey,
        @Nullable VulkanRenderPassKey renderPassKey,
        boolean persistentSwapchainPass,
        boolean cacheableRenderPass,
        boolean targetsSwapchain,
        int swapchainImageIndex,
        VulkanicPassResourceModel.PassExecutionPlan resourcePlan
    ) {
        BeginPassPlan {
            colors = List.copyOf(colors);
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(layoutPlan, "layoutPlan");
            Objects.requireNonNull(compatibilityKey, "compatibilityKey");
            Objects.requireNonNull(resourcePlan, "resourcePlan");
        }

        int attachmentCount() {
            return colors.size() + (depth == null ? 0 : 1);
        }

        List<ColorAttachment> colorAttachmentsForActiveState() {
            List<ColorAttachment> attachments = new ArrayList<>(colors.size());
            for (AttachmentExecution<ColorAttachment> color : colors) {
                ColorAttachment attachment = color.attachmentForActiveState();
                if (attachment != null) {
                    attachments.add(attachment);
                }
            }
            return attachments;
        }

        List<Integer> colorFinalLayouts() {
            List<Integer> layouts = new ArrayList<>(colors.size());
            for (AttachmentExecution<ColorAttachment> color : colors) {
                if (!color.request().swapchain()) {
                    layouts.add(color.plan().finalLayout());
                }
            }
            return layouts;
        }

        @Nullable
        DepthAttachment depthAttachmentForActiveState() {
            return depth == null ? null : depth.attachmentForActiveState();
        }

        int depthFinalLayout() {
            return depth == null ? VK10.VK_IMAGE_LAYOUT_UNDEFINED : depth.plan().finalLayout();
        }
    }

    record FinalLayout<Attachment>(@Nullable Attachment attachment, int layout) {}

    record EndPassResult<ColorAttachment, DepthAttachment>(
        List<FinalLayout<ColorAttachment>> colorFinalLayouts,
        @Nullable FinalLayout<DepthAttachment> depthFinalLayout,
        int swapchainImageIndex,
        int swapchainFinalLayout
    ) {
        EndPassResult {
            colorFinalLayouts = List.copyOf(colorFinalLayouts);
        }
    }

    record PresentComposeTargetPlan(
        int swapchainImageIndex,
        long imageHandle,
        long imageViewHandle,
        int width,
        int height,
        int format
    ) {}
}
