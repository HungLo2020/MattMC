package net.minecraft.util.valueproviders;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.NativeData;
import net.minecraft.resources.NativeValues;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.Weighted;
import net.minecraft.util.random.WeightedList;

public abstract class IntProvider {
	/** Rust decodes, validates and encodes providers (`content/data/values.rs`). */
	public static final NativeValues.ValueCodec<IntProvider> CODEC = NativeValues.codec(NativeValues.Kind.INT_PROVIDER, IntProvider::project, IntProvider::unproject);
	public static final Codec<IntProvider> NON_NEGATIVE_CODEC = codec(0, Integer.MAX_VALUE);
	public static final Codec<IntProvider> POSITIVE_CODEC = codec(1, Integer.MAX_VALUE);

	public static Codec<IntProvider> codec(int i, int j) {
		return CODEC.bounded(i, j);
	}

	public static <T extends IntProvider> Codec<T> validateCodec(int i, int j, Codec<T> codec) {
		return codec.validate(intProvider -> validate(i, j, intProvider));
	}

	private static <T extends IntProvider> DataResult<T> validate(int i, int j, T intProvider) {
		if (intProvider.getMinValue() < i) {
			return DataResult.error(() -> "Value provider too low: " + i + " [" + intProvider.getMinValue() + "-" + intProvider.getMaxValue() + "]");
		} else {
			return intProvider.getMaxValue() > j
				? DataResult.error(() -> "Value provider too high: " + j + " [" + intProvider.getMinValue() + "-" + intProvider.getMaxValue() + "]")
				: DataResult.success(intProvider);
		}
	}

	/** A provider from its decoded value: a constant, or a typed provider. */
	public static IntProvider project(Object value) {
		NativeData.Variant either = (NativeData.Variant)value;
		if (either.index() == 0) {
			return ConstantInt.of((Integer)either.fields()[0]);
		}
		NativeData.Variant typed = (NativeData.Variant)either.fields()[0];
		Object[] f = typed.fields();
		return switch (typed.index()) {
			case 0 -> ConstantInt.of((Integer)f[0]);
			case 1 -> UniformInt.of((Integer)f[0], (Integer)f[1]);
			case 2 -> BiasedToBottomInt.of((Integer)f[0], (Integer)f[1]);
			case 3 -> ClampedInt.of(project(f[0]), (Integer)f[1], (Integer)f[2]);
			case 4 -> {
				List<Weighted<IntProvider>> entries = new ArrayList<>();
				for (Object entry : NativeData.list(f[0])) {
					Object[] e = NativeData.record(entry);
					entries.add(new Weighted<>(project(e[0]), (Integer)e[1]));
				}
				yield new WeightedListInt(WeightedList.of(entries));
			}
			case 5 -> ClampedNormalInt.of((Float)f[0], (Float)f[1], (Integer)f[2], (Integer)f[3]);
			default -> throw new IllegalStateException("Unknown int provider case " + typed.index());
		};
	}

	/** A provider's decoded value, for Rust to check and encode. */
	public static Object unproject(IntProvider provider) {
		return switch (provider) {
			case ConstantInt p -> new NativeData.Variant(0, new Object[]{p.getValue()});
			case UniformInt p -> typed(1, p.getMinValue(), p.getMaxValue());
			case BiasedToBottomInt p -> typed(2, p.getMinValue(), p.getMaxValue());
			case ClampedInt p -> typed(3, unproject(p.source()), p.minInclusive(), p.maxInclusive());
			case WeightedListInt p -> {
				List<Object> entries = new ArrayList<>();
				for (Weighted<IntProvider> entry : p.distribution().unwrap()) {
					entries.add(new Object[]{unproject(entry.value()), entry.weight()});
				}
				yield typed(4, entries);
			}
			case ClampedNormalInt p -> typed(5, p.mean(), p.deviation(), p.getMinValue(), p.getMaxValue());
			default -> throw new IllegalArgumentException("Unknown int provider " + provider);
		};
	}

	private static NativeData.Variant typed(int index, Object... fields) {
		return new NativeData.Variant(1, new Object[]{new NativeData.Variant(index, fields)});
	}

	public abstract int sample(RandomSource randomSource);

	public abstract int getMinValue();

	public abstract int getMaxValue();

	public abstract IntProviderType<?> getType();
}
