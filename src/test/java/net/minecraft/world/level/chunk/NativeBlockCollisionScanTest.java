package net.minecraft.world.level.chunk;

import java.lang.reflect.Field;
import java.util.ArrayList;
import net.minecraft.SharedConstants;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Cursor3D;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.BlockCollisions;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NativeCollisionGeometry;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NativeBlockCollisionScanTest {
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); assertTrue(NativeCollisionGeometry.ready());
    }
    private record Fixture(LevelChunk chunk, LevelChunkSection section, CollisionGetter world) {}
    private static void set(Class<?> owner, Object target, String name, Object value) throws Exception {
        Field f=owner.getDeclaredField(name);f.setAccessible(true);f.set(target,value);
    }
    private static Fixture fixture() throws Exception {
        var level=mock(ClientLevel.class);var chunk=mock(LevelChunk.class,CALLS_REAL_METHODS);
        doReturn(level).when(chunk).getLevel();set(LevelChunk.class,chunk,"level",level);
        set(ChunkAccess.class,chunk,"levelHeightAccessor",level);
        doReturn(0).when(chunk).getMinY();doReturn(16).when(chunk).getHeight();
        var section=new LevelChunkSection(new PalettedContainer<>(Blocks.AIR.defaultBlockState(),Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY)),null);
        set(ChunkAccess.class,chunk,"sections",new LevelChunkSection[]{section});
        var world=mock(CollisionGetter.class);
        when(world.getChunkForCollisions(anyInt(),anyInt())).thenReturn(chunk);
        return new Fixture(chunk,section,world);
    }
    private static Cursor3D cursor() {var c=new Cursor3D(0,0,0,4,2,2);assertTrue(c.advance());return c;}
    private static int prefix(LevelChunk chunk, Cursor3D cursor, AABB box, BlockPos.MutableBlockPos position) {
        return (int)(NativeBlockCollisionScan.next(chunk,cursor,box,position) >>> 32);
    }
    @Test void emptyPrefixStopsAtTheFirstPossibleHitAndSeesPrimaryMutations() throws Exception {
        var f=fixture();var box=new AABB(2.1,1.1,1.1,2.9,1.9,1.9);
        assertEquals(45,prefix(f.chunk,cursor(),box,new BlockPos.MutableBlockPos()));
        f.section.setBlockState(2,1,1,Blocks.STONE.defaultBlockState());
        assertEquals(22,prefix(f.chunk,cursor(),box,new BlockPos.MutableBlockPos()));
        assertEquals(45,prefix(f.chunk,cursor(),new AABB(8,8,8,9,9,9),new BlockPos.MutableBlockPos()));
        f.section.setBlockState(2,1,1,Blocks.WATER.defaultBlockState());
        assertEquals(22,prefix(f.chunk,cursor(),box,new BlockPos.MutableBlockPos()));
    }
    @Test void callbackMutationIsObservedBeforeTheNextIteratorResult() throws Exception {
        var f=fixture();f.section.setBlockState(1,1,1,Blocks.STONE.defaultBlockState());
        var iterator=new BlockCollisions<BlockPos>(f.world,CollisionContext.empty(),new AABB(1.1,1.1,1.1,2.9,1.9,1.9),false,(p,s)->{
            f.section.setBlockState(2,1,1,Blocks.STONE.defaultBlockState());return p.immutable();
        });
        var results=new ArrayList<BlockPos>();while(iterator.hasNext())results.add(iterator.next());
        assertEquals(java.util.List.of(new BlockPos(1,1,1),new BlockPos(2,1,1)),results);
    }
    @Test void exhaustedScanPreservesTheEscapedMutableResultPosition() throws Exception {
        var f=fixture();f.section.setBlockState(1,1,1,Blocks.STONE.defaultBlockState());
        var iterator=new BlockCollisions<BlockPos>(f.world,CollisionContext.empty(),new AABB(1.1,1.1,1.1,2.9,1.9,1.9),false,(p,s)->p);
        BlockPos held=iterator.next();assertEquals(new BlockPos(1,1,1),held);
        assertFalse(iterator.hasNext());
        assertEquals(new BlockPos(2,2,2),held); // Last non-corner, even though no collision was yielded.
    }
    @Test void customContextStillReceivesAirCells() throws Exception {
        var f=fixture();var context=mock(CollisionContext.class);
        when(context.getCollisionShape(any(),any(),any())).thenReturn(Shapes.block());
        var iterator=new BlockCollisions<BlockPos>(f.world,context,new AABB(1.1,1.1,1.1,2.9,1.9,1.9),false,(p,s)->p.immutable());
        var results=new ArrayList<BlockPos>();while(iterator.hasNext())results.add(iterator.next());
        assertEquals(java.util.List.of(new BlockPos(1,1,1),new BlockPos(2,1,1)),results);
        verify(context,times(2)).getCollisionShape(any(),any(),any());
    }
    @Test void replacementAndEscapedArrayAreReadmittedWithoutStaleOwners() throws Exception {
        var f=fixture();var box=new AABB(2.1,1.1,1.1,2.9,1.9,1.9);
        assertEquals(45,prefix(f.chunk,cursor(),box,new BlockPos.MutableBlockPos()));
        var replacement=new LevelChunkSection(new PalettedContainer<>(Blocks.STONE.defaultBlockState(),Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY)),null);
        replacement.recalcBlockCounts();f.chunk.getSections()[0]=replacement;
        assertEquals(22,prefix(f.chunk,cursor(),box,new BlockPos.MutableBlockPos()));
    }
    @Test void retiredPaletteRebindsAndPublishesTheNewCandidateState() throws Exception {
        var f=fixture();var box=new AABB(2.1,1.1,1.1,2.9,1.9,1.9);var position=new BlockPos.MutableBlockPos();
        f.section.setBlockState(2,1,1,Blocks.WATER.defaultBlockState());
        long before=NativeBlockCollisionScan.next(f.chunk,cursor(),box,position);
        assertEquals(Block.getId(Blocks.WATER.defaultBlockState())+1,(int)before);
        f.section.getStates().installGenerated(0,java.util.List.of(Blocks.STONE.defaultBlockState()),new long[0]);
        var next=cursor();long after=NativeBlockCollisionScan.next(f.chunk,next,box,position);
        int prefix=(int)(after>>>32);
        if ((int)after==0 && prefix>0) {
            next.skip(prefix-1);assertTrue(next.advance());
            after=NativeBlockCollisionScan.next(f.chunk,next,box,position);
            prefix+=(int)(after>>>32);
        }
        assertEquals((Block.getId(Blocks.STONE.defaultBlockState())+1)|65536,(int)after);
        assertEquals(22,prefix);
    }
    @Test void skippedLeadingCornerDoesNotPublishAnUnvisitedPosition() throws Exception {
        var f=fixture();f.section.setBlockState(1,0,0,Blocks.MOVING_PISTON.defaultBlockState());
        var position=new BlockPos.MutableBlockPos(91,92,93);
        long result=NativeBlockCollisionScan.next(f.chunk,cursor(),new AABB(0,0,0,3,3,3),position);
        assertEquals(1,result>>>32);assertEquals(Block.getId(Blocks.MOVING_PISTON.defaultBlockState())+1,(int)result);
        assertEquals(new BlockPos(91,92,93),position);
    }
    @Test void contextualShapesOnExcludedEdgesDoNotReturnToJava() throws Exception {
        var f=fixture();
        f.section.setBlockState(1,0,0,Blocks.WATER.defaultBlockState());
        f.section.setBlockState(2,0,0,Blocks.LAVA.defaultBlockState());
        f.section.setBlockState(3,0,0,Blocks.POWDER_SNOW.defaultBlockState());
        assertEquals(45,prefix(f.chunk,cursor(),new AABB(1.1,1.1,1.1,2.9,1.9,1.9),new BlockPos.MutableBlockPos()));
    }
    @Test void nonCubeStaticCollisionIsResolvedBeforeJavaShapeJoining() throws Exception {
        var f=fixture();f.section.setBlockState(2,1,1,Blocks.OAK_SLAB.defaultBlockState());
        long hit=NativeBlockCollisionScan.next(f.chunk,cursor(),new AABB(2.1,1.1,1.1,2.9,1.4,1.9),new BlockPos.MutableBlockPos());
        assertEquals((Block.getId(Blocks.OAK_SLAB.defaultBlockState())+1)|65536,(int)hit);
        assertEquals(45,prefix(f.chunk,cursor(),new AABB(2.1,1.6,1.1,2.9,1.9,1.9),new BlockPos.MutableBlockPos()));
    }
    @Test void oversizedNonfiniteAndCustomQueriesDeclineBeforeTheTraversal() {
        var box=new AABB(0,0,0,1,1,1);
        assertTrue(NativeBlockCollisionScan.supports(cursor(),box,CollisionContext.empty()));
        assertFalse(NativeBlockCollisionScan.supports(new Cursor3D(0,0,0,128,128,128),box,CollisionContext.empty()));
        assertFalse(NativeBlockCollisionScan.supports(cursor(),new AABB(0,0,0,Double.POSITIVE_INFINITY,1,1),CollisionContext.empty()));
        assertFalse(NativeBlockCollisionScan.supports(cursor(),box,mock(CollisionContext.class)));
    }
    @Test void cursorBulkSkipPreservesNextCoordinatesAndBounds() {
        var c=cursor();c.skip(21);assertTrue(c.advance());
        assertEquals(2,c.nextX());assertEquals(1,c.nextY());assertEquals(1,c.nextZ());
        assertThrows(IllegalArgumentException.class,()->c.skip(100));
        c.skip(22);assertFalse(c.advance());
    }
}
