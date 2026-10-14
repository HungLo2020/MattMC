package com.seibel.distanthorizons.core.file.fullDatafile.V2;

import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiWorldGenerationStep;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.config.eventHandlers.presets.ThreadPresetConfigEventHandler;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV1;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.file.fullDatafile.V1.FullDataSourceProviderV1;
import com.seibel.distanthorizons.core.level.IDhLevel;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.sql.dto.FullDataSourceV1DTO;
import com.seibel.distanthorizons.core.util.threading.PriorityTaskPicker;
import com.seibel.distanthorizons.core.util.threading.ThreadPoolUtil;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual migration conversion, native source ownership, and legacy-row lifetime. */
@Timeout(10)
class DataMigratorV1LifecycleTest {
    @TempDir Path directory;
    private static final long POS = DhSectionPos.encode((byte) 6, 0, 0);

    @BeforeAll static void initializeConfig() { ThreadPresetConfigEventHandler.INSTANCE.toString(); }

    @Test void discardedUpdateClosesConvertedNativeSourceAndKeepsLegacyRow() throws Exception {
        verifyMigration(Outcome.DISCARDED);
    }

    @Test void assertionErrorUpdateStageClosesConvertedNativeSourceAndKeepsLegacyRow() throws Exception {
        verifyMigration(Outcome.FAILED);
    }

    @Test void synchronousRejectionClosesConvertedNativeSourceAndKeepsLegacyRow() throws Exception {
        verifyMigration(Outcome.REJECTED);
    }

    @Test void successfulUpdateClosesConvertedNativeSourceAndDeletesLegacyRow() throws Exception {
        verifyMigration(Outcome.SUCCEEDED);
    }

    @Test void shutDownUpdaterCancelsMigrationAndKeepsLegacyRow() throws Exception {
        verifyMigration(Outcome.UPDATER_SHUTDOWN);
    }

    @Test void missingUpdateExecutorCancelsMigrationAndKeepsLegacyRow() throws Exception {
        verifyMigration(Outcome.EXECUTOR_MISSING);
    }

    @Test void terminatedUpdateExecutorCancelsMigrationAndKeepsLegacyRow() throws Exception {
        verifyMigration(Outcome.EXECUTOR_TERMINATED);
    }

    @Test void queuedUpdateCancelledByUpdaterCloseKeepsLegacyRow() throws Exception {
        verifyMigration(Outcome.QUEUED_UPDATER_SHUTDOWN);
    }

    @Test void cancelledUpdateStopsBeforeSelectingTheSameLegacyRowsAgain() throws Exception {
        verifyMigration(Outcome.DISCARDED, true);
    }

    @Test void laterCancelledUpdateStopsBatchEvenWhenFirstStageFailsWithAssertionError() throws Exception {
        int oldThreads = Config.Common.MultiThreading.numberOfThreads.get();
        Config.Common.MultiThreading.numberOfThreads.setWithoutFiringEvents(1);
        PriorityTaskPicker picker = new PriorityTaskPicker();
        PriorityTaskPicker.Executor executor = picker.createExecutor("mixed-migration-owner-test");
        CountDownLatch release = new CountDownLatch(1);
        List<FullDataSourceV2> converted = new ArrayList<>();
        long secondPos = DhSectionPos.encode((byte) 6, 1, 0);
        FullDataUpdaterV2 updater = spy(new FullDataUpdaterV2(mock(FullDataSourceProviderV2.class), "mixed-migration-owner-test"));
        FullDataSourceProviderV1<IDhLevel> legacyProvider = null;
        try (var pools = mockStatic(ThreadPoolUtil.class)) {
            pools.when(ThreadPoolUtil::getChunkToLodBuilderExecutor).thenReturn(executor);
            DataMigratorV1 migrator = new DataMigratorV1(updater, mock(IDhLevel.class), "mixed-migration-owner-test", directory.toFile());
            Field providerField = DataMigratorV1.class.getDeclaredField("v1DataSourceProvider");
            providerField.setAccessible(true);
            legacyProvider = spy((FullDataSourceProviderV1<IDhLevel>) providerField.get(migrator));
            providerField.set(migrator, legacyProvider);
            for (long pos : new long[] { POS, secondPos }) {
                legacyProvider.repo.save(new FullDataSourceV1DTO(pos, 0, (byte) 0,
                    EDhApiWorldGenerationStep.LIGHT, FullDataSourceV1.DATA_TYPE_NAME,
                    FullDataSourceV1.DATA_FORMAT_VERSION, new byte[0]));
                assertTrue(legacyProvider.repo.existsWithKey(pos));
            }
            doReturn(new ArrayList<>(List.of(FullDataSourceV1.createEmpty(POS), FullDataSourceV1.createEmpty(secondPos))),
                new ArrayList<FullDataSourceV1>()).when(legacyProvider).getDataSourcesToMigrate(anyInt());
            doThrow(new AssertionError("first update stage failed")).when(updater).updateDataSource(any());
            doAnswer(invocation -> {
                FullDataSourceV2 source = invocation.getArgument(0);
                converted.add(source);
                assertTrue(source.nativeSource().isLive());
                if (source.getPos() == secondPos) {
                    CountDownLatch started = new CountDownLatch(1);
                    executor.execute(() -> {
                        started.countDown();
                        try { release.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    });
                    assertTrue(started.await(2, TimeUnit.SECONDS));
                }
                @SuppressWarnings("unchecked")
                CompletableFuture<Void> future = (CompletableFuture<Void>) invocation.callRealMethod();
                if (source.getPos() == POS) {
                    ExecutionException failure = assertThrows(ExecutionException.class, () -> future.get(2, TimeUnit.SECONDS));
                    assertInstanceOf(AssertionError.class, failure.getCause());
                } else {
                    assertEquals(1, executor.getQueueSize());
                    executor.clearQueue();
                    assertTrue(future.isCancelled());
                }
                return future;
            }).when(updater).updateDataSourceAsync(any());

            Method migrate = DataMigratorV1.class.getDeclaredMethod("convertLegacyDataSources");
            migrate.setAccessible(true);
            migrate.invoke(migrator);
            verify(legacyProvider, times(1)).getDataSourcesToMigrate(anyInt());
            assertEquals(2, converted.size());
            for (FullDataSourceV2 source : converted) {
                assertFalse(source.nativeSource().isLive());
                assertTrue(legacyProvider.repo.existsWithKey(source.getPos()));
            }
            Field stopped = DataMigratorV1.class.getDeclaredField("migrationStoppedWithError");
            stopped.setAccessible(true);
            assertTrue(stopped.getBoolean(migrator));
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
            Config.Common.MultiThreading.numberOfThreads.removeListener(executor);
            updater.close();
            if (legacyProvider != null) legacyProvider.close();
            for (FullDataSourceV2 source : converted) source.close();
            picker.shutdownNow();
            Config.Common.MultiThreading.numberOfThreads.setWithoutFiringEvents(oldThreads);
        }
    }

    private void verifyMigration(Outcome outcome) throws Exception { verifyMigration(outcome, false); }

    @SuppressWarnings("unchecked")
    private void verifyMigration(Outcome outcome, boolean checkStopFirst) throws Exception {
        int oldThreads = Config.Common.MultiThreading.numberOfThreads.get();
        Config.Common.MultiThreading.numberOfThreads.setWithoutFiringEvents(1);
        PriorityTaskPicker picker = new PriorityTaskPicker();
        PriorityTaskPicker.Executor executor = picker.createExecutor("migration-owner-test");
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<FullDataSourceV2> converted = new AtomicReference<>();
        AtomicReference<CompletableFuture<Void>> submitted = new AtomicReference<>();
        FullDataSourceProviderV2 destination = mock(FullDataSourceProviderV2.class);
        FullDataUpdaterV2 updater = spy(new FullDataUpdaterV2(destination, "migration-owner-test"));
        FullDataSourceProviderV1<IDhLevel> legacyProvider = null;
        try (var pools = mockStatic(ThreadPoolUtil.class)) {
            pools.when(ThreadPoolUtil::getChunkToLodBuilderExecutor).thenReturn(executor);
            DataMigratorV1 migrator = new DataMigratorV1(updater, mock(IDhLevel.class), "migration-owner-test", directory.toFile());
            Field providerField = DataMigratorV1.class.getDeclaredField("v1DataSourceProvider");
            providerField.setAccessible(true);
            legacyProvider = spy((FullDataSourceProviderV1<IDhLevel>) providerField.get(migrator));
            providerField.set(migrator, legacyProvider);
            // Persist a real legacy row. The read boundary supplies its decoded source,
            // leaving this test independent of the retired byte serialization format.
            legacyProvider.repo.save(new FullDataSourceV1DTO(POS, 0, (byte) 0,
                EDhApiWorldGenerationStep.LIGHT, FullDataSourceV1.DATA_TYPE_NAME,
                FullDataSourceV1.DATA_FORMAT_VERSION, new byte[0]));
            assertTrue(legacyProvider.repo.existsWithKey(POS));
            doReturn(new ArrayList<>(List.of(FullDataSourceV1.createEmpty(POS))), new ArrayList<FullDataSourceV1>())
                .when(legacyProvider).getDataSourcesToMigrate(anyInt());

            if (outcome == Outcome.DISCARDED || outcome == Outcome.QUEUED_UPDATER_SHUTDOWN) {
                CountDownLatch started = new CountDownLatch(1);
                executor.execute(() -> {
                    started.countDown();
                    try { release.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                });
                assertTrue(started.await(2, TimeUnit.SECONDS));
            }
            if (outcome == Outcome.UPDATER_SHUTDOWN) updater.close();
            if (outcome == Outcome.EXECUTOR_MISSING) pools.when(ThreadPoolUtil::getChunkToLodBuilderExecutor).thenReturn(null);
            if (outcome == Outcome.EXECUTOR_TERMINATED) {
                executor.shutdown();
                assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
            }
            if (outcome == Outcome.FAILED) {
                doThrow(new AssertionError("update failed")).when(updater).updateDataSource(any());
            } else if (outcome != Outcome.QUEUED_UPDATER_SHUTDOWN) {
                doNothing().when(updater).updateDataSource(any());
            }
            doAnswer(invocation -> {
                FullDataSourceV2 source = invocation.getArgument(0);
                converted.set(source);
                assertTrue(source.nativeSource().isLive(), "migration must create a real native source");
                assertTrue(source.applyToParent);
                if (outcome == Outcome.REJECTED) throw new RejectedExecutionException("submission rejected");
                CompletableFuture<Void> future = (CompletableFuture<Void>) invocation.callRealMethod();
                submitted.set(future);
                if (outcome == Outcome.DISCARDED) {
                    assertEquals(1, executor.getQueueSize());
                    executor.clearQueue();
                    assertTrue(future.isCancelled());
                }
                if (outcome == Outcome.QUEUED_UPDATER_SHUTDOWN) {
                    assertEquals(1, executor.getQueueSize());
                    updater.close();
                    release.countDown();
                }
                return future;
            }).when(updater).updateDataSourceAsync(any());

            Method migrate = DataMigratorV1.class.getDeclaredMethod("convertLegacyDataSources");
            migrate.setAccessible(true);
            migrate.invoke(migrator);
            if (outcome == Outcome.FAILED || outcome == Outcome.SUCCEEDED || outcome == Outcome.QUEUED_UPDATER_SHUTDOWN) {
                int completedTasks = outcome == Outcome.QUEUED_UPDATER_SHUTDOWN ? 2 : 1;
                assertTimeoutPreemptively(java.time.Duration.ofSeconds(2), () -> {
                    while (executor.getCompletedTaskCount() < completedTasks) Thread.sleep(1);
                });
            }
            if (checkStopFirst) verify(legacyProvider, times(1)).getDataSourcesToMigrate(anyInt());
            assertNotNull(converted.get());
            assertFalse(converted.get().nativeSource().isLive(), "every terminal update path must close the migrator-owned source");
            assertEquals(outcome != Outcome.SUCCEEDED, legacyProvider.repo.existsWithKey(POS),
                "only a successful update may delete the persisted legacy row");
            if (outcome == Outcome.UPDATER_SHUTDOWN || outcome == Outcome.EXECUTOR_MISSING
                || outcome == Outcome.EXECUTOR_TERMINATED || outcome == Outcome.QUEUED_UPDATER_SHUTDOWN) {
                assertTrue(submitted.get().isCancelled(), "unavailable update admission must be explicit cancellation");
            }
            if (outcome == Outcome.DISCARDED || outcome == Outcome.UPDATER_SHUTDOWN
                || outcome == Outcome.EXECUTOR_MISSING || outcome == Outcome.EXECUTOR_TERMINATED
                || outcome == Outcome.QUEUED_UPDATER_SHUTDOWN) {
                verify(legacyProvider, times(1)).getDataSourcesToMigrate(anyInt());
                Field stopped = DataMigratorV1.class.getDeclaredField("migrationStoppedWithError");
                stopped.setAccessible(true);
                assertTrue(stopped.getBoolean(migrator), "cancelled migration must stop while preserving rows for a later reopen");
            }
            if (outcome == Outcome.QUEUED_UPDATER_SHUTDOWN) {
                verify(destination, never()).get(anyLong());
                Field counts = FullDataUpdaterV2.class.getDeclaredField("queuedUpdateCountsByPos");
                counts.setAccessible(true);
                assertTrue(((java.util.Map<?, ?>) counts.get(updater)).isEmpty());
            }
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
            Config.Common.MultiThreading.numberOfThreads.removeListener(executor);
            updater.close();
            if (legacyProvider != null) legacyProvider.close();
            if (converted.get() != null) converted.get().close();
            picker.shutdownNow();
            Config.Common.MultiThreading.numberOfThreads.setWithoutFiringEvents(oldThreads);
        }
    }

    private enum Outcome { DISCARDED, FAILED, REJECTED, SUCCEEDED, UPDATER_SHUTDOWN, EXECUTOR_MISSING, EXECUTOR_TERMINATED, QUEUED_UPDATER_SHUTDOWN }
}
