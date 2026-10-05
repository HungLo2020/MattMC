package net.minecraft.world.level.levelgen.synth;

import net.minecraft.util.RandomSource;

public class SimplexNoise {
	private final int[] p = new int[512];
	public final double xo;
	public final double yo;
	public final double zo;

	public SimplexNoise(RandomSource randomSource) {
		this.xo = randomSource.nextDouble() * 256.0;
		this.yo = randomSource.nextDouble() * 256.0;
		this.zo = randomSource.nextDouble() * 256.0;
		int i = 0;

		while (i < 256) {
			this.p[i] = i++;
		}

		for (int ix = 0; ix < 256; ix++) {
			int j = randomSource.nextInt(256 - ix);
			int k = this.p[ix];
			this.p[ix] = this.p[j + ix];
			this.p[j + ix] = k;
		}
	}

	public double getValue(double d, double e) {
		return nativeNoise().simplex2(d, e);
	}

	public double getValue(double d, double e, double f) {
		return nativeNoise().sample(d, e, f, 0.0, 0.0, 1);
	}

    /** End island height: one bounded call for the original 25 by 25 sampling loop. */
    public float endIslandHeight(int x, int z) {
        return nativeNoise().endIslandHeight(x, z);
    }

	private volatile NativeNoise nativeNoise;
    /** The state endIslandHeight samples, for native density routers. */
    public NativeNoiseState nativeState() { return nativeNoise(); }
	NativeNoise nativeNoise() {
		NativeNoise value = nativeNoise;
		if (value == null) {
			synchronized (this) {
				value = nativeNoise;
				if (value == null) {
					var state = NativeNoise.allocate(5, 1, 0, 0);
					writeNative(state, 0);
					value = new NativeNoise(state);
					nativeNoise = value;
				}
			}
		}
		return value;
	}

	void writeNative(java.lang.foreign.MemorySegment state, int index) {
		byte[] permutation = new byte[256];
		for (int i = 0; i < 256; i++) permutation[i] = (byte)p[i];
		NativeNoise.octave(state, index, xo, yo, zo, 1.0, permutation);
	}

}
