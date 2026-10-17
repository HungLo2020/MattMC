package net.minecraft.server.level;
import static org.junit.jupiter.api.Assertions.*;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.junit.jupiter.api.*;
@org.junit.jupiter.api.parallel.Isolated
class ChunkDependencyRetirementContractTest {
 @BeforeAll static void bootstrap(){net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();}
 record Observed(ChunkAccess chunk,Runnable read) implements ChunkResult<ChunkAccess>{
  public boolean isSuccess(){return true;}
  public ChunkAccess orElse(ChunkAccess fallback){read.run();return chunk;}
  public String getError(){return null;}
  public ChunkResult<ChunkAccess> ifSuccess(Consumer<ChunkAccess> fn){fn.accept(chunk);return this;}
  public <R>ChunkResult<R> map(Function<ChunkAccess,R> fn){return ChunkResult.of(fn.apply(chunk));}
  public <E extends Throwable>ChunkAccess orElseThrow(Supplier<E> error){return chunk;}
 }
 record Lease(CompletableFuture<ChunkResult<ChunkAccess>> last,ChunkResult<ChunkAccess> result,RuntimeException failure,CompletableFuture<?> observed){}
 static void collected(List<WeakReference<?>> weak){
  for(int i=0;i<160;i++){System.gc();try{Thread.sleep(20);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}if(weak.stream().allMatch(ref->ref.get()==null))return;}
  assertTrue(weak.stream().allMatch(ref->ref.get()==null),"range retained input futures after their final engine use");
 }
 static Lease pending(boolean failing){
  var fixture=new ChunkReadinessDependencyContractTest.Fixture();var output=fixture.start();List<WeakReference<?>> weak=new ArrayList<>();for(int i=1;i<9;i++)weak.add(new WeakReference<>(fixture.leaves.get(i)));
  RuntimeException failure=new RuntimeException("source failure");
  CompletableFuture<?> observed=failing?output.handle((value,error)->{collected(weak);return null;}):output;
  for(int i=1;i<9;i++)fixture.complete(i);
  ChunkResult<ChunkAccess> result=new Observed(fixture.chunks.getFirst(),()->collected(weak));
  org.mockito.Mockito.framework().clearInlineMocks();org.mockito.internal.progress.ThreadSafeMockingProgress.mockingProgress().resetOngoingStubbing();
  return new Lease(fixture.leaves.getFirst(),result,failure,observed);
 }
 @Test void joinedInputFuturesRetireBeforeResultCallbacks(){Lease lease=pending(false);lease.last.complete(lease.result);lease.observed.join();}
 @Test void failedInputFuturesRetireBeforeThePublicFailureCallback(){Lease lease=pending(true);lease.last.completeExceptionally(lease.failure);lease.observed.join();}
}
