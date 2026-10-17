package net.minecraft.world.level.levelgen;

import java.util.function.LongFunction;
import net.minecraft.util.RandomSource;

public class WorldgenRandom extends LegacyRandomSource {
	private final RandomSource randomSource;
	private volatile NativeWorldgenSource nativeState;

	public WorldgenRandom(RandomSource randomSource) {
		super(0L);
		this.randomSource = randomSource;
	}

	/** Native decoration: the wrapped source. */
	public RandomSource nativeSource() {
		return this.randomSource;
	}

	private NativeWorldgenSource state() {
		var value=this.nativeState;
		if(value==null){synchronized(this){value=this.nativeState;if(value==null)this.nativeState=value=new NativeWorldgenSource();}}
		return value;
	}

	public int getCount() { var value=this.nativeState;return value==null?0:value.count(); }
	public int nativeCount() { var value=this.nativeState;return value==null?0:value.count(); }
	public void nativeSetCount(int count) { this.state().count(count); }
	final RandomSource nativeTarget() { return this.randomSource; }
	final int nativeNextLegacy(int bits) { return ((LegacyRandomSource)this.randomSource).next(bits); }
	final long nativeNextWord() { return this.randomSource.nextLong(); }
	final NativeWorldgenSource nativeState() { return this.state(); }
	public static long nativeNoiseLeaf(RandomSource source,boolean simplex) {
		if(source==null||source.getClass()!=WorldgenRandom.class)return 0;
		var actual=(WorldgenRandom)source;var target=NativeWorldgenSource.target(actual.randomSource);
		if(target.id()==0)return 0;
		try{return actual.state().leaf(target,simplex);}finally{java.lang.ref.Reference.reachabilityFence(actual);}
	}

	@Override
	public RandomSource fork() {
		return this.randomSource.fork();
	}

	@Override
	public PositionalRandomFactory forkPositional() {
		return this.randomSource.forkPositional();
	}

	@Override
	public int next(int bits) { return this.state().next(this,bits); }

	@Override
	public synchronized void setSeed(long l) {
		if (this.randomSource != null) {
			this.randomSource.setSeed(l);
		}
	}

	public long setDecorationSeed(long seed,int x,int z) { return NativeWorldgenSource.seed(this,1,seed,x,z,0); }
	public void setFeatureSeed(long seed,int index,int step) { NativeWorldgenSource.seed(this,3,seed,index,step,0); }
	public void setLargeFeatureSeed(long seed,int x,int z) { NativeWorldgenSource.seed(this,2,seed,x,z,0); }
	public void setLargeFeatureWithSalt(long seed,int x,int z,int salt) { NativeWorldgenSource.seed(this,4,seed,x,z,salt); }
	public static RandomSource seedSlimeChunk(int x,int z,long seed,long salt) { return RandomSource.create(NativeWorldgenSource.slime(seed,x,z,salt)); }

	public static enum Algorithm {
		LEGACY(LegacyRandomSource::new),
		XOROSHIRO(XoroshiroRandomSource::new);

		private final LongFunction<RandomSource> constructor;

		private Algorithm(final LongFunction<RandomSource> longFunction) {
			this.constructor = longFunction;
		}

		public RandomSource newInstance(long l) {
			return (RandomSource)this.constructor.apply(l);
		}
	}
}
