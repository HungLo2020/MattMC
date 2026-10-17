package net.minecraft.world.level.levelgen;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Public dispatch and committed prefixes, compared with original Frozen first. */
final class LegacySourceContractTest {
    static final class Reference extends java.util.Random {
        Reference(long seed) { super(seed); }
        int bits(int count) { return super.next(count); }
    }
    static void same(double expected, double actual) {
        assertEquals(Double.doubleToRawLongBits(expected), Double.doubleToRawLongBits(actual));
    }
    @Test void mixedDrawsKeepTheIndependentJdkSequenceAndOriginalFloatRoundedDouble() {
        for (long seed : new long[]{0, 42, -1, Long.MIN_VALUE, Long.MAX_VALUE}) {
            var source = new LegacyRandomSource(seed);
            var reference = new Reference(seed);
            for (int i = 0; i < 128; i++) {
                assertEquals(reference.nextInt(), source.nextInt());
                assertEquals(reference.nextInt(7), source.nextInt(7));
                assertEquals(reference.nextInt(1073741825), source.nextInt(1073741825));
                assertEquals(reference.nextLong(), source.nextLong());
                assertEquals(reference.nextBoolean(), source.nextBoolean());
                assertEquals(reference.nextFloat(), source.nextFloat());
                same((double)(float)reference.nextDouble(), source.nextDouble());
            }
        }
    }
    @Test void unusualBitCountsKeepJavaShiftMaskingAndAdvanceExactlyOnce() {
        var source = new LegacyRandomSource(42);
        var reference = new Reference(42);
        for (int bits : new int[]{Integer.MIN_VALUE,-65,-33,-1,0,1,31,32,33,47,48,49,63,64,65,Integer.MAX_VALUE}) {
            assertEquals(reference.bits(bits), source.next(bits), "bits=" + bits);
            assertEquals(reference.nextLong(), source.nextLong());
        }
    }
    @SuppressWarnings("unchecked") static <T,E extends Throwable> T raise(Throwable failure) throws E { throw (E)failure; }
    static final class Effects extends LegacyRandomSource {
        final List<Integer> effects = new ArrayList<>();
        final IOException marker = new IOException("actual Legacy draw");
        Runnable callback;
        int failAt;
        Effects() { super(42); }
        @Override public int next(int bits) {
            effects.add(bits);
            Runnable effect = callback; callback = null;
            if (effect != null) effect.run();
            if (effects.size() == failAt) return LegacySourceContractTest.<Integer,RuntimeException>raise(marker);
            return super.next(bits);
        }
    }
    @Test void inheritedOperationsKeepTheActualVirtualBitDrawOrder() {
        var source = new Effects();
        source.nextInt(); source.nextLong(); source.nextBoolean(); source.nextFloat(); source.nextDouble();
        assertEquals(List.of(32,32,32,1,24,26,27), source.effects);
        source.effects.clear();
        assertEquals("Bound must be positive", assertThrows(IllegalArgumentException.class, () -> source.nextInt(0)).getMessage());
        assertTrue(source.effects.isEmpty());
    }
    @Test void secondLongDrawFailureKeepsTheOriginalCheckedThrowableAndFirstDrawPrefix() {
        var source = new Effects(); source.failAt = 2;
        assertSame(source.marker, assertThrows(IOException.class, source::nextLong));
        assertEquals(List.of(32,32), source.effects);
        var control = new LegacyRandomSource(42); control.next(32);
        source.failAt = 0;
        assertEquals(control.nextLong(), source.nextLong());
    }
    @Test void bitCallbackReseedingAndNestedDrawsRemainVisibleToTheOuterOperation() {
        var source = new Effects(); var control = new LegacyRandomSource(19);
        int nested = control.nextInt(); long expected = control.nextLong();
        source.callback = () -> { source.setSeed(19); assertEquals(nested, source.nextInt()); };
        assertEquals(expected, source.nextLong());
        assertEquals(List.of(32,32,32), source.effects);
        assertEquals(control.nextLong(), source.nextLong());
    }
    static final class ConstructorDispatch extends LegacyRandomSource {
        long seen;
        int calls;
        ConstructorDispatch(long seed) { super(seed); }
        @Override public void setSeed(long seed) { calls++; seen = seed; super.setSeed(seed); }
    }
    @Test void constructionKeepsVirtualSetSeedBeforeSubclassInitialization() {
        var source = new ConstructorDispatch(19);
        assertEquals(1, source.calls); assertEquals(19, source.seen);
        assertEquals(new LegacyRandomSource(19).nextLong(), source.nextLong());
    }
    static Field gaussianField() throws Exception {
        var field = LegacyRandomSource.class.getDeclaredField("gaussianSource"); field.setAccessible(true); return field;
    }
    static MarsagliaPolarGaussian gaussian(LegacyRandomSource source) throws Exception {
        return (MarsagliaPolarGaussian)gaussianField().get(source);
    }
    @Test void seedResetDiscardsOnlyItsOwnActualDefaultCache() throws Exception {
        var source = new LegacyRandomSource(42); var detached = new MarsagliaPolarGaussian(source);
        source.nextGaussian(); detached.nextGaussian();
        var expected = new LegacyRandomSource(42); expected.nextGaussian();
        var expectedDetached = new MarsagliaPolarGaussian(expected); expectedDetached.nextGaussian();
        source.setSeed(19);
        same(expectedDetached.nextGaussian(), detached.nextGaussian());
        same(new LegacyRandomSource(19).nextGaussian(), source.nextGaussian());
    }
    @Test void replacedDefaultCacheKeepsEscapedOldCacheIndependentAcrossReseeding() throws Exception {
        var source = new LegacyRandomSource(42); var escaped = gaussian(source);
        var control = new LegacyRandomSource(42); escaped.nextGaussian(); control.nextGaussian();
        var replacement = new MarsagliaPolarGaussian(source); gaussianField().set(source, replacement);
        replacement.nextGaussian(); source.setSeed(19);
        same(gaussian(control).nextGaussian(), escaped.nextGaussian());
        same(new LegacyRandomSource(19).nextGaussian(), replacement.nextGaussian());
    }
    @Test void nullDefaultCacheThrowsOnlyAfterTheNewSeedIsCommitted() throws Exception {
        var source = new LegacyRandomSource(42); gaussianField().set(source, null);
        assertThrows(NullPointerException.class, () -> source.setSeed(19));
        assertEquals(new LegacyRandomSource(19).nextLong(), source.nextLong());
    }
    static final class ResetEffect extends MarsagliaPolarGaussian {
        final IOException marker = new IOException("actual Legacy reset");
        int calls;
        ResetEffect(LegacyRandomSource source) { super(source); }
        @Override public void reset() { calls++; LegacySourceContractTest.<Void,RuntimeException>raise(marker); }
    }
    @Test void customResetKeepsItsCheckedThrowableAndCommittedSeedPrefix() throws Exception {
        var source = new LegacyRandomSource(42); var effect = new ResetEffect(source);
        gaussianField().set(source, effect);
        assertSame(effect.marker, assertThrows(IOException.class, () -> source.setSeed(19)));
        assertEquals(1, effect.calls);
        assertEquals(new LegacyRandomSource(19).nextLong(), source.nextLong());
    }
    @Test void wrapperKeepsItsOwnCachedGaussianWhenTheWrappedSourceIsReseeded() {
        var source = new WorldgenRandom(new LegacyRandomSource(42));
        var control = new WorldgenRandom(new LegacyRandomSource(42));
        same(control.nextGaussian(), source.nextGaussian());
        int count = source.getCount();
        source.setSeed(19); control.setSeed(19);
        same(control.nextGaussian(), source.nextGaussian());
        assertEquals(count, source.getCount());
        same(control.nextGaussian(), source.nextGaussian());
        assertEquals(control.getCount(), source.getCount());
        assertEquals(control.nextLong(), source.nextLong());
    }

}
