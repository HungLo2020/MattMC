package net.minecraft.server.level;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import net.minecraft.util.thread.BlockableEventLoop;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

/** Callback scope must not anchor unrelated holders, maps or old range results. */
@Isolated
class ChunkPreparationLifetimeContractTest {
    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }
    static void releaseHarnessRoots() {
        framework().clearInlineMocks();
        org.mockito.internal.progress.ThreadSafeMockingProgress.mockingProgress().resetOngoingStubbing();
    }
    record Projection(CompletableFuture<ChunkResult<List<ChunkAccess>>> source,
        CompletableFuture<ChunkResult<LevelChunk>> result, WeakReference<?> map, WeakReference<?> holder) {}
    static Projection projection(boolean entity) {
        ChunkMap map = mock(ChunkMap.class, CALLS_REAL_METHODS);
        ChunkHolder holder = mock(ChunkHolder.class);
        CompletableFuture<ChunkResult<List<ChunkAccess>>> source = new CompletableFuture<>();
        doReturn(source).when(map).getChunkRangeFuture(same(holder), anyInt(), any());
        var result = entity ? map.prepareEntityTickingChunk(holder) : map.prepareAccessibleChunk(holder);
        releaseHarnessRoots();
        return new Projection(source, result, new WeakReference<>(map), new WeakReference<>(holder));
    }
    static void collect(List<WeakReference<?>> targets, String message) throws Exception {
        for (int attempt = 0; attempt < 160; attempt++) {
            System.gc(); Thread.sleep(20);
            if (targets.stream().allMatch(reference -> reference.get() == null)) return;
        }
        assertTrue(targets.stream().allMatch(reference -> reference.get() == null), message);
    }
    @Test void accessibleAndEntityProjectionDoNotRetainTheMapOrHolder() throws Exception {
        for (boolean entity : new boolean[]{false, true}) {
            Projection lease = projection(entity);
            collect(List.of(lease.map, lease.holder), "accessible/entity projection retained an unrelated map or holder");
            LevelChunk chunk = mock(LevelChunk.class);
            lease.source.complete(ChunkResult.of(List.of(chunk)));
            assertSame(chunk, lease.result.join().orElse(null));
        }
    }
    record SendScope(Object[] callback, List<WeakReference<?>> retired, List<WeakReference<?>> retained) {}
    static SendScope delayedSend() throws Exception {
        ChunkMap map = mock(ChunkMap.class, CALLS_REAL_METHODS);
        ChunkHolder holder = mock(ChunkHolder.class);
        LevelChunk center = mock(LevelChunk.class);
        ChunkAccess first = mock(ChunkAccess.class), last = mock(ChunkAccess.class);
        List<ChunkAccess> chunks = new ArrayList<>(List.of(first, center, last));
        var source = CompletableFuture.completedFuture(ChunkResult.of(chunks));
        Object[] callback = {null};
        CompletableFuture<Void> send = new CompletableFuture<>() {
            @Override public boolean isDone() { return false; }
            @Override public CompletableFuture<Void> thenAcceptAsync(Consumer<? super Void> action, Executor executor) {
                callback[0] = action;
                return new CompletableFuture<>();
            }
        };
        BlockableEventLoop<Runnable> executor = mock(BlockableEventLoop.class);
        doAnswer(call -> { ((Runnable)call.getArgument(0)).run(); return null; }).when(executor).execute(any(Runnable.class));
        for (var entry : List.of(new Object[]{"mainThreadExecutor", executor}, new Object[]{"level", mock(ServerLevel.class)})) {
            var field = ChunkMap.class.getDeclaredField((String)entry[0]); field.setAccessible(true); field.set(map, entry[1]);
        }
        doReturn(send).when(holder).getSendSyncFuture();
        doReturn(source).when(map).getChunkRangeFuture(same(holder), anyInt(), any());
        assertSame(center, map.prepareTickingChunk(holder).join().orElse(null));
        releaseHarnessRoots();
        return new SendScope(callback,
            List.of(new WeakReference<>(source), new WeakReference<>(chunks), new WeakReference<>(first), new WeakReference<>(last)),
            List.of(new WeakReference<>(map), new WeakReference<>(holder), new WeakReference<>(center)));
    }
    @Test void delayedSendKeepsItsActualResourcesWithoutTheOldRangeOrNeighbors() throws Exception {
        SendScope scope = delayedSend();
        collect(scope.retired, "delayed send retained the old range or unrelated neighbor chunks");
        assertNotNull(scope.callback[0]);
        for (var reference : scope.retained) assertNotNull(reference.get(), "delayed send lost an actual callback resource");
        Reference.reachabilityFence(scope.callback[0]);
        scope.callback[0] = null;
        collect(scope.retained, "released send callback retained its map, holder or chunk");
    }
}
