package net.minecraft.world.level.levelgen;

import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Exact parity of the native NOISE fill with doFill's Java loop, through the
 * production fillFromNoise path, for every vanilla noise setting. */
class NativeNoiseFillTest {
    static net.minecraft.server.packs.resources.MultiPackResourceManager resources;
    static RegistryAccess.Frozen registries;
    static List<Holder.Reference<NoiseGeneratorSettings>> settings;

    @BeforeAll static void load() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        resources = new net.minecraft.server.packs.resources.MultiPackResourceManager(net.minecraft.server.packs.PackType.SERVER_DATA,
            List.of(net.minecraft.server.packs.repository.ServerPacksSource.createVanillaPackSource()));
        var layers = net.minecraft.server.RegistryLayer.createRegistryAccess();
        var tags = net.minecraft.tags.TagLoader.loadTagsForExistingRegistries(resources, layers.getLayer(net.minecraft.server.RegistryLayer.STATIC));
        var lookups = net.minecraft.tags.TagLoader.buildUpdatedLookups(layers.getAccessForLoading(net.minecraft.server.RegistryLayer.WORLDGEN), tags);
        registries = net.minecraft.resources.RegistryDataLoader.load(resources, lookups, net.minecraft.resources.RegistryDataLoader.WORLDGEN_REGISTRIES);
        settings = registries.lookupOrThrow(Registries.NOISE_SETTINGS).listElements()
            .sorted(java.util.Comparator.comparing(holder -> holder.key().location().toString())).toList();
    }

    @AfterAll static void close() {
        NativeNoiseFill.setEnabled(true);
        resources.close();
    }

    record Filled(ProtoChunk chunk, NoiseChunk noise) {}

    /** Native fills by cell traversal: Rust-owned, Rust-owned with Java-filled slices, or Java's per-cell loop. */
    enum Traversal { NATIVE, UPLOADED, JAVA }

    static Filled fill(Holder<NoiseGeneratorSettings> setting, RandomState random, ChunkPos pos, boolean nativeFill) {
        return fill(setting, random, pos, nativeFill, Traversal.NATIVE);
    }

    static Filled fill(Holder<NoiseGeneratorSettings> setting, RandomState random, ChunkPos pos, boolean nativeFill, Traversal traversal) {
        return fill(setting, random, pos, nativeFill, traversal, DensityFunctions.BeardifierMarker.INSTANCE);
    }

    static Filled fill(Holder<NoiseGeneratorSettings> setting, RandomState random, ChunkPos pos, boolean nativeFill, Traversal traversal,
                       DensityFunctions.BeardifierOrMarker beardifier) {
        var config = setting.value();
        var noise = config.noiseSettings();
        var biome = registries.lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS);
        var generator = new NoiseBasedChunkGenerator(new FixedBiomeSource(biome), setting);
        var chunk = new ProtoChunk(pos, UpgradeData.EMPTY, LevelHeightAccessor.create(noise.minY(), noise.height()),
            PalettedContainerFactory.create(registries), null);
        // The generator's own NoiseChunk inputs, with no structures and no blending.
        // The Java route also keeps Java preliminary surface levels and aquifer sources;
        // only the native traversal route instantiates chunk noise natively.
        NativeSurfaceLevel.setEnabled(nativeFill);
        NativeFluidSources.setEnabled(nativeFill);
        NativeChunkNoise.setEnabled(nativeFill && traversal == Traversal.NATIVE);
        NoiseChunk noiseChunk;
        try {
            noiseChunk = chunk.getOrCreateNoiseChunk(access -> NoiseChunk.forChunk(access, random, beardifier,
                config, new AquiferFluidPicker(config.seaLevel(), config.defaultFluid()), Blender.empty()));
        } finally {
            NativeSurfaceLevel.setEnabled(true);
            NativeChunkNoise.setEnabled(true);
        }
        // Aquifers bind their sources when first used, during the fill.
        NativeFluidSources.setEnabled(nativeFill);
        NativeNoiseFill.setEnabled(nativeFill);
        NativeNoiseFill.setNativeTraversal(traversal != Traversal.JAVA);
        NativeNoiseFill.uploadSlices = traversal == Traversal.UPLOADED;
        try {
            generator.fillFromNoise(Blender.empty(), random, null, chunk).join();
        } finally {
            NativeNoiseFill.setEnabled(true);
            NativeNoiseFill.setNativeTraversal(true);
            NativeNoiseFill.uploadSlices = false;
            NativeFluidSources.setEnabled(true);
        }
        return new Filled(chunk, noiseChunk);
    }

    static byte[] network(LevelChunkSection section) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        section.write(buffer);
        byte[] bytes = new byte[buffer.readableBytes()];
        buffer.readBytes(bytes);
        return bytes;
    }

    static byte[] containerBytes(LevelChunkSection section) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        section.getStates().write(buffer);
        byte[] bytes = new byte[buffer.readableBytes()];
        buffer.readBytes(bytes);
        return bytes;
    }

    /** The saved (codec) form: palette entries, storage words and bits per entry. */
    static String saved(LevelChunkSection section) {
        var packed = section.getStates().pack(net.minecraft.world.level.chunk.Strategy.createForBlockStates(net.minecraft.world.level.block.Block.BLOCK_STATE_REGISTRY));
        return packed.paletteEntries() + " " + packed.storage().map(stream -> Arrays.toString(stream.toArray())).orElse("-") + " " + packed.bitsPerEntry();
    }

    static int field(LevelChunkSection section, String name) {
        try {
            var field = LevelChunkSection.class.getDeclaredField(name);
            field.setAccessible(true);
            return (short)field.get(section);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }

    static long oreBlocks, rawOreBlocks;

    static void compare(Filled java, Filled candidate, String context) {
        var a = java.chunk();
        var b = candidate.chunk();
        for (int index = 0; index < b.getSectionsCount(); index++) {
            b.getSection(index).getStates().count((state, count) -> {
                if (state.is(net.minecraft.world.level.block.Blocks.COPPER_ORE) || state.is(net.minecraft.world.level.block.Blocks.DEEPSLATE_IRON_ORE)) oreBlocks += count;
                if (state.is(net.minecraft.world.level.block.Blocks.RAW_COPPER_BLOCK) || state.is(net.minecraft.world.level.block.Blocks.RAW_IRON_BLOCK)) rawOreBlocks += count;
            });
        }
        assertEquals(a.getSectionsCount(), b.getSectionsCount());
        for (int index = 0; index < a.getSectionsCount(); index++) {
            LevelChunkSection left = a.getSection(index), right = b.getSection(index);
            int section = index;
            assertArrayEquals(network(left), network(right), () -> context + " section " + section + " palette/storage/count bytes");
            assertEquals(saved(left), saved(right), () -> context + " section " + section + " saved pack");
            assertEquals(field(left, "tickingBlockCount"), field(right, "tickingBlockCount"), () -> context + " ticking " + section);
            assertEquals(field(left, "tickingFluidCount"), field(right, "tickingFluidCount"), () -> context + " fluid " + section);
        }
        for (Heightmap.Types type : new Heightmap.Types[]{Heightmap.Types.OCEAN_FLOOR_WG, Heightmap.Types.WORLD_SURFACE_WG}) {
            assertArrayEquals(a.getOrCreateHeightmapUnprimed(type).getRawData(), b.getOrCreateHeightmapUnprimed(type).getRawData(),
                () -> context + " heightmap " + type);
        }
        var left = a.getPostProcessing();
        var right = b.getPostProcessing();
        for (int index = 0; index < left.length; index++) {
            int section = index;
            assertEquals(left[index] == null ? null : new ArrayList<>(left[index]), right[index] == null ? null : new ArrayList<>(right[index]),
                () -> context + " post-processing " + section);
        }
        // Later aquifer reads (carvers) through the filled chunk's shared caches.
        var pos = a.getPos();
        for (int y = a.getMinY(); y < a.getMaxY(); y += 11) {
            for (int dx = 0; dx < 16; dx += 5) {
                var point = new DensityFunction.SinglePointContext(pos.getMinBlockX() + dx, y, pos.getMinBlockZ() + 15 - dx);
                assertEquals(java.noise().aquifer().computeSubstance(point, -0.25), candidate.noise().aquifer().computeSubstance(point, -0.25),
                    () -> context + " later aquifer " + point);
            }
        }
    }

    /** The cell traversal with Rust-owned aquifer materials against the same
     * traversal asking Java for them, for every vanilla setting: sections,
     * heightmaps, post-processing and later aquifer reads must match, and every
     * aquifer on the native route must take it. */
    @Test void rustAquiferMaterialsMatchJavaPreparedMaterials() {
        long[] seeds = {0, 42, -7_340_013_412_337L};
        ChunkPos[] positions = {new ChunkPos(0, 0), new ChunkPos(-1, 3), new ChunkPos(37, -91), new ChunkPos(-6250, 4321)};
        long before = NativeNoiseFill.AQUIFER_FILLS.get();
        int pairs = 0, aquifers = 0;
        for (var setting : settings) {
            for (long seed : seeds) {
                var noises = registries.lookupOrThrow(Registries.NOISE);
                for (ChunkPos pos : positions) {
                    String context = setting.key().location() + " seed " + seed + " " + pos + " aquifer materials";
                    NativeNoiseFill.nativeAquifer = false;
                    Filled java;
                    try {
                        java = fill(setting, RandomState.create(setting.value(), noises, seed), pos, true);
                    } finally {
                        NativeNoiseFill.nativeAquifer = true;
                    }
                    long native0 = NativeNoiseFill.AQUIFER_FILLS.get();
                    var candidate = fill(setting, RandomState.create(setting.value(), noises, seed), pos, true);
                    if (setting.value().aquifersEnabled()) {
                        assertEquals(native0 + 1, NativeNoiseFill.AQUIFER_FILLS.get(), () -> context + " must prepare materials in Rust");
                        aquifers++;
                    }
                    compare(java, candidate, context);
                    pairs++;
                }
            }
        }
        // A lava sea: aquifer statuses below the sea are lava, which only an
        // aquifer-enabled setting with a lava default fluid exercises.
        var overworld = settings.stream().filter(h -> h.is(NoiseGeneratorSettings.OVERWORLD)).findFirst().orElseThrow().value();
        Holder<NoiseGeneratorSettings> lavaSea = Holder.direct(new NoiseGeneratorSettings(overworld.noiseSettings(), overworld.defaultBlock(),
            net.minecraft.world.level.block.Blocks.LAVA.defaultBlockState(), overworld.noiseRouter(), overworld.surfaceRule(), overworld.spawnTarget(),
            overworld.seaLevel(), overworld.disableMobGeneration(), true, overworld.oreVeinsEnabled(), overworld.useLegacyRandomSource()));
        for (ChunkPos pos : positions) {
            var noises = registries.lookupOrThrow(Registries.NOISE);
            NativeNoiseFill.nativeAquifer = false;
            Filled java;
            try {
                java = fill(lavaSea, RandomState.create(lavaSea.value(), noises, 5), pos, true);
            } finally {
                NativeNoiseFill.nativeAquifer = true;
            }
            long native0 = NativeNoiseFill.AQUIFER_FILLS.get();
            var candidate = fill(lavaSea, RandomState.create(lavaSea.value(), noises, 5), pos, true);
            assertEquals(native0 + 1, NativeNoiseFill.AQUIFER_FILLS.get(), () -> "lava sea " + pos + " must prepare materials in Rust");
            compare(java, candidate, "lava sea " + pos);
            pairs++;
        }
        assertTrue(aquifers > 0);
        System.out.println("FILL_AQUIFER_PARITY pairs=" + pairs + " rust_aquifers=" + (NativeNoiseFill.AQUIFER_FILLS.get() - before));
    }

    @Test void nativeFillMatchesJavaLoopForEveryVanillaSetting() {
        long[] seeds = {0, 42, -7_340_013_412_337L};
        ChunkPos[] positions = {new ChunkPos(0, 0), new ChunkPos(-1, 3), new ChunkPos(37, -91), new ChunkPos(-6250, 4321), new ChunkPos(131_000, -131_000)};
        long before = NativeNoiseFill.RUNS.get(), slicesBefore = NativeNoiseRouter.SLICES.get(), slices = 0;
        long traversalsBefore = NativeNoiseFill.TRAVERSALS.get(), instancesBefore = NativeNoiseFill.INSTANCES.get();
        int pairs = 0;
        for (var setting : settings) {
            for (long seed : seeds) {
                var noises = registries.lookupOrThrow(Registries.NOISE);
                for (ChunkPos pos : positions) {
                    String context = setting.key().location() + " seed " + seed + " " + pos;
                    var java = fill(setting, RandomState.create(setting.value(), noises, seed), pos, false);
                    var candidate = fill(setting, RandomState.create(setting.value(), noises, seed), pos, true);
                    compare(java, candidate, context);
                    pairs++;
                    slices += 16 / setting.value().noiseSettings().getCellWidth() + 1;
                }
            }
        }
        assertTrue(oreBlocks > 0 && rawOreBlocks > 0, "ore veins and raw ore blocks must be exercised: " + oreBlocks + "/" + rawOreBlocks);
        long runs = NativeNoiseFill.RUNS.get() - before;
        long nativeSlices = NativeNoiseRouter.SLICES.get() - slicesBefore, traversals = NativeNoiseFill.TRAVERSALS.get() - traversalsBefore;
        assertEquals(pairs, traversals, "every native fill must take the Rust cell traversal");
        long instances = NativeNoiseFill.INSTANCES.get() - instancesBefore;
        assertEquals(pairs, instances, "every native fill must instantiate chunk noise natively");
        System.out.println("NOISE_FILL_PARITY pairs=" + pairs + " native_runs=" + runs + " native_slices=" + nativeSlices + " traversals=" + traversals + " instances=" + instances
            + " ore_blocks=" + oreBlocks
            + " raw_ore_blocks=" + rawOreBlocks);
        assertEquals(pairs, runs, "every vanilla setting must take the native fill");
        assertEquals(slices, nativeSlices, "every native fill must take native interpolation slices");
    }

    /** Chunks beside villages, outposts, ancient cities and trial chambers: the
     * recorded structure geometry adjusts terrain through the native Beardifier
     * term of a natively instantiated chunk, as Java's cell cache adds it. */
    @Test void structureChunksMatchJavaLoop() throws Exception {
        var settingsByName = new java.util.HashMap<String, Holder.Reference<NoiseGeneratorSettings>>();
        for (var setting : settings) settingsByName.put(setting.key().location().getPath(), setting);
        long instances = NativeNoiseFill.INSTANCES.get();
        int pairs = 0;
        for (var fixture : NativeBeardifierVerification.fixtures()) {
            var cell = fixture.full().get(0);
            var pos = new ChunkPos(cell.x() >> 4, cell.z() >> 4);
            for (String name : new String[]{"overworld", "amplified"}) {
                var setting = settingsByName.get(name);
                var noises = registries.lookupOrThrow(Registries.NOISE);
                String context = name + " " + fixture.name();
                var java = fill(setting, RandomState.create(setting.value(), noises, 42), pos, false, Traversal.NATIVE,
                    Beardifier.forGeometry(fixture.pieces(), fixture.junctions(), fixture.bounds()));
                var candidate = fill(setting, RandomState.create(setting.value(), noises, 42), pos, true, Traversal.NATIVE,
                    Beardifier.forGeometry(fixture.pieces(), fixture.junctions(), fixture.bounds()));
                assertNotNull(candidate.noise().nativeBeardifier(), context + " has native structure cells");
                compare(java, candidate, context);
                pairs++;
            }
        }
        assertTrue(pairs >= 20, "structure fixtures");
        assertEquals(pairs, NativeNoiseFill.INSTANCES.get() - instances, "structure chunks must instantiate natively");
        System.out.println("STRUCTURE_FILL_PARITY pairs=" + pairs);
    }

    /** Java-filled uploaded slices and Java's per-cell loop give the same chunks. */
    @Test void cellTraversalsMatchJavaLoop() {
        long[] seeds = {0, -7_340_013_412_337L};
        ChunkPos[] positions = {new ChunkPos(0, 0), new ChunkPos(37, -91), new ChunkPos(-6250, 4321)};
        long traversals = NativeNoiseFill.TRAVERSALS.get();
        int pairs = 0;
        for (var setting : settings) {
            for (long seed : seeds) {
                var noises = registries.lookupOrThrow(Registries.NOISE);
                for (ChunkPos pos : positions) {
                    String context = setting.key().location() + " seed " + seed + " " + pos;
                    var java = fill(setting, RandomState.create(setting.value(), noises, seed), pos, false);
                    compare(java, fill(setting, RandomState.create(setting.value(), noises, seed), pos, true, Traversal.UPLOADED), context + " uploaded");
                    compare(java, fill(setting, RandomState.create(setting.value(), noises, seed), pos, true, Traversal.JAVA), context + " java cells");
                    pairs++;
                }
            }
        }
        assertEquals(pairs, NativeNoiseFill.TRAVERSALS.get() - traversals, "uploaded slices keep the Rust traversal; the Java loop does not");
        System.out.println("CELL_TRAVERSAL_PARITY pairs=" + pairs);
    }

    /** Palette growth beyond what NOISE-stage sections reach: every resize
     * (single, linear, 5..8-bit hash maps, global) against real section writes. */
    @Test void sectionReplayMatchesPalettedContainerResizes() {
        var factory = PalettedContainerFactory.create(registries);
        int checked = 0;
        for (long seed = 0; seed < 40; seed++) {
            var rng = new java.util.Random(seed);
            int distinct = new int[]{3, 15, 16, 17, 31, 33, 64, 65, 200, 255, 256, 257, 400}[(int)(seed % 13)];
            int writes = 64 + rng.nextInt(4032);
            var states = new ArrayList<net.minecraft.world.level.block.state.BlockState>();
            for (int index = 0; states.size() < distinct; index += 1 + rng.nextInt(7)) {
                var state = net.minecraft.world.level.block.Block.stateById(1 + index % (net.minecraft.world.level.block.Block.BLOCK_STATE_REGISTRY.size() - 1));
                if (!state.isAir() && !states.contains(state)) states.add(state);
            }
            var positions = new ArrayList<Integer>();
            for (int index = 0; index < 4096; index++) positions.add(index);
            java.util.Collections.shuffle(positions, rng);
            var java = new LevelChunkSection(factory);
            var candidate = new LevelChunkSection(factory);
            int[] sequence = new int[writes * 2];
            for (int write = 0; write < writes; write++) {
                int index = positions.get(write);
                var state = states.get(write < distinct ? write : rng.nextInt(distinct));
                java.setBlockState(index & 15, index >> 8, (index >> 4) & 15, state, false);
                sequence[write * 2] = index;
                sequence[write * 2 + 1] = net.minecraft.world.level.block.Block.getId(state);
            }
            NativeNoiseFill.replayInto(candidate, sequence);
            // Network bytes keep in-memory palette order; the saved form re-packs it.
            assertArrayEquals(containerBytes(java), containerBytes(candidate), "replay seed " + seed + " distinct " + distinct + " palette order");
            assertEquals(saved(java), saved(candidate), "replay seed " + seed + " distinct " + distinct);
            checked++;
        }
        System.out.println("SECTION_REPLAY_PARITY sequences=" + checked);
    }

    @Test void nativeAquiferLocationsMatchJavaRandomDraws() throws Throwable {
        var overworld = settings.stream().filter(holder -> holder.key().location().getPath().equals("overworld")).findFirst().orElseThrow();
        var config = overworld.value();
        var noises = registries.lookupOrThrow(Registries.NOISE);
        int checked = 0;
        for (long seed : new long[]{0, 42, Long.MIN_VALUE, Long.MAX_VALUE}) {
            var random = RandomState.create(config, noises, seed);
            PositionalRandomFactory[] factories = {random.aquiferRandom(), new LegacyRandomSource(seed).forkPositional(),
                new XoroshiroRandomSource(seed ^ 0x5EED).forkPositional()};
            for (PositionalRandomFactory factory : factories) {
                for (ChunkPos pos : new ChunkPos[]{new ChunkPos(0, 0), new ChunkPos(-3, 7), new ChunkPos(9000, -12000)}) {
                    var ns = config.noiseSettings();
                    var noiseChunk = new NoiseChunk(16 / ns.getCellWidth(), random, pos.getMinBlockX(), pos.getMinBlockZ(), ns,
                        DensityFunctions.BeardifierMarker.INSTANCE, config, new AquiferFluidPicker(config.seaLevel(), config.defaultFluid()), Blender.empty());
                    var router = random.router();
                    var java = (Aquifer.NoiseBasedAquifer)Aquifer.create(noiseChunk, pos, router, factory, ns.minY(), ns.height(),
                        new AquiferFluidPicker(config.seaLevel(), config.defaultFluid()));
                    var candidate = (Aquifer.NoiseBasedAquifer)Aquifer.create(noiseChunk, pos, router, factory, ns.minY(), ns.height(),
                        new AquiferFluidPicker(config.seaLevel(), config.defaultFluid()));
                    int width = ns.getCellWidth(), height = ns.getCellHeight();
                    for (int cy = ns.minY(); cy < ns.minY() + ns.height(); cy += height) {
                        for (int cx = 0; cx < 16; cx += width) {
                            for (int cz = 0; cz < 16; cz += width) {
                                java.cellLocations(pos.getMinBlockX() + cx, cy, pos.getMinBlockZ() + cz, width, height);
                                candidate.nativeAquifer().cellLocations(pos.getMinBlockX() + cx, cy, pos.getMinBlockZ() + cz);
                            }
                        }
                    }
                    assertArrayEquals(java.locationCache(), candidate.locationCache(), () -> factory.getClass().getSimpleName() + " seed " + seed + " " + pos);
                    checked += Arrays.stream(java.locationCache()).filter(value -> value != Long.MAX_VALUE).count();
                }
            }
        }
        System.out.println("AQUIFER_LOCATION_PARITY locations=" + checked);
        assertTrue(checked > 10_000);
    }

    /** Rust derives each state's fill flags from its block registry; check every state against Java. */
    @Test void stateFlagsMatchEveryState() throws Throwable {
        assertTrue(net.minecraft.world.level.block.NativeBlockRegistry.ready());
        var call = net.minecraft.util.NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_noise_fill_state_flags",
            java.lang.foreign.FunctionDescriptor.of(java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.ADDRESS,
                java.lang.foreign.ValueLayout.JAVA_INT));
        int states = net.minecraft.world.level.block.Block.BLOCK_STATE_REGISTRY.size();
        byte[] flags = new byte[states];
        try (var arena = java.lang.foreign.Arena.ofConfined()) {
            var out = arena.allocate(states);
            assertEquals(states, (int)call.invokeExact(out, states));
            java.lang.foreign.MemorySegment.copy(out, java.lang.foreign.ValueLayout.JAVA_BYTE, 0, flags, 0, states);
        }
        for (int id = 0; id < states; id++) {
            var state = net.minecraft.world.level.block.Block.BLOCK_STATE_REGISTRY.byId(id);
            int expected = (state.isAir() ? 1 : 0) | (state.blocksMotion() ? 2 : 0) | (!state.getFluidState().isEmpty() ? 4 : 0)
                | (state.isRandomlyTicking() ? 8 : 0) | (state.is(net.minecraft.world.level.block.Blocks.AIR) ? 16 : 0);
            assertEquals(expected, flags[id], "flags of " + state);
        }
        System.out.println("NOISE_FLAGS_PARITY states=" + states);
    }
}
