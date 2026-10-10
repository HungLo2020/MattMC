import com.seibel.distanthorizons.common.wrappers.block.*;
import com.seibel.distanthorizons.core.dataObjects.BlockBiomeWrapperPair;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPosMutable;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Actual Frozen mutable cache-key histories; uses the companion recorder's class pins. */
public final class GenerateFrozenDhTintKeyOracle {
    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        var pins=new com.google.gson.JsonObject();for(var c:GenerateFrozenDhTintOracle.PINNED)pins.addProperty(c.getName(),GenerateFrozenDhTintOracle.hash(c));
        if(!pins.equals(com.google.gson.JsonParser.parseString(Files.readString(Path.of(args[1])))))throw new IllegalStateException("Frozen compiled classes changed");
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector.INSTANCE.bind(com.seibel.distanthorizons.core.wrapperInterfaces.IWrapperFactory.class,com.seibel.distanthorizons.common.wrappers.WrapperFactory.INSTANCE);
        Config.Client.Advanced.Graphics.Quality.lodBiomeBlending.setWithoutFiringEvents(0);
        var field=AbstractDhTintGetter.class.getDeclaredField("COLOR_BY_BLOCK_BIOME_PAIR");field.setAccessible(true);
        var cache=(Map<BlockBiomeWrapperPair,Integer>)field.get(null);
        var serial=BiomeWrapper.class.getDeclaredField("serialString");serial.setAccessible(true);
        var hashField=BiomeWrapper.class.getDeclaredField("hashCode");hashField.setAccessible(true);
        var level=(IClientLevelWrapper)Proxy.newProxyInstance(IClientLevelWrapper.class.getClassLoader(),new Class<?>[]{IClientLevelWrapper.class},(p,m,a)->{throw new AssertionError(m.toString());});
        var source=FullDataSourceV2.createEmpty(6);
        try(var out=new DataOutputStream(Files.newOutputStream(Path.of(args[0])))) {
            out.writeInt(0x44544b31);out.writeInt(32);
            for(int test=0;test<32;test++) {
                String a="test:key_a_"+test,c="test:key_c_"+test;int hash=Objects.hash(a);
                var biomes=new BiomeWrapper[]{GenerateFrozenDhTintOracle.biome(a),GenerateFrozenDhTintOracle.biome(a),GenerateFrozenDhTintOracle.biome(c)};
                for(var b:biomes)hashField.setInt(b,hash);
                out.writeUTF(a);out.writeUTF(c);out.writeInt(hash);
                int[][] actions={{0,0,0},{1,0,0xff1975e3},{0,1,0},{2,0,0},{0,0,0},{0,1,0},{0,2,0},{1,1,0xff3e9721},{0,1,0},{2,1,0},{0,1,0},{0,0,0},{1,2,0xffea7039},{0,0,0},{0,2,0}};
                out.writeInt(actions.length);
                for(var op:actions) {
                    out.writeInt(op[0]);out.writeInt(op[1]);out.writeInt(op[2]);
                    if(op[0]==2){serial.set(biomes[op[1]],c);}
                    else if(op[0]==1)cache.put(BlockBiomeWrapperPair.get(BlockStateWrapper.AIR,biomes[op[1]]),op[2]);
                    var getter=new TintWithoutLevelOverrider();getter.update(biomes[op[1]],BlockStateWrapper.AIR,source,level);
                    out.writeInt(getter.tryGetBlockTint(new DhBlockPosMutable(0,0,0)));
                }
            }
        }
        System.out.println("Actual Frozen mutable-key histories=32 operations=480");
    }
}
