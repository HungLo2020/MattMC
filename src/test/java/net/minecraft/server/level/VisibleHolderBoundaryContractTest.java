package net.minecraft.server.level;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.stream.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.chunk.status.*;
import org.junit.jupiter.api.*;
import it.unimi.dsi.fastutil.longs.*;
class VisibleHolderBoundaryContractTest {
    @BeforeAll static void bootstrap() { net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap(); }
    static void set(ChunkMap map,String name,Object value) throws Exception {
        try { Field f=ChunkMap.class.getDeclaredField(name); f.setAccessible(true); f.set(map,value); } catch(NoSuchFieldException absent) {}
    }
    static ChunkMap map() throws Exception {
        ChunkMap map=mock(ChunkMap.class,CALLS_REAL_METHODS);
        set(map,"level",mock(ServerLevel.class));
        try { Method init=ChunkMap.class.getDeclaredMethod("initializeVerification"); init.setAccessible(true); init.invoke(map); }
        catch(NoSuchMethodException original) {
            set(map,"updatingChunkMap",new Long2ObjectLinkedOpenHashMap<ChunkHolder>());
            set(map,"visibleChunkMap",new Long2ObjectLinkedOpenHashMap<ChunkHolder>());
            set(map,"pendingUnloads",new Long2ObjectLinkedOpenHashMap<ChunkHolder>()); set(map,"toDrop",new LongOpenHashSet());
        }
        return map;
    }
    @SuppressWarnings("unchecked") static Collection<ChunkHolder> visible(ChunkMap map) throws Exception {
        Field f=ChunkMap.class.getDeclaredField("visibleChunkMap"); f.setAccessible(true); Object value=f.get(map);
        Method values=value.getClass().getMethod("values"); values.setAccessible(true); return (Collection<ChunkHolder>)values.invoke(value);
    }
    @Test void retainedPromotedValuesAndStreamsKeepTheirOriginalHoldersAndOrder() throws Exception {
        ChunkMap map=map(); long a=ChunkPos.asLong(4,-7),b=ChunkPos.asLong(5,-7),c=ChunkPos.asLong(6,-7);
        ChunkHolder first=map.updateChunkScheduling(a,33,null,ChunkLevel.MAX_LEVEL+1);
        ChunkHolder second=map.updateChunkScheduling(b,33,null,ChunkLevel.MAX_LEVEL+1);
        assertTrue(map.promoteChunkMap()); Collection<ChunkHolder> old=visible(map);
        Stream<ChunkHolder> stream=map.allChunksWithAtLeastStatus(ChunkStatus.FULL);
        ChunkHolder third=map.updateChunkScheduling(c,33,null,ChunkLevel.MAX_LEVEL+1); assertTrue(map.promoteChunkMap());
        assertEquals(List.of(first,second),stream.toList()); assertEquals(List.of(first,second),new ArrayList<>(old));
        assertEquals(List.of(first,second,third),map.allChunksWithAtLeastStatus(ChunkStatus.FULL).toList());
        Iterator<ChunkHolder> iterator=old.iterator(); assertSame(first,iterator.next()); iterator.remove();
        assertEquals(List.of(second),new ArrayList<>(old));
        assertSame(first,map.getVisibleChunkIfPresent(a),"mutation of the retained old view cannot mutate the current promoted map");
        assertThrows(IllegalStateException.class,iterator::remove); assertSame(second,iterator.next()); assertFalse(iterator.hasNext());
        assertThrows(NoSuchElementException.class,iterator::next);
        assertFalse(map.promoteChunkMap());
    }
}
