package net.minecraft.world.level.levelgen;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import net.minecraft.world.level.levelgen.placement.CaveSurface;

/**
 * The Rust column pass against Java's column loop over the same Rust storage
 * (and against plain Java storage): blocks, heightmaps and marks, the
 * per-thread biome temperature caches (entries and order), the context Java
 * sees before every answer, the chunks that keep Java's loop, and a Java answer
 * that throws.
 */
class NativeSurfacePassTest {
    private static final long[] SEEDS = {0, -7_340_013_412_337L};
    private static ChunkPos[] POSITIONS;

    @BeforeAll static void load() {
        NativeNoiseFillTest.load();
        POSITIONS = new ChunkPos[]{new ChunkPos(0, 0), new ChunkPos(-1, 3), new ChunkPos(-6250, 4321), new ChunkPos(131_000, -131_000)};
    }

    @AfterAll static void close() {
        NativeNoiseFillTest.close();
    }

    @AfterEach void reset() {
        NativeSurfaceChunk.setPassEnabled(true);
        NativeSurfaceChunk.setEnabled(true);
        NativeSurface.respondHook = null;
    }

    enum Route { JAVA_STORAGE, JAVA_LOOP, PASS }

    static NativeNoiseFillTest.Filled run(Holder<NoiseGeneratorSettings> setting, long seed, ChunkPos pos, int salt, Route route, SurfaceRules.RuleSource rule) {
        NativeSurfaceChunk.setPassEnabled(route == Route.PASS);
        try {
            return NativeSurfaceChunkTest.surface(setting, seed, pos, salt, route != Route.JAVA_STORAGE, rule);
        } finally {
            NativeSurfaceChunk.setPassEnabled(true);
        }
    }

    static List<Holder<Biome>> biomes() {
        return new ArrayList<>(NativeNoiseFillTest.registries.lookupOrThrow(Registries.BIOME).listElements().toList());
    }

    /** A rule that asks Java's temperature at every block before the setting's own rules. */
    static SurfaceRules.RuleSource withTemperature(Holder<NoiseGeneratorSettings> setting) {
        return SurfaceRules.sequence(SurfaceRules.ifTrue(SurfaceRules.temperature(), SurfaceRules.state(Blocks.SNOW_BLOCK.defaultBlockState())),
            setting.value().surfaceRule());
    }

    @Test void passMatchesJavaLoopAndStorageForEveryVanillaSetting() {
        long passBefore = NativeSurfaceChunk.PASS_CHUNKS.get(), meltBefore = NativeSurfaceChunk.PASS_MELT_CALLS.get();
        int pairs = 0, passChunks = 0;
        for (var setting : NativeNoiseFillTest.settings) {
            for (long seed : SEEDS) {
                for (int index = 0; index < POSITIONS.length; index++) {
                    String context = setting.key().location() + " seed " + seed + " " + POSITIONS[index];
                    var rule = setting.value().surfaceRule();
                    // Java storage and the pass start from the same temperature caches.
                    var caches = NativeFreezeVerification.begin(biomes(), null, BlockPos.ZERO);
                    var java = run(setting, seed, POSITIONS[index], index, Route.JAVA_STORAGE, rule);
                    caches.afterNative(0);
                    long started = NativeSurfaceChunk.PASS_CHUNKS.get();
                    var pass = run(setting, seed, POSITIONS[index], index, Route.PASS, rule);
                    passChunks += (int)(NativeSurfaceChunk.PASS_CHUNKS.get() - started);
                    assertTrue(caches.afterJava(0), () -> context + ": temperature caches after the pass: " + caches.difference);
                    caches.finish();
                    var loop = run(setting, seed, POSITIONS[index], index, Route.JAVA_LOOP, rule);
                    NativeNoiseFillTest.compare(java, pass, context + " (pass)");
                    NativeNoiseFillTest.compare(java, loop, context + " (Java loop)");
                    pairs++;
                }
            }
        }
        long melts = NativeSurfaceChunk.PASS_MELT_CALLS.get() - meltBefore;
        assertTrue(passChunks > pairs / 2, "most chunks must run the pass: " + passChunks + " of " + pairs);
        assertTrue(melts > 0, "the corpus must reach the frozen ocean melt check");
        System.out.println("SURFACE_PASS_PARITY pairs=" + pairs + " pass=" + (NativeSurfaceChunk.PASS_CHUNKS.get() - passBefore) + " melts=" + melts);
    }

    /** Context fields a Java answer can read: block X/Z, surface depth, both counters. */
    static List<String> contexts(Holder<NoiseGeneratorSettings> setting, long seed, ChunkPos pos, int salt, Route route, SurfaceRules.RuleSource rule) {
        List<String> seen = new ArrayList<>();
        NativeSurface.respondHook = context -> seen.add(context.blockX + "," + context.blockZ + " depth " + context.surfaceDepth
            + " xz " + context.lastUpdateXZ + " y " + context.lastUpdateY);
        try {
            run(setting, seed, pos, salt, route, rule);
        } finally {
            NativeSurface.respondHook = null;
        }
        return seen;
    }

    @Test void javaAnswersSeeTheContextJavasLoopLeaves() {
        var setting = NativeNoiseFillTest.settings.stream().filter(s -> s.is(NoiseGeneratorSettings.OVERWORLD)).findFirst().orElseThrow();
        int answers = 0;
        for (int index = 0; index < POSITIONS.length; index++) {
            var rule = withTemperature(setting);
            var cache = NativeFreezeVerification.begin(biomes(), null, BlockPos.ZERO);
            List<String> loop = contexts(setting, SEEDS[1], POSITIONS[index], index, Route.JAVA_LOOP, rule);
            cache.afterNative(0);
            long started = NativeSurfaceChunk.PASS_CHUNKS.get();
            List<String> pass = contexts(setting, SEEDS[1], POSITIONS[index], index, Route.PASS, rule);
            assertEquals(started + 1, NativeSurfaceChunk.PASS_CHUNKS.get(), "the temperature rule must run the pass");
            assertEquals(loop, pass, "the context before every Java answer");
            assertTrue(cache.afterJava(0), () -> "temperature caches: " + cache.difference);
            cache.finish();
            answers += pass.size();
        }
        assertTrue(answers > 1000, "the temperature rule must ask Java: " + answers);
    }

    @Test void rulesWithoutBiomesKeepJavasLoop() {
        var setting = NativeNoiseFillTest.settings.stream().filter(s -> s.is(NoiseGeneratorSettings.OVERWORLD)).findFirst().orElseThrow();
        var rule = SurfaceRules.ifTrue(SurfaceRules.stoneDepthCheck(0, false, CaveSurface.FLOOR), SurfaceRules.state(Blocks.SNOW_BLOCK.defaultBlockState()));
        long chunks = NativeSurfaceChunk.CHUNKS.get(), passes = NativeSurfaceChunk.PASS_CHUNKS.get();
        var java = run(setting, 1L, POSITIONS[1], 1, Route.JAVA_STORAGE, rule);
        var candidate = run(setting, 1L, POSITIONS[1], 1, Route.PASS, rule);
        NativeNoiseFillTest.compare(java, candidate, "rule without biomes");
        assertEquals(chunks + 1, NativeSurfaceChunk.CHUNKS.get(), "the chunk still runs on Rust storage");
        assertEquals(passes, NativeSurfaceChunk.PASS_CHUNKS.get(), "without the quart biome table the column loop stays Java");
    }

    /** NativeSurfaceChunkTest.surface on a chunk the caller keeps. */
    static void build(Holder<NoiseGeneratorSettings> setting, RandomState random, NativeNoiseFillTest.Filled filled, long seed, int salt, Route route,
                      SurfaceRules.RuleSource rule) {
        var chunk = filled.chunk();
        chunk.setPersistedStatus(net.minecraft.world.level.chunk.status.ChunkStatus.NOISE);
        var generator = new NoiseBasedChunkGenerator(new net.minecraft.world.level.biome.FixedBiomeSource(NativeNoiseFillTest.registries
            .lookupOrThrow(Registries.BIOME).getOrThrow(net.minecraft.world.level.biome.Biomes.PLAINS)), setting);
        NativeSurfaceChunk.setPassEnabled(route == Route.PASS);
        try {
            random.surfaceSystem().buildSurface(random, NativeSurfaceChunkTest.mixed(seed, salt), NativeNoiseFillTest.registries.lookupOrThrow(Registries.BIOME),
                setting.value().useLegacyRandomSource(), new WorldGenerationContext(generator, chunk), chunk, filled.noise(), rule);
        } finally {
            NativeSurfaceChunk.setPassEnabled(true);
        }
    }

    @Test void aThrowingJavaAnswerLeavesTheChunkAsJavasLoopDoes() {
        var setting = NativeNoiseFillTest.settings.stream().filter(s -> s.is(NoiseGeneratorSettings.OVERWORLD)).findFirst().orElseThrow();
        var rule = withTemperature(setting);
        for (int limit : new int[]{1, 777}) {
            NativeNoiseFillTest.Filled[] filled = new NativeNoiseFillTest.Filled[2];
            int[] calls = new int[2];
            for (Route route : new Route[]{Route.JAVA_LOOP, Route.PASS}) {
                int at = route.ordinal() - 1;
                RuntimeException failure = new RuntimeException("answer " + limit);
                var random = RandomState.create(setting.value(), NativeNoiseFillTest.registries.lookupOrThrow(Registries.NOISE), 5L);
                filled[at] = NativeNoiseFillTest.fill(setting, random, POSITIONS[2], true);
                NativeSurface.respondHook = context -> {
                    if (++calls[at] == limit) throw failure;
                };
                try {
                    var thrown = assertThrows(RuntimeException.class, () -> build(setting, random, filled[at], 5L, 2, route, rule));
                    assertSame(failure, thrown, "the Java answer's exception propagates by identity");
                } finally {
                    NativeSurface.respondHook = null;
                }
            }
            assertEquals(calls[0], calls[1], "both routes stop at the same answer");
            // Neither route installs the stage: the chunk keeps its NOISE blocks.
            NativeNoiseFillTest.compare(filled[0], filled[1], "after answer " + limit + " threw");
            var untouched = NativeNoiseFillTest.fill(setting, RandomState.create(setting.value(), NativeNoiseFillTest.registries.lookupOrThrow(Registries.NOISE), 5L),
                POSITIONS[2], true);
            untouched.chunk().setPersistedStatus(net.minecraft.world.level.chunk.status.ChunkStatus.NOISE);
            NativeNoiseFillTest.compare(untouched, filled[1], "nothing installed after answer " + limit + " threw");
        }
    }
}
