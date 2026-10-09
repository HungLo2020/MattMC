package net.minecraft.world.level.chunk;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.lang.ref.Reference;
import net.minecraft.util.NativeLibraryLoader;

/** Section-local Rust counters; the sole Java projection is a read-only CPU word. */
final class NativeSectionCounters {
    private static MethodHandle handle(String name, FunctionDescriptor descriptor) {
        return NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_section_counters_" + name, descriptor);
    }
    private static final MethodHandle CREATE = handle("create", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
    private static final MethodHandle RELEASE = handle("release", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private static final MethodHandle SET = handle("set", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
    private static final MethodHandle ADJUST = handle("adjust", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
    private static final VarHandle LONG = ValueLayout.JAVA_LONG.varHandle();
    private final MemorySegment owner;
    NativeSectionCounters(long packed) {
        try {
            var pointer = (MemorySegment)CREATE.invokeExact(packed);
            if (pointer.address() == 0) throw new IllegalStateException("Missing section counters");
            try { owner = pointer.asReadOnly().reinterpret(8, Arena.ofAuto(), NativeSectionCounters::release); }
            catch (Throwable failure) { release(pointer); throw failure; }
        } catch (RuntimeException | Error failure) { throw failure; }
        catch (Throwable failure) { throw new IllegalStateException("Cannot create section counters", failure); }
    }
    private static void release(MemorySegment pointer) {
        try { RELEASE.invokeExact(pointer); }
        catch (Throwable failure) { throw new IllegalStateException("Cannot release section counters", failure); }
    }
    MemorySegment owner() { return owner; }
    long packed() {
        try { return (long)LONG.getAcquire(owner, 0L); }
        finally { Reference.reachabilityFence(this); }
    }
    int get(int lane) { return (short)(packed() >>> (lane * 16)); }
    void set(int a, int b, int c) {
        try { SET.invokeExact(owner, a, b, c); }
        catch (RuntimeException | Error failure) { throw failure; }
        catch (Throwable failure) { throw new IllegalStateException("Cannot set section counters", failure); }
        finally { Reference.reachabilityFence(this); }
    }
    void adjust(int lane, int value, boolean replace) {
        try { ADJUST.invokeExact(owner, lane, value, replace ? 1 : 0); }
        catch (RuntimeException | Error failure) { throw failure; }
        catch (Throwable failure) { throw new IllegalStateException("Cannot adjust section counters", failure); }
        finally { Reference.reachabilityFence(this); }
    }
}
