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
import java.lang.management.BufferPoolMXBean;
import java.lang.management.GarbageCollectorMXBean;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

@Tag("performance")
class ChunkMeshingHotPathBenchmarkTest {
    private static final int QUAD_COUNT = Integer.getInteger("mattmc.perf.quads", 32_768);
    private static final int WARMUP_ITERATIONS = Integer.getInteger("mattmc.perf.warmup", 8);
    private static final int MEASURE_ITERATIONS = Integer.getInteger("mattmc.perf.iterations", 25);
    private static final long WARMUP_MILLIS = Long.getLong("mattmc.perf.warmupMillis", 1_500L);
    private static final long MEASURE_MILLIS = Long.getLong("mattmc.perf.measureMillis", 2_500L);
    private static final int FORK_INDEX = Integer.getInteger("mattmc.perf.forkIndex", 0);
    private static final int SECTION_INDEX = 11;
    private static final int SECTION_BLOCKS = 16 * 16 * 16;
    private static final int INDEX_BYTES_PER_QUAD = TranslucentData.INDICES_PER_QUAD * Integer.BYTES;
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

        for (ReplaySection section : replayCorpus()) {
            ChunkMeshBufferBuilder replayBuilder = new ChunkMeshBufferBuilder(ChunkMeshFormats.COMPACT, Math.max(4, section.expectedQuads() * 4));
            ChunkVertexEncoder.Vertex[][] replayQuads = syntheticReplayQuads(section);
            int[] sectionStates = replaySectionStates(section);
            try {
                results.add(measure("full_section_replay_" + section.name,
                        () -> runJavaFullSectionReplay(section, replayBuilder, sectionStates)));
                results.add(measure("prebuilt_quad_replay_section_" + section.name,
                        () -> runJavaReplaySection(section, replayBuilder, replayQuads)));
            } finally {
                replayBuilder.destroy();
            }
        }

        results.add(measure("transfer_copy_4k", () -> runTransferCopy(4 * 1024)));
        results.add(measure("transfer_copy_64k", () -> runTransferCopy(64 * 1024)));
        results.add(measure("transfer_copy_1m", () -> runTransferCopy(1024 * 1024)));

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
        BenchmarkAccounting.record(0, 0, 0,
                (long) QUAD_COUNT * INDEX_BYTES_PER_QUAD, QUAD_COUNT, 0, 0);
        return sample(indexBuffer, QUAD_COUNT * TranslucentData.INDICES_PER_QUAD);
    }

    private static long runMeshBufferBuild(ChunkMeshBufferBuilder builder, ChunkVertexEncoder.Vertex[][] quads)
            throws Exception {
        builder.start(SECTION_INDEX);
        for (int index = 0; index < QUAD_COUNT; index++) {
            builder.push(quads[index], materialBits(index));
        }

        BenchmarkAccounting.record(0, 0,
                (long) builder.count() * ChunkMeshFormats.COMPACT.getVertexFormat().getStride(),
                (long) QUAD_COUNT * INDEX_BYTES_PER_QUAD, QUAD_COUNT, 0, 0);
        return ((long) builder.count() << 32) ^ MESH_FINISHER.finish(builder);
    }

    private static long runJavaReplaySection(ReplaySection section, ChunkMeshBufferBuilder builder,
            ChunkVertexEncoder.Vertex[][] quads) throws Exception {
        StageTimer stages = StageTimer.start();
        builder.start(SECTION_INDEX);
        stages.mark("prebuilt_quad_setup");
        int quadCount = section.expectedQuads();
        if (quadCount == 0) {
            stages.mark("empty_section");
            BenchmarkAccounting.record(0, 0, 0, 0, 0,
                    section.fallbackLikeBlocks, section.fallbackLikeBlocks);
            return section.expectedSummary() ^ stages.checksum();
        }
        for (int index = 0; index < quadCount; index++) {
            builder.push(quads[index], materialBits(index));
        }
        stages.mark("prebuilt_quad_packing");
        BenchmarkAccounting.record(0, 0,
                (long) builder.count() * ChunkMeshFormats.COMPACT.getVertexFormat().getStride(),
                (long) quadCount * INDEX_BYTES_PER_QUAD, quadCount,
                section.fallbackLikeBlocks, section.fallbackLikeBlocks);
        long checksum = ((long) builder.count() << 32) ^ MESH_FINISHER.finish(builder);
        stages.mark("final_assembly_and_handoff");
        return checksum ^ section.expectedSummary() ^ stages.checksum();
    }

    private static long runJavaFullSectionReplay(ReplaySection section, ChunkMeshBufferBuilder builder,
            int[] sectionStates) throws Exception {
        StageTimer stages = StageTimer.start();
        builder.start(SECTION_INDEX);
        stages.mark("java_section_setup");

        ChunkVertexEncoder.Vertex[] vertices = ChunkVertexEncoder.Vertex.uninitializedQuad();
        int emittedQuads = 0;
        long semanticChecksum = section.expectedSummary();

        for (int index = 0; index < SECTION_BLOCKS; index++) {
            int stateId = sectionStates[index];
            if (stateId == 0) {
                continue;
            }

            int localX = index & 15;
            int localY = (index >>> 8) & 15;
            int localZ = (index >>> 4) & 15;
            long seed = 0x9e3779b97f4a7c15L ^ (long) index * 0xbf58476d1ce4e5b9L;

            if (stateId == 200) {
                emitJavaReplayFluidQuad(builder, vertices, stateId, localX, localY, localZ, seed);
                semanticChecksum = semanticChecksum * 31L + fluidSemanticHash(sectionStates, index, seed);
                emittedQuads++;
            } else {
                int modelId = selectReplayModel(stateId, seed);
                if (!isReplayFaceCulled(sectionStates, index, stateId, modelId)) {
                    emitJavaReplayModelQuad(builder, vertices, stateId, modelId, localX, localY, localZ, seed);
                    emittedQuads++;
                }
                semanticChecksum = semanticChecksum * 31L + modelId;
            }
        }
        stages.mark("java_section_scan_lookup_and_emit");

        for (int index = 0; index < section.fallbackLikeBlocks; index++) {
            int localX = index & 15;
            int localY = (index >>> 8) & 15;
            int localZ = (index >>> 4) & 15;
            writeReplayQuad(vertices, localX, localY, localZ, 0xff806040, 0.5F, 0x00f000f0,
                    index ^ section.name.hashCode());
            builder.push(vertices, materialBits(index));
            emittedQuads++;
            semanticChecksum = semanticChecksum * 31L + index;
        }
        stages.mark("java_callback_fallback_geometry");

        BenchmarkAccounting.record(0, 0,
                (long) builder.count() * ChunkMeshFormats.COMPACT.getVertexFormat().getStride(),
                (long) emittedQuads * INDEX_BYTES_PER_QUAD, emittedQuads,
                section.fallbackLikeBlocks, section.fallbackLikeBlocks);
        if (emittedQuads == 0) {
            stages.mark("final_assembly_and_handoff");
            return semanticChecksum ^ stages.checksum();
        }
        long checksum = ((long) builder.count() << 32) ^ MESH_FINISHER.finish(builder);
        stages.mark("final_assembly_and_handoff");
        return checksum ^ semanticChecksum ^ stages.checksum();
    }

    private static long runTransferCopy(int bytes) {
        ByteBuffer source = ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder());
        ByteBuffer target = ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder());
        for (int offset = 0; offset < bytes; offset += Integer.BYTES) {
            source.putInt(offset, offset * 31);
        }
        target.clear();
        source.clear();
        target.put(source);
        BenchmarkAccounting.record(0, bytes, bytes, 0, 0, 0, 0);
        return sample(target.order(ByteOrder.nativeOrder()));
    }

    private static BenchmarkResult measure(String name, BenchmarkAction action) throws Exception {
        settleMemory();
        MemorySnapshot before = MemorySnapshot.capture();
        GcSnapshot gcBefore = GcSnapshot.capture();
        MemorySnapshot peak = before;
        long checksum = 0L;
        long warmupDeadline = System.nanoTime() + WARMUP_MILLIS * 1_000_000L;
        int warmupCount = 0;
        do {
            checksum ^= action.run();
            peak = peak.max(MemorySnapshot.capture());
            warmupCount++;
        } while (warmupCount < WARMUP_ITERATIONS || System.nanoTime() < warmupDeadline);

        StageTimer.reset();
        BenchmarkAccounting.reset();
        List<Long> sampleList = new ArrayList<>();
        long measureDeadline = System.nanoTime() + MEASURE_MILLIS * 1_000_000L;
        do {
            long start = System.nanoTime();
            checksum ^= action.run();
            sampleList.add(System.nanoTime() - start);
            peak = peak.max(MemorySnapshot.capture());
        } while (sampleList.size() < MEASURE_ITERATIONS || System.nanoTime() < measureDeadline);
        MemorySnapshot after = MemorySnapshot.capture();
        GcSnapshot gcAfter = GcSnapshot.capture();
        peak = peak.max(after);
        Map<String, Long> stageNanos = StageTimer.snapshot();
        AccountingSnapshot accounting = BenchmarkAccounting.snapshot();

        long[] samples = new long[sampleList.size()];
        for (int index = 0; index < sampleList.size(); index++) {
            samples[index] = sampleList.get(index);
        }
        long[] sorted = samples.clone();
        Arrays.sort(sorted);

        long total = 0L;
        for (long sample : samples) {
            total += sample;
        }
        double mean = total / (double) samples.length;
        double variance = 0.0D;
        for (long sample : samples) {
            double delta = sample - mean;
            variance += delta * delta;
        }
        variance /= samples.length;

        return new BenchmarkResult(
                name,
                mean,
                sorted[sorted.length / 2],
                percentile(sorted, 0.90D),
                percentile(sorted, 0.99D),
                sorted[0],
                sorted[sorted.length - 1],
                Math.sqrt(variance),
                samples.length,
                warmupCount,
                checksum,
                stageNanos,
                accounting,
                before,
                after,
                peak,
                gcBefore,
                gcAfter);
    }

    private static long percentile(long[] sorted, double percentile) {
        if (sorted.length == 0) {
            return 0L;
        }
        int index = (int) Math.ceil(percentile * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(sorted.length - 1, index))];
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

    private static ChunkVertexEncoder.Vertex[][] syntheticReplayQuads(ReplaySection section) {
        int quadCount = section.expectedQuads();
        ChunkVertexEncoder.Vertex[][] quads = new ChunkVertexEncoder.Vertex[Math.max(1, quadCount)][];
        for (int index = 0; index < quads.length; index++) {
            float baseX = index & 15;
            float baseY = (index >>> 8) & 15;
            float baseZ = (index >>> 4) & 15;
            quads[index] = quad(baseX, baseY, baseZ, index ^ section.name.hashCode());
        }
        return quads;
    }

    private static int[] replaySectionStates(ReplaySection section) {
        int[] states = new int[SECTION_BLOCKS];
        for (int index = 0; index < SECTION_BLOCKS; index++) {
            states[index] = stateForReplayIndex(section, index);
        }
        return states;
    }

    private static List<ReplaySection> replayCorpus() {
        return List.of(
                new ReplaySection("empty", 0, 0, 0, 0, 0, 0, 1),
                new ReplaySection("dense_cube_terrain", 4096, 0, 0, 0, 0, 0, 4),
                new ReplaySection("normal_surface_terrain", 1536, 0, 0, 0, 0, 0, 8),
                new ReplaySection("foliage_tinted_models", 512, 1024, 0, 0, 0, 0, 6),
                new ReplaySection("weighted_and_multipart_models", 384, 384, 768, 0, 0, 0, 5),
                new ReplaySection("fluid_heavy", 64, 0, 0, 3200, 0, 0, 8),
                new ReplaySection("waterlogged_geometry", 768, 384, 0, 768, 0, 0, 5),
                new ReplaySection("translucent_heavy", 0, 0, 0, 2048, 1024, 0, 4),
                new ReplaySection("complex_modded_static_serializable", 640, 768, 1024, 384, 384, 256, 3));
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

    private static void emitJavaReplayModelQuad(ChunkMeshBufferBuilder builder, ChunkVertexEncoder.Vertex[] vertices,
            int stateId, int modelId, int localX, int localY, int localZ, long seed) {
        int tint = stateId == 101 ? 0xff70aa50 : 0xffffffff;
        float ao = stateId == 100 ? 0.8F : 1.0F;
        int light = 0x00f000f0 | ((stateId & 15) << 4);
        float offsetX = stateId == 102 ? ((seed & 1L) == 0L ? 0.0F : 0.125F) : 0.0F;
        float offsetZ = stateId == 102 ? ((seed & 2L) == 0L ? 0.0F : -0.125F) : 0.0F;
        writeReplayQuad(vertices, localX + offsetX, localY, localZ + offsetZ, tint, ao, light,
                modelId ^ (int) seed);
        builder.push(vertices, materialBits(modelId));
    }

    private static void emitJavaReplayFluidQuad(ChunkMeshBufferBuilder builder, ChunkVertexEncoder.Vertex[] vertices,
            int stateId, int localX, int localY, int localZ, long seed) {
        float height = 0.8888889F;
        float flow = ((seed >>> 2) & 1L) == 0L ? 0.0F : 0.25F;
        int color = 0xcc3f76e4;
        int light = 0x00f000f0;
        writeVertex(vertices[0], localX, localY + height, localZ, 0.0F + flow, 0.0F, seedAsInt(seed, 0), color, 1.0F, light);
        writeVertex(vertices[1], localX + 1.0F, localY + height, localZ, 1.0F + flow, 0.0F, seedAsInt(seed, 1), color, 1.0F, light);
        writeVertex(vertices[2], localX + 1.0F, localY + height, localZ + 1.0F, 1.0F + flow, 1.0F, seedAsInt(seed, 2), color, 1.0F, light);
        writeVertex(vertices[3], localX, localY + height, localZ + 1.0F, 0.0F + flow, 1.0F, seedAsInt(seed, 3), color, 1.0F, light);
        builder.push(vertices, materialBits(stateId));
    }

    private static void writeReplayQuad(ChunkVertexEncoder.Vertex[] vertices, float baseX, float baseY, float baseZ,
            int color, float ao, int light, int seed) {
        writeVertex(vertices[0], baseX, baseY, baseZ, 0.0F, 0.0F, seed, color, ao, light);
        writeVertex(vertices[1], baseX + 1.0F, baseY, baseZ, 1.0F, 0.0F, seed, color, ao, light);
        writeVertex(vertices[2], baseX + 1.0F, baseY + 1.0F, baseZ, 1.0F, 1.0F, seed, color, ao, light);
        writeVertex(vertices[3], baseX, baseY + 1.0F, baseZ, 0.0F, 1.0F, seed, color, ao, light);
    }

    private static void writeVertex(ChunkVertexEncoder.Vertex vertex, float x, float y, float z,
            float u, float v, int seed, int color, float ao, int light) {
        ChunkVertexEncoder.Vertex.writeVertex(vertex, x, y, z, color, ao, u, v, light);
        vertex.iris$setData((byte) (seed & 15), (byte) (seed & 3), seed & 0xffff,
                seed & 15, (seed >>> 4) & 15, (seed >>> 8) & 15);
    }

    private static int selectReplayModel(int stateId, long seed) {
        if (stateId == 102) {
            return ((seed >>> 4) & 3L) == 0L ? 78 : 77;
        }
        if (stateId == 101) {
            return 78;
        }
        return 77;
    }

    private static boolean isReplayFaceCulled(int[] states, int index, int stateId, int modelId) {
        // The deterministic corpus uses one visible representative quad per non-air state.
        // Still read the neighbor slot so the timed region includes the culling lookup shape.
        int localZ = (index >>> 4) & 15;
        int neighborIndex = localZ == 0 ? -1 : index - 16;
        return neighborIndex >= 0 && states[neighborIndex] == stateId && modelId == Integer.MIN_VALUE;
    }

    private static long fluidSemanticHash(int[] states, int index, long seed) {
        int above = index + 256 < states.length ? states[index + 256] : 0;
        int north = ((index >>> 4) & 15) == 0 ? 0 : states[index - 16];
        int south = ((index >>> 4) & 15) == 15 ? 0 : states[index + 16];
        return seed ^ ((long) above << 32) ^ ((long) north << 16) ^ south;
    }

    private static int seedAsInt(long seed, int salt) {
        return (int) (seed ^ (seed >>> 32) ^ (salt * 0x9e3779b9));
    }

    private static int stateForReplayIndex(ReplaySection section, int index) {
        if (index < section.solidBlocks) {
            return 100;
        }
        index -= section.solidBlocks;
        if (index < section.foliageBlocks) {
            return 101;
        }
        index -= section.foliageBlocks;
        if (index < section.weightedMultipartBlocks) {
            return 102;
        }
        index -= section.weightedMultipartBlocks;
        if (index < section.fluidBlocks + section.translucentBlocks) {
            return 200;
        }
        return 0;
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
        builder.append("  \"warmup_millis\": ").append(WARMUP_MILLIS).append(",\n");
        builder.append("  \"measure_millis\": ").append(MEASURE_MILLIS).append(",\n");
        builder.append("  \"fork_index\": ").append(FORK_INDEX).append(",\n");
        builder.append("  \"benchmark_limitations\": [\n");
        builder.append("    \"Historical hot-path rows use synthetic repeated quads and are retained only for trend comparison.\",\n");
        builder.append("    \"full_section_replay rows scan deterministic section-shaped state data and emit geometry through the frozen Java mesh builder inside the timed region.\",\n");
        builder.append("    \"prebuilt_quad_replay_section rows are frozen-only historical rows that push already-created quads and must not be compared to current full-section replay.\",\n");
        builder.append("    \"Replay section rows are deterministic section-shaped workloads, not live gameplay captures.\",\n");
        builder.append("    \"Frozen Java replay emits equivalent deterministic section work through Java mesh builders, not current native snapshot records.\"\n");
        builder.append("  ],\n");
        builder.append("  \"results\": [\n");
        for (int index = 0; index < results.size(); index++) {
            BenchmarkResult result = results.get(index);
            builder.append("    {\n");
            builder.append("      \"name\": \"").append(result.name).append("\",\n");
            builder.append(String.format(Locale.ROOT, "      \"mean_ms\": %.6f,%n", result.meanMillis()));
            builder.append(String.format(Locale.ROOT, "      \"median_ms\": %.6f,%n", result.medianMillis()));
            builder.append(String.format(Locale.ROOT, "      \"stddev_ms\": %.6f,%n", result.stddevMillis()));
            builder.append(String.format(Locale.ROOT, "      \"p90_ms\": %.6f,%n", result.p90Millis()));
            builder.append(String.format(Locale.ROOT, "      \"p99_ms\": %.6f,%n", result.p99Millis()));
            builder.append(String.format(Locale.ROOT, "      \"min_ms\": %.6f,%n", result.minMillis()));
            builder.append(String.format(Locale.ROOT, "      \"max_ms\": %.6f,%n", result.maxMillis()));
            builder.append(String.format(Locale.ROOT, "      \"mega_quads_per_second\": %.6f,%n",
                    result.megaQuadsPerSecond()));
            builder.append("      \"samples\": ").append(result.sampleCount).append(",\n");
            builder.append("      \"warmup_invocations\": ").append(result.warmupCount).append(",\n");
            builder.append("      \"checksum\": ").append(result.checksum).append(",\n");
            appendStageJson(builder, result.stageNanos, result.sampleCount);
            appendAccountingJson(builder, result.accounting, result.sampleCount);
            appendGcJson(builder, result.gcBefore, result.gcAfter);
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

    private static void appendAccountingJson(StringBuilder builder, AccountingSnapshot accounting, int samples) {
        builder.append("      \"accounting\": {\n");
        appendAccountingValueJson(builder, "native_calls_total", accounting.nativeCalls, true);
        appendAccountingValueJson(builder, "native_calls_per_invocation", accounting.nativeCalls, samples, true);
        appendAccountingValueJson(builder, "abi_payload_bytes_total", accounting.abiPayloadBytes, true);
        appendAccountingValueJson(builder, "abi_payload_bytes_per_invocation", accounting.abiPayloadBytes, samples, true);
        appendAccountingValueJson(builder, "bytes_copied_total", accounting.bytesCopied, true);
        appendAccountingValueJson(builder, "bytes_copied_per_invocation", accounting.bytesCopied, samples, true);
        appendAccountingValueJson(builder, "output_vertex_bytes_total", accounting.outputVertexBytes, true);
        appendAccountingValueJson(builder, "output_vertex_bytes_per_invocation", accounting.outputVertexBytes, samples, true);
        appendAccountingValueJson(builder, "output_index_bytes_total", accounting.outputIndexBytes, true);
        appendAccountingValueJson(builder, "output_index_bytes_per_invocation", accounting.outputIndexBytes, samples, true);
        appendAccountingValueJson(builder, "output_quads_total", accounting.outputQuads, true);
        appendAccountingValueJson(builder, "output_quads_per_invocation", accounting.outputQuads, samples, true);
        appendAccountingValueJson(builder, "fallback_like_blocks_total", accounting.fallbackBlocks, true);
        appendAccountingValueJson(builder, "fallback_like_blocks_per_invocation", accounting.fallbackBlocks, samples, true);
        appendAccountingValueJson(builder, "fallback_like_quads_total", accounting.fallbackQuads, true);
        appendAccountingValueJson(builder, "fallback_like_quads_per_invocation", accounting.fallbackQuads, samples, false);
        builder.append("      },\n");
    }

    private static void appendGcJson(StringBuilder builder, GcSnapshot before, GcSnapshot after) {
        builder.append("      \"gc\": {\n");
        builder.append("        \"collection_count_delta\": ").append(after.collectionCount - before.collectionCount).append(",\n");
        builder.append("        \"collection_time_millis_delta\": ").append(after.collectionTimeMillis - before.collectionTimeMillis).append('\n');
        builder.append("      },\n");
    }

    private static void appendAccountingValueJson(StringBuilder builder, String name, long value,
            boolean trailingComma) {
        builder.append("        \"").append(name).append("\": ").append(value);
        if (trailingComma) {
            builder.append(',');
        }
        builder.append('\n');
    }

    private static void appendAccountingValueJson(StringBuilder builder, String name, long value, int samples,
            boolean trailingComma) {
        builder.append("        \"").append(name).append("\": ")
                .append(String.format(Locale.ROOT, "%.3f", value / (double) Math.max(1, samples)));
        if (trailingComma) {
            builder.append(',');
        }
        builder.append('\n');
    }

    private static void appendStageJson(StringBuilder builder, Map<String, Long> stageNanos, int samples) {
        builder.append("      \"stage_timing_ms_per_invocation\": {\n");
        int index = 0;
        for (Map.Entry<String, Long> entry : stageNanos.entrySet()) {
            double millis = entry.getValue() / (double) Math.max(1, samples) / 1_000_000.0D;
            builder.append("        \"").append(entry.getKey()).append("\": ")
                    .append(String.format(Locale.ROOT, "%.6f", millis));
            if (++index < stageNanos.size()) {
                builder.append(',');
            }
            builder.append('\n');
        }
        builder.append("      },\n");
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

    private record GcSnapshot(long collectionCount, long collectionTimeMillis) {
        static GcSnapshot capture() {
            long count = 0L;
            long time = 0L;
            for (GarbageCollectorMXBean collector : ManagementFactory.getGarbageCollectorMXBeans()) {
                long collectorCount = collector.getCollectionCount();
                long collectorTime = collector.getCollectionTime();
                if (collectorCount > 0) {
                    count += collectorCount;
                }
                if (collectorTime > 0) {
                    time += collectorTime;
                }
            }
            return new GcSnapshot(count, time);
        }
    }

    private record AccountingSnapshot(long nativeCalls, long abiPayloadBytes, long bytesCopied,
            long outputVertexBytes, long outputIndexBytes, long outputQuads, long fallbackBlocks,
            long fallbackQuads) {
    }

    private record BenchmarkResult(String name, double meanNanos, long medianNanos, long p90Nanos, long p99Nanos,
            long minNanos, long maxNanos, double stddevNanos, int sampleCount, int warmupCount,
            long checksum, Map<String, Long> stageNanos, AccountingSnapshot accounting,
            MemorySnapshot memoryBefore, MemorySnapshot memoryAfter, MemorySnapshot memoryPeak,
            GcSnapshot gcBefore, GcSnapshot gcAfter) {
        double meanMillis() {
            return this.meanNanos / 1_000_000.0;
        }

        double medianMillis() {
            return this.medianNanos / 1_000_000.0;
        }

        double stddevMillis() {
            return this.stddevNanos / 1_000_000.0;
        }

        double p90Millis() {
            return this.p90Nanos / 1_000_000.0;
        }

        double p99Millis() {
            return this.p99Nanos / 1_000_000.0;
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

    private record ReplaySection(String name, int solidBlocks, int foliageBlocks, int weightedMultipartBlocks,
            int fluidBlocks, int translucentBlocks, int fallbackLikeBlocks, int weight) {
        int expectedQuads() {
            return this.solidBlocks + this.foliageBlocks + this.weightedMultipartBlocks
                    + this.fluidBlocks + this.translucentBlocks + this.fallbackLikeBlocks;
        }

        long expectedSummary() {
            long summary = this.weight;
            summary = summary * 31L + this.solidBlocks;
            summary = summary * 31L + this.foliageBlocks;
            summary = summary * 31L + this.weightedMultipartBlocks;
            summary = summary * 31L + this.fluidBlocks;
            summary = summary * 31L + this.translucentBlocks;
            summary = summary * 31L + this.fallbackLikeBlocks;
            return summary;
        }
    }

    private static final class StageTimer {
        private static final ThreadLocal<LinkedHashMap<String, Long>> STAGES =
                ThreadLocal.withInitial(LinkedHashMap::new);
        private final long startedAt;
        private long previous;
        private long checksum;

        private StageTimer(long startedAt) {
            this.startedAt = startedAt;
            this.previous = startedAt;
        }

        static StageTimer start() {
            return new StageTimer(System.nanoTime());
        }

        void mark(String name) {
            long now = System.nanoTime();
            long delta = now - this.previous;
            STAGES.get().merge(name, delta, Long::sum);
            this.checksum = this.checksum * 31L + name.hashCode();
            this.checksum = this.checksum * 31L + delta;
            this.previous = now;
        }

        long checksum() {
            return this.checksum ^ (this.previous - this.startedAt);
        }

        static void reset() {
            STAGES.get().clear();
        }

        static Map<String, Long> snapshot() {
            return new LinkedHashMap<>(STAGES.get());
        }
    }

    private static final class BenchmarkAccounting {
        private static final ThreadLocal<MutableAccounting> ACCOUNTING =
                ThreadLocal.withInitial(MutableAccounting::new);

        static void reset() {
            ACCOUNTING.get().reset();
        }

        static void record(long nativeCalls, long abiPayloadBytes, long outputVertexBytes,
                long outputIndexBytes, long outputQuads, long fallbackBlocks, long fallbackQuads) {
            MutableAccounting accounting = ACCOUNTING.get();
            accounting.nativeCalls += nativeCalls;
            accounting.abiPayloadBytes += abiPayloadBytes;
            accounting.bytesCopied += outputVertexBytes == abiPayloadBytes ? outputVertexBytes : 0;
            accounting.outputVertexBytes += outputVertexBytes;
            accounting.outputIndexBytes += outputIndexBytes;
            accounting.outputQuads += outputQuads;
            accounting.fallbackBlocks += fallbackBlocks;
            accounting.fallbackQuads += fallbackQuads;
        }

        static AccountingSnapshot snapshot() {
            MutableAccounting accounting = ACCOUNTING.get();
            return new AccountingSnapshot(accounting.nativeCalls, accounting.abiPayloadBytes,
                    accounting.bytesCopied, accounting.outputVertexBytes, accounting.outputIndexBytes,
                    accounting.outputQuads, accounting.fallbackBlocks, accounting.fallbackQuads);
        }
    }

    private static final class MutableAccounting {
        private long nativeCalls;
        private long abiPayloadBytes;
        private long bytesCopied;
        private long outputVertexBytes;
        private long outputIndexBytes;
        private long outputQuads;
        private long fallbackBlocks;
        private long fallbackQuads;

        private void reset() {
            this.nativeCalls = 0L;
            this.abiPayloadBytes = 0L;
            this.bytesCopied = 0L;
            this.outputVertexBytes = 0L;
            this.outputIndexBytes = 0L;
            this.outputQuads = 0L;
            this.fallbackBlocks = 0L;
            this.fallbackQuads = 0L;
        }
    }
}
