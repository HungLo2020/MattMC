package net.minecraft.server.level;

import java.lang.management.ManagementFactory;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.LightLayer;

/** Actual public producers, native/Java dispatcher, coordinator and propagation.
 * Trace formatting, reflection, state capture and metrics are outside timing. */
public final class BenchmarkThreadedLighting {
    static volatile long sink;
    static long checksum;
    static long sample(VerifyThreadedLighting.Fixture f,int count,int steps,int index,String prefix,
                       java.lang.management.CompilationMXBean compilation,com.sun.management.ThreadMXBean memory,long thread) throws Exception {
        checksum=0;long compiled=compilation.getTotalCompilationTime();long allocated=memory.getThreadAllocatedBytes(thread);
        long dispatch=f.dispatch.executed,light=f.light.executed;
        long began=System.nanoTime();for(int j=0;j<steps;j++)cycle(f,count,j);long elapsed=System.nanoTime()-began;
        long bytes=memory.getThreadAllocatedBytes(thread)-allocated;long jit=compilation.getTotalCompilationTime()-compiled;
        long check=observe(f);f.snapshot("untimed observation");
        long nativeBytes=0,pins=0;
        Object tasks=VerifyThreadedLighting.field(f.engine,"lightTasks");
        if(!(tasks instanceof java.util.Collection<?>)){
            nativeBytes=((Number)VerifyThreadedLighting.invoke(tasks,"nativeAllocationBytes",new Class<?>[0])).longValue();
            pins=((Number)VerifyThreadedLighting.invoke(tasks,"pinnedCount",new Class<?>[0])).longValue();
        }
        var status=java.nio.file.Files.readString(java.nio.file.Path.of("/proc/self/status"));
        String rss=status.lines().filter(line->line.startsWith("VmRSS:")).findFirst().orElse("VmRSS: unavailable");
        String swap=status.lines().filter(line->line.startsWith("VmSwap:")).findFirst().orElse("VmSwap: unavailable");
        System.out.println(prefix+" "+index+" "+elapsed+" "+bytes+" "+jit+" "+check+" "+(f.dispatch.executed-dispatch)+" "+(f.light.executed-light));
        System.out.println("RESOURCE "+prefix+" "+index+" "+nativeBytes+" "+pins+" "+rss+" "+swap);
        return elapsed;
    }
    static void cycle(VerifyThreadedLighting.Fixture f,int count,int phase) {
        for(int i=0;i<Math.min(count,64);i++)f.edit(i,(phase&1)==0);
        for(int i=64;i<count;i++)f.engine.checkBlock(new BlockPos(i%32-16,24,i/32%32-16));
        if(count>0)f.engine.waitForPendingTasks(0,0).thenRun(()->checksum=Long.rotateLeft(checksum,7)^count);
        f.transport();f.engine.tryScheduleUpdate();f.transport();
    }
    static long observe(VerifyThreadedLighting.Fixture f) throws Exception {
        if(f.pending()!=0||f.scheduled()||f.engine.hasLightWork())throw new AssertionError("unfinished producer workload");
        long value=checksum;
        for(int i=0;i<64;i++)value=Long.rotateLeft(value,3)^f.engine.getLayerListener(LightLayer.BLOCK).getLightValue(new BlockPos(i%32-16,24,i/32-1));
        sink=value;return value;
    }
    public static void main(String[] args) throws Exception {
        VerifyThreadedLighting.pinClasses();SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
        int count=Integer.parseInt(args[0]),steps=Integer.parseInt(args[1]),samples=Integer.parseInt(args[2]);
        var compilation=ManagementFactory.getCompilationMXBean();
        var memory=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();memory.setThreadAllocatedMemoryEnabled(true);
        long thread=Thread.currentThread().threadId();
        long start=System.nanoTime();
        try(var f=new VerifyThreadedLighting.Fixture(false,true)) {
            long constructed=System.nanoTime();f.initialize();long first=System.nanoTime();
            System.out.println("COLD "+(constructed-start)+" "+(first-constructed));
            System.out.println("ENGINE_CONSTRUCTION "+f.engineConstructionNs);
            f.assertReady();
            int quiet=0,warm=0;
            while(warm<32||quiet<12) {
                if(warm>=512)throw new AssertionError("compiler did not settle");
                long before=compilation.getTotalCompilationTime();
                sample(f,count,steps,warm,"WARM_SAMPLE",compilation,memory,thread);
                quiet=before==compilation.getTotalCompilationTime()?quiet+1:0;warm++;
            }
            System.out.println("WARM "+warm);
            for(int sample=0;sample<samples;sample++) {
                sample(f,count,steps,sample,"SAMPLE",compilation,memory,thread);
            }
        }
    }
}
