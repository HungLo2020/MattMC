package net.minecraft.world.level.chunk;

import com.seibel.distanthorizons.api.enums.config.EDhApiDataCompressionMode;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.enums.EDhDirection;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.sql.dto.FullDataSourceV2DTO;
import com.seibel.distanthorizons.core.util.FullDataPointUtil;
import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeDhCodecTest {
    private static final Method READ = method("readBlobToDataSourceDataArrayV2", ByteArrayList.class, FullDataSourceV2.class, EDhDirection.class, EDhApiDataCompressionMode.class);
    private static final Method WRITE = method("writeDataSourceDataArrayToBlobV2", FullDataSourceV2.class, ByteArrayList.class, EDhDirection.class, EDhApiDataCompressionMode.class);
    private static Method method(String name, Class<?>... types) {
        try { var m=FullDataSourceV2DTO.class.getDeclaredMethod(name,types);m.setAccessible(true);return m; }
        catch(Exception e) { throw new AssertionError(e); }
    }
    private static void invoke(Method method,Object... values) throws Throwable {
        try { method.invoke(null,values); } catch(InvocationTargetException e) { throw e.getCause(); }
    }
    private static FullDataSourceV2 empty() {return FullDataSourceV2.createEmpty(DhSectionPos.encode((byte)6,0,0));}
    private static long point(int id,int height,int y,int block,int sky) throws Exception {return FullDataPointUtil.encode(id,height,y,(byte)block,(byte)sky);}
    private static void fill(FullDataSourceV2 source) throws Exception {
        var random=new Random(882);
        for(int i=0;i<4096;i++) {
            var column=source.getColumn(i);
            for(int j=0,n=random.nextInt(9);j<n;j++)column.add(point(random.nextInt(4000),1+random.nextInt(40),random.nextInt(3000),random.nextInt(16),random.nextInt(16)));
        }
    }
    private static String outcome(Method method,Object... args) {
        try{invoke(method,args);return "ok";}catch(Throwable e){return e.getClass().getName()+":"+e.getMessage();}
    }
    private static void same(FullDataSourceV2 a,FullDataSourceV2 b) {
        for(int i=0;i<4096;i++) {
            var first=a.getColumn(i);var second=b.getColumn(i);
            assertEquals(first.size(),second.size(),"column "+i);
            for(int p=0;p<first.size();p++)assertEquals(first.getLong(p),second.getLong(p),"column "+i+" point "+p);
        }
    }
    @Test void productionCodecMatchesCompatibilityBytesAndValuesAcrossEveryCompressionAndBorder() throws Throwable {
        long encoded=NativeDhCodec.ENCODED.get(),decoded=NativeDhCodec.DECODED.get();
        try(var source=empty()) {
            fill(source);
            for(var mode:EDhApiDataCompressionMode.values())for(var direction:new EDhDirection[]{null,EDhDirection.NORTH,EDhDirection.SOUTH,EDhDirection.EAST,EDhDirection.WEST}) {
                var javaBytes=new ByteArrayList();var rustBytes=new ByteArrayList();
                try(var java=empty();var rust=empty()) {
                    if (mode == EDhApiDataCompressionMode.Z_STD_STREAM) {
                        NativeDhCodec.enabled=false;var expected=outcome(WRITE,source,javaBytes,direction,mode);
                        NativeDhCodec.enabled=true;assertEquals(expected,outcome(WRITE,source,rustBytes,direction,mode));
                        NativeDhCodec.enabled=false;invoke(WRITE,source,javaBytes,direction,EDhApiDataCompressionMode.UNCOMPRESSED);
                        var raw=new java.io.ByteArrayOutputStream();
                        try(var compressed=new com.github.luben.zstd.ZstdOutputStream(raw)){compressed.write(javaBytes.toByteArray());}
                        var legacy=ByteArrayList.wrap(raw.toByteArray());
                        invoke(READ,legacy,java,direction,mode);NativeDhCodec.enabled=true;invoke(READ,legacy,rust,direction,mode);same(java,rust);continue;
                    }
                    NativeDhCodec.enabled=false;invoke(WRITE,source,javaBytes,direction,mode);invoke(READ,javaBytes,java,direction,mode);
                    NativeDhCodec.enabled=true;invoke(WRITE,source,rustBytes,direction,mode);invoke(READ,rustBytes,rust,direction,mode);
                    assertEquals(javaBytes,rustBytes,mode+"/"+direction);same(java,rust);
                }
            }
        } finally {NativeDhCodec.enabled=true;}
        assertTrue(NativeDhCodec.ENCODED.get()>encoded);assertTrue(NativeDhCodec.DECODED.get()>decoded);
    }
    @Test void truncatedAndMalformedVarintsPreserveMutationPrefixesAndExceptions() throws Throwable {
        try(var source=empty()) {
            source.getColumn(0).add(point(7,3,2,11,5));
            var good=new ByteArrayList();invoke(WRITE,source,good,EDhDirection.WEST,EDhApiDataCompressionMode.UNCOMPRESSED);
            for(int cut:new int[]{0,1,63,64,65,good.size()-2,good.size()-1,good.size()}) {
                var bytes=new ByteArrayList(good.subList(0,cut));
                try(var java=empty();var rust=empty()) {
                    java.getColumn(0).add(88);rust.getColumn(0).add(88);
                    NativeDhCodec.enabled=false;var expected=outcome(READ,bytes,java,EDhDirection.WEST,EDhApiDataCompressionMode.UNCOMPRESSED);
                    NativeDhCodec.enabled=true;var actual=outcome(READ,bytes,rust,EDhDirection.WEST,EDhApiDataCompressionMode.UNCOMPRESSED);
                    assertEquals(expected,actual,"cut "+cut);same(java,rust);
                }
            }
            for(byte[] bad:new byte[][]{{(byte)128,(byte)128,(byte)128,(byte)128,(byte)128},{(byte)255,(byte)255,(byte)255,(byte)255,15}}) {
                try(var java=empty();var rust=empty()) {
                    NativeDhCodec.enabled=false;var expected=outcome(READ,ByteArrayList.wrap(bad),java,EDhDirection.WEST,EDhApiDataCompressionMode.UNCOMPRESSED);
                    NativeDhCodec.enabled=true;var actual=outcome(READ,ByteArrayList.wrap(bad),rust,EDhDirection.WEST,EDhApiDataCompressionMode.UNCOMPRESSED);
                    assertEquals(expected,actual);same(java,rust);
                }
            }
        } finally {NativeDhCodec.enabled=true;}
    }
    @Test void initAndCloseKeepUnescapedPointsNativeAndDemandReadersRemainWritable() throws Exception {
        var source=empty();var escaped=source.getColumn(1);escaped.add(99);
        assertTrue(NativeDhCodec.initialize(source));assertSame(escaped,source.getColumn(1));assertEquals(0,escaped.getLong(0));
        var field=NativeDhSource.class.getDeclaredField("bindings");field.setAccessible(true);
        var bindings=(Object[])field.get(source.nativeSource());
        assertNull(bindings[2]);source.close();assertNull(bindings[2],"close must not construct unescaped readers");
        var late=source.getColumn(2);assertEquals(0,late.getLong(0));late.add(7);assertEquals(2,late.size());assertSame(late,source.getColumn(2));
        source.setColumn(2,null);assertNull(source.getColumn(2));source.close();
    }
    @Test void aliasesAndCustomListsKeepCompatibilityAdmission() throws Exception {
        try(var source=empty()) {
            var same=source.getColumn(0);source.setColumn(1,same);
            assertFalse(NativeDhCodec.initialize(source));
            source.setColumn(1,it.unimi.dsi.fastutil.longs.LongArrayList.of(11));assertFalse(NativeDhCodec.initialize(source));
        }
    }
}
