package net.minecraft.world.level.levelgen.synth;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryFileCodec;
import net.minecraft.util.RandomSource;

public class NormalNoise {
	private final PerlinNoise first;
	private final PerlinNoise second;
	private final double maxValue;
	private final NormalNoise.NoiseParameters parameters;

	@Deprecated
	public static NormalNoise createLegacyNetherBiome(RandomSource randomSource, NormalNoise.NoiseParameters noiseParameters) {
		return new NormalNoise(randomSource, noiseParameters, false);
	}

	public static NormalNoise create(RandomSource randomSource, int i, double... ds) {
		return create(randomSource, new NormalNoise.NoiseParameters(i, new DoubleArrayList(ds)));
	}

	public static NormalNoise create(RandomSource randomSource, NormalNoise.NoiseParameters noiseParameters) {
		return new NormalNoise(randomSource, noiseParameters, true);
	}

    private final NativeNormal nativePlan;
    private NormalNoise(RandomSource randomSource,NormalNoise.NoiseParameters noiseParameters,boolean modern){
        int firstOctave=noiseParameters.firstOctave;DoubleList amplitudes=noiseParameters.amplitudes;
        this.parameters=noiseParameters;this.nativePlan=new NativeNormal(randomSource,firstOctave,amplitudes,modern);
        this.first=nativePlan.first;this.second=nativePlan.second;this.maxValue=nativePlan.max;
    }

	public double maxValue() {
		return this.maxValue;
	}


	public double getValue(double d, double e, double f) {
		return nativeNoise().sample(d, e, f);
	}

	public NormalNoise.NoiseParameters parameters() {
		return this.parameters;
	}

	@VisibleForTesting
	public void parityConfigString(StringBuilder stringBuilder) {
		stringBuilder.append("NormalNoise {");
		stringBuilder.append("first: ");
		this.first.parityConfigString(stringBuilder);
		stringBuilder.append(", second: ");
		this.second.parityConfigString(stringBuilder);
		stringBuilder.append("}");
	}

	public record NoiseParameters(int firstOctave, DoubleList amplitudes) {
		public static final Codec<NormalNoise.NoiseParameters> DIRECT_CODEC = RecordCodecBuilder.create(
			instance -> instance.group(
					Codec.INT.fieldOf("firstOctave").forGetter(NormalNoise.NoiseParameters::firstOctave),
					Codec.DOUBLE.listOf().fieldOf("amplitudes").forGetter(NormalNoise.NoiseParameters::amplitudes)
				)
				.apply(instance, NormalNoise.NoiseParameters::new)
		);
		public static final Codec<Holder<NormalNoise.NoiseParameters>> CODEC = RegistryFileCodec.create(Registries.NOISE, DIRECT_CODEC);

		public NoiseParameters(int i, List<Double> list) {
			this(i, new DoubleArrayList(list));
		}

		public NoiseParameters(int i, double d, double... ds) {
			this(i, Util.make(new DoubleArrayList(ds), doubleArrayList -> doubleArrayList.add(0, d)));
		}
	}

	private volatile NativeNoise nativeNoise;

    /** Internal read-only state bridge for compiled density programs. */
    public NativeNoiseState nativeState() { return nativeNoise(); }

	NativeNoise nativeNoise() {
		NativeNoise value = nativeNoise;
		if (value == null || !nativePlan.matches(parameters.amplitudes,value)) {
			synchronized (this) {
				value = nativeNoise;
				if (value == null || !nativePlan.matches(parameters.amplitudes,value)) {
                    var state=nativePlan.allocate();
                    nativePlan.write(parameters.amplitudes,state);
					value = new NativeNoise(state);
					nativeNoise = value;
				}
			}
		}
		return value;
	}

	/** Evaluate packed xyz triples in one bounded native batch at a time. */
	public void fill(double[] xyz, double[] output) {
		nativeNoise().batch(xyz, output, 0.0, 0.0, 0);
	}

}
