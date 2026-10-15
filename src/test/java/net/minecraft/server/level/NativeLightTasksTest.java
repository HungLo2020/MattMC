package net.minecraft.server.level;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Adapter lifetime/admission checks supplement the actual Frozen production oracle. */
@Tag("parity")
class NativeLightTasksTest {
    @BeforeAll static void bootstrap(){SharedConstants.tryDetectVersion();Bootstrap.bootStrap();}
    @Test void prePropagationPostOrderAndFailurePrefixesRetainCallableIdentity() {
        var tasks=new NativeLightTasks();var trace=new ArrayList<String>();var once=new AtomicInteger();
        Runnable pre=()->trace.add("pre"),post=()->{trace.add("post");if(once.getAndIncrement()==0)throw new IllegalStateException("post");};
        tasks.append(false,pre);tasks.append(true,post);tasks.append(false,pre);tasks.append(true,()->trace.add("last"));
        assertThrows(IllegalStateException.class,()->tasks.runUpdate(()->trace.add("propagate")));
        assertEquals(List.of("pre","pre","propagate","post"),trace);assertEquals(3,tasks.pending());assertEquals(3,tasks.pinnedCount());
        tasks.runUpdate(()->trace.add("propagate"));assertEquals(List.of("pre","pre","propagate","post","pre","propagate","post","last"),trace);
        assertEquals(0,tasks.pending());assertEquals(0,tasks.pinnedCount());
        tasks.append(false,()->{throw new IllegalArgumentException("pre");});
        assertThrows(IllegalArgumentException.class,()->tasks.runUpdate(()->fail("propagation must not execute")));assertEquals(1,tasks.pending());assertEquals(1,tasks.pinnedCount());
    }
    @Test void nestedPostPassPreservesOriginalOuterRemovalFailureAndReusableRows() {
        var tasks=new NativeLightTasks();var once=new AtomicInteger();var calls=new AtomicInteger();
        tasks.append(true,()->{calls.incrementAndGet();if(once.getAndIncrement()==0)tasks.runUpdate(()->{});});
        var error=assertThrows(IndexOutOfBoundsException.class,()->tasks.runUpdate(()->{}));
        assertEquals("Index (0) is greater than or equal to list size (0)",error.getMessage());assertEquals(2,calls.get());
        assertEquals(0,tasks.pending());assertEquals(0,tasks.pinnedCount());tasks.append(true,calls::incrementAndGet);tasks.runUpdate(()->{});assertEquals(3,calls.get());
    }
    @Test void thresholdAndLiveCursorDoNotDetachOrConsumeBeyondCapturedBatch() {
        var tasks=new NativeLightTasks();var called=new AtomicInteger();
        for(int i=0;i<1001;i++)assertEquals(i>=999,tasks.append(false,called::incrementAndGet));
        tasks.runUpdate(()->{});assertEquals(1000,called.get());assertEquals(1,tasks.pending());
        tasks.runUpdate(()->{});assertEquals(1001,called.get());assertEquals(0,tasks.pinnedCount());
        tasks.append(false,()->tasks.append(false,called::incrementAndGet));tasks.append(true,called::incrementAndGet);
        tasks.runUpdate(()->{});assertEquals(1002,called.get());assertEquals(1,tasks.pending());tasks.runUpdate(()->{});assertEquals(1003,called.get());
    }
    @Test void admissionHasSingleWinnerAndOnlyNormalCompletionResetsIt() throws Exception {
        var tasks=new NativeLightTasks();assertFalse(tasks.requestSchedule(false));assertFalse(tasks.scheduled());
        try(var pool=Executors.newFixedThreadPool(8)) {
            var barrier=new CyclicBarrier(8);var futures=new ArrayList<Future<Boolean>>();
            for(int i=0;i<8;i++)futures.add(pool.submit(()->{barrier.await();return tasks.requestSchedule(true);}));
            int winners=0;for(var f:futures)if(f.get(10,TimeUnit.SECONDS))winners++;assertEquals(1,winners);
        }
        tasks.append(false,()->{throw new IllegalStateException();});assertThrows(IllegalStateException.class,()->tasks.runUpdate(()->{}));
        assertTrue(tasks.scheduled());assertFalse(tasks.requestSchedule(true));tasks.finishSchedule();assertFalse(tasks.scheduled());assertTrue(tasks.requestSchedule(true));
    }
    @Test void repeatedBurstsRetirePinsAndBoundNativeCapacityAtPeakMembership() {
        var tasks=new NativeLightTasks();Runnable callback=()->{};long capacity=0;int references=0;
        for(int round=0;round<40;round++) {
            for(int i=0;i<4096;i++)tasks.append(i%2==0,callback);
            assertEquals(4096,tasks.pending());assertEquals(4096,tasks.pinnedCount());
            while(tasks.pending()!=0)tasks.runUpdate(()->{});
            assertEquals(0,tasks.pinnedCount());
            if(round==0){capacity=tasks.nativeAllocationBytes();references=tasks.referenceCapacity();}
            else {assertEquals(capacity,tasks.nativeAllocationBytes());assertEquals(references,tasks.referenceCapacity());}
        }
    }
    record Queued(WeakReference<NativeLightTasks> owner,WeakReference<Object> payload,Runnable run){}
    static Queued queued() {
        var owner=new NativeLightTasks();Object payload=new Object();owner.append(true,()->assertNotNull(payload));
        return new Queued(new WeakReference<>(owner),new WeakReference<>(payload),()->owner.runUpdate(()->{}));
    }
    @Test void queuedExecutionStronglyRetainsOwnerAndRetiresCallbackPayload() throws Exception {
        var queued=queued();System.gc();assertNotNull(queued.owner.get());assertNotNull(queued.payload.get());queued.run.run();
        assertEquals(0,queued.owner.get().pinnedCount());
        for(int i=0;i<50&&queued.payload.get()!=null;i++){System.gc();Thread.sleep(20);}
        assertNull(queued.payload.get());assertNotNull(queued.owner.get());
    }
    static WeakReference<NativeLightTasks> executeAndDrop(){var queued=queued();queued.run.run();return queued.owner;}
    @Test void unreachableExecutedOwnerReleasesItsNativeAllocation() throws Exception {
        System.gc();long baseline=NativeLightTasks.liveAllocations();
        var owner=executeAndDrop();
        for(int i=0;i<200&&(owner.get()!=null||NativeLightTasks.liveAllocations()>baseline);i++){System.gc();Thread.sleep(10);}
        assertNull(owner.get());assertTrue(NativeLightTasks.liveAllocations()<=baseline);
    }
}
