package net.vulkanic.backends.vulkan;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkAttachmentDescription;
import org.lwjgl.vulkan.VkAttachmentReference;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkFramebufferCreateInfo;
import org.lwjgl.vulkan.VkRenderPassCreateInfo;
import org.lwjgl.vulkan.VkSubpassDependency;
import org.lwjgl.vulkan.VkSubpassDescription;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Backend-internal owner for native Vulkan render-pass and framebuffer objects.
 *
 * <p>Semantic pass planning, attachment layout policy, pre-pass barriers,
 * command-buffer recording, and queue synchronization remain outside this
 * manager. This class owns only native object materialization, cache identity,
 * invalidation, and destruction for {@code VkRenderPass}/{@code VkFramebuffer}
 * handles.</p>
 */
final class VulkanNativeRenderTargetLifecycleManager {
    private final Map<VulkanRenderPassKey, Long> renderPassCache = new HashMap<>();
    private final Set<Long> cachedRenderPassHandles = new HashSet<>();
    private final Map<FramebufferKey, Long> framebufferCache = new HashMap<>();
    private final Set<Long> cachedFramebufferHandles = new HashSet<>();

    NativeRenderTarget materializeTransient(
        VkDevice device,
        MemoryStack stack,
        String operation,
        VulkanRenderPassExecutionCoordinator.BeginPassPlan<?, ?> plan,
        long[] attachmentViewHandles,
        VkResultChecker checker
    ) {
        Objects.requireNonNull(plan, "plan");
        long renderPass = createRenderPass(device, stack, renderPassOperation(operation), plan.layoutPlan(), checker);
        try {
            long framebuffer = createFramebuffer(
                device,
                stack,
                framebufferOperation(operation),
                renderPass,
                attachmentViewHandles,
                plan.width(),
                plan.height(),
                1,
                checker
            );
            return new NativeRenderTarget(renderPass, framebuffer, false, false);
        } catch (RuntimeException exception) {
            destroyRenderPassNow(device, renderPass);
            throw exception;
        }
    }

    NativeRenderTarget materializeFramebuffer(
        VkDevice device,
        MemoryStack stack,
        String operation,
        VulkanRenderPassExecutionCoordinator.BeginPassPlan<?, ?> plan,
        long[] attachmentViewHandles,
        int[] attachmentTextureIds,
        VkResultChecker checker
    ) {
        Objects.requireNonNull(plan, "plan");
        VulkanRenderPassKey renderPassKey = plan.layoutPlan().requireRenderPassKey();
        FramebufferKey framebufferKey = new FramebufferKey(
            renderPassKey,
            attachmentViewHandles,
            attachmentTextureIds,
            plan.width(),
            plan.height(),
            1
        );

        Long cachedFramebuffer = framebufferCache.get(framebufferKey);
        if (cachedFramebuffer != null) {
            Long cachedRenderPass = renderPassCache.get(renderPassKey);
            if (cachedRenderPass == null) {
                throw new IllegalStateException("Cached framebuffer exists without its compatible render pass.");
            }
            return new NativeRenderTarget(cachedRenderPass, cachedFramebuffer, true, true);
        }

        Long cachedRenderPass = renderPassCache.get(renderPassKey);
        boolean createdRenderPass = cachedRenderPass == null;
        long renderPass = createdRenderPass
            ? createRenderPass(device, stack, renderPassOperation(operation), plan.layoutPlan(), checker)
            : cachedRenderPass;

        long framebuffer = VK10.VK_NULL_HANDLE;
        try {
            framebuffer = createFramebuffer(
                device,
                stack,
                framebufferOperation(operation),
                renderPass,
                attachmentViewHandles,
                plan.width(),
                plan.height(),
                1,
                checker
            );
            if (createdRenderPass) {
                renderPassCache.put(renderPassKey, renderPass);
                cachedRenderPassHandles.add(renderPass);
            }
            framebufferCache.put(framebufferKey, framebuffer);
            cachedFramebufferHandles.add(framebuffer);
        } catch (RuntimeException exception) {
            destroyFramebufferNow(device, framebuffer);
            if (createdRenderPass) {
                destroyRenderPassNow(device, renderPass);
            }
            throw exception;
        }

        return new NativeRenderTarget(renderPass, framebuffer, true, true);
    }

    long createPipelineCompatibleRenderPass(
        VkDevice device,
        MemoryStack stack,
        VulkanRenderPassCompatibilityKey compatibilityKey,
        VkResultChecker checker
    ) {
        Objects.requireNonNull(compatibilityKey, "compatibilityKey");
        VulkanRenderPassLayoutPlanner.RenderPassPlan layoutPlan =
            VulkanRenderPassLayoutPlanner.planPipelineCompatible(compatibilityKey);
        return createRenderPass(device, stack, "vkCreateRenderPass(pipeline-compatible)", layoutPlan, checker);
    }

    PresentTargets createSwapchainPresentTargets(
        VkDevice device,
        MemoryStack stack,
        List<Long> imageViewHandles,
        int imageFormat,
        int width,
        int height,
        VkResultChecker checker
    ) {
        Objects.requireNonNull(imageViewHandles, "imageViewHandles");
        if (imageViewHandles.isEmpty()) {
            return new PresentTargets(VK10.VK_NULL_HANDLE, List.of());
        }

        VulkanRenderPassLayoutPlanner.RenderPassPlan layoutPlan =
            VulkanRenderPassLayoutPlanner.planPipelineCompatible(
                VulkanRenderPassCompatibilityKey.swapchainPresent(imageFormat)
            );
        long renderPass = VK10.VK_NULL_HANDLE;
        List<Long> framebuffers = new ArrayList<>(imageViewHandles.size());
        try {
            renderPass = createRenderPass(device, stack, "vkCreateRenderPass(swapchainPresent)", layoutPlan, checker);
            for (long imageViewHandle : imageViewHandles) {
                long framebuffer = createFramebuffer(
                    device,
                    stack,
                    "vkCreateFramebuffer(swapchainPresent)",
                    renderPass,
                    new long[] {imageViewHandle},
                    width,
                    height,
                    1,
                    checker
                );
                framebuffers.add(framebuffer);
            }
            return new PresentTargets(renderPass, framebuffers);
        } catch (RuntimeException exception) {
            for (long framebuffer : framebuffers) {
                destroyFramebufferNow(device, framebuffer);
            }
            destroyRenderPassNow(device, renderPass);
            throw exception;
        }
    }

    long createRenderPass(
        VkDevice device,
        MemoryStack stack,
        String operation,
        VulkanRenderPassLayoutPlanner.RenderPassPlan layoutPlan,
        VkResultChecker checker
    ) {
        Objects.requireNonNull(device, "device");
        Objects.requireNonNull(stack, "stack");
        Objects.requireNonNull(layoutPlan, "layoutPlan");
        Objects.requireNonNull(checker, "checker");

        int colorCount = layoutPlan.colorAttachments().size();
        int attachmentCount = colorCount + (layoutPlan.hasDepthAttachment() ? 1 : 0);
        VkAttachmentDescription.Buffer attachments = attachmentCount == 0
            ? null
            : VkAttachmentDescription.calloc(attachmentCount, stack);
        for (int colorIndex = 0; colorIndex < colorCount; colorIndex++) {
            writeAttachmentDescription(attachments.get(colorIndex), layoutPlan.colorAttachment(colorIndex));
        }
        if (layoutPlan.hasDepthAttachment()) {
            writeAttachmentDescription(attachments.get(colorCount), layoutPlan.depthAttachment());
        }

        VkAttachmentReference.Buffer colorReferences = colorCount == 0
            ? null
            : VkAttachmentReference.calloc(colorCount, stack);
        for (int colorIndex = 0; colorIndex < colorCount; colorIndex++) {
            colorReferences.get(colorIndex)
                .attachment(colorIndex)
                .layout(layoutPlan.colorAttachment(colorIndex).subpassLayout());
        }

        VkAttachmentReference depthReference = null;
        if (layoutPlan.hasDepthAttachment()) {
            depthReference = VkAttachmentReference.calloc(stack)
                .attachment(colorCount)
                .layout(layoutPlan.depthAttachment().subpassLayout());
        }

        VkSubpassDescription.Buffer subpasses = VkSubpassDescription.calloc(1, stack);
        subpasses.get(0)
            .pipelineBindPoint(VK10.VK_PIPELINE_BIND_POINT_GRAPHICS)
            .colorAttachmentCount(colorCount)
            .pColorAttachments(colorReferences)
            .pDepthStencilAttachment(depthReference);

        VkSubpassDependency.Buffer dependencies = allocateSubpassDependencies(stack, layoutPlan.dependencyIntent());
        VkRenderPassCreateInfo renderPassCreateInfo = VkRenderPassCreateInfo.calloc(stack)
            .sType$Default()
            .pAttachments(attachments)
            .pSubpasses(subpasses)
            .pDependencies(dependencies);

        java.nio.LongBuffer pRenderPass = stack.mallocLong(1);
        checker.check(operation, VK10.vkCreateRenderPass(device, renderPassCreateInfo, null, pRenderPass));
        return pRenderPass.get(0);
    }

    private static void writeAttachmentDescription(
        VkAttachmentDescription target,
        VulkanRenderPassLayoutPlanner.AttachmentPlan plan
    ) {
        target.format(plan.format())
            .samples(VK10.VK_SAMPLE_COUNT_1_BIT)
            .loadOp(plan.loadOp())
            .storeOp(plan.storeOp())
            .stencilLoadOp(plan.stencilLoadOp())
            .stencilStoreOp(plan.stencilStoreOp())
            .initialLayout(plan.initialLayout())
            .finalLayout(plan.finalLayout());
    }

    private static VkSubpassDependency.Buffer allocateSubpassDependencies(
        MemoryStack stack,
        List<VulkanRenderPassLayoutPlanner.SubpassDependencyPlan> dependencyIntent
    ) {
        VkSubpassDependency.Buffer dependencies = VkSubpassDependency.calloc(dependencyIntent.size(), stack);
        for (int index = 0; index < dependencyIntent.size(); index++) {
            VulkanRenderPassLayoutPlanner.SubpassDependencyPlan source = dependencyIntent.get(index);
            dependencies.get(index)
                .srcSubpass(source.srcSubpass())
                .dstSubpass(source.dstSubpass())
                .srcStageMask(source.srcStageMask())
                .dstStageMask(source.dstStageMask())
                .srcAccessMask(source.srcAccessMask())
                .dstAccessMask(source.dstAccessMask())
                .dependencyFlags(source.dependencyFlags());
        }
        return dependencies;
    }

    long createFramebuffer(
        VkDevice device,
        MemoryStack stack,
        String operation,
        long renderPass,
        long[] attachmentViewHandles,
        int width,
        int height,
        int layers,
        VkResultChecker checker
    ) {
        Objects.requireNonNull(device, "device");
        Objects.requireNonNull(stack, "stack");
        Objects.requireNonNull(attachmentViewHandles, "attachmentViewHandles");
        Objects.requireNonNull(checker, "checker");
        java.nio.LongBuffer attachments = attachmentViewHandles.length == 0
            ? null
            : stack.mallocLong(attachmentViewHandles.length);
        for (int index = 0; index < attachmentViewHandles.length; index++) {
            attachments.put(index, attachmentViewHandles[index]);
        }

        VkFramebufferCreateInfo framebufferCreateInfo = VkFramebufferCreateInfo.calloc(stack)
            .sType$Default()
            .renderPass(renderPass)
            .pAttachments(attachments)
            .width(width)
            .height(height)
            .layers(layers);

        java.nio.LongBuffer pFramebuffer = stack.mallocLong(1);
        checker.check(operation, VK10.vkCreateFramebuffer(device, framebufferCreateInfo, null, pFramebuffer));
        return pFramebuffer.get(0);
    }

    boolean isCachedRenderPass(long renderPassHandle) {
        return renderPassHandle != VK10.VK_NULL_HANDLE && cachedRenderPassHandles.contains(renderPassHandle);
    }

    boolean isCachedFramebuffer(long framebufferHandle) {
        return framebufferHandle != VK10.VK_NULL_HANDLE && cachedFramebufferHandles.contains(framebufferHandle);
    }

    InvalidationResult invalidateForTexture(int textureId, HandleDestroyer destroyer) {
        if (textureId < 0) {
            return InvalidationResult.empty();
        }
        List<FramebufferKey> invalidFramebuffers = framebufferCache.keySet().stream()
            .filter(key -> key.containsTexture(textureId))
            .toList();
        int destroyedFramebuffers = 0;
        for (FramebufferKey key : invalidFramebuffers) {
            Long handle = framebufferCache.remove(key);
            if (handle != null) {
                cachedFramebufferHandles.remove(handle);
                destroyer.destroyFramebuffer(handle);
                destroyedFramebuffers++;
            }
        }
        return new InvalidationResult(0, destroyedFramebuffers);
    }

    InvalidationResult invalidateAll(HandleDestroyer destroyer) {
        Objects.requireNonNull(destroyer, "destroyer");
        int destroyedFramebuffers = 0;
        for (long framebuffer : new ArrayList<>(framebufferCache.values())) {
            if (framebuffer != VK10.VK_NULL_HANDLE) {
                destroyer.destroyFramebuffer(framebuffer);
                destroyedFramebuffers++;
            }
        }
        framebufferCache.clear();
        cachedFramebufferHandles.clear();

        int destroyedRenderPasses = 0;
        for (long renderPass : new ArrayList<>(renderPassCache.values())) {
            if (renderPass != VK10.VK_NULL_HANDLE) {
                destroyer.destroyRenderPass(renderPass);
                destroyedRenderPasses++;
            }
        }
        renderPassCache.clear();
        cachedRenderPassHandles.clear();
        return new InvalidationResult(destroyedRenderPasses, destroyedFramebuffers);
    }

    void destroyFramebufferNow(VkDevice device, long framebufferHandle) {
        if (device != null && framebufferHandle != VK10.VK_NULL_HANDLE) {
            VK10.vkDestroyFramebuffer(device, framebufferHandle, null);
        }
    }

    void destroyRenderPassNow(VkDevice device, long renderPassHandle) {
        if (device != null && renderPassHandle != VK10.VK_NULL_HANDLE) {
            VK10.vkDestroyRenderPass(device, renderPassHandle, null);
        }
    }

    int cachedRenderPassCountForTests() {
        return renderPassCache.size();
    }

    int cachedFramebufferCountForTests() {
        return framebufferCache.size();
    }

    void cacheRenderPassForTests(VulkanRenderPassKey key, long handle) {
        renderPassCache.put(key, handle);
        cachedRenderPassHandles.add(handle);
    }

    void cacheFramebufferForTests(FramebufferKey key, long handle) {
        framebufferCache.put(key, handle);
        cachedFramebufferHandles.add(handle);
    }

    static FramebufferKey framebufferKeyForTests(
        VulkanRenderPassKey renderPassKey,
        long[] attachmentViewHandles,
        int[] attachmentTextureIds,
        int width,
        int height,
        int layers
    ) {
        return new FramebufferKey(renderPassKey, attachmentViewHandles, attachmentTextureIds, width, height, layers);
    }

    private static String renderPassOperation(String operation) {
        return operation.startsWith("vkCreateRenderPass")
            ? operation
            : "vkCreateRenderPass(" + operation + ")";
    }

    private static String framebufferOperation(String operation) {
        return operation.startsWith("vkCreateFramebuffer")
            ? operation
            : "vkCreateFramebuffer(" + operation + ")";
    }

    interface VkResultChecker {
        void check(String operation, int result);
    }

    interface HandleDestroyer {
        void destroyRenderPass(long renderPassHandle);

        void destroyFramebuffer(long framebufferHandle);
    }

    record NativeRenderTarget(
        long renderPassHandle,
        long framebufferHandle,
        boolean cachedRenderPass,
        boolean cachedFramebuffer
    ) {}

    record PresentTargets(long renderPassHandle, List<Long> framebufferHandles) {
        PresentTargets {
            framebufferHandles = List.copyOf(framebufferHandles);
        }
    }

    record InvalidationResult(int renderPasses, int framebuffers) {
        static InvalidationResult empty() {
            return new InvalidationResult(0, 0);
        }
    }

    record FramebufferKey(
        VulkanRenderPassKey renderPassKey,
        long[] attachmentViewHandles,
        int[] attachmentTextureIds,
        int width,
        int height,
        int layers
    ) {
        FramebufferKey {
            Objects.requireNonNull(renderPassKey, "renderPassKey");
            attachmentViewHandles = Objects.requireNonNull(attachmentViewHandles, "attachmentViewHandles").clone();
            attachmentTextureIds = Objects.requireNonNull(attachmentTextureIds, "attachmentTextureIds").clone();
        }

        boolean containsTexture(int textureId) {
            for (int candidate : attachmentTextureIds) {
                if (candidate == textureId) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof FramebufferKey key)) {
                return false;
            }
            return width == key.width
                && height == key.height
                && layers == key.layers
                && renderPassKey.equals(key.renderPassKey)
                && java.util.Arrays.equals(attachmentViewHandles, key.attachmentViewHandles)
                && java.util.Arrays.equals(attachmentTextureIds, key.attachmentTextureIds);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(renderPassKey, width, height, layers);
            result = 31 * result + java.util.Arrays.hashCode(attachmentViewHandles);
            result = 31 * result + java.util.Arrays.hashCode(attachmentTextureIds);
            return result;
        }
    }
}
