package net.minecraft.world.level.levelgen;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class XoroshiroOperationsContractTest {
 static Object state(XoroshiroRandomSource value){return XoroshiroRandomSource.CODEC.encodeStart(JsonOps.INSTANCE,value).getOrThrow();}
 static final class IntegerSource extends XoroshiroRandomSource {int draws;IntegerSource(){super(42);}@Override public int nextInt(){draws++;return -1;}}
 @Test void boundedMethodKeepsVirtualUnboundedDrawAndRejectsBeforeDrawing(){var source=new IntegerSource();Object initial=state(source);assertEquals(6,source.nextInt(7));assertEquals(1,source.draws);assertEquals(initial,state(source));assertEquals("Bound must be positive",assertThrows(IllegalArgumentException.class,()->source.nextInt(0)).getMessage());assertEquals(1,source.draws);}
 static final class LongTrap extends XoroshiroRandomSource {LongTrap(){super(42);}@Override public long nextLong(){throw new AssertionError("source virtual nextLong");}public Xoroshiro128PlusPlus nativeGenerator(){throw new AssertionError("source virtual nativeGenerator");}}
 @Test void privateGeneratorOperationsDoNotCallOverriddenSourceLongOrAccessor(){var source=new LongTrap();var control=new XoroshiroRandomSource(42);assertEquals(control.nextDouble(),source.nextDouble());assertEquals(control.nextFloat(),source.nextFloat());assertEquals(control.nextBoolean(),source.nextBoolean());source.consumeCount(-3);control.consumeCount(-3);source.consumeCount(17);control.consumeCount(17);assertEquals(control.nextInt(1073741825),source.nextInt(1073741825));assertEquals(control.nextGaussian(),source.nextGaussian());assertEquals(state(control),state(source));}
 @Test void originalFloatRoundingPermitsTheDoubleEndpointAndLeafOffset(){assertEquals(1.0,new XoroshiroRandomSource(-1,1).nextDouble());assertEquals(256.0,new net.minecraft.world.level.levelgen.synth.ImprovedNoise(new XoroshiroRandomSource(-1,1)).xo);}

    static final class ReseedingGenerator extends Xoroshiro128PlusPlus {
        final XoroshiroRandomSource source;
        final long first;
        int calls;
        ReseedingGenerator(XoroshiroRandomSource source, long first) {
            super(1, 2);
            this.source = source;
            this.first = first;
        }
        @Override public long nextLong() {
            assertEquals(1, ++calls, "old generator reused after callback reseed");
            source.setSeed(19);
            return first;
        }
    }
    static ReseedingGenerator install(XoroshiroRandomSource source, long first) throws Exception {
        var generator = new ReseedingGenerator(source, first);
        var field = XoroshiroRandomSource.class.getDeclaredField("randomNumberGenerator");
        field.setAccessible(true);
        field.set(source, generator);
        return generator;
    }
    static void parentState(XoroshiroRandomSource control, XoroshiroRandomSource source, ReseedingGenerator generator) {
        assertEquals(1, generator.calls);
        assertEquals(state(control), state(source));
    }
    @Test void forkReadsTheActualGeneratorAgainAfterCallbackReseeding() throws Exception {
        var source = new XoroshiroRandomSource(42);
        var generator = install(source, 123);
        var control = new XoroshiroRandomSource(19);
        var child = new XoroshiroRandomSource(123, control.nextLong());
        assertEquals(child.nextLong(), source.fork().nextLong());
        parentState(control, source, generator);
    }
    @Test void positionalForkReadsTheActualGeneratorAgainAfterCallbackReseeding() throws Exception {
        var source = new XoroshiroRandomSource(42);
        var generator = install(source, 456);
        var control = new XoroshiroRandomSource(19);
        var factory = new XoroshiroRandomSource.XoroshiroPositionalRandomFactory(456, control.nextLong());
        assertEquals(factory.at(7, -11, 23).nextLong(), source.forkPositional().at(7, -11, 23).nextLong());
        parentState(control, source, generator);
    }
    @Test void boundedRejectionContinuesWithTheGeneratorInstalledByItsCallback() throws Exception {
        var source = new XoroshiroRandomSource(42);
        var generator = install(source, 0);
        var control = new XoroshiroRandomSource(19);
        assertEquals(control.nextInt(1073741825), source.nextInt(1073741825));
        parentState(control, source, generator);
    }
}
