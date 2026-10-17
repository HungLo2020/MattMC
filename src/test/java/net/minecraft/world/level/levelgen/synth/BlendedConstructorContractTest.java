package net.minecraft.world.level.levelgen.synth;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import org.junit.jupiter.api.Test;
/** Original public Blended producer effects; baseline fixture before migration. */
class BlendedConstructorContractTest {
 @org.junit.jupiter.api.BeforeAll static void bootstrap(){net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();}
 static final class Source implements RandomSource {
  final RandomSource actual=RandomSource.create(42);int doubles,ints,consumed;Runnable onDouble;
  public RandomSource fork(){throw new AssertionError("fork");}public PositionalRandomFactory forkPositional(){throw new AssertionError("forkPositional");}public void setSeed(long seed){throw new AssertionError("setSeed");}
  public double nextDouble(){doubles++;var action=onDouble;if(action!=null)action.run();return actual.nextDouble();}
  public int nextInt(int bound){ints++;return actual.nextInt(bound);}public int nextInt(){return actual.nextInt();}public long nextLong(){return actual.nextLong();}public void consumeCount(int n){consumed+=n;actual.consumeCount(n);}
  public boolean nextBoolean(){throw new AssertionError("boolean");}public float nextFloat(){throw new AssertionError("float");}public double nextGaussian(){throw new AssertionError("gaussian");}
 }
 static BlendedNoise create(RandomSource source){return new BlendedNoise(source,.25,.125,80,160,4);}
 @Test void genuineSourceFailureAtSecondChildKeepsTheOriginalPrefix(){var source=new Source();var failure=new RuntimeException("actual second Blended child");source.onDouble=()->{if(source.doubles==49)throw failure;};assertSame(failure,assertThrows(RuntimeException.class,()->create(source)));assertEquals(49,source.doubles);assertEquals(16*256,source.ints);assertEquals(0,source.consumed);var expected=RandomSource.create(42);for(int i=0;i<16;i++)new ImprovedNoise(expected);assertEquals(expected.nextLong(),source.nextLong());}
 @Test void actualSourceReentryHasIndependentOuterAndInnerConstruction(){var source=new Source();var inner=new ArrayList<BlendedNoise>();source.onDouble=()->{source.onDouble=null;inner.add(create(source));};var outer=create(source);assertEquals(1,inner.size());assertNotSame(outer,inner.get(0));assertEquals(2*40*3,source.doubles);assertEquals(2*40*256,source.ints);assertEquals(0,source.consumed);var expected=RandomSource.create(42);for(int i=0;i<80;i++)new ImprovedNoise(expected);assertEquals(expected.nextLong(),source.nextLong());assertTrue(Double.isFinite(outer.compute(new DensityFunction.SinglePointContext(7,64,-19))));assertTrue(Double.isFinite(inner.get(0).compute(new DensityFunction.SinglePointContext(7,64,-19))));}
 @Test void withNewRandomPreservesConfigurationAndUsesTheActualNewSource(){var original=create(RandomSource.create(7));var source=new Source();var reset=original.withNewRandom(source);var expected=create(RandomSource.create(42));assertEquals(120,source.doubles);assertEquals(40*256,source.ints);assertEquals(0,source.consumed);assertEquals(Double.doubleToRawLongBits(expected.maxValue()),Double.doubleToRawLongBits(reset.maxValue()));assertEquals(Double.doubleToRawLongBits(-reset.maxValue()),Double.doubleToRawLongBits(reset.minValue()));for(int x:new int[]{-12345,-1,0,17,12345})for(int y:new int[]{-64,0,64,320}){var context=new DensityFunction.SinglePointContext(x,y,73-x);assertEquals(Double.doubleToRawLongBits(expected.compute(context)),Double.doubleToRawLongBits(reset.compute(context)));}}
}
