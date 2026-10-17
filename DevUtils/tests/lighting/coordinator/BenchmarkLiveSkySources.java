package net.minecraft.world.level.lighting;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.SectionPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunkGetter;

/** Times the actual contextual source producer; queue drain and pixel checks are untimed. */
public final class BenchmarkLiveSkySources {
    static volatile long sink;
    static final class Sources extends ChunkSkyLightSources {
        final int height;
        long reads;
        Sources(int height) {super(LevelHeightAccessor.create(0,64));this.height=height;}
        @Override public int getLowestSourceY(int x,int z) {reads++;return height;}
    }
    static final class Layer extends DataLayer {
        long writes;
        Layer() {super(0);}
        @Override public DataLayer copy() {return this;}
        @Override public void set(int x,int y,int z,int value) {super.set(x,y,z,value);writes++;}
    }
    final Object fixture;
    final SkyLightEngine engine;
    final Layer layer=new Layer();
    final Sources[] sources=new Sources[5];
    final Object queue;
    final Method size,dequeue;
    final boolean nativeQueue,write;
    final ChunkPos position=new ChunkPos(0,0);
    final com.sun.management.ThreadMXBean memory=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
    final long thread=Thread.currentThread().threadId();
    long queueHash;

    @SuppressWarnings("unchecked")
    BenchmarkLiveSkySources(boolean write) throws Exception {
        this.write=write;memory.setThreadAllocatedMemoryEnabled(true);
        Class<?> type=Class.forName(VerifyLightCoordinator.class.getName()+"$Fixture");
        Constructor<?> constructor=type.getDeclaredConstructor(boolean.class);constructor.setAccessible(true);
        fixture=constructor.newInstance(true);Object world=VerifyLiveSkySourceContracts.field(fixture,"world");
        var chunks=(Map<Long,ChunkAccess>)VerifyLiveSkySourceContracts.field(world,"chunks");
        Field source=ChunkAccess.class.getDeclaredField("skyLightSources");source.setAccessible(true);
        int[][] positions={{0,0},{0,-1},{0,1},{-1,0},{1,0}};
        for(int i=0;i<5;i++) {
            sources[i]=new Sources(write?48:64);
            source.set(chunks.get(ChunkPos.asLong(positions[i][0],positions[i][1])),sources[i]);
        }
        var storage=new SkyLightSectionStorage((LightChunkGetter)world) {
            @Override protected int getTopSectionY(long column) {return 4;}
            @Override protected int getBottomSectionY() {return 3;}
        };
        storage.updatingSectionData.setLayer(SectionPos.asLong(0,3,0),layer);
        storage.updatingSectionData.clearCache();engine=new SkyLightEngine((LightChunkGetter)world,storage);
        Object actual;boolean nativeOwner;
        try {actual=VerifyLiveSkySourceContracts.field(engine,"lightWork");nativeOwner=true;}
        catch(NoSuchFieldException frozen) {actual=VerifyLiveSkySourceContracts.field(engine,"increaseQueue");nativeOwner=false;}
        queue=actual;nativeQueue=nativeOwner;
        size=queue.getClass().getDeclaredMethod("size",nativeQueue?new Class<?>[]{int.class}:new Class<?>[0]);size.setAccessible(true);
        dequeue=queue.getClass().getDeclaredMethod(nativeQueue?"dequeue":"dequeueLong",nativeQueue?new Class<?>[]{int.class}:new Class<?>[0]);dequeue.setAccessible(true);
        System.out.println("FIXTURE custom-storage=true custom-sources=5 native-queue="+nativeQueue+" write="+write);
    }
    long[] sourceCycle() throws Exception {
        long reads=0;for(var source:sources)reads+=source.reads;
        long writes=layer.writes,bytes=memory.getThreadAllocatedBytes(thread),start=System.nanoTime();
        engine.propagateLightSources(position);
        long ns=System.nanoTime()-start;bytes=memory.getThreadAllocatedBytes(thread)-bytes;
        long after=0;for(var source:sources)after+=source.reads;
        if(after-reads!=(write?1280:256)||layer.writes-writes!=(write?4096:0))throw new AssertionError("Source work changed");
        int count=((Number)size.invoke(queue,nativeQueue?new Object[]{1}:new Object[0])).intValue();
        if(count!=(write?512:0))throw new AssertionError("Source queue changed: "+count);
        long hash=1469598103934665603L;
        for(int i=0;i<count;i++)hash=(hash^((Number)dequeue.invoke(queue,nativeQueue?new Object[]{1}:new Object[0])).longValue())*1099511628211L;
        if(queueHash!=0&&queueHash!=hash)throw new AssertionError("Queue order changed");queueHash=hash;
        long pixels=0;
        for(int z=0;z<16;z++)for(int y=0;y<16;y++)for(int x=0;x<16;x++)pixels+=layer.get(x,y,z);
        if(pixels!=(write?61440:0))throw new AssertionError("Source pixels changed");
        sink=hash^pixels;return new long[]{ns,bytes};
    }
    void sample(int index,String prefix,int steps) throws Exception {
        var compilation=ManagementFactory.getCompilationMXBean();long jit=compilation.getTotalCompilationTime();
        long gc=0;for(var collector:ManagementFactory.getGarbageCollectorMXBeans())gc+=Math.max(0,collector.getCollectionCount());
        long ns=0,bytes=0;
        for(int i=0;i<steps;i++){long[] row=sourceCycle();ns+=row[0];bytes+=row[1];}
        long gcAfter=0;for(var collector:ManagementFactory.getGarbageCollectorMXBeans())gcAfter+=Math.max(0,collector.getCollectionCount());
        System.out.println(prefix+" "+index+" ns="+ns+" bytes="+bytes+" jit="+(compilation.getTotalCompilationTime()-jit)+" gc="+(gcAfter-gc)+" steps="+steps+" hash="+queueHash+" reads="+(write?1280:256)+" writes="+(write?4096:0)+" words="+(write?512:0));
        String status=java.nio.file.Files.readString(java.nio.file.Path.of("/proc/self/status"));
        System.out.println("RESOURCE "+prefix+" "+index+" "+status.lines().filter(s->s.startsWith("VmRSS:")||s.startsWith("VmSwap:")).collect(java.util.stream.Collectors.joining(" ")));
    }
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
        Field output=VerifyLightCoordinator.class.getDeclaredField("out");output.setAccessible(true);
        try(var quiet=new PrintWriter(OutputStream.nullOutputStream())) {
            output.set(null,quiet);var benchmark=new BenchmarkLiveSkySources(args[0].equals("write"));
            var compilation=ManagementFactory.getCompilationMXBean();int warm=0,settled=0,steps=Integer.parseInt(args[1]);
            if(args.length>2&&args[2].equals("smoke")){benchmark.sample(0,"SMOKE",1);return;}
            while(warm<32||settled<12) {
                if(warm==512)throw new AssertionError("Compiler did not settle");
                long before=compilation.getTotalCompilationTime();benchmark.sample(warm++,"WARM",steps);
                settled=before==compilation.getTotalCompilationTime()?settled+1:0;
            }
            for(int i=0;i<12;i++)benchmark.sample(i,"SAMPLE",steps);
            java.lang.ref.Reference.reachabilityFence(benchmark.fixture);
        }
    }
}
