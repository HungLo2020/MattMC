package net.minecraft.world.level.chunk;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.lang.foreign.StructLayout;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import net.logging.LogUtils;
import net.minecraft.util.NativeLibraryLoader;
import org.slf4j.Logger;

/**
 * Releases chunk-section native objects once their Java owner is unreachable.
 *
 * <p>Each registration is one phantom reference holding the raw pointer, and
 * a dedicated thread releases dead references in batches through
 * {@code mattmc_native_release_batch} (Rust {@code world/level/chunk/reclaim.rs}).
 * An {@code Arena.ofAuto()} per object instead costs an implicit session, a
 * shared resource list, cleanup actions and a JDK {@code Common-Cleaner}
 * registration, and that single shared thread fell millions of releases
 * behind during world generation, retaining gigabytes of native memory.
 *
 * <p>The referent decides the lifetime: register the segment or object that
 * every reader of the native memory holds (an owner's segment, a view), and
 * keep using segments derived from it only while the referent is reachable.
 */
final class NativeReclaimer {
    static final byte LIVE_SECTION = 1;
    static final byte LIVE_SECTION_VIEW = 2;
    static final byte LIVE_BIOME = 3;
    static final byte LIVE_BIOME_VIEW = 4;
    static final byte SECTION_COUNTERS = 5;
    static final byte DH_CONTRIBUTORS = 6;
    private static final int KINDS = 7;

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int BATCH = 1024;
    private static final MethodHandle RELEASE_BATCH = NativeLibraryLoader.downcallHandle("mattmc_rust",
        "mattmc_native_release_batch", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    private static final ReferenceQueue<Object> QUEUE = new ReferenceQueue<>();
    /** Keeps each registration reachable until it is released. */
    private static final Set<Owner> LIVE = ConcurrentHashMap.newKeySet();
    private static final AtomicLong RELEASED = new AtomicLong();
    private static final AtomicLong REGISTRATIONS = new AtomicLong();
    private static final LongAdder[] REGISTERED_BY_KIND = new LongAdder[KINDS];
    /** Unreleased registrations (each holds roughly 1.5 KB of native memory) allowed to accumulate between pressure collections. */
    private static final int GC_STEP = Math.max(1024, Integer.getInteger("mattmc.nativeReclaimer.gcStep", 262_144));
    private static final long MIN_GC_INTERVAL_MS = 1000;
    private static final Semaphore GC_REQUEST = new Semaphore(0);
    private static volatile int gcMark = GC_STEP;

    private static final class Owner extends PhantomReference<Object> {
        final long address;
        final byte kind;

        Owner(Object referent, long address, byte kind) {
            super(referent, QUEUE);
            this.address = address;
            this.kind = kind;
        }
    }

    static {
        for (int i = 0; i < KINDS; i++) REGISTERED_BY_KIND[i] = new LongAdder();
        start(NativeReclaimer::drain, "MattMC-Native-Reclaimer");
        start(NativeReclaimer::collectUnderPressure, "MattMC-Native-Reclaimer-GC");
        if (Boolean.getBoolean("mattmc.dev.nativeReclaimerStats")) start(NativeReclaimer::logStats, "MattMC-Native-Reclaimer-Stats");
    }

    private NativeReclaimer() {
    }

    private static void start(Runnable body, String name) {
        Thread thread = new Thread(body, name);
        thread.setDaemon(true);
        thread.start();
    }

    /** Releases {@code pointer} (a reference of {@code kind}) once {@code referent} is unreachable. */
    static void register(Object referent, MemorySegment pointer, byte kind) {
        if (referent == null || pointer.address() == 0) throw new IllegalArgumentException("Missing native owner");
        LIVE.add(new Owner(referent, pointer.address(), kind));
        REGISTERED_BY_KIND[kind].increment();
        if ((REGISTRATIONS.incrementAndGet() & 1023) == 0 && LIVE.size() >= gcMark && GC_REQUEST.availablePermits() == 0) GC_REQUEST.release();
    }

    /** Registrations not yet released, and releases so far (diagnostics and tests). */
    static int pending() { return LIVE.size(); }
    static long released() { return RELEASED.get(); }

    private static void drain() {
        // Thread-confined scratch, allocated once for the process.
        MemorySegment kinds = Arena.global().allocate(BATCH);
        MemorySegment pointers = Arena.global().allocate(ValueLayout.ADDRESS, BATCH);
        while (true) {
            try {
                int count = 0;
                Owner owner = (Owner)QUEUE.remove();
                while (owner != null) {
                    LIVE.remove(owner);
                    kinds.set(ValueLayout.JAVA_BYTE, count, owner.kind);
                    pointers.setAtIndex(ValueLayout.JAVA_LONG, count, owner.address);
                    if (++count == BATCH) break;
                    owner = (Owner)QUEUE.poll();
                }
                int released = (int)RELEASE_BATCH.invokeExact(kinds, pointers, count);
                RELEASED.addAndGet(released);
                if (released != count) {
                    LOGGER.error("Native reclaimer stopped at unknown kind {}; {} pointers leaked", kinds.get(ValueLayout.JAVA_BYTE, released), count - released);
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            } catch (Throwable failure) {
                LOGGER.error("Native reclaimer batch failed", failure);
            }
        }
    }

    /**
     * Native memory creates no heap pressure, so ZGC can go minutes without the
     * old-generation collection that discovers dead promoted owners, and DH
     * world generation then retained gigabytes of dead sections. As the JDK
     * does for direct buffers, request a collection once unreleased
     * registrations grow {@link #GC_STEP} past the level the previous one left.
     * The collection is concurrent; only this thread waits for it.
     */
    private static void collectUnderPressure() {
        while (true) {
            try {
                GC_REQUEST.acquire();
                GC_REQUEST.drainPermits();
                long started = System.nanoTime();
                int before = LIVE.size();
                System.gc();
                // Let the drainer release what the collection discovered before setting the next mark.
                for (int i = 0; i < 30; i++) {
                    long released = RELEASED.get();
                    Thread.sleep(100);
                    if (RELEASED.get() == released) break;
                }
                int after = LIVE.size();
                gcMark = after + GC_STEP;
                LOGGER.debug("Native reclaimer collection: {} -> {} pending in {} ms", before, after, (System.nanoTime() - started) / 1_000_000);
                Thread.sleep(MIN_GC_INTERVAL_MS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            } catch (Throwable failure) {
                LOGGER.error("Native reclaimer collection failed", failure);
            }
        }
    }

    /** {@code -Dmattmc.dev.nativeReclaimerStats=true}: registrations and glibc malloc totals every 5 seconds. */
    private static void logStats() {
        StructLayout mallinfo2 = MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("arena"), ValueLayout.JAVA_LONG.withName("ordblks"),
            ValueLayout.JAVA_LONG.withName("smblks"), ValueLayout.JAVA_LONG.withName("hblks"), ValueLayout.JAVA_LONG.withName("hblkhd"),
            ValueLayout.JAVA_LONG.withName("usmblks"), ValueLayout.JAVA_LONG.withName("fsmblks"), ValueLayout.JAVA_LONG.withName("uordblks"),
            ValueLayout.JAVA_LONG.withName("fordblks"), ValueLayout.JAVA_LONG.withName("keepcost"));
        Linker linker = Linker.nativeLinker();
        MethodHandle info = linker.defaultLookup().find("mallinfo2").map(symbol -> linker.downcallHandle(symbol, FunctionDescriptor.of(mallinfo2))).orElse(null);
        MemorySegment out = Arena.global().allocate(mallinfo2);
        SegmentAllocator into = (size, alignment) -> out;
        while (true) {
            try {
                Thread.sleep(5000);
                StringBuilder kinds = new StringBuilder();
                for (int i = 1; i < KINDS; i++) kinds.append(i == 1 ? "" : ",").append(REGISTERED_BY_KIND[i].sum());
                LOGGER.info("Native reclaimer stats pending={} released={} registered={}", LIVE.size(), RELEASED.get(), kinds);
                if (info != null) {
                    var result = (MemorySegment)info.invokeExact(into);
                    LOGGER.info("Native reclaimer malloc arenaMB={} inUseMB={} freeMB={} mmapMB={}", result.get(ValueLayout.JAVA_LONG, 0) >> 20,
                        result.get(ValueLayout.JAVA_LONG, 56) >> 20, result.get(ValueLayout.JAVA_LONG, 64) >> 20, result.get(ValueLayout.JAVA_LONG, 32) >> 20);
                }
            } catch (InterruptedException interrupted) {
                return;
            } catch (Throwable failure) {
                LOGGER.warn("Native reclaimer stats failed", failure);
                return;
            }
        }
    }
}
