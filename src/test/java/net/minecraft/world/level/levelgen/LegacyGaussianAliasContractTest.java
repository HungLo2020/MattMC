package net.minecraft.world.level.levelgen;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** The public VM draw target and the real cached value have separate identities. */
final class LegacyGaussianAliasContractTest {
    static void target(MarsagliaPolarGaussian gaussian, net.minecraft.util.RandomSource source) throws Exception {
        var field = MarsagliaPolarGaussian.class.getDeclaredField("randomSource");
        field.setAccessible(true); field.set(gaussian, source);
    }
    static MarsagliaPolarGaussian actual(LegacyRandomSource source) throws Exception {
        var field = LegacyRandomSource.class.getDeclaredField("gaussianSource");
        field.setAccessible(true); return (MarsagliaPolarGaussian) field.get(source);
    }
    static void same(double expected, double actual) {
        assertEquals(Double.doubleToRawLongBits(expected), Double.doubleToRawLongBits(actual));
    }
    @Test void changedDrawTargetKeepsBothAlreadyProducedCachesIndependent() throws Exception {
        var a = new LegacyRandomSource(42); var b = new LegacyRandomSource(19);
        var controlA = new LegacyRandomSource(42); var controlB = new LegacyRandomSource(19);
        var ga = actual(a); var gb = actual(b); var ca = actual(controlA); var cb = actual(controlB);
        same(ca.nextGaussian(), ga.nextGaussian()); same(cb.nextGaussian(), gb.nextGaussian());
        target(ga, b);
        same(ca.nextGaussian(), ga.nextGaussian()); same(cb.nextGaussian(), gb.nextGaussian());
        same(cb.nextGaussian(), ga.nextGaussian()); same(cb.nextGaussian(), ga.nextGaussian());
    }
    @Test void nullDrawTargetDoesNotDiscardTheAlreadyProducedSecondValue() throws Exception {
        var source = new LegacyRandomSource(42); var control = new LegacyRandomSource(42);
        var gaussian = actual(source); var expected = actual(control);
        same(expected.nextGaussian(), gaussian.nextGaussian());
        target(gaussian, null);
        same(expected.nextGaussian(), gaussian.nextGaussian());
        assertThrows(NullPointerException.class, gaussian::nextGaussian);
    }
}
