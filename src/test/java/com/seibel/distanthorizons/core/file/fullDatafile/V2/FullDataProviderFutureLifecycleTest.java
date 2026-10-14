package com.seibel.distanthorizons.core.file.fullDatafile.V2;

import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV1;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.file.fullDatafile.V1.FullDataSourceProviderV1;
import com.seibel.distanthorizons.core.level.IDhLevel;
import com.seibel.distanthorizons.core.util.threading.PriorityTaskPicker;
import com.seibel.distanthorizons.core.util.threading.ThreadPoolUtil;
import com.seibel.distanthorizons.core.config.eventHandlers.presets.ThreadPresetConfigEventHandler;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real provider getAsync owners; only the disk-read result is supplied by a fixture. */
@Timeout(10)
class FullDataProviderFutureLifecycleTest {
    @TempDir Path tempDir;
    private PriorityTaskPicker picker;
    private PriorityTaskPicker.Executor executor;
    private final CountDownLatch entered = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);
    private int oldThreads;
    private double oldRatio;

    @BeforeEach void setUp() {
        ThreadPresetConfigEventHandler.INSTANCE.toString();
        oldThreads = Config.Common.MultiThreading.numberOfThreads.get();
        oldRatio = Config.Common.MultiThreading.threadRunTimeRatio.get();
        Config.Common.MultiThreading.numberOfThreads.setWithoutFiringEvents(1);
        Config.Common.MultiThreading.threadRunTimeRatio.setWithoutFiringEvents(1.0);
        picker = new PriorityTaskPicker();
        executor = picker.createExecutor("provider-owner-work");
    }

    @AfterEach void tearDown() throws Exception {
        release.countDown();
        if (executor != null) {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
            Config.Common.MultiThreading.numberOfThreads.removeListener(executor);
        }
        if (picker != null) picker.shutdownNow();
        Config.Common.MultiThreading.numberOfThreads.setWithoutFiringEvents(oldThreads);
        Config.Common.MultiThreading.threadRunTimeRatio.setWithoutFiringEvents(oldRatio);
    }

    @Test void discardedV1ReadTerminatesCallerFuture() throws Exception {
        blockWorker();
        try (var pools = mockStatic(ThreadPoolUtil.class);
             var provider = new FullDataSourceProviderV1<>(mock(IDhLevel.class), tempDir.toFile())) {
            pools.when(ThreadPoolUtil::getFileHandlerExecutor).thenReturn(executor);
            CompletableFuture<FullDataSourceV1> future = provider.getAsync(6L);
            assertEquals(1, executor.getQueueSize());
            executor.clearQueue();
            assertTrue(future.isCancelled());
        }
    }

    @Test void discardedV2ReadTerminatesCallerFuture() throws Exception {
        blockWorker();
        try (var pools = mockStatic(ThreadPoolUtil.class);
             var provider = new V2Provider(mock(FullDataSourceV2.class))) {
            pools.when(ThreadPoolUtil::getFileHandlerExecutor).thenReturn(executor);
            CompletableFuture<FullDataSourceV2> future = provider.getAsync(6L);
            assertEquals(1, executor.getQueueSize());
            executor.clearQueue();
            assertTrue(future.isCancelled());
        }
    }

    @Test void cancellationDuringV2ReadClosesUnclaimedResult() throws Exception {
        try (FullDataSourceV2 source = spy(FullDataSourceV2.createEmpty(6L));
             var pools = mockStatic(ThreadPoolUtil.class); var provider = new V2Provider(source)) {
            assertTrue(source.nativeSource().isLive());
            pools.when(ThreadPoolUtil::getFileHandlerExecutor).thenReturn(executor);
            CompletableFuture<FullDataSourceV2> future = provider.getAsync(6L);
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            future.cancel(false);
            release.countDown();
            awaitIdle();
            verify(source, times(1)).close();
            assertFalse(source.nativeSource().isLive(), "cancelled delivery must release the actual native source owner");
        }
    }

    private class V2Provider extends FullDataSourceProviderV2 {
        private final FullDataSourceV2 source;
        V2Provider(FullDataSourceV2 source) throws SQLException, IOException {
            super(mock(IDhLevel.class, RETURNS_DEEP_STUBS), null, tempDir.toFile());
            this.source = source;
            try {
                Field field = FullDataSourceProviderV2.class.getDeclaredField("updatePropagator");
                field.setAccessible(true);
                ((FullDataUpdatePropagatorV2) field.get(this)).close();
            } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        }
        @Override public FullDataSourceV2 get(long pos) { entered.countDown(); await(release); return source; }
        @Override public void close() {
            try {
                Field migratorField = FullDataSourceProviderV2.class.getDeclaredField("dataMigratorV1");
                migratorField.setAccessible(true);
                Field legacyField = DataMigratorV1.class.getDeclaredField("v1DataSourceProvider");
                legacyField.setAccessible(true);
                ((FullDataSourceProviderV1<?>) legacyField.get(migratorField.get(this))).close();
            } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
            super.close();
        }
    }

    private void blockWorker() throws Exception {
        executor.execute(() -> { entered.countDown(); await(release); });
        assertTrue(entered.await(2, TimeUnit.SECONDS));
    }

    private void awaitIdle() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (executor.getRunningTaskCount() != 0 && System.nanoTime() < deadline) Thread.sleep(1);
        assertEquals(0, executor.getRunningTaskCount());
    }

    private static void await(CountDownLatch latch) {
        try { latch.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
