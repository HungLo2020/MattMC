package com.seibel.distanthorizons.core.render;

import com.seibel.distanthorizons.api.enums.rendering.EDhApiRendererMode;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.config.eventHandlers.presets.ThreadPresetConfigEventHandler;
import com.seibel.distanthorizons.core.dataObjects.render.ColumnRenderSource;
import com.seibel.distanthorizons.core.enums.EDhDirection;
import com.seibel.distanthorizons.core.file.fullDatafile.V2.FullDataSourceProviderV2;
import com.seibel.distanthorizons.core.level.IDhClientLevel;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.util.threading.PriorityTaskPicker;
import com.seibel.distanthorizons.core.util.threading.ThreadPoolUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual section owners behind a blocked picker, without a renderer or client. */
class LodRenderSectionAdmissionTest {
    private int oldThreads;
    private EDhApiRendererMode oldRenderer;
    private Object oldRenderExecutor;
    private Field executorField;
    private final long pos = DhSectionPos.encode((byte) 6, 0, 0);

    @BeforeAll
    static void initializeConfigInRuntimeOrder() {
        ThreadPresetConfigEventHandler.INSTANCE.toString();
    }

    @BeforeEach
    void installSettings() throws Exception {
        oldThreads = Config.Common.MultiThreading.numberOfThreads.get();
        oldRenderer = Config.Client.Advanced.Debugging.rendererMode.get();
        Config.Common.MultiThreading.numberOfThreads.setWithoutFiringEvents(1);
        Config.Client.Advanced.Debugging.rendererMode.setWithoutFiringEvents(EDhApiRendererMode.DEFAULT);
        executorField = ThreadPoolUtil.class.getDeclaredField("renderSectionLoadThreadPool");
        executorField.setAccessible(true);
        oldRenderExecutor = executorField.get(null);
    }

    @AfterEach
    void restoreSettings() throws Exception {
        executorField.set(null, oldRenderExecutor);
        Config.Common.MultiThreading.numberOfThreads.setWithoutFiringEvents(oldThreads);
        Config.Client.Advanced.Debugging.rendererMode.setWithoutFiringEvents(oldRenderer);
    }

    @Test
    void closingQueuedSectionTerminatesBuildAndReturnsUploadSlot() throws Exception {
        try (BlockedPicker blocked = new BlockedPicker()) {
            executorField.set(null, blocked.executor);
            AtomicInteger uploads = new AtomicInteger();
            FullDataSourceProviderV2 provider = mock(FullDataSourceProviderV2.class);
            LodRenderSection section = section(provider, uploads);
            assertTrue(section.uploadRenderDataToGpuAsync());
            CompletableFuture<Void> build = section.getRenderDataBuildFuture();
            assertEquals(1, uploads.get());

            section.close();

            assertTrue(build.isDone(), "removing an accepted build must complete its owner future");
            assertEquals(0, uploads.get(), "close must return the real section upload slot");
            assertFalse(section.gpuUploadInProgress());
            assertNull(section.getRenderDataBuildFuture());
            assertNull(queuedRunnable(section));
            assertFalse(section.uploadRenderDataToGpuAsync(), "closed sections cannot reacquire an upload slot");
            verify(provider, never()).get(anyLong());
        }
    }

    @Test
    void discardedBuildClearsBusyStateAndCanBeQueuedOnReplacementExecutor() throws Exception {
        try (BlockedPicker first = new BlockedPicker(); BlockedPicker replacement = new BlockedPicker()) {
            executorField.set(null, first.executor);
            AtomicInteger uploads = new AtomicInteger();
            LodRenderSection section = section(mock(FullDataSourceProviderV2.class), uploads);
            try {
                assertTrue(section.uploadRenderDataToGpuAsync());
                CompletableFuture<Void> discarded = section.getRenderDataBuildFuture();
                first.executor.shutdownNow();

                assertTrue(discarded.isDone(), "shutdown must terminate a queued section build");
                assertEquals(0, uploads.get());
                assertFalse(section.gpuUploadInProgress(), "a discarded build must not pin the section busy");
                assertNull(section.getRenderDataBuildFuture());
                assertNull(queuedRunnable(section));
                executorField.set(null, replacement.executor);
                assertTrue(section.uploadRenderDataToGpuAsync(), "a live section can retry discarded work");
                assertEquals(1, uploads.get());
            } finally {
                section.close();
            }
            assertEquals(0, uploads.get());
        }
    }

    @Test
    void callerCancellationClearsQueuedBuildAndDoesNotBlockRetry() throws Exception {
        try (BlockedPicker blocked = new BlockedPicker()) {
            executorField.set(null, blocked.executor);
            AtomicInteger uploads = new AtomicInteger();
            LodRenderSection section = section(mock(FullDataSourceProviderV2.class), uploads);
            try {
                assertTrue(section.uploadRenderDataToGpuAsync());
                assertTrue(section.getRenderDataBuildFuture().cancel(false));
                assertEquals(0, uploads.get());
                assertFalse(section.gpuUploadInProgress());
                assertTrue(section.uploadRenderDataToGpuAsync());
                assertEquals(1, uploads.get());
            } finally {
                section.close();
            }
            assertEquals(0, uploads.get());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void queuedRenderSourceLoadHasTerminalFutureWhenPickerDiscardsIt() throws Exception {
        try (BlockedPicker blocked = new BlockedPicker()) {
            executorField.set(null, blocked.executor);
            FullDataSourceProviderV2 provider = mock(FullDataSourceProviderV2.class);
            LodRenderSection section = section(provider, new AtomicInteger());
            Method load = LodRenderSection.class.getDeclaredMethod("getRenderSourceForPosAsync", long.class, EDhDirection.class);
            load.setAccessible(true);
            CompletableFuture<ColumnRenderSource> future = (CompletableFuture<ColumnRenderSource>) load.invoke(section, pos, null);
            blocked.executor.shutdownNow();
            assertTrue(future.isDone(), "dropped center/neighbor loads must release the aggregate build");
            verify(provider, never()).get(anyLong());
            section.close();
        }
    }

    @Test
    void failedRenderLoadTerminatesBuildAndClearsUploadOwnership() throws Exception {
        PriorityTaskPicker picker = new PriorityTaskPicker();
        PriorityTaskPicker.Executor executor = picker.createExecutor("section-failed-load-test");
        executorField.set(null, executor);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        FullDataSourceProviderV2 provider = mock(FullDataSourceProviderV2.class);
        when(provider.get(pos)).thenAnswer(invocation -> {
            entered.countDown();
            assertTrue(release.await(3, TimeUnit.SECONDS));
            throw new AssertionError("source read failed");
        });
        AtomicInteger uploads = new AtomicInteger();
        LodRenderSection section = section(provider, uploads);
        try {
            assertTrue(section.uploadRenderDataToGpuAsync());
            CompletableFuture<Void> build = section.getRenderDataBuildFuture();
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            release.countDown();
            assertThrows(java.util.concurrent.ExecutionException.class, () -> build.get(3, TimeUnit.SECONDS));
            assertTimeoutPreemptively(java.time.Duration.ofSeconds(3), () -> {
                while (uploads.get() != 0 || section.gpuUploadInProgress()) Thread.sleep(1);
            });
            assertEquals(0, uploads.get());
            assertFalse(section.gpuUploadInProgress());
            assertNull(queuedRunnable(section));
        } finally {
            release.countDown();
            section.close();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(3, TimeUnit.SECONDS));
            Config.Common.MultiThreading.numberOfThreads.removeListener(executor);
        }
    }

    private static Object queuedRunnable(LodRenderSection section) throws Exception {
        Field field = LodRenderSection.class.getDeclaredField("getAndBuildRenderDataRunnable");
        field.setAccessible(true);
        return field.get(section);
    }

    private LodRenderSection section(FullDataSourceProviderV2 provider, AtomicInteger uploads) {
        return new LodRenderSection(pos, mock(LodQuadTree.class), mock(IDhClientLevel.class), provider, uploads);
    }

    private static final class BlockedPicker implements AutoCloseable {
        final PriorityTaskPicker picker = new PriorityTaskPicker();
        final PriorityTaskPicker.Executor executor = picker.createExecutor("section-admission-test");
        final CountDownLatch release = new CountDownLatch(1);

        BlockedPicker() throws Exception {
            CountDownLatch started = new CountDownLatch(1);
            executor.execute(() -> {
                started.countDown();
                try { release.await(); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            });
            assertTrue(started.await(5, TimeUnit.SECONDS), "real picker blocker did not start");
        }

        @Override public void close() throws Exception {
            release.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
            Config.Common.MultiThreading.numberOfThreads.removeListener(executor);
        }
    }
}
