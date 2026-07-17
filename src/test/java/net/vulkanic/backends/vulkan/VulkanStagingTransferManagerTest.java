package net.vulkanic.backends.vulkan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class VulkanStagingTransferManagerTest {
    @Test
    void uploadStagingLifetimeSurvivesUntilSubmissionSlotRetires() {
        VulkanStagingTransferManager manager = new VulkanStagingTransferManager(2);
        List<Long> destroyed = new ArrayList<>();

        VulkanStagingTransferManager.StagingBufferRecord staging =
            manager.recordUploadAllocation(0x10L, 0x20L, 64L);
        manager.markMapped(staging);
        manager.markUnmapped(staging);
        manager.associateTransferCommand(staging, 0x30L);
        manager.retireAfterTransfer(staging, 1);

        assertEquals(1, manager.pendingRetirementCountForTests());
        assertEquals(1, manager.pendingRetirementCountForTests(1));
        assertFalse(manager.canReuse(staging));
        assertTrue(destroyed.isEmpty());

        manager.retireImmediateSlot(0, record -> destroyed.add(record.bufferHandle()));
        assertTrue(destroyed.isEmpty(), "wrong submit slot must not retire staging");

        manager.retireImmediateSlot(1, record -> destroyed.add(record.bufferHandle()));
        assertEquals(List.of(0x10L), destroyed);
        assertEquals(0, manager.pendingRetirementCountForTests());
        assertTrue(manager.canReuse(staging));
    }

    @Test
    void readbackStagingLifetimeSurvivesThroughMappingAndResultConsumption() {
        VulkanStagingTransferManager manager = new VulkanStagingTransferManager(1);
        List<String> events = new ArrayList<>();

        VulkanStagingTransferManager.ReadbackTransferRecord readback =
            manager.recordReadbackStaging(7, 16L);
        manager.associateReadbackCommand(readback, 0x40L);

        try (VulkanStagingTransferManager.ReadbackResult result =
                 manager.mapReadbackResult(readback, ByteBuffer.allocate(16), () -> events.add("unmap"))) {
            assertEquals(16, result.data().remaining());
            assertEquals(1, manager.liveReadbackCountForTests());
        }

        assertEquals(List.of("unmap"), events);
        assertEquals(0, manager.liveReadbackCountForTests());
        assertEquals(
            VulkanStagingTransferManager.TransferState.RESULT_CONSUMED,
            readback.stateForTests()
        );
    }

    @Test
    void stagingReuseIsAllowedOnlyAfterRetirement() {
        VulkanStagingTransferManager manager = new VulkanStagingTransferManager(1);
        VulkanStagingTransferManager.StagingBufferRecord staging =
            manager.recordUploadAllocation(0x11L, 0x22L, 32L);

        assertFalse(manager.canReuse(staging));

        manager.markMapped(staging);
        manager.markUnmapped(staging);
        manager.associateTransferCommand(staging, 0x33L);
        manager.retireAfterTransfer(staging, -1);

        assertFalse(manager.canReuse(staging));

        manager.retireGlobal(record -> {
        });

        assertTrue(manager.canReuse(staging));
    }

    @Test
    void failedTransferCleanupDestroysUnsubmittedStagingImmediately() {
        VulkanStagingTransferManager manager = new VulkanStagingTransferManager(1);
        List<Long> destroyed = new ArrayList<>();
        VulkanStagingTransferManager.StagingBufferRecord staging =
            manager.recordUploadAllocation(0x12L, 0x24L, 8L);

        manager.cleanupFailedTransfer(staging, false, -1, true, record -> destroyed.add(record.bufferHandle()));

        assertEquals(List.of(0x12L), destroyed);
        assertEquals(0, manager.pendingRetirementCountForTests());
        assertEquals(VulkanStagingTransferManager.TransferState.FAILED, staging.stateForTests());
    }

    @Test
    void failedTransferDefersCleanupWhenCommandMayReferenceStaging() {
        VulkanStagingTransferManager manager = new VulkanStagingTransferManager(1);
        List<Long> destroyed = new ArrayList<>();
        VulkanStagingTransferManager.StagingBufferRecord staging =
            manager.recordUploadAllocation(0x13L, 0x26L, 8L);

        manager.cleanupFailedTransfer(staging, true, 0, true, record -> destroyed.add(record.bufferHandle()));

        assertTrue(destroyed.isEmpty());
        assertEquals(1, manager.pendingRetirementCountForTests());

        manager.retireImmediateSlot(0, record -> destroyed.add(record.bufferHandle()));
        assertEquals(List.of(0x13L), destroyed);
    }

    @Test
    void duplicateRetirementIsHarmless() {
        VulkanStagingTransferManager manager = new VulkanStagingTransferManager(1);
        List<Long> destroyed = new ArrayList<>();
        VulkanStagingTransferManager.StagingBufferRecord staging =
            manager.recordUploadAllocation(0x14L, 0x28L, 8L);

        manager.retireAfterTransfer(staging, -1);
        manager.retireAfterTransfer(staging, -1);
        manager.retireGlobal(record -> destroyed.add(record.bufferHandle()));
        manager.retireGlobal(record -> destroyed.add(record.bufferHandle()));

        assertEquals(List.of(0x14L), destroyed);
        assertEquals(0, manager.pendingRetirementCountForTests());
    }

    @Test
    void shutdownAndDeviceLossCleanupDrainOrDropStagingSafely() {
        VulkanStagingTransferManager manager = new VulkanStagingTransferManager(2);
        List<Long> destroyed = new ArrayList<>();
        VulkanStagingTransferManager.StagingBufferRecord liveDeviceRecord =
            manager.recordUploadAllocation(0x15L, 0x2AL, 8L);
        manager.retireAfterTransfer(liveDeviceRecord, 0);

        manager.cleanupForShutdownOrDeviceLoss(true, record -> destroyed.add(record.bufferHandle()));
        assertEquals(List.of(0x15L), destroyed);
        assertEquals(0, manager.pendingRetirementCountForTests());

        VulkanStagingTransferManager.StagingBufferRecord lostDeviceRecord =
            manager.recordUploadAllocation(0x16L, 0x2CL, 8L);
        manager.retireAfterTransfer(lostDeviceRecord, -1);
        manager.recordReadbackStaging(9, 4L);

        manager.cleanupForShutdownOrDeviceLoss(false, record -> destroyed.add(record.bufferHandle()));

        assertEquals(List.of(0x15L), destroyed, "device-loss cleanup must not call Vulkan destroy callbacks");
        assertEquals(0, manager.pendingRetirementCountForTests());
        assertEquals(0, manager.liveReadbackCountForTests());
    }

    @Test
    void rejectsInvalidLifecycleInputs() {
        VulkanStagingTransferManager manager = new VulkanStagingTransferManager(1);
        VulkanStagingTransferManager.StagingBufferRecord staging =
            manager.recordUploadAllocation(0x17L, 0x2EL, 8L);

        assertThrows(IllegalArgumentException.class, () -> manager.associateTransferCommand(staging, 0L));
        assertThrows(IllegalArgumentException.class, () -> manager.recordReadbackStaging(0, 4L));
    }
}
