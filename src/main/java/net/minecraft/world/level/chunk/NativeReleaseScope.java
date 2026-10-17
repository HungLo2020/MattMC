package net.minecraft.world.level.chunk;

import java.lang.foreign.Arena;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import net.logging.LogUtils;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Releases a short-lived workload's native chunk storage explicitly, all at once.
 *
 * <p>While a scope is open on a thread, the section block and biome owners,
 * counters, chunk slabs and chunk inputs created on that thread are bound to
 * the scope's shared arena, with their view segments, instead of being freed by
 * the GC-driven {@link NativeReclaimer}. Closing the scope closes the arena,
 * which frees them all. A read after that throws FFM's "already closed"
 * exception rather than reading freed memory. Rust items stay reference
 * counted, so native consumers that retain one keep it alive.
 *
 * <p>Use it only around work whose chunks provably do not escape it. Today
 * that is one DH batch generation event ({@code GenerationEvent}): every chunk
 * is an event-local copy, and its results are copied into LOD sources before
 * the event returns. Objects created on other threads (vanilla's parallel noise
 * steps) keep the ordinary reclaimer. {@code -Dmattmc.dh.eventReleaseScope=false}
 * turns scopes off.
 */
public final class NativeReleaseScope implements AutoCloseable {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final boolean ENABLED = !"false".equals(System.getProperty("mattmc.dh.eventReleaseScope"));
    private static final ThreadLocal<NativeReleaseScope> CURRENT = new ThreadLocal<>();
    /** Arenas whose close found a segment still in use; retried by later closes. */
    private static final ConcurrentLinkedQueue<Arena> DEFERRED = new ConcurrentLinkedQueue<>();
    private static final AtomicLong CLOSED = new AtomicLong(), DEFERRED_CLOSES = new AtomicLong();

    private final Arena arena = Arena.ofShared();
    private final Thread thread = Thread.currentThread();
    @Nullable private final NativeReleaseScope previous;
    private boolean closed;

    private NativeReleaseScope(@Nullable NativeReleaseScope previous) {
        this.previous = previous;
    }

    /** Opens a scope on this thread, or returns null when scopes are disabled. */
    public static @Nullable NativeReleaseScope open() {
        if (!ENABLED) return null;
        var scope = new NativeReleaseScope(CURRENT.get());
        CURRENT.set(scope);
        return scope;
    }

    /** The arena native chunk storage created now should be bound to, or null outside a scope. */
    static @Nullable Arena current() {
        var scope = CURRENT.get();
        return scope == null ? null : scope.arena;
    }

    /** Scopes closed, and closes that had to be retried (diagnostics and tests). */
    public static long closed() { return CLOSED.get(); }
    public static long deferredCloses() { return DEFERRED_CLOSES.get(); }

    @Override public void close() {
        if (this.closed) return;
        if (Thread.currentThread() != this.thread) throw new IllegalStateException("Release scope closed on another thread");
        this.closed = true;
        if (CURRENT.get() == this) {
            if (this.previous == null) CURRENT.remove(); else CURRENT.set(this.previous);
        }
        for (Arena pending; (pending = DEFERRED.poll()) != null; ) {
            if (!tryClose(pending)) { DEFERRED.add(pending); break; }
        }
        if (tryClose(this.arena)) CLOSED.incrementAndGet();
        else {
            DEFERRED_CLOSES.incrementAndGet();
            DEFERRED.add(this.arena);
        }
    }

    private static boolean tryClose(Arena arena) {
        try {
            arena.close();
            return true;
        } catch (IllegalStateException inUse) {
            // Another thread is still inside a call on one of its segments.
            if (DEFERRED_CLOSES.get() == 0) LOGGER.warn("Native release scope still in use; closing it later", inUse);
            return false;
        }
    }
}
