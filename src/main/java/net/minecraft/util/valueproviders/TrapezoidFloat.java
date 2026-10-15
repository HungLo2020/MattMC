package net.minecraft.util.valueproviders;

import net.minecraft.util.RandomSource;

public class TrapezoidFloat extends FloatProvider {
	private final float min;
	private final float max;
	private final float plateau;

	public static TrapezoidFloat of(float f, float g, float h) {
		return new TrapezoidFloat(f, g, h);
	}

	private TrapezoidFloat(float f, float g, float h) {
		this.min = f;
		this.max = g;
		this.plateau = h;
	}

	public float plateau() {
		return this.plateau;
	}

	@Override
	public float sample(RandomSource randomSource) {
		float f = this.max - this.min;
		float g = (f - this.plateau) / 2.0F;
		float h = f - g;
		return this.min + randomSource.nextFloat() * h + randomSource.nextFloat() * g;
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
		return FloatProviderType.TRAPEZOID;
	}

	public String toString() {
		return "trapezoid(" + this.plateau + ") in [" + this.min + "-" + this.max + "]";
	}
}
