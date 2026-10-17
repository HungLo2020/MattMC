package net.minecraft.world.level.levelgen;
import java.lang.management.ManagementFactory;
import java.util.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.synth.*;
/** Real Gaussian wrapper kernel, creation and bounded physical retirement; bounded intermediate screen. */
public final class WorldgenGaussianScreen {
 static volatile Object retained;static volatile long sink;
 static long gc(){return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(b->Math.max(0,b.getCollectionCount())).sum();}
 static long[] stats(String name)throws Exception{try{var type=Class.forName(name);var method=type.getDeclaredMethod("statistics");method.setAccessible(true);return(long[])method.invoke(null);}catch(ClassNotFoundException absent){return new long[0];}}
 static final String[] TYPES={"net.minecraft.world.level.levelgen.NativeWorldgenSource","net.minecraft.world.level.levelgen.NativeLegacySource","net.minecraft.world.level.levelgen.NativeXoroshiroSource","net.minecraft.world.level.levelgen.NativeGaussian"};
 static long[][] stats()throws Exception{long[][] result=new long[TYPES.length][];for(int i=0;i<TYPES.length;i++)result[i]=stats(TYPES[i]);return result;}
 static void settle()throws Exception{retained=null;System.gc();Thread.sleep(20);}
 static long work(int kind,WorldgenRandom[] sources,Object[] values){long sum=0;for(int i=0;i<sources.length;i++){
  WorldgenRandom source=kind>=2?new WorldgenRandom(kind==2?new LegacyRandomSource(1234567L+i):new XoroshiroRandomSource(1234567L+i)):sources[i];
  for(int j=0;j<(kind<2?32:3);j++)sum=Long.rotateLeft(sum,7)^Double.doubleToRawLongBits(source.nextGaussian());
  sum^=source.nextLong();sum+=source.getCount();values[i]=source;
 }retained=values;sink=sum;return sum;}
 static void row(int kind,boolean smoke)throws Exception{
  settle();long[][] initial=stats();int count=smoke?16:4096,target=smoke?1:4,warmup=smoke?1:12,accepted=0;
  var thread=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();var compiler=ManagementFactory.getCompilationMXBean();long tid=Thread.currentThread().threadId();
  for(int batch=0;batch<60&&accepted<target;batch++){
   settle();var sources=new WorldgenRandom[count];if(kind<2)for(int i=0;i<count;i++)sources[i]=new WorldgenRandom(kind==1?new XoroshiroRandomSource(1234567L+i):new LegacyRandomSource(1234567L+i));var values=new Object[count];
   long[][] before=stats();long jit=compiler.getTotalCompilationTime(),collections=gc(),allocated=thread.getThreadAllocatedBytes(tid),cpu=thread.getCurrentThreadCpuTime(),start=System.nanoTime();long sum=work(kind,sources,values);long elapsed=System.nanoTime()-start;cpu=thread.getCurrentThreadCpuTime()-cpu;allocated=thread.getThreadAllocatedBytes(tid)-allocated;long jitDelta=compiler.getTotalCompilationTime()-jit,gcDelta=gc()-collections;long[][] after=stats();
   if(after[0].length!=0&&(after[0][1]-before[0][1]!=count||after[0][4]!=before[0][4]))throw new AssertionError("actual wrapper/count work differs");
   if(after[3].length!=0&&(after[3][4]-before[3][4]!=(kind<2?16L*count:2L*count)||after[3][4]-before[3][4]!=after[3][5]-before[3][5]||after[3][3]!=before[3][3]))throw new AssertionError("actual Gaussian frame work differs");
   boolean quiet=batch>=warmup&&jitDelta==0&&gcDelta==0;if(quiet)accepted++;System.out.println("WORLDGEN_GAUSSIAN_SCREEN {\"kind\":"+kind+",\"batch\":"+batch+",\"accepted\":"+quiet+",\"count\":"+count+",\"elapsed_ns\":"+elapsed+",\"cpu_ns\":"+cpu+",\"java_bytes\":"+allocated+",\"jit_ms\":"+jitDelta+",\"gc\":"+gcDelta+",\"sum\":"+sum+",\"owners_before\":"+Arrays.deepToString(before)+",\"owners_after\":"+Arrays.deepToString(after)+"}");
  }
  if(accepted!=target)throw new AssertionError("insufficient quiet samples");long deadline=System.nanoTime()+5_000_000_000L;
  while(true){settle();long[][] after=stats();boolean done=true;for(int i=0;i<TYPES.length;i++)if(after[i].length!=0){if(after[i][0]>initial[i][0])done=false;if((i==0||i==3)&&after[i][3]>initial[i][3])done=false;}if(done){System.out.println("WORLDGEN_GAUSSIAN_RETIREMENT "+kind+" "+Arrays.deepToString(after));break;}if(System.nanoTime()>deadline)throw new AssertionError("actual native owners retained after row");}
 }
 public static void main(String[]args)throws Exception{
  boolean smoke=Arrays.asList(args).contains("--smoke");net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
  for(Class<?> c:new Class<?>[]{WorldgenRandom.class,LegacyRandomSource.class,XoroshiroRandomSource.class,MarsagliaPolarGaussian.class}){var url=c.getResource(c.getSimpleName()+".class");try(var input=url.openStream()){System.out.println("WORLDGEN_GAUSSIAN_SCREEN_ORIGIN "+c.getName()+" "+url+" "+HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(input.readAllBytes())));}}
  for(int kind=0;kind<4;kind++)row(kind,smoke);
 }
}
