package net.minecraft.server.level;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.status.*;
import net.minecraft.util.thread.BlockableEventLoop;
import org.junit.jupiter.api.*;

class ChunkPreparationContractTest {
 @BeforeAll static void bootstrap(){net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();}
 static void field(ChunkMap map,String name,Object value)throws Exception{var f=ChunkMap.class.getDeclaredField(name);f.setAccessible(true);f.set(map,value);}
 @Test void accessibleAndEntityPreparationPreserveRangePolicyAndCenterProjection(){
  for(boolean entity:new boolean[]{false,true}){
   ChunkMap map=mock(ChunkMap.class,CALLS_REAL_METHODS);ChunkHolder holder=mock(ChunkHolder.class);LevelChunk first=mock(LevelChunk.class),middle=mock(LevelChunk.class),last=mock(LevelChunk.class);
   doAnswer(call->{assertSame(holder,call.getArgument(0));assertEquals(entity?2:1,(int)call.getArgument(1));IntFunction<ChunkStatus> status=call.getArgument(2);for(int i=0;i<3;i++)assertSame(entity?ChunkStatus.FULL:ChunkLevel.getStatusAroundFullChunk(i),status.apply(i));return CompletableFuture.completedFuture(ChunkResult.of(List.of(first,middle,last)));}).when(map).getChunkRangeFuture(same(holder),anyInt(),any());
   assertSame(middle,(entity?map.prepareEntityTickingChunk(holder):map.prepareAccessibleChunk(holder)).join().orElse(null));
  }
 }
 @Test void customRangeThenApplyCanReplaceTheActualAccessibleFuture(){
  ChunkMap map=mock(ChunkMap.class,CALLS_REAL_METHODS);ChunkHolder holder=mock(ChunkHolder.class);CompletableFuture<ChunkResult<LevelChunk>> replacement=new CompletableFuture<>();int[] calls={0};
  CompletableFuture<ChunkResult<List<ChunkAccess>>> range=new CompletableFuture<>(){@Override public <U>CompletableFuture<U> thenApply(Function<? super ChunkResult<List<ChunkAccess>>,? extends U> fn){calls[0]++;return(CompletableFuture<U>)replacement;}};
  doReturn(range).when(map).getChunkRangeFuture(same(holder),anyInt(),any());assertSame(replacement,map.prepareAccessibleChunk(holder));assertEquals(1,calls[0]);
 }
 @Test void tickingPreparationPassesTheActualExecutorToItsVirtualAsyncAdapter()throws Exception{
  ChunkMap map=mock(ChunkMap.class,CALLS_REAL_METHODS);ChunkHolder holder=mock(ChunkHolder.class);BlockableEventLoop<Runnable> executor=mock(BlockableEventLoop.class);field(map,"mainThreadExecutor",executor);CompletableFuture<ChunkResult<LevelChunk>> replacement=new CompletableFuture<>();int[] calls={0};
  CompletableFuture<ChunkResult<List<ChunkAccess>>> range=new CompletableFuture<>(){@Override public <U>CompletableFuture<U> thenApplyAsync(Function<? super ChunkResult<List<ChunkAccess>>,? extends U> fn,Executor actual){assertSame(executor,actual);calls[0]++;return(CompletableFuture<U>)replacement;}};
  doReturn(range).when(map).getChunkRangeFuture(same(holder),eq(1),any());assertSame(replacement,map.prepareTickingChunk(holder));assertEquals(1,calls[0]);
 }
 @Test void tickingEffectsKeepTheirOrderAndTheSendFutureCapturedBeforeReentry()throws Exception{
  ChunkMap map=mock(ChunkMap.class,CALLS_REAL_METHODS);ChunkHolder holder=mock(ChunkHolder.class);ServerLevel level=mock(ServerLevel.class);LevelChunk chunk=mock(LevelChunk.class);BlockableEventLoop<Runnable> executor=mock(BlockableEventLoop.class);field(map,"mainThreadExecutor",executor);field(map,"level",level);doAnswer(call->{((Runnable)call.getArgument(0)).run();return null;}).when(executor).execute(any(Runnable.class));List<String> trace=new ArrayList<>();
  doAnswer(call->{trace.add("postprocess");return null;}).when(chunk).postProcessGeneration(level);doAnswer(call->{trace.add("tick");return null;}).when(level).startTickingChunk(chunk);
  CompletableFuture<Void> send=new CompletableFuture<>(){@Override public boolean isDone(){trace.add("done");doReturn(CompletableFuture.completedFuture(null)).when(holder).getSendSyncFuture();return false;}@Override public CompletableFuture<Void> thenAcceptAsync(Consumer<? super Void> action,Executor actual){trace.add("register");assertSame(executor,actual);return new CompletableFuture<>();}};
  doAnswer(call->{trace.add("send");return send;}).when(holder).getSendSyncFuture();doReturn(CompletableFuture.completedFuture(ChunkResult.of(List.of(chunk)))).when(map).getChunkRangeFuture(same(holder),eq(1),any());assertSame(chunk,map.prepareTickingChunk(holder).join().orElse(null));assertEquals(List.of("postprocess","tick","send","done","register"),trace);
 }
 @Test void postprocessFailurePreservesItsIdentityAndStopsBeforeTickAndSend()throws Exception{
  ChunkMap map=mock(ChunkMap.class,CALLS_REAL_METHODS);ChunkHolder holder=mock(ChunkHolder.class);ServerLevel level=mock(ServerLevel.class);LevelChunk chunk=mock(LevelChunk.class);BlockableEventLoop<Runnable> executor=mock(BlockableEventLoop.class);field(map,"mainThreadExecutor",executor);field(map,"level",level);doAnswer(call->{((Runnable)call.getArgument(0)).run();return null;}).when(executor).execute(any(Runnable.class));RuntimeException failure=new RuntimeException("postprocess");doThrow(failure).when(chunk).postProcessGeneration(level);doReturn(CompletableFuture.completedFuture(ChunkResult.of(List.of(chunk)))).when(map).getChunkRangeFuture(same(holder),eq(1),any());assertSame(failure,assertThrows(CompletionException.class,()->map.prepareTickingChunk(holder).join()).getCause());verify(level,never()).startTickingChunk(any());verify(holder,never()).getSendSyncFuture();
 }
}
