import com.seibel.distanthorizons.api.enums.config.EDhApiDataCompressionMode;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.enums.EDhDirection;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.sql.dto.FullDataSourceV2DTO;
import com.seibel.distanthorizons.core.util.FullDataPointUtil;
import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Same observer source, compiled separately against actual Current and Frozen. */
public final class VerifyDhPointCodec {
    static Method method(String name,Class<?>...types)throws Exception {
        try{var m=FullDataSourceV2DTO.class.getDeclaredMethod(name,types);m.setAccessible(true);return m;}
        catch(NoSuchMethodException e){for(var m:FullDataSourceV2DTO.class.getDeclaredMethods())if(m.getName().equals(name)&&m.getParameterCount()==types.length){m.setAccessible(true);return m;}throw e;}
    }
    static final Method WRITE,READ,LEGACY;
    static {try {
        WRITE=method("writeDataSourceDataArrayToBlobV2",FullDataSourceV2.class,ByteArrayList.class,EDhDirection.class,EDhApiDataCompressionMode.class);
        READ=method("readBlobToDataSourceDataArrayV2",ByteArrayList.class,FullDataSourceV2.class,EDhDirection.class,EDhApiDataCompressionMode.class);
        LEGACY=method("readBlobToDataSourceDataArrayV1",ByteArrayList.class,FullDataSourceV2.class,EDhApiDataCompressionMode.class);
    } catch(Exception e){throw new ExceptionInInitializerError(e);}}
    static FullDataSourceV2 empty(){return FullDataSourceV2.createEmpty(DhSectionPos.encode((byte)6,0,0));}
    static void invoke(Method method,Object...args)throws Throwable {
        for(int i=0;i<args.length;i++)if(args[i] instanceof FullDataSourceV2 source && method.getParameterTypes()[i].isArray())args[i]=FullDataSourceV2.class.getField("dataPoints").get(source);
        try{method.invoke(null,args);}catch(InvocationTargetException e){throw e.getCause();}
    }
    static String outcome(Method m,Object...args){try{invoke(m,args);return "ok";}catch(Throwable e){return e.getClass().getName()+":"+e.getMessage();}}
    static void state(PrintWriter out,String label,FullDataSourceV2 source){
        out.println("STATE "+label);
        for(int x=0;x<64;x++)for(int z=0;z<64;z++){
            var c=source.getColumnAtRelPos(x,z);out.print(x*64+z+":"+c.size());
            for(int i=0;i<c.size();i++){out.print(':');out.print(Long.toUnsignedString(c.getLong(i),16));}out.println();
        }
    }
    static void fill(FullDataSourceV2 source)throws Exception {
        var random=new Random(1672839);
        for(int x=0;x<64;x++)for(int z=0;z<64;z++){
            var c=source.getColumnAtRelPos(x,z);
            for(int i=0,n=random.nextInt(17);i<n;i++)c.add(FullDataPointUtil.encode(random.nextInt(4000000),1+random.nextInt(100),random.nextInt(3900),(byte)random.nextInt(16),(byte)random.nextInt(16)));
        }
    }
    static ByteArrayList legacy(boolean invalid)throws IOException {
        var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);
        for(int i=0;i<4096;i++){
            int count=i%503==0?2:0;out.writeShort(count);
            for(int j=0;j<count;j++)out.writeLong((long)(i+1)|((invalid&&i==0&&j==1?0L:3L)<<32)|(27L<<44)|(7L<<60));
        }
        return ByteArrayList.wrap(bytes.toByteArray());
    }
    public static void main(String[]args)throws Throwable {
        int cases=0;
        try(var out=new PrintWriter(Files.newBufferedWriter(Path.of(args[0])));var source=empty()) {
            fill(source);
            for(var mode:EDhApiDataCompressionMode.values())for(var side:new EDhDirection[]{null,EDhDirection.NORTH,EDhDirection.SOUTH,EDhDirection.EAST,EDhDirection.WEST}){
                var bytes=new ByteArrayList();String label=mode+"/"+side;
                out.println("WRITE "+label+" "+outcome(WRITE,source,bytes,side,mode));
                if(mode==EDhApiDataCompressionMode.Z_STD_STREAM){
                    invoke(WRITE,source,bytes,side,EDhApiDataCompressionMode.UNCOMPRESSED);
                    var raw=new ByteArrayOutputStream();try(var compressed=new com.github.luben.zstd.ZstdOutputStream(raw)){compressed.write(bytes.toByteArray());}
                    bytes=ByteArrayList.wrap(raw.toByteArray());
                }
                out.println("BYTES "+label+" "+HexFormat.of().formatHex(bytes.toByteArray()));
                try(var target=empty()){out.println("READ "+label+" "+outcome(READ,bytes,target,side,mode));state(out,label,target);}
                cases++;
            }
            for(boolean invalid:new boolean[]{false,true}) {
                var bytes=legacy(invalid);
                for(int cut:new int[]{0,1,2,9,17,bytes.size()-1,bytes.size()})try(var target=empty()) {
                    target.getColumnAtRelPos(0,0).add(123);
                    String label="legacy/"+invalid+"/"+cut;
                    out.println("READ "+label+" "+outcome(LEGACY,new ByteArrayList(bytes.subList(0,cut)),target,EDhApiDataCompressionMode.UNCOMPRESSED));state(out,label,target);cases++;
                }
            }
            var bytes=new ByteArrayList();invoke(WRITE,source,bytes,EDhDirection.WEST,EDhApiDataCompressionMode.UNCOMPRESSED);
            for(int cut:new int[]{0,1,63,64,65,127,bytes.size()-2,bytes.size()-1,bytes.size()})try(var target=empty()) {
                target.getColumnAtRelPos(0,0).add(123);String label="truncated/"+cut;
                out.println("READ "+label+" "+outcome(READ,new ByteArrayList(bytes.subList(0,cut)),target,EDhDirection.WEST,EDhApiDataCompressionMode.UNCOMPRESSED));state(out,label,target);cases++;
            }
            for(byte[] bad:new byte[][]{{(byte)128,(byte)128,(byte)128,(byte)128,(byte)128},{(byte)255,(byte)255,(byte)255,(byte)255,15}})try(var target=empty()) {
                String label="malformed/"+HexFormat.of().formatHex(bad);
                out.println("READ "+label+" "+outcome(READ,ByteArrayList.wrap(bad),target,EDhDirection.WEST,EDhApiDataCompressionMode.UNCOMPRESSED));state(out,label,target);cases++;
            }
            if(args.length>2) {
                var manifest=Path.of(args[2]);var data=com.google.gson.JsonParser.parseString(Files.readString(manifest)).getAsJsonObject();
                for(var item:data.getAsJsonArray("rows"))try(var target=empty()) {
                    var row=item.getAsJsonObject();String file=row.get("file").getAsString(),direction=row.get("direction").getAsString();
                    var side=direction.equals("interior")?null:EDhDirection.valueOf(direction.toUpperCase(Locale.ROOT));
                    var mode=EDhApiDataCompressionMode.getFromValue((byte)row.get("compression").getAsInt());
                    var savedBytes=ByteArrayList.wrap(Files.readAllBytes(manifest.getParent().resolve(file)));
                    String label="saved/"+file;String status=outcome(READ,savedBytes,target,side,mode);
                    out.println("READ "+label+" "+status);state(out,label,target);
                    if(!status.equals("ok"))throw new AssertionError("Saved source rejected: "+label+" "+status);
                    var encoded=new ByteArrayList();invoke(WRITE,target,encoded,side,mode);
                    out.println("REENCODE "+label+" "+HexFormat.of().formatHex(encoded.toByteArray()));cases++;
                }
            }
        }
        if(args[1].equals("current")){
            var type=Class.forName("net.minecraft.world.level.chunk.NativeDhCodec");
            long decoded=((java.util.concurrent.atomic.AtomicLong)type.getField("DECODED").get(null)).get();
            long encoded=((java.util.concurrent.atomic.AtomicLong)type.getField("ENCODED").get(null)).get();
            if(decoded==0||encoded==0)throw new AssertionError("Native point codec not exercised");
            System.out.println("NATIVE_POINT_CODECS "+decoded+" "+encoded);
        }
        System.out.println("POINT_CODEC_CASES "+cases);
    }
}
