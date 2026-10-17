package net.minecraft.world.level.levelgen;

import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Exact parity of aquifer fluid statuses computed with Rust-owned noise sources
 * and surface levels against Java answering each source request, for every
 * aquifer-enabled vanilla setting. */
class NativeFluidSourcesTest {
    @BeforeAll static void load() {
        NativeNoiseRouterTest.load();
    }

    @AfterAll static void close() {
        NativeFluidSources.setEnabled(true);
        NativeNoiseRouterTest.close();
    }

    /** A chunk whose aquifer binds native or Java sources when first used. */
    static Aquifer.NoiseBasedAquifer aquifer(NoiseGeneratorSettings config, RandomState random, ChunkPos pos, boolean nativeSources) {
        NativeFluidSources.setEnabled(nativeSources);
        try {
            var aquifer = (Aquifer.NoiseBasedAquifer)NativeNoiseRouterTest.chunk(config, random, pos).aquifer();
            aquifer.nativeAquifer();
            return aquifer;
        } finally {
            NativeFluidSources.setEnabled(true);
        }
    }

    static long statuses, points, values;

    static void compare(NoiseGeneratorSettings config, RandomState random, ChunkPos pos, String context) {
        long before = NativeFluidSources.STATUSES.get();
        var java = aquifer(config, random, pos, false);
        var candidate = aquifer(config, random, pos, true);
        var noise = config.noiseSettings();
        int width = noise.getCellWidth(), height = noise.getCellHeight();
        // Every aquifer centre the fill's cells draw, then each centre's status.
        for (int y = noise.minY(); y < noise.minY() + noise.height(); y += height) {
            for (int x = 0; x < 16; x += width) {
                for (int z = 0; z < 16; z += width) {
                    java.cellLocations(pos.getMinBlockX() + x, y, pos.getMinBlockZ() + z, width, height);
                    candidate.cellLocations(pos.getMinBlockX() + x, y, pos.getMinBlockZ() + z, width, height);
                }
            }
        }
        long[] locations = java.locationCache();
        assertArrayEquals(locations, candidate.locationCache());
        for (int index = 0; index < locations.length; index++) {
            if (locations[index] == Long.MAX_VALUE) continue;
            int at = index;
            assertEquals(java.getAquiferStatus(index), candidate.getAquiferStatus(index), () -> context + " status " + at);
            points++;
        }
        // Arbitrary points across the aquifer's region, inside and outside the
        // chunk's FlatCache grid (the chunk plus one quart).
        var rng = new java.util.Random(pos.toLong() ^ context.hashCode());
        for (int sample = 0; sample < 60; sample++) {
            int x = pos.getMinBlockX() - 10 + rng.nextInt(36), z = pos.getMinBlockZ() - 2 + rng.nextInt(28);
            int y = noise.minY() + rng.nextInt(noise.height());
            assertEquals(java.nativeAquifer().fluid(x, y, z), candidate.nativeAquifer().fluid(x, y, z), context + " fluid " + x + ", " + y + ", " + z);
            points++;
        }
        // Each source's value, bit for bit, with this chunk's FlatCache binding:
        // corner values inside the grid (every corner), the input outside it.
        var chunk = NativeNoiseRouterTest.chunk(config, random, pos);
        var owner = (Aquifer.NoiseBasedAquifer)chunk.aquifer();
        var sources = random.nativeFluidSources();
        int size = chunk.noiseSizeXZ + 1;
        int[] grid = {chunk.firstNoiseX, chunk.firstNoiseZ, size};
        double[] memo = new double[sources.slots() * size * size];
        byte[] present = new byte[memo.length];
        for (int sample = 0; sample < 160; sample++) {
            int x = pos.getMinBlockX() - 12 + rng.nextInt(44), z = pos.getMinBlockZ() - 12 + rng.nextInt(44);
            int y = noise.minY() + rng.nextInt(noise.height());
            var point = new DensityFunction.SinglePointContext(x, y, z);
            for (int kind = 3; kind <= 7; kind++) {
                double expected = owner.noiseSource(kind, point), actual = sources.value(kind - 3, x, y, z, grid, memo, present);
                int k = kind;
                assertEquals(Double.doubleToRawLongBits(expected), Double.doubleToRawLongBits(actual),
                    () -> context + " source " + k + " at " + point + ": " + expected + " vs " + actual);
                values++;
            }
        }
        long used = NativeFluidSources.STATUSES.get() - before;
        assertTrue(used > 0, context + " must take native sources");
        statuses += used;
    }

    @Test void nativeSourcesMatchJavaForAquiferSettings() {
        long[] seeds = {0, 42, -7_340_013_412_337L};
        ChunkPos[] positions = {new ChunkPos(0, 0), new ChunkPos(-1, 3), new ChunkPos(37, -91), new ChunkPos(-6250, 4321),
            new ChunkPos(131_000, -131_000), new ChunkPos(-1_874_999, 1_874_999)};
        int chunks = 0;
        for (var setting : NativeNoiseRouterTest.settings) {
            if (!setting.value().aquifersEnabled()) continue;
            for (long seed : seeds) {
                var random = RandomState.create(setting.value(), NativeNoiseRouterTest.noises, seed);
                assertNotNull(random.nativeFluidSources(), setting.key().location() + " must compile natively");
                for (ChunkPos pos : positions) {
                    compare(setting.value(), random, pos, setting.key().location() + " seed " + seed + " " + pos);
                    chunks++;
                }
            }
        }
        assertTrue(chunks >= 54);
        System.out.println("FLUID_SOURCES_PARITY chunks=" + chunks + " points=" + points + " native_statuses=" + statuses + " source_values=" + values);
    }

    @Test void gatesKeepJavaSources() {
        var overworld = NativeSurfaceLevelTest.settings("overworld");
        var r = overworld.noiseRouter();
        // An unsupported source keeps every source in Java.
        var custom = new NoiseRouter(r.barrierNoise(), r.fluidLevelFloodednessNoise(), r.fluidLevelSpreadNoise(),
            DensityFunctions.add(r.lavaNoise(), DensityFunctions.BeardifierMarker.INSTANCE), r.temperature(), r.vegetation(), r.continents(),
            r.erosion(), r.depth(), r.ridges(), r.preliminarySurfaceLevel(), r.finalDensity(), r.veinToggle(), r.veinRidged(), r.veinGap());
        assertNull(NativeFluidSources.compile(RandomState.create(new NoiseGeneratorSettings(overworld.noiseSettings(), overworld.defaultBlock(),
            overworld.defaultFluid(), custom, overworld.surfaceRule(), overworld.spawnTarget(), overworld.seaLevel(), overworld.disableMobGeneration(),
            overworld.aquifersEnabled(), overworld.oreVeinsEnabled(), overworld.useLegacyRandomSource()), NativeNoiseRouterTest.noises, 1).router()));
        // Without native surface levels the sources stay in Java too.
        var random = RandomState.create(overworld, NativeNoiseRouterTest.noises, 1);
        long before = NativeFluidSources.STATUSES.get();
        NativeSurfaceLevel.setEnabled(false);
        try {
            var aquifer = (Aquifer.NoiseBasedAquifer)NativeNoiseRouterTest.chunk(overworld, random, new ChunkPos(4, 4)).aquifer();
            aquifer.nativeAquifer().fluid(70, 20, 70);
        } finally {
            NativeSurfaceLevel.setEnabled(true);
        }
        NativeFluidSources.setEnabled(false);
        try {
            assertNull(RandomState.create(overworld, NativeNoiseRouterTest.noises, 2).nativeFluidSources());
        } finally {
            NativeFluidSources.setEnabled(true);
        }
        assertEquals(before, NativeFluidSources.STATUSES.get());
    }
}
