package net.minecraft.world.level.levelgen;

import com.seibel.distanthorizons.common.wrappers.block.*;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPosMutable;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.GZIPInputStream;
import net.minecraft.world.level.chunk.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/** Actual Frozen cached neighborhoods; synthetic CPU contract evidence only. */
@Tag("parity")
class NativeDhTintTest {
    @BeforeAll static void bootstrap() throws Exception { NativeDhColumnsTest.bootstrap(); }
    private static void assertScratchReleasedColumns() throws Exception {
        var local=NativeDhTintCache.class.getDeclaredField("SCRATCH");local.setAccessible(true);
        var scratch=((ThreadLocal<?>)local.get(null)).get();var pins=scratch.getClass().getDeclaredField("pins");pins.setAccessible(true);
        for(var column:(Object[])pins.get(scratch))assertNull(column,"Completed or declined calls must not retain column owners");
    }
    private static BiomeWrapper biome(String name) throws Exception {
        var ctor=BiomeWrapper.class.getDeclaredConstructors()[0];ctor.setAccessible(true);var b=(BiomeWrapper)ctor.newInstance(null,null);
        var serial=BiomeWrapper.class.getDeclaredField("serialString");serial.setAccessible(true);serial.set(b,name);
        var hash=BiomeWrapper.class.getDeclaredField("hashCode");hash.setAccessible(true);hash.setInt(b,Objects.hash(name));return b;
    }
    @Test void ordinaryGetterMatchesEveryFrozenColorCacheMissAndPositionPrefix() throws Exception {
        var blend=Config.Client.Advanced.Graphics.Quality.lodBiomeBlending;int saved=blend.get();
        var level=(IClientLevelWrapper)Proxy.newProxyInstance(IClientLevelWrapper.class.getClassLoader(),new Class<?>[]{IClientLevelWrapper.class},(p,m,a)->{if(m.getName().equals("getMinHeight"))return -64;throw new AssertionError(m.toString());});
        try(var in=new DataInputStream(new GZIPInputStream(Objects.requireNonNull(getClass().getResourceAsStream("/world/chunk/frozen-dh-tint.bin.gz"))))) {
            assertEquals(0x44485431,in.readInt());assertEquals(7,in.readInt());for(int i=0;i<7;i++){assertFalse(in.readUTF().isEmpty());assertEquals(64,in.readUTF().length());}
            assertTrue(in.readUTF().contains("Actual Frozen"));assertEquals(288,in.readInt());int nativeWindows=0;
            for(int test=0;test<288;test++) {
                long pos=in.readLong();int x=in.readInt(),y=in.readInt(),z=in.readInt(),min=in.readInt(),radius=in.readInt(),mask=in.readInt();
                var cache=BlockStateWrapper.AIR.nativeTintCache();var biomes=new BiomeWrapper[4];
                try(var source=FullDataSourceV2.createEmpty(pos)) {
                    for(int i=0;i<4;i++){biomes[i]=biome(in.readUTF());int color=in.readInt();source.mapping.addIfNotPresentAndGetId(biomes[i],BlockStateWrapper.AIR);if((mask&(1<<i))!=0)cache.put(biomes[i].nativeTintIdentity(),color);}
                    int count=in.readInt();for(int i=0;i<count;i++){int index=in.readInt(),n=in.readInt();for(int j=0;j<n;j++)source.dataPoints[index].add(in.readLong());}
                    int expected=in.readInt(),expectedX=in.readInt(),expectedZ=in.readInt();
                    blend.setWithoutFiringEvents(radius);var getter=new TintWithoutLevelOverrider();getter.update(biomes[0],BlockStateWrapper.AIR,source,level);
                    var mutable=new DhBlockPosMutable(x,y,z);assertEquals(expected,getter.tryGetBlockTint(mutable),"case"+test);assertEquals(expectedX,mutable.getX());assertEquals(expectedZ,mutable.getZ());
                    mutable=new DhBlockPosMutable(x,y,z);
                    if(cache.tryBlend(biomes[0].nativeTintIdentity(),source,mutable,min,radius)) {nativeWindows++;assertEquals(expected,cache.blendedColor());assertEquals(expectedX,mutable.getX());assertEquals(expectedZ,mutable.getZ());}
                }
            }
            assertTrue(nativeWindows>80,"Native admission exercised");assertEquals(-1,in.read());
        }finally{blend.setWithoutFiringEvents(saved);}
    }
    @Test void sharedIdentityCachePreservesAllColorsAndConcurrentPublication() throws Exception {
        var a=new NativeDhTintCache("test:shared_jni_cache");var b=new NativeDhTintCache("test:shared_jni_cache");
        var biome=new NativeDhTintCache.BiomeIdentity("test:shared_jni_biome");assertEquals(0,a.get(biome));
        for(int color:new int[]{Integer.MIN_VALUE,-1,0,Integer.MAX_VALUE}){a.put(biome,color);assertEquals((1L<<32)|(color&0xffffffffL),b.get(biome));}
        var pool=Executors.newFixedThreadPool(4);
        try {var jobs=new ArrayList<Future<?>>();for(int thread=0;thread<4;thread++){int id=thread;jobs.add(pool.submit(()->{var key=new NativeDhTintCache.BiomeIdentity("test:thread_"+id);for(int i=0;i<2000;i++){a.put(key,i);assertEquals((1L<<32)|i,b.get(key));}}));}for(var job:jobs)job.get(30,TimeUnit.SECONDS);}
        finally{pool.shutdownNow();assertTrue(pool.awaitTermination(30,TimeUnit.SECONDS));}
    }
    @Test void customColumnDeclinesWithoutPositionMutationAndReadOnlySpansRefreshAfterGrowth() throws Exception {
        var cache=new NativeDhTintCache("test:decline");var center=biome("test:decline_center");cache.put(center.nativeTintIdentity(),0xff123456);
        try(var source=FullDataSourceV2.createEmpty(6)) {
            source.mapping.addIfNotPresentAndGetId(center,BlockStateWrapper.AIR);
            var pos=new DhBlockPosMutable(30,0,30);
            source.dataPoints[30*64+30]=new it.unimi.dsi.fastutil.longs.LongArrayList(new long[]{1L<<32});
            assertFalse(cache.tryBlend(center.nativeTintIdentity(),source,pos,0,3));assertEquals(30,pos.getX());assertEquals(30,pos.getZ());
            assertScratchReleasedColumns();
            var nativeList=NativeDhColumnList.empty();source.dataPoints[30*64+30]=nativeList;
            nativeList.add(1L<<32);assertTrue(cache.tryBlend(center.nativeTintIdentity(),source,pos,0,3));assertEquals(0xff123456,cache.blendedColor());
            pos=new DhBlockPosMutable(30,0,30);nativeList.clear();for(int i=0;i<100;i++)nativeList.add(1L<<32);
            assertTrue(cache.tryBlend(center.nativeTintIdentity(),source,pos,0,3));assertEquals(0xff123456,cache.blendedColor());
            assertScratchReleasedColumns();
            pos=new DhBlockPosMutable(30,0,30);nativeList.set(0,(1L<<32)|Integer.MAX_VALUE);
            assertFalse(cache.tryBlend(center.nativeTintIdentity(),source,pos,0,3));assertEquals(30,pos.getX());assertEquals(30,pos.getZ());
            assertScratchReleasedColumns();
            nativeList.clear();for(int i=0;i<4097;i++)nativeList.add(1L<<32);
            assertFalse(cache.tryBlend(center.nativeTintIdentity(),source,pos,0,3));assertEquals(30,pos.getX());assertEquals(30,pos.getZ());
            assertScratchReleasedColumns();
        }
    }
    @Test void actualFrozenMutableKeyHistoriesPreserveHashAndOriginalBindings() throws Exception {
        try(var in=new DataInputStream(new GZIPInputStream(Objects.requireNonNull(getClass().getResourceAsStream("/world/chunk/frozen-dh-tint-keys.bin.gz"))))) {
            assertEquals(0x44544b31,in.readInt());assertEquals(32,in.readInt());
            for(int test=0;test<32;test++) {
                String a=in.readUTF(),c=in.readUTF();int hash=in.readInt();var cache=new NativeDhTintCache("test:key_control_"+test);
                var biomes=new NativeDhTintCache.BiomeIdentity[]{new NativeDhTintCache.BiomeIdentity(a,hash),new NativeDhTintCache.BiomeIdentity(a,hash),new NativeDhTintCache.BiomeIdentity(c,hash)};
                int count=in.readInt();for(int step=0;step<count;step++) {
                    int action=in.readInt();var b=biomes[in.readInt()];int color=in.readInt(),expected=in.readInt();
                    if(action==1)cache.put(b,color);if(action==2)b.update(c);
                    long result=cache.get(b);assertEquals(expected,result>>>32==0?Integer.MIN_VALUE:(int)result,"case"+test+" step"+step);
                }
            }
            assertEquals(-1,in.read());
        }
    }
    @Test void resolverCallbacksMayMutateLaterSamplesBeforeCachedNativeTraversal() throws Exception {
        var config=Config.Client.Advanced.Graphics.Quality.lodBiomeBlending;int saved=config.get();
        var field=AbstractDhTintGetter.class.getDeclaredField("BIOME_BY_RESOURCE_STRING");field.setAccessible(true);
        @SuppressWarnings("unchecked") var registryCache=(Map<String,net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome>>)field.get(null);
        var world=(net.minecraft.world.level.Level)NativeDhColumnsTest.registryOnlyFixtureWorld();
        var holder=world.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BIOME).getOrThrow(net.minecraft.world.level.biome.Biomes.PLAINS);
        var first=biome("test:callback_first");var second=biome("test:callback_second");
        registryCache.put(first.getSerialString(),holder);registryCache.put(second.getSerialString(),holder);
        var level=(IClientLevelWrapper)Proxy.newProxyInstance(IClientLevelWrapper.class.getClassLoader(),new Class<?>[]{IClientLevelWrapper.class},(p,m,a)->{if(m.getName().equals("getMinHeight"))return 0;throw new AssertionError(m.toString());});
        try(var source=FullDataSourceV2.createEmpty(6)) {
            source.mapping.addIfNotPresentAndGetId(first,BlockStateWrapper.AIR);source.mapping.addIfNotPresentAndGetId(second,BlockStateWrapper.AIR);
            source.dataPoints[27*64+27].add(1L<<32);source.dataPoints[30*64+30].add(1L<<32);
            config.setWithoutFiringEvents(3);var getter=new TintWithoutLevelOverrider();getter.update(first,BlockStateWrapper.AIR,source,level);
            assertEquals(Integer.MIN_VALUE,getter.tryGetBlockTint(new DhBlockPosMutable(30,0,30)));
            var calls=new java.util.concurrent.atomic.AtomicInteger();
            int result=getter.getBlockTint(new net.minecraft.core.BlockPos(30,0,30),(biome,x,z)->{
                assertSame(holder.value(),biome);assertEquals(0,x);assertEquals(0,z);
                int call=calls.incrementAndGet();if(call==1){source.dataPoints[30*64+30].set(0,(1L<<32)|1);return 0xff0000;}return 0x00ff00;
            });
            assertEquals(2,calls.get());assertEquals(0xff7f7f00,result);
            assertEquals(result,getter.tryGetBlockTint(new DhBlockPosMutable(30,0,30)));assertEquals(2,calls.get());
        }finally{config.setWithoutFiringEvents(saved);registryCache.remove(first.getSerialString());registryCache.remove(second.getSerialString());}
    }

}
