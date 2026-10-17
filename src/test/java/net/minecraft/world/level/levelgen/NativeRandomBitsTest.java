package net.minecraft.world.level.levelgen;
import java.lang.ref.WeakReference;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;
final class NativeRandomBitsTest {
 @BeforeAll static void bootstrap(){net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();}
 private record Pending(ImprovedNoise leaf,WeakReference<XoroshiroRandomSource> source,WeakReference<Xoroshiro128PlusPlus> generator,long sample){}
 private static Pending pending(){var source=new XoroshiroRandomSource(42);var leaf=new ImprovedNoise(source);return new Pending(leaf,new WeakReference<>(source),new WeakReference<>(source.nativeGenerator()),Double.doubleToRawLongBits(leaf.noise(1.25,3.75,-.5)));}
 private static void settle()throws Exception{for(int i=0;i<3;i++){System.gc();Thread.sleep(15);}}
 @Test void actualLeafOutlivesTheRetiredSourceAndItsCanonicalNativeState()throws Exception{
  settle();long[] sourceBefore=NativeXoroshiroSource.statistics();long[] before=NativeRandomBits.statistics();var pending=pending();assertEquals(before[1]+1,NativeRandomBits.statistics()[1]);assertEquals(sourceBefore[1]+1,NativeXoroshiroSource.statistics()[1]);
  for(int i=0;i<120&&(pending.source.get()!=null||pending.generator.get()!=null||NativeRandomBits.statistics()[2]<before[2]+1);i++){System.gc();Thread.sleep(20);}
  assertTrue(NativeXoroshiroSource.statistics()[2]>=sourceBefore[2]+1);assertTrue(NativeXoroshiroSource.statistics()[0]<=sourceBefore[0]);assertNull(pending.source.get());assertNull(pending.generator.get());assertTrue(NativeRandomBits.statistics()[2]>=before[2]+1,"actual native source owner was not retired");assertTrue(NativeRandomBits.statistics()[0]<=before[0]);assertEquals(pending.sample,Double.doubleToRawLongBits(pending.leaf.noise(1.25,3.75,-.5)));
 }
}
