package net.minecraft.world.level.levelgen.synth;

import static org.junit.jupiter.api.Assertions.*;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

/** Real graph/reader ownership across genuine VM callbacks and facade collection. */
@Isolated
class NativePerlinTest {
    @Test void actualAmplitudeFailureRetiresThePartiallyBuiltGraph() {
        var failure=new RuntimeException("actual amplitude after first leaf");
        var amplitudes=new DoubleArrayList(new double[]{1,1}){@Override public double getDouble(int index){if(index==1)throw failure;return super.getDouble(index);}};
        long[] before=NativePerlin.statistics();
        assertSame(failure,assertThrows(RuntimeException.class,()->PerlinNoise.create(RandomSource.create(42),-1,amplitudes)));
        long[] after=NativePerlin.statistics();assertEquals(before[0],after[0]);assertEquals(before[1]+1,after[1]);assertEquals(before[2]+1,after[2]);
    }
    @Test void actualReaderFailurePreservesItsGraphAndLaterInvocation() {
        boolean[] armed={false};var failure=new RuntimeException("actual amplitude reader");
        var amplitudes=new DoubleArrayList(new double[]{1,.5,1}){@Override public double getDouble(int index){if(armed[0])throw failure;return super.getDouble(index);}};
        var noise=PerlinNoise.create(RandomSource.create(42),-2,amplitudes);double expected=noise.maxBrokenValue(.5);long[] before=NativePerlin.statistics();
        armed[0]=true;assertSame(failure,assertThrows(RuntimeException.class,()->noise.maxBrokenValue(.5)));armed[0]=false;
        long[] after=NativePerlin.statistics();for(int i=0;i<3;i++)assertEquals(before[i],after[i]);assertEquals(before[3],after[3]);assertEquals(before[4]+1,after[4]);assertEquals(before[5]+1,after[5]);assertEquals(Double.doubleToRawLongBits(expected),Double.doubleToRawLongBits(noise.maxBrokenValue(.5)));
    }
    private record Pending(long reader,WeakReference<NativePerlin> facade) {}
    private static Pending pending()throws Exception {
        var graph=new NativePerlin(RandomSource.create(42),new DoubleArrayList(new double[]{1}),0,true,new ImprovedNoise[1]);
        Method reader=NativePerlin.class.getDeclaredMethod("reader",int.class,double.class);reader.setAccessible(true);
        return new Pending((long)reader.invoke(graph,0,1.0),new WeakReference<>(graph));
    }
    @Test void actualReaderOwnsGraphAfterItsArenaFacadeIsCollected()throws Throwable {
        for(int i=0;i<20;i++){System.gc();Thread.sleep(10);}long[] before=NativePerlin.statistics();var pending=pending();long reader=pending.reader;
        try {
            for(int i=0;i<120&&pending.facade.get()!=null;i++){System.gc();Thread.sleep(20);}
            assertNull(pending.facade.get(),"actual native graph facade remains rooted");
            for(int i=0;i<10;i++){System.gc();Thread.sleep(20);}
            assertEquals(before[0]+1,NativePerlin.statistics()[0],"actual reader lost its graph after facade cleanup");assertEquals(before[3]+1,NativePerlin.statistics()[3]);
            Method run=NativePerlin.class.getDeclaredMethod("read",long.class,it.unimi.dsi.fastutil.doubles.DoubleList.class,NativeNoise.class,java.lang.foreign.MemorySegment.class,int.class);run.setAccessible(true);
            long consumed=reader;reader=0;long result=(long)run.invoke(null,consumed,new DoubleArrayList(new double[]{1}),null,null,0);
            assertEquals(Double.doubleToRawLongBits(3.0),result);
            assertEquals(before[0],NativePerlin.statistics()[0]);assertEquals(before[2]+1,NativePerlin.statistics()[2]);assertEquals(before[3],NativePerlin.statistics()[3]);
        } finally {
            if(reader!=0){var field=NativePerlin.class.getDeclaredField("READER_RELEASE");field.setAccessible(true);((java.lang.invoke.MethodHandle)field.get(null)).invokeExact(reader);}
        }
    }
    @Test void rejectedOctaveFactoryRetiresItsActualNativeFrame(){
        long[] before=NativePerlin.statistics();assertEquals("Need some octaves!",assertThrows(IllegalArgumentException.class,()->PerlinNoise.create(RandomSource.create(42),java.util.List.of())).getMessage());
        long[] after=NativePerlin.statistics();assertEquals(before[6],after[6]);assertEquals(before[7]+1,after[7]);assertEquals(before[8]+1,after[8]);
    }
}
