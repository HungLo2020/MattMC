package net.minecraft.client.multiplayer;

import java.io.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.zip.GZIPInputStream;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("parity")
class NativeClientResidencyParityTest {
    private static final List<long[]> events = new ArrayList<>();
    private static boolean fail;
    private static class Stop extends RuntimeException {}
    private static class World extends ClientLevel {
        World() { super(null,null,null,null,0,0,null,false,0L,0); }
        @Override public void unload(LevelChunk chunk) { event(0, ((Chunk)chunk).id); }
        @Override public void onSectionBecomingNonEmpty(long key) { event(1,key); }
    }
    private static class Section extends LevelChunkSection {
        boolean empty;
        Section() { super((PalettedContainer)null,null); }
        @Override public boolean hasOnlyAir() { return empty; }
    }
    private static class Chunk extends LevelChunk {
        int id,min; ChunkPos pos; Section[] sections;
        Chunk() { super(null,null); }
        @Override public ChunkPos getPos() { return pos; }
        @Override public LevelChunkSection[] getSectionsForRead() { return sections; }
        @Override public int getSectionYFromSectionIndex(int index) { return min+index; }
    }
    private static void event(int type,long value) { events.add(new long[]{type,value});if(fail)throw new Stop(); }
    private static Object allocate(Class<?> type) throws Exception {
        Class<?> unsafe=Class.forName("sun.misc.Unsafe");Field f=unsafe.getDeclaredField("theUnsafe");f.setAccessible(true);
        return unsafe.getMethod("allocateInstance",Class.class).invoke(f.get(null),type);
    }
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion();Bootstrap.bootStrap(); }
    @Test void actualStorageAndOuterRadiusTransitionsMatchEveryFrozenRecordedPrefix() throws Exception {
        try(var in=new DataInputStream(new GZIPInputStream(Objects.requireNonNull(getClass().getResourceAsStream("/world/chunk/frozen-client-residency.bin.gz"))))) {
            assertEquals(0x43525331,in.readInt());
            assertEquals("b7885ea3f3e9bbe00b7088afc2fbc615eca9f397731b9d5b9b0171239c867658",in.readUTF());
            assertEquals("2080d2ab49335be229d6d37fa81ab9f2b7f5ddd5b23c3560c9c4740503f7a48a",in.readUTF());
            assertTrue(in.readUTF().contains("Actual Frozen"));
            var cache=(ClientChunkCache)allocate(ClientChunkCache.class);
            Field level=ClientChunkCache.class.getDeclaredField("level");level.setAccessible(true);level.set(cache,allocate(World.class));
            cache.storage=cache.new Storage(3);
            var chunks=new Chunk[in.readInt()];
            for(int i=0;i<chunks.length;i++) {
                var c=(Chunk)allocate(Chunk.class);chunks[i]=c;c.id=in.readInt();c.pos=new ChunkPos(in.readInt(),in.readInt());c.min=in.readInt();
                c.sections=new Section[in.readInt()];for(int row=0;row<c.sections.length;row++){c.sections[row]=(Section)allocate(Section.class);c.sections[row].empty=in.readBoolean();}
            }
            var snapshot=NativeClientResidency.class.getDeclaredMethod("snapshot");snapshot.setAccessible(true);
            int steps=in.readInt();assertEquals(600,steps);
            for(int step=0;step<steps;step++) {
                int op=in.readUnsignedByte();var chunk=chunks[in.readInt()];int x=in.readInt(),y=in.readInt(),z=in.readInt();long mask=in.readLong();
                boolean flag=in.readBoolean();fail=in.readBoolean();events.clear();String exception="";
                var storage=cache.storage;int index=storage.getIndex(chunk.pos.x,chunk.pos.z);
                try { switch(op) {
                    case 0->storage.replace(index,flag?chunk:null);
                    case 1->storage.drop(index,chunk);
                    case 2->{for(int row=0;row<chunk.sections.length;row++)chunk.sections[row].empty=(mask&(1L<<row))!=0;storage.refreshEmptySections(chunk);}
                    case 3->storage.onSectionEmptinessChanged(x,y,z,flag);
                    case 4->cache.updateViewCenter(x,z);
                    case 5->cache.updateViewRadius(Math.floorMod(y,5));
                    default->throw new AssertionError(op);
                }} catch(Stop expected) {exception="callback";}
                storage=cache.storage;assertNotNull(storage.nativeResidency);
                assertEquals(in.readUTF(),exception,"exception step"+step);
                assertEquals(in.readInt(),cache.getLoadedChunksCount(),"count step"+step);
                assertEquals(in.readInt(),storage.chunks.length());
                for(int slot=0;slot<storage.chunks.length();slot++)assertEquals(in.readInt(),storage.chunks.get(slot)==null?0:((Chunk)storage.chunks.get(slot)).id,"slot step"+step);
                var empty=(it.unimi.dsi.fastutil.longs.LongOpenHashSet)snapshot.invoke(storage.nativeResidency);
                assertEquals(in.readInt(),empty.size(),"empty size step"+step);for(long key:empty)assertEquals(in.readLong(),key,"exact iteration step"+step);
                assertEquals(in.readInt(),events.size(),"events step"+step);for(var event:events){assertEquals(in.readUnsignedByte(),event[0]);assertEquals(in.readLong(),event[1]);}
                assertEquals(in.readBoolean(),storage.inRange(x,z));assertEquals(in.readInt(),storage.getIndex(x,z));
            }
            assertEquals(-1,in.read());fail=false;
        }
    }
}
