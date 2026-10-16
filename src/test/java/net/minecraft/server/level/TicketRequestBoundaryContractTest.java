package net.minecraft.server.level;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import net.minecraft.world.level.*;
import org.junit.jupiter.api.*;
class TicketRequestBoundaryContractTest {
    @BeforeAll static void bootstrap() { net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap(); }
    static class Storage extends TicketStorage {
        List<String> events = new ArrayList<>();
        @Override public void setDirty() { if(events!=null)events.add("dirty"); super.setDirty(); }
    }
    @Test void canonicalUnknownDuplicatePreservesOriginalBindingListenerAndDirtyOrder() {
        Storage storage=new Storage(); long key=ChunkPos.asLong(4,-7);
        storage.setLoadingChunkUpdatedListener((pos,level,decrease)->storage.events.add("load:"+level+":"+decrease));
        Ticket first=new Ticket(TicketType.UNKNOWN,33);
        assertTrue(storage.addTicket(key,first));
        assertEquals(List.of("load:33:true","dirty"),storage.events);
        List<Ticket> list=storage.getTickets(key); storage.events.clear();
        assertFalse(storage.addTicket(key,new Ticket(TicketType.UNKNOWN,33)));
        assertEquals(List.of("dirty"),storage.events); assertSame(first,storage.getTickets(key).getFirst());
        assertSame(list,storage.getTickets(key)); assertTrue(storage.isDirty());
    }
    @Test void throwingLoadingListenerLeavesCommittedTicketBeforeDirtyAndDuplicateDoesNotReplayIt() {
        Storage storage=new Storage(); long key=ChunkPos.asLong(4,-7); RuntimeException failure=new RuntimeException("original loading listener");
        storage.setLoadingChunkUpdatedListener((pos,level,decrease)->{storage.events.add("load");throw failure;});
        Ticket first=new Ticket(TicketType.UNKNOWN,33);
        assertSame(failure,assertThrows(RuntimeException.class,()->storage.addTicket(key,first)));
        assertEquals(List.of("load"),storage.events); assertSame(first,storage.getTickets(key).getFirst()); assertFalse(storage.isDirty());
        storage.events.clear(); assertFalse(storage.addTicket(key,new Ticket(TicketType.UNKNOWN,33)));
        assertEquals(List.of("dirty"),storage.events); assertSame(first,storage.getTickets(key).getFirst());
    }

    @Test void actualRequestOwnerPreservesCanonicalTicketIdentityAndDirtyBeforeHolderQueries() throws Exception {
        ChunkMap map=VisibleHolderBoundaryContractTest.map();
        ServerChunkCache.MainThreadExecutor executor=org.mockito.Mockito.mock(ServerChunkCache.MainThreadExecutor.class);
        ServerChunkCache cache=ChunkRequestBoundaryContractTest.cache(map,executor,true);
        ChunkRequestBoundaryContractTest.set(cache,"ticketStorage",new TicketStorage());
        NestedChunkRequestContractTest.bindActualRequestGraph(cache,map);
        var field=ServerChunkCache.class.getDeclaredField("ticketStorage");field.setAccessible(true);
        TicketStorage storage=(TicketStorage)field.get(cache);long key=ChunkPos.asLong(4,-7);
        ChunkHolder holder=org.mockito.Mockito.mock(ChunkHolder.class);
        org.mockito.Mockito.when(map.getVisibleChunkIfPresent(key)).thenReturn(holder);
        org.mockito.Mockito.when(holder.getTicketLevel()).thenAnswer(call->{assertTrue(storage.isDirty(),"dirty effect precedes each actual holder query");return 33;});
        var expected=new java.util.concurrent.CompletableFuture<ChunkResult<net.minecraft.world.level.chunk.ChunkAccess>>();
        org.mockito.Mockito.when(holder.scheduleChunkGenerationTask(net.minecraft.world.level.chunk.status.ChunkStatus.FULL,map)).thenReturn(expected);
        assertSame(expected,cache.getChunkFuture(4,-7,net.minecraft.world.level.chunk.status.ChunkStatus.FULL,true));
        Ticket first=storage.getTickets(key).getFirst();assertSame(TicketType.UNKNOWN,first.getType());assertEquals(33,first.getTicketLevel());
        String original=first.toString();first.decreaseTicksLeft();storage.setDirty(false);
        assertSame(expected,cache.getChunkFuture(4,-7,net.minecraft.world.level.chunk.status.ChunkStatus.FULL,true));
        assertEquals(1,storage.getTickets(key).size());assertSame(first,storage.getTickets(key).getFirst());assertEquals(original,first.toString());
        org.mockito.Mockito.verify(holder,org.mockito.Mockito.times(4)).getTicketLevel();
    }
}
