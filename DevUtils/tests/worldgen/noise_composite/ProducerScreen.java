package net.minecraft.world.level.levelgen.synth;
import java.lang.management.ManagementFactory;
import java.util.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.DensityFunction;
/** Real complete construction, first snapshot/sample and source successor; bounded intermediate screen. */
public final class ProducerScreen {
 static volatile Object retained;static volatile long sink;
 static long gc(){return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(b->Math.max(0,b.getCollectionCount())).sum();}
 static long[] stats(String name)throws Exception{try{var type=Class.forName("net.minecraft.world.level.levelgen.synth."+name);var method=type.getDeclaredMethod("statistics");method.setAccessible(true);return(long[])method.invoke(null);}catch(ClassNotFoundException absent){return new long[0];}}
 static void settle()throws Exception{retained=null;for(int i=0;i<3;i++){System.gc();Thread.sleep(10);}}
 static long work(int kind,RandomSource[] sources,Object[] values){long sum=0;var point=new DensityFunction.SinglePointContext(13,64,-7);for(int i=0;i<sources.length;i++){if(kind<2){var noise=new BlendedNoise(sources[i],.25,.125,80,160,4);values[i]=noise;sum+=Double.doubleToRawLongBits(noise.maxValue());sum^=Double.doubleToRawLongBits(noise.compute(point));}else{var noise=new PerlinSimplexNoise(sources[i],List.of(-2,0,2));values[i]=noise;sum+=Double.doubleToRawLongBits(noise.getValue(1.25,3.75,true));}sum^=sources[i].nextLong();}retained=values;sink=sum;return sum;}
 static boolean live(long[]value,long[]before,int...positions){for(int p:positions)if(value.length!=0&&value[p]>before[p])return false;return true;}
 public static void main(String[]args)throws Exception{
  boolean smoke=Arrays.asList(args).contains("--smoke");int only=-1;for(String arg:args)if(arg.startsWith("--kind="))only=Integer.parseInt(arg.substring(7));int target=smoke?1:6,warmup=smoke?2:24;
  net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
  for(Class<?> c:new Class<?>[]{BlendedNoise.class,PerlinSimplexNoise.class,SimplexNoise.class,PerlinNoise.class,XoroshiroRandomSource.class}){var url=c.getResource(c.getSimpleName()+".class");try(var input=url.openStream()){System.out.println("PRODUCER_ORIGIN "+c.getName()+" "+url+" "+HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(input.readAllBytes())));}}
  var thread=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();var compiler=ManagementFactory.getCompilationMXBean();long tid=Thread.currentThread().threadId();
  for(int kind=0;kind<4;kind++){if(only>=0&&kind!=only)continue;settle();long[] initialLeaf=stats("NativeNoiseSeed"),initialPerlin=stats("NativePerlin"),initialBlended=stats("NativeBlended"),initialSimplex=stats("NativeSimplexOctaves");int count=smoke?32:(kind<2?512:4096),accepted=0;
   for(int batch=0;batch<90&&accepted<target;batch++){settle();var sources=new RandomSource[count];for(int i=0;i<count;i++)sources[i]=(kind&1)==0?RandomSource.create(1234567L+i):new XoroshiroRandomSource(1234567L+i);var values=new Object[count];long[] leaf=stats("NativeNoiseSeed"),perlin=stats("NativePerlin"),blended=stats("NativeBlended"),simplex=stats("NativeSimplexOctaves");long jit=compiler.getTotalCompilationTime(),collections=gc(),allocated=thread.getThreadAllocatedBytes(tid),cpu=thread.getCurrentThreadCpuTime(),start=System.nanoTime();long sum=work(kind,sources,values);long elapsed=System.nanoTime()-start;cpu=thread.getCurrentThreadCpuTime()-cpu;allocated=thread.getThreadAllocatedBytes(tid)-allocated;long jitDelta=compiler.getTotalCompilationTime()-jit,gcDelta=gc()-collections;long[] la=stats("NativeNoiseSeed"),pa=stats("NativePerlin"),ba=stats("NativeBlended"),sa=stats("NativeSimplexOctaves");long leaves=(kind<2?40L:3L)*count;
    if(la.length!=0&&(la[0]!=leaf[0]||la[1]-leaf[1]!=leaves||la[2]-leaf[2]!=leaves))throw new AssertionError("actual leaf work/custody differs");
    if(pa.length!=0){long graphs=kind<2?3L*count:0,readers=kind<2?(ba.length!=0?2L:4L)*count:0;if(pa[1]-perlin[1]!=graphs||pa[3]!=perlin[3]||pa[4]-perlin[4]!=readers||pa[5]-perlin[5]!=readers)throw new AssertionError("actual Perlin graph/reader work differs");}
    if(ba.length!=0&&(ba[1]-blended[1]!=(kind<2?count:0)))throw new AssertionError("actual Blended producer work differs");
    if(sa.length!=0&&(sa[1]-simplex[1]!=(kind>=2?count:0)))throw new AssertionError("actual Simplex producer work differs");
    boolean quiet=batch>=warmup&&jitDelta==0&&gcDelta==0;if(quiet)accepted++;System.out.println("PRODUCER_SCREEN {\"kind\":"+kind+",\"batch\":"+batch+",\"accepted\":"+quiet+",\"count\":"+count+",\"elapsed_ns\":"+elapsed+",\"cpu_ns\":"+cpu+",\"java_bytes\":"+allocated+",\"jit_ms\":"+jitDelta+",\"gc\":"+gcDelta+",\"sum\":"+sum+"}");
   }
   if(accepted!=target)throw new AssertionError("insufficient quiet samples");settle();for(int retry=0;retry<100;retry++){boolean done=live(stats("NativeNoiseSeed"),initialLeaf,0,3)&&live(stats("NativePerlin"),initialPerlin,0,3,6)&&live(stats("NativeBlended"),initialBlended,0)&&live(stats("NativeSimplexOctaves"),initialSimplex,0);if(done)break;if(retry==99)throw new AssertionError("actual native owners retained after row");settle();}System.out.println("PRODUCER_RETIREMENT "+kind+" leaf="+Arrays.toString(stats("NativeNoiseSeed"))+" perlin="+Arrays.toString(stats("NativePerlin"))+" blended="+Arrays.toString(stats("NativeBlended"))+" simplex="+Arrays.toString(stats("NativeSimplexOctaves")));
  }Thread.sleep(250);
 }
}
