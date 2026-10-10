package net.minecraft.world.level.levelgen;

import org.junit.jupiter.api.Tag;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Exact parity of the Rust-owned CARVERS stage with Java's carvers: chunks
 * filled and surfaced by the production stages, then carved through
 * {@code carveChunk} both ways. Sections (network and saved forms, counters),
 * heightmaps, post-processing marks, later aquifer reads, the carving mask and
 * the aquifer's schedule flag must match. */
@Tag("parity")
class NativeCarversTest {
    @BeforeAll static void load() {
        NativeNoiseFillTest.load();
        bindStaticTags();
    }

    /** Binds the vanilla pack's block tags (carvers' replaceable sets), as a
     * server's resource reload does. */
    static void bindStaticTags() {
        var layers = net.minecraft.server.RegistryLayer.createRegistryAccess();
        net.minecraft.tags.TagLoader.loadTagsForExistingRegistries(NativeNoiseFillTest.resources, layers.getLayer(net.minecraft.server.RegistryLayer.STATIC))
            .forEach(net.minecraft.core.Registry.PendingTags::apply);
    }

    @AfterAll static void close() {
        NativeCarvers.setEnabled(true);
        NativeNoiseFillTest.close();
    }

    static MultiNoiseBiomeSource source(Holder<NoiseGeneratorSettings> setting) {
        var key = setting.is(NoiseGeneratorSettings.NETHER) ? MultiNoiseBiomeSourceParameterLists.NETHER : MultiNoiseBiomeSourceParameterLists.OVERWORLD;
        return MultiNoiseBiomeSource.createFromPreset(NativeNoiseFillTest.registries.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST).getOrThrow(key));
    }

    /** A chunk through NOISE and SURFACE, then CARVERS with Java's or the native carvers. */
    static NativeNoiseFillTest.Filled carve(Holder<NoiseGeneratorSettings> setting, long seed, ChunkPos pos, boolean nativeCarvers) {
        var registries = NativeNoiseFillTest.registries;
        var config = setting.value();
        var random = RandomState.create(config, registries.lookupOrThrow(Registries.NOISE), seed);
        var filled = NativeNoiseFillTest.fill(setting, random, pos, true);
        var chunk = filled.chunk();
        chunk.setPersistedStatus(ChunkStatus.NOISE);
        var biomeSource = source(setting);
        var generator = new NoiseBasedChunkGenerator(biomeSource, setting);
        var manager = new BiomeManager((x, y, z) -> biomeSource.getNoiseBiome(x, y, z, random.sampler()), BiomeManager.obfuscateSeed(seed));
        random.surfaceSystem().buildSurface(random, manager, registries.lookupOrThrow(Registries.BIOME), config.useLegacyRandomSource(),
            new WorldGenerationContext(generator, chunk), chunk, filled.noise(), config.surfaceRule());
        chunk.setPersistedStatus(ChunkStatus.SURFACE);
        Map<Long, ChunkAccess> neighbours = new HashMap<>();
        var height = LevelHeightAccessor.create(config.noiseSettings().minY(), config.noiseSettings().height());
        var containers = PalettedContainerFactory.create(registries);
        NoiseBasedChunkGenerator.CarverChunks chunks = (x, z) -> x == pos.x && z == pos.z ? chunk
            : neighbours.computeIfAbsent(ChunkPos.asLong(x, z), key -> new ProtoChunk(new ChunkPos(x, z), UpgradeData.EMPTY, height, containers, null));
        NativeCarvers.setEnabled(nativeCarvers);
        try {
            generator.carveChunk(chunks, registries, seed, random, manager, chunk, filled.noise());
        } finally {
            NativeCarvers.setEnabled(true);
        }
        return filled;
    }

    static void compare(NativeNoiseFillTest.Filled java, NativeNoiseFillTest.Filled candidate, String context) {
        assertArrayEquals(java.chunk().getOrCreateCarvingMask().toArray(), candidate.chunk().getOrCreateCarvingMask().toArray(), () -> context + " carving mask");
        assertEquals(java.noise().aquifer().shouldScheduleFluidUpdate(), candidate.noise().aquifer().shouldScheduleFluidUpdate(), () -> context + " schedule flag");
        NativeNoiseFillTest.compare(java, candidate, context);
    }

    @Test void nativeCarversMatchJavaForEveryVanillaSetting() {
        ChunkPos[] positions = {new ChunkPos(0, 0), new ChunkPos(-1, 3), new ChunkPos(37, -91), new ChunkPos(-6250, 4321)};
        long before = NativeCarvers.CHUNKS.get(), topBefore = NativeCarvers.TOP_CALLS.get();
        int pairs = 0;
        for (var setting : NativeNoiseFillTest.settings) {
            for (long seed : new long[]{0, -7_340_013_412_337L, 42}) {
                for (ChunkPos pos : positions) {
                    String context = setting.key().location() + " seed " + seed + " " + pos;
                    var java = carve(setting, seed, pos, false);
                    long carved = NativeCarvers.CHUNKS.get();
                    var candidate = carve(setting, seed, pos, true);
                    assertEquals(carved + 1, NativeCarvers.CHUNKS.get(), () -> context + " must carve natively");
                    compare(java, candidate, context);
                    pairs++;
                }
            }
        }
        System.out.println("CARVERS_PARITY pairs=" + pairs + " native=" + (NativeCarvers.CHUNKS.get() - before)
            + " top_calls=" + (NativeCarvers.TOP_CALLS.get() - topBefore));
    }

    @Test void gatesKeepJavaCarving() {
        var registries = NativeNoiseFillTest.registries;
        var carvers = registries.lookupOrThrow(Registries.CONFIGURED_CARVER).listElements().toList();
        assertFalse(carvers.isEmpty());
        for (var carver : carvers) assertTrue(NativeCarvers.supports(carver.value()), carver.key() + " is built in");
        // A subclass may override createTunnel or carveBlock: its carving stays Java.
        var custom = new net.minecraft.world.level.levelgen.carver.CaveWorldCarver(net.minecraft.world.level.levelgen.carver.CaveCarverConfiguration.CODEC) {};
        var config = (net.minecraft.world.level.levelgen.carver.CaveCarverConfiguration)carvers.stream()
            .map(holder -> holder.value().config()).filter(c -> c instanceof net.minecraft.world.level.levelgen.carver.CaveCarverConfiguration).findFirst().orElseThrow();
        assertFalse(NativeCarvers.supports(custom.configured(config)));
        // Top materials may only read heightmaps: vanilla rules do, a custom condition does not.
        for (var setting : NativeNoiseFillTest.settings) assertTrue(NativeSurface.readsOnlyHeightmaps(setting.value().surfaceRule()), setting.key() + " rule");
        var reading = SurfaceRules.ifTrue(new SurfaceRules.ConditionSource() {
            public net.minecraft.util.KeyDispatchDataCodec<? extends SurfaceRules.ConditionSource> codec() { throw new UnsupportedOperationException(); }
            public SurfaceRules.Condition apply(SurfaceRules.Context c) { return () -> true; }
        }, SurfaceRules.state(net.minecraft.world.level.block.Blocks.CLAY.defaultBlockState()));
        assertFalse(NativeSurface.readsOnlyHeightmaps(reading));
        // Chunks with a blending mask or the route disabled keep Java's carvers.
        var overworld = NativeNoiseFillTest.settings.stream().filter(h -> h.is(NoiseGeneratorSettings.OVERWORLD)).findFirst().orElseThrow();
        var random = RandomState.create(overworld.value(), registries.lookupOrThrow(Registries.NOISE), 3);
        var filled = NativeNoiseFillTest.fill(overworld, random, new ChunkPos(2, 2), true);
        var generator = new NoiseBasedChunkGenerator(source(overworld), overworld);
        var context = new net.minecraft.world.level.levelgen.carver.CarvingContext(generator, registries, filled.chunk(), filled.noise(), random,
            overworld.value().surfaceRule());
        assertTrue(NativeCarvers.eligible(filled.chunk(), filled.noise(), context));
        NativeCarvers.setEnabled(false);
        try {
            assertFalse(NativeCarvers.eligible(filled.chunk(), filled.noise(), context), "disabled");
        } finally {
            NativeCarvers.setEnabled(true);
        }
        filled.chunk().getOrCreateCarvingMask().setAdditionalMask((x, y, z) -> false);
        assertFalse(NativeCarvers.eligible(filled.chunk(), filled.noise(), context), "blending mask");
    }
}
