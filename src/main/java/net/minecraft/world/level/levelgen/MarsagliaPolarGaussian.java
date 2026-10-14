package net.minecraft.world.level.levelgen;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

public class MarsagliaPolarGaussian {
	public final RandomSource randomSource;
	private double nextNextGaussian;
	private boolean haveNextNextGaussian;

	public MarsagliaPolarGaussian(RandomSource randomSource) {
		this.randomSource = randomSource;
	}

	/** Native decoration: the cached second value travels with the random state. */
	public boolean nativeHas() {
		return this.haveNextNextGaussian;
	}

	public double nativeValue() {
		return this.nextNextGaussian;
	}

	public void nativeSet(boolean has, double value) {
		this.haveNextNextGaussian = has;
		this.nextNextGaussian = value;
	}

	public void reset() {
		this.haveNextNextGaussian = false;
	}

	public double nextGaussian() {
		if (this.haveNextNextGaussian) {
			this.haveNextNextGaussian = false;
			return this.nextNextGaussian;
		} else {
			double d;
			double e;
			double f;
			do {
				d = 2.0 * this.randomSource.nextDouble() - 1.0;
				e = 2.0 * this.randomSource.nextDouble() - 1.0;
				f = Mth.square(d) + Mth.square(e);
			} while (f >= 1.0 || f == 0.0);

			double g = Math.sqrt(-2.0 * Math.log(f) / f);
			this.nextNextGaussian = e * g;
			this.haveNextNextGaussian = true;
			return d * g;
		}
	}
}
