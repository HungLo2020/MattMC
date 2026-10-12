package net.vulkanic.world;

import com.seibel.distanthorizons.core.dataObjects.render.columnViews.ColumnArrayView;
import com.seibel.distanthorizons.core.dataObjects.render.columnViews.IColumnDataView;
import com.seibel.distanthorizons.core.util.RenderDataPointReducingList;
import com.seibel.distanthorizons.core.util.RenderDataPointUtil;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Actual Frozen scalar reduction corpus and supplemental compatibility checks. */
@Tag("parity")
class NativeDhRenderReductionTest {
    private static final long GUARD = 0x1122334455667788L;
    private static ColumnArrayView output(int size,int target) {
        long[] values=new long[size+3];Arrays.fill(values,GUARD);
        return new ColumnArrayView(new LongArrayList(values),size,1,target);
    }
    @Test void all3616ActualFrozenCasesAdmitOrPreserveOriginalFailureAndPublishThroughConsumer() throws Exception {
        try(var in=new DataInputStream(new GZIPInputStream(Objects.requireNonNull(getClass().getResourceAsStream("/world/chunk/frozen-dh-render-reduction.bin.gz"))))) {
            assertEquals(0x44525231,in.readInt());assertEquals(6,in.readInt());
            for(int i=0;i<6;i++){assertFalse(in.readUTF().isEmpty());assertEquals(64,in.readUTF().length());}
            assertTrue(in.readUTF().contains("Actual Frozen render reduction"));assertEquals(3616,in.readInt());
            for(int test=0;test<3616;test++) {
                String name=in.readUTF();int target=in.readInt(),size=in.readInt(),count=in.readInt();long[] points=new long[count];
                for(int i=0;i<count;i++)points[i]=in.readLong();String exception=in.readUTF();long[] expected=new long[size+3];for(int i=0;i<expected.length;i++)expected[i]=in.readLong();
                var source=new ColumnArrayView(new LongArrayList(points),count,0,count);var nativeOutput=output(size,target);
                boolean admitted=NativeDhRenderReduction.reduce(source,null,nativeOutput,null);assertEquals(exception.isEmpty(),admitted,"admission "+name);
                if(admitted)assertArrayEquals(expected,nativeOutput.data.toLongArray(),name);
                else for(long value:nativeOutput.data)assertEquals(GUARD,value,"decline leaves destination untouched");
                var consumer=output(size,target);
                if(exception.isEmpty())RenderDataPointUtil.mergeMultiData(source,consumer);
                else assertEquals(exception,assertThrows(RuntimeException.class,()->RenderDataPointUtil.mergeMultiData(source,consumer)).getClass().getName());
                assertArrayEquals(expected,consumer.data.toLongArray(),"ordinary consumer "+name);
                assertArrayEquals(points,source.data.toLongArray(),"source retained "+name);
                // Replay the unchanged Frozen corpus with primary Rust spans,
                // including mixed heap/native calls and native alias exclusion.
                try(var nativeInput=new NativeDhRenderSource(Math.max(1,(count+4095)/4096));
                    var nativeDestination=new NativeDhRenderSource(Math.max(1,(size+3+4095)/4096))) {
                    for(int p=0;p<count;p++)nativeInput.set(p,points[p]);
                    var retainedInput=new ColumnArrayView(nativeInput,count,0,count);
                    for(int storage=0;storage<3;storage++) {
                        for(int p=0;p<size+3;p++)nativeDestination.set(p,GUARD);
                        var destination=storage==2?output(size,target):new ColumnArrayView(nativeDestination,size,1,target);
                        assertEquals(exception.isEmpty(),NativeDhRenderReduction.reduce(storage==0?source:retainedInput,null,destination,null),"primary admission "+name);
                        if(exception.isEmpty()) {
                            long[] actual=new long[size+3];destination.data.getElements(0,actual,0,actual.length);
                            assertArrayEquals(expected,actual,"primary storage "+storage+" "+name);
                        }
                    }
                }
            }
            assertEquals(-1,in.read());
        }
    }
    @Test void semanticCompanionsFollowActualCompatibilityReducerIncludingWaterRestoration() {
        for(int seed=0;seed<64;seed++)for(int target=0;target<=8;target++) {
            var random=new Random(seed);long[] points=new long[32];int[] materials=new int[32];int y=0;
            for(int i=0;i<points.length;i++) {int bottom=y+random.nextInt(3);y=bottom+1+random.nextInt(4);points[i]=RenderDataPointUtil.createDataPoint(i%3==0?128:255,random.nextInt(256),40,90,y,bottom,15,0,i%5==0?12:2);materials[i]=i%4;}
            var source=new ColumnArrayView(new LongArrayList(points),32,0,32);var expected=output(target+2,target);int[] expectedMaterials=new int[target+4];Arrays.fill(expectedMaterials,99);
            try(var original=new RenderDataPointReducingList(source,materials)){original.reduce(target);original.copyTo(expected,expectedMaterials);}
            var actual=output(target+2,target);int[] actualMaterials=new int[target+4];Arrays.fill(actualMaterials,99);
            assertTrue(NativeDhRenderReduction.reduce(source,materials,actual,actualMaterials));assertArrayEquals(expected.data.toLongArray(),actual.data.toLongArray(),"seed"+seed+" target"+target);assertArrayEquals(expectedMaterials,actualMaterials);
            var consumer=output(target+2,target);int[] consumerMaterials=new int[target+4];Arrays.fill(consumerMaterials,99);RenderDataPointUtil.mergeMultiData(source,materials,consumer,consumerMaterials);assertArrayEquals(expected.data.toLongArray(),consumer.data.toLongArray());assertArrayEquals(expectedMaterials,consumerMaterials);
        }
    }
    @Test void aliasedInputOutputViewsUseCompleteOldInputBeforePublication() {
        long a=RenderDataPointUtil.createDataPoint(3,0,0xff123456,15,0,2),b=RenderDataPointUtil.createDataPoint(7,4,0xff987654,4,8,3);
        var storage=new LongArrayList(new long[]{GUARD,a,b,GUARD,GUARD});var source=new ColumnArrayView(storage,2,1,2);var output=new ColumnArrayView(storage,2,1,1);
        long expected=RenderDataPointReducingList.reduceToOne(source);assertTrue(NativeDhRenderReduction.reduce(source,null,output,null));assertArrayEquals(new long[]{GUARD,expected,0,GUARD,GUARD},storage.toLongArray());
        storage=new LongArrayList(new long[]{GUARD,a,b,GUARD,GUARD});source=new ColumnArrayView(storage,2,1,2);output=new ColumnArrayView(storage,2,1,1);int[] identities={31,47,99};
        assertTrue(NativeDhRenderReduction.reduce(source,identities,output,identities));assertEquals(-1,identities[0]);assertEquals(0,identities[1]);assertEquals(99,identities[2]);assertEquals(GUARD,storage.getLong(0));assertEquals(GUARD,storage.getLong(3));
    }
    @Test void customViewRetainsGetterExceptionPrefixAndZeroTargetDoesNotReadIt() {
        class Custom implements IColumnDataView {
            int calls,sizes;final RuntimeException failure=new IllegalStateException("custom reducer getter");
            public long get(int i){if(++calls==3)throw failure;return RenderDataPointUtil.createDataPoint(i+1,i,0xff123456,15,0,2);}
            public int size(){sizes++;return 4;}public int verticalSize(){return 4;}public int dataCount(){return 1;}
            public IColumnDataView subView(int start,int n){throw new AssertionError();}public void copyTo(long[] dest,int offset,int count){throw new AssertionError();}
        }
        var custom=new Custom();assertFalse(NativeDhRenderReduction.reduce(custom,null,output(2,2),null));assertEquals(0,custom.calls);assertEquals(0,custom.sizes);
        assertSame(custom.failure,assertThrows(RuntimeException.class,()->RenderDataPointUtil.mergeMultiData(custom,output(2,2))));assertEquals(3,custom.calls);assertEquals(1,custom.sizes);
        var zero=output(3,0);RenderDataPointUtil.mergeMultiData(custom,zero);assertArrayEquals(new long[]{GUARD,0,0,0,GUARD,GUARD},zero.data.toLongArray());assertEquals(3,custom.calls);assertEquals(1,custom.sizes);
    }
    @Test void customStorageAndOversizedInputsKeepOriginalPathsAndNoNativePartialWrites() {
        class CustomList extends LongArrayList {int reads;@Override public long getLong(int i){reads++;return super.getLong(i);}}
        var list=new CustomList();list.add(RenderDataPointUtil.createDataPoint(4,0,0xff123456,15,0,2));var source=new ColumnArrayView(list,1,0,1);var destination=output(2,2);
        assertFalse(NativeDhRenderReduction.reduce(source,null,destination,null));assertEquals(0,list.reads);RenderDataPointUtil.mergeMultiData(source,destination);assertEquals(1,list.reads);
        long[] large=new long[4097];large[4096]=RenderDataPointUtil.createDataPoint(4,0,0xff123456,15,0,2);var oversized=new ColumnArrayView(new LongArrayList(large),large.length,0,large.length);var result=output(2,2);
        assertFalse(NativeDhRenderReduction.reduce(oversized,null,result,null));RenderDataPointUtil.mergeMultiData(oversized,result);assertEquals(large[4096],result.get(0));assertEquals(0,result.get(1));
    }
}
