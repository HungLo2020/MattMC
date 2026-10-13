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

/** Rust owns immutable four-column semantic footprints; readers retain shared CPU leases. */
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
    /** Adopt one retained footprint (as returned by the source table) as four read-only views. */
    static LongList[] adopt(MemorySegment pointer) { return new NativeDhContributors(pointer).columns.clone(); }
    /** A native span of {@code column}'s points: borrowed for native lists, copied into {@code scratch} otherwise. */
    static MemorySegment points(LongList column, Arena scratch) {
        if (column instanceof ReadOnlyColumn view) return view.points;
        if (column instanceof NativeDhColumnList view) return view.stagePoints();
        var copy = scratch.allocate(ValueLayout.JAVA_LONG, Math.max(1, column.size())).asSlice(0, column.size() * 8L);
        for (int i = 0; i < column.size(); i++) copy.setAtIndex(ValueLayout.JAVA_LONG, i, column.getLong(i));
        return copy;
    }
    public static boolean isSnapshot(LongList[] columns) {
        if (columns == null || columns.length != 4) return false;
        for (var column : columns) if (column != null && !(column instanceof ReadOnlyColumn)) return false;
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
    private static void release(MemorySegment pointer) {
        try { RELEASE.invokeExact(pointer); } catch (Throwable e) { throw new IllegalStateException(e); }
    }
}
