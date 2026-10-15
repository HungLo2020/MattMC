package net.minecraft.server.level;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Reference;
import net.minecraft.util.NativeLibraryLoader;

/** Native player-loading decisions; Java callers execute one yielded effect at
 * a time. Calls that take locks are ordinary safepoint-capable downcalls. */
final class NativePlayerLoading {
    static final int DONE = 0, RESORT = 1, ENTER = 2, LEAVE = 3, HAS_TICKET = 4, ATTACH = 5, KEY = 6;
    private static final ValueLayout.OfLong J = ValueLayout.JAVA_LONG;
    private static final ValueLayout.OfInt I = ValueLayout.JAVA_INT;
    private static MethodHandle handle(String name, FunctionDescriptor descriptor) {
        return NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_player_loading_" + name, descriptor);
    }
    private static final MethodHandle CREATE = handle("create", FunctionDescriptor.of(J, I));
    private static final MethodHandle RELEASE = handle("release", FunctionDescriptor.ofVoid(J));
    private static final MethodHandle TAKE = handle("take_player", FunctionDescriptor.of(I, J, J, I));
    private static final MethodHandle QUEUE_LEVEL = handle("queue_level", FunctionDescriptor.of(I, J, J));
    private static final MethodHandle SET_QUEUE_LEVEL = handle("set_queue_level", FunctionDescriptor.of(I, J, J, I));
    private static final MethodHandle ADMITS = handle("admits", FunctionDescriptor.of(I, J, J));
    private static final MethodHandle MARK_RELEASE = handle("mark_release", FunctionDescriptor.of(I, J, J));
    private static final MethodHandle RETAINED_BYTES = handle("retained_bytes", FunctionDescriptor.of(J, J));
    private static final MethodHandle WALK = handle("walk", FunctionDescriptor.of(J, J, I, I));
    private static final MethodHandle WALK_OUTPUT = handle("walk_output", FunctionDescriptor.of(ValueLayout.ADDRESS, J));
    private static final MethodHandle WALK_NEXT = handle("walk_next", FunctionDescriptor.of(I, J, I));
    private static final MethodHandle WALK_RELEASE = handle("walk_release", FunctionDescriptor.ofVoid(J));
    private static final MethodHandle ADMISSION_CREATE = handle("admission_create", FunctionDescriptor.of(J, I));
    private static final MethodHandle ADMISSION_RELEASE = handle("admission_release", FunctionDescriptor.ofVoid(J));
    private static final MethodHandle MAY_POP = handle("admission_may_pop", FunctionDescriptor.of(I, J));
    private static final MethodHandle STARTED = handle("admission_started", FunctionDescriptor.of(I, J, J));
    private static final MethodHandle RELEASED = handle("admission_released", FunctionDescriptor.of(I, J, J));
    private static final MethodHandle ADMISSION_WALK = handle("admission_walk", FunctionDescriptor.of(J, J));
    private static final MethodHandle ADMISSION_BYTES = handle("admission_retained_bytes", FunctionDescriptor.of(J, J));

    private static RuntimeException failure(Throwable error) {
        if (error instanceof RuntimeException runtime) return runtime;
        if (error instanceof Error fatal) throw fatal;
        return new IllegalStateException("Native player-loading call failed", error);
    }

    private static void check(int status) {
        if (status == -3) throw new OutOfMemoryError("Native player-loading allocation failed");
        if (status < 0) throw new IllegalStateException("Native player-loading fault: " + status);
    }

    private static void release(MethodHandle method, long id) {
        try { method.invokeExact(id); }
        catch (Throwable error) { throw failure(error); }
    }

    private static final class Owner {
        final long id;
        final MemorySegment memory;

        Owner(MethodHandle create, MethodHandle release, int parameter) {
            long created;
            try { created = (long)create.invokeExact(parameter); }
            catch (Throwable error) { throw failure(error); }
            if (created == 0) throw new OutOfMemoryError("Native player-loading allocation failed");
            try {
                id = created;
                memory = MemorySegment.ofAddress(created).reinterpret(1, Arena.ofAuto(),
                    segment -> NativePlayerLoading.release(release, segment.address()));
            } catch (Throwable error) {
                NativePlayerLoading.release(release, created);
                throw error;
            }
        }
    }

    private final Owner owner;

    NativePlayerLoading(int maximumDistance) { owner = new Owner(CREATE, RELEASE, maximumDistance); }

    /** The graph facade holds its own exclusion; there is no Java replay. */
    void takePlayer(long graph, int field) {
        try { check((int)TAKE.invokeExact(owner.id, graph, field)); }
        catch (Throwable error) { throw failure(error); }
        finally { Reference.reachabilityFence(owner.memory); }
    }

    int queueLevel(long key) {
        try { return (int)QUEUE_LEVEL.invokeExact(owner.id, key); }
        catch (Throwable error) { throw failure(error); }
        finally { Reference.reachabilityFence(owner.memory); }
    }

    void queueLevel(long key, int level) {
        try { check((int)SET_QUEUE_LEVEL.invokeExact(owner.id, key, level)); }
        catch (Throwable error) { throw failure(error); }
        finally { Reference.reachabilityFence(owner.memory); }
    }

    boolean admits(long key) {
        try { return (int)ADMITS.invokeExact(owner.id, key) != 0; }
        catch (Throwable error) { throw failure(error); }
        finally { Reference.reachabilityFence(owner.memory); }
    }

    void markRelease(long key) {
        try { check((int)MARK_RELEASE.invokeExact(owner.id, key)); }
        catch (Throwable error) { throw failure(error); }
        finally { Reference.reachabilityFence(owner.memory); }
    }

    /** Native owner payload and table capacities; excludes Arc/allocator metadata. */
    long retainedBytes() {
        try { return (long)RETAINED_BYTES.invokeExact(owner.id); }
        catch (Throwable error) { throw failure(error); }
        finally { Reference.reachabilityFence(owner.memory); }
    }

    /** 0: changed levels; 1: live view-distance scan; 2: readiness releases. */
    Walk walk(int kind, int target) {
        long created;
        try { created = (long)WALK.invokeExact(owner.id, kind, target); }
        catch (Throwable error) { throw failure(error); }
        finally { Reference.reachabilityFence(owner.memory); }
        return Walk.adopt(created);
    }

    /** Independent native admission state for the public throttling dispatcher. */
    static final class Admission {
        private final Owner owner;

        Admission(int maximum) { owner = new Owner(ADMISSION_CREATE, ADMISSION_RELEASE, maximum); }

        boolean mayPop() {
            try { return (int)MAY_POP.invokeExact(owner.id) != 0; }
            catch (Throwable error) { throw failure(error); }
            finally { Reference.reachabilityFence(owner.memory); }
        }

        void started(long key) {
            try { check((int)STARTED.invokeExact(owner.id, key)); }
            catch (Throwable error) { throw failure(error); }
            finally { Reference.reachabilityFence(owner.memory); }
        }

        void released(long key) {
            try { check((int)RELEASED.invokeExact(owner.id, key)); }
            catch (Throwable error) { throw failure(error); }
            finally { Reference.reachabilityFence(owner.memory); }
        }

        Walk walk() {
            long created;
            try { created = (long)ADMISSION_WALK.invokeExact(owner.id); }
            catch (Throwable error) { throw failure(error); }
            finally { Reference.reachabilityFence(owner.memory); }
            return Walk.adopt(created);
        }

        long retainedBytes() {
            try { return (long)ADMISSION_BYTES.invokeExact(owner.id); }
            catch (Throwable error) { throw failure(error); }
            finally { Reference.reachabilityFence(owner.memory); }
        }
    }

    /** Native Arc keeps the owner alive independently. Output is numeric storage
     * private to this invocation, borrowed only until its next step or close. */
    static final class Walk implements AutoCloseable {
        private final long id;
        private final MemorySegment output;
        private final Thread thread = Thread.currentThread();
        private boolean closed;

        private Walk(long id) throws Throwable {
            this.id = id;
            output = ((MemorySegment)WALK_OUTPUT.invokeExact(id)).reinterpret(4L * Long.BYTES).asReadOnly();
        }

        private static Walk adopt(long id) {
            if (id == 0) throw new OutOfMemoryError("Native player-loading walk allocation failed");
            try { return new Walk(id); }
            catch (Throwable error) {
                release(WALK_RELEASE, id);
                throw failure(error);
            }
        }

        int next(int answer) {
            ensureOpen();
            int status;
            try { status = (int)WALK_NEXT.invokeExact(id, answer); }
            catch (Throwable error) { throw failure(error); }
            check(status);
            if (status == 10) throw new ArrayIndexOutOfBoundsException("Index " + key() + " out of bounds for length " + level());
            if (status == 11) throw new NullPointerException("Cannot invoke \"it.unimi.dsi.fastutil.longs.LongArrayList.getLong(int)\" because \"this.wrapped\" is null");
            if (status == 12) throw new IndexOutOfBoundsException("Index (" + key() + ") is greater than or equal to list size (" + level() + ")");
            return status;
        }

        private void ensureThread() {
            if (Thread.currentThread() != thread) throw new IllegalStateException("Player-loading walk used from another thread");
        }

        private void ensureOpen() {
            ensureThread();
            if (closed) throw new IllegalStateException("Player-loading walk closed");
        }

        long key() { ensureOpen(); return output.getAtIndex(J, 0); }
        int level() { ensureOpen(); return (int)output.getAtIndex(J, 1); }

        @Override
        public void close() {
            ensureThread();
            if (!closed) {
                closed = true;
                release(WALK_RELEASE, id);
            }
        }
    }
}
