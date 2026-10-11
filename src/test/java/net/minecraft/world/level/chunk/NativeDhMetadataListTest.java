package net.minecraft.world.level.chunk;

import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.bytes.ByteList;
import it.unimi.dsi.fastutil.longs.LongList;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.io.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("parity")
class NativeDhMetadataListTest {
    private static void equal(ByteList expected, ByteList actual) {
        assertEquals(expected,actual);assertEquals(actual,expected);
        assertEquals(expected.hashCode(),actual.hashCode());
        assertArrayEquals(expected.toByteArray(),actual.toByteArray());
    }
    @Test void mutableViewsMatchByteArrayListAcrossSignedValuesResizeAndIteratorEdits() {
        try(var source=new NativeDhSource(new LongList[4096])) {
            var actual=source.generation();var expected=new ByteArrayList(new byte[4096]);
            for(int i=0;i<4096;i++){actual.set(i,(byte)i);expected.set(i,(byte)i);}
            equal(expected,actual);
            actual.size(4095);expected.size(4095);equal(expected,actual);
            actual.size(5000);expected.size(5000);equal(expected,actual);
            actual.addElements(7,new byte[]{-128,-1,0,127});expected.addElements(7,new byte[]{-128,-1,0,127});
            actual.removeElements(10,90);expected.removeElements(10,90);equal(expected,actual);
            var a=actual.listIterator(20);var e=expected.listIterator(20);
            assertEquals(e.nextByte(),a.nextByte());a.set((byte)-65);e.set((byte)-65);
            a.add((byte)17);e.add((byte)17);assertEquals(e.previousByte(),a.previousByte());a.remove();e.remove();
            actual.subList(1,4).clear();expected.subList(1,4).clear();equal(expected,actual);
            byte[] copied=new byte[30];actual.getElements(4,copied,3,20);
            byte[] other=new byte[30];expected.getElements(4,other,3,20);assertArrayEquals(other,copied);
            actual.setElements(3,new byte[]{99,-128});expected.setElements(3,new byte[]{99,-128});equal(expected,actual);
            assertThrows(IndexOutOfBoundsException.class,()->actual.addElements(0,new byte[2],0,3));equal(expected,actual);
            assertThrows(IllegalArgumentException.class,()->actual.size(-1));equal(expected,actual);
            actual.clear();expected.clear();
            actual.addElements(0,new byte[]{1,2,3});expected.addElements(0,new byte[]{1,2,3});
            actual.addAll(1,actual);expected.addAll(1,expected);equal(expected,actual);
            actual.addAll(2,actual.subList(0,3));expected.addAll(2,expected.subList(0,3));equal(expected,actual);
            actual.clear();expected.clear();actual.size(4096);expected.size(4096);equal(expected,actual);
        }
    }
    @Test void retainedMetadataSurvivesSourceCloseAndSerializesAsIndependentValues() throws Exception {
        var source=new NativeDhSource(new LongList[4096]);var generation=source.generation();var compression=source.compression();
        source.initializeUniform(true);source.uniform(123,false);
        generation.set(0,(byte)-128);compression.set(4095,(byte)127);
        source.close();assertFalse(source.isLive());
        assertEquals((byte)-128,generation.getByte(0));assertEquals((byte)127,compression.getByte(4095));
        assertTrue(source.uniform(0));assertFalse(source.uniform(123));
        generation.add(0,(byte)31);compression.removeByte(4095);
        var buffer=new ByteArrayOutputStream();try(var out=new ObjectOutputStream(buffer)){out.writeObject(generation);}
        ByteList restored;try(var in=new ObjectInputStream(new ByteArrayInputStream(buffer.toByteArray()))){restored=(ByteList)in.readObject();}
        equal(generation,restored);restored.set(0,(byte)9);assertEquals((byte)31,generation.getByte(0));
        source.close(); // Table release is idempotent; metadata lifetime is independent.
    }
    @Test void persistenceDecoderRetainsSuccessfulBytePrefixBeforeTruncatedBlobFailure() throws Exception {
        var dto=com.seibel.distanthorizons.core.sql.dto.FullDataSourceV2DTO.class;
        var mode=com.seibel.distanthorizons.api.enums.config.EDhApiDataCompressionMode.UNCOMPRESSED;
        for(String name:new String[]{"readBlobToGenerationSteps","readBlobToWorldCompressionMode"}) {
            var method=dto.getDeclaredMethod(name,ByteArrayList.class,ByteList.class,mode.getClass());method.setAccessible(true);
            for(int count:new int[]{0,137,4096})try(var source=new NativeDhSource(new LongList[4096])) {
                var output=name.equals("readBlobToGenerationSteps")?source.generation():source.compression();
                byte[] expected=new byte[4096];java.util.Arrays.fill(expected,(byte)73);output.setElements(0,expected);
                byte[] input=new byte[count];for(int i=0;i<count;i++)input[i]=(byte)(i*17);
                System.arraycopy(input,0,expected,0,count);
                if(count==4096)method.invoke(null,new ByteArrayList(input),output,mode);
                else {
                    var failure=assertThrows(java.lang.reflect.InvocationTargetException.class,()->method.invoke(null,new ByteArrayList(input),output,mode));
                    assertInstanceOf(com.seibel.distanthorizons.core.util.objects.DataCorruptedException.class,failure.getCause());
                }
                assertArrayEquals(expected,output.toByteArray());
            }
        }
    }
}
