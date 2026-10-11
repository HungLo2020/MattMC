package net.minecraft.client.multiplayer;

import java.lang.reflect.Field;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class NativeClientChunkRangeTest {
    static class World extends ClientLevel {
        World() { super(null,null,null,null,0,0,null,false,0L,0); }
        @Override public void unload(LevelChunk chunk) {}
    }
    static class CustomChunk extends LevelChunk {
        ChunkPos position;
        CustomChunk() { super(null,null); }
        @Override public ChunkPos getPos() { return position; }
    }
    static Object allocate(Class<?> type) throws Exception {
        Class<?> unsafe=Class.forName("sun.misc.Unsafe"); Field f=unsafe.getDeclaredField("theUnsafe"); f.setAccessible(true);
        return unsafe.getMethod("allocateInstance",Class.class).invoke(f.get(null),type);
    }
    static void field(Object target,Class<?> type,String name,Object value) throws Exception {
        Field f=type.getDeclaredField(name);f.setAccessible(true);f.set(target,value);
    }
    static ClientChunkCache cache(int radius) throws Exception {
        var cache=(ClientChunkCache)allocate(ClientChunkCache.class);
        field(cache,ClientChunkCache.class,"level",allocate(World.class));
        cache.storage=cache.new Storage(radius);return cache;
    }
    static LevelChunk chunk(int x,int z) throws Exception {
        var chunk=(LevelChunk)allocate(LevelChunk.class);
        field(chunk,ChunkAccess.class,"chunkPos",new ChunkPos(x,z));
        field(chunk,ChunkAccess.class,"sections",new LevelChunkSection[0]);
        return chunk;
    }
    static void install(ClientChunkCache cache,LevelChunk chunk) {
        var pos=chunk.getPos();cache.storage.replace(cache.storage.getIndex(pos.x,pos.z),chunk);
    }
    static List<ChunkPos> scan(ClientChunkCache cache,int x,int z,int radius) {
        var result=new ArrayList<ChunkPos>();
        cache.forEachLoadedChunkInRange(x,z,radius,c->result.add(c.getPos()));return result;
    }
    static List<ChunkPos> reference(ClientChunkCache cache,int x,int z,int radius) {
        var result=new ArrayList<ChunkPos>();
        for(int cz=z-radius;cz<=z+radius;cz++)for(int cx=x-radius;cx<=x+radius;cx++) {
            var c=cache.getChunk(cx,cz,ChunkStatus.FULL,false);if(c!=null)result.add(c.getPos());
        }
        return result;
    }
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion();Bootstrap.bootStrap(); }
    @Test void stockSparseDenseNegativeAndStaleSlotsPreserveCompleteRowOrder() throws Exception {
        var cache=cache(9);var random=new Random(0x43524731);
        for(int step=0;step<200;step++) {
            int x=random.nextInt(45)-22,z=random.nextInt(45)-22;
            install(cache,chunk(x,z));
            if(step%13==0)cache.updateViewCenter(x,z);
            assertEquals(reference(cache,x,z,11),scan(cache,x,z,11),"step"+step);
        }
        cache=cache(9);
        for(int z=-9;z<=9;z++)for(int x=-9;x<=9;x++)install(cache,chunk(x,z));
        assertEquals(361,scan(cache,0,0,9).size()); // More than five native pages.
        assertEquals(reference(cache,0,0,9),scan(cache,0,0,9));
    }
    @Test void callbackReplacementDropCenterAndRadiusChangesInvalidateUnconsumedPages() throws Exception {
        var cache=cache(3);
        install(cache,chunk(-1,0));install(cache,chunk(0,0));install(cache,chunk(1,0));
        var seen=new ArrayList<ChunkPos>();
        cache.forEachLoadedChunkInRange(0,0,3,c->{
            seen.add(c.getPos());
            if(c.getPos().x==-1)try {
                install(cache,chunk(2,0));cache.drop(new ChunkPos(0,0));
                cache.updateViewRadius(3);
            }catch(Exception failure){throw new RuntimeException(failure);}
        });
        assertEquals(List.of(new ChunkPos(-1,0),new ChunkPos(1,0),new ChunkPos(2,0)),seen);
        seen.clear();
        cache.forEachLoadedChunkInRange(0,0,3,c->{seen.add(c.getPos());cache.updateViewCenter(100,100);});
        assertEquals(List.of(new ChunkPos(-1,0)),seen);
    }
    @Test void nestedReadersAndThrowingCallbacksKeepIndependentPagesAndReleaseScratch() throws Exception {
        var cache=cache(3);install(cache,chunk(0,0));install(cache,chunk(1,0));
        var seen=new ArrayList<ChunkPos>();
        cache.forEachLoadedChunkInRange(0,0,3,c->{assertEquals(2,scan(cache,0,0,3).size());seen.add(c.getPos());});
        assertEquals(reference(cache,0,0,3),seen);
        var stop=new IllegalStateException("callback");
        assertSame(stop,assertThrows(IllegalStateException.class,()->cache.forEachLoadedChunkInRange(0,0,3,c->{throw stop;})));
        assertEquals(seen,scan(cache,0,0,3));
    }
    @Test void customBindingsKeepLiveGettersAndDoNotPoisonFutureStockAdmission() throws Exception {
        var cache=cache(3);install(cache,chunk(0,0));
        var custom=(CustomChunk)allocate(CustomChunk.class);custom.position=new ChunkPos(1,0);
        field(custom,ChunkAccess.class,"sections",new LevelChunkSection[0]);install(cache,custom);
        assertEquals(reference(cache,0,0,3),scan(cache,0,0,3));
        custom.position=new ChunkPos(2,0); // Physical slot must still match the new coordinate.
        assertEquals(List.of(new ChunkPos(0,0)),scan(cache,0,0,3));
        cache.storage.replace(cache.storage.getIndex(1,0),null);
        install(cache,chunk(2,0));assertEquals(reference(cache,0,0,3),scan(cache,0,0,3));
    }
}
