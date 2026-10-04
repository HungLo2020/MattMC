package net.minecraft.world.level.lighting;

import it.unimi.dsi.fastutil.HashCommon;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Reference;
import java.util.NoSuchElementException;
import java.util.concurrent.locks.ReentrantLock;
import net.minecraft.util.NativeLibraryLoader;

/** Lifetime and exception adapter for the native-owned queue. Like the original,
 * callers own a queue exclusively. A reentrant access lease serializes native
 * calls and can cover a graph operation without locking again for each node. */
public class LeveledPriorityQueue {
    private static final MethodHandle CREATE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_light_queue_create",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
    private static final MethodHandle RELEASE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_light_queue_release",
        FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
    private static final MethodHandle APPLY = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_light_queue_apply",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG,
            ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
    // Fast symbols cannot allocate or block; they return 4 before any mutation
    // if the bounded path is unsuitable. Ordinary symbols perform every retry.
    private static final MethodHandle FAST_ENQUEUE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_light_queue_enqueue_fast",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT), Linker.Option.critical(false));
    private static final MethodHandle FAST_DEQUEUE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_light_queue_dequeue_fast",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT), Linker.Option.critical(false));
    private static final MethodHandle FAST_POP = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_light_queue_pop_fast",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS), Linker.Option.critical(true));
    private static final MethodHandle FAST_RESCHEDULE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_light_queue_reschedule_bytes_fast",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT), Linker.Option.critical(false));
    private static final MethodHandle FAST_CANCEL_COMPUTED = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_light_queue_cancel_computed_bytes_fast",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT), Linker.Option.critical(false));
    private static final MethodHandle FAST_ENQUEUE_COMPUTED = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_light_queue_enqueue_computed_bytes_fast",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT), Linker.Option.critical(false));
    private static final MethodHandle ERROR_INDEX = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_light_queue_error_index",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG), Linker.Option.critical(false));
    private final ReentrantLock access = new ReentrantLock();
    private final MemorySegment handle;
    private final long handleId;
    private final MemorySegment output;
    private final long[] popped = new long[1];
    private final MemorySegment popBuffer = MemorySegment.ofArray(this.popped);
    private final int levels;
    // Scalar view returned by Rust after every mutation, including failed scans.
    private int first;

    public LeveledPriorityQueue(int levels, int expected) {
        if (levels < 0) throw new NegativeArraySizeException(Integer.toString(levels));
        if (levels > 0) {
            if (expected < 0) throw new IllegalArgumentException("The expected number of elements must be nonnegative");
            HashCommon.arraySize(expected, 0.5F);
        }
        this.levels = levels;
        this.first = levels;
        long id;
        try { id = (long)CREATE.invokeExact(levels, expected); }
        catch (Throwable error) { throw new IllegalStateException("Cannot create lighting queue", error); }
        if (id == 0) throw new OutOfMemoryError("Native lighting queue allocation failed");
        MemorySegment pointer = MemorySegment.ofAddress(id);
        try {
            Arena arena = Arena.ofAuto();
            this.output = arena.allocate(8, 8);
            this.handleId = id;
            this.handle = pointer.reinterpret(1, arena, LeveledPriorityQueue::release);
        } catch (Throwable error) {
            release(pointer);
            throw error;
        }
    }

    private static void release(MemorySegment pointer) {
        try { RELEASE.invokeExact(pointer.address()); }
        catch (Throwable error) { throw new IllegalStateException("Cannot release lighting queue", error); }
    }

    // Like the original scalar read, this never dereferences native state.
    public boolean isEmpty() { return this.first >= this.levels; }

    public void enqueue(long value, int level) {
        boolean acquired = begin();
        try {
            long result;
            try {
                result = (long)FAST_ENQUEUE.invokeExact(this.handleId, value, level);
                if ((int)result == 4) result = enqueueSlow(value, level);
            }
            catch (Throwable error) { throw new IllegalStateException("Cannot enqueue lighting work", error); }
            finally { Reference.reachabilityFence(this.handle); Reference.reachabilityFence(this.output); }
            this.check(result);
        } finally { if (acquired) this.access.unlock(); }
    }

    public void dequeue(long value, int level, int bound) {
        boolean acquired = begin();
        try {
            long result;
            try {
                result = (long)FAST_DEQUEUE.invokeExact(this.handleId, value, level, bound);
                if ((int)result == 4) result = dequeueSlow(value, level, bound);
            }
            catch (Throwable error) { throw new IllegalStateException("Cannot dequeue lighting work", error); }
            finally { Reference.reachabilityFence(this.handle); Reference.reachabilityFence(this.output); }
            // An invalid bucket fails first; otherwise an overlong scan fails at levels.
            this.check(result);
        } finally { if (acquired) this.access.unlock(); }
    }

    public long removeFirstLong() {
        boolean acquired = begin();
        try {
            MemorySegment output = this.output;
            long result;
            try {
                result = (long)FAST_POP.invokeExact(this.handleId, this.popBuffer);
                if ((int)result == 4) result = popSlow(output);
            }
            catch (Throwable error) { throw new IllegalStateException("Cannot pop lighting work", error); }
            finally { Reference.reachabilityFence(this.handle); Reference.reachabilityFence(this.output); }
            long value = this.popped[0];
            this.check(result);
            return value;
        } finally { if (acquired) this.access.unlock(); }
    }

    void reschedule(long value, int current, int previous, int next) {
        boolean acquired = begin();
        try {
            long result;
            try {
                result = ((current | previous | next) & ~255) != 0 ? 4L
                    : (long)FAST_RESCHEDULE.invokeExact(this.handleId, value, current | (previous << 8) | (next << 16));
                if ((int)result == 4) result = scheduleSlow(3, value, current, ((long)next << 32) | (previous & 0xffffffffL));
            }
            catch (Throwable error) { throw new IllegalStateException("Cannot schedule lighting work", error); }
            finally { Reference.reachabilityFence(this.handle); Reference.reachabilityFence(this.output); }
            this.check(result);
        } finally { if (acquired) this.access.unlock(); }
    }

    void cancelComputed(long value, int current, int computed) {
        boolean acquired = begin();
        try {
            long result;
            try {
                result = ((current | computed) & ~255) != 0 ? 4L
                    : (long)FAST_CANCEL_COMPUTED.invokeExact(this.handleId, value, current | (computed << 8));
                if ((int)result == 4) result = scheduleSlow(4, value, current, (long)computed);
            }
            catch (Throwable error) { throw new IllegalStateException("Cannot schedule lighting work", error); }
            finally { Reference.reachabilityFence(this.handle); Reference.reachabilityFence(this.output); }
            this.check(result);
        } finally { if (acquired) this.access.unlock(); }
    }

    void enqueueComputed(long value, int current, int computed) {
        boolean acquired = begin();
        try {
            long result;
            try {
                result = ((current | computed) & ~255) != 0 ? 4L
                    : (long)FAST_ENQUEUE_COMPUTED.invokeExact(this.handleId, value, current | (computed << 8));
                if ((int)result == 4) result = scheduleSlow(5, value, current, (long)computed);
            }
            catch (Throwable error) { throw new IllegalStateException("Cannot schedule lighting work", error); }
            finally { Reference.reachabilityFence(this.handle); Reference.reachabilityFence(this.output); }
            this.check(result);
        } finally { if (acquired) this.access.unlock(); }
    }

    // Keep allocating retries separate from the hot adapters. Inlining both
    // foreign stubs into every caller makes the surrounding graph unnecessarily
    // large; growth/collision retries still execute synchronously under ownership.
    private long enqueueSlow(long value, int level) throws Throwable {
        return (long)APPLY.invokeExact(this.handleId, 0, value, level, 0L, this.output);
    }

    private long dequeueSlow(long value, int level, int bound) throws Throwable {
        return (long)APPLY.invokeExact(this.handleId, 1, value, level, (long)bound, this.output);
    }

    private long popSlow(MemorySegment output) throws Throwable {
        long result = (long)APPLY.invokeExact(this.handleId, 2, 0L, 0, 0L, output);
        this.popped[0] = output.get(ValueLayout.JAVA_LONG, 0);
        return result;
    }

    private long scheduleSlow(int operation, long value, int current, long details) throws Throwable {
        return (long)APPLY.invokeExact(this.handleId, operation, value, current, details, this.output);
    }

    boolean beginAccess() { return begin(); }
    void endAccess(boolean acquired) { if (acquired) this.access.unlock(); }

    private boolean begin() {
        if (this.access.isHeldByCurrentThread()) return false;
        this.access.lock();
        return true;
    }

    private void check(long result) {
        int nextFirst = (int)(result >>> 32);
        if (this.first != nextFirst) this.first = nextFirst;
        if ((int)result != 0) throwFailure((int)result);
    }

    private int errorIndex() {
        try { return (int)ERROR_INDEX.invokeExact(this.handleId); }
        catch (Throwable error) { throw new IllegalStateException("Cannot read native queue failure", error); }
        finally { Reference.reachabilityFence(this.handle); Reference.reachabilityFence(this.output); }
    }

    private void throwFailure(int code) {
        switch (code) {
            case 1: throw new ArrayIndexOutOfBoundsException("Index " + errorIndex() + " out of bounds for length " + this.levels);
            case 2: throw new NoSuchElementException();
            case 3: throw new OutOfMemoryError("Native lighting queue allocation failed");
            default: throw new IllegalStateException("Invalid native lighting queue status");
        }
    }
}
