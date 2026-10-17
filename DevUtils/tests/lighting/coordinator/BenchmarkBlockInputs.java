package net.minecraft.server.level;

import java.io.*;
import java.lang.management.*;
import java.nio.charset.StandardCharsets;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

/** Complete 64-edit public lighting cycles, with untimed full-state observations. */
public final class BenchmarkBlockInputs {
    static final CompilationMXBean COMPILATION=ManagementFactory.getCompilationMXBean();
    static final com.sun.management.ThreadMXBean MEMORY=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
    static final java.util.List<GarbageCollectorMXBean> COLLECTORS=ManagementFactory.getGarbageCollectorMXBeans();
    static long[] collections(){long count=0,ms=0;for(var gc:COLLECTORS){count+=Math.max(0,gc.getCollectionCount());ms+=Math.max(0,gc.getCollectionTime());}return new long[]{count,ms};}
    static boolean sample(VerifyThreadedLighting.Fixture fixture,int steps,String prefix,int index)throws Exception {
        BenchmarkThreadedLighting.checksum=0;
        long thread=Thread.currentThread().threadId(),compiled=COMPILATION.getTotalCompilationTime(),bytes=MEMORY.getThreadAllocatedBytes(thread);
        long dispatch=fixture.dispatch.executed,light=fixture.light.executed;long[] gc=collections();
        long began=System.nanoTime();for(int i=0;i<steps;i++)BenchmarkThreadedLighting.cycle(fixture,64,i);long ns=System.nanoTime()-began;
        bytes=MEMORY.getThreadAllocatedBytes(thread)-bytes;long jit=COMPILATION.getTotalCompilationTime()-compiled;long[] after=collections();
        long checksum=BenchmarkThreadedLighting.observe(fixture);
        var text=new StringWriter();VerifyThreadedLighting.trace=new PrintWriter(text);
        try{fixture.snapshot("untimed complete block-input cycle");}finally{VerifyThreadedLighting.trace.close();VerifyThreadedLighting.trace=null;}
        String state=VerifyThreadedLighting.hash(text.toString().getBytes(StandardCharsets.UTF_8));
        System.out.println(prefix+" "+index+" "+ns+" "+bytes+" "+jit+" "+(after[0]-gc[0])+" "+(after[1]-gc[1])+" "+checksum+" "+(fixture.dispatch.executed-dispatch)+" "+(fixture.light.executed-light)+" "+state);
        String status=java.nio.file.Files.readString(java.nio.file.Path.of("/proc/self/status"));
        System.out.println("RESOURCE "+prefix+" "+index+" "+status.lines().filter(line->line.startsWith("VmRSS:")||line.startsWith("VmSwap:")).reduce("",(a,b)->a+" "+b));
        return jit==0&&after[0]==gc[0]&&after[1]==gc[1];
    }
    public static void main(String[] args)throws Exception {
        VerifyThreadedLighting.pinClasses();SharedConstants.tryDetectVersion();Bootstrap.bootStrap();MEMORY.setThreadAllocatedMemoryEnabled(true);
        int steps=Integer.parseInt(args[0]),samples=Integer.parseInt(args[1]);
        try(var fixture=new VerifyThreadedLighting.Fixture(false,true)){
            fixture.initialize();fixture.assertReady();int quiet=0,warm=0;
            while(warm<32||quiet<12){if(warm>=512)throw new AssertionError("Compiler/GC did not settle");boolean clean=sample(fixture,steps,"WARM_BLOCK_INPUT_SAMPLE",warm++);quiet=clean?quiet+1:0;}
            for(int i=0;i<samples;i++)sample(fixture,steps,"BLOCK_INPUT_SAMPLE",i);
        }
    }
}
