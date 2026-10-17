package net.minecraft.world.level.levelgen.synth;
import static org.junit.jupiter.api.Assertions.*;
import it.unimi.dsi.fastutil.doubles.*;
import java.lang.foreign.MemorySegment;
import java.lang.ref.WeakReference;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Method;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
/** Actual Normal parent/pair-reader custody through VM failure and arena cleanup. */
@Isolated
class NativeNormalTest {
 @Test void actualIteratorFailureRetiresParentAfterBothChildBindings(){var failure=new RuntimeException("actual normal iterator");var values=new DoubleArrayList(new double[]{1,1}){@Override public DoubleListIterator iterator(){throw failure;}};long[] before=NativeNormal.statistics();assertSame(failure,assertThrows(RuntimeException.class,()->NormalNoise.create(RandomSource.create(42),new NormalNoise.NoiseParameters(-1,values))));long[] after=NativeNormal.statistics();assertEquals(before[0],after[0]);assertEquals(before[1]+1,after[1]);assertEquals(before[2]+1,after[2]);}
 @Test void secondChildAmplitudeFailureRetiresPairReaderAndKeepsSnapshot(){boolean[] armed={false};var failure=new RuntimeException("actual second child tail");var values=new DoubleArrayList(new double[]{1,1}){@Override public double getDouble(int index){if(armed[0]&&index==2)throw failure;return super.getDouble(index);}};var source=new CompositeConstructorContractTest.Source();source.onHash=()->values.add(1.0);var noise=NormalNoise.create(source,new NormalNoise.NoiseParameters(-1,values));double expected=noise.getValue(12.25,-4.5,6.75);long[] parent=NativeNormal.statistics(),reader=NativePerlin.statistics();armed[0]=true;assertSame(failure,assertThrows(RuntimeException.class,()->noise.getValue(12.25,-4.5,6.75)));armed[0]=false;assertArrayEquals(parent,NativeNormal.statistics());long[] after=NativePerlin.statistics();assertEquals(reader[3],after[3]);assertEquals(reader[4]+1,after[4]);assertEquals(reader[5]+1,after[5]);assertEquals(Double.doubleToRawLongBits(expected),Double.doubleToRawLongBits(noise.getValue(12.25,-4.5,6.75)));}
 private record Pending(long reader,MemorySegment destination,long expected,WeakReference<NativeNormal> facade){}
 private static Pending pending()throws Exception{var values=new DoubleArrayList(new double[]{1,1});var graph=new NativeNormal(RandomSource.create(42),-1,values,true);var prior=graph.allocate();graph.write(values,prior);var snapshot=new NativeNoise(prior);long expected=Double.doubleToRawLongBits(snapshot.sample(1.25,7.5,-3.75));var destination=graph.allocate();Method create=NativeNormal.class.getDeclaredMethod("reader",int.class);create.setAccessible(true);return new Pending((long)create.invoke(graph,1),destination,expected,new WeakReference<>(graph));}
 @Test void pairReaderKeepsActualChildrenAfterParentFacadeCleanup()throws Throwable{for(int i=0;i<20;i++){System.gc();Thread.sleep(10);}long[] parent=NativeNormal.statistics(),children=NativePerlin.statistics();var pending=pending();long reader=pending.reader;
  try{for(int i=0;i<120&&pending.facade.get()!=null;i++){System.gc();Thread.sleep(20);}assertNull(pending.facade.get(),"actual Normal facade is still rooted");for(int i=0;i<12;i++){System.gc();Thread.sleep(20);}assertEquals(parent[0],NativeNormal.statistics()[0],"unused Normal metadata owner survived facade cleanup");assertEquals(parent[2]+1,NativeNormal.statistics()[2]);assertTrue(NativePerlin.statistics()[0]>=children[0]+2,"pair reader lost an actual child");long consumed=reader;reader=0;NativePerlin.read(consumed,new DoubleArrayList(new double[]{1,1}),null,pending.destination,0);var snapshot=new NativeNoise(pending.destination);assertEquals(pending.expected,Double.doubleToRawLongBits(snapshot.sample(1.25,7.5,-3.75)));assertEquals(children[3],NativePerlin.statistics()[3]);}
  finally{if(reader!=0){var field=NativePerlin.class.getDeclaredField("READER_RELEASE");field.setAccessible(true);((MethodHandle)field.get(null)).invokeExact(reader);}}
 }
}
