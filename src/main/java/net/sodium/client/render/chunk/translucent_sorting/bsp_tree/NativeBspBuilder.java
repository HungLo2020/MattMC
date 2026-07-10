package net.sodium.client.render.chunk.translucent_sorting.bsp_tree;

import net.minecraft.util.NativeLibraryLoader;
import net.sodium.client.render.chunk.translucent_sorting.QuadSplittingMode;
import net.sodium.client.render.chunk.translucent_sorting.quad.NativeFullTQuad;
import net.sodium.client.render.chunk.translucent_sorting.quad.TQuad;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

final class NativeBspBuilder {
    private static final int OK = 0;
    private static final int SORT_FAILED = 1;
    private static final int POSITION_FLOATS = 12;
    private static final int EXTENT_FLOATS = 6;
    private static final int RECORD_STRIDE = 84;
    private static final int OFFSET_POSITIONS = 0;
    private static final int OFFSET_EXTENTS = OFFSET_POSITIONS + POSITION_FLOATS * Float.BYTES;
    private static final int OFFSET_ACCURATE_DOT_PRODUCT = OFFSET_EXTENTS + EXTENT_FLOATS * Float.BYTES;
    private static final int OFFSET_FACING = OFFSET_ACCURATE_DOT_PRODUCT + Float.BYTES;
    private static final int OFFSET_PACKED_NORMAL = OFFSET_FACING + Integer.BYTES;

    private static final MethodHandle BUILD_RECORDS = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_translucent_bsp_build_records",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS));
    private static final MethodHandle BUILD_FULL_QUADS = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_translucent_bsp_build_full_quads",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS));

    private NativeBspBuilder() {
    }

    static BuiltTree build(TQuad[] quads, NativeBspBuildResult result, BSPNode oldRoot,
            boolean prepareNodeReuse, QuadSplittingMode quadSplittingMode) {
        if (quadSplittingMode.allowsSplitting()) {
            return buildFullQuads(quads, result, oldRoot, prepareNodeReuse, quadSplittingMode);
        }
        return buildRecords(quads, result, oldRoot, prepareNodeReuse);
    }

    private static BuiltTree buildRecords(TQuad[] quads, NativeBspBuildResult result, BSPNode oldRoot,
            boolean prepareNodeReuse) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment records = quads.length == 0
                    ? MemorySegment.NULL
                    : arena.allocate((long) quads.length * RECORD_STRIDE, Integer.BYTES);
            for (int index = 0; index < quads.length; index++) {
                writeRecord(records.asSlice((long) index * RECORD_STRIDE), quads[index]);
            }

            MemorySegment treeHandle = arena.allocate(ValueLayout.JAVA_LONG);
            MemorySegment indexQuadCount = arena.allocate(ValueLayout.JAVA_INT);
            MemorySegment reusableRootHandle = arena.allocate(ValueLayout.JAVA_LONG);
            int status = invokeBuildRecords(records, quads.length, result.nativeHandle(),
                    oldRoot == null ? 0 : oldRoot.nativeHandle(), prepareNodeReuse ? 1 : 0,
                    treeHandle, indexQuadCount, reusableRootHandle);
            if (status == SORT_FAILED) {
                throw new BSPBuildFailureException("Native BSP builder could not partition or topo-sort the geometry");
            }
            check(status, "native BSP tree construction");

            long handle = treeHandle.get(ValueLayout.JAVA_LONG, 0);
            if (handle == 0) {
                throw new IllegalStateException("Native BSP builder returned a null tree handle");
            }
            return new BuiltTree(handle, indexQuadCount.get(ValueLayout.JAVA_INT, 0), null,
                    reusableRootHandle.get(ValueLayout.JAVA_LONG, 0));
        }
    }

    private static BuiltTree buildFullQuads(TQuad[] quads, NativeBspBuildResult result,
            BSPNode oldRoot, boolean prepareNodeReuse, QuadSplittingMode quadSplittingMode) {
        NativeFullTQuad[] fullQuads = new NativeFullTQuad[quads.length];
        for (int index = 0; index < quads.length; index++) {
            if (!(quads[index] instanceof NativeFullTQuad fullQuad)) {
                throw new IllegalArgumentException("Quad splitting BSP builds require native full translucent quads");
            }
            fullQuads[index] = fullQuad;
        }

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment handles = fullQuads.length == 0
                    ? MemorySegment.NULL
                    : arena.allocate(ValueLayout.JAVA_LONG, fullQuads.length);
            for (int index = 0; index < fullQuads.length; index++) {
                handles.setAtIndex(ValueLayout.JAVA_LONG, index, fullQuads[index].nativeHandle());
            }

            MemorySegment treeHandle = arena.allocate(ValueLayout.JAVA_LONG);
            MemorySegment indexQuadCount = arena.allocate(ValueLayout.JAVA_INT);
            MemorySegment updatedQuadsHandle = arena.allocate(ValueLayout.JAVA_LONG);
            MemorySegment reusableRootHandle = arena.allocate(ValueLayout.JAVA_LONG);
            int status = invokeBuildFullQuads(handles, fullQuads.length, result.nativeHandle(),
                    quadSplittingMode.getMaxTotalQuads(fullQuads.length),
                    quadSplittingMode.quantizeTriggerNormals() ? 1 : 0,
                    oldRoot == null ? 0 : oldRoot.nativeHandle(), prepareNodeReuse ? 1 : 0,
                    treeHandle, indexQuadCount, updatedQuadsHandle, reusableRootHandle);
            if (status == SORT_FAILED) {
                throw new BSPBuildFailureException("Native BSP builder could not partition or topo-sort the geometry");
            }
            check(status, "native full-quad BSP tree construction");

            long handle = treeHandle.get(ValueLayout.JAVA_LONG, 0);
            if (handle == 0) {
                throw new IllegalStateException("Native full-quad BSP builder returned a null tree handle");
            }
            long updatedHandle = updatedQuadsHandle.get(ValueLayout.JAVA_LONG, 0);
            NativeUpdatedQuads updatedQuads = updatedHandle == 0
                    ? null
                    : NativeUpdatedQuads.fromHandle(updatedHandle, fullQuads);
            return new BuiltTree(handle, indexQuadCount.get(ValueLayout.JAVA_INT, 0), updatedQuads,
                    reusableRootHandle.get(ValueLayout.JAVA_LONG, 0));
        }
    }

    private static void writeRecord(MemorySegment record, TQuad quad) {
        float[] positions = quad.getVertexPositions();
        for (int index = 0; index < POSITION_FLOATS; index++) {
            record.set(ValueLayout.JAVA_FLOAT, OFFSET_POSITIONS + (long) index * Float.BYTES, positions[index]);
        }

        float[] extents = quad.getExtents();
        for (int index = 0; index < EXTENT_FLOATS; index++) {
            record.set(ValueLayout.JAVA_FLOAT, OFFSET_EXTENTS + (long) index * Float.BYTES, extents[index]);
        }

        record.set(ValueLayout.JAVA_FLOAT, OFFSET_ACCURATE_DOT_PRODUCT, quad.getAccurateDotProduct());
        record.set(ValueLayout.JAVA_INT, OFFSET_FACING, quad.getFacing().ordinal());
        record.set(ValueLayout.JAVA_INT, OFFSET_PACKED_NORMAL, quad.getPackedNormal());
    }

    private static void check(int status, String operation) {
        if (status != OK) {
            throw new IllegalStateException(operation + " failed with native status " + status);
        }
    }

    private static int invokeBuildRecords(MemorySegment records, int recordCount, long resultHandle,
            long oldRootHandle, int prepareNodeReuse, MemorySegment treeHandle, MemorySegment indexQuadCount,
            MemorySegment reusableRootHandle) {
        try {
            return (int) BUILD_RECORDS.invokeExact(records, recordCount, resultHandle, oldRootHandle,
                    prepareNodeReuse, treeHandle, indexQuadCount, reusableRootHandle);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust BSP tree construction downcall failed", throwable);
        }
    }

    private static int invokeBuildFullQuads(MemorySegment handles, int handleCount, long resultHandle,
            int maxQuadCount, int quantizeTriggerNormals, long oldRootHandle, int prepareNodeReuse,
            MemorySegment treeHandle, MemorySegment indexQuadCount, MemorySegment updatedQuadsHandle,
            MemorySegment reusableRootHandle) {
        try {
            return (int) BUILD_FULL_QUADS.invokeExact(handles, handleCount, resultHandle, maxQuadCount,
                    quantizeTriggerNormals, oldRootHandle, prepareNodeReuse, treeHandle, indexQuadCount,
                    updatedQuadsHandle, reusableRootHandle);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust full-quad BSP tree construction downcall failed", throwable);
        }
    }

    record BuiltTree(long handle, int indexQuadCount, NativeUpdatedQuads updatedQuads, long reusableRootHandle) {
    }
}
