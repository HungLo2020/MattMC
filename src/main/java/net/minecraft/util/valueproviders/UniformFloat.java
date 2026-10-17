package net.minecraft.util.valueproviders;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

public class UniformFloat extends FloatProvider {
	private final float minInclusive;
	private final float maxExclusive;

	private UniformFloat(float f, float g) {
		this.minInclusive = f;
		this.maxExclusive = g;
	}

	public static UniformFloat of(float f, float g) {
		if (g <= f) {
			throw new IllegalArgumentException("Max must exceed min");
		} else {
			return new UniformFloat(f, g);
		}
	}

	@Override
	public float sample(RandomSource randomSource) {
		return Mth.randomBetween(randomSource, this.minInclusive, this.maxExclusive);
	}

	@Override
	public float getMinValue() {
		return this.minInclusive;
	}

	@Override
	public float getMaxValue() {
		return this.maxExclusive;
	}

	@Override
	public FloatProviderType<?> getType() {
		return FloatProviderType.UNIFORM;
	}

	public String toString() {
		return "[" + this.minInclusive + "-" + this.maxExclusive + "]";
	}
}
