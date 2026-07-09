package net.sodium.client.render.chunk.vertex.format;

import net.minecraft.util.NativeLibraryLoader;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Cleaner;

public final class NativeChunkQuadBuffer implements AutoCloseable {
    private static final int OK = 0;
    private static final Cleaner CLEANER = Cleaner.create();

    private static final MethodHandle CREATE = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_chunk_quad_buffer_create",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS));
    private static final MethodHandle ENSURE_CAPACITY = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_chunk_quad_buffer_ensure_capacity",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS));
    private static final MethodHandle DESTROY = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_chunk_quad_buffer_destroy",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG));

    private final State state;
    private final Cleaner.Cleanable cleanable;

    private int capacity;
    private long address;

    private NativeChunkQuadBuffer(long handle, int capacity, long address) {
        this.state = new State(handle);
        this.cleanable = CLEANER.register(this, this.state);
        this.capacity = capacity;
        this.address = address;
    }

    public static NativeChunkQuadBuffer create(int capacity) {
        if (capacity < 0) {
            throw new IllegalArgumentException("Native quad buffer capacity must be non-negative");
        }

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment handleSegment = arena.allocate(ValueLayout.JAVA_LONG);
            MemorySegment addressSegment = arena.allocate(ValueLayout.JAVA_LONG);

            check(invokeCreate(capacity, handleSegment, addressSegment), "native chunk quad buffer creation");

            long handle = handleSegment.get(ValueLayout.JAVA_LONG, 0);
            if (handle == 0) {
                throw new IllegalStateException("Native chunk quad buffer creation returned a null handle");
            }

            return new NativeChunkQuadBuffer(handle, capacity, addressSegment.get(ValueLayout.JAVA_LONG, 0));
        }
    }

    public void ensureCapacity(int requiredCapacity) {
        if (requiredCapacity < 0) {
            throw new IllegalArgumentException("Native quad buffer capacity must be non-negative");
        }
        if (requiredCapacity <= this.capacity) {
            return;
        }

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment addressSegment = arena.allocate(ValueLayout.JAVA_LONG);
            check(invokeEnsureCapacity(this.state.getHandle(), requiredCapacity, addressSegment),
                    "native chunk quad buffer resize");
            this.capacity = requiredCapacity;
            this.address = addressSegment.get(ValueLayout.JAVA_LONG, 0);
        }
    }

    public long address() {
        this.state.getHandle();
        return this.address;
    }

    public long addressAt(int quadIndex) {
        if (quadIndex < 0 || quadIndex >= this.capacity) {
            throw new IndexOutOfBoundsException("Native quad index " + quadIndex + " outside capacity " + this.capacity);
        }

        return this.address() + (long) quadIndex * NativeChunkMeshEncoder.NATIVE_QUAD_STRIDE;
    }

    public int capacity() {
        return this.capacity;
    }

    @Override
    public void close() {
        this.cleanable.clean();
        this.address = 0;
        this.capacity = 0;
    }

    private static void check(int status, String operation) {
        if (status != OK) {
            throw new IllegalStateException(operation + " failed with native status " + status);
        }
    }

    private static int invokeCreate(int capacity, MemorySegment handleOutput, MemorySegment addressOutput) {
        try {
            return (int) CREATE.invokeExact(capacity, handleOutput, addressOutput);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust chunk quad buffer creation downcall failed", throwable);
        }
    }

    private static int invokeEnsureCapacity(long handle, int capacity, MemorySegment addressOutput) {
        try {
            return (int) ENSURE_CAPACITY.invokeExact(handle, capacity, addressOutput);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust chunk quad buffer resize downcall failed", throwable);
        }
    }

    private static int invokeDestroy(long handle) {
        try {
            return (int) DESTROY.invokeExact(handle);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust chunk quad buffer destroy downcall failed", throwable);
        }
    }

    private static final class State implements Runnable {
        private long handle;

        private State(long handle) {
            this.handle = handle;
        }

        private synchronized long getHandle() {
            if (this.handle == 0) {
                throw new IllegalStateException("Native chunk quad buffer has been closed");
            }
            return this.handle;
        }

        @Override
        public synchronized void run() {
            long handle = this.handle;
            if (handle == 0) {
                return;
            }

            check(invokeDestroy(handle), "native chunk quad buffer destroy");
            this.handle = 0;
        }
    }
}
