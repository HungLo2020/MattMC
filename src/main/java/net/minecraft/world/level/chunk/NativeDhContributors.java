package net.minecraft.world.level.chunk;

import it.unimi.dsi.fastutil.longs.AbstractLongList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Reference;
import java.util.Objects;
import java.util.RandomAccess;
import net.minecraft.util.NativeLibraryLoader;
import org.jetbrains.annotations.Nullable;

/** Rust owns immutable four-column semantic footprints; readers retain CPU leases. */
public final class NativeDhContributors {
    private static MethodHandle handle(String name, FunctionDescriptor signature, Linker.Option... options) {
        return NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_dh_contributors_" + name, signature, options);
    }
    private static final MethodHandle CREATE = handle("create", FunctionDescriptor.of(ValueLayout.ADDRESS,
        ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT), Linker.Option.critical(true));
    private static final MethodHandle VIEW = handle("view", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    private static final MethodHandle RELEASE = handle("release", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private static final ThreadLocal<MemorySegment> METADATA = ThreadLocal.withInitial(() -> Arena.ofAuto().allocate(64, 8));
    private final MemorySegment owner;
    private final LongList[] columns = new LongList[4];
    private NativeDhContributors(MemorySegment pointer) {
        boolean attached = false;
        try {
            var metadata = METADATA.get();
            if ((int) VIEW.invokeExact(pointer, metadata) != 1) throw new IllegalStateException("Invalid contributor owner");
            // Every column holds and fences this object, so it decides the native lifetime.
            owner = pointer.reinterpret(1).asReadOnly(); NativeReclaimer.register(this, pointer, NativeReclaimer.DH_CONTRIBUTORS); attached = true;
            for (int i = 0; i < 4; i++) {
                long length = metadata.getAtIndex(ValueLayout.JAVA_LONG, i * 2L + 1);
                if (length == -1) continue;
                if (length < 0 || length > 4096) throw new IllegalStateException("Invalid contributor extent");
                var points = metadata.getAtIndex(ValueLayout.ADDRESS, i * 2L).reinterpret(length * 8).asReadOnly();
                columns[i] = new ReadOnlyColumn(this, points);
            }
        } catch (RuntimeException | Error e) { if (!attached) release(pointer); throw e; }
        catch (Throwable e) { if (!attached) release(pointer); throw new IllegalStateException(e); }
    }
    private static final class ReadOnlyColumn extends AbstractLongList implements RandomAccess, java.io.Serializable {
        private static final long serialVersionUID = 1L;
        private final Object owner;
        private final MemorySegment points;
        private ReadOnlyColumn(Object owner, MemorySegment points) { this.owner = owner; this.points = points; }
        @Override public int size() { return (int) (points.byteSize() / 8); }
        @Override public long getLong(int index) {
            Objects.checkIndex(index, size());
            try { return points.getAtIndex(ValueLayout.JAVA_LONG, index); }
            finally { Reference.reachabilityFence(owner); }
        }
        @Override public void getElements(int from, long[] array, int offset, int length) {
            Objects.checkFromIndexSize(from, length, size()); Objects.checkFromIndexSize(offset, length, array.length);
            try { MemorySegment.copy(points, ValueLayout.JAVA_LONG, from * 8L, array, offset, length); }
            finally { Reference.reachabilityFence(owner); }
        }
        private Object writeReplace() { return new LongArrayList(this); }
    }
    /** Readers borrow immutable native descriptors and keep their CPU owner alive. */
    static LongList[] fromDescriptors(Object owner, MemorySegment metadata) {
        var columns = new LongList[4];
        for (int i = 0; i < 4; i++) {
            long length = metadata.getAtIndex(ValueLayout.JAVA_LONG, i * 2L + 1);
            if (length == -1) continue;
            if (length < 0 || length > 4096) throw new IllegalStateException("Invalid contributor extent");
            var points = metadata.getAtIndex(ValueLayout.ADDRESS, i * 2L).reinterpret(length * 8).asReadOnly();
            columns[i] = new ReadOnlyColumn(owner, points);
        }
        return columns;
    }
    public static boolean isSnapshot(LongList[] columns) {
        if (columns == null || columns.length != 4) return false;
        for (var column : columns) if (column != null && !(column instanceof ReadOnlyColumn)) return false;
        return true;
    }
    /** Stage immutable CPU spans only; mutable/custom footprints keep compatibility. */
    static boolean stageSnapshot(LongList[] columns, MemorySegment metadata) {
        if (!isSnapshot(columns)) return false;
        for (int i = 0; i < 4; i++) {
            var column = (ReadOnlyColumn) columns[i];
            if (column != null && column.size() > 4096) return false;
            metadata.setAtIndex(ValueLayout.ADDRESS, i * 2L, column == null ? MemorySegment.NULL : column.points);
            metadata.setAtIndex(ValueLayout.JAVA_LONG, i * 2L + 1, column == null ? -1L : (long) column.size());
        }
        return true;
    }
    private static MemorySegment data(LongList column) {
        if (column == null) return MemorySegment.NULL;
        if (column instanceof ReadOnlyColumn view) return view.points;
        if (column instanceof NativeDhColumnList view) return view.stagePoints();
        return MemorySegment.ofArray(((LongArrayList) column).elements());
    }
    private static int count(LongList column, int limit) { return column == null ? -1 : Math.min(column.size(), limit); }
    /** Returns immutable native snapshots, or declines to the caller's exact Java callbacks. */
    @Nullable public static LongList[] snapshot(LongList[] input, int[] remap, int limit) {
        if (input == null || input.length != 4 || limit < 0) return null;
        boolean complete = true;
        int needed = 0;
        for (var column : input) {
            if (column == null) continue;
            if (!(column instanceof ReadOnlyColumn) && !(column instanceof NativeDhColumnList) && column.getClass() != LongArrayList.class) return null;
            int size = column.size(); needed = Math.max(needed, Math.min(size, limit)); complete &= size <= limit;
        }
        if (remap == null && complete && isSnapshot(input)) return input.clone();
        if (needed > 4096) return null;
        try {
            var mapping = remap == null ? MemorySegment.NULL : MemorySegment.ofArray(remap);
            int mappingSize = remap == null ? -1 : remap.length;
            var pointer = (MemorySegment) CREATE.invokeExact(data(input[0]), count(input[0], limit), data(input[1]), count(input[1], limit),
                data(input[2]), count(input[2], limit), data(input[3]), count(input[3], limit), mapping, mappingSize, needed);
            return pointer.address() == 0 ? null : new NativeDhContributors(pointer).columns.clone();
        } catch (RuntimeException | Error e) { throw e; } catch (Throwable e) { throw new IllegalStateException(e); }
        finally { Reference.reachabilityFence(input); Reference.reachabilityFence(remap); }
    }
    private static final MethodHandle DECODE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_dh_contributors_decode",
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS),
        Linker.Option.critical(true));
    private static final ThreadLocal<long[]> DECODE_CELLS = ThreadLocal.withInitial(() -> new long[4096 * 9]);
    /** Every cell of one saved sidecar shares this owner; any retained view pins it. */
    private static final class SavedBatch {}
    /** Receives one decoded cell (x-major index) with its four columns. */
    @FunctionalInterface public interface SavedCellSink { void accept(int cell, LongList[] columns); }
    /** Saved-sidecar stop statuses; see {@link #decodeSaved}. */
    public static final int SAVED_COMPLETE = 0, SAVED_END_OF_INPUT = 1, SAVED_OVER_BOUND = 2;

    /**
     * Decodes a whole decompressed V4 contributor sidecar in Rust into one
     * batch owner, and passes each completed present cell to {@code sink} in
     * saved order. Returns {@code [status, value]}: {@link #SAVED_COMPLETE},
     * {@link #SAVED_END_OF_INPUT}, or {@link #SAVED_OVER_BOUND} with the
     * offending size. Cells before the stop are still delivered, as the Java
     * reader applies them before it throws.
     */
    public static int[] decodeSaved(byte[] bytes, int offset, int length, SavedCellSink sink) {
        java.util.Objects.checkFromIndexSize(offset, length, bytes.length);
        long[] cells = DECODE_CELLS.get();
        long[] result = new long[3];
        MemorySegment pointer;
        try {
            pointer = (MemorySegment) DECODE.invokeExact(MemorySegment.ofArray(bytes).asSlice(offset, length), length,
                MemorySegment.ofArray(cells), MemorySegment.ofArray(result));
        } catch (RuntimeException | Error e) { throw e; } catch (Throwable e) { throw new IllegalStateException(e); }
        int status = (int) result[0], count = (int) result[1];
        if (status == 3) throw new OutOfMemoryError("Cannot decode DH contributor sidecar");
        if (status < 0 || status > 2 || count < 0 || count > 4096 || (count > 0) != (pointer.address() != 0))
            throw new IllegalStateException("Invalid DH contributor sidecar decode");
        if (count > 0) {
            var batch = new SavedBatch();
            NativeReclaimer.register(batch, pointer, NativeReclaimer.DH_CONTRIBUTOR_BATCH);
            var descriptors = MemorySegment.ofArray(cells);
            for (int row = 0; row < count; row++) {
                int cell = (int) cells[row * 9];
                sink.accept(cell, fromDescriptors(batch, descriptors.asSlice((row * 9L + 1) * 8, 64)));
            }
            Reference.reachabilityFence(batch);
        }
        return new int[] {status, (int) result[2]};
    }
    private static void release(MemorySegment pointer) {
        try { RELEASE.invokeExact(pointer); } catch (Throwable e) { throw new IllegalStateException(e); }
    }
}
