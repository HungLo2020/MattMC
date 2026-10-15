package net.minecraft.util.valueproviders;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

public class ClampedNormalFloat extends FloatProvider {
	private final float mean;
	private final float deviation;
	private final float min;
	private final float max;

	public static ClampedNormalFloat of(float f, float g, float h, float i) {
		return new ClampedNormalFloat(f, g, h, i);
	}

	private ClampedNormalFloat(float f, float g, float h, float i) {
		this.mean = f;
		this.deviation = g;
		this.min = h;
		this.max = i;
	}

	@Override
	public float sample(RandomSource randomSource) {
		return sample(randomSource, this.mean, this.deviation, this.min, this.max);
	}

	public static float sample(RandomSource randomSource, float f, float g, float h, float i) {
		return Mth.clamp(Mth.normal(randomSource, f, g), h, i);
	}

	@Override
	public float getMinValue() {
		return this.min;
	}

	@Override
	public float getMaxValue() {
		return this.max;
	}

	@Override
	public FloatProviderType<?> getType() {
		return FloatProviderType.CLAMPED_NORMAL;
	}

	public String toString() {
		return "normal(" + this.mean + ", " + this.deviation + ") in [" + this.min + "-" + this.max + "]";
	}

	public float mean() {
		return this.mean;
	}

	public float deviation() {
		return this.deviation;
	}
}
