package net.minecraft.world.level.levelgen.synth;
import static org.junit.jupiter.api.Assertions.*;
import java.lang.ref.WeakReference;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import net.minecraft.util.RandomSource;
/** Actual Simplex graph failure, reentry, VM allocation and retirement checks. */
@Isolated
class NativeSimplexOctavesTest {
 @Test void actualSkipFailureRetiresTheGraphAfterItsBoundLeaves(){
  var source=new SimplexCompositeConstructorContractTest.Source();var failure=new RuntimeException("actual partial Simplex graph");source.consumeFailure=failure;long[] before=NativeSimplexOctaves.statistics();assertSame(failure,assertThrows(RuntimeException.class,()->new PerlinSimplexNoise(source,List.of(-3,-1,0))));long[] after=NativeSimplexOctaves.statistics();assertEquals(before[0],after[0]);assertEquals(before[1]+1,after[1]);assertEquals(before[2]+1,after[2]);
 }
 @Test void actualSourceReentryKeepsBothCompletedGraphsIndependent(){
  var source=new SimplexCompositeConstructorContractTest.Source();var inner=new ArrayList<PerlinSimplexNoise>();source.onDouble=()->inner.add(new PerlinSimplexNoise(source,List.of(-3,-1,0)));long[] before=NativeSimplexOctaves.statistics();var outer=new PerlinSimplexNoise(source,List.of(-3,-1,0));assertEquals(1,inner.size());long[] after=NativeSimplexOctaves.statistics();assertEquals(before[1]+2,after[1]);var expectedSource=RandomSource.create(42);var expectedInner=new PerlinSimplexNoise(expectedSource,List.of(-3,-1,0));var expectedOuter=new PerlinSimplexNoise(expectedSource,List.of(-3,-1,0));assertEquals(expectedSource.nextLong(),source.nextLong());for(boolean offsets:new boolean[]{false,true}){assertEquals(Double.doubleToRawLongBits(expectedInner.getValue(1.25,3.75,offsets)),Double.doubleToRawLongBits(inner.get(0).getValue(1.25,3.75,offsets)));assertEquals(Double.doubleToRawLongBits(expectedOuter.getValue(1.25,3.75,offsets)),Double.doubleToRawLongBits(outer.getValue(1.25,3.75,offsets)));}
 }
 record Pending(WeakReference<PerlinSimplexNoise> facade,NativeNoise snapshot,long expected){}
 static Pending pending(NoiseConstructionProbe probe){var noise=new PerlinSimplexNoise(RandomSource.create(42),List.of(-3,-1,0),probe.id);var snapshot=(NativeNoise)noise.nativeState();return new Pending(new WeakReference<>(noise),snapshot,Double.doubleToRawLongBits(noise.getValue(1.25,3.75,true)));}
 static void settle()throws Exception{for(int i=0;i<20;i++){System.gc();Thread.sleep(10);}}
 @Test void immutableSnapshotKeepsWorkingAfterActualNativeGraphAndLeavesRetire()throws Exception{
  settle();
  try(var probe=new NoiseConstructionProbe()){
   var pending=pending(probe);
   for(int i=0;i<120&&!pending.facade.refersTo(null);i++){System.gc();Thread.sleep(20);}
   assertTrue(pending.facade.refersTo(null));settle();
   assertArrayEquals(new long[]{3,0,1,0,1,1},probe.read(),"this graph and all its actual leaves must retire");
   assertEquals(pending.expected,Double.doubleToRawLongBits(pending.snapshot.sample(1.25,3.75,0,0,0,1)));
  }
 }
 @Test void actualVmArrayLimitRetiresInitialLeafWithoutAllocatingTheNativeVector()throws Exception{
  settle();
  try(var probe=new NoiseConstructionProbe()){
   var source=new SimplexCompositeConstructorContractTest.Source();
   assertThrows(OutOfMemoryError.class,()->new PerlinSimplexNoise(source,List.of(0,Integer.MAX_VALUE-1),probe.id));
   assertEquals(259,source.effects.size());settle();
   assertArrayEquals(new long[]{1,0,0,0,0,0},probe.read(),"actual initial leaf must retire; no native vector may be attempted");
  }
 }
}
