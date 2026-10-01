package net.minecraft.world.level.lighting;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.ticks.ProtoChunkTicks;

/** Real packed sections, loaded without a world/server/renderer. */
final class SkyLightSourcesFixtures {
    static final String[] PATTERNS={"solid","water","leaves","glass","empty","shapes","global","islands"};
    static ProtoChunk withSections(LevelChunkSection[] sections,int minY,int height) {
        return new ProtoChunk(new ChunkPos(137,-83),UpgradeData.EMPTY,sections,new ProtoChunkTicks<>(),new ProtoChunkTicks<>(),LevelHeightAccessor.create(minY,height),null,null);
    }
    static ProtoChunk chunk(String pattern,int minY,int height,long seed) {
        var random=new Random(seed);var sections=new LevelChunkSection[height/16];
        BlockState[] shapes={Blocks.OAK_SLAB.defaultBlockState(),Blocks.OAK_SLAB.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.SLAB_TYPE,net.minecraft.world.level.block.state.properties.SlabType.TOP),
            Blocks.OAK_STAIRS.defaultBlockState(),Blocks.GLASS.defaultBlockState(),Blocks.AIR.defaultBlockState(),Blocks.CAVE_AIR.defaultBlockState(),Blocks.VOID_AIR.defaultBlockState(),Blocks.SHORT_GRASS.defaultBlockState()};
        for(int s=0;s<sections.length;s++) {
            var states=new PalettedContainer<>(Blocks.AIR.defaultBlockState(),Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY));
            for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++) {
                BlockState state=switch(pattern) {
                    case "solid" -> Blocks.STONE.defaultBlockState();
                    case "water" -> Blocks.WATER.defaultBlockState();
                    case "leaves" -> Blocks.OAK_LEAVES.defaultBlockState();
                    case "glass" -> Blocks.GLASS.defaultBlockState();
                    case "empty" -> Blocks.AIR.defaultBlockState();
                    case "shapes" -> shapes[random.nextInt(shapes.length)];
                    case "global" -> Block.BLOCK_STATE_REGISTRY.byId(random.nextInt(Block.BLOCK_STATE_REGISTRY.size()));
                    case "islands" -> s*16+y<32+((x+z)&7) && (x+z+seed)%3!=0?Blocks.END_STONE.defaultBlockState():Blocks.AIR.defaultBlockState();
                    default -> throw new IllegalArgumentException(pattern);
                };
                if(!state.is(Blocks.AIR))states.set(x,y,z,state);
            }
            sections[s]=new LevelChunkSection(states,null);
        }
        return withSections(sections,minY,height);
    }
    static List<com.google.gson.JsonObject> records() {
        try(var stream=SkyLightSourcesFixtures.class.getResourceAsStream("/worldgen/heightmap/chunks.json")) {
            var root=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(Objects.requireNonNull(stream),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            var records=new ArrayList<com.google.gson.JsonObject>();
            for(var record:root.getAsJsonArray("chunks"))records.add(record.getAsJsonObject());
            return records;
        }catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}
    }
    static ProtoChunk recorded(com.google.gson.JsonObject record) {
        int minY=record.get("min_y").getAsInt(),height=record.get("height").getAsInt();
        var sections=new LevelChunkSection[height/16];
        for(var item:record.getAsJsonArray("sections")) {
            var section=item.getAsJsonObject();var palette=new ArrayList<BlockState>();
            for(var state:section.getAsJsonArray("palette"))palette.add(BlockState.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,state).result().orElseThrow());
            var data=section.getAsJsonArray("data");long[] words=new long[data.size()];for(int i=0;i<words.length;i++)words[i]=data.get(i).getAsLong();
            var packed=new PalettedContainerRO.PackedData<>(palette,words.length==0?Optional.empty():Optional.of(java.util.stream.LongStream.of(words)));
            sections[section.get("y").getAsInt()-minY/16]=new LevelChunkSection(PalettedContainer.unpack(Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY),packed).result().orElseThrow(),null);
        }
        for(var section:sections)if(section==null)throw new IllegalArgumentException("Incomplete fixture");
        return withSections(sections,minY,height);
    }
    static net.minecraft.util.BitStorage storage(ChunkSkyLightSources sources) {
        try { var field=ChunkSkyLightSources.class.getDeclaredField("heightmap");field.setAccessible(true);return (net.minecraft.util.BitStorage)field.get(sources); }
        catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    static void randomize(ChunkSkyLightSources sources,long seed) {
        var random=new Random(seed);var words=storage(sources).getRaw();for(int i=0;i<words.length;i++)words[i]=random.nextLong();
    }
}
