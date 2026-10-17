package net.minecraft.world.level.levelgen;

import com.google.common.annotations.VisibleForTesting;
import net.minecraft.util.RandomSource;

public class LegacyRandomSource implements BitRandomSource {
	private final NativeLegacySource nativeSource;
	private final MarsagliaPolarGaussian gaussianSource;

	public LegacyRandomSource(long l) {
		this.nativeSource=new NativeLegacySource();
		this.gaussianSource=MarsagliaPolarGaussian.nativeSource(this,this.nativeSource);
		this.setSeed(l);
	}

	LegacyRandomSource(NativeLegacySource actual) {
        this.nativeSource=actual;
        this.gaussianSource=MarsagliaPolarGaussian.nativeSource(this,actual);
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
		private final NativePositionalFactory nativeFactory;

		public LegacyPositionalRandomFactory(long l) {
			this.nativeFactory = new NativePositionalFactory(2,l,0);
		}

		long seed() { return this.nativeFactory.seed(0); }
        final NativePositionalFactory nativeFactory() { return this.nativeFactory; }

        @Override public RandomSource at(int x,int y,int z) { return this.nativeFactory.at(x,y,z); }
        @Override public RandomSource fromHashOf(String string) { return this.nativeFactory.fromHashOf(string); }
        @Override public RandomSource fromSeed(long seed) { return this.nativeFactory.fromSeed(seed); }

		@VisibleForTesting
		@Override
		public void parityConfigString(StringBuilder stringBuilder) {
			stringBuilder.append("LegacyPositionalRandomFactory{").append(this.seed()).append("}");
		}
	}
}
