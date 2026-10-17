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
    private static final MethodHandle SHARED = call("shared", FunctionDescriptor.of(ValueLayout.ADDRESS,
        ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT));
    private static final MethodHandle OWN = call("make_owned", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
    private static final MethodHandle RESIZE = call("resize", FunctionDescriptor.of(ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    private static final MethodHandle RELEASE = call("release", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private static final MethodHandle VIEW = call("view", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    private static final MethodHandle SET = call("set", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG));
    private static final MethodHandle ADD = call("add", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG));
    private static final MethodHandle REMOVE = call("remove", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
    private static final MethodHandle INSERT = call("insert", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT), Linker.Option.critical(true));
    private static final MethodHandle REMAP = call("remap", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT), Linker.Option.critical(true));
    private static final MethodHandle HASH = call("hash", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    private static final MethodHandle CACHED_HASH = call("cached_hash", FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS), Linker.Option.critical(false));
    private static final MethodHandle OWNER_HASH = call("owner_hash", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
    private static final ThreadLocal<MemorySegment> METADATA = ThreadLocal.withInitial(() -> Arena.ofAuto().allocate(16, 8));
    private final Arena arena;
    /** Keeps a grouped column's owner (and its single cleanup) reachable. */
    private NativeDhColumnGroup group;
    private final MemorySegment owner;
    private MemorySegment points;
    private long extentAddress;
    private int extentCount;
    NativeDhMutableColumn(MemorySegment input, int count) { this(input, count, null); }
    /** {@code group} shares one arena with its owner's other columns (null: own arena). */
    NativeDhMutableColumn(MemorySegment input, int count, NativeDhColumnGroup group) {
        arena = group == null ? Arena.ofAuto() : group.arena;
        MemorySegment pointer = MemorySegment.NULL; boolean attached = false;
        try {
            pointer = (MemorySegment) CREATE.invokeExact(input, count);
            if (pointer.address() == 0) throw new OutOfMemoryError("Cannot allocate native DH column");
            owner = group == null ? pointer.reinterpret(1, arena, NativeDhMutableColumn::release) : group.adopt(pointer);
            attached = true;
            this.group = group;
            refresh();
        } catch (RuntimeException | Error e) { if (!attached && pointer.address() != 0) release(pointer); throw e; }
        catch (Throwable e) { if (!attached && pointer.address() != 0) release(pointer); throw new IllegalStateException(e); }
    }
    /** Adopt an already produced Rust vector without copying its points again. */
    private NativeDhMutableColumn(MemorySegment pointer) { this(pointer, null); }
    private NativeDhMutableColumn(MemorySegment pointer, NativeDhColumnGroup group) {
        arena = group == null ? Arena.ofAuto() : group.arena;
        this.group = group;
        boolean attached = false;
        try {
            owner = group == null ? pointer.reinterpret(1, arena, NativeDhMutableColumn::release) : group.adopt(pointer); attached = true; refresh();
        } catch (RuntimeException | Error e) { if (!attached) release(pointer); throw e; }
        catch (Throwable e) { if (!attached) release(pointer); throw new IllegalStateException(e); }
    }
    static NativeDhMutableColumn shared(int kind, MemorySegment owner, long first, int count) {
        try {
            var pointer = (MemorySegment) SHARED.invokeExact(kind, owner, first, count);
            if (pointer.address() == 0) throw new IllegalStateException("Invalid DH shared column");
            return new NativeDhMutableColumn(pointer);
        } catch (RuntimeException | Error e) { throw e; } catch (Throwable e) { throw new IllegalStateException(e); }
    }
    void makeOwned() {
        try {
            int result = (int) OWN.invokeExact(owner);
            if (result == -1) throw new OutOfMemoryError("Cannot detach DH column");
            if (result != 1) throw new IllegalStateException("Invalid DH column owner");
            refresh();
        } catch (RuntimeException | Error e) { throw e; } catch (Throwable e) { throw new IllegalStateException(e); }
        finally { Reference.reachabilityFence(this); }
    }
    static NativeDhMutableColumn adoptOwner(MemorySegment pointer, NativeDhColumnGroup group) {
        if (pointer.address() == 0) throw new IllegalStateException("Missing native DH replacement");
        return new NativeDhMutableColumn(pointer, group);
    }
    static NativeDhColumnList adopt(MemorySegment pointer) { return adopt(pointer, null); }
    static NativeDhColumnList adopt(MemorySegment pointer, NativeDhColumnGroup group) {
        if (pointer.address() == 0) throw new IllegalStateException("Missing native DH replacement");
        return NativeDhColumnList.fromOwned(new NativeDhMutableColumn(pointer, group));
    }
    NativeDhColumnGroup group() { return group; }
    static void release(MemorySegment pointer) {
        try { RELEASE.invokeExact(pointer); } catch (Throwable e) { throw new IllegalStateException(e); }
    }
    private void refresh() throws Throwable {
        MemorySegment view = METADATA.get();
        if ((int) VIEW.invokeExact(owner, view) != 1) throw new IllegalStateException("Invalid DH column owner");
        long count = view.getAtIndex(ValueLayout.JAVA_LONG, 1);
        if (count < Integer.MIN_VALUE || count > Integer.MAX_VALUE) throw new IllegalStateException("DH column size");
        long address=view.getAtIndex(ValueLayout.JAVA_LONG,0);
        points = MemorySegment.ofAddress(address).reinterpret(Math.max(0, count) * 8, arena, null).asReadOnly();
        extentAddress=address;extentCount=(int)count;
    }
    MemorySegment points() { return points; }
    MemorySegment stageOwner() { return owner; }
    void acceptView(MemorySegment address, long count) { acceptView(address.address(), count); }
    void acceptView(long address, long count) {
        if (count < 0 || count > 4096) throw new IllegalStateException("Invalid DH occlusion extent");
        acceptCodecView(address, count);
    }
    void acceptCodecView(long address, long count) {
        if (count < Integer.MIN_VALUE || count > Integer.MAX_VALUE) throw new IllegalStateException("Invalid DH codec extent");
        if (extentAddress == address && extentCount == count) return;
        points = MemorySegment.ofAddress(address).reinterpret(Math.max(0, count) * 8, arena, null).asReadOnly();
        extentAddress=address;extentCount=(int)count;
    }
    int size() { return extentCount; }
    void resize(int count) {
        try {
            int result = (int) RESIZE.invokeExact(owner, count);
            if (result == 2) throw new ArrayIndexOutOfBoundsException(extentCount);
            if (result == -1) throw new OutOfMemoryError("Cannot resize native DH column");
            if (result != 1) throw new IndexOutOfBoundsException(count);
            refresh();
        } catch (RuntimeException | Error e) { throw e; }
        catch (Throwable e) { throw new IllegalStateException(e); }
        finally { Reference.reachabilityFence(this); }
    }
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
    static int hash(MemorySegment points, int count) {
        try { return (int) HASH.invokeExact(points, count); }
        catch (RuntimeException | Error e) { throw e; } catch (Throwable e) { throw new IllegalStateException(e); }
    }
    int hash() {
        try {
            long result = (long) CACHED_HASH.invokeExact(owner);
            return (result >>> 32) != 0 ? (int) result : (int) OWNER_HASH.invokeExact(owner);
        } catch (RuntimeException | Error e) { throw e; } catch (Throwable e) { throw new IllegalStateException(e); }
        finally { Reference.reachabilityFence(this); }
    }
}
