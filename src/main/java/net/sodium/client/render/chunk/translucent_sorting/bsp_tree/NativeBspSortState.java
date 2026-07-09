package net.sodium.client.render.chunk.translucent_sorting.bsp_tree;

import net.minecraft.util.NativeLibraryLoader;
import net.sodium.client.util.NativeBuffer;
import org.lwjgl.system.MemoryUtil;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Cleaner;

final class NativeBspSortState implements AutoCloseable {
    private static final int OK = 0;
    private static final Cleaner CLEANER = Cleaner.create();

    private static final MethodHandle CREATE = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_translucent_bsp_sort_state_create",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS));
    private static final MethodHandle DESTROY = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_translucent_bsp_sort_state_destroy",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG));
    private static final MethodHandle ADDRESS = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_translucent_bsp_sort_state_address",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS));
    private static final MethodHandle WRITE_INDEX_BUFFER = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_translucent_bsp_sort_state_write_index_buffer",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT));

    private final State state;
    private final Cleaner.Cleanable cleanable;
    private final long indexAddress;
    private final int quadCapacity;
    private int quadCount;

    private NativeBspSortState(long handle, long indexAddress, int quadCapacity) {
        this.state = new State(handle);
        this.cleanable = CLEANER.register(this, this.state);
        this.indexAddress = indexAddress;
        this.quadCapacity = quadCapacity;
    }

    static NativeBspSortState create(int quadCapacity) {
        if (quadCapacity < 0) {
            throw new IllegalArgumentException("Invalid BSP sort quad capacity: " + quadCapacity);
        }

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment handleSegment = arena.allocate(ValueLayout.JAVA_LONG);
            check(invokeCreate(quadCapacity, handleSegment), "native BSP sort state creation");
            long handle = handleSegment.get(ValueLayout.JAVA_LONG, 0);
            if (handle == 0) {
                throw new IllegalStateException("Native BSP sort state creation returned a null handle");
            }

            MemorySegment addressSegment = arena.allocate(ValueLayout.JAVA_LONG);
            MemorySegment capacitySegment = arena.allocate(ValueLayout.JAVA_INT);
            check(invokeAddress(handle, addressSegment, capacitySegment), "native BSP sort state address query");
            long indexAddress = addressSegment.get(ValueLayout.JAVA_LONG, 0);
            int capacity = capacitySegment.get(ValueLayout.JAVA_INT, 0);
            if (quadCapacity > 0 && indexAddress == 0) {
                throw new IllegalStateException("Native BSP sort state returned a null index buffer");
            }
            if (capacity != quadCapacity) {
                throw new IllegalStateException("Native BSP sort state capacity " + capacity
                        + " did not match requested capacity " + quadCapacity);
            }

            return new NativeBspSortState(handle, indexAddress, capacity);
        }
    }

    void append(int quadIndex) {
        if (quadIndex < 0) {
            throw new IllegalArgumentException("Invalid BSP sort quad index: " + quadIndex);
        }
        if (this.quadCount >= this.quadCapacity) {
            throw new IllegalStateException("BSP sort wrote more quad indexes than the index buffer can hold");
        }

        MemoryUtil.memPutInt(this.indexAddress + (long) this.quadCount * Integer.BYTES, quadIndex);
        this.quadCount++;
    }

    void appendBatch(int[] quadIndexes, int quadIndexCount) {
        if (quadIndexCount < 0 || quadIndexCount > quadIndexes.length) {
            throw new IllegalArgumentException("Invalid BSP sort batch length: " + quadIndexCount);
        }
        if (quadIndexCount == 0) {
            return;
        }

        if (this.quadCount + quadIndexCount > this.quadCapacity) {
            throw new IllegalStateException("BSP sort wrote more quad indexes than the index buffer can hold");
        }

        long writeAddress = this.indexAddress + (long) this.quadCount * Integer.BYTES;
        for (int index = 0; index < quadIndexCount; index++) {
            int quadIndex = quadIndexes[index];
            if (quadIndex < 0) {
                throw new IllegalArgumentException("Invalid BSP sort quad index: " + quadIndex);
            }

            MemoryUtil.memPutInt(writeAddress + (long) index * Integer.BYTES, quadIndex);
        }
        this.quadCount += quadIndexCount;
    }

    void writeIndexBuffer(NativeBuffer nativeBuffer) {
        check(invokeWriteIndexBuffer(this.state.getHandle(), this.quadCount,
                MemoryUtil.memAddress(nativeBuffer.getDirectBuffer()), nativeBuffer.getLength()),
                "native BSP sort index buffer writing");
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

    private static int invokeCreate(int quadCapacity, MemorySegment handleOutput) {
        try {
            return (int) CREATE.invokeExact(quadCapacity, handleOutput);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust BSP sort state creation downcall failed", throwable);
        }
    }

    private static int invokeDestroy(long handle) {
        try {
            return (int) DESTROY.invokeExact(handle);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust BSP sort state destroy downcall failed", throwable);
        }
    }

    private static int invokeAddress(long handle, MemorySegment addressOutput, MemorySegment capacityOutput) {
        try {
            return (int) ADDRESS.invokeExact(handle, addressOutput, capacityOutput);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust BSP sort state address downcall failed", throwable);
        }
    }

    private static int invokeWriteIndexBuffer(long handle, int quadCount, long outputAddress, int outputCapacity) {
        try {
            return (int) WRITE_INDEX_BUFFER.invokeExact(handle, quadCount, outputAddress, outputCapacity);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust BSP sort state index buffer write downcall failed", throwable);
        }
    }

    private static final class State implements Runnable {
        private long handle;

        private State(long handle) {
            this.handle = handle;
        }

        private synchronized long getHandle() {
            if (this.handle == 0) {
                throw new IllegalStateException("Native BSP sort state has been closed");
            }
            return this.handle;
        }

        @Override
        public synchronized void run() {
            long handle = this.handle;
            if (handle == 0) {
                return;
            }

            check(invokeDestroy(handle), "native BSP sort state destroy");
            this.handle = 0;
        }
    }
}
