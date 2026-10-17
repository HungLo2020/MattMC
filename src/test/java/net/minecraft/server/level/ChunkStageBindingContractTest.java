package net.minecraft.server.level;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkPyramid;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkStep;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

/** Actual callback roots and independently reentrant completion resources. */
@Isolated
class ChunkStageBindingContractTest {
    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }
    static final class Engine extends CompletableFuture<ChunkAccess> {
        final CompletableFuture<ChunkResult<ChunkAccess>> exposed = new CompletableFuture<>();
        BiFunction<ChunkAccess, Throwable, ChunkResult<ChunkAccess>> callback;
        Runnable onRegistration = () -> {};
        RuntimeException registrationFailure;
        @Override @SuppressWarnings({"unchecked", "rawtypes"})
        public <U> CompletableFuture<U> handle(BiFunction<? super ChunkAccess, Throwable, ? extends U> action) {
            callback = (BiFunction)action;
            onRegistration.run();
            if (registrationFailure != null) throw registrationFailure;
            return (CompletableFuture<U>)exposed;
        }
        ChunkResult<ChunkAccess> fire(ChunkAccess chunk) { return callback.apply(chunk, null); }
    }
    static final class Fixture {
        final GenerationChunkHolderContractTest.Holder holder = new GenerationChunkHolderContractTest.Holder(0, 0);
        final GenerationChunkHolderContractTest.MapStub map = new GenerationChunkHolderContractTest.MapStub();
        final Engine engine = new Engine();
        Fixture() { map.next = engine; }
        CompletableFuture<ChunkResult<ChunkAccess>> apply() {
            return GenerationChunkHolderContractTest.apply(holder, map, ChunkStatus.EMPTY);
        }
    }
    @Test void synchronousRegistrationCallbackPublishesBeforeTheActualHandleReturns() {
        Fixture f = new Fixture();
        ProtoChunk chunk = mock(ProtoChunk.class);
        f.engine.onRegistration = () -> {
            var result = f.engine.fire(chunk);
            var slot = f.holder.getAllFutures().getFirst().getSecond();
            assertTrue(slot.isDone());
            assertSame(chunk, slot.join().orElse(null));
            assertNotSame(result, slot.join());
            f.engine.exposed.complete(result);
        };
        assertSame(f.engine.exposed, f.apply());
        assertSame(chunk, f.engine.exposed.join().orElse(null));
    }
    @Test void registrationThrowAfterSynchronousCompletionKeepsItsPublishedPrefixAndCallableCallback() {
        Fixture f = new Fixture();
        ProtoChunk chunk = mock(ProtoChunk.class);
        RuntimeException failure = new RuntimeException("after actual synchronous callback");
        f.engine.onRegistration = () -> f.engine.fire(chunk);
        f.engine.registrationFailure = failure;
        assertSame(failure, assertThrows(RuntimeException.class, f::apply));
        assertSame(chunk, f.holder.getAllFutures().getFirst().getSecond().join().orElse(null));
        var repeated = assertThrows(IllegalStateException.class, () -> f.engine.fire(chunk));
        assertEquals("Trying to complete a future but found it to be completed successfully already", repeated.getMessage());
    }
    record CallbackLease(BiFunction<ChunkAccess, Throwable, ChunkResult<ChunkAccess>> callback,
            WeakReference<?> holder, WeakReference<?> step, WeakReference<?> map,
            WeakReference<?> cache, WeakReference<?> engine, WeakReference<?> work) {}
    static CallbackLease detachedCallback() {
        var holder = new GenerationChunkHolderContractTest.Holder(0, 0);
        var map = new GenerationChunkHolderContractTest.MapStub();
        var engine = new Engine(); map.next = engine;
        var original = ChunkPyramid.LOADING_PYRAMID.getStepTo(ChunkStatus.EMPTY);
        var step = new ChunkStep(original.targetStatus(), original.directDependencies(), original.accumulatedDependencies(),
            original.blockStateWriteRadius(), original.task());
        var cache = StaticCache2D.<GenerationChunkHolder>create(0, 0, 0, (x, z) -> holder);
        var work = holder.applyStep(step, map, cache);
        return new CallbackLease(engine.callback, new WeakReference<>(holder), new WeakReference<>(step),
            new WeakReference<>(map), new WeakReference<>(cache), new WeakReference<>(engine), new WeakReference<>(work));
    }
    static WeakReference<?>[] useDetachedCallback() throws Exception {
        var lease = detachedCallback();
        ChunkStageInvocationContractTest.collected(lease.map, lease.cache, lease.engine, lease.work);
        assertNotNull(lease.holder.get(), "callable callback still needs its actual holder");
        assertNotNull(lease.step.get(), "callable callback still needs its original step");
        ProtoChunk chunk = mock(ProtoChunk.class);
        assertSame(chunk, lease.callback.apply(chunk, null).orElse(null));
        return new WeakReference<?>[]{lease.holder, lease.step};
    }
    @Test void retainedCallbackAloneOwnsHolderAndStepWithoutRetainingMapCacheEngineOrWork() throws Exception {
        var roots = useDetachedCallback();
        framework().clearInlineMocks();
        org.mockito.internal.progress.ThreadSafeMockingProgress.mockingProgress().resetOngoingStubbing();
        ChunkStageInvocationContractTest.collected(roots);
    }
    static final class TaskHolder extends GenerationChunkHolder {
        final java.util.List<CompletableFuture<?>> saves = new java.util.ArrayList<>();
        WeakReference<GeneratingChunkMap> expectedMap;
        WeakReference<StaticCache2D<GenerationChunkHolder>> observedCache;
        TaskHolder() { super(net.minecraft.world.level.ChunkPos.ZERO); updateHighestAllowedStatus(null); }
        public int getTicketLevel() { return 33; }
        public int getQueueLevel() { return 33; }
        protected void addSaveDependency(CompletableFuture<?> future) { saves.add(future); }
        @Override CompletableFuture<ChunkResult<ChunkAccess>> applyStep(ChunkStep step,
                GeneratingChunkMap map, StaticCache2D<GenerationChunkHolder> cache) {
            assertSame(expectedMap.get(), map, "genuine holder entry received a replacement map argument");
            observedCache = new WeakReference<>(cache);
            return super.applyStep(step, map, cache);
        }
    }
    record CancelledTaskCallback(BiFunction<ChunkAccess, Throwable, ChunkResult<ChunkAccess>> callback,
            WeakReference<TaskHolder> holder, WeakReference<?> task,
            WeakReference<?> map, WeakReference<?> cache) {}
    static long[] nativeBindingStatistics() {
        if (!Boolean.getBoolean("stage.bindingWitness")) return null;
        try {
            var stats = Class.forName("net.minecraft.server.level.NativeChunkGeneration")
                .getDeclaredMethod("stageBindingStatistics");
            stats.setAccessible(true);
            return (long[])stats.invoke(null);
        } catch (ClassNotFoundException | NoSuchMethodException absent) {
            return null; // Assert only after exercising the genuine task and callback below.
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    static CancelledTaskCallback cancelledTaskCallback() {
        long[] before = nativeBindingStatistics();
        var holder = new TaskHolder();
        var engine = new Engine();
        var map = new GenerationChunkHolderContractTest.MapStub() {
            @Override public GenerationChunkHolder acquireGeneration(long key) {
                assertEquals(0L, key); holder.increaseGenerationRefCount(); return holder;
            }
            @Override public void releaseGeneration(GenerationChunkHolder cell) {
                assertSame(holder, cell); cell.decreaseGenerationRefCount();
            }
            @Override public CompletableFuture<ChunkAccess> applyStep(GenerationChunkHolder cell,
                    ChunkStep step, StaticCache2D<GenerationChunkHolder> cache) {
                assertSame(holder, cell);
                assertSame(holder.observedCache.get(), cache, "actual engine received a replacement cache argument");
                return engine;
            }
        };
        holder.expectedMap = new WeakReference<>(map);
        var task = ChunkGenerationTask.create(map, ChunkStatus.EMPTY, net.minecraft.world.level.ChunkPos.ZERO);
        assertSame(engine.exposed, task.runUntilWait());
        task.markForCancellation();
        engine.exposed.complete(GenerationChunkHolder.UNLOADED_CHUNK);
        assertNull(task.runUntilWait());
        assertTrue(holder.saves.stream().allMatch(CompletableFuture::isDone));
        if (Boolean.getBoolean("stage.bindingWitness")) {
            long[] after = nativeBindingStatistics();
            assertNotNull(before, "actual task still has no native stage binding owner");
            assertNotNull(after, "actual task still has no native stage binding owner");
            assertEquals(before[1] + 1, after[1], "actual admission must create one binding");
            assertEquals(before[4] + 1, after[4], "actual registration must create one callback lease");
            assertEquals(before[6], after[6], "external work completion invoked the engine callback");
            assertTrue(after[7] >= before[7] + 2, "actual task did not consume its binding");
            assertTrue(after[8] >= before[8] + 1, "actual pending task did not consume its binding");
        }
        return new CancelledTaskCallback(engine.callback, new WeakReference<>(holder), new WeakReference<>(task),
            new WeakReference<>(map), holder.observedCache);
    }
    static WeakReference<?> lateAfterTaskRelease() throws Exception {
        var lease = cancelledTaskCallback();
        ChunkStageInvocationContractTest.collected(lease.task, lease.map, lease.cache);
        assertNotNull(lease.holder.get(), "task release retired resources still needed by the real callback");
        ProtoChunk chunk = mock(ProtoChunk.class);
        assertSame(chunk, lease.callback.apply(chunk, null).orElse(null));
        assertSame(chunk, lease.holder.get().getAllFutures().getFirst().getSecond().join().orElse(null));
        assertTrue(lease.holder.get().saves.stream().allMatch(CompletableFuture::isDone));
        return lease.holder;
    }
    @Test void realCallbackSurvivesActualTaskCancellationReleaseAndCollection() throws Exception {
        var holder = lateAfterTaskRelease();
        framework().clearInlineMocks();
        org.mockito.internal.progress.ThreadSafeMockingProgress.mockingProgress().resetOngoingStubbing();
        ChunkStageInvocationContractTest.collected(holder);
    }
    static final class BarrierStatus extends ChunkStatus {
        final int index;
        final CyclicBarrier barrier;
        BarrierStatus(int index, CyclicBarrier barrier) {
            super(ChunkStatus.EMPTY, java.util.EnumSet.noneOf(net.minecraft.world.level.levelgen.Heightmap.Types.class),
                net.minecraft.world.level.chunk.status.ChunkType.PROTOCHUNK);
            this.index = index; this.barrier = barrier;
        }
        @Override public int getIndex() {
            try { barrier.await(5, TimeUnit.SECONDS); }
            catch (Exception failure) { throw new AssertionError("concurrent stage callback was serialized across a VM effect", failure); }
            return index;
        }
        @Override public String getName() { return "test:concurrent_stage_" + index; }
    }
    @Test void concurrentInvocationsOfOneCallbackKeepIndependentActualResources() throws Exception {
        Fixture f = new Fixture();
        var barrier = new CyclicBarrier(2);
        var leftStatus = new BarrierStatus(0, barrier);
        var rightStatus = new BarrierStatus(1, barrier);
        Map<Thread, ChunkStatus> targets = new ConcurrentHashMap<>();
        ChunkStep step = mock(ChunkStep.class);
        when(step.targetStatus()).thenAnswer(ignored -> targets.getOrDefault(Thread.currentThread(), ChunkStatus.EMPTY));
        assertSame(f.engine.exposed, f.holder.applyStep(step, f.map, null));
        ProtoChunk left = mock(ProtoChunk.class), right = mock(ProtoChunk.class);
        try (var threads = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first = threads.submit(() -> {
                targets.put(Thread.currentThread(), leftStatus);
                try { return f.engine.fire(left); } finally { targets.remove(Thread.currentThread()); }
            });
            var second = threads.submit(() -> {
                targets.put(Thread.currentThread(), rightStatus);
                try { return f.engine.fire(right); } finally { targets.remove(Thread.currentThread()); }
            });
            assertSame(left, first.get(10, TimeUnit.SECONDS).orElse(null));
            assertSame(right, second.get(10, TimeUnit.SECONDS).orElse(null));
        }
        var slots = f.holder.getAllFutures();
        assertSame(left, slots.get(0).getSecond().join().orElse(null));
        assertSame(right, slots.get(1).getSecond().join().orElse(null));
    }
}
