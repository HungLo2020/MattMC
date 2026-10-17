package net.minecraft.world.level.levelgen;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.world.level.levelgen.synth.*;
final class XoroshiroLeafDispatchContractTest {
 @BeforeAll static void bootstrap(){net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();}
 static final class CallbackSource extends XoroshiroRandomSource {
  final List<String> events=new ArrayList<>();CallbackSource(){super(42);}
  @Override public double nextDouble(){events.add("d");return .125*events.size();}
  @Override public int nextInt(int bound){events.add("i"+bound);return 0;}
  public Xoroshiro128PlusPlus nativeGenerator(){throw new AssertionError("must not call overridable generator accessor");}
 }
 static double[] leaf(XoroshiroRandomSource source,boolean simplex){if(simplex){var n=new SimplexNoise(source);return new double[]{n.xo,n.yo,n.zo};}var n=new ImprovedNoise(source);return new double[]{n.xo,n.yo,n.zo};}
 @Test void actualSourceSubclassKeepsAllOriginalCallbacksAndPrivateState(){for(boolean simplex:new boolean[]{false,true}){var source=new CallbackSource();assertArrayEquals(new double[]{32,64,96},leaf(source,simplex));var expected=new ArrayList<String>();expected.addAll(List.of("d","d","d"));for(int i=256;i>0;i--)expected.add("i"+i);assertEquals(expected,source.events);assertEquals(new XoroshiroRandomSource(42).nextLong(),source.nextLong());}}
 static final class CallbackGenerator extends Xoroshiro128PlusPlus {int draws;CallbackGenerator(){super(42,81);}@Override public long nextLong(){draws++;return Long.MAX_VALUE;}}
 @Test void actualPrivateGeneratorSubclassKeepsItsVirtualDraws()throws Exception{for(boolean simplex:new boolean[]{false,true}){var source=new XoroshiroRandomSource(42);var generator=new CallbackGenerator();var field=XoroshiroRandomSource.class.getDeclaredField("randomNumberGenerator");field.setAccessible(true);field.set(source,generator);assertArrayEquals(new double[]{128.0,128.0,128.0},leaf(source,simplex));assertEquals(259,generator.draws);}}
}
