package net.minecraft.world.level.levelgen.synth;

import com.google.common.annotations.VisibleForTesting;
import net.minecraft.util.RandomSource;

public final class ImprovedNoise {
	private final byte[] p;
	public final double xo;
	public final double yo;
	public final double zo;

	public ImprovedNoise(RandomSource randomSource) {
		this.xo = randomSource.nextDouble() * 256.0;
		this.yo = randomSource.nextDouble() * 256.0;
		this.zo = randomSource.nextDouble() * 256.0;
		this.p = new byte[256];

		for (int i = 0; i < 256; i++) {
			this.p[i] = (byte)i;
		}

		for (int i = 0; i < 256; i++) {
			int j = randomSource.nextInt(256 - i);
			byte b = this.p[i];
			this.p[i] = this.p[i + j];
			this.p[i + j] = b;
		}
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
		NoiseUtils.parityNoiseOctaveConfigString(stringBuilder, this.xo, this.yo, this.zo, this.p);
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

	void writeNative(java.lang.foreign.MemorySegment state, int index, double amplitude) {
		NativeNoise.octave(state, index, xo, yo, zo, amplitude, p);
	}

}
