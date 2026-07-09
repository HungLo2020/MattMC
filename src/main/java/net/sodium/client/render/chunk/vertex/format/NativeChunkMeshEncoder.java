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
    private static final int INDEX_MODE_NONE = 0;
    private static final int INDEX_MODE_SHARED = 1;
    private static final int INDEX_MODE_SORTED_QUADS = 2;
    private static final int INDEX_MODE_KEY_SORTED = 3;
    private static final MethodHandle VERIFY = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_chunk_mesh_verify",
            FunctionDescriptor.of(ValueLayout.JAVA_INT));
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
        if (keys.length == 0) {
            return;
        }

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment keysSegment = arena.allocate(ValueLayout.JAVA_INT, keys.length);

            for (int index = 0; index < keys.length; index++) {
                keysSegment.setAtIndex(ValueLayout.JAVA_INT, index, keys[index]);
            }

            check(invokeWriteKeySorted(MemoryUtil.memAddress(output), output.remaining(), keysSegment, keys.length),
                    "native key-sorted quad index buffer writing");
            output.position(output.position() + keys.length * 6);
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
