package net.minecraft.world.level.levelgen.synth;
import java.lang.management.ManagementFactory;
import java.util.*;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
/** Bounded real public construction/metadata and cached sampling, with actual owner/work checks. */
public final class CompositeScreen {
 static volatile Object retained;static volatile long sink;static PerlinNoise perlin;static NormalNoise normal;
 static long gc(){return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(b->Math.max(0,b.getCollectionCount())).sum();}
 static long[] stats(String simple)throws Exception{try{var c=Class.forName("net.minecraft.world.level.levelgen.synth."+simple);var m=c.getDeclaredMethod("statistics");m.setAccessible(true);return(long[])m.invoke(null);}catch(ClassNotFoundException absent){return new long[0];}}
 static RandomSource[] sources(int kind,int count){var a=new RandomSource[count];for(int i=0;i<count;i++)a[i]=(kind&1)==0?RandomSource.create(1234567L+i):new XoroshiroRandomSource(1234567L+i);return a;}
 static void prepare(int kind){if(kind==4){perlin=PerlinNoise.create(RandomSource.create(42),-3,1,0,1,1);perlin.getValue(0,0,0);}else if(kind==5){normal=NormalNoise.create(RandomSource.create(42),-3,1,0,1,1);normal.getValue(0,0,0);}}
 static long work(int kind,int count,RandomSource[] sources,Object[] leaves){long sum=0;for(int i=0;i<count;i++){
  if(kind<4){var n=kind<2?PerlinNoise.create(sources[i],List.of(-3,-1,0)):PerlinNoise.createLegacyForLegacyNetherBiome(sources[i],-3,new DoubleArrayList(new double[]{1,0,1,1}));leaves[i]=n;sum+=Double.doubleToRawLongBits(n.maxBrokenValue(.25));sum^=sources[i].nextLong();}
  else{double x=(i&255)*.25,y=64+(i&7)*.125,z=-(i&31)*.5;double v=kind==4?perlin.getValue(x,y,z):normal.getValue(x,y,z);sum+=Double.doubleToRawLongBits(v);}
 }retained=leaves;sink=sum;return sum;}
 static void settle()throws Exception{retained=null;for(int i=0;i<3;i++){System.gc();Thread.sleep(10);}}
 public static void main(String[] args)throws Exception{
  for(Class<?> c:new Class<?>[]{PerlinNoise.class,NormalNoise.class,ImprovedNoise.class,RandomSource.class,XoroshiroRandomSource.class}){var url=c.getResource(c.getSimpleName()+".class");try(var input=url.openStream()){System.out.println("COMPOSITE_ORIGIN "+c.getName()+" "+url+" "+HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(input.readAllBytes())));}}
  var thread=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();var compiler=ManagementFactory.getCompilationMXBean();long tid=Thread.currentThread().threadId();
  for(int kind=0;kind<6;kind++){
   settle();long[] rowRoot=stats("NativePerlin"),rowLeaf=stats("NativeNoiseSeed");prepare(kind);int accepted=0,count=kind<4?8192:262144;
   for(int batch=0;batch<90&&accepted<6;batch++){
    settle();var sources=kind<4?sources(kind,count):null;var leaves=kind<4?new Object[count]:null;long[] before=stats("NativePerlin"),leafBefore=stats("NativeNoiseSeed");long jit=compiler.getTotalCompilationTime(),collections=gc();
    long allocated=thread.getThreadAllocatedBytes(tid),cpu=thread.getCurrentThreadCpuTime(),start=System.nanoTime();long sum=work(kind,count,sources,leaves);long elapsed=System.nanoTime()-start;cpu=thread.getCurrentThreadCpuTime()-cpu;allocated=thread.getThreadAllocatedBytes(tid)-allocated;
    long jitDelta=compiler.getTotalCompilationTime()-jit,gcDelta=gc()-collections;long[] after=stats("NativePerlin"),leafAfter=stats("NativeNoiseSeed");
    if(after.length!=0&&(after[3]!=before[3]||after[4]-before[4]!=count||after[5]-before[5]!=count||after[1]-before[1]!=(kind<4?count:0)||after[6]!=before[6]||after[7]-before[7]!=(kind<2?count:0)||after[8]-before[8]!=(kind<2?count:0)))throw new AssertionError("actual graph/reader work or retirement differs");
    if(leafAfter.length!=0&&(leafAfter[0]!=leafBefore[0]||leafAfter[1]-leafBefore[1]!=(kind<4?count*3L:0)||leafAfter[2]-leafBefore[2]!=(kind<4?count*3L:0)))throw new AssertionError("actual leaf builder work differs");
    boolean quiet=batch>=24&&jitDelta==0&&gcDelta==0;if(quiet)accepted++;
    System.out.println("COMPOSITE_SCREEN {\"kind\":"+kind+",\"batch\":"+batch+",\"accepted\":"+quiet+",\"count\":"+count+",\"elapsed_ns\":"+elapsed+",\"cpu_ns\":"+cpu+",\"java_bytes\":"+allocated+",\"jit_ms\":"+jitDelta+",\"gc\":"+gcDelta+",\"sum\":"+sum+"}");
   }
   if(accepted!=6)throw new AssertionError("insufficient quiet samples");perlin=null;normal=null;settle();long[] end=stats("NativePerlin"),leafEnd=stats("NativeNoiseSeed");
   for(int i=0;i<100&&((end.length!=0&&(end[0]>rowRoot[0]||end[3]>rowRoot[3]||end[6]>rowRoot[6]))||(leafEnd.length!=0&&leafEnd[3]>rowLeaf[3]));i++){settle();end=stats("NativePerlin");leafEnd=stats("NativeNoiseSeed");}
   if((end.length!=0&&(end[0]>rowRoot[0]||end[3]>rowRoot[3]||end[6]>rowRoot[6]))||(leafEnd.length!=0&&leafEnd[3]>rowLeaf[3]))throw new AssertionError("native graph/reader/factory/leaf retained after row");
   System.out.println("COMPOSITE_RETIREMENT "+kind+" roots "+Arrays.toString(rowRoot)+" "+Arrays.toString(end)+" leaves "+Arrays.toString(rowLeaf)+" "+Arrays.toString(leafEnd));
  }
  Thread.sleep(250);
 }
}
