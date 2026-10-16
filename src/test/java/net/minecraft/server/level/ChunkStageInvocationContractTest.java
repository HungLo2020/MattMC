package net.minecraft.server.level;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkStep;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

/** Genuine stage-future methods and completion effects, portable to Frozen. */
@Isolated
class ChunkStageInvocationContractTest {
    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }
    static final class Engine extends CompletableFuture<ChunkAccess> {
        final CompletableFuture<ChunkResult<ChunkAccess>> exposed = new CompletableFuture<>();
        BiFunction<ChunkAccess, Throwable, ?> callback;
        RuntimeException registrationFailure;
        int registrations;
        @Override @SuppressWarnings({"unchecked", "rawtypes"})
        public <U> CompletableFuture<U> handle(BiFunction<? super ChunkAccess, Throwable, ? extends U> action) {
            registrations++;
            callback = (BiFunction)action;
            if (registrationFailure != null) throw registrationFailure;
            return (CompletableFuture<U>)exposed;
        }
        @SuppressWarnings("unchecked")
        ChunkResult<ChunkAccess> fire(ChunkAccess chunk, Throwable failure) {
            return (ChunkResult<ChunkAccess>)callback.apply(chunk, failure);
        }
    }
    static final class Fixture {
        final GenerationChunkHolderContractTest.Holder holder = new GenerationChunkHolderContractTest.Holder(0, 0);
        final GenerationChunkHolderContractTest.MapStub map = new GenerationChunkHolderContractTest.MapStub();
        final Engine engine = new Engine();
        Fixture() { map.next = engine; }
        CompletableFuture<ChunkResult<ChunkAccess>> apply(ChunkStatus status) {
            return GenerationChunkHolderContractTest.apply(holder, map, status);
        }
    }
    @Test void actualHandleReplacementIsTheReturnedWorkFuture() {
        Fixture f = new Fixture();
        assertSame(f.engine.exposed, f.apply(ChunkStatus.EMPTY));
        var slot = f.apply(ChunkStatus.EMPTY);
        assertNotSame(f.engine.exposed, slot);
        assertEquals(1, f.engine.registrations);
        ProtoChunk chunk = mock(ProtoChunk.class);
        var result = f.engine.fire(chunk, null);
        assertSame(chunk, result.orElse(null));
        assertSame(chunk, slot.join().orElse(null));
        assertNotSame(result, slot.join(), "engine result and published-slot wrappers are distinct");
    }
    @Test void handleRegistrationThrowKeepsStartedWorkAndOriginalException() {
        Fixture f = new Fixture();
        RuntimeException failure = new RuntimeException("handle registration");
        f.engine.registrationFailure = failure;
        assertSame(failure, assertThrows(RuntimeException.class, () -> f.apply(ChunkStatus.EMPTY)));
        var slot = f.apply(ChunkStatus.EMPTY);
        assertFalse(slot.isDone());
        assertEquals(1, f.map.trace.size());
        assertEquals(1, f.engine.registrations);
        assertSame(ChunkStatus.EMPTY, f.holder.getLatestStatus());
    }
    @Test void externalCancellationOfWorkDoesNotCancelItsStatusPublication() {
        Fixture f = new Fixture();
        var work = f.apply(ChunkStatus.EMPTY);
        var slot = f.apply(ChunkStatus.EMPTY);
        assertTrue(work.cancel(false));
        ProtoChunk chunk = mock(ProtoChunk.class);
        assertSame(chunk, f.engine.fire(chunk, null).orElse(null));
        assertSame(chunk, slot.join().orElse(null));
        assertTrue(work.isCancelled());
    }
    @Test void downgradeDuringPendingEngineDoesNotRedirectTheCapturedCallback() {
        Fixture f = new Fixture();
        f.apply(ChunkStatus.EMPTY);
        var old = f.apply(ChunkStatus.EMPTY);
        f.holder.disallow();
        assertSame(GenerationChunkHolder.UNLOADED_CHUNK, old.join());
        ProtoChunk chunk = mock(ProtoChunk.class);
        assertSame(chunk, f.engine.fire(chunk, null).orElse(null));
        var replacement = f.holder.getAllFutures().getFirst().getSecond();
        assertNotSame(old, replacement);
        assertSame(chunk, replacement.join().orElse(null));
        assertSame(GenerationChunkHolder.UNLOADED_CHUNK, old.join());
    }
    @Test void repeatedCustomCallbackObservesActualObtrudedSlotOutcome() {
        Fixture f = new Fixture();
        f.apply(ChunkStatus.EMPTY);
        var slot = f.apply(ChunkStatus.EMPTY);
        ProtoChunk chunk = mock(ProtoChunk.class);
        f.engine.fire(chunk, null);
        RuntimeException changed = new RuntimeException("obtruded published slot");
        slot.obtrudeException(changed);
        assertSame(changed, assertThrows(CompletionException.class, () -> f.engine.fire(chunk, null)).getCause());
    }
    @Test void nestedSuccessfulStageCannotOverwriteOuterCompletionResources() {
        Fixture f = new Fixture();
        f.apply(ChunkStatus.EMPTY);
        var slot = f.apply(ChunkStatus.EMPTY);
        Engine nested = new Engine();
        ProtoChunk outerChunk = mock(ProtoChunk.class), nestedChunk = mock(ProtoChunk.class);
        var observer = slot.thenRun(() -> {
            f.map.next = nested;
            assertSame(nested.exposed, f.apply(ChunkStatus.STRUCTURE_STARTS));
            assertSame(nestedChunk, nested.fire(nestedChunk, null).orElse(null));
            assertSame(outerChunk, slot.join().orElse(null));
        });
        assertSame(outerChunk, f.engine.fire(outerChunk, null).orElse(null));
        observer.join();
        assertSame(outerChunk, f.holder.getAllFutures().getFirst().getSecond().join().orElse(null));
        assertSame(nestedChunk, f.holder.getAllFutures().get(1).getSecond().join().orElse(null));
    }
    @Test void fallbackRereadsTheActualStepTargetBeforeRequestingItsFuture() {
        Fixture f = new Fixture();
        f.apply(ChunkStatus.EMPTY);
        f.engine.fire(mock(ProtoChunk.class), null);
        ChunkStep step = mock(ChunkStep.class);
        when(step.targetStatus()).thenReturn(ChunkStatus.EMPTY, ChunkStatus.EMPTY, ChunkStatus.STRUCTURE_STARTS);
        var future = f.holder.applyStep(step, f.map, null);
        assertSame(future, f.holder.getAllFutures().get(1).getSecond());
        assertFalse(future.isDone());
        assertEquals(1, f.map.trace.size());
        verify(step, times(3)).targetStatus();
    }
    static long[] stageCountsWhenPresent() throws Exception {
        try {
            var method = Class.forName("net.minecraft.server.level.NativeChunkGeneration").getDeclaredMethod("stageStatistics");
            method.setAccessible(true);
            return (long[])method.invoke(null);
        } catch (ClassNotFoundException | NoSuchMethodException absentOnOriginal) { return null; }
    }
    @Test void actualTaskAdvancesMultipleStagesAndResumesItsPendingEngine() throws Exception {
        boolean witness = Boolean.getBoolean("stage.nativeWitness");
        long[] before = witness ? stageCountsWhenPresent() : null;
        var holders = new java.util.LinkedHashMap<Long, GenerationChunkHolderContractTest.Holder>();
        var pending = new CompletableFuture<ChunkAccess>();
        var calls = new java.util.ArrayList<ChunkStatus>();
        ProtoChunk chunk = mock(ProtoChunk.class);
        when(chunk.getPersistedStatus()).thenReturn(ChunkStatus.FULL);
        var map = new GenerationChunkHolderContractTest.MapStub() {
            @Override public GenerationChunkHolder acquireGeneration(long key) {
                var cell = holders.computeIfAbsent(key, k -> {
                    var pos = new net.minecraft.world.level.ChunkPos(k);
                    return new GenerationChunkHolderContractTest.Holder(pos.x, pos.z);
                });
                cell.increaseGenerationRefCount(); return cell;
            }
            @Override public void releaseGeneration(GenerationChunkHolder cell) { cell.decreaseGenerationRefCount(); }
            @Override public CompletableFuture<ChunkAccess> applyStep(GenerationChunkHolder cell, ChunkStep step,
                    net.minecraft.util.StaticCache2D<GenerationChunkHolder> cache) {
                calls.add(step.targetStatus());
                return cell.getPos().toLong() == 0 && step.targetStatus() == ChunkStatus.EMPTY
                    ? pending : CompletableFuture.completedFuture(chunk);
            }
        };
        var task = ChunkGenerationTask.create(map, ChunkStatus.STRUCTURE_STARTS, net.minecraft.world.level.ChunkPos.ZERO);
        var waiting = task.runUntilWait();
        assertNotNull(waiting); assertFalse(waiting.isDone());
        assertTrue(calls.contains(ChunkStatus.EMPTY));
        pending.complete(chunk);
        assertNull(task.runUntilWait());
        assertTrue(calls.contains(ChunkStatus.STRUCTURE_STARTS));
        assertTrue(holders.values().stream().flatMap(cell -> cell.saves.stream()).allMatch(CompletableFuture::isDone));
        if (witness) {
            assertNotNull(before, "native stage controller was not present");
            long[] after = stageCountsWhenPresent();
            assertTrue(after[0] - before[0] >= 2, "actual task did not enter native stage admission");
            assertTrue(after[1] - before[1] >= 2, "actual task did not dispatch multiple native-controlled stages");
            assertTrue(after[2] - before[2] >= 2, "actual pending stage did not resume through native completion");
            assertEquals(before[3], after[3], "successful production task reported fatal");
        }
        java.lang.ref.Reference.reachabilityFence(task);
    }
    record PendingLease(CompletableFuture<ChunkAccess> engine,
            CompletableFuture<ChunkResult<ChunkAccess>> work,
            java.lang.ref.WeakReference<?> map, java.lang.ref.WeakReference<?> cache,
            java.lang.ref.WeakReference<?> holder) {}
    static PendingLease pendingLease() {
        var holder = new GenerationChunkHolderContractTest.Holder(0, 0);
        var map = new GenerationChunkHolderContractTest.MapStub();
        var engine = new CompletableFuture<ChunkAccess>();
        map.next = engine;
        var cache = net.minecraft.util.StaticCache2D.<GenerationChunkHolder>create(0, 0, 0, (x, z) -> holder);
        var work = holder.applyStep(net.minecraft.world.level.chunk.status.ChunkPyramid.LOADING_PYRAMID
            .getStepTo(ChunkStatus.EMPTY), map, cache);
        return new PendingLease(engine, work, new java.lang.ref.WeakReference<>(map),
            new java.lang.ref.WeakReference<>(cache), new java.lang.ref.WeakReference<>(holder));
    }
    static void collected(java.lang.ref.WeakReference<?>... references) throws Exception {
        for (int attempt = 0; attempt < 160; attempt++) {
            System.gc(); Thread.sleep(20);
            if (java.util.Arrays.stream(references).allMatch(ref -> ref.get() == null)) return;
        }
        assertTrue(java.util.Arrays.stream(references).allMatch(ref -> ref.get() == null),
            "stage callback retained resources after their original last use");
    }
    @Test void pendingCallbackRetainsTheHolderButNotMapCacheOrCompletedHolderRoots() throws Exception {
        PendingLease lease = pendingLease();
        collected(lease.map, lease.cache);
        assertNotNull(lease.holder.get(), "the actual pending callback still needs its original holder");
        lease.engine.complete(mock(ProtoChunk.class));
        org.mockito.Mockito.framework().clearInlineMocks();
        org.mockito.internal.progress.ThreadSafeMockingProgress.mockingProgress().resetOngoingStubbing();
        collected(lease.holder);
        java.lang.ref.Reference.reachabilityFence(lease.engine);
        java.lang.ref.Reference.reachabilityFence(lease.work);
    }
    @Test @SuppressWarnings("unchecked")
    void exceptionalEngineCallbackReportsFatalBeforeReturningWithoutPublishingAStatus() throws Exception {
        var field = net.minecraft.server.MinecraftServer.class.getDeclaredField("fatalException");
        field.setAccessible(true);
        var fatal = (AtomicReference<RuntimeException>)field.get(null);
        RuntimeException original = fatal.getAndSet(null);
        try {
            Fixture f = new Fixture();
            f.apply(ChunkStatus.EMPTY);
            var slot = f.apply(ChunkStatus.EMPTY);
            RuntimeException failure = new RuntimeException("actual engine failure");
            ProtoChunk chunk = mock(ProtoChunk.class);
            assertSame(chunk, f.engine.fire(chunk, failure).orElse(null));
            assertNotNull(fatal.get());
            assertSame(failure, fatal.get().getCause());
            assertFalse(slot.isDone(), "fatal engine callback does not publish the successful slot");
        } finally { fatal.set(original); }
    }
}
