package net.minecraft.world.level.chunk;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.lang.foreign.ValueLayout;
import java.lang.reflect.*;
import java.io.IOException;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.*;
import net.minecraft.world.level.lighting.ChunkSkyLightSources;
import org.junit.jupiter.api.*;

/** Real source storage and native region buffers through existing worldgen callers. */
class NativeDecorationSkyTransportTest {
    static net.minecraft.server.packs.resources.CloseableResourceManager resources;
    static RegistryAccess registries;
    static final LevelHeightAccessor HEIGHT=LevelHeightAccessor.create(-16,32);
    @BeforeAll static void load(){
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
        resources=new net.minecraft.server.packs.resources.MultiPackResourceManager(net.minecraft.server.packs.PackType.SERVER_DATA,List.of(net.minecraft.server.packs.repository.ServerPacksSource.createVanillaPackSource()));
        var layers=net.minecraft.server.RegistryLayer.createRegistryAccess();
        var tags=net.minecraft.tags.TagLoader.loadTagsForExistingRegistries(resources,layers.getLayer(net.minecraft.server.RegistryLayer.STATIC));
        var lookups=net.minecraft.tags.TagLoader.buildUpdatedLookups(layers.getAccessForLoading(net.minecraft.server.RegistryLayer.WORLDGEN),tags);
        registries=net.minecraft.resources.RegistryDataLoader.load(resources,lookups,net.minecraft.resources.RegistryDataLoader.WORLDGEN_REGISTRIES);
    }
    @AfterAll static void closeResources(){resources.close();}
    static ProtoChunk chunk(int x,int z){
        var chunk=new ProtoChunk(new ChunkPos(x,z),UpgradeData.EMPTY,HEIGHT,PalettedContainerFactory.create(registries),null);
        chunk.setPersistedStatus(net.minecraft.world.level.chunk.status.ChunkStatus.CARVERS);return chunk;
    }
    static Object field(Object value,String name)throws Exception{var f=value.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(value);}
    static void field(Object value,String name,Object data)throws Exception{var f=value.getClass().getDeclaredField(name);f.setAccessible(true);f.set(value,data);}
    static Object call(Object value,String name)throws Throwable{
        var m=value.getClass().getDeclaredMethod(name);m.setAccessible(true);
        try{return m.invoke(value);}catch(InvocationTargetException failure){throw failure.getCause();}
    }
    static NativeDecorationRegion region()throws Exception{
        // Use the genuine native region constructor/owner/buffers; these metadata
        // calls need no feature plan or world block callbacks.
        var world=mock(WorldGenRegion.class);when(world.getHeight()).thenReturn(32);when(world.getMinY()).thenReturn(-16);when(world.getMaxY()).thenReturn(15);
        var ctor=NativeDecorationRegion.class.getDeclaredConstructor(WorldGenRegion.class,int.class,NativeLiveBiomeSection.Binding.class);ctor.setAccessible(true);
        var region=ctor.newInstance(world,2,null);var chunks=(ProtoChunk[])field(region,"chunks");
        for(int i=0;i<9;i++)chunks[i]=chunk(i%3,i/3);field(region,"lightChunks",1L);return region;
    }
    static final class Raw extends ChunkSkyLightSources {
        final String name;final List<String> calls;final long[] words;
        Runnable effect;Throwable failure;
        Raw(String name,List<String> calls){super(HEIGHT);this.name=name;this.calls=calls;words=super.nativeRaw().clone();}
        @Override public long[] nativeRaw(){calls.add(name+".raw");if(effect!=null){var run=effect;effect=null;run.run();}if(failure!=null)return NativeDecorationSkyTransportTest.<RuntimeException,long[]>raise(failure);return words;}
        @Override public int nativeBits(){calls.add(name+".bits");return super.nativeBits();}
    }
    @SuppressWarnings("unchecked")static<E extends Throwable,T>T raise(Throwable failure)throws E{throw(E)failure;}
    @Test void transactionCaptureRollbackKeepsEscapedArrayAndOneVirtualReadPerPhase()throws Exception{
        var calls=new ArrayList<String>();var chunk=chunk(0,0);var source=new Raw("source",calls);chunk.skyLightSources=source;
        Arrays.fill(source.words,17);long[] alias=source.nativeRaw();calls.clear();
        var tx=DecorationTransaction.begin(List.of(chunk),chunk,registries);
        try{Arrays.fill(alias,91);}finally{tx.rollback();}
        assertEquals(List.of("source.raw","source.raw"),calls);for(long word:alias)assertEquals(17,word);assertSame(alias,source.words);
    }
    @Test void baseSnapshotRestoreKeepsEscapedIdentityAndLiteralShortSuffix()throws Exception{
        try(var region=region()){
            var chunk=((ProtoChunk[])field(region,"chunks"))[0];var source=chunk.getSkyLightSources();long[] alias=source.nativeRaw();Arrays.fill(alias,17);
            var maps=region.mapsForVerification();Arrays.fill(alias,91);region.restoreMapsForVerification(maps);assertArrayEquals(maps.sky()[0],alias);assertSame(alias,source.nativeRaw());
            maps.sky()[0]=new long[]{23};region.restoreMapsForVerification(maps);assertEquals(23,alias[0]);for(int i=1;i<alias.length;i++)assertEquals(17,alias[i]);
        }
    }
    @Test void customRestoreRereadsPublicSnapshotLengthAfterRawCallback()throws Exception{
        try(var region=region()){
            var chunk=((ProtoChunk[])field(region,"chunks"))[0];var calls=new ArrayList<String>();var source=new Raw("source",calls);chunk.skyLightSources=source;
            Arrays.fill(source.words,7);var maps=region.mapsForVerification();Arrays.fill(source.words,91);calls.clear();
            source.effect=()->maps.sky()[0]=new long[1];
            region.restoreMapsForVerification(maps);
            assertEquals(List.of("source.raw"),calls);assertEquals(7,source.words[0]);for(int i=1;i<source.words.length;i++)assertEquals(91,source.words[i],"length is reread from the public container after the callback");
        }
    }
    @Test void nativeCopyProjectionAndComparisonKeepOneActualRawCall()throws Throwable{
        try(var region=region()){
            var chunk=((ProtoChunk[])field(region,"chunks"))[0];var calls=new ArrayList<String>();var source=new Raw("source",calls);chunk.skyLightSources=source;
            for(int i=0;i<source.words.length;i++)source.words[i]=100+i;
            call(region,"copyWritableState");assertEquals(List.of("source.raw"),calls);calls.clear();
            long sky=(long)call(region,"outSky");var out=region.out();for(int i=0;i<source.words.length;i++)assertEquals(source.words[i],out.getAtIndex(ValueLayout.JAVA_LONG,sky+2+i));
            var before=region.effectsForVerification();assertNull(region.compareEffectsForVerification(before,new long[0],true));assertEquals(List.of("source.raw"),calls);calls.clear();
            out.setAtIndex(ValueLayout.JAVA_LONG,sky+2,777);out.setAtIndex(ValueLayout.JAVA_LONG,sky+1,1);region.afterPlacement();assertEquals(777,source.words[0]);assertEquals(List.of("source.raw"),calls);
        }
    }
    @Test void sourceSnapshotFailurePreservesOriginalThrowableAndOrderedPrefix()throws Exception{
        try(var region=region()){
            var chunks=(ProtoChunk[])field(region,"chunks");var calls=new ArrayList<String>();var first=new Raw("first",calls);var second=new Raw("second",calls);chunks[0].skyLightSources=first;chunks[1].skyLightSources=second;field(region,"lightChunks",3L);var failure=new IOException("actual second sky read");second.failure=failure;
            assertSame(failure,assertThrows(Throwable.class,region::mapsForVerification));assertEquals(List.of("first.raw","second.raw"),calls);
        }
    }
}
