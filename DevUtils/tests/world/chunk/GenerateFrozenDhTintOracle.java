import com.seibel.distanthorizons.common.wrappers.WrapperFactory;
import com.seibel.distanthorizons.common.wrappers.block.*;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dataObjects.fullData.FullDataPointIdMap;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.pos.*;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPosMutable;
import com.seibel.distanthorizons.core.util.FullDataPointUtil;
import com.seibel.distanthorizons.core.wrapperInterfaces.IWrapperFactory;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftSharedWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import com.google.gson.*;
import net.minecraft.core.BlockPos;

/** Records the untouched Frozen tint getter and positional utilities, not copied expected policy. */
public final class GenerateFrozenDhTintOracle {
    static final Class<?>[] PINNED={AbstractDhTintGetter.class,TintWithoutLevelOverrider.class,FullDataSourceV2.class,FullDataPointIdMap.class,DhLodPos.class,DhSectionPos.class,BiomeWrapper.class};
    static String hash(Class<?> c) throws Exception {try(var in=c.getResourceAsStream(c.getSimpleName()+".class")){return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes()));}}
    static BiomeWrapper biome(String name) throws Exception {
        var ctor=BiomeWrapper.class.getDeclaredConstructors()[0];ctor.setAccessible(true);var b=(BiomeWrapper)ctor.newInstance(null,null);
        var serial=BiomeWrapper.class.getDeclaredField("serialString");serial.setAccessible(true);serial.set(b,name);
        var hash=BiomeWrapper.class.getDeclaredField("hashCode");hash.setAccessible(true);hash.setInt(b,Objects.hash(name));return b;
    }
    public static void main(String[] args) throws Exception {
        var pins=new JsonObject();for(var c:PINNED)pins.addProperty(c.getName(),hash(c));
        if(args[0].equals("--pins")){Files.writeString(Path.of(args[1]),pins.toString());return;}
        if(!pins.equals(JsonParser.parseString(Files.readString(Path.of(args[1])))))throw new IllegalStateException("Frozen compiled classes changed");
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        SingletonInjector.INSTANCE.bind(IWrapperFactory.class,WrapperFactory.INSTANCE);
        var shared=(IMinecraftSharedWrapper)Proxy.newProxyInstance(IMinecraftSharedWrapper.class.getClassLoader(),new Class<?>[]{IMinecraftSharedWrapper.class},(p,m,a)->switch(m.getName()) {
            case "getInstallationDirectory"->Path.of("run").toFile();case "isDedicatedServer"->false;case "getDelayedSetupComplete"->true;default->throw new UnsupportedOperationException(m.toString());});
        SingletonInjector.INSTANCE.bind(IMinecraftSharedWrapper.class,shared);
        var level=(IClientLevelWrapper)Proxy.newProxyInstance(IClientLevelWrapper.class.getClassLoader(),new Class<?>[]{IClientLevelWrapper.class},(p,m,a)->{if(m.getName().equals("getMinHeight"))return -64;throw new UnsupportedOperationException(m.toString());});
        var random=new Random(3819837);int cases=288;
        var cacheField=AbstractDhTintGetter.class.getDeclaredField("COLOR_BY_BLOCK_BIOME_PAIR");cacheField.setAccessible(true);
        @SuppressWarnings("unchecked") var cache=(Map<com.seibel.distanthorizons.core.dataObjects.BlockBiomeWrapperPair,Integer>)cacheField.get(null);
        try(var out=new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(Path.of(args[0]))))) {
            out.writeInt(0x44485431);out.writeInt(PINNED.length);for(var c:PINNED){out.writeUTF(c.getName());out.writeUTF(hash(c));}
            out.writeUTF("Actual Frozen cached DH tint, synthetic sparse neighborhoods and cache prefixes; not gameplay or FPS");out.writeInt(cases);
            for(int test=0;test<cases;test++) {
                int detail=6+test%3,sx=test%2==0?-2:1,sz=test%4<2?-1:2;
                long pos=DhSectionPos.encode((byte)detail,sx,sz);int width=1<<detail;
                int x=sx*width+(test%6==0?0:test%6==1?width-1:test%6==2?-2:test%6==3?width+2:width/2);
                int z=sz*width+(test%5==0?0:test%5==1?width-1:width/2),y=-64+test%4*8,radius=test%4;
                var source=FullDataSourceV2.createEmpty(pos);var biomes=new BiomeWrapper[4];int[] colors={0xff1975e3,0x003e9721,0xfeea7039,Integer.MIN_VALUE};
                int cachedMask=(test/12)%6==0?0:(test/12)%6==1?7:15;
                for(int i=0;i<4;i++){biomes[i]=biome("test:tint_"+test+"_"+i);source.mapping.addIfNotPresentAndGetId(biomes[i],BlockStateWrapper.AIR);}
                var columns=new TreeMap<Integer,long[]>();
                for(int px=x-3;px<=x+3;px++)for(int pz=z-3;pz<=z+3;pz++) {
                    var request=new DhLodPos((byte)0,px,pz);
                    if(!DhSectionPos.contains(pos,request.getSectionPosWithSectionDetailLevel((byte)6)))continue;
                    var rel=request.getDhSectionRelativePositionForDetailLevel((byte)(detail-6));int index=rel.x*64+rel.z;
                    if(columns.containsKey(index))continue;
                    long[] points=random.nextInt(5)==0?new long[0]:new long[]{FullDataPointUtil.encode(random.nextInt(4),8,24,(byte)0,(byte)0),0,FullDataPointUtil.encode(random.nextInt(4),16,0,(byte)15,(byte)7),FullDataPointUtil.encode(random.nextInt(4),12,8,(byte)3,(byte)6)};
                    columns.put(index,points);source.dataPoints[index]=new LongArrayList(points);
                }
                var getter=new TintWithoutLevelOverrider();Config.Client.Advanced.Graphics.Quality.lodBiomeBlending.setWithoutFiringEvents(0);
                for(int i=0;i<4;i++)if((cachedMask&(1<<i))!=0)cache.put(com.seibel.distanthorizons.core.dataObjects.BlockBiomeWrapperPair.get(BlockStateWrapper.AIR,biomes[i]),colors[i]);
                Config.Client.Advanced.Graphics.Quality.lodBiomeBlending.setWithoutFiringEvents(radius);getter.update(biomes[0],BlockStateWrapper.AIR,source,level);
                var mutable=new DhBlockPosMutable(x,y,z);int result=getter.tryGetBlockTint(mutable);
                out.writeLong(pos);out.writeInt(x);out.writeInt(y);out.writeInt(z);out.writeInt(-64);out.writeInt(radius);out.writeInt(cachedMask);
                for(int i=0;i<4;i++){out.writeUTF(biomes[i].getSerialString());out.writeInt(colors[i]);}
                out.writeInt(columns.size());for(var entry:columns.entrySet()){out.writeInt(entry.getKey());out.writeInt(entry.getValue().length);for(long p:entry.getValue())out.writeLong(p);}
                out.writeInt(result);out.writeInt(mutable.getX());out.writeInt(mutable.getZ());source.close();
            }
        }
        System.out.println("Actual Frozen cached tint cases="+cases);
    }
}
