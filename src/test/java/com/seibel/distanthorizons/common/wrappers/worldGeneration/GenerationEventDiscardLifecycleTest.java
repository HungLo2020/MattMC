package com.seibel.distanthorizons.common.wrappers.worldGeneration;

import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiDistantGeneratorMode;
import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiWorldGenerationStep;
import com.seibel.distanthorizons.common.wrappers.worldGeneration.params.ThreadWorldGenParams;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.pos.DhChunkPos;
import com.seibel.distanthorizons.core.util.objects.RunOnThisThreadExecutorService;
import com.seibel.distanthorizons.core.util.threading.PriorityTaskPicker;
import com.seibel.distanthorizons.core.config.eventHandlers.presets.ThreadPresetConfigEventHandler;
import org.junit.jupiter.api.*;

import java.lang.reflect.Field;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual GenerationEvent futures must end when their picker work is discarded. */
@Timeout(10)
class GenerationEventDiscardLifecycleTest {
    private PriorityTaskPicker picker;
    private PriorityTaskPicker.Executor blocker;
    private PriorityTaskPicker.Executor executor;
    private final CountDownLatch release = new CountDownLatch(1);
    private int oldThreads;
    private double oldRatio;

    @BeforeEach void setUp() throws Exception {
        ThreadPresetConfigEventHandler.INSTANCE.toString();
        oldThreads = Config.Common.MultiThreading.numberOfThreads.get();
        oldRatio = Config.Common.MultiThreading.threadRunTimeRatio.get();
        Config.Common.MultiThreading.numberOfThreads.setWithoutFiringEvents(1);
        Config.Common.MultiThreading.threadRunTimeRatio.setWithoutFiringEvents(1.0);
        picker = new PriorityTaskPicker();
        blocker = picker.createExecutor("generation-owner-blocker");
        executor = picker.createExecutor("generation-owner-work");
        CountDownLatch started = new CountDownLatch(1);
        blocker.execute(() -> { started.countDown(); await(release); });
        assertTrue(started.await(2, TimeUnit.SECONDS));
    }

    @AfterEach void tearDown() throws Exception {
        release.countDown();
        for (PriorityTaskPicker.Executor pool : new PriorityTaskPicker.Executor[]{executor, blocker}) {
            if (pool != null) {
                pool.shutdownNow();
                pool.awaitTermination(2, TimeUnit.SECONDS);
                Config.Common.MultiThreading.numberOfThreads.removeListener(pool);
            }
        }
        if (picker != null) picker.shutdownNow();
        Config.Common.MultiThreading.numberOfThreads.setWithoutFiringEvents(oldThreads);
        Config.Common.MultiThreading.threadRunTimeRatio.setWithoutFiringEvents(oldRatio);
    }

    @Test void clearingAcceptedGenerationCancelsItsOwnerFuture() {
        BatchGenerationEnvironment environment = mock(BatchGenerationEnvironment.class);
        GenerationEvent event = start(environment, EDhApiDistantGeneratorMode.SURFACE, executor);
        assertEquals(1, executor.getQueueSize());
        executor.clearQueue();
        assertTrue(event.future.isCancelled(), "accepted generation must become terminal when its picker task is discarded");
        verify(environment, never()).generateEvent(any());
    }

    @Test void shuttingDownAcceptedGenerationCancelsItsOwnerFuture() {
        GenerationEvent event = start(mock(BatchGenerationEnvironment.class), EDhApiDistantGeneratorMode.SURFACE, executor);
        executor.shutdownNow();
        assertTrue(event.future.isCancelled());
    }

    @Test void immediateRejectedGenerationIsTerminal() {
        executor.shutdown();
        GenerationEvent event = start(mock(BatchGenerationEnvironment.class), EDhApiDistantGeneratorMode.SURFACE, executor);
        assertTrue(event.future.isCompletedExceptionally());
    }

    @Test void shutdownExceptionInsideGenerationCannotReportSuccess() {
        BatchGenerationEnvironment environment = mock(BatchGenerationEnvironment.class);
        doThrow(new RejectedExecutionException("generation stopped")).when(environment).generateEvent(any());
        GenerationEvent event = start(environment, EDhApiDistantGeneratorMode.SURFACE, new RunOnThisThreadExecutorService());
        assertTrue(event.future.isCompletedExceptionally(), "shutdown must be terminal failure, not successful missing data");
    }

    @Test void shutdownExceptionInsideInternalServerGenerationCannotLeaveFuturePending() throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        BatchGenerationEnvironment environment = mock(BatchGenerationEnvironment.class);
        InternalServerGenerator internal = mock(InternalServerGenerator.class);
        Field field = BatchGenerationEnvironment.class.getDeclaredField("internalServerGenerator");
        field.setAccessible(true);
        field.set(environment, internal);
        doThrow(new RejectedExecutionException("internal generation stopped")).when(internal).generateChunksViaInternalServer(any());
        GenerationEvent event = start(environment, EDhApiDistantGeneratorMode.INTERNAL_SERVER, new RunOnThisThreadExecutorService());
        assertTrue(event.future.isCompletedExceptionally(), "internal shutdown must not leave the generation future pending");
    }

    @Test void successfulGenerationStillCompletesNormally() {
        BatchGenerationEnvironment environment = mock(BatchGenerationEnvironment.class);
        GenerationEvent event = start(environment, EDhApiDistantGeneratorMode.SURFACE, new RunOnThisThreadExecutorService());
        assertTrue(event.future.isDone());
        assertFalse(event.future.isCompletedExceptionally());
        verify(environment).generateEvent(event);
    }

    private static GenerationEvent start(BatchGenerationEnvironment environment, EDhApiDistantGeneratorMode mode, ExecutorService pool) {
        // World/registry construction is the boundary; the event and its scheduling are real.
        try (var params = mockStatic(ThreadWorldGenParams.class)) {
            return GenerationEvent.start(new DhChunkPos(0, 0), 1, environment, mode,
                EDhApiWorldGenerationStep.FEATURES, chunk -> {}, pool);
        }
    }

    private static void await(CountDownLatch latch) {
        try { latch.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
