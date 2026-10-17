package net.minecraft.server.level;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.BiConsumer;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkPyramid;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkStep;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Scheduling contracts; stage effects are controlled leaves, not a FULL-server oracle. */
class ChunkGenerationTaskContractTest {
    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    static final ChunkResult<ChunkAccess> SUCCESS = ChunkResult.of(null);

    static final class Holder extends GenerationChunkHolder {
        final Fixture fixture;
        final List<CompletableFuture<?>> saves = new ArrayList<>();
        ChunkStatus persisted;
        Holder(Fixture fixture, ChunkPos pos) { super(pos); this.fixture = fixture; persisted = fixture.initial; }
        public int getTicketLevel() { return 33; }
        public int getQueueLevel() { return 33; }
        protected void addSaveDependency(CompletableFuture<?> future) {
            saves.add(future);
            fixture.onSave.accept(this, future);
        }
        @Override public ChunkStatus getPersistedStatus() { return persisted; }
        @Override CompletableFuture<ChunkResult<ChunkAccess>> applyStep(ChunkStep step, GeneratingChunkMap map,
                StaticCache2D<GenerationChunkHolder> cache) {
            fixture.steps.add(step.targetStatus().getName() + ":" + pos.x + "," + pos.z);
            assertSame(this, cache.get(pos.x, pos.z));
            if (step.targetStatus().isAfter(persisted)) persisted = step.targetStatus();
            return fixture.effect.apply(this, step);
        }
    }

    static final class Fixture implements GeneratingChunkMap {
        final ChunkStatus initial;
        final Map<Long, Holder> holders = new LinkedHashMap<>();
        final List<ChunkPos> acquired = new ArrayList<>(), released = new ArrayList<>();
        final List<String> steps = new ArrayList<>();
        BiFunction<Holder, ChunkStep, CompletableFuture<ChunkResult<ChunkAccess>>> effect =
            (holder, step) -> CompletableFuture.completedFuture(SUCCESS);
        BiConsumer<Holder, CompletableFuture<?>> onSave = (holder, future) -> {};
        RuntimeException releaseFailure;
        int throwOnRelease = -1;
        Fixture(ChunkStatus initial) { this.initial = initial; }
        public GenerationChunkHolder acquireGeneration(long key) {
            ChunkPos pos = new ChunkPos(key);
            acquired.add(pos);
            Holder holder = holders.computeIfAbsent(key, ignored -> new Holder(this, pos));
            holder.increaseGenerationRefCount();
            return holder;
        }
        public void releaseGeneration(GenerationChunkHolder holder) {
            released.add(holder.getPos());
            if (released.size() == throwOnRelease) throw releaseFailure;
            holder.decreaseGenerationRefCount();
        }
        public CompletableFuture<ChunkAccess> applyStep(GenerationChunkHolder holder, ChunkStep step,
                StaticCache2D<GenerationChunkHolder> cache) { throw new AssertionError("holder override expected"); }
        public ChunkGenerationTask scheduleGenerationTask(ChunkStatus status, ChunkPos pos) { throw new AssertionError("unexpected reschedule"); }
        public void runGenerationTasks() { throw new AssertionError("unexpected drive"); }
        ChunkGenerationTask create(ChunkStatus target) { return ChunkGenerationTask.create(this, target, new ChunkPos(4, -7)); }
    }

    static List<String> layer(ChunkStatus target, ChunkPyramid pyramid, ChunkStatus status) {
        List<String> expected = new ArrayList<>();
        int radius = pyramid.getStepTo(target).getAccumulatedRadiusOf(status);
        for (int x = 4 - radius; x <= 4 + radius; x++)
            for (int z = -7 - radius; z <= -7 + radius; z++)
                expected.add(status.getName() + ":" + x + "," + z);
        return expected;
    }

    @Test void cacheAcquisitionKeepsTheExactClaimPrefixWhenSaveAdmissionThrows() {
        Fixture fixture = new Fixture(ChunkStatus.FULL);
        RuntimeException failure = new RuntimeException("second cache claim");
        fixture.onSave = (holder, future) -> {
            if (fixture.acquired.size() == 2) throw failure;
        };
        assertSame(failure, assertThrows(RuntimeException.class, () -> fixture.create(ChunkStatus.FULL)));
        assertEquals(2, fixture.acquired.size(), "later cells cannot be claimed before the throwing callback");
        assertTrue(fixture.released.isEmpty());
        for (Holder holder : fixture.holders.values()) {
            assertEquals(1, holder.saves.size());
            assertFalse(holder.saves.getFirst().isDone());
            fixture.releaseGeneration(holder);
            assertTrue(holder.saves.getFirst().isDone());
        }
    }

    @Test void firstClaimCallbackCanPreclaimTheNextLiveCacheCell() {
        Fixture fixture = new Fixture(ChunkStatus.FULL);
        Holder[] preclaimed = {null};
        fixture.onSave = (holder, future) -> {
            if (fixture.acquired.size() == 1 && preclaimed[0] == null) {
                ChunkPos next = new ChunkPos(holder.pos.x, holder.pos.z + 1);
                preclaimed[0] = fixture.holders.computeIfAbsent(next.toLong(), ignored -> new Holder(fixture, next));
                preclaimed[0].increaseGenerationRefCount();
            }
        };
        var task = fixture.create(ChunkStatus.FULL);
        assertSame(preclaimed[0], fixture.holders.get(fixture.acquired.get(1).toLong()));
        assertEquals(1, preclaimed[0].saves.size(), "the later cache claim shares the callback's live epoch");
        task.markForCancellation();
        assertNull(task.runUntilWait());
        assertFalse(preclaimed[0].saves.getFirst().isDone(), "the callback still owns one claim");
        preclaimed[0].decreaseGenerationRefCount();
        assertTrue(preclaimed[0].saves.getFirst().isDone());
    }

    @Test void releaseCallbackCanReplaceTheNextCellsSaveEpochBeforeItIsCaptured() {
        Fixture fixture = new Fixture(ChunkStatus.FULL);
        var task = fixture.create(ChunkStatus.FULL);
        Holder first = fixture.holders.get(fixture.acquired.getFirst().toLong());
        Holder next = fixture.holders.get(fixture.acquired.get(1).toLong());
        var old = next.saves.getFirst();
        var callback = first.saves.getFirst().thenRun(() -> {
            assertEquals(List.of(first.pos), fixture.released);
            next.decreaseGenerationRefCount();
            assertTrue(old.isDone());
            next.increaseGenerationRefCount();
            assertEquals(2, next.saves.size());
            assertFalse(next.saves.getLast().isDone());
        });
        task.markForCancellation();
        assertNull(task.runUntilWait());
        callback.join();
        assertNotSame(old, next.saves.getLast());
        assertTrue(next.saves.getLast().isDone(), "release must complete the epoch captured AFTER the earlier callback");
        assertEquals(fixture.acquired, fixture.released);
    }

    @Test void acquisitionAndReleaseUseTheSameXThenZOrder() {
        Fixture fixture = new Fixture(ChunkStatus.FULL);
        var task = fixture.create(ChunkStatus.FULL);
        int radius = ChunkPyramid.GENERATION_PYRAMID.getStepTo(ChunkStatus.FULL).getAccumulatedRadiusOf(ChunkStatus.EMPTY);
        List<ChunkPos> expected = new ArrayList<>();
        for (int x = 4 - radius; x <= 4 + radius; x++)
            for (int z = -7 - radius; z <= -7 + radius; z++) expected.add(new ChunkPos(x, z));
        assertEquals(expected, fixture.acquired);
        assertSame(fixture.holders.get(ChunkPos.asLong(4, -7)), task.getCenter());
        assertNull(task.runUntilWait());
        assertEquals(expected, fixture.released);
    }

    @Test void loadedChunksUseEveryLoadingLayerWithoutRegenerating() {
        Fixture fixture = new Fixture(ChunkStatus.FULL);
        assertNull(fixture.create(ChunkStatus.FULL).runUntilWait());
        List<String> expected = new ArrayList<>();
        for (ChunkStatus status : ChunkStatus.getStatusList()) expected.addAll(layer(ChunkStatus.FULL, ChunkPyramid.LOADING_PYRAMID, status));
        assertEquals(expected, fixture.steps);
    }

    @Test void freshRequestRepeatsEmptyThenUsesGenerationRadii() {
        Fixture fixture = new Fixture(ChunkStatus.EMPTY);
        assertNull(fixture.create(ChunkStatus.FULL).runUntilWait());
        List<String> expected = layer(ChunkStatus.FULL, ChunkPyramid.LOADING_PYRAMID, ChunkStatus.EMPTY);
        for (ChunkStatus status : ChunkStatus.getStatusList()) expected.addAll(layer(ChunkStatus.FULL, ChunkPyramid.GENERATION_PYRAMID, status));
        assertEquals(expected, fixture.steps);
        assertEquals(fixture.acquired, fixture.released);
    }

    @Test void waitsProbeLastPendingFutureFirstEvenAfterCancellation() {
        Fixture fixture = new Fixture(ChunkStatus.FULL);
        var first = new CompletableFuture<ChunkResult<ChunkAccess>>();
        var last = new CompletableFuture<ChunkResult<ChunkAccess>>();
        fixture.effect = (holder, step) -> {
            if (step.targetStatus() == ChunkStatus.EMPTY && holder.pos.x == 3 && holder.pos.z == -8) return first;
            if (step.targetStatus() == ChunkStatus.EMPTY && holder.pos.x == 5 && holder.pos.z == -6) return last;
            return CompletableFuture.completedFuture(SUCCESS);
        };
        var task = fixture.create(ChunkStatus.FULL);
        assertSame(last, task.runUntilWait());
        task.markForCancellation();
        assertSame(last, task.runUntilWait());
        last.complete(GenerationChunkHolder.UNLOADED_CHUNK);
        assertSame(first, task.runUntilWait());
        assertTrue(fixture.released.isEmpty());
        first.complete(SUCCESS);
        assertNull(task.runUntilWait());
        assertEquals(fixture.acquired, fixture.released);
        assertEquals(layer(ChunkStatus.FULL, ChunkPyramid.LOADING_PYRAMID, ChunkStatus.EMPTY), fixture.steps);
    }

    @Test void getNowThrowRetainsThePendingLayerEntryForRetry() {
        Fixture fixture = new Fixture(ChunkStatus.FULL);
        RuntimeException failure = new RuntimeException("getNow");
        boolean[] fail = {false};
        CompletableFuture<ChunkResult<ChunkAccess>> pending = new CompletableFuture<>() {
            @Override public ChunkResult<ChunkAccess> getNow(ChunkResult<ChunkAccess> fallback) {
                if (fail[0]) throw failure;
                return super.getNow(fallback);
            }
        };
        fixture.effect = (holder, step) -> pending;
        var task = fixture.create(ChunkStatus.EMPTY);
        assertSame(pending, task.runUntilWait());
        fail[0] = true;
        assertSame(failure, assertThrows(RuntimeException.class, task::runUntilWait));
        assertTrue(fixture.released.isEmpty());
        fail[0] = false;
        pending.complete(SUCCESS);
        assertNull(task.runUntilWait());
        assertEquals(fixture.acquired, fixture.released);
    }

    @Test void cancellationInsideStageStopsOnlyBeforeTheNextCellAndStillWaits() {
        Fixture fixture = new Fixture(ChunkStatus.FULL);
        var task = fixture.create(ChunkStatus.FULL);
        var pending = new CompletableFuture<ChunkResult<ChunkAccess>>();
        fixture.effect = (holder, step) -> { task.markForCancellation(); return pending; };
        assertSame(pending, task.runUntilWait());
        assertEquals(1, fixture.steps.size());
        assertTrue(fixture.released.isEmpty());
        pending.complete(SUCCESS);
        assertNull(task.runUntilWait());
        assertEquals(fixture.acquired, fixture.released);
    }

    @Test void immediateGetNowCancellationStillProbesItsExactResultBeforeStopping() {
        Fixture fixture = new Fixture(ChunkStatus.FULL);
        var task = fixture.create(ChunkStatus.FULL);
        List<String> calls = new ArrayList<>();
        @SuppressWarnings("unchecked") ChunkResult<ChunkAccess> result = mock(ChunkResult.class);
        when(result.isSuccess()).thenAnswer(ignored -> {
            calls.add("success");
            assertTrue(fixture.released.isEmpty());
            return true;
        });
        fixture.effect = (holder, step) -> {
            calls.add("apply");
            return new CompletableFuture<>() {
                @Override public ChunkResult<ChunkAccess> getNow(ChunkResult<ChunkAccess> fallback) {
                    calls.add("getNow");
                    task.markForCancellation();
                    return result;
                }
            };
        };
        assertNull(task.runUntilWait());
        assertEquals(List.of("apply", "getNow", "success"), calls);
        assertEquals(1, fixture.steps.size());
        assertEquals(fixture.acquired, fixture.released);
        verify(result, times(1)).isSuccess();
    }

    @Test void immediateSuccessThrowDoesNotCommitTheLayerOrRetainItsFuture() {
        Fixture fixture = new Fixture(ChunkStatus.FULL);
        RuntimeException failure = new RuntimeException("immediate success");
        @SuppressWarnings("unchecked") ChunkResult<ChunkAccess> result = mock(ChunkResult.class);
        when(result.isSuccess()).thenThrow(failure);
        fixture.effect = (holder, step) -> CompletableFuture.completedFuture(result);
        var task = fixture.create(ChunkStatus.EMPTY);
        assertSame(failure, assertThrows(RuntimeException.class, task::runUntilWait));
        assertTrue(fixture.released.isEmpty());
        fixture.effect = (holder, step) -> CompletableFuture.completedFuture(SUCCESS);
        assertNull(task.runUntilWait());
        assertEquals(2, fixture.steps.size(), "retry must schedule the uncommitted layer again");
        assertEquals(fixture.acquired, fixture.released);
        verify(result, times(1)).isSuccess();
    }

    @Test void immediateUnsuccessfulResultCancelsBeforeTheNextCell() {
        Fixture fixture = new Fixture(ChunkStatus.FULL);
        fixture.effect = (holder, step) -> CompletableFuture.completedFuture(GenerationChunkHolder.UNLOADED_CHUNK);
        assertNull(fixture.create(ChunkStatus.FULL).runUntilWait());
        assertEquals(1, fixture.steps.size());
        assertEquals(fixture.acquired, fixture.released);
    }

    @Test void immediateCallbacksCanReenterTheSameTaskAndAppendPendingWork() {
        for (String boundary : List.of("apply", "getNow", "isSuccess")) {
            Fixture fixture = new Fixture(ChunkStatus.FULL);
            var task = fixture.create(ChunkStatus.EMPTY);
            var pending = new CompletableFuture<ChunkResult<ChunkAccess>>();
            boolean[] first = {true};
            @SuppressWarnings("unchecked") ChunkResult<ChunkAccess> result = mock(ChunkResult.class);
            when(result.isSuccess()).thenAnswer(ignored -> {
                if (boundary.equals("isSuccess")) assertSame(pending, task.runUntilWait());
                assertEquals(2, fixture.steps.size());
                assertTrue(fixture.released.isEmpty());
                return true;
            });
            fixture.effect = (holder, step) -> {
                if (!first[0]) return pending;
                first[0] = false;
                if (boundary.equals("apply")) assertSame(pending, task.runUntilWait());
                return new CompletableFuture<>() {
                    @Override public ChunkResult<ChunkAccess> getNow(ChunkResult<ChunkAccess> fallback) {
                        if (boundary.equals("getNow")) assertSame(pending, task.runUntilWait());
                        return result;
                    }
                };
            };
            assertSame(pending, task.runUntilWait());
            assertEquals(2, fixture.steps.size());
            assertTrue(fixture.released.isEmpty());
            verify(result, times(1)).isSuccess();
            pending.complete(SUCCESS);
            assertNull(task.runUntilWait());
            assertEquals(fixture.acquired, fixture.released);
        }
    }

    @Test void releaseThrowRetainsTheOriginalPrefixAndRetryStartsAtTheBeginning() {
        Fixture fixture = new Fixture(ChunkStatus.FULL);
        var task = fixture.create(ChunkStatus.FULL);
        task.markForCancellation();
        fixture.releaseFailure = new RuntimeException("release");
        fixture.throwOnRelease = 2;
        assertSame(fixture.releaseFailure, assertThrows(RuntimeException.class, task::runUntilWait));
        assertEquals(fixture.acquired.subList(0, 2), fixture.released);
        fixture.throwOnRelease = -1;
        var second = assertThrows(IllegalStateException.class, task::runUntilWait);
        assertEquals("More releases than claims. Count: -1", second.getMessage());
        assertEquals(fixture.acquired.getFirst(), fixture.released.getLast());
    }

    @SuppressWarnings("unchecked")
    static <E extends Throwable, T> T raise(Throwable failure) throws E { throw (E)failure; }

    @Test void checkedStageThrowableRetainsItsIdentityAndNoClaimIsReleased() {
        Fixture fixture = new Fixture(ChunkStatus.FULL);
        Exception failure = new Exception("checked stage effect");
        fixture.effect = (holder, step) -> raise(failure);
        var task = fixture.create(ChunkStatus.EMPTY);
        assertSame(failure, assertThrows(Throwable.class, task::runUntilWait));
        assertTrue(fixture.released.isEmpty());
        fixture.effect = (holder, step) -> CompletableFuture.completedFuture(SUCCESS);
        assertNull(task.runUntilWait());
        assertEquals(2, fixture.steps.size(), "failed layer did not commit scheduled status");
        assertEquals(fixture.acquired, fixture.released);
    }

    @Test void resultFailureOccursAfterThePendingEntryWasRemoved() {
        Fixture fixture = new Fixture(ChunkStatus.FULL);
        var pending = new CompletableFuture<ChunkResult<ChunkAccess>>();
        fixture.effect = (holder, step) -> pending;
        var task = fixture.create(ChunkStatus.EMPTY);
        assertSame(pending, task.runUntilWait());
        @SuppressWarnings("unchecked") ChunkResult<ChunkAccess> bad = mock(ChunkResult.class);
        RuntimeException failure = new RuntimeException("result.isSuccess");
        when(bad.isSuccess()).thenThrow(failure);
        pending.complete(bad);
        assertSame(failure, assertThrows(RuntimeException.class, task::runUntilWait));
        assertTrue(fixture.released.isEmpty());
        assertNull(task.runUntilWait(), "retry cannot probe the removed failing result again");
        assertEquals(fixture.acquired, fixture.released);
    }

    @Test void reentrantGetNowMayRemoveTheLiveLastEntryBeforeTheOuterPop() {
        Fixture fixture = new Fixture(ChunkStatus.FULL);
        ChunkGenerationTask[] task = new ChunkGenerationTask[1];
        boolean[] reenter = {false};
        CompletableFuture<ChunkResult<ChunkAccess>> pending = new CompletableFuture<>() {
            @Override public ChunkResult<ChunkAccess> getNow(ChunkResult<ChunkAccess> fallback) {
                if (reenter[0]) {
                    reenter[0] = false;
                    assertNull(task[0].runUntilWait());
                }
                return super.getNow(fallback);
            }
        };
        fixture.effect = (holder, step) -> pending;
        task[0] = fixture.create(ChunkStatus.EMPTY);
        assertSame(pending, task[0].runUntilWait());
        pending.complete(SUCCESS);
        reenter[0] = true;
        assertThrows(java.util.NoSuchElementException.class, task[0]::runUntilWait);
        assertEquals(fixture.acquired, fixture.released);
    }

    @Test void cancellationBeforeFirstDriveReleasesWithoutProbingOrScheduling() {
        Fixture fixture = new Fixture(ChunkStatus.FULL);
        var task = fixture.create(ChunkStatus.FULL);
        task.markForCancellation();
        assertNull(task.runUntilWait());
        assertTrue(fixture.steps.isEmpty());
        assertEquals(fixture.acquired, fixture.released);
    }

    @Test void pendingWaitReturnsTheCapturedFutureAfterGetNowReentersAndReusesItsRow() {
        Fixture fixture = new Fixture(ChunkStatus.FULL);
        ChunkGenerationTask[] task = new ChunkGenerationTask[1];
        var later = new CompletableFuture<ChunkResult<ChunkAccess>>();
        boolean[] trigger = {false}, innerCanFinish = {false};
        CompletableFuture<ChunkResult<ChunkAccess>> original = new CompletableFuture<>() {
            @Override public ChunkResult<ChunkAccess> getNow(ChunkResult<ChunkAccess> fallback) {
                if (trigger[0]) {
                    trigger[0] = false;
                    innerCanFinish[0] = true;
                    assertSame(later, task[0].runUntilWait());
                    return null;
                }
                return innerCanFinish[0] ? SUCCESS : null;
            }
        };
        fixture.effect = (holder, step) -> step.targetStatus() == ChunkStatus.EMPTY ? original : later;
        task[0] = fixture.create(ChunkStatus.FULL);
        assertSame(original, task[0].runUntilWait());
        trigger[0] = true;
        assertSame(original, task[0].runUntilWait(), "wait must return the captured future, not the current occupant of a reused native row");
        task[0].markForCancellation();
        later.complete(SUCCESS);
        assertNull(task[0].runUntilWait());
        assertEquals(fixture.acquired, fixture.released);
    }

    @Test void nullProfilerZonePreservesTheOriginalFailureMessageAndClaimPrefix() {
        Fixture fixture = new Fixture(ChunkStatus.FULL);
        var task = fixture.create(ChunkStatus.EMPTY);
        var profiler = mock(net.minecraft.util.profiling.ProfilerFiller.class);
        when(profiler.zone("scheduleLayer")).thenReturn(null);
        try (var scope = net.minecraft.util.profiling.Profiler.use(profiler)) {
            var failure = assertThrows(NullPointerException.class, task::runUntilWait);
            assertTrue(failure.getMessage().endsWith("\"zone\" is null"), failure.getMessage());
        }
        assertTrue(fixture.steps.isEmpty());
        assertTrue(fixture.released.isEmpty());
        task.markForCancellation();
        assertNull(task.runUntilWait());
        assertEquals(fixture.acquired, fixture.released);
    }

    @Test void nullStageFuturePreservesFailureBlameAndDoesNotCommitTheLayer() {
        Fixture fixture = new Fixture(ChunkStatus.FULL);
        fixture.effect = (holder, step) -> null;
        var task = fixture.create(ChunkStatus.EMPTY);
        var failure = assertThrows(NullPointerException.class, task::runUntilWait);
        assertTrue(failure.getMessage().endsWith("\"completableFuture\" is null"), failure.getMessage());
        assertEquals(1, fixture.steps.size());
        assertTrue(fixture.released.isEmpty());
        fixture.effect = (holder, step) -> CompletableFuture.completedFuture(SUCCESS);
        assertNull(task.runUntilWait());
        assertEquals(2, fixture.steps.size(), "failed layer must run again rather than commit its status");
        assertEquals(fixture.acquired, fixture.released);
    }

    @Test void foreignTargetWithEmptyIndexDoesNotFinishAsCanonicalEmpty() {
        class ForeignTarget extends ChunkStatus {
            ForeignTarget() {
                super(null, java.util.EnumSet.noneOf(net.minecraft.world.level.levelgen.Heightmap.Types.class),
                    net.minecraft.world.level.chunk.status.ChunkType.PROTOCHUNK);
            }
            @Override public String getName() { return "test:foreign_empty"; }
        }
        Fixture fixture = new Fixture(ChunkStatus.FULL);
        var task = fixture.create(new ForeignTarget());
        assertThrows(RuntimeException.class, task::runUntilWait);
        assertEquals(1, fixture.steps.size(), "only canonical EMPTY completed before the original dependency failure");
        assertTrue(fixture.released.isEmpty(), "a foreign target must not successfully finish by matching its numeric index");
        task.markForCancellation();
        assertNull(task.runUntilWait());
        assertEquals(fixture.acquired, fixture.released);
    }

    @Test void foreignTargetCreationDoesNotAddIndexQueriesAfterCacheAcquisition() {
        int[] queries = {0};
        class ForeignTarget extends ChunkStatus {
            ForeignTarget() {
                super(ChunkStatus.EMPTY, java.util.EnumSet.noneOf(net.minecraft.world.level.levelgen.Heightmap.Types.class),
                    net.minecraft.world.level.chunk.status.ChunkType.PROTOCHUNK);
            }
            @Override public int getIndex() { queries[0]++; return super.getIndex(); }
            @Override public String getName() { return "test:foreign_target"; }
        }
        Fixture fixture = new Fixture(ChunkStatus.FULL);
        var task = fixture.create(new ForeignTarget());
        assertEquals(1, queries[0], "only the original creation-time pyramid lookup may query the target");
        task.markForCancellation();
        assertNull(task.runUntilWait());
        assertEquals(fixture.acquired, fixture.released);
    }

    @Test void canLoadUsesThePersistedStatusVirtualComparisonBeforeNumericGenerationChecks() {
        List<String> calls = new ArrayList<>();
        class ForeignPersisted extends ChunkStatus {
            ForeignPersisted() {
                super(ChunkStatus.LIGHT, java.util.EnumSet.noneOf(net.minecraft.world.level.levelgen.Heightmap.Types.class),
                    net.minecraft.world.level.chunk.status.ChunkType.PROTOCHUNK);
            }
            @Override public int getIndex() { calls.add("index"); return ChunkStatus.FULL.getIndex(); }
            @Override public boolean isBefore(ChunkStatus other) { calls.add("before"); return false; }
            @Override public String getName() { return "test:foreign_persisted"; }
        }
        Fixture fixture = new Fixture(ChunkStatus.FULL);
        ChunkStatus foreign = new ForeignPersisted();
        fixture.effect = (holder, step) -> {
            if (step.targetStatus() == ChunkStatus.EMPTY && holder.pos.equals(new ChunkPos(4, -7))) holder.persisted = foreign;
            return CompletableFuture.completedFuture(SUCCESS);
        };
        assertNull(fixture.create(ChunkStatus.FULL).runUntilWait());
        assertEquals("before", calls.getFirst(), "the original can-load virtual call precedes generation index comparisons");
        assertTrue(calls.stream().filter("before"::equals).count() >= 2);
        assertEquals(fixture.acquired, fixture.released);
    }
}
