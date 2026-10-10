import com.google.gson.JsonParser;
import com.seibel.distanthorizons.common.wrappers.block.BlockStateWrapper;
import com.seibel.distanthorizons.common.wrappers.chunk.ChunkWrapper;
import com.seibel.distanthorizons.core.generation.DhLightingEngine;
import com.seibel.distanthorizons.core.wrapperInterfaces.chunk.IChunkWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.chunk.ChunkLightStorage;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.ticks.ProtoChunkTicks;

/** Actual Frozen palette writes/counters/lighting; no expected algorithm copied. */
public final class GenerateFrozenDhStaleCountersOracle {
    public static void main(String[] args) throws Exception {
        boolean mutableEscape=args.length>2 && Boolean.parseBoolean(args[2]);
        var pins=JsonParser.parseString(Files.readString(Path.of(args[1]))).getAsJsonObject();
        Class<?>[] classes=mutableEscape
            ? new Class<?>[]{BlockStateWrapper.class,ChunkWrapper.class,ProtoChunk.class,LevelChunkSection.class,ChunkAccess.class,DhLightingEngine.class,ChunkLightStorage.class,ChunkLightStorage.LightSection.class}
            : new Class<?>[]{BlockStateWrapper.class,ChunkWrapper.class,ProtoChunk.class,LevelChunkSection.class,ChunkAccess.class,DhLightingEngine.class};
        for(var c:classes)try(var stream=c.getResourceAsStream("/"+c.getName().replace('.','/')+".class")) {
            String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes()));
            if(!hash.equals(pins.get(c.getName()).getAsString()))throw new IllegalStateException("Not pinned Frozen: "+c);
        }
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
        var method=GenerateFrozenDhLightingOracle.class.getDeclaredMethod("registryOnlyLevel");method.setAccessible(true);
        var level=(com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper)method.invoke(null);
        var light=DhLightingEngine.class.getDeclaredMethod("lightChunk",IChunkWrapper.class,ArrayList.class,int.class,boolean.class,boolean.class);light.setAccessible(true);
        try(var out=new DataOutputStream(Files.newOutputStream(Path.of(args[0])))) {
            out.writeInt(mutableEscape ? 0x44484531 : 0x44485331);out.writeInt(classes.length);
            for(var c:classes){out.writeUTF(c.getName());out.writeUTF(pins.get(c.getName()).getAsString());}
            out.writeInt(3);
            for(int test=0;test<3;test++) {
                var sections=new LevelChunkSection[2];
                for(int s=0;s<2;s++)sections[s]=new LevelChunkSection(new PalettedContainer<>(Blocks.AIR.defaultBlockState(),Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY)),null);
                sections[0].getStates().set(1,4,2,test==1?Blocks.STONE.defaultBlockState():Blocks.GLOWSTONE.defaultBlockState());
                if(test==2)sections[0].recalcBlockCounts();
                var chunk=new ProtoChunk(new ChunkPos(-37,41),UpgradeData.EMPTY,sections,new ProtoChunkTicks<>(),new ProtoChunkTicks<>(),LevelHeightAccessor.create(-64,32),null,null);
                var wrapper=new ChunkWrapper(chunk,level);var nearby=new ArrayList<IChunkWrapper>();nearby.add(wrapper);
                out.writeInt(test);out.writeBoolean(sections[0].hasOnlyAir());
                int phases=mutableEscape ? 10 : 3;
                out.writeInt(wrapper.getWorldBlockLightPosList().size());out.writeInt(phases);
                for(int phase=0;phase<phases;phase++) {
                    boolean escape=mutableEscape && phase==2;
                    if(mutableEscape)out.writeBoolean(escape);
                    if(escape)wrapper.setDhBlockLight(0,-64,0,wrapper.getDhBlockLight(0,-64,0));
                    if(phase%2==1)sections[0].getStates().set(1,4,2,Blocks.AIR.defaultBlockState());
                    else if(phase>0)sections[0].getStates().set(1,4,2,Blocks.GLOWSTONE.defaultBlockState());
                    out.writeInt((int)light.invoke(DhLightingEngine.INSTANCE,wrapper,nearby,15,true,true));
                    for(int y=-64;y<-32;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++)
                        out.writeByte(wrapper.getDhBlockLight(x,y,z)|(wrapper.getDhSkyLight(x,y,z)<<4));
                }
                System.out.println("Recorded stale-counter case "+test);
            }
        }
    }
}
