package net.sodium.client.render.chunk.translucent_sorting;

import net.minecraft.util.NativeLibraryLoader;
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

public final class NativeTranslucentSectionGeometry implements AutoCloseable {
    private static final int RECORD_STRIDE = 56;
    private static final int POSITION_FLOATS = 12;
    private static final int OFFSET_POSITIONS = 0;
    private static final int OFFSET_FACING = OFFSET_POSITIONS + POSITION_FLOATS * Float.BYTES;
    private static final int OFFSET_PACKED_NORMAL = OFFSET_FACING + Integer.BYTES;
    private static final int OK = 0;
    private static final Cleaner CLEANER = Cleaner.create();

    private static final MethodHandle CREATE = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_translucent_section_geometry_create",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS));
    private static final MethodHandle DESTROY = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_translucent_section_geometry_destroy",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG));
    private static final MethodHandle WRITE_DISTANCE_SORT = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_translucent_section_geometry_distance_sort_write",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_FLOAT,
                    ValueLayout.JAVA_FLOAT,
                    ValueLayout.JAVA_FLOAT));
    private static final MethodHandle WRITE_DYNAMIC_SORT = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_translucent_section_geometry_dynamic_sort_write",
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

    private NativeTranslucentSectionGeometry(long handle, int quadCount) {
        this.state = new State(handle);
        this.cleanable = CLEANER.register(this, this.state);
        this.quadCount = quadCount;
    }

    static NativeTranslucentSectionGeometry create(MemorySegment records, int recordCount, MemorySegment handleOutput) {
        check(invokeCreate(records, recordCount, handleOutput), "native translucent section geometry creation");
        long handle = handleOutput.get(ValueLayout.JAVA_LONG, 0);
        if (handle == 0) {
            throw new IllegalStateException("Native translucent section geometry creation returned a null handle");
        }
        return new NativeTranslucentSectionGeometry(handle, recordCount);
    }

    static NativeTranslucentSectionGeometry create(TQuad[] quads) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment recordsSegment = allocateRecords(arena, quads.length);
            MemorySegment handleSegment = arena.allocate(ValueLayout.JAVA_LONG);

            for (int index = 0; index < quads.length; index++) {
                TQuad quad = quads[index];
                writeRecord(recordsSegment, index, quad.getVertexPositions(), quad.getFacing().ordinal(),
                        quad.getPackedNormal());
            }

            return create(recordsSegment, quads.length, handleSegment);
        }
    }

    public void writeDistanceSortedIndexBuffer(NativeBuffer indexBuffer, Vector3fc cameraPos) {
        this.writeDistanceSortedIndexBuffer(MemoryUtil.memAddress(indexBuffer.getDirectBuffer()),
                indexBuffer.getLength(), cameraPos);
    }

    void writeDistanceSortedIndexBuffer(long outputAddress, int outputCapacity, Vector3fc cameraPos) {
        long handle = this.state.getHandle();
        check(invokeWriteDistanceSort(handle, outputAddress, outputCapacity, cameraPos.x(), cameraPos.y(),
                cameraPos.z()),
                "native translucent distance sort writing");
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
            check(invokeWriteDynamicSort(handle, outputAddress, outputCapacity, cameraPos.x(), cameraPos.y(),
                    cameraPos.z(), initial ? 1 : 0, directTriggerSort ? 1 : 0,
                    gfniTrigger ? 1 : 0, directTrigger ? 1 : 0, consecutiveTopoSortFailures, stateSegment, 3),
                    "native translucent dynamic sort writing");

            return new DynamicSortResult(
                    stateSegment.getAtIndex(ValueLayout.JAVA_INT, 0) != 0,
                    stateSegment.getAtIndex(ValueLayout.JAVA_INT, 1) != 0,
                    stateSegment.getAtIndex(ValueLayout.JAVA_INT, 2));
        }
    }

    public int getQuadCount() {
        return this.quadCount;
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

    private static int invokeCreate(MemorySegment records, int recordCount, MemorySegment handleOutput) {
        try {
            return (int) CREATE.invokeExact(records, recordCount, handleOutput);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust translucent section geometry creation downcall failed", throwable);
        }
    }

    private static int invokeDestroy(long handle) {
        try {
            return (int) DESTROY.invokeExact(handle);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust translucent section geometry destroy downcall failed", throwable);
        }
    }

    private static int invokeWriteDistanceSort(long handle, long outputAddress, int outputCapacity, float cameraX,
            float cameraY, float cameraZ) {
        try {
            return (int) WRITE_DISTANCE_SORT.invokeExact(handle, outputAddress, outputCapacity, cameraX, cameraY,
                    cameraZ);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust translucent distance sort downcall failed", throwable);
        }
    }

    private static int invokeWriteDynamicSort(long handle, long outputAddress, int outputCapacity, float cameraX,
            float cameraY, float cameraZ, int initial, int directTriggerSort, int gfniTrigger, int directTrigger,
            int consecutiveTopoSortFailures, MemorySegment outputState, int outputStateLength) {
        try {
            return (int) WRITE_DYNAMIC_SORT.invokeExact(handle, outputAddress, outputCapacity, cameraX, cameraY,
                    cameraZ, initial, directTriggerSort, gfniTrigger, directTrigger, consecutiveTopoSortFailures,
                    outputState, outputStateLength);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust translucent dynamic sort downcall failed", throwable);
        }
    }

    public record DynamicSortResult(boolean gfniTrigger, boolean directTrigger, int consecutiveTopoSortFailures) {
    }

    private static final class State implements Runnable {
        private long handle;

        private State(long handle) {
            this.handle = handle;
        }

        private synchronized long getHandle() {
            if (this.handle == 0) {
                throw new IllegalStateException("Native translucent section geometry has been closed");
            }
            return this.handle;
        }

        @Override
        public synchronized void run() {
            long handle = this.handle;
            if (handle == 0) {
                return;
            }

            check(invokeDestroy(handle), "native translucent section geometry destroy");
            this.handle = 0;
        }
    }
}
