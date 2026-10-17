package net.minecraft.util.valueproviders;

import net.minecraft.util.RandomSource;

public class BiasedToBottomInt extends IntProvider {
	private final int minInclusive;
	private final int maxInclusive;

	private BiasedToBottomInt(int i, int j) {
		this.minInclusive = i;
		this.maxInclusive = j;
	}

	public static BiasedToBottomInt of(int i, int j) {
		return new BiasedToBottomInt(i, j);
	}

	@Override
	public int sample(RandomSource randomSource) {
		return this.minInclusive + randomSource.nextInt(randomSource.nextInt(this.maxInclusive - this.minInclusive + 1) + 1);
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
		return IntProviderType.BIASED_TO_BOTTOM;
	}

	public String toString() {
		return "[" + this.minInclusive + "-" + this.maxInclusive + "]";
	}
}
