package net.minecraft.server.level;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Reference;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.util.NativeLibraryLoader;

/** Actual native counter resource; no Ticket or storage backreference. */
final class NativeTicket {
    static final long CELL_BYTES = 48;
    private static final MemorySegment CELLS = MemorySegment.NULL.reinterpret(Long.MAX_VALUE);
    private static final MethodHandle RESERVE = call("resource_reserve", FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG), false);
    private static final MethodHandle ADOPT = call("resource_adopt", FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT), false);
    private static final MethodHandle RELEASE = call("resource_release", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT), false);
    private static final MethodHandle ADDRESS = call("address", FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT), true);
    private static final MethodHandle SET = call("set", FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG), true);
    private static final MethodHandle DECREASE = call("decrease", FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG), true);
    private static final MethodHandle BIND = call("bind_resource", FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT), true);
    private static final MethodHandle INITIAL_TIMED_OUT = call("timed_out_value", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG), true);
    private static final MethodHandle TIMED_OUT = call("timed_out", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG), true);

    private static MethodHandle call(String name, FunctionDescriptor descriptor, boolean critical) {
        return critical ? NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_ticket_cells_" + name, descriptor, Linker.Option.critical(false))
            : NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_ticket_cells_" + name, descriptor);
    }
    private static RuntimeException failure(Throwable t) {
        if (t instanceof RuntimeException r) return r;
        if (t instanceof Error e) throw e;
        return new IllegalStateException("Native ticket counter failed", t);
    }
    static MemorySegment reserve(long initial) { return create(initial, -1); }
    static MemorySegment adopt(int cell) { return create(0, cell); }
    private static MemorySegment create(long initial, int existing) {
        Arena arena = Arena.ofAuto();
        Release cleanup = new Release();
        long cell;
        try { cell = existing < 0 ? (long)RESERVE.invokeExact(initial) : (long)ADOPT.invokeExact(existing); }
        catch (Throwable t) { throw failure(t); }
        if (cell < 0) throw new OutOfMemoryError("Native ticket counter allocation failed");
        cleanup.cell.set((int)cell);
        try {
            long address = (long)ADDRESS.invokeExact((int)cell);
            return MemorySegment.ofAddress(address).reinterpret(CELL_BYTES, arena, cleanup).asReadOnly();
        } catch (Throwable t) {
            cleanup.run();
            throw failure(t);
        }
    }
    private static final class Release implements java.util.function.Consumer<MemorySegment>, Runnable {
        private final AtomicInteger cell = new AtomicInteger(-1);
        public void accept(MemorySegment ignored) { run(); }
        public void run() {
            int owned = cell.getAndSet(-1);
            if (owned < 0) return;
            try { RELEASE.invokeExact(owned); }
            catch (Throwable t) { throw failure(t); }
        }
    }
    static long ticks(MemorySegment owner) {
        try { return CELLS.get(ValueLayout.JAVA_LONG, owner.address()); }
        finally { Reference.reachabilityFence(owner); }
    }
    static long historical(MemorySegment owner) {
        try { return CELLS.get(ValueLayout.JAVA_LONG, owner.address() + 16); }
        finally { Reference.reachabilityFence(owner); }
    }
    static int boundCell(MemorySegment owner) {
        try { return CELLS.get(ValueLayout.JAVA_INT, owner.address() + 24); }
        finally { Reference.reachabilityFence(owner); }
    }
    static int resourceCell(MemorySegment owner) {
        try { return CELLS.get(ValueLayout.JAVA_INT, owner.address() + 28); }
        finally { Reference.reachabilityFence(owner); }
    }
    static void set(MemorySegment owner, long value) {
        try { SET.invokeExact(owner.address(), value); }
        catch (Throwable t) { throw failure(t); }
        finally { Reference.reachabilityFence(owner); }
    }
    static void decrease(MemorySegment owner) {
        try { DECREASE.invokeExact(owner.address()); }
        catch (Throwable t) { throw failure(t); }
        finally { Reference.reachabilityFence(owner); }
    }
    static boolean initialTimedOut(long initial) {
        try { return (int)INITIAL_TIMED_OUT.invokeExact(initial) != 0; }
        catch (Throwable t) { throw failure(t); }
    }
    static boolean timedOut(MemorySegment owner) {
        try { return (int)TIMED_OUT.invokeExact(owner.address()) != 0; }
        catch (Throwable t) { throw failure(t); }
        finally { Reference.reachabilityFence(owner); }
    }
    static void bind(MemorySegment owner, int cell) {
        try { BIND.invokeExact(owner.address(), cell); }
        catch (Throwable t) { throw failure(t); }
        finally { Reference.reachabilityFence(owner); }
    }
}
