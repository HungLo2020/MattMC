package net.minecraft.server.level;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkStep;
import net.minecraft.util.StaticCache2D;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

/** Real native binding/callback identities, actual task consumption and last-use retirement. */
@Isolated
class NativeStageBindingOwnerTest {
    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }
    static void settle() throws Exception {
        framework().clearInlineMocks();
        org.mockito.internal.progress.ThreadSafeMockingProgress.mockingProgress().resetOngoingStubbing();
        for (int i = 0; i < 40; i++) { System.gc(); Thread.sleep(20); }
    }
    static void retired(long[] before) throws Exception {
        for (int i = 0; i < 160; i++) {
            System.gc(); Thread.sleep(20);
            long[] after = NativeChunkGeneration.stageBindingStatistics();
            if (after[0] <= before[0] && after[3] <= before[3] && after[2] >= before[2] + 1 && after[5] >= before[5] + 1) return;
        }
        long[] after = NativeChunkGeneration.stageBindingStatistics();
        assertTrue(after[0] <= before[0], "last real use left the native stage binding alive");
        assertTrue(after[3] <= before[3], "last real callback left its native lease alive");
        assertTrue(after[2] >= before[2] + 1, "native stage binding destructor did not run");
        assertTrue(after[5] >= before[5] + 1, "native callback lease destructor did not run");
    }
    static WeakReference<?> pendingOwner(long[] before) throws Exception {
        var lease = ChunkStageBindingContractTest.cancelledTaskCallback();
        ChunkStageInvocationContractTest.collected(lease.task(), lease.map(), lease.cache());
        long[] pending = NativeChunkGeneration.stageBindingStatistics();
        assertEquals(before[1] + 1, pending[1], "one actual stage must create one native binding");
        assertEquals(before[4] + 1, pending[4]);
        assertTrue(pending[0] >= before[0] + 1, "task release retired the callback's binding");
        assertTrue(pending[3] >= before[3] + 1, "callable callback lost its native lease");
        assertTrue(pending[7] >= before[7] + 2, "actual task never consumed the native binding");
        assertTrue(pending[8] >= before[8] + 1, "actual pending row never used the native binding");
        assertEquals(before[6], pending[6], "external work completion was mistaken for an engine callback");
        assertNotNull(lease.holder().get());
        ProtoChunk chunk = mock(ProtoChunk.class);
        assertSame(chunk, lease.callback().apply(chunk, null).orElse(null));
        long[] completed = NativeChunkGeneration.stageBindingStatistics();
        assertEquals(before[6] + 1, completed[6]);
        assertEquals(before[1] + 1, completed[1], "completion reconstructed an independent native binding");
        assertTrue(completed[3] >= before[3] + 1, "the actual callback is still callable after its first invocation");
        Reference.reachabilityFence(lease.callback());
        return lease.holder();
    }
    @Test void actualPendingTaskAndLateCallbackShareOneNativeOwnerUntilTheirRealLastUses() throws Exception {
        settle(); long[] before = NativeChunkGeneration.stageBindingStatistics();
        var holder = pendingOwner(before);
        settle(); ChunkStageInvocationContractTest.collected(holder); retired(before);
    }
    record RetainedWork(ChunkStageBindingContractTest.Engine engine,
            CompletableFuture<ChunkResult<ChunkAccess>> work, WeakReference<?> holder) {}
    static RetainedWork synchronousOwner(long[] before) {
        var f = new ChunkStageBindingContractTest.Fixture();
        ProtoChunk chunk = mock(ProtoChunk.class);
        f.engine.onRegistration = () -> f.engine.exposed.complete(f.engine.fire(chunk));
        assertSame(f.engine.exposed, f.apply());
        assertSame(chunk, f.engine.exposed.join().orElse(null));
        long[] first = NativeChunkGeneration.stageBindingStatistics();
        assertEquals(before[1] + 1, first[1]);
        assertEquals(before[4] + 1, first[4]);
        assertEquals(before[6] + 1, first[6]);
        assertTrue(first[0] >= before[0] + 1);
        assertTrue(first[3] >= before[3] + 1, "a retained callable callback was retired after synchronous completion");
        assertThrows(IllegalStateException.class, () -> f.engine.fire(chunk));
        long[] repeated = NativeChunkGeneration.stageBindingStatistics();
        assertEquals(before[1] + 1, repeated[1]);
        assertEquals(before[4] + 1, repeated[4]);
        assertEquals(before[6] + 2, repeated[6]);
        f.engine.callback = null;
        f.engine.onRegistration = () -> {};
        return new RetainedWork(f.engine, f.engine.exposed, new WeakReference<>(f.holder));
    }
    @Test void synchronousAndRepeatedCallbacksReuseTheBindingAndRetireWhileEngineAndWorkRemain() throws Exception {
        settle(); long[] before = NativeChunkGeneration.stageBindingStatistics();
        var retained = synchronousOwner(before);
        settle(); ChunkStageInvocationContractTest.collected(retained.holder()); retired(before);
        Reference.reachabilityFence(retained.engine()); Reference.reachabilityFence(retained.work());
    }
    record PendingOnly(ChunkGenerationTask task, GenerationChunkHolderContractTest.Holder holder,
            ChunkStageBindingContractTest.Engine engine, WeakReference<NativeChunkGeneration.StageBinding> facade) {}
    static PendingOnly abandonedCallback() {
        var holder = new GenerationChunkHolderContractTest.Holder(0, 0);
        var engine = new ChunkStageBindingContractTest.Engine();
        var map = new GenerationChunkHolderContractTest.MapStub() {
            @Override public GenerationChunkHolder acquireGeneration(long key) {
                holder.increaseGenerationRefCount(); return holder;
            }
            @Override public void releaseGeneration(GenerationChunkHolder cell) { cell.decreaseGenerationRefCount(); }
            @Override public CompletableFuture<ChunkAccess> applyStep(GenerationChunkHolder cell,
                    net.minecraft.world.level.chunk.status.ChunkStep step,
                    net.minecraft.util.StaticCache2D<GenerationChunkHolder> cache) { return engine; }
        };
        var task = ChunkGenerationTask.create(map, net.minecraft.world.level.chunk.status.ChunkStatus.EMPTY,
            net.minecraft.world.level.ChunkPos.ZERO);
        assertSame(engine.exposed, task.runUntilWait());
        var binding = NativeChunkGeneration.StageBinding.capture(holder.generationOwner(), engine.exposed);
        assertNotNull(binding, "actual pending engine has no native binding");
        engine.callback = null; // No actual callback can run after this deliberate abandonment.
        return new PendingOnly(task, holder, engine, new WeakReference<>(binding));
    }
    @Test void pendingNativeConsumerOwnsBindingAfterCallbackAndVmFacadeCollection() throws Exception {
        settle(); long[] before = NativeChunkGeneration.stageBindingStatistics();
        var pending = abandonedCallback();
        settle(); ChunkStageInvocationContractTest.collected(pending.facade());
        long[] live = NativeChunkGeneration.stageBindingStatistics();
        assertEquals(before[1] + 1, live[1]);
        assertTrue(live[3] <= before[3], "abandoned real callback lease did not retire");
        assertTrue(live[0] >= before[0] + 1, "pending task lost native binding after VM facade collection");
        assertEquals(before[2], live[2], "pending native consumer retired its binding early");
        assertSame(pending.engine().exposed, pending.task().runUntilWait());
        pending.task().markForCancellation();
        pending.engine().exposed.complete(GenerationChunkHolder.UNLOADED_CHUNK);
        assertNull(pending.task().runUntilWait());
        assertTrue(pending.holder().saves.stream().allMatch(CompletableFuture::isDone));
        retired(before);
        Reference.reachabilityFence(pending);
    }

    @Test void actualInvocationRootsBridgeCallbackAbandonmentBeforeGenuineVirtualReturn() throws Exception {
        settle(); long[] before = NativeChunkGeneration.stageBindingStatistics();
        var engine = new ChunkStageBindingContractTest.Engine();
        class ReturnHolder extends GenerationChunkHolder {
            final java.util.List<CompletableFuture<?>> saves = new java.util.ArrayList<>();
            WeakReference<NativeChunkGeneration.StageBinding> facade;
            ReturnHolder() { super(net.minecraft.world.level.ChunkPos.ZERO); updateHighestAllowedStatus(null); }
            public int getTicketLevel() { return 33; }
            public int getQueueLevel() { return 33; }
            protected void addSaveDependency(CompletableFuture<?> future) { saves.add(future); }
            @Override CompletableFuture<ChunkResult<ChunkAccess>> applyStep(
                    net.minecraft.world.level.chunk.status.ChunkStep step, GeneratingChunkMap map,
                    net.minecraft.util.StaticCache2D<GenerationChunkHolder> cache) {
                var future = super.applyStep(step, map, cache);
                var binding = NativeChunkGeneration.StageBinding.capture(generationOwner(), future);
                assertNotNull(binding);
                facade = new WeakReference<>(binding); binding = null;
                engine.callback = null;
                try { settle(); } catch (Exception failure) { throw new AssertionError(failure); }
                assertNotNull(facade.get(), "native task invocation lost its binding before genuine virtual return");
                return future;
            }
        }
        var holder = new ReturnHolder();
        var map = new GenerationChunkHolderContractTest.MapStub() {
            @Override public GenerationChunkHolder acquireGeneration(long key) {
                holder.increaseGenerationRefCount(); return holder;
            }
            @Override public void releaseGeneration(GenerationChunkHolder cell) { cell.decreaseGenerationRefCount(); }
            @Override public CompletableFuture<ChunkAccess> applyStep(GenerationChunkHolder cell,
                    net.minecraft.world.level.chunk.status.ChunkStep step,
                    net.minecraft.util.StaticCache2D<GenerationChunkHolder> cache) { return engine; }
        };
        var task = ChunkGenerationTask.create(map, net.minecraft.world.level.chunk.status.ChunkStatus.EMPTY,
            net.minecraft.world.level.ChunkPos.ZERO);
        assertSame(engine.exposed, task.runUntilWait());
        settle(); ChunkStageInvocationContractTest.collected(holder.facade);
        long[] pending = NativeChunkGeneration.stageBindingStatistics();
        assertEquals(before[1] + 1, pending[1]);
        assertTrue(pending[0] >= before[0] + 1, "actual pending row lost the invocation's binding");
        assertEquals(before[2], pending[2]);
        assertTrue(pending[3] <= before[3], "abandoned callback lease remained rooted by the invocation");
        task.markForCancellation(); engine.exposed.complete(GenerationChunkHolder.UNLOADED_CHUNK);
        assertNull(task.runUntilWait());
        assertTrue(holder.saves.stream().allMatch(CompletableFuture::isDone));
        retired(before);
        Reference.reachabilityFence(holder); Reference.reachabilityFence(task); Reference.reachabilityFence(engine);
    }
    @Test void laterActualTaskRetainsExistingBindingAfterTheOriginalVmFacadeIsCollected() throws Exception {
  net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
  NativeStageBindingOwnerTest.settle();long[] before=NativeChunkGeneration.stageBindingStatistics();
  var engine=new ChunkStageBindingContractTest.Engine();var holder=new GenerationChunkHolderContractTest.Holder(0,0);
  var map=new GenerationChunkHolderContractTest.MapStub(){
   @Override public GenerationChunkHolder acquireGeneration(long k){holder.increaseGenerationRefCount();return holder;}
   @Override public void releaseGeneration(GenerationChunkHolder h){holder.decreaseGenerationRefCount();}
   @Override public CompletableFuture<ChunkAccess> applyStep(GenerationChunkHolder h,ChunkStep s,StaticCache2D<GenerationChunkHolder> c){return engine;}
  };
  var first=ChunkGenerationTask.create(map,ChunkStatus.EMPTY,net.minecraft.world.level.ChunkPos.ZERO);
  assertSame(engine.exposed,first.runUntilWait());
  var facade=new WeakReference<>(NativeChunkGeneration.StageBinding.capture(holder.generationOwner(),engine.exposed));
  engine.callback=null;NativeStageBindingOwnerTest.settle();ChunkStageInvocationContractTest.collected(facade);
  var second=ChunkGenerationTask.create(map,ChunkStatus.EMPTY,net.minecraft.world.level.ChunkPos.ZERO);
  var slot=second.runUntilWait();assertNotNull(slot);
  first.markForCancellation();engine.exposed.complete(GenerationChunkHolder.UNLOADED_CHUNK);assertNull(first.runUntilWait());
  NativeStageBindingOwnerTest.settle();
  long[] live=NativeChunkGeneration.stageBindingStatistics();
  assertEquals(before[1]+1,live[1],"later consumer reconstructed an unrelated native binding");
  assertTrue(live[0]>=before[0]+1,"later actual pending consumer lost existing native binding after original facade collection");
  assertEquals(before[2],live[2]);
  second.markForCancellation();((CompletableFuture<ChunkResult<ChunkAccess>>)(CompletableFuture<?>)slot).complete(GenerationChunkHolder.UNLOADED_CHUNK);assertNull(second.runUntilWait());
  NativeStageBindingOwnerTest.retired(before);Reference.reachabilityFence(second);Reference.reachabilityFence(holder);
 }
 @Test void actualNativeInvocationOwnsBindingAcrossGenuineImmediateFutureQueryAfterFacadeGc() throws Exception {
  net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();NativeStageBindingOwnerTest.settle();
  long[] before=NativeChunkGeneration.stageBindingStatistics();
  var holder=new GenerationChunkHolderContractTest.Holder(0,0);
  var callback=new AtomicReference<BiFunction<ChunkAccess,Throwable,ChunkResult<ChunkAccess>>>();
  var chunk=mock(ProtoChunk.class);
  var queries=new java.util.concurrent.atomic.AtomicInteger();
  var exposed=new CompletableFuture<ChunkResult<ChunkAccess>>(){
   @Override public ChunkResult<ChunkAccess> getNow(ChunkResult<ChunkAccess> fallback){
    queries.incrementAndGet();
    var facade=new WeakReference<>(NativeChunkGeneration.StageBinding.capture(holder.generationOwner(),this));
    assertNotNull(facade.get());callback.set(null);
    try {NativeStageBindingOwnerTest.settle();ChunkStageInvocationContractTest.collected(facade);}catch(Exception e){throw new AssertionError(e);}
    long[] live=NativeChunkGeneration.stageBindingStatistics();
    assertTrue(live[0]>=before[0]+1,"native immediate invocation lost binding during genuine getNow after facade GC");
    assertEquals(before[2],live[2]);return ChunkResult.of(chunk);
   }
  };
  var engine=new CompletableFuture<ChunkAccess>(){
   @Override @SuppressWarnings({"unchecked","rawtypes"}) public <U> CompletableFuture<U> handle(BiFunction<? super ChunkAccess,Throwable,? extends U> f){callback.set((BiFunction)f);return (CompletableFuture<U>)(CompletableFuture<?>)exposed;}
  };
  var map=new GenerationChunkHolderContractTest.MapStub(){
   @Override public GenerationChunkHolder acquireGeneration(long k){holder.increaseGenerationRefCount();return holder;}
   @Override public void releaseGeneration(GenerationChunkHolder h){holder.decreaseGenerationRefCount();}
   @Override public CompletableFuture<ChunkAccess> applyStep(GenerationChunkHolder h,ChunkStep s,StaticCache2D<GenerationChunkHolder> c){return engine;}
  };
  var task=ChunkGenerationTask.create(map,ChunkStatus.EMPTY,net.minecraft.world.level.ChunkPos.ZERO);
  assertNull(task.runUntilWait());assertEquals(1,queries.get());NativeStageBindingOwnerTest.retired(before);
  Reference.reachabilityFence(task);Reference.reachabilityFence(holder);Reference.reachabilityFence(exposed);
 }
}
