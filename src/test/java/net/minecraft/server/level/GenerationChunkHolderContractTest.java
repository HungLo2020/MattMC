package net.minecraft.server.level;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.concurrent.CompletableFuture;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkPyramid;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkStep;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

/** Observable contracts shared with the pre-migration implementation. No private-field probes. */
class GenerationChunkHolderContractTest {
    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }
    static final class Holder extends GenerationChunkHolder {
        int level = 33;
        final List<CompletableFuture<?>> saves = new ArrayList<>();
        Runnable onSave = () -> {};
        Holder(int x, int z) { super(new ChunkPos(x, z)); updateHighestAllowedStatus(null); }
        public int getTicketLevel() { return level; }
        public int getQueueLevel() { return level; }
        protected void addSaveDependency(CompletableFuture<?> future) { saves.add(future); onSave.run(); }
        void disallow() { level = ChunkLevel.MAX_LEVEL + 1; updateHighestAllowedStatus(null); }
        void allow() { level = 33; updateHighestAllowedStatus(null); }
    }

    static class MapStub implements GeneratingChunkMap {
        final List<String> trace = new ArrayList<>();
        CompletableFuture<ChunkAccess> next = new CompletableFuture<>();
        public GenerationChunkHolder acquireGeneration(long key) { throw new AssertionError("unexpected acquire"); }
        public void releaseGeneration(GenerationChunkHolder holder) { throw new AssertionError("unexpected release"); }
        public CompletableFuture<ChunkAccess> applyStep(GenerationChunkHolder holder, ChunkStep step,
                StaticCache2D<GenerationChunkHolder> cache) {
            trace.add(step.targetStatus().getName() + ":" + holder.getPos());
            return next;
        }
        public ChunkGenerationTask scheduleGenerationTask(ChunkStatus target, ChunkPos pos) { throw new AssertionError("unexpected reschedule"); }
        public void runGenerationTasks() { throw new AssertionError("unexpected drive"); }
    }

    static CompletableFuture<ChunkResult<ChunkAccess>> apply(Holder holder, MapStub map, ChunkStatus status) {
        return holder.applyStep(ChunkPyramid.LOADING_PYRAMID.getStepTo(status), map, null);
    }

    static final class IndexedStatus extends ChunkStatus {
        final int index;
        int indexQueries;
        IndexedStatus(int index) {
            super(ChunkStatus.EMPTY, java.util.EnumSet.noneOf(net.minecraft.world.level.levelgen.Heightmap.Types.class),
                net.minecraft.world.level.chunk.status.ChunkType.PROTOCHUNK);
            this.index = index;
        }
        @Override public int getIndex() { indexQueries++; return index; }
        @Override public boolean isAfter(ChunkStatus other) { return false; }
        @Override public String getName() { return "test:foreign_index"; }
    }

    static int[] invalidFutureIndices() {
        int size = ChunkStatus.getStatusList().size();
        return new int[]{-1, size, size + 1, size + 2, Integer.MAX_VALUE};
    }

    @Test void uncheckedForeignIndicesKeepTheOriginalFutureArrayBoundary() {
        int size = ChunkStatus.getStatusList().size();
        for (int index : invalidFutureIndices()) {
            Holder holder = new Holder(0, 0);
            IndexedStatus foreign = new IndexedStatus(index);
            var failure = assertThrowsExactly(ArrayIndexOutOfBoundsException.class,
                () -> holder.getChunkIfPresentUnchecked(foreign));
            assertEquals("Index " + index + " out of bounds for length " + size, failure.getMessage());
            assertEquals(1, foreign.indexQueries);
            assertTrue(holder.getAllFutures().stream().allMatch(pair -> pair.getSecond() == null));
        }
    }

    @Test void invalidForeignRequestThrowsBeforeTaskAdmissionOrFuturePublication() {
        int size = ChunkStatus.getStatusList().size();
        for (int index : invalidFutureIndices()) {
            Holder holder = new Holder(0, 0);
            IndexedStatus foreign = new IndexedStatus(index);
            ChunkMap map = mock(ChunkMap.class);
            var failure = assertThrowsExactly(ArrayIndexOutOfBoundsException.class,
                () -> holder.scheduleChunkGenerationTask(foreign, map));
            assertEquals("Index " + index + " out of bounds for length " + size, failure.getMessage());
            assertEquals(1, foreign.indexQueries);
            verifyNoInteractions(map);
            assertTrue(holder.getAllFutures().stream().allMatch(pair -> pair.getSecond() == null));
        }
    }

    @Test void invalidForeignCompletionKeepsTheOriginalStageFailurePrefix() {
        int size = ChunkStatus.getStatusList().size();
        for (int index : invalidFutureIndices()) {
            Holder holder = new Holder(0, 0);
            MapStub map = new MapStub();
            ProtoChunk chunk = mock(ProtoChunk.class);
            map.next = CompletableFuture.completedFuture(chunk);
            apply(holder, map, ChunkStatus.EMPTY).join();
            IndexedStatus foreign = new IndexedStatus(index);
            var original = ChunkPyramid.LOADING_PYRAMID.getStepTo(ChunkStatus.STRUCTURE_STARTS);
            var step = new ChunkStep(foreign, original.directDependencies(), original.accumulatedDependencies(),
                original.blockStateWriteRadius(), original.task());
            var failure = assertThrows(java.util.concurrent.CompletionException.class,
                () -> holder.applyStep(step, map, null).join());
            assertEquals(ArrayIndexOutOfBoundsException.class, failure.getCause().getClass());
            assertEquals("Index " + index + " out of bounds for length " + size, failure.getCause().getMessage());
            assertEquals(1, foreign.indexQueries);
            assertEquals(2, map.trace.size(), "the stage ran before its completion failed");
            assertSame(chunk, holder.getChunkIfPresentUnchecked(ChunkStatus.EMPTY));
            assertTrue(holder.getAllFutures().stream().skip(1).allMatch(pair -> pair.getSecond() == null));
        }
    }

    @Test void startedWorkRetainsForeignStatusIdentityAcrossSuccessfulPublication() {
        class ForeignStatus extends ChunkStatus {
            ForeignStatus() {
                super(ChunkStatus.EMPTY, java.util.EnumSet.noneOf(net.minecraft.world.level.levelgen.Heightmap.Types.class),
                    net.minecraft.world.level.chunk.status.ChunkType.PROTOCHUNK);
            }
            @Override public String getName() { return "test:foreign_started"; }
        }
        Holder holder = new Holder(0, 0);
        MapStub map = new MapStub();
        ProtoChunk chunk = mock(ProtoChunk.class);
        map.next = CompletableFuture.completedFuture(chunk);
        apply(holder, map, ChunkStatus.EMPTY).join();
        var original = ChunkPyramid.LOADING_PYRAMID.getStepTo(ChunkStatus.STRUCTURE_STARTS);
        ChunkStatus foreign = new ForeignStatus();
        var step = new ChunkStep(foreign, original.directDependencies(), original.accumulatedDependencies(),
            original.blockStateWriteRadius(), original.task());
        assertSame(chunk, holder.applyStep(step, map, null).join().orElse(null));
        assertSame(foreign, holder.getLatestStatus());
        assertSame(chunk, holder.getChunkIfPresentUnchecked(foreign));
    }

    static void publishEveryStatus(Holder holder) {
        MapStub map = new MapStub();
        ProtoChunk chunk = mock(ProtoChunk.class);
        for (ChunkStatus status : ChunkStatus.getStatusList()) {
            map.next = CompletableFuture.completedFuture(chunk);
            apply(holder, map, status).join();
        }
    }

    @Test void completedFullRequestReturnsItsActualSlotWithoutScheduling() {
        Holder holder = new Holder(0, 0);
        publishEveryStatus(holder);
        var expected = holder.getAllFutures().getLast().getSecond();
        ChunkMap map = mock(ChunkMap.class);
        assertSame(expected, holder.scheduleChunkGenerationTask(ChunkStatus.FULL, map));
        assertSame(expected, holder.scheduleChunkGenerationTask(ChunkStatus.FULL, map));
        verifyNoInteractions(map);
    }

    @Test void foreignFullIndexKeepsItsVirtualQueriesAndActualFutureIdentity() {
        Holder holder = new Holder(0, 0);
        publishEveryStatus(holder);
        var expected = holder.getAllFutures().getLast().getSecond();
        var foreign = new IndexedStatus(ChunkStatus.getStatusList().size() - 1);
        ChunkMap map = mock(ChunkMap.class);
        assertSame(expected, holder.scheduleChunkGenerationTask(foreign, map));
        assertEquals(1, foreign.indexQueries);
        verifyNoInteractions(map);
    }

    @Test void demotionDisallowsCompletedFullWithoutRetiringItsActualFuture() {
        Holder holder = new Holder(0, 0);
        publishEveryStatus(holder);
        var expected = holder.getAllFutures().getLast().getSecond();
        holder.disallow();
        ChunkMap map = mock(ChunkMap.class);
        assertSame(GenerationChunkHolder.UNLOADED_CHUNK_FUTURE, holder.scheduleChunkGenerationTask(ChunkStatus.FULL, map));
        assertSame(expected, holder.getAllFutures().getLast().getSecond());
        verifyNoInteractions(map);
    }

    @Test void lateFullCompletionRemainsDisallowedUntilItsActualStatusIsReadmitted() {
        Holder holder = new Holder(0, 0);
        MapStub map = new MapStub();
        ProtoChunk chunk = mock(ProtoChunk.class);
        for (ChunkStatus status : ChunkStatus.getStatusList()) {
            if (status == ChunkStatus.FULL) break;
            map.next = CompletableFuture.completedFuture(chunk);
            apply(holder, map, status).join();
        }
        map.next = new CompletableFuture<>();
        var work = apply(holder, map, ChunkStatus.FULL);
        var old = apply(holder, map, ChunkStatus.FULL);
        holder.disallow();
        assertSame(GenerationChunkHolder.UNLOADED_CHUNK, old.join());
        assertNull(holder.getAllFutures().getLast().getSecond());
        map.next.complete(chunk);
        var replacement = holder.getAllFutures().getLast().getSecond();
        assertNotSame(old, replacement);
        assertSame(chunk, replacement.join().orElse(null));
        assertSame(chunk, work.join().orElse(null));
        ChunkMap scheduler = mock(ChunkMap.class);
        assertSame(GenerationChunkHolder.UNLOADED_CHUNK_FUTURE, holder.scheduleChunkGenerationTask(ChunkStatus.FULL, scheduler));
        holder.allow();
        assertSame(replacement, holder.scheduleChunkGenerationTask(ChunkStatus.FULL, scheduler));
        verifyNoInteractions(scheduler);
    }

    @Test void concurrentRequestsShareTheFutureButNotTheWorkResultWrapper() {
        Holder holder = new Holder(0, 0);
        MapStub map = new MapStub();
        var work = apply(holder, map, ChunkStatus.EMPTY);
        var first = apply(holder, map, ChunkStatus.EMPTY);
        var second = apply(holder, map, ChunkStatus.EMPTY);
        assertNotSame(work, first);
        assertSame(first, second);
        assertFalse(first.isDone());
        ProtoChunk chunk = mock(ProtoChunk.class);
        map.next.complete(chunk);
        assertSame(chunk, first.join().orElse(null));
        assertSame(chunk, work.join().orElse(null));
        assertEquals(1, map.trace.size());
        assertSame(first, holder.getAllFutures().getFirst().getSecond());
    }

    @Test void startedStatusCommitsBeforeSynchronousStageFailure() {
        Holder holder = new Holder(0, 0);
        RuntimeException failure = new RuntimeException("stage");
        MapStub map = new MapStub() {
            @Override public CompletableFuture<ChunkAccess> applyStep(GenerationChunkHolder h, ChunkStep s,
                    StaticCache2D<GenerationChunkHolder> cache) { throw failure; }
        };
        assertSame(failure, assertThrows(RuntimeException.class, () -> apply(holder, map, ChunkStatus.EMPTY)));
        var pending = apply(holder, map, ChunkStatus.EMPTY);
        assertFalse(pending.isDone(), "a synchronous throw does not roll back startedWork");
        assertSame(pending, holder.getAllFutures().getFirst().getSecond());
    }

    @Test void outOfOrderStartFailsWithoutCreatingFutureOrRunningStage() {
        Holder holder = new Holder(0, 0);
        MapStub map = new MapStub();
        IllegalStateException failure = assertThrows(IllegalStateException.class,
            () -> apply(holder, map, ChunkStatus.BIOMES));
        assertEquals("Unexpected last startedWork status: null while trying to start: minecraft:biomes", failure.getMessage());
        assertTrue(map.trace.isEmpty());
        assertTrue(holder.getAllFutures().stream().allMatch(pair -> pair.getSecond() == null));
    }

    @Test void disallowedWorkUsesTheGlobalUnloadedFutureWithoutEffects() {
        Holder holder = new Holder(0, 0);
        holder.disallow();
        MapStub map = new MapStub();
        assertSame(GenerationChunkHolder.UNLOADED_CHUNK_FUTURE, apply(holder, map, ChunkStatus.EMPTY));
        assertTrue(map.trace.isEmpty());
        assertTrue(holder.getAllFutures().stream().allMatch(pair -> pair.getSecond() == null));
    }

    @Test void demotionCompletesBeforeItClearsTheSlot() {
        Holder holder = new Holder(0, 0);
        MapStub map = new MapStub();
        apply(holder, map, ChunkStatus.EMPTY);
        var pending = apply(holder, map, ChunkStatus.EMPTY);
        List<Object> observed = new ArrayList<>();
        pending.thenRun(() -> {
            observed.add(holder.getAllFutures().getFirst().getSecond());
            observed.add(holder.getChunkIfPresent(ChunkStatus.EMPTY));
        });
        holder.disallow();
        assertSame(pending, observed.getFirst());
        assertNull(observed.get(1));
        assertSame(GenerationChunkHolder.UNLOADED_CHUNK, pending.join());
        assertNull(holder.getAllFutures().getFirst().getSecond());
    }

    @Test void lateStageSuccessAfterDemotionPublishesANewFuture() {
        Holder holder = new Holder(0, 0);
        MapStub map = new MapStub();
        var work = apply(holder, map, ChunkStatus.EMPTY);
        var old = apply(holder, map, ChunkStatus.EMPTY);
        holder.disallow();
        ProtoChunk chunk = mock(ProtoChunk.class);
        map.next.complete(chunk);
        var replacement = holder.getAllFutures().getFirst().getSecond();
        assertNotSame(old, replacement);
        assertSame(GenerationChunkHolder.UNLOADED_CHUNK, old.join());
        assertSame(chunk, replacement.join().orElse(null));
        assertSame(chunk, work.join().orElse(null));
        assertNull(holder.getChunkIfPresent(ChunkStatus.EMPTY));
        assertSame(chunk, holder.getChunkIfPresentUnchecked(ChunkStatus.EMPTY));
    }

    @Test void completionCallbacksSeePublishedChunkAndCanDemoteInline() {
        Holder holder = new Holder(0, 0);
        MapStub map = new MapStub();
        apply(holder, map, ChunkStatus.EMPTY);
        var pending = apply(holder, map, ChunkStatus.EMPTY);
        ProtoChunk chunk = mock(ProtoChunk.class);
        var callback = pending.thenRun(() -> {
            assertSame(chunk, holder.getChunkIfPresentUnchecked(ChunkStatus.EMPTY));
            holder.disallow();
        });
        map.next.complete(chunk);
        callback.join();
        assertSame(pending, holder.getAllFutures().getFirst().getSecond(), "demotion keeps an already successful future");
        assertSame(chunk, pending.join().orElse(null));
    }

    @Test void replacementUsesOneFutureAcrossEveryPreFullSlot() {
        Holder holder = new Holder(0, 0);
        MapStub map = new MapStub();
        ProtoChunk proto = mock(ProtoChunk.class);
        for (ChunkStatus status : ChunkStatus.getStatusList()) {
            map.next = CompletableFuture.completedFuture(proto);
            apply(holder, map, status).join();
        }
        var oldFull = holder.getAllFutures().getLast().getSecond();
        ImposterProtoChunk imposter = mock(ImposterProtoChunk.class);
        holder.replaceProtoChunk(imposter);
        var futures = holder.getAllFutures();
        var shared = futures.getFirst().getSecond();
        for (int i = 0; i < futures.size() - 1; i++) {
            assertSame(shared, futures.get(i).getSecond());
            assertSame(imposter, futures.get(i).getSecond().join().orElse(null));
        }
        assertSame(oldFull, futures.getLast().getSecond());
    }

    @Test void replacementFailureKeepsItsCompletedPrefix() {
        Holder holder = new Holder(0, 0);
        MapStub map = new MapStub();
        ProtoChunk proto = mock(ProtoChunk.class);
        map.next.complete(proto);
        apply(holder, map, ChunkStatus.EMPTY).join();
        ImposterProtoChunk imposter = mock(ImposterProtoChunk.class);
        assertThrows(NullPointerException.class, () -> holder.replaceProtoChunk(imposter));
        assertSame(imposter, holder.getChunkIfPresentUnchecked(ChunkStatus.EMPTY));
        assertNull(holder.getAllFutures().get(1).getSecond());
    }

    @Test void allOutstandingClaimsShareTheFenceUntilFinalRelease() {
        Holder holder = new Holder(0, 0);
        holder.increaseGenerationRefCount();
        holder.increaseGenerationRefCount();
        assertEquals(1, holder.saves.size());
        var first = holder.saves.getFirst();
        holder.decreaseGenerationRefCount();
        assertFalse(first.isDone());
        holder.decreaseGenerationRefCount();
        assertTrue(first.isDone());
        holder.increaseGenerationRefCount();
        assertEquals(2, holder.saves.size());
        assertNotSame(first, holder.saves.getLast());
        assertFalse(holder.saves.getLast().isDone());
        holder.decreaseGenerationRefCount();
    }

    @Test void releaseCompletesCapturedEpochBeforeReentrantAcquire() {
        Holder holder = new Holder(0, 0);
        holder.increaseGenerationRefCount();
        var old = holder.saves.getFirst();
        old.thenRun(holder::increaseGenerationRefCount);
        holder.decreaseGenerationRefCount();
        assertTrue(old.isDone());
        assertEquals(2, holder.saves.size());
        assertFalse(holder.saves.getLast().isDone());
        holder.decreaseGenerationRefCount();
        assertTrue(holder.saves.getLast().isDone());
    }

    @Test void saveDependencyThrowRetainsTheClaimAndEpoch() {
        Holder holder = new Holder(0, 0);
        RuntimeException failure = new RuntimeException("save admission");
        holder.onSave = () -> { throw failure; };
        assertSame(failure, assertThrows(RuntimeException.class, holder::increaseGenerationRefCount));
        assertFalse(holder.saves.getFirst().isDone());
        holder.onSave = () -> {};
        holder.increaseGenerationRefCount();
        assertEquals(1, holder.saves.size());
        holder.decreaseGenerationRefCount();
        assertFalse(holder.saves.getFirst().isDone());
        holder.decreaseGenerationRefCount();
        assertTrue(holder.saves.getFirst().isDone());
    }

    @Test void overReleaseCommitsNegativeCountBeforeThrow() {
        Holder holder = new Holder(0, 0);
        assertEquals("More releases than claims. Count: -1",
            assertThrows(IllegalStateException.class, holder::decreaseGenerationRefCount).getMessage());
        holder.increaseGenerationRefCount();
        assertTrue(holder.saves.isEmpty(), "-1→0 is not a first claim");
        holder.increaseGenerationRefCount();
        assertEquals(1, holder.saves.size());
        holder.decreaseGenerationRefCount();
    }

    static void replacementAndImposterFence(boolean releaseOldFirst) {
        Holder center = new Holder(0, 0);
        ProtoChunk proto = mock(ProtoChunk.class);
        when(proto.getPersistedStatus()).thenReturn(ChunkStatus.FULL);
        ImposterProtoChunk imposter = mock(ImposterProtoChunk.class);
        when(imposter.getPersistedStatus()).thenReturn(ChunkStatus.FULL);
        net.minecraft.world.level.chunk.LevelChunk full = mock(net.minecraft.world.level.chunk.LevelChunk.class);
        when(full.getPersistedStatus()).thenReturn(ChunkStatus.FULL);
        var load = new CompletableFuture<ChunkAccess>();
        Map<Long, Holder> holders = new LinkedHashMap<>();
        holders.put(0L, center);
        List<ChunkGenerationTask> tasks = new ArrayList<>();
        MapStub map = new MapStub() {
            @Override public GenerationChunkHolder acquireGeneration(long key) {
                Holder holder = holders.computeIfAbsent(key, value -> {
                    ChunkPos pos = new ChunkPos(value);
                    return new Holder(pos.x, pos.z);
                });
                holder.increaseGenerationRefCount();
                return holder;
            }
            @Override public void releaseGeneration(GenerationChunkHolder holder) { holder.decreaseGenerationRefCount(); }
            @Override public CompletableFuture<ChunkAccess> applyStep(GenerationChunkHolder holder, ChunkStep step,
                    StaticCache2D<GenerationChunkHolder> cache) {
                if (holder == center && step.targetStatus() == ChunkStatus.EMPTY) return load;
                if (holder == center && step.targetStatus() == ChunkStatus.FULL) {
                    assertEquals(1, center.saves.size());
                    assertFalse(center.saves.getFirst().isDone(), "imposter transition cannot release generation claims");
                    center.replaceProtoChunk(imposter);
                    return CompletableFuture.completedFuture(full);
                }
                return CompletableFuture.completedFuture(proto);
            }
        };
        ChunkMap scheduler = mock(ChunkMap.class);
        when(scheduler.scheduleGenerationTask(any(), any())).thenAnswer(call -> {
            var task = ChunkGenerationTask.create(map, call.getArgument(0), call.getArgument(1));
            tasks.add(task);
            return task;
        });
        var empty = center.scheduleChunkGenerationTask(ChunkStatus.EMPTY, scheduler);
        var old = tasks.getFirst();
        var oldWork = old.runUntilWait();
        assertNotSame(empty, oldWork, "work handle and public slot future have different identity");
        var epoch = center.saves.getFirst();
        var requestedFull = center.scheduleChunkGenerationTask(ChunkStatus.FULL, scheduler);
        assertEquals(2, tasks.size());
        var current = tasks.getLast();
        assertSame(empty, current.runUntilWait());
        assertSame(oldWork, old.runUntilWait(), "cancelled old task still waits for its original layer");
        epoch.thenRun(center::increaseGenerationRefCount);
        var observed = requestedFull.thenRun(() -> {
            assertFalse(epoch.isDone(), "FULL completion is before the outstanding claims' final release");
            var slots = center.getAllFutures();
            var shared = slots.getFirst().getSecond();
            for (int index = 0; index < slots.size() - 1; index++) {
                assertSame(shared, slots.get(index).getSecond());
                assertSame(imposter, shared.join().orElse(null));
            }
        });
        load.complete(proto);
        if (releaseOldFirst) {
            assertNull(old.runUntilWait());
            assertFalse(epoch.isDone());
            assertSame(requestedFull, center.scheduleChunkGenerationTask(ChunkStatus.FULL, scheduler));
            assertEquals(2, tasks.size(), "old removeTask CAS cannot remove its replacement");
        }
        assertNull(current.runUntilWait());
        observed.join();
        assertSame(full, requestedFull.join().orElse(null));
        if (!releaseOldFirst) {
            assertFalse(epoch.isDone(), "the late replaced task still owns a claim after FULL/imposter publication");
            assertNull(old.runUntilWait());
        }
        assertTrue(epoch.isDone());
        assertEquals(2, center.saves.size());
        assertNotSame(epoch, center.saves.getLast());
        assertFalse(center.saves.getLast().isDone(), "inline reacquire belongs to a new, uncompleted save epoch");
        center.decreaseGenerationRefCount();
        assertTrue(center.saves.getLast().isDone());
    }

    @Test void replacedTaskClaimSurvivesFullAndImposterUntilItsLateRelease() { replacementAndImposterFence(false); }
    @Test void oldTaskRemovalCannotClearReplacementAndItsSaveFenceEpoch() { replacementAndImposterFence(true); }
}
