package net.minecraft.world.level.levelgen;

import com.seibel.distanthorizons.api.enums.config.EDhApiDataCompressionMode;
import com.seibel.distanthorizons.common.wrappers.WrapperFactory;
import com.seibel.distanthorizons.core.dataObjects.fullData.FullDataPointIdMap;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.dataObjects.transformers.LodDataBuilder;
import com.seibel.distanthorizons.core.util.objects.dataStreams.*;
import com.seibel.distanthorizons.core.wrapperInterfaces.block.IBlockStateWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.*;
import com.seibel.distanthorizons.common.wrappers.chunk.ChunkWrapper;
import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import net.minecraft.world.level.chunk.NativeDhIdMap;
import net.minecraft.world.level.chunk.NativeDhMaterials;
import net.minecraft.util.NativeLibraryLoader;
import java.lang.foreign.*;
import java.lang.ref.Reference;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/** Actual Frozen ordered-map corpus and normal native-producer/recipient consumers. */
@Tag("parity")
class NativeDhIdMapTest {
    record Pair(IBiomeWrapper biome,IBlockStateWrapper state) { }
    static ILevelWrapper level;
    @BeforeAll static void bootstrap() throws Exception {
        NativeDhColumnsTest.bootstrap();var field=NativeDhColumnsTest.class.getDeclaredField("level");field.setAccessible(true);level=(ILevelWrapper)field.get(null);
    }
    static Object field(FullDataPointIdMap map,String name) throws Exception {
        var f=FullDataPointIdMap.class.getDeclaredField(name);f.setAccessible(true);return f.get(map);
    }
    static FullDataPointIdMap imported(FullDataPointIdMap map) throws Exception {
        var bytes=new ByteArrayList();try(var out=DhDataOutputStream.create(EDhApiDataCompressionMode.UNCOMPRESSED,bytes)){map.serialize(out);}
        try(var in=DhDataInputStream.create(bytes,EDhApiDataCompressionMode.UNCOMPRESSED)){return FullDataPointIdMap.deserialize(in,map.getPos(),level);}
    }
    @Test void completeFrozenMapCorpusMatchesNativeBindingsHashesAndExceptionPrefixes() throws Exception {
        try(var in=new DataInputStream(new GZIPInputStream(Objects.requireNonNull(getClass().getResourceAsStream("/world/chunk/frozen-dh-id-map.bin.gz"))))) {
            assertEquals(0x44484931,in.readInt());assertEquals("67f8da0de69158f97fec4b3f00e00cf48827d3ae02a944de5c13c0fbb570f07a",in.readUTF());assertEquals(64,in.readUTF().length());assertTrue(in.readUTF().contains("Actual Frozen ordered map"));
            var bank=new ArrayList<Pair>();for(int n=in.readInt();n>0;n--){String b=in.readUTF(),s=in.readUTF();var biome=WrapperFactory.INSTANCE.deserializeBiomeWrapperOrGetDefault(b,level);var state=WrapperFactory.INSTANCE.deserializeBlockStateWrapperOrGetDefault(s,level);assertEquals(b,biome.getSerialString());assertEquals(s,state.getSerialString());bank.add(new Pair(biome,state));}
            assertEquals(300,in.readInt());var maps=new FullDataPointIdMap[]{new FullDataPointIdMap(6),new FullDataPointIdMap(-1),new FullDataPointIdMap(0x800000007fffffffL)};int failures=0;
            for(int step=0;step<300;step++) {
                int op=in.readUnsignedByte(),dst=in.readUnsignedByte(),src=in.readUnsignedByte(),entry=in.readInt();long pos=in.readLong();String exception="",message="";int[] ids=new int[0];
                try {switch(op){
                    case 0 -> {var p=bank.get(entry);ids=new int[]{maps[dst].addIfNotPresentAndGetId(p.biome,p.state)};}
                    case 1 -> ids=maps[dst].mergeAndReturnRemappedEntityIds(maps[src]);
                    case 2 -> maps[dst].addAll(maps[src]);
                    case 3 -> maps[dst].clear(pos);
                    case 4 -> maps[dst]=imported(maps[dst]);
                    default -> fail("action");
                }}catch(ArrayIndexOutOfBoundsException e){exception=e.getClass().getSimpleName();message=e.getMessage();failures++;}
                assertEquals(in.readUTF(),exception,"step"+step);assertEquals(in.readUTF(),message,"step"+step);int[] expected=new int[in.readInt()];for(int i=0;i<expected.length;i++)expected[i]=in.readInt();assertArrayEquals(expected,ids,"step"+step);
                for(var map:maps){assertEquals(in.readLong(),map.getPos());assertEquals(in.readInt(),map.hashCode(),"step"+step);assertEquals(in.readInt(),map.size());for(int i=0;i<map.size();i++){assertEquals(in.readUTF(),map.getBiomeWrapper(i).getSerialString());assertEquals(in.readUTF(),map.getBlockStateWrapper(i).getSerialString());}assertInstanceOf(NativeDhIdMap.class,field(map,"nativeMap"));assertTrue(((Map<?,?>)field(map,"idMap")).isEmpty(),"No Java identity-index mirror");}
            }
            assertTrue(failures>0);assertEquals(-1,in.read());
        }
    }
    @Test void importedDuplicatesSurviveNativeSelfMergeAndCompatibilityDetachment() throws Exception {
        var biome=WrapperFactory.INSTANCE.deserializeBiomeWrapperOrGetDefault("minecraft:plains",level);
        var block=WrapperFactory.INSTANCE.deserializeBlockStateWrapperOrGetDefault("minecraft:stone",level);
        var original=new FullDataPointIdMap(6);assertEquals(0,original.addIfNotPresentAndGetId(biome,block));original.addAll(new FullDataPointIdMap(6));
        var copies=new FullDataPointIdMap(6);copies.addAll(original);copies.addAll(original);var map=imported(copies);
        var error=assertThrows(ArrayIndexOutOfBoundsException.class,()->map.mergeAndReturnRemappedEntityIds(map));
        assertEquals("Index 2 out of bounds for length 2",error.getMessage());assertEquals(3,map.size());assertEquals(2,map.addIfNotPresentAndGetId(biome,block));
        int[] calls={0};var type=IBiomeWrapper.class;
        var custom=(IBiomeWrapper)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(p,m,a)->switch(m.getName()){
            case "getSerialString" -> {calls[0]++;yield biome.getSerialString();}case "hashCode" -> biome.hashCode();case "equals" -> p==a[0];default -> throw new UnsupportedOperationException(m.toString());});
        assertEquals(2,map.addIfNotPresentAndGetId(custom,block));assertNull(field(map,"nativeMap"));assertEquals(1,((Map<?,?>)field(map,"idMap")).size());assertTrue(calls[0]>0);assertEquals(3,map.size());
        map.clear(-1);assertEquals(0,map.addIfNotPresentAndGetId(biome,block));assertInstanceOf(NativeDhIdMap.class,field(map,"nativeMap"));assertEquals(1,map.size());
    }
    @Test void normalSavedChunkProducerPublishesMaterialRemapWithoutJavaIndex() throws Exception {
        var chunk=NativeDhColumnsTest.class.getDeclaredMethod("chunk",int.class,int.class);chunk.setAccessible(true);
        var light=NativeDhColumnsTest.class.getDeclaredMethod("light",net.minecraft.world.level.chunk.ProtoChunk.class);light.setAccessible(true);
        var wrapper=(ChunkWrapper)light.invoke(null,chunk.invoke(null,0,1));
        try(var source=LodDataBuilder.createFromChunk(level,wrapper);var recipient=FullDataSourceV2.createEmpty(source.getPos())) {
            assertTrue(recipient.updateFromDataSource(source));assertEquals(source.mapping.size(),recipient.mapping.size());
            assertInstanceOf(NativeDhIdMap.class,field(source.mapping,"nativeMap"));assertInstanceOf(NativeDhIdMap.class,field(recipient.mapping,"nativeMap"));
            assertTrue(((Map<?,?>)field(source.mapping,"idMap")).isEmpty());assertTrue(((Map<?,?>)field(recipient.mapping,"idMap")).isEmpty());
            assertInstanceOf(net.minecraft.world.level.chunk.NativeDhColumns.class,field(source.mapping,"nativeColumns"));
            assertTrue(((List<?>)field(source.mapping,"blockBiomePairList")).isEmpty(),"Read-only producer keeps original binding readers");
            for(int id=0;id<source.mapping.size();id++){assertSame(source.mapping.getBiomeWrapper(id),recipient.mapping.getBiomeWrapper(id));assertSame(source.mapping.getBlockStateWrapper(id),recipient.mapping.getBlockStateWrapper(id));}
            int before=recipient.mapping.hashCode();assertArrayEquals(java.util.stream.IntStream.range(0,source.mapping.size()).toArray(),recipient.mapping.mergeAndReturnRemappedEntityIds(source.mapping));assertEquals(before,recipient.mapping.hashCode());
        }
    }
    static byte[] facts(NativeDhMaterials materials) throws Throwable {
        var f=NativeDhMaterials.class.getDeclaredField("owner");f.setAccessible(true);var pointer=(MemorySegment)f.get(materials);
        var view=NativeLibraryLoader.downcallHandle("mattmc_rust","mattmc_dh_materials_view",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.ADDRESS));
        try(var arena=Arena.ofConfined()) {
            var output=arena.allocate(16,8);assertEquals(1,(int)view.invokeExact(pointer,output));
            long count=output.getAtIndex(ValueLayout.JAVA_LONG,1);assertTrue(count>0);
            return output.getAtIndex(ValueLayout.ADDRESS,0).reinterpret(count*3).toArray(ValueLayout.JAVA_BYTE);
        } finally { Reference.reachabilityFence(materials); }
    }
    static boolean usesMap(NativeDhMaterials materials) throws Exception {
        var f=NativeDhMaterials.class.getDeclaredField("nativeMapInput");f.setAccessible(true);return f.getBoolean(materials);
    }
    @Test void intrinsicMaterialConsumerUsesRetainedMapStatesAndKeepsIndependentFacts() throws Throwable {
        var biome=WrapperFactory.INSTANCE.deserializeBiomeWrapperOrGetDefault("minecraft:plains",level);
        var type=IBiomeWrapper.class;
        var custom=(IBiomeWrapper)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(p,m,a)->switch(m.getName()){
            case "getSerialString" -> biome.getSerialString();case "hashCode" -> biome.hashCode();case "equals" -> p==a[0];default -> throw new UnsupportedOperationException(m.toString());});
        var nativeMap=new FullDataPointIdMap(6);var compatibility=new FullDataPointIdMap(6);
        var stone=com.seibel.distanthorizons.common.wrappers.block.BlockStateWrapper.fromBlockState(net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),level);
        var water=com.seibel.distanthorizons.common.wrappers.block.BlockStateWrapper.fromBlockState(net.minecraft.world.level.block.Blocks.WATER.defaultBlockState(),level);
        for(var state:List.of(stone,water,com.seibel.distanthorizons.common.wrappers.block.BlockStateWrapper.AIR)) {
            nativeMap.addIfNotPresentAndGetId(biome,state);compatibility.addIfNotPresentAndGetId(custom,state);
        }
        try(var direct=NativeDhMaterials.create(nativeMap);var original=NativeDhMaterials.create(compatibility)) {
            assertNotNull(direct);assertNotNull(original);assertTrue(usesMap(direct));assertFalse(usesMap(original));
            byte[] before=facts(direct);assertArrayEquals(facts(original),before);assertEquals(9,before.length);assertEquals(1,before[5]);assertEquals(0,before[6]);assertEquals(1,before[7]);
            nativeMap.clear(-1);nativeMap.addIfNotPresentAndGetId(biome,com.seibel.distanthorizons.common.wrappers.block.BlockStateWrapper.AIR);
            assertArrayEquals(before,facts(direct),"Facts survive source clear/replacement without retaining source storage");
            try(var changed=NativeDhMaterials.create(nativeMap)){assertNotNull(changed);assertTrue(usesMap(changed));assertEquals(3,facts(changed).length);assertArrayEquals(Arrays.copyOfRange(before,6,9),facts(changed));}
        }
    }

}
