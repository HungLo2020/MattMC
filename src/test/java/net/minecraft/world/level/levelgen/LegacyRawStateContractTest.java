package net.minecraft.world.level.levelgen;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Bridge APIs absent from original Frozen; compare published pre and Current. */
final class LegacyRawStateContractTest {
 @Test void rawImportMasksAllBitsAndDoesNotRescrambleThem(){
  var source=new LegacyRandomSource(42);
  for(long value:new long[]{0,-1,Long.MIN_VALUE,Long.MAX_VALUE,0x123456789abcdefL}){source.setRawSeed(value);assertEquals(value&281474976710655L,source.rawSeed());}
 }
 @Test void rawImportLeavesTheActualGeneratedCacheAndSuccessorStateUntouched(){
  var source=new LegacyRandomSource(42);source.nextGaussian();double cached=source.nativeGaussian().nativeValue();source.setRawSeed(7);
  assertEquals(Double.doubleToRawLongBits(cached),Double.doubleToRawLongBits(source.nextGaussian()));assertEquals(7,source.rawSeed());assertFalse(source.nativeGaussian().nativeHas());
 }
 static final class Traps extends LegacyRandomSource {
  Traps(){super(42);}
  @Override public long rawSeed(){throw new AssertionError("virtual raw read");}
  @Override public void setRawSeed(long seed){throw new AssertionError("virtual raw write");}
  @Override public MarsagliaPolarGaussian nativeGaussian(){throw new AssertionError("virtual Gaussian projection");}
 }
 @Test void actualPrivateSourceStateAndCacheNeverCallOverriddenBridgeProjections(){
  var source=new Traps();var control=new LegacyRandomSource(42);
  assertEquals(control.nextLong(),source.nextLong());assertEquals(control.nextDouble(),source.nextDouble());
  assertEquals(Double.doubleToRawLongBits(control.nextGaussian()),Double.doubleToRawLongBits(source.nextGaussian()));
  source.setSeed(19);control.setSeed(19);assertEquals(control.nextLong(),source.nextLong());
 }
}
