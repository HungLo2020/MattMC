package net.minecraft.server.level;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.lang.reflect.*;
import java.util.concurrent.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.status.*;
import org.junit.jupiter.api.*;
import sun.misc.Unsafe;

class ChunkRequestBoundaryContractTest {
    @BeforeAll static void bootstrap() { net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap(); }
    static void set(Object target, String name, Object value) throws Exception {
        Field f = ServerChunkCache.class.getDeclaredField(name); f.setAccessible(true); f.set(target, value);
    }
    static ServerChunkCache cache(ChunkMap map, ServerChunkCache.MainThreadExecutor executor, boolean main) throws Exception {
        Field f = Unsafe.class.getDeclaredField("theUnsafe"); f.setAccessible(true);
        ServerChunkCache cache = (ServerChunkCache)((Unsafe)f.get(null)).allocateInstance(ServerChunkCache.class);
        set(cache,"mainThread",main ? Thread.currentThread() : new Thread());
        set(cache,"chunkMap",map); set(cache,"mainThreadProcessor",executor); set(cache,"ticketStorage",mock(TicketStorage.class));
        try { set(cache,"lastChunkPos",new long[4]); } catch(NoSuchFieldException nativeKeys) {}
        set(cache,"lastChunkStatus",new ChunkStatus[4]); set(cache,"lastChunk",new ChunkAccess[4]);
        // Runtime setup only: Frozen has no request graph; candidate must bind
        // the actual native source/registry before exercising the same public API.
        try {
            Field graphField=ServerChunkCache.class.getDeclaredField("requestGraph");
            Class<?> graphType=Class.forName("net.minecraft.server.level.NativeChunkGeneration$RequestGraph");
            Method ownerMethod=ChunkMap.class.getDeclaredMethod("generationRegistryOwner");ownerMethod.setAccessible(true);
            if(ownerMethod.invoke(map)==null){
                Class<?> registry=Class.forName("net.minecraft.server.level.NativeChunkHolders");
                var registryCtor=registry.getDeclaredConstructor(int.class);registryCtor.setAccessible(true);
                Object owner=registryCtor.newInstance(ChunkLevel.MAX_LEVEL);
                Method requestOwner=registry.getDeclaredMethod("requestOwner");requestOwner.setAccessible(true);
                when(ownerMethod.invoke(map)).thenReturn(requestOwner.invoke(owner));
            }
            TicketStorage storage=new TicketStorage();set(cache,"ticketStorage",storage);
            var ctor=graphType.getDeclaredConstructor(ChunkMap.class,TicketStorage.class);ctor.setAccessible(true);
            graphField.setAccessible(true);graphField.set(cache,ctor.newInstance(map,storage));clearInvocations(map);
        } catch(NoSuchFieldException originalRuntime) {}
        Method clear = ServerChunkCache.class.getDeclaredMethod("clearCache"); clear.setAccessible(true); clear.invoke(cache);
        return cache;
    }
    @Test void nullFullCacheEntryAndDistinctStatusRemainObservableUntilClear() throws Exception {
        ChunkMap map = mock(ChunkMap.class); ServerChunkCache.MainThreadExecutor executor = mock(ServerChunkCache.MainThreadExecutor.class);
        ServerChunkCache cache = cache(map,executor,true); long key = ChunkPos.asLong(4,-7);
        assertNull(cache.getChunk(4,-7,ChunkStatus.FULL,false));
        ChunkHolder holder = mock(ChunkHolder.class); LevelChunk full = mock(LevelChunk.class); ProtoChunk proto = mock(ProtoChunk.class);
        when(map.getVisibleChunkIfPresent(key)).thenReturn(holder); when(holder.getTicketLevel()).thenReturn(33);
        when(holder.scheduleChunkGenerationTask(ChunkStatus.FULL,map)).thenReturn(CompletableFuture.completedFuture(ChunkResult.of(full)));
        when(holder.scheduleChunkGenerationTask(ChunkStatus.EMPTY,map)).thenReturn(CompletableFuture.completedFuture(ChunkResult.of(proto)));
        assertNull(cache.getChunkNow(4,-7)); assertNull(cache.getChunk(4,-7,ChunkStatus.FULL,false));
        verifyNoInteractions(holder);
        assertSame(proto,cache.getChunk(4,-7,ChunkStatus.EMPTY,false));
        assertSame(full,cache.getChunk(4,-7,ChunkStatus.FULL,true));
        assertSame(full,cache.getChunkNow(4,-7));
        Method clear = ServerChunkCache.class.getDeclaredMethod("clearCache"); clear.setAccessible(true); clear.invoke(cache);
        when(holder.getChunkIfPresent(ChunkStatus.FULL)).thenReturn(null);
        assertNull(cache.getChunkNow(4,-7)); verify(holder).getChunkIfPresent(ChunkStatus.FULL);
    }
    @Test void offThreadNowDoesNotDispatchAndBothBlockingRequestsPreserveRejectionIdentity() throws Exception {
        ChunkMap map = mock(ChunkMap.class); ServerChunkCache.MainThreadExecutor executor = mock(ServerChunkCache.MainThreadExecutor.class);
        ServerChunkCache cache = cache(map,executor,false); RejectedExecutionException failure = new RejectedExecutionException("original executor");
        doThrow(failure).when(executor).execute(any(Runnable.class));
        assertNull(cache.getChunkNow(4,-7)); verifyNoInteractions(map,executor);
        assertSame(failure,assertThrows(RejectedExecutionException.class,()->cache.getChunkFuture(4,-7,ChunkStatus.FULL,true)));
        assertSame(failure,assertThrows(RejectedExecutionException.class,()->cache.getChunk(4,-7,ChunkStatus.FULL,true)));
        verifyNoInteractions(map);
    }

    @Test void ticketReentryKeepsInitialHolderThenResolvesTheNewVisibleHolderAfterUpdates() throws Exception {
        ChunkMap map=mock(ChunkMap.class); ServerChunkCache.MainThreadExecutor executor=mock(ServerChunkCache.MainThreadExecutor.class);
        ServerChunkCache cache=cache(map,executor,true); set(cache,"distanceManager",mock(DistanceManager.class));
        TicketStorage storage=mock(TicketStorage.class); set(cache,"ticketStorage",storage);
        long key=ChunkPos.asLong(4,-7); ChunkHolder initial=mock(ChunkHolder.class), replacement=mock(ChunkHolder.class);
        java.util.List<String> trace=new java.util.ArrayList<>(); int[] lookups={0},level={33};
        when(map.getVisibleChunkIfPresent(key)).thenAnswer(call -> { trace.add("lookup:"+lookups[0]); return lookups[0]++==0?initial:replacement; });
        doAnswer(call -> { Ticket ticket=call.getArgument(0); assertSame(TicketType.UNKNOWN,ticket.getType()); assertEquals(33,ticket.getTicketLevel()); trace.add("ticket"); level[0]=34; return null; }).when(storage).addTicket(any(Ticket.class),any(ChunkPos.class));
        when(initial.getTicketLevel()).thenAnswer(call -> {trace.add("old-level:"+level[0]); return level[0];});
        when(replacement.getTicketLevel()).thenAnswer(call -> {trace.add("new-level"); return 33;});
        var expected=new CompletableFuture<ChunkResult<ChunkAccess>>();
        when(replacement.scheduleChunkGenerationTask(ChunkStatus.FULL,map)).thenAnswer(call->{trace.add("schedule");return expected;});
        assertSame(expected,cache.getChunkFuture(4,-7,ChunkStatus.FULL,true));
        assertEquals(java.util.List.of("lookup:0","ticket","old-level:34","lookup:1","new-level","new-level","schedule"),trace);
        verify(initial,never()).scheduleChunkGenerationTask(any(),any());
    }
    @Test void duplicateAdmissionQueriesCustomHolderTwiceAndTicketFailureStopsBeforeEitherQuery() throws Exception {
        ChunkMap map=mock(ChunkMap.class); ServerChunkCache.MainThreadExecutor executor=mock(ServerChunkCache.MainThreadExecutor.class);
        ServerChunkCache cache=cache(map,executor,true); TicketStorage storage=mock(TicketStorage.class); set(cache,"ticketStorage",storage);
        ChunkHolder holder=mock(ChunkHolder.class); when(map.getVisibleChunkIfPresent(anyLong())).thenReturn(holder); when(holder.getTicketLevel()).thenReturn(33);
        var future=new CompletableFuture<ChunkResult<ChunkAccess>>(); when(holder.scheduleChunkGenerationTask(ChunkStatus.FULL,map)).thenReturn(future);
        assertSame(future,cache.getChunkFuture(4,-7,ChunkStatus.FULL,true)); verify(holder,times(2)).getTicketLevel();
        clearInvocations(holder); RuntimeException failure=new RuntimeException("ticket listener failure");
        doThrow(failure).when(storage).addTicket(any(Ticket.class),any(ChunkPos.class));
        assertSame(failure,assertThrows(RuntimeException.class,()->cache.getChunkFuture(4,-7,ChunkStatus.FULL,true)));
        verifyNoInteractions(holder);
    }
    @Test void cacheHitsDoNotRefreshInsertionOrderAndFifthInsertEvictsOldest() throws Exception {
        ChunkMap map=mock(ChunkMap.class); var executor=mock(ServerChunkCache.MainThreadExecutor.class);
        ServerChunkCache cache=cache(map,executor,true); ChunkHolder holder=mock(ChunkHolder.class);
        when(map.getVisibleChunkIfPresent(anyLong())).thenReturn(holder); when(holder.getTicketLevel()).thenReturn(33);
        LevelChunk chunk=mock(LevelChunk.class); when(holder.scheduleChunkGenerationTask(ChunkStatus.FULL,map)).thenReturn(CompletableFuture.completedFuture(ChunkResult.of(chunk)));
        for(int x=0;x<4;x++) assertSame(chunk,cache.getChunk(x,9,ChunkStatus.FULL,false));
        clearInvocations(map,holder);
        assertSame(chunk,cache.getChunk(0,9,ChunkStatus.FULL,false)); verifyNoInteractions(map,holder);
        assertSame(chunk,cache.getChunk(4,9,ChunkStatus.FULL,false));
        assertSame(chunk,cache.getChunk(1,9,ChunkStatus.FULL,false));
        assertSame(chunk,cache.getChunk(0,9,ChunkStatus.FULL,false));
        verify(holder,times(2)).scheduleChunkGenerationTask(ChunkStatus.FULL,map);
    }
    @Test void generationTaskFailureLeavesCacheUntilSuccessfulUpdateInvalidation() throws Exception {
        ChunkMap map=mock(ChunkMap.class); var executor=mock(ServerChunkCache.MainThreadExecutor.class);
        ServerChunkCache cache=cache(map,executor,true); DistanceManager distance=mock(DistanceManager.class);set(cache,"distanceManager",distance);
        assertNull(cache.getChunk(4,-7,ChunkStatus.FULL,false));
        when(distance.runAllUpdates(map)).thenReturn(true); RuntimeException failure=new RuntimeException("generation task prefix");
        doThrow(failure).when(map).runGenerationTasks();
        assertSame(failure,assertThrows(RuntimeException.class,cache::runDistanceManagerUpdates));
        clearInvocations(map); assertNull(cache.getChunkNow(4,-7));verifyNoInteractions(map);
        doNothing().when(map).runGenerationTasks();assertTrue(cache.runDistanceManagerUpdates());
        clearInvocations(map);assertNull(cache.getChunkNow(4,-7));verify(map).getVisibleChunkIfPresent(ChunkPos.asLong(4,-7));
    }
    @Test void failedBlockingCreateDoesNotPublishANullCacheEntry() throws Exception {
        ChunkMap map=mock(ChunkMap.class); var executor=mock(ServerChunkCache.MainThreadExecutor.class);
        ServerChunkCache cache=cache(map,executor,true);ChunkHolder holder=mock(ChunkHolder.class);
        when(map.getVisibleChunkIfPresent(anyLong())).thenReturn(holder);when(holder.getTicketLevel()).thenReturn(33);
        when(holder.scheduleChunkGenerationTask(ChunkStatus.FULL,map)).thenReturn(GenerationChunkHolder.UNLOADED_CHUNK_FUTURE);
        // The pause-only create failure is checked in an isolated runner.
        org.junit.jupiter.api.Assumptions.assumeTrue(Boolean.getBoolean("mattmc.verify.requestFailure"));
        assertThrows(IllegalStateException.class,()->cache.getChunk(4,-7,ChunkStatus.FULL,true));
        LevelChunk chunk=mock(LevelChunk.class);when(holder.getChunkIfPresent(ChunkStatus.FULL)).thenReturn(chunk);
        assertSame(chunk,cache.getChunkNow(4,-7));verify(holder).getChunkIfPresent(ChunkStatus.FULL);
    }

    @Test void blockingJoinReentryUsesIndependentFramesAndPublishesIntoTheLiveCache() throws Exception {
        ChunkMap map=mock(ChunkMap.class); var executor=mock(ServerChunkCache.MainThreadExecutor.class);
        ServerChunkCache cache=cache(map,executor,true);ChunkHolder holder=mock(ChunkHolder.class);LevelChunk chunk=mock(LevelChunk.class);
        when(map.getVisibleChunkIfPresent(anyLong())).thenReturn(holder);when(holder.getTicketLevel()).thenReturn(33);
        CompletableFuture<ChunkResult<ChunkAccess>> outer=new CompletableFuture<>(){
            @Override public ChunkResult<ChunkAccess> join(){
                for(int x=1;x<=4;x++)assertSame(chunk,cache.getChunk(x,9,ChunkStatus.FULL,false));
                return ChunkResult.of(chunk);
            }
        };
        int[] schedules={0};when(holder.scheduleChunkGenerationTask(ChunkStatus.FULL,map)).thenAnswer(call->schedules[0]++==0?outer:CompletableFuture.completedFuture(ChunkResult.of(chunk)));
        assertSame(chunk,cache.getChunk(0,9,ChunkStatus.FULL,false));assertEquals(5,schedules[0]);
        assertSame(chunk,cache.getChunk(2,9,ChunkStatus.FULL,false));assertEquals(5,schedules[0]);
        assertSame(chunk,cache.getChunk(1,9,ChunkStatus.FULL,false));assertEquals(6,schedules[0]);
    }

}
