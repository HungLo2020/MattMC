package net.minecraft.world.level.levelgen.synth;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Locale;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;

public class BlendedNoise implements DensityFunction.SimpleFunction {
	private static final Codec<Double> SCALE_RANGE = Codec.doubleRange(0.001, 1000.0);
	private static final MapCodec<BlendedNoise> DATA_CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(
				SCALE_RANGE.fieldOf("xz_scale").forGetter(blendedNoise -> blendedNoise.xzScale),
				SCALE_RANGE.fieldOf("y_scale").forGetter(blendedNoise -> blendedNoise.yScale),
				SCALE_RANGE.fieldOf("xz_factor").forGetter(blendedNoise -> blendedNoise.xzFactor),
				SCALE_RANGE.fieldOf("y_factor").forGetter(blendedNoise -> blendedNoise.yFactor),
				Codec.doubleRange(1.0, 8.0).fieldOf("smear_scale_multiplier").forGetter(blendedNoise -> blendedNoise.smearScaleMultiplier)
			)
			.apply(instance, BlendedNoise::createUnseeded)
	);
	public static final KeyDispatchDataCodec<BlendedNoise> CODEC = KeyDispatchDataCodec.of(DATA_CODEC);
	private final PerlinNoise minLimitNoise;
	private final PerlinNoise maxLimitNoise;
	private final PerlinNoise mainNoise;
	private final double xzMultiplier;
	private final double yMultiplier;
	private final double xzFactor;
	private final double yFactor;
	private final double smearScaleMultiplier;
	private final double maxValue;
	private final double xzScale;
	private final double yScale;

	public static BlendedNoise createUnseeded(double d, double e, double f, double g, double h) {
		return new BlendedNoise(new XoroshiroRandomSource(0L), d, e, f, g, h);
	}

    private final NativeBlended nativePlan;
    private BlendedNoise(NativeBlended plan,double d,double e,double f,double g,double h){
        nativePlan=plan;minLimitNoise=plan.children[0];maxLimitNoise=plan.children[1];mainNoise=plan.children[2];
        xzScale=d;yScale=e;xzFactor=f;yFactor=g;smearScaleMultiplier=h;xzMultiplier=plan.xz;yMultiplier=plan.y;maxValue=plan.max;
    }
    @VisibleForTesting
    public BlendedNoise(RandomSource randomSource,double d,double e,double f,double g,double h){this(new NativeBlended(randomSource,d,e,f,g,h),d,e,f,g,h);}

	public BlendedNoise withNewRandom(RandomSource randomSource) {
		return new BlendedNoise(randomSource, this.xzScale, this.yScale, this.xzFactor, this.yFactor, this.smearScaleMultiplier);
	}

	@Override
	public double compute(DensityFunction.FunctionContext functionContext) {
		return nativeNoise().sample(functionContext.blockX(), functionContext.blockY(), functionContext.blockZ());
	}

    /** Raw block coordinates, sampled by the same native state as compute. */
    public void fillNative(double[] coordinates, double[] output) {
        fillNative(coordinates, output, output.length);
    }
    public void fillNative(double[] coordinates, double[] output, int count) {
        nativeNoise().batch(coordinates, output, count, 0., 0., 0);
    }

	@Override
	public double minValue() {
		return -this.maxValue();
	}

	@Override
	public double maxValue() {
		return this.maxValue;
	}

	@VisibleForTesting
	public void parityConfigString(StringBuilder stringBuilder) {
		stringBuilder.append("BlendedNoise{minLimitNoise=");
		this.minLimitNoise.parityConfigString(stringBuilder);
		stringBuilder.append(", maxLimitNoise=");
		this.maxLimitNoise.parityConfigString(stringBuilder);
		stringBuilder.append(", mainNoise=");
		this.mainNoise.parityConfigString(stringBuilder);
		stringBuilder.append(
				String.format(
					Locale.ROOT,
					", xzScale=%.3f, yScale=%.3f, xzMainScale=%.3f, yMainScale=%.3f, cellWidth=4, cellHeight=8",
					684.412,
					684.412,
					8.555150000000001,
					4.277575000000001
				)
			)
			.append('}');
	}

	@Override
	public KeyDispatchDataCodec<? extends DensityFunction> codec() {
		return CODEC;
	}

	private volatile NativeNoise nativeNoise;
    /** The state compute samples, for native density routers. */
    public NativeNoiseState nativeState() { return nativeNoise(); }
	NativeNoise nativeNoise() {
		NativeNoise value = nativeNoise;
		if (value == null) {
			synchronized (this) {
				value = nativeNoise;
				if (value == null) {
					var state = nativePlan.allocate();
                    nativePlan.write(state);
					value = new NativeNoise(state);
					nativeNoise = value;
				}
			}
		}
		return value;
	}

}
