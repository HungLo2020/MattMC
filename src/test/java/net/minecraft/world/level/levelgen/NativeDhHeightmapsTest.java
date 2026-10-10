package net.minecraft.world.level.levelgen;

import com.seibel.distanthorizons.common.wrappers.block.BlockStateWrapper;
import com.seibel.distanthorizons.common.wrappers.chunk.ChunkWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import java.io.*;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.GZIPInputStream;
import net.minecraft.SharedConstants;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("parity")
class NativeDhHeightmapsTest {
    private static ILevelWrapper level;
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        System.setProperty("net.bytebuddy.experimental", "true");
        Level registryOnly = org.mockito.Mockito.mock(Level.class);
        org.mockito.Mockito.when(registryOnly.registryAccess()).thenReturn(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        level = (ILevelWrapper)Proxy.newProxyInstance(ILevelWrapper.class.getClassLoader(), new Class<?>[]{ILevelWrapper.class},
            (proxy,method,args) -> {
                if (method.getName().equals("getWrappedMcObject")) return registryOnly;
                throw new UnsupportedOperationException(method.toString());
            });
    }
    private record Expected(int min, int max, int[] solid, int[] blocking) {}
    private static List<Expected> frozen() throws IOException {
        try (var input = new DataInputStream(new GZIPInputStream(Objects.requireNonNull(NativeDhHeightmapsTest.class.getResourceAsStream("/world/chunk/frozen-dh-heightmaps.bin.gz"))))) {
            assertEquals(0x44484831,input.readInt());
            assertEquals("68c7e9206b17b042ecc9307d02a9fd6dcd3fcf0fbfc5ba02b75951c11f4400cd",input.readUTF());
            assertEquals("71dada60ecbd485877e412bf0a2e7b8f437aa09e7f5579817e97f21c55dd69e6",input.readUTF());
            assertEquals("3430938c5556f13efcd286e6312b50312ec7e994673553022d3a70ff4881dbdc",input.readUTF());
            input.readUTF(); input.readUTF();
            for (int i=0,count=input.readInt();i<count;i++) input.skipNBytes(input.readInt()*48L);
            input.skipNBytes(input.readInt()*14L);
            var result=new ArrayList<Expected>();
            for (int i=0,count=input.readInt();i<count;i++) {
                input.readUTF();input.readLong();int minY=input.readInt(),height=input.readInt(),min=input.readInt(),max=input.readInt();
                input.skipNBytes(height*256L*4);
                int[] solid=new int[256],blocking=new int[256];for(int col=0;col<256;col++){solid[col]=input.readInt();blocking[col]=input.readInt();}
                result.add(new Expected(min,max,solid,blocking));
            }
            assertEquals(-1,input.read()); return result;
        }
    }
    @Test void everyCachedFrozenStateThroughActualJavaGeometryExportAndNativeScan() throws Exception {
        try (var input = new DataInputStream(new GZIPInputStream(Objects.requireNonNull(NativeDhHeightmapsTest.class.getResourceAsStream("/world/chunk/frozen-dh-heightmaps.bin.gz"))))) {
            assertEquals(0x44484831,input.readInt());for(int i=0;i<5;i++)input.readUTF();
            for(int i=0,count=input.readInt();i<count;i++)input.skipNBytes(input.readInt()*48L);
            int admitted=0;
            for(int i=0,count=input.readInt();i<count;i++) {
                int id=input.readInt(),shape=input.readInt();boolean air=input.readBoolean();
                input.readBoolean();input.readBoolean();input.readUnsignedByte();
                boolean solid=input.readBoolean();int opacity=input.readUnsignedByte();
                if(shape<0)continue;
                BlockState state=Block.BLOCK_STATE_REGISTRY.byId(id);
                assertNotNull(state);assertEquals(air,state.isAir(),"Frozen state identity "+id);
                var states=new PalettedContainer<>(state,Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY));
                var source=HeightmapFixtures.withSections(new LevelChunkSection[]{new LevelChunkSection(states,null)},0,16);
                var fields=NativeDhHeightmaps.build(source);
                assertNotNull(fields,"Frozen cached state "+id);
                assertEquals(solid?15:0,fields.solid(0,0),"Frozen solidity "+id);
                assertEquals(opacity!=0?15:0,fields.blocking(0,0),"Frozen light blocking "+id);
                admitted++;
            }
            assertEquals(31532,admitted);
        }
    }
    @Test void savedFrozenFieldsThroughActualNativeOwnersAndDhConsumer() throws Exception {
        var expected=frozen();assertEquals(16,expected.size());
        int index=0;
        for (var record:HeightmapFixtures.saved()) {
            var source=HeightmapFixtures.recorded(record);var fields=NativeDhHeightmaps.build(source);
            assertNotNull(fields,"Must exercise actual native path, chunk "+index);
            assertThrows(IndexOutOfBoundsException.class,()->fields.solid(0,16));
            assertThrows(IndexOutOfBoundsException.class,()->fields.blocking(16,0));
            var wrapper=new ChunkWrapper(source,level);var oracle=expected.get(index++);
            assertEquals(oracle.min,fields.minHeight());assertEquals(oracle.max,fields.maxHeight());
            assertEquals(oracle.min,wrapper.getMinNonEmptyHeight());assertEquals(oracle.max,wrapper.getMaxNonEmptyHeight());
            for(int x=0;x<16;x++)for(int z=0;z<16;z++) {
                int col=x*16+z;
                assertEquals(oracle.solid[col],fields.solid(x,z));assertEquals(oracle.blocking[col],fields.blocking(x,z));
                assertEquals(oracle.solid[col],wrapper.getSolidHeightMapValue(x,z));assertEquals(oracle.blocking[col],wrapper.getLightBlockingHeightMapValue(x,z));
            }
            assertThrows(IndexOutOfBoundsException.class,()->wrapper.getSolidHeightMapValue(-1,0));
        }
    }
    @Test void originalCallbacksAndPublicWrapperOverridesRemainCompatible() {
        var source=HeightmapFixtures.chunk("solid",-64,32,0);
        var calls=new ArrayList<Long>();
        var custom=new ProtoChunk(source.getPos(),UpgradeData.EMPTY,source.getSections(),new net.minecraft.world.ticks.ProtoChunkTicks<>(),new net.minecraft.world.ticks.ProtoChunkTicks<>(),LevelHeightAccessor.create(-64,32),null,null) {
            @Override public BlockState getBlockState(BlockPos pos) {calls.add(pos.asLong());return super.getBlockState(pos);}
        };
        assertNull(NativeDhHeightmaps.build(custom));assertTrue(calls.isEmpty());
        var wrapper=new ChunkWrapper(custom,level);assertFalse(calls.isEmpty());
        assertEquals(-33,wrapper.getSolidHeightMapValue(0,0));
        BlockState stone=Blocks.STONE.defaultBlockState();
        BlockStateWrapper previous=BlockStateWrapper.WRAPPER_BY_BLOCK_STATE.put(stone,BlockStateWrapper.AIR);
        try {
            assertNull(NativeDhHeightmaps.build(source));
            assertEquals(-64,new ChunkWrapper(source,level).getSolidHeightMapValue(0,0));
        } finally {if(previous==null)BlockStateWrapper.WRAPPER_BY_BLOCK_STATE.remove(stone);else BlockStateWrapper.WRAPPER_BY_BLOCK_STATE.put(stone,previous);}
        // Moving piston collision is contextual and must execute the old path.
        var states=new PalettedContainer<>(Blocks.MOVING_PISTON.defaultBlockState(),Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY));
        var dynamic=HeightmapFixtures.withSections(new LevelChunkSection[]{new LevelChunkSection(states,null)},0,16);
        assertNull(NativeDhHeightmaps.build(dynamic));
    }
    @Test void rebuiltFieldsDoNotMutateRetainedViewsAndMcMapsRemainAvailable() {
        var source=HeightmapFixtures.chunk("empty",-64,32,0);
        var first=NativeDhHeightmaps.build(source);assertNotNull(first);assertEquals(-64,first.solid(0,0));assertEquals(-48,first.maxHeight());
        var wrapper=new ChunkWrapper(source,level,false);
        assertEquals(source.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE).getFirstAvailable(0,0),wrapper.getSolidHeightMapValue(0,0));
        source.getSection(1).setBlockState(0,15,0,Blocks.STONE.defaultBlockState());
        var next=NativeDhHeightmaps.build(source);assertNotNull(next);assertEquals(-33,next.solid(0,0));assertEquals(-64,first.solid(0,0));
        wrapper.createDhHeightMaps();assertEquals(-33,wrapper.getSolidHeightMapValue(0,0));
        source.getSection(1).setBlockState(0,15,0,Blocks.AIR.defaultBlockState());wrapper.createDhHeightMaps();assertEquals(-64,wrapper.getSolidHeightMapValue(0,0));
    }
    @Test void parallelReadersConsumeTheSameLiveStorage() throws Exception {
        var source=HeightmapFixtures.recorded(HeightmapFixtures.saved().getFirst());
        var expected=NativeDhHeightmaps.build(source);assertNotNull(expected);
        try(var executor=Executors.newFixedThreadPool(8)) {
            var tasks=new ArrayList<Future<?>>();
            for(int thread=0;thread<8;thread++)tasks.add(executor.submit(()->{for(int pass=0;pass<8;pass++){
                var fields=NativeDhHeightmaps.build(source);assertNotNull(fields);
                for(int x=0;x<16;x++)for(int z=0;z<16;z++){assertEquals(expected.solid(x,z),fields.solid(x,z));assertEquals(expected.blocking(x,z),fields.blocking(x,z));}
            }}));
            for(var task:tasks)task.get();
        }
    }
}
