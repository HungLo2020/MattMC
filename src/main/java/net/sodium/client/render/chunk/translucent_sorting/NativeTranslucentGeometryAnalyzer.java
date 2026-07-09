package net.sodium.client.render.chunk.translucent_sorting;

import net.minecraft.util.NativeLibraryLoader;
import net.sodium.client.model.quad.properties.ModelQuadFacing;
import net.sodium.client.render.chunk.translucent_sorting.quad.RegularTQuad;
import net.sodium.client.render.chunk.translucent_sorting.quad.TQuad;
import net.sodium.client.render.chunk.vertex.format.ChunkVertexEncoder;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

final class NativeTranslucentGeometryAnalyzer {
    private static final int RECORD_STRIDE = 56;
    private static final int POSITION_FLOATS = 12;
    private static final int OFFSET_POSITIONS = 0;
    private static final int OFFSET_FACING = OFFSET_POSITIONS + POSITION_FLOATS * Float.BYTES;
    private static final int OFFSET_PACKED_NORMAL = OFFSET_FACING + Integer.BYTES;
    private static final int METRIC_COUNT = 5;
    private static final int OK = 0;
    private static final int SORT_FAILED = 1;

    private static final MethodHandle VERIFY = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_translucent_analyzer_verify",
            FunctionDescriptor.of(ValueLayout.JAVA_INT));
    private static final MethodHandle CREATE = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_translucent_analyzer_create",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS));
    private static final MethodHandle DESTROY = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_translucent_analyzer_destroy",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG));
    private static final MethodHandle APPEND_RECORD = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_translucent_analyzer_append_record",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.ADDRESS));
    private static final MethodHandle APPEND_NATIVE_QUAD = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_translucent_analyzer_append_native_quad",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT));
    private static final MethodHandle RECORD_COUNT = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_translucent_analyzer_record_count",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.ADDRESS));
    private static final MethodHandle WRITE_RECORDS_BY_FACING = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_translucent_analyzer_write_records_by_facing",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT));
    private static final MethodHandle ANALYZE = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_translucent_analyzer_analyze_handle",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT));
    private static final MethodHandle STATIC_TOPO_SORT = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_translucent_static_topo_sort_handle",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT));
    private static final int VERIFY_STATUS = invokeVerify();

    private long handle;

    NativeTranslucentGeometryAnalyzer() {
        check(VERIFY_STATUS, "native translucent analyzer verification");
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment handleSegment = arena.allocate(ValueLayout.JAVA_LONG);
            check(invokeCreate(handleSegment), "native translucent analyzer creation");
            this.handle = handleSegment.get(ValueLayout.JAVA_LONG, 0);
            if (this.handle == 0) {
                throw new IllegalStateException("Native translucent analyzer creation returned a null handle");
            }
        }
    }

    boolean appendQuad(ChunkVertexEncoder.Vertex[] vertices, ModelQuadFacing facing, int packedNormal) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment recordSegment = arena.allocate(RECORD_STRIDE, Integer.BYTES);
            writeRecord(recordSegment, vertices, facing.ordinal(), packedNormal);
            int status = invokeAppendRecord(this.getHandle(), recordSegment);
            if (status == SORT_FAILED) {
                return true;
            }
            check(status, "native translucent analyzer record append");
            return false;
        }
    }

    boolean appendNativeQuad(long nativeQuadAddress, ModelQuadFacing facing, int packedNormal) {
        int status = invokeAppendNativeQuad(this.getHandle(), nativeQuadAddress, facing.ordinal(), packedNormal);
        if (status == SORT_FAILED) {
            return true;
        }
        check(status, "native translucent analyzer native quad append");
        return false;
    }

    Analysis analyze(SortBehavior.SortMode sortMode) {
        int[] metrics = new int[METRIC_COUNT];
        int[] meshFacingCounts = new int[ModelQuadFacing.COUNT];
        int recordCount = this.getRecordCount();
        int[] staticKeys = new int[recordCount];

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment metricsSegment = arena.allocate(ValueLayout.JAVA_INT, metrics.length);
            MemorySegment meshFacingCountsSegment = arena.allocate(ValueLayout.JAVA_INT, meshFacingCounts.length);
            MemorySegment staticKeysSegment = arena.allocate(ValueLayout.JAVA_INT, staticKeys.length);

            check(invokeAnalyze(
                    this.getHandle(),
                    sortMode.ordinal(),
                    metricsSegment,
                    metrics.length,
                    meshFacingCountsSegment,
                    meshFacingCounts.length,
                    staticKeysSegment,
                    staticKeys.length
            ), "native translucent geometry analysis");

            for (int index = 0; index < metrics.length; index++) {
                metrics[index] = metricsSegment.getAtIndex(ValueLayout.JAVA_INT, index);
            }
            for (int index = 0; index < meshFacingCounts.length; index++) {
                meshFacingCounts[index] = meshFacingCountsSegment.getAtIndex(ValueLayout.JAVA_INT, index);
            }
            for (int index = 0; index < metrics[4]; index++) {
                staticKeys[index] = staticKeysSegment.getAtIndex(ValueLayout.JAVA_INT, index);
            }
        }

        return new Analysis(
                SortType.values()[metrics[0]],
                metrics[1],
                metrics[2],
                metrics[3] != 0,
                meshFacingCounts,
                java.util.Arrays.copyOf(staticKeys, metrics[4]),
                recordCount
        );
    }

    int[] staticTopoSort(boolean failOnIntersection) {
        int[] quadIndexes = new int[this.getRecordCount()];

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment quadIndexesSegment = arena.allocate(ValueLayout.JAVA_INT, quadIndexes.length);
            int status = invokeStaticTopoSort(this.getHandle(), failOnIntersection ? 1 : 0,
                    quadIndexesSegment, quadIndexes.length);

            if (status == SORT_FAILED) {
                return null;
            }
            check(status, "native static translucent topo sort");

            for (int index = 0; index < quadIndexes.length; index++) {
                quadIndexes[index] = quadIndexesSegment.getAtIndex(ValueLayout.JAVA_INT, index);
            }
        }

        return quadIndexes;
    }

    NativeTranslucentSectionGeometry createSectionGeometry() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment handleSegment = arena.allocate(ValueLayout.JAVA_LONG);
            return NativeTranslucentSectionGeometry.createFromAnalyzer(this.getHandle(), this.getRecordCount(),
                    handleSegment);
        }
    }

    TQuad[] buildRegularQuadsByFacing() {
        int recordCount = this.getRecordCount();
        TQuad[] quads = new TQuad[recordCount];

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment recordsSegment = recordCount == 0
                    ? MemorySegment.NULL
                    : arena.allocate((long) recordCount * RECORD_STRIDE, Integer.BYTES);
            check(invokeWriteRecordsByFacing(this.getHandle(), recordsSegment, recordCount),
                    "native translucent analyzer record copy");

            for (int recordIndex = 0; recordIndex < recordCount; recordIndex++) {
                TQuad quad = buildRegularQuad(recordsSegment, recordIndex);
                if (quad == null) {
                    throw new IllegalStateException("Native translucent records produced an unexpected invalid quad");
                }
                quads[recordIndex] = quad;
            }
        }

        return quads;
    }

    void destroy() {
        long handle = this.handle;
        if (handle != 0) {
            check(invokeDestroy(handle), "native translucent analyzer destroy");
            this.handle = 0;
        }
    }

    int getRecordCount() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment countSegment = arena.allocate(ValueLayout.JAVA_INT);
            check(invokeRecordCount(this.getHandle(), countSegment), "native translucent analyzer count query");
            return countSegment.get(ValueLayout.JAVA_INT, 0);
        }
    }

    private static TQuad buildRegularQuad(MemorySegment recordsSegment, int recordIndex) {
        ChunkVertexEncoder.Vertex[] vertices = ChunkVertexEncoder.Vertex.uninitializedQuad();
        long recordOffset = (long) recordIndex * RECORD_STRIDE;

        for (int vertexIndex = 0; vertexIndex < 4; vertexIndex++) {
            ChunkVertexEncoder.Vertex vertex = vertices[vertexIndex];
            long positionOffset = recordOffset + OFFSET_POSITIONS + (long) vertexIndex * 3 * Float.BYTES;
            vertex.x = recordsSegment.get(ValueLayout.JAVA_FLOAT, positionOffset);
            vertex.y = recordsSegment.get(ValueLayout.JAVA_FLOAT, positionOffset + Float.BYTES);
            vertex.z = recordsSegment.get(ValueLayout.JAVA_FLOAT, positionOffset + 2L * Float.BYTES);
        }

        ModelQuadFacing facing = ModelQuadFacing.VALUES[recordsSegment.get(ValueLayout.JAVA_INT,
                recordOffset + OFFSET_FACING)];
        int packedNormal = recordsSegment.get(ValueLayout.JAVA_INT, recordOffset + OFFSET_PACKED_NORMAL);
        return RegularTQuad.fromVertices(vertices, facing, packedNormal);
    }

    private long getHandle() {
        if (this.handle == 0) {
            throw new IllegalStateException("Native translucent analyzer has been destroyed");
        }
        return this.handle;
    }

    private static void check(int status, String operation) {
        if (status != OK) {
            throw new IllegalStateException(operation + " failed with native status " + status);
        }
    }

    private static int invokeVerify() {
        try {
            return (int) VERIFY.invokeExact();
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust translucent analyzer verification downcall failed", throwable);
        }
    }

    private static int invokeCreate(MemorySegment handleOutput) {
        try {
            return (int) CREATE.invokeExact(handleOutput);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust translucent analyzer creation downcall failed", throwable);
        }
    }

    private static int invokeDestroy(long handle) {
        try {
            return (int) DESTROY.invokeExact(handle);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust translucent analyzer destroy downcall failed", throwable);
        }
    }

    private static int invokeAppendRecord(long handle, MemorySegment record) {
        try {
            return (int) APPEND_RECORD.invokeExact(handle, record);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust translucent analyzer append downcall failed", throwable);
        }
    }

    private static int invokeAppendNativeQuad(long handle, long nativeQuadAddress, int facing, int packedNormal) {
        try {
            return (int) APPEND_NATIVE_QUAD.invokeExact(handle, nativeQuadAddress, facing, packedNormal);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust translucent analyzer native append downcall failed", throwable);
        }
    }

    private static int invokeRecordCount(long handle, MemorySegment countOutput) {
        try {
            return (int) RECORD_COUNT.invokeExact(handle, countOutput);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust translucent analyzer count downcall failed", throwable);
        }
    }

    private static int invokeWriteRecordsByFacing(long handle, MemorySegment records, int recordCount) {
        try {
            return (int) WRITE_RECORDS_BY_FACING.invokeExact(handle, records, recordCount);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust translucent analyzer record copy downcall failed", throwable);
        }
    }

    private static int invokeAnalyze(long handle, int sortMode, MemorySegment metrics, int metricsLength,
            MemorySegment meshFacingCounts, int meshFacingCountsLength, MemorySegment staticKeys,
            int staticKeysLength) {
        try {
            return (int) ANALYZE.invokeExact(handle, sortMode, metrics, metricsLength,
                    meshFacingCounts, meshFacingCountsLength, staticKeys, staticKeysLength);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust translucent analyzer downcall failed", throwable);
        }
    }

    private static int invokeStaticTopoSort(long handle, int failOnIntersection, MemorySegment quadIndexes,
            int quadIndexesLength) {
        try {
            return (int) STATIC_TOPO_SORT.invokeExact(handle, failOnIntersection, quadIndexes, quadIndexesLength);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust static translucent topo sort downcall failed", throwable);
        }
    }

    private static void writeRecord(MemorySegment recordSegment, ChunkVertexEncoder.Vertex[] vertices,
            int facingOrdinal, int packedNormal) {
        for (int vertexIndex = 0; vertexIndex < 4; vertexIndex++) {
            ChunkVertexEncoder.Vertex vertex = vertices[vertexIndex];
            long positionOffset = OFFSET_POSITIONS + (long) vertexIndex * 3 * Float.BYTES;
            recordSegment.set(ValueLayout.JAVA_FLOAT, positionOffset, vertex.x);
            recordSegment.set(ValueLayout.JAVA_FLOAT, positionOffset + Float.BYTES, vertex.y);
            recordSegment.set(ValueLayout.JAVA_FLOAT, positionOffset + 2L * Float.BYTES, vertex.z);
        }

        recordSegment.set(ValueLayout.JAVA_INT, OFFSET_FACING, facingOrdinal);
        recordSegment.set(ValueLayout.JAVA_INT, OFFSET_PACKED_NORMAL, packedNormal);
    }

    record Analysis(
            SortType sortType,
            int quadHash,
            int alignedFacingBitmap,
            boolean doubleUnaligned,
            int[] meshFacingCounts,
            int[] staticKeys,
            int quadCount
    ) {
    }
}
