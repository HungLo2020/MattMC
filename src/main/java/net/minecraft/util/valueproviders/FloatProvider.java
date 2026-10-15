package net.minecraft.util.valueproviders;

import com.mojang.serialization.Codec;
import net.minecraft.resources.NativeData;
import net.minecraft.resources.NativeValues;

public abstract class FloatProvider implements SampledFloat {
	/** Rust decodes, validates and encodes providers (`content/data/values.rs`). */
	public static final NativeValues.ValueCodec<FloatProvider> CODEC = NativeValues.codec(NativeValues.Kind.FLOAT_PROVIDER, FloatProvider::project, FloatProvider::unproject);

	public static Codec<FloatProvider> codec(float f, float g) {
		return CODEC.bounded(f, g);
	}

	/** A provider from its decoded value: a constant, or a typed provider. */
	public static FloatProvider project(Object value) {
		NativeData.Variant either = (NativeData.Variant)value;
		if (either.index() == 0) {
			return ConstantFloat.of((Float)either.fields()[0]);
		}
		NativeData.Variant typed = (NativeData.Variant)either.fields()[0];
		Object[] f = typed.fields();
		return switch (typed.index()) {
			case 0 -> ConstantFloat.of((Float)f[0]);
			case 1 -> UniformFloat.of((Float)f[0], (Float)f[1]);
			case 2 -> ClampedNormalFloat.of((Float)f[0], (Float)f[1], (Float)f[2], (Float)f[3]);
			case 3 -> TrapezoidFloat.of((Float)f[0], (Float)f[1], (Float)f[2]);
			default -> throw new IllegalStateException("Unknown float provider case " + typed.index());
		};
	}

	/** A provider's decoded value, for Rust to check and encode. */
	public static Object unproject(FloatProvider provider) {
		return switch (provider) {
			case ConstantFloat p -> new NativeData.Variant(0, new Object[]{p.getValue()});
			case UniformFloat p -> typed(1, p.getMinValue(), p.getMaxValue());
			case ClampedNormalFloat p -> typed(2, p.mean(), p.deviation(), p.getMinValue(), p.getMaxValue());
			case TrapezoidFloat p -> typed(3, p.getMinValue(), p.getMaxValue(), p.plateau());
			default -> throw new IllegalArgumentException("Unknown float provider " + provider);
		};
	}

	private static NativeData.Variant typed(int index, Object... fields) {
		return new NativeData.Variant(1, new Object[]{new NativeData.Variant(index, fields)});
	}

	public abstract float getMinValue();

	public abstract float getMaxValue();

	public abstract FloatProviderType<?> getType();
}
