package net.minecraft.world.level.levelgen;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Existing public raw-cache bridge; absent from the original Frozen API. */
final class GaussianRawStateContractTest {
    static MarsagliaPolarGaussian[] owners() {
        return new MarsagliaPolarGaussian[]{new XoroshiroRandomSource(42).nativeGaussian(),
            new MarsagliaPolarGaussian(new XoroshiroRandomSource(42)), new LegacyRandomSource(42).nativeGaussian()};
    }
    @Test void cachedRawNanPayloadSurvivesPopAndReset() {
        long bits = 0x7ff8000000000042L;
        for (var gaussian : owners()) {
            gaussian.nativeSet(true, Double.longBitsToDouble(bits));
            assertEquals(bits, Double.doubleToRawLongBits(gaussian.nextGaussian())); assertFalse(gaussian.nativeHas());
            assertEquals(bits, Double.doubleToRawLongBits(gaussian.nativeValue()));
            gaussian.reset(); assertEquals(bits, Double.doubleToRawLongBits(gaussian.nativeValue()));
        }
    }
    @Test void emptySignedZeroCacheRetainsItsValueBitsAfterReset() {
        for (var gaussian : owners()) {
            gaussian.nativeSet(false, -0.0); gaussian.reset();
            assertFalse(gaussian.nativeHas()); assertEquals(Long.MIN_VALUE, Double.doubleToRawLongBits(gaussian.nativeValue()));
        }
    }
    @Test void actualGeneratedCacheValueRemainsObservableAfterItIsPopped() {
        for (var gaussian : owners()) {
            gaussian.nextGaussian(); assertTrue(gaussian.nativeHas());
            long bits = Double.doubleToRawLongBits(gaussian.nativeValue());
            assertEquals(bits, Double.doubleToRawLongBits(gaussian.nextGaussian())); assertFalse(gaussian.nativeHas());
            assertEquals(bits, Double.doubleToRawLongBits(gaussian.nativeValue()));
            gaussian.reset(); assertEquals(bits, Double.doubleToRawLongBits(gaussian.nativeValue()));
        }
    }
    @Test void nullSourceStillReturnsAnAlreadyCachedValueBeforeItsNextDrawFails() {
        var gaussian = new MarsagliaPolarGaussian(null);
        gaussian.nativeSet(true, -0.0);
        assertEquals(Long.MIN_VALUE, Double.doubleToRawLongBits(gaussian.nextGaussian()));
        assertThrows(NullPointerException.class, gaussian::nextGaussian);
    }
}
