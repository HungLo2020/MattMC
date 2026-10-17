package net.minecraft.world.level.levelgen.synth;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import it.unimi.dsi.fastutil.ints.IntRBTreeSet;
import it.unimi.dsi.fastutil.ints.IntSortedSet;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

public class PerlinNoise {
	private static final int ROUND_OFF = 33554432;
	private final ImprovedNoise[] noiseLevels;
	private final int firstOctave;
	private final DoubleList amplitudes;
	private final double lowestFreqValueFactor;
	private final double lowestFreqInputFactor;
	private final double maxValue;

	@Deprecated
	public static PerlinNoise createLegacyForBlendedNoise(RandomSource randomSource, IntStream intStream) {
		return new PerlinNoise(
			randomSource, makeAmplitudes(new IntRBTreeSet((Collection<? extends Integer>)intStream.boxed().collect(ImmutableList.toImmutableList()))), false
		);
	}

	@Deprecated
	public static PerlinNoise createLegacyForLegacyNetherBiome(RandomSource randomSource, int i, DoubleList doubleList) {
		return new PerlinNoise(randomSource, Pair.of(i, doubleList), false);
	}

	public static PerlinNoise create(RandomSource randomSource, IntStream intStream) {
		return create(randomSource, (List<Integer>)intStream.boxed().collect(ImmutableList.toImmutableList()));
	}

	public static PerlinNoise create(RandomSource randomSource, List<Integer> list) {
		return new PerlinNoise(randomSource, makeAmplitudes(new IntRBTreeSet(list)), true);
	}

	public static PerlinNoise create(RandomSource randomSource, int i, double d, double... ds) {
		DoubleArrayList doubleArrayList = new DoubleArrayList(ds);
		doubleArrayList.add(0, d);
		return new PerlinNoise(randomSource, Pair.of(i, doubleArrayList), true);
	}

	public static PerlinNoise create(RandomSource randomSource, int i, DoubleList doubleList) {
		return new PerlinNoise(randomSource, Pair.of(i, doubleList), true);
	}

    private static Pair<Integer, DoubleList> makeAmplitudes(IntSortedSet octaves) {
        return NativePerlin.amplitudes(octaves);
    }

    void bindNormal(long driver,int slot){nativePlan.bindNormal(driver,slot);}
    void bindBlended(long driver,int slot){nativePlan.bindBlended(driver,slot);}
    DoubleList nativeAmplitudes(){return amplitudes;}
    private final NativePerlin nativePlan;
    protected PerlinNoise(RandomSource randomSource, Pair<Integer, DoubleList> pair, boolean modern) {
        this.firstOctave = pair.getFirst();
        this.amplitudes = pair.getSecond();
        int count = this.amplitudes.size();
        // Genuine public-octave identities retain their original parent custody.
        this.noiseLevels = new ImprovedNoise[count];
        this.nativePlan = new NativePerlin(randomSource, amplitudes, firstOctave, modern, noiseLevels);
        this.lowestFreqInputFactor = nativePlan.input;
        this.lowestFreqValueFactor = nativePlan.value;
        this.maxValue = nativePlan.max;
    }

	protected double maxValue() {
		return this.maxValue;
	}


	public double getValue(double d, double e, double f) {
		return this.getValue(d, e, f, 0.0, 0.0, false);
	}

	@Deprecated
	public double getValue(double d, double e, double f, double g, double h, boolean bl) {
		return nativeNoise().sample(d, e, f, g, h, bl ? 1 : 0);
	}

    public double maxBrokenValue(double value) {
        return nativePlan.edge(amplitudes, value);
    }

	@Nullable
	public ImprovedNoise getOctaveNoise(int i) {
		return this.noiseLevels[this.noiseLevels.length - 1 - i];
	}

	public static double wrap(double d) {
		return d - Mth.lfloor(d / 3.3554432E7 + 0.5) * 3.3554432E7;
	}

	protected int firstOctave() {
		return this.firstOctave;
	}

	protected DoubleList amplitudes() {
		return this.amplitudes;
	}

	@VisibleForTesting
	public void parityConfigString(StringBuilder stringBuilder) {
		stringBuilder.append("PerlinNoise{");
		List<String> list = this.amplitudes.stream().map(double_ -> String.format(Locale.ROOT, "%.2f", double_)).toList();
		stringBuilder.append("first octave: ").append(this.firstOctave).append(", amplitudes: ").append(list).append(", noise levels: [");

		for (int i = 0; i < this.noiseLevels.length; i++) {
			stringBuilder.append(i).append(": ");
			ImprovedNoise improvedNoise = this.noiseLevels[i];
			if (improvedNoise == null) {
				stringBuilder.append("null");
			} else {
				improvedNoise.parityConfigString(stringBuilder);
			}

			stringBuilder.append(", ");
		}

		stringBuilder.append("]");
		stringBuilder.append("}");
	}

	private volatile NativeNoise nativeNoise;
	NativeNoise nativeNoise() {
		NativeNoise value = nativeNoise;
		if (value == null || !matchesAmplitudes(value)) {
			synchronized (this) {
				value = nativeNoise;
				if (value == null || !matchesAmplitudes(value)) {
					var state = NativeNoise.allocate(2, noiseLevels.length, 0, 0, lowestFreqInputFactor, lowestFreqValueFactor);
					writeNative(state, 0);
					value = new NativeNoise(state);
					nativeNoise = value;
				}
			}
		}
		return value;
	}

	// The public factories accept mutable lists. Preserve changes made after first use.
    boolean matchesAmplitudes(NativeNoise value) {
        return nativePlan.matches(amplitudes, value);
    }

	int octaveCount() { return noiseLevels.length; }
	double inputFactor() { return lowestFreqInputFactor; }
	double valueFactor() { return lowestFreqValueFactor; }
    void writeNative(java.lang.foreign.MemorySegment state, int start) {
        nativePlan.write(amplitudes, state, start);
    }

}
