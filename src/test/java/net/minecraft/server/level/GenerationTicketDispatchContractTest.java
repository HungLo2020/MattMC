package net.minecraft.server.level;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.world.level.*;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.status.*;
import org.junit.jupiter.api.*;
class GenerationTicketDispatchContractTest {
 @BeforeAll static void bootstrap(){net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();}
 record Fixture(TicketStorage storage,ServerChunkCache cache,ChunkHolder holder,CompletableFuture<ChunkResult<ChunkAccess>> future){}
 static Fixture fixture(Ticket stored)throws Exception {
  ChunkMap map=VisibleHolderBoundaryContractTest.map();
  ServerChunkCache.MainThreadExecutor executor=mock(ServerChunkCache.MainThreadExecutor.class);
  ServerChunkCache cache=ChunkRequestBoundaryContractTest.cache(map,executor,true);
  TicketStorage storage=new TicketStorage();
  ChunkRequestBoundaryContractTest.set(cache,"ticketStorage",storage);NestedChunkRequestContractTest.bindActualRequestGraph(cache,map);
  var actual=ServerChunkCache.class.getDeclaredField("ticketStorage");actual.setAccessible(true);storage=(TicketStorage)actual.get(cache);
  storage.addTicket(ChunkPos.asLong(4,-7),stored);
  ChunkHolder holder=mock(ChunkHolder.class);when(map.getVisibleChunkIfPresent(ChunkPos.asLong(4,-7))).thenReturn(holder);when(holder.getTicketLevel()).thenReturn(33);
  var future=new CompletableFuture<ChunkResult<ChunkAccess>>();when(holder.scheduleChunkGenerationTask(ChunkStatus.FULL,map)).thenReturn(future);
  return new Fixture(storage,cache,holder,future);
 }
 @Test void realChunkRequestCallsTheStoredResetOverride()throws Exception {
  class Stored extends Ticket {int resets;Stored(){super(TicketType.UNKNOWN,33);}@Override public void resetTicksLeft(){resets++;super.resetTicksLeft();}}
  Stored stored=new Stored();Fixture fixture=fixture(stored);
  assertSame(fixture.future(),fixture.cache().getChunkFuture(4,-7,ChunkStatus.FULL,true));assertEquals(1,stored.resets);
 }
 @Test void realChunkRequestRetainsGetterFailureBeforeHolderQueries()throws Exception {
  IllegalStateException marker=new IllegalStateException("request stored getter");
  class Stored extends Ticket {boolean armed;Stored(){super(TicketType.UNKNOWN,33);}@Override public TicketType getType(){if(armed)throw marker;return super.getType();}}
  Stored stored=new Stored();Fixture fixture=fixture(stored);stored.armed=true;clearInvocations(fixture.holder());
  assertSame(marker,assertThrows(IllegalStateException.class,()->fixture.cache().getChunkFuture(4,-7,ChunkStatus.FULL,true)));verifyNoInteractions(fixture.holder());
 }
 @Test void realChunkRequestMatchesTheActualStoredType()throws Exception {
  class Stored extends Ticket {TicketType type=TicketType.UNKNOWN;Stored(){super(TicketType.UNKNOWN,33);}@Override public TicketType getType(){return type;}}
  Stored stored=new Stored();Fixture fixture=fixture(stored);stored.type=TicketType.PORTAL;
  assertSame(fixture.future(),fixture.cache().getChunkFuture(4,-7,ChunkStatus.FULL,true));assertEquals(2,fixture.storage().getTickets(ChunkPos.asLong(4,-7)).size());
 }
}
