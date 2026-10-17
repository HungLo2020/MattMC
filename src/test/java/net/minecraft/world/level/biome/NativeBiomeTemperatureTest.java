package net.minecraft.world.level.biome;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Isolated;

/** Actual native cache history, frame lifetime, and idempotent arena adoption. */
@Isolated
class NativeBiomeTemperatureTest {
 @BeforeAll static void bootstrap(){BiomeTemperatureContractTest.bootstrap();}
 static NativeBiomeTemperature cache(Biome biome)throws Exception {Field f=Biome.class.getDeclaredField("temperatureCache");f.setAccessible(true);return (NativeBiomeTemperature)((ThreadLocal<?>)f.get(biome)).get();}
 @Test void verifierCopiesAndRestoresTheRealFifoAndNanBits()throws Exception {
  var b=new BiomeTemperatureContractTest.Subject(Float.intBitsToFloat(0x7fc12345));
  b.warm(b.at(71),63);b.warm(b.at(72),63);var c=cache(b.biome);var snapshot=c.snapshot();
  assertArrayEquals(new long[]{71,0x7fc12345L,72,0x7fc12345L},snapshot);
  c.restore(new long[]{81,Float.floatToRawIntBits(.2f),82,Float.floatToRawIntBits(.1f)});
  int reads=b.reads;assertTrue(b.warm(b.at(81),63));assertFalse(b.warm(b.at(82),63));assertEquals(reads,b.reads);
  c.restore(snapshot);assertArrayEquals(snapshot,c.snapshot());assertFalse(b.warm(b.at(71),63));assertEquals(reads+1,b.reads);
 }
 @Test void invalidVerificationSnapshotLeavesTheActualCacheUntouched()throws Exception {
  var b=new BiomeTemperatureContractTest.Subject();b.warm(b.at(1),63);var c=cache(b.biome);var before=c.snapshot();
  assertThrows(IllegalArgumentException.class,()->c.restore(new long[]{2,0,2,1}));assertArrayEquals(before,c.snapshot());
  assertThrows(IllegalArgumentException.class,()->c.restore(new long[]{2}));assertArrayEquals(before,c.snapshot());
 }
 @Test void genuineThrowReleasesTheFrameWithoutCommitting()throws Exception {
  var b=new BiomeTemperatureContractTest.Subject();var c=cache(b.biome);long[] before=NativeBiomeTemperature.statistics();
  b.failure=new RuntimeException("genuineY");assertSame(b.failure,assertThrows(RuntimeException.class,()->b.warm(b.at(1),63)));
  long[] after=NativeBiomeTemperature.statistics();assertEquals(before[6],after[6]);assertEquals(before[5],after[5]);assertArrayEquals(new long[0],c.snapshot());
 }
 static WeakReference<?> removedOwner()throws Exception {
  var b=new BiomeTemperatureContractTest.Subject();b.warm(b.at(7),63);Object c=cache(b.biome);var weak=new WeakReference<>(c);
  Field f=Biome.class.getDeclaredField("temperatureCache");f.setAccessible(true);((ThreadLocal<?>)f.get(b.biome)).remove();return weak;
 }
 @Test void lastActualCacheOwnerCollectionRunsItsNativeDestructor()throws Exception {
  for(int i=0;i<20;i++){System.gc();Thread.sleep(10);}long[] before=NativeBiomeTemperature.statistics();var weak=removedOwner();
  for(int i=0;i<160;i++){System.gc();Thread.sleep(20);long[] now=NativeBiomeTemperature.statistics();if(weak.get()==null&&now[2]>=before[2]+1&&now[0]<=before[0])return;}
  assertNull(weak.get());assertTrue(NativeBiomeTemperature.statistics()[2]>=before[2]+1,"actual cache destructor never ran");assertTrue(NativeBiomeTemperature.statistics()[0]<=before[0]);
 }
 @Test void callbackReentryAndCollectionLeaveOuterNativeInvocationUsable()throws Exception {
  var b=new BiomeTemperatureContractTest.Subject();var c=cache(b.biome);long[] before=NativeBiomeTemperature.statistics();
  b.callback=()->{b.callback=null;assertTrue(b.warm(b.at(2),63));System.gc();assertEquals(before[6]+1,NativeBiomeTemperature.statistics()[6]);};
  assertTrue(b.warm(b.at(1),63));assertArrayEquals(new long[]{2,Float.floatToRawIntBits(.2f),1,Float.floatToRawIntBits(.2f)},c.snapshot());assertEquals(before[6],NativeBiomeTemperature.statistics()[6]);Reference.reachabilityFence(c);
 }
 @Test void adoptionCleanupCanRunTwiceAndAfterRegistrationFailure()throws Throwable {
  var create=NativeBiomeTemperature.class.getDeclaredField("CREATE");create.setAccessible(true);
  long[] before=NativeBiomeTemperature.statistics();long id=(long)((java.lang.invoke.MethodHandle)create.get(null)).invokeExact();assertNotEquals(0,id);
  var type=Class.forName("net.minecraft.world.level.biome.NativeBiomeTemperature$Release");var constructor=type.getDeclaredConstructor(long.class);constructor.setAccessible(true);
  @SuppressWarnings("unchecked") var cleanup=(java.util.function.Consumer<java.lang.foreign.MemorySegment>)constructor.newInstance(id);
  try {throw new IllegalStateException("simulated owner adoption failure");}
  catch(IllegalStateException expected){cleanup.accept(java.lang.foreign.MemorySegment.NULL);}
  cleanup.accept(java.lang.foreign.MemorySegment.NULL);
  long[] after=NativeBiomeTemperature.statistics();assertEquals(before[0],after[0]);assertEquals(before[1]+1,after[1]);assertEquals(before[2]+1,after[2]);
 }
}
