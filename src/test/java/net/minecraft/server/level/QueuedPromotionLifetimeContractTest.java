package net.minecraft.server.level;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.lang.ref.*;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.chunk.*;
import org.junit.jupiter.api.*;
class QueuedPromotionLifetimeContractTest {
    @BeforeAll static void bootstrap() { net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap(); }
    static class Holder extends ChunkHolder {
        Holder() { super(new ChunkPos(4,-7),33,LevelHeightAccessor.create(0,16),null,(p,q,l,set)->set.accept(l),null); }
        void drive(ChunkMap map,java.util.concurrent.Executor executor) {updateFutures(map,executor);}
        void save(CompletableFuture<?> dependency) {addSaveDependency(dependency);}
    }
    @SuppressWarnings({"unchecked","rawtypes"})
    static WeakReference<CompletableFuture<?>> prepareThenRetire(Holder holder,ChunkMap map,List<Runnable> queue) {
        CompletableFuture<ChunkResult<LevelChunk>>[] source=new CompletableFuture[]{new CompletableFuture<>()};
        when(map.prepareAccessibleChunk(holder)).thenAnswer(call->source[0]);
        holder.drive(map,queue::add);
        var weak=new WeakReference<CompletableFuture<?>>(source[0]);
        source[0].complete(ChunkResult.of(mock(LevelChunk.class)));
        assertEquals(1,queue.size());
        holder.setTicketLevel(ChunkLevel.MAX_LEVEL+1);holder.drive(map,queue::add);
        holder.save(CompletableFuture.completedFuture(null));
        source[0]=null; clearInvocations(map);
        return weak;
    }
    @Test void queuedPromotionRetainsTheOriginalHolderButNotItsRetiredPreparedFuture() throws Exception {
        Holder holder=new Holder();ChunkMap map=mock(ChunkMap.class);List<Runnable> queue=new ArrayList<>();
        var weak=prepareThenRetire(holder,map,queue);
        for(int attempt=0;attempt<80 && weak.refersTo(null)==false;attempt++) {System.gc();Thread.sleep(20);}
        assertNull(weak.get(),"queued promotion must not retain a retired prepared future after save/full bindings move on");
        assertEquals(1,queue.size());queue.removeFirst().run();
        verify(map).onFullChunkStatusChange(holder.getPos(),FullChunkStatus.FULL);
        Reference.reachabilityFence(holder);Reference.reachabilityFence(queue);
    }
}
