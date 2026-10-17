package net.minecraft.world.level.levelgen.synth;

import com.google.common.annotations.VisibleForTesting;
import net.minecraft.util.RandomSource;

public final class ImprovedNoise {
	private final NativeNoiseSeed seed;
	public final double xo;
	public final double yo;
	public final double zo;

	public ImprovedNoise(RandomSource randomSource) {
        this.seed = new NativeNoiseSeed(randomSource, false);
        this.xo = seed.offset(0);
        this.yo = seed.offset(1);
        this.zo = seed.offset(2);
    }

	public double noise(double d, double e, double f) {
		return nativeNoise().sample(d, e, f);
	}

	@Deprecated
	public double noise(double d, double e, double f, double g, double h) {
		return nativeNoise().sample(d, e, f, g, h, 0);
	}

	public double noiseWithDerivative(double d, double e, double f, double[] ds) {
		return nativeNoise().derivative(d, e, f, ds);
	}

	@VisibleForTesting
	public void parityConfigString(StringBuilder stringBuilder) {
		NoiseUtils.parityNoiseOctaveConfigString(stringBuilder, this.xo, this.yo, this.zo, this.seed.diagnosticPermutation());
	}

	private volatile NativeNoise nativeNoise;
	NativeNoise nativeNoise() {
		NativeNoise value = nativeNoise;
		if (value == null) {
			synchronized (this) {
				value = nativeNoise;
				if (value == null) {
					var state = NativeNoise.allocate(1, 1, 0, 0);
					writeNative(state, 0, 1.0);
					value = new NativeNoise(state);
					nativeNoise = value;
				}
			}
		}
		return value;
	}

    void bindPerlin(long driver) { seed.bindPerlin(driver); }

	void writeNative(java.lang.foreign.MemorySegment state, int index, double amplitude) {
		seed.write(state, index, amplitude);
	}

}
