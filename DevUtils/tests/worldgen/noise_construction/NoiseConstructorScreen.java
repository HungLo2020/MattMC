package net.minecraft.world.level.levelgen.synth;

import java.lang.management.ManagementFactory;
import java.util.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;

/** Short actual public-constructor screen; no copied noise or RNG implementation. */
public final class NoiseConstructorScreen {
    static volatile Object retained;
    static volatile long sink;
    static long gc() { return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(b->Math.max(0,b.getCollectionCount())).sum(); }
    static long[] stats()throws Exception {
        try {
            var type=Class.forName("net.minecraft.world.level.levelgen.synth.NativeNoiseSeed");
            var method=type.getDeclaredMethod("statistics");method.setAccessible(true);return (long[])method.invoke(null);
        }catch(ClassNotFoundException expected){return new long[0];}
    }
    static RandomSource[] sources(int family,int count) {
        var sources=new RandomSource[count];
        for(int i=0;i<count;i++)sources[i]=family==0?RandomSource.create(1234567L+i):new XoroshiroRandomSource(1234567L+i);
        return sources;
    }
    static long work(int kind,RandomSource[] sources,Object[] leaves) {
        long sum=0;
        for(int i=0;i<leaves.length;i++) {
            if(kind==0){var noise=new ImprovedNoise(sources[i]);leaves[i]=noise;sum+=Double.doubleToRawLongBits(noise.xo);sum^=Double.doubleToRawLongBits(noise.yo);sum+=Double.doubleToRawLongBits(noise.zo);}
            else {var noise=new SimplexNoise(sources[i]);leaves[i]=noise;sum+=Double.doubleToRawLongBits(noise.xo);sum^=Double.doubleToRawLongBits(noise.yo);sum+=Double.doubleToRawLongBits(noise.zo);}
            sum^=sources[i].nextLong();
        }
        retained=leaves;sink=sum;return sum;
    }
    static void settle()throws Exception {retained=null;for(int i=0;i<3;i++){System.gc();Thread.sleep(10);}}
    public static void main(String[] args)throws Exception {
        for(Class<?> type:new Class<?>[]{ImprovedNoise.class,SimplexNoise.class,RandomSource.class,XoroshiroRandomSource.class}) {
            var url=type.getResource(type.getSimpleName()+".class");
            try(var input=url.openStream()){System.out.println("NOISE_ORIGIN "+type.getName()+" "+url+" "+HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(input.readAllBytes())));}
        }
        var thread=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
        var compiler=ManagementFactory.getCompilationMXBean();long tid=Thread.currentThread().threadId();
        for(int kind=0;kind<2;kind++)for(int family=0;family<2;family++) {
            int count=Integer.getInteger("mattmc.noise.screen.count", 4096);
            long[] rowStart=stats();int accepted=0;
            for(int batch=0;batch<90&&accepted<6;batch++) {
                settle();var sources=sources(family,count);var leaves=new Object[count];
                long[] before=stats();long jit=compiler.getTotalCompilationTime(),collections=gc();
                long allocated=thread.getThreadAllocatedBytes(tid),cpu=thread.getCurrentThreadCpuTime(),start=System.nanoTime();
                long sum=work(kind,sources,leaves);
                long elapsed=System.nanoTime()-start;cpu=thread.getCurrentThreadCpuTime()-cpu;allocated=thread.getThreadAllocatedBytes(tid)-allocated;
                long jitDelta=compiler.getTotalCompilationTime()-jit,gcDelta=gc()-collections;long[] after=stats();
                if(after.length!=0&&(after[0]!=before[0]||after[1]-before[1]!=count||after[2]-before[2]!=count||after[4]-before[4]!=count))throw new AssertionError("actual native constructor work/retirement differs");
                boolean quiet=batch>=24&&jitDelta==0&&gcDelta==0;if(quiet)accepted++;
                System.out.println("NOISE_SCREEN {\"kind\":"+kind+",\"family\":"+family+",\"batch\":"+batch+",\"accepted\":"+quiet+",\"count\":"+count+",\"elapsed_ns\":"+elapsed+",\"cpu_ns\":"+cpu+",\"java_bytes\":"+allocated+",\"jit_ms\":"+jitDelta+",\"gc\":"+gcDelta+",\"sum\":"+sum+"}");
            }
            if(accepted!=6)throw new AssertionError("insufficient quiet samples");
            settle();
            long[] end=stats();for(int i=0;i<100&&end.length!=0&&end[3]>rowStart[3];i++){settle();end=stats();}
            if(end.length!=0&&(end[0]!=rowStart[0]||end[3]>rowStart[3]))throw new AssertionError("native builders or leaf owners retained after row");
            System.out.println("NOISE_RETIREMENT "+kind+" "+family+" "+Arrays.toString(rowStart)+" "+Arrays.toString(end));
        }
        Thread.sleep(250);
    }
}
