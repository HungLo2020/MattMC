package com.seibel.distanthorizons.core.generation;

import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiWorldGeneratorReturnType;
import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiDistantGeneratorMode;
import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiWorldGenerationStep;
import com.seibel.distanthorizons.common.wrappers.worldGeneration.BatchGenerationEnvironment;
import com.seibel.distanthorizons.common.wrappers.worldGeneration.GenerationEvent;
import com.seibel.distanthorizons.common.wrappers.worldGeneration.params.ThreadWorldGenParams;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.pos.DhChunkPos;
import com.seibel.distanthorizons.core.util.threading.PriorityTaskPicker;
import com.seibel.distanthorizons.core.util.threading.ThreadPoolUtil;
import com.seibel.distanthorizons.api.interfaces.override.worldGenerator.IDhApiWorldGenerator;
import com.seibel.distanthorizons.core.generation.tasks.IWorldGenTaskTracker;
import com.seibel.distanthorizons.core.generation.tasks.WorldGenResult;
import com.seibel.distanthorizons.core.level.IDhServerLevel;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos2D;
import com.seibel.distanthorizons.core.config.eventHandlers.presets.ThreadPresetConfigEventHandler;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Method;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Tests real queue ownership using controlled completions at the world-generator boundary. */
@Timeout(10)
class WorldGenerationQueueLifecycleTest {
    private IDhApiWorldGenerator generator;
    private WorldGenerationQueue queue;
    private static final long POS = DhSectionPos.encode((byte) 6, 0, 0);

    @BeforeEach void setUp() {
        ThreadPresetConfigEventHandler.INSTANCE.toString();
        generator = mock(IDhApiWorldGenerator.class);
        when(generator.getReturnType()).thenReturn(EDhApiWorldGeneratorReturnType.VANILLA_CHUNKS);
        queue = new WorldGenerationQueue(generator, mock(IDhServerLevel.class));
    }

    @AfterEach void tearDown() { if (queue != null) queue.close(); }

    @Test void immediateSuccessfulCompletionDoesNotLeaveStaleInProgressEntry() throws Exception {
        verifyImmediate(CompletableFuture.completedFuture(null), true);
    }

    @Test void immediateFailedCompletionDoesNotLeaveStaleInProgressEntry() throws Exception {
        verifyImmediate(CompletableFuture.failedFuture(new IllegalStateException("generator failed")), false);
    }

    @Test void immediateCancelledCompletionDoesNotLeaveStaleInProgressEntry() throws Exception {
        CompletableFuture<Void> generation = new CompletableFuture<>();
        generation.cancel(false);
        verifyImmediate(generation, false);
    }

    @Test void asynchronousCancellationReleasesInProgressOwnership() throws Exception {
        CompletableFuture<Void> generation = new CompletableFuture<>();
        useGenerationFuture(generation);
        CompletableFuture<WorldGenResult> result = submit();
        assertTrue(startNext());
        assertEquals(1, queue.getInProgressTaskCount());
        generation.cancel(false);
        assertFalse(result.get(1, TimeUnit.SECONDS).success);
        assertEquals(0, queue.getInProgressTaskCount());
    }

    @Test void synchronousSubmissionFailureTerminatesRetrievalAndReleasesOwnership() throws Exception {
        when(generator.generateChunks(anyInt(), anyInt(), anyInt(), anyByte(), any(), any(), any()))
            .thenThrow(new RejectedExecutionException("generation stopped before return"));
        CompletableFuture<WorldGenResult> result = submit();
        assertDoesNotThrow(this::startNext);
        assertTrue(result.isDone(), "a synchronous generator failure must not orphan the selected request");
        assertFalse(result.get(1, TimeUnit.SECONDS).success);
        assertEquals(0, queue.getInProgressTaskCount());
    }

    @Test void removingWaitingRequestTerminatesItsFuture() {
        CompletableFuture<WorldGenResult> result = submit();
        queue.removeRetrievalRequestIf(pos -> pos == POS);
        assertEquals(0, queue.getWaitingTaskCount());
        assertTrue(result.isDone(), "removing queued ownership must terminate the caller's future");
    }

    @Test void replacingWaitingRequestTerminatesThePreviousFuture() {
        CompletableFuture<WorldGenResult> previous = submit();
        CompletableFuture<WorldGenResult> current = submit();
        assertTrue(previous.isDone(), "same-position replacement must not orphan the previous caller");
        assertFalse(current.isDone());
        assertEquals(1, queue.getWaitingTaskCount());
    }

    @Test void cancellingWaitingRequestReleasesItsAdmissionSlot() {
        CompletableFuture<WorldGenResult> result = submit();
        result.cancel(false);
        assertEquals(0, queue.getWaitingTaskCount());
    }

    @Test void closingTerminatesAndRemovesWaitingRequests() throws Exception {
        CompletableFuture<WorldGenResult> result = submit();
        queue.startClosingAsync(true, true).get(1, TimeUnit.SECONDS);
        assertTrue(result.isDone());
        assertEquals(0, queue.getWaitingTaskCount());
    }

    @Test void discardingRealGenerationEventReleasesRetrievalAndQueueOwnership() throws Exception {
        int oldThreads = Config.Common.MultiThreading.numberOfThreads.get();
        Config.Common.MultiThreading.numberOfThreads.setWithoutFiringEvents(1);
        PriorityTaskPicker picker = new PriorityTaskPicker();
        PriorityTaskPicker.Executor executor = picker.createExecutor("queue-generation-owner");
        CountDownLatch release = new CountDownLatch(1);
        try (var pools = mockStatic(ThreadPoolUtil.class); var params = mockStatic(ThreadWorldGenParams.class)) {
            pools.when(ThreadPoolUtil::getWorldGenExecutor).thenReturn(executor);
            CountDownLatch entered = new CountDownLatch(1);
            executor.execute(() -> {
                entered.countDown();
                try { release.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            });
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            BatchGenerationEnvironment environment = mock(BatchGenerationEnvironment.class);
            when(generator.generateChunks(anyInt(), anyInt(), anyInt(), anyByte(), any(), any(), any()))
                .thenAnswer(invocation -> GenerationEvent.start(new DhChunkPos(0, 0), 1, environment,
                    EDhApiDistantGeneratorMode.SURFACE, EDhApiWorldGenerationStep.FEATURES, chunk -> {}, executor).future);
            CompletableFuture<WorldGenResult> result = submit();
            assertTrue(startNext());
            assertEquals(1, executor.getQueueSize());
            assertEquals(1, queue.getInProgressTaskCount());
            executor.clearQueue();
            assertTrue(result.isDone(), "picker discard must reach both actual generation owners");
            assertFalse(result.get(1, TimeUnit.SECONDS).success);
            assertEquals(0, queue.getInProgressTaskCount());
        } finally {
            release.countDown();
            executor.shutdownNow();
            executor.awaitTermination(2, TimeUnit.SECONDS);
            Config.Common.MultiThreading.numberOfThreads.removeListener(executor);
            picker.shutdownNow();
            Config.Common.MultiThreading.numberOfThreads.setWithoutFiringEvents(oldThreads);
        }
    }

    @Test void gracefulCloseDuringGeneratorSubmissionWaitsForItsOwnedCompletion() throws Exception {
        java.util.concurrent.atomic.AtomicReference<CompletableFuture<Void>> closing = new java.util.concurrent.atomic.AtomicReference<>();
        CompletableFuture<Void> generation = new CompletableFuture<>();
        when(generator.generateChunks(anyInt(), anyInt(), anyInt(), anyByte(), any(), any(), any()))
            .thenAnswer(invocation -> {
                assertEquals(1, queue.getInProgressTaskCount(), "submission must already have a published owner");
                closing.set(queue.startClosingAsync(false, false));
                assertFalse(closing.get().isDone(), "graceful closure must await a generator still submitting");
                return generation;
            });
        CompletableFuture<WorldGenResult> result = submit();
        assertTrue(startNext());
        assertFalse(result.isDone());
        generation.complete(null);
        closing.get().get(1, TimeUnit.SECONDS);
        assertTrue(result.get(1, TimeUnit.SECONDS).success);
        assertEquals(0, queue.getInProgressTaskCount());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void cancellationPreservesTheRequestedWorkerInterruptPolicy(boolean interrupt) throws Exception {
        RecordingCancellationFuture generation = new RecordingCancellationFuture();
        useGenerationFuture(generation);
        CompletableFuture<WorldGenResult> result = submit();
        assertTrue(startNext());
        queue.startClosingAsync(true, interrupt).get(1, TimeUnit.SECONDS);
        assertEquals(java.util.List.of(interrupt), generation.cancellations);
        assertTrue(generation.isCancelled());
        assertFalse(result.get(1, TimeUnit.SECONDS).success);
        assertEquals(0, queue.getInProgressTaskCount());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void cancellationDuringSubmissionForwardsItsPolicyWhenWorkerArrives(boolean interrupt) throws Exception {
        RecordingCancellationFuture generation = new RecordingCancellationFuture();
        when(generator.generateChunks(anyInt(), anyInt(), anyInt(), anyByte(), any(), any(), any()))
            .thenAnswer(invocation -> {
                queue.startClosingAsync(true, interrupt).get(1, TimeUnit.SECONDS);
                assertTrue(generation.cancellations.isEmpty(), "worker has not been returned to the queue yet");
                return generation;
            });
        CompletableFuture<WorldGenResult> result = submit();
        assertTrue(startNext());
        assertEquals(java.util.List.of(interrupt), generation.cancellations);
        assertTrue(generation.isCancelled());
        assertFalse(result.get(1, TimeUnit.SECONDS).success);
        assertEquals(0, queue.getInProgressTaskCount());
    }

    @Test void synchronousGeneratorErrorReleasesOwnershipBeforePropagating() throws Exception {
        AssertionError failure = new AssertionError("fatal generator submission");
        when(generator.generateChunks(anyInt(), anyInt(), anyInt(), anyByte(), any(), any(), any())).thenThrow(failure);
        CompletableFuture<WorldGenResult> result = submit();
        java.lang.reflect.InvocationTargetException thrown = assertThrows(java.lang.reflect.InvocationTargetException.class, this::startNext);
        assertSame(failure, thrown.getCause());
        assertTrue(result.isDone());
        assertFalse(result.get(1, TimeUnit.SECONDS).success);
        assertEquals(0, queue.getInProgressTaskCount());
        assertEquals(0, queue.getWaitingTaskCount());
    }

    private static final class RecordingCancellationFuture extends CompletableFuture<Void> {
        final java.util.List<Boolean> cancellations = new CopyOnWriteArrayList<>();
        @Override public boolean cancel(boolean interrupt) {
            cancellations.add(interrupt);
            return super.cancel(interrupt);
        }
    }

    private void verifyImmediate(CompletableFuture<Void> generation, boolean success) throws Exception {
        useGenerationFuture(generation);
        CompletableFuture<WorldGenResult> result = submit();
        assertTrue(startNext());
        assertEquals(success, result.get(1, TimeUnit.SECONDS).success);
        assertEquals(0, queue.getInProgressTaskCount(), "completion before listener registration must not leave an inserted dead task");
        assertEquals(0, queue.getWaitingTaskCount());
    }

    private void useGenerationFuture(CompletableFuture<Void> generation) {
        when(generator.generateChunks(anyInt(), anyInt(), anyInt(), anyByte(), any(), any(), any())).thenReturn(generation);
    }

    private CompletableFuture<WorldGenResult> submit() {
        return queue.submitRetrievalTask(POS, (byte) 0, mock(IWorldGenTaskTracker.class));
    }

    private boolean startNext() throws Exception {
        Method method = WorldGenerationQueue.class.getDeclaredMethod("tryStartNextWorldGenTask", DhBlockPos2D.class);
        method.setAccessible(true);
        return (boolean) method.invoke(queue, DhBlockPos2D.ZERO);
    }
}
