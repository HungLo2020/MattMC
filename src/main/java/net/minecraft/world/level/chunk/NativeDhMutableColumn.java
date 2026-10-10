package net.minecraft.world.level.chunk;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Reference;
import net.minecraft.util.NativeLibraryLoader;

/** Rust owns the mutable point vector; CPU views expire on each mutation. */
final class NativeDhMutableColumn {
    private static MethodHandle call(String name, FunctionDescriptor descriptor, Linker.Option... options) {
        return NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_dh_column_" + name, descriptor, options);
    }
    private static final MethodHandle CREATE = call("create", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT), Linker.Option.critical(true));
    private static final MethodHandle RELEASE = call("release", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private static final MethodHandle VIEW = call("view", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    private static final MethodHandle SET = call("set", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG));
    private static final MethodHandle ADD = call("add", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG));
    private static final MethodHandle REMOVE = call("remove", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
    private static final MethodHandle INSERT = call("insert", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT), Linker.Option.critical(true));
    private static final MethodHandle REMAP = call("remap", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT), Linker.Option.critical(true));
    private static final MethodHandle HASH = call("hash", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    private static final MethodHandle CULL = call("cull", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    private static final MethodHandle REDUCE = call("reduce", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    private static final ThreadLocal<MemorySegment> METADATA = ThreadLocal.withInitial(() -> Arena.ofAuto().allocate(16, 8));
    private final Arena arena = Arena.ofAuto();
    private final MemorySegment owner;
    private MemorySegment points;
    NativeDhMutableColumn(MemorySegment input, int count) {
        MemorySegment pointer = MemorySegment.NULL; boolean attached = false;
        try {
            pointer = (MemorySegment) CREATE.invokeExact(input, count);
            if (pointer.address() == 0) throw new OutOfMemoryError("Cannot allocate native DH column");
            owner = pointer.reinterpret(1, arena, NativeDhMutableColumn::release); attached = true;
            refresh();
        } catch (RuntimeException | Error e) { if (!attached && pointer.address() != 0) release(pointer); throw e; }
        catch (Throwable e) { if (!attached && pointer.address() != 0) release(pointer); throw new IllegalStateException(e); }
    }
    /** Adopt an already produced Rust vector without copying its points again. */
    private NativeDhMutableColumn(MemorySegment pointer) {
        boolean attached = false;
        try {
            owner = pointer.reinterpret(1, arena, NativeDhMutableColumn::release); attached = true; refresh();
        } catch (RuntimeException | Error e) { if (!attached) release(pointer); throw e; }
        catch (Throwable e) { if (!attached) release(pointer); throw new IllegalStateException(e); }
    }
    static NativeDhColumnList adopt(MemorySegment pointer) {
        if (pointer.address() == 0) throw new IllegalStateException("Missing native DH replacement");
        return NativeDhColumnList.fromOwned(new NativeDhMutableColumn(pointer));
    }
    static NativeDhColumnList reduce(NativeDhMaterials policy) {
        try {
            int result = (int) REDUCE.invokeExact(policy.neighbors, policy.owner, policy.result);
            if (result == 0) return null;
            if (result == 1) return NativeDhColumnList.empty();
            MemorySegment pointer = policy.result.get(ValueLayout.ADDRESS, 0);
            if (result != 2 || pointer.address() == 0) throw new IllegalStateException("Invalid native DH reduction result");
            return NativeDhColumnList.fromOwned(new NativeDhMutableColumn(pointer));
        } catch (RuntimeException | Error e) { throw e; } catch (Throwable e) { throw new IllegalStateException(e); }
        finally { Reference.reachabilityFence(policy); }
    }
    private static void release(MemorySegment pointer) {
        try { RELEASE.invokeExact(pointer); } catch (Throwable e) { throw new IllegalStateException(e); }
    }
    private void refresh() throws Throwable {
        MemorySegment view = METADATA.get();
        if ((int) VIEW.invokeExact(owner, view) != 1) throw new IllegalStateException("Invalid DH column owner");
        long count = view.getAtIndex(ValueLayout.JAVA_LONG, 1);
        if (count < 0 || count > Integer.MAX_VALUE) throw new IllegalStateException("DH column size");
        points = view.getAtIndex(ValueLayout.ADDRESS, 0).reinterpret(count * 8, arena, null).asReadOnly();
    }
    MemorySegment points() { return points; }
    MemorySegment stageOwner() { return owner; }
    void acceptView(MemorySegment address, long count) {
        if (count < 0 || count > 4096) throw new IllegalStateException("Invalid DH occlusion extent");
        points = address.reinterpret(count * 8, arena, null).asReadOnly();
    }
    int size() { return (int) (points.byteSize() / 8); }
    void set(int index, long value) {
        try { if ((int) SET.invokeExact(owner, index, value) != 1) throw new IndexOutOfBoundsException(index); }
        catch (RuntimeException | Error e) { throw e; } catch (Throwable e) { throw new IllegalStateException(e); }
        finally { Reference.reachabilityFence(this); }
    }
    void remove(int from, int to) {
        try { if ((int) REMOVE.invokeExact(owner, from, to) != 1) throw new IndexOutOfBoundsException(); refresh(); }
        catch (RuntimeException | Error e) { throw e; } catch (Throwable e) { throw new IllegalStateException(e); }
        finally { Reference.reachabilityFence(this); }
    }
    void insert(int index, MemorySegment source, int count) {
        try {
            int result = (int) INSERT.invokeExact(owner, index, source, count);
            if (result == -1) throw new OutOfMemoryError("Cannot grow native DH column");
            if (result != 1) throw new IndexOutOfBoundsException();
            refresh();
        } catch (RuntimeException | Error e) { throw e; } catch (Throwable e) { throw new IllegalStateException(e); }
        finally { Reference.reachabilityFence(this); }
    }
    void add(int index, long value) {
        try {
            int result = (int) ADD.invokeExact(owner, index, value);
            if (result == -1) throw new OutOfMemoryError("Cannot grow native DH column");
            if (result != 1) throw new IndexOutOfBoundsException(index);
            refresh();
        } catch (RuntimeException | Error e) { throw e; } catch (Throwable e) { throw new IllegalStateException(e); }
        finally { Reference.reachabilityFence(this); }
    }
    void remap(int[] ids) {
        try {
            int failed = (int) REMAP.invokeExact(owner, MemorySegment.ofArray(ids), ids.length);
            if (failed >= 0) com.seibel.distanthorizons.core.util.FullDataPointUtil.remap(ids, points.getAtIndex(ValueLayout.JAVA_LONG, failed));
        } catch (RuntimeException | Error e) { throw e; } catch (Throwable e) { throw new IllegalStateException(e); }
        finally { Reference.reachabilityFence(this); }
    }
    int cull(NativeDhMaterials policy) {
        try { int result = (int) CULL.invokeExact(owner, policy.neighbors, policy.owner); if (result == 2) refresh(); return result; }
        catch (RuntimeException | Error e) { throw e; } catch (Throwable e) { throw new IllegalStateException(e); }
        finally { Reference.reachabilityFence(this); Reference.reachabilityFence(policy); }
    }
    static int hash(MemorySegment points, int count) {
        try { return (int) HASH.invokeExact(points, count); }
        catch (RuntimeException | Error e) { throw e; } catch (Throwable e) { throw new IllegalStateException(e); }
    }
}
