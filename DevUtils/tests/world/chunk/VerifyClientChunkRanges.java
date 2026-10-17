import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Consumer;
import java.util.zip.*;
import net.minecraft.client.multiplayer.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/** Actual Frozen getChunk traversal versus Current retained range consumer.
 * Compiles separately against each repository, never copies its lookup policy. */
public class VerifyClientChunkRanges {
    static class World extends ClientLevel {
        World() { super(null,null,null,null,0,0,null,false,0L,0); }
        @Override public void unload(LevelChunk chunk) {}
    }
    static Object allocate(Class<?> c) throws Exception {
        Class<?> u=Class.forName("sun.misc.Unsafe");Field f=u.getDeclaredField("theUnsafe");f.setAccessible(true);
        return u.getMethod("allocateInstance",Class.class).invoke(f.get(null),c);
    }
    static Field field(Class<?> type,String name) throws Exception {var f=type.getDeclaredField(name);f.setAccessible(true);return f;}
    static Method method(Class<?> type,String name,Class<?>... args) throws Exception {var m=type.getDeclaredMethod(name,args);m.setAccessible(true);return m;}
    static class Fixture {
        ClientChunkCache cache;Class<?> type;Method replace,index;Method nativeRange;
        Fixture(int radius,boolean current) throws Exception {
            cache=(ClientChunkCache)allocate(ClientChunkCache.class);
            field(ClientChunkCache.class,"level").set(cache,allocate(World.class));
            type=Class.forName("net.minecraft.client.multiplayer.ClientChunkCache$Storage");
            var ctor=type.getDeclaredConstructors()[0];ctor.setAccessible(true);
            field(ClientChunkCache.class,"storage").set(cache,ctor.newInstance(cache,radius));
            replace=method(type,"replace",int.class,LevelChunk.class);index=method(type,"getIndex",int.class,int.class);
            if(current)nativeRange=ClientChunkCache.class.getMethod("forEachLoadedChunkInRange",int.class,int.class,int.class,Consumer.class);
        }
        void put(int x,int z) throws Exception {
            var chunk=(LevelChunk)allocate(LevelChunk.class);
            field(ChunkAccess.class,"chunkPos").set(chunk,new ChunkPos(x,z));
            field(ChunkAccess.class,"sections").set(chunk,new LevelChunkSection[0]);
            var storage=field(ClientChunkCache.class,"storage").get(cache);
            replace.invoke(storage,index.invoke(storage,x,z),chunk);
        }
        void scan(int x,int z,int radius,Consumer<LevelChunk> callback) throws Exception {
            if(nativeRange!=null) {
                try {nativeRange.invoke(cache,x,z,radius,callback);}
                catch(InvocationTargetException failure) {if(failure.getCause() instanceof Stop stop)throw stop;throw failure;}
            } else for(int cz=z-radius;cz<=z+radius;cz++)for(int cx=x-radius;cx<=x+radius;cx++) {
                var chunk=cache.getChunk(cx,cz,ChunkStatus.FULL,false);if(chunk!=null)callback.accept(chunk);
            }
        }
    }
    static class Stop extends RuntimeException {}
    static String sha(byte[] bytes) throws Exception {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    public static void main(String[] args) throws Exception {
        boolean current=args[0].equals("current");
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();int cases=0;
        try(var out=new DataOutputStream(bytes)) {
            out.writeInt(0x43524731);
            for(int origin:new int[]{0,-4097,1874990,-1874990}) {
                var f=new Fixture(9,current);f.cache.updateViewCenter(origin,origin);
                var random=new Random(0x43524731);
                for(int z=-9;z<=9;z++)for(int x=-9;x<=9;x++)f.put(origin+x,origin+z);
                for(int step=0;step<160;step++) {
                    int x=origin+random.nextInt(37)-18,z=origin+random.nextInt(37)-18;
                    switch(step%5) {
                        case 0->f.put(x,z);
                        case 1->f.cache.drop(new ChunkPos(x,z));
                        case 2->f.cache.updateViewCenter(x,z);
                        case 3->f.cache.updateViewRadius(random.nextInt(10));
                        case 4->{for(int i=0;i<12;i++)f.put(x+i%4,z+i/4);}
                    }
                    var selected=new ArrayList<ChunkPos>();
                    f.scan(x,z,11,c->selected.add(c.getPos()));
                    out.writeInt(selected.size());for(var p:selected){out.writeInt(p.x);out.writeInt(p.z);}cases++;
                }
            }
            for(int action=0;action<5;action++) {
                var f=new Fixture(9,current);
                for(int z=-9;z<=9;z++)for(int x=-9;x<=9;x++)f.put(x,z);
                var selected=new ArrayList<ChunkPos>();int kind=action;boolean[] first={true};boolean stopped=false;
                try { f.scan(0,0,9,c->{
                    selected.add(c.getPos());
                    if(first[0]) { first[0]=false;try {switch(kind) {
                        case 0->{f.cache.drop(new ChunkPos(-8,-9));f.put(0,0);}
                        case 1->f.cache.updateViewCenter(9,9);
                        case 2->f.cache.updateViewRadius(2);
                        case 3->{var nested=new ArrayList<ChunkPos>();f.scan(0,0,9,n->nested.add(n.getPos()));if(nested.size()!=361)throw new AssertionError();}
                        case 4->throw new Stop();
                    }}catch(Stop stop){throw stop;}catch(Exception failure){throw new RuntimeException(failure);}}
                }); }catch(Stop expected){stopped=true;}
                out.writeBoolean(stopped);out.writeInt(selected.size());for(var p:selected){out.writeInt(p.x);out.writeInt(p.z);}cases++;
            }
        }
        if(current) {
            byte[] expected;try(var in=new GZIPInputStream(Files.newInputStream(Path.of(args[1])))){expected=in.readAllBytes();}
            if(!Arrays.equals(expected,bytes.toByteArray()))throw new AssertionError("Frozen range/callback outputs differ");
        } else try(var out=new GZIPOutputStream(Files.newOutputStream(Path.of(args[1])))){bytes.writeTo(out);}
        System.out.println("RANGE_CASES "+cases+" "+sha(bytes.toByteArray())+" PASS");
        for(String name:new String[]{"net.minecraft.client.multiplayer.ClientChunkCache","net.minecraft.client.multiplayer.ClientChunkCache$Storage","net.minecraft.world.level.chunk.LevelChunk","net.minecraft.world.level.chunk.ChunkAccess"}) {
            var type=Class.forName(name);try(var in=type.getResourceAsStream("/"+name.replace('.','/')+".class")){System.out.println("CLASS "+name+" "+sha(in.readAllBytes()));}
        }
        if(current) {
            Path image=Path.of(System.getProperty("mattmc.rust.natives.dir"),"mattmc_rust-linux-x64.so").toRealPath();
            String maps=Files.readString(Path.of("/proc/self/maps"));
            if(maps.lines().noneMatch(line->line.endsWith(" "+image)))throw new AssertionError("Expected native image not mapped");
            System.out.println("NATIVE_IMAGE "+sha(Files.readAllBytes(image))+" "+image);
        }
    }
}
