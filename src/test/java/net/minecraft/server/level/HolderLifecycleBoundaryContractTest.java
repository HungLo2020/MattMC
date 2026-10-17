package net.minecraft.server.level;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.chunk.*;
import org.junit.jupiter.api.*;

/** Pre-revision public oracle: no private state setup or native field probes. */
class HolderLifecycleBoundaryContractTest {
    @BeforeAll static void bootstrap() { net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap(); }
    static class Holder extends ChunkHolder {
        Holder(int level, LevelChangeListener listener) {
            super(new ChunkPos(4, -7), level, LevelHeightAccessor.create(0, 16), null, listener, null);
        }
        void save(CompletableFuture<?> value) { addSaveDependency(value); }
        void drive(ChunkMap map, Executor executor) { updateFutures(map, executor); }
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test void saveAndSendReentryResolvesTheLaterLiveDependencyBeforeCombining() {
        for (boolean save : new boolean[]{false, true}) {
            Holder holder = new Holder(33, (p, q, l, set) -> set.accept(l));
            Consumer<CompletableFuture<?>> add = save ? holder::save : holder::addSendDependency;
            Supplier<CompletableFuture<?>> get = save ? holder::getSaveSyncFuture : holder::getSendSyncFuture;
            CompletableFuture<Void> outer = new CompletableFuture<>(), combined = new CompletableFuture<>();
            List<String> trace = new ArrayList<>();
            CompletableFuture<Void> inner = new CompletableFuture<>() {
                @Override public <U,V> CompletableFuture<V> thenCombine(CompletionStage<? extends U> other,
                        BiFunction<? super Void, ? super U, ? extends V> action) {
                    trace.add("combine-inner"); assertSame(outer, other); return (CompletableFuture)combined;
                }
            };
            CompletableFuture<Void> first = new CompletableFuture<>() {
                int calls;
                @Override public boolean isDone() {
                    trace.add("probe:" + ++calls);
                    if (calls == 1) { add.accept(inner); return false; }
                    return true;
                }
                @Override public <U,V> CompletableFuture<V> thenCombine(CompletionStage<? extends U> other,
                        BiFunction<? super Void, ? super U, ? extends V> action) {
                    throw new AssertionError("combined stale future captured before isDone reentry");
                }
            };
            add.accept(first); add.accept(outer);
            assertSame(combined, get.get());
            assertEquals(List.of("probe:1", "probe:2", "combine-inner"), trace);
        }
    }
    @Test void listenerFailureKeepsTheOriginalTransitionPrefixAndLiveQueueSupplier() {
        List<String> trace = new ArrayList<>();
        List<IntSupplier> suppliers = new ArrayList<>();
        RuntimeException failure = new RuntimeException("level listener");
        int[] calls = {0};
        Holder holder = new Holder(33, (pos, queue, level, set) -> {
            suppliers.add(queue); trace.add("listener:" + queue.getAsInt());
            if (calls[0]++ == 0) { set.accept(7); throw failure; }
            set.accept(level);
        });
        ChunkMap map = mock(ChunkMap.class);
        List<CompletableFuture<ChunkResult<LevelChunk>>> prepared = new ArrayList<>();
        when(map.prepareAccessibleChunk(holder)).thenAnswer(call -> {
            trace.add("prepare"); var future = new CompletableFuture<ChunkResult<LevelChunk>>(); prepared.add(future); return future;
        });
        doAnswer(call -> { trace.add("promote:" + call.getArgument(1)); return null; })
            .when(map).onFullChunkStatusChange(eq(holder.getPos()), any());
        assertSame(failure, assertThrows(RuntimeException.class, () -> holder.drive(map, Runnable::run)));
        assertSame(prepared.getFirst(), holder.getFullChunkFuture());
        holder.drive(map, Runnable::run);
        assertEquals(List.of("prepare", "listener:" + (ChunkLevel.MAX_LEVEL + 1), "prepare", "listener:7"), trace);
        assertEquals(33, suppliers.getFirst().getAsInt(), "the listener receives a live queue-level supplier");
        assertSame(prepared.getLast(), holder.getFullChunkFuture());
        LevelChunk chunk = mock(LevelChunk.class);
        prepared.getFirst().complete(ChunkResult.of(chunk));
        assertEquals(4, trace.size(), "cancelled old confirmation cannot promote after replacement");
        prepared.getLast().complete(ChunkResult.of(chunk));
        assertEquals("promote:FULL", trace.getLast());
        assertTrue(holder.isReadyForSaving());
    }

    static CompletableFuture<?> pending(ChunkHolder holder) throws Exception {
        try {
            var field = ChunkHolder.class.getDeclaredField("pendingFullStateConfirmation"); field.setAccessible(true);
            return (CompletableFuture<?>)field.get(holder);
        } catch (NoSuchFieldException absent) {
            var field = GenerationChunkHolder.class.getDeclaredField("generation"); field.setAccessible(true);
            Object owner = field.get(holder);
            var base = owner.getClass().getDeclaredField("READY_BASE"); base.setAccessible(true);
            var get = owner.getClass().getDeclaredMethod("binding", int.class); get.setAccessible(true);
            return (CompletableFuture<?>)get.invoke(owner, base.getInt(null) + 3);
        }
    }
    @Test void promotionCapturesItsFutureBeforeCancellationReentry() throws Exception {
        RuntimeException retry = new RuntimeException("retry outer transition");
        int[] listeners = {0}, promotions = {0};
        Holder holder = new Holder(33, (p,q,l,set) -> { if (listeners[0]++ == 0) throw retry; set.accept(l); });
        ChunkMap map = mock(ChunkMap.class);
        List<CompletableFuture<ChunkResult<LevelChunk>>> prepared = new ArrayList<>();
        when(map.prepareAccessibleChunk(holder)).thenAnswer(call -> {
            var next = new CompletableFuture<ChunkResult<LevelChunk>>(); prepared.add(next); return next;
        });
        doAnswer(call -> { promotions[0]++; return null; }).when(map).onFullChunkStatusChange(any(), any());
        assertSame(retry, assertThrows(RuntimeException.class, () -> holder.drive(map, Runnable::run)));
        pending(holder).whenComplete((value, error) -> {
            holder.setTicketLevel(ChunkLevel.MAX_LEVEL + 1); holder.drive(map, Runnable::run);
            holder.setTicketLevel(33); holder.drive(map, Runnable::run);
        });
        holder.drive(map, Runnable::run);
        assertEquals(3, prepared.size());
        assertSame(prepared.get(2), holder.getFullChunkFuture(), "cancellation reentry replaced the live full future");
        prepared.get(1).complete(ChunkResult.of(mock(LevelChunk.class)));
        assertEquals(1, promotions[0], "outer promotion retains the argument captured before cancellation");
        prepared.get(2).complete(ChunkResult.of(mock(LevelChunk.class)));
        assertEquals(2, promotions[0], "nested confirmation remains an actual independent future");
    }

    @Test void nullDependenciesRetainTheOriginalFailureBoundaryAndBlame() {
        for (boolean save : new boolean[]{false, true}) {
            Holder holder = new Holder(33, (p,q,l,set) -> set.accept(l));
            Consumer<CompletableFuture<?>> add = save ? holder::save : holder::addSendDependency;
            add.accept(null);
            String message = "Cannot invoke \"java.util.concurrent.CompletableFuture.isDone()\" because \"this." + (save ? "saveSync" : "sendSync") + "\" is null";
            assertEquals(message, assertThrows(NullPointerException.class, () -> add.accept(new CompletableFuture<>())).getMessage());
            if (save) assertEquals(message, assertThrows(NullPointerException.class, holder::isReadyForSaving).getMessage());
            else assertEquals(message, assertThrows(NullPointerException.class, holder::getChunkToSend).getMessage());
        }
    }
}
