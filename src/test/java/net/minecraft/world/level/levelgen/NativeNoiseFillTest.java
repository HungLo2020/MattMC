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

    static Filled fill(Holder<NoiseGeneratorSettings> setting, RandomState random, ChunkPos pos, boolean nativeFill) {
        var config = setting.value();
        var noise = config.noiseSettings();
        var biome = registries.lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS);
        var generator = new NoiseBasedChunkGenerator(new FixedBiomeSource(biome), setting);
        var chunk = new ProtoChunk(pos, UpgradeData.EMPTY, LevelHeightAccessor.create(noise.minY(), noise.height()),
            PalettedContainerFactory.create(registries), null);
        // The generator's own NoiseChunk inputs, with no structures and no blending.
        var noiseChunk = chunk.getOrCreateNoiseChunk(access -> NoiseChunk.forChunk(access, random, DensityFunctions.BeardifierMarker.INSTANCE,
            config, new AquiferFluidPicker(config.seaLevel(), config.defaultFluid()), Blender.empty()));
        NativeNoiseFill.setEnabled(nativeFill);
        try {
            generator.fillFromNoise(Blender.empty(), random, null, chunk).join();
        } finally {
            NativeNoiseFill.setEnabled(true);
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

    @Test void nativeFillMatchesJavaLoopForEveryVanillaSetting() {
        long[] seeds = {0, 42, -7_340_013_412_337L};
        ChunkPos[] positions = {new ChunkPos(0, 0), new ChunkPos(-1, 3), new ChunkPos(37, -91), new ChunkPos(-6250, 4321), new ChunkPos(131_000, -131_000)};
        long before = NativeNoiseFill.RUNS.get();
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
                }
            }
        }
        assertTrue(oreBlocks > 0 && rawOreBlocks > 0, "ore veins and raw ore blocks must be exercised: " + oreBlocks + "/" + rawOreBlocks);
        long runs = NativeNoiseFill.RUNS.get() - before;
        System.out.println("NOISE_FILL_PARITY pairs=" + pairs + " native_runs=" + runs + " ore_blocks=" + oreBlocks + " raw_ore_blocks=" + rawOreBlocks);
        assertEquals(pairs, runs, "every vanilla setting must take the native fill");
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
}
