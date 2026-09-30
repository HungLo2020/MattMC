package net.minecraft.world.level.levelgen.synth;

import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.NativeDensityMath;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NativeNoiseStateTest {
    @Test
    void bridgeRetainsIdentityAndReadOnlyState() {
        var noise = NormalNoise.create(RandomSource.create(42), -3, 1.0, 0.5, 1.0);
        NativeNoiseState state = noise.nativeState();
        assertSame(noise.nativeNoise(), state);
        assertSame(state, noise.nativeState());
        assertTrue(state.state().isReadOnly());
        assertEquals(80L + 1320L * state.octaveCount(), state.state().byteSize());
        assertTrue(state.critical());
        for (int i = -100; i <= 100; i++) {
            assertEquals(Double.doubleToLongBits(noise.getValue(i * 0.25, i * 0.5, -i * 0.25)),
                Double.doubleToLongBits(NativeDensityMath.noise(noise, 1, i, i, -i, 0, 0, 0, 0.25, 0.5)));
        }
    }

    @Test
    void changedAmplitudesPublishNewStateWithoutMutatingOldSnapshot() {
        var amplitudes = new DoubleArrayList(new double[]{1.0, 0.5, 1.0});
        var noise = NormalNoise.create(RandomSource.create(42), new NormalNoise.NoiseParameters(-3, amplitudes));
        NativeNoiseState before = noise.nativeState();
        byte[] bytes = before.state().toArray(java.lang.foreign.ValueLayout.JAVA_BYTE);
        amplitudes.set(1, 0.25);
        NativeNoiseState after = noise.nativeState();
        assertNotSame(before, after);
        assertSame(after, noise.nativeState());
        assertArrayEquals(bytes, before.state().toArray(java.lang.foreign.ValueLayout.JAVA_BYTE));
        assertTrue(after.state().isReadOnly());
    }
}
