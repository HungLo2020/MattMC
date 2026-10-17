package net.minecraft.world.level.levelgen;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class XoroshiroRawStateContractTest {
 @Test void publicBridgePreservesRawZeroInsteadOfConstructorNormalization(){var rng=new Xoroshiro128PlusPlus(0,0);assertNotEquals(0,rng.nextLong());rng.nativeSetState(0,0);assertEquals(0,rng.nativeSeedLo());assertEquals(0,rng.nativeSeedHi());for(int i=0;i<4;i++)assertEquals(0,rng.nextLong());}
 @Test void rawRestoreContinuesTheSameActualSequence(){var rng=new Xoroshiro128PlusPlus(42,81);for(int i=0;i<7;i++)rng.nextLong();long lo=rng.nativeSeedLo(),hi=rng.nativeSeedHi();var control=new Xoroshiro128PlusPlus(lo,hi);for(int i=0;i<7;i++)rng.nextLong();rng.nativeSetState(lo,hi);for(int i=0;i<100;i++)assertEquals(control.nextLong(),rng.nextLong());}
 @Test void generatorEscapedBeforeReseedKeepsItsOwnOriginalState(){var source=new XoroshiroRandomSource(42);var old=source.nativeGenerator();var control=new Xoroshiro128PlusPlus(old.nativeSeedLo(),old.nativeSeedHi());source.setSeed(19);assertNotSame(old,source.nativeGenerator());for(int i=0;i<32;i++)assertEquals(control.nextLong(),old.nextLong());assertEquals(new XoroshiroRandomSource(19).nextLong(),source.nextLong());}
}
