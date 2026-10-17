package net.minecraft.world.level.levelgen;
import java.lang.management.ManagementFactory;
import java.util.*;
import net.minecraft.util.RandomSource;
/** Real positional source production, hash conversion and physical owner retirement. */
public final class PositionalFactoryScreen {
    static volatile Object retained;static volatile long sink;
    static final String[] TYPES={"NativePositionalFactory","NativeLegacySource","NativeXoroshiroSource","NativeGaussian"};
    record Batch(PositionalRandomFactory factory,RandomSource[] sources){}
    static long[] stats(String name)throws Exception{try{var c=Class.forName("net.minecraft.world.level.levelgen."+name);var m=c.getDeclaredMethod("statistics");m.setAccessible(true);return(long[])m.invoke(null);}catch(ClassNotFoundException absent){return new long[0];}}
    static long[][] stats()throws Exception{long[][] values=new long[TYPES.length][];for(int i=0;i<values.length;i++)values[i]=stats(TYPES[i]);return values;}
    static void settle()throws Exception{retained=null;System.gc();Thread.sleep(20);}
    static void retire(long[][] initial)throws Exception{
        long deadline=System.nanoTime()+5_000_000_000L;
        while(true){settle();var after=stats();boolean done=true;for(int i=0;i<after.length;i++)if(after[i].length!=0&&(after[i][0]>initial[i][0]||i==3&&after[i][3]>initial[i][3]))done=false;if(done)return;if(System.nanoTime()>deadline)throw new AssertionError("actual factory/source/cache/frame did not retire");}
    }
    static PositionalRandomFactory factory(boolean xoro,long seed){return xoro?new XoroshiroRandomSource.XoroshiroPositionalRandomFactory(seed,~seed):new LegacyRandomSource.LegacyPositionalRandomFactory(seed);}
    static Batch prepare(int kind,int count){return new Batch(kind<2?factory(kind==1,1234567):null,new RandomSource[count]);}
    static long work(int kind,Batch batch){
        long sum=0;
        for(int i=0;i<batch.sources.length;i++){
            RandomSource source=kind<2?batch.factory.at(i*1000003,-i,i*-47):factory(kind==3,1234567L+i).fromHashOf("minecraft:overworld");
            batch.sources[i]=source;
            for(int j=0;j<(kind<2?4:3);j++)sum=Long.rotateLeft(sum,7)^Double.doubleToRawLongBits(source.nextGaussian());
            sum^=source.nextLong();
        }
        retained=batch;sink=sum;return sum;
    }
    static long gc(){return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(b->Math.max(0,b.getCollectionCount())).sum();}
    static void origin(Class<?> c)throws Exception{var url=c.getResource("/"+c.getName().replace('.','/')+".class");try(var input=url.openStream()){System.out.println("POSITIONAL_SCREEN_ORIGIN "+c.getName()+" "+url+" "+HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(input.readAllBytes())));}}
    public static void main(String[] args)throws Exception{
        boolean smoke=Arrays.asList(args).contains("--smoke");
        for(Class<?> c:new Class<?>[]{LegacyRandomSource.class,XoroshiroRandomSource.class,LegacyRandomSource.LegacyPositionalRandomFactory.class,XoroshiroRandomSource.XoroshiroPositionalRandomFactory.class})origin(c);
        var thread=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();thread.setThreadAllocatedMemoryEnabled(true);if(!thread.isThreadCpuTimeEnabled())thread.setThreadCpuTimeEnabled(true);var compiler=ManagementFactory.getCompilationMXBean();long tid=Thread.currentThread().threadId();
        for(int kind=0;kind<4;kind++){
            settle();var initial=stats();int count=smoke?16:4096,target=smoke?1:4,warmup=smoke?1:12,accepted=0;
            for(int batch=0;batch<70&&accepted<target;batch++){
                retire(initial);var data=prepare(kind,count);var before=stats();
                long jit=compiler.getTotalCompilationTime(),collections=gc(),allocated=thread.getThreadAllocatedBytes(tid),cpu=thread.getCurrentThreadCpuTime(),start=System.nanoTime();long sum=work(kind,data);long elapsed=System.nanoTime()-start;cpu=thread.getCurrentThreadCpuTime()-cpu;allocated=thread.getThreadAllocatedBytes(tid)-allocated;long jitDelta=compiler.getTotalCompilationTime()-jit,gcDelta=gc()-collections;var after=stats();
                for(int i=0;i<3;i++)if(after[i].length!=0){long expected=i==0?(kind<2?0:count):i==1?(kind==0||kind==2?count:0):(kind==1||kind==3?count:0);if(after[i][1]-before[i][1]!=expected)throw new AssertionError("actual factory/source creation work differs "+i);}
                if(after[3].length!=0&&(after[3][1]-before[3][1]!=count||after[3][4]-before[3][4]!=2L*count||after[3][4]-before[3][4]!=after[3][5]-before[3][5]||after[3][3]!=before[3][3]))throw new AssertionError("actual cache/frame work differs");
                boolean quiet=batch>=warmup&&jitDelta==0&&gcDelta==0;if(quiet)accepted++;
                System.out.println("POSITIONAL_SCREEN {\"kind\":"+kind+",\"batch\":"+batch+",\"accepted\":"+quiet+",\"count\":"+count+",\"elapsed_ns\":"+elapsed+",\"cpu_ns\":"+cpu+",\"java_bytes\":"+allocated+",\"jit_ms\":"+jitDelta+",\"gc\":"+gcDelta+",\"sum\":"+sum+",\"owners_before\":"+Arrays.deepToString(before)+",\"owners_after\":"+Arrays.deepToString(after)+"}");
            }
            if(accepted!=target)throw new AssertionError("insufficient quiet samples");retire(initial);System.out.println("POSITIONAL_RETIREMENT "+kind+" "+Arrays.deepToString(stats()));
        }
        Thread.sleep(250);
    }
}
