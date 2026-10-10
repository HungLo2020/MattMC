package net.minecraft.world.level.chunk.storage;

import org.junit.jupiter.api.Tag;
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

@Tag("parity")
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

    @Test void syntheticPalettesLightAndEmptySectionsEncodeIdentically(@TempDir Path dir) throws Exception {
        var states = new ArrayList<BlockState>();
        for (int id = 0; id < Block.BLOCK_STATE_REGISTRY.size(); id++) states.add(Block.BLOCK_STATE_REGISTRY.byId(id));
        List<Holder<Biome>> biomes = new ArrayList<>(registries.lookupOrThrow(Registries.BIOME).listElements().toList());
        var random = new Random(77);
        int chunks = 0;
        var synthetic = new ArrayList<CompoundTag>();
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
            synthetic.add(data.write());
            chunks++;
        }
        // The same chunks loaded back on both routes.
        int rust = loads(dir, synthetic, true);
        System.out.println("CHUNK_SECTIONS_PARITY synthetic_chunks=" + chunks + " loaded_by_rust=" + rust);
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

    /** Everything parse produced: the tape it re-encodes to, every section's
     * storage, light and Y. */
    static String fingerprint(SerializableChunkData data) throws Exception {
        if (data == null) return "null";
        var out = new StringBuilder(java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
            .digest(NativeNbtRegionAccess.writeTape(data.write()))));
        for (var section : data.sectionData()) {
            out.append('\n').append(section.y()).append(' ').append(net.minecraft.world.level.chunk.SectionFingerprint.of(section.chunkSection()));
            for (var layer : new DataLayer[]{section.blockLight(), section.skyLight()}) {
                out.append(layer == null ? " -" : " " + java.util.Arrays.hashCode(layer.getData()));
            }
        }
        return out.toString();
    }

    /** Loads each tag through a region file on both routes and compares what parse built. */
    static int loads(Path dir, List<CompoundTag> tags, boolean expectRust) throws Exception {
        var storage = new RegionFileStorage(new RegionStorageInfo("load", net.minecraft.world.level.Level.OVERWORLD, "chunk"), dir, false);
        int rust = 0;
        try {
            for (CompoundTag tag : tags) {
                var pos = new ChunkPos(tag.getIntOr("xPos", 0), tag.getIntOr("zPos", 0));
                storage.write(pos, tag);
                String java, native_;
                Exception javaError = null, nativeError = null;
                try {
                    java = fingerprint(SerializableChunkData.parse(LevelHeightAccessor.create(-64, 384), factory, storage.read(pos)));
                } catch (Exception e) {
                    java = null;
                    javaError = e;
                }
                long before = NativeChunkSections.LOADED.get();
                try {
                    var loaded = new IOWorker.Loaded(storage.readTape(pos), null);
                    native_ = fingerprint(SerializableChunkData.parseLoaded(LevelHeightAccessor.create(-64, 384), factory, loaded, t -> t));
                } catch (Exception e) {
                    native_ = null;
                    nativeError = e;
                }
                if (javaError != null || nativeError != null) {
                    assertNotNull(javaError, "only Rust failed: " + nativeError);
                    assertNotNull(nativeError, "only Java failed: " + javaError);
                    Throwable cause = nativeError instanceof java.io.UncheckedIOException u ? u.getCause() : nativeError;
                    assertEquals(javaError.getClass(), cause.getClass());
                } else {
                    assertEquals(java, native_, "chunk " + pos);
                }
                if (NativeChunkSections.LOADED.get() > before) rust++;
            }
        } finally {
            storage.close();
        }
        if (expectRust) assertEquals(tags.size(), rust, "every chunk must load through Rust");
        return rust;
    }

    @Test void savedChunksLoadIdentically(@TempDir Path dir) throws Exception {
        int rust = loads(dir, corpus(), true);
        System.out.println("CHUNK_SECTIONS_LOAD_PARITY saved_chunks=" + rust);
    }

    /** Edited saved chunks: what Rust must decline (Java decodes) and what it must still read. */
    @Test void nonCanonicalAndOddSectionsLoadIdentically(@TempDir Path dir) throws Exception {
        var tags = corpus();
        var edited = new ArrayList<CompoundTag>();
        int i = 0;
        for (CompoundTag original : tags.subList(0, 24)) {
            CompoundTag tag = original.copy();
            var sections = tag.getListOrEmpty("sections");
            var section = sections.getCompound(sections.size() / 2).orElseThrow();
            switch (i++ % 8) {
                case 0 -> section.getCompound("block_states").orElseThrow().getListOrEmpty("palette").getCompound(0).orElseThrow()
                    .putString("Name", "stone"); // no namespace: Java resolves it, Rust declines
                case 1 -> section.getCompound("biomes").orElseThrow().getListOrEmpty("palette").set(0, net.minecraft.nbt.StringTag.valueOf("plains"));
                case 2 -> section.putByteArray("BlockLight", new byte[1000]); // DataLayer rejects: both fail
                case 3 -> section.putString("unknown", "ignored"); // both ignore it
                case 4 -> section.putByte("Y", (byte)100); // outside the height: containers ignored
                case 5 -> section.getCompound("block_states").orElseThrow().putString("extra", "ignored");
                case 6 -> section.remove("biomes"); // default biome container
                default -> section.putInt("Y", section.getByteOr("Y", (byte)0)); // non-byte Y: Rust declines
            }
            edited.add(tag);
        }
        int rust = loads(dir, edited, false);
        // Unknown keys, out-of-height sections, missing biomes and extra container keys stay on Rust (cases 3-6).
        assertTrue(rust >= 12, "Rust loads " + rust);
        System.out.println("CHUNK_SECTIONS_LOAD_PARITY edited_chunks=" + edited.size() + " rust=" + rust);
    }

    @Test void olderDataVersionsTakeTheUpgradePath(@TempDir Path dir) throws Exception {
        var storage = new RegionFileStorage(new RegionStorageInfo("old", net.minecraft.world.level.Level.OVERWORLD, "chunk"), dir, false);
        try {
            CompoundTag tag = corpus().getFirst().copy();
            int current = tag.getIntOr("DataVersion", 0);
            tag.putInt("DataVersion", current - 1);
            var pos = new ChunkPos(tag.getIntOr("xPos", 0), tag.getIntOr("zPos", 0));
            storage.write(pos, tag);
            int[] upgrades = {0};
            long before = NativeChunkSections.LOADED.get();
            var parsed = SerializableChunkData.parseLoaded(LevelHeightAccessor.create(-64, 384), factory, new IOWorker.Loaded(storage.readTape(pos), null),
                t -> {
                    upgrades[0]++;
                    return t;
                });
            assertEquals(1, upgrades[0], "an older chunk must be upgraded");
            assertEquals(before, NativeChunkSections.LOADED.get(), "an older chunk must not use the Rust sections");
            assertEquals(fingerprint(SerializableChunkData.parse(LevelHeightAccessor.create(-64, 384), factory, tag)), fingerprint(parsed));
        } finally {
            storage.close();
        }
    }

    /** #818: a section Rust rejects partway through packing (a storage ID
     * outside its palette, after valid ones) must not affect the next save
     * on the same thread. */
    @Test void rejectedSectionThenValidChunkOnTheSameThread() throws Exception {
        var stone = Blocks.STONE.defaultBlockState();
        var air = Blocks.AIR.defaultBlockState();
        var states = factory.createForBlockStates();
        states.getAndSetUnchecked(1, 0, 0, stone); // palette [air, stone]
        var section = new LevelChunkSection(states, factory.createForBiomes());
        // Then storage ids 0 and 1, then 5: no palette entry (as corrupted saved data could hold).
        long[] words = net.minecraft.world.level.chunk.SectionFingerprint.rawWords(states);
        assertEquals(256, words.length, "four-bit storage");
        words[0] = 0L | 1L << 4 | 5L << 8;
        var broken = chunk(List.of(new SerializableChunkData.SectionData(0, section, null, null)));
        Class<?>[] failures = new Class<?>[2];
        for (int route = 0; route < 2; route++) {
            NativeChunkSections.setEnabled(route == 1);
            try {
                broken.encode();
            } catch (RuntimeException expected) {
                failures[route] = expected.getClass();
            } finally {
                NativeChunkSections.setEnabled(true);
            }
        }
        assertNotNull(failures[0], "the original encoding rejects the section");
        assertEquals(failures[0], failures[1]);
        // The same thread then saves ordinary chunks exactly as Java does.
        for (CompoundTag tag : corpus().subList(0, 6)) {
            byte[][] tapes = tapes(parse(tag));
            assertArrayEquals(tapes[0], tapes[1]);
        }
    }

    /** #818: a tape the writer could not produce fails the store at the region
     * write, as the original's region write did; the future does not stay pending. */
    @Test void unwritableTapeFailsTheStore(@TempDir Path dir) throws Exception {
        var data = parse(corpus().getFirst());
        var info = new RegionStorageInfo("failure", net.minecraft.world.level.Level.OVERWORLD, "chunk");
        try (var worker = new IOWorker(info, dir, false)) {
            var failure = new java.io.IOException("tape writer failed");
            var stored = worker.storeEncoded(data.chunkPos(), () -> new SerializableChunkData.Encoded(null, data::write, failure));
            var error = assertThrows(java.util.concurrent.CompletionException.class,
                () -> stored.orTimeout(30, java.util.concurrent.TimeUnit.SECONDS).join());
            assertSame(failure, error.getCause());
            // Nothing was written.
            worker.synchronize(true).join();
            assertTrue(worker.loadAsync(data.chunkPos()).join().isEmpty());
        }
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
            // A pending encoded chunk loads from its tape like a region read.
            for (CompoundTag tag : tags.subList(0, 8)) {
                var data = parse(tag);
                var loaded = worker.loadForParse(data.chunkPos()).join().orElseThrow();
                assertNotNull(loaded.tape());
                assertEquals(fingerprint(data), fingerprint(SerializableChunkData.parseLoaded(LevelHeightAccessor.create(-64, 384), factory, loaded, t -> t)));
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

    /** Rust builds every state's {@code BlockState.CODEC} fragment from its block registry. */
    @Test void everyStateFragmentMatchesTheCodec() throws Throwable {
        assertTrue(net.minecraft.world.level.block.NativeBlockRegistry.ready());
        var call = net.minecraft.util.NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_chunk_sections_fragment",
            java.lang.foreign.FunctionDescriptor.of(java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.JAVA_INT,
                java.lang.foreign.ValueLayout.ADDRESS, java.lang.foreign.ValueLayout.JAVA_INT));
        int states = Block.BLOCK_STATE_REGISTRY.size();
        long bytes = 0;
        try (var arena = java.lang.foreign.Arena.ofConfined()) {
            var out = arena.allocate(4096);
            for (int id = 0; id < states; id++) {
                BlockState state = Block.BLOCK_STATE_REGISTRY.byId(id);
                byte[] expected = NativeNbtRegionAccess.elementTape(BlockState.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, state).getOrThrow());
                int length = (int)call.invokeExact(id, out, 4096);
                assertEquals(expected.length, length, "fragment length of " + state);
                assertArrayEquals(expected, out.asSlice(0, length).toArray(java.lang.foreign.ValueLayout.JAVA_BYTE), "fragment of " + state);
                bytes += length;
            }
        }
        assertEquals(-1, (int)call.invokeExact(states, java.lang.foreign.MemorySegment.NULL, 0));
        System.out.println("CHUNK_SECTIONS_VOCABULARY_PARITY states=" + states + " bytes=" + bytes);
    }
}
