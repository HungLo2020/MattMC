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

/** Lifetime and exception adapter for the native-owned player chunk-distance
 * graphs of {@link DistanceManager}. Rust owns player presence, both distance
 * graphs, their pending levels and work queues. Each run returns the original
 * ordered {@code setLevel} calls, which trackers replay into their published
 * level views. Like the original trackers, callers use one instance exclusively;
 * a lock still serializes native access. Sinks must not reenter this instance. */
final class PlayerChunkDistances {
    static final int NATURAL_SPAWN = 0;
    static final int PLAYER_TICKETS = 1;
    private static final MethodHandle CREATE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_player_distance_create",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
    private static final MethodHandle RELEASE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_player_distance_release",
        FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
    private static final MethodHandle ENTERED = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_player_distance_entered",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG));
    private static final MethodHandle VACATED = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_player_distance_vacated",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG));
    private static final MethodHandle RUN = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_player_distance_run",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
    // Copies already-recorded pairs into a heap array; it never allocates or blocks.
    private static final MethodHandle DRAIN = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_player_distance_drain",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT),
        Linker.Option.critical(true));
    private final ReentrantLock access = new ReentrantLock();
    private final MemorySegment handle;
    private final long handleId;
    private final int[] maxDistances;
    // Per-field queued-work bits from the last native result.
    private int work;
    private long[] changes = new long[512];

    @FunctionalInterface
    interface LevelSink {
        void setLevel(long position, int level);
    }

    PlayerChunkDistances(int naturalSpawnDistance, int playerTicketDistance) {
        validate(naturalSpawnDistance);
        validate(playerTicketDistance);
        this.maxDistances = new int[]{naturalSpawnDistance, playerTicketDistance};
        long id;
        try { id = (long)CREATE.invokeExact(naturalSpawnDistance, playerTicketDistance); }
        catch (Throwable error) { throw new IllegalStateException("Cannot create player chunk distances", error); }
        if (id == 0) throw new OutOfMemoryError("Native player chunk distance allocation failed");
        MemorySegment pointer = MemorySegment.ofAddress(id);
        try {
            this.handleId = id;
            this.handle = pointer.reinterpret(1, Arena.ofAuto(), PlayerChunkDistances::release);
        } catch (Throwable error) {
            release(pointer);
            throw error;
        }
    }

    // The original tracker passed {@code distance + 2} levels to its graph and queue.
    private static void validate(int distance) {
        int levels = distance + 2;
        if (levels >= 254) throw new IllegalArgumentException("Level count must be < 254.");
        if (levels < 0) throw new NegativeArraySizeException(Integer.toString(levels));
    }

    private static void release(MemorySegment pointer) {
        try { RELEASE.invokeExact(pointer.address()); }
        catch (Throwable error) { throw new IllegalStateException("Cannot release player chunk distances", error); }
    }

    int maxDistance(int field) {
        return this.maxDistances[field];
    }

    boolean hasWork(int field) {
        return (this.work & (1 << field)) != 0;
    }

    /** The chunk now holds a player: every field receives {@code update(pos, 0, true)}. */
    void playerEntered(long position) {
        this.access.lock();
        try {
            long status;
            try { status = (long)ENTERED.invokeExact(this.handleId, position); }
            catch (Throwable error) { throw new IllegalStateException("Cannot update player chunk distances", error); }
            finally { Reference.reachabilityFence(this.handle); }
            this.check(status);
        } finally { this.access.unlock(); }
    }

    /** The chunk's player set emptied: every field receives {@code update(pos, MAX_VALUE, false)}. */
    void chunkVacated(long position) {
        this.access.lock();
        try {
            long status;
            try { status = (long)VACATED.invokeExact(this.handleId, position); }
            catch (Throwable error) { throw new IllegalStateException("Cannot update player chunk distances", error); }
            finally { Reference.reachabilityFence(this.handle); }
            this.check(status);
        } finally { this.access.unlock(); }
    }

    /** {@code runUpdates(Integer.MAX_VALUE)} for one field, then replays its
     * {@code setLevel} calls into {@code sink} in their original order. */
    void runAllUpdates(int field, LevelSink sink) {
        if (!this.hasWork(field)) return;
        int count;
        long[] changes;
        this.access.lock();
        try {
            try {
                long status = (long)RUN.invokeExact(this.handleId, field, Integer.MAX_VALUE);
                this.check(status);
                count = (int)(status >>> 32);
                if (count * 2L > this.changes.length) this.changes = new long[Math.max(count * 2, this.changes.length * 2)];
                changes = this.changes;
                int drained = (int)DRAIN.invokeExact(this.handleId, field, MemorySegment.ofArray(changes), changes.length / 2);
                if (drained != count) throw new IllegalStateException("Player chunk distance changes were not drained");
            }
            catch (RuntimeException | Error error) { throw error; }
            catch (Throwable error) { throw new IllegalStateException("Cannot run player chunk distances", error); }
            finally { Reference.reachabilityFence(this.handle); }
        } finally { this.access.unlock(); }
        for (int index = 0; index < count; index++) {
            sink.setLevel(changes[index * 2], (int)changes[index * 2 + 1]);
        }
    }

    private void check(long status) {
        this.work = (int)(status >>> 8) & 3;
        switch ((int)status & 255) {
            case 0: return;
            case 3: throw new OutOfMemoryError("Native player chunk distance allocation failed");
            case 5: throw new IllegalArgumentException("Unknown player chunk distance field");
            default: throw new IllegalStateException("Player chunk distance queue failed: " + (status & 255));
        }
    }
}
