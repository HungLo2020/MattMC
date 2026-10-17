import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import com.seibel.distanthorizons.common.wrappers.WrapperFactory;
import com.seibel.distanthorizons.common.wrappers.block.*;
import com.seibel.distanthorizons.common.wrappers.chunk.ChunkWrapper;
import com.seibel.distanthorizons.core.dataObjects.fullData.*;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.dataObjects.transformers.LodDataBuilder;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.generation.DhLightingEngine;
import com.seibel.distanthorizons.core.util.FullDataPointUtil;
import com.seibel.distanthorizons.core.wrapperInterfaces.IWrapperFactory;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftSharedWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.chunk.IChunkWrapper;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import com.seibel.distanthorizons.coreapi.DependencyInjection.ApiEventInjector;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.LongStream;
import net.minecraft.SharedConstants;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.ticks.ProtoChunkTicks;

/** Records actual untouched Frozen DH hash production; expected hash policy is not copied. */
public final class GenerateFrozenDhChunkHashOracle {
    private static final Class<?>[] PINNED = {BlockStateWrapper.class, BiomeWrapper.class,
        ChunkWrapper.class, ProtoChunk.class, DhLightingEngine.class, LodDataBuilder.class,
        FullDataSourceV2.class, FullDataPointIdMap.class, FullDataPointUtil.class,
        WrapperFactory.class, ApiEventInjector.class, IChunkWrapper.class, LevelChunkSection.class, PalettedContainer.class, DhBlockPos.class};
    private static String digest(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
    private static String hash(Class<?> type) throws Exception {
        try (var stream=type.getResourceAsStream(type.getSimpleName()+".class")) {
            return digest(stream.readAllBytes());
        }
    }
    private static ILevelWrapper registryOnlyLevel(RegistryAccess access) throws Exception {
        Class<?> type=Class.forName("sun.misc.Unsafe");Field f=type.getDeclaredField("theUnsafe");f.setAccessible(true);
        Object unsafe=f.get(null);
        Level level=(Level)type.getMethod("allocateInstance",Class.class).invoke(unsafe,ServerLevel.class);
        Field registry=Level.class.getDeclaredField("registryAccess");registry.setAccessible(true);registry.set(level,access);
        return (ILevelWrapper)Proxy.newProxyInstance(ILevelWrapper.class.getClassLoader(),new Class<?>[]{ILevelWrapper.class},
            (proxy,method,args)->switch(method.getName()) {
                case "getWrappedMcObject" -> level;
                case "toString" -> "Frozen column fixture registry-only level";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy==args[0];
                default -> throw new UnsupportedOperationException("Unexpected fixture callback: "+method);
            });
    }
    private static ProtoChunk recorded(JsonObject record,ChunkPos position,PalettedContainerFactory factory,
                                       List<Holder<Biome>> biomes,int pattern) {
        int min=record.get("min_y").getAsInt(),height=record.get("height").getAsInt();
        var sections=new LevelChunkSection[height/16];
        for(var element:record.getAsJsonArray("sections")) {
            var item=element.getAsJsonObject();var palette=new ArrayList<BlockState>();
            for(var state:item.getAsJsonArray("palette"))palette.add(BlockState.CODEC.parse(JsonOps.INSTANCE,state).result().orElseThrow());
            var data=item.getAsJsonArray("data");long[] words=new long[data.size()];
            for(int i=0;i<words.length;i++)words[i]=data.get(i).getAsLong();
            var packed=new PalettedContainerRO.PackedData<>(palette,words.length==0?Optional.empty():Optional.of(LongStream.of(words)));
            var states=PalettedContainer.unpack(factory.blockStatesStrategy(),packed).result().orElseThrow();
            var biomeData=factory.createForBiomes();int s=item.get("y").getAsInt()-min/16;
            for(int y=0;y<4;y++)for(int z=0;z<4;z++)for(int x=0;x<4;x++)
                biomeData.getAndSetUnchecked(x,y,z,biomes.get(pattern==0?0:(x+z*3+y+s)%biomes.size()));
            sections[s]=new LevelChunkSection(states,biomeData);
        }
        for(var section:sections)if(section==null)throw new IllegalArgumentException("Incomplete saved sections");
        var result=new ProtoChunk(position,UpgradeData.EMPTY,sections,new ProtoChunkTicks<>(),new ProtoChunkTicks<>(),
            LevelHeightAccessor.create(min,height),factory,null);
        result.setPersistedStatus(net.minecraft.world.level.chunk.status.ChunkStatus.BIOMES);
        return result;
    }
    private static void runCase(DataOutputStream out,JsonObject record,int index,int pattern,
                                PalettedContainerFactory factory,Registry<Biome> registry,
                                List<Holder<Biome>> biomes,ILevelWrapper level) throws Exception {
        var chunk=recorded(record,new ChunkPos(-37+index,41-index),factory,biomes,pattern);
        var wrapper=new ChunkWrapper(chunk,level);
        for(int phase=0;phase<3;phase++) {
            var sections=chunk.getSections();int middle=sections.length/2;
            if(phase==1) {
                sections[middle].getStates().getAndSetUnchecked(2,6,4,net.minecraft.world.level.block.Blocks.GLOWSTONE.defaultBlockState());
                @SuppressWarnings("unchecked") var data=(PalettedContainer<Holder<Biome>>)sections[middle].getBiomes();
                for(int y=0;y<4;y++)for(int z=0;z<4;z++)for(int x=0;x<4;x++)data.getAndSetUnchecked(x,y,z,biomes.get(1));
            }
            if(phase==2) {
                Field empty=LevelChunkSection.class.getDeclaredField("nonEmptyBlockCount");empty.setAccessible(true);empty.setShort(sections[middle],(short)0);
                var lights=wrapper.getWorldBlockLightPosList();Collections.reverse(lights);
                if(!lights.isEmpty())lights.add(lights.get(0));
                lights.add(new com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos(chunk.getPos().getMinBlockX()+15,chunk.getMaxY()+1,chunk.getPos().getMinBlockZ()+15));
            }
            int expected=wrapper.getBlockBiomeHashCode();
            out.writeUTF("saved-"+index+"-biomes-"+pattern+"-phase-"+phase);
            out.writeInt(chunk.getMinY());out.writeInt(chunk.getHeight());
            out.writeInt(wrapper.getMinNonEmptyHeight());out.writeInt(wrapper.getMaxNonEmptyHeight());
            for(var section:sections)out.writeBoolean(section.hasOnlyAir());
            for(var section:sections)for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++)out.writeInt(Block.getId(section.getBlockState(x,y,z)));
            for(var section:sections)for(int y=0;y<4;y++)for(int z=0;z<4;z++)for(int x=0;x<4;x++)out.writeInt(registry.getId(section.getNoiseBiome(x,y,z).value()));
            for(int x=0;x<16;x++)for(int z=0;z<16;z++){out.writeInt(wrapper.getSolidHeightMapValue(x,z));out.writeInt(wrapper.getLightBlockingHeightMapValue(x,z));}
            var lights=wrapper.getWorldBlockLightPosList();out.writeInt(lights.size());
            for(var p:lights){out.writeInt(p.getX()&15);out.writeInt(p.getY());out.writeInt(p.getZ()&15);}
            out.writeInt(expected);
            System.out.println("Recorded hash saved="+index+" biomes="+pattern+" phase="+phase+" hash="+expected);
        }
    }
    public static void main(String[] args) throws Exception {
        if(args.length==2 && args[0].equals("--pins")) {
            var pins=new JsonObject();for(var type:PINNED)pins.addProperty(type.getName(),hash(type));
            Files.writeString(Path.of(args[1]),pins.toString());return;
        }
        if(args.length!=3)throw new IllegalArgumentException("output saved-chunks-json class-pin-json");
        var pins=JsonParser.parseString(Files.readString(Path.of(args[2]))).getAsJsonObject();
        for(var type:PINNED)if(!hash(type).equals(pins.get(type.getName()).getAsString()))
            throw new IllegalStateException("Not pinned Frozen: "+type);
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
        SingletonInjector.INSTANCE.bind(IWrapperFactory.class,WrapperFactory.INSTANCE);
        // Configuration initialization needs environment data, not a live client.
        // Unexpected callbacks refuse the fixture rather than emulating policy.
        File install=Path.of(System.getProperty("mattmc.fixture.installation_dir")).toFile();
        var shared=(IMinecraftSharedWrapper)Proxy.newProxyInstance(IMinecraftSharedWrapper.class.getClassLoader(),
            new Class<?>[]{IMinecraftSharedWrapper.class},(proxy,method,input)->switch(method.getName()) {
                case "getInstallationDirectory" -> install;
                case "isDedicatedServer" -> false;
                case "getDelayedSetupComplete" -> true;
                default -> throw new UnsupportedOperationException("Unexpected fixture environment callback: "+method);
            });
        SingletonInjector.INSTANCE.bind(IMinecraftSharedWrapper.class,shared);
        try(var resources=new net.minecraft.server.packs.resources.MultiPackResourceManager(net.minecraft.server.packs.PackType.SERVER_DATA,
                List.of(net.minecraft.server.packs.repository.ServerPacksSource.createVanillaPackSource()))) {
            var layers=net.minecraft.server.RegistryLayer.createRegistryAccess();
            var tags=net.minecraft.tags.TagLoader.loadTagsForExistingRegistries(resources,layers.getLayer(net.minecraft.server.RegistryLayer.STATIC));
            var lookups=net.minecraft.tags.TagLoader.buildUpdatedLookups(layers.getAccessForLoading(net.minecraft.server.RegistryLayer.WORLDGEN),tags);
            var worldgen=net.minecraft.resources.RegistryDataLoader.load(resources,lookups,net.minecraft.resources.RegistryDataLoader.WORLDGEN_REGISTRIES);
            var access=layers.replaceFrom(net.minecraft.server.RegistryLayer.WORLDGEN,worldgen).compositeAccess();
            var registry=access.lookupOrThrow(Registries.BIOME);var factory=PalettedContainerFactory.create(access);var level=registryOnlyLevel(access);
            List<Holder<Biome>> biomes=List.of(registry.getOrThrow(Biomes.PLAINS),registry.getOrThrow(Biomes.OCEAN),
                registry.getOrThrow(Biomes.DESERT),registry.getOrThrow(Biomes.SAVANNA));
            byte[] saved=Files.readAllBytes(Path.of(args[1]));var records=JsonParser.parseString(new String(saved,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("chunks");
            try(var out=new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(Path.of(args[0]))))) {
                out.writeInt(0x44484831);out.writeInt(PINNED.length);
                for(var type:PINNED){out.writeUTF(type.getName());out.writeUTF(hash(type));}
                out.writeUTF(digest(saved));out.writeUTF("Actual Frozen getBlockBiomeHashCode over saved blocks and declared biome patterns; cached heights/emitters, raw palette edits, stale empty counters and escaped emitter reorder/duplicate/top row. Supplemental CPU fixture, not runtime parity.");
                out.writeInt(registry.size());
                for(int id=0;id<registry.size();id++) {
                    var wrapper=BiomeWrapper.getBiomeWrapper(registry.get(id).orElseThrow(),level);
                    out.writeUTF(wrapper.getSerialString());out.writeInt(wrapper.hashCode());
                }
                out.writeInt(Block.BLOCK_STATE_REGISTRY.size());
                for(var state:Block.BLOCK_STATE_REGISTRY) {
                    var wrapper=BlockStateWrapper.fromBlockState(state,level);out.writeUTF(wrapper.getSerialString());out.writeInt(wrapper.hashCode());
                }
                out.writeInt(records.size()*6);
                for(int index=0;index<records.size();index++)for(int pattern=0;pattern<2;pattern++)
                    runCase(out,records.get(index).getAsJsonObject(),index,pattern,factory,registry,biomes,level);
            }
        }
    }
}
