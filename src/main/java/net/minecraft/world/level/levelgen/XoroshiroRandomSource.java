package net.minecraft.world.level.levelgen;
import com.google.common.annotations.VisibleForTesting;
import com.mojang.serialization.Codec;
import net.minecraft.util.RandomSource;
/** Native source/current-generator ownership; VM projection preserves actual escapes. */
public class XoroshiroRandomSource implements RandomSource {
 public static final Codec<XoroshiroRandomSource> CODEC=Xoroshiro128PlusPlus.CODEC.xmap(XoroshiroRandomSource::new,source->source.generator());
 private Xoroshiro128PlusPlus randomNumberGenerator;
 private Xoroshiro128PlusPlus attached;
 private final NativeXoroshiroSource nativeSource;
 private final MarsagliaPolarGaussian gaussianSource;
 public XoroshiroRandomSource(long seed){nativeSource=new NativeXoroshiroSource(seed);gaussianSource=MarsagliaPolarGaussian.nativeSource(this,nativeSource);}
 public XoroshiroRandomSource(RandomSupport.Seed128bit seed){nativeSource=new NativeXoroshiroSource(seed.seedLo(),seed.seedHi());gaussianSource=MarsagliaPolarGaussian.nativeSource(this,nativeSource);}
 public XoroshiroRandomSource(long lo,long hi){nativeSource=new NativeXoroshiroSource(lo,hi);gaussianSource=MarsagliaPolarGaussian.nativeSource(this,nativeSource);}
 XoroshiroRandomSource(NativeXoroshiroSource actual,boolean retained){nativeSource=actual;gaussianSource=MarsagliaPolarGaussian.nativeSource(this,actual);}
 private XoroshiroRandomSource(Xoroshiro128PlusPlus root){nativeSource=root.sourceOwner();gaussianSource=MarsagliaPolarGaussian.nativeSource(this,nativeSource);randomNumberGenerator=root;attached=root;}
 // The identities are real VM facts. Reflection/custom supplied generators keep dispatch.
 private Xoroshiro128PlusPlus observed(){var root=randomNumberGenerator;if(root!=null&&root!=attached){Xoroshiro128PlusPlus.attach(nativeSource,root);attached=root;}return root;}
 private Xoroshiro128PlusPlus generator(){var root=observed();if(root==null){root=Xoroshiro128PlusPlus.fromSource(nativeSource);randomNumberGenerator=root;attached=root;}return root;}
 private long word(){var root=observed();return root==null?nativeSource.next():root.nextLong();}
 private boolean nativeWords(){var root=observed();return getClass()==XoroshiroRandomSource.class&&(root==null||root.getClass()==Xoroshiro128PlusPlus.class);}
 static NativeXoroshiroSource nativeGaussianProducer(RandomSource source){if(source==null||source.getClass()!=XoroshiroRandomSource.class)return null;var actual=(XoroshiroRandomSource)source;return actual.nativeWords()?actual.nativeSource:null;}
 static NativeXoroshiroSource nativeWorldgenProducer(RandomSource source){if(source==null||source.getClass()!=XoroshiroRandomSource.class)return null;var actual=(XoroshiroRandomSource)source;if(actual.nativeSource==null)return null;return actual.nativeWords()?actual.nativeSource:null;}
 public MarsagliaPolarGaussian nativeGaussian(){return gaussianSource;}
 public Xoroshiro128PlusPlus nativeGenerator(){return generator();}
 public static long nativeNoiseLeaf(RandomSource source,boolean simplex){if(source==null||source.getClass()!=XoroshiroRandomSource.class)return 0;var actual=(XoroshiroRandomSource)source;var root=actual.observed();if(root!=null&&root.getClass()!=Xoroshiro128PlusPlus.class)return 0;return actual.nativeSource.leaf(simplex);}
 @Override public RandomSource fork(){return new XoroshiroRandomSource(word(),word());}
 @Override public PositionalRandomFactory forkPositional(){return new XoroshiroPositionalRandomFactory(word(),word());}
 @Override public void setSeed(long seed){boolean embedded=gaussianSource!=null&&gaussianSource.nativeUsesSourceCache(nativeSource);nativeSource.seed(seed,embedded);randomNumberGenerator=null;attached=null;if(!embedded)gaussianSource.reset();}
 @Override public int nextInt(){return(int)word();}
 @Override public int nextInt(int bound){if(bound<=0)throw new IllegalArgumentException("Bound must be positive");if(nativeWords()){var root=randomNumberGenerator;return root==null?nativeSource.bounded(bound):root.nativeBounded(bound);}long l=Integer.toUnsignedLong(this.nextInt()),m=l*bound,n=m&4294967295L;if(n<bound){for(int limit=Integer.remainderUnsigned(~bound+1,bound);n<limit;n=m&4294967295L){l=Integer.toUnsignedLong(this.nextInt());m=l*bound;}}return(int)(m>>32);}
 @Override public long nextLong(){return word();}
 @Override public boolean nextBoolean(){return(word()&1L)!=0;}
 @Override public float nextFloat(){if(nativeWords()){var root=randomNumberGenerator;return root==null?nativeSource.nextFloat():root.nativeFloat();}return(float)nextBits(24)*5.9604645E-8F;}
 @Override public double nextDouble(){if(nativeWords()){var root=randomNumberGenerator;return root==null?nativeSource.nextDouble():root.nativeDouble();}return nextBits(53)*1.110223E-16F;}
 @Override public double nextGaussian(){return gaussianSource.nextGaussian();}
 @Override public void consumeCount(int count){if(count<=0)return;if(nativeWords()){var root=randomNumberGenerator;if(root==null)nativeSource.consume(count);else root.nativeConsume(count);return;}for(int i=0;i<count;i++)word();}
 private long nextBits(int count){return word()>>>64-count;}
	public static class XoroshiroPositionalRandomFactory implements PositionalRandomFactory {
		private final NativePositionalFactory nativeFactory;

		public XoroshiroPositionalRandomFactory(long l, long m) {
			this.nativeFactory = new NativePositionalFactory(1,l,m);
		}

		long seedLo() { return this.nativeFactory.seed(0); }

		long seedHi() { return this.nativeFactory.seed(1); }
        final NativePositionalFactory nativeFactory() { return this.nativeFactory; }

        @Override public RandomSource at(int x,int y,int z) { return this.nativeFactory.at(x,y,z); }
        @Override public RandomSource fromHashOf(String string) { return this.nativeFactory.fromHashOf(string); }
        @Override public RandomSource fromSeed(long seed) { return this.nativeFactory.fromSeed(seed); }

		@VisibleForTesting
		@Override
		public void parityConfigString(StringBuilder stringBuilder) {
			stringBuilder.append("seedLo: ").append(this.nativeFactory.seed(0)).append(", seedHi: ").append(this.nativeFactory.seed(1));
		}
	}
}
