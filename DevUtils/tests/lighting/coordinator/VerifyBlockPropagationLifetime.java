package net.minecraft.world.level.lighting;

import java.lang.foreign.*;
import java.lang.invoke.*;
import java.lang.ref.*;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.*;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.world.level.chunk.DataLayer;

/** Actual queue facade/arena GC while its production Rust block pass is suspended. */
public final class VerifyBlockPropagationLifetime {
    static MethodHandle handle(String name,FunctionDescriptor type){return NativeLibraryLoader.downcallHandle("mattmc_rust",name,type);}
    static final MethodHandle RUN=handle("mattmc_light_block_run_effects",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.ADDRESS));
    static final MethodHandle PROBE=handle("mattmc_light_block_work_probe_new",FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.ADDRESS));
    static final MethodHandle OWNERS=handle("mattmc_light_block_work_probe_owners",FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG));
    static final MethodHandle DROP=handle("mattmc_light_block_work_probe_release",FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
    static final MethodHandle ENQUEUE=handle("mattmc_light_work_enqueue",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.JAVA_INT,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG));
    static final long[] POSITIONS={BlockPos.asLong(8,8,8),BlockPos.asLong(10,8,10),BlockPos.asLong(12,8,12)};
    static final long ENTRY=LightEngine.QueueEntry.increaseLightFromEmission(1,true);
    static final Exception MARKER=new Exception("original block propagation lifetime effect");
    static final MemorySegment LEAF;
    static NativeLightQueues retainedBase;
    static Invocation current;
    static{
        try{
            var method=MethodHandles.lookup().findStatic(VerifyBlockPropagationLifetime.class,"leaf",MethodType.methodType(long.class,int.class,long.class,long.class,long.class));
            LEAF=Linker.nativeLinker().upcallStub(method,FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.JAVA_INT,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG),Arena.global());
        }catch(ReflectiveOperationException failure){throw new ExceptionInInitializerError(failure);}
    }
    record Capture(long address,long probe,List<WeakReference<Object>> weak){}
    static final class Invocation {
        final Capture capture;final boolean abandon,heldControl;
        final DataLayer layer=new DataLayer();final List<Long> sourceReads=new ArrayList<>();
        boolean first=true,controlAlive,collected;Throwable failure;
        Invocation(Capture capture,boolean abandon,boolean heldControl){this.capture=capture;this.abandon=abandon;this.heldControl=heldControl;}
    }
    static Object field(Object object,String name)throws Exception{var field=object.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(object);}
    static Capture capture()throws Throwable {
        var queue=new NativeLightQueues();queue.enqueue(1,POSITIONS[0],ENTRY);queue.enqueue(1,POSITIONS[1],ENTRY);
        var owner=queue.ownerForNativeCall();long probe=(long)PROBE.invokeExact(owner);
        if(probe==0||(long)OWNERS.invokeExact(probe)!=1)throw new AssertionError("Actual base queue owner required");
        retainedBase=queue;
        return new Capture(owner.address(),probe,List.of(new WeakReference<>(queue),new WeakReference<>(owner),
            new WeakReference<>(field(queue,"lifetime")),new WeakReference<>(field(queue,"counts")),new WeakReference<>(field(queue,"popped"))));
    }
    static boolean collected(Capture capture){return capture.weak().stream().allMatch(weak->weak.get()==null);}
    static long owners(Capture capture)throws Throwable{return(long)OWNERS.invokeExact(capture.probe());}
    static void firstRead(Invocation call)throws Throwable {
        call.first=false;
        if(owners(call.capture)!=2)throw new AssertionError("Actual base and production kernel must both own queue");
        if(call.heldControl){
            for(int i=0;i<3;i++){System.gc();Thread.sleep(20);}
            call.controlAlive=!collected(call.capture)&&owners(call.capture)==2;
            if(!call.controlAlive)throw new AssertionError("Caller retained-base control collected");
        }
        if(!Boolean.getBoolean("blockPropagation.keepBase"))retainedBase=null;
        long deadline=System.nanoTime()+5_000_000_000L;
        while((!collected(call.capture)||owners(call.capture)!=1)&&System.nanoTime()<deadline){System.gc();Thread.sleep(10);}
        call.collected=collected(call.capture)&&owners(call.capture)==1;
        if(!call.collected)throw new AssertionError("Actual VM queue/segments/arena did not collect and leave exactly the kernel owner");
        if(call.abandon)throw MARKER;
        // The suspended consumer must see a real addition to its actual queue,
        // after its VM base and arena have retired, not a captured input vector.
        int status=(int)ENQUEUE.invokeExact(MemorySegment.ofAddress(call.capture.address()),1,POSITIONS[2],ENTRY);
        if(status!=0)throw new AssertionError("Actual callback producer failed");
    }
    static long leaf(int action,long a,long b,long c){
        var call=current;
        try{
            return switch(action){
                case 0->{if(call.first)firstRead(call);if(a==POSITIONS[0]||a==POSITIONS[1]||a==POSITIONS[2])call.sourceReads.add(a);yield call.layer.get(BlockPos.getX(a)&15,BlockPos.getY(a)&15,BlockPos.getZ(a)&15);}
                case 1->{call.layer.set(BlockPos.getX(a)&15,BlockPos.getY(a)&15,BlockPos.getZ(a)&15,(int)b);yield 0;}
                case 2->a==SectionPos.asLong(0,0,0)?1:0;
                case 8->0;
                default->throw new AssertionError("Level-one actual input must not request state or shape leaf: "+action);
            };
        }catch(Throwable failure){call.failure=failure;return Long.MIN_VALUE;}
    }
    static void run(boolean abandon,boolean heldControl)throws Throwable {
        var capture=capture();var call=new Invocation(capture,abandon,heldControl);current=call;
        try(Arena arena=Arena.ofConfined()){
            var output=arena.allocate(ValueLayout.JAVA_LONG);
            // This raw address has no VM arena anchor. retainedBase keeps the
            // real facade alive until the first leaf proves the kernel capture.
            int status=(int)RUN.invokeExact(MemorySegment.ofAddress(capture.address()),LEAF,output);
            if(call.failure!=null&&(!abandon||call.failure!=MARKER))throw call.failure;
            if(!call.collected||call.first||owners(capture)!=0)throw new AssertionError("Actual queue crossed final consumer/failure release");
            if(abandon){
                if(status!=-2||call.failure!=MARKER||!call.sourceReads.isEmpty())throw new AssertionError("Original first failed effect was replayed or consumed later work");
            }else{
                if(status!=0||output.get(ValueLayout.JAVA_LONG,0)!=3||!call.sourceReads.equals(List.of(POSITIONS[0],POSITIONS[1],POSITIONS[2])))throw new AssertionError("Production consumer lost actual live queue payload/FIFO addition");
                for(long pos:POSITIONS)if(call.layer.get(BlockPos.getX(pos)&15,BlockPos.getY(pos)&15,BlockPos.getZ(pos)&15)!=1)throw new AssertionError("Actual light payload missing");
            }
            System.out.println("BLOCK_PROPAGATION_LIFETIME abandon="+abandon+" caller-control="+heldControl+" control-alive="+call.controlAlive+" VMcollected=true live-addition="+!abandon+" finalNativeOwners=0 same-marker="+(call.failure==MARKER));
        }finally{
            retainedBase=null;current=null;
            long deadline=System.nanoTime()+5_000_000_000L;
            while(owners(capture)!=0&&System.nanoTime()<deadline){System.gc();Thread.sleep(10);}
            try{if(owners(capture)!=0)throw new AssertionError("Actual base cleanup failed after control");}finally{DROP.invokeExact(capture.probe());}
            Reference.reachabilityFence(call.layer);
        }
    }
    public static void main(String[] args)throws Throwable{
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
        for(boolean abandon:new boolean[]{false,true})for(boolean heldControl:new boolean[]{false,true})run(abandon,heldControl);
    }
}
