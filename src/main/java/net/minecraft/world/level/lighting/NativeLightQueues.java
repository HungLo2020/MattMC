package net.minecraft.world.level.lighting;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.lang.ref.Reference;
import java.util.NoSuchElementException;
import net.minecraft.util.NativeLibraryLoader;

/** Retained CPU work queues shared by light producers and propagation. */
public final class NativeLightQueues {
    private static final MethodHandle CREATE = handle("create", FunctionDescriptor.of(ValueLayout.ADDRESS));
    private static final MethodHandle RELEASE = handle("release", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private static final MethodHandle VIEW = handle("view", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    private static final MethodHandle ENQUEUE = handle("enqueue", FunctionDescriptor.of(ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG));
    private static final MethodHandle POP = handle("pop", FunctionDescriptor.of(ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
    private static final VarHandle COUNT = ValueLayout.JAVA_INT.varHandle();
    private final Arena lifetime = Arena.ofAuto();
    private final MemorySegment owner;
    private final MemorySegment counts;
    private final MemorySegment popped = Arena.ofAuto().allocate(ValueLayout.JAVA_LONG);

    public NativeLightQueues() {
        try {
            MemorySegment pointer = (MemorySegment) CREATE.invokeExact();
            if (pointer.address() == 0) throw new IllegalStateException("No light queue owner");
            try { owner = pointer.asReadOnly().reinterpret(1, lifetime, NativeLightQueues::release); }
            catch (RuntimeException | Error failure) { release(pointer); throw failure; }
            counts = ((MemorySegment) VIEW.invokeExact(owner)).asReadOnly().reinterpret(8, lifetime, null);
        } catch (Throwable failure) { throw rethrow(failure); }
    }
    private static MethodHandle handle(String suffix, FunctionDescriptor descriptor) {
        return NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_light_work_" + suffix, descriptor);
    }
    private static void release(MemorySegment pointer) {
        try { RELEASE.invokeExact(pointer); }
        catch (Throwable failure) { throw rethrow(failure); }
    }
    private static RuntimeException rethrow(Throwable failure) {
        if (failure instanceof Error error) throw error;
        if (failure instanceof RuntimeException exception) return exception;
        return new IllegalStateException("Native light queues failed", failure);
    }
    int size(int side) {
        try { return (int) COUNT.getAcquire(counts, side * 4L); }
        finally { Reference.reachabilityFence(this); }
    }
    boolean isEmpty(int side) { return size(side) == 0; }
    void enqueue(int side, long position, long entry) {
        try {
            int status = (int) ENQUEUE.invokeExact(owner, side, position, entry);
            if (status != 0) throw new IllegalStateException("Light queue rejected entry: " + status);
        } catch (Throwable failure) { throw rethrow(failure); }
        finally { Reference.reachabilityFence(this); }
    }
    long dequeue(int side) {
        try {
            int status = (int) POP.invokeExact(owner, side, popped);
            if (status == 1) throw new NoSuchElementException();
            if (status != 0) throw new IllegalStateException("Light queue rejected read: " + status);
            return popped.get(ValueLayout.JAVA_LONG, 0);
        } catch (Throwable failure) { throw rethrow(failure); }
        finally { Reference.reachabilityFence(this); }
    }
    /** Borrowed semantic CPU owner; callers fence this object after the call. */
    public MemorySegment ownerForNativeCall() { return owner; }
}
