package net.vulkanic.world;

import it.unimi.dsi.fastutil.longs.AbstractLongList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Cleaner;
import java.lang.ref.Reference;
import java.util.Objects;
import java.util.RandomAccess;
import net.minecraft.util.NativeLibraryLoader;

/** Primary Rust CPU storage, shared by conversion, reduction and quad builders.
 * Callers exclude source close/mutation during a build. Scoped spans expire on
 * close; no raw pointer, view or cleaner can outlive the allocation unchecked. */
public final class NativeDhRenderSource extends AbstractLongList implements RandomAccess, AutoCloseable {
    private static final Cleaner CLEANER = Cleaner.create();
    private static MethodHandle bind(String name, FunctionDescriptor descriptor) {
        return NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_dh_render_" + name, descriptor, Linker.Option.critical(true));
    }
    private static final MethodHandle CREATE = bind("source_create", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
    private static final MethodHandle RELEASE = bind("source_release", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private static final MethodHandle SEMANTICS = bind("source_semantics", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    private static final MethodHandle UNIFORM = bind("source_uniform", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    private static final MethodHandle CLEAR = bind("source_clear_semantics", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
    private static final MethodHandle SPAN = bind("span", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG));
    private static final class Lease implements Runnable {
        final Arena arena;
        final MemorySegment pointer;
        Lease(Arena arena, MemorySegment pointer) { this.arena = arena; this.pointer = pointer; }
        public synchronized void run() {
            if (!arena.scope().isAlive()) return;
            // close refuses while a downcall holds a scoped span. Never release
            // the Rust owner until all FFM access has been invalidated.
            arena.close();
            try { RELEASE.invokeExact(pointer); } catch (Throwable e) { throw failure(e); }
        }
    }
    private final Arena arena = Arena.ofShared();
    private final MemorySegment owner;
    private final MemorySegment points;
    private final int count;
    private final Cleaner.Cleanable cleanable;
    private final Lease lease;
    private MemorySegment materials = MemorySegment.NULL, variants = MemorySegment.NULL, uniform = MemorySegment.NULL;

    public NativeDhRenderSource(int height) {
        if (height < 0) { arena.close(); throw new IllegalArgumentException("Negative render column height"); }
        long[] metadata = new long[2];
        MemorySegment pointer = MemorySegment.NULL;
        Lease created = null;
        try {
            pointer = (MemorySegment)CREATE.invokeExact(height, MemorySegment.ofArray(metadata));
            if (pointer.address() == 0) throw new OutOfMemoryError("Cannot allocate DH render source");
            created = new Lease(arena, pointer);
            lease = created;
            owner = pointer.reinterpret(1, arena, null);
            count = Math.toIntExact(metadata[1]);
            points = MemorySegment.ofAddress(metadata[0]).reinterpret(count * 8L, arena, null);
            cleanable = CLEANER.register(this, created);
        } catch (Throwable e) {
            if (created != null) created.run(); else {
                arena.close();
                if (pointer.address() != 0) try { RELEASE.invokeExact(pointer); } catch (Throwable cleanup) { e.addSuppressed(cleanup); }
            }
            throw failure(e);
        }
    }
    private static RuntimeException failure(Throwable e) {
        if (e instanceof Error error) throw error;
        return e instanceof RuntimeException error ? error : new IllegalStateException("Native DH render source operation failed", e);
    }
    @Override public int size() { return arena.scope().isAlive() ? count : 0; }
    @Override public long getLong(int index) { Objects.checkIndex(index, size()); try { return points.getAtIndex(ValueLayout.JAVA_LONG, index); } finally { Reference.reachabilityFence(this); } }
    @Override public long set(int index, long value) {
        long old = getLong(index);
        try { points.setAtIndex(ValueLayout.JAVA_LONG, index, value); return old; } finally { Reference.reachabilityFence(this); }
    }
    @Override public void getElements(int from, long[] destination, int offset, int length) {
        Objects.checkFromIndexSize(from, length, size()); Objects.checkFromIndexSize(offset, length, destination.length);
        try { MemorySegment.copy(points, ValueLayout.JAVA_LONG, from * 8L, destination, offset, length); } finally { Reference.reachabilityFence(this); }
    }
    private void ensureSemantics() {
        if (materials != MemorySegment.NULL) return;
        long[] metadata = new long[2];
        try {
            int result = (int)SEMANTICS.invokeExact(owner, MemorySegment.ofArray(metadata));
            if (result == -1) throw new OutOfMemoryError("Cannot allocate DH render semantics");
            if (result != 1) throw new IllegalStateException("Invalid DH render source");
            materials = MemorySegment.ofAddress(metadata[0]).reinterpret(count * 2L, arena, null);
            variants = MemorySegment.ofAddress(metadata[1]).reinterpret(count, arena, null);
        } catch (Throwable e) { throw failure(e); }
        finally { Reference.reachabilityFence(this); }
    }
    public int material(int index) { Objects.checkIndex(index, size()); try { return materials == MemorySegment.NULL ? 0 : materials.getAtIndex(ValueLayout.JAVA_SHORT, index); } finally { Reference.reachabilityFence(this); } }
    public byte variant(int index) { Objects.checkIndex(index, size()); try { return variants == MemorySegment.NULL ? 0 : variants.getAtIndex(ValueLayout.JAVA_BYTE, index); } finally { Reference.reachabilityFence(this); } }
    public void material(int index, short value) {
        Objects.checkIndex(index, size()); if (value == 0 && materials == MemorySegment.NULL) return;
        ensureSemantics(); try { materials.setAtIndex(ValueLayout.JAVA_SHORT, index, value); } finally { Reference.reachabilityFence(this); }
    }
    public void variant(int index, byte value) {
        Objects.checkIndex(index, size()); if (value == 0 && variants == MemorySegment.NULL) return;
        ensureSemantics(); try { variants.setAtIndex(ValueLayout.JAVA_BYTE, index, value); } finally { Reference.reachabilityFence(this); }
    }
    public boolean uniform(int column) {
        Objects.checkIndex(column, 4096); try { return uniform != MemorySegment.NULL && uniform.getAtIndex(ValueLayout.JAVA_BYTE, column) != 0; } finally { Reference.reachabilityFence(this); }
    }
    public void uniform(int column, boolean value) {
        Objects.checkIndex(column, 4096); if (!value && uniform == MemorySegment.NULL) return;
        if (uniform == MemorySegment.NULL) try {
            var pointer = (MemorySegment)UNIFORM.invokeExact(owner);
            if (pointer.address() == 0) throw new OutOfMemoryError("Cannot allocate DH uniformity plane");
            uniform = pointer.reinterpret(4096, arena, null);
        } catch (Throwable e) { throw failure(e); }
        finally { Reference.reachabilityFence(this); }
        try { uniform.setAtIndex(ValueLayout.JAVA_BYTE, column, (byte)(value ? 1 : 0)); } finally { Reference.reachabilityFence(this); }
    }
    public void clearSemantics(int column, int height) {
        try {
            if ((int)CLEAR.invokeExact(owner, column, height) != 1) throw new IndexOutOfBoundsException("Invalid DH semantic column");
        } catch (Throwable e) { throw failure(e); }
        finally { Reference.reachabilityFence(this); }
    }
    /** Exact storage admission. Custom list/getter contracts retain compatibility. */
    public static boolean canonical(LongList data) { return data != null && (data.getClass() == LongArrayList.class || data instanceof NativeDhRenderSource); }
    public static MemorySegment borrow(LongList data, int offset, int length) {
        Objects.checkFromIndexSize(offset, length, data.size());
        if (data instanceof NativeDhRenderSource nativeData) return nativeData.points.asSlice(offset * 8L, length * 8L);
        return MemorySegment.ofArray(((LongArrayList)data).elements()).asSlice(offset * 8L, length * 8L);
    }
    /** Scoped CPU owner for Rust to resolve multiple windows from one source. */
    public MemorySegment borrowOwner() { return owner; }
    public static int span(LongList data, int offset, int length, int operation, long value) {
        try { return (int)SPAN.invokeExact(borrow(data, offset, length), length, operation, value); }
        catch (Throwable e) { throw failure(e); }
        finally { Reference.reachabilityFence(data); }
    }
    /** Copy without projecting native points back into a Java array. */
    public static void copy(LongList source, int sourceOffset, LongList destination, int destinationOffset, int length) {
        try { MemorySegment.copy(borrow(source, sourceOffset, length), 0, borrow(destination, destinationOffset, length), 0, length * 8L); }
        finally { Reference.reachabilityFence(source); Reference.reachabilityFence(destination); }
    }
    @Override public void close() { lease.run(); cleanable.clean(); }
}
