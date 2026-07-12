package net.sodium.client.perf;

import net.sodium.client.SodiumClientMod;
import net.sodium.client.gui.SodiumGameOptions;
import net.minecraft.core.SectionPos;
import net.sodium.client.render.chunk.data.BuiltSectionMeshParts;
import net.sodium.client.render.chunk.translucent_sorting.SortBehavior;
import net.sodium.client.render.chunk.translucent_sorting.TranslucentGeometryCollector;
import net.sodium.client.render.chunk.translucent_sorting.SortType;
import net.sodium.client.render.chunk.translucent_sorting.data.CombinedCameraPos;
import net.sodium.client.render.chunk.translucent_sorting.data.TranslucentData;
import net.sodium.client.model.quad.properties.ModelQuadFacing;
import net.sodium.client.render.chunk.vertex.format.ChunkMeshFormats;
import net.sodium.client.render.chunk.vertex.format.NativeChunkMeshEncoder;
import net.sodium.client.render.chunk.vertex.format.NativeSectionMeshBuilder;
import net.sodium.client.render.chunk.vertex.format.NativeStaticBlockModelCache;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.lwjgl.system.MemoryUtil;

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
    private static final MeshFinisher MESH_FINISHER = createMeshFinisher();
    private static final int SECTION_BLOCKS = 16 * 16 * 16;
    private static final int INDEX_BYTES_PER_QUAD = TranslucentData.INDICES_PER_QUAD * Integer.BYTES;
    private static final int NATIVE_FLAG_AIR = 1;
    private static final int NATIVE_FLAG_MODEL = 1 << 1;
    private static final int NATIVE_FLAG_FLUID = 1 << 2;
    private static final int NATIVE_FLAG_SOLID_RENDER = 1 << 3;
    private static final int NATIVE_FLAG_FULL_OCCLUSION = 1 << 4;
    private static final int NATIVE_FLAG_CAN_OCCLUDE = 1 << 7;
    private static final int NATIVE_FLAG_BLOCKS_MOTION = 1 << 8;
    private static final SectionPos BENCHMARK_SECTION_POS = SectionPos.of(0, SECTION_INDEX, 0);
    private static final CombinedCameraPos ZERO_CAMERA_POS = new CombinedCameraPos() {
        private final Vector3f relative = new Vector3f();
        private final Vector3d absolute = new Vector3d();

        @Override
        public Vector3fc getRelativeCameraPos() {
            return this.relative;
        }

        @Override
        public Vector3dc getAbsoluteCameraPos() {
            return this.absolute;
        }
    };

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

        NativeSectionMeshBuilder sectionBuilder = NativeSectionMeshBuilder.create(4096);
        ByteBuffer sectionRecords = createNativeSectionBenchmarkRecords();
        try {
            installNativeSectionBenchmarkMetadata();
            results.add(measure("native_section_scan_and_build",
                    () -> runNativeSectionScan(sectionBuilder, sectionRecords)));
        } finally {
            NativeStaticBlockModelCache.clear();
            MemoryUtil.memFree(sectionRecords);
            sectionBuilder.close();
        }

        installNativeSectionBenchmarkMetadata();
        try {
            for (ReplaySection section : replayCorpus()) {
                NativeSectionMeshBuilder replayBuilder = NativeSectionMeshBuilder.create(4096);
                ByteBuffer replayRecords = createReplaySectionRecords(section);
                try {
                    results.add(measure("snapshot_create_" + section.name,
                            () -> runSnapshotCreate(section)));
                    results.add(measure("full_section_replay_" + section.name,
                            () -> runReplaySectionEndToEnd(section, replayBuilder)));
                    results.add(measure("replay_section_" + section.name,
                            () -> runReplaySection(section, replayBuilder, replayRecords)));
                } finally {
                    MemoryUtil.memFree(replayRecords);
                    replayBuilder.close();
                }
            }

            ReplaySection fluidHeavySection = replaySection("fluid_heavy");
            for (FluidDiagnosticMode mode : FluidDiagnosticMode.values()) {
                NativeSectionMeshBuilder diagnosticBuilder = NativeSectionMeshBuilder.create(4096);
                ByteBuffer diagnosticRecords = createReplaySectionRecords(fluidHeavySection);
                try {
                    results.add(measure("diagnostic_fluid_heavy_" + mode.resultName,
                            () -> runFluidHeavyDiagnostic(fluidHeavySection, diagnosticBuilder, diagnosticRecords,
                                    mode)));
                } finally {
                    MemoryUtil.memFree(diagnosticRecords);
                    diagnosticBuilder.close();
                }
            }

            results.add(measure("empty_native_call_overhead",
                    () -> runEmptyNativeCall(NativeSectionMeshBuilder.create(1))));
            results.add(measure("transfer_copy_4k", () -> runTransferCopy(4 * 1024)));
            results.add(measure("transfer_copy_64k", () -> runTransferCopy(64 * 1024)));
            results.add(measure("transfer_copy_1m", () -> runTransferCopy(1024 * 1024)));
        } finally {
            NativeStaticBlockModelCache.clear();
        }

        writeResults(results);
        for (BenchmarkResult result : results) {
            System.out.printf(Locale.ROOT,
                    "%s: mean %.3f ms, median %.3f ms, min %.3f ms, %.1f Mquads/s, checksum %d%n",
                    result.name, result.meanMillis(), result.medianMillis(), result.minMillis(),
                    result.megaQuadsPerSecond(), result.checksum);
        }
    }

    private static ReplaySection replaySection(String name) {
        for (ReplaySection section : replayCorpus()) {
            if (section.name.equals(name)) {
                return section;
            }
        }
        throw new IllegalArgumentException("Unknown replay section " + name);
    }

    private static long runIndexEmission(int[] quadIndexes, IntBuffer indexBuffer) {
        indexBuffer.clear();
        TranslucentData.writeQuadVertexIndexes(indexBuffer, quadIndexes);
        BenchmarkAccounting.record(0, 0, 0,
                (long) QUAD_COUNT * INDEX_BYTES_PER_QUAD, QUAD_COUNT, 0, 0);
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
            appendQuad(builder, blockEmission, renderType, blockId, localX, localY, localZ, materialBits,
                    basePositions[baseIndex], basePositions[baseIndex + 1], basePositions[baseIndex + 2]);
        }

        BenchmarkAccounting.record((long) QUAD_COUNT + 1L,
                (long) QUAD_COUNT * NativeChunkMeshEncoder.NATIVE_QUAD_STRIDE,
                (long) builder.count() * ChunkMeshFormats.COMPACT.getNativeFormat().stride(),
                (long) QUAD_COUNT * INDEX_BYTES_PER_QUAD, QUAD_COUNT, 0, 0);
        return ((long) builder.count() << 32) ^ MESH_FINISHER.finish(builder);
    }

    private static long runNativeSectionScan(NativeSectionMeshBuilder sectionBuilder, ByteBuffer records) {
        sectionBuilder.start(SECTION_INDEX);
        int committed = sectionBuilder.appendNativeSectionEncoded(MemoryUtil.memAddress(records), 4096, 0,
                ChunkMeshFormats.COMPACT.getNativeFormat(), SECTION_INDEX, false, false);
        BuiltSectionMeshParts mesh = sectionBuilder.finishMesh(ChunkMeshFormats.COMPACT.getNativeFormat(), 0,
                false, true, false);
        try {
            ByteBuffer vertexBuffer = mesh == null ? null
                    : mesh.getVertexData().getDirectBuffer().order(ByteOrder.nativeOrder());
            BenchmarkAccounting.recordNativeProfile(sectionBuilder.copyProfile());
            BenchmarkAccounting.record(2, records.remaining(),
                    vertexBuffer == null ? 0 : vertexBuffer.remaining(),
                    (long) committed * INDEX_BYTES_PER_QUAD, committed, 0, 0);
            return (((long) committed) << 32) ^ (vertexBuffer == null ? 0L : sample(vertexBuffer));
        } finally {
            if (mesh != null) {
                mesh.getVertexData().free();
            }
        }
    }

    private static long runReplaySection(ReplaySection section, NativeSectionMeshBuilder sectionBuilder,
            ByteBuffer records) {
        StageTimer stages = StageTimer.start();
        sectionBuilder.start(SECTION_INDEX);
        stages.mark("java_snapshot_ready");
        int recordCount = nativeSectionRecordCount(records);
        int committed = 0;
        if (recordCount != 0) {
            committed += sectionBuilder.appendNativeSectionEncoded(MemoryUtil.memAddress(records), recordCount, -1,
                    ChunkMeshFormats.COMPACT.getNativeFormat(), SECTION_INDEX, false, false);
        }
        stages.mark("native_all_passes");
        if (section.fallbackLikeBlocks > 0) {
            appendReplayFallbackQuads(sectionBuilder, section.fallbackLikeBlocks);
            committed += section.fallbackLikeBlocks;
            stages.mark("java_fallback_geometry");
        }
        BuiltSectionMeshParts mesh = sectionBuilder.finishMesh(ChunkMeshFormats.COMPACT.getNativeFormat(), 0,
                false, true, false);
        stages.mark("final_assembly_and_handoff");
        try {
            ByteBuffer vertexBuffer = mesh == null ? null
                    : mesh.getVertexData().getDirectBuffer().order(ByteOrder.nativeOrder());
            BenchmarkAccounting.recordNativeProfile(sectionBuilder.copyProfile());
            BenchmarkAccounting.record((recordCount == 0 ? 0 : 2) + (section.fallbackLikeBlocks > 0 ? 1 : 0),
                    records.remaining()
                            + (long) section.fallbackLikeBlocks * NativeChunkMeshEncoder.FLAT_QUAD_RECORD_STRIDE,
                    vertexBuffer == null ? 0 : vertexBuffer.remaining(),
                    (long) committed * INDEX_BYTES_PER_QUAD, committed,
                    section.fallbackLikeBlocks, section.fallbackLikeBlocks);
            long meshChecksum = vertexBuffer == null ? 0L : sample(vertexBuffer);
            long summary = section.expectedSummary();
            return (((long) committed) << 32) ^ meshChecksum ^ summary ^ stages.checksum();
        } finally {
            if (mesh != null) {
                mesh.getVertexData().free();
            }
        }
    }

    private static long runReplaySectionEndToEnd(ReplaySection section, NativeSectionMeshBuilder sectionBuilder) {
        StageTimer stages = StageTimer.start();
        int recordCount = section.nativeRecordCount();
        int bytes = Math.max(1, recordCount * NativeChunkMeshEncoder.NATIVE_SECTION_BLOCK_RECORD_STRIDE);
        ByteBuffer records = MemoryUtil.memAlloc(bytes).order(ByteOrder.nativeOrder());
        records.limit(recordCount * NativeChunkMeshEncoder.NATIVE_SECTION_BLOCK_RECORD_STRIDE);
        stages.mark("java_snapshot_allocation");
        try {
            fillReplaySectionRecords(section, records);
            stages.mark("java_snapshot_population");
            sectionBuilder.start(SECTION_INDEX);
            int committed = 0;
            if (recordCount != 0) {
                committed += sectionBuilder.appendNativeSectionEncoded(MemoryUtil.memAddress(records), recordCount, -1,
                        ChunkMeshFormats.COMPACT.getNativeFormat(), SECTION_INDEX, false, false);
            }
            stages.mark("native_all_passes");
            if (section.fallbackLikeBlocks > 0) {
                appendReplayFallbackQuads(sectionBuilder, section.fallbackLikeBlocks);
                committed += section.fallbackLikeBlocks;
                stages.mark("java_fallback_geometry");
            }
            BuiltSectionMeshParts mesh = sectionBuilder.finishMesh(ChunkMeshFormats.COMPACT.getNativeFormat(), 0,
                    false, true, false);
            stages.mark("final_assembly_and_handoff");
            try {
                ByteBuffer vertexBuffer = mesh == null ? null
                        : mesh.getVertexData().getDirectBuffer().order(ByteOrder.nativeOrder());
                BenchmarkAccounting.recordNativeProfile(sectionBuilder.copyProfile());
                BenchmarkAccounting.record((recordCount == 0 ? 0 : 2) + (section.fallbackLikeBlocks > 0 ? 1 : 0),
                        records.remaining()
                                + (long) section.fallbackLikeBlocks * NativeChunkMeshEncoder.FLAT_QUAD_RECORD_STRIDE,
                        vertexBuffer == null ? 0 : vertexBuffer.remaining(),
                        (long) committed * INDEX_BYTES_PER_QUAD, committed,
                        section.fallbackLikeBlocks, section.fallbackLikeBlocks);
                long meshChecksum = vertexBuffer == null ? 0L : sample(vertexBuffer);
                return (((long) committed) << 32) ^ meshChecksum ^ section.expectedSummary() ^ stages.checksum();
            } finally {
                if (mesh != null) {
                    mesh.getVertexData().free();
                }
            }
        } finally {
            MemoryUtil.memFree(records);
        }
    }

    private static long runFluidHeavyDiagnostic(ReplaySection section, NativeSectionMeshBuilder sectionBuilder,
            ByteBuffer records, FluidDiagnosticMode mode) {
        StageTimer stages = StageTimer.start();
        sectionBuilder.start(SECTION_INDEX);
        stages.mark("java_snapshot_ready");

        TranslucentGeometryCollector collector = null;
        TranslucentData translucentData = null;
        BuiltSectionMeshParts mesh = null;
        int committed = 0;
        int sortTypeOrdinal = -1;
        try {
            int recordCount = nativeSectionRecordCount(records);
            long analyzerHandle = 0L;
            if (mode.usesAnalyzer) {
                collector = new TranslucentGeometryCollector(BENCHMARK_SECTION_POS, SortBehavior.STATIC);
                analyzerHandle = collector.nativeAnalyzerHandle();
            }

            if (recordCount != 0) {
                committed = sectionBuilder.appendNativeSectionEncoded(MemoryUtil.memAddress(records), recordCount, 2,
                        ChunkMeshFormats.COMPACT.getNativeFormat(), SECTION_INDEX, false, false, analyzerHandle);
            }
            stages.mark("native_fluid_generation");

            if (mode.finishCollector) {
                SortType sortType = collector.finishRendering();
                sortTypeOrdinal = sortType.ordinal();
                stages.mark("translucent_analysis_and_sort_choice");
            }

            if (mode.createTranslucentData) {
                translucentData = collector.getTranslucentData(null, ZERO_CAMERA_POS);
                stages.mark("translucent_sort_data");
            }

            if (mode.finishMesh) {
                mesh = sectionBuilder.finishMesh(ChunkMeshFormats.COMPACT.getNativeFormat(), 0,
                        false, true, false);
                stages.mark("final_assembly_and_handoff");
            }

            ByteBuffer vertexBuffer = mesh == null ? null
                    : mesh.getVertexData().getDirectBuffer().order(ByteOrder.nativeOrder());
            BenchmarkAccounting.recordNativeProfile(sectionBuilder.copyProfile());
            BenchmarkAccounting.record(1, records.remaining(),
                    vertexBuffer == null ? 0 : vertexBuffer.remaining(),
                    (long) committed * INDEX_BYTES_PER_QUAD, committed,
                    section.fallbackLikeBlocks, section.fallbackLikeBlocks);
            long meshChecksum = vertexBuffer == null ? 0L : sample(vertexBuffer);
            return (((long) committed) << 32) ^ meshChecksum ^ stages.checksum() ^ sortTypeOrdinal;
        } finally {
            if (mesh != null) {
                mesh.getVertexData().free();
            }
            if (translucentData != null) {
                translucentData.close();
            } else if (collector != null) {
                collector.discardNativeAnalyzerForBenchmark();
            }
        }
    }

    private static void appendReplayFallbackQuads(NativeSectionMeshBuilder sectionBuilder, int quadCount) {
        NativeSectionMeshBuilder.FacingBuffer fallbackBuffer = new NativeSectionMeshBuilder.FacingBuffer(
                ChunkMeshFormats.COMPACT.getNativeFormat(), sectionBuilder, ModelQuadFacing.UNASSIGNED.ordinal(),
                false);
        fallbackBuffer.start(SECTION_INDEX);
        for (int index = 0; index < quadCount; index++) {
            int localX = index & 15;
            int localY = (index >>> 8) & 15;
            int localZ = (index >>> 4) & 15;
            appendQuad(fallbackBuffer, (byte) 0, (byte) 0, 61 + (index & 7), localX, localY, localZ,
                    materialBits(index), localX, localY, localZ);
        }
        fallbackBuffer.count();
    }

    private static long runEmptyNativeCall(NativeSectionMeshBuilder builder) {
        try {
            builder.start(SECTION_INDEX);
            int committed = builder.appendNativeSectionEncoded(0L, 0, 0, ChunkMeshFormats.COMPACT.getNativeFormat(),
                    SECTION_INDEX, false, false);
            BenchmarkAccounting.recordNativeProfile(builder.copyProfile());
            BenchmarkAccounting.record(1, 0, 0, 0, committed, 0, 0);
            return committed;
        } finally {
            builder.close();
        }
    }

    private static long runSnapshotCreate(ReplaySection section) {
        StageTimer stages = StageTimer.start();
        int recordCount = section.nativeRecordCount();
        int bytes = Math.max(1, recordCount * NativeChunkMeshEncoder.NATIVE_SECTION_BLOCK_RECORD_STRIDE);
        ByteBuffer records = MemoryUtil.memAlloc(bytes).order(ByteOrder.nativeOrder());
        records.limit(recordCount * NativeChunkMeshEncoder.NATIVE_SECTION_BLOCK_RECORD_STRIDE);
        stages.mark("java_snapshot_allocation");
        try {
            fillReplaySectionRecords(section, records);
            stages.mark("java_snapshot_population");
            BenchmarkAccounting.record(0, records.remaining(), 0, 0,
                    section.expectedNativeQuads(), section.fallbackLikeBlocks, section.fallbackLikeBlocks);
            return ((long) recordCount << 32) ^ sample(records);
        } finally {
            MemoryUtil.memFree(records);
        }
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

    private static void installNativeSectionBenchmarkMetadata() {
        NativeStaticBlockModelCache.clear();
        NativeStaticBlockModelCache.register(77, (recordAddress, index) ->
                NativeChunkMeshEncoder.writeStaticModelQuadRecord(recordAddress, 5,
                        net.minecraft.core.Direction.NORTH.get3DDataValue(), net.sodium.client.model.quad.properties.ModelQuadFacing.NEG_Z.ordinal(),
                        net.sodium.client.model.quad.properties.ModelQuadFacing.NEG_Z.getPackedAlignedNormal(), (byte) 0, (byte) 0, true,
                        0.0F, 0.0F, 0.0F, 0xffffffff, 0.0F, 0.0F, -1,
                        1.0F, 0.0F, 0.0F, 0xffffffff, 1.0F, 0.0F, -1,
                        1.0F, 1.0F, 0.0F, 0xffffffff, 1.0F, 1.0F, -1,
                        0.0F, 1.0F, 0.0F, 0xffffffff, 0.0F, 1.0F, -1), 1);
        NativeStaticBlockModelCache.registerSelector(8, 0,
                (recordAddress, index) -> NativeChunkMeshEncoder.writeNativeModelSelectorEntry(recordAddress, 77, 1),
                1);
        NativeStaticBlockModelCache.register(78, (recordAddress, index) ->
                NativeChunkMeshEncoder.writeStaticModelQuadRecord(recordAddress, 7,
                        net.minecraft.core.Direction.UP.get3DDataValue(), net.sodium.client.model.quad.properties.ModelQuadFacing.POS_Y.ordinal(),
                        net.sodium.client.model.quad.properties.ModelQuadFacing.POS_Y.getPackedAlignedNormal(), (byte) 0, (byte) 0, true,
                        0.0F, 1.0F, 0.0F, 0xff78b85a, 0.0F, 0.0F, -1,
                        1.0F, 1.0F, 0.0F, 0xff78b85a, 1.0F, 0.0F, -1,
                        1.0F, 1.0F, 1.0F, 0xff78b85a, 1.0F, 1.0F, -1,
                        0.0F, 1.0F, 1.0F, 0xff78b85a, 0.0F, 1.0F, -1), 1);
        NativeStaticBlockModelCache.registerSelector(10, 0,
                (recordAddress, index) -> NativeChunkMeshEncoder.writeNativeModelSelectorEntry(recordAddress, 78, 1),
                1);
        NativeStaticBlockModelCache.registerSelector(11, 1, (recordAddress, index) ->
                NativeChunkMeshEncoder.writeNativeModelSelectorEntry(recordAddress, index == 0 ? 8 : 10,
                        index == 0 ? 3 : 1), 2);
        NativeStaticBlockModelCache.registerSelector(9, 1,
                (recordAddress, index) -> NativeChunkMeshEncoder.writeNativeModelSelectorEntry(recordAddress, 8, 1),
                1);
        NativeStaticBlockModelCache.registerState(0, -1, NATIVE_FLAG_AIR, 0, -1, 0, 0, -1, 0, -1, -1, 0);
        NativeStaticBlockModelCache.registerState(100, 9,
                NATIVE_FLAG_MODEL | NATIVE_FLAG_SOLID_RENDER | NATIVE_FLAG_FULL_OCCLUSION
                        | NATIVE_FLAG_CAN_OCCLUDE | NATIVE_FLAG_BLOCKS_MOTION,
                5, 0, 0, 0, 41, 0, -1, -1, 1);
        NativeStaticBlockModelCache.registerState(101, 10, NATIVE_FLAG_MODEL, 7, 1, 0, 0, 42, 0, -1, -1, 2);
        NativeStaticBlockModelCache.registerState(102, 11, NATIVE_FLAG_MODEL, 5, 0, 0, 0, 43, 0, -1, -1, 3);
        NativeStaticBlockModelCache.registerState(200, -1, NATIVE_FLAG_FLUID, 0, -1, 0, 0, -1, 9, 2, 44, 4,
                1, 0.8888889F, 0, 0, 0.0F, 0.0F, 3,
                0.0F, 1.0F, 0.0F, 1.0F, 0.0009765625F,
                0.0F, 1.0F, 0.0F, 1.0F, 0.0009765625F,
                0.0F, 1.0F, 0.0F, 1.0F, 0.0009765625F, 1);
    }

    private static ByteBuffer createNativeSectionBenchmarkRecords() {
        ByteBuffer records = MemoryUtil.memAlloc(4096 * NativeChunkMeshEncoder.NATIVE_SECTION_BLOCK_RECORD_STRIDE)
                .order(ByteOrder.nativeOrder());
        long base = MemoryUtil.memAddress(records);
        for (int index = 0; index < 4096; index++) {
            int localX = index & 15;
            int localY = (index >>> 8) & 15;
            int localZ = (index >>> 4) & 15;
            NativeChunkMeshEncoder.writeNativeSectionBlockRecord(base + (long) index
                            * NativeChunkMeshEncoder.NATIVE_SECTION_BLOCK_RECORD_STRIDE,
                    100, 41, localX, localY, localZ, index * 31L,
                    0, 0, 0, 0, 0, 0, 0x00f000f0, 0.0F, 0.0F, 0.0F);
        }
        return records;
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

    private static ByteBuffer createReplaySectionRecords(ReplaySection section) {
        int recordCount = section.nativeRecordCount();
        int bytes = Math.max(1, recordCount * NativeChunkMeshEncoder.NATIVE_SECTION_BLOCK_RECORD_STRIDE);
        ByteBuffer records = MemoryUtil.memAlloc(bytes).order(ByteOrder.nativeOrder());
        records.limit(recordCount * NativeChunkMeshEncoder.NATIVE_SECTION_BLOCK_RECORD_STRIDE);
        fillReplaySectionRecords(section, records);
        return records;
    }

    private static void fillReplaySectionRecords(ReplaySection section, ByteBuffer records) {
        long base = MemoryUtil.memAddress(records);
        int[] lightWords = new int[27];
        Arrays.fill(lightWords, 0xf0);
        int[] airNeighborhood = new int[27];
        int[] solidNeighborhood = new int[27];
        Arrays.fill(solidNeighborhood, 100);
        int[] fluidSurfaceNeighborhood = solidNeighborhood.clone();
        fluidSurfaceNeighborhood[22] = 200;

        int recordIndex = 0;
        for (int index = 0; index < SECTION_BLOCKS; index++) {
            int stateId = stateForReplayIndex(section, index);
            if (stateId == 0) {
                continue;
            }
            int localX = index & 15;
            int localY = (index >>> 8) & 15;
            int localZ = (index >>> 4) & 15;
            int[] neighborhood = stateId == 0 ? airNeighborhood
                    : stateId == 200 ? fluidSurfaceNeighborhood : solidNeighborhood;
            neighborhood[13] = stateId;
            NativeChunkMeshEncoder.writeNativeSectionBlockRecord(base + (long) recordIndex
                            * NativeChunkMeshEncoder.NATIVE_SECTION_BLOCK_RECORD_STRIDE,
                    stateId, stateId == 0 ? -1 : 41 + (stateId % 11), localX, localY, localZ,
                    0x9e3779b97f4a7c15L ^ (long) index * 0xbf58476d1ce4e5b9L,
                    0, 0, 0, 0, 0, 0, lightWords, neighborhood, 0xff70aa50, 0xcc3f76e4,
                    (index & 1) == 0 ? 0.0F : 0.35F, (index & 2) == 0 ? 0.0F : -0.25F,
                    localX, localY, localZ);
            neighborhood[13] = 0;
            recordIndex++;
        }
    }

    private static int nativeSectionRecordCount(ByteBuffer records) {
        return records.remaining() / NativeChunkMeshEncoder.NATIVE_SECTION_BLOCK_RECORD_STRIDE;
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

    private static void appendQuad(NativeSectionMeshBuilder.FacingBuffer builder, byte blockEmission,
            byte renderType, int blockId,
            int localX, int localY, int localZ, int materialBits, float baseX, float baseY, float baseZ) {
        builder.appendFlatQuad(materialBits, blockEmission, renderType, false, blockId, localX, localY, localZ,
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
        builder.append("  \"warmup_millis\": ").append(WARMUP_MILLIS).append(",\n");
        builder.append("  \"measure_millis\": ").append(MEASURE_MILLIS).append(",\n");
        builder.append("  \"fork_index\": ").append(FORK_INDEX).append(",\n");
        builder.append("  \"benchmark_limitations\": [\n");
        builder.append("    \"Historical hot-path rows use synthetic repeated quads and are retained only for trend comparison.\",\n");
        builder.append("    \"full_section_replay rows include Java snapshot allocation/population, native section calls, Rust section scan/producers, packing, fallback, and final mesh assembly.\",\n");
        builder.append("    \"replay_section rows reuse a prebuilt native snapshot and are retained as boundary-free native replay diagnostics.\",\n");
        builder.append("    \"Replay section rows are deterministic section-shaped workloads, not live gameplay captures.\",\n");
        builder.append("    \"Current Rust internal substages are exported once per benchmark operation; section_scanning is inclusive of native producers.\"\n");
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
            appendNativeProfileJson(builder, result.accounting.nativeProfile, result.sampleCount);
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

    private static void appendNativeProfileJson(StringBuilder builder, long[] nativeProfile, int samples) {
        builder.append("      \"native_profile\": {\n");
        builder.append("        \"stage_nanos_total\": {\n");
        for (int index = 0; index < NativeSectionMeshBuilder.Profile.STAGE_COUNT; index++) {
            builder.append("          \"").append(NativeSectionMeshBuilder.Profile.STAGE_NAMES[index]).append("\": ")
                    .append(nativeProfile[index]);
            if (index + 1 < NativeSectionMeshBuilder.Profile.STAGE_COUNT) {
                builder.append(',');
            }
            builder.append('\n');
        }
        builder.append("        },\n");
        builder.append("        \"stage_nanos_per_invocation\": {\n");
        for (int index = 0; index < NativeSectionMeshBuilder.Profile.STAGE_COUNT; index++) {
            builder.append("          \"").append(NativeSectionMeshBuilder.Profile.STAGE_NAMES[index]).append("\": ")
                    .append(String.format(Locale.ROOT, "%.3f", nativeProfile[index] / (double) Math.max(1, samples)));
            if (index + 1 < NativeSectionMeshBuilder.Profile.STAGE_COUNT) {
                builder.append(',');
            }
            builder.append('\n');
        }
        builder.append("        },\n");
        builder.append("        \"counts_total\": {\n");
        for (int index = 0; index < NativeSectionMeshBuilder.Profile.COUNT_COUNT; index++) {
            int profileIndex = NativeSectionMeshBuilder.Profile.STAGE_COUNT + index;
            builder.append("          \"").append(NativeSectionMeshBuilder.Profile.COUNT_NAMES[index]).append("\": ")
                    .append(nativeProfile[profileIndex]);
            if (index + 1 < NativeSectionMeshBuilder.Profile.COUNT_COUNT) {
                builder.append(',');
            }
            builder.append('\n');
        }
        builder.append("        },\n");
        builder.append("        \"counts_per_invocation\": {\n");
        for (int index = 0; index < NativeSectionMeshBuilder.Profile.COUNT_COUNT; index++) {
            int profileIndex = NativeSectionMeshBuilder.Profile.STAGE_COUNT + index;
            builder.append("          \"").append(NativeSectionMeshBuilder.Profile.COUNT_NAMES[index]).append("\": ")
                    .append(String.format(Locale.ROOT, "%.3f", nativeProfile[profileIndex] / (double) Math.max(1, samples)));
            if (index + 1 < NativeSectionMeshBuilder.Profile.COUNT_COUNT) {
                builder.append(',');
            }
            builder.append('\n');
        }
        builder.append("        }\n");
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
            long fallbackQuads, long[] nativeProfile) {
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
        int nativeRecordCount() {
            return this.solidBlocks + this.foliageBlocks + this.weightedMultipartBlocks + this.fluidBlocks
                    + this.translucentBlocks;
        }

        int expectedNativeQuads() {
            return this.solidBlocks + this.foliageBlocks + this.weightedMultipartBlocks + this.fluidBlocks
                    + this.translucentBlocks;
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

    private enum FluidDiagnosticMode {
        NO_ANALYZER_NO_ASSEMBLY("no_analyzer_no_assembly", false, false, false, false),
        ANALYZER_INGEST_NO_SORT_NO_ASSEMBLY("analyzer_ingest_no_sort_no_assembly", true, false, false, false),
        ANALYZER_SORT_NO_ASSEMBLY("analyzer_sort_no_assembly", true, true, false, false),
        COMPLETE_NATIVE_FLUID_PATH("complete_native_path", true, true, true, true);

        private final String resultName;
        private final boolean usesAnalyzer;
        private final boolean finishCollector;
        private final boolean createTranslucentData;
        private final boolean finishMesh;

        FluidDiagnosticMode(String resultName, boolean usesAnalyzer, boolean finishCollector,
                boolean createTranslucentData, boolean finishMesh) {
            this.resultName = resultName;
            this.usesAnalyzer = usesAnalyzer;
            this.finishCollector = finishCollector;
            this.createTranslucentData = createTranslucentData;
            this.finishMesh = finishMesh;
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

        static void recordNativeProfile(long[] values) {
            MutableAccounting accounting = ACCOUNTING.get();
            int length = Math.min(values.length, accounting.nativeProfile.length);
            for (int index = 0; index < length; index++) {
                accounting.nativeProfile[index] += values[index];
            }
        }

        static AccountingSnapshot snapshot() {
            MutableAccounting accounting = ACCOUNTING.get();
            return new AccountingSnapshot(accounting.nativeCalls, accounting.abiPayloadBytes,
                    accounting.bytesCopied, accounting.outputVertexBytes, accounting.outputIndexBytes,
                    accounting.outputQuads, accounting.fallbackBlocks, accounting.fallbackQuads,
                    accounting.nativeProfile.clone());
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
        private final long[] nativeProfile = new long[NativeSectionMeshBuilder.Profile.METRIC_COUNT];

        private void reset() {
            this.nativeCalls = 0L;
            this.abiPayloadBytes = 0L;
            this.bytesCopied = 0L;
            this.outputVertexBytes = 0L;
            this.outputIndexBytes = 0L;
            this.outputQuads = 0L;
            this.fallbackBlocks = 0L;
            this.fallbackQuads = 0L;
            Arrays.fill(this.nativeProfile, 0L);
        }
    }
}
