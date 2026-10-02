package net.minecraft.world.level.levelgen.carver;

/** Literal original built-in predicates. */
final class JavaCarverGeometry {
	static boolean cave(double d, double e, double f, double g) {
		return e <= g ? true : d * d + e * e + f * f >= 1.0;
	}
	static boolean canyon(CarvingContext carvingContext, float[] fs, double d, double e, double f, int i) {
		int j = i - carvingContext.getMinGenY();
		return (d * d + f * f) * fs[j - 1] + e * e / 6.0 >= 1.0;
	}
}
