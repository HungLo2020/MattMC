package net.minecraft.world.level;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Reference;
import java.util.AbstractList;
import java.util.NoSuchElementException;
import java.util.RandomAccess;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.NativeLibraryLoader;

/**
 * Downcalls for the native ticket storage behind {@link TicketStorage}
 * ({@code chunk_distance/ticket_storage}), plus the process-wide binding of
 * stored {@link Ticket} objects to their shared native {@code ticksLeft}
 * cells. Like the original storage it is used by one thread at a time.
 */
final class NativeTicketStorage {
	private static MethodHandle handle(String name, FunctionDescriptor descriptor) {
		return NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_ticket_" + name, descriptor, Linker.Option.critical(false));
	}
	private static MethodHandle heap(String name, FunctionDescriptor descriptor) {
		return NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_ticket_" + name, descriptor, Linker.Option.critical(true));
	}
	private static MethodHandle lifecycle(String name, FunctionDescriptor descriptor) {
		return NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_ticket_" + name, descriptor);
	}
	private static final ValueLayout.OfInt I = ValueLayout.JAVA_INT;
	private static final ValueLayout.OfLong J = ValueLayout.JAVA_LONG;
	private static final MethodHandle CREATE = lifecycle("storage_create", FunctionDescriptor.of(J, I));
	private static final MethodHandle RELEASE = lifecycle("storage_release", FunctionDescriptor.ofVoid(J));
	private static final MethodHandle LISTEN = handle("storage_listen", FunctionDescriptor.ofVoid(J, I, I, J));
	private static final MethodHandle ADD = handle("storage_add_adopt", FunctionDescriptor.of(J, J, J, I, I, I, J, I, J));
    private static final MethodHandle ADD_COMMIT = heap("storage_commit_prepared", FunctionDescriptor.of(J, J, J, I, J, I, I, I, J, I, ValueLayout.ADDRESS));
    private static final MethodHandle CELLS_RESERVE = lifecycle("cells_resource_reserve", FunctionDescriptor.of(J, J));
    private static final long PREPARED = 1L << 31;
	private static final MethodHandle ADD_DEACTIVATED = handle("storage_deactivated_adopt", FunctionDescriptor.of(J, J, J, I, I, I, J, I, J));
	private static final MethodHandle REMOVE = heap("storage_remove", FunctionDescriptor.of(J, J, J, I, I, I, ValueLayout.ADDRESS));
	private static final MethodHandle NOTIFY = handle("storage_notify", FunctionDescriptor.of(J, J, I, J, I, I));
	private static final MethodHandle LIST_LEVEL = handle("storage_list_level", FunctionDescriptor.of(I, J, J, I));
	private static final MethodHandle LEVEL = handle("storage_level", FunctionDescriptor.of(I, J, J, I));
	private static final MethodHandle LIST = handle("storage_list", FunctionDescriptor.of(J, J, J));
	private static final MethodHandle LIST_SIZE = handle("storage_list_size", FunctionDescriptor.of(I, J, J));
	private static final MethodHandle LIST_GET = handle("storage_list_get", FunctionDescriptor.of(I, J, J, I));
	private static final MethodHandle LIST_PUT = heap("storage_list_adopt", FunctionDescriptor.of(J, J, J, I, I, I, I, I, J, I, J, ValueLayout.ADDRESS));
	private static final MethodHandle LIST_REMOVE = heap("storage_list_remove", FunctionDescriptor.of(J, J, J, I, ValueLayout.ADDRESS));
	private static final MethodHandle LOWEST = handle("storage_lowest", FunctionDescriptor.of(I, J, J, I));
	private static final MethodHandle STATE = handle("storage_state", FunctionDescriptor.of(I, J));
	private static final MethodHandle EXPORT = heap("storage_export", FunctionDescriptor.of(I, J, I, I, ValueLayout.ADDRESS, I));
	private static final MethodHandle CLEAR_DEACTIVATED = handle("storage_clear_deactivated", FunctionDescriptor.of(J, J));
	private static final MethodHandle WALK = handle("storage_walk", FunctionDescriptor.of(J, J, I));
	private static final MethodHandle STEP = heap("storage_step", FunctionDescriptor.of(J, J, J, I, ValueLayout.ADDRESS, ValueLayout.ADDRESS, I));
	private static final MethodHandle WALK_END = handle("storage_walk_end", FunctionDescriptor.ofVoid(J));
	private static final MethodHandle BATCH_VALUE = handle("storage_batch_value", FunctionDescriptor.of(J, J, J, I));
	private static final MethodHandle CELLS_PAGE = handle("cells_page", FunctionDescriptor.of(J, I));
	private static final MethodHandle CELLS_DRAIN = heap("cells_drain", FunctionDescriptor.of(I, ValueLayout.ADDRESS, I));
	private static final MethodHandle CELLS_FREE = lifecycle("cells_source_resource_release", FunctionDescriptor.of(I, I));

    private static final MethodHandle ROOTS_ENABLE = lifecycle("storage_roots_enable", FunctionDescriptor.of(J, J));
    private static final MethodHandle ROOT_COUNT = lifecycle("storage_root_count", FunctionDescriptor.of(I, J, I));
    private static final MethodHandle ROOTS_DRAIN = heap("source_roots_drain", FunctionDescriptor.of(I, ValueLayout.ADDRESS, I));

    private static final MethodHandle VM_MAP_BEGIN = handle("vm_map_begin", FunctionDescriptor.of(J, I));
    private static final MethodHandle VM_MAP_NEXT = handle("vm_map_next", FunctionDescriptor.of(J, J, I));
    static long vmMapBegin(int mode) {
        try { return (long)VM_MAP_BEGIN.invokeExact(mode); }
        catch (Throwable error) { throw failure(error); }
    }
    static long vmMapNext(long command, int answer) {
        try { return (long)VM_MAP_NEXT.invokeExact(command, answer); }
        catch (Throwable error) { throw failure(error); }
    }

    private static final MethodHandle CONSUMER_CREATE = lifecycle("consumer_create", FunctionDescriptor.of(J, J, I));
    private static final MethodHandle CONSUMER_STEP = lifecycle("consumer_step", FunctionDescriptor.ofVoid(J, I));
    private static final MethodHandle CONSUMER_RELEASE = lifecycle("consumer_release", FunctionDescriptor.ofVoid(J));
    final class Consumer implements AutoCloseable {
        private final Arena arena = Arena.ofConfined();
        private final MemorySegment state;
        private final long handle;
        private ListRoots listRoots;
        Consumer(int mode) {
            ConsumerRelease cleanup = new ConsumerRelease();
            long created;
            try { created = (long)CONSUMER_CREATE.invokeExact(NativeTicketStorage.this.id, mode); }
            catch (Throwable error) { this.arena.close(); throw failure(error); }
            finally { Reference.reachabilityFence(NativeTicketStorage.this.owner); }
            if (created == 0) { this.arena.close(); throw new OutOfMemoryError("Native ticket consumer allocation failed"); }
            this.handle = created;
            cleanup.handle.set(created);
            try { this.state = MemorySegment.ofAddress(created).reinterpret(24, this.arena, cleanup).asReadOnly(); }
            catch (Throwable error) { cleanup.run(); this.arena.close(); throw failure(error); }
        }
        void step(int answer) {
            try { CONSUMER_STEP.invokeExact(this.handle, answer); }
            catch (Throwable error) { throw failure(error); }
        }
        void captureList() { this.listRoots = NativeTicketStorage.this.rootsForList(this.state.get(J, 16)); }
        int command() { return (int)this.state.get(J, 0); }
        long key() { return this.state.get(J, 8); }
        int argument() { return (int)this.state.get(J, 16); }
        public void close() {
            try { this.arena.close(); }
            finally { Reference.reachabilityFence(this.listRoots); Reference.reachabilityFence(NativeTicketStorage.this.owner); }
        }
    }
    private static final class ConsumerRelease implements java.util.function.Consumer<MemorySegment>, Runnable {
        final java.util.concurrent.atomic.AtomicLong handle = new java.util.concurrent.atomic.AtomicLong();
        public void accept(MemorySegment ignored) { this.run(); }
        public void run() {
            long owned = this.handle.getAndSet(0);
            if (owned == 0) return;
            try { CONSUMER_RELEASE.invokeExact(owned); }
            catch (Throwable error) { throw failure(error); }
            finishNativeRelease();
        }
    }
    Consumer consumer(int mode) { return new Consumer(mode); }

    private static final MethodHandle WALK_LIST = lifecycle("storage_walk_list", FunctionDescriptor.of(J, J, J, ValueLayout.ADDRESS));
    java.util.List<Ticket> walkList(long walk) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment result = arena.allocate(J);
            long status;
            try { status = (long)WALK_LIST.invokeExact(this.id, walk, result); }
            catch (Throwable error) { throw failure(error); }
            finally { Reference.reachabilityFence(this.owner); }
            checkInWalk(status);
            long list = result.get(J, 0);
            return list == -1L ? null : this.view(list);
        }
    }
    private static final MethodHandle NULL_PUT = lifecycle("storage_list_put_null", FunctionDescriptor.of(J, J, J, I, I, ValueLayout.ADDRESS));
    private long putNull(long list, int mode, int index) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment result = arena.allocate(J);
            long status;
            try { status = (long)NULL_PUT.invokeExact(this.id, list, mode, index, result); }
            catch (Throwable error) { throw failure(error); }
            finally { Reference.reachabilityFence(this.owner); }
            this.io[0] = result.get(J, 0);
            return status;
        }
    }
    private static final MethodHandle UNMAP = lifecycle("storage_unmap", FunctionDescriptor.of(J, J, J));
    private static final MethodHandle REMOVAL_NEXT = handle("removal_next", FunctionDescriptor.of(J, J, I));
    static long removalNext(long command, int answer) {
        try { return (long)REMOVAL_NEXT.invokeExact(command, answer); }
        catch (Throwable error) { throw failure(error); }
    }
    void unmap(long key) {
        this.modifications++;
        long status;
        try { status = (long)UNMAP.invokeExact(this.id, key); }
        catch (Throwable error) { throw failure(error); }
        finally { Reference.reachabilityFence(this.owner); }
        check(status);
    }
    java.util.List<Ticket> nullableTickets(long key) {
        long list;
        try { list = (long)LIST.invokeExact(this.id, key); }
        catch (Throwable error) { throw failure(error); }
        finally { Reference.reachabilityFence(this.owner); }
        return list == -1L ? null : this.view(list);
    }

    private static final MethodHandle GET_OR_CREATE = lifecycle("storage_get_or_create", FunctionDescriptor.of(J, J, J, ValueLayout.ADDRESS));
    private static final MethodHandle EXPIRY_NEXT = handle("expiry_next", FunctionDescriptor.of(I, I, I));
    static int expiryNext(int command, int answer) {
        try { return (int)EXPIRY_NEXT.invokeExact(command, answer); }
        catch (Throwable error) { throw failure(error); }
    }
    private static final MethodHandle ADMISSION_INIT = heap("admission_init", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private static final MethodHandle ADMISSION_NEXT = heap("admission_next", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, I));
    static final class Admission implements AutoCloseable {
        private final Arena arena = Arena.ofConfined();
        private final MemorySegment state;
        Admission() {
            try {
                this.state = this.arena.allocate(24, 8);
                ADMISSION_INIT.invokeExact(this.state);
            } catch (Throwable error) { this.arena.close(); throw failure(error); }
        }
        MemorySegment listResult() { return this.state.asSlice(8, 8); }
        int command() { return (int)this.state.get(J, 0); }
        void next(int answer) {
            try { ADMISSION_NEXT.invokeExact(this.state, answer); }
            catch (Throwable error) { throw failure(error); }
        }
        public void close() { this.arena.close(); }
    }

    private static final MethodHandle CURSOR_RESERVE = lifecycle("cursor_reserve", FunctionDescriptor.of(J, J));
    private static final MethodHandle CURSOR_OPEN = lifecycle("cursor_open", FunctionDescriptor.of(J, J, J, J, J, I));
    private static final MethodHandle CURSOR_RESUME = lifecycle("cursor_resume", FunctionDescriptor.of(J, J, J, J, J));
    private static final MethodHandle CURSOR_CLOSE = lifecycle("cursor_close", FunctionDescriptor.of(J, J, J, J));
    private static final MethodHandle CURSOR_ABORT = lifecycle("cursor_abort_binding", FunctionDescriptor.of(J, J, J));
    /** Actual bounded VM resource descriptors, one per native source slot. No
     * Ticket/source backreferences; active state and incarnations stay native. */
    private final it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<MemorySegment> cursorViews = new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>(0);
    static final class LiveCursor implements AutoCloseable {
        private final NativeTicketStorage storage;
        private final MemorySegment state;
        private final long address;
        private long generation;
        final java.util.List<Ticket> list;
        LiveCursor(NativeTicketStorage storage, long value, int mode, java.util.List<Ticket> actualList) {
            this.storage = storage;
            try {
            long created;
            try { created = (long)CURSOR_RESERVE.invokeExact(storage.id); }
            catch (Throwable error) { throw failure(error); }
            finally { Reference.reachabilityFence(storage.owner); }
            if (created == 0) throw new OutOfMemoryError("Native ticket cursor allocation failed");
            this.address = created;
            MemorySegment prepared;
            try {
                synchronized (storage.cursorViews) {
                    prepared = storage.cursorViews.get(created);
                    if (prepared == null) {
                        prepared = MemorySegment.ofAddress(created).reinterpret(80).asReadOnly();
                        storage.cursorViews.put(created, prepared);
                    }
                }
            } catch (Throwable error) {
                try { check((long)CURSOR_ABORT.invokeExact(storage.id, created)); }
                catch (Throwable rollback) { if (rollback != error) error.addSuppressed(rollback); }
                finally { Reference.reachabilityFence(storage.owner); }
                throw failure(error);
            }
            this.state = prepared;
            this.generation = prepared.get(J, 72);
            try {
                if (mode == 0) storage.modifications++;
                long status;
                try { status = (long)CURSOR_OPEN.invokeExact(storage.id, this.address, this.generation, value, mode); }
                finally { Reference.reachabilityFence(storage.owner); }
                check(status);
                long sourceList = this.state.get(J, 16);
                this.list = actualList != null ? actualList : sourceList == -1L && mode == 3 ? null : storage.view(sourceList);
            } catch (Throwable error) {
                try { this.close(); } catch (Throwable rollback) { if (rollback != error) error.addSuppressed(rollback); }
                throw failure(error);
            }
            } finally { Reference.reachabilityFence(storage); }
        }
        private void checkLive() {
            try {
                if (this.generation == 0 || this.state.get(J, 72) != this.generation) throw new IllegalStateException("Retired native ticket cursor");
            } finally { Reference.reachabilityFence(this.storage); }
        }
        private long word() {
            this.checkLive();
            try { return this.state.get(J, 0); }
            finally { Reference.reachabilityFence(this.storage); }
        }
        int command() { return (int)this.word(); }
        boolean captureNative() { return (this.word() & (1L << 33)) != 0; }
        boolean selectPrevious() { return (this.word() & (1L << 32)) != 0; }
        Ticket ticket() {
            this.checkLive();
            try { return Cells.ticket(this.state.get(I, 8)); }
            finally { Reference.reachabilityFence(this.storage); }
        }
        int index() {
            this.checkLive();
            try { return this.state.get(I, 12); }
            finally { Reference.reachabilityFence(this.storage); }
        }
        void next(long answer) {
            this.checkLive();
            try { check((long)CURSOR_RESUME.invokeExact(this.storage.id, this.address, this.generation, answer)); }
            catch (Throwable error) { throw failure(error); }
            finally { Reference.reachabilityFence(this.storage); Reference.reachabilityFence(this.storage.owner); }
        }
        public void close() {
            long owned = this.generation;
            if (owned == 0) return;
            this.generation = 0;
            try { check((long)CURSOR_CLOSE.invokeExact(this.storage.id, this.address, owned)); }
            catch (Throwable error) { throw failure(error); }
            finally { Reference.reachabilityFence(this.list); Reference.reachabilityFence(this.storage); Reference.reachabilityFence(this.storage.owner); }
        }
    }
    LiveCursor admission(long key) { return new LiveCursor(this, key, 0, null); }
    LiveCursor removal(long key) { return new LiveCursor(this, key, 3, null); }
    static LiveCursor query(java.util.List<Ticket> actualList, boolean simulation) {
        if (!(actualList instanceof TicketList nativeList)) return null;
        NativeTicketStorage parent = nativeList.roots.parent.get();
        if (parent == null) return null;
        long current;
        try { current = (long)LIST_SOURCE.invokeExact(parent.id, nativeList.list); }
        catch (Throwable error) { throw failure(error); }
        finally { Reference.reachabilityFence(parent); }
        if (current != nativeList.roots.source) return null;
        return new LiveCursor(parent, nativeList.list, simulation ? 1 : 2, actualList);
    }
    static long levelPair(int stored, int incoming) { return (long)stored << 32 | Integer.toUnsignedLong(incoming); }

    private static final MethodHandle QUERY_BEGIN = handle("query_begin", FunctionDescriptor.of(J, I, I));
    private static final MethodHandle QUERY_NEXT = handle("query_next", FunctionDescriptor.of(J, J, J));
    static long queryBegin(boolean simulation, boolean present) {
        try { return (long)QUERY_BEGIN.invokeExact(simulation ? 1 : 0, present ? 1 : 0); }
        catch (Throwable t) { throw failure(t); }
    }
    static long queryNext(long command, long answer) {
        try { return (long)QUERY_NEXT.invokeExact(command, answer); }
        catch (Throwable t) { throw failure(t); }
    }

	static final int LOADING = 0;
	static final int SIMULATION = 1;
	static final int PURGE = 0;
	static final int DEACTIVATE = 1;
	static final int JAVA_PREDICATE = 4;
    static final int DEACTIVATE_EFFECTS = 5;
    static final int REMOVED_QUERY = 6;
    static final int LIVE_NOTIFY = 7;
    static final int DIRTY_EVENT = 8;
	static final int ACTIVATE = 3;
	static final long RESULT = 1L << 8;
	static final long DIRTY = 1L << 9;
	private static final long PENDING = 1L << 10;
	static final long PAIRED = 1L << 20;
	static final long WANT_SIMULATION = 1L << 14;
	static final long WANT_LOADING = 1L << 15;
	static final int DONE = 1;
	static final int READY = 2;
	static final int BATCH = 3;
	static final int NOTIFY_EVENT = 4;
	static final int ACTIVATE_EVENT = 5;

	static int resume(long status) {
		return (int)(status >>> 12) & 3;
	}

	static int event(long status) {
		return (int)(status >>> 16) & 15;
	}

    /** Actual VM root registry capability; avoids initializing it on empty-store rollback. */
    private static volatile boolean cellsReady;
    static void finishNativeRelease() { if (cellsReady) Cells.drain(); }

	private final MemorySegment owner;
	private final long id;
    private final MapRoots roots = new MapRoots();
	/** Out-values of remove and walk steps. */
	final long[] io = new long[4];
	/**
	 * Most tickets per Java-predicate batch. {@code -Dmattmc.ticketStorage.batch=N}
	 * fixes the size instead (1 is one ticket per call), for parity tests.
	 */
	private static final int BATCH_LIMIT = 256;
	private static final int FIXED_BATCH = Math.min(BATCH_LIMIT, Integer.getInteger("mattmc.ticketStorage.batch", 0));
	/** {@code (key, cell, physical list VM source)} bindings of a predicate batch. */
	final long[] batch = new long[3 * BATCH_LIMIT];
	/** Next batch size: grows while predicates keep returning false. */
	private int batchSize = FIXED_BATCH > 0 ? FIXED_BATCH : 16;
	/**
	 * Counts calls that may change the lists, so a predicate batch knows whether
	 * its remaining tickets are still the ones the original would visit.
	 */
	long modifications;
	private long[] export = new long[64];
	/** One view per list arena index, so an unchanged list keeps its identity. */
	private java.lang.ref.WeakReference<TicketList>[] views = new java.lang.ref.WeakReference[16];

    NativeTicketStorage() {
        Arena arena = Arena.ofAuto();
        long created;
        try { created = (long)CREATE.invokeExact(TicketStorage.ABSENT_LEVEL); }
        catch (Throwable t) { throw failure(t); }
        if (created == 0) throw new OutOfMemoryError("Native ticket storage allocation failed");
        this.id = created;
        NativeRelease cleanup;
        try { cleanup = new NativeRelease(created); }
        catch (Throwable t) { release(created); throw failure(t); }
        try {
            this.owner = MemorySegment.ofAddress(created).reinterpret(1, arena, cleanup);
            long source = (long)ROOTS_ENABLE.invokeExact(created);
            if (source == 0) throw new IllegalStateException("Native ticket root scope initialization failed");
            this.roots.source = source;
            Cells.registerSource(this.roots);
        } catch (Throwable t) { cleanup.run(); throw failure(t); }
    }
    Object generationBinding() { return this.roots; }
    private int rootCount(int cell) {
        try { return (int)ROOT_COUNT.invokeExact(this.id, cell); }
        catch (Throwable error) { throw failure(error); }
        finally { Reference.reachabilityFence(this.owner); }
    }
    private static final MethodHandle LIST_SOURCE = lifecycle("storage_list_source", FunctionDescriptor.of(J, J, J));
    private static final MethodHandle ROOT_LIST = heap("storage_root_list", FunctionDescriptor.of(J, J, J, I, ValueLayout.ADDRESS));
    private static final MethodHandle RESOURCE_RETAIN = lifecycle("list_resource_retain", FunctionDescriptor.of(J, J, J));
    private static final MethodHandle RESOURCE_RELEASE = lifecycle("list_resource_release", FunctionDescriptor.ofVoid(J));
    private static final MethodHandle RESOURCE_SIZE = lifecycle("list_resource_size", FunctionDescriptor.of(I, J));
    private static final MethodHandle RESOURCE_GET = lifecycle("list_resource_get", FunctionDescriptor.of(I, J, I));
    private static final MethodHandle RESOURCE_COUNT = lifecycle("list_resource_root_count", FunctionDescriptor.of(I, J, I));
    private static final MethodHandle RESOURCE_PREPARE = lifecycle("list_resource_prepare", FunctionDescriptor.of(J, J, I, I));
    private static final MethodHandle RESOURCE_PUBLISH = heap("list_resource_publish", FunctionDescriptor.of(J, J, I, I, I, I, I, J, I, ValueLayout.ADDRESS));
    private static final MethodHandle RESOURCE_REMOVE = heap("list_resource_remove", FunctionDescriptor.of(J, J, I, ValueLayout.ADDRESS));
    /** Only VM resource anchors. Native owners count their actual associations. */
    private abstract static class SourceRoots {
        long source;
        abstract void release(long key);
    }
    private static final class MapRoots extends SourceRoots {
        final RootLists lists = new RootLists();
        @Override void release(long key) { this.lists.remove(key); }
    }
    private static final class RootLists extends it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<ListRoots> {
        @Override protected void rehash(int size) { if (size > this.n) super.rehash(size); }
    }
    private static final class ListRoots extends SourceRoots {
        final RootSlots tickets = new RootSlots();
        final MemorySegment owner;
        final long id;
        final long list;
        final java.lang.ref.WeakReference<NativeTicketStorage> parent;
        final long[] io = new long[4];
        ListRoots(NativeTicketStorage parent, long list) {
            this.parent = new java.lang.ref.WeakReference<>(parent);
            this.list = list;
            Arena arena = Arena.ofAuto();
            ListRelease cleanup = new ListRelease();
            long created;
            try { created = (long)RESOURCE_RETAIN.invokeExact(parent.id, list); }
            catch (Throwable error) { throw failure(error); }
            finally { Reference.reachabilityFence(parent); }
            if (created == 0) throw new IllegalStateException("Missing native ticket list resource");
            this.id = created;
            cleanup.handle.set(created);
            try {
                this.owner = MemorySegment.ofAddress(created).reinterpret(16, arena, cleanup).asReadOnly();
                this.source = this.owner.get(J, 8);
                Cells.registerSource(this);
            } catch (Throwable error) { cleanup.run(); throw failure(error); }
        }
        @Override void release(long key) { this.tickets.remove((int)key); }
        int rootCount(int cell) {
            try { return (int)RESOURCE_COUNT.invokeExact(this.id, cell); }
            catch (Throwable error) { throw failure(error); }
            finally { Reference.reachabilityFence(this); }
        }
        void modified() {
            NativeTicketStorage parent = this.parent.get();
            if (parent == null) return;
            try { if ((long)LIST_SOURCE.invokeExact(parent.id, this.list) == this.source) parent.modifications++; }
            catch (Throwable error) { throw failure(error); }
            finally { Reference.reachabilityFence(parent); }
        }
    }
    private static final class ListRelease implements java.util.function.Consumer<MemorySegment>, Runnable {
        final java.util.concurrent.atomic.AtomicLong handle = new java.util.concurrent.atomic.AtomicLong();
        public void accept(MemorySegment ignored) { this.run(); }
        public void run() {
            long owned = this.handle.getAndSet(0);
            if (owned == 0) return;
            try { RESOURCE_RELEASE.invokeExact(owned); }
            catch (Throwable error) { throw failure(error); }
            finishNativeRelease();
        }
    }
    /** Primitive resource keys and no shrinking: release never allocates. */
    private static final class RootSlots extends it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<Ticket> {
        @Override protected void rehash(int size) { if (size > this.n) super.rehash(size); }
    }
    private static final class SourceIndex extends it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<Cells.SourceReference> {
        @Override protected void rehash(int size) { if (size > this.n) super.rehash(size); }
    }
    private ListRoots rootsFor(long key, boolean deactivated) {
        long status;
        try { status = (long)ROOT_LIST.invokeExact(this.id, key, deactivated ? 1 : 0, MemorySegment.ofArray(this.io)); }
        catch (Throwable error) { throw failure(error); }
        finally { Reference.reachabilityFence(this); }
        long list = this.io[0];
        check(status);
        return ((TicketList)this.view(list)).roots;
    }
    private ListRoots rootsForList(long list) { return ((TicketList)this.view(list)).roots; }


    MemorySegment generationOwner() { return this.owner.asReadOnly(); }
    private static final class NativeRelease implements java.util.function.Consumer<MemorySegment>, Runnable {
        private final java.util.concurrent.atomic.AtomicLong handle;
        NativeRelease(long id) { this.handle = new java.util.concurrent.atomic.AtomicLong(id); }
        public void accept(MemorySegment ignored) { run(); }
        public void run() { long owned = handle.getAndSet(0); if (owned != 0) release(owned); }
    }
    private static void release(long handle) {
        try { RELEASE.invokeExact(handle); }
        catch (Throwable t) { throw new IllegalStateException("Cannot release native ticket storage", t); }
        finishNativeRelease();
    }

	private static RuntimeException failure(Throwable t) {
		if (t instanceof RuntimeException r) return r;
		if (t instanceof Error e) throw e;
		return new IllegalStateException("Native ticket storage call failed", t);
	}

	/** Throws the Java exception a status reports, after unpinning released tickets. */
	static long check(long status) {
		if ((status & PENDING) != 0) Cells.drain();
		return fault(status);
	}

	/** {@link #check} without unpinning: a walk unpins once, at its end. */
	static long checkInWalk(long status) {
		return fault(status);
	}

	static boolean released(long status) {
		return (status & PENDING) != 0;
	}

	private static long fault(long status) {
		switch ((int)status & 255) {
			case 0: return status;
			case 3: throw new OutOfMemoryError("Native ticket storage allocation failed");
			case 10: throw new NullPointerException();
			case 11: throw new ArrayIndexOutOfBoundsException();
			case 12: throw new IndexOutOfBoundsException();
			case 13: throw new NoSuchElementException();
			case 14: throw new IllegalStateException();
			default: throw new IllegalStateException("Ticket distance queue failed: " + (status & 255));
		}
	}

	void listen(int which, int java, long graph) {
		try { LISTEN.invokeExact(this.id, which, java, graph); }
		catch (Throwable t) { throw failure(t); }
		finally { Reference.reachabilityFence(this.owner); }
	}

    long add(long key, Ticket ticket) {
        this.modifications++;
        int type = Types.id(ticket.getType());
        int cell = ticket.boundCell();
        int level = ticket.getTicketLevel();
        int flags = Types.flags(type);
        long timeout = ticket.getType().timeout();
        long initial = cell < 0 ? ticket.unboundTicksLeft() : 0L;
        if (cell >= 0) {
            try (Cells.Prepared prepared = Cells.prepareBound(this.rootsFor(key, false), ticket, cell)) {
                long result;
                try { result = (long)ADD.invokeExact(this.id, key, type, level, flags, timeout, cell, initial); }
                catch (Throwable error) { throw failure(error); }
                finally { Reference.reachabilityFence(this.owner); Reference.reachabilityFence(ticket); }
                prepared.finish(result);
                check(result);
                return result;
            }
        }
        long status;
        try { status = (long)ADD.invokeExact(this.id, key, type, level, flags, timeout, cell, initial); }
        catch (Throwable t) { throw failure(t); }
        finally { Reference.reachabilityFence(this.owner); Reference.reachabilityFence(ticket); }
        if ((status & PREPARED) != 0) return check(this.commitPrepared(key, 0, status, ticket, type, level, flags, timeout, initial));
        Cells.bindNew(this.rootsFor(key, false), ticket, status);
        if (cell >= 0 && (status & RESULT) != 0) Cells.pin(this.rootsFor(key, false), ticket);
        return check(status);
    }

    private long commitPrepared(long target, int index, long command, Ticket ticket, int type, int level, int flags, long timeout, long initial) {
        return commitPrepared(target, index, command, ticket, type, level, flags, timeout, initial, null);
    }
    private long commitPrepared(long target, int index, long command, Ticket ticket, int type, int level, int flags, long timeout, long initial, Ticket[] previous) {
        int producer = (int)(command >>> 24) & 3;
        ListRoots roots = producer == 0 ? this.rootsFor(target, false) : producer == 1 ? this.rootsFor(target, true) : this.rootsForList(target);
        try (Cells.Prepared prepared = Cells.prepare(roots, ticket, initial)) {
            long status;
            try { status = (long)ADD_COMMIT.invokeExact(this.id, target, index, command, type, level, flags, timeout, prepared.cell, MemorySegment.ofArray(this.io)); }
            catch (Throwable t) { throw failure(t); }
            finally { Reference.reachabilityFence(this.owner); Reference.reachabilityFence(ticket); }
            if (previous != null && (status & 255) == 0) previous[0] = Cells.ticket((int)this.io[0]);
            prepared.finish(status);
            return status;
        }
    }

    long generationTypeDescriptor() {
        int type = Types.id(TicketType.UNKNOWN);
        return Integer.toUnsignedLong(type) | Integer.toUnsignedLong(Types.flags(type)) << 32;
    }
    void prepareGenerationRequest() { this.modifications++; }
    long completeGenerationRequest(long key, int level, Ticket ticket, long status) {
        if ((status & PREPARED) != 0) {
            int type = Types.id(TicketType.UNKNOWN);
            return check(this.commitPrepared(key, 0, status, ticket, type, level, Types.flags(type), TicketType.UNKNOWN.timeout(), TicketType.UNKNOWN.timeout()));
        }
        Cells.bindNew(this.rootsFor(key, false), ticket, status);
        return check(status);
    }

    void addDeactivated(long key, Ticket ticket) {
        this.modifications++;
        int type = Types.id(Ticket.nativeBindingType(ticket));
        int cell = ticket.boundCell();
        int level = Ticket.nativeBindingLevel(ticket);
        int flags = Types.flags(type);
        long timeout = Ticket.nativeBindingType(ticket).timeout();
        long initial = cell < 0 ? ticket.unboundTicksLeft() : 0L;
        if (cell >= 0) {
            try (Cells.Prepared prepared = Cells.prepareBound(this.rootsFor(key, true), ticket, cell)) {
                long result;
                try { result = (long)ADD_DEACTIVATED.invokeExact(this.id, key, type, level, flags, timeout, cell, initial); }
                catch (Throwable error) { throw failure(error); }
                finally { Reference.reachabilityFence(this.owner); Reference.reachabilityFence(ticket); }
                prepared.finish(result);
                check(result);
                return;
            }
        }
        long status;
        try { status = (long)ADD_DEACTIVATED.invokeExact(this.id, key, type, level, flags, timeout, cell, initial); }
        catch (Throwable t) { throw failure(t); }
        finally { Reference.reachabilityFence(this.owner); Reference.reachabilityFence(ticket); }
        if ((status & PREPARED) != 0) status = this.commitPrepared(key, 0, status, ticket, type, level, flags, timeout, initial);
        else {
            Cells.bindNew(this.rootsFor(key, true), ticket, status);
            if (cell >= 0 && (status & 255) == 0) Cells.pin(this.rootsFor(key, true), ticket);
        }
        check(status);
    }

	/** On success {@code io[0]} is the list removed from. */
	long remove(long key, Ticket ticket) {
		this.modifications++;
		int type = Types.id(ticket.getType());
		long status;
		try { status = (long)REMOVE.invokeExact(this.id, key, type, ticket.getTicketLevel(), Types.flags(type), MemorySegment.ofArray(this.io)); }
		catch (Throwable t) { throw failure(t); }
		finally { Reference.reachabilityFence(this.owner); }
		return check(status);
	}

	/** Delivers to a native tracker; false when the listener is Java's to call. */
	boolean notify(int which, long key, int level, boolean decrease) {
		long status;
		try { status = (long)NOTIFY.invokeExact(this.id, which, key, level, decrease ? 1 : 0); }
		catch (Throwable t) { throw failure(t); }
		finally { Reference.reachabilityFence(this.owner); }
		return (check(status) & RESULT) != 0;
	}

	int listLevel(long list, boolean simulate) {
		try { return (int)LIST_LEVEL.invokeExact(this.id, list, simulate ? 1 : 0); }
		catch (Throwable t) { throw failure(t); }
		finally { Reference.reachabilityFence(this.owner); }
	}

	int level(long key, boolean simulate) {
		try { return (int)LEVEL.invokeExact(this.id, key, simulate ? 1 : 0); }
		catch (Throwable t) { throw failure(t); }
		finally { Reference.reachabilityFence(this.owner); }
	}

	/** The cell of the lowest ticket of the kind at {@code key}, or -1. */
	int lowest(long key, boolean simulate) {
		try { return (int)LOWEST.invokeExact(this.id, key, simulate ? 1 : 0); }
		catch (Throwable t) { throw failure(t); }
		finally { Reference.reachabilityFence(this.owner); }
	}

	int state() {
		try { return (int)STATE.invokeExact(this.id); }
		catch (Throwable t) { throw failure(t); }
		finally { Reference.reachabilityFence(this.owner); }
	}

	/** Ordered export (see the native ABI); the array may be longer than the count. */
	long[] export(int mode, int arg, int[] count) {
		try {
			int n = (int)EXPORT.invokeExact(this.id, mode, arg, MemorySegment.ofArray(this.export), this.export.length);
			if (n < 0) {
				this.export = new long[Math.max(-n, this.export.length * 2)];
				n = (int)EXPORT.invokeExact(this.id, mode, arg, MemorySegment.ofArray(this.export), this.export.length);
			}
			if (n < 0) throw new IllegalStateException("Ticket storage export did not fit");
			count[0] = n;
			return this.export;
		}
		catch (Throwable t) { throw failure(t); }
		finally { Reference.reachabilityFence(this.owner); }
	}

	void clearDeactivated() {
		this.modifications++;
		long status;
		try { status = (long)CLEAR_DEACTIVATED.invokeExact(this.id); }
		catch (Throwable t) { throw failure(t); }
		finally { Reference.reachabilityFence(this.owner); }
		check(status);
	}

	long walk(int kind) {
		try { return (long)WALK.invokeExact(this.id, kind); }
		catch (Throwable t) { throw failure(t); }
		finally { Reference.reachabilityFence(this.owner); }
	}

	/** The raw status: the caller marks the storage dirty before {@link #check}. */
	long step(long walk, int answer) {
		this.modifications++;
		try { return (long)STEP.invokeExact(this.id, walk, answer, MemorySegment.ofArray(this.io), MemorySegment.ofArray(this.batch), this.batchSize); }
		catch (Throwable t) { throw failure(t); }
		finally { Reference.reachabilityFence(this.owner); }
	}

	/** Throws what the original's {@code entry.getValue()} read would for batched ticket {@code index}. */
	int batchValueSize(long walk, int index) {
		long status;
		try { status = (long)BATCH_VALUE.invokeExact(this.id, walk, index); }
		catch (Throwable t) { throw failure(t); }
		finally { Reference.reachabilityFence(this.owner); }
		return (int)(check(status) >>> 32);
	}

	/** Sizes the next batch from how many tickets this one's predicates consumed. */
	void consumed(int tested, int offered) {
		if (FIXED_BATCH == 0) {
			this.batchSize = tested >= offered ? Math.min(BATCH_LIMIT, offered * 2) : Math.max(8, Math.min(BATCH_LIMIT, tested + (tested >> 1)));
		}
	}

	static void endWalk(long walk, boolean released) {
		try { WALK_END.invokeExact(walk); }
		catch (Throwable t) { throw failure(t); }
		if (released) Cells.drain();
	}

    static Object captureBatchList(long source, Object retained) {
        try {
            synchronized (Cells.LOCK) {
                Cells.SourceReference reference = Cells.SOURCES.get(source);
                SourceRoots roots = reference == null ? null : reference.get();
                if (!(roots instanceof ListRoots)) throw new IllegalStateException("Missing captured ticket list VM resource");
                return roots;
            }
        } finally { Reference.reachabilityFence(retained); }
    }

	/** {@code getTickets(key)}: a live view of the active list, or the shared empty list. */
	java.util.List<Ticket> tickets(long key) {
		long list;
		try { list = (long)LIST.invokeExact(this.id, key); }
		catch (Throwable t) { throw failure(t); }
		finally { Reference.reachabilityFence(this.owner); }
        return this.view(list);
    }
    java.util.List<Ticket> getOrCreate(long key, Admission operation) {
        this.modifications++;
        long status;
        try { status = (long)GET_OR_CREATE.invokeExact(this.id, key, operation.listResult()); }
        catch (Throwable error) { throw failure(error); }
        finally { Reference.reachabilityFence(this.owner); }
        check(status);
        return this.view(operation.listResult().get(J, 0));
    }
    private java.util.List<Ticket> view(long list) {
        if (list == -1L) return java.util.List.of();
        long source;
        try { source = (long)LIST_SOURCE.invokeExact(this.id, list); }
        catch (Throwable error) { throw failure(error); }
        finally { Reference.reachabilityFence(this); }
        if (source == 0) throw new IllegalStateException("Retired native ticket list mapping");
        int index = (int)list;
        if (index >= this.views.length) this.views = java.util.Arrays.copyOf(this.views, Math.max(index + 1, this.views.length * 2));
        java.lang.ref.WeakReference<TicketList> reference = this.views[index];
        TicketList view = reference == null ? null : reference.get();
        if (view != null && view.roots.source == source) return view;
        ListRoots roots = this.roots.lists.get(source);
        if (roots == null) {
            roots = new ListRoots(this, list);
            this.roots.lists.put(source, roots);
        }
        view = new TicketList(roots);
        this.views[index] = new java.lang.ref.WeakReference<>(view);
        return view;
    }

    /** A public view owns its actual list and VM Ticket anchors, independently
     * of the map that originally routed to it. Mutations bypass notifications. */
    private static final class TicketList extends AbstractList<Ticket> implements RandomAccess {
        final ListRoots roots;
        final long list;
        TicketList(ListRoots roots) { this.roots = roots; this.list = roots.list; }
        private int nativeSize() {
            try { return (int)RESOURCE_SIZE.invokeExact(this.roots.id); }
            catch (Throwable error) { throw failure(error); }
            finally { Reference.reachabilityFence(this.roots); }
        }
        @Override public int size() { return this.nativeSize(); }
        @Override public Ticket get(int index) {
            if (index < 0 || index >= this.size()) throw new IndexOutOfBoundsException(index);
            try { int cell = (int)RESOURCE_GET.invokeExact(this.roots.id, index); return Cells.ticket(cell); }
            catch (Throwable error) { throw failure(error); }
            finally { Reference.reachabilityFence(this.roots); }
        }
        @Override public void add(int index, Ticket ticket) { this.roots.modified(); this.put(false, index, ticket); }
        @Override public Ticket set(int index, Ticket ticket) { this.roots.modified(); return this.put(true, index, ticket); }
        @Override public Ticket remove(int index) {
            this.roots.modified();
            long status;
            try { status = (long)RESOURCE_REMOVE.invokeExact(this.roots.id, index, MemorySegment.ofArray(this.roots.io)); }
            catch (Throwable error) { throw failure(error); }
            finally { Reference.reachabilityFence(this.roots); }
            Ticket previous = (status & 255) == 0 ? Cells.ticket((int)this.roots.io[0]) : null;
            check(status);
            Reference.reachabilityFence(this.roots);
            return previous;
        }
        private Ticket put(boolean replace, int index, Ticket ticket) {
            long status;
            try { status = (long)RESOURCE_PREPARE.invokeExact(this.roots.id, index, replace ? 1 : 0); }
            catch (Throwable error) { throw failure(error); }
            finally { Reference.reachabilityFence(this.roots); }
            check(status);
            if (ticket == null) return this.publish(index, replace, -1, 0, 0, 0L, -1, null);
            int type = Types.id(Ticket.nativeBindingType(ticket));
            int level = Ticket.nativeBindingLevel(ticket);
            int flags = Types.flags(type);
            long timeout = Ticket.nativeBindingType(ticket).timeout();
            int cell = ticket.boundCell();
            try (Cells.Prepared prepared = cell >= 0 ? Cells.prepareBound(this.roots, ticket, cell) : Cells.prepare(this.roots, ticket, ticket.unboundTicksLeft())) {
                return this.publish(index, replace, type, level, flags, timeout, prepared.cell, prepared);
            }
        }
        private Ticket publish(int index, boolean replace, int type, int level, int flags, long timeout, int cell, Cells.Prepared prepared) {
            long status;
            try { status = (long)RESOURCE_PUBLISH.invokeExact(this.roots.id, index, replace ? 1 : 0, type, level, flags, timeout, cell, MemorySegment.ofArray(this.roots.io)); }
            catch (Throwable error) { throw failure(error); }
            finally { Reference.reachabilityFence(this.roots); }
            // Capture before bind callbacks, queue draining or shared IO reuse.
            Ticket previous = (status & 255) == 0 ? Cells.ticket((int)this.roots.io[0]) : null;
            if (prepared != null) prepared.finish(status);
            check(status);
            Reference.reachabilityFence(this.roots);
            return previous;
        }
    }

	/**
	 * Ticket types by identity (the storage's {@code ==} comparisons) with their
	 * native flags; {@code equals(FORCED)} keeps the record equality the
	 * original used.
	 */
	static final class Types {
		private static volatile TicketType[] types = new TicketType[0];
		private static volatile int[] flags = new int[0];

		static int id(TicketType type) {
			TicketType[] known = types;
			for (int i = 0; i < known.length; i++) {
				if (known[i] == type) return i;
			}
			return register(type);
		}

		static int flags(int id) {
			return flags[id];
		}

		private static synchronized int register(TicketType type) {
			TicketType[] known = types;
			for (int i = 0; i < known.length; i++) {
				if (known[i] == type) return i;
			}
			int value = type.flags() & 31 | (type.equals(TicketType.FORCED) ? 32 : 0) | (type == TicketType.UNKNOWN ? 64 : 0);
			int[] nextFlags = java.util.Arrays.copyOf(flags, known.length + 1);
			nextFlags[known.length] = value;
			flags = nextFlags;
			TicketType[] next = java.util.Arrays.copyOf(known, known.length + 1);
			next[known.length] = type;
			types = next;
			return known.length;
		}
	}

	/**
	 * Weak lookup by native cell; actual strong Ticket references belong to each
	 * list's {@link ListRoots}. Native list-specific entry counts select
	 * release records, so one source cannot unpin another source's Ticket.
	 * Counter resource leases survive entry removal and retire separately when
	 * their actual JVM owners become unreachable. Preparing, binding and draining
	 * hold {@link Cells#LOCK}; release records are applied before a new association
	 * is prepared. No process-global strong Ticket reference anchors source cycles.
	 */
	static final class Cells {
		private static final Object LOCK = new Object();
		private static final java.lang.ref.Cleaner CLEANER = java.lang.ref.Cleaner.create();
		private static volatile java.lang.ref.WeakReference<?>[] weak = new java.lang.ref.WeakReference<?>[64];
		private static long[] pages = new long[0];
		private static long[] drained = new long[64];

		/** The ticket bound to {@code cell}; stored cells are always pinned. */
		static Ticket ticket(int cell) {
            if (cell < 0) return null;
            java.lang.ref.WeakReference<?>[] values = weak;
            if (cell >= values.length) return null;
            java.lang.ref.WeakReference<?> reference = values[cell];
            return reference == null ? null : (Ticket)reference.get();
        }
        private static void ensure(int cell) {
            if (cell >= weak.length) weak = java.util.Arrays.copyOf(weak, Math.max(cell + 1, weak.length * 2));
        }
        private static final java.lang.ref.ReferenceQueue<SourceRoots> SOURCE_QUEUE = new java.lang.ref.ReferenceQueue<>();
        private static final SourceIndex SOURCES = new SourceIndex();
        private static long[] sourceReleases = new long[128];
        static { cellsReady = true; }
        private static final class SourceReference extends java.lang.ref.WeakReference<SourceRoots> {
            final long source;
            SourceReference(SourceRoots roots) { super(roots, SOURCE_QUEUE); this.source = roots.source; }
        }
        static void registerSource(SourceRoots roots) {
            synchronized (LOCK) { clearCollectedSources(); SOURCES.put(roots.source, new SourceReference(roots)); }
        }
        private static void clearCollectedSources() {
            SourceReference reference;
            while ((reference = (SourceReference)SOURCE_QUEUE.poll()) != null) {
                if (SOURCES.get(reference.source) == reference) SOURCES.remove(reference.source);
            }
        }
        private static void retainSource(ListRoots storage, int cell, Ticket ticket) {
            drain();
            Ticket previous = storage.tickets.get(cell);
            try { storage.tickets.put(cell, ticket); }
            catch (Throwable error) {
                if (previous == null) storage.tickets.remove(cell);
                else storage.tickets.put(cell, previous);
                throw error;
            }
        }

        /** Actual roots and cleanup are ready before an entry can become visible. */
        static Prepared prepareBound(ListRoots storage, Ticket ticket, int cell) {
            synchronized (LOCK) {
                ensure(cell);
                java.lang.ref.WeakReference<Ticket> reference = new java.lang.ref.WeakReference<>(ticket);
                Prepared prepared = new Prepared(storage, ticket, cell, address(cell), null, false);
                retainSource(storage, cell, ticket);
                weak[cell] = reference;
                return prepared;
            }
        }
        static Prepared prepare(ListRoots storage, Ticket ticket, long initial) {
            MemorySegment counter = Ticket.prepareNativeCounter(ticket, initial);
            if (counter != null) {
                int cell = counter.get(ValueLayout.JAVA_INT, 28);
                synchronized (LOCK) {
                    ensure(cell);
                    java.lang.ref.WeakReference<Ticket> reference = new java.lang.ref.WeakReference<>(ticket);
                    Prepared prepared = new Prepared(storage, ticket, cell, counter.address(), null, true);
                    retainSource(storage, cell, ticket);
                    weak[cell] = reference;
                    return prepared;
                }
            }
            long reserved;
            try { reserved = (long)CELLS_RESERVE.invokeExact(initial); }
            catch (Throwable t) { throw failure(t); }
            if (reserved < 0) throw new OutOfMemoryError("Native ticket storage allocation failed");
            int cell = (int)reserved;
            Free release = null;
            try {
                release = new Free(cell);
                synchronized (LOCK) {
                    ensure(cell);
                    java.lang.ref.WeakReference<Ticket> reference = new java.lang.ref.WeakReference<>(ticket);
                    long address = address(cell);
                    java.lang.ref.Cleaner.Cleanable cleanup = CLEANER.register(ticket, release);
                    Prepared prepared = new Prepared(storage, ticket, cell, address, cleanup, true);
                    retainSource(storage, cell, ticket);
                    weak[cell] = reference;
                    return prepared;
                }
            } catch (Throwable t) {
                if (release != null) release.run();
                else {
                    try { int ignored = (int)CELLS_FREE.invokeExact(cell); }
                    catch (Throwable cleanup) { t.addSuppressed(cleanup); }
                }
                throw failure(t);
            }
        }

        static final class Prepared implements AutoCloseable {
            final ListRoots storage;
            final Ticket ticket;
            final int cell;
            final long address;
            final java.lang.ref.Cleaner.Cleanable cleanup;
            final boolean needsBind;
            boolean published;
            Prepared(ListRoots storage, Ticket ticket, int cell, long address,
                     java.lang.ref.Cleaner.Cleanable cleanup, boolean needsBind) {
                this.storage=storage; this.ticket=ticket; this.cell=cell; this.address=address;
                this.cleanup=cleanup; this.needsBind=needsBind;
            }
            void finish(long status) {
                synchronized (LOCK) {
                    this.published = storage.rootCount(cell) != 0;
                    if (published && needsBind) ticket.bindCell(cell, address);
                }
            }
            public void close() {
                synchronized (LOCK) {
                    boolean retained = storage.rootCount(cell) != 0;
                    if (!retained) {
                        if (storage.tickets.get(cell) == ticket) storage.tickets.remove(cell);
                        if (!this.published && cleanup != null) cleanup.clean();
                    }
                    drain();
                }
            }
        }
        static void bindNew(ListRoots storage, Ticket ticket, long status) {
            int cell = (int)(status >>> 32) - 1;
            if (cell < 0) return;
            synchronized (LOCK) {
                ensure(cell);
                retainSource(storage, cell, ticket);
                weak[cell] = new java.lang.ref.WeakReference<>(ticket);
                ticket.bindCell(cell, address(cell));
            }
            if (!Ticket.hasNativeCounter(ticket)) CLEANER.register(ticket, new Free(cell));
        }
        static void pin(ListRoots storage, Ticket ticket) {
            int cell = ticket.boundCell();
            if (cell < 0) return;
            synchronized (LOCK) { retainSource(storage, cell, ticket); }
        }

		private static long address(int cell) {
			int page = cell >>> 12;
			if (page >= pages.length) pages = java.util.Arrays.copyOf(pages, Math.max(page + 1, pages.length * 2));
			if (pages[page] == 0) {
				try { pages[page] = (long)CELLS_PAGE.invokeExact(page); }
				catch (Throwable t) { throw failure(t); }
			}
			return pages[page] + (long)(cell & 4095) * 48;
		}

        /** Process source-specific releases and native cell retirement together. */
        static void drain() {
            synchronized (LOCK) {
                clearCollectedSources();
                int count;
                do {
                    try { count = (int)ROOTS_DRAIN.invokeExact(MemorySegment.ofArray(sourceReleases), sourceReleases.length / 2); }
                    catch (Throwable error) { throw failure(error); }
                    for (int i=0;i<count;i++) {
                        SourceReference reference = SOURCES.get(sourceReleases[i*2]);
                        SourceRoots roots = reference == null ? null : reference.get();
                        if (roots != null) roots.release(sourceReleases[i*2+1]);
                    }
                } while (count == sourceReleases.length / 2);
                try {
                    count = (int)CELLS_DRAIN.invokeExact(MemorySegment.ofArray(drained), drained.length);
                    if (count < 0) {
                        drained = new long[Math.max(-count, drained.length * 2)];
                        int ignored = (int)CELLS_DRAIN.invokeExact(MemorySegment.ofArray(drained), drained.length);
                    }
                } catch (Throwable error) { throw failure(error); }
            }
        }

		/** A ticket's cleaner: its cell is reused only once the ticket is unreachable. */
        private static final class Free implements Runnable {
            private final java.util.concurrent.atomic.AtomicInteger slot;
            Free(int cell) { this.slot = new java.util.concurrent.atomic.AtomicInteger(cell); }
            @Override public void run() {
                int cell = slot.getAndSet(-1);
                if (cell < 0) return;
                synchronized (LOCK) {
                    if (cell < weak.length) weak[cell] = null;
                    try { int freed = (int)CELLS_FREE.invokeExact(cell); }
                    catch (Throwable t) { throw failure(t); }
                }
            }
        }

	}
}
