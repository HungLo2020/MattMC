package net.minecraft.world.level.levelgen;

import com.seibel.distanthorizons.common.wrappers.block.BlockStateWrapper;
import com.seibel.distanthorizons.common.wrappers.chunk.ChunkWrapper;
import com.seibel.distanthorizons.core.generation.DhLightingEngine;
import com.seibel.distanthorizons.core.wrapperInterfaces.chunk.*;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.GZIPInputStream;
import net.minecraft.SharedConstants;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.NativeDhLighting;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("parity")
class NativeDhLightingTest {
    private static ILevelWrapper level;
    private static Method light;
    @BeforeAll static void bootstrap() throws Exception {
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();System.setProperty("net.bytebuddy.experimental","true");
        Level registryOnly=org.mockito.Mockito.mock(Level.class);
        org.mockito.Mockito.when(registryOnly.registryAccess()).thenReturn(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        level=(ILevelWrapper)Proxy.newProxyInstance(ILevelWrapper.class.getClassLoader(),new Class<?>[]{ILevelWrapper.class},
            (proxy,method,args)-> {if(method.getName().equals("getWrappedMcObject"))return registryOnly;throw new UnsupportedOperationException(method.toString());});
        light=DhLightingEngine.class.getDeclaredMethod("lightChunk",IChunkWrapper.class,ArrayList.class,int.class,boolean.class,boolean.class);light.setAccessible(true);
    }
    private record Phase(int sky,boolean block,boolean updateSky,int work,byte[] cells) {}
    private static List<List<Phase>> soloExpected() throws IOException {
        try(var in=new DataInputStream(new GZIPInputStream(Objects.requireNonNull(NativeDhLightingTest.class.getResourceAsStream("/world/lighting/frozen-dh-lighting.bin.gz"))))) {
            assertEquals(0x44484c31,in.readInt());assertEquals(7,in.readInt());
            for(int i=0;i<7;i++){in.readUTF();String pin=in.readUTF();assertEquals(64,pin.length());if(i==3)assertEquals("f08e6005aa6dab745989ce904d04a8fc112f40bfc73f577587bdbc66d86ef1bf",pin);}
            assertEquals("ee865336a6a8cc6d801dada6a0b54da98564a6ed6d216df3cefb4666f7fc187b",in.readUTF());in.readUTF();
            in.skipNBytes(in.readInt()*2L);assertEquals(20,in.readInt());var result=new ArrayList<List<Phase>>();
            for(int i=0;i<16;i++) {
                assertEquals("saved-solo-"+i,in.readUTF());assertEquals(1,in.readInt());assertEquals(4,in.readInt());
                in.readInt();int height=in.readInt();in.readInt();in.readInt();in.skipNBytes(height*256L*4);
                in.skipNBytes(in.readInt()*4L);in.skipNBytes(height*256L);assertEquals(1,in.readInt());assertEquals(4,in.readInt());assertEquals(3,in.readInt());
                var phases=new ArrayList<Phase>();for(int p=0;p<3;p++) {
                    int sky=in.readInt();boolean block=in.readBoolean(),updateSky=in.readBoolean();int work=in.readInt();byte[] cells=in.readNBytes(height*256);
                    assertEquals(cells.length,height*256);assertTrue(in.readBoolean());assertTrue(in.readBoolean());phases.add(new Phase(sky,block,updateSky,work,cells));
                }result.add(phases);
            }return result;
        }
    }
    @Test void actualProductionBridgeAndDirectGettersMatchAllSavedFrozenSoloPasses() throws Exception {
        var expected=soloExpected();var records=HeightmapFixtures.saved();assertEquals(16,records.size());
        for(int i=0;i<records.size();i++) {
            var source=HeightmapFixtures.recorded(records.get(i));var wrapper=new ChunkWrapper(source,level);
            var nearby=new ArrayList<IChunkWrapper>();nearby.add(wrapper);
            wrapper.getWorldBlockLightPosList();assertNotNull(wrapper.nativeDhLightingInput().sources(),"Normal hash/beacon cache is Rust owned before lighting");
            for(var phase:expected.get(i)) {
                int work=(int)light.invoke(DhLightingEngine.INSTANCE,wrapper,nearby,phase.sky,phase.block,phase.updateSky);
                assertEquals(phase.work,work,"Frozen work chunk "+i);var inputs=wrapper.nativeDhLightingInput();assertNotNull(inputs);assertNotNull(inputs.previous(),"Actual native admission");
                assertTrue(wrapper.isDhBlockLightingCorrect());assertTrue(wrapper.isDhSkyLightCorrect());
                for(int y=source.getMinY();y<=source.getMaxY();y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++) {
                    int index=(y-source.getMinY())*256+z*16+x;int packed=Byte.toUnsignedInt(phase.cells[index]);
                    assertEquals(packed&15,wrapper.getDhBlockLight(x,y,z),"Frozen block chunk"+i+" cell"+index);
                    assertEquals(packed>>4,wrapper.getDhSkyLight(x,y,z),"Frozen sky chunk"+i+" cell"+index);
                }
            }
        }
    }
    @Test void immutableFieldsSurviveRebuildAndPublicMutableStorageDetaches() {
        var source=HeightmapFixtures.chunk("empty",-64,32,0);source.getSection(1).setBlockState(1,7,2,Blocks.GLOWSTONE.defaultBlockState());
        var wrapper=new ChunkWrapper(source,level);var nearby=new ArrayList<IChunkWrapper>();nearby.add(wrapper);
        assertTrue(NativeDhLighting.tryLight(wrapper,nearby,15,true,true)>=0);var first=wrapper.nativeDhLightingInput().previous();assertNotNull(first);assertEquals(1,first.sourceCount());
        int old=first.light(2,-41,2,false);assertTrue(old>0);
        if(!IChunkWrapper.RUN_RELATIVE_POS_INDEX_VALIDATION)assertEquals(wrapper.getDhBlockLight(2,-41,2),wrapper.getDhBlockLight(18,-41,18),"Original LightSection masks coordinates when validation is off");
        source.getSection(1).setBlockState(8,7,8,Blocks.GLOWSTONE.defaultBlockState());
        assertTrue(NativeDhLighting.tryLight(wrapper,nearby,15,true,true)>=0);var next=wrapper.nativeDhLightingInput().previous();assertNotSame(first,next);assertEquals(1,next.sourceCount(),"Original source-position cache remains stale");assertEquals(old,first.light(2,-41,2,false));
        wrapper.setDhBlockLight(2,-41,2,3);assertNull(wrapper.nativeDhLightingInput());assertEquals(3,wrapper.getDhBlockLight(2,-41,2));assertEquals(old,first.light(2,-41,2,false));
        assertEquals(1,wrapper.getWorldBlockLightPosList().size());
        var injected=ChunkLightStorage.createSkyLightStorage(wrapper);injected.set(2,-41,2,11);wrapper.setSkyLightStorage(injected);
        assertEquals(11,wrapper.getDhSkyLight(2,-41,2));injected.set(2,-41,2,7);assertEquals(7,wrapper.getDhSkyLight(2,-41,2));
    }
    @Test void customWrappersAndPublicCachedSourcesOrStateOverridesDeclineBeforeCallbacks() {
        var source=HeightmapFixtures.chunk("empty",-64,32,0);var calls=new int[1];
        var custom=new ChunkWrapper(source,level){@Override public int getDhBlockLight(int x,int y,int z){calls[0]++;return 7;}};
        var nearby=new ArrayList<IChunkWrapper>();nearby.add(custom);assertEquals(-1,NativeDhLighting.tryLight(custom,nearby,15,true,true));assertEquals(0,calls[0]);
        var wrapper=new ChunkWrapper(source,level);nearby.clear();nearby.add(wrapper);wrapper.getWorldBlockLightPosList().add(new com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos(0,-63,0));assertEquals(-1,NativeDhLighting.tryLight(wrapper,nearby,15,true,true));
        wrapper=new ChunkWrapper(source,level);nearby.clear();nearby.add(wrapper);
        var air=Blocks.AIR.defaultBlockState();var stone=BlockStateWrapper.fromBlockState(Blocks.STONE.defaultBlockState(),level);
        var previous=BlockStateWrapper.WRAPPER_BY_BLOCK_STATE.put(air,stone);
        try{assertEquals(-1,NativeDhLighting.tryLight(wrapper,nearby,15,true,true));assertNull(wrapper.nativeDhLightingInput().previous());}
        finally{if(previous==null)BlockStateWrapper.WRAPPER_BY_BLOCK_STATE.remove(air);else BlockStateWrapper.WRAPPER_BY_BLOCK_STATE.put(air,previous);}
    }
    @Test void independentParallelPassesReadOneLiveWorldWithoutSharingScratchOrOutputs() throws Exception {
        var source=HeightmapFixtures.recorded(HeightmapFixtures.saved().getFirst());var expected=soloExpected().getFirst().getFirst();
        try(var executor=Executors.newFixedThreadPool(8)) {
            var tasks=new ArrayList<Future<?>>();for(int t=0;t<8;t++)tasks.add(executor.submit(()->{
                for(int p=0;p<4;p++){var wrapper=new ChunkWrapper(source,level);var nearby=new ArrayList<IChunkWrapper>();nearby.add(wrapper);
                    assertEquals(expected.work,NativeDhLighting.tryLight(wrapper,nearby,15,true,true));assertNotNull(wrapper.nativeDhLightingInput().previous());}
            }));for(var task:tasks)task.get();
        }
    }
    @Test void coldNineChunkBordersAndMissingCenterSeedsMatchActualFrozenProduction() throws Exception {
        try(var in=new DataInputStream(new GZIPInputStream(Objects.requireNonNull(NativeDhLightingTest.class.getResourceAsStream("/world/lighting/frozen-dh-lighting-cold.bin.gz"))))) {
            assertEquals(0x44484c31,in.readInt());assertEquals(7,in.readInt());
            for(int i=0;i<7;i++){in.readUTF();String pin=in.readUTF();assertEquals(64,pin.length());if(i==3)assertEquals("f08e6005aa6dab745989ce904d04a8fc112f40bfc73f577587bdbc66d86ef1bf",pin);}
            assertEquals("ee865336a6a8cc6d801dada6a0b54da98564a6ed6d216df3cefb4666f7fc187b",in.readUTF());in.readUTF();in.skipNBytes(in.readInt()*2L);assertEquals(2,in.readInt());
            for(int testCase=0;testCase<2;testCase++) {
                String name=in.readUTF();int count=in.readInt();var wrappers=new ChunkWrapper[9];int[] slots=new int[count];
                for(int i=0;i<count;i++) {
                    int slot=in.readInt();slots[i]=slot;int min=in.readInt(),height=in.readInt(),bottom=in.readInt(),top=in.readInt();
                    var template=HeightmapFixtures.recorded(HeightmapFixtures.saved().getFirst());
                    var source=new net.minecraft.world.level.chunk.ProtoChunk(new net.minecraft.world.level.ChunkPos(-37+slot%3-1,41+slot/3-1),
                        net.minecraft.world.level.chunk.UpgradeData.EMPTY,template.getSections(),new net.minecraft.world.ticks.ProtoChunkTicks<>(),new net.minecraft.world.ticks.ProtoChunkTicks<>(),
                        net.minecraft.world.level.LevelHeightAccessor.create(min,height),null,null);
                    var wrapper=new ChunkWrapper(source,level);wrappers[slot]=wrapper;assertEquals(bottom,wrapper.getMinNonEmptyHeight());assertEquals(top,wrapper.getMaxNonEmptyHeight());
                    // Verify the same actual input, including the complete native-backed states.
                    for(var section:source.getSections())for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++)
                        assertEquals(in.readInt(),net.minecraft.world.level.block.Block.getId(section.getBlockState(x,y,z)));
                    in.skipNBytes(in.readInt()*4L);
                    byte[] initial=in.readNBytes(height*256);assertEquals(height*256,initial.length);for(byte value:initial)assertEquals(0,value);
                }
                var nearby=new ArrayList<IChunkWrapper>();for(int i=0,n=in.readInt();i<n;i++){int slot=in.readInt();nearby.add(slot<0?null:wrappers[slot]);}
                assertEquals(3,in.readInt());for(int phase=0;phase<3;phase++) {
                    int sky=in.readInt();boolean block=in.readBoolean(),updateSky=in.readBoolean();int expectedWork=in.readInt();
                    int actualWork=(int)light.invoke(DhLightingEngine.INSTANCE,wrappers[4],nearby,sky,block,updateSky);assertEquals(expectedWork,actualWork,name+" work phase"+phase);
                    for(int slot:slots) {
                        var wrapper=wrappers[slot];var source=wrapper.getChunk();assertNotNull(wrapper.nativeDhLightingInput().previous(),"Actual native neighborhood admission");
                        for(int y=source.getMinY();y<=source.getMaxY();y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++) {
                            int packed=in.readUnsignedByte();assertEquals(packed&15,wrapper.getDhBlockLight(x,y,z),name+" block slot"+slot);assertEquals(packed>>4,wrapper.getDhSkyLight(x,y,z),name+" sky slot"+slot);
                        }
                        assertEquals(in.readBoolean(),wrapper.isDhBlockLightingCorrect());assertEquals(in.readBoolean(),wrapper.isDhSkyLightCorrect());
                    }
                }
            }
            assertEquals(-1,in.read());
        }
    }

}
