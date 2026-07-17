package net.vulkanic.backends.vulkan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class VulkanDeferredResourceLifetimeTest {
    @Test
    void destroysResourcesOnlyAfterSubmittedFenceCompletes() {
        VulkanDeferredResourceLifetime<String> lifetime = new VulkanDeferredResourceLifetime<>(2, 2);
        List<String> destroyed = new ArrayList<>();

        long generation = lifetime.reserveFrameWorkGeneration(0);
        lifetime.enqueueDestroy(true, true, true, 0, false, -1, () -> destroyed.add("image"));
        lifetime.registerSubmittedWork(101L, generation);

        lifetime.flushPendingDestroys(true, false);
        assertTrue(destroyed.isEmpty(), "resource must remain alive while its submitted fence is incomplete");
        assertEquals(1, lifetime.pendingDestroyCountForTests());

        lifetime.markFenceComplete(101L, true);
        assertEquals(List.of("image"), destroyed);
        assertEquals(0, lifetime.pendingDestroyCountForTests());
    }

    @Test
    void preservesGenerationOrderingAcrossFrameAndImmediateSubmits() {
        VulkanDeferredResourceLifetime<String> lifetime = new VulkanDeferredResourceLifetime<>(2, 2);
        List<String> destroyed = new ArrayList<>();

        long frameGeneration = lifetime.reserveFrameWorkGeneration(0);
        lifetime.enqueueDestroy(true, true, true, 0, false, -1, () -> destroyed.add("frame-resource"));
        lifetime.registerSubmittedWork(201L, frameGeneration);

        long immediateGeneration = lifetime.reserveImmediateWorkGeneration(1);
        lifetime.enqueueDestroy(true, true, false, -1, true, 1, () -> destroyed.add("immediate-resource"));
        lifetime.registerSubmittedWork(202L, immediateGeneration);

        lifetime.markFenceComplete(201L, true);
        assertEquals(List.of("frame-resource"), destroyed);
        assertEquals(1, lifetime.pendingDestroyCountForTests());

        lifetime.markFenceComplete(202L, true);
        assertEquals(List.of("frame-resource", "immediate-resource"), destroyed);
        assertEquals(0, lifetime.pendingDestroyCountForTests());
    }

    @Test
    void duplicateTransientHandleRetirementIsHarmless() {
        VulkanDeferredResourceLifetime<String> lifetime = new VulkanDeferredResourceLifetime<>(2, 2);
        List<String> retired = new ArrayList<>();

        lifetime.trackTransientRenderPassHandle(11L, -1);
        lifetime.trackTransientRenderPassHandle(11L, -1);
        lifetime.trackTransientFramebufferHandle(22L, 0);
        lifetime.trackTransientFramebufferHandle(22L, 0);

        lifetime.retireGlobalTransientResources(
            resource -> retired.add("descriptor:" + resource),
            handle -> retired.add("framebuffer:" + handle),
            handle -> retired.add("renderpass:" + handle)
        );
        lifetime.retireImmediateTransientResources(
            0,
            resource -> retired.add("descriptor:" + resource),
            handle -> retired.add("framebuffer:" + handle),
            handle -> retired.add("renderpass:" + handle)
        );

        assertEquals(List.of("renderpass:11", "framebuffer:22"), retired);
        assertEquals(0, lifetime.transientRenderPassCountForTests());
        assertEquals(0, lifetime.transientRenderPassCountForTests(0));
    }

    @Test
    void forcedShutdownDrainsOrDropsSafelyDependingOnDeviceAvailability() {
        VulkanDeferredResourceLifetime<String> lifetime = new VulkanDeferredResourceLifetime<>(2, 2);
        List<String> destroyed = new ArrayList<>();

        long generation = lifetime.reserveFrameWorkGeneration(0);
        lifetime.enqueueDestroy(true, true, true, 0, false, -1, () -> destroyed.add("pending"));
        lifetime.registerSubmittedWork(301L, generation);

        lifetime.flushPendingDestroys(true, true);
        assertEquals(List.of("pending"), destroyed);
        assertEquals(0, lifetime.pendingDestroyCountForTests());
        assertEquals(lifetime.submittedWorkGenerationForTests(), lifetime.completedWorkGenerationForTests());

        lifetime.enqueueDestroy(true, true, false, -1, false, -1, () -> destroyed.add("dropped"));
        lifetime.flushPendingDestroys(false, true);
        assertEquals(List.of("pending"), destroyed, "device-loss cleanup must not call Vulkan destroy callbacks");
        assertEquals(0, lifetime.pendingDestroyCountForTests());
    }

    @Test
    void preservesDestructionOrderingForResourceKindsAndTransients() {
        VulkanDeferredResourceLifetime<String> lifetime = new VulkanDeferredResourceLifetime<>(2, 2);
        List<String> destroyed = new ArrayList<>();

        long generation = lifetime.reserveFrameWorkGeneration(1);
        lifetime.enqueueDestroy(true, true, true, 1, false, -1, () -> destroyed.add("buffer"));
        lifetime.enqueueDestroy(true, true, true, 1, false, -1, () -> destroyed.add("image"));
        lifetime.enqueueDestroy(true, true, true, 1, false, -1, () -> destroyed.add("view"));
        lifetime.enqueueDestroy(true, true, true, 1, false, -1, () -> destroyed.add("sampler"));
        lifetime.enqueueDestroy(true, true, true, 1, false, -1, () -> destroyed.add("pipeline"));
        lifetime.registerSubmittedWork(401L, generation);

        lifetime.markFenceComplete(401L, true);
        assertEquals(List.of("buffer", "image", "view", "sampler", "pipeline"), destroyed);

        lifetime.trackTransientDescriptorResource("desc-a", -1);
        lifetime.trackTransientFramebufferHandle(501L, -1);
        lifetime.trackTransientRenderPassHandle(502L, -1);
        lifetime.trackFrameDescriptorResource(0, "frame-desc-a");

        lifetime.retireGlobalTransientResources(
            resource -> destroyed.add("descriptor:" + resource),
            handle -> destroyed.add("framebuffer:" + handle),
            handle -> destroyed.add("renderpass:" + handle)
        );
        lifetime.retireFrameDescriptorResources(0, resource -> destroyed.add("frame-descriptor:" + resource));

        assertEquals(List.of(
            "buffer",
            "image",
            "view",
            "sampler",
            "pipeline",
            "descriptor:desc-a",
            "framebuffer:501",
            "renderpass:502",
            "frame-descriptor:frame-desc-a"
        ), destroyed);
        assertEquals(0, lifetime.transientDescriptorCountForTests());
        assertEquals(0, lifetime.transientFramebufferCountForTests());
        assertEquals(0, lifetime.transientFrameDescriptorCountForTests(0));
    }
}
