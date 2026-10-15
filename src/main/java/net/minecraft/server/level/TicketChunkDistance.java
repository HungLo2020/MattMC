package net.minecraft.server.level;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Reference;
import java.util.function.LongConsumer;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.TicketStorage;

/** Lifetime and exception adapter for a native-owned ticket distance graph
 * (simulation or loading) and its mirror of each chunk's lowest ticket level of
 * that kind. The graph's native lock is its one synchronization domain: every
 * call here takes it, as does the ticket storage notifying the graph directly.
 * No lock is held while the sink runs. */
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
    private final MemorySegment handle;
    private final long handleId;
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

    /** The listener a tracker binds to its storage: the storage notifies this graph natively. */
    TicketStorage.NativeChunkUpdated listener(TicketStorage ticketStorage, TicketStorage.ChunkUpdated update) {
        return new TicketStorage.NativeChunkUpdated() {
            @Override
            public void update(long l, int i, boolean bl) {
                update.update(l, i, bl);
            }

            @Override
            public TicketStorage ticketStorage() {
                return ticketStorage;
            }

            @Override
            public void withGraph(LongConsumer binder) {
                try { binder.accept(TicketChunkDistance.this.handleId); }
                finally { Reference.reachabilityFence(TicketChunkDistance.this.handle); }
            }
        };
    }

    /** The native handle, for a registry taking this graph's changes; {@link #fence} after the call. */
    long handleId() {
        return this.handleId;
    }

    void fence() {
        Reference.reachabilityFence(this.handle);
    }

    /** {@code runUpdates(budget)} leaving the recorded changes for a registry to take;
     * returns the remaining budget. */
    int run(int budget) {
        try {
            long status = (long)RUN.invokeExact(this.handleId, budget);
            check(status);
            return (int)(status >>> 32);
        }
        catch (RuntimeException | Error error) { throw error; }
        catch (Throwable error) { throw new IllegalStateException("Cannot run ticket chunk distance", error); }
        finally { Reference.reachabilityFence(this.handle); }
    }

    /** Records a chunk's current ticket level without scheduling graph work. */
    void seed(long position, int ticketLevel) {
        long status;
        try { status = (long)SEED.invokeExact(this.handleId, position, ticketLevel); }
        catch (Throwable error) { throw new IllegalStateException("Cannot seed ticket chunk distance", error); }
        finally { Reference.reachabilityFence(this.handle); }
        check(status);
    }

    /** {@code update(pos, level, decrease)} with the storage's current ticket level at {@code pos}. */
    void update(long position, int ticketLevel, int level, boolean decrease) {
        long status;
        try { status = (long)UPDATE.invokeExact(this.handleId, position, ticketLevel, level, decrease ? 1 : 0); }
        catch (Throwable error) { throw new IllegalStateException("Cannot update ticket chunk distance", error); }
        finally { Reference.reachabilityFence(this.handle); }
        check(status);
    }

    /** {@code runUpdates(budget)}, then replays its {@code setLevel} calls into
     * {@code sink} in their original order; returns the remaining budget. */
    int runUpdates(int budget, PlayerChunkDistances.LevelSink sink) {
        int count, remaining;
        long[] changes;
        try {
            // Queued work may come from storage notifications Java never saw.
            long status = (long)RUN.invokeExact(this.handleId, budget);
            check(status);
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
        for (int index = 0; index < count; index++) {
            sink.setLevel(changes[index * 2], (int)changes[index * 2 + 1]);
        }
        return remaining;
    }

    private static void check(long status) {
        switch ((int)status & 255) {
            case 0: return;
            case 3: throw new OutOfMemoryError("Native ticket chunk distance allocation failed");
            default: throw new IllegalStateException("Ticket chunk distance queue failed: " + (status & 255));
        }
    }
}
