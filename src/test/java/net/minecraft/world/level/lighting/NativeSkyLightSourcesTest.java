package net.minecraft.world.level.lighting;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeSkyLightSourcesTest {
    @BeforeAll static void bootstrap(){SharedConstants.tryDetectVersion();Bootstrap.bootStrap();}
    private static void compare(ChunkAccess chunk,long seed) {
        var a=new ChunkSkyLightSources(chunk);var b=new ChunkSkyLightSources(chunk);
        SkyLightSourcesFixtures.randomize(a,seed);SkyLightSourcesFixtures.randomize(b,seed);
        JavaSkyLightSources.fill(a,chunk);
        assertTrue(NativeSkyLightSources.fill(chunk,chunk.getMinY()-1,SkyLightSourcesFixtures.storage(b)),"Must exercise Rust");
        assertArrayEquals(SkyLightSourcesFixtures.storage(a).getRaw(),SkyLightSourcesFixtures.storage(b).getRaw());
        for(int z=0;z<16;z++)for(int x=0;x<16;x++)assertEquals(a.getLowestSourceY(x,z),b.getLowestSourceY(x,z));
        assertEquals(a.getHighestLowestSourceY(),b.getHighestLowestSourceY());
        b.fillFrom(chunk);assertArrayEquals(SkyLightSourcesFixtures.storage(a).getRaw(),SkyLightSourcesFixtures.storage(b).getRaw());
    }
    @Test void exactOcclusionCatalogForEveryShapePair() throws Throwable {
        // Rust derives the catalog from its block registry; compare it with Java's shapes.
        var tablesCall=net.minecraft.util.NativeLibraryLoader.downcallHandle("mattmc_rust","mattmc_skylight_sources_tables",
            java.lang.foreign.FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.JAVA_INT));
        assertTrue(net.minecraft.world.level.block.NativeBlockRegistry.ready());
        int states=Block.BLOCK_STATE_REGISTRY.size();
        int count=(int)tablesCall.invokeExact(MemorySegment.NULL,0,MemorySegment.NULL,0);
        int[] descriptors=new int[states];MemorySegment edges;
        try(var arena=java.lang.foreign.Arena.ofConfined()){
            var d=arena.allocate(states*4L,4);var e=arena.allocate(Math.max(1,(long)count*count));
            assertEquals(count,(int)tablesCall.invokeExact(d,states,e,count*count));
            MemorySegment.copy(d,ValueLayout.JAVA_INT,0,descriptors,0,states);
            edges=java.lang.foreign.Arena.ofAuto().allocate(e.byteSize());edges.copyFrom(e);
        }
        assertTrue(count>0);
        var ups=new IdentityHashMap<VoxelShape,Integer>();var downs=new IdentityHashMap<VoxelShape,Integer>();
        for(int id=0;id<descriptors.length;id++) {
            var state=Block.BLOCK_STATE_REGISTRY.byId(id);assertEquals(BlockState.class,state.getClass());
            assertEquals(state.getLightBlock()!=0,(descriptors[id]&1)!=0);
            ups.put(LightEngine.getOcclusionShape(state,Direction.UP),(descriptors[id]>>>1)&32767);
            downs.put(LightEngine.getOcclusionShape(state,Direction.DOWN),descriptors[id]>>>16);
        }
        long pairs=0;
        for(var down:downs.entrySet())for(var up:ups.entrySet()) {
            assertEquals(Shapes.faceShapeOccludes(down.getKey(),up.getKey()),edges.get(ValueLayout.JAVA_BYTE,(long)down.getValue()*count+up.getValue())!=0);pairs++;
        }
        System.out.println("SKYLIGHT_CATALOG_PARITY states="+descriptors.length+" compact_faces="+count+" original_shape_pairs="+pairs);
    }
    @Test void seededPatternsAndDimensions() {
        int cases=0;
        for(var pattern:SkyLightSourcesFixtures.PATTERNS)for(int seed=0;seed<32;seed++) {
            int minY=new int[]{-64,0,-2032,2032}[seed%4],height=new int[]{16,32,128,384}[seed%4];
            compare(SkyLightSourcesFixtures.chunk(pattern,minY,height,seed),seed);cases++;
        }
        System.out.println("SKYLIGHT_PARITY fixtures="+cases+" columns="+(cases*256));
    }
    @Test void everyRegisteredStateAndVerticalPair() {
        int count=Block.BLOCK_STATE_REGISTRY.size(),cases=0;
        for(int start=0;start<count;start+=256) {
            var chunk=SkyLightSourcesFixtures.chunk("empty",-64,32,start);
            var random=new Random(start);
            for(int c=0;c<256;c++) {
                var state=Block.BLOCK_STATE_REGISTRY.byId((start+c)%count);
                chunk.getSection(1).setBlockState(c%16,0,c/16,state);
                chunk.getSection(0).setBlockState(c%16,15,c/16,Block.BLOCK_STATE_REGISTRY.byId(random.nextInt(count)));
            }
            compare(chunk,start);cases++;
        }
        System.out.println("SKYLIGHT_ALL_STATES_PARITY fixtures="+cases+" registry_states="+count);
    }
    @Test void savedTerrainAndReprimingChanges() {
        int cases=0;
        for(var record:SkyLightSourcesFixtures.records()) {
            var chunk=SkyLightSourcesFixtures.recorded(record);
            for(int step=0;step<4;step++) {
                compare(chunk,step);cases++;
                chunk.getSection(chunk.getSectionsCount()-1).setBlockState(step,15,step,step%2==0?Blocks.STONE.defaultBlockState():Blocks.GLASS.defaultBlockState());
            }
        }
        System.out.println("SKYLIGHT_SAVED_PARITY fixtures="+cases+" saved_chunks=16");
    }
    @Test void sentinelAirGapsAndSectionEdges() {
        var chunk=SkyLightSourcesFixtures.chunk("glass",-64,64,0);
        for(int s=0;s<4;s++) {
            for(int c=0;c<256;c++)chunk.getSection(s).setBlockState(c%16,(c+s)%16,c/16,Blocks.OAK_SLAB.defaultBlockState());
            compare(chunk,s);
        }
        // Empty section shortcuts intentionally bypass the upper/lower edge.
        var empty=SkyLightSourcesFixtures.chunk("empty",-64,64,0);
        var sections=chunk.getSections().clone();sections[2]=empty.getSection(2);
        compare(SkyLightSourcesFixtures.withSections(sections,-64,64),19);
        var a=new ChunkSkyLightSources(empty);a.fillFrom(empty);
        for(int z=0;z<16;z++)for(int x=0;x<16;x++)assertEquals(Integer.MIN_VALUE,a.getLowestSourceY(x,z));
        assertEquals(Integer.MIN_VALUE,a.getHighestLowestSourceY());
    }
    @Test void corruptedIdsAndCustomReadersKeepOriginalBehavior() throws Exception {
        var chunk=SkyLightSourcesFixtures.chunk("solid",-64,32,1);
        var field=PalettedContainer.class.getDeclaredField("data");field.setAccessible(true);
        var data=(PalettedContainer.Data<?>)field.get(chunk.getSection(1).getStates());
        var words=data.storage().getRaw();words[words.length-1]|=15L<<60;
        var a=new ChunkSkyLightSources(chunk);var b=new ChunkSkyLightSources(chunk);
        var expected=assertThrows(RuntimeException.class,()->JavaSkyLightSources.fill(a,chunk));
        var actual=assertThrows(RuntimeException.class,()->b.fillFrom(chunk));
        assertEquals(expected.getClass(),actual.getClass());assertEquals(expected.getMessage(),actual.getMessage());
        assertArrayEquals(SkyLightSourcesFixtures.storage(a).getRaw(),SkyLightSourcesFixtures.storage(b).getRaw());
        var source=SkyLightSourcesFixtures.chunk("glass",-64,32,1);var seen=new ArrayList<Integer>();
        var custom=new ProtoChunk(source.getPos(),UpgradeData.EMPTY,source.getSections(),new net.minecraft.world.ticks.ProtoChunkTicks<>(),new net.minecraft.world.ticks.ProtoChunkTicks<>(),source,null,null) {
            @Override public LevelChunkSection getSection(int i){seen.add(i);return super.getSection(i);}
        };
        var c=new ChunkSkyLightSources(custom);var d=new ChunkSkyLightSources(custom);
        assertFalse(NativeSkyLightSources.fill(custom,-65,SkyLightSourcesFixtures.storage(d)));assertTrue(seen.isEmpty());
        JavaSkyLightSources.fill(c,custom);var trace=List.copyOf(seen);seen.clear();d.fillFrom(custom);assertEquals(trace,seen);
        assertArrayEquals(SkyLightSourcesFixtures.storage(c).getRaw(),SkyLightSourcesFixtures.storage(d).getRaw());
        assertFalse(NativeSkyLightSources.fill(source,-64,SkyLightSourcesFixtures.storage(d)));
    }
    @Test void incrementalUpdatesAfterNativeFillStayEquivalent() {
        var chunk=SkyLightSourcesFixtures.chunk("shapes",-64,64,77);
        var a=new ChunkSkyLightSources(chunk);var b=new ChunkSkyLightSources(chunk);
        JavaSkyLightSources.fill(a,chunk);b.fillFrom(chunk);
        var random=new Random(1977);
        BlockState[] values={Blocks.AIR.defaultBlockState(),Blocks.STONE.defaultBlockState(),Blocks.OAK_SLAB.defaultBlockState(),Blocks.GLASS.defaultBlockState(),Blocks.WATER.defaultBlockState()};
        for(int step=0;step<500;step++) {
            int x=random.nextInt(16),z=random.nextInt(16),y=random.nextInt(64)-64;
            chunk.getSection((y+64)/16).setBlockState(x,y&15,z,values[random.nextInt(values.length)]);
            assertEquals(a.update(chunk,x,y,z),b.update(chunk,x,y,z));
            assertArrayEquals(SkyLightSourcesFixtures.storage(a).getRaw(),SkyLightSourcesFixtures.storage(b).getRaw());
            if(step%50==0) { JavaSkyLightSources.fill(a,chunk);b.fillFrom(chunk);assertArrayEquals(SkyLightSourcesFixtures.storage(a).getRaw(),SkyLightSourcesFixtures.storage(b).getRaw()); }
        }
    }
    @Test void levelChunksAndCustomSections() {
        String previous=System.getProperty("net.bytebuddy.experimental");System.setProperty("net.bytebuddy.experimental","true");
        try {
            var level=org.mockito.Mockito.mock(net.minecraft.world.level.Level.class);
            org.mockito.Mockito.when(level.getMinY()).thenReturn(-64);org.mockito.Mockito.when(level.getHeight()).thenReturn(128);org.mockito.Mockito.when(level.getSectionsCount()).thenReturn(8);
            var source=SkyLightSourcesFixtures.chunk("shapes",-64,128,193);
            var chunk=new LevelChunk(level,source.getPos(),UpgradeData.EMPTY,new net.minecraft.world.ticks.LevelChunkTicks<>(),new net.minecraft.world.ticks.LevelChunkTicks<>(),0,source.getSections(),null,null);
            compare(chunk,77);org.mockito.Mockito.when(level.isDebug()).thenReturn(true);compare(chunk,79);
            // fillFrom reads sections directly, including in debug worlds.
            var seen=new ArrayList<Integer>();var sections=source.getSections().clone();
            sections[sections.length-1]=new LevelChunkSection(source.getSection(sections.length-1).getStates(),null) {
                @Override public BlockState getBlockState(int x,int y,int z){seen.add(x+y*256+z*16);return super.getBlockState(x,y,z);}
            };
            var custom=SkyLightSourcesFixtures.withSections(sections,-64,128);
            var a=new ChunkSkyLightSources(custom);var b=new ChunkSkyLightSources(custom);
            assertFalse(NativeSkyLightSources.fill(custom,-65,SkyLightSourcesFixtures.storage(b)));assertTrue(seen.isEmpty());
            JavaSkyLightSources.fill(a,custom);var expected=List.copyOf(seen);seen.clear();b.fillFrom(custom);assertEquals(expected,seen);
            assertArrayEquals(SkyLightSourcesFixtures.storage(a).getRaw(),SkyLightSourcesFixtures.storage(b).getRaw());
        }finally{if(previous==null)System.clearProperty("net.bytebuddy.experimental");else System.setProperty("net.bytebuddy.experimental",previous);}
    }
    @Test void criticalEmptyClearDuringGc() throws Exception {
        var source=SkyLightSourcesFixtures.chunk("empty",-64,384,73);
        var running=new java.util.concurrent.atomic.AtomicBoolean(true);
        var collector=new Thread(()->{while(running.get()){System.gc();Thread.yield();}},"skylight-test-gc");collector.setDaemon(true);collector.start();
        try { for(int i=0;i<200;i++)compare(source,i); }
        finally { running.set(false);collector.join(10000); }
        assertFalse(collector.isAlive());
    }
    @Test void parallelReadersAndMaximumHeight() throws Exception {
        compare(SkyLightSourcesFixtures.chunk("glass",-2048,4096,1),3);
        var chunk=SkyLightSourcesFixtures.chunk("global",-64,128,19);
        try(var executor=Executors.newFixedThreadPool(4)) {
            var futures=new ArrayList<Future<?>>();
            for(int i=0;i<4;i++)futures.add(executor.submit(()->{for(int j=0;j<40;j++)compare(chunk,j);}));
            for(var future:futures)future.get();
        }
    }
}
