package net.minecraft.world.level.chunk;

import java.lang.foreign.MemorySegment;
import java.lang.ref.Reference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

/** Modeled failure/drainer ordering against the actual registration/claim code.
 * Fake handles are claimed by counters, never passed to native destruction. */
public final class VerifyNativeReclaimerRegistration {
    static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    static Thread claimant(NativeReclaimer.Owner record,AtomicBoolean result) throws Exception {
        var started=new CountDownLatch(1);
        var thread=new Thread(()->{started.countDown();result.set(record.claim());});
        thread.start();started.await();
        long deadline=System.nanoTime()+3_000_000_000L;
        while(thread.getState()!=Thread.State.BLOCKED&&thread.isAlive()&&System.nanoTime()<deadline)Thread.onSpinWait();
        require(thread.getState()==Thread.State.BLOCKED,"claim did not serialize with registration");
        return thread;
    }
    public static void main(String[] args) throws Exception {
        Object referent=new Object();var owner=MemorySegment.ofAddress(0x211);var view=MemorySegment.ofAddress(0x212);
        try {
            int pending=NativeReclaimer.pending();
            for(int stage=0;stage<=4;stage++){
                int failing=stage;long owners=NativeReclaimer.registered((byte)16),views=NativeReclaimer.registered((byte)17);
                Error original=new AssertionError("modeled registration failure at"+stage);
                var claimed=new AtomicBoolean();var thread=new Thread[1];var records=new NativeReclaimer.Owner[2];
                try {
                    NativeReclaimer.registerLightMap(referent,owner,view,(at,first,second)->{
                        records[0]=first;records[1]=second;
                        if(at==failing){
                            try{thread[0]=claimant(first,claimed);}catch(Exception failure){throw new AssertionError(failure);}
                            throw original;
                        }
                    });
                    throw new AssertionError("failure did not escape");
                }catch(Error failure){require(failure==original,"original error replaced");}
                thread[0].join();
                require(!claimed.get()&&!records[0].claim()&&!records[1].claim(),"cancelled lease claimed");
                require(NativeReclaimer.pending()==pending,"partial LIVE registration survived");
                require(NativeReclaimer.registered((byte)16)==owners&&NativeReclaimer.registered((byte)17)==views,"failed registration counted");
            }
            for(boolean pressureFailure:new boolean[]{false,true}){
                long owners=NativeReclaimer.registered((byte)16),views=NativeReclaimer.registered((byte)17);
                var records=new NativeReclaimer.Owner[2];var claimed=new AtomicBoolean();var thread=new Thread[1];
                NativeReclaimer.registerLightMap(referent,owner,view,(at,first,second)->{
                    records[0]=first;records[1]=second;
                    if(at==4)try{thread[0]=claimant(first,claimed);}catch(Exception failure){throw new AssertionError(failure);}
                    if(at==5&&pressureFailure)throw new AssertionError("modeled optional pressure failure");
                });
                thread[0].join();
                require(claimed.get()&&!records[0].claim(),"committed first lease not claimed exactly once");
                require(records[1].claim()&&!records[1].claim(),"committed second lease not claimed exactly once");
                require(NativeReclaimer.pending()==pending,"claimed entries survived");
                require(NativeReclaimer.registered((byte)16)==owners+1&&NativeReclaimer.registered((byte)17)==views+1,"committed pair counter mismatch");
            }
            System.out.println("PASS reclaimer: five modeled registration failure boundaries, serialized drainer cancellation/commit claims, postcommit pressure failure; no partial LIVE entries or double claims; fake handles never freed; no real heap exhaustion");
        }finally{Reference.reachabilityFence(referent);Reference.reachabilityFence(owner);Reference.reachabilityFence(view);}
    }
}
