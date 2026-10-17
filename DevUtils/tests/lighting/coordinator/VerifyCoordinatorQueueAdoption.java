package net.minecraft.world.level.lighting;

import java.lang.foreign.*;
import java.lang.invoke.*;
import java.lang.ref.*;
import net.minecraft.world.level.chunk.DataLayer;

/** Actual reservation handoff, with modeled errors around the native call. */
public final class VerifyCoordinatorQueueAdoption {
    static final Error MARKER=new AssertionError("modeled queue handoff failure");
    static MethodHandle delegate;
    static Object field(Object owner,String name)throws Exception {
        var f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);
    }
    static int failBefore(MemorySegment owner,long key,MemorySegment reservation,int index,MemorySegment layer,long identity,MemorySegment out) {
        throw MARKER;
    }
    static int failAfter(MemorySegment owner,long key,MemorySegment reservation,int index,MemorySegment layer,long identity,MemorySegment out)throws Throwable {
        int status=(int)delegate.invokeExact(owner,key,reservation,index,layer,identity,out);
        if(status!=0)throw new AssertionError("Actual queue rejected control: "+status);
        throw MARKER;
    }
    static WeakReference<?> exercise()throws Throwable {
        var storage=new BlockLightSectionStorage(null);var coordinator=storage.coordinator;
        var map=storage.updatingSectionData.nativeMap;
        var f=NativeLightCoordinator.class.getDeclaredField("QUEUE");f.setAccessible(true);delegate=(MethodHandle)f.get(null);
        var owner=(MemorySegment)field(coordinator,"owner");
        var pins=field(map,"pins");var identities=(java.util.Map<?,?>)field(pins,"identities");int before=identities.size();
        var type=MethodType.methodType(int.class,MemorySegment.class,long.class,MemorySegment.class,int.class,MemorySegment.class,long.class,MemorySegment.class);
        var input=new DataLayer(7);
        try(Arena arena=Arena.ofConfined()) {
            var out=arena.allocate(3L*Long.BYTES,Long.BYTES);
            var rejected=MethodHandles.lookup().findStatic(VerifyCoordinatorQueueAdoption.class,"failBefore",type);
            try {map.queueForCoordinator(owner,rejected,7,input,out);throw new AssertionError("Missing pre-transfer error");}
            catch(Error error){if(error!=MARKER)throw error;}
            if(out.getAtIndex(ValueLayout.JAVA_LONG,2)!=0||identities.size()!=before||!storage.queuedSections.isEmpty())throw new AssertionError("Rejected reservation retained ownership");
            if(map.queueForCoordinator(MemorySegment.NULL,delegate,7,input,out)>=0||identities.size()!=before)throw new AssertionError("Native rejection did not roll back reservation");
            var committed=MethodHandles.lookup().findStatic(VerifyCoordinatorQueueAdoption.class,"failAfter",type);
            try {map.queueForCoordinator(owner,committed,7,input,out);throw new AssertionError("Missing post-transfer error");}
            catch(Error error){if(error!=MARKER)throw error;}
            if(out.getAtIndex(ValueLayout.JAVA_LONG,2)!=1||storage.queuedSections.get(7)!=input||!coordinator.inconsistent())throw new AssertionError("Committed reservation was rolled back");
            if(map.queueForCoordinator(owner,delegate,7,null,out)!=0||out.getAtIndex(ValueLayout.JAVA_LONG,2)!=0||!storage.queuedSections.isEmpty()||!coordinator.inconsistent())throw new AssertionError("Null removal changed pending prefix");
            map.drainForCoordinator();
            if(identities.size()!=before)throw new AssertionError("Transferred identity did not retire");
            // Cross a native reservation-batch boundary with ordinary, escaped
            // and subclass payloads; Java identities must remain the originals.
            DataLayer[] many=new DataLayer[70];
            for(int i=0;i<many.length;i++){
                DataLayer value=i%11==0?new DataLayer(i%16){}:new DataLayer(i%16);
                if(i%7==0)value.getData()[0]=(byte)i;
                many[i]=value;
                if(map.queueForCoordinator(owner,delegate,100+i,value,out)!=0||out.getAtIndex(ValueLayout.JAVA_LONG,2)!=1)throw new AssertionError("Batch rollover rejected input");
            }
            for(int i=0;i<many.length;i++)if(storage.queuedSections.get(100+i)!=many[i])throw new AssertionError("Batch rollover changed Java identity");
            Object existing=identities.get(many[0]);long existingId=(long)field(existing,"id");int bound=identities.size();
            for(int i=0;i<70;i++){
                if(map.queueForCoordinator(owner,delegate,1000+i,many[0],out)!=0||storage.queuedSections.get(1000+i)!=many[0])throw new AssertionError("Aliased rollover changed payload");
                if(identities.size()!=bound||(long)field(identities.get(many[0]),"id")!=existingId)throw new AssertionError("Prepared identity replaced an existing binding");
            }
            storage.queuedSections.clear();
            // Retirement is deliberately bounded per call. The expanded alias
            // fixture can leave more than one batch after clear's own drain.
            MemorySegment pending=(MemorySegment)field(pins,"pending");
            for(;;){
                int remaining=(int)ValueLayout.JAVA_INT.varHandle().getAcquire(pending,0L);
                if(remaining==0)break;
                map.drainForCoordinator();
                if((int)ValueLayout.JAVA_INT.varHandle().getAcquire(pending,0L)>=remaining)throw new AssertionError("Retirement made no progress");
            }
            if(identities.size()!=before)throw new AssertionError("Rollover identities did not retire");
            Reference.reachabilityFence(many);
        }
        Reference.reachabilityFence(input);Reference.reachabilityFence(storage);return new WeakReference<>(coordinator);
    }
    public static void main(String[] args)throws Throwable {
        long before=NativeLightCoordinator.liveForVerification();var owner=exercise();
        for(int i=0;i<200&&(owner.get()!=null||NativeLightCoordinator.liveForVerification()!=before);i++){System.gc();Thread.sleep(10);}
        if(owner.get()!=null||NativeLightCoordinator.liveForVerification()!=before)throw new AssertionError("Queue handoff retained storage");
        System.out.println("PASS actual queue handoff: pre-transfer/rejection rollback, post-transfer error receipt, original marker, null-removal pending prefix, 70-entry rollover/custom/escaped and repeated-alias identity, retirement and storage lifetime; modeled errors, no real heap exhaustion");
    }
}
