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

public final class NativeChunkMeshEncoder {
    public static final int NATIVE_QUAD_STRIDE = 152;

    private static final int OK = 0;
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
    private static final MethodHandle ASSEMBLE = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_chunk_mesh_assemble",
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
        if (inputAddresses.length != ModelQuadFacing.COUNT || inputVertexCounts.length != ModelQuadFacing.COUNT) {
            throw new IllegalArgumentException("Expected one input buffer per chunk quad facing");
        }

        if (vertexSegments.length != ModelQuadFacing.COUNT << 1) {
            throw new IllegalArgumentException("Unexpected vertex segment array length: " + vertexSegments.length);
        }

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment inputAddressesSegment = arena.allocate(ValueLayout.JAVA_LONG, inputAddresses.length);
            MemorySegment inputVertexCountsSegment = arena.allocate(ValueLayout.JAVA_INT, inputVertexCounts.length);
            MemorySegment vertexSegmentsSegment = arena.allocate(ValueLayout.JAVA_INT, vertexSegments.length);

            for (int index = 0; index < inputAddresses.length; index++) {
                inputAddressesSegment.setAtIndex(ValueLayout.JAVA_LONG, index, inputAddresses[index]);
                inputVertexCountsSegment.setAtIndex(ValueLayout.JAVA_INT, index, inputVertexCounts[index]);
            }

            check(invokeAssemble(
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
                    separateAo ? 1 : 0
            ), "native chunk mesh assembly");

            for (int index = 0; index < vertexSegments.length; index++) {
                vertexSegments[index] = vertexSegmentsSegment.getAtIndex(ValueLayout.JAVA_INT, index);
            }
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

    private static int invokeAssemble(
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
            int separateAo
    ) {
        try {
            return (int) ASSEMBLE.invokeExact(inputAddresses, inputVertexCounts, inputCount, outputAddress, outputCapacity,
                    vertexSegments, vertexSegmentsLength, quadStride, vertexStride, blockIdOffset, normalOffset, tangentOffset,
                    midUvOffset, midBlockOffset, sectionIndex, visibleSlices, forceUnassigned, sliceReordering, separateAo);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust chunk mesh assembly downcall failed", throwable);
        }
    }
}
