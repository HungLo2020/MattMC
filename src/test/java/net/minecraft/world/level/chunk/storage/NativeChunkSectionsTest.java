package net.minecraft.world.level.chunk.storage;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Random;
import java.util.zip.InflaterInputStream;
import it.unimi.dsi.fastutil.shorts.ShortList;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NativeNbtRegionAccess;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.NativeChunkSections;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldgenTestRegistries;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class NativeChunkSectionsTest {
    static RegistryAccess registries;
    static PalettedContainerFactory factory;

    @BeforeAll static void load() {
        registries = WorldgenTestRegistries.load();
        factory = PalettedContainerFactory.create(registries);
    }

    @AfterAll static void close() {
        NativeChunkSections.setEnabled(true);
        WorldgenTestRegistries.close();
    }

    /** The saved chunks of {@code storage/chunks.bin} as their NBT. */
    static List<CompoundTag> corpus() throws Exception {
        try (var in = new DataInputStream(NativeChunkSectionsTest.class.getResourceAsStream("/storage/chunks.bin"))) {
            assertEquals(0x434E4254, in.readInt());
            int count = in.readInt();
            var tags = new ArrayList<CompoundTag>();
            for (int i = 0; i < count; i++) {
                in.readInt();
                in.readInt();
                byte[] data = in.readNBytes(in.readInt());
                tags.add(NbtIo.read(new DataInputStream(new InflaterInputStream(new ByteArrayInputStream(data))), NbtAccounter.unlimitedHeap()));
            }
            return tags;
        }
    }

    static SerializableChunkData parse(CompoundTag tag) {
        return SerializableChunkData.parse(LevelHeightAccessor.create(-64, 384), factory, tag);
    }

    /** The tape on each route; the Rust route must engage. */
    static byte[][] tapes(SerializableChunkData data) throws Exception {
        NativeChunkSections.setEnabled(false);
        byte[] java = data.encode().tape();
        assertArrayEquals(java, NativeNbtRegionAccess.writeTape(data.write()), "the Java route is the original tape");
        NativeChunkSections.setEnabled(true);
        long before = NativeChunkSections.CHUNKS.get();
        byte[] rust = data.encode().tape();
        assertEquals(before + 1, NativeChunkSections.CHUNKS.get(), "Rust must encode the sections");
        return new byte[][]{java, rust};
    }

    @Test void savedChunksEncodeIdentically() throws Exception {
        int chunks = 0, sections = 0;
        long bytes = 0;
        for (CompoundTag tag : corpus()) {
            var data = parse(tag);
            byte[][] tapes = tapes(data);
            assertArrayEquals(tapes[0], tapes[1], "chunk " + data.chunkPos() + " " + data.chunkStatus());
            chunks++;
            sections += data.sectionData().size();
            bytes += tapes[0].length;
        }
        System.out.println("CHUNK_SECTIONS_PARITY saved_chunks=" + chunks + " sections=" + sections + " tape_bytes=" + bytes);
    }

    private static LevelChunkSection section(Random random, List<BlockState> states, List<Holder<Biome>> biomes, int distinctStates, int distinctBiomes) {
        PalettedContainer<BlockState> blocks = factory.createForBlockStates();
        var pool = new ArrayList<BlockState>();
        for (int i = 0; i < distinctStates; i++) pool.add(states.get(random.nextInt(states.size())));
        boolean sparse = random.nextBoolean();
        for (int i = 0; i < 4096; i++) {
            if (sparse && random.nextInt(4) != 0) continue;
            blocks.getAndSetUnchecked(i & 15, i >> 8, i >> 4 & 15, pool.get(random.nextInt(pool.size())));
        }
        PalettedContainer<Holder<Biome>> biome = factory.createForBiomes();
        var biomePool = new ArrayList<Holder<Biome>>();
        for (int i = 0; i < distinctBiomes; i++) biomePool.add(biomes.get(random.nextInt(biomes.size())));
        for (int i = 0; i < 64; i++) biome.getAndSetUnchecked(i & 3, i >> 4, i >> 2 & 3, biomePool.get(random.nextInt(biomePool.size())));
        return new LevelChunkSection(blocks, biome);
    }

    private static DataLayer light(Random random) {
        return switch (random.nextInt(4)) {
            case 0 -> new DataLayer();
            case 1 -> new DataLayer(random.nextInt(16));
            default -> {
                byte[] bytes = new byte[2048];
                random.nextBytes(bytes);
                yield new DataLayer(bytes);
            }
        };
    }

    @Test void syntheticPalettesLightAndEmptySectionsEncodeIdentically() throws Exception {
        var states = new ArrayList<BlockState>();
        for (int id = 0; id < Block.BLOCK_STATE_REGISTRY.size(); id++) states.add(Block.BLOCK_STATE_REGISTRY.byId(id));
        List<Holder<Biome>> biomes = new ArrayList<>(registries.lookupOrThrow(Registries.BIOME).listElements().toList());
        var random = new Random(77);
        int chunks = 0;
        for (int round = 0; round < 64; round++) {
            var sections = new ArrayList<SerializableChunkData.SectionData>();
            for (int y = -5; y < 21; y++) {
                int kind = random.nextInt(8);
                // State palettes from single value through linear, hash map and global; biomes likewise.
                int distinct = new int[]{1, 1, 3, 12, 40, 200, 600, 2000}[random.nextInt(8)];
                int distinctBiomes = new int[]{1, 2, 5, 9, 20, 40}[random.nextInt(6)];
                LevelChunkSection section = kind == 0 ? null : section(random, states, biomes, distinct, distinctBiomes);
                DataLayer block = random.nextInt(3) == 0 ? null : light(random);
                DataLayer sky = random.nextInt(3) == 0 ? null : light(random);
                sections.add(new SerializableChunkData.SectionData(y, section, block, sky));
            }
            var heightmaps = new EnumMap<Heightmap.Types, long[]>(Heightmap.Types.class);
            heightmaps.put(Heightmap.Types.WORLD_SURFACE, new long[37]);
            var data = new SerializableChunkData(factory, new ChunkPos(round - 30, 7 - round), -4, round, round * 3L, ChunkStatus.FULL, null, null,
                UpgradeData.EMPTY, null, heightmaps, new ChunkAccess.PackedTicks(List.of(), List.of()), new ShortList[24], round % 2 == 0, sections, List.of(),
                List.of(), new CompoundTag());
            byte[][] tapes = tapes(data);
            assertArrayEquals(tapes[0], tapes[1], "round " + round);
            chunks++;
        }
        System.out.println("CHUNK_SECTIONS_PARITY synthetic_chunks=" + chunks);
    }

    private static SerializableChunkData chunk(List<SerializableChunkData.SectionData> sections) {
        var heightmaps = new EnumMap<Heightmap.Types, long[]>(Heightmap.Types.class);
        return new SerializableChunkData(factory, new ChunkPos(3, -9), -4, 1L, 2L, ChunkStatus.FULL, null, null, UpgradeData.EMPTY, null,
            heightmaps, new ChunkAccess.PackedTicks(List.of(), List.of()), new ShortList[24], true, sections, List.of(), List.of(), new CompoundTag());
    }

    @Test void emptySectionListsAndRepeatedPaletteEntriesEncodeIdentically() throws Exception {
        // No section has content: the list is empty (element type 0).
        var none = new ArrayList<SerializableChunkData.SectionData>();
        for (int y = -5; y < 21; y++) none.add(new SerializableChunkData.SectionData(y, null, null, null));
        byte[][] tapes = tapes(chunk(none));
        assertArrayEquals(tapes[0], tapes[1]);
        // A palette naming the same state twice: packing merges equal states.
        var strategy = factory.blockStatesStrategy();
        var stone = Blocks.STONE.defaultBlockState();
        var dirt = Blocks.DIRT.defaultBlockState();
        long[] words = new long[256];
        for (int i = 0; i < words.length; i++) words[i] = 0x0123012301230123L * (i % 3 + 1) & 0x3333333333333333L;
        var packed = new net.minecraft.world.level.chunk.PalettedContainerRO.PackedData<>(List.of(stone, dirt, stone, Blocks.GLASS.defaultBlockState()),
            java.util.Optional.of(java.util.stream.LongStream.of(words)), 4);
        var states = PalettedContainer.unpack(strategy, packed).getOrThrow();
        var section = new LevelChunkSection(states, factory.createForBiomes());
        tapes = tapes(chunk(List.of(new SerializableChunkData.SectionData(0, section, null, null))));
        assertArrayEquals(tapes[0], tapes[1]);
    }

    @Test void pendingReadsAndDiskRoundTripMatchTheTag(@TempDir Path dir) throws Exception {
        var tags = corpus();
        var info = new RegionStorageInfo("test", net.minecraft.world.level.Level.OVERWORLD, "chunk");
        try (var worker = new IOWorker(info, dir, false)) {
            for (CompoundTag tag : tags.subList(0, 8)) {
                var data = parse(tag);
                worker.storeEncoded(data.chunkPos(), data::encode);
                // Before the write: pending scans and reads build the original tag (scan first, before any read builds it).
                var visitor = new net.minecraft.nbt.visitors.CollectToTag();
                worker.scanChunk(data.chunkPos(), visitor).join();
                assertEquals(data.write(), visitor.getResult());
                assertEquals(data.write(), worker.loadAsync(data.chunkPos()).join().orElseThrow());
            }
            worker.synchronize(true).join();
            for (CompoundTag tag : tags.subList(0, 8)) {
                var data = parse(tag);
                assertEquals(data.write(), worker.loadAsync(data.chunkPos()).join().orElseThrow(), "read back " + data.chunkPos());
            }
        }
        assertTrue(Files.list(dir).findAny().isPresent());
        System.out.println("CHUNK_SECTIONS_IO_ROUND_TRIP chunks=8");
    }
}
