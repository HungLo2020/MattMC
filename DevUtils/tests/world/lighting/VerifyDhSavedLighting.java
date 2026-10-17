import com.seibel.distanthorizons.common.wrappers.chunk.ChunkWrapper;
import com.seibel.distanthorizons.common.wrappers.worldGeneration.chunkFileHandling.ChunkCompoundTagParser;
import com.seibel.distanthorizons.core.wrapperInterfaces.chunk.ChunkLightStorage;
import java.io.*;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.GZIPOutputStream;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.*;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.ticks.ProtoChunkTicks;

/** Execute untouched Frozen saved-light imports, or an identical complete CPU workload. */
public final class VerifyDhSavedLighting {
    record Scene(int min, int count, int kind, byte[][] block, byte[][] sky) {}
    static byte[] bytes(int mode, int seed) {
        if (mode == 0) return null;
        if (mode == 1) return new byte[0];
        if (mode == 6) return new byte[3];
        byte[] b = new byte[mode == 5 ? 2057 : 2048];
        if (mode == 3) Arrays.fill(b,(byte)0x77);
        if (mode >= 4) for(int i=0;i<b.length;i++) b[i]=(byte)(i*17+seed*31);
        return b;
    }
    static Scene scene(int seed) {
        int count = seed % 2 == 0 ? 3 : 8;
        int rows = seed % 11 == 0 ? 0 : count + seed % 3;
        byte[][] b=new byte[rows][],s=new byte[rows][];
        for(int i=0;i<rows;i++) { b[i]=bytes((seed+i*3)%6,seed);s[i]=bytes((seed*3+i)%6,seed+1); }
        if(seed>=56 && rows>0) { b[0]=bytes(6,0);s[0]=seed%2==0?new byte[0]:null; }
        return new Scene(-64-seed%3*16,count,seed>=52&&seed<56?seed-51:0,b,s);
    }
    static CompoundTag tag(Scene s) {
        if(s.kind==4)return null;
        var root=new CompoundTag();
        if(s.kind==1)return root;
        if(s.kind==2){root.putInt("sections",7);return root;}
        var rows=new ListTag();
        if(s.kind==3){rows.add(IntTag.valueOf(8));root.put("sections",rows);return root;}
        for(int i=0;i<s.block.length;i++) {
            var row=new CompoundTag();row.putByte("Y",(byte)(31-i*7));
            if(s.block[i]!=null)row.putByteArray("BlockLight",s.block[i]);
            if(s.sky[i]!=null)row.putByteArray("SkyLight",s.sky[i]);rows.add(row);
        }
        root.put("sections",rows);return root;
    }
    static ProtoChunk chunk(int min,int count) {
        var sections=new LevelChunkSection[count];
        for(int i=0;i<count;i++)sections[i]=new LevelChunkSection(new PalettedContainer<>(Blocks.AIR.defaultBlockState(),Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY)),null);
        return new ProtoChunk(new ChunkPos(-37,41),UpgradeData.EMPTY,sections,new ProtoChunkTicks<>(),new ProtoChunkTicks<>(),LevelHeightAccessor.create(min,count*16),null,null);
    }
    static String hash(Class<?> c) throws Exception {
        try(var stream=c.getResourceAsStream("/"+c.getName().replace('.','/')+".class")) {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes()));
        }
    }
    static void array(DataOutputStream out,byte[] bytes)throws IOException {out.writeInt(bytes==null?-1:bytes.length);if(bytes!=null)out.write(bytes);}
    static void snapshot(DataOutputStream out,ChunkLightStorage storage,int min,int count)throws IOException {
        out.writeBoolean(storage.isEmpty());
        for(int section=0;section<count;section++) {
            var v=storage.lightSections==null?null:storage.lightSections[section];out.writeBoolean(v!=null);
            if(v!=null) { out.writeByte(v.constantValue);out.writeBoolean(v.data!=null);for(short n:v.counts)out.writeShort(n); }
        }
        for(int y=min-1;y<=min+count*16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++)out.writeByte(storage.get(x,y,z));
    }
    static void record(Path path)throws Exception {
        try(var out=new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(path)))) {
            out.writeInt(0x44484931);
            Class<?>[] pins={ChunkCompoundTagParser.class,ChunkLightStorage.class,ChunkLightStorage.LightSection.class,ChunkWrapper.class,CompoundTag.class,ListTag.class,ProtoChunk.class};
            out.writeInt(pins.length);for(var c:pins){out.writeUTF(c.getName());out.writeUTF(hash(c));}
            out.writeInt(64);
            for(int seed=0;seed<64;seed++) {
                var s=scene(seed);out.writeInt(seed);out.writeInt(s.min);out.writeInt(s.count);out.writeInt(s.kind);out.writeInt(s.block.length);
                for(int i=0;i<s.block.length;i++){array(out,s.block[i]);array(out,s.sky[i]);}
                ChunkCompoundTagParser.CombinedChunkLightStorage result=null;String error="";
                try { result=ChunkCompoundTagParser.readLight(chunk(s.min,s.count),tag(s)); }catch(RuntimeException e){error=e.getClass().getName();}
                out.writeUTF(error);out.writeBoolean(result!=null);
                if(result!=null) {
                    snapshot(out,result.blockLightStorage,s.min,s.count);snapshot(out,result.skyLightStorage,s.min,s.count);
                    result.blockLightStorage.clear();result.skyLightStorage.clear();
                    result.blockLightStorage.set(7,s.min+1,9,3);result.skyLightStorage.set(7,s.min+1,9,11);
                    snapshot(out,result.blockLightStorage,s.min,s.count);snapshot(out,result.skyLightStorage,s.min,s.count);
                }
            }
        }
        System.out.println("Recorded64 actual Frozen saved-light imports and mutable clear/edit outcomes");
    }
    static Method nativeRead;
    static Object read(ChunkAccess chunk,CompoundTag tag)throws Exception {
        if(nativeRead!=null)return nativeRead.invoke(null,chunk,tag);
        return ChunkCompoundTagParser.readLight(chunk,tag);
    }
    static long consume(Object result)throws Exception {
        if(nativeRead==null) {
            var r=(ChunkCompoundTagParser.CombinedChunkLightStorage)result;long sum=r.blockLightStorage.isEmpty()?0:1;
            for(int i=0;i<32;i++)sum=sum*31+r.blockLightStorage.get(i&15,-64+i*11%384,i*3&15)+16*r.skyLightStorage.get(i&15,-64+i*11%384,i*3&15);
            return sum;
        }
        var f=result.getClass().getMethod("nativeField").invoke(result);if(f==null)throw new IllegalStateException("Native import declined");
        var light=f.getClass().getMethod("light",int.class,int.class,int.class,boolean.class);
        long sum=(boolean)f.getClass().getMethod("hasAllocatedSections",boolean.class).invoke(f,false)?1:0;
        for(int i=0;i<32;i++)sum=sum*31+(int)light.invoke(f,i&15,-64+i*11%384,i*3&15,false)+16*(int)light.invoke(f,i&15,-64+i*11%384,i*3&15,true);
        return sum;
    }
    static long round(ChunkAccess chunk,CompoundTag tag,int calls)throws Exception {long sum=0;for(int i=0;i<calls;i++)sum+=consume(read(chunk,tag));return sum;}
    public static void main(String[] args)throws Exception {
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
        if(args[0].equals("record")){record(Path.of(args[1]));return;}
        if(!args[0].equals("bench"))throw new IllegalArgumentException("record PATH | bench CALLS");
        try { nativeRead=ChunkCompoundTagParser.class.getMethod("readLightingForGeneration",ChunkAccess.class,CompoundTag.class); }catch(NoSuchMethodException ignored){}
        int calls=Integer.parseInt(args[1]);byte[][] block=new byte[24][],sky=new byte[24][];
        for(int i=0;i<24;i++){block[i]=bytes(i%3==0?1:4,i);sky[i]=bytes(i%3==2?1:4,i+1);}
        var input=tag(new Scene(-64,24,0,block,sky));var chunk=chunk(-64,24);
        for(int i=0;i<5;i++)round(chunk,input,calls);
        var images=new TreeSet<String>();
        for(String line:Files.readAllLines(Path.of("/proc/self/maps"))) {
            String[] parts=line.split("\\s+",6);if(parts.length==6&&parts[5].contains("mattmc_rust"))images.add(parts[5]);
        }
        if(images.size()>1)throw new IllegalStateException("Multiple native images: "+images);
        String nativeHash=images.isEmpty()?"":HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(Path.of(images.first()))));
        long start=System.nanoTime(),sum=round(chunk,input,calls),elapsed=System.nanoTime()-start;
        System.out.println("{\"elapsed_ns\":"+elapsed+",\"calls\":"+calls+",\"checksum\":"+sum+",\"native_import\":"+(nativeRead!=null)+",\"native_image_sha256\":\""+nativeHash+"\"}");
    }
}
