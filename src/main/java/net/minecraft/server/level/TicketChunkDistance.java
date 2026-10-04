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
import net.minecraft.world.level.ChunkPos;

/** Lifetime and exception adapter for a native-owned ticket distance graph
 * (simulation or loading) and its mirror of each chunk's lowest ticket level of
 * that kind. Callers use one instance exclusively, as with the original
 * trackers; a lock still serializes native access. Sinks must not reenter. */
final class TicketChunkDistance {
    private static final MethodHandle CREATE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_ticket_distance_create",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
    private static final MethodHandle RELEASE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_ticket_distance_release",
        FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
    private static final MethodHandle SEED = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_ticket_distance_seed",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT));
    private static final MethodHandle UPDATE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_ticket_distance_update",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
    private static final MethodHandle RUN = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_ticket_distance_run",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT));
    // Copies already-recorded pairs into a heap array; it never allocates or blocks.
    private static final MethodHandle DRAIN = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_ticket_distance_drain",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT),
        Linker.Option.critical(true));
    static final int SIMULATION = 0;
    static final int LOADING = 1;
    private final ReentrantLock access = new ReentrantLock();
    private final MemorySegment handle;
    private final long handleId;
    private boolean work;
    private long[] changes = new long[512];

    TicketChunkDistance(int kind, int absentTicketLevel) {
        long id;
        try { id = (long)CREATE.invokeExact(kind, absentTicketLevel, ChunkLevel.MAX_LEVEL, ChunkPos.MAX_COORDINATE_VALUE); }
        catch (Throwable error) { throw new IllegalStateException("Cannot create ticket chunk distance", error); }
        if (id == 0) throw new OutOfMemoryError("Native ticket chunk distance allocation failed");
        MemorySegment pointer = MemorySegment.ofAddress(id);
        try {
            this.handleId = id;
            this.handle = pointer.reinterpret(1, Arena.ofAuto(), TicketChunkDistance::release);
        } catch (Throwable error) {
            release(pointer);
            throw error;
        }
    }

    private static void release(MemorySegment pointer) {
        try { RELEASE.invokeExact(pointer.address()); }
        catch (Throwable error) { throw new IllegalStateException("Cannot release ticket chunk distance", error); }
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
            catch (Throwable error) { throw new IllegalStateException("Cannot seed ticket chunk distance", error); }
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
            catch (Throwable error) { throw new IllegalStateException("Cannot update ticket chunk distance", error); }
            finally { Reference.reachabilityFence(this.handle); }
            this.check(status);
        } finally { this.access.unlock(); }
    }

    /** {@code runUpdates(budget)}, then replays its {@code setLevel} calls into
     * {@code sink} in their original order; returns the remaining budget. */
    int runUpdates(int budget, PlayerChunkDistances.LevelSink sink) {
        if (!this.work) return budget;
        int count, remaining;
        long[] changes;
        this.access.lock();
        try {
            try {
                long status = (long)RUN.invokeExact(this.handleId, budget);
                this.check(status);
                remaining = (int)(status >>> 32);
                count = (int)DRAIN.invokeExact(this.handleId, MemorySegment.ofArray(this.changes), this.changes.length / 2);
                if (count < 0) {
                    this.changes = new long[Math.max(-count * 2, this.changes.length * 2)];
                    count = (int)DRAIN.invokeExact(this.handleId, MemorySegment.ofArray(this.changes), this.changes.length / 2);
                }
                if (count < 0) throw new IllegalStateException("Ticket chunk distance changes were not drained");
                changes = this.changes;
            }
            catch (RuntimeException | Error error) { throw error; }
            catch (Throwable error) { throw new IllegalStateException("Cannot run ticket chunk distance", error); }
            finally { Reference.reachabilityFence(this.handle); }
        } finally { this.access.unlock(); }
        for (int index = 0; index < count; index++) {
            sink.setLevel(changes[index * 2], (int)changes[index * 2 + 1]);
        }
        return remaining;
    }

    private void check(long status) {
        this.work = (status & 256) != 0;
        switch ((int)status & 255) {
            case 0: return;
            case 3: throw new OutOfMemoryError("Native ticket chunk distance allocation failed");
            default: throw new IllegalStateException("Ticket chunk distance queue failed: " + (status & 255));
        }
    }
}
