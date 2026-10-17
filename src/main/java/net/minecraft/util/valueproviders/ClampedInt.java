package net.minecraft.util.valueproviders;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

public class ClampedInt extends IntProvider {
	private final IntProvider source;
	private final int minInclusive;
	private final int maxInclusive;

	public static ClampedInt of(IntProvider intProvider, int i, int j) {
		return new ClampedInt(intProvider, i, j);
	}

	public ClampedInt(IntProvider intProvider, int i, int j) {
		this.source = intProvider;
		this.minInclusive = i;
		this.maxInclusive = j;
	}

	@Override
	public int sample(RandomSource randomSource) {
		return Mth.clamp(this.source.sample(randomSource), this.minInclusive, this.maxInclusive);
	}

	@Override
	public int getMinValue() {
		return Math.max(this.minInclusive, this.source.getMinValue());
	}

	@Override
	public int getMaxValue() {
		return Math.min(this.maxInclusive, this.source.getMaxValue());
	}

	@Override
	public IntProviderType<?> getType() {
		return IntProviderType.CLAMPED;
	}

	public IntProvider source() {
		return this.source;
	}

	public int minInclusive() {
		return this.minInclusive;
	}

	public int maxInclusive() {
		return this.maxInclusive;
	}
}
