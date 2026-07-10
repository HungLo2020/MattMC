package net.sodium.client.perf;

import net.sodium.client.SodiumClientMod;
import net.sodium.client.gui.SodiumGameOptions;
import net.sodium.client.render.chunk.translucent_sorting.data.TranslucentData;
import net.sodium.client.render.chunk.vertex.builder.ChunkMeshBufferBuilder;
import net.sodium.client.render.chunk.vertex.format.ChunkMeshFormats;
import net.sodium.client.render.chunk.vertex.format.ChunkVertexEncoder;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;

@Tag("performance")
class ChunkMeshingHotPathBenchmarkTest {
    private static final int QUAD_COUNT = Integer.getInteger("mattmc.perf.quads", 32_768);
    private static final int WARMUP_ITERATIONS = Integer.getInteger("mattmc.perf.warmup", 8);
    private static final int MEASURE_ITERATIONS = Integer.getInteger("mattmc.perf.iterations", 25);
    private static final int SECTION_INDEX = 11;
    private static final MeshFinisher MESH_FINISHER = createMeshFinisher();

    @BeforeAll
    static void installDefaultSodiumOptionsForNativeBufferAllocation() throws Exception {
        try {
            SodiumClientMod.options();
            return;
        } catch (IllegalStateException ignored) {
            // Unit tests do not run Sodium's full client initializer.
        }

        Field configField = SodiumClientMod.class.getDeclaredField("CONFIG");
        configField.setAccessible(true);
        configField.set(null, SodiumGameOptions.defaults());
    }

    @Test
    void benchmarkChunkMeshingHotPaths() throws Exception {
        Assumptions.assumeTrue(shouldRunPerfBenchmarks(),
                "Set -Dmattmc.runPerfBenchmarks=true or MATTMC_RUN_PERF_BENCHMARKS=true to run chunk meshing performance benchmarks");

        int[] quadIndexes = shuffledQuadIndexes();
        IntBuffer indexBuffer = ByteBuffer
                .allocateDirect(QUAD_COUNT * TranslucentData.INDICES_PER_QUAD * Integer.BYTES)
                .order(ByteOrder.nativeOrder())
                .asIntBuffer();
        ChunkVertexEncoder.Vertex[][] quads = syntheticQuads();

        List<BenchmarkResult> results = new ArrayList<>();
        results.add(measure("translucent_quad_index_emission", () -> runIndexEmission(quadIndexes, indexBuffer)));

        ChunkMeshBufferBuilder builder = new ChunkMeshBufferBuilder(ChunkMeshFormats.COMPACT, QUAD_COUNT * 4);
        try {
            results.add(measure("compact_chunk_mesh_buffer_build", () -> runMeshBufferBuild(builder, quads)));
        } finally {
            builder.destroy();
        }

        writeResults(results);
        for (BenchmarkResult result : results) {
            System.out.printf(Locale.ROOT,
                    "%s: mean %.3f ms, median %.3f ms, min %.3f ms, %.1f Mquads/s, checksum %d%n",
                    result.name, result.meanMillis(), result.medianMillis(), result.minMillis(),
                    result.megaQuadsPerSecond(), result.checksum);
        }
    }

    private static long runIndexEmission(int[] quadIndexes, IntBuffer indexBuffer) {
        indexBuffer.clear();
        TranslucentData.writeQuadVertexIndexes(indexBuffer, quadIndexes);
        return sample(indexBuffer, QUAD_COUNT * TranslucentData.INDICES_PER_QUAD);
    }

    private static long runMeshBufferBuild(ChunkMeshBufferBuilder builder, ChunkVertexEncoder.Vertex[][] quads)
            throws Exception {
        builder.start(SECTION_INDEX);
        for (int index = 0; index < QUAD_COUNT; index++) {
            builder.push(quads[index], materialBits(index));
        }

        return ((long) builder.count() << 32) ^ MESH_FINISHER.finish(builder);
    }

    private static BenchmarkResult measure(String name, BenchmarkAction action) throws Exception {
        long checksum = 0L;
        for (int iteration = 0; iteration < WARMUP_ITERATIONS; iteration++) {
            checksum ^= action.run();
        }

        long[] samples = new long[MEASURE_ITERATIONS];
        for (int iteration = 0; iteration < MEASURE_ITERATIONS; iteration++) {
            long start = System.nanoTime();
            checksum ^= action.run();
            samples[iteration] = System.nanoTime() - start;
        }

        long[] sorted = samples.clone();
        Arrays.sort(sorted);

        long total = 0L;
        for (long sample : samples) {
            total += sample;
        }

        return new BenchmarkResult(
                name,
                total / (double) samples.length,
                sorted[sorted.length / 2],
                sorted[0],
                sorted[sorted.length - 1],
                checksum);
    }

    private static int[] shuffledQuadIndexes() {
        int[] quadIndexes = new int[QUAD_COUNT];
        for (int index = 0; index < quadIndexes.length; index++) {
            quadIndexes[index] = index;
        }

        Random random = new Random(0x4d455348L);
        for (int index = quadIndexes.length - 1; index > 0; index--) {
            int other = random.nextInt(index + 1);
            int swap = quadIndexes[index];
            quadIndexes[index] = quadIndexes[other];
            quadIndexes[other] = swap;
        }

        return quadIndexes;
    }

    private static ChunkVertexEncoder.Vertex[][] syntheticQuads() {
        ChunkVertexEncoder.Vertex[][] quads = new ChunkVertexEncoder.Vertex[QUAD_COUNT][];
        for (int index = 0; index < quads.length; index++) {
            float baseX = (index & 15) + ((index >>> 4) & 3) * 0.03125F;
            float baseY = ((index >>> 6) & 15) + 0.125F;
            float baseZ = ((index >>> 10) & 15) + 0.25F;
            quads[index] = quad(baseX, baseY, baseZ, index);
        }

        return quads;
    }

    private static ChunkVertexEncoder.Vertex[] quad(float baseX, float baseY, float baseZ, int seed) {
        ChunkVertexEncoder.Vertex[] vertices = ChunkVertexEncoder.Vertex.uninitializedQuad();
        writeVertex(vertices[0], baseX, baseY, baseZ, 0.0F, 0.0F, seed);
        writeVertex(vertices[1], baseX + 1.0F, baseY, baseZ, 1.0F, 0.0F, seed);
        writeVertex(vertices[2], baseX + 1.0F, baseY + 1.0F, baseZ, 1.0F, 1.0F, seed);
        writeVertex(vertices[3], baseX, baseY + 1.0F, baseZ, 0.0F, 1.0F, seed);
        return vertices;
    }

    private static void writeVertex(ChunkVertexEncoder.Vertex vertex, float x, float y, float z,
            float u, float v, int seed) {
        ChunkVertexEncoder.Vertex.writeVertex(vertex, x, y, z, 0xff806040, 0.5F, u, v, 0x00f000f0);
        vertex.iris$setData((byte) (seed & 15), (byte) (seed & 3), seed & 0xffff,
                seed & 15, (seed >>> 4) & 15, (seed >>> 8) & 15);
    }

    private static int materialBits(int index) {
        return 1 | ((index & 7) << 2);
    }

    private static long sample(IntBuffer buffer, int valueCount) {
        long checksum = valueCount;
        int step = Math.max(1, valueCount / 128);
        for (int index = 0; index < valueCount; index += step) {
            checksum = (checksum * 31L) ^ buffer.get(index);
        }
        if (valueCount > 0) {
            checksum = (checksum * 31L) ^ buffer.get(valueCount - 1);
        }
        return checksum;
    }

    private static long sample(ByteBuffer buffer) {
        int valueCount = buffer.limit() / Integer.BYTES;
        long checksum = valueCount;
        int step = Math.max(1, valueCount / 128);
        for (int index = 0; index < valueCount; index += step) {
            checksum = (checksum * 31L) ^ buffer.getInt(index * Integer.BYTES);
        }
        if (valueCount > 0) {
            checksum = (checksum * 31L) ^ buffer.getInt((valueCount - 1) * Integer.BYTES);
        }
        return checksum;
    }

    private static void writeResults(List<BenchmarkResult> results) throws IOException {
        Path output = Path.of(System.getProperty("mattmc.perf.output",
                "build/perf/chunk-meshing-hotpaths.json"));
        Files.createDirectories(output.getParent());
        Files.writeString(output, toJson(results));
    }

    private static String toJson(List<BenchmarkResult> results) {
        StringBuilder builder = new StringBuilder();
        builder.append("{\n");
        builder.append("  \"implementation\": \"").append(implementation()).append("\",\n");
        builder.append("  \"quad_count\": ").append(QUAD_COUNT).append(",\n");
        builder.append("  \"warmup_iterations\": ").append(WARMUP_ITERATIONS).append(",\n");
        builder.append("  \"measure_iterations\": ").append(MEASURE_ITERATIONS).append(",\n");
        builder.append("  \"results\": [\n");
        for (int index = 0; index < results.size(); index++) {
            BenchmarkResult result = results.get(index);
            builder.append("    {\n");
            builder.append("      \"name\": \"").append(result.name).append("\",\n");
            builder.append(String.format(Locale.ROOT, "      \"mean_ms\": %.6f,%n", result.meanMillis()));
            builder.append(String.format(Locale.ROOT, "      \"median_ms\": %.6f,%n", result.medianMillis()));
            builder.append(String.format(Locale.ROOT, "      \"min_ms\": %.6f,%n", result.minMillis()));
            builder.append(String.format(Locale.ROOT, "      \"max_ms\": %.6f,%n", result.maxMillis()));
            builder.append(String.format(Locale.ROOT, "      \"mega_quads_per_second\": %.6f,%n",
                    result.megaQuadsPerSecond()));
            builder.append("      \"checksum\": ").append(result.checksum).append("\n");
            builder.append("    }");
            if (index + 1 < results.size()) {
                builder.append(',');
            }
            builder.append('\n');
        }
        builder.append("  ]\n");
        builder.append("}\n");
        return builder.toString();
    }

    private static String implementation() {
        try {
            Class.forName("net.sodium.client.render.chunk.vertex.format.NativeSectionMeshBuilder");
            return "rust-panama-native";
        } catch (ClassNotFoundException ignored) {
            return "java";
        }
    }

    private static boolean shouldRunPerfBenchmarks() {
        return Boolean.getBoolean("mattmc.runPerfBenchmarks")
                || Boolean.parseBoolean(System.getenv("MATTMC_RUN_PERF_BENCHMARKS"));
    }

    private static MeshFinisher createMeshFinisher() {
        try {
            Method sectionBuilderMethod = ChunkMeshBufferBuilder.class.getMethod("sectionBuilder");
            Method nativeFormatMethod = ChunkMeshBufferBuilder.class.getMethod("nativeFormat");
            Class<?> nativeSectionMeshBuilderClass =
                    Class.forName("net.sodium.client.render.chunk.vertex.format.NativeSectionMeshBuilder");
            Class<?> nativeChunkVertexFormatClass =
                    Class.forName("net.sodium.client.render.chunk.vertex.format.NativeChunkVertexFormat");
            Method finishMeshMethod = nativeSectionMeshBuilderClass.getMethod("finishMesh",
                    nativeChunkVertexFormatClass, int.class, boolean.class, boolean.class, boolean.class);
            Method getVertexDataMethod =
                    Class.forName("net.sodium.client.render.chunk.data.BuiltSectionMeshParts")
                            .getMethod("getVertexData");
            Class<?> nativeBufferClass = Class.forName("net.sodium.client.util.NativeBuffer");
            Method getDirectBufferMethod = nativeBufferClass.getMethod("getDirectBuffer");
            Method freeMethod = nativeBufferClass.getMethod("free");

            return builder -> {
                Object sectionBuilder = sectionBuilderMethod.invoke(builder);
                Object nativeFormat = nativeFormatMethod.invoke(builder);
                Object mesh = finishMeshMethod.invoke(sectionBuilder, nativeFormat, 0, false, true, false);
                Object vertexData = getVertexDataMethod.invoke(mesh);

                try {
                    ByteBuffer buffer = ((ByteBuffer) getDirectBufferMethod.invoke(vertexData))
                            .order(ByteOrder.nativeOrder());
                    return sample(buffer);
                } finally {
                    freeMethod.invoke(vertexData);
                }
            };
        } catch (ReflectiveOperationException ignored) {
            return builder -> sample(builder.slice().order(ByteOrder.nativeOrder()));
        }
    }

    @FunctionalInterface
    private interface MeshFinisher {
        long finish(ChunkMeshBufferBuilder builder) throws Exception;
    }

    @FunctionalInterface
    private interface BenchmarkAction {
        long run() throws Exception;
    }

    private record BenchmarkResult(String name, double meanNanos, long medianNanos, long minNanos, long maxNanos,
            long checksum) {
        double meanMillis() {
            return this.meanNanos / 1_000_000.0;
        }

        double medianMillis() {
            return this.medianNanos / 1_000_000.0;
        }

        double minMillis() {
            return this.minNanos / 1_000_000.0;
        }

        double maxMillis() {
            return this.maxNanos / 1_000_000.0;
        }

        double megaQuadsPerSecond() {
            return QUAD_COUNT / (this.meanNanos / 1_000_000_000.0) / 1_000_000.0;
        }
    }
}
