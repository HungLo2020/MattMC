package net.minecraft.world.level.levelgen.synth;
import static org.junit.jupiter.api.Assertions.*;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.ref.WeakReference;
import java.util.*;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
/** Actual Blended failure and transferred-reader lifetime checks. */
@Isolated
class NativeBlendedTest {
 @org.junit.jupiter.api.BeforeAll static void bootstrap(){net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();}
 @Test void genuineSecondChildFailureRetiresTheActualParentGraph(){var source=new BlendedConstructorContractTest.Source();var failure=new RuntimeException("actual second native Blended child");source.onDouble=()->{if(source.doubles==49)throw failure;};long[] before=NativeBlended.statistics();assertSame(failure,assertThrows(RuntimeException.class,()->new BlendedNoise(source,.25,.125,80,160,4)));long[] after=NativeBlended.statistics();assertEquals(before[0],after[0]);assertEquals(before[1]+1,after[1]);assertEquals(before[2]+1,after[2]);assertEquals(49,source.doubles);assertEquals(16*256,source.ints);}
 record Pending(long reader,DoubleList[] amplitudes,MemorySegment destination,long expected,WeakReference<NativeBlended> facade){}
 static Pending pending()throws Exception{var graph=new NativeBlended(RandomSource.create(42),.25,.125,80,160,4);var prior=graph.allocate();graph.write(prior);long expected=Double.doubleToRawLongBits(new NativeNoise(prior).sample(1.25,7.5,-3.75));var destination=graph.allocate();var reader=NativeBlended.class.getDeclaredMethod("reader");reader.setAccessible(true);return new Pending((long)reader.invoke(graph),new DoubleList[]{graph.children[0].nativeAmplitudes(),graph.children[1].nativeAmplitudes(),graph.children[2].nativeAmplitudes()},destination,expected,new WeakReference<>(graph));}
 static void settle()throws Exception{for(int i=0;i<20;i++){System.gc();Thread.sleep(10);}}
 @Test void transferredReaderKeepsThreeActualChildrenAfterAllGraphFacadesAreCollected()throws Throwable{
  settle();long[] parent=NativeBlended.statistics(),children=NativePerlin.statistics(),leaf=NativeNoiseSeed.statistics();var pending=pending();long reader=pending.reader;
  try{for(int i=0;i<120&&pending.facade.get()!=null;i++){System.gc();Thread.sleep(20);}assertNull(pending.facade.get());settle();assertEquals(parent[0],NativeBlended.statistics()[0]);assertEquals(parent[2]+1,NativeBlended.statistics()[2]);assertEquals(children[0]+3,NativePerlin.statistics()[0]);long consumed=reader;reader=0;NativeBlended.read(consumed,pending.amplitudes,pending.destination);assertEquals(pending.expected,Double.doubleToRawLongBits(new NativeNoise(pending.destination).sample(1.25,7.5,-3.75)));assertEquals(children[0],NativePerlin.statistics()[0]);assertEquals(children[3],NativePerlin.statistics()[3]);assertEquals(leaf[3],NativeNoiseSeed.statistics()[3]);}
  finally{if(reader!=0){var field=NativeBlended.class.getDeclaredField("READER_RELEASE");field.setAccessible(true);((MethodHandle)field.get(null)).invokeExact(reader);}}
 }
 @Test void readerSelectsEachOriginalChildAmplitudeList(){var graph=new NativeBlended(RandomSource.create(42),.25,.125,80,160,4);double[] values={.375,.625,.875};for(int i=0;i<3;i++)graph.children[i].nativeAmplitudes().set(0,values[i]);var state=graph.allocate();graph.write(state);int[] slots={0,16,32};for(int i=0;i<3;i++)assertEquals(Double.doubleToRawLongBits(values[i]),Double.doubleToRawLongBits(state.get(java.lang.foreign.ValueLayout.JAVA_DOUBLE,NativeNoise.HEADER+(long)slots[i]*NativeNoise.OCTAVE+24)));}

}
