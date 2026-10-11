package net.minecraft.world.level.chunk.storage;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import net.minecraft.util.NativeLibraryLoader;

/** Rust-owned CPU tape. Close releases bulk storage immediately; its FFM scope
 * releases the small native wrapper if a caller discards an unconsumed result. */
public final class NativeChunkTape implements AutoCloseable {
    private static final MethodHandle CLOSE = handle("close", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private static final MethodHandle RELEASE = handle("release", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private static final MethodHandle COPY = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_nbt_owned_tape_copy",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT), Linker.Option.critical(true));
    private final MemorySegment owner;
    private final int length;
    private static MethodHandle handle(String name, FunctionDescriptor descriptor) {
        return NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_nbt_owned_tape_" + name, descriptor);
    }
    private NativeChunkTape(MemorySegment owner, int length) { this.owner = owner; this.length = length; }
    static NativeChunkTape adopt(MemorySegment pointer, long length) {
        boolean adopted = false;
        try {
            if (pointer.address() == 0 || length < 0 || length > Integer.MAX_VALUE) throw new IllegalStateException("Invalid native chunk tape");
            var owner = pointer.asReadOnly().reinterpret(1, Arena.ofAuto(), NativeChunkTape::release);
            adopted = true;
            return new NativeChunkTape(owner, (int)length);
        } catch (RuntimeException | Error failure) {
            if (!adopted && pointer.address() != 0) release(pointer);
            throw failure;
        }
    }
    /** Scoped CPU owner handle, never a GPU handle or Java input address. */
    public MemorySegment owner() { return owner; }
    /** Explicit compatibility escape for an old version or declined codec. */
    public byte[] copy() {
        byte[] result = new byte[length];
        try {
            if ((int)COPY.invokeExact(owner, MemorySegment.ofArray(result), length) != 0) throw new IllegalStateException("Chunk tape is closed");
            return result;
        } catch (Throwable failure) { throw failure(failure); }
    }
    @Override public void close() {
        try { CLOSE.invokeExact(owner); } catch (Throwable failure) { throw failure(failure); }
    }
    static void release(MemorySegment owner) {
        try { RELEASE.invokeExact(owner); } catch (Throwable failure) { throw failure(failure); }
    }
    private static RuntimeException failure(Throwable failure) {
        if (failure instanceof RuntimeException runtime) return runtime;
        if (failure instanceof Error error) throw error;
        return new IllegalStateException("Native chunk tape ownership failed", failure);
    }
}
