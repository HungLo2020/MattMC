package net.minecraft.util.valueproviders;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

public class ClampedNormalInt extends IntProvider {
	private final float mean;
	private final float deviation;
	private final int minInclusive;
	private final int maxInclusive;

	public static ClampedNormalInt of(float f, float g, int i, int j) {
		return new ClampedNormalInt(f, g, i, j);
	}

	private ClampedNormalInt(float f, float g, int i, int j) {
		this.mean = f;
		this.deviation = g;
		this.minInclusive = i;
		this.maxInclusive = j;
	}

	@Override
	public int sample(RandomSource randomSource) {
		return sample(randomSource, this.mean, this.deviation, this.minInclusive, this.maxInclusive);
	}

	public static int sample(RandomSource randomSource, float f, float g, float h, float i) {
		return (int)Mth.clamp(Mth.normal(randomSource, f, g), h, i);
	}

	@Override
	public int getMinValue() {
		return this.minInclusive;
	}

	@Override
	public int getMaxValue() {
		return this.maxInclusive;
	}

	@Override
	public IntProviderType<?> getType() {
		return IntProviderType.CLAMPED_NORMAL;
	}

	public String toString() {
		return "normal(" + this.mean + ", " + this.deviation + ") in [" + this.minInclusive + "-" + this.maxInclusive + "]";
	}

	public float mean() {
		return this.mean;
	}

	public float deviation() {
		return this.deviation;
	}
}
