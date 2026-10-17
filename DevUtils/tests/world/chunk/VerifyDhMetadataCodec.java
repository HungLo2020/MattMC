import com.seibel.distanthorizons.api.enums.config.EDhApiDataCompressionMode;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.sql.dto.FullDataSourceV2DTO;
import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.bytes.ByteList;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Actual Frozen generation/compression metadata byte policy. Uniformity is a
 * later Current sidecar, verified by separate production compatibility tests. */
public final class VerifyDhMetadataCodec {
    static Method method(String name){for(var m:FullDataSourceV2DTO.class.getDeclaredMethods())if(m.getName().equals(name)){m.setAccessible(true);return m;}throw new AssertionError(name);}
    static final Method GEN_WRITE=method("writeGenerationStepsToBlob"),GEN_READ=method("readBlobToGenerationSteps"),COMP_READ=method("readBlobToWorldCompressionMode");
    static String call(Method method,Object...args){try{method.invoke(null,args);return "ok";}catch(InvocationTargetException e){var c=e.getCause();return c.getClass().getName()+":"+c.getMessage();}catch(Exception e){throw new AssertionError(e);}}
    static FullDataSourceV2 empty(){return FullDataSourceV2.createEmpty(DhSectionPos.encode((byte)6,0,0));}
    static void state(PrintWriter out,String name,ByteList values){out.println("STATE "+name+" "+values.size()+" "+HexFormat.of().formatHex(values.toByteArray()));}
    static ByteArrayList compress(byte[] data,EDhApiDataCompressionMode mode)throws Exception{
        var result=new ByteArrayList();
        if(mode==EDhApiDataCompressionMode.Z_STD_STREAM){var raw=new ByteArrayOutputStream();try(var out=new com.github.luben.zstd.ZstdOutputStream(raw)){out.write(data);}return ByteArrayList.wrap(raw.toByteArray());}
        try(var out=com.seibel.distanthorizons.core.util.objects.dataStreams.DhDataOutputStream.create(mode,result)){out.write(data);}return result;
    }
    public static void main(String[]args)throws Exception {
        int cases=0;
        try(var out=new PrintWriter(Files.newBufferedWriter(Path.of(args[0])))) {
            for(var mode:EDhApiDataCompressionMode.values())for(int length:new int[]{0,1,4095,4096,4100})for(int cut:new int[]{0,1,137,4095,4096})for(boolean generation:new boolean[]{true,false})try(var source=empty()){
                ByteList values=generation?source.columnGenerationSteps:source.columnWorldCompressionMode;values.size(length);for(int i=0;i<length;i++)values.set(i,(byte)77);
                byte[] bytes=new byte[cut];for(int i=0;i<cut;i++)bytes[i]=(byte)(i*61);
                var input=compress(bytes,mode);String label=mode+"/"+length+"/"+cut+"/"+generation;
                out.println("READ "+label+" "+call(generation?GEN_READ:COMP_READ,input,values,mode));state(out,label,values);cases++;
            }
            for(var mode:EDhApiDataCompressionMode.values())try(var source=empty()){
                for(int i=0;i<4096;i++)source.columnGenerationSteps.set(i,(byte)(i*31));
                var bytes=new ByteArrayList();out.println("WRITE "+mode+" "+call(GEN_WRITE,source.columnGenerationSteps,bytes,mode));out.println("BYTES "+mode+" "+HexFormat.of().formatHex(bytes.toByteArray()));cases++;
            }
            if(args.length>2){var manifest=Path.of(args[2]);var json=com.google.gson.JsonParser.parseString(Files.readString(manifest)).getAsJsonObject();
                for(var item:json.getAsJsonArray("rows"))try(var source=empty()){
                    var row=item.getAsJsonObject();var mode=EDhApiDataCompressionMode.getFromValue((byte)row.get("compression").getAsInt());
                    for(String field:new String[]{"compressedColumnGenStepByteArray","compressedWorldCompressionModeByteArray"}){
                        var name=row.getAsJsonObject("fields").getAsJsonObject(field).get("file").getAsString();var bytes=ByteArrayList.wrap(Files.readAllBytes(manifest.getParent().resolve(name)));
                        ByteList values=field.contains("GenStep")?source.columnGenerationSteps:source.columnWorldCompressionMode;
                        out.println("SAVED "+name+" "+call(field.contains("GenStep")?GEN_READ:COMP_READ,bytes,values,mode));state(out,name,values);cases++;
                    }
                }
            }
        }
        if(args[1].equals("current")){
            var type=Class.forName("net.minecraft.world.level.chunk.NativeDhMetadataCodec");
            long decoded=((java.util.concurrent.atomic.AtomicLong)type.getField("DECODED").get(null)).get();long encoded=((java.util.concurrent.atomic.AtomicLong)type.getField("ENCODED").get(null)).get();
            if(decoded==0||encoded==0)throw new AssertionError("Native metadata codec not exercised");System.out.println("NATIVE_METADATA_CODECS "+decoded+" "+encoded);
        }
        System.out.println("METADATA_CODEC_CASES "+cases);
    }
}
