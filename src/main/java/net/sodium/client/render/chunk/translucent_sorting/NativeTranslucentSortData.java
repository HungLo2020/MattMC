package net.sodium.client.render.chunk.translucent_sorting;

import net.minecraft.util.NativeLibraryLoader;
import net.sodium.client.render.chunk.translucent_sorting.data.CombinedCameraPos;
import net.sodium.client.render.chunk.translucent_sorting.data.PresentSorter;
import net.sodium.client.render.chunk.translucent_sorting.data.Sorter;
import net.sodium.client.render.chunk.translucent_sorting.quad.TQuad;
import net.sodium.client.util.NativeBuffer;
import org.joml.Vector3fc;
import org.lwjgl.system.MemoryUtil;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Cleaner;

public final class NativeTranslucentSortData implements AutoCloseable {
    private static final int RECORD_STRIDE = 56;
    private static final int POSITION_FLOATS = 12;
    private static final int OFFSET_POSITIONS = 0;
    private static final int OFFSET_FACING = OFFSET_POSITIONS + POSITION_FLOATS * Float.BYTES;
    private static final int OFFSET_PACKED_NORMAL = OFFSET_FACING + Integer.BYTES;
    private static final int OK = 0;
    private static final int SORT_FAILED = 1;
    private static final Cleaner CLEANER = Cleaner.create();

    private static final MethodHandle CREATE_STATIC_TOPO = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_translucent_sort_data_static_topo_create",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS));
    private static final MethodHandle CREATE_STATIC_TOPO_FROM_ANALYZER = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_translucent_sort_data_static_topo_create_from_analyzer",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS));
    private static final MethodHandle CREATE_STATIC_SNR = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_translucent_sort_data_static_snr_create",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS));
    private static final MethodHandle CREATE_DYNAMIC_TOPO = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_translucent_sort_data_dynamic_topo_create",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS));
    private static final MethodHandle CREATE_DYNAMIC_TOPO_FROM_ANALYZER = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_translucent_sort_data_dynamic_topo_create_from_analyzer",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.ADDRESS));
    private static final MethodHandle DESTROY = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_translucent_sort_data_destroy",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG));
    private static final MethodHandle WRITE_STATIC = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_translucent_sort_data_static_write",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT));
    private static final MethodHandle WRITE_DYNAMIC = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_translucent_sort_data_dynamic_write",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_FLOAT,
                    ValueLayout.JAVA_FLOAT,
                    ValueLayout.JAVA_FLOAT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT));

    private final State state;
    private final Cleaner.Cleanable cleanable;
    private final int quadCount;

    private NativeTranslucentSortData(long handle, int quadCount) {
        this.state = new State(handle);
        this.cleanable = CLEANER.register(this, this.state);
        this.quadCount = quadCount;
    }

    public static NativeTranslucentSortData createStaticNormalRelative(int[] meshFacingCounts, int[] sortKeys,
            int quadCount, boolean doubleUnaligned) {
        if (quadCount < 0) {
            throw new IllegalArgumentException("Invalid translucent quad count: " + quadCount);
        }

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment meshFacingCountsSegment = arena.allocate(ValueLayout.JAVA_INT, meshFacingCounts.length);
            for (int index = 0; index < meshFacingCounts.length; index++) {
                meshFacingCountsSegment.setAtIndex(ValueLayout.JAVA_INT, index, meshFacingCounts[index]);
            }

            MemorySegment sortKeysSegment = sortKeys.length == 0
                    ? MemorySegment.NULL
                    : arena.allocate(ValueLayout.JAVA_INT, sortKeys.length);
            for (int index = 0; index < sortKeys.length; index++) {
                sortKeysSegment.setAtIndex(ValueLayout.JAVA_INT, index, sortKeys[index]);
            }

            MemorySegment handleSegment = arena.allocate(ValueLayout.JAVA_LONG);
            check(invokeCreateStaticSnr(meshFacingCountsSegment, meshFacingCounts.length, sortKeysSegment,
                    sortKeys.length, quadCount, doubleUnaligned ? 1 : 0, handleSegment),
                    "native translucent static normal-relative sort data creation");
            return fromHandleSegment(handleSegment, quadCount,
                    "Native translucent static normal-relative sort data creation");
        }
    }

    public static NativeTranslucentSortData createStaticTopo(TQuad[] quads, boolean failOnIntersection) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment recordsSegment = allocateRecords(arena, quads.length);
            MemorySegment handleSegment = arena.allocate(ValueLayout.JAVA_LONG);

            for (int index = 0; index < quads.length; index++) {
                TQuad quad = quads[index];
                writeRecord(recordsSegment, index, quad.getVertexPositions(), quad.getFacing().ordinal(),
                        quad.getPackedNormal());
            }

            int status = invokeCreateStaticTopo(recordsSegment, quads.length, failOnIntersection ? 1 : 0,
                    handleSegment);
            if (status == SORT_FAILED) {
                return null;
            }
            check(status, "native translucent static topo sort data creation");
            return fromHandleSegment(handleSegment, quads.length, "Native translucent static topo sort data creation");
        }
    }

    static NativeTranslucentSortData createStaticTopoFromAnalyzer(long analyzerHandle, int quadCount,
            boolean failOnIntersection) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment handleSegment = arena.allocate(ValueLayout.JAVA_LONG);
            int status = invokeCreateStaticTopoFromAnalyzer(analyzerHandle, failOnIntersection ? 1 : 0,
                    handleSegment);
            if (status == SORT_FAILED) {
                return null;
            }
            check(status, "native translucent analyzer static topo sort data creation");
            return fromHandleSegment(handleSegment, quadCount,
                    "Native translucent analyzer static topo sort data creation");
        }
    }

    public static NativeTranslucentSortData createDynamicTopo(TQuad[] quads) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment recordsSegment = allocateRecords(arena, quads.length);
            MemorySegment handleSegment = arena.allocate(ValueLayout.JAVA_LONG);

            for (int index = 0; index < quads.length; index++) {
                TQuad quad = quads[index];
                writeRecord(recordsSegment, index, quad.getVertexPositions(), quad.getFacing().ordinal(),
                        quad.getPackedNormal());
            }

            check(invokeCreateDynamicTopo(recordsSegment, quads.length, handleSegment),
                    "native translucent dynamic topo sort data creation");
            return fromHandleSegment(handleSegment, quads.length,
                    "Native translucent dynamic topo sort data creation");
        }
    }

    static NativeTranslucentSortData createDynamicTopoFromAnalyzer(long analyzerHandle, int quadCount) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment handleSegment = arena.allocate(ValueLayout.JAVA_LONG);
            check(invokeCreateDynamicTopoFromAnalyzer(analyzerHandle, handleSegment),
                    "native translucent analyzer dynamic topo sort data creation");
            return fromHandleSegment(handleSegment, quadCount,
                    "Native translucent analyzer dynamic topo sort data creation");
        }
    }

    public Sorter createStaticSorter() {
        return new NativeStaticSorter(this);
    }

    public DynamicSortResult writeDynamicSortedIndexBuffer(NativeBuffer indexBuffer, Vector3fc cameraPos,
            boolean initial, boolean directTriggerSort, boolean gfniTrigger, boolean directTrigger,
            int consecutiveTopoSortFailures) {
        return this.writeDynamicSortedIndexBuffer(MemoryUtil.memAddress(indexBuffer.getDirectBuffer()),
                indexBuffer.getLength(), cameraPos, initial, directTriggerSort, gfniTrigger, directTrigger,
                consecutiveTopoSortFailures);
    }

    DynamicSortResult writeDynamicSortedIndexBuffer(long outputAddress, int outputCapacity, Vector3fc cameraPos,
            boolean initial, boolean directTriggerSort, boolean gfniTrigger, boolean directTrigger,
            int consecutiveTopoSortFailures) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment stateSegment = arena.allocate(ValueLayout.JAVA_INT, 3);
            long handle = this.state.getHandle();
            check(invokeWriteDynamic(handle, outputAddress, outputCapacity, cameraPos.x(), cameraPos.y(),
                    cameraPos.z(), initial ? 1 : 0, directTriggerSort ? 1 : 0,
                    gfniTrigger ? 1 : 0, directTrigger ? 1 : 0, consecutiveTopoSortFailures, stateSegment, 3),
                    "native translucent sort data dynamic writing");

            return new DynamicSortResult(
                    stateSegment.getAtIndex(ValueLayout.JAVA_INT, 0) != 0,
                    stateSegment.getAtIndex(ValueLayout.JAVA_INT, 1) != 0,
                    stateSegment.getAtIndex(ValueLayout.JAVA_INT, 2));
        }
    }

    public int quadCount() {
        return this.quadCount;
    }

    @Override
    public void close() {
        this.cleanable.clean();
    }

    private void writeStaticIndexBuffer(NativeBuffer indexBuffer) {
        check(invokeWriteStatic(this.state.getHandle(), MemoryUtil.memAddress(indexBuffer.getDirectBuffer()),
                indexBuffer.getLength()), "native translucent sort data static writing");
    }

    private static NativeTranslucentSortData fromHandleSegment(MemorySegment handleSegment, int quadCount,
            String operation) {
        long handle = handleSegment.get(ValueLayout.JAVA_LONG, 0);
        if (handle == 0) {
            throw new IllegalStateException(operation + " returned a null handle");
        }

        return new NativeTranslucentSortData(handle, quadCount);
    }

    private static MemorySegment allocateRecords(Arena arena, int recordCount) {
        if (recordCount == 0) {
            return MemorySegment.NULL;
        }

        return arena.allocate((long) recordCount * RECORD_STRIDE, Integer.BYTES);
    }

    private static void writeRecord(MemorySegment recordsSegment, int recordIndex, float[] positions,
            int facingOrdinal, int packedNormal) {
        if (positions.length != POSITION_FLOATS) {
            throw new IllegalArgumentException("Expected " + POSITION_FLOATS + " quad position floats");
        }

        long recordOffset = (long) recordIndex * RECORD_STRIDE;
        for (int positionIndex = 0; positionIndex < POSITION_FLOATS; positionIndex++) {
            recordsSegment.set(ValueLayout.JAVA_FLOAT,
                    recordOffset + OFFSET_POSITIONS + (long) positionIndex * Float.BYTES,
                    positions[positionIndex]);
        }

        recordsSegment.set(ValueLayout.JAVA_INT, recordOffset + OFFSET_FACING, facingOrdinal);
        recordsSegment.set(ValueLayout.JAVA_INT, recordOffset + OFFSET_PACKED_NORMAL, packedNormal);
    }

    private static void check(int status, String operation) {
        if (status != OK) {
            throw new IllegalStateException(operation + " failed with native status " + status);
        }
    }

    private static int invokeCreateStaticTopo(MemorySegment records, int recordCount, int failOnIntersection,
            MemorySegment handleOutput) {
        try {
            return (int) CREATE_STATIC_TOPO.invokeExact(records, recordCount, failOnIntersection, handleOutput);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust translucent static topo sort data creation downcall failed",
                    throwable);
        }
    }

    private static int invokeCreateStaticTopoFromAnalyzer(long analyzerHandle, int failOnIntersection,
            MemorySegment handleOutput) {
        try {
            return (int) CREATE_STATIC_TOPO_FROM_ANALYZER.invokeExact(analyzerHandle, failOnIntersection,
                    handleOutput);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust translucent analyzer static topo sort data creation downcall failed",
                    throwable);
        }
    }

    private static int invokeCreateStaticSnr(MemorySegment meshFacingCounts, int meshFacingCountLen,
            MemorySegment sortKeys, int sortKeyLen, int quadCount, int doubleUnaligned, MemorySegment handleOutput) {
        try {
            return (int) CREATE_STATIC_SNR.invokeExact(meshFacingCounts, meshFacingCountLen, sortKeys, sortKeyLen,
                    quadCount, doubleUnaligned, handleOutput);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust translucent static SNR sort data creation downcall failed",
                    throwable);
        }
    }

    private static int invokeCreateDynamicTopo(MemorySegment records, int recordCount, MemorySegment handleOutput) {
        try {
            return (int) CREATE_DYNAMIC_TOPO.invokeExact(records, recordCount, handleOutput);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust translucent dynamic topo sort data creation downcall failed",
                    throwable);
        }
    }

    private static int invokeCreateDynamicTopoFromAnalyzer(long analyzerHandle, MemorySegment handleOutput) {
        try {
            return (int) CREATE_DYNAMIC_TOPO_FROM_ANALYZER.invokeExact(analyzerHandle, handleOutput);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust translucent analyzer dynamic topo sort data creation downcall failed",
                    throwable);
        }
    }

    private static int invokeDestroy(long handle) {
        try {
            return (int) DESTROY.invokeExact(handle);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust translucent sort data destroy downcall failed", throwable);
        }
    }

    private static int invokeWriteStatic(long handle, long outputAddress, int outputCapacity) {
        try {
            return (int) WRITE_STATIC.invokeExact(handle, outputAddress, outputCapacity);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust translucent static sort data write downcall failed", throwable);
        }
    }

    private static int invokeWriteDynamic(long handle, long outputAddress, int outputCapacity, float cameraX,
            float cameraY, float cameraZ, int initial, int directTriggerSort, int gfniTrigger, int directTrigger,
            int consecutiveTopoSortFailures, MemorySegment outputState, int outputStateLength) {
        try {
            return (int) WRITE_DYNAMIC.invokeExact(handle, outputAddress, outputCapacity, cameraX, cameraY, cameraZ,
                    initial, directTriggerSort, gfniTrigger, directTrigger, consecutiveTopoSortFailures,
                    outputState, outputStateLength);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust translucent dynamic sort data write downcall failed", throwable);
        }
    }

    public record DynamicSortResult(boolean gfniTrigger, boolean directTrigger, int consecutiveTopoSortFailures) {
    }

    private static final class NativeStaticSorter extends PresentSorter {
        private final NativeTranslucentSortData sortData;

        private NativeStaticSorter(NativeTranslucentSortData sortData) {
            this.sortData = sortData;
        }

        @Override
        public void writeIndexBuffer(CombinedCameraPos cameraPos, boolean initial) {
            this.initBufferWithQuadLength(this.sortData.quadCount());
            this.sortData.writeStaticIndexBuffer(this.getIndexBuffer());
        }

        @Override
        public void destroy() {
            try {
                this.sortData.close();
            } finally {
                super.destroy();
            }
        }
    }

    private static final class State implements Runnable {
        private long handle;

        private State(long handle) {
            this.handle = handle;
        }

        private synchronized long getHandle() {
            if (this.handle == 0) {
                throw new IllegalStateException("Native translucent sort data has been closed");
            }
            return this.handle;
        }

        @Override
        public synchronized void run() {
            long handle = this.handle;
            if (handle == 0) {
                return;
            }

            check(invokeDestroy(handle), "native translucent sort data destroy");
            this.handle = 0;
        }
    }
}
