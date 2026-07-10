package net.sodium.client.render.chunk.translucent_sorting.bsp_tree;

import net.minecraft.util.NativeLibraryLoader;
import net.sodium.client.util.NativeBuffer;
import org.joml.Vector3fc;
import org.lwjgl.system.MemoryUtil;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Cleaner;

public final class NativeBspTree implements AutoCloseable {
    static final int NULL_NODE = -1;

    private static final int OK = 0;
    private static final int REMAP_NONE = 0;
    private static final int REMAP_FIXED_OFFSET = 1;
    private static final int REMAP_INDEX_MAP = 2;
    private static final Cleaner CLEANER = Cleaner.create();

    private static final MethodHandle CREATE = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_translucent_bsp_tree_create",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
    private static final MethodHandle DESTROY = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_translucent_bsp_tree_destroy",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG));
    private static final MethodHandle SET_ROOT = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_translucent_bsp_tree_set_root",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT));
    private static final MethodHandle ADD_LEAF_SINGLE = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_translucent_bsp_tree_add_leaf_single",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS));
    private static final MethodHandle ADD_LEAF_DOUBLE = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_translucent_bsp_tree_add_leaf_double",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS));
    private static final MethodHandle ADD_LEAF_MULTI = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_translucent_bsp_tree_add_leaf_multi",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS));
    private static final MethodHandle ADD_FIXED_DOUBLE = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_translucent_bsp_tree_add_fixed_double",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS));
    private static final MethodHandle ADD_BINARY = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_translucent_bsp_tree_add_binary",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_FLOAT,
                    ValueLayout.JAVA_FLOAT,
                    ValueLayout.JAVA_FLOAT,
                    ValueLayout.JAVA_FLOAT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS));
    private static final MethodHandle ADD_MULTI_PARTITION = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_translucent_bsp_tree_add_multi_partition",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_FLOAT,
                    ValueLayout.JAVA_FLOAT,
                    ValueLayout.JAVA_FLOAT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS));
    private static final MethodHandle WRITE_INDEX_BUFFER = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_translucent_bsp_tree_write_index_buffer",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_FLOAT,
                    ValueLayout.JAVA_FLOAT,
                    ValueLayout.JAVA_FLOAT));

    private final State state;
    private final Cleaner.Cleanable cleanable;

    private NativeBspTree(long handle) {
        this.state = new State(handle);
        this.cleanable = CLEANER.register(this, this.state);
    }

    static NativeBspTree fromHandle(long handle) {
        if (handle == 0) {
            throw new IllegalArgumentException("Native BSP tree handle must not be null");
        }
        return new NativeBspTree(handle);
    }

    public void writeIndexBuffer(NativeBuffer indexBuffer, Vector3fc cameraPos) {
        check(invokeWriteIndexBuffer(this.state.getHandle(), MemoryUtil.memAddress(indexBuffer.getDirectBuffer()),
                indexBuffer.getLength(), cameraPos.x(), cameraPos.y(), cameraPos.z()),
                "native BSP tree index buffer writing");
    }

    @Override
    public void close() {
        this.cleanable.clean();
    }

    record Remap(int kind, int indexCount, int fixedIndexOffset, int[] indexMap) {
        static final Remap NONE = new Remap(REMAP_NONE, 0, 0, null);
    }

    static final class Builder implements AutoCloseable {
        private long handle;
        private boolean finished;

        private Builder(long handle) {
            this.handle = handle;
        }

        static Builder create() {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment handleSegment = arena.allocate(ValueLayout.JAVA_LONG);
                check(invokeCreate(handleSegment), "native BSP tree creation");
                long handle = handleSegment.get(ValueLayout.JAVA_LONG, 0);
                if (handle == 0) {
                    throw new IllegalStateException("Native BSP tree creation returned a null handle");
                }

                return new Builder(handle);
            }
        }

        int addLeafSingle(int quad) {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment output = arena.allocate(ValueLayout.JAVA_INT);
                check(invokeAddLeafSingle(this.requireHandle(), quad, output), "native BSP single leaf addition");
                return output.get(ValueLayout.JAVA_INT, 0);
            }
        }

        int addLeafDouble(int quadA, int quadB) {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment output = arena.allocate(ValueLayout.JAVA_INT);
                check(invokeAddLeafDouble(this.requireHandle(), quadA, quadB, output),
                        "native BSP double leaf addition");
                return output.get(ValueLayout.JAVA_INT, 0);
            }
        }

        int addLeafMulti(int[] quads) {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment indexes = allocateInts(arena, quads);
                MemorySegment output = arena.allocate(ValueLayout.JAVA_INT);
                check(invokeAddLeafMulti(this.requireHandle(), indexes, quads.length, output),
                        "native BSP multi leaf addition");
                return output.get(ValueLayout.JAVA_INT, 0);
            }
        }

        int addFixedDouble(Remap remap, int first, int second) {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment indexMap = allocateInts(arena, remap.indexMap());
                MemorySegment output = arena.allocate(ValueLayout.JAVA_INT);
                check(invokeAddFixedDouble(this.requireHandle(), remap.kind(), remap.indexCount(),
                        remap.fixedIndexOffset(), indexMap, length(remap.indexMap()), first, second, output),
                        "native BSP fixed double node addition");
                return output.get(ValueLayout.JAVA_INT, 0);
            }
        }

        int addBinary(Remap remap, Vector3fc normal, float distance, int inside, int outside, int[] onPlane) {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment indexMap = allocateInts(arena, remap.indexMap());
                MemorySegment onPlaneIndexes = allocateInts(arena, onPlane);
                MemorySegment output = arena.allocate(ValueLayout.JAVA_INT);
                check(invokeAddBinary(this.requireHandle(), remap.kind(), remap.indexCount(),
                        remap.fixedIndexOffset(), indexMap, length(remap.indexMap()),
                        normal.x(), normal.y(), normal.z(), distance, inside, outside,
                        onPlaneIndexes, length(onPlane), output), "native BSP binary node addition");
                return output.get(ValueLayout.JAVA_INT, 0);
            }
        }

        int addMultiPartition(Remap remap, Vector3fc normal, float[] distances, int[] partitions,
                int[][] onPlaneQuads) {
            if (onPlaneQuads.length != distances.length) {
                throw new IllegalArgumentException("Expected one on-plane index list per partition plane");
            }

            try (Arena arena = Arena.ofConfined()) {
                int totalOnPlaneIndexes = 0;
                int[] onPlaneCounts = new int[onPlaneQuads.length];
                for (int index = 0; index < onPlaneQuads.length; index++) {
                    int count = length(onPlaneQuads[index]);
                    onPlaneCounts[index] = count;
                    totalOnPlaneIndexes += count;
                }

                int[] flattenedOnPlane = new int[totalOnPlaneIndexes];
                int writeOffset = 0;
                for (int[] planeQuads : onPlaneQuads) {
                    if (planeQuads != null) {
                        System.arraycopy(planeQuads, 0, flattenedOnPlane, writeOffset, planeQuads.length);
                        writeOffset += planeQuads.length;
                    }
                }

                MemorySegment indexMap = allocateInts(arena, remap.indexMap());
                MemorySegment planeDistances = allocateFloats(arena, distances);
                MemorySegment partitionIndexes = allocateInts(arena, partitions);
                MemorySegment onPlaneIndexes = allocateInts(arena, flattenedOnPlane);
                MemorySegment counts = allocateInts(arena, onPlaneCounts);
                MemorySegment output = arena.allocate(ValueLayout.JAVA_INT);
                check(invokeAddMultiPartition(this.requireHandle(), remap.kind(), remap.indexCount(),
                        remap.fixedIndexOffset(), indexMap, length(remap.indexMap()),
                        normal.x(), normal.y(), normal.z(), planeDistances, distances.length,
                        partitionIndexes, partitions.length, onPlaneIndexes, flattenedOnPlane.length,
                        counts, onPlaneCounts.length, output), "native BSP multi partition node addition");
                return output.get(ValueLayout.JAVA_INT, 0);
            }
        }

        NativeBspTree finish(int rootIndex, int indexQuadCount) {
            return NativeBspTree.fromHandle(this.finishHandle(rootIndex, indexQuadCount));
        }

        long finishHandle(int rootIndex, int indexQuadCount) {
            long handle = this.requireHandle();
            check(invokeSetRoot(handle, rootIndex, indexQuadCount), "native BSP tree root assignment");
            this.finished = true;
            this.handle = 0;
            return handle;
        }

        @Override
        public void close() {
            if (!this.finished && this.handle != 0) {
                check(invokeDestroy(this.handle), "native BSP tree destroy");
                this.handle = 0;
            }
        }

        private long requireHandle() {
            if (this.handle == 0) {
                throw new IllegalStateException("Native BSP tree builder has been closed");
            }
            return this.handle;
        }
    }

    private static MemorySegment allocateInts(Arena arena, int[] values) {
        if (values == null || values.length == 0) {
            return MemorySegment.NULL;
        }

        MemorySegment segment = arena.allocate(ValueLayout.JAVA_INT, values.length);
        for (int index = 0; index < values.length; index++) {
            segment.setAtIndex(ValueLayout.JAVA_INT, index, values[index]);
        }
        return segment;
    }

    private static MemorySegment allocateFloats(Arena arena, float[] values) {
        if (values == null || values.length == 0) {
            return MemorySegment.NULL;
        }

        MemorySegment segment = arena.allocate(ValueLayout.JAVA_FLOAT, values.length);
        for (int index = 0; index < values.length; index++) {
            segment.setAtIndex(ValueLayout.JAVA_FLOAT, index, values[index]);
        }
        return segment;
    }

    private static int length(int[] values) {
        return values == null ? 0 : values.length;
    }

    private static void check(int status, String operation) {
        if (status != OK) {
            throw new IllegalStateException(operation + " failed with native status " + status);
        }
    }

    private static int invokeCreate(MemorySegment outputHandle) {
        try {
            return (int) CREATE.invokeExact(outputHandle);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust BSP tree creation downcall failed", throwable);
        }
    }

    private static int invokeDestroy(long handle) {
        try {
            return (int) DESTROY.invokeExact(handle);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust BSP tree destroy downcall failed", throwable);
        }
    }

    static void destroyHandle(long handle) {
        check(invokeDestroy(handle), "native BSP tree destroy");
    }

    private static int invokeSetRoot(long handle, int rootIndex, int indexQuadCount) {
        try {
            return (int) SET_ROOT.invokeExact(handle, rootIndex, indexQuadCount);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust BSP tree root downcall failed", throwable);
        }
    }

    private static int invokeAddLeafSingle(long handle, int quad, MemorySegment outputNode) {
        try {
            return (int) ADD_LEAF_SINGLE.invokeExact(handle, quad, outputNode);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust BSP tree single leaf downcall failed", throwable);
        }
    }

    private static int invokeAddLeafDouble(long handle, int quadA, int quadB, MemorySegment outputNode) {
        try {
            return (int) ADD_LEAF_DOUBLE.invokeExact(handle, quadA, quadB, outputNode);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust BSP tree double leaf downcall failed", throwable);
        }
    }

    private static int invokeAddLeafMulti(long handle, MemorySegment indexes, int indexCount,
            MemorySegment outputNode) {
        try {
            return (int) ADD_LEAF_MULTI.invokeExact(handle, indexes, indexCount, outputNode);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust BSP tree multi leaf downcall failed", throwable);
        }
    }

    private static int invokeAddFixedDouble(long handle, int remapKind, int remapIndexCount,
            int fixedOffset, MemorySegment indexMap, int indexMapLength, int first, int second,
            MemorySegment outputNode) {
        try {
            return (int) ADD_FIXED_DOUBLE.invokeExact(handle, remapKind, remapIndexCount, fixedOffset,
                    indexMap, indexMapLength, first, second, outputNode);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust BSP tree fixed double downcall failed", throwable);
        }
    }

    private static int invokeAddBinary(long handle, int remapKind, int remapIndexCount,
            int fixedOffset, MemorySegment indexMap, int indexMapLength, float normalX, float normalY,
            float normalZ, float distance, int inside, int outside, MemorySegment onPlane, int onPlaneLength,
            MemorySegment outputNode) {
        try {
            return (int) ADD_BINARY.invokeExact(handle, remapKind, remapIndexCount, fixedOffset,
                    indexMap, indexMapLength, normalX, normalY, normalZ, distance, inside, outside,
                    onPlane, onPlaneLength, outputNode);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust BSP tree binary downcall failed", throwable);
        }
    }

    private static int invokeAddMultiPartition(long handle, int remapKind, int remapIndexCount,
            int fixedOffset, MemorySegment indexMap, int indexMapLength, float normalX, float normalY,
            float normalZ, MemorySegment distances, int distanceCount, MemorySegment partitions,
            int partitionCount, MemorySegment onPlaneIndexes, int onPlaneIndexCount, MemorySegment onPlaneCounts,
            int onPlaneCountCount, MemorySegment outputNode) {
        try {
            return (int) ADD_MULTI_PARTITION.invokeExact(handle, remapKind, remapIndexCount, fixedOffset,
                    indexMap, indexMapLength, normalX, normalY, normalZ, distances, distanceCount,
                    partitions, partitionCount, onPlaneIndexes, onPlaneIndexCount, onPlaneCounts,
                    onPlaneCountCount, outputNode);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust BSP tree multi partition downcall failed", throwable);
        }
    }

    private static int invokeWriteIndexBuffer(long handle, long outputAddress, int outputCapacity,
            float cameraX, float cameraY, float cameraZ) {
        try {
            return (int) WRITE_INDEX_BUFFER.invokeExact(handle, outputAddress, outputCapacity,
                    cameraX, cameraY, cameraZ);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust BSP tree write downcall failed", throwable);
        }
    }

    private static final class State implements Runnable {
        private long handle;

        private State(long handle) {
            this.handle = handle;
        }

        private synchronized long getHandle() {
            if (this.handle == 0) {
                throw new IllegalStateException("Native BSP tree has been closed");
            }
            return this.handle;
        }

        @Override
        public synchronized void run() {
            long handle = this.handle;
            if (handle == 0) {
                return;
            }

            check(invokeDestroy(handle), "native BSP tree destroy");
            this.handle = 0;
        }
    }
}
