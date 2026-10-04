package net.minecraft.server.level;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Reference;
import java.util.concurrent.locks.ReentrantLock;
import net.minecraft.util.NativeLibraryLoader;

/** Lifetime and exception adapter for the native-owned simulation distance
 * graph and its mirror of each chunk's lowest simulating ticket level. Callers
 * use one instance exclusively, as with the original tracker; a lock still
 * serializes native access. Sinks must not reenter this instance. */
final class SimulationChunkDistance {
    private static final MethodHandle CREATE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_simulation_distance_create",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT));
    private static final MethodHandle RELEASE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_simulation_distance_release",
        FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
    private static final MethodHandle SEED = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_simulation_distance_seed",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT));
    private static final MethodHandle UPDATE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_simulation_distance_update",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
    private static final MethodHandle RUN = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_simulation_distance_run",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT));
    // Copies already-recorded pairs into a heap array; it never allocates or blocks.
    private static final MethodHandle DRAIN = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_simulation_distance_drain",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT),
        Linker.Option.critical(true));
    private final ReentrantLock access = new ReentrantLock();
    private final MemorySegment handle;
    private final long handleId;
    private boolean work;
    private long[] changes = new long[512];

    SimulationChunkDistance(int absentTicketLevel) {
        long id;
        try { id = (long)CREATE.invokeExact(absentTicketLevel); }
        catch (Throwable error) { throw new IllegalStateException("Cannot create simulation chunk distance", error); }
        if (id == 0) throw new OutOfMemoryError("Native simulation chunk distance allocation failed");
        MemorySegment pointer = MemorySegment.ofAddress(id);
        try {
            this.handleId = id;
            this.handle = pointer.reinterpret(1, Arena.ofAuto(), SimulationChunkDistance::release);
        } catch (Throwable error) {
            release(pointer);
            throw error;
        }
    }

    private static void release(MemorySegment pointer) {
        try { RELEASE.invokeExact(pointer.address()); }
        catch (Throwable error) { throw new IllegalStateException("Cannot release simulation chunk distance", error); }
    }

    boolean hasWork() {
        return this.work;
    }

    /** Records a chunk's current ticket level without scheduling graph work. */
    void seed(long position, int ticketLevel) {
        this.access.lock();
        try {
            long status;
            try { status = (long)SEED.invokeExact(this.handleId, position, ticketLevel); }
            catch (Throwable error) { throw new IllegalStateException("Cannot seed simulation chunk distance", error); }
            finally { Reference.reachabilityFence(this.handle); }
            this.check(status);
        } finally { this.access.unlock(); }
    }

    /** {@code update(pos, level, decrease)} with the storage's current ticket level at {@code pos}. */
    void update(long position, int ticketLevel, int level, boolean decrease) {
        this.access.lock();
        try {
            long status;
            try { status = (long)UPDATE.invokeExact(this.handleId, position, ticketLevel, level, decrease ? 1 : 0); }
            catch (Throwable error) { throw new IllegalStateException("Cannot update simulation chunk distance", error); }
            finally { Reference.reachabilityFence(this.handle); }
            this.check(status);
        } finally { this.access.unlock(); }
    }

    /** {@code runUpdates(Integer.MAX_VALUE)}, then replays its {@code setLevel}
     * calls into {@code sink} in their original order. */
    void runAllUpdates(PlayerChunkDistances.LevelSink sink) {
        if (!this.work) return;
        int count;
        long[] changes;
        this.access.lock();
        try {
            try {
                long status = (long)RUN.invokeExact(this.handleId, Integer.MAX_VALUE);
                this.check(status);
                count = (int)(status >>> 32);
                if (count * 2L > this.changes.length) this.changes = new long[Math.max(count * 2, this.changes.length * 2)];
                changes = this.changes;
                int drained = (int)DRAIN.invokeExact(this.handleId, MemorySegment.ofArray(changes), changes.length / 2);
                if (drained != count) throw new IllegalStateException("Simulation chunk distance changes were not drained");
            }
            catch (RuntimeException | Error error) { throw error; }
            catch (Throwable error) { throw new IllegalStateException("Cannot run simulation chunk distance", error); }
            finally { Reference.reachabilityFence(this.handle); }
        } finally { this.access.unlock(); }
        for (int index = 0; index < count; index++) {
            sink.setLevel(changes[index * 2], (int)changes[index * 2 + 1]);
        }
    }

    private void check(long status) {
        this.work = (status & 256) != 0;
        switch ((int)status & 255) {
            case 0: return;
            case 3: throw new OutOfMemoryError("Native simulation chunk distance allocation failed");
            default: throw new IllegalStateException("Simulation chunk distance queue failed: " + (status & 255));
        }
    }
}
