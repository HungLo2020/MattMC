package net.minecraft.world.level.levelgen;

import com.seibel.distanthorizons.api.enums.config.EDhApiWorldCompressionMode;
import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiWorldGenerationStep;
import com.seibel.distanthorizons.common.wrappers.WrapperFactory;
import com.seibel.distanthorizons.common.wrappers.block.*;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.util.FullDataPointUtil;
import com.seibel.distanthorizons.core.wrapperInterfaces.IWrapperFactory;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftSharedWrapper;
import java.lang.reflect.*;
import java.nio.file.Path;
import java.util.Objects;

/** Identical compiled source lifecycle/update workload; supplemental CPU evidence. */
public final class DhSourceVerification {
    private static BiomeWrapper biome() throws Exception {
        var ctor=BiomeWrapper.class.getDeclaredConstructors()[0];ctor.setAccessible(true);
        var value=(BiomeWrapper)ctor.newInstance(null,null);
        var text=BiomeWrapper.class.getDeclaredField("serialString");text.setAccessible(true);text.set(value,"test:source_workflow");
        var hash=BiomeWrapper.class.getDeclaredField("hashCode");hash.setAccessible(true);hash.setInt(value,Objects.hash("test:source_workflow"));
        return value;
    }
    /** CPU-only registered stone identity; no world/contextual color callbacks. */
    private static BlockStateWrapper stone() throws Exception {
        var ctor=BlockStateWrapper.class.getDeclaredConstructors()[0];ctor.setAccessible(true);
        var value=(BlockStateWrapper)ctor.newInstance(null,null);
        for(var entry:java.util.Map.of("blockState",net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),
            "serialString","minecraft:stone_STATE_", "hashCode",Objects.hash("minecraft:stone_STATE_"),"opacity",16).entrySet()) {
            var field=BlockStateWrapper.class.getDeclaredField(entry.getKey());field.setAccessible(true);field.set(value,entry.getValue());
        }
        return value;
    }
    private static long run(String mode, FullDataSourceV2 input, FullDataSourceV2 target, it.unimi.dsi.fastutil.longs.LongList changedColumn, int rounds) throws Exception {
        long checksum=0;
        for(int i=0;i<rounds;i++) {
            if(mode.equals("create")) {
                try(var source=FullDataSourceV2.createEmpty(6)) { checksum+=source.hashCode(); }
            } else if(mode.equals("downsample")) {
                // A fresh child each round, so every round writes all 4096 cells.
                try(var child=FullDataSourceV2.createEmpty(6)) { checksum+=child.updateFromDataSource(input)?1:0; checksum+=child.hashCode(); }
            } else {
                // Existing authoritative points change before each complete update.
                changedColumn.set(0,FullDataPointUtil.encode(0,8,24,(byte)(i&15),(byte)15));
                checksum+=target.updateFromDataSource(input)?1:0;
                checksum+=target.hashCode();
            }
        }
        return checksum;
    }
    public static void main(String[] args) throws Exception {
        String mode=args[2];
        if(!java.util.Set.of("create","merge","cull","parent","downsample","generation","render","tintcheck").contains(mode))throw new IllegalArgumentException(mode);
        // parent: the 64x64 block-detail input reduces into its parent's quadrant.
        // downsample: a parent-detail input projects onto a block-detail child.
        long inputPos=mode.equals("downsample") ? com.seibel.distanthorizons.core.pos.DhSectionPos.getParentPos(6) : 6;
        long targetPos=mode.equals("parent") ? com.seibel.distanthorizons.core.pos.DhSectionPos.getParentPos(6) : 6;
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        SingletonInjector.INSTANCE.bind(IWrapperFactory.class,WrapperFactory.INSTANCE);
        var shared=(IMinecraftSharedWrapper)Proxy.newProxyInstance(IMinecraftSharedWrapper.class.getClassLoader(),new Class<?>[]{IMinecraftSharedWrapper.class},(p,m,a)->switch(m.getName()){
            case "getInstallationDirectory"->Path.of("run").toFile();case "isDedicatedServer"->false;case "getDelayedSetupComplete"->true;default->throw new UnsupportedOperationException(m.toString());});
        SingletonInjector.INSTANCE.bind(IMinecraftSharedWrapper.class,shared);
        if(mode.equals("generation")){DhSourceVerificationGeneration.run(Integer.parseInt(args[0]),Integer.parseInt(args[1]));return;}
        if(mode.equals("tintcheck")){DhSourceVerificationRender.tintCheck();return;}
        if(mode.equals("render")){DhSourceVerificationRender.run(Integer.parseInt(args[0]),Integer.parseInt(args[1]));return;}
        Config.Common.LodBuilding.worldCompression.setWithoutFiringEvents(mode.equals("cull")
            ? EDhApiWorldCompressionMode.VISUALLY_EQUAL : EDhApiWorldCompressionMode.MERGE_SAME_BLOCKS);
        try(var input=FullDataSourceV2.createEmpty(inputPos);var target=FullDataSourceV2.createEmpty(targetPos)) {
            input.mapping.addIfNotPresentAndGetId(biome(),mode.equals("cull")
                ? stone()
                : BlockStateWrapper.AIR);
            // Bind Frozen/Current array and return-type differences once, outside timing.
            var field=FullDataSourceV2.class.getDeclaredField("dataPoints");field.setAccessible(true);
            var columns=(Object[])field.get(input);
            var getter=FullDataSourceV2.class.getDeclaredMethod("getColumnAtRelPos",int.class,int.class);
            for(int x=0;x<64;x++)for(int z=0;z<64;z++)if(columns[x*64+z]==null)columns[x*64+z]=getter.invoke(input,x,z);
            var generation=(it.unimi.dsi.fastutil.bytes.ByteList)FullDataSourceV2.class.getField("columnGenerationSteps").get(input);
            for(int x=0;x<64;x++)for(int z=0;z<64;z++) {
                var list=(it.unimi.dsi.fastutil.longs.LongList)columns[x*64+z];
                for(int j=3;j>=0;j--)list.add(FullDataPointUtil.encode(0,8,j*8,(byte)5,(byte)15));
                generation.set(x*64+z,EDhApiWorldGenerationStep.LIGHT.value);
            }
            input.isEmpty=false;
            int rounds=Integer.parseInt(args[0]),warm=Integer.parseInt(args[1]);
            for(int i=0;i<5;i++)run(mode,input,target,(it.unimi.dsi.fastutil.longs.LongList)columns[0],warm);
            if(mode.equals("cull")&&((it.unimi.dsi.fastutil.longs.LongList)getter.invoke(target,1,1)).size()>=4)
                throw new AssertionError("Culling workload must remove actual hidden points");
            long start=System.nanoTime();long checksum=run(mode,input,target,(it.unimi.dsi.fastutil.longs.LongList)columns[0],rounds);long elapsed=System.nanoTime()-start;
            System.out.println("DH_SOURCE "+elapsed+" "+checksum);
        }
    }
}
