package net.minecraft.world.level.levelgen;

import com.seibel.distanthorizons.common.wrappers.WrapperFactory;
import com.seibel.distanthorizons.common.wrappers.block.*;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dataObjects.BlockBiomeWrapperPair;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPosMutable;
import com.seibel.distanthorizons.core.util.FullDataPointUtil;
import com.seibel.distanthorizons.core.wrapperInterfaces.IWrapperFactory;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftSharedWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import java.lang.reflect.*;
import java.nio.file.Path;
import java.util.*;

/** Identical compiled ordinary cached-neighborhood workload; CPU only, no FPS claim. */
public final class DhTintVerification {
    private static BiomeWrapper biome(String name) throws Exception {
        var ctor=BiomeWrapper.class.getDeclaredConstructors()[0];ctor.setAccessible(true);var b=(BiomeWrapper)ctor.newInstance(null,null);
        var serial=BiomeWrapper.class.getDeclaredField("serialString");serial.setAccessible(true);serial.set(b,name);
        var hash=BiomeWrapper.class.getDeclaredField("hashCode");hash.setAccessible(true);hash.setInt(b,Objects.hash(name));return b;
    }
    @SuppressWarnings("unchecked")
    private static void seed(BiomeWrapper biome,int color) throws Exception {
        try {
            var cache=BlockStateWrapper.class.getMethod("nativeTintCache").invoke(BlockStateWrapper.AIR);
            var identity=BiomeWrapper.class.getMethod("nativeTintIdentity").invoke(biome);
            cache.getClass().getMethod("put",identity.getClass(),int.class).invoke(cache,identity,color);
        }catch(NoSuchMethodException e) {
            var field=AbstractDhTintGetter.class.getDeclaredField("COLOR_BY_BLOCK_BIOME_PAIR");field.setAccessible(true);
            ((Map<BlockBiomeWrapperPair,Integer>)field.get(null)).put(BlockBiomeWrapperPair.get(BlockStateWrapper.AIR,biome),color);
        }
    }
    private static long run(TintWithoutLevelOverrider getter,int rounds) {
        long checksum=0;var pos=new DhBlockPosMutable(0,0,0);
        for(int i=0;i<rounds;i++){pos.setX(8+i%48);pos.setY(i%4*8);pos.setZ(8+(i/48)%48);checksum+=getter.tryGetBlockTint(pos);checksum+=pos.getX()*31L+pos.getZ();}
        return checksum;
    }
    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        SingletonInjector.INSTANCE.bind(IWrapperFactory.class,WrapperFactory.INSTANCE);
        var shared=(IMinecraftSharedWrapper)Proxy.newProxyInstance(IMinecraftSharedWrapper.class.getClassLoader(),new Class<?>[]{IMinecraftSharedWrapper.class},(p,m,a)->switch(m.getName()){
            case "getInstallationDirectory"->Path.of("run").toFile();case "isDedicatedServer"->false;case "getDelayedSetupComplete"->true;default->throw new UnsupportedOperationException(m.toString());});
        SingletonInjector.INSTANCE.bind(IMinecraftSharedWrapper.class,shared);
        var level=(IClientLevelWrapper)Proxy.newProxyInstance(IClientLevelWrapper.class.getClassLoader(),new Class<?>[]{IClientLevelWrapper.class},(p,m,a)->{if(m.getName().equals("getMinHeight"))return 0;throw new UnsupportedOperationException(m.toString());});
        int radius=args.length<3?3:Integer.parseInt(args[2]),detail=args.length<4?6:Integer.parseInt(args[3]);
        var source=FullDataSourceV2.createEmpty(detail);var biomes=new BiomeWrapper[4];int[] colors={0xff1975e3,0xff3e9721,0xffea7039,0xffce8e93};
        for(int i=0;i<4;i++){biomes[i]=biome("test:benchmark_tint_"+i);source.mapping.addIfNotPresentAndGetId(biomes[i],BlockStateWrapper.AIR);seed(biomes[i],colors[i]);}
        // Frozen exposes LongArrayList[] and Current LongList[]; bind that
        // storage difference once, outside both warmup and measured work.
        var field=FullDataSourceV2.class.getDeclaredField("dataPoints");field.setAccessible(true);
        var columns=(Object[])field.get(source);
            var columnGetter=FullDataSourceV2.class.getDeclaredMethod("getColumnAtRelPos",int.class,int.class);
            for(int x=0;x<64;x++)for(int z=0;z<64;z++)if(columns[x*64+z]==null)columns[x*64+z]=columnGetter.invoke(source,x,z);
        for(int x=0;x<64;x++)for(int z=0;z<64;z++)for(int j=3;j>=0;j--)((it.unimi.dsi.fastutil.longs.LongList)columns[x*64+z]).add(FullDataPointUtil.encode((x+3*z+j)%4,8,j*8,(byte)5,(byte)15));
        Config.Client.Advanced.Graphics.Quality.lodBiomeBlending.setWithoutFiringEvents(radius);
        var getter=new TintWithoutLevelOverrider();getter.update(biomes[0],BlockStateWrapper.AIR,source,level);
        int rounds=Integer.parseInt(args[0]),warm=Integer.parseInt(args[1]);for(int i=0;i<5;i++)run(getter,warm);
        long start=System.nanoTime();long checksum=run(getter,rounds);long elapsed=System.nanoTime()-start;
        System.out.println("DH_TINT "+elapsed+" "+checksum);source.close();
    }
}
