package net.minecraft.world.level.levelgen;

import java.util.List;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Exact parity of natively instantiated chunk noise with the chunk's wrapped
 * Java graph: every slice value of every template root against the Java
 * interpolator it stands for, the climate sampler a native chunk later builds,
 * and the gates. Full-chunk parity is in {@code NativeNoiseFillTest}. */
class NativeChunkNoiseTest {
    @BeforeAll static void load() {
        NativeNoiseRouterTest.load();
    }

    @AfterAll static void close() {
        NativeChunkNoise.setEnabled(true);
        NativeNoiseRouterTest.close();
    }

    static long values;

    /** The Java interpolator each template root stands for: the cell cache's
     * inputs in order, then the ore vein interpolators. */
    static NoiseChunk.NoiseInterpolator[] javaRoots(NoiseChunk chunk, NativeChunkNoise template) {
        var cells = (NativeCellDensity)chunk.aquiferDensity.evaluator();
        var inputs = cells.inputs();
        var roots = new NoiseChunk.NoiseInterpolator[template.roots];
        for (int index = 0; index < template.cellInputs.length; index++) roots[template.cellInputs[index]] = inputs[index];
        if (template.ore != null) {
            var ridged = (DensityFunctions.MulOrAdd)chunk.veinRidged;
            var max = (DensityFunctions.Ap2)ridged.input();
            roots[template.ore[0]] = (NoiseChunk.NoiseInterpolator)chunk.veinToggle;
            roots[template.ore[1]] = (NoiseChunk.NoiseInterpolator)((DensityFunctions.Mapped)max.argument1()).input();
            roots[template.ore[2]] = (NoiseChunk.NoiseInterpolator)((DensityFunctions.Mapped)max.argument2()).input();
        }
        for (var root : roots) assertNotNull(root, "every root maps to an interpolator");
        return roots;
    }

    static void compareSlice(double[] values, NoiseChunk.NoiseInterpolator[] roots, boolean first, int columns, int points, String context) {
        assertNotNull(values, context + " declined");
        for (int k = 0; k < roots.length; k++) {
            double[][] slice = first ? roots[k].slice0 : roots[k].slice1;
            for (int column = 0; column < columns; column++) {
                for (int y = 0; y < points; y++) {
                    double expected = slice[column][y], actual = values[(k * columns + column) * points + y];
                    int root = k, z = column, at = y;
                    assertEquals(Double.doubleToRawLongBits(expected), Double.doubleToRawLongBits(actual),
                        () -> context + " root " + root + " column " + z + " y " + at + ": " + expected + " vs " + actual);
                    NativeChunkNoiseTest.values++;
                }
            }
        }
    }

    /** One chunk: the native instance's slices against the same chunk's Java slices. */
    static void compareChunk(NoiseGeneratorSettings config, RandomState random, ChunkPos pos, String context) {
        NoiseChunk chunk = NativeNoiseRouterTest.chunk(config, random, pos);
        NativeChunkNoise template = chunk.nativeNoise();
        assertNotNull(template, context + " must instantiate natively");
        NativeNoiseRouter instance = template.instance(chunk);
        try {
            // The same chunk's Java graph, wrapped on request, fills Java slices.
            chunk.ensureWrapped();
            var roots = javaRoots(chunk, template);
            int columns = chunk.cellCountXZ + 1, points = chunk.cellCountY + 1, cells = 16 / chunk.cellWidth;
            chunk.initializeForFirstCellX();
            compareSlice(instance.sliceValues(chunk.firstCellX() * chunk.cellWidth), roots, true, columns, points, context + " first slice");
            for (int cellX = 0; cellX < cells; cellX++) {
                chunk.advanceCellX(cellX);
                compareSlice(instance.sliceValues((chunk.firstCellX() + cellX + 1) * chunk.cellWidth), roots, false, columns, points,
                    context + " cell x " + cellX);
                chunk.swapSlices();
            }
        } finally {
            instance.close();
        }
    }

    /** FlatCache tables hold the input at Y 0, not at the slice's Y: a Y-dependent
     * FlatCache input distinguishes a table read from a direct evaluation. */
    @Test void flatCacheTablesKeepTheirYZeroValues() {
        DensityFunction cave = DensityFunctions.noise(NativeNoiseRouterTest.noise(Noises.CAVE_ENTRANCE), 0.75, 0.5);
        DensityFunction terrain = DensityFunctions.add(DensityFunctions.flatCache(DensityFunctions.add(cave, DensityFunctions.yClampedGradient(-64, 320, 1, -1))),
            DensityFunctions.mul(DensityFunctions.constant(0.5), DensityFunctions.flatCache(DensityFunctions.cache2d(DensityFunctions.shiftA(NativeNoiseRouterTest.noise(Noises.SHIFT))))));
        DensityFunction finalDensity = DensityFunctions.mul(DensityFunctions.constant(0.64), DensityFunctions.interpolated(terrain)).squeeze();
        var config = NativeNoiseRouterTest.withFinalDensity(finalDensity);
        int chunks = 0;
        for (long seed : new long[]{3, -8}) {
            var random = RandomState.create(config, NativeNoiseRouterTest.noises, seed);
            for (ChunkPos pos : new ChunkPos[]{new ChunkPos(0, 0), new ChunkPos(-31, 77)}) {
                compareChunk(config, random, pos, "y-dependent flat cache seed " + seed + " " + pos);
                chunks++;
            }
        }
        System.out.println("CHUNK_NOISE_FLAT chunks=" + chunks);
    }

    @Test void instanceSlicesMatchJavaInterpolators() {
        long[] seeds = {0, 42, -7_340_013_412_337L};
        ChunkPos[] positions = {new ChunkPos(0, 0), new ChunkPos(-1, 3), new ChunkPos(37, -91), new ChunkPos(-6250, 4321),
            new ChunkPos(131_000, -131_000), new ChunkPos(-1_874_999, 1_874_999)};
        int chunks = 0;
        long before = values;
        for (var setting : NativeNoiseRouterTest.settings) {
            for (long seed : seeds) {
                var random = RandomState.create(setting.value(), NativeNoiseRouterTest.noises, seed);
                for (ChunkPos pos : positions) {
                    compareChunk(setting.value(), random, pos, setting.key().location() + " seed " + seed + " " + pos);
                    chunks++;
                }
            }
        }
        System.out.println("CHUNK_NOISE_PARITY chunks=" + chunks + " values=" + (values - before));
    }

    /** The biome stage builds its sampler from a native chunk without the constructor's wrapping. */
    @Test void climateSamplerMatchesWrappedChunk() {
        int samples = 0;
        for (var setting : NativeNoiseRouterTest.settings) {
            var config = setting.value();
            var random = RandomState.create(config, NativeNoiseRouterTest.noises, 99);
            var pos = new ChunkPos(-23, 57);
            NativeChunkNoise.setEnabled(false);
            NoiseChunk java;
            try {
                java = NativeNoiseRouterTest.chunk(config, random, pos);
            } finally {
                NativeChunkNoise.setEnabled(true);
            }
            NoiseChunk candidate = NativeNoiseRouterTest.chunk(config, random, pos);
            assertNull(java.nativeNoise());
            assertNotNull(candidate.nativeNoise());
            Climate.Sampler left = java.cachedClimateSampler(random.router(), config.spawnTarget());
            Climate.Sampler right = candidate.cachedClimateSampler(random.router(), config.spawnTarget());
            for (int qx = -2; qx < 7; qx++) {
                for (int qz = -2; qz < 7; qz++) {
                    for (int qy = -16; qy < 80; qy += 7) {
                        int x = (pos.getMinBlockX() >> 2) + qx, z = (pos.getMinBlockZ() >> 2) + qz;
                        assertEquals(left.sample(x, qy, z), right.sample(x, qy, z), setting.key().location() + " climate " + x + " " + qy + " " + z);
                        samples++;
                    }
                }
            }
        }
        System.out.println("CHUNK_NOISE_CLIMATE samples=" + samples);
    }

    @Test void gatesKeepWrappedChunks() {
        var config = NativeSurfaceLevelTest.settings("overworld");
        var random = RandomState.create(config, NativeNoiseRouterTest.noises, 5);
        var noise = config.noiseSettings();
        var fluids = new AquiferFluidPicker(config.seaLevel(), config.defaultFluid());
        assertNotNull(new NoiseChunk(4, random, 0, 0, noise, DensityFunctions.BeardifierMarker.INSTANCE, config, fluids, Blender.empty()).nativeNoise());
        assertNotNull(new NoiseChunk(4, random, 0, 0, noise, Beardifier.EMPTY, config, fluids, Blender.empty()).nativeNoise());
        // Structures' terrain adjustment, subclasses and the property keep Java wrapping.
        var beardifier = new DensityFunctions.BeardifierOrMarker() {
            @Override public double compute(DensityFunction.FunctionContext context) { return 0.25; }
            @Override public double minValue() { return 0.25; }
            @Override public double maxValue() { return 0.25; }
        };
        assertNull(new NoiseChunk(4, random, 0, 0, noise, beardifier, config, fluids, Blender.empty()).nativeNoise());
        assertNull(new NoiseChunk(4, random, 0, 0, noise, DensityFunctions.BeardifierMarker.INSTANCE, config, fluids, Blender.empty()) {}.nativeNoise());
        NativeChunkNoise.setEnabled(false);
        try {
            var chunk = new NoiseChunk(4, random, 0, 0, noise, DensityFunctions.BeardifierMarker.INSTANCE, config, fluids, Blender.empty());
            assertNull(chunk.nativeNoise());
            assertFalse(chunk.interpolators.isEmpty(), "a wrapped chunk wraps in its constructor");
        } finally {
            NativeChunkNoise.setEnabled(true);
        }
        // A native chunk wraps only when asked.
        var lazy = new NoiseChunk(4, random, 0, 0, noise, DensityFunctions.BeardifierMarker.INSTANCE, config, fluids, Blender.empty());
        assertTrue(lazy.interpolators.isEmpty());
        lazy.ensureWrapped();
        assertFalse(lazy.interpolators.isEmpty());
        assertNotNull(lazy.aquiferDensity);
    }
}
