package net.minecraft.server.level;

import java.io.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.GZIPInputStream;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import static org.junit.jupiter.api.Assertions.*;

/** Actual Frozen histories; callback pins are supplemental adapter controls. */
@Tag("parity")
class NativeChunkTasksTest {
    @BeforeAll static void bootstrap() { net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap(); }
    record Task(int id) implements Runnable { public void run() {} }
    private static NativeChunkTasks nativeQueue(ChunkTaskPriorityQueue queue) throws Exception {
        Field field=ChunkTaskPriorityQueue.class.getDeclaredField("queue");field.setAccessible(true);return (NativeChunkTasks)field.get(queue);
    }
    @Test void actualFrozenOperationsMatchPublicQueueOrderAndFailurePrefixes() throws Exception {
        try(var in=new DataInputStream(new GZIPInputStream(Objects.requireNonNull(getClass().getResourceAsStream("/world/chunk/frozen-chunk-tasks.bin.gz"))))) {
            assertEquals(0x43545131,in.readInt());assertEquals(5,in.readInt());
            for(int i=0;i<5;i++){assertFalse(in.readUTF().isEmpty());assertEquals(64,in.readUTF().length());}
            assertTrue(in.readUTF().contains("Actual Frozen server chunk-task queue"));assertEquals(ChunkTaskPriorityQueue.PRIORITY_LEVEL_COUNT,in.readInt());assertEquals(8,in.readInt());
            int steps=0;
            for(int seed=0;seed<8;seed++) {
                var queue=new ChunkTaskPriorityQueue("frozen");var nativeQueue=nativeQueue(queue);var tasks=new HashMap<Integer,Task>();int count=in.readInt();
                for(int step=0;step<count;step++) {
                    int action=in.readInt();long pos=in.readLong();int a=in.readInt(),b=in.readInt(),token=in.readInt();
                    String exception=in.readUTF();boolean work=in.readBoolean();String text=in.readUTF();int pending=in.readInt();boolean popped=in.readBoolean();
                    long outputPos=0;var expected=new ArrayList<Integer>();
                    if(popped){outputPos=in.readLong();int n=in.readInt();for(int j=0;j<n;j++)expected.add(in.readInt());}
                    String actualException="";ChunkTaskPriorityQueue.TasksForChunk result=null;
                    try {
                        switch(action) {
                            case 0->queue.submit(token==0?null:tasks.computeIfAbsent(token,Task::new),pos,a);
                            case 1->queue.resortChunkTasks(a,new ChunkPos(pos),b);
                            case 2->queue.release(pos,a!=0);
                            case 3->result=queue.pop();
                        }
                    }catch(RuntimeException e){actualException=e.getClass().getName();}
                    String context="seed"+seed+" step"+step;
                    assertEquals(exception,actualException,context);assertEquals(work,queue.hasWork(),context);assertEquals(text,queue.toString(),context);
                    assertEquals(pending,nativeQueue.pending(),context);assertEquals(pending,nativeQueue.pinnedCount(),context);assertEquals(popped,result!=null,context);
                    if(result!=null) {
                        assertEquals(outputPos,result.chunkPos(),context);assertEquals(expected,result.tasks().stream().map(t->t==null?0:((Task)t).id()).toList(),context);
                        for(Runnable task:result.tasks())if(task!=null)assertSame(tasks.get(((Task)task).id()),task);
                        // The returned list remains an independent mutable ArrayList.
                        result.tasks().add(null);result.tasks().clear();assertEquals(pending,nativeQueue.pending());
                    }
                    steps++;
                }
                assertEquals(0,nativeQueue.pinnedCount());assertFalse(queue.hasWork());
            }
            assertTrue(steps>35000);assertEquals(-1,in.read());
        }
    }
    @Test void callableReentryNullsDuplicateIdentityAndSourceFailureKeepPinsCorrect() throws Exception {
        var queue=new ChunkTaskPriorityQueue("callbacks");var nativeQueue=nativeQueue(queue);var calls=new ArrayList<Integer>();
        Runnable task=()->{calls.add(1);queue.submit(()->calls.add(2),0,0);};
        queue.submit(task,0,1);queue.submit(null,0,1);queue.submit(task,0,1);queue.release(0,false);
        var batch=queue.pop();assertSame(task,batch.tasks().get(0));assertNull(batch.tasks().get(1));assertSame(task,batch.tasks().get(2));assertEquals(0,nativeQueue.pinnedCount());
        batch.tasks().get(0).run();assertEquals(List.of(1),calls);queue.pop().tasks().get(0).run();assertEquals(List.of(1,2),calls);
        queue.submit(task,1,0);assertThrows(ArrayIndexOutOfBoundsException.class,()->queue.resortChunkTasks(0,new ChunkPos(1),-1));assertEquals(0,nativeQueue.pinnedCount());assertEquals(0,nativeQueue.pending());
        queue.resortChunkTasks(ChunkTaskPriorityQueue.PRIORITY_LEVEL_COUNT,null,-1);
        assertThrows(ArrayIndexOutOfBoundsException.class,()->queue.resortChunkTasks(-1,null,0));
        assertThrows(NullPointerException.class,()->queue.resortChunkTasks(0,null,0));
        assertThrows(ArrayIndexOutOfBoundsException.class,()->queue.submit(task,0,-1));assertEquals(0,nativeQueue.pinnedCount());
    }
    @Test void repeatedGrowthReleaseAndSlotReuseRetainOnlyPendingCallableReferences() throws Exception {
        var queue=new NativeChunkTasks(4);Runnable task=()->{};
        var field=NativeChunkTasks.class.getDeclaredField("references");field.setAccessible(true);
        for(int round=0;round<80;round++) {
            for(int i=0;i<2048;i++)queue.submit(task,i%16,i%4);
            assertEquals(2048,queue.pending());assertEquals(2048,queue.pinnedCount());assertEquals(2048,queue.referenceCapacity());
            for(int i=0;i<8;i++)queue.release(i,true);
            while(queue.hasWork())queue.pop();
            assertEquals(0,queue.pending());assertEquals(0,queue.pinnedCount());assertEquals(2048,queue.referenceCapacity());
            for(var reference:(Object[])field.get(queue))assertNull(reference);
        }
        // Large batches exercise full-width metadata and retire every callable row.
        for (int i=0;i<65536;i++) queue.submit(task,0,0);
        assertEquals(65536,queue.pending());
        assertEquals(65536,queue.pop().tasks().size());
        assertEquals(0,queue.pending());assertEquals(0,queue.pinnedCount());
    }
    @Test void consecutiveDispatcherMutationsAndConcurrentAtomicReadersPreserveAllTasks() throws Exception {
        var queue=new NativeChunkTasks(4);var stop=new AtomicBoolean();var pool=Executors.newFixedThreadPool(5);
        var dispatcher=new net.minecraft.util.thread.ConsecutiveExecutor(pool,"native-chunk-test");
        try {
            var observer=pool.submit(()->{while(!stop.get()){int top=queue.top();assertTrue(top>=0&&top<=4);assertTrue(queue.pending()>=0);}});
            var workers=new ArrayList<Future<?>>();Runnable task=()->{};
            for(int worker=0;worker<4;worker++){int id=worker;workers.add(pool.submit(()->{for(int i=0;i<2000;i++){int priority=i%4;dispatcher.schedule(()->queue.submit(task,id,priority));}}));}
            for(var worker:workers)worker.get(30,TimeUnit.SECONDS);
            dispatcher.scheduleWithResult(future->future.complete(null)).get(30,TimeUnit.SECONDS);
            assertEquals(8000,queue.pending());int count=0;while(queue.hasWork())count+=queue.pop().tasks().size();assertEquals(8000,count);assertEquals(0,queue.pinnedCount());
            stop.set(true);observer.get(30,TimeUnit.SECONDS);
        } finally {stop.set(true);dispatcher.close();pool.shutdownNow();assertTrue(pool.awaitTermination(30,TimeUnit.SECONDS));}
    }
}
