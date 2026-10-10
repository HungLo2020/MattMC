package net.minecraft.world.level.chunk;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.lang.reflect.Field;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NativeClientResidencyTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion();Bootstrap.bootStrap(); }
    private static LevelChunk chunk(int count) throws Exception {
        var chunk=mock(LevelChunk.class,CALLS_REAL_METHODS);
        var level=mock(ClientLevel.class);doReturn(level).when(chunk).getLevel();
        Field accessor=ChunkAccess.class.getDeclaredField("levelHeightAccessor");accessor.setAccessible(true);accessor.set(chunk,level);
        doReturn(0).when(chunk).getMinY();doReturn(count*16).when(chunk).getHeight();doReturn(new ChunkPos(0,0)).when(chunk).getPos();
        var sections=new LevelChunkSection[count];
        for(int i=0;i<count;i++)sections[i]=new LevelChunkSection(new PalettedContainer<>(Blocks.AIR.defaultBlockState(),Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY)),null);
        Field field=ChunkAccess.class.getDeclaredField("sections");field.setAccessible(true);field.set(chunk,sections);return chunk;
    }
    private static long install(NativeClientResidency world,LevelChunk chunk) {
        long id=world.publish(24,true);world.admitted();world.position(0,0);world.addEmpty(chunk,24,id);return id;
    }
    @Test void ordinaryBulkRefreshReadsFutureCounterAfterCallbackAndKeepsOldSnapshotIndependent() throws Exception {
        var chunk=chunk(2);var world=NativeClientResidency.create(3);assertNotNull(world);
        long id=install(world,chunk);var previous=world.snapshot();assertEquals(2,previous.size());
        var inputs=chunk.nativeDhSections();assertNotNull(inputs);
        chunk.getSection(0).setBlockState(0,0,0,Blocks.STONE.defaultBlockState());
        chunk.getSection(1).setBlockState(0,0,0,Blocks.STONE.defaultBlockState());
        var events=new ArrayList<Long>();
        world.refresh(chunk,24,id,key->{events.add(key);assertFalse(Thread.holdsLock(chunk));
            assertArrayEquals(new long[]{1},world.snapshot().toLongArray());
            chunk.getSection(1).setBlockState(0,0,0,Blocks.AIR.defaultBlockState());});
        assertEquals(List.of(0L),events);assertArrayEquals(new long[]{1},world.snapshot().toLongArray());
        assertEquals(2,previous.size());assertSame(inputs,chunk.nativeDhSections());
        assertEquals(1,world.count());world.dropped(24);world.position(0,0);world.dropEmpty(chunk);
        assertEquals(0,world.count());assertEquals(0,world.fingerprint());assertTrue(world.snapshot().isEmpty());assertEquals(2,previous.size());
    }
    @Test void callbackArrayReplacementAndPublicSetEscapePreserveRemainingOriginalRows() throws Exception {
        var chunk=chunk(2);var world=NativeClientResidency.create(3);assertNotNull(world);long id=install(world,chunk);
        for(var section:chunk.getSectionsForRead())section.setBlockState(0,0,0,Blocks.STONE.defaultBlockState());
        int[] reads={0};var custom=new LevelChunkSection(new PalettedContainer<>(Blocks.AIR.defaultBlockState(),Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY)),null) {
            @Override public boolean hasOnlyAir() {reads[0]++;return true;}
        };
        var events=new ArrayList<Long>();
        world.refresh(chunk,24,id,key->{events.add(key);chunk.getSections()[1]=custom;
            var escaped=world.emptySections();assertSame(escaped,world.emptySections());escaped.remove(1);escaped.add(17);});
        assertEquals(List.of(0L),events);assertEquals(1,reads[0]);
        assertEquals(new LongOpenHashSet(new long[]{1,17}),world.emptySections());
        world.emptySections().remove(17);assertFalse(world.empty(17,false));
    }
    @Test void throwingCallbackLeavesFutureCounterAndMembershipUntouched() throws Exception {
        var chunk=chunk(2);var world=NativeClientResidency.create(3);assertNotNull(world);long id=install(world,chunk);
        for(var section:chunk.getSectionsForRead())section.setBlockState(0,0,0,Blocks.STONE.defaultBlockState());
        var stop=new IllegalStateException("callback");
        assertSame(stop,assertThrows(IllegalStateException.class,()->world.refresh(chunk,24,id,key->{throw stop;})));
        assertArrayEquals(new long[]{1},world.snapshot().toLongArray());
        var events=new ArrayList<Long>();world.refresh(chunk,24,id,events::add);assertEquals(List.of(1L),events);assertTrue(world.snapshot().isEmpty());
    }
    private static class CustomWorld extends ClientLevel {
        CustomWorld() { super(null,null,null,null,0,0,null,false,0L,0); }
    }
    @Test void customWorldHeightGettersKeepOriginalPerRowCallsAndKeys() throws Exception {
        var chunk=chunk(2);var level=mock(CustomWorld.class);int[] reads={0};
        when(level.getMinY()).thenAnswer(invocation->reads[0]++==0?0:16);
        when(level.getHeight()).thenReturn(32);doReturn(level).when(chunk).getLevel();
        Field accessor=ChunkAccess.class.getDeclaredField("levelHeightAccessor");accessor.setAccessible(true);accessor.set(chunk,level);
        doCallRealMethod().when(chunk).getMinY();doCallRealMethod().when(chunk).getHeight();
        var world=NativeClientResidency.create(3);assertNotNull(world);install(world,chunk);
        assertEquals(2,reads[0]);assertEquals(new LongOpenHashSet(new long[]{0,2}),world.snapshot());
        reads[0]=0;world.dropEmpty(chunk);assertEquals(2,reads[0]);assertTrue(world.snapshot().isEmpty());
    }
    @Test void publicEscapeKeepsFastutilTableOrderAcrossGrowthCollisionsDeletionAndShrink() {
        var world=NativeClientResidency.create(3);assertNotNull(world);var reference=new LongOpenHashSet();
        for(long i=0;i<4000;i++){long key=i*0x9e3779b97f4a7c15L;assertEquals(reference.add(key),world.empty(key,true));}
        var before=world.snapshot();assertArrayEquals(reference.toLongArray(),before.toLongArray());
        var escaped=world.emptySections();assertSame(escaped,world.emptySections());assertArrayEquals(reference.toLongArray(),escaped.toLongArray());
        for(long i=3999;i>=0;i--){long key=i*0x9e3779b97f4a7c15L;assertEquals(reference.remove(key),world.empty(key,false));
            if(i%79==0)assertArrayEquals(reference.toLongArray(),escaped.toLongArray());}
        for(long i=-300;i<0;i++){reference.add(i);escaped.add(i);}
        assertArrayEquals(reference.toLongArray(),world.snapshot().toLongArray());assertEquals(4000,before.size());
        world.center(Integer.MIN_VALUE,Integer.MAX_VALUE);assertEquals(Integer.MIN_VALUE,world.centerX());assertEquals(Integer.MAX_VALUE,world.centerZ());
        long id=world.publish(0,true);assertEquals(id,world.identity(0));world.admitted();assertEquals(1,world.count());
    }
}
