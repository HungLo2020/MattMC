package net.minecraft.world.level.levelgen;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Original wrapper/cache/real virtual effects before native producer fusion. */
final class WorldgenGaussianContractTest {
    @SuppressWarnings("unchecked") static <T,E extends Throwable>T raise(Throwable failure)throws E { throw (E)failure; }
    static MarsagliaPolarGaussian cache(WorldgenRandom source)throws Exception {
        var field=LegacyRandomSource.class.getDeclaredField("gaussianSource");field.setAccessible(true);return (MarsagliaPolarGaussian)field.get(source);
    }
    static Field oldField(String name)throws Exception {var field=MarsagliaPolarGaussian.class.getDeclaredField(name);field.setAccessible(true);return field;}
    static boolean has(MarsagliaPolarGaussian cache)throws Exception {
        try{return (boolean)MarsagliaPolarGaussian.class.getMethod("nativeHas").invoke(cache);}
        catch(NoSuchMethodException original){return oldField("haveNextNextGaussian").getBoolean(cache);}
    }
    static double value(MarsagliaPolarGaussian cache)throws Exception {
        try{return (double)MarsagliaPolarGaussian.class.getMethod("nativeValue").invoke(cache);}
        catch(NoSuchMethodException original){return oldField("nextNextGaussian").getDouble(cache);}
    }
    static void set(MarsagliaPolarGaussian cache,boolean has,double value)throws Exception {
        try{MarsagliaPolarGaussian.class.getMethod("nativeSet",boolean.class,double.class).invoke(cache,has,value);}
        catch(NoSuchMethodException original){oldField("nextNextGaussian").setDouble(cache,value);oldField("haveNextNextGaussian").setBoolean(cache,has);}
    }
    static void target(MarsagliaPolarGaussian cache,net.minecraft.util.RandomSource source)throws Exception {var field=MarsagliaPolarGaussian.class.getDeclaredField("randomSource");field.setAccessible(true);field.set(cache,source);}
    static long[] owners()throws Exception {
        try{var type=Class.forName("net.minecraft.world.level.levelgen.NativeWorldgenSource");var method=type.getDeclaredMethod("statistics");method.setAccessible(true);return (long[])method.invoke(null);}
        catch(ClassNotFoundException original){return new long[0];}
    }
    static final class Doubles extends WorldgenRandom {
        int calls;Runnable callback;
        Doubles(){super(new LegacyRandomSource(42));}
        @Override public double nextDouble(){int call=++calls;var action=callback;callback=null;if(action!=null)action.run();return (call&1)==1?.75:.5;}
    }
    @Test void actualWrapperSubclassDoubleDispatchAndCachedHitDoNotCountBits(){
        var source=new Doubles();double expected=.5*Math.sqrt(-2*Math.log(.25)/.25);
        assertEquals(Double.doubleToRawLongBits(expected),Double.doubleToRawLongBits(source.nextGaussian()));assertEquals(2,source.calls);assertEquals(0,source.getCount());
        assertEquals(0.0,source.nextGaussian());assertEquals(2,source.calls);assertEquals(0,source.getCount());
    }
    @Test void realWrapperReentryKeepsTheOuterMissAndFinalCacheCommitOrder()throws Exception {
        var source=new Doubles();source.callback=()->{assertEquals(0.0,source.nextGaussian());assertEquals(3,source.calls);};
        double expected=.5*Math.sqrt(-2*Math.log(.25)/.25);assertEquals(expected,source.nextGaussian());assertEquals(4,source.calls);assertTrue(has(cache(source)));assertEquals(0.0,value(cache(source)));assertEquals(0.0,source.nextGaussian());assertEquals(4,source.calls);assertEquals(0,source.getCount());
    }
    static final class Bits extends LegacyRandomSource {
        final List<Integer> calls=new ArrayList<>();final IOException marker=new IOException("actual Gaussian backing bits");int failAt;
        Bits(){super(42);}
        @Override public int next(int bits){calls.add(bits);if(calls.size()==failAt)return WorldgenGaussianContractTest.<Integer,RuntimeException>raise(marker);return super.next(bits);}
    }
    @Test void actualBackingFailureCommitsThreeCountsAndOnlyTheFirstDouble()throws Exception {
        var backing=new Bits();var source=new WorldgenRandom(backing);backing.failAt=3;
        assertSame(backing.marker,assertThrows(IOException.class,source::nextGaussian));assertEquals(List.of(26,27,26),backing.calls);assertEquals(3,source.getCount());assertFalse(has(cache(source)));
        backing.failAt=0;var control=new java.util.Random(42);control.nextDouble();assertEquals(control.nextLong(),backing.nextLong());assertEquals(3,source.getCount());
    }
    @Test void replacingTheActualGaussianTargetPreservesCacheAndUsesTheNewWrapperOnlyAfterPop()throws Exception {
        var first=new WorldgenRandom(new LegacyRandomSource(42));var second=new WorldgenRandom(new LegacyRandomSource(19));var control=new WorldgenRandom(new LegacyRandomSource(19));
        first.nextGaussian();var cache=cache(first);long cached=Double.doubleToRawLongBits(value(cache));int count=first.getCount();target(cache,second);
        assertEquals(cached,Double.doubleToRawLongBits(first.nextGaussian()));assertEquals(count,first.getCount());assertEquals(0,second.getCount());
        assertEquals(Double.doubleToRawLongBits(control.nextGaussian()),Double.doubleToRawLongBits(first.nextGaussian()));assertEquals(control.getCount(),second.getCount());assertEquals(count,first.getCount());
    }
    @Test void cachedValueDoesNotCreateAnUnusedCountOwnerOrConsumeItsBacking()throws Exception {
        var backing=new LegacyRandomSource(42);var source=new WorldgenRandom(backing);set(cache(source),true,19.25);long[] before=owners();
        assertEquals(19.25,source.nextGaussian());assertEquals(0,source.getCount());long[] after=owners();if(before.length!=0)assertEquals(before[1],after[1]);assertEquals(new java.util.Random(42).nextLong(),backing.nextLong());
    }
    @Test void cachedNullTargetPopsBeforeTheOriginalUncachedFailure()throws Exception {
        var source=new WorldgenRandom(new LegacyRandomSource(42));var cache=cache(source);set(cache,true,19.25);target(cache,null);
        assertEquals(19.25,source.nextGaussian());assertEquals(0,source.getCount());assertThrows(NullPointerException.class,source::nextGaussian);assertFalse(has(cache));assertEquals(0,source.getCount());
    }
    @Test void wrapperAndStandaloneCachesRemainIndependentAcrossDelegatedReseed()throws Exception {
        var source=new WorldgenRandom(new LegacyRandomSource(42));var own=cache(source);var detached=new MarsagliaPolarGaussian(source);
        source.nextGaussian();double saved=value(own);assertTrue(has(own));assertFalse(has(detached));detached.nextGaussian();assertTrue(has(own));assertTrue(has(detached));int count=source.getCount();source.setSeed(19);
        assertEquals(Double.doubleToRawLongBits(saved),Double.doubleToRawLongBits(source.nextGaussian()));assertEquals(count,source.getCount());assertTrue(has(detached));detached.reset();assertFalse(has(detached));
    }
}
