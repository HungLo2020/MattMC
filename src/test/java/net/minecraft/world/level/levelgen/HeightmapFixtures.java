package net.minecraft.world.level.levelgen;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.ticks.ProtoChunkTicks;

/** Immutable real packed sections shared by independent test chunks. */
final class HeightmapFixtures {
    static final String[] WORKLOADS = {"solid", "overworld", "nether", "end", "water", "leaves", "empty", "global"};
    private static List<com.google.gson.JsonObject> saved;

    static List<com.google.gson.JsonObject> saved() {
        if (saved != null) return saved;
        try (var stream = HeightmapFixtures.class.getResourceAsStream("/worldgen/heightmap/chunks.json")) {
            if (stream == null) throw new IllegalStateException("Missing heightmap corpus");
            var json = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            var values = new ArrayList<com.google.gson.JsonObject>();
            for (var entry : json.getAsJsonArray("chunks")) values.add(entry.getAsJsonObject());
            saved = List.copyOf(values); return saved;
        } catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
    }

    static ProtoChunk recorded(com.google.gson.JsonObject record) {
        int minY = record.get("min_y").getAsInt(), height = record.get("height").getAsInt();
        var sections = new LevelChunkSection[height/16];
        var strategy = Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY);
        for (var item : record.getAsJsonArray("sections")) {
            var section = item.getAsJsonObject();
            var palette = new ArrayList<BlockState>();
            for (var state : section.getAsJsonArray("palette"))
                palette.add(BlockState.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,state).result().orElseThrow());
            var data = section.getAsJsonArray("data"); long[] words = new long[data.size()];
            for(int i=0;i<words.length;i++)words[i]=data.get(i).getAsLong();
            var packed = new PalettedContainerRO.PackedData<>(palette,words.length==0?Optional.empty():Optional.of(java.util.stream.LongStream.of(words)));
            var storage = PalettedContainer.unpack(strategy,packed).result().orElseThrow();
            sections[section.get("y").getAsInt()-minY/16] = new LevelChunkSection(storage,null);
        }
        for (var section : sections) if(section==null)throw new IllegalStateException("Incomplete saved chunk");
        return withSections(sections,minY,height);
    }
    static ProtoChunk chunk(String pattern, int minY, int height, long seed) {
        var random = new Random(seed);
        var sections = new LevelChunkSection[height / 16];
        BlockState[] mixed = {Blocks.STONE.defaultBlockState(), Blocks.DIRT.defaultBlockState(),
            Blocks.OAK_LEAVES.defaultBlockState(), Blocks.WATER.defaultBlockState(),
            Blocks.LAVA.defaultBlockState(), Blocks.SHORT_GRASS.defaultBlockState(),
            Blocks.CAVE_AIR.defaultBlockState(), Blocks.VOID_AIR.defaultBlockState(), Blocks.AIR.defaultBlockState()};
        for (int s = 0; s < sections.length; s++) {
            var states = new PalettedContainer<>(Blocks.AIR.defaultBlockState(), Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY));
            for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                int wy = minY + s * 16 + y;
                BlockState value = switch (pattern) {
                    case "solid" -> Blocks.STONE.defaultBlockState();
                    case "empty" -> Blocks.AIR.defaultBlockState();
                    case "water" -> Blocks.WATER.defaultBlockState();
                    case "leaves" -> Blocks.OAK_LEAVES.defaultBlockState();
                    case "overworld" -> wy > 56 + ((x * 3 + z * 7 + (int)seed) & 15) ? Blocks.AIR.defaultBlockState() : mixed[random.nextInt(mixed.length)];
                    case "nether" -> wy > minY + height - 7 ? mixed[random.nextInt(mixed.length)] : Blocks.NETHERRACK.defaultBlockState();
                    case "end" -> wy > minY + 28 + ((x + z) & 7) || ((x + z + seed) & 3) == 0 ? Blocks.AIR.defaultBlockState() : Blocks.END_STONE.defaultBlockState();
                    case "global" -> Block.BLOCK_STATE_REGISTRY.byId(random.nextInt(Math.min(2048, Block.BLOCK_STATE_REGISTRY.size())));
                    case "backlog" -> x*16+z < 8+(int)(seed%31) ? Blocks.WATER.defaultBlockState() : Blocks.STONE.defaultBlockState();
                    case "backlog_leaves" -> x*16+z < 8+(int)(seed%31) ? Blocks.OAK_LEAVES.defaultBlockState() : Blocks.STONE.defaultBlockState();
                    case "backlog_cave_air" -> x*16+z < 8+(int)(seed%31) ? Blocks.CAVE_AIR.defaultBlockState() : Blocks.STONE.defaultBlockState();
                    case "backlog_air" -> x*16+z < 8+(int)(seed%31) ? Blocks.AIR.defaultBlockState() : Blocks.STONE.defaultBlockState();
                    default -> throw new IllegalArgumentException(pattern);
                };
                if (!value.is(Blocks.AIR)) states.set(x, y, z, value);
            }
            sections[s] = new LevelChunkSection(states, null);
        }
        return withSections(sections,minY,height);
    }

    static ProtoChunk withSections(LevelChunkSection[] sections, int minY, int height) {
        return new ProtoChunk(new ChunkPos(-37,41),UpgradeData.EMPTY,sections,new ProtoChunkTicks<>(),new ProtoChunkTicks<>(),LevelHeightAccessor.create(minY,height),null,null);
    }

    static void initialMaps(ChunkAccess chunk, long seed) {
        var random = new Random(seed);
        for (var type : Heightmap.Types.values()) {
            long[] data = chunk.getOrCreateHeightmapUnprimed(type).getRawData();
            for (int i = 0; i < data.length; i++) data[i] = random.nextLong();
        }
    }

    static long checksum(ChunkAccess chunk, Set<Heightmap.Types> types) {
        long sum = 0;
        for (var type : types) for (long value : chunk.getOrCreateHeightmapUnprimed(type).getRawData()) sum = sum * 31 + value;
        return sum;
    }
}
