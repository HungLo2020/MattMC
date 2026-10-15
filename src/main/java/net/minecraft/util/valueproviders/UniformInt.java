package net.minecraft.util.valueproviders;

import net.minecraft.resources.NativeData;
import net.minecraft.resources.NativeValues;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

public class UniformInt extends IntProvider {
	/** The uniform case's fields without a type (`content/data/values.rs`). */
	public static final NativeValues.ValueCodec<UniformInt> CODEC = NativeValues.codec(
		NativeValues.Kind.UNIFORM_INT,
		value -> UniformInt.of((Integer)NativeData.record(value)[0], (Integer)NativeData.record(value)[1]),
		uniform -> new Object[]{uniform.getMinValue(), uniform.getMaxValue()}
	);
	private final int minInclusive;
	private final int maxInclusive;

	private UniformInt(int i, int j) {
		this.minInclusive = i;
		this.maxInclusive = j;
	}

	public static UniformInt of(int i, int j) {
		return new UniformInt(i, j);
	}

	@Override
	public int sample(RandomSource randomSource) {
		return Mth.randomBetweenInclusive(randomSource, this.minInclusive, this.maxInclusive);
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
		return IntProviderType.UNIFORM;
	}

	public String toString() {
		return "[" + this.minInclusive + "-" + this.maxInclusive + "]";
	}
}
