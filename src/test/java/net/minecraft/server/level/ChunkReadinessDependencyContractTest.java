package net.minecraft.server.level;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.BiConsumer;
import java.util.function.Function;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Original range-future effects, including externally mutable/custom prerequisites. */
class ChunkReadinessDependencyContractTest {
    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    static class Leaf extends CompletableFuture<ChunkResult<ChunkAccess>> {
        final int index;
        final List<String> trace;
        Runnable onJoin = () -> {};
        Leaf(int index, List<String> trace) { this.index = index; this.trace = trace; }
        @Override public ChunkResult<ChunkAccess> join() {
            trace.add("join:" + index);
            onJoin.run();
            return super.join();
        }
        @Override public <U> CompletableFuture<U> thenApply(Function<? super ChunkResult<ChunkAccess>, ? extends U> action) {
            throw new AssertionError("range aggregation must not dispatch leaf thenApply");
        }
        @Override public CompletableFuture<ChunkResult<ChunkAccess>> whenComplete(
                BiConsumer<? super ChunkResult<ChunkAccess>, ? super Throwable> action) {
            throw new AssertionError("range aggregation must not dispatch leaf whenComplete");
        }
        @Override public boolean isDone() { throw new AssertionError("extra observable isDone query"); }
    }

    static final class Fixture {
        final ChunkMap map = mock(ChunkMap.class, CALLS_REAL_METHODS);
        final ChunkHolder center = mock(ChunkHolder.class);
        final List<String> trace = new ArrayList<>();
        final List<Leaf> leaves = new ArrayList<>();
        final List<ChunkAccess> chunks = new ArrayList<>();
        Fixture() {
            when(center.getPos()).thenReturn(new ChunkPos(10, -20));
            for (int row = 0; row < 9; row++) {
                int index = row;
                Leaf leaf = new Leaf(index, trace);
                leaves.add(leaf);
                chunks.add(mock(ChunkAccess.class));
                ChunkHolder holder = mock(ChunkHolder.class);
                doAnswer(call -> { trace.add("schedule:" + index); return leaf; })
                    .when(holder).scheduleChunkGenerationTask(ChunkStatus.FULL, map);
                doReturn(holder).when(map).getUpdatingChunkIfPresent(ChunkPos.asLong(9 + row % 3, -21 + row / 3));
            }
        }
        CompletableFuture<ChunkResult<List<ChunkAccess>>> start() {
            return map.getChunkRangeFuture(center, 1, distance -> {
                trace.add("distance:" + distance);
                return ChunkStatus.FULL;
            });
        }
        void complete(int index) { leaves.get(index).complete(ChunkResult.of(chunks.get(index))); }
        void completeAll() { for (int i = 8; i >= 0; i--) complete(i); }
        List<String> joins() { return trace.stream().filter(value -> value.startsWith("join:")).toList(); }
    }

    @Test void reverseCompletionWaitsForEveryPrerequisiteThenJoinsInOriginalOrder() {
        Fixture f = new Fixture();
        var result = f.start();
        assertEquals(List.of("distance:1", "schedule:0", "distance:1", "schedule:1", "distance:1", "schedule:2",
            "distance:1", "schedule:3", "distance:0", "schedule:4", "distance:1", "schedule:5",
            "distance:1", "schedule:6", "distance:1", "schedule:7", "distance:1", "schedule:8"), f.trace);
        for (int i = 8; i > 0; i--) { f.complete(i); assertFalse(result.isDone()); }
        assertTrue(f.joins().isEmpty());
        f.complete(0);
        assertEquals(f.chunks, result.join().orElse(null));
        assertEquals(List.of("join:0", "join:1", "join:2", "join:3", "join:4", "join:5", "join:6", "join:7", "join:8"), f.joins());
    }

    @Test void exceptionalPrerequisitesWaitForAllAndSelectTheFirstInRangeOrder() {
        Fixture f = new Fixture();
        var result = f.start();
        RuntimeException first = new RuntimeException("first"), later = new RuntimeException("later");
        f.leaves.get(8).completeExceptionally(later);
        f.leaves.get(1).completeExceptionally(first);
        for (int i = 2; i < 8; i++) f.complete(i);
        assertFalse(result.isDone());
        f.complete(0);
        assertSame(first, assertThrows(CompletionException.class, result::join).getCause());
        assertTrue(f.joins().isEmpty(), "exceptional aggregation does not enter the join effect pass");
    }

    @Test void joinReentryObservesALaterFuturesActualObtrudedValue() {
        Fixture f = new Fixture();
        ChunkAccess replacement = mock(ChunkAccess.class);
        f.leaves.getFirst().onJoin = () -> f.leaves.get(8).obtrudeValue(ChunkResult.of(replacement));
        var result = f.start();
        f.completeAll();
        List<ChunkAccess> actual = result.join().orElse(null);
        assertSame(replacement, actual.get(8));
        assertSame(f.chunks.getFirst(), actual.getFirst());
    }

    @Test void obtrudedRecoveryBeforeThePartnerCompletesChangesTheActualPairOutcome() {
        Fixture f = new Fixture();
        var result = f.start();
        f.leaves.getFirst().completeExceptionally(new RuntimeException("recovered before pair completion"));
        f.leaves.getFirst().obtrudeValue(ChunkResult.of(f.chunks.getFirst()));
        for (int index = 8; index > 0; index--) f.complete(index);
        assertEquals(f.chunks, result.join().orElse(null));
    }

    @Test void obtrudedRecoveryAfterPairCompletionDoesNotRewriteItsPublishedFailure() {
        Fixture f = new Fixture();
        var result = f.start();
        RuntimeException failure = new RuntimeException("already published pair failure");
        f.leaves.getFirst().completeExceptionally(failure);
        f.complete(1);
        f.leaves.getFirst().obtrudeValue(ChunkResult.of(f.chunks.getFirst()));
        for (int index = 8; index > 1; index--) f.complete(index);
        assertSame(failure, assertThrows(CompletionException.class, result::join).getCause());
    }

    @Test void allJoinsPrecedeResultQueriesAndAnUnloadedResultStopsLaterQueries() {
        Fixture f = new Fixture();
        var result = f.start();
        for (int i = 0; i < 9; i++) {
            int index = i;
            ChunkResult<ChunkAccess> value = mock(ChunkResult.class);
            when(value.orElse(null)).thenAnswer(call -> {
                assertEquals(9, f.joins().size());
                f.trace.add("result:" + index);
                return index == 2 ? null : f.chunks.get(index);
            });
            f.leaves.get(i).complete(value);
        }
        assertFalse(result.join().isSuccess());
        assertEquals(List.of("result:0", "result:1", "result:2"),
            f.trace.stream().filter(value -> value.startsWith("result:")).toList());
    }

    @Test void aLaterJoinFailurePrecedesAnEarlierNullResultDiagnostic() {
        Fixture f = new Fixture();
        RuntimeException failure = new RuntimeException("later join");
        f.leaves.get(8).onJoin = () -> { throw failure; };
        var result = f.start();
        f.leaves.getFirst().complete(null);
        for (int i = 1; i < 9; i++) f.complete(i);
        assertSame(failure, assertThrows(CompletionException.class, result::join).getCause());
        verify(f.map, never()).debugFuturesAndCreateReportedException(any(), any());
    }

    @Test void missingHolderStopsSchedulingAtTheOriginalRangePrefix() {
        Fixture f = new Fixture();
        doReturn(null).when(f.map).getUpdatingChunkIfPresent(ChunkPos.asLong(11, -21));
        var result = f.start();
        assertFalse(result.join().isSuccess());
        assertEquals(List.of("distance:1", "schedule:0", "distance:1", "schedule:1"), f.trace);
    }

    @Test void externalCompletionSkipsResultQueriesButPreservesTheEarlierJoinPass() {
        Fixture f = new Fixture();
        var result = f.start();
        ChunkResult<List<ChunkAccess>> external = ChunkResult.error("external completion");
        assertTrue(result.complete(external));
        for (Leaf leaf : f.leaves) {
            ChunkResult<ChunkAccess> value = mock(ChunkResult.class);
            when(value.orElse(null)).thenThrow(new AssertionError("completed public adapter must skip result queries"));
            leaf.complete(value);
        }
        assertSame(external, result.join());
        assertEquals(List.of("join:0", "join:1", "join:2", "join:3", "join:4", "join:5", "join:6", "join:7", "join:8"), f.joins());
    }

    @Test void zeroRadiusPreservesTheActualVirtualThenApplyAdapter() {
        ChunkMap map = mock(ChunkMap.class, CALLS_REAL_METHODS);
        ChunkHolder center = mock(ChunkHolder.class);
        CompletableFuture<ChunkResult<List<ChunkAccess>>> adapted = new CompletableFuture<>();
        CompletableFuture<ChunkResult<ChunkAccess>> leaf = new CompletableFuture<>() {
            @Override public <U> CompletableFuture<U> thenApply(Function<? super ChunkResult<ChunkAccess>, ? extends U> action) {
                return (CompletableFuture<U>)adapted;
            }
        };
        doReturn(leaf).when(center).scheduleChunkGenerationTask(ChunkStatus.FULL, map);
        assertSame(adapted, map.getChunkRangeFuture(center, 0, distance -> { assertEquals(0, distance); return ChunkStatus.FULL; }));
        verify(center, never()).getPos();
    }
}
