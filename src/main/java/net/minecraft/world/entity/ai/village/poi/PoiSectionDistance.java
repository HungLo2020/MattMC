package net.minecraft.world.entity.ai.village.poi;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Reference;
import java.util.concurrent.locks.ReentrantLock;
import net.minecraft.util.NativeLibraryLoader;

/** Lifetime and exception adapter for the native-owned POI section distance
 * graph and its mirror of {@code PoiManager.isVillageCenter}. Callers use one
 * instance exclusively, as with the original tracker; a lock still serializes
 * native access. Sinks must not reenter this instance. */
final class PoiSectionDistance {
    private static final MethodHandle CREATE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_poi_distance_create",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG));
    private static final MethodHandle RELEASE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_poi_distance_release",
        FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
    private static final MethodHandle APPLY = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_poi_distance_apply",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT));
    private static final MethodHandle RUN = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_poi_distance_run",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT));
    // Copies already-recorded pairs into a heap array; it never allocates or blocks.
    private static final MethodHandle DRAIN = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_poi_distance_drain",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT),
        Linker.Option.critical(true));
    private static final int SEED = 0;
    private static final int SECTION_CHANGED = 1;
    private static final int CLEAR_CENTRES = 2;
    private final ReentrantLock access = new ReentrantLock();
    private final MemorySegment handle;
    private final long handleId;
    private boolean work;
    private long[] changes = new long[512];

    @FunctionalInterface
    interface LevelSink {
        void setLevel(long position, int level);
    }

    PoiSectionDistance() {
        long id;
        try { id = (long)CREATE.invokeExact(); }
        catch (Throwable error) { throw new IllegalStateException("Cannot create POI section distance", error); }
        if (id == 0) throw new OutOfMemoryError("Native POI section distance allocation failed");
        MemorySegment pointer = MemorySegment.ofAddress(id);
        try {
            this.handleId = id;
            this.handle = pointer.reinterpret(1, Arena.ofAuto(), PoiSectionDistance::release);
        } catch (Throwable error) {
            release(pointer);
            throw error;
        }
    }

    private static void release(MemorySegment pointer) {
        try { RELEASE.invokeExact(pointer.address()); }
        catch (Throwable error) { throw new IllegalStateException("Cannot release POI section distance", error); }
    }

    /** {@code update(pos, getLevelFromSource(pos), false)} with the section's current village-centre state. */
    void sectionChanged(long position, boolean villageCenter) {
        this.apply(SECTION_CHANGED, position, villageCenter);
    }

    /** Records a section's village-centre state without scheduling graph work. */
    void seed(long position, boolean villageCenter) {
        this.apply(SEED, position, villageCenter);
    }

    /** Forgets every recorded centre; callers reseed the current ones. */
    void clearCentres() {
        this.apply(CLEAR_CENTRES, 0L, false);
    }

    private void apply(int operation, long position, boolean villageCenter) {
        this.access.lock();
        try {
            long status;
            try { status = (long)APPLY.invokeExact(this.handleId, operation, position, villageCenter ? 1 : 0); }
            catch (Throwable error) { throw new IllegalStateException("Cannot update POI section distance", error); }
            finally { Reference.reachabilityFence(this.handle); }
            this.check(status);
        } finally { this.access.unlock(); }
    }

    /** {@code runUpdates(budget)}, then replays its {@code setLevel} calls into
     * {@code sink} in their original order; returns the remaining budget. */
    int runUpdates(int budget, LevelSink sink) {
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
                if (count < 0) throw new IllegalStateException("POI section distance changes were not drained");
                changes = this.changes;
            }
            catch (RuntimeException | Error error) { throw error; }
            catch (Throwable error) { throw new IllegalStateException("Cannot run POI section distance", error); }
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
            case 3: throw new OutOfMemoryError("Native POI section distance allocation failed");
            default: throw new IllegalStateException("POI section distance failed: " + (status & 255));
        }
    }
}
