package net.vulkanic.backends.vulkan;

import net.vulkanic.VulkanicRenderPassDescriptor;
import net.vulkanic.VulkanicResourceUsage;
import org.junit.jupiter.api.Test;
import org.lwjgl.vulkan.EXTAttachmentFeedbackLoopLayout;
import org.lwjgl.vulkan.KHRSwapchain;
import org.lwjgl.vulkan.VK10;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class VulkanRenderPassExecutionCoordinatorTest {
    @Test
    void defaultFramebufferColorDepthPassPublishesActiveAndFinalLayouts() {
        TestHarness harness = new TestHarness();
        TestAttachment color = harness.texture(1, VK10.VK_IMAGE_ASPECT_COLOR_BIT);
        TestAttachment depth = harness.texture(2, VK10.VK_IMAGE_ASPECT_DEPTH_BIT);

        VulkanRenderPassExecutionCoordinator.BeginPassPlan<TestAttachment, TestAttachment> plan =
            harness.coordinator.planFramebufferPass(
                "default",
                640,
                480,
                List.of(color(color, VK10.VK_FORMAT_R8G8B8A8_UNORM)),
                depth(depth, VK10.VK_FORMAT_D32_SFLOAT)
            );

        harness.coordinator.completeBegin(plan, 100, 200);

        assertTrue(harness.coordinator.isRenderPassActive());
        assertEquals(640, harness.coordinator.activeWidth());
        assertEquals(480, harness.coordinator.activeHeight());
        assertEquals(1, harness.coordinator.activeColorAttachmentCount());
        assertTrue(harness.coordinator.hasActiveDepthAttachment());
        assertEquals(VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL, harness.tracker.layoutFor(color.textureId(), 0, -1));
        assertEquals(VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL, harness.tracker.layoutFor(depth.textureId(), 0, -1));

        VulkanRenderPassExecutionCoordinator.EndPassResult<TestAttachment, TestAttachment> end =
            harness.coordinator.completeEnd();

        assertFalse(harness.coordinator.isRenderPassActive());
        assertEquals(List.of(new VulkanRenderPassExecutionCoordinator.FinalLayout<>(
            color,
            VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL
        )), end.colorFinalLayouts());
        assertEquals(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, harness.tracker.layoutFor(color.textureId(), 0, -1));
        assertEquals(VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL, harness.tracker.layoutFor(depth.textureId(), 0, -1));
    }

    @Test
    void virtualFramebufferMultipleColorAttachmentsKeepOrderingAndRenderArea() {
        TestHarness harness = new TestHarness();
        int framebuffer = harness.virtualFramebuffers.createFramebuffer();
        harness.virtualFramebuffers.recordAttachment(framebuffer, 0x8CE0, 11);
        harness.virtualFramebuffers.recordAttachment(framebuffer, 0x8CE1, 12);
        VulkanVirtualFramebufferManager.FramebufferSnapshot snapshot =
            harness.coordinator.resolveFramebufferSnapshot(framebuffer);

        TestAttachment color0 = harness.texture(11, VK10.VK_IMAGE_ASPECT_COLOR_BIT);
        TestAttachment color1 = harness.texture(12, VK10.VK_IMAGE_ASPECT_COLOR_BIT);
        VulkanRenderPassExecutionCoordinator.BeginPassPlan<TestAttachment, TestAttachment> plan =
            harness.coordinator.planFramebufferPass(
                "virtual-fbo",
                320,
                200,
                List.of(
                    color(color0, VK10.VK_FORMAT_R8G8B8A8_UNORM),
                    color(color1, VK10.VK_FORMAT_R16G16B16A16_SFLOAT)
                ),
                null
            );

        assertEquals(11, snapshot.attachment(0x8CE0));
        assertEquals(12, snapshot.attachment(0x8CE1));
        assertEquals(2, plan.layoutPlan().colorAttachments().size());
        assertEquals(2, plan.compatibilityKey().colorAttachmentCount());

        harness.coordinator.completeBegin(plan, 101, 201);
        assertEquals(2, harness.coordinator.activeColorAttachmentCount());
        assertEquals(320, harness.coordinator.activeWidth());
        assertEquals(200, harness.coordinator.activeHeight());
    }

    @Test
    void depthOnlyColorOnlyAndClearPassesPreserveAttachmentPolicy() {
        TestHarness harness = new TestHarness();
        TestAttachment depth = harness.texture(21, VK10.VK_IMAGE_ASPECT_DEPTH_BIT);
        VulkanRenderPassExecutionCoordinator.BeginPassPlan<TestAttachment, TestAttachment> depthOnly =
            harness.coordinator.planFramebufferPass(
                "depth-only",
                64,
                64,
                List.of(),
                depth(depth, VK10.VK_FORMAT_D32_SFLOAT)
            );

        assertEquals(0, depthOnly.compatibilityKey().colorAttachmentCount());
        assertTrue(depthOnly.compatibilityKey().hasDepthAttachment());
        harness.coordinator.abandonActivePass();

        TestAttachment color = harness.texture(22, VK10.VK_IMAGE_ASPECT_COLOR_BIT);
        VulkanRenderPassExecutionCoordinator.BeginPassPlan<TestAttachment, TestAttachment> clearColor =
            harness.coordinator.planFramebufferPass(
                "clear-color",
                64,
                64,
                List.of(color(
                    color,
                    VK10.VK_FORMAT_R8G8B8A8_UNORM,
                    false,
                    VulkanicRenderPassDescriptor.LoadOp.CLEAR,
                    VulkanicRenderPassDescriptor.StoreOp.DONT_CARE,
                    OptionalInt.of(0xFF336699)
                )),
                null
            );

        assertEquals(1, clearColor.compatibilityKey().colorAttachmentCount());
        assertFalse(clearColor.compatibilityKey().hasDepthAttachment());
        assertTrue(clearColor.layoutPlan().colorAttachment(0).clear());
        assertEquals(VK10.VK_ATTACHMENT_STORE_OP_DONT_CARE, clearColor.layoutPlan().colorAttachment(0).storeOp());
    }

    @Test
    void feedbackLoopPassUsesFeedbackLayoutsAndPublishesThroughImageTracker() {
        TestHarness harness = new TestHarness();
        TestAttachment color = harness.texture(31, VK10.VK_IMAGE_ASPECT_COLOR_BIT);
        VulkanRenderPassExecutionCoordinator.BeginPassPlan<TestAttachment, TestAttachment> plan =
            harness.coordinator.planFramebufferPass(
                "feedback",
                128,
                128,
                List.of(color(color, VK10.VK_FORMAT_R8G8B8A8_UNORM, true)),
                null
            );

        assertTrue(plan.compatibilityKey().feedbackLoop());
        assertEquals(
            EXTAttachmentFeedbackLoopLayout.VK_IMAGE_LAYOUT_ATTACHMENT_FEEDBACK_LOOP_OPTIMAL_EXT,
            plan.layoutPlan().colorAttachment(0).subpassLayout()
        );

        harness.coordinator.completeBegin(plan, 102, 202);
        assertEquals(
            EXTAttachmentFeedbackLoopLayout.VK_IMAGE_LAYOUT_ATTACHMENT_FEEDBACK_LOOP_OPTIMAL_EXT,
            harness.tracker.layoutFor(color.textureId(), 0, -1)
        );
        harness.coordinator.completeEnd();
        assertEquals(
            plan.layoutPlan().colorAttachment(0).finalLayout(),
            harness.tracker.layoutFor(color.textureId(), 0, -1)
        );
    }

    @Test
    void attachmentReplacementAndRepeatedCyclesDoNotLeaveStaleActiveState() {
        TestHarness harness = new TestHarness();
        TestAttachment oldColor = harness.texture(41, VK10.VK_IMAGE_ASPECT_COLOR_BIT);
        TestAttachment newColor = harness.texture(42, VK10.VK_IMAGE_ASPECT_COLOR_BIT);

        harness.beginAndEnd("old", oldColor);
        assertFalse(harness.coordinator.isActiveAttachment(oldColor));

        VulkanRenderPassExecutionCoordinator.BeginPassPlan<TestAttachment, TestAttachment> second =
            harness.coordinator.planFramebufferPass(
                "new",
                90,
                70,
                List.of(color(newColor, VK10.VK_FORMAT_R8G8B8A8_UNORM)),
                null
            );
        harness.coordinator.completeBegin(second, 103, 203);

        assertFalse(harness.coordinator.isActiveAttachment(oldColor));
        assertTrue(harness.coordinator.isActiveAttachment(newColor));
        assertEquals(List.of(42), new ArrayList<>(harness.coordinator.activeAttachmentTextureIds()));

        harness.coordinator.completeEnd();
        assertFalse(harness.coordinator.isRenderPassActive());
    }

    @Test
    void compatibleFramebufferReuseInvalidationAndDuplicateAbandonAreSafe() {
        TestHarness harness = new TestHarness();
        TestAttachment color = harness.texture(51, VK10.VK_IMAGE_ASPECT_COLOR_BIT);
        VulkanRenderPassExecutionCoordinator.BeginPassPlan<TestAttachment, TestAttachment> plan =
            harness.coordinator.planFramebufferPass(
                "cacheable",
                32,
                32,
                List.of(color(color, VK10.VK_FORMAT_R8G8B8A8_UNORM)),
                null
            );
        List<Long> destroyed = new ArrayList<>();

        assertNull(harness.coordinator.cachedRenderPass(plan));
        harness.coordinator.cacheRenderPass(plan, 700);
        assertEquals(700L, harness.coordinator.cachedRenderPass(plan));

        harness.coordinator.completeBegin(plan, 700, 800);
        harness.coordinator.abandonActivePass();
        harness.coordinator.abandonActivePass();
        assertFalse(harness.coordinator.isRenderPassActive());

        harness.coordinator.invalidateForResizeDeviceLossOrShutdown(destroyed::add);
        assertEquals(List.of(700L), destroyed);
        assertNull(harness.coordinator.cachedRenderPass(plan));
    }

    @Test
    void presentComposeSwapchainPassTracksSwapchainTargetWithoutTexturePublication() {
        TestHarness harness = new TestHarness();
        VulkanRenderPassExecutionCoordinator.PresentComposeTargetPlan target =
            harness.coordinator.planPresentComposeTarget(
                2,
                0xCAFE,
                0xBEEF,
                1920,
                1080,
                VK10.VK_FORMAT_B8G8R8A8_UNORM
            );
        VulkanRenderPassExecutionCoordinator.AttachmentRequest<TestAttachment> swapchainColor =
            VulkanRenderPassExecutionCoordinator.AttachmentRequest.color(
                null,
                -1,
                target.imageViewHandle(),
                target.format(),
                false,
                true,
                target.swapchainImageIndex(),
                KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR,
                VulkanicRenderPassDescriptor.LoadOp.CLEAR,
                VulkanicRenderPassDescriptor.StoreOp.STORE,
                OptionalInt.empty(),
                VulkanicResourceUsage.INFERRED,
                VulkanicResourceUsage.INFERRED,
                VulkanicResourceUsage.INFERRED
            );

        VulkanRenderPassExecutionCoordinator.BeginPassPlan<TestAttachment, TestAttachment> pass =
            harness.coordinator.planTextureViewPass(
                "present-compose",
                target.width(),
                target.height(),
                swapchainColor,
                null,
                true
            );
        harness.coordinator.completeBegin(pass, 900, 901);

        assertTrue(harness.coordinator.activeTargetsSwapchain());
        assertEquals(2, harness.coordinator.activeSwapchainImageIndex());
        assertEquals(1, harness.coordinator.activeColorAttachmentCount());

        VulkanRenderPassExecutionCoordinator.EndPassResult<TestAttachment, TestAttachment> end =
            harness.coordinator.completeEnd();
        assertEquals(2, end.swapchainImageIndex());
        assertEquals(KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR, end.swapchainFinalLayout());
        assertTrue(end.colorFinalLayouts().isEmpty());
    }

    @Test
    void incompatibleAttachmentAndInvalidPresentComposeRequestsAreRejected() {
        TestHarness harness = new TestHarness();
        TestAttachment depth = harness.texture(61, VK10.VK_IMAGE_ASPECT_DEPTH_BIT);

        assertThrows(
            IllegalArgumentException.class,
            () -> harness.coordinator.planTextureViewPass(
                "bad-texture-view",
                16,
                16,
                depth(depth, VK10.VK_FORMAT_D32_SFLOAT),
                null,
                false
            )
        );
        assertThrows(
            IllegalStateException.class,
            () -> harness.coordinator.planPresentComposeTarget(
                0,
                VK10.VK_NULL_HANDLE,
                1,
                16,
                16,
                VK10.VK_FORMAT_B8G8R8A8_UNORM
            )
        );
    }

    @Test
    void equivalentPlansHaveStableKeysAndIncompatibleLayoutsDoNotShareIdentity() {
        TestHarness harness = new TestHarness();
        TestAttachment colorA = harness.texture(71, VK10.VK_IMAGE_ASPECT_COLOR_BIT);
        TestAttachment colorB = harness.texture(72, VK10.VK_IMAGE_ASPECT_COLOR_BIT);

        VulkanRenderPassExecutionCoordinator.BeginPassPlan<TestAttachment, TestAttachment> first =
            harness.coordinator.planFramebufferPass(
                "first",
                16,
                16,
                List.of(color(colorA, VK10.VK_FORMAT_R8G8B8A8_UNORM)),
                null
            );
        VulkanRenderPassExecutionCoordinator.BeginPassPlan<TestAttachment, TestAttachment> equivalent =
            harness.coordinator.planFramebufferPass(
                "renamed",
                16,
                16,
                List.of(color(colorB, VK10.VK_FORMAT_R8G8B8A8_UNORM)),
                null
            );
        VulkanRenderPassExecutionCoordinator.BeginPassPlan<TestAttachment, TestAttachment> differentFormat =
            harness.coordinator.planFramebufferPass(
                "different",
                16,
                16,
                List.of(color(colorB, VK10.VK_FORMAT_R16G16B16A16_SFLOAT)),
                null
            );

        assertEquals(first.renderPassKey(), equivalent.renderPassKey());
        assertEquals(first.compatibilityKey(), equivalent.compatibilityKey());
        assertNotEquals(first.renderPassKey(), differentFormat.renderPassKey());
        assertNotEquals(first.compatibilityKey(), differentFormat.compatibilityKey());
    }

    private static VulkanRenderPassExecutionCoordinator.AttachmentRequest<TestAttachment> color(
        TestAttachment attachment,
        int format
    ) {
        return color(
            attachment,
            format,
            false,
            VulkanicRenderPassDescriptor.LoadOp.LOAD,
            VulkanicRenderPassDescriptor.StoreOp.STORE,
            OptionalInt.empty()
        );
    }

    private static VulkanRenderPassExecutionCoordinator.AttachmentRequest<TestAttachment> color(
        TestAttachment attachment,
        int format,
        boolean feedbackLoop
    ) {
        return color(
            attachment,
            format,
            feedbackLoop,
            VulkanicRenderPassDescriptor.LoadOp.LOAD,
            VulkanicRenderPassDescriptor.StoreOp.STORE,
            OptionalInt.empty()
        );
    }

    private static VulkanRenderPassExecutionCoordinator.AttachmentRequest<TestAttachment> color(
        TestAttachment attachment,
        int format,
        boolean feedbackLoop,
        VulkanicRenderPassDescriptor.LoadOp loadOp,
        VulkanicRenderPassDescriptor.StoreOp storeOp,
        OptionalInt clearColor
    ) {
        return VulkanRenderPassExecutionCoordinator.AttachmentRequest.color(
            attachment,
            attachment.textureId(),
            attachment.textureId() * 100L,
            format,
            feedbackLoop,
            false,
            -1,
            VK10.VK_IMAGE_LAYOUT_UNDEFINED,
            loadOp,
            storeOp,
            clearColor,
            VulkanicResourceUsage.INFERRED,
            VulkanicResourceUsage.INFERRED,
            VulkanicResourceUsage.INFERRED
        );
    }

    private static VulkanRenderPassExecutionCoordinator.AttachmentRequest<TestAttachment> depth(
        TestAttachment attachment,
        int format
    ) {
        return VulkanRenderPassExecutionCoordinator.AttachmentRequest.depth(
            attachment,
            attachment.textureId(),
            attachment.textureId() * 100L,
            format,
            false,
            format == VK10.VK_FORMAT_D24_UNORM_S8_UINT,
            VK10.VK_IMAGE_LAYOUT_UNDEFINED,
            VulkanicRenderPassDescriptor.LoadOp.LOAD,
            VulkanicRenderPassDescriptor.StoreOp.STORE,
            OptionalDouble.empty(),
            VulkanicResourceUsage.INFERRED,
            VulkanicResourceUsage.INFERRED,
            VulkanicResourceUsage.INFERRED
        );
    }

    private static final class TestHarness {
        final VulkanVirtualFramebufferManager virtualFramebuffers = new VulkanVirtualFramebufferManager();
        final VulkanRenderTargetStateManager<TestAttachment, TestAttachment> renderTargetState =
            new VulkanRenderTargetStateManager<>();
        final VulkanImageStateTracker tracker = new VulkanImageStateTracker();
        final VulkanRenderPassExecutionCoordinator<TestAttachment, TestAttachment> coordinator =
            new VulkanRenderPassExecutionCoordinator<>(virtualFramebuffers, renderTargetState, tracker);

        TestAttachment texture(int textureId, int aspectMask) {
            tracker.registerTexture(
                textureId,
                textureId * 4096L,
                aspectMask,
                1,
                1,
                false,
                VK10.VK_IMAGE_LAYOUT_UNDEFINED
            );
            return new TestAttachment(textureId);
        }

        void beginAndEnd(String label, TestAttachment color) {
            VulkanRenderPassExecutionCoordinator.BeginPassPlan<TestAttachment, TestAttachment> plan =
                coordinator.planFramebufferPass(
                    label,
                    32,
                    32,
                    List.of(color(color, VK10.VK_FORMAT_R8G8B8A8_UNORM)),
                    null
                );
            coordinator.completeBegin(plan, 300, 400);
            coordinator.completeEnd();
        }
    }

    private record TestAttachment(int textureId) implements VulkanRenderPassExecutionCoordinator.TextureIdentity {}
}
