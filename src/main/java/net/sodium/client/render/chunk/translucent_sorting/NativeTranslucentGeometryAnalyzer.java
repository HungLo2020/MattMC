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

public final class NativeTranslucentGeometryAnalyzer {
    private static final int RECORD_STRIDE = 56;
    private static final int POSITION_FLOATS = 12;
    private static final int OFFSET_POSITIONS = 0;
    private static final int OFFSET_FACING = OFFSET_POSITIONS + POSITION_FLOATS * Float.BYTES;
    private static final int OFFSET_PACKED_NORMAL = OFFSET_FACING + Integer.BYTES;
    private static final int TOPO_RECORD_STRIDE = 84;
    private static final int TOPO_EXTENT_FLOATS = 6;
    private static final int OFFSET_TOPO_POSITIONS = 0;
    private static final int OFFSET_TOPO_EXTENTS = OFFSET_TOPO_POSITIONS + POSITION_FLOATS * Float.BYTES;
    private static final int OFFSET_TOPO_ACCURATE_DOT_PRODUCT = OFFSET_TOPO_EXTENTS + TOPO_EXTENT_FLOATS * Float.BYTES;
    private static final int OFFSET_TOPO_FACING = OFFSET_TOPO_ACCURATE_DOT_PRODUCT + Float.BYTES;
    private static final int OFFSET_TOPO_PACKED_NORMAL = OFFSET_TOPO_FACING + Integer.BYTES;
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
    private static final MethodHandle APPEND_NATIVE_QUAD_BATCH = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_translucent_analyzer_append_native_quad_batch",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.ADDRESS));
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
    private static final MethodHandle GEOMETRY_PLANES_CREATE_FROM_RECORDS = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_geometry_planes_create_from_records",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS));
    private static final MethodHandle GEOMETRY_PLANES_CREATE_FROM_ANALYZER = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_geometry_planes_create_from_analyzer",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.ADDRESS));
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
    private static final MethodHandle TOPO_GRAPH_SORT = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_translucent_topo_graph_sort_records",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT));
    private static final MethodHandle TOPO_QUAD_STORE_CREATE = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_translucent_topo_quad_store_create",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS));
    private static final MethodHandle TOPO_QUAD_STORE_DESTROY = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_translucent_topo_quad_store_destroy",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG));
    private static final MethodHandle TOPO_QUAD_STORE_SET = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_translucent_topo_quad_store_set",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS));
    private static final MethodHandle TOPO_QUAD_STORE_REMOVE = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_translucent_topo_quad_store_remove",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT));
    private static final MethodHandle TOPO_QUAD_STORE_BSP_DOUBLE_LEAF_POSSIBLE = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_translucent_topo_quad_store_bsp_double_leaf_possible",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS));
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

    public static int[] topoGraphSort(TQuad[] quads, boolean failOnIntersection) {
        return topoGraphSort(quads, quads.length, null, failOnIntersection);
    }

    public static int[] topoGraphSort(TQuad[] quads, int quadCount, int[] activeToRealIndex,
            boolean failOnIntersection) {
        if (quadCount < 0 || quadCount > quads.length) {
            throw new IllegalArgumentException("Invalid translucent topo quad count: " + quadCount);
        }
        if (activeToRealIndex != null && activeToRealIndex.length < quadCount) {
            throw new IllegalArgumentException("Active translucent topo index map is shorter than the quad count");
        }

        int[] quadIndexes = new int[quadCount];

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment recordsSegment = quadCount == 0
                    ? MemorySegment.NULL
                    : arena.allocate((long) quadCount * TOPO_RECORD_STRIDE, Integer.BYTES);
            for (int quadIndex = 0; quadIndex < quadCount; quadIndex++) {
                writeTopoRecord(recordsSegment.asSlice((long) quadIndex * TOPO_RECORD_STRIDE), quads[quadIndex]);
            }

            MemorySegment activeToRealIndexSegment = MemorySegment.NULL;
            int activeToRealIndexLength = 0;
            if (activeToRealIndex != null) {
                activeToRealIndexSegment = arena.allocate(ValueLayout.JAVA_INT, activeToRealIndex.length);
                activeToRealIndexLength = activeToRealIndex.length;
                for (int index = 0; index < activeToRealIndex.length; index++) {
                    activeToRealIndexSegment.setAtIndex(ValueLayout.JAVA_INT, index, activeToRealIndex[index]);
                }
            }

            MemorySegment quadIndexesSegment = quadCount == 0
                    ? MemorySegment.NULL
                    : arena.allocate(ValueLayout.JAVA_INT, quadCount);
            int status = invokeTopoGraphSort(recordsSegment, quadCount, activeToRealIndexSegment,
                    activeToRealIndexLength, failOnIntersection ? 1 : 0, quadIndexesSegment, quadIndexes.length);
            if (status == SORT_FAILED) {
                return null;
            }
            check(status, "native translucent topo graph sort");

            for (int index = 0; index < quadIndexes.length; index++) {
                quadIndexes[index] = quadIndexesSegment.getAtIndex(ValueLayout.JAVA_INT, index);
            }
        }

        return quadIndexes;
    }

    public static TopoQuadStore createTopoQuadStore(TQuad[] quads) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment recordsSegment = quads.length == 0
                    ? MemorySegment.NULL
                    : arena.allocate((long) quads.length * TOPO_RECORD_STRIDE, Integer.BYTES);
            for (int quadIndex = 0; quadIndex < quads.length; quadIndex++) {
                writeTopoRecord(recordsSegment.asSlice((long) quadIndex * TOPO_RECORD_STRIDE), quads[quadIndex]);
            }

            MemorySegment handleSegment = arena.allocate(ValueLayout.JAVA_LONG);
            check(invokeTopoQuadStoreCreate(recordsSegment, quads.length, handleSegment),
                    "native translucent topo quad store creation");
            long handle = handleSegment.get(ValueLayout.JAVA_LONG, 0);
            if (handle == 0) {
                throw new IllegalStateException("Native translucent topo quad store creation returned a null handle");
            }
            return new TopoQuadStore(handle);
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

    int appendNativeQuadBatch(long nativeQuadAddress, int quadCount, ModelQuadFacing facing, long packedNormalsAddress,
            long validityAddress) {
        if (quadCount < 0) {
            throw new IllegalArgumentException("Invalid native translucent quad count: " + quadCount);
        }
        if (quadCount == 0) {
            return 0;
        }

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment validCountSegment = arena.allocate(ValueLayout.JAVA_INT);
            check(invokeAppendNativeQuadBatch(this.getHandle(), nativeQuadAddress, quadCount, facing.ordinal(),
                    MemorySegment.ofAddress(packedNormalsAddress), validityAddress, validCountSegment),
                    "native translucent analyzer batch append");
            return validCountSegment.get(ValueLayout.JAVA_INT, 0);
        }
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

    NativeTranslucentSortData createStaticTopoSortData(boolean failOnIntersection) {
        return NativeTranslucentSortData.createStaticTopoFromAnalyzer(this.getHandle(), this.getRecordCount(),
                failOnIntersection);
    }

    NativeTranslucentSortData createDynamicTopoSortData() {
        return NativeTranslucentSortData.createDynamicTopoFromAnalyzer(this.getHandle(), this.getRecordCount());
    }

    long createGeometryPlanesHandle() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment handleSegment = arena.allocate(ValueLayout.JAVA_LONG);
            check(invokeCreateGeometryPlanesFromAnalyzer(this.getHandle(), handleSegment),
                    "native translucent geometry plane creation from analyzer");
            long handle = handleSegment.get(ValueLayout.JAVA_LONG, 0);
            if (handle == 0) {
                throw new IllegalStateException("Native translucent geometry plane creation returned a null handle");
            }
            return handle;
        }
    }

    public static long createGeometryPlanes(TQuad[] quads) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment recordsSegment = quads.length == 0
                    ? MemorySegment.NULL
                    : arena.allocate((long) quads.length * RECORD_STRIDE, Integer.BYTES);
            for (int quadIndex = 0; quadIndex < quads.length; quadIndex++) {
                writeQuadRecord(recordsSegment.asSlice((long) quadIndex * RECORD_STRIDE), quads[quadIndex]);
            }

            MemorySegment handleSegment = arena.allocate(ValueLayout.JAVA_LONG);
            check(invokeCreateGeometryPlanesFromRecords(recordsSegment, quads.length, handleSegment),
                    "native translucent geometry plane creation from quad records");
            long handle = handleSegment.get(ValueLayout.JAVA_LONG, 0);
            if (handle == 0) {
                throw new IllegalStateException("Native translucent geometry plane creation returned a null handle");
            }
            return handle;
        }
    }

    long handle() {
        return this.getHandle();
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

    private static int invokeAppendNativeQuadBatch(long handle, long nativeQuadAddress, int quadCount, int facing,
            MemorySegment packedNormals, long validityAddress, MemorySegment validCountOutput) {
        try {
            return (int) APPEND_NATIVE_QUAD_BATCH.invokeExact(handle, nativeQuadAddress, quadCount, facing,
                    packedNormals, validityAddress, validCountOutput);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust translucent analyzer native batch append downcall failed",
                    throwable);
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

    private static int invokeCreateGeometryPlanesFromRecords(MemorySegment records, int recordCount,
            MemorySegment handleOutput) {
        try {
            return (int) GEOMETRY_PLANES_CREATE_FROM_RECORDS.invokeExact(records, recordCount, handleOutput);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust translucent geometry plane record downcall failed", throwable);
        }
    }

    private static int invokeCreateGeometryPlanesFromAnalyzer(long handle, MemorySegment handleOutput) {
        try {
            return (int) GEOMETRY_PLANES_CREATE_FROM_ANALYZER.invokeExact(handle, handleOutput);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust translucent analyzer geometry plane downcall failed", throwable);
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

    private static int invokeTopoGraphSort(MemorySegment records, int recordCount,
            MemorySegment activeToRealIndex, int activeToRealIndexLength, int failOnIntersection,
            MemorySegment quadIndexes, int quadIndexesLength) {
        try {
            return (int)TOPO_GRAPH_SORT.invokeExact(records, recordCount, activeToRealIndex,
                    activeToRealIndexLength, failOnIntersection, quadIndexes, quadIndexesLength);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust translucent topo graph sort downcall failed", throwable);
        }
    }

    private static int invokeTopoQuadStoreCreate(MemorySegment records, int recordCount, MemorySegment outputHandle) {
        try {
            return (int)TOPO_QUAD_STORE_CREATE.invokeExact(records, recordCount, outputHandle);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust translucent topo quad store creation downcall failed", throwable);
        }
    }

    private static int invokeTopoQuadStoreDestroy(long handle) {
        try {
            return (int)TOPO_QUAD_STORE_DESTROY.invokeExact(handle);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust translucent topo quad store destroy downcall failed", throwable);
        }
    }

    private static int invokeTopoQuadStoreSet(long handle, int index, MemorySegment record) {
        try {
            return (int)TOPO_QUAD_STORE_SET.invokeExact(handle, index, record);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust translucent topo quad store set downcall failed", throwable);
        }
    }

    private static int invokeTopoQuadStoreRemove(long handle, int index) {
        try {
            return (int)TOPO_QUAD_STORE_REMOVE.invokeExact(handle, index);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust translucent topo quad store remove downcall failed", throwable);
        }
    }

    private static int invokeTopoQuadStoreBspDoubleLeafPossible(long handle, int quadAIndex, int quadBIndex,
            int failOnIntersection, MemorySegment result) {
        try {
            return (int)TOPO_QUAD_STORE_BSP_DOUBLE_LEAF_POSSIBLE.invokeExact(handle, quadAIndex, quadBIndex,
                    failOnIntersection, result);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust translucent topo quad store BSP double leaf downcall failed",
                    throwable);
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

    private static void writeQuadRecord(MemorySegment recordSegment, TQuad quad) {
        float[] vertexPositions = quad.getVertexPositions();
        for (int positionIndex = 0; positionIndex < POSITION_FLOATS; positionIndex++) {
            recordSegment.set(ValueLayout.JAVA_FLOAT, OFFSET_POSITIONS + (long) positionIndex * Float.BYTES,
                    vertexPositions[positionIndex]);
        }

        recordSegment.set(ValueLayout.JAVA_INT, OFFSET_FACING, quad.getFacing().ordinal());
        recordSegment.set(ValueLayout.JAVA_INT, OFFSET_PACKED_NORMAL, quad.getPackedNormal());
    }

    private static void writeTopoRecord(MemorySegment recordSegment, TQuad quad) {
        float[] vertexPositions = quad.getVertexPositions();
        for (int positionIndex = 0; positionIndex < POSITION_FLOATS; positionIndex++) {
            recordSegment.set(ValueLayout.JAVA_FLOAT, OFFSET_TOPO_POSITIONS + (long) positionIndex * Float.BYTES,
                    vertexPositions[positionIndex]);
        }

        float[] extents = quad.getExtents();
        for (int extentIndex = 0; extentIndex < TOPO_EXTENT_FLOATS; extentIndex++) {
            recordSegment.set(ValueLayout.JAVA_FLOAT, OFFSET_TOPO_EXTENTS + (long) extentIndex * Float.BYTES,
                    extents[extentIndex]);
        }

        recordSegment.set(ValueLayout.JAVA_FLOAT, OFFSET_TOPO_ACCURATE_DOT_PRODUCT, quad.getAccurateDotProduct());
        recordSegment.set(ValueLayout.JAVA_INT, OFFSET_TOPO_FACING, quad.getFacing().ordinal());
        recordSegment.set(ValueLayout.JAVA_INT, OFFSET_TOPO_PACKED_NORMAL, quad.getPackedNormal());
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

    public static final class TopoQuadStore implements AutoCloseable {
        private long handle;

        private TopoQuadStore(long handle) {
            this.handle = handle;
        }

        public void set(int index, TQuad quad) {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment recordSegment = arena.allocate(TOPO_RECORD_STRIDE, Integer.BYTES);
                writeTopoRecord(recordSegment, quad);
                check(invokeTopoQuadStoreSet(this.getHandle(), index, recordSegment),
                        "native translucent topo quad store update");
            }
        }

        public void remove(int index) {
            check(invokeTopoQuadStoreRemove(this.getHandle(), index),
                    "native translucent topo quad store removal");
        }

        public boolean bspDoubleLeafPossible(int quadAIndex, int quadBIndex, boolean failOnIntersection) {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment resultSegment = arena.allocate(ValueLayout.JAVA_INT);
                check(invokeTopoQuadStoreBspDoubleLeafPossible(this.getHandle(), quadAIndex, quadBIndex,
                        failOnIntersection ? 1 : 0, resultSegment),
                        "native translucent topo quad store BSP double leaf test");
                return resultSegment.get(ValueLayout.JAVA_INT, 0) != 0;
            }
        }

        @Override
        public void close() {
            long handle = this.handle;
            if (handle != 0) {
                check(invokeTopoQuadStoreDestroy(handle), "native translucent topo quad store destroy");
                this.handle = 0;
            }
        }

        private long getHandle() {
            if (this.handle == 0) {
                throw new IllegalStateException("Native translucent topo quad store has been closed");
            }
            return this.handle;
        }
    }
}
