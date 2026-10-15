package net.minecraft.world.item.enchantment;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.core.Registry;
import net.minecraft.resources.NativeData;
import net.minecraft.resources.NativeValues;
import net.minecraft.util.Mth;

public interface LevelBasedValue {
	/** Rust decodes, validates and encodes values (`content/data/values.rs`). */
	NativeValues.ValueCodec<LevelBasedValue> CODEC = NativeValues.codec(NativeValues.Kind.LEVEL_BASED_VALUE, LevelBasedValue::project, LevelBasedValue::unproject);

	/** The type registry's entries: each case's fields as the native codec reads them. */
	static MapCodec<? extends LevelBasedValue> bootstrap(Registry<MapCodec<? extends LevelBasedValue>> registry) {
		Registry.register(registry, "clamped", CODEC.dispatchCase("minecraft:clamped"));
		Registry.register(registry, "fraction", CODEC.dispatchCase("minecraft:fraction"));
		Registry.register(registry, "levels_squared", CODEC.dispatchCase("minecraft:levels_squared"));
		Registry.register(registry, "linear", CODEC.dispatchCase("minecraft:linear"));
		return Registry.register(registry, "lookup", CODEC.dispatchCase("minecraft:lookup"));
	}

	/** A value from its decoded form: a constant, or a typed value. */
	static LevelBasedValue project(Object value) {
		NativeData.Variant either = (NativeData.Variant)value;
		if (either.index() == 0) {
			return new LevelBasedValue.Constant((Float)either.fields()[0]);
		}
		NativeData.Variant typed = (NativeData.Variant)either.fields()[0];
		Object[] f = typed.fields();
		return switch (typed.index()) {
			case 0 -> new LevelBasedValue.Clamped(project(f[0]), (Float)f[1], (Float)f[2]);
			case 1 -> new LevelBasedValue.Fraction(project(f[0]), project(f[1]));
			case 2 -> new LevelBasedValue.LevelsSquared((Float)f[0]);
			case 3 -> new LevelBasedValue.Linear((Float)f[0], (Float)f[1]);
			case 4 -> new LevelBasedValue.Lookup(NativeData.list(f[0]).stream().map(Float.class::cast).toList(), project(f[1]));
			default -> throw new IllegalStateException("Unknown level-based value case " + typed.index());
		};
	}

	/** A value's decoded form, for Rust to check and encode. */
	static Object unproject(LevelBasedValue value) {
		return switch (value) {
			case LevelBasedValue.Constant v -> new NativeData.Variant(0, new Object[]{v.value()});
			case LevelBasedValue.Clamped v -> typed(0, unproject(v.value()), v.min(), v.max());
			case LevelBasedValue.Fraction v -> typed(1, unproject(v.numerator()), unproject(v.denominator()));
			case LevelBasedValue.LevelsSquared v -> typed(2, v.added());
			case LevelBasedValue.Linear v -> typed(3, v.base(), v.perLevelAboveFirst());
			case LevelBasedValue.Lookup v -> typed(4, List.copyOf(v.values()), unproject(v.fallback()));
			default -> throw new IllegalArgumentException("Unknown level-based value " + value);
		};
	}

	private static NativeData.Variant typed(int index, Object... fields) {
		return new NativeData.Variant(1, new Object[]{new NativeData.Variant(index, fields)});
	}

	static LevelBasedValue.Constant constant(float f) {
		return new LevelBasedValue.Constant(f);
	}

	static LevelBasedValue.Linear perLevel(float f, float g) {
		return new LevelBasedValue.Linear(f, g);
	}

	static LevelBasedValue.Linear perLevel(float f) {
		return perLevel(f, f);
	}

	static LevelBasedValue.Lookup lookup(List<Float> list, LevelBasedValue levelBasedValue) {
		return new LevelBasedValue.Lookup(list, levelBasedValue);
	}

	float calculate(int i);

	public record Clamped(LevelBasedValue value, float min, float max) implements LevelBasedValue {
		@Override
		public float calculate(int i) {
			return Mth.clamp(this.value.calculate(i), this.min, this.max);
		}
	}

	public record Constant(float value) implements LevelBasedValue {
		@Override
		public float calculate(int i) {
			return this.value;
		}
	}

	public record Fraction(LevelBasedValue numerator, LevelBasedValue denominator) implements LevelBasedValue {
		@Override
		public float calculate(int i) {
			float f = this.denominator.calculate(i);
			return f == 0.0F ? 0.0F : this.numerator.calculate(i) / f;
		}
	}

	public record LevelsSquared(float added) implements LevelBasedValue {
		@Override
		public float calculate(int i) {
			return Mth.square(i) + this.added;
		}
	}

	public record Linear(float base, float perLevelAboveFirst) implements LevelBasedValue {
		@Override
		public float calculate(int i) {
			return this.base + this.perLevelAboveFirst * (i - 1);
		}
	}

	public record Lookup(List<Float> values, LevelBasedValue fallback) implements LevelBasedValue {
		@Override
		public float calculate(int i) {
			return i <= this.values.size() ? (Float)this.values.get(i - 1) : this.fallback.calculate(i);
		}
	}
}
