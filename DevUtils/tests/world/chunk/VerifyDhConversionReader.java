import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.sql.dto.FullDataSourceV2DTO;
import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import java.lang.invoke.*;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.util.*;

/** Same saved-source hydration and ordered point-consumption workload on
 * unchanged Frozen and Current. Contextual colors and GPU rendering excluded. */
public final class VerifyDhConversionReader {
    static MethodHandle sourceOwner, open, column;
    static boolean nativeReader;
    static void setup(boolean requested) throws Exception {
        if (!requested) return;
        try {
            var ownerClass=Class.forName("net.minecraft.world.level.chunk.NativeDhSource");
            var readerClass=Class.forName("net.minecraft.world.level.chunk.NativeDhConversionReader");
            var lookup=MethodHandles.publicLookup();
            sourceOwner=lookup.unreflect(FullDataSourceV2.class.getMethod("nativeSource"))
                .asType(MethodType.methodType(Object.class,FullDataSourceV2.class));
            open=lookup.unreflect(ownerClass.getMethod("conversionReader"))
                .asType(MethodType.methodType(AutoCloseable.class,Object.class));
            column=lookup.unreflect(readerClass.getMethod("column",int.class))
                .asType(MethodType.methodType(LongList.class,AutoCloseable.class,int.class));
            nativeReader=true;
        } catch(ClassNotFoundException ignored) {}
    }
    static List<FullDataSourceV2DTO> inputs(Path manifest) throws Exception {
        var data=com.google.gson.JsonParser.parseString(Files.readString(manifest)).getAsJsonObject();
        var result=new ArrayList<FullDataSourceV2DTO>();
        for(var item:data.getAsJsonArray("rows")) {
            var row=item.getAsJsonObject();var dto=FullDataSourceV2DTO.CreateEmptyDataSourceForDecoding();
            dto.pos=DhSectionPos.encode((byte)(row.get("level").getAsInt()+6),row.get("x").getAsInt(),row.get("z").getAsInt());
            dto.dataFormatVersion=(byte)row.get("version").getAsInt();dto.compressionModeValue=(byte)row.get("compression").getAsInt();
            for(var field:row.getAsJsonObject("fields").entrySet())
                FullDataSourceV2DTO.class.getField(field.getKey()).set(dto,ByteArrayList.wrap(Files.readAllBytes(
                    manifest.getParent().resolve(field.getValue().getAsJsonObject().get("file").getAsString()))));
            result.add(dto);
        }
        return result;
    }
    static long run(List<FullDataSourceV2DTO> inputs) throws Throwable {
        long hash=1;
        for(var dto:inputs)try(var source=dto.createUnitTestDataSource();
            var reader=nativeReader?(AutoCloseable)open.invokeExact((Object)sourceOwner.invokeExact(source)):null) {
            for(int i=0;i<4096;i++) {
                LongList points=reader==null?source.getColumnAtRelPos(i/64,i%64):(LongList)column.invokeExact(reader,i);
                hash=31*hash+(points==null?-1:points.size());
                if(points!=null)for(int j=0;j<points.size();j++)hash=31*hash+points.getLong(j);
            }
        }
        return hash;
    }
    public static void main(String[] args) throws Throwable {
        setup(Boolean.parseBoolean(args[1]));var inputs=inputs(Path.of(args[0]));
        var bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();bean.setThreadAllocatedMemoryEnabled(true);
        System.out.println("READER "+nativeReader);
        long expected=0;
        try {
            for(int i=0;i<16;i++) {
                long before=bean.getThreadAllocatedBytes(Thread.currentThread().threadId()),start=System.nanoTime();
                long hash=run(inputs),elapsed=System.nanoTime()-start;
                long allocated=bean.getThreadAllocatedBytes(Thread.currentThread().threadId())-before;
                if(i==0)expected=hash;else if(expected!=hash)throw new AssertionError("Point state changed");
                if(i>=4)System.out.println("SAMPLE "+(i-4)+" "+elapsed+" "+hash+" "+allocated);
            }
        } finally { for(var dto:inputs)dto.close(); }
    }
}
