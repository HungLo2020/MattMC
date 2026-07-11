package net.sodium.client.perf;

import net.sodium.client.SodiumClientMod;
import net.sodium.client.gui.SodiumGameOptions;
import net.sodium.client.render.chunk.translucent_sorting.data.TranslucentData;
import net.sodium.client.render.chunk.vertex.format.ChunkMeshFormats;
import net.sodium.client.render.chunk.vertex.format.NativeChunkMeshEncoder;
import net.sodium.client.render.chunk.vertex.format.NativeSectionMeshBuilder;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.management.BufferPoolMXBean;
import java.lang.management.ManagementFactory;
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
        float[] basePositions = syntheticQuadBasePositions();

        List<BenchmarkResult> results = new ArrayList<>();
        results.add(measure("translucent_quad_index_emission", () -> runIndexEmission(quadIndexes, indexBuffer)));

        NativeSectionMeshBuilder.FacingBuffer builder =
                NativeSectionMeshBuilder.createEncodedFacingBuffer(ChunkMeshFormats.COMPACT, QUAD_COUNT * 4);
        try {
            results.add(measure("compact_chunk_mesh_buffer_build", () -> runMeshBufferBuild(builder, basePositions)));
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

    private static long runMeshBufferBuild(NativeSectionMeshBuilder.FacingBuffer builder,
            float[] basePositions)
            throws Exception {
        builder.start(SECTION_INDEX);
        for (int index = 0; index < QUAD_COUNT; index++) {
            int baseIndex = index * 3;
            int materialBits = materialBits(index);
            byte blockEmission = (byte) (index & 15);
            byte renderType = (byte) (index & 3);
            int blockId = index & 0xffff;
            int localX = index & 15;
            int localY = (index >>> 4) & 15;
            int localZ = (index >>> 8) & 15;
            long quadAddress = builder.prepareStagedQuad(materialBits, blockEmission, renderType, false, blockId,
                    localX, localY, localZ);
            writeQuad(quadAddress, blockEmission, renderType, blockId, localX, localY, localZ, materialBits,
                    basePositions[baseIndex], basePositions[baseIndex + 1], basePositions[baseIndex + 2]);
            builder.commitStagedQuad();
        }

        return ((long) builder.count() << 32) ^ MESH_FINISHER.finish(builder);
    }

    private static BenchmarkResult measure(String name, BenchmarkAction action) throws Exception {
        settleMemory();
        MemorySnapshot before = MemorySnapshot.capture();
        MemorySnapshot peak = before;
        long checksum = 0L;
        for (int iteration = 0; iteration < WARMUP_ITERATIONS; iteration++) {
            checksum ^= action.run();
            peak = peak.max(MemorySnapshot.capture());
        }

        long[] samples = new long[MEASURE_ITERATIONS];
        for (int iteration = 0; iteration < MEASURE_ITERATIONS; iteration++) {
            long start = System.nanoTime();
            checksum ^= action.run();
            samples[iteration] = System.nanoTime() - start;
            peak = peak.max(MemorySnapshot.capture());
        }
        MemorySnapshot after = MemorySnapshot.capture();
        peak = peak.max(after);

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
                checksum,
                before,
                after,
                peak);
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

    private static float[] syntheticQuadBasePositions() {
        float[] positions = new float[QUAD_COUNT * 3];
        for (int index = 0; index < QUAD_COUNT; index++) {
            int baseIndex = index * 3;
            positions[baseIndex] = (index & 15) + ((index >>> 4) & 3) * 0.03125F;
            positions[baseIndex + 1] = ((index >>> 6) & 15) + 0.125F;
            positions[baseIndex + 2] = ((index >>> 10) & 15) + 0.25F;
        }

        return positions;
    }

    private static void writeQuad(long quadAddress, byte blockEmission, byte renderType, int blockId,
            int localX, int localY, int localZ, int materialBits, float baseX, float baseY, float baseZ) {
        NativeChunkMeshEncoder.writeNativeQuad(quadAddress, blockEmission, renderType, false, blockId, localX, localY,
                localZ, materialBits,
                baseX, baseY, baseZ, 0xff806040, 0.5F, 0.0F, 0.0F, 0x00f000f0,
                baseX + 1.0F, baseY, baseZ, 0xff806040, 0.5F, 1.0F, 0.0F, 0x00f000f0,
                baseX + 1.0F, baseY + 1.0F, baseZ, 0xff806040, 0.5F, 1.0F, 1.0F, 0x00f000f0,
                baseX, baseY + 1.0F, baseZ, 0xff806040, 0.5F, 0.0F, 1.0F, 0x00f000f0);
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
            builder.append("      \"checksum\": ").append(result.checksum).append(",\n");
            appendMemoryJson(builder, result);
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

    private static void appendMemoryJson(StringBuilder builder, BenchmarkResult result) {
        builder.append("      \"memory\": {\n");
        appendSnapshotJson(builder, "before", result.memoryBefore, true);
        appendSnapshotJson(builder, "after", result.memoryAfter, true);
        appendSnapshotJson(builder, "peak", result.memoryPeak, true);
        appendMemoryDeltaJson(builder, "heap_used_delta_bytes", result.memoryBefore.heapUsedBytes,
                result.memoryAfter.heapUsedBytes, true);
        appendMemoryDeltaJson(builder, "direct_used_delta_bytes", result.memoryBefore.directUsedBytes,
                result.memoryAfter.directUsedBytes, true);
        appendMemoryDeltaJson(builder, "rss_delta_bytes", result.memoryBefore.rssBytes,
                result.memoryAfter.rssBytes, true);
        appendMemoryDeltaJson(builder, "rss_peak_delta_bytes", result.memoryBefore.rssBytes,
                result.memoryPeak.rssBytes, true);
        appendMemoryDeltaJson(builder, "rss_high_water_delta_bytes", result.memoryBefore.rssHighWaterBytes,
                result.memoryPeak.rssHighWaterBytes, false);
        builder.append("      }\n");
    }

    private static void appendSnapshotJson(StringBuilder builder, String name, MemorySnapshot snapshot,
            boolean trailingComma) {
        builder.append("        \"").append(name).append("\": {\n");
        appendMemoryValueJson(builder, "heap_used_bytes", snapshot.heapUsedBytes, true);
        appendMemoryValueJson(builder, "non_heap_used_bytes", snapshot.nonHeapUsedBytes, true);
        appendMemoryValueJson(builder, "direct_used_bytes", snapshot.directUsedBytes, true);
        appendMemoryValueJson(builder, "mapped_used_bytes", snapshot.mappedUsedBytes, true);
        appendMemoryValueJson(builder, "rss_bytes", snapshot.rssBytes, true);
        appendMemoryValueJson(builder, "rss_high_water_bytes", snapshot.rssHighWaterBytes, false);
        builder.append("        }");
        if (trailingComma) {
            builder.append(',');
        }
        builder.append('\n');
    }

    private static void appendMemoryDeltaJson(StringBuilder builder, String name, long before, long after,
            boolean trailingComma) {
        builder.append("        \"").append(name).append("\": ");
        if (before < 0 || after < 0) {
            builder.append("null");
        } else {
            builder.append(after - before);
        }
        if (trailingComma) {
            builder.append(',');
        }
        builder.append('\n');
    }

    private static void appendMemoryValueJson(StringBuilder builder, String name, long value, boolean trailingComma) {
        builder.append("          \"").append(name).append("\": ");
        if (value < 0) {
            builder.append("null");
        } else {
            builder.append(value);
        }
        if (trailingComma) {
            builder.append(',');
        }
        builder.append('\n');
    }

    private static void settleMemory() throws InterruptedException {
        System.gc();
        Thread.sleep(50L);
    }

    private static MeshFinisher createMeshFinisher() {
        try {
            Method sectionBuilderMethod = NativeSectionMeshBuilder.FacingBuffer.class.getMethod("sectionBuilder");
            Method nativeFormatMethod = NativeSectionMeshBuilder.FacingBuffer.class.getMethod("nativeFormat");
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
        long finish(NativeSectionMeshBuilder.FacingBuffer builder) throws Exception;
    }

    @FunctionalInterface
    private interface BenchmarkAction {
        long run() throws Exception;
    }

    private record MemorySnapshot(long heapUsedBytes, long nonHeapUsedBytes, long directUsedBytes,
            long mappedUsedBytes, long rssBytes, long rssHighWaterBytes) {
        static MemorySnapshot capture() {
            Runtime runtime = Runtime.getRuntime();
            long heapUsed = runtime.totalMemory() - runtime.freeMemory();
            long nonHeapUsed = ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage().getUsed();
            long directUsed = bufferPoolMemoryUsed("direct");
            long mappedUsed = bufferPoolMemoryUsed("mapped");
            long rss = procStatusKilobytes("VmRSS");
            long rssHighWater = procStatusKilobytes("VmHWM");
            return new MemorySnapshot(heapUsed, nonHeapUsed, directUsed, mappedUsed, rss, rssHighWater);
        }

        MemorySnapshot max(MemorySnapshot other) {
            return new MemorySnapshot(
                    Math.max(this.heapUsedBytes, other.heapUsedBytes),
                    Math.max(this.nonHeapUsedBytes, other.nonHeapUsedBytes),
                    Math.max(this.directUsedBytes, other.directUsedBytes),
                    Math.max(this.mappedUsedBytes, other.mappedUsedBytes),
                    Math.max(this.rssBytes, other.rssBytes),
                    Math.max(this.rssHighWaterBytes, other.rssHighWaterBytes));
        }

        private static long bufferPoolMemoryUsed(String poolName) {
            for (BufferPoolMXBean pool : ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class)) {
                if (poolName.equals(pool.getName())) {
                    return pool.getMemoryUsed();
                }
            }
            return -1L;
        }

        private static long procStatusKilobytes(String key) {
            try {
                for (String line : Files.readAllLines(Path.of("/proc/self/status"))) {
                    if (!line.startsWith(key + ":")) {
                        continue;
                    }

                    String[] parts = line.trim().split("\\s+");
                    if (parts.length >= 2) {
                        return Long.parseLong(parts[1]) * 1024L;
                    }
                }
            } catch (IOException | NumberFormatException ignored) {
                // /proc is Linux-specific. Missing values are emitted as null in JSON.
            }
            return -1L;
        }
    }

    private record BenchmarkResult(String name, double meanNanos, long medianNanos, long minNanos, long maxNanos,
            long checksum, MemorySnapshot memoryBefore, MemorySnapshot memoryAfter, MemorySnapshot memoryPeak) {
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
