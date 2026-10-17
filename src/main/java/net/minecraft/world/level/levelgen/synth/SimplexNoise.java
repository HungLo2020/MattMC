package net.minecraft.world.level.levelgen.synth;

import net.minecraft.util.RandomSource;

public class SimplexNoise {
	private final NativeNoiseSeed seed;
	public final double xo;
	public final double yo;
	public final double zo;

	public SimplexNoise(RandomSource randomSource) {
        this.seed = new NativeNoiseSeed(randomSource, true);
        this.xo = seed.offset(0);
        this.yo = seed.offset(1);
        this.zo = seed.offset(2);
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
		seed.write(state, index, 1.0);
	}

}
