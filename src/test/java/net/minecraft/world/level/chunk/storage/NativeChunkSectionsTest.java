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
import net.minecraft.world.level.Level;
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

    @Tag("slow") @Test void syntheticPalettesLightAndEmptySectionsEncodeIdentically(@TempDir Path dir) throws Exception {
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

    @Test void savedChunksMatchActualFrozenOwnersAndSurviveDecodeScope() throws Exception {
        NativeChunkSections.setEnabled(true);
        DecodedChunkVerification.factory = factory;
        var dataField = PalettedContainer.class.getDeclaredField("data");
        dataField.setAccessible(true);
        var lightField = DataLayer.class.getDeclaredField("data");
        lightField.setAccessible(true);
        int lights = 0;
        var tags = corpus();
        try (var input = new DataInputStream(new java.util.zip.GZIPInputStream(
                getClass().getResourceAsStream("/storage/frozen-decoded-chunks.bin.gz")))) {
            assertEquals(0x44434f31, input.readInt());
            assertEquals(tags.size(), input.readInt());
            for (var tag : tags) {
                var position = new ChunkPos(tag.getIntOr("xPos", 0), tag.getIntOr("zPos", 0));
                assertEquals(position.toLong(), input.readLong());
                byte[] expected = input.readNBytes(input.readInt());
                long before = NativeChunkSections.LOADED.get();
                var parsed = SerializableChunkData.parseLoaded(LevelHeightAccessor.create(-64, 384), factory,
                    new IOWorker.Loaded(NativeNbtRegionAccess.writeTape(tag), null), t -> {
                        throw new AssertionError("Current saved chunk must not require an upgrade");
                    });
                assertEquals(before + 1, NativeChunkSections.LOADED.get());
                assertNotNull(parsed);
                // parseLoaded already closed its native decode scope. Returned owners
                // must remain independent, with no Java packed/light mirrors.
                for (var row : parsed.sectionData()) {
                    if (row.chunkSection() != null) {
                        assertNull(dataField.get(row.chunkSection().getStates()));
                        assertNull(dataField.get(row.chunkSection().getBiomes()));
                    }
                    for (var layer : new DataLayer[]{row.blockLight(), row.skyLight()}) if (layer != null) {
                        assertNull(lightField.get(layer));
                        assertNotEquals(0, layer.nativeLightOwnerForMap().address());
                        lights++;
                    }
                }
                assertArrayEquals(expected, DecodedChunkVerification.fingerprint(parsed), "actual Frozen " + position);
                for (var row : parsed.sectionData()) if (row.chunkSection() != null) {
                    var copied = row.chunkSection().getStates().copy();
                    var original = row.chunkSection().getBlockState(0, 0, 0);
                    copied.getAndSetUnchecked(0, 0, 0, original.isAir() ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
                    assertSame(original, row.chunkSection().getBlockState(0, 0, 0));
                }
            }
            assertEquals(-1, input.read());
        }
        assertTrue(lights > 0, "Saved lighting participates in the primary owner path");
    }

    @Test void ownedRegionTapeParsesFrozenCorpusAndClosesBeforeReturnedOwnersAreUsed(@TempDir Path dir) throws Exception {
        NativeChunkSections.setEnabled(true);
        DecodedChunkVerification.factory = factory;
        try (var storage = new RegionFileStorage(new RegionStorageInfo("owned-decode", Level.OVERWORLD, "chunk"), dir, false);
             var input = new DataInputStream(new java.util.zip.GZIPInputStream(
                 getClass().getResourceAsStream("/storage/frozen-decoded-chunks.bin.gz")))) {
            var tags = corpus();
            assertEquals(0x44434f31, input.readInt());
            assertEquals(tags.size(), input.readInt());
            for (var tag : tags) {
                var pos = new ChunkPos(tag.getIntOr("xPos", 0), tag.getIntOr("zPos", 0));
                storage.write(pos, tag);
                assertEquals(pos.toLong(), input.readLong());
                var expected = input.readNBytes(input.readInt());
                var tape = storage.readOwnedTape(pos);
                assertNotNull(tape);
                assertArrayEquals(storage.readTape(pos), tape.copy());
                long before = NativeChunkSections.LOADED.get();
                var parsed = SerializableChunkData.parseLoaded(LevelHeightAccessor.create(-64, 384), factory,
                    new IOWorker.Loaded(null, null, tape), t -> { throw new AssertionError("Unexpected upgrade"); });
                assertEquals(before + 1, NativeChunkSections.LOADED.get());
                assertThrows(IllegalStateException.class, tape::copy);
                tape.close(); // close is idempotent, independent section owners remain valid
                assertArrayEquals(expected, DecodedChunkVerification.fingerprint(parsed));
            }
            assertEquals(-1, input.read());
            assertNull(storage.readOwnedTape(new ChunkPos(31, 31)));
        }
    }

    @Test void olderOwnedTapeUsesUpgradeAndClosesOnUpgradeFailure(@TempDir Path dir) throws Exception {
        var tag = corpus().getFirst();
        tag.putInt("DataVersion", 1);
        var pos = new ChunkPos(tag.getIntOr("xPos", 0), tag.getIntOr("zPos", 0));
        try (var storage = new RegionFileStorage(new RegionStorageInfo("owned-upgrade", Level.OVERWORLD, "chunk"), dir, false)) {
            storage.write(pos, tag);
            var tape = storage.readOwnedTape(pos);
            var marker = new IllegalArgumentException("upgrade failure");
            var error = assertThrows(IllegalArgumentException.class, () -> SerializableChunkData.parseLoaded(
                LevelHeightAccessor.create(-64, 384), factory, new IOWorker.Loaded(null, null, tape), full -> {
                    assertEquals(tag, full);
                    throw marker;
                }));
            assertSame(marker, error);
            assertThrows(IllegalStateException.class, tape::copy);
        }
    }

    @Test void customFactoryCodecsKeepTheirLoadAndSaveCallbacks() throws Exception {
        var calls = new int[2];
        var blockCodec = factory.blockStatesContainerCodec().xmap(value -> { calls[0]++; return value; },
            value -> { calls[1]++; return value; });
        var custom = new PalettedContainerFactory(factory.blockStatesStrategy(), factory.defaultBlockState(), blockCodec,
            factory.biomeStrategy(), factory.defaultBiome(), factory.biomeContainerCodec());
        var tag = corpus().getFirst();
        long loaded = NativeChunkSections.LOADED.get(), encoded = NativeChunkSections.CHUNKS.get();
        NativeChunkSections.setEnabled(true);
        var parsed = SerializableChunkData.parseLoaded(LevelHeightAccessor.create(-64, 384), custom,
            new IOWorker.Loaded(NativeNbtRegionAccess.writeTape(tag), null), java.util.function.UnaryOperator.identity());
        assertNotNull(parsed);
        assertTrue(calls[0] > 0);
        assertEquals(loaded, NativeChunkSections.LOADED.get());
        parsed.encode();
        assertTrue(calls[1] > 0);
        assertEquals(encoded, NativeChunkSections.CHUNKS.get());
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

    /** Fixed counterparts of the Rust FFI recovery inputs, not generated by Rust. */
    static SerializableChunkData recoveryOracleFixture(boolean zero) {
        return recoveryOracleFixture(zero, false);
    }

    // Exact-class admission guards in PalettedContainer bypass native live
    // ownership, resize, histogram and packing for these test-only subclasses.
    private static final class JavaOnlyPalette<T> extends PalettedContainer<T> {
        JavaOnlyPalette(T value, net.minecraft.world.level.chunk.Strategy<T> strategy) { super(value, strategy); }
    }

    private static SerializableChunkData recoveryOracleFixture(boolean zero, boolean javaOnly) {
        var registry = registries.lookupOrThrow(Registries.BIOME);
        var plains = registry.getOrThrow(net.minecraft.world.level.biome.Biomes.PLAINS);
        var desert = registry.getOrThrow(net.minecraft.world.level.biome.Biomes.DESERT);
        PalettedContainer<BlockState> states = javaOnly
            ? new JavaOnlyPalette<>(Blocks.STONE.defaultBlockState(), factory.blockStatesStrategy())
            : factory.createForBlockStates();
        PalettedContainer<Holder<Biome>> biomes = javaOnly
            ? new JavaOnlyPalette<>(plains, factory.biomeStrategy()) : factory.createForBiomes();
        if (javaOnly) {
            assertNotEquals(PalettedContainer.class, states.getClass());
            assertNotEquals(PalettedContainer.class, biomes.getClass());
            assertFalse(biomes.isNativeBiomeOwned());
        }
        for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
            states.getAndSetUnchecked(x, y, z, Blocks.STONE.defaultBlockState());
        }
        for (int y = 0; y < 4; y++) for (int z = 0; z < 4; z++) for (int x = 0; x < 4; x++) {
            biomes.getAndSetUnchecked(x, y, z, plains);
        }
        if (!zero) {
            states.getAndSetUnchecked(0, 0, 0, Blocks.DIRT.defaultBlockState());
            biomes.getAndSetUnchecked(0, 0, 0, desert);
        }
        return chunk(List.of(new SerializableChunkData.SectionData(0, new LevelChunkSection(states, biomes), null, null)));
    }

    /** Serialize the named sections record through retained Java write/TapeWriter only. */
    static byte[] cleanJavaRecoverySectionsTape(boolean zero) throws Exception {
        CompoundTag root;
        NativeChunkSections.setEnabled(false);
        try { root = recoveryOracleFixture(zero, true).write(); }
        finally { NativeChunkSections.setEnabled(true); }
        var writerClass = Class.forName("net.minecraft.nbt.NativeNbt$TapeWriter");
        var constructor = writerClass.getDeclaredConstructor(int.class);
        constructor.setAccessible(true);
        Object writer = constructor.newInstance(4096);
        var write = writerClass.getDeclaredMethod("writeTag", String.class, net.minecraft.nbt.Tag.class);
        write.setAccessible(true);
        write.invoke(writer, "sections", root.get("sections"));
        var output = writerClass.getDeclaredField("output");
        output.setAccessible(true);
        return ((java.io.ByteArrayOutputStream)output.get(writer)).toByteArray();
    }

    @Test void recoveryFfiFixturesMatchCleanJavaAndNativeSections() throws Exception {
        for (boolean zero : new boolean[]{false, true}) {
            String name = zero ? "chunk-recovery-zero-sections.bin" : "chunk-recovery-valid-sections.bin";
            byte[] expected;
            try (var in = NativeChunkSectionsTest.class.getResourceAsStream("/storage/" + name)) {
                assertNotNull(in, "checked-in clean Java oracle " + name);
                expected = in.readAllBytes();
            }
            assertArrayEquals(expected, cleanJavaRecoverySectionsTape(zero), "clean Java named sections " + name);
            var data = recoveryOracleFixture(zero);
            NativeChunkSections.setEnabled(true);
            byte[] nativeTape = NativeChunkSections.encode(data.sectionData(), factory);
            assertNotNull(nativeTape, "the native route must engage");
            assertArrayEquals(expected, nativeTape, "native sections " + name);
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

    /** The production save shape: a real malformed snapshot throws from write/encode,
     * then the I/O supplier (including background encode future::join) must settle. */
    @Test @org.junit.jupiter.api.Timeout(45)
    void malformedSnapshotCompletesSaveAccountingAndRecovers(@TempDir Path dir) throws Exception {
        var states = factory.createForBlockStates();
        states.getAndSetUnchecked(1, 0, 0, Blocks.STONE.defaultBlockState());
        var section = new LevelChunkSection(states, factory.createForBiomes());
        long[] words = net.minecraft.world.level.chunk.SectionFingerprint.rawWords(states);
        words[0] = 0L | 1L << 4 | 5L << 8;
        var broken = chunk(List.of(new SerializableChunkData.SectionData(0, section, null, null)));
        var info = new RegionStorageInfo("snapshot-failure", Level.OVERWORLD, "chunk");
        var healthy = parse(corpus().getFirst());
        try (var worker = new IOWorker(info, dir, false)) {
            for (boolean encodedRoute : new boolean[]{false, true}) {
                var active = new java.util.concurrent.atomic.AtomicInteger(1);
                var reports = new java.util.concurrent.atomic.AtomicInteger();
                java.util.concurrent.CompletableFuture<Void> save;
                if (encodedRoute) {
                    var encoded = java.util.concurrent.CompletableFuture.supplyAsync(broken::encode);
                    save = worker.storeEncoded(broken.chunkPos(), encoded::join);
                } else {
                    save = worker.store(broken.chunkPos(), broken::write);
                }
                var accounted = save.handle((unused, error) -> {
                    if (error != null) reports.incrementAndGet();
                    active.decrementAndGet();
                    return error;
                });
                assertNotNull(accounted.get(5, java.util.concurrent.TimeUnit.SECONDS));
                assertEquals(0, active.get());
                assertEquals(1, reports.get());
                worker.storeEncoded(healthy.chunkPos(), healthy::encode).get(5, java.util.concurrent.TimeUnit.SECONDS);
                worker.synchronize(true).get(5, java.util.concurrent.TimeUnit.SECONDS);
                assertEquals(healthy.write(), worker.loadAsync(healthy.chunkPos()).get(5, java.util.concurrent.TimeUnit.SECONDS).orElseThrow());
            }
        }
        try (var worker = new IOWorker(info, dir, false)) {
            assertEquals(healthy.write(), worker.loadAsync(healthy.chunkPos()).get(5, java.util.concurrent.TimeUnit.SECONDS).orElseThrow());
        }
    }

    /** Partially mocked server boundary, real ChunkMap.save and its callback,
     * real snapshot encoding, executors, IOWorker and native region files. */
    @Test @org.junit.jupiter.api.Timeout(60)
    void chunkMapSaveReportsFailureAndReleasesActualAccounting(@TempDir Path dir) throws Exception {
        var states = factory.createForBlockStates();
        states.getAndSetUnchecked(1, 0, 0, Blocks.STONE.defaultBlockState());
        var section = new LevelChunkSection(states, factory.createForBiomes());
        long[] words = net.minecraft.world.level.chunk.SectionFingerprint.rawWords(states);
        words[0] = 0L | 1L << 4 | 5L << 8;
        var broken = chunk(List.of(new SerializableChunkData.SectionData(0, section, null, null)));
        Class<?> expectedFailure = assertThrows(RuntimeException.class, broken::encode).getClass();
        var healthy = parse(corpus().getFirst());
        var expectedHealthy = healthy.write();
        var failedChunk = new net.minecraft.world.level.chunk.ProtoChunk(broken.chunkPos(), UpgradeData.EMPTY,
            LevelHeightAccessor.create(-64, 384), factory, null);
        failedChunk.setPersistedStatus(ChunkStatus.FULL);
        var healthyChunk = new net.minecraft.world.level.chunk.ProtoChunk(healthy.chunkPos(), UpgradeData.EMPTY,
            LevelHeightAccessor.create(-64, 384), factory, null);
        healthyChunk.setPersistedStatus(ChunkStatus.FULL);
        var level = org.mockito.Mockito.mock(net.minecraft.server.level.ServerLevel.class);
        var server = org.mockito.Mockito.mock(net.minecraft.server.MinecraftServer.class);
        org.mockito.Mockito.when(level.getServer()).thenReturn(server);
        var map = org.mockito.Mockito.mock(net.minecraft.server.level.ChunkMap.class, org.mockito.Mockito.CALLS_REAL_METHODS);
        var active = new java.util.concurrent.atomic.AtomicInteger();
        var info = new RegionStorageInfo("chunk-map-recovery", Level.OVERWORLD, "chunk");
        try (var worker = new IOWorker(info, dir, false);
             var snapshots = org.mockito.Mockito.mockStatic(SerializableChunkData.class);
             var dh = org.mockito.Mockito.mockStatic(com.seibel.distanthorizons.core.api.internal.SharedApi.class)) {
            // These stubs avoid world acquisition/POI/DH work; save, writeEncoded,
            // supplyAsync, encode, returned future and production handle stay real.
            snapshots.when(() -> SerializableChunkData.copyOf(level, failedChunk)).thenReturn(broken);
            snapshots.when(() -> SerializableChunkData.copyOf(level, healthyChunk)).thenReturn(healthy);
            dh.when(() -> com.seibel.distanthorizons.core.api.internal.SharedApi.isChunkAtChunkPosAlreadyUpdating(
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(true);
            setFixtureField(ChunkStorage.class, map, "worker", worker);
            setFixtureField(net.minecraft.server.level.ChunkMap.class, map, "level", level);
            setFixtureField(net.minecraft.server.level.ChunkMap.class, map, "poiManager",
                org.mockito.Mockito.mock(net.minecraft.world.entity.ai.village.poi.PoiManager.class));
            setFixtureField(net.minecraft.server.level.ChunkMap.class, map, "activeChunkWrites", active);
            setFixtureField(net.minecraft.server.level.ChunkMap.class, map, "chunkTypeCache",
                new it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap());
            var save = net.minecraft.server.level.ChunkMap.class.getDeclaredMethod("save", ChunkAccess.class);
            save.setAccessible(true);
            assertEquals(true, save.invoke(map, failedChunk), "must reach asynchronous save rather than synchronous catch");
            var error = org.mockito.ArgumentCaptor.forClass(Throwable.class);
            org.mockito.Mockito.verify(server, org.mockito.Mockito.timeout(5000).times(1)).reportChunkSaveFailure(
                error.capture(), org.mockito.ArgumentMatchers.eq(info), org.mockito.ArgumentMatchers.eq(broken.chunkPos()));
            Throwable cause = error.getValue();
            while (cause.getCause() != null) cause = cause.getCause();
            assertEquals(expectedFailure, cause.getClass());
            awaitActualSaveAccounting(active);
            assertTrue(worker.loadAsync(broken.chunkPos()).get(5, java.util.concurrent.TimeUnit.SECONDS).isEmpty());
            org.mockito.Mockito.verify(map).writeEncoded(org.mockito.ArgumentMatchers.eq(broken.chunkPos()), org.mockito.ArgumentMatchers.any());
            assertEquals(true, save.invoke(map, healthyChunk));
            awaitActualSaveAccounting(active);
            worker.synchronize(true).get(5, java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(expectedHealthy, worker.loadAsync(healthy.chunkPos()).get(5, java.util.concurrent.TimeUnit.SECONDS).orElseThrow());
            org.mockito.Mockito.verify(server, org.mockito.Mockito.times(1)).reportChunkSaveFailure(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        }
        try (var reopened = new IOWorker(info, dir, false)) {
            assertEquals(expectedHealthy, reopened.loadAsync(healthy.chunkPos()).get(5, java.util.concurrent.TimeUnit.SECONDS).orElseThrow());
        }
    }

    private static void setFixtureField(Class<?> owner, Object target, String name, Object value) throws Exception {
        var field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void awaitActualSaveAccounting(java.util.concurrent.atomic.AtomicInteger active) throws Exception {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        while (active.get() != 0 && System.nanoTime() < deadline) Thread.sleep(1);
        assertEquals(0, active.get(), "actual ChunkMap activeChunkWrites must return to zero");
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
                assertTrue(loaded.tape() != null || loaded.nativeTape() != null, "Pending encoded tape or completed disk-owned tape");
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
