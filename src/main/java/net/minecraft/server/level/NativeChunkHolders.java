package net.minecraft.server.level;

import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.ref.Reference;
import java.util.Arrays;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.util.NativeLibraryLoader;
import org.jetbrains.annotations.Nullable;

/**
 * Downcalls for the native ChunkMap holder registry ({@code world/level/chunk_holders}).
 * Rust owns the structure: which chunks have holders, the updating/visible/pending maps
 * in their linked order, the drop and eager-save sets and the save throttle. This class
 * keeps the {@link ChunkHolder} objects by id. Every call takes the registry lock, so
 * the off-thread readers of the original (light, Distant Horizons) stay safe.
 */
final class NativeChunkHolders {
	private static MethodHandle handle(String name, FunctionDescriptor descriptor) {
		return NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_chunk_holders_" + name, descriptor, Linker.Option.critical(false));
	}
	private static MethodHandle heap(String name, FunctionDescriptor descriptor) {
		return NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_chunk_holders_" + name, descriptor, Linker.Option.critical(true));
	}
	private static final ValueLayout.OfInt I = ValueLayout.JAVA_INT;
	private static final ValueLayout.OfLong J = ValueLayout.JAVA_LONG;
	private static final MethodHandle CREATE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_chunk_holders_create", FunctionDescriptor.of(J, I));
	private static final MethodHandle RELEASE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_chunk_holders_release", FunctionDescriptor.ofVoid(J));
	private static final MethodHandle SCHEDULE = handle("schedule", FunctionDescriptor.of(J, J, J, I, I, I));
	private static final MethodHandle COMMIT_NEW = handle("commit_new", FunctionDescriptor.of(J, J, J, I));
	private static final MethodHandle ATTACH_GENERATION = handle("attach_generation", FunctionDescriptor.of(J, J, I, J));
	private static final MethodHandle ABORT_NEW = handle("abort_new", FunctionDescriptor.ofVoid(J, I));
	private static final MethodHandle UPDATING = handle("updating", FunctionDescriptor.of(I, J, J));
	private static final MethodHandle DROPPING = handle("dropping", FunctionDescriptor.of(I, J, J));
	private static final MethodHandle PROMOTE = heap("promote", FunctionDescriptor.of(I, J, ValueLayout.ADDRESS, I));
	private static final MethodHandle ENTRIES = heap("entries", FunctionDescriptor.of(I, J, I, ValueLayout.ADDRESS, I));
	private static final MethodHandle DROP_PASS = heap("drop_pass", FunctionDescriptor.of(I, J, ValueLayout.ADDRESS, I));
	private static final MethodHandle FINISH_UNLOAD = handle("finish_unload", FunctionDescriptor.of(I, J, J, I));
	private static final MethodHandle MARK_UNSAVED = handle("mark_unsaved", FunctionDescriptor.of(J, J, J));
	private static final MethodHandle EAGER_START = handle("eager_start", FunctionDescriptor.ofVoid(J));
	private static final MethodHandle EAGER_NEXT = heap("eager_next", FunctionDescriptor.of(J, J, ValueLayout.ADDRESS));
	private static final MethodHandle EAGER_REMOVE = handle("eager_remove", FunctionDescriptor.of(J, J));
	private static final MethodHandle NEXT_SAVE = handle("next_save", FunctionDescriptor.of(J, J, J));
	private static final MethodHandle SET_NEXT_SAVE = handle("set_next_save", FunctionDescriptor.of(J, J, I, J, J));
	private static final MethodHandle WORK = handle("work", FunctionDescriptor.of(I, J));
	private static final MethodHandle STATUS_LEVELS = handle("status_levels", FunctionDescriptor.ofVoid(J, I, I, I));
	private static final MethodHandle TRANSITION = handle("transition", FunctionDescriptor.of(J, I, I, I, I, I));
	private static final MethodHandle TAKE_LEVELS = handle("take_levels", FunctionDescriptor.of(J, J, J));
	private static final MethodHandle APPLY_LEVELS = heap("apply_levels", FunctionDescriptor.of(I, J, ValueLayout.ADDRESS, I));
	private static final MethodHandle DISCARD_LEVELS = handle("discard_levels", FunctionDescriptor.ofVoid(J));
	private static final MethodHandle HOLDER_LEVELS = handle("holder_levels", FunctionDescriptor.of(J, J, I));
	private static final MethodHandle HOLDER_SET = handle("holder_set", FunctionDescriptor.of(J, J, I, I, I));
	private static final MethodHandle BEGIN_TRANSITION = handle("begin_transition", FunctionDescriptor.of(J, J, I));
	private static final int FULL_LEVEL = ChunkLevel.byStatus(FullChunkStatus.FULL);
	private static final int BLOCK_TICKING_LEVEL = ChunkLevel.byStatus(FullChunkStatus.BLOCK_TICKING);
	private static final int ENTITY_TICKING_LEVEL = ChunkLevel.byStatus(FullChunkStatus.ENTITY_TICKING);

	/** Status-transition effects ({@link #beginTransition}), in {@code updateFutures}' order. */
	static final long PREPARE_FULL = 1, UNLOAD_FULL = 1 << 1, PREPARE_TICKING = 1 << 2, UNLOAD_TICKING = 1 << 3,
		PREPARE_ENTITY = 1 << 4, UNLOAD_ENTITY = 1 << 5;
	/** Applied level-change kinds ({@link #applyLevels}). */
	static final int APPLY_UPDATE = 0, APPLY_LEVEL = 1, APPLY_NEW = 2;

	static final int UNCHANGED = 0;
	static final int LEVEL = 1;
	static final int REVIVED = 2;
	static final int NEW = 3;

	private final MemorySegment owner;
	private final long id;
	/** Holder objects by native id; the native side frees an id when its unload completes. */
	private volatile ChunkHolder[] holders = new ChunkHolder[256];
	private final Reference2IntOpenHashMap<ChunkHolder> ids = new Reference2IntOpenHashMap<>();
	private long[] buffer = new long[512];
	private final int[] done = new int[1];

	NativeChunkHolders(int maxLevel) {
		Arena arena = Arena.ofAuto();
		long created;
		try { created = (long)CREATE.invokeExact(maxLevel); }
		catch (Throwable t) { throw failure(t); }
		if (created == 0) throw new OutOfMemoryError("Native chunk holder registry allocation failed");
		this.id = created;
		NativeRelease cleanup;
		try { cleanup = new NativeRelease(created, RELEASE); }
		catch (Throwable t) { release(created); throw failure(t); }
		try {
			this.owner = MemorySegment.ofAddress(created).reinterpret(1, arena, cleanup);
		} catch (Throwable t) {
			cleanup.run();
			throw failure(t);
		}
		this.ids.defaultReturnValue(-1);
		try { STATUS_LEVELS.invokeExact(this.id, FULL_LEVEL, BLOCK_TICKING_LEVEL, ENTITY_TICKING_LEVEL); }
		catch (Throwable t) { throw failure(t); }
		finally { Reference.reachabilityFence(this.owner); }
	}

    MemorySegment requestOwner() { return this.owner; }

	/** {@code updateFutures}' effects between two levels, for a holder without a registry. */
	static long transition(int oldLevel, int level) {
		try { return (long)TRANSITION.invokeExact(oldLevel, level, FULL_LEVEL, BLOCK_TICKING_LEVEL, ENTITY_TICKING_LEVEL); }
		catch (Throwable t) { throw failure(t); }
	}

	/** The demotion target of a transition's effects, or null. */
	@Nullable
	static FullChunkStatus demotion(long effects) {
		int status = (int)(effects >>> 8 & 255);
		return status == 0 ? null : FullChunkStatus.values()[status - 1];
	}

	/** Moves the loading graph's changes from its last run into the registry. */
	void takeLevels(TicketChunkDistance distance) {
		try { check((long)TAKE_LEVELS.invokeExact(this.id, distance.handleId())); }
		catch (Throwable t) { throw failure(t); }
		finally {
			Reference.reachabilityFence(this.owner);
			distance.fence();
		}
	}

	/**
	 * Applies the next taken level changes to holders natively: (chunk, word) records in
	 * {@code buffer}, word bits 0..8 kind, 8..40 holder id, 40.. level. Returns the record
	 * count, 0 when all are applied; an {@link #APPLY_NEW} record is always the last.
	 */
	int applyLevels(long[] buffer) {
		try { return (int)check((int)APPLY_LEVELS.invokeExact(this.id, MemorySegment.ofArray(buffer), buffer.length / 2)); }
		catch (Throwable t) { throw failure(t); }
		finally { Reference.reachabilityFence(this.owner); }
	}

	void discardLevels() {
		try { DISCARD_LEVELS.invokeExact(this.id); }
		catch (Throwable t) { throw failure(t); }
		finally { Reference.reachabilityFence(this.owner); }
	}

	/** Bits 0..16 ticket level, 16..32 old level, bit 32 accessible since the last save. */
	long holderLevels(int holderId) {
		try { return check((long)HOLDER_LEVELS.invokeExact(this.id, holderId)); }
		catch (Throwable t) { throw failure(t); }
		finally { Reference.reachabilityFence(this.owner); }
	}

	/** Mode 0 {@code setTicketLevel(value)}, 1 old level = level, 2 {@code refreshAccessibility}. */
	void holderSet(int holderId, int mode, int value) {
		try { check((long)HOLDER_SET.invokeExact(this.id, holderId, mode, value)); }
		catch (Throwable t) { throw failure(t); }
		finally { Reference.reachabilityFence(this.owner); }
	}

	/** {@code updateFutures}' effects for the holder; marks it accessible when it now is. */
	long beginTransition(int holderId) {
		try { return check((long)BEGIN_TRANSITION.invokeExact(this.id, holderId)); }
		catch (Throwable t) { throw failure(t); }
		finally { Reference.reachabilityFence(this.owner); }
	}

	/** JDK25 can register cleanup before allocating reinterpret's result.
	 * Rollback and arena cleanup must consume the same handle exactly once. */
	private static final class NativeRelease implements Consumer<MemorySegment>, Runnable {
		private static final VarHandle HANDLE;
		static {
			try { HANDLE = MethodHandles.lookup().findVarHandle(NativeRelease.class, "handle", long.class); }
			catch (ReflectiveOperationException e) { throw new ExceptionInInitializerError(e); }
		}
		private volatile long handle;
		private final MethodHandle function;
		NativeRelease(long handle, MethodHandle function) { this.handle = handle; this.function = function; }
		public void accept(MemorySegment ignored) { run(); }
		public void run() {
			long owned = (long)HANDLE.getAndSet(this, 0L);
			if (owned != 0) {
				try { function.invokeExact(owned); }
				catch (Throwable t) { throw new IllegalStateException("Cannot release native chunk holders", t); }
			}
		}
	}

	private static void release(long handle) {
		try { RELEASE.invokeExact(handle); }
		catch (Throwable t) { throw new IllegalStateException("Cannot release native chunk holders", t); }
	}

	private static RuntimeException failure(Throwable t) {
		if (t instanceof RuntimeException r) return r;
		if (t instanceof Error e) throw e;
		return new IllegalStateException("Native chunk holder call failed", t);
	}

	private static long check(long result) {
		if (result == -3) throw new OutOfMemoryError("Native chunk holder registry allocation failed");
		if (result < 0) throw new IllegalStateException("Native chunk holder registry fault: " + result);
		return result;
	}

	@Nullable
	ChunkHolder holder(int holderId) {
		ChunkHolder[] table = this.holders;
		return holderId < 0 || holderId >= table.length ? null : table[holderId];
	}

	/** {@code updateChunkScheduling}'s decision; see the native ABI. */
	long schedule(long key, int level, boolean hasHolder, int oldLevel) {
		try { return check((long)SCHEDULE.invokeExact(this.id, key, level, hasHolder ? 1 : 0, oldLevel)); }
		catch (Throwable t) { throw failure(t); }
		finally { Reference.reachabilityFence(this.owner); }
	}

	/** Records a holder constructed for {@code holderId}, then commits it to the updating map. */
	synchronized void commitNew(long key, int holderId, ChunkHolder holder) {
		ChunkHolder[] table = this.holders;
		if (holderId >= table.length) table = Arrays.copyOf(table, Math.max(holderId + 1, table.length * 2));
		table[holderId] = holder;
		this.holders = table;
		this.ids.put(holder, holderId);
		try { check((long)COMMIT_NEW.invokeExact(this.id, key, holderId)); }
		catch (Throwable t) { throw failure(t); }
		finally { Reference.reachabilityFence(this.owner); }
		NativeChunkGeneration.Holder generation = holder.generationOwner();
		try { check((long)ATTACH_GENERATION.invokeExact(this.id, holderId, generation.handleId())); }
		catch (Throwable t) { throw failure(t); }
		finally { generation.fence(); Reference.reachabilityFence(this.owner); }
	}

	void abortNew(int holderId) {
		try { ABORT_NEW.invokeExact(this.id, holderId); }
		catch (Throwable t) { throw failure(t); }
		finally { Reference.reachabilityFence(this.owner); }
	}

	/**
	 * The updating holder at {@code key}. Ids are freed and reused only under this
	 * monitor ({@link #finishUnload}, {@link #commitNew}), so an off-thread reader
	 * cannot see an id freed and reassigned between the native lookup and the table
	 * read, and never resolves another chunk's holder.
	 */
	@Nullable
	synchronized ChunkHolder updating(long key) {
		int holderId;
		try { holderId = (int)UPDATING.invokeExact(this.id, key); }
		catch (Throwable t) { throw failure(t); }
		finally { Reference.reachabilityFence(this.owner); }
		return this.holder(holderId);
	}

	boolean dropping(long key) {
		try { return (int)DROPPING.invokeExact(this.id, key) != 0; }
		catch (Throwable t) { throw failure(t); }
		finally { Reference.reachabilityFence(this.owner); }
	}

    /** Native snapshot and stable opaque bindings; a retained view survives id reuse. */
    synchronized Visible visible() { return snapshot(false); }
    @Nullable synchronized Visible promote() { return snapshot(true); }
    @Nullable private Visible snapshot(boolean promote) {
        Arena arena = Arena.ofAuto();
        long view;
        try { view = (long)(promote ? ViewBindings.PROMOTE : ViewBindings.CURRENT).invokeExact(this.id); }
        catch (Throwable t) { throw failure(t); }
        finally { Reference.reachabilityFence(this.owner); }
        if (view == 0) return null;
        Visible prepared = new Visible(view, arena, this.holders);
        if (promote) {
            try { ViewBindings.COMMIT.invokeExact(this.id, view); }
            catch (Throwable t) { throw failure(t); }
            finally { Reference.reachabilityFence(this.owner); Reference.reachabilityFence(prepared); }
        }
        return prepared;
    }

    private static final class ViewBindings {
        static final MethodHandle CURRENT = handle("view_current", FunctionDescriptor.of(J, J));
        static final MethodHandle PROMOTE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_chunk_holders_view_prepare", FunctionDescriptor.of(J, J));
        static final MethodHandle COMMIT = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_chunk_holders_view_commit", FunctionDescriptor.ofVoid(J, J));
        static final MethodHandle RELEASE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_chunk_holders_view_release", FunctionDescriptor.ofVoid(J));
        static final MethodHandle GET = handle("view_get", FunctionDescriptor.of(I, J, J));
        static final MethodHandle SIZE = handle("view_size", FunctionDescriptor.of(I, J));
        static final MethodHandle BINDINGS = heap("view_bindings", FunctionDescriptor.of(I, J, ValueLayout.ADDRESS, I));
        static final MethodHandle WALK = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_chunk_holders_view_walk", FunctionDescriptor.of(J, J));
        static final MethodHandle WALK_RELEASE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_chunk_holders_view_walk_release", FunctionDescriptor.ofVoid(J));
        static final MethodHandle HAS_NEXT = handle("view_walk_has_next", FunctionDescriptor.of(I, J));
        static final MethodHandle NEXT = handle("view_walk_next", FunctionDescriptor.of(I, J));
        static final MethodHandle REMOVE = handle("view_walk_remove", FunctionDescriptor.of(I, J));
    }

    static final class Visible {
        private final MemorySegment owner;
        private final long id;
        private final ChunkHolder[] bindings;
        private final java.util.Collection<ChunkHolder> values = new java.util.AbstractCollection<>() {
            public int size() { return Visible.this.size(); }
            public java.util.Iterator<ChunkHolder> iterator() { return new Cursor(); }
            public java.util.Spliterator<ChunkHolder> spliterator() {
                return java.util.Spliterators.spliterator(iterator(), size(), java.util.Spliterator.ORDERED | java.util.Spliterator.NONNULL);
            }
        };
        Visible(long id, Arena arena, ChunkHolder[] source) {
            this.id = id;
            this.owner = NativeChunkGeneration.adopt(id, 1, arena, ViewBindings.RELEASE);
            this.bindings = new ChunkHolder[source.length];
            int[] ids = new int[size()];
            try {
                int count = (int)ViewBindings.BINDINGS.invokeExact(id, MemorySegment.ofArray(ids), ids.length);
                if (count != ids.length) throw new IllegalStateException("Native visible snapshot changed during adoption");
                for (int binding : ids) this.bindings[binding] = source[binding];
            } catch (Throwable t) { throw failure(t); }
            finally { Reference.reachabilityFence(this); }
        }
        long handleId() { return id; }
        void fence() { Reference.reachabilityFence(this); }
        @Nullable public ChunkHolder get(long key) {
            try { int binding = (int)ViewBindings.GET.invokeExact(id, key); return binding < 0 ? null : bindings[binding]; }
            catch (Throwable t) { throw failure(t); }
            finally { Reference.reachabilityFence(this); }
        }
        public int size() {
            try { return (int)ViewBindings.SIZE.invokeExact(id); }
            catch (Throwable t) { throw failure(t); }
            finally { Reference.reachabilityFence(this); }
        }
        public java.util.Collection<ChunkHolder> values() { return values; }
        public Iterable<it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry<ChunkHolder>> long2ObjectEntrySet() {
            return () -> new java.util.Iterator<>() {
                final Cursor cursor = new Cursor();
                public boolean hasNext() { return cursor.hasNext(); }
                public it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry<ChunkHolder> next() {
                    ChunkHolder holder = cursor.next();
                    return new it.unimi.dsi.fastutil.longs.AbstractLong2ObjectMap.BasicEntry<>(cursor.key(), holder);
                }
                public void remove() { cursor.remove(); }
            };
        }
        private final class Cursor implements java.util.Iterator<ChunkHolder> {
            final MemorySegment owner;
            final long walk;
            Cursor() {
                Arena arena = Arena.ofAuto();
                try { walk = (long)ViewBindings.WALK.invokeExact(id); }
                catch (Throwable t) { throw failure(t); }
                finally { Reference.reachabilityFence(Visible.this); }
                if (walk == 0) throw new OutOfMemoryError("Native visible iterator allocation failed");
                owner = NativeChunkGeneration.adopt(walk, Long.BYTES, arena, ViewBindings.WALK_RELEASE);
            }
            public boolean hasNext() {
                try { return (int)ViewBindings.HAS_NEXT.invokeExact(walk) != 0; }
                catch (Throwable t) { throw failure(t); }
                finally { Reference.reachabilityFence(this); }
            }
            public ChunkHolder next() {
                try {
                    int binding = (int)ViewBindings.NEXT.invokeExact(walk);
                    if (binding == -13) throw new java.util.NoSuchElementException();
                    return bindings[binding];
                } catch (Throwable t) { throw failure(t); }
                finally { Reference.reachabilityFence(this); }
            }
            long key() {
                try { return (long)ValueLayout.JAVA_LONG.varHandle().getVolatile(owner, 0L); }
                finally { Reference.reachabilityFence(this); }
            }
            public void remove() {
                try {
                    int binding = (int)ViewBindings.REMOVE.invokeExact(walk);
                    if (binding == -14) throw new IllegalStateException();
                    bindings[binding] = null;
                } catch (Throwable t) { throw failure(t); }
                finally { Reference.reachabilityFence(this); }
            }
        }
    }

	/** Mode 0 updating, 1 visible: (key, holder) in iteration order. */
	void forEach(int mode, BiConsumer<Long, ChunkHolder> consumer) {
		long[] pairs;
		int n;
		try {
			pairs = this.buffer;
			n = (int)ENTRIES.invokeExact(this.id, mode, MemorySegment.ofArray(pairs), pairs.length / 2);
			if (n < 0) {
				pairs = this.buffer = new long[Math.max(-n * 2, pairs.length * 2)];
				n = (int)ENTRIES.invokeExact(this.id, mode, MemorySegment.ofArray(pairs), pairs.length / 2);
			}
		}
		catch (Throwable t) { throw failure(t); }
		finally { Reference.reachabilityFence(this.owner); }
		long[] copy = Arrays.copyOf(pairs, n * 2);
		for (int i = 0; i < n; i++) consumer.accept(copy[i * 2], this.holder((int)copy[i * 2 + 1]));
	}

	boolean updatingIsEmpty() {
		return (this.work() & 2) == 0;
	}

	/** The drop loop: (key, holder id) pairs now pending unload, in order. */
	long[] dropPass(int[] count) {
		long[] pairs = this.buffer;
		int n;
		try {
			n = (int)DROP_PASS.invokeExact(this.id, MemorySegment.ofArray(pairs), pairs.length / 2);
			if (n < 0 && n > -3_000_000) {
				pairs = this.buffer = new long[Math.max(-n * 2, pairs.length * 2)];
				n = (int)DROP_PASS.invokeExact(this.id, MemorySegment.ofArray(pairs), pairs.length / 2);
			}
		}
		catch (Throwable t) { throw failure(t); }
		finally { Reference.reachabilityFence(this.owner); }
		check(n);
		count[0] = n;
		return Arrays.copyOf(pairs, n * 2);
	}

	/**
	 * {@code pendingUnloads.remove(key, holder)}; the holder's id is freed when true.
	 * The holder is resolved by identity: a completion left over from an earlier unload
	 * of a since-revived or since-freed holder finds no id, or not the pending one.
	 */
	synchronized boolean finishUnload(long key, ChunkHolder holder) {
		int holderId = this.ids.getInt(holder);
		if (holderId < 0) return false;
		long levels = this.holderLevels(holderId);
		boolean removed;
		try { removed = (int)FINISH_UNLOAD.invokeExact(this.id, key, holderId) != 0; }
		catch (Throwable t) { throw failure(t); }
		finally { Reference.reachabilityFence(this.owner); }
		if (removed) {
			holder.detach(levels);
			this.holders[holderId] = null;
			this.ids.removeInt(holder);
		}
		return removed;
	}

	void markUnsaved(long key) {
		try { check((long)MARK_UNSAVED.invokeExact(this.id, key)); }
		catch (Throwable t) { throw failure(t); }
		finally { Reference.reachabilityFence(this.owner); }
	}

	void eagerStart() {
		try { EAGER_START.invokeExact(this.id); }
		catch (Throwable t) { throw failure(t); }
		finally { Reference.reachabilityFence(this.owner); }
	}

	/** The eager-save walk's next key; {@code done[0]} is 1 at its end. */
	long eagerNext() {
		try { return (long)EAGER_NEXT.invokeExact(this.id, MemorySegment.ofArray(this.done)); }
		catch (Throwable t) { throw failure(t); }
		finally { Reference.reachabilityFence(this.owner); }
	}

	boolean eagerDone() {
		return this.done[0] != 0;
	}

	void eagerRemove() {
		try { check((long)EAGER_REMOVE.invokeExact(this.id)); }
		catch (Throwable t) { throw failure(t); }
		finally { Reference.reachabilityFence(this.owner); }
	}

	long nextSave(long key) {
		try { return (long)NEXT_SAVE.invokeExact(this.id, key); }
		catch (Throwable t) { throw failure(t); }
		finally { Reference.reachabilityFence(this.owner); }
	}

	/** Mode 0 put, 1 remove, 2 clear. */
	void setNextSave(int mode, long key, long time) {
		try { check((long)SET_NEXT_SAVE.invokeExact(this.id, mode, key, time)); }
		catch (Throwable t) { throw failure(t); }
		finally { Reference.reachabilityFence(this.owner); }
	}

	/** Bit 0 pending unloads, 1 updating holders, 2 chunks to drop. */
	int work() {
		try { return (int)WORK.invokeExact(this.id); }
		catch (Throwable t) { throw failure(t); }
		finally { Reference.reachabilityFence(this.owner); }
	}
}
