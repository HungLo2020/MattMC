package net.minecraft.world.level.levelgen.synth;

import it.unimi.dsi.fastutil.ints.IntRBTreeSet;
import it.unimi.dsi.fastutil.ints.IntSortedSet;
import java.util.List;
import net.minecraft.util.RandomSource;

public class PerlinSimplexNoise {
    private final NativeSimplexOctaves nativePlan;
    public PerlinSimplexNoise(RandomSource randomSource,List<Integer> list){this(randomSource,new IntRBTreeSet(list));}
    private PerlinSimplexNoise(RandomSource randomSource,IntSortedSet octaves){nativePlan=new NativeSimplexOctaves(randomSource,octaves);}

	public double getValue(double d, double e, boolean bl) {
		return nativeNoise().sample(d, e, 0.0, 0.0, 0.0, bl ? 1 : 0);
	}

	private volatile NativeNoise nativeNoise;
	/** The validated native state, for Rust callers that evaluate this noise directly. */
	public NativeNoiseState nativeState() { return nativeNoise(); }
	NativeNoise nativeNoise() {
		NativeNoise value = nativeNoise;
		if (value == null) {
			synchronized (this) {
				value = nativeNoise;
				if (value == null) {
                    var state=nativePlan.allocate();
                    nativePlan.write(state);
					value = new NativeNoise(state);
					nativeNoise = value;
				}
			}
		}
		return value;
	}

}
