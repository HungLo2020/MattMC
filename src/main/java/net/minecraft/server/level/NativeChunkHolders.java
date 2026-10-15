package net.minecraft.server.level;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Reference;
import java.util.Arrays;
import java.util.function.BiConsumer;
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
		long created;
		try { created = (long)CREATE.invokeExact(maxLevel); }
		catch (Throwable t) { throw failure(t); }
		if (created == 0) throw new OutOfMemoryError("Native chunk holder registry allocation failed");
		MemorySegment pointer = MemorySegment.ofAddress(created);
		try {
			this.id = created;
			this.owner = pointer.reinterpret(1, Arena.ofAuto(), NativeChunkHolders::release);
		} catch (Throwable t) {
			release(pointer);
			throw t;
		}
		this.ids.defaultReturnValue(-1);
	}

	private static void release(MemorySegment pointer) {
		try { RELEASE.invokeExact(pointer.address()); }
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

	/** The promoted visible map, or null when nothing changed since the last promote. */
	@Nullable
	Long2ObjectLinkedOpenHashMap<ChunkHolder> promote() {
		int n;
		try {
			n = (int)PROMOTE.invokeExact(this.id, MemorySegment.ofArray(this.buffer), this.buffer.length / 2);
			if (n < 0) {
				this.buffer = new long[Math.max(-n * 2, this.buffer.length * 2)];
				n = (int)PROMOTE.invokeExact(this.id, MemorySegment.ofArray(this.buffer), this.buffer.length / 2);
			}
		}
		catch (Throwable t) { throw failure(t); }
		finally { Reference.reachabilityFence(this.owner); }
		if (n == 0) return null;
		return this.map(this.buffer, n - 1);
	}

	private Long2ObjectLinkedOpenHashMap<ChunkHolder> map(long[] pairs, int count) {
		Long2ObjectLinkedOpenHashMap<ChunkHolder> map = new Long2ObjectLinkedOpenHashMap<>(count);
		for (int i = 0; i < count; i++) map.put(pairs[i * 2], this.holder((int)pairs[i * 2 + 1]));
		return map;
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
		boolean removed;
		try { removed = (int)FINISH_UNLOAD.invokeExact(this.id, key, holderId) != 0; }
		catch (Throwable t) { throw failure(t); }
		finally { Reference.reachabilityFence(this.owner); }
		if (removed) {
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
