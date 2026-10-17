package net.minecraft.world.level.levelgen;

import com.google.common.annotations.VisibleForTesting;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

public class LegacyRandomSource implements BitRandomSource {
	private final NativeLegacySource nativeSource = new NativeLegacySource();
	private final MarsagliaPolarGaussian gaussianSource = MarsagliaPolarGaussian.nativeSource(this, this.nativeSource);

	public LegacyRandomSource(long l) {
		this.setSeed(l);
	}

	@Override
	public RandomSource fork() {
		return new LegacyRandomSource(this.nextLong());
	}

	@Override
	public PositionalRandomFactory forkPositional() {
		return new LegacyRandomSource.LegacyPositionalRandomFactory(this.nextLong());
	}

	@Override
	public void setSeed(long seed) {
		boolean embedded = this.gaussianSource != null && this.gaussianSource.nativeUsesSourceCache(this.nativeSource);
		this.nativeSource.seed(seed, embedded);
		if (!embedded) this.gaussianSource.reset();
	}

	@Override
	public int next(int bits) { return this.nativeSource.next(bits); }

	static NativeLegacySource nativeGaussianProducer(RandomSource source) {
		return source != null && source.getClass() == LegacyRandomSource.class ? ((LegacyRandomSource)source).nativeSource : null;
	}

	public static long nativeNoiseLeaf(RandomSource source, boolean simplex) {
		var owner = nativeGaussianProducer(source);
		return owner == null ? 0 : owner.leaf(simplex);
	}

	/** The scrambled 48-bit state, for native code that continues this sequence. */
	public long rawSeed() {
		return this.nativeSource.raw();
	}

	/** Continues from a scrambled state written back by native code (the Gaussian cache is untouched). */
	public void setRawSeed(long l) {
		this.nativeSource.raw(l);
	}

	/** Native decoration: this source's gaussian cache (a {@code WorldgenRandom} keeps its own). */
	public MarsagliaPolarGaussian nativeGaussian() {
		return this.gaussianSource;
	}

	@Override
	public double nextGaussian() {
		return this.gaussianSource.nextGaussian();
	}

	public static class LegacyPositionalRandomFactory implements PositionalRandomFactory {
		private final long seed;

		public LegacyPositionalRandomFactory(long l) {
			this.seed = l;
		}

		long seed() { return this.seed; }

		@Override
		public RandomSource at(int i, int j, int k) {
			long l = Mth.getSeed(i, j, k);
			long m = l ^ this.seed;
			return new LegacyRandomSource(m);
		}

		@Override
		public RandomSource fromHashOf(String string) {
			int i = string.hashCode();
			return new LegacyRandomSource(i ^ this.seed);
		}

		@Override
		public RandomSource fromSeed(long l) {
			return new LegacyRandomSource(l);
		}

		@VisibleForTesting
		@Override
		public void parityConfigString(StringBuilder stringBuilder) {
			stringBuilder.append("LegacyPositionalRandomFactory{").append(this.seed).append("}");
		}
	}
}
