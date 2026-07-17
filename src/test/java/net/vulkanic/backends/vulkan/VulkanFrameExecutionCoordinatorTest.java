package net.vulkanic.backends.vulkan;

import org.junit.jupiter.api.Test;
import org.lwjgl.system.Pointer;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkCommandBuffer;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class VulkanFrameExecutionCoordinatorTest {
    private static final Unsafe UNSAFE = unsafe();
    private static final Field POINTER_ADDRESS = pointerAddressField();

    @Test
    void successfulAcquireRecordSubmitPresentSequencePublishesOneFrameGeneration() {
        Fixture fixture = readyFixture();

        VulkanFrameExecutionCoordinator.FrameBeginPlan begin = fixture.coordinator.planFrameBegin(false);
        assertEquals(0, begin.frameSlot());
        assertEquals(21L, begin.frameFence());
        assertEquals(11L, begin.imageAvailableSemaphore());
        fixture.coordinator.completeFrameFenceWait(begin, true, fixture.hooks());

        VulkanFrameExecutionCoordinator.AcquiredImagePlan acquired = fixture.coordinator.planAcquiredImage(0);
        fixture.coordinator.completeFrameAcquire(acquired);
        assertTrue(fixture.swapchain.frameInProgress());

        VulkanFrameExecutionCoordinator.FrameCommandBufferBeginPlan recording =
            fixture.coordinator.planFrameCommandBufferBegin();
        fixture.coordinator.completeFrameCommandBufferBegin(recording);
        assertTrue(fixture.swapchain.isCurrentFrameCommandBufferRecording());

        VulkanFrameExecutionCoordinator.FrameSubmitPlan submit =
            fixture.coordinator.planFrameCommandBufferSubmit();
        fixture.coordinator.completeFrameSubmit(submit);
        assertFalse(fixture.swapchain.isCurrentFrameCommandBufferRecording());
        assertEquals(submit.reservedGeneration(), fixture.lifetime.submittedWorkGenerationForTests());

        VulkanFrameExecutionCoordinator.PresentPlan present = fixture.coordinator.planPresent();
        fixture.coordinator.completePresent(present, VulkanFrameExecutionCoordinator.PresentResult.SUCCESS);

        assertFalse(fixture.swapchain.frameInProgress());
        assertEquals(-1, fixture.swapchain.acquiredImageIndex());
        assertEquals(1, fixture.swapchain.currentFrameSyncIndex());
    }

    @Test
    void multipleFrameSlotReuseCyclesRetireOnlyTheReusedFrameBucket() {
        Fixture fixture = readyFixture();

        cycleWithoutRecording(fixture, 0);
        fixture.lifetime.trackFrameDescriptorResource(1, "frame-one");

        VulkanFrameExecutionCoordinator.FrameBeginPlan secondBegin = fixture.coordinator.planFrameBegin(false);
        fixture.coordinator.completeFrameFenceWait(secondBegin, true, fixture.hooks());

        assertEquals(List.of("frame-one"), fixture.retiredDescriptors);
        cycleAfterCompletedBegin(fixture, 1);

        assertEquals(0, fixture.swapchain.currentFrameSyncIndex());
    }

    @Test
    void failedAcquireSkipsFrameAndPreservesOutOfDateStatus() {
        Fixture fixture = readyFixture();

        VulkanFrameExecutionCoordinator.FrameBeginPlan begin = fixture.coordinator.planFrameBegin(false);
        fixture.coordinator.completeFrameFenceWait(begin, true, fixture.hooks());
        fixture.coordinator.markAcquireOutOfDate();
        fixture.coordinator.skipFrameAndAdvance();

        assertTrue(fixture.swapchain.lastAcquireOutOfDate());
        assertFalse(fixture.swapchain.frameInProgress());
        assertEquals(1, fixture.swapchain.currentFrameSyncIndex());
    }

    @Test
    void commandRecordingFailureRollsBackCurrentFrameRecordingFlag() {
        Fixture fixture = readyFixture();
        beginAcquiredFrame(fixture, 0);

        VulkanFrameExecutionCoordinator.FrameCommandBufferBeginPlan begin =
            fixture.coordinator.planFrameCommandBufferBegin();
        fixture.coordinator.completeFrameCommandBufferBegin(begin);
        fixture.coordinator.failFrameCommandBufferBegin(begin);

        assertFalse(fixture.swapchain.isCurrentFrameCommandBufferRecording());
        assertTrue(fixture.swapchain.frameInProgress());
    }

    @Test
    void failedFrameSubmitBeforeQueueKeepsRecordingForNativeRecovery() {
        Fixture fixture = readyFixture();
        beginAcquiredFrame(fixture, 0);
        VulkanFrameExecutionCoordinator.FrameCommandBufferBeginPlan recording =
            fixture.coordinator.planFrameCommandBufferBegin();
        fixture.coordinator.completeFrameCommandBufferBegin(recording);

        VulkanFrameExecutionCoordinator.FrameSubmitPlan submit =
            fixture.coordinator.planFrameCommandBufferSubmit();
        fixture.coordinator.failFrameSubmit(submit, false);

        assertTrue(fixture.swapchain.isCurrentFrameCommandBufferRecording());
        assertEquals(0L, fixture.lifetime.completedWorkGenerationForTests());
    }

    @Test
    void failedFrameSubmitAfterPossibleQueueOwnershipPublishesGenerationAndStopsRecording() {
        Fixture fixture = readyFixture();
        beginAcquiredFrame(fixture, 0);
        fixture.coordinator.completeFrameCommandBufferBegin(fixture.coordinator.planFrameCommandBufferBegin());

        VulkanFrameExecutionCoordinator.FrameSubmitPlan submit =
            fixture.coordinator.planFrameCommandBufferSubmit();
        fixture.coordinator.failFrameSubmit(submit, true);

        assertFalse(fixture.swapchain.isCurrentFrameCommandBufferRecording());
        assertEquals(submit.reservedGeneration(), fixture.lifetime.submittedWorkGenerationForTests());
    }

    @Test
    void outOfDateAndSuboptimalPresentationUseSameFinalizationPath() {
        Fixture outOfDate = readyFixture();
        beginAcquiredFrame(outOfDate, 0);
        VulkanFrameExecutionCoordinator.PresentPlan outOfDatePresent = outOfDate.coordinator.planPresent();
        outOfDate.coordinator.completePresent(outOfDatePresent, VulkanFrameExecutionCoordinator.PresentResult.OUT_OF_DATE);

        assertTrue(outOfDate.swapchain.lastPresentOutOfDate());
        assertFalse(outOfDate.swapchain.frameInProgress());
        assertEquals(1, outOfDate.swapchain.currentFrameSyncIndex());

        Fixture suboptimal = readyFixture();
        beginAcquiredFrame(suboptimal, 1);
        VulkanFrameExecutionCoordinator.PresentPlan suboptimalPresent = suboptimal.coordinator.planPresent();
        suboptimal.coordinator.completePresent(suboptimalPresent, VulkanFrameExecutionCoordinator.PresentResult.SUBOPTIMAL);

        assertTrue(suboptimal.swapchain.lastPresentSuboptimal());
        assertFalse(suboptimal.swapchain.lastPresentOutOfDate());
        assertEquals(1, suboptimal.swapchain.currentFrameSyncIndex());
    }

    @Test
    void abandonedFrameCleanupIsIdempotent() {
        Fixture fixture = readyFixture();
        beginAcquiredFrame(fixture, 0);

        fixture.coordinator.abandonFrame();
        fixture.coordinator.abandonFrame();

        assertFalse(fixture.swapchain.frameInProgress());
        assertEquals(-1, fixture.swapchain.acquiredImageIndex());
        assertEquals(1, fixture.swapchain.currentFrameSyncIndex());
    }

    @Test
    void resizeOrDeviceLossResetClearsFrameAndSubmissionState() {
        Fixture fixture = readyFixture();
        beginAcquiredFrame(fixture, 0);
        fixture.coordinator.completeFrameCommandBufferBegin(fixture.coordinator.planFrameCommandBufferBegin());
        fixture.commands.markImmediateRecordingStarted();

        fixture.coordinator.resetForDeviceLossOrShutdown();

        assertFalse(fixture.swapchain.frameInProgress());
        assertFalse(fixture.swapchain.isCurrentFrameCommandBufferRecording());
        assertFalse(fixture.commands.commandBufferRecording());
        assertEquals(VK10.VK_NULL_HANDLE, fixture.commands.currentCommandPool());
    }

    @Test
    void shutdownWithPendingGenerationsAndResourcesDrainsAllBucketsOnce() {
        Fixture fixture = readyFixture();
        VulkanStagingTransferManager.StagingBufferRecord globalStaging =
            fixture.staging.recordUploadAllocation(501L, 601L, 64L);
        VulkanStagingTransferManager.StagingBufferRecord slotStaging =
            fixture.staging.recordUploadAllocation(502L, 602L, 64L);
        fixture.staging.retireAfterTransfer(globalStaging, -1);
        fixture.staging.retireAfterTransfer(slotStaging, 1);
        fixture.lifetime.trackTransientDescriptorResource("global", -1);
        fixture.lifetime.trackTransientDescriptorResource("slot-one", 1);
        fixture.lifetime.trackTransientFramebufferHandle(701L, -1);
        fixture.lifetime.trackTransientRenderPassHandle(801L, 1);
        fixture.lifetime.trackFrameDescriptorResource(0, "frame-zero");

        fixture.coordinator.cleanupForShutdownOrDeviceLoss(true, fixture.hooks());
        fixture.coordinator.cleanupForShutdownOrDeviceLoss(true, fixture.hooks());

        assertEquals(List.of(501L, 502L), fixture.destroyedStaging);
        assertEquals(List.of("global", "slot-one", "frame-zero"), fixture.retiredDescriptors);
        assertEquals(List.of(701L), fixture.destroyedFramebuffers);
        assertEquals(List.of(801L), fixture.destroyedRenderPasses);
        assertEquals(0, fixture.staging.pendingRetirementCountForTests());
        assertEquals(0, fixture.lifetime.transientDescriptorCountForTests());
        assertEquals(0, fixture.lifetime.transientFrameDescriptorCountForTests(0));
    }

    @Test
    void deviceLossCleanupDrainsStateWithoutCallingNativeDestroyHooks() {
        Fixture fixture = readyFixture();
        VulkanStagingTransferManager.StagingBufferRecord staging =
            fixture.staging.recordUploadAllocation(501L, 601L, 64L);
        fixture.staging.retireAfterTransfer(staging, -1);
        fixture.lifetime.trackTransientFramebufferHandle(701L, -1);
        fixture.lifetime.trackTransientRenderPassHandle(801L, -1);
        fixture.lifetime.trackTransientDescriptorResource("global", -1);

        fixture.coordinator.cleanupForShutdownOrDeviceLoss(false, fixture.hooks());

        assertTrue(fixture.destroyedStaging.isEmpty());
        assertTrue(fixture.retiredDescriptors.isEmpty());
        assertTrue(fixture.destroyedFramebuffers.isEmpty());
        assertTrue(fixture.destroyedRenderPasses.isEmpty());
        assertEquals(0, fixture.staging.pendingRetirementCountForTests());
        assertEquals(0, fixture.lifetime.transientDescriptorCountForTests());
    }

    @Test
    void immediateSubmissionInterleavedWithFrameSubmissionRetiresIndependentGenerations() {
        Fixture fixture = readyFixture();

        fixture.commands.markImmediateRecordingStarted();
        long immediateGeneration = fixture.commands.reserveImmediateWorkGeneration(fixture.lifetime);
        VulkanFrameExecutionCoordinator.ImmediateSubmitPlan immediate =
            fixture.coordinator.planImmediateSubmit(0xA00L);
        assertNotNull(immediate);
        fixture.coordinator.completeImmediateSubmitQueued(immediate);

        beginAcquiredFrame(fixture, 0);
        VulkanFrameExecutionCoordinator.FrameSubmitPlan frameSubmit =
            fixture.coordinator.planFrameSemaphoreBridgeSubmit();
        fixture.coordinator.completeFrameSubmit(frameSubmit);

        assertNotEquals(immediateGeneration, frameSubmit.reservedGeneration());
        assertTrue(fixture.commands.immediateSubmitInFlight());
        fixture.coordinator.completeImmediateSubmitFence(immediate, true, fixture.hooks());
        assertFalse(fixture.commands.immediateSubmitInFlight());
        assertEquals(immediateGeneration, fixture.lifetime.completedWorkGenerationForTests());
    }

    @Test
    void duplicateImmediateCleanupIsSafe() {
        Fixture fixture = readyFixture();

        fixture.commands.markImmediateRecordingStarted();
        fixture.commands.reserveImmediateWorkGeneration(fixture.lifetime);
        VulkanFrameExecutionCoordinator.ImmediateSubmitPlan immediate =
            fixture.coordinator.planImmediateSubmit(0xA00L);
        fixture.coordinator.completeImmediateSubmitQueued(immediate);
        fixture.coordinator.completeImmediateSubmitFence(immediate, true, fixture.hooks());
        fixture.coordinator.completeImmediateSubmitFence(immediate, true, fixture.hooks());

        assertFalse(fixture.commands.immediateSubmitInFlight());
        assertEquals(0, fixture.staging.pendingRetirementCountForTests(0));
    }

    @Test
    void planFrameBeginReportsPendingImmediateSubmissionInsteadOfMutatingIt() {
        Fixture fixture = readyFixture();
        fixture.commands.markImmediateRecordingStarted();

        VulkanFrameExecutionCoordinator.FrameBeginPlan begin = fixture.coordinator.planFrameBegin(false);

        assertEquals(0xA00L, begin.pendingImmediateCommandBufferHandle());
        assertTrue(fixture.commands.commandBufferRecording());
    }

    private static void cycleWithoutRecording(Fixture fixture, int imageIndex) {
        VulkanFrameExecutionCoordinator.FrameBeginPlan begin = fixture.coordinator.planFrameBegin(false);
        fixture.coordinator.completeFrameFenceWait(begin, true, fixture.hooks());
        cycleAfterCompletedBegin(fixture, imageIndex);
    }

    private static void cycleAfterCompletedBegin(Fixture fixture, int imageIndex) {
        VulkanFrameExecutionCoordinator.AcquiredImagePlan acquired = fixture.coordinator.planAcquiredImage(imageIndex);
        fixture.coordinator.completeFrameAcquire(acquired);
        VulkanFrameExecutionCoordinator.FrameSubmitPlan submit =
            fixture.coordinator.planFrameSemaphoreBridgeSubmit();
        fixture.coordinator.completeFrameSubmit(submit);
        fixture.coordinator.completePresent(
            fixture.coordinator.planPresent(),
            VulkanFrameExecutionCoordinator.PresentResult.SUCCESS
        );
    }

    private static void beginAcquiredFrame(Fixture fixture, int imageIndex) {
        VulkanFrameExecutionCoordinator.FrameBeginPlan begin = fixture.coordinator.planFrameBegin(false);
        fixture.coordinator.completeFrameFenceWait(begin, true, fixture.hooks());
        VulkanFrameExecutionCoordinator.AcquiredImagePlan acquired = fixture.coordinator.planAcquiredImage(imageIndex);
        fixture.coordinator.completeFrameAcquire(acquired);
    }

    private static Fixture readyFixture() {
        VulkanSwapchainStateManager swapchain = new VulkanSwapchainStateManager(2);
        swapchain.setImageAvailableSemaphore(0, 11L);
        swapchain.setImageAvailableSemaphore(1, 12L);
        swapchain.setFrameFence(0, 21L);
        swapchain.setFrameFence(1, 22L);
        swapchain.installSwapchain(
            VK10.VK_FORMAT_B8G8R8A8_UNORM,
            41,
            51,
            1280,
            720,
            List.of(91L, 92L),
            List.of(101L, 102L),
            new long[] { 201L, 202L }
        );

        VulkanCommandSubmissionStateManager commands = new VulkanCommandSubmissionStateManager(3, 2);
        commands.installImmediateSlot(0, 0xA0L, commandBuffer(0xA00L), 0xB0L);
        commands.installImmediateSlot(1, 0xA1L, commandBuffer(0xA01L), 0xB1L);
        commands.installImmediateSlot(2, 0xA2L, commandBuffer(0xA02L), 0xB2L);
        commands.installFrameSlot(0, 0xF0L, commandBuffer(0xF00L));
        commands.installFrameSlot(1, 0xF1L, commandBuffer(0xF01L));
        commands.activateImmediateSlot(0);

        VulkanDeferredResourceLifetime<String> lifetime = new VulkanDeferredResourceLifetime<>(2, 3);
        VulkanStagingTransferManager staging = new VulkanStagingTransferManager(3);
        VulkanFrameExecutionCoordinator<String> coordinator =
            new VulkanFrameExecutionCoordinator<>(swapchain, commands, lifetime, staging);
        return new Fixture(swapchain, commands, lifetime, staging, coordinator);
    }

    private static VkCommandBuffer commandBuffer(long address) {
        try {
            VkCommandBuffer commandBuffer = (VkCommandBuffer) UNSAFE.allocateInstance(VkCommandBuffer.class);
            POINTER_ADDRESS.setLong(commandBuffer, address);
            return commandBuffer;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to create test VkCommandBuffer handle", exception);
        }
    }

    private static Unsafe unsafe() {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            return (Unsafe) field.get(null);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("sun.misc.Unsafe is unavailable for Vulkan handle test fixtures", exception);
        }
    }

    private static Field pointerAddressField() {
        try {
            Field field = Pointer.Default.class.getDeclaredField("address");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("LWJGL pointer address field is unavailable for tests", exception);
        }
    }

    private static final class Fixture {
        final VulkanSwapchainStateManager swapchain;
        final VulkanCommandSubmissionStateManager commands;
        final VulkanDeferredResourceLifetime<String> lifetime;
        final VulkanStagingTransferManager staging;
        final VulkanFrameExecutionCoordinator<String> coordinator;
        final List<Long> destroyedStaging = new ArrayList<>();
        final List<String> retiredDescriptors = new ArrayList<>();
        final List<Long> destroyedFramebuffers = new ArrayList<>();
        final List<Long> destroyedRenderPasses = new ArrayList<>();

        Fixture(
            VulkanSwapchainStateManager swapchain,
            VulkanCommandSubmissionStateManager commands,
            VulkanDeferredResourceLifetime<String> lifetime,
            VulkanStagingTransferManager staging,
            VulkanFrameExecutionCoordinator<String> coordinator
        ) {
            this.swapchain = swapchain;
            this.commands = commands;
            this.lifetime = lifetime;
            this.staging = staging;
            this.coordinator = coordinator;
        }

        VulkanFrameExecutionCoordinator.RetirementHooks<String> hooks() {
            return new VulkanFrameExecutionCoordinator.RetirementHooks<>(
                record -> destroyedStaging.add(record.bufferHandle()),
                retiredDescriptors::add,
                destroyedFramebuffers::add,
                destroyedRenderPasses::add
            );
        }
    }
}
