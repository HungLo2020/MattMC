package net.minecraft.world.level.levelgen;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Original VM cache, callback and error-prefix contracts for both real owners. */
final class GaussianEffectsContractTest {
    static final double EXPECTED = .5 * Math.sqrt(-2 * Math.log(.25) / .25);
    static final class Script extends XoroshiroRandomSource {
        final double[] words;
        final IOException sentinel = new IOException("original");
        int calls;
        int failAt = -1;
        int reenterAt = -1;
        int resetAt = -1;
        MarsagliaPolarGaussian gaussian;
        Script(double... words) { super(42); this.words = words; }
        @Override public double nextDouble() {
            int call = calls++;
            if (call == failAt) return GaussianEffectsContractTest.<Double, RuntimeException>raise(sentinel);
            if (call == reenterAt) same(EXPECTED, gaussian.nextGaussian());
            if (call == resetAt) gaussian.reset();
            return words[call];
        }
        public MarsagliaPolarGaussian nativeGaussian() { throw new AssertionError("virtual cache accessor"); }
    }
    @SuppressWarnings("unchecked")
    private static <T, E extends Throwable> T raise(Throwable failure) throws E { throw (E) failure; }
    static void same(double expected, double actual) {
        assertEquals(Double.doubleToRawLongBits(expected), Double.doubleToRawLongBits(actual));
    }
    static MarsagliaPolarGaussian actualDefault(Script source) throws Exception {
        var field = XoroshiroRandomSource.class.getDeclaredField("gaussianSource");
        field.setAccessible(true);
        return (MarsagliaPolarGaussian) field.get(source);
    }
    static MarsagliaPolarGaussian attach(Script source, boolean embedded) throws Exception {
        return source.gaussian = embedded ? actualDefault(source) : new MarsagliaPolarGaussian(source);
    }
    @Test void cachedSecondValueConsumesNoDrawAndResetDiscardsIt() throws Exception {
        for (boolean embedded : new boolean[]{false, true}) {
            var source = new Script(.75, .5, .75, .5, .75, .5);
            var gaussian = attach(source, embedded);
            same(EXPECTED, gaussian.nextGaussian()); same(0, gaussian.nextGaussian()); assertEquals(2, source.calls);
            same(EXPECTED, gaussian.nextGaussian()); gaussian.reset();
            same(EXPECTED, gaussian.nextGaussian()); same(0, gaussian.nextGaussian()); assertEquals(6, source.calls);
        }
    }
    @Test void zeroAndUnitRadiusPairsKeepTheirExactRejectionDrawPrefix() throws Exception {
        for (boolean embedded : new boolean[]{false, true}) {
            var source = new Script(.5, .5, 1, .5, .75, .5);
            var gaussian = attach(source, embedded);
            same(EXPECTED, gaussian.nextGaussian()); same(0, gaussian.nextGaussian()); assertEquals(6, source.calls);
        }
    }
    @Test void originalCheckedFailureLeavesNoFabricatedCachedValue() throws Exception {
        for (boolean embedded : new boolean[]{false, true}) {
            var source = new Script(.75, .5, .75, .5);
            source.failAt = 1;
            var gaussian = attach(source, embedded);
            assertSame(source.sentinel, assertThrows(IOException.class, gaussian::nextGaussian)); assertEquals(2, source.calls);
            same(EXPECTED, gaussian.nextGaussian()); same(0, gaussian.nextGaussian()); assertEquals(4, source.calls);
        }
    }
    @Test void nestedActualCallbackCacheChangesDoNotReplayOuterDraws() throws Exception {
        for (boolean embedded : new boolean[]{false, true}) {
            var source = new Script(.75, .75, .5, .5);
            source.reenterAt = 0;
            var gaussian = attach(source, embedded);
            same(EXPECTED, gaussian.nextGaussian()); same(0, gaussian.nextGaussian()); assertEquals(4, source.calls);
        }
    }
    @Test void callbackResetPrecedesTheSuccessfulOuterCacheCommit() throws Exception {
        for (boolean embedded : new boolean[]{false, true}) {
            var source = new Script(.75, .5);
            source.resetAt = 1;
            var gaussian = attach(source, embedded);
            same(EXPECTED, gaussian.nextGaussian()); same(0, gaussian.nextGaussian()); assertEquals(2, source.calls);
        }
    }
    @Test void sourceReseedingResetsOnlyItsActualDefaultGaussian() throws Exception {
        var source = new Script(.75, .5, .75, .5, .75, .5);
        var embedded = actualDefault(source);
        var detached = new MarsagliaPolarGaussian(source);
        same(EXPECTED, embedded.nextGaussian()); same(EXPECTED, detached.nextGaussian());
        source.setSeed(19);
        same(EXPECTED, embedded.nextGaussian()); same(0, detached.nextGaussian()); assertEquals(6, source.calls);
    }
    static final class ProjectionTrap extends MarsagliaPolarGaussian {
        ProjectionTrap(Script source) { super(source); }
        public boolean nativeHas() { throw new AssertionError("virtual has"); }
        public double nativeValue() { throw new AssertionError("virtual value"); }
        public void nativeSet(boolean has, double value) { throw new AssertionError("virtual set"); }
    }
    @Test void basePrivateCacheNeverCallsOverridablePublicProjections() {
        var source = new Script(.75, .5);
        var gaussian = new ProjectionTrap(source);
        same(EXPECTED, gaussian.nextGaussian()); same(0, gaussian.nextGaussian()); assertEquals(2, source.calls);
    }
    @Test void nullSourceFailsOnlyAtTheActualDrawWithOriginalFieldMessage() {
        var gaussian = new MarsagliaPolarGaussian(null);
        var failure = assertThrows(NullPointerException.class, gaussian::nextGaussian);
        assertTrue(failure.getMessage().contains("this.randomSource"), failure.getMessage());
    }
    @Test void nonFiniteActualSourceValuesKeepRejectionAndCachedNanEffects() throws Exception {
        for (boolean embedded : new boolean[]{false, true}) {
            var infinite = new Script(Double.POSITIVE_INFINITY, .5, .75, .5);
            var gaussian = attach(infinite, embedded);
            same(EXPECTED, gaussian.nextGaussian()); same(0, gaussian.nextGaussian()); assertEquals(4, infinite.calls);
            for (double other : new double[]{.5, Double.longBitsToDouble(0x7ff8000000000052L)}) {
                var nan = new Script(Double.longBitsToDouble(0x7ff8000000000042L), other);
                gaussian = attach(nan, embedded);
                assertTrue(Double.isNaN(gaussian.nextGaussian()));
                assertTrue(Double.isNaN(gaussian.nextGaussian())); assertEquals(2, nan.calls);
            }
        }
    }
    @Test void failedOuterCallbackKeepsTheCacheProducedByActualReentry() throws Exception {
        for (boolean embedded : new boolean[]{false, true}) {
            var source = new Script(.75, .75, .5, .5);
            source.reenterAt = 0; source.failAt = 3;
            var gaussian = attach(source, embedded);
            assertSame(source.sentinel, assertThrows(IOException.class, gaussian::nextGaussian));
            same(0, gaussian.nextGaussian()); assertEquals(4, source.calls);
        }
    }
}
