package net.minecraft.server.level;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.ProtoChunk;

/** Actual initialization, source propagation and unload; full state observation is untimed. */
public final class BenchmarkLightRequests {
    static MethodHandle nativeLive;
    static volatile String sink;

    static long live(int which) throws Throwable {
        return nativeLive == null ? 0 : (long)nativeLive.invokeExact(which);
    }

    static long gcCount() {
        return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(bean -> Math.max(0,bean.getCollectionCount())).sum();
    }

    static void cycle(VerifyThreadedLighting.Fixture f, ProtoChunk chunk) throws Exception {
        var initialized=f.engine.initializeLight(chunk,false);f.drain();
        if (!initialized.isDone() || initialized.join()!=chunk) throw new AssertionError("initialization did not complete");
        var lit=f.engine.lightChunk(chunk,false);f.drain();
        if (!lit.isDone() || lit.join()!=chunk || !chunk.isLightCorrect()) throw new AssertionError("light POST did not complete");
        if (!f.engine.lightOnInColumn(SectionPos.getZeroNode(0,0)) ||
            f.engine.getLayerListener(LightLayer.BLOCK).getLightValue(new BlockPos(8,24,8))!=15)
            throw new AssertionError("real emitter/column is not lit");
        f.engine.updateChunkStatus(chunk.getPos());f.drain();
        if (f.engine.lightOnInColumn(SectionPos.getZeroNode(0,0))) throw new AssertionError("unload did not disable column");
    }

    static String observe(VerifyThreadedLighting.Fixture f) throws Exception {
        if (f.pending()!=0 || f.scheduled() || f.engine.hasLightWork()) throw new AssertionError("unfinished lifecycle");
        var text=new StringWriter();VerifyThreadedLighting.trace=new PrintWriter(text);
        try { f.snapshot("request lifecycle"); }
        finally { VerifyThreadedLighting.trace=null; }
        return VerifyThreadedLighting.hash(text.toString().getBytes(StandardCharsets.UTF_8));
    }

    static long sample(VerifyThreadedLighting.Fixture f, ProtoChunk chunk, String prefix, int index, int steps) throws Throwable {
        var compilation=ManagementFactory.getCompilationMXBean();
        var memory=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
        long thread=Thread.currentThread().threadId(), jit=compilation.getTotalCompilationTime(), gc=gcCount();
        long allocated=memory.getThreadAllocatedBytes(thread),dispatch=f.dispatch.executed,light=f.light.executed;
        long start=System.nanoTime();
        for (int i=0;i<steps;i++) cycle(f,chunk);
        long elapsed=System.nanoTime()-start,bytes=memory.getThreadAllocatedBytes(thread)-allocated;
        long measuredJit=compilation.getTotalCompilationTime()-jit,measuredGc=gcCount()-gc;
        sink=observe(f);
        System.out.println(prefix+" "+index+" "+elapsed+" "+bytes+" "+measuredJit+" "+measuredGc+" "+sink+" "+(f.dispatch.executed-dispatch)+" "+(f.light.executed-light));
        String status=Files.readString(Path.of("/proc/self/status"));
        String rss=status.lines().filter(line->line.startsWith("VmRSS:")).findFirst().orElse("VmRSS: unavailable");
        String swap=status.lines().filter(line->line.startsWith("VmSwap:")).findFirst().orElse("VmSwap: unavailable");
        System.out.println("RESOURCE "+prefix+" "+index+" native_requests_observed="+(nativeLive!=null)+" request_owners="+live(0)+" request_frames="+live(1)+" "+rss+" "+swap);
        if (live(1)!=0) throw new AssertionError("completed sample retains a native frame");
        return compilation.getTotalCompilationTime()-jit;
    }

    public static void main(String[] args) throws Throwable {
        VerifyThreadedLighting.pinClasses();SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
        if (Boolean.getBoolean("threadedLight.nativeRequests")) {
            var loader=Class.forName("net.minecraft.util.NativeLibraryLoader");
            nativeLive=(MethodHandle)loader.getMethod("downcallHandle",String.class,String.class,FunctionDescriptor.class)
                .invoke(null,"mattmc_rust","mattmc_light_request_live",FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.JAVA_INT));
        }
        boolean verifyOnly=args[0].equals("verify");
        int steps=verifyOnly?0:Integer.parseInt(args[0]),samples=verifyOnly?0:Integer.parseInt(args[1]);
        ((com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean()).setThreadAllocatedMemoryEnabled(true);
        long owners=live(0),frames=live(1);
        try (var f=new VerifyThreadedLighting.Fixture(false,true)) {
            f.initialize();f.assertReady();
            var chunk=f.world.chunks.get(ChunkPos.asLong(0,0));
            if (verifyOnly) {
                for (int i=0;i<3;i++) {cycle(f,chunk);System.out.println("FIXTURE_CHECK "+i+" "+observe(f));}
                return;
            }
            int quiet=0,warm=0;
            while (warm<32 || quiet<12) {
                if (warm>=512) throw new AssertionError("lifecycle compiler did not settle");
                quiet=sample(f,chunk,"WARM_SAMPLE",warm++,steps)==0?quiet+1:0;
            }
            for (int index=0;index<samples;index++) sample(f,chunk,"SAMPLE",index,steps);
            for (int i=0;i<200 && (live(0)>owners || live(1)>frames);i++) {System.gc();Thread.sleep(10);}
            if (live(0)>owners || live(1)>frames) throw new AssertionError("native requests retained after completed workload");
            System.out.println("RELEASE request_owner_delta="+(live(0)-owners)+" request_frame_delta="+(live(1)-frames));
            java.lang.ref.Reference.reachabilityFence(f.engine);
        }
    }
}
