package com.seibel.distanthorizons.core.file.fullDatafile.V2;

import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos;
import com.seibel.distanthorizons.core.sql.dto.FullDataSourceV2DTO;
import com.seibel.distanthorizons.core.sql.repo.FullDataSourceV2Repo;
import com.seibel.distanthorizons.core.util.threading.PriorityTaskPicker;
import com.seibel.distanthorizons.core.util.threading.ThreadPoolUtil;
import com.seibel.distanthorizons.core.config.eventHandlers.presets.ThreadPresetConfigEventHandler;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real propagation/update owners, real picker admission, and persisted retry flags. */
@Timeout(10)
class FullDataUpdateDiscardLifecycleTest {
    @TempDir Path tempDir;
    private PriorityTaskPicker picker;
    private PriorityTaskPicker.Executor blocker;
    private PriorityTaskPicker.Executor executor;
    private final CountDownLatch release = new CountDownLatch(1);
    private FullDataSourceV2Repo repo;
    private FullDataSourceProviderV2 provider;
    private FullDataUpdaterV2 updater;
    private FullDataUpdatePropagatorV2 propagator;
    private int oldThreads;
    private double oldRatio;

    @BeforeEach void setUp() throws Exception {
        ThreadPresetConfigEventHandler.INSTANCE.toString();
        oldThreads = Config.Common.MultiThreading.numberOfThreads.get();
        oldRatio = Config.Common.MultiThreading.threadRunTimeRatio.get();
        Config.Common.MultiThreading.numberOfThreads.setWithoutFiringEvents(1);
        Config.Common.MultiThreading.threadRunTimeRatio.setWithoutFiringEvents(1.0);
        picker = new PriorityTaskPicker();
        blocker = picker.createExecutor("update-owner-blocker");
        executor = picker.createExecutor("update-owner-work");
        CountDownLatch started = new CountDownLatch(1);
        blocker.execute(() -> { started.countDown(); await(release); });
        assertTrue(started.await(2, TimeUnit.SECONDS));
        repo = new FullDataSourceV2Repo("jdbc:sqlite", tempDir.resolve("full-data.sqlite").toFile());
        provider = mock(FullDataSourceProviderV2.class);
        Field repoField = FullDataSourceProviderV2.class.getDeclaredField("repo");
        repoField.setAccessible(true);
        repoField.set(provider, repo);
        updater = new FullDataUpdaterV2(provider, "discard-test");
        propagator = new FullDataUpdatePropagatorV2(provider, updater, "discard-test");
        // Drive actual owner passes deterministically instead of its periodic polling thread.
        propagator.close();
        assertTrue(propagator.updateQueueProcessor.awaitTermination(2, TimeUnit.SECONDS));
    }

    @AfterEach void tearDown() throws Exception {
        release.countDown();
        if (propagator != null) propagator.close();
        if (updater != null) updater.close();
        for (PriorityTaskPicker.Executor pool : new PriorityTaskPicker.Executor[]{executor, blocker}) {
            if (pool != null) {
                pool.shutdownNow();
                pool.awaitTermination(2, TimeUnit.SECONDS);
                Config.Common.MultiThreading.numberOfThreads.removeListener(pool);
            }
        }
        if (picker != null) picker.shutdownNow();
        if (repo != null) repo.close();
        Config.Common.MultiThreading.numberOfThreads.setWithoutFiringEvents(oldThreads);
        Config.Common.MultiThreading.threadRunTimeRatio.setWithoutFiringEvents(oldRatio);
    }

    @Test void discardedParentUpdateReleasesOwnershipAndKeepsDatabaseRetryFlag() throws Exception {
        verifyPropagationDiscard(false, false);
    }

    @Test void discardedChildUpdateReleasesOwnershipAndKeepsDatabaseRetryFlag() throws Exception {
        verifyPropagationDiscard(true, false);
    }

    @Test void rejectedParentUpdateReleasesOwnershipAndKeepsDatabaseRetryFlag() throws Exception {
        verifyPropagationDiscard(false, true);
    }

    @Test void rejectedChildUpdateReleasesOwnershipAndKeepsDatabaseRetryFlag() throws Exception {
        verifyPropagationDiscard(true, true);
    }

    @Test void discardedAsyncUpdateTerminatesFutureAndReleasesQueuedCount() throws Exception {
        FullDataSourceV2 source = mock(FullDataSourceV2.class);
        long pos = DhSectionPos.encode((byte) 6, 0, 0);
        when(source.getPos()).thenReturn(pos);
        try (var pools = mockStatic(ThreadPoolUtil.class)) {
            pools.when(ThreadPoolUtil::getChunkToLodBuilderExecutor).thenReturn(executor);
            CompletableFuture<Void> future = updater.updateDataSourceAsync(source);
            assertEquals(1, queuedUpdates().size());
            assertEquals(1, executor.getQueueSize());
            executor.clearQueue();
            assertTrue(future.isCancelled(), "discarded update must terminate its caller future");
            assertTrue(queuedUpdates().isEmpty(), "discarded update must release its actual owner count");
        }
    }

    @Test void cancellingOneQueuedUpdateDoesNotReleaseAnotherUpdatesCount() throws Exception {
        FullDataSourceV2 source = mock(FullDataSourceV2.class);
        long pos = DhSectionPos.encode((byte) 6, 0, 0);
        when(source.getPos()).thenReturn(pos);
        try (var pools = mockStatic(ThreadPoolUtil.class)) {
            pools.when(ThreadPoolUtil::getChunkToLodBuilderExecutor).thenReturn(executor);
            CompletableFuture<Void> first = updater.updateDataSourceAsync(source);
            CompletableFuture<Void> second = updater.updateDataSourceAsync(source);
            first.cancel(false);
            assertEquals(1, queuedUpdates().get(pos).get());
            executor.clearQueue();
            assertTrue(second.isCancelled());
            assertTrue(queuedUpdates().isEmpty());
        }
    }

    @Test void cancellingRunningUpdateRetainsItsCountUntilTheOperationReturns() throws Exception {
        release.countDown();
        CountDownLatch updateEntered = new CountDownLatch(1);
        CountDownLatch updateRelease = new CountDownLatch(1);
        FullDataSourceV2 source = mock(FullDataSourceV2.class);
        long pos = DhSectionPos.encode((byte) 6, 0, 0);
        when(source.getPos()).thenReturn(pos);
        when(provider.get(pos)).thenAnswer(invocation -> {
            updateEntered.countDown();
            await(updateRelease);
            return null;
        });
        try (var pools = mockStatic(ThreadPoolUtil.class)) {
            pools.when(ThreadPoolUtil::getChunkToLodBuilderExecutor).thenReturn(executor);
            CompletableFuture<Void> future = updater.updateDataSourceAsync(source);
            assertTrue(updateEntered.await(2, TimeUnit.SECONDS));
            future.cancel(false);
            assertEquals(1, queuedUpdates().get(pos).get(), "terminal cancellation does not mean running update work stopped");
            verify(source, never()).close();
            updateRelease.countDown();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (executor.getRunningTaskCount() != 0 && System.nanoTime() < deadline) Thread.sleep(1);
            assertEquals(0, executor.getRunningTaskCount());
            assertTrue(queuedUpdates().isEmpty());
            // The caller retains its input until the actual operation has returned.
            source.close();
        } finally {
            updateRelease.countDown();
        }
    }

    private void verifyPropagationDiscard(boolean childUpdate, boolean rejectImmediately) throws Exception {
        long storedPos = DhSectionPos.encode((byte) 7, 0, 0);
        try (FullDataSourceV2DTO dto = FullDataSourceV2DTO.CreateEmptyDataSourceForDecoding()) {
            dto.pos = storedPos;
            dto.dataFormatVersion = (byte) FullDataSourceV2DTO.DATA_FORMAT.V4_LATEST;
            dto.compressionModeValue = 0;
            dto.applyToParent = !childUpdate;
            dto.applyToChildren = childUpdate;
            repo.save(dto);
        }
        long ownedPos = childUpdate ? storedPos : DhSectionPos.getParentPos(storedPos);
        if (rejectImmediately) executor.shutdown();
        runPropagation(childUpdate);
        if (!rejectImmediately) {
            assertTrue(updatingPositions().contains(ownedPos), "ownership must be published before queued work");
            assertEquals(1, executor.getQueueSize());
            executor.clearQueue();
        }
        assertTrue(updatingPositions().isEmpty(), "discard/rejection must release the real updating position");
        assertTrue((childUpdate ? repo.getChildPositionsToUpdate(0, 0, 5) : repo.getPositionsToUpdate(0, 0, 5)).contains(storedPos),
            "unapplied database flag must remain available for retry");
        if (!rejectImmediately) {
            runPropagation(childUpdate);
            assertTrue(updatingPositions().contains(ownedPos));
            assertEquals(1, executor.getQueueSize(), "the same position must be admitted on retry");
            executor.clearQueue();
        }
    }

    private void runPropagation(boolean children) throws Exception {
        Method method = FullDataUpdatePropagatorV2.class.getDeclaredMethod(children ? "runChildUpdates" : "runParentUpdates",
            PriorityTaskPicker.Executor.class, DhBlockPos.class);
        method.setAccessible(true);
        method.invoke(propagator, executor, DhBlockPos.ZERO);
    }

    @SuppressWarnings("unchecked") private Set<Long> updatingPositions() throws Exception {
        Field field = FullDataUpdatePropagatorV2.class.getDeclaredField("updatingPosSet");
        field.setAccessible(true);
        return (Set<Long>) field.get(propagator);
    }

    @SuppressWarnings("unchecked") private Map<Long, java.util.concurrent.atomic.AtomicInteger> queuedUpdates() throws Exception {
        Field field = FullDataUpdaterV2.class.getDeclaredField("queuedUpdateCountsByPos");
        field.setAccessible(true);
        return (Map<Long, java.util.concurrent.atomic.AtomicInteger>) field.get(updater);
    }

    private static void await(CountDownLatch latch) {
        try { latch.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
