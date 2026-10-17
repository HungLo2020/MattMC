package net.minecraft.world.level.levelgen;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Original public factory values, virtual effects and failure ordering. */
public class PositionalFactoryContractTest {
    static void same(RandomSource expected, RandomSource actual) {
        for (int i=0;i<16;i++) {
            assertEquals(expected.nextLong(),actual.nextLong());
            assertEquals(expected.nextInt(37),actual.nextInt(37));
            assertEquals(Double.doubleToRawLongBits(expected.nextGaussian()),Double.doubleToRawLongBits(actual.nextGaussian()));
        }
    }
    @Test void legacyCoordinateAndSeedConstructionKeepOverflowAndJavaStringHash() {
        for(long seed:new long[]{0,42,-1,Long.MIN_VALUE,Long.MAX_VALUE}) {
            var factory=new LegacyRandomSource.LegacyPositionalRandomFactory(seed);
            for(int[] xyz:new int[][]{{0,0,0},{7,-11,23},{Integer.MIN_VALUE,Integer.MAX_VALUE,-1},{Integer.MAX_VALUE,-1,Integer.MIN_VALUE}}) {
                same(new LegacyRandomSource(Mth.getSeed(xyz[0],xyz[1],xyz[2])^seed),factory.at(xyz[0],xyz[1],xyz[2]));
            }
            same(new LegacyRandomSource(Long.MIN_VALUE),factory.fromSeed(Long.MIN_VALUE));
            for(String value:new String[]{"","minecraft:overworld","Aa","BB","\ud800","\ud83d\ude00"})same(new LegacyRandomSource((long)value.hashCode()^seed),factory.fromHashOf(value));
        }
    }
    @Test void xoroshiroCoordinateAndSeedConstructionKeepRawPairFallbacks() {
        for(long[] pair:new long[][]{{0,0},{42,42},{-1,7},{Long.MIN_VALUE,Long.MAX_VALUE}}) {
            var factory=new XoroshiroRandomSource.XoroshiroPositionalRandomFactory(pair[0],pair[1]);
            for(int[] xyz:new int[][]{{0,0,0},{7,-11,23},{Integer.MIN_VALUE,Integer.MAX_VALUE,-1}})same(new XoroshiroRandomSource(Mth.getSeed(xyz[0],xyz[1],xyz[2])^pair[0],pair[1]),factory.at(xyz[0],xyz[1],xyz[2]));
            for(long seed:new long[]{0,42,-1,Long.MIN_VALUE,Long.MAX_VALUE})same(new XoroshiroRandomSource(seed^pair[0],seed^pair[1]),factory.fromSeed(seed));
        }
    }
    @Test void xoroshiroHashUsesOriginalUtf8Md5IncludingUnpairedSurrogates() {
        var factory=new XoroshiroRandomSource.XoroshiroPositionalRandomFactory(Long.MIN_VALUE,19);
        for(String value:new String[]{"","minecraft:overworld","\u0000","\u03bb\ud83d\ude00","\ud800","\udc00","\udc00\ud800","a".repeat(129)}) {
            var seed=RandomSupport.seedFromHashOf(value);
            same(new XoroshiroRandomSource(seed.seedLo()^Long.MIN_VALUE,seed.seedHi()^19),factory.fromHashOf(value));
        }
    }
    @Test void defaultPositionAndResourceMethodsKeepActualSubclassDispatch() {
        var marker=new LegacyRandomSource(91);var seen=new ArrayList<String>();
        var factory=new XoroshiroRandomSource.XoroshiroPositionalRandomFactory(3,4) {
            @Override public RandomSource at(int x,int y,int z){seen.add(x+","+y+","+z);return marker;}
            @Override public RandomSource fromHashOf(String text){seen.add(text);return marker;}
        };
        assertSame(marker,factory.at(new BlockPos(7,-11,23)));
        assertSame(marker,factory.fromHashOf(ResourceLocation.parse("minecraft:overworld")));
        assertEquals(List.of("7,-11,23","minecraft:overworld"),seen);
        assertThrows(NullPointerException.class,()->factory.at((BlockPos)null));
        assertThrows(NullPointerException.class,()->factory.fromHashOf((ResourceLocation)null));
        assertEquals(2,seen.size());
    }
    @SuppressWarnings("unchecked") static <E extends Throwable,T>T raise(Throwable value)throws E{throw(E)value;}
    @Test void genuineFactoryFailureKeepsOriginalThrowableAndPrefix() {
        var failure=new IOException("actual positional callback");var seen=new ArrayList<String>();
        var factory=new LegacyRandomSource.LegacyPositionalRandomFactory(19) {
            @Override public RandomSource at(int x,int y,int z){seen.add("at");return PositionalFactoryContractTest.<RuntimeException,RandomSource>raise(failure);}
        };
        assertSame(failure,assertThrows(Throwable.class,()->factory.at(new BlockPos(1,2,3))));
        assertEquals(List.of("at"),seen);
        same(new LegacyRandomSource(7),factory.fromSeed(7));
    }
    @Test void nullHashFailureAndConfigurationAppendLeaveFactoryUsable() {
        for(PositionalRandomFactory factory:new PositionalRandomFactory[]{new LegacyRandomSource.LegacyPositionalRandomFactory(-17),new XoroshiroRandomSource.XoroshiroPositionalRandomFactory(-17,23)}) {
            assertThrows(NullPointerException.class,()->factory.fromHashOf((String)null));
            assertThrows(NullPointerException.class,()->factory.parityConfigString(null));
            var builder=new StringBuilder("prefix ");factory.parityConfigString(builder);
            assertEquals(factory instanceof LegacyRandomSource.LegacyPositionalRandomFactory?"prefix LegacyPositionalRandomFactory{-17}":"prefix seedLo: -17, seedHi: 23",builder.toString());
            same(factory.fromSeed(91),factory.fromSeed(91));
        }
    }
    @Test void legacyForkKeepsVirtualWordAndWorldgenKeepsDelegatedFactoryIdentity() {
        class Words extends LegacyRandomSource {int calls;Words(){super(0);}@Override public long nextLong(){calls++;return 12345;}}
        var words=new Words();var fork=words.forkPositional();assertEquals(1,words.calls);
        same(new LegacyRandomSource.LegacyPositionalRandomFactory(12345).at(7,8,9),fork.at(7,8,9));
        var marker=new LegacyRandomSource.LegacyPositionalRandomFactory(99);
        var backing=new LegacyRandomSource(19){@Override public PositionalRandomFactory forkPositional(){return marker;}};
        var wrapper=new WorldgenRandom(backing);assertSame(marker,wrapper.forkPositional());assertEquals(0,wrapper.getCount());
    }
}
