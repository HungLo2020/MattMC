package net.minecraft.world.level.levelgen;

import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.CubicSpline;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.synth.BlendedNoise;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Bit-exact parity of natively filled interpolation slices with NoiseChunk's
 * Java slice fill, for every vanilla noise setting and for synthetic routers
 * covering every router operation and every gate. Full-chunk parity through
 * the production fill is covered by {@code NativeNoiseFillTest}. */
class NativeNoiseRouterTest {
    static net.minecraft.server.packs.resources.MultiPackResourceManager resources;
    static RegistryAccess.Frozen registries;
    static List<Holder.Reference<NoiseGeneratorSettings>> settings;
    static HolderGetter<NormalNoise.NoiseParameters> noises;

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
        noises = registries.lookupOrThrow(Registries.NOISE);
    }

    @AfterAll static void close() {
        NativeNoiseRouter.setEnabled(true);
        resources.close();
    }

    static NoiseChunk chunk(NoiseGeneratorSettings config, RandomState random, ChunkPos pos) {
        var noise = config.noiseSettings();
        return new NoiseChunk(16 / noise.getCellWidth(), random, pos.getMinBlockX(), pos.getMinBlockZ(), noise, DensityFunctions.BeardifierMarker.INSTANCE,
            config, new AquiferFluidPicker(config.seaLevel(), config.defaultFluid()), Blender.empty());
    }

    static long points;

    static void compareSlices(NoiseChunk java, NoiseChunk candidate, String context) {
        assertEquals(java.interpolators.size(), candidate.interpolators.size());
        for (int index = 0; index < java.interpolators.size(); index++) {
            var left = java.interpolators.get(index);
            var right = candidate.interpolators.get(index);
            compareSlice(left.slice0, right.slice0, context + " interpolator " + index + " slice0");
            compareSlice(left.slice1, right.slice1, context + " interpolator " + index + " slice1");
        }
    }

    static void compareSlice(double[][] java, double[][] candidate, String context) {
        assertEquals(java.length, candidate.length);
        for (int column = 0; column < java.length; column++) {
            for (int y = 0; y < java[column].length; y++) {
                int z = column, at = y;
                assertEquals(Double.doubleToRawLongBits(java[column][y]), Double.doubleToRawLongBits(candidate[column][y]),
                    () -> context + " column " + z + " y " + at + ": " + java[z][at] + " vs " + candidate[z][at]);
                points++;
            }
        }
    }

    /** Java and native slice fills of the same chunk, compared after every slice. */
    static boolean compareChunk(NoiseGeneratorSettings config, RandomState random, ChunkPos pos, String context) {
        NoiseChunk java = chunk(config, random, pos), candidate = chunk(config, random, pos);
        NativeNoiseRouter router = NativeNoiseRouter.create(candidate);
        if (router == null) return false;
        try {
            long before = NativeNoiseRouter.SLICES.get();
            int cells = 16 / config.noiseSettings().getCellWidth();
            java.initializeForFirstCellX();
            long counter = candidate.interpolationCounter, arrays = candidate.arrayInterpolationCounter;
            candidate.initializeForFirstCellX(router);
            assertTrue(candidate.interpolationCounter > counter && candidate.arrayInterpolationCounter > arrays, "counters advance");
            compareSlices(java, candidate, context + " first slice");
            for (int cellX = 0; cellX < cells; cellX++) {
                java.advanceCellX(cellX);
                candidate.advanceCellX(cellX, router);
                compareSlices(java, candidate, context + " cell x " + cellX);
                java.swapSlices();
                candidate.swapSlices();
            }
            assertEquals(cells + 1, NativeNoiseRouter.SLICES.get() - before, context + " every slice native");
        } finally {
            router.close();
        }
        return true;
    }

    @Test void routerSlicesMatchJavaForEveryVanillaSetting() {
        long[] seeds = {0, 42, -7_340_013_412_337L};
        ChunkPos[] positions = {new ChunkPos(0, 0), new ChunkPos(-1, 3), new ChunkPos(37, -91), new ChunkPos(-6250, 4321),
            new ChunkPos(131_000, -131_000), new ChunkPos(-1_874_999, 1_874_999)};
        int chunks = 0;
        long before = points;
        for (var setting : settings) {
            for (long seed : seeds) {
                var random = RandomState.create(setting.value(), noises, seed);
                for (ChunkPos pos : positions) {
                    String context = setting.key().location() + " seed " + seed + " " + pos;
                    assertTrue(compareChunk(setting.value(), random, pos, context), context + " must take the native router");
                    chunks++;
                }
            }
        }
        System.out.println("NOISE_ROUTER_PARITY chunks=" + chunks + " settings=" + settings.size() + " points=" + (points - before));
    }

    static NoiseGeneratorSettings withFinalDensity(DensityFunction finalDensity) {
        var overworld = settings.stream().filter(holder -> holder.key().location().getPath().equals("overworld")).findFirst().orElseThrow().value();
        var r = overworld.noiseRouter();
        var router = new NoiseRouter(r.barrierNoise(), r.fluidLevelFloodednessNoise(), r.fluidLevelSpreadNoise(), r.lavaNoise(), r.temperature(),
            r.vegetation(), r.continents(), r.erosion(), r.depth(), r.ridges(), r.preliminarySurfaceLevel(), finalDensity,
            r.veinToggle(), r.veinRidged(), r.veinGap());
        return new NoiseGeneratorSettings(overworld.noiseSettings(), overworld.defaultBlock(), overworld.defaultFluid(), router,
            overworld.surfaceRule(), overworld.spawnTarget(), overworld.seaLevel(), overworld.disableMobGeneration(),
            overworld.aquifersEnabled(), overworld.oreVeinsEnabled(), overworld.useLegacyRandomSource());
    }

    static Holder<NormalNoise.NoiseParameters> noise(net.minecraft.resources.ResourceKey<NormalNoise.NoiseParameters> key) {
        return noises.getOrThrow(key);
    }

    static DensityFunctions.Spline.Coordinate coordinate(DensityFunction function) {
        return new DensityFunctions.Spline.Coordinate(Holder.direct(function));
    }

    /** Every router operation, each as its own interpolator. */
    static List<DensityFunction> synthetic() {
        DensityFunction shiftX = DensityFunctions.flatCache(DensityFunctions.cache2d(DensityFunctions.shiftA(noise(Noises.SHIFT))));
        DensityFunction shiftZ = DensityFunctions.flatCache(DensityFunctions.cache2d(DensityFunctions.shiftB(noise(Noises.SHIFT))));
        DensityFunction continents = DensityFunctions.shiftedNoise2d(shiftX, shiftZ, 0.25, noise(Noises.CONTINENTALNESS));
        DensityFunction erosion = DensityFunctions.shiftedNoise2d(shiftX, shiftZ, 0.25, noise(Noises.EROSION));
        DensityFunction cave = DensityFunctions.noise(noise(Noises.CAVE_ENTRANCE), 0.75, 0.5);
        DensityFunction shared = DensityFunctions.cacheOnce(DensityFunctions.add(cave, DensityFunctions.yClampedGradient(-64, 320, 1.5, -1.5)));
        var inner = CubicSpline.builder(coordinate(erosion)).addPoint(-0.5F, 0.2F).addPoint(0.1F, -0.3F, 0.7F).addPoint(0.6F, 0.9F).build();
        var spline = CubicSpline.builder(coordinate(continents)).addPoint(-1.0F, -0.4F, 0.1F).addPoint(-0.2F, inner).addPoint(0.3F, 0.6F)
            .addPoint(0.8F, inner).build();
        var yspline = CubicSpline.builder(coordinate(shared)).addPoint(-1.2F, 1.0F).addPoint(0.0F, -0.5F, 2.0F).addPoint(1.1F, 0.25F).build();
        return List.of(
            DensityFunctions.constant(0.375),
            DensityFunctions.yClampedGradient(-40, 200, -2.0, 3.0),
            DensityFunctions.noise(noise(Noises.JAGGED), 1500.0, 0.0),
            DensityFunctions.noise(noise(Noises.NOODLE), 1.0, 1.0),
            DensityFunctions.add(DensityFunctions.shift(noise(Noises.SHIFT)), DensityFunctions.shiftA(noise(Noises.SHIFT))),
            DensityFunctions.mul(DensityFunctions.shiftB(noise(Noises.SHIFT)), DensityFunctions.constant(-1.25)),
            continents,
            DensityFunctions.weirdScaledSampler(shared, noise(Noises.SPAGHETTI_3D_1), DensityFunctions.WeirdScaledSampler.RarityValueMapper.TYPE1),
            DensityFunctions.weirdScaledSampler(cave, noise(Noises.SPAGHETTI_3D_1), DensityFunctions.WeirdScaledSampler.RarityValueMapper.TYPE2),
            // Short circuits: MUL by zero, MIN below and MAX above the right bound.
            DensityFunctions.mul(DensityFunctions.yClampedGradient(0, 64, 0.0, 1.0), cave),
            DensityFunctions.min(shared, DensityFunctions.noise(noise(Noises.NOODLE), 2.0, 1.0)),
            DensityFunctions.max(cave.clamp(-0.1, 0.1), DensityFunctions.noise(noise(Noises.JAGGED), 3.0, 1.0)),
            DensityFunctions.rangeChoice(shared, -0.2, 0.3, cave.abs(), erosion.square()),
            DensityFunctions.add(shared.cube(), DensityFunctions.add(cave.halfNegative(), shared.quarterNegative())),
            DensityFunctions.add(shared.squeeze(), DensityFunctions.add(DensityFunctions.constant(3.0), cave.abs()).invert()),
            BlendedNoise.createUnseeded(0.25, 0.125, 80.0, 160.0, 8.0),
            DensityFunctions.endIslands(0L),
            DensityFunctions.spline(spline),
            DensityFunctions.spline(yspline),
            DensityFunctions.blendDensity(DensityFunctions.add(DensityFunctions.mul(DensityFunctions.blendAlpha(), cave), DensityFunctions.blendOffset())),
            DensityFunctions.cache2d(DensityFunctions.add(continents, DensityFunctions.flatCache(erosion))));
    }

    static DensityFunction interpolatedSum(List<DensityFunction> functions) {
        DensityFunction sum = DensityFunctions.zero();
        for (DensityFunction function : functions) sum = DensityFunctions.add(sum, DensityFunctions.interpolated(function));
        return sum;
    }

    @Test void syntheticRoutersCoverEveryOperation() {
        var functions = synthetic();
        var config = withFinalDensity(interpolatedSum(functions));
        int chunks = 0;
        for (long seed : new long[]{1, -99_123}) {
            var random = RandomState.create(config, noises, seed);
            for (ChunkPos pos : new ChunkPos[]{new ChunkPos(0, 0), new ChunkPos(-17, 9), new ChunkPos(5_000, -250_000)}) {
                assertTrue(compareChunk(config, random, pos, "synthetic seed " + seed + " " + pos), "synthetic router must be native");
                chunks++;
            }
        }
        System.out.println("NOISE_ROUTER_SYNTHETIC chunks=" + chunks + " interpolators=" + functions.size());
    }

    static boolean routes(DensityFunction finalDensity) {
        var config = withFinalDensity(finalDensity);
        var candidate = chunk(config, RandomState.create(config, noises, 3), new ChunkPos(2, -2));
        NativeNoiseRouter router = NativeNoiseRouter.create(candidate);
        if (router == null) return false;
        router.close();
        return true;
    }

    @Test void gatesKeepJavaSlicesForUnprovenGraphs() {
        DensityFunction cave = DensityFunctions.noise(noise(Noises.CAVE_ENTRANCE), 0.75, 0.5);
        assertTrue(routes(DensityFunctions.interpolated(cave)));
        // Cache2D over Y-dependent input keeps one value per column: not transparent.
        assertFalse(routes(DensityFunctions.interpolated(DensityFunctions.cache2d(cave))));
        assertFalse(routes(DensityFunctions.interpolated(DensityFunctions.cache2d(DensityFunctions.yClampedGradient(0, 10, 0, 1)))));
        // A CacheOnce the cell cache also reads with the chunk as context.
        DensityFunction once = DensityFunctions.cacheOnce(cave);
        assertFalse(routes(DensityFunctions.add(DensityFunctions.interpolated(once), once)));
        assertTrue(routes(DensityFunctions.add(DensityFunctions.interpolated(once), DensityFunctions.cacheOnce(DensityFunctions.constant(2)))));
        // Unsupported functions and nested interpolation.
        assertFalse(routes(DensityFunctions.interpolated(DensityFunctions.add(cave, DensityFunctions.interpolated(cave)))));
        assertFalse(routes(DensityFunctions.interpolated(new DensityFunctions.FindTopSurface(cave, DensityFunctions.constant(64), -64, 8))));
        assertFalse(routes(DensityFunctions.interpolated(DensityFunctions.cacheAllInCell(cave))));
        NativeNoiseRouter.setEnabled(false);
        try {
            assertFalse(routes(DensityFunctions.interpolated(cave)));
        } finally {
            NativeNoiseRouter.setEnabled(true);
        }
    }

    /** The Java fill takes over any slice Rust declines, without partial writes. */
    @Test void outOfGridChunksFallBackPerSlice() {
        var overworld = settings.stream().filter(holder -> holder.key().location().getPath().equals("overworld")).findFirst().orElseThrow().value();
        var random = RandomState.create(overworld, noises, 5);
        var pos = new ChunkPos(1, 1);
        NoiseChunk java = chunk(overworld, random, pos), candidate = chunk(overworld, random, pos);
        NativeNoiseRouter router = NativeNoiseRouter.create(candidate);
        assertNotNull(router);
        try {
            long before = NativeNoiseRouter.SLICES.get();
            java.initializeForFirstCellX();
            candidate.initializeForFirstCellX(router);
            // A cell beyond the chunk's FlatCache grid: Rust reports it, Java fills it.
            java.advanceCellX(8);
            candidate.advanceCellX(8, router);
            compareSlices(java, candidate, "outside grid");
            assertEquals(1, NativeNoiseRouter.SLICES.get() - before);
        } finally {
            router.close();
        }
    }
}
