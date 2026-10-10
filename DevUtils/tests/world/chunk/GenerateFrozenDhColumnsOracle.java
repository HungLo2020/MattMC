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

/** Records actual untouched Frozen column production; expected algorithms are not copied. */
public final class GenerateFrozenDhColumnsOracle {
    private static final Class<?>[] PINNED = {BlockStateWrapper.class, BiomeWrapper.class,
        ChunkWrapper.class, ProtoChunk.class, DhLightingEngine.class, LodDataBuilder.class,
        FullDataSourceV2.class, FullDataPointIdMap.class, FullDataPointUtil.class,
        WrapperFactory.class, ApiEventInjector.class};
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
        var wrapper=new ChunkWrapper(chunk,level);wrapper.getWorldBlockLightPosList();
        var nearby=new ArrayList<IChunkWrapper>();nearby.add(wrapper);
        DhLightingEngine.INSTANCE.lightChunk(wrapper,nearby,15);
        out.writeUTF("saved-"+index+"-biomes-"+pattern);out.writeInt(chunk.getPos().x);out.writeInt(chunk.getPos().z);
        out.writeInt(chunk.getMinY());out.writeInt(chunk.getHeight());out.writeInt(wrapper.getMinNonEmptyHeight());
        out.writeInt(wrapper.getMaxNonEmptyHeight());
        for(var section:chunk.getSections())for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++)
            out.writeInt(Block.getId(section.getBlockState(x,y,z)));
        for(var section:chunk.getSections())for(int y=0;y<4;y++)for(int z=0;z<4;z++)for(int x=0;x<4;x++)
            out.writeInt(registry.getId(section.getNoiseBiome(x,y,z).value()));
        for(int y=chunk.getMinY();y<=chunk.getMaxY();y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++)
            out.writeByte(wrapper.getDhBlockLight(x,y,z)|(wrapper.getDhSkyLight(x,y,z)<<4));
        for(int x=0;x<16;x++)for(int z=0;z<16;z++) {
            out.writeInt(wrapper.getSolidHeightMapValue(x,z));out.writeInt(wrapper.getLightBlockingHeightMapValue(x,z));
        }
        try(var source=LodDataBuilder.createFromChunk(level,wrapper)) {
            if(source==null)throw new IllegalStateException("Frozen column builder declined "+index);
            out.writeLong(source.getPos());out.writeBoolean(source.isEmpty);out.writeBoolean(source.applyToParent);
            out.writeInt(source.hashCode());out.writeInt(source.mapping.hashCode());out.writeInt(source.mapping.size());
            for(int id=0;id<source.mapping.size();id++) {
                var biome=(BiomeWrapper)source.mapping.getBiomeWrapper(id);var block=(BlockStateWrapper)source.mapping.getBlockStateWrapper(id);
                out.writeInt(registry.getId(biome.biome.value()));
                out.writeInt(block.blockState==null?0:Block.getId(block.blockState));
                out.writeUTF(biome.getSerialString());out.writeUTF(block.getSerialString());
            }
            for(int x=0;x<FullDataSourceV2.WIDTH;x++)for(int z=0;z<FullDataSourceV2.WIDTH;z++) {
                int column=FullDataSourceV2.relativePosToIndex(x,z);var data=source.getColumnAtRelPos(x,z);
                out.writeByte(source.columnGenerationSteps.getByte(column));out.writeByte(source.columnWorldCompressionMode.getByte(column));
                out.writeInt(data==null?-1:data.size());if(data!=null)for(int p=0;p<data.size();p++)out.writeLong(data.getLong(p));
            }
            System.out.println("Recorded saved="+index+" biomes="+pattern+" materials="+source.mapping.size()+" hash="+source.hashCode());
        }
    }
    public static void main(String[] args) throws Exception {
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
                out.writeInt(0x44484331);out.writeInt(PINNED.length);
                for(var type:PINNED){out.writeUTF(type.getName());out.writeUTF(hash(type));}
                out.writeUTF(digest(saved));out.writeUTF("Actual Frozen production builder over saved blocks and declared uniform/varied vanilla biome patterns; actual production lighting. Supplemental CPU fixture, not saved-biome/runtime parity.");
                out.writeInt(registry.size());
                for(int id=0;id<registry.size();id++) {
                    var wrapper=BiomeWrapper.getBiomeWrapper(registry.get(id).orElseThrow(),level);
                    out.writeUTF(wrapper.getSerialString());out.writeInt(wrapper.hashCode());
                }
                out.writeInt(Block.BLOCK_STATE_REGISTRY.size());
                for(var state:Block.BLOCK_STATE_REGISTRY) {
                    var wrapper=BlockStateWrapper.fromBlockState(state,level);out.writeUTF(wrapper.getSerialString());out.writeInt(wrapper.hashCode());
                }
                out.writeInt(records.size()*2);
                for(int index=0;index<records.size();index++)for(int pattern=0;pattern<2;pattern++)
                    runCase(out,records.get(index).getAsJsonObject(),index,pattern,factory,registry,biomes,level);
            }
        }
    }
}
