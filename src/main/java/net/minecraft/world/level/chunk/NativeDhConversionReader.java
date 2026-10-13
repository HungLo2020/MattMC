package net.minecraft.world.level.chunk;

import it.unimi.dsi.fastutil.longs.AbstractLongList;
import it.unimi.dsi.fastutil.longs.LongList;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Reference;
import net.minecraft.util.NativeLibraryLoader;

/** Conversion-local read cursor. Rust retains one selected column; Java does
 * not register persistent list/arena identities for each visited source slot.
 * Consume a returned list before selecting another column. Never export it.
 * Existing DH source mutation exclusion still applies during conversion. */
public final class NativeDhConversionReader extends AbstractLongList implements AutoCloseable {
    private static MethodHandle bind(String name, FunctionDescriptor type, Linker.Option... options) {
        return NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_dh_reader_" + name, type, options);
    }
    private static final MethodHandle CREATE = bind("create", FunctionDescriptor.of(ValueLayout.ADDRESS));
    private static final MethodHandle RELEASE = bind("release", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private static final MethodHandle SELECT = bind("select", FunctionDescriptor.of(ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    private static final MethodHandle SIZE = bind("size", FunctionDescriptor.of(ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS), Linker.Option.critical(false));
    private static final MethodHandle GET = bind("get", FunctionDescriptor.of(ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS), Linker.Option.critical(false));
    private final NativeDhSource source;
    private final Arena arena = Arena.ofConfined();
    private final MemorySegment owner;
    private final MemorySegment output = arena.allocate(ValueLayout.JAVA_LONG);

    NativeDhConversionReader(NativeDhSource source) {
        this.source = source;
        MemorySegment pointer = MemorySegment.NULL;
        try {
            pointer = (MemorySegment)CREATE.invokeExact();
            if (pointer.address() == 0) throw new OutOfMemoryError("DH conversion reader");
            owner = pointer.reinterpret(1, arena, NativeDhConversionReader::release);
        } catch (Throwable failure) {
            arena.close();
            if (pointer.address() != 0) release(pointer);
            throw failure(failure);
        }
    }
    /** Existing escaped/custom identities retain their original getter contract. */
    public LongList column(int index) {
        if (!arena.scope().isAlive()) throw new IllegalStateException("Closed DH conversion reader");
        java.util.Objects.checkIndex(index, 4096);
        if (!source.isLive() || source.boundColumn(index) != null) return source.getColumn(index);
        try {
            int result = (int)SELECT.invokeExact(owner, source.stageOwner(), index);
            return result == 1 ? this : result == 0 ? null : source.getColumn(index);
        } catch (Throwable failure) { throw failure(failure); }
        finally { Reference.reachabilityFence(source); }
    }
    @Override public int size() {
        try { return (int)SIZE.invokeExact(owner); }
        catch (Throwable failure) { throw failure(failure); }
    }
    @Override public long getLong(int index) {
        try {
            if ((int)GET.invokeExact(owner, index, output) != 1) throw new IndexOutOfBoundsException(index);
            return output.get(ValueLayout.JAVA_LONG, 0);
        } catch (Throwable failure) { throw failure(failure); }
    }
    @Override public void close() { if (arena.scope().isAlive()) arena.close(); }
    /** Pin only for a synchronous native consumer. No selected point address
     * escapes; Rust resolves the live lease after each contextual callback. */
    public MemorySegment borrowOwner() { return owner; }
    private static void release(MemorySegment pointer) {
        try { RELEASE.invokeExact(pointer); } catch (Throwable failure) { throw failure(failure); }
    }
    private static RuntimeException failure(Throwable failure) {
        if (failure instanceof Error error) throw error;
        return failure instanceof RuntimeException error ? error : new IllegalStateException("DH conversion reader", failure);
    }
}
