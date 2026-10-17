package net.minecraft.world.level.levelgen.synth;
import static org.junit.jupiter.api.Assertions.*;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;
/** Real constructor metadata and public sampling at Java binary subnormal boundaries. */
class PerlinBinaryFrequencyContractTest {
 @Test void originalJavaPowersArePreservedAcrossEveryRepresentableBinaryFrequency()throws Exception {
  var input=PerlinNoise.class.getDeclaredField("lowestFreqInputFactor");input.setAccessible(true);
  for(int exponent=-1076;exponent<=1025;exponent++)check(input,exponent);
  check(input,Integer.MIN_VALUE);check(input,Integer.MAX_VALUE);
 }
 private static void check(java.lang.reflect.Field input,int exponent)throws Exception {
  var noise=PerlinNoise.create(RandomSource.create(42),exponent,new DoubleArrayList(new double[]{1}));
  assertEquals(Double.doubleToRawLongBits(Math.pow(2.0,exponent)),Double.doubleToRawLongBits(input.getDouble(noise)),"actual constructor frequency at "+exponent);
 }
 @Test void actualPublicSampleUsesTheOriginalSubnormalInputFactor(){
  var noise=PerlinNoise.create(RandomSource.create(42),-1024,new DoubleArrayList(new double[]{1}));
  assertEquals(4585530132662145614L,Double.doubleToRawLongBits(noise.getValue(1.0e308,0,0)),"actual public sample lost subnormal frequency");
 }
}
