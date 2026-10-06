package net.minecraft.world.level.levelgen;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Exact parity of the SURFACE stage on Rust-owned storage with the same stage
 * writing Java sections: section palettes, storage, network and saved forms,
 * counters, both heightmaps and post-processing marks. */
class NativeSurfaceChunkTest {
    @BeforeAll static void load() {
        NativeNoiseFillTest.load();
    }

    @AfterAll static void close() {
        NativeSurfaceChunk.setEnabled(true);
        NativeNoiseFillTest.close();
    }

    /** Quart biomes cycling through every biome, so biome conditions, the eroded
     * badlands and frozen ocean extensions and steep all run within a chunk. */
    static BiomeManager mixed(long seed, int salt) {
        List<Holder.Reference<Biome>> biomes = NativeNoiseFillTest.registries.lookupOrThrow(Registries.BIOME).listElements()
            .sorted(Comparator.comparing(holder -> holder.key().location().toString())).toList();
        return new BiomeManager((x, y, z) -> biomes.get(Math.floorMod(x * 31 + (y >> 2) * 17 + z * 71 + salt, biomes.size())), BiomeManager.obfuscateSeed(seed));
    }

    static NativeNoiseFillTest.Filled surface(Holder<NoiseGeneratorSettings> setting, long seed, ChunkPos pos, int salt, boolean owned) {
        return surface(setting, seed, pos, salt, owned, setting.value().surfaceRule());
    }

    static NativeNoiseFillTest.Filled surface(Holder<NoiseGeneratorSettings> setting, long seed, ChunkPos pos, int salt, boolean owned,
                                              SurfaceRules.RuleSource rule) {
        var random = RandomState.create(setting.value(), NativeNoiseFillTest.registries.lookupOrThrow(Registries.NOISE), seed);
        var filled = NativeNoiseFillTest.fill(setting, random, pos, true);
        var chunk = filled.chunk();
        // As at the SURFACE stage: the NOISE status persisted, its heightmaps primed.
        chunk.setPersistedStatus(ChunkStatus.NOISE);
        var generator = new NoiseBasedChunkGenerator(new FixedBiomeSource(NativeNoiseFillTest.registries.lookupOrThrow(Registries.BIOME)
            .getOrThrow(net.minecraft.world.level.biome.Biomes.PLAINS)), setting);
        var config = setting.value();
        NativeSurfaceChunk.setEnabled(owned);
        try {
            random.surfaceSystem().buildSurface(random, mixed(seed, salt), NativeNoiseFillTest.registries.lookupOrThrow(Registries.BIOME),
                config.useLegacyRandomSource(), new WorldGenerationContext(generator, chunk), chunk, filled.noise(), rule);
        } finally {
            NativeSurfaceChunk.setEnabled(true);
        }
        return filled;
    }

    @Test void ownedSurfaceMatchesJavaStorageForEveryVanillaSetting() {
        long[] seeds = {0, -7_340_013_412_337L};
        ChunkPos[] positions = {new ChunkPos(0, 0), new ChunkPos(-1, 3), new ChunkPos(-6250, 4321), new ChunkPos(131_000, -131_000)};
        long before = NativeSurfaceChunk.CHUNKS.get();
        int pairs = 0;
        for (var setting : NativeNoiseFillTest.settings) {
            for (long seed : seeds) {
                for (int index = 0; index < positions.length; index++) {
                    String context = setting.key().location() + " seed " + seed + " " + positions[index];
                    var java = surface(setting, seed, positions[index], index, false);
                    long owned = NativeSurfaceChunk.CHUNKS.get();
                    var candidate = surface(setting, seed, positions[index], index, true);
                    assertEquals(owned + 1, NativeSurfaceChunk.CHUNKS.get(), () -> context + " must run on Rust storage");
                    NativeNoiseFillTest.compare(java, candidate, context);
                    pairs++;
                }
            }
        }
        System.out.println("SURFACE_CHUNK_PARITY pairs=" + pairs + " owned=" + (NativeSurfaceChunk.CHUNKS.get() - before));
    }

    /** buildSurface over a fixture with either storage; the fingerprint covers
     * every block, primed heightmap and post-processing list. */
    static String fingerprint(NativeSurfaceVerification.Fixture fixture, SurfaceRules.RuleSource rule, boolean owned) throws Exception {
        NativeSurfaceChunk.setEnabled(owned);
        try {
            fixture.run(rule);
        } finally {
            NativeSurfaceChunk.setEnabled(true);
        }
        return NativeSurfaceVerification.fingerprint(fixture.chunk());
    }

    /** Two steep conditions are two slots of one shared lazy condition. The first
     * is asked at the floor; air then replaces the top six blocks, lowering the
     * column's own height, which an edge column's steep check reads. When the
     * second slot is asked it must keep the first answer. */
    static SurfaceRules.RuleSource sharedSteep() {
        return SurfaceRules.sequence(
            SurfaceRules.ifTrue(SurfaceRules.ON_FLOOR, SurfaceRules.ifTrue(SurfaceRules.steep(), SurfaceRules.state(Blocks.DIRT.defaultBlockState()))),
            SurfaceRules.ifTrue(SurfaceRules.stoneDepthCheck(5, false, net.minecraft.world.level.levelgen.placement.CaveSurface.FLOOR),
                SurfaceRules.state(Blocks.AIR.defaultBlockState())),
            SurfaceRules.ifTrue(SurfaceRules.steep(), SurfaceRules.state(Blocks.CLAY.defaultBlockState())),
            SurfaceRules.state(Blocks.GRAVEL.defaultBlockState()));
    }

    @Test void sharedSteepAnswerHoldsOnRealTerrain() {
        int pairs = 0;
        for (var setting : NativeNoiseFillTest.settings) {
            if (!setting.is(NoiseGeneratorSettings.OVERWORLD) && !setting.is(NoiseGeneratorSettings.AMPLIFIED)) continue;
            for (long seed : new long[]{0, 42}) {
                for (ChunkPos pos : new ChunkPos[]{new ChunkPos(0, 0), new ChunkPos(-1, 3), new ChunkPos(37, -91)}) {
                    String context = setting.key().location() + " seed " + seed + " " + pos + " shared steep";
                    var java = surface(setting, seed, pos, 0, false, sharedSteep());
                    var candidate = surface(setting, seed, pos, 0, true, sharedSteep());
                    NativeNoiseFillTest.compare(java, candidate, context);
                    pairs++;
                }
            }
        }
        assertEquals(12, pairs);
    }

    @Test void syntheticColumnsAndCustomRulesMatchJavaStorage() throws Exception {
        var registries = NativeNoiseFillTest.registries;
        var noises = registries.lookupOrThrow(Registries.NOISE);
        SurfaceRules.RuleSource sharedSteep = sharedSteep();
        int chunks = 0;
        long before = NativeSurfaceChunk.CHUNKS.get();
        for (var setting : NativeNoiseFillTest.settings) {
            var config = setting.value();
            for (long seed : new long[]{42, Long.MIN_VALUE}) {
                var random = RandomState.create(config, noises, seed);
                var rules = new java.util.ArrayList<SurfaceRules.RuleSource>(NativeSurfaceVerification.customRules());
                rules.add(config.surfaceRule());
                rules.add(sharedSteep);
                for (int variant = 0; variant < rules.size() * 2; variant++) {
                    var rule = rules.get(variant % rules.size());
                    int[] location = NativeSurfaceVerification.POSITIONS[variant % NativeSurfaceVerification.POSITIONS.length];
                    boolean mixed = variant >= rules.size();
                    String java = fingerprint(NativeSurfaceVerification.fixture(registries, config, random, seed, variant, location, mixed), rule, false);
                    String owned = fingerprint(NativeSurfaceVerification.fixture(registries, config, random, seed, variant, location, mixed), rule, true);
                    int at = variant;
                    assertEquals(java, owned, () -> setting.key().location() + " seed " + seed + " rule " + at % rules.size() + " mixed " + mixed);
                    chunks++;
                }
            }
        }
        assertTrue(NativeSurfaceChunk.CHUNKS.get() - before > chunks / 2, "most synthetic chunks must run on Rust storage");
        System.out.println("SURFACE_CHUNK_SYNTHETIC chunks=" + chunks + " owned=" + (NativeSurfaceChunk.CHUNKS.get() - before));
    }

    @Test void gatesKeepJavaStorage() throws Exception {
        var registries = NativeNoiseFillTest.registries;
        var setting = NativeNoiseFillTest.settings.stream().filter(holder -> holder.is(NoiseGeneratorSettings.OVERWORLD)).findFirst().orElseThrow();
        var config = setting.value();
        var random = RandomState.create(config, registries.lookupOrThrow(Registries.NOISE), 7);
        var fixture = NativeSurfaceVerification.fixture(registries, config, random, 7, 3, new int[]{2, 5}, true);
        var context = new SurfaceRules.Context(random.surfaceSystem(), random, fixture.chunk(), fixture.noise(), fixture.manager()::getBiome,
            fixture.biomes(), fixture.context());
        var rule = new NativeSurface(config.surfaceRule(), context, fixture.biomes(), fixture.manager());
        var biomes = fixture.biomes();
        var stone = config.defaultBlock();
        try (var owned = NativeSurfaceChunk.create(fixture.chunk(), rule, fixture.manager(), biomes, stone)) {
            assertNotNull(owned, "a NOISE ProtoChunk with primed heightmaps runs on Rust storage");
        }
        NativeSurfaceChunk.setEnabled(false);
        try {
            assertNull(NativeSurfaceChunk.create(fixture.chunk(), rule, fixture.manager(), biomes, stone), "disabled");
        } finally {
            NativeSurfaceChunk.setEnabled(true);
        }
        // Extension-owned conditions keep incremental Java writes.
        var custom = new NativeSurface(NativeSurfaceVerification.customRules().getLast(), context, biomes, fixture.manager());
        assertNull(NativeSurfaceChunk.create(fixture.chunk(), custom, fixture.manager(), biomes, stone), "unbatched rule");
        // After light initialization setBlockState also updates lighting.
        fixture.chunk().setPersistedStatus(ChunkStatus.INITIALIZE_LIGHT);
        assertNull(NativeSurfaceChunk.create(fixture.chunk(), rule, fixture.manager(), biomes, stone), "lit chunk");
        // Unprimed heightmaps are primed by setBlockState itself.
        var unprimed = NativeSurfaceVerification.fixture(registries, config, random, 7, 4, new int[]{0, 0}, false);
        var fresh = new net.minecraft.world.level.chunk.ProtoChunk(unprimed.chunk().getPos(), net.minecraft.world.level.chunk.UpgradeData.EMPTY,
            LevelHeightAccessor.create(config.noiseSettings().minY(), config.noiseSettings().height()),
            net.minecraft.world.level.chunk.PalettedContainerFactory.create(registries), null);
        fresh.setPersistedStatus(ChunkStatus.NOISE);
        var freshContext = new SurfaceRules.Context(random.surfaceSystem(), random, fresh, unprimed.noise(), unprimed.manager()::getBiome, biomes, unprimed.context());
        var freshRule = new NativeSurface(config.surfaceRule(), freshContext, biomes, unprimed.manager());
        assertNull(NativeSurfaceChunk.create(fresh, freshRule, unprimed.manager(), biomes, stone), "unprimed heightmaps");
        Heightmap.primeHeightmaps(fresh, EnumSet.of(Heightmap.Types.WORLD_SURFACE_WG, Heightmap.Types.OCEAN_FLOOR_WG));
        try (var owned = NativeSurfaceChunk.create(fresh, freshRule, unprimed.manager(), biomes, stone)) {
            assertNotNull(owned, "an empty chunk is still modelled");
        }
    }
}
