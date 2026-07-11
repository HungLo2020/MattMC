package net.sodium.client.render.chunk.vertex.format;

import net.minecraft.util.NativeLibraryLoader;
import net.sodium.client.model.quad.properties.ModelQuadFacing;
import org.lwjgl.system.MemoryUtil;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

public final class NativeChunkMeshEncoder {
    public static final int NATIVE_QUAD_STRIDE = 152;

    private static final int OK = 0;
    public static final int COMPACT_VALUE_STRIDE = 0;
    public static final int COMPACT_VALUE_POSITION_OFFSET = 1;
    public static final int COMPACT_VALUE_COLOR_OFFSET = 2;
    public static final int COMPACT_VALUE_TEXTURE_OFFSET = 3;
    public static final int COMPACT_VALUE_LIGHT_MATERIAL_INDEX_OFFSET = 4;
    public static final int COMPACT_VALUE_BLOCK_ID_OFFSET = 5;
    public static final int COMPACT_VALUE_NORMAL_OFFSET = 6;
    public static final int COMPACT_VALUE_TANGENT_OFFSET = 7;
    public static final int COMPACT_VALUE_MID_UV_OFFSET = 8;
    public static final int COMPACT_VALUE_MID_BLOCK_OFFSET = 9;
    public static final int COMPACT_VALUE_POSITION_MAX_VALUE = 10;
    public static final int COMPACT_VALUE_TEXTURE_MAX_VALUE = 11;
    private static final int POSITION_COMPONENT_X = 0;
    private static final int POSITION_COMPONENT_Y = 1;
    private static final int POSITION_COMPONENT_Z = 2;
    private static final int INDEX_MODE_NONE = 0;
    private static final int INDEX_MODE_SHARED = 1;
    private static final int INDEX_MODE_SORTED_QUADS = 2;
    private static final int INDEX_MODE_KEY_SORTED = 3;
    private static final MethodHandle VERIFY = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_chunk_mesh_verify",
            FunctionDescriptor.of(ValueLayout.JAVA_INT));
    private static final MethodHandle COMPACT_FORMAT_VALUE = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_chunk_compact_format_value",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT));
    private static final MethodHandle WRITE_NATIVE_QUAD_METADATA = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_chunk_native_quad_write_metadata",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT));
    private static final MethodHandle WRITE_NATIVE_QUAD = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_chunk_native_quad_write",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_INT));
    private static final MethodHandle WRITE_NATIVE_QUAD_VERTEX = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_chunk_native_quad_write_vertex",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_FLOAT,
                    ValueLayout.JAVA_FLOAT,
                    ValueLayout.JAVA_FLOAT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_FLOAT,
                    ValueLayout.JAVA_FLOAT,
                    ValueLayout.JAVA_FLOAT,
                    ValueLayout.JAVA_INT));
    private static final MethodHandle NATIVE_QUAD_POSITION = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_chunk_native_quad_position",
            FunctionDescriptor.of(ValueLayout.JAVA_FLOAT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT));
    private static final MethodHandle ENCODE = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_chunk_mesh_encode",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT));
    private static final MethodHandle SCATTERED_ENCODE = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_chunk_mesh_scattered_encode",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT));
    private static final MethodHandle ASSEMBLE_OUTPUT = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_chunk_mesh_output_assemble",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT));
    private static final MethodHandle WRITE_SHARED = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_chunk_shared_quad_index_buffer_write",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT));
    private static final MethodHandle WRITE_SORTED = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_chunk_sorted_quad_index_buffer_write",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT));
    private static final MethodHandle WRITE_KEY_SORTED = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_chunk_key_sorted_quad_index_buffer_write",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT));
    private NativeChunkMeshEncoder() {
    }

    public static void verifyAvailable() {
        check(invokeVerify(), "native chunk mesh encoder verification");
    }

    public static int compactFormatValue(int value) {
        int result = invokeCompactFormatValue(value);
        if (result < 0) {
            throw new IllegalArgumentException("Unknown compact chunk vertex format value: " + value);
        }
        return result;
    }

    public static NativeChunkVertexFormat compactNativeFormat() {
        return new NativeChunkVertexFormat(
                compactFormatValue(COMPACT_VALUE_STRIDE),
                compactFormatValue(COMPACT_VALUE_BLOCK_ID_OFFSET),
                compactFormatValue(COMPACT_VALUE_NORMAL_OFFSET),
                compactFormatValue(COMPACT_VALUE_TANGENT_OFFSET),
                compactFormatValue(COMPACT_VALUE_MID_UV_OFFSET),
                compactFormatValue(COMPACT_VALUE_MID_BLOCK_OFFSET));
    }

    public static void encode(
            ByteBuffer logicalVertices,
            int vertexCount,
            ByteBuffer output,
            int outputVertexOffset,
            NativeChunkVertexFormat format,
            int sectionIndex,
            boolean separateAo
    ) {
        if (vertexCount == 0) {
            return;
        }

        long inputAddress = MemoryUtil.memAddress(logicalVertices);
        long outputAddress = MemoryUtil.memAddress(output, outputVertexOffset * format.stride());

        check(invokeEncode(
                inputAddress,
                vertexCount,
                outputAddress,
                output.remaining() - outputVertexOffset * format.stride(),
                NATIVE_QUAD_STRIDE,
                format.stride(),
                format.blockIdOffset(),
                format.normalOffset(),
                format.tangentOffset(),
                format.midUvOffset(),
                format.midBlockOffset(),
                sectionIndex,
                separateAo ? 1 : 0
        ), "native chunk vertex encoding");
    }

    public static void encodeScattered(
            long inputAddress,
            int[] outputVertexOffsets,
            int updateCount,
            ByteBuffer output,
            NativeChunkVertexFormat format,
            int sectionIndex,
            boolean separateAo
    ) {
        if (updateCount < 0 || updateCount > outputVertexOffsets.length) {
            throw new IllegalArgumentException("Invalid scattered encode update count: " + updateCount);
        }
        if (updateCount == 0) {
            return;
        }

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment outputVertexOffsetsSegment = arena.allocate(ValueLayout.JAVA_INT, updateCount);

            for (int index = 0; index < updateCount; index++) {
                outputVertexOffsetsSegment.setAtIndex(ValueLayout.JAVA_INT, index, outputVertexOffsets[index]);
            }

            check(invokeScatteredEncode(
                    inputAddress,
                    outputVertexOffsetsSegment,
                    updateCount,
                    MemoryUtil.memAddress(output),
                    output.remaining(),
                    NATIVE_QUAD_STRIDE,
                    format.stride(),
                    format.blockIdOffset(),
                    format.normalOffset(),
                    format.tangentOffset(),
                    format.midUvOffset(),
                    format.midBlockOffset(),
                    sectionIndex,
                    separateAo ? 1 : 0
            ), "native scattered chunk vertex encoding");
        }
    }

    public static void writeNativeQuadMetadata(long ptr, byte blockEmission, byte renderType, boolean ignoreMidBlock,
            int blockId, int localX, int localY, int localZ, int materialBits) {
        check(invokeWriteNativeQuadMetadata(ptr, blockEmission, renderType, ignoreMidBlock ? 1 : 0, blockId, localX,
                localY, localZ, materialBits), "native quad metadata writing");
    }

    public static void writeNativeQuadVertex(long ptr, int vertexIndex, float x, float y, float z, int color,
            float ao, float u, float v, int light) {
        if (vertexIndex < 0 || vertexIndex >= 4) {
            throw new IllegalArgumentException("Invalid quad vertex index: " + vertexIndex);
        }

        check(invokeWriteNativeQuadVertex(ptr, vertexIndex, x, y, z, color, ao, u, v, light),
                "native quad vertex writing");
    }

    public static void writeNativeQuad(
            long ptr,
            byte blockEmission,
            byte renderType,
            boolean ignoreMidBlock,
            int blockId,
            int localX,
            int localY,
            int localZ,
            int materialBits,
            float x0, float y0, float z0, int color0, float ao0, float u0, float v0, int light0,
            float x1, float y1, float z1, int color1, float ao1, float u1, float v1, int light1,
            float x2, float y2, float z2, int color2, float ao2, float u2, float v2, int light2,
            float x3, float y3, float z3, int color3, float ao3, float u3, float v3, int light3
    ) {
        check(invokeWriteNativeQuad(ptr, blockEmission, renderType, ignoreMidBlock ? 1 : 0, blockId, localX, localY,
                localZ, materialBits,
                x0, y0, z0, color0, ao0, u0, v0, light0,
                x1, y1, z1, color1, ao1, u1, v1, light1,
                x2, y2, z2, color2, ao2, u2, v2, light2,
                x3, y3, z3, color3, ao3, u3, v3, light3), "native quad writing");
    }

    public static float nativeQuadX(long ptr, int vertexIndex) {
        return nativeQuadPosition(ptr, vertexIndex, POSITION_COMPONENT_X);
    }

    public static float nativeQuadY(long ptr, int vertexIndex) {
        return nativeQuadPosition(ptr, vertexIndex, POSITION_COMPONENT_Y);
    }

    public static float nativeQuadZ(long ptr, int vertexIndex) {
        return nativeQuadPosition(ptr, vertexIndex, POSITION_COMPONENT_Z);
    }

    private static float nativeQuadPosition(long ptr, int vertexIndex, int component) {
        if (vertexIndex < 0 || vertexIndex >= 4) {
            throw new IllegalArgumentException("Invalid quad vertex index: " + vertexIndex);
        }

        return invokeNativeQuadPosition(ptr, vertexIndex, component);
    }

    public static void assemble(
            long[] inputAddresses,
            int[] inputVertexCounts,
            ByteBuffer output,
            int[] vertexSegments,
            NativeChunkVertexFormat format,
            int sectionIndex,
            int visibleSlices,
            boolean forceUnassigned,
            boolean sliceReordering,
            boolean separateAo
    ) {
        assembleOutput(inputAddresses, inputVertexCounts, output, vertexSegments, format, sectionIndex,
                visibleSlices, forceUnassigned, sliceReordering, separateAo, null, INDEX_MODE_NONE, 0, null, 0);
    }

    public static void assembleWithSharedIndex(
            long[] inputAddresses,
            int[] inputVertexCounts,
            ByteBuffer output,
            int[] vertexSegments,
            NativeChunkVertexFormat format,
            int sectionIndex,
            int visibleSlices,
            boolean forceUnassigned,
            boolean sliceReordering,
            boolean separateAo,
            ByteBuffer indexOutput,
            int indexStride
    ) {
        assembleOutput(inputAddresses, inputVertexCounts, output, vertexSegments, format, sectionIndex,
                visibleSlices, forceUnassigned, sliceReordering, separateAo,
                indexOutput, INDEX_MODE_SHARED, indexStride, null, 0);
    }

    private static void assembleOutput(
            long[] inputAddresses,
            int[] inputVertexCounts,
            ByteBuffer output,
            int[] vertexSegments,
            NativeChunkVertexFormat format,
            int sectionIndex,
            int visibleSlices,
            boolean forceUnassigned,
            boolean sliceReordering,
            boolean separateAo,
            ByteBuffer indexOutput,
            int indexMode,
            int indexStride,
            int[] indexValues,
            int indexValueCount
    ) {
        if (inputAddresses.length != ModelQuadFacing.COUNT || inputVertexCounts.length != ModelQuadFacing.COUNT) {
            throw new IllegalArgumentException("Expected one input buffer per chunk quad facing");
        }

        if (vertexSegments.length != ModelQuadFacing.COUNT << 1) {
            throw new IllegalArgumentException("Unexpected vertex segment array length: " + vertexSegments.length);
        }
        if (indexValueCount < 0 || (indexValues != null && indexValueCount > indexValues.length)) {
            throw new IllegalArgumentException("Invalid index value count: " + indexValueCount);
        }
        if (indexMode != INDEX_MODE_NONE && indexOutput == null) {
            throw new IllegalArgumentException("Index output buffer is required for index mode " + indexMode);
        }

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment inputAddressesSegment = arena.allocate(ValueLayout.JAVA_LONG, inputAddresses.length);
            MemorySegment inputVertexCountsSegment = arena.allocate(ValueLayout.JAVA_INT, inputVertexCounts.length);
            MemorySegment vertexSegmentsSegment = arena.allocate(ValueLayout.JAVA_INT, vertexSegments.length);
            MemorySegment indexValuesSegment = MemorySegment.NULL;

            for (int index = 0; index < inputAddresses.length; index++) {
                inputAddressesSegment.setAtIndex(ValueLayout.JAVA_LONG, index, inputAddresses[index]);
                inputVertexCountsSegment.setAtIndex(ValueLayout.JAVA_INT, index, inputVertexCounts[index]);
            }
            if (indexValues != null && indexValueCount > 0) {
                indexValuesSegment = arena.allocate(ValueLayout.JAVA_INT, indexValueCount);

                for (int index = 0; index < indexValueCount; index++) {
                    indexValuesSegment.setAtIndex(ValueLayout.JAVA_INT, index, indexValues[index]);
                }
            }

            check(invokeAssembleOutput(
                    inputAddressesSegment,
                    inputVertexCountsSegment,
                    ModelQuadFacing.COUNT,
                    MemoryUtil.memAddress(output),
                    output.remaining(),
                    vertexSegmentsSegment,
                    vertexSegments.length,
                    NATIVE_QUAD_STRIDE,
                    format.stride(),
                    format.blockIdOffset(),
                    format.normalOffset(),
                    format.tangentOffset(),
                    format.midUvOffset(),
                    format.midBlockOffset(),
                    sectionIndex,
                    visibleSlices,
                    forceUnassigned ? 1 : 0,
                    sliceReordering ? 1 : 0,
                    separateAo ? 1 : 0,
                    indexOutput == null ? 0L : MemoryUtil.memAddress(indexOutput),
                    indexOutput == null ? 0 : indexOutput.remaining(),
                    indexMode,
                    indexStride,
                    indexValuesSegment,
                    indexValueCount
            ), "native chunk mesh output assembly");

            for (int index = 0; index < vertexSegments.length; index++) {
                vertexSegments[index] = vertexSegmentsSegment.getAtIndex(ValueLayout.JAVA_INT, index);
            }
        }
    }

    public static void writeSharedQuadIndexBuffer(ByteBuffer output, int indexStride, int primitiveCount) {
        if (primitiveCount == 0) {
            return;
        }

        check(invokeWriteShared(MemoryUtil.memAddress(output), output.remaining(), indexStride, primitiveCount),
                "native shared quad index buffer writing");
    }

    public static void writeQuadVertexIndexes(IntBuffer output, int[] quadIndexes) {
        writeQuadVertexIndexes(output, quadIndexes, quadIndexes.length);
    }

    public static void writeQuadVertexIndexes(IntBuffer output, int[] quadIndexes, int quadIndexCount) {
        if (quadIndexCount < 0 || quadIndexCount > quadIndexes.length) {
            throw new IllegalArgumentException("Invalid quad index count: " + quadIndexCount);
        }
        if (quadIndexCount == 0) {
            return;
        }

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment quadIndexSegment = arena.allocate(ValueLayout.JAVA_INT, quadIndexCount);

            for (int index = 0; index < quadIndexCount; index++) {
                quadIndexSegment.setAtIndex(ValueLayout.JAVA_INT, index, quadIndexes[index]);
            }

            check(invokeWriteSorted(MemoryUtil.memAddress(output), output.remaining(), quadIndexSegment, quadIndexCount),
                    "native sorted quad index buffer writing");
            output.position(output.position() + quadIndexCount * 6);
        }
    }

    public static void writeQuadVertexIndexesSortedByKey(IntBuffer output, int[] keys) {
        writeQuadVertexIndexesSortedByKey(output, keys, 0, keys.length);
    }

    public static void writeQuadVertexIndexesSortedByKey(IntBuffer output, int[] keys, int offset, int count) {
        if (offset < 0 || count < 0 || offset + count > keys.length) {
            throw new IllegalArgumentException("Invalid key range: offset=" + offset + ", count=" + count);
        }
        if (count == 0) {
            return;
        }

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment keysSegment = arena.allocate(ValueLayout.JAVA_INT, count);

            for (int index = 0; index < count; index++) {
                keysSegment.setAtIndex(ValueLayout.JAVA_INT, index, keys[offset + index]);
            }

            check(invokeWriteKeySorted(MemoryUtil.memAddress(output), output.remaining(), keysSegment, count),
                    "native key-sorted quad index buffer writing");
            output.position(output.position() + count * 6);
        }
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
            throw new IllegalStateException("Rust chunk mesh verification downcall failed", throwable);
        }
    }

    private static int invokeCompactFormatValue(int value) {
        try {
            return (int) COMPACT_FORMAT_VALUE.invokeExact(value);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust compact chunk vertex format downcall failed", throwable);
        }
    }

    private static int invokeWriteNativeQuadMetadata(
            long ptr,
            int blockEmission,
            int renderType,
            int ignoreMidBlock,
            int blockId,
            int localX,
            int localY,
            int localZ,
            int materialBits
    ) {
        try {
            return (int) WRITE_NATIVE_QUAD_METADATA.invokeExact(ptr, blockEmission, renderType, ignoreMidBlock,
                    blockId, localX, localY, localZ, materialBits);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust native quad metadata downcall failed", throwable);
        }
    }

    private static int invokeWriteNativeQuadVertex(
            long ptr,
            int vertexIndex,
            float x,
            float y,
            float z,
            int color,
            float ao,
            float u,
            float v,
            int light
    ) {
        try {
            return (int) WRITE_NATIVE_QUAD_VERTEX.invokeExact(ptr, vertexIndex, x, y, z, color, ao, u, v, light);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust native quad vertex downcall failed", throwable);
        }
    }

    private static int invokeWriteNativeQuad(
            long ptr,
            int blockEmission,
            int renderType,
            int ignoreMidBlock,
            int blockId,
            int localX,
            int localY,
            int localZ,
            int materialBits,
            float x0, float y0, float z0, int color0, float ao0, float u0, float v0, int light0,
            float x1, float y1, float z1, int color1, float ao1, float u1, float v1, int light1,
            float x2, float y2, float z2, int color2, float ao2, float u2, float v2, int light2,
            float x3, float y3, float z3, int color3, float ao3, float u3, float v3, int light3
    ) {
        try {
            return (int) WRITE_NATIVE_QUAD.invokeExact(ptr, blockEmission, renderType, ignoreMidBlock, blockId,
                    localX, localY, localZ, materialBits,
                    x0, y0, z0, color0, ao0, u0, v0, light0,
                    x1, y1, z1, color1, ao1, u1, v1, light1,
                    x2, y2, z2, color2, ao2, u2, v2, light2,
                    x3, y3, z3, color3, ao3, u3, v3, light3);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust native quad downcall failed", throwable);
        }
    }

    private static float invokeNativeQuadPosition(long ptr, int vertexIndex, int component) {
        try {
            return (float) NATIVE_QUAD_POSITION.invokeExact(ptr, vertexIndex, component);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust native quad position downcall failed", throwable);
        }
    }

    private static int invokeEncode(
            long inputAddress,
            int vertexCount,
            long outputAddress,
            int outputCapacity,
            int quadStride,
            int vertexStride,
            int blockIdOffset,
            int normalOffset,
            int tangentOffset,
            int midUvOffset,
            int midBlockOffset,
            int sectionIndex,
            int separateAo
    ) {
        try {
            return (int) ENCODE.invokeExact(inputAddress, vertexCount, outputAddress, outputCapacity, quadStride, vertexStride,
                    blockIdOffset, normalOffset, tangentOffset, midUvOffset, midBlockOffset, sectionIndex, separateAo);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust chunk vertex encoding downcall failed", throwable);
        }
    }

    private static int invokeScatteredEncode(
            long inputAddress,
            MemorySegment outputVertexOffsets,
            int updateCount,
            long outputAddress,
            int outputCapacity,
            int quadStride,
            int vertexStride,
            int blockIdOffset,
            int normalOffset,
            int tangentOffset,
            int midUvOffset,
            int midBlockOffset,
            int sectionIndex,
            int separateAo
    ) {
        try {
            return (int) SCATTERED_ENCODE.invokeExact(inputAddress, outputVertexOffsets, updateCount, outputAddress,
                    outputCapacity, quadStride, vertexStride, blockIdOffset, normalOffset, tangentOffset, midUvOffset,
                    midBlockOffset, sectionIndex, separateAo);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust scattered chunk vertex encoding downcall failed", throwable);
        }
    }

    private static int invokeAssembleOutput(
            MemorySegment inputAddresses,
            MemorySegment inputVertexCounts,
            int inputCount,
            long outputAddress,
            int outputCapacity,
            MemorySegment vertexSegments,
            int vertexSegmentsLength,
            int quadStride,
            int vertexStride,
            int blockIdOffset,
            int normalOffset,
            int tangentOffset,
            int midUvOffset,
            int midBlockOffset,
            int sectionIndex,
            int visibleSlices,
            int forceUnassigned,
            int sliceReordering,
            int separateAo,
            long indexOutputAddress,
            int indexOutputCapacity,
            int indexMode,
            int indexStride,
            MemorySegment indexValues,
            int indexValueCount
    ) {
        try {
            return (int) ASSEMBLE_OUTPUT.invokeExact(inputAddresses, inputVertexCounts, inputCount, outputAddress,
                    outputCapacity, vertexSegments, vertexSegmentsLength, quadStride, vertexStride, blockIdOffset,
                    normalOffset, tangentOffset, midUvOffset, midBlockOffset, sectionIndex, visibleSlices,
                    forceUnassigned, sliceReordering, separateAo, indexOutputAddress, indexOutputCapacity, indexMode,
                    indexStride, indexValues, indexValueCount);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust chunk mesh output assembly downcall failed", throwable);
        }
    }

    private static int invokeWriteShared(long outputAddress, int outputCapacity, int indexStride, int primitiveCount) {
        try {
            return (int) WRITE_SHARED.invokeExact(outputAddress, outputCapacity, indexStride, primitiveCount);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust shared quad index buffer downcall failed", throwable);
        }
    }

    private static int invokeWriteSorted(long outputAddress, int outputCapacity, MemorySegment quadIndexes, int quadIndexCount) {
        try {
            return (int) WRITE_SORTED.invokeExact(outputAddress, outputCapacity, quadIndexes, quadIndexCount);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust sorted quad index buffer downcall failed", throwable);
        }
    }

    private static int invokeWriteKeySorted(long outputAddress, int outputCapacity, MemorySegment keys, int keyCount) {
        try {
            return (int) WRITE_KEY_SORTED.invokeExact(outputAddress, outputCapacity, keys, keyCount);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust key-sorted quad index buffer downcall failed", throwable);
        }
    }
}
