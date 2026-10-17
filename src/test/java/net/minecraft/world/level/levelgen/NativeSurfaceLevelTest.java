package net.minecraft.world.level.levelgen;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.Holder;
import net.minecraft.util.CubicSpline;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.synth.BlendedNoise;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Exact parity of Rust-owned preliminary surface levels with NoiseChunk's
 * Java evaluation of the chunk-wrapped function: the cache the aquifer fills
 * during construction, and single columns in and far outside the chunk. */
class NativeSurfaceLevelTest {
    @BeforeAll static void load() {
        NativeNoiseRouterTest.load();
    }

    @AfterAll static void close() {
        NativeSurfaceLevel.setEnabled(true);
        NativeNoiseRouterTest.close();
    }

    static NoiseChunk chunk(NoiseGeneratorSettings config, RandomState random, ChunkPos pos, boolean nativeLevels) {
        NativeSurfaceLevel.setEnabled(nativeLevels);
        try {
            return NativeNoiseRouterTest.chunk(config, random, pos);
        } finally {
            NativeSurfaceLevel.setEnabled(true);
        }
    }

    static Map<Long, Integer> cache(NoiseChunk chunk) {
        try {
            var field = NoiseChunk.class.getDeclaredField("preliminarySurfaceLevelCache");
            field.setAccessible(true);
            var map = new TreeMap<Long, Integer>();
            for (Long2IntMap.Entry entry : ((Long2IntMap)field.get(chunk)).long2IntEntrySet()) map.put(entry.getLongKey(), entry.getIntValue());
            return map;
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }

    static long columns;

    /** Java and native chunks of one position: the constructor's cache, then columns around and far away. */
    static void compare(NoiseGeneratorSettings config, RandomState random, ChunkPos pos, String context) {
        long before = NativeSurfaceLevel.COLUMNS.get();
        NoiseChunk java = chunk(config, random, pos, false), candidate = chunk(config, random, pos, true);
        assertEquals(cache(java), cache(candidate), context + " constructor cache");
        var rng = new java.util.Random(pos.toLong() ^ context.hashCode());
        for (int sample = 0; sample < 48; sample++) {
            int spread = sample < 24 ? 40 : 4000;
            int x = pos.getMinBlockX() + rng.nextInt(2 * spread) - spread, z = pos.getMinBlockZ() + rng.nextInt(2 * spread) - spread;
            int left = java.preliminarySurfaceLevel(x, z), right = candidate.preliminarySurfaceLevel(x, z);
            assertEquals(left, right, context + " column " + x + ", " + z);
        }
        int[] box = {pos.getMinBlockX() - 30, pos.getMinBlockZ() - 30, pos.getMaxBlockX() + 30, pos.getMaxBlockZ() + 30};
        assertEquals(java.maxPreliminarySurfaceLevel(box[0], box[1], box[2], box[3]), candidate.maxPreliminarySurfaceLevel(box[0], box[1], box[2], box[3]));
        assertEquals(cache(java), cache(candidate), context + " cache after lookups");
        long used = NativeSurfaceLevel.COLUMNS.get() - before;
        assertTrue(used > 0, context + " must take the native route");
        columns += used;
    }

    @Test void nativeLevelsMatchJavaForEveryVanillaSetting() {
        long[] seeds = {0, 42, -7_340_013_412_337L};
        ChunkPos[] positions = {new ChunkPos(0, 0), new ChunkPos(-1, 3), new ChunkPos(37, -91), new ChunkPos(-6250, 4321),
            new ChunkPos(131_000, -131_000), new ChunkPos(-1_874_999, 1_874_999)};
        int chunks = 0;
        long before = columns;
        for (var setting : NativeNoiseRouterTest.settings) {
            for (long seed : seeds) {
                var random = RandomState.create(setting.value(), NativeNoiseRouterTest.noises, seed);
                assertNotNull(random.nativeSurfaceLevel(), setting.key().location() + " must compile natively");
                for (ChunkPos pos : positions) {
                    compare(setting.value(), random, pos, setting.key().location() + " seed " + seed + " " + pos);
                    chunks++;
                }
            }
        }
        System.out.println("SURFACE_LEVEL_PARITY chunks=" + chunks + " settings=" + NativeNoiseRouterTest.settings.size() + " native_columns=" + (columns - before));
    }

    static NoiseGeneratorSettings withPreliminary(DensityFunction preliminary) {
        var overworld = settings("overworld");
        var r = overworld.noiseRouter();
        var router = new NoiseRouter(r.barrierNoise(), r.fluidLevelFloodednessNoise(), r.fluidLevelSpreadNoise(), r.lavaNoise(), r.temperature(),
            r.vegetation(), r.continents(), r.erosion(), r.depth(), r.ridges(), preliminary, r.finalDensity(), r.veinToggle(), r.veinRidged(), r.veinGap());
        return new NoiseGeneratorSettings(overworld.noiseSettings(), overworld.defaultBlock(), overworld.defaultFluid(), router,
            overworld.surfaceRule(), overworld.spawnTarget(), overworld.seaLevel(), overworld.disableMobGeneration(),
            overworld.aquifersEnabled(), overworld.oreVeinsEnabled(), overworld.useLegacyRandomSource());
    }

    static NoiseGeneratorSettings settings(String name) {
        return NativeNoiseRouterTest.settings.stream().filter(holder -> holder.key().location().getPath().equals(name)).findFirst().orElseThrow().value();
    }

    static DensityFunctions.Spline.Coordinate coordinate(DensityFunction function) {
        return new DensityFunctions.Spline.Coordinate(Holder.direct(function));
    }

    /** Every router operation and every cache marker, under FindTopSurface and as plain roots. */
    @Test void syntheticSurfaceFunctionsMatchJava() {
        var functions = NativeNoiseRouterTest.synthetic();
        DensityFunction continents = functions.get(6);
        DensityFunction cave = DensityFunctions.noise(NativeNoiseRouterTest.noise(Noises.CAVE_ENTRANCE), 0.75, 0.5);
        DensityFunction gradient = DensityFunctions.yClampedGradient(-64, 320, 1.0, -1.0);
        DensityFunction flat = DensityFunctions.flatCache(DensityFunctions.cache2d(continents));
        var spline = CubicSpline.builder(coordinate(flat)).addPoint(-1.0F, 40.0F, 3.0F).addPoint(0.0F, 90.0F).addPoint(1.0F, 250.0F, -2.0F).build();
        List<DensityFunction> roots = new java.util.ArrayList<>();
        for (DensityFunction function : functions) {
            DensityFunction density = DensityFunctions.add(gradient, DensityFunctions.mul(DensityFunctions.constant(0.25), DensityFunctions.interpolated(function)));
            roots.add(DensityFunctions.findTopSurface(DensityFunctions.cacheOnce(density), DensityFunctions.spline(spline), -64, 8));
            roots.add(DensityFunctions.mul(DensityFunctions.constant(100.0), DensityFunctions.cacheAllInCell(function)));
        }
        // Searches longer than one lane frame, bounds at and below the floor, NaN and huge bounds.
        roots.add(DensityFunctions.findTopSurface(DensityFunctions.add(cave, DensityFunctions.constant(-0.6)), DensityFunctions.constant(1500), -2000, 4));
        roots.add(DensityFunctions.findTopSurface(DensityFunctions.add(cave, DensityFunctions.constant(-0.9)), DensityFunctions.constant(900), -900, 1));
        roots.add(DensityFunctions.findTopSurface(cave, DensityFunctions.constant(-64), -64, 8));
        roots.add(DensityFunctions.findTopSurface(cave, DensityFunctions.constant(-200), -64, 8));
        roots.add(DensityFunctions.findTopSurface(cave, DensityFunctions.constant(Double.NaN), -64, 8));
        roots.add(DensityFunctions.findTopSurface(cave, DensityFunctions.add(flat, DensityFunctions.constant(64)), -64, 16));
        roots.add(DensityFunctions.mul(DensityFunctions.constant(1e12), cave));
        roots.add(DensityFunctions.mul(DensityFunctions.constant(-7.5), BlendedNoise.createUnseeded(0.25, 0.125, 80.0, 160.0, 8.0)));
        int checked = 0;
        for (DensityFunction root : roots) {
            var config = withPreliminary(root);
            var random = RandomState.create(config, NativeNoiseRouterTest.noises, 11);
            assertNotNull(random.nativeSurfaceLevel(), "synthetic root " + checked + " must compile natively");
            for (ChunkPos pos : new ChunkPos[]{new ChunkPos(0, 0), new ChunkPos(-170, 3_000)}) compare(config, random, pos, "synthetic " + checked + " " + pos);
            checked++;
        }
        System.out.println("SURFACE_LEVEL_SYNTHETIC roots=" + checked);
    }

    @Test void gatesKeepJavaLevels() {
        DensityFunction cave = DensityFunctions.noise(NativeNoiseRouterTest.noise(Noises.CAVE_ENTRANCE), 0.75, 0.5);
        DensityFunction gradient = DensityFunctions.yClampedGradient(-64, 320, 1.0, -1.0);
        // A Y-dependent FlatCache or Cache2D reads its corner (Y 0) or first value, not the point.
        for (DensityFunction cached : List.of(DensityFunctions.flatCache(cave), DensityFunctions.cache2d(gradient))) {
            var random = RandomState.create(withPreliminary(DensityFunctions.findTopSurface(cached, DensityFunctions.constant(100), -64, 8)),
                NativeNoiseRouterTest.noises, 3);
            assertNull(random.nativeSurfaceLevel());
        }
        // FindTopSurface below the root and unsupported functions.
        assertNull(RandomState.create(withPreliminary(DensityFunctions.add(DensityFunctions.findTopSurface(cave, DensityFunctions.constant(100), -64, 8),
            DensityFunctions.constant(1))), NativeNoiseRouterTest.noises, 3).nativeSurfaceLevel());
        assertNull(RandomState.create(withPreliminary(DensityFunctions.add(cave, DensityFunctions.BeardifierMarker.INSTANCE)),
            NativeNoiseRouterTest.noises, 3).nativeSurfaceLevel());
        // Chunks with a blender or a subclass keep Java; so does the property.
        var config = settings("overworld");
        var random = RandomState.create(config, NativeNoiseRouterTest.noises, 3);
        var noise = config.noiseSettings();
        var subclass = new NoiseChunk(16 / noise.getCellWidth(), random, 0, 0, noise, DensityFunctions.BeardifierMarker.INSTANCE, config,
            new AquiferFluidPicker(config.seaLevel(), config.defaultFluid()), Blender.empty()) {};
        long before = NativeSurfaceLevel.COLUMNS.get();
        subclass.preliminarySurfaceLevel(100, 100);
        NativeSurfaceLevel.setEnabled(false);
        try {
            assertNull(random.nativeSurfaceLevel());
            NativeNoiseRouterTest.chunk(config, random, new ChunkPos(9, 9)).preliminarySurfaceLevel(-300, 7);
        } finally {
            NativeSurfaceLevel.setEnabled(true);
        }
        assertEquals(before, NativeSurfaceLevel.COLUMNS.get());
    }
}
