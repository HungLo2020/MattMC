package net.minecraft.world.level.levelgen;

import com.seibel.distanthorizons.common.wrappers.block.BiomeWrapper;
import com.seibel.distanthorizons.common.wrappers.block.BlockStateWrapper;
import com.seibel.distanthorizons.common.wrappers.chunk.ChunkWrapper;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos;
import com.seibel.distanthorizons.core.wrapperInterfaces.chunk.IChunkWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/** Actual Frozen producer replay and supplemental admission controls; CPU only. */
@Tag("parity")
class NativeDhChunkHashTest {
    private static ILevelWrapper level;
    @BeforeAll static void bootstrap() throws Exception {
        NativeDhColumnsTest.bootstrap();
        var field=NativeDhColumnsTest.class.getDeclaredField("level");field.setAccessible(true);level=(ILevelWrapper)field.get(null);
    }
    private static ProtoChunk chunk(int index,int pattern) throws Exception {
        var method=NativeDhColumnsTest.class.getDeclaredMethod("chunk",int.class,int.class);method.setAccessible(true);return (ProtoChunk)method.invoke(null,index,pattern);
    }
    private static NativeDhHeightmaps heights(ChunkWrapper wrapper) throws Exception {
        var field=ChunkWrapper.class.getDeclaredField("nativeHeightMaps");field.setAccessible(true);var value=(NativeDhHeightmaps)field.get(wrapper);assertNotNull(value);return value;
    }
    private static void staleEmpty(LevelChunkSection section) throws Exception {
        var field=LevelChunkSection.class.getDeclaredField("counters");field.setAccessible(true);Object counters=field.get(section);
        var set=counters.getClass().getDeclaredMethod("set",int.class,int.class,int.class);set.setAccessible(true);set.invoke(counters,0,0,0);
    }
    private static int original(ChunkWrapper wrapper) throws Throwable {
        var proxy=(IChunkWrapper)Proxy.newProxyInstance(IChunkWrapper.class.getClassLoader(),new Class<?>[]{IChunkWrapper.class},(p,m,a)->{
            if(m.getName().equals("getBlockBiomeHashCode"))return InvocationHandler.invokeDefault(p,m,a);
            try{return m.invoke(wrapper,a);}catch(InvocationTargetException e){throw e.getCause();}
        });
        return proxy.getBlockBiomeHashCode();
    }
    @Test void all96FrozenHashesThroughActualRetainedInputsAndBothEmitterRoutes() throws Exception {
        try(var in=new DataInputStream(new GZIPInputStream(Objects.requireNonNull(getClass().getResourceAsStream("/world/chunk/frozen-dh-chunk-hash.bin.gz"))))) {
            assertEquals(0x44484831,in.readInt());assertEquals(15,in.readInt());
            for(int i=0;i<15;i++){in.readUTF();assertEquals(64,in.readUTF().length());}
            assertEquals("ee865336a6a8cc6d801dada6a0b54da98564a6ed6d216df3cefb4666f7fc187b",in.readUTF());assertTrue(in.readUTF().contains("Actual Frozen getBlockBiomeHashCode"));
            for(int table=0;table<2;table++)for(int n=in.readInt();n>0;n--){in.readUTF();in.readInt();}
            assertEquals(96,in.readInt());
            ProtoChunk chunk=null;ChunkWrapper wrapper=null;NativeDhHeightmaps retained=null;
            var ocean=((Level)level.getWrappedMcObject()).registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(net.minecraft.world.level.biome.Biomes.OCEAN);
            for(int test=0;test<96;test++) {
                String name=in.readUTF();int phase=test%3;
                if(phase==0){chunk=chunk(test/6,(test/3)%2);wrapper=new ChunkWrapper(chunk,level);retained=heights(wrapper);}
                var sections=chunk.getSectionsForRead();int middle=sections.length/2;
                if(phase==1){
                    sections[middle].getStates().getAndSetUnchecked(2,6,4,Blocks.GLOWSTONE.defaultBlockState());
                    @SuppressWarnings("unchecked") var data=(PalettedContainer<Holder<Biome>>)sections[middle].getBiomes();
                    for(int y=0;y<4;y++)for(int z=0;z<4;z++)for(int x=0;x<4;x++)data.getAndSetUnchecked(x,y,z,ocean);
                }
                if(phase==2){
                    staleEmpty(sections[middle]);var lights=wrapper.getWorldBlockLightPosList();Collections.reverse(lights);
                    if(!lights.isEmpty())lights.add(lights.get(0));
                    lights.add(new DhBlockPos(chunk.getPos().getMinBlockX()+15,chunk.getMaxY()+1,chunk.getPos().getMinBlockZ()+15));
                }
                assertEquals(chunk.getMinY(),in.readInt());int height=in.readInt();assertEquals(chunk.getHeight(),height);
                assertEquals(wrapper.getMinNonEmptyHeight(),in.readInt());assertEquals(wrapper.getMaxNonEmptyHeight(),in.readInt());
                for(var section:sections)assertEquals(section.hasOnlyAir(),in.readBoolean());
                in.skipNBytes(height*256L*4+height/16L*64*4);
                for(int x=0;x<16;x++)for(int z=0;z<16;z++){assertEquals(wrapper.getSolidHeightMapValue(x,z),in.readInt());assertEquals(wrapper.getLightBlockingHeightMapValue(x,z),in.readInt());}
                int count=in.readInt();int[][] positions=new int[count][3];for(var p:positions){p[0]=in.readInt();p[1]=in.readInt();p[2]=in.readInt();}int expected=in.readInt();
                long nativeHash=NativeDhChunkHash.hash(level,wrapper,retained);assertEquals(1,nativeHash>>>32,"Native admission "+name);assertEquals(expected,(int)nativeHash,name);
                assertEquals(expected,wrapper.getBlockBiomeHashCode(),"Ordinary consumer "+name);assertSame(retained,heights(wrapper));
                var actual=wrapper.getWorldBlockLightPosList();assertEquals(count,actual.size());
                for(int i=0;i<count;i++){assertEquals(positions[i][0],actual.get(i).getX()&15);assertEquals(positions[i][1],actual.get(i).getY());assertEquals(positions[i][2],actual.get(i).getZ()&15);}
            }
            assertEquals(-1,in.read());
        }
    }
    @Test void publicWrapperReplacementDeclinesBeforeNativeHashAndKeepsOriginalResult() throws Throwable {
        var chunk=chunk(0,0);var section=chunk.getSectionsForRead()[chunk.getSectionsForRead().length/2];
        section.setBlockState(2,6,4,Blocks.GLOWSTONE.defaultBlockState());assertFalse(section.hasOnlyAir());
        var wrapper=new ChunkWrapper(chunk,level);var fields=heights(wrapper);
        var state=Blocks.GLOWSTONE.defaultBlockState();var old=BlockStateWrapper.WRAPPER_BY_BLOCK_STATE.put(state,BlockStateWrapper.AIR);
        try {
            assertEquals(0,NativeDhChunkHash.hash(level,wrapper,fields));assertEquals(original(wrapper),wrapper.getBlockBiomeHashCode());
        } finally {if(old==null)BlockStateWrapper.WRAPPER_BY_BLOCK_STATE.remove(state);else BlockStateWrapper.WRAPPER_BY_BLOCK_STATE.put(state,old);}
        assertEquals(1,NativeDhChunkHash.hash(level,wrapper,fields)>>>32);
    }
    @Test void escapedBiomeAndCustomWrapperCallbacksKeepCompatibilityAndExceptionPrefix() throws Throwable {
        var chunk=chunk(0,0);var wrapper=new ChunkWrapper(chunk,level);var fields=heights(wrapper);
        @SuppressWarnings("unchecked") var biomes=(PalettedContainer<Holder<Biome>>)chunk.getSectionsForRead()[0].getBiomes();
        var escape=PalettedContainer.class.getDeclaredMethod("dataForCompatibilityMutation");escape.setAccessible(true);escape.invoke(biomes);
        assertEquals(0,NativeDhChunkHash.hash(level,wrapper,fields));assertEquals(original(wrapper),wrapper.getBlockBiomeHashCode());
        class Custom extends ChunkWrapper {
            int calls;final RuntimeException failure=new IllegalStateException("original sampled callback");
            Custom(){super(chunk,level,false);}
            @Override public int getMinNonEmptyHeight(){return chunk.getMinY();}
            @Override public int getMaxNonEmptyHeight(){return chunk.getMinY()+16;}
            @Override public com.seibel.distanthorizons.core.wrapperInterfaces.block.IBlockStateWrapper getBlockState(int x,int y,int z,
                com.seibel.distanthorizons.core.wrapperInterfaces.misc.IMutableBlockPosWrapper p,
                com.seibel.distanthorizons.core.wrapperInterfaces.block.IBlockStateWrapper previous){calls++;throw failure;}
        }
        var custom=new Custom();assertSame(custom.failure,assertThrows(RuntimeException.class,custom::getBlockBiomeHashCode));assertEquals(1,custom.calls);
    }
    @Test void mutableEmitterSubclassUsesOriginalCallbackAndInvalidPositionException() throws Throwable {
        var wrapper=new ChunkWrapper(chunk(0,0),level);var fields=heights(wrapper);var list=wrapper.getWorldBlockLightPosList();
        class Position extends DhBlockPos {
            int calls;Position(){super(0,wrapper.getChunk().getMinY(),0);}
            @Override public void mutateToChunkRelativePos(com.seibel.distanthorizons.core.pos.blockPos.DhBlockPosMutable p){calls++;super.mutateToChunkRelativePos(p);}
        }
        var custom=new Position();list.clear();list.add(custom);
        assertEquals(0,NativeDhChunkHash.hash(level,wrapper,fields));assertEquals(0,custom.calls);
        assertEquals(original(wrapper),wrapper.getBlockBiomeHashCode());assertEquals(2,custom.calls);
        list.clear();list.add(new DhBlockPos(0,wrapper.getChunk().getMinY()-1,0));
        assertEquals(0,NativeDhChunkHash.hash(level,wrapper,fields));
        if(IChunkWrapper.RUN_RELATIVE_POS_INDEX_VALIDATION)assertThrows(IndexOutOfBoundsException.class,wrapper::getBlockBiomeHashCode);
        else assertEquals(original(wrapper),wrapper.getBlockBiomeHashCode());
    }
}
