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
import java.util.Arrays;

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
    private static final MethodHandle ANALYZE = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_translucent_analyzer_analyze",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT));
    private static final MethodHandle STATIC_TOPO_SORT = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_translucent_static_topo_sort",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT));
    private static final int VERIFY_STATUS = invokeVerify();

    private float[] positions;
    private int[] facings;
    private int[] packedNormals;
    private int recordCount;
    private int capacity;

    NativeTranslucentGeometryAnalyzer() {
        check(VERIFY_STATUS, "native translucent analyzer verification");
    }

    boolean appendQuad(ChunkVertexEncoder.Vertex[] vertices, ModelQuadFacing facing, int packedNormal) {
        if (isInvalid(vertices)) {
            return true;
        }

        this.ensureCapacity(this.recordCount + 1);
        int positionOffset = this.recordCount * POSITION_FLOATS;

        for (int vertexIndex = 0; vertexIndex < 4; vertexIndex++) {
            ChunkVertexEncoder.Vertex vertex = vertices[vertexIndex];
            int vertexOffset = positionOffset + vertexIndex * 3;
            this.positions[vertexOffset] = vertex.x;
            this.positions[vertexOffset + 1] = vertex.y;
            this.positions[vertexOffset + 2] = vertex.z;
        }

        this.facings[this.recordCount] = facing.ordinal();
        this.packedNormals[this.recordCount] = packedNormal;
        this.recordCount++;
        return false;
    }

    Analysis analyze(SortBehavior.SortMode sortMode) {
        int[] metrics = new int[METRIC_COUNT];
        int[] meshFacingCounts = new int[ModelQuadFacing.COUNT];
        int[] staticKeys = new int[this.recordCount];

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment metricsSegment = arena.allocate(ValueLayout.JAVA_INT, metrics.length);
            MemorySegment meshFacingCountsSegment = arena.allocate(ValueLayout.JAVA_INT, meshFacingCounts.length);
            MemorySegment staticKeysSegment = arena.allocate(ValueLayout.JAVA_INT, staticKeys.length);
            MemorySegment recordsSegment = this.copyRecords(arena);

            check(invokeAnalyze(
                    recordsSegment,
                    this.recordCount,
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
                Arrays.copyOf(staticKeys, metrics[4]),
                this.recordCount
        );
    }

    int[] staticTopoSort(boolean failOnIntersection) {
        int[] quadIndexes = new int[this.recordCount];

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment recordsSegment = this.copyRecords(arena);
            MemorySegment quadIndexesSegment = arena.allocate(ValueLayout.JAVA_INT, quadIndexes.length);
            int status = invokeStaticTopoSort(recordsSegment, this.recordCount, failOnIntersection ? 1 : 0,
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
            MemorySegment recordsSegment = this.copyRecords(arena);
            MemorySegment handleSegment = arena.allocate(ValueLayout.JAVA_LONG);
            return NativeTranslucentSectionGeometry.create(recordsSegment, this.recordCount, handleSegment);
        }
    }

    TQuad[] buildRegularQuadsByFacing() {
        TQuad[] quads = new TQuad[this.recordCount];
        int outputIndex = 0;

        for (ModelQuadFacing facing : ModelQuadFacing.VALUES) {
            for (int recordIndex = 0; recordIndex < this.recordCount; recordIndex++) {
                if (this.readFacing(recordIndex) != facing.ordinal()) {
                    continue;
                }

                TQuad quad = this.buildRegularQuad(recordIndex, facing);
                if (quad != null) {
                    quads[outputIndex++] = quad;
                }
            }
        }

        if (outputIndex != quads.length) {
            throw new IllegalStateException("Native translucent records produced an unexpected valid quad count");
        }
        return quads;
    }

    void destroy() {
        this.positions = null;
        this.facings = null;
        this.packedNormals = null;
        this.recordCount = 0;
        this.capacity = 0;
    }

    int getRecordCount() {
        return this.recordCount;
    }

    private TQuad buildRegularQuad(int recordIndex, ModelQuadFacing facing) {
        ChunkVertexEncoder.Vertex[] vertices = ChunkVertexEncoder.Vertex.uninitializedQuad();
        int positionOffset = recordIndex * POSITION_FLOATS;

        for (int vertexIndex = 0; vertexIndex < 4; vertexIndex++) {
            int vertexOffset = positionOffset + vertexIndex * 3;
            ChunkVertexEncoder.Vertex vertex = vertices[vertexIndex];
            vertex.x = this.positions[vertexOffset];
            vertex.y = this.positions[vertexOffset + 1];
            vertex.z = this.positions[vertexOffset + 2];
        }

        return RegularTQuad.fromVertices(vertices, facing, this.readPackedNormal(recordIndex));
    }

    private int readFacing(int recordIndex) {
        return this.facings[recordIndex];
    }

    private int readPackedNormal(int recordIndex) {
        return this.packedNormals[recordIndex];
    }

    private void ensureCapacity(int requiredCapacity) {
        if (requiredCapacity <= this.capacity) {
            return;
        }

        int newCapacity = Math.max(requiredCapacity, Math.max(16, this.capacity * 2));
        this.positions = Arrays.copyOf(this.positions == null ? new float[0] : this.positions,
                newCapacity * POSITION_FLOATS);
        this.facings = Arrays.copyOf(this.facings == null ? new int[0] : this.facings, newCapacity);
        this.packedNormals = Arrays.copyOf(this.packedNormals == null ? new int[0] : this.packedNormals,
                newCapacity);
        this.capacity = newCapacity;
    }

    private MemorySegment copyRecords(Arena arena) {
        if (this.recordCount == 0) {
            return MemorySegment.NULL;
        }

        MemorySegment recordsSegment = arena.allocate((long) this.recordCount * RECORD_STRIDE, Integer.BYTES);
        for (int recordIndex = 0; recordIndex < this.recordCount; recordIndex++) {
            long recordOffset = (long) recordIndex * RECORD_STRIDE;
            int positionOffset = recordIndex * POSITION_FLOATS;

            for (int positionIndex = 0; positionIndex < POSITION_FLOATS; positionIndex++) {
                recordsSegment.set(ValueLayout.JAVA_FLOAT,
                        recordOffset + OFFSET_POSITIONS + (long) positionIndex * Float.BYTES,
                        this.positions[positionOffset + positionIndex]);
            }

            recordsSegment.set(ValueLayout.JAVA_INT, recordOffset + OFFSET_FACING, this.facings[recordIndex]);
            recordsSegment.set(ValueLayout.JAVA_INT, recordOffset + OFFSET_PACKED_NORMAL,
                    this.packedNormals[recordIndex]);
        }

        return recordsSegment;
    }

    private static boolean isInvalid(ChunkVertexEncoder.Vertex[] vertices) {
        float lastX = vertices[3].x;
        float lastY = vertices[3].y;
        float lastZ = vertices[3].z;
        int sameVertexMap = 0;

        for (int index = 0; index < 4; index++) {
            ChunkVertexEncoder.Vertex vertex = vertices[index];
            if (Math.abs(vertex.x - lastX) < TQuad.VERTEX_EPSILON
                    && Math.abs(vertex.y - lastY) < TQuad.VERTEX_EPSILON
                    && Math.abs(vertex.z - lastZ) < TQuad.VERTEX_EPSILON) {
                sameVertexMap |= 1 << index;
            }

            if (index != 3) {
                lastX = vertex.x;
                lastY = vertex.y;
                lastZ = vertex.z;
            }
        }

        return Integer.bitCount(sameVertexMap) > 1;
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

    private static int invokeAnalyze(
            MemorySegment records,
            int recordCount,
            int sortMode,
            MemorySegment metrics,
            int metricsLength,
            MemorySegment meshFacingCounts,
            int meshFacingCountsLength,
            MemorySegment staticKeys,
            int staticKeysLength
    ) {
        try {
            return (int) ANALYZE.invokeExact(records, recordCount, sortMode, metrics, metricsLength,
                    meshFacingCounts, meshFacingCountsLength, staticKeys, staticKeysLength);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust translucent analyzer downcall failed", throwable);
        }
    }

    private static int invokeStaticTopoSort(
            MemorySegment records,
            int recordCount,
            int failOnIntersection,
            MemorySegment quadIndexes,
            int quadIndexesLength
    ) {
        try {
            return (int) STATIC_TOPO_SORT.invokeExact(records, recordCount, failOnIntersection, quadIndexes,
                    quadIndexesLength);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust static translucent topo sort downcall failed", throwable);
        }
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
