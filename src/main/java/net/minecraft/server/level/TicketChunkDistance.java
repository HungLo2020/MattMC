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

/** Lifetime and exception adapter for native ticket propagation. Operations
 * retain the graph and yield live queries and setters to Java without a native
 * lock or borrowed graph state spanning those effects. Legacy mirror entry
 * points remain available to native storage compatibility consumers. */
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
        Arena arena = Arena.ofAuto();
        Release cleanup = new Release(RELEASE);
        long id;
        try { id = (long)CREATE.invokeExact(kind, absentTicketLevel, ChunkLevel.MAX_LEVEL, ChunkPos.MAX_COORDINATE_VALUE); }
        catch (Throwable error) { throw failure(error); }
        if (id == 0) throw new OutOfMemoryError("Native ticket chunk distance allocation failed");
        cleanup.id.set(id);
        this.handleId = id;
        try { this.handle = MemorySegment.ofAddress(id).reinterpret(1, arena, cleanup).asReadOnly(); }
        catch (Throwable error) { cleanup.run(); throw failure(error); }
    }
    private static RuntimeException failure(Throwable error) {
        if (error instanceof RuntimeException r) return r;
        if (error instanceof Error e) throw e;
        return new IllegalStateException("Native ticket distance call failed", error);
    }
    private static final class Release implements java.util.function.Consumer<MemorySegment>, Runnable {
        final java.util.concurrent.atomic.AtomicLong id = new java.util.concurrent.atomic.AtomicLong();
        final MethodHandle function;
        Release(MethodHandle function) { this.function = function; }
        public void accept(MemorySegment ignored) { run(); }
        public void run() {
            long owned = id.getAndSet(0);
            if (owned == 0) return;
            try { function.invokeExact(owned); }
            catch (Throwable error) { throw failure(error); }
        }
    }

    interface Effects {
        int level(long position);
        int source(long position);
        void set(long position, int level);
    }
    private static final MethodHandle OP_CREATE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_ticket_distance_operation_create",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG,
            ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
    private static final MethodHandle OP_STEP = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_ticket_distance_operation_step",
        FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT));
    private static final MethodHandle OP_RELEASE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_ticket_distance_operation_release",
        FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));

    void updateEffects(long position, int level, boolean decrease, Effects effects) {
        invokeEffects(false, position, level, decrease, 0, effects);
    }
    int runEffects(int budget, Effects effects) { return invokeEffects(true, 0, 0, false, budget, effects); }
    private int invokeEffects(boolean run, long position, int level, boolean decrease, int budget, Effects effects) {
        Arena arena = Arena.ofConfined();
        Release cleanup = new Release(OP_RELEASE);
        long id;
        try { id = (long)OP_CREATE.invokeExact(this.handleId, run ? 1 : 0, position, level, decrease ? 1 : 0, budget); }
        catch (Throwable error) { arena.close(); throw failure(error); }
        finally { Reference.reachabilityFence(this.handle); }
        if (id == 0) { arena.close(); throw new OutOfMemoryError("Native ticket distance operation allocation failed"); }
        cleanup.id.set(id);
        MemorySegment owner;
        try { owner = MemorySegment.ofAddress(id).reinterpret(24, arena, cleanup).asReadOnly(); }
        catch (Throwable error) { cleanup.run(); arena.close(); throw failure(error); }
        try (arena) {
            int answer = 0;
            while (true) {
                try { OP_STEP.invokeExact(id, answer); }
                catch (Throwable error) { throw failure(error); }
                int command = (int)owner.get(ValueLayout.JAVA_LONG, 0);
                long key = owner.get(ValueLayout.JAVA_LONG, 8);
                int argument = (int)owner.get(ValueLayout.JAVA_LONG, 16);
                answer = 0;
                switch (command) {
                    case 1: answer = effects.level(key); break;
                    case 2: answer = effects.source(key); break;
                    case 3: effects.set(key, argument); break;
                    case 4: return argument;
                    case 5: check(argument); throw new IllegalStateException("Unknown native ticket operation error");
                    default: throw new IllegalStateException("Unknown native ticket operation command");
                }
            }
        } finally { Reference.reachabilityFence(this.handle); Reference.reachabilityFence(effects); }
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
