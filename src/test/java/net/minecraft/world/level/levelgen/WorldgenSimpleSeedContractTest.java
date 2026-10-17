package net.minecraft.world.level.levelgen;
import java.io.IOException;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Original self-virtual seed effects, reentry and committed checked-error prefix. */
final class WorldgenSimpleSeedContractTest {
 @SuppressWarnings("unchecked")static<T,E extends Throwable>T raise(Throwable failure)throws E{throw(E)failure;}
 static final class Effects extends WorldgenRandom {
  boolean ready;int next;int effects;final IOException marker=new IOException("single seed actual callback");
  Effects(RandomSource source){super(source);}
  @Override public synchronized void setSeed(long seed){super.setSeed(seed);if(ready){effects++;assertEquals(0,getCount());assertEquals(next,nextInt());assertEquals(1,getCount());WorldgenSimpleSeedContractTest.<Void,RuntimeException>raise(marker);}}
 }
 static void verify(boolean feature){
  long expectedSeed=feature?50045L:607669103814L;var backing=new LegacyRandomSource(42);var wrapper=new Effects(backing);var control=new java.util.Random(expectedSeed);wrapper.next=control.nextInt();wrapper.ready=true;
  assertSame(wrapper.marker,assertThrows(IOException.class,()->{if(feature)wrapper.setFeatureSeed(42,3,5);else wrapper.setLargeFeatureWithSalt(17,1,2,3);}));assertEquals(1,wrapper.effects);assertEquals(1,wrapper.getCount());assertEquals(control.nextInt(),backing.nextInt());
 }
 @Test void featureSeedPreservesItsRealVirtualReentryAndFailureAfterSeedCommit(){verify(true);}
 @Test void saltedSeedPreservesItsRealVirtualReentryAndFailureAfterSeedCommit(){verify(false);}
}
