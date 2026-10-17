package net.minecraft.world.level.chunk;
import java.lang.foreign.*;
import java.lang.invoke.*;
import java.lang.ref.WeakReference;
import java.lang.reflect.*;
import java.lang.management.ManagementFactory;
import java.util.*;
import net.minecraft.util.NativeLibraryLoader;

/** Actual region copy/projection and rollback snapshots, with scoped physical source retirement. */
public final class SkyConsumerScreen {
    static MethodHandle copy;
    static MethodHandle probe,owners,drop;
    static volatile long sink;
    record Sample(long sum,long elapsed,long cpu,long bytes,long jit,long gc,List<Long> probes,List<WeakReference<?>> weak,long[] independent){}
    static Object field(Object value,Class<?> type,String name)throws Exception{var f=type.getDeclaredField(name);f.setAccessible(true);return f.get(value);}
    static void init()throws Exception{
        NativeDecorationSkyTransportTest.load();
        var method=NativeDecorationRegion.class.getDeclaredMethod("copyWritableState");method.setAccessible(true);copy=MethodHandles.lookup().unreflect(method);
        try{
            Class.forName("net.minecraft.world.level.lighting.NativeSkySourceOwner");
            probe=NativeLibraryLoader.downcallHandle("mattmc_rust","mattmc_sky_source_probe_new",FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG));
            owners=NativeLibraryLoader.downcallHandle("mattmc_rust","mattmc_sky_source_probe_owners",FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG));
            drop=NativeLibraryLoader.downcallHandle("mattmc_rust","mattmc_sky_source_probe_release",FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
        }catch(ClassNotFoundException original){}
    }
    static long gc(){return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(b->Math.max(0,b.getCollectionCount())).sum();}
    static Sample sample(int kind,int count)throws Throwable{
        var region=NativeDecorationSkyTransportTest.region();var chunks=(ProtoChunk[])NativeDecorationSkyTransportTest.field(region,"chunks");var source=chunks[0].getSkyLightSources();
        var p=new ArrayList<Long>();var weak=new ArrayList<WeakReference<?>>();
        if(probe!=null)for(var chunk:chunks){
            var actual=chunk.getSkyLightSources();var f=actual.getClass().getDeclaredField("nativeOwner");f.setAccessible(true);Object owner=f.get(actual);long id=(long)field(owner,owner.getClass(),"id");
            long watch=(long)probe.invokeExact(id);p.add(watch);weak.add(new WeakReference<>(actual));weak.add(new WeakReference<>(owner));weak.add(new WeakReference<>(field(owner,owner.getClass(),"owner")));
        }
        long[] initial=new long[26];initial[0]=7;
        try{source.getClass().getMethod("nativeRestore",long[].class).invoke(source,(Object)initial);}
        catch(NoSuchMethodException original){System.arraycopy(initial,0,source.nativeRaw(),0,initial.length);}
        long[] alias=kind==1?source.nativeRaw():null;
        long sky=(long)NativeDecorationSkyTransportTest.call(region,"outSky");var out=region.out();
        var thread=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();var compiler=ManagementFactory.getCompilationMXBean();long tid=Thread.currentThread().threadId(),jit=compiler.getTotalCompilationTime(),collections=gc(),allocated=thread.getThreadAllocatedBytes(tid),cpu=thread.getCurrentThreadCpuTime(),start=System.nanoTime(),sum=0;
        try{
            for(int i=1;i<=count;i++){
                if(kind==0){
                    copy.invokeExact(region);sum=Long.rotateLeft(sum,7)^out.getAtIndex(ValueLayout.JAVA_LONG,sky+2);
                    out.setAtIndex(ValueLayout.JAVA_LONG,sky+2,i&63);out.setAtIndex(ValueLayout.JAVA_LONG,sky+1,1);region.afterPlacement();
                }else{
                    alias[0]=i&63;var maps=region.mapsForVerification();alias[0]=999;region.restoreMapsForVerification(maps);sum=Long.rotateLeft(sum,7)^alias[0];
                }
            }
            long elapsed=System.nanoTime()-start;cpu=thread.getCurrentThreadCpuTime()-cpu;allocated=thread.getThreadAllocatedBytes(tid)-allocated;long jd=compiler.getTotalCompilationTime()-jit,gd=gc()-collections;
            long[] independent=region.mapsForVerification().sky()[0];if(independent[0]!=(count&63))throw new AssertionError("real source final value not projected/restored");sink=sum;
            return new Sample(sum,elapsed,cpu,allocated,jd,gd,p,weak,independent);
        }finally{region.close();}
    }
    static void retire(Sample sample)throws Throwable{
        long deadline=System.nanoTime()+5_000_000_000L;
        try{
            while(true){
                boolean done=sample.weak.stream().allMatch(value->value.refersTo(null));for(long p:sample.probes)done&=(long)owners.invokeExact(p)==0;
                if(done)break;if(System.nanoTime()>deadline)throw new AssertionError("actual region source stores/facades/anchors did not retire");System.gc();Thread.sleep(10);
            }
            if(sample.independent[0]!=2048%64&&sample.independent[0]!=8%64)throw new AssertionError("independent snapshot changed after source retirement");
        }finally{for(long p:sample.probes)drop.invokeExact(p);}
    }
    public static void main(String[] args)throws Throwable{
        init();boolean smoke=Arrays.asList(args).contains("--smoke");int count=smoke?8:2048,target=smoke?1:4,warm=smoke?1:12;
        var thread=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();thread.setThreadAllocatedMemoryEnabled(true);if(!thread.isThreadCpuTimeEnabled())thread.setThreadCpuTimeEnabled(true);
        for(Class<?> c:new Class<?>[]{NativeDecorationRegion.class,DecorationTransaction.class,net.minecraft.world.level.lighting.ChunkSkyLightSources.class}){
            var url=c.getResource(c.getSimpleName()+".class");try(var input=url.openStream()){System.out.println("SKY_CONSUMER_ORIGIN "+c.getName()+" "+url+" "+HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(input.readAllBytes())));}
        }
        for(int kind=0;kind<2;kind++){
            int accepted=0;
            for(int batch=0;batch<70&&accepted<target;batch++){
                System.gc();Thread.sleep(20);Sample value=sample(kind,count);boolean quiet=batch>=warm&&value.jit==0&&value.gc==0;if(quiet)accepted++;
                System.out.println("SKY_CONSUMER_SCREEN {\"kind\":"+kind+",\"batch\":"+batch+",\"accepted\":"+quiet+",\"count\":"+count+",\"elapsed_ns\":"+value.elapsed+",\"cpu_ns\":"+value.cpu+",\"java_bytes\":"+value.bytes+",\"jit_ms\":"+value.jit+",\"gc\":"+value.gc+",\"sum\":"+value.sum+"}");
                retire(value);System.out.println("SKY_CONSUMER_RETIREMENT "+kind+" sources="+value.probes.size()+" allActualOwners=0 snapshot="+value.independent[0]);
            }
            if(accepted!=target)throw new AssertionError("insufficient quiet samples");
        }
        NativeDecorationSkyTransportTest.closeResources();Thread.sleep(250);
    }
}
