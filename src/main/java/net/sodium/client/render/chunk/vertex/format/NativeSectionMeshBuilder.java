package net.sodium.client.render.chunk.vertex.format;

import net.minecraft.util.NativeLibraryLoader;
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

    public int sectionIndex() {
        return this.sectionIndex;
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
