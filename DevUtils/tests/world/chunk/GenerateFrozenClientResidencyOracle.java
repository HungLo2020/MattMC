import com.google.gson.JsonParser;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicReferenceArray;
import net.minecraft.client.multiplayer.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.*;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

/** Executes untouched Frozen Storage; fixture callbacks supply CPU facts only. */
public class GenerateFrozenClientResidencyOracle {
    static final List<long[]> events = new ArrayList<>();
    static boolean failCallback;
    static class Stop extends RuntimeException {}
    static class World extends ClientLevel {
        World() { super(null,null,null,null,0,0,null,false,0L,0); }
        @Override public void unload(LevelChunk chunk) { event(0, ((Chunk)chunk).id); }
        @Override public void onSectionBecomingNonEmpty(long key) { event(1, key); }
    }
    static class Section extends LevelChunkSection {
        boolean empty;
        Section() { super((PalettedContainer)null, null); }
        @Override public boolean hasOnlyAir() { return empty; }
    }
    static class Chunk extends LevelChunk {
        int id, min;
        ChunkPos pos;
        Section[] sections;
        Chunk() { super(null,null); }
        @Override public ChunkPos getPos() { return pos; }
        @Override public LevelChunkSection[] getSections() { return sections; }
        @Override public int getSectionYFromSectionIndex(int i) { return min + i; }
    }
    static void event(int type, long value) { events.add(new long[]{type,value}); if(failCallback) throw new Stop(); }
    static Field field(Class<?> c,String n) throws Exception { var f=c.getDeclaredField(n);f.setAccessible(true);return f; }
    static Method method(Class<?> c,String n,Class<?>... p) throws Exception { var m=c.getDeclaredMethod(n,p);m.setAccessible(true);return m; }
    static Object allocate(Class<?> c) throws Exception {
        Class<?> u=Class.forName("sun.misc.Unsafe");var f=field(u,"theUnsafe");return u.getMethod("allocateInstance",Class.class).invoke(f.get(null),c);
    }
    static String hash(Class<?> c) throws Exception {
        try(var in=c.getResourceAsStream("/"+c.getName().replace('.','/')+".class")) {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes()));
        }
    }
    public static void main(String[] args) throws Exception {
        var pins=JsonParser.parseString(Files.readString(Path.of(args[1]))).getAsJsonObject();
        for(var entry:pins.entrySet()) if(!hash(Class.forName(entry.getKey())).equals(entry.getValue().getAsString())) throw new IllegalStateException("Not pinned Frozen "+entry.getKey());
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
        Class<?> type=Class.forName("net.minecraft.client.multiplayer.ClientChunkCache$Storage");
        var constructor=type.getDeclaredConstructors()[0];constructor.setAccessible(true);
        var cache=(ClientChunkCache)allocate(ClientChunkCache.class);field(ClientChunkCache.class,"level").set(cache,allocate(World.class));
        Object storage=constructor.newInstance(cache,3); field(ClientChunkCache.class,"storage").set(cache,storage);
        Field centerX=field(type,"viewCenterX"), centerZ=field(type,"viewCenterZ");
        Field count=field(type,"chunkCount"), slots=field(type,"chunks"), empty=field(type,"loadedEmptySections");
        Method index=method(type,"getIndex",int.class,int.class), range=method(type,"inRange",int.class,int.class);
        Method replace=method(type,"replace",int.class,LevelChunk.class), drop=method(type,"drop",int.class,LevelChunk.class);
        Method refresh=method(type,"refreshEmptySections",LevelChunk.class), changed=method(type,"onSectionEmptinessChanged",int.class,int.class,int.class,boolean.class);
        var random=new Random(0x43525331);Chunk[] chunks=new Chunk[40];
        for(int i=0;i<chunks.length;i++) {
            var c=(Chunk)allocate(Chunk.class);c.id=i+1;c.pos=new ChunkPos(i%9-4,i/9-2);c.min=i%3==0?-4:0;
            c.sections=new Section[24];for(int s=0;s<24;s++){c.sections[s]=(Section)allocate(Section.class);c.sections[s].empty=random.nextBoolean();}chunks[i]=c;
        }
        int steps=600;
        try(var out=new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(Path.of(args[0]))))) {
            out.writeInt(0x43525331);out.writeUTF(hash(type));out.writeUTF(hash(LongOpenHashSet.class));
            out.writeUTF("Actual Frozen client Storage with immutable chunk coordinates and mutable section-empty facts; strict unload/nonempty callback traces, including exceptions. Supplemental CPU policy proof only.");
            out.writeInt(chunks.length);for(var c:chunks){out.writeInt(c.id);out.writeInt(c.pos.x);out.writeInt(c.pos.z);out.writeInt(c.min);out.writeInt(c.sections.length);for(var s:c.sections)out.writeBoolean(s.empty);}
            out.writeInt(steps);
            for(int step=0;step<steps;step++) {
                int op=step<8?new int[]{0,0,2,3,1,4,2,1}[step]:random.nextInt(6), id=random.nextInt(chunks.length);
                var c=chunks[id];int x=step%31==0?Integer.MIN_VALUE:random.nextInt(17)-8;
                int z=step%37==0?Integer.MAX_VALUE:random.nextInt(17)-8,y=random.nextInt(32)-8;
                long mask=random.nextLong();boolean flag=random.nextBoolean();failCallback=step%17==0;
                out.writeByte(op);out.writeInt(id);out.writeInt(x);out.writeInt(y);out.writeInt(z);out.writeLong(mask);out.writeBoolean(flag);out.writeBoolean(failCallback);
                events.clear();String exception="";
                try { switch(op) {
                    case 0 -> replace.invoke(storage,(int)index.invoke(storage,c.pos.x,c.pos.z),flag?c:null);
                    case 1 -> drop.invoke(storage,(int)index.invoke(storage,c.pos.x,c.pos.z),c);
                    case 2 -> {for(int s=0;s<c.sections.length;s++)c.sections[s].empty=(mask&(1L<<s))!=0;refresh.invoke(storage,c);}
                    case 3 -> changed.invoke(storage,x,y,z,flag);
                    case 4 -> {centerX.setInt(storage,x);centerZ.setInt(storage,z);}
                    case 5 -> cache.updateViewRadius(Math.floorMod(y,5));
                }} catch(InvocationTargetException e){if(!(e.getCause() instanceof Stop))throw e;exception="callback";}
                storage=field(ClientChunkCache.class,"storage").get(cache);
                out.writeUTF(exception);out.writeInt(count.getInt(storage));
                var array=(AtomicReferenceArray<LevelChunk>)slots.get(storage);out.writeInt(array.length());for(int s=0;s<array.length();s++)out.writeInt(array.get(s)==null?0:((Chunk)array.get(s)).id);
                var set=(LongOpenHashSet)empty.get(storage);out.writeInt(set.size());for(long key:set)out.writeLong(key);
                out.writeInt(events.size());for(var event:events){out.writeByte((int)event[0]);out.writeLong(event[1]);}
                out.writeBoolean((boolean)range.invoke(storage,x,z));out.writeInt((int)index.invoke(storage,x,z));
            }
        }
        System.out.println("Recorded600 actual Frozen residency operations; all slots/counts/empty iteration/callback and exception prefixes.");
    }
}
