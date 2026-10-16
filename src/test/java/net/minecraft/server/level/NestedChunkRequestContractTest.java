package net.minecraft.server.level;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.status.*;
import org.junit.jupiter.api.*;
class NestedChunkRequestContractTest {
 @BeforeAll static void bootstrap(){net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();}
 static void bindActualRequestGraph(ServerChunkCache cache,ChunkMap map) throws Exception {
  Field field;
  try{field=ServerChunkCache.class.getDeclaredField("requestGraph");}catch(NoSuchFieldException original){return;}
  Class<?> type=Class.forName("net.minecraft.server.level.NativeChunkGeneration$RequestGraph");
  var ctor=type.getDeclaredConstructor(ChunkMap.class,TicketStorage.class);ctor.setAccessible(true);
  TicketStorage storage=new TicketStorage();ChunkRequestBoundaryContractTest.set(cache,"ticketStorage",storage);
  field.setAccessible(true);field.set(cache,ctor.newInstance(map,storage));
 }
 @Test void terminalSchedulingCanReenterTheSameActualRequestOwnerWithoutClobberingOuterResult() throws Exception {
  ChunkMap map=VisibleHolderBoundaryContractTest.map();
  ServerChunkCache.MainThreadExecutor executor=mock(ServerChunkCache.MainThreadExecutor.class);
  ServerChunkCache cache=ChunkRequestBoundaryContractTest.cache(map,executor,true);bindActualRequestGraph(cache,map);
  ChunkHolder holder=mock(ChunkHolder.class);List<String> trace=new ArrayList<>();int[] calls={0};
  when(map.getVisibleChunkIfPresent(anyLong())).thenAnswer(call->{trace.add("lookup");return holder;});
  when(holder.getTicketLevel()).thenAnswer(call->{trace.add("level");return 33;});
  var outer=new CompletableFuture<ChunkResult<ChunkAccess>>();var inner=new CompletableFuture<ChunkResult<ChunkAccess>>();
  when(holder.scheduleChunkGenerationTask(ChunkStatus.FULL,map)).thenAnswer(call->{
   if(calls[0]++==0){trace.add("outer-schedule");assertSame(inner,cache.getChunkFuture(4,-7,ChunkStatus.FULL,false));trace.add("outer-resume");return outer;}
   trace.add("inner-schedule");return inner;
  });
  assertSame(outer,cache.getChunkFuture(4,-7,ChunkStatus.FULL,false));
  assertEquals(List.of("lookup","level","outer-schedule","lookup","level","inner-schedule","outer-resume"),trace);
 }
}
