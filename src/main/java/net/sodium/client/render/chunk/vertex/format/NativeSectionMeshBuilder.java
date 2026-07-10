package net.sodium.client.render.chunk.vertex.format;

import net.minecraft.util.NativeLibraryLoader;
import net.sodium.client.model.quad.properties.ModelQuadFacing;
import net.sodium.client.render.chunk.data.BuiltSectionMeshParts;
import net.sodium.client.render.chunk.translucent_sorting.bsp_tree.NativeUpdatedQuads;
import net.sodium.client.util.NativeBuffer;
import org.lwjgl.system.MemoryUtil;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Cleaner;
import java.nio.ByteBuffer;

public final class NativeSectionMeshBuilder implements AutoCloseable {
    private static final int OK = 0;
    private static final Cleaner CLEANER = Cleaner.create();

    private static final MethodHandle CREATE = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_section_mesh_builder_create",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS));
    private static final MethodHandle DESTROY = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_section_mesh_builder_destroy",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG));
    private static final MethodHandle START = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_section_mesh_builder_start",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG));
    private static final MethodHandle PREPARE_QUAD = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_section_mesh_builder_prepare_quad",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS));
    private static final MethodHandle COMMIT_QUAD = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_section_mesh_builder_commit_quad",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT));
    private static final MethodHandle APPEND_BATCH = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_section_mesh_builder_append_batch",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS));
    private static final MethodHandle APPEND_BATCH_FILTERED = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_section_mesh_builder_append_batch_filtered",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.ADDRESS));
    private static final MethodHandle APPEND_TRANSLUCENT_BATCH = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_section_mesh_builder_append_translucent_batch",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT));
    private static final MethodHandle STAGING_ADDRESSES = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_section_mesh_builder_staging_addresses",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS));
    private static final MethodHandle FACING_ADDRESS = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_section_mesh_builder_facing_address",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS));
    private static final MethodHandle FACING_VERTEX_COUNT = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_section_mesh_builder_facing_vertex_count",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS));
    private static final MethodHandle TOTAL_VERTEX_COUNT = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_section_mesh_builder_total_vertex_count",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.ADDRESS));
    private static final MethodHandle ASSEMBLE = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_section_mesh_builder_assemble",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
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
    private static final MethodHandle ENCODE_SCATTERED_UNASSIGNED = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_section_mesh_builder_encode_scattered_unassigned",
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

    private final State state;
    private final Cleaner.Cleanable cleanable;
    private int sectionIndex;

    private NativeSectionMeshBuilder(long handle) {
        this.state = new State(handle);
        this.cleanable = CLEANER.register(this, this.state);
    }

    public static NativeSectionMeshBuilder create(int initialQuadCapacity) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment handleSegment = arena.allocate(ValueLayout.JAVA_LONG);
            check(invokeCreate(initialQuadCapacity, handleSegment), "native section mesh builder creation");
            long handle = handleSegment.get(ValueLayout.JAVA_LONG, 0);
            if (handle == 0) {
                throw new IllegalStateException("Native section mesh builder creation returned a null handle");
            }
            return new NativeSectionMeshBuilder(handle);
        }
    }

    public void start(int sectionIndex) {
        check(invokeStart(this.state.getHandle()), "native section mesh builder start");
        this.sectionIndex = sectionIndex;
    }

    public long prepareQuadAddress(int facing) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment addressSegment = arena.allocate(ValueLayout.JAVA_LONG);
            check(invokePrepareQuad(this.state.getHandle(), facing, addressSegment),
                    "native section mesh builder quad preparation");
            return addressSegment.get(ValueLayout.JAVA_LONG, 0);
        }
    }

    public void commitQuad(int facing) {
        check(invokeCommitQuad(this.state.getHandle(), facing), "native section mesh builder quad commit");
    }

    public int appendBatch(int facing, long batchAddress, int quadCount) {
        if (quadCount < 0) {
            throw new IllegalArgumentException("Invalid quad count: " + quadCount);
        }
        if (quadCount == 0) {
            return 0;
        }

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment committedCountSegment = arena.allocate(ValueLayout.JAVA_INT);
            check(invokeAppendBatch(this.state.getHandle(), facing, batchAddress, quadCount, committedCountSegment),
                    "native section mesh builder batch append");
            return committedCountSegment.get(ValueLayout.JAVA_INT, 0);
        }
    }

    public int appendBatchFiltered(int facing, long batchAddress, int quadCount, long validityAddress) {
        if (quadCount < 0) {
            throw new IllegalArgumentException("Invalid quad count: " + quadCount);
        }
        if (quadCount == 0) {
            return 0;
        }

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment committedCountSegment = arena.allocate(ValueLayout.JAVA_INT);
            check(invokeAppendBatchFiltered(this.state.getHandle(), facing, batchAddress, quadCount,
                    validityAddress, committedCountSegment), "native section mesh builder filtered batch append");
            return committedCountSegment.get(ValueLayout.JAVA_INT, 0);
        }
    }

    public TranslucentBatchResult appendTranslucentBatch(int facing, long batchAddress, int quadCount,
            long analyzerHandle, int translucentFacing, long packedNormalsAddress) {
        if (quadCount < 0) {
            throw new IllegalArgumentException("Invalid quad count: " + quadCount);
        }
        if (quadCount == 0) {
            return new TranslucentBatchResult(0, 0);
        }

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment countsSegment = arena.allocate(ValueLayout.JAVA_INT, 2);
            check(invokeAppendTranslucentBatch(this.state.getHandle(), facing, batchAddress, quadCount,
                    analyzerHandle, translucentFacing, packedNormalsAddress, countsSegment, 2),
                    "native section mesh builder translucent batch append");
            return new TranslucentBatchResult(countsSegment.getAtIndex(ValueLayout.JAVA_INT, 0),
                    countsSegment.getAtIndex(ValueLayout.JAVA_INT, 1));
        }
    }

    public StagingBuffers stagingBuffers(int facing) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment quadAddressSegment = arena.allocate(ValueLayout.JAVA_LONG);
            MemorySegment packedNormalsAddressSegment = arena.allocate(ValueLayout.JAVA_LONG);
            MemorySegment validityAddressSegment = arena.allocate(ValueLayout.JAVA_LONG);
            MemorySegment capacitySegment = arena.allocate(ValueLayout.JAVA_INT);
            check(invokeStagingAddresses(this.state.getHandle(), facing, quadAddressSegment,
                    packedNormalsAddressSegment, validityAddressSegment, capacitySegment),
                    "native section mesh builder staging address query");
            return new StagingBuffers(
                    quadAddressSegment.get(ValueLayout.JAVA_LONG, 0),
                    packedNormalsAddressSegment.get(ValueLayout.JAVA_LONG, 0),
                    validityAddressSegment.get(ValueLayout.JAVA_LONG, 0),
                    capacitySegment.get(ValueLayout.JAVA_INT, 0));
        }
    }

    public long facingAddress(int facing) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment addressSegment = arena.allocate(ValueLayout.JAVA_LONG);
            check(invokeFacingAddress(this.state.getHandle(), facing, addressSegment),
                    "native section mesh builder facing address query");
            return addressSegment.get(ValueLayout.JAVA_LONG, 0);
        }
    }

    public int facingVertexCount(int facing) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment countSegment = arena.allocate(ValueLayout.JAVA_INT);
            check(invokeFacingVertexCount(this.state.getHandle(), facing, countSegment),
                    "native section mesh builder facing count query");
            return countSegment.get(ValueLayout.JAVA_INT, 0);
        }
    }

    public int totalVertexCount() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment countSegment = arena.allocate(ValueLayout.JAVA_INT);
            check(invokeTotalVertexCount(this.state.getHandle(), countSegment),
                    "native section mesh builder total count query");
            return countSegment.get(ValueLayout.JAVA_INT, 0);
        }
    }

    public void assemble(ByteBuffer output, int[] vertexSegments, NativeChunkVertexFormat format,
            int visibleSlices, boolean forceUnassigned, boolean sliceReordering, boolean separateAo) {
        if (vertexSegments.length != 14) {
            throw new IllegalArgumentException("Unexpected vertex segment array length: " + vertexSegments.length);
        }

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment vertexSegmentsSegment = arena.allocate(ValueLayout.JAVA_INT, vertexSegments.length);
            check(invokeAssemble(this.state.getHandle(), MemoryUtil.memAddress(output), output.remaining(),
                    vertexSegmentsSegment, vertexSegments.length, NativeChunkMeshEncoder.NATIVE_QUAD_STRIDE,
                    format.stride(), format.blockIdOffset(), format.normalOffset(), format.tangentOffset(),
                    format.midUvOffset(), format.midBlockOffset(), this.sectionIndex, visibleSlices,
                    forceUnassigned ? 1 : 0, sliceReordering ? 1 : 0, separateAo ? 1 : 0),
                    "native section mesh builder assembly");

            for (int index = 0; index < vertexSegments.length; index++) {
                vertexSegments[index] = vertexSegmentsSegment.getAtIndex(ValueLayout.JAVA_INT, index);
            }
        }
    }

    public BuiltSectionMeshParts finishMesh(NativeChunkVertexFormat format, int visibleSlices,
            boolean forceUnassigned, boolean sliceReordering, boolean separateAo) {
        int vertexTotal = this.totalVertexCount();
        if (vertexTotal == 0) {
            return null;
        }

        int[] vertexSegments = createVertexSegments();
        NativeBuffer mergedBuffer = new NativeBuffer(vertexTotal * format.stride());
        this.assemble(mergedBuffer.getDirectBuffer(), vertexSegments, format, visibleSlices, forceUnassigned,
                sliceReordering, separateAo);
        return new BuiltSectionMeshParts(mergedBuffer, vertexSegments);
    }

    public BuiltSectionMeshParts finishModifiedTranslucentMesh(NativeUpdatedQuads updatedQuads,
            NativeChunkVertexFormat format, boolean separateAo) {
        int vertexTotal = updatedQuads.getMeshQuadCount() * 4;
        NativeBuffer mergedBuffer = new NativeBuffer(vertexTotal * format.stride());
        ByteBuffer mergedBufferBuilder = mergedBuffer.getDirectBuffer();

        this.assemble(mergedBufferBuilder, createVertexSegments(), format, 0, true, false, separateAo);
        updatedQuads.applyBufferUpdates(format, this.sectionIndex, mergedBufferBuilder);

        int[] vertexSegments = createVertexSegments();
        int unassignedSegmentIndex = ModelQuadFacing.UNASSIGNED.ordinal() << 1;
        vertexSegments[unassignedSegmentIndex] = vertexTotal;
        vertexSegments[unassignedSegmentIndex + 1] = ModelQuadFacing.UNASSIGNED.ordinal();

        return new BuiltSectionMeshParts(mergedBuffer, vertexSegments);
    }

    public void encodeScatteredUnassigned(int[] outputVertexOffsets, int updateCount, ByteBuffer output,
            NativeChunkVertexFormat format, boolean separateAo) {
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

            check(invokeEncodeScatteredUnassigned(this.state.getHandle(), outputVertexOffsetsSegment, updateCount,
                    MemoryUtil.memAddress(output), output.remaining(), NativeChunkMeshEncoder.NATIVE_QUAD_STRIDE,
                    format.stride(), format.blockIdOffset(), format.normalOffset(), format.tangentOffset(),
                    format.midUvOffset(), format.midBlockOffset(), this.sectionIndex, separateAo ? 1 : 0),
                    "native section mesh builder scattered update encoding");
        }
    }

    public int sectionIndex() {
        return this.sectionIndex;
    }

    private static int[] createVertexSegments() {
        return new int[ModelQuadFacing.COUNT << 1];
    }

    @Override
    public void close() {
        this.cleanable.clean();
    }

    private static void check(int status, String operation) {
        if (status != OK) {
            throw new IllegalStateException(operation + " failed with native status " + status);
        }
    }

    private static int invokeCreate(int initialQuadCapacity, MemorySegment handleOutput) {
        try {
            return (int) CREATE.invokeExact(initialQuadCapacity, handleOutput);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust section mesh builder creation downcall failed", throwable);
        }
    }

    private static int invokeDestroy(long handle) {
        try {
            return (int) DESTROY.invokeExact(handle);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust section mesh builder destroy downcall failed", throwable);
        }
    }

    private static int invokeStart(long handle) {
        try {
            return (int) START.invokeExact(handle);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust section mesh builder start downcall failed", throwable);
        }
    }

    private static int invokePrepareQuad(long handle, int facing, MemorySegment addressOutput) {
        try {
            return (int) PREPARE_QUAD.invokeExact(handle, facing, addressOutput);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust section mesh builder prepare downcall failed", throwable);
        }
    }

    private static int invokeCommitQuad(long handle, int facing) {
        try {
            return (int) COMMIT_QUAD.invokeExact(handle, facing);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust section mesh builder commit downcall failed", throwable);
        }
    }

    private static int invokeAppendBatch(long handle, int facing, long batchAddress, int quadCount,
            MemorySegment committedCountOutput) {
        try {
            return (int) APPEND_BATCH.invokeExact(handle, facing, batchAddress, quadCount, committedCountOutput);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust section mesh builder batch append downcall failed", throwable);
        }
    }

    private static int invokeAppendBatchFiltered(long handle, int facing, long batchAddress, int quadCount,
            long validityAddress, MemorySegment committedCountOutput) {
        try {
            return (int) APPEND_BATCH_FILTERED.invokeExact(handle, facing, batchAddress, quadCount,
                    validityAddress, committedCountOutput);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust section mesh builder filtered batch append downcall failed",
                    throwable);
        }
    }

    private static int invokeAppendTranslucentBatch(long handle, int facing, long batchAddress, int quadCount,
            long analyzerHandle, int translucentFacing, long packedNormalsAddress, MemorySegment outputCounts,
            int outputCountsLength) {
        try {
            return (int) APPEND_TRANSLUCENT_BATCH.invokeExact(handle, facing, batchAddress, quadCount,
                    analyzerHandle, translucentFacing, packedNormalsAddress, outputCounts, outputCountsLength);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust section mesh builder translucent batch downcall failed", throwable);
        }
    }

    private static int invokeStagingAddresses(long handle, int facing, MemorySegment quadAddressOutput,
            MemorySegment packedNormalsAddressOutput, MemorySegment validityAddressOutput,
            MemorySegment capacityOutput) {
        try {
            return (int) STAGING_ADDRESSES.invokeExact(handle, facing, quadAddressOutput,
                    packedNormalsAddressOutput, validityAddressOutput, capacityOutput);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust section mesh builder staging address downcall failed", throwable);
        }
    }

    private static int invokeFacingAddress(long handle, int facing, MemorySegment addressOutput) {
        try {
            return (int) FACING_ADDRESS.invokeExact(handle, facing, addressOutput);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust section mesh builder address downcall failed", throwable);
        }
    }

    private static int invokeFacingVertexCount(long handle, int facing, MemorySegment countOutput) {
        try {
            return (int) FACING_VERTEX_COUNT.invokeExact(handle, facing, countOutput);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust section mesh builder count downcall failed", throwable);
        }
    }

    private static int invokeTotalVertexCount(long handle, MemorySegment countOutput) {
        try {
            return (int) TOTAL_VERTEX_COUNT.invokeExact(handle, countOutput);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust section mesh builder total count downcall failed", throwable);
        }
    }

    private static int invokeAssemble(long handle, long outputAddress, int outputCapacity,
            MemorySegment vertexSegments, int vertexSegmentsLength, int quadStride, int vertexStride,
            int blockIdOffset, int normalOffset, int tangentOffset, int midUvOffset, int midBlockOffset,
            int sectionIndex, int visibleSlices, int forceUnassigned, int sliceReordering, int separateAo) {
        try {
            return (int) ASSEMBLE.invokeExact(handle, outputAddress, outputCapacity, vertexSegments,
                    vertexSegmentsLength, quadStride, vertexStride, blockIdOffset, normalOffset, tangentOffset,
                    midUvOffset, midBlockOffset, sectionIndex, visibleSlices, forceUnassigned, sliceReordering,
                    separateAo);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust section mesh builder assembly downcall failed", throwable);
        }
    }

    private static int invokeEncodeScatteredUnassigned(long handle, MemorySegment outputVertexOffsets,
            int updateCount, long outputAddress, int outputCapacity, int quadStride, int vertexStride,
            int blockIdOffset, int normalOffset, int tangentOffset, int midUvOffset, int midBlockOffset,
            int sectionIndex, int separateAo) {
        try {
            return (int) ENCODE_SCATTERED_UNASSIGNED.invokeExact(handle, outputVertexOffsets, updateCount,
                    outputAddress, outputCapacity, quadStride, vertexStride, blockIdOffset, normalOffset,
                    tangentOffset, midUvOffset, midBlockOffset, sectionIndex, separateAo);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust section mesh builder scattered update downcall failed", throwable);
        }
    }

    public record StagingBuffers(long quadAddress, long packedNormalsAddress, long validityAddress, int capacity) {
    }

    public record TranslucentBatchResult(int validCount, int committedCount) {
    }

    private static final class State implements Runnable {
        private long handle;

        private State(long handle) {
            this.handle = handle;
        }

        private synchronized long getHandle() {
            if (this.handle == 0) {
                throw new IllegalStateException("Native section mesh builder has been closed");
            }
            return this.handle;
        }

        @Override
        public synchronized void run() {
            long handle = this.handle;
            if (handle == 0) {
                return;
            }

            check(invokeDestroy(handle), "native section mesh builder destroy");
            this.handle = 0;
        }
    }
}
