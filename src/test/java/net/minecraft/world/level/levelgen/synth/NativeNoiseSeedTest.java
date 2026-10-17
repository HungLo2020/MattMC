package net.minecraft.world.level.levelgen.synth;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.ref.WeakReference;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

/** Exercises real builder failure custody, transferred leaf cleanup and lazy evaluation. */
@Isolated
class NativeNoiseSeedTest {
    @Test void genuineRandomFailureRetiresBuilderWithoutPublishingLeaf() {
        for(boolean simplex:new boolean[]{false,true}) {
            var source=new NoiseConstructorContractTest.Source();source.throwInt=57;
            long[] before=NativeNoiseSeed.statistics();
            assertSame(source.failure,assertThrows(RuntimeException.class,()->new NativeNoiseSeed(source,simplex)));
            long[] after=NativeNoiseSeed.statistics();
            assertEquals(before[0],after[0]);assertEquals(before[1]+1,after[1]);assertEquals(before[2]+1,after[2]);
            assertEquals(before[4],after[4]);
        }
    }
    @Test void malformedOffsetsRemainAcceptedUntilActualEvaluation() {
        var source=new NoiseConstructorContractTest.Source();source.scripted=new double[]{Double.NaN,0.5,0.25};
        var noise=new ImprovedNoise(source);NoiseConstructorContractTest.shape(source);
        assertEquals("Invalid native noise state: -5",assertThrows(IllegalArgumentException.class,()->noise.noise(0,0,0)).getMessage());
        assertTrue(Double.isNaN(noise.xo));assertDoesNotThrow(()->noise.parityConfigString(new StringBuilder()));
        assertEquals(259,source.calls.size());
    }
    private record Copied(WeakReference<ImprovedNoise> leaf,NativeNoise snapshot,double value) {}
    private static Copied copy() {
        var noise=new ImprovedNoise(RandomSource.create(123));
        return new Copied(new WeakReference<>(noise),noise.nativeNoise(),noise.noise(1.25,2.5,3.75));
    }
    @Test void copiedEvaluatorDoesNotRetainRetiredSeedFacade() throws Exception {
        for(int i=0;i<20;i++){System.gc();Thread.sleep(10);}
        long[] before=NativeNoiseSeed.statistics();var copied=copy();
        for(int i=0;i<120&&(copied.leaf.get()!=null||NativeNoiseSeed.statistics()[5]<before[5]+1);i++){System.gc();Thread.sleep(20);}
        assertNull(copied.leaf.get());assertTrue(NativeNoiseSeed.statistics()[5]>=before[5]+1,"actual native leaf was not retired");
        assertEquals(Double.doubleToRawLongBits(copied.value),Double.doubleToRawLongBits(copied.snapshot.sample(1.25,2.5,3.75)));
        assertEquals(before[0],NativeNoiseSeed.statistics()[0]);
    }
}
