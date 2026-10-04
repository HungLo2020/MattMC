package net.minecraft.world.level.lighting;

import java.lang.foreign.*;
import java.nio.file.*;

final class LightingQueueAffinity {
    /** Pin the mutator separately from GC/compiler workers, equally in every mode. */
    static void pin() throws Throwable {
        int cpu=Integer.getInteger("mattmc.lightingQueue.cpu",-1);
        if(cpu<0)return;
        String background=System.getProperty("mattmc.lightingQueue.backgroundCpus");
        var libc=SymbolLookup.libraryLookup("libc.so.6",Arena.global());
        var linker=Linker.nativeLinker();
        var tidCall=linker.downcallHandle(libc.find("gettid").orElseThrow(),FunctionDescriptor.of(ValueLayout.JAVA_INT));
        var affinity=linker.downcallHandle(libc.find("sched_setaffinity").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.JAVA_INT,ValueLayout.JAVA_LONG,ValueLayout.ADDRESS));
        int current=(int)tidCall.invokeExact();int workers=0;
        try(var arena=Arena.ofConfined()) {
            var mask=arena.allocate(128,8);
            for(var value:background.split(",")) {
                int c=Integer.parseInt(value);long at=c/8;
                mask.set(ValueLayout.JAVA_BYTE,at,(byte)(mask.get(ValueLayout.JAVA_BYTE,at)|(1<<(c%8))));
            }
            try(var tasks=Files.list(Path.of("/proc/self/task"))) {
                for(var task:tasks.toList()) {
                    int tid=Integer.parseInt(task.getFileName().toString());
                    if(tid==current)continue;
                    int status=(int)affinity.invokeExact(tid,128L,mask);
                    if(status!=0 && Files.exists(task))throw new IllegalStateException("Cannot pin JVM worker "+tid);
                    workers++;
                }
            }
            mask.fill((byte)0);mask.set(ValueLayout.JAVA_BYTE,cpu/8,(byte)(1<<(cpu%8)));
            if((int)affinity.invokeExact(0,128L,mask)!=0)throw new IllegalStateException("Cannot pin benchmark mutator");
        }
        System.out.println("LIGHT_QUEUE_AFFINITY main="+cpu+" background="+background+" workers="+workers);
    }

}
