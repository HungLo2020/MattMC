package net.minecraft.world.level.chunk;

import com.seibel.distanthorizons.api.enums.config.EDhApiDataCompressionMode;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.sql.dto.FullDataSourceV2DTO;
import com.seibel.distanthorizons.core.util.objects.dataStreams.DhDataInputStream;
import com.seibel.distanthorizons.core.util.objects.dataStreams.DhDataOutputStream;
import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.bytes.ByteList;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeDhMetadataCodecTest {
    private static Method method(String name,Class<?>...args) {try{var m=FullDataSourceV2DTO.class.getDeclaredMethod(name,args);m.setAccessible(true);return m;}catch(Exception e){throw new AssertionError(e);}}
    private static final Method READ_GEN=method("readBlobToGenerationSteps",ByteArrayList.class,ByteList.class,EDhApiDataCompressionMode.class);
    private static final Method READ_COMP=method("readBlobToWorldCompressionMode",ByteArrayList.class,ByteList.class,EDhApiDataCompressionMode.class);
    private static final Method READ_UNIFORM=method("readBlobToSemanticHorizontalUniformity",ByteArrayList.class,FullDataSourceV2.class,EDhApiDataCompressionMode.class);
    private static final Method WRITE_GEN=method("writeGenerationStepsToBlob",ByteList.class,ByteArrayList.class,EDhApiDataCompressionMode.class);
    private static final Method WRITE_COMP=method("writeWorldCompressionModeToBlob",FullDataSourceV2.class,ByteArrayList.class,EDhApiDataCompressionMode.class);
    private static FullDataSourceV2 empty(){return FullDataSourceV2.createEmpty(DhSectionPos.encode((byte)6,0,0));}
    private static String outcome(Method method,Object...args) {try{method.invoke(null,args);return "ok";}catch(InvocationTargetException e){var c=e.getCause();return c.getClass().getName()+":"+c.getMessage();}catch(Exception e){throw new AssertionError(e);}}
    @Test void nativePlanesMatchOriginalIoAcrossLengthsSignedBytesAndEofPrefixes() throws Exception {
        for(int length:new int[]{0,1,4095,4096,4100})for(int cut:new int[]{0,1,137,4095,4096})try(var rust=empty()) {
            var java = new ByteArrayList(4096);java.size(length);rust.columnGenerationSteps.size(length);
            for(int i=0;i<length;i++){java.set(i,(byte)77);rust.columnGenerationSteps.set(i,(byte)77);}
            var bytes=new byte[cut];for(int i=0;i<cut;i++)bytes[i]=(byte)(i*61);
            var blob=ByteArrayList.wrap(bytes);
            NativeDhCodec.enabled=false;String expected=outcome(READ_GEN,blob,java,EDhApiDataCompressionMode.UNCOMPRESSED);
            NativeDhCodec.enabled=true;String actual=outcome(READ_GEN,blob,rust.columnGenerationSteps,EDhApiDataCompressionMode.UNCOMPRESSED);
            assertEquals(expected,actual,length+"/"+cut);assertArrayEquals(java.toByteArray(),rust.columnGenerationSteps.toByteArray());
        }finally{NativeDhCodec.enabled=true;}
        assertTrue(NativeDhMetadataCodec.DECODED.get()>0);
    }
    @Test void uniformityAndCompressedOutputsMatchExistingPolicyAndReaderIdentities() throws Exception {
        for(var mode:EDhApiDataCompressionMode.values()) {
            if(mode==EDhApiDataCompressionMode.Z_STD_STREAM)continue;
            try(var source=empty();var java=empty();var rust=empty()) {
                for(int i=0;i<4096;i++){source.columnGenerationSteps.set(i,(byte)(i*31));source.columnWorldCompressionMode.set(i,(byte)(i*7));source.setSemanticHorizontalUniformity(i/64,i%64,i%3==0);}
                var javaGen=new ByteArrayList();var rustGen=new ByteArrayList();var javaComp=new ByteArrayList();var rustComp=new ByteArrayList();
                NativeDhCodec.enabled=false;assertEquals("ok",outcome(WRITE_GEN,source.columnGenerationSteps,javaGen,mode));assertEquals("ok",outcome(WRITE_COMP,source,javaComp,mode));
                NativeDhCodec.enabled=true;assertEquals("ok",outcome(WRITE_GEN,source.columnGenerationSteps,rustGen,mode));assertEquals("ok",outcome(WRITE_COMP,source,rustComp,mode));
                assertEquals(javaGen,rustGen);assertEquals(javaComp,rustComp);
                var identity=rust.columnWorldCompressionMode;
                NativeDhCodec.enabled=false;assertEquals("ok",outcome(READ_COMP,javaComp,java.columnWorldCompressionMode,mode));assertEquals("ok",outcome(READ_UNIFORM,javaComp,java,mode));
                NativeDhCodec.enabled=true;assertEquals("ok",outcome(READ_COMP,rustComp,rust.columnWorldCompressionMode,mode));assertEquals("ok",outcome(READ_UNIFORM,rustComp,rust,mode));
                assertSame(identity,rust.columnWorldCompressionMode);assertArrayEquals(java.columnWorldCompressionMode.toByteArray(),rust.columnWorldCompressionMode.toByteArray());
                for(int x=0;x<64;x++)for(int z=0;z<64;z++)assertEquals(java.hasSemanticHorizontalUniformity(x,z),rust.hasSemanticHorizontalUniformity(x,z));
            }finally{NativeDhCodec.enabled=true;}
        }
        assertTrue(NativeDhMetadataCodec.ENCODED.get()>0);
    }
    @Test void uniformDecodePreservesSkipAndPrefixAndNormalizesNonzero() throws Exception {
        byte[] bytes=new byte[8192];Arrays.fill(bytes,0,4096,(byte)88);for(int i=4096;i<8192;i++)bytes[i]=(byte)(i%4==0?1:i%4==1?2:255);
        for(int cut:new int[]{0,4095,4096,4097,4119,8192})try(var java=empty();var rust=empty()) {
            for(int i=0;i<4096;i++){java.setSemanticHorizontalUniformity(i/64,i%64,true);rust.setSemanticHorizontalUniformity(i/64,i%64,true);}
            var blob=ByteArrayList.wrap(Arrays.copyOf(bytes,cut));
            NativeDhCodec.enabled=false;String expected=outcome(READ_UNIFORM,blob,java,EDhApiDataCompressionMode.UNCOMPRESSED);
            NativeDhCodec.enabled=true;String actual=outcome(READ_UNIFORM,blob,rust,EDhApiDataCompressionMode.UNCOMPRESSED);assertEquals(expected,actual);
            for(int x=0;x<64;x++)for(int z=0;z<64;z++)assertEquals(java.hasSemanticHorizontalUniformity(x,z),rust.hasSemanticHorizontalUniformity(x,z));
        }finally{NativeDhCodec.enabled=true;}
    }
    @Test void byteArrayBulkModeDoesNotImportTrailingDataAndPreservesMetadataLeaseAfterSourceClose() throws Exception {
        var source=empty();var values=source.columnGenerationSteps;source.close();
        byte[] data=new byte[4100];data[4096]=97;
        try(var in=DhDataInputStream.create(data,EDhApiDataCompressionMode.UNCOMPRESSED)) {
            assertTrue(in.codecByteArrayInput());assertTrue(NativeDhMetadataCodec.decode(values,in));
            // The bounded read-ahead is internal to the private DTO scope; it
            // neither changes the plane extent nor imports bytes past its payload.
            assertEquals(4096,values.size());assertEquals(0,values.getByte(4095));
        }
    }
}
