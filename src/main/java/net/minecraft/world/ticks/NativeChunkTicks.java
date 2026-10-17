package net.minecraft.world.ticks;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Reference;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.NativeLibraryLoader;
import org.jetbrains.annotations.Nullable;

/**
 * Native per-chunk scheduled-tick queue and dedup set behind {@link LevelChunkTicks}.
 * Rust owns ordering (an exact port of {@code java.util.PriorityQueue}'s array heap
 * under {@link ScheduledTick#DRAIN_ORDER}), (x, y, z, type) dedup keys and entry
 * lifetime. This class keeps the {@link ScheduledTick} objects by entry id, so the
 * same object comes back from {@link #peek()}. Like the original queue it is not
 * thread-safe: one thread at a time, possibly handed off after construction.
 */
final class NativeChunkTicks<T> {
	private static MethodHandle handle(String name, FunctionDescriptor descriptor) {
		return NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_chunk_ticks_" + name, descriptor, Linker.Option.critical(false));
	}
	private static MethodHandle lifecycle(String name, FunctionDescriptor descriptor) {
		return NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_chunk_ticks_" + name, descriptor);
	}
	private static final ValueLayout.OfInt I = ValueLayout.JAVA_INT;
	private static final ValueLayout.OfLong J = ValueLayout.JAVA_LONG;
	private static final MethodHandle CREATE = lifecycle("create", FunctionDescriptor.of(ValueLayout.ADDRESS));
	private static final MethodHandle RELEASE = lifecycle("release", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
	private static final MethodHandle LIVE = lifecycle("live", FunctionDescriptor.of(J));
	private static final MethodHandle SCHEDULE = handle("schedule", FunctionDescriptor.of(J, J, J, I, J, I, I, I, I, I));
	private static final MethodHandle ADD_KEY = handle("add_key", FunctionDescriptor.of(I, J, I, I, I, I));
	private static final MethodHandle CONTAINS_KEY = handle("contains_key", FunctionDescriptor.of(I, J, I, I, I, I));
	private static final MethodHandle POLL = handle("poll", FunctionDescriptor.of(J, J));
	private static final MethodHandle COPY_IDS = handle("copy_ids", FunctionDescriptor.of(I, J, J, I));
	private static final MethodHandle REMOVE_AT = handle("remove_at", FunctionDescriptor.of(I, J, I, J));
	private static final MethodHandle LIVE_ENTRIES = handle("live_entries", FunctionDescriptor.of(I, J));
	private static final MethodHandle REMOVE_IN_BOX = handle("remove_in_box", FunctionDescriptor.of(I, J, I, I, I, I, I, I, J, I, J));
	/** Native result meaning another call on this container was already in progress. */
	private static final int BUSY = -2;

	private static RuntimeException failure(Throwable t) {
		if (t instanceof RuntimeException r) return r;
		if (t instanceof Error e) throw e;
		return new IllegalStateException("Native chunk tick call failed", t);
	}

	private final MemorySegment owner;
	private final long address;
	/** Entry id to its ScheduledTick; cleared when the native side frees the id. */
	private Object[] ticks = new Object[8];
	/**
	 * Per-container opaque type handles by object identity (the dedup equality
	 * {@code type() == type()}); index = handle. Not registry ids. Bounded by the
	 * distinct types scheduled into this container and released with it.
	 */
	private Object[] types = new Object[2];
	private int typeCount;
	/**
	 * Java-owned report buffer the native mutators fill on the calling thread:
	 * [head id, size, mod count, moved id]. Read back immediately after each call.
	 */
	private final MemorySegment report = Arena.ofAuto().allocate(ValueLayout.JAVA_INT, 4);
	private final long reportAddress = this.report.address();
	/**
	 * Java-owned copy of the queue's id array, refreshed in one call when first read
	 * after any mutation, so iteration does not cross per element.
	 */
	private MemorySegment idSnapshot = Arena.ofAuto().allocate(ValueLayout.JAVA_INT, 16);
	private int idSnapshotSize;
	private boolean idSnapshotValid;
	private int headId = -1;
	private int size;
	private int modCount;

	NativeChunkTicks() {
		try {
			MemorySegment pointer = (MemorySegment)CREATE.invokeExact();
			if (pointer.address() == 0L) {
				throw new IllegalStateException("Native chunk tick allocation failed");
			}
			boolean attached = false;
			try {
				// The cleaner captures only the static release handle.
				this.owner = pointer.reinterpret(1, Arena.ofAuto(), NativeChunkTicks::release);
				attached = true;
			} finally {
				if (!attached) release(pointer);
			}
			this.address = this.owner.address();
		} catch (Throwable t) {
			throw failure(t);
		}
	}

	private static void release(MemorySegment pointer) {
		try {
			RELEASE.invokeExact(pointer);
		} catch (Throwable t) {
			throw failure(t);
		}
	}

	static long liveContainers() {
		try {
			return (long)LIVE.invokeExact();
		} catch (Throwable t) {
			throw failure(t);
		}
	}

	private int handleOf(Object type) {
		for (int i = 0; i < this.typeCount; i++) {
			if (this.types[i] == type) {
				return i;
			}
		}
		if (this.typeCount == this.types.length) {
			this.types = java.util.Arrays.copyOf(this.types, this.typeCount * 2);
		}
		this.types[this.typeCount] = type;
		return this.typeCount++;
	}

	long address() {
		return this.address;
	}

	long reportAddress() {
		return this.reportAddress;
	}

	/** An entry a native drain polled: clears its slot and returns the same object. */
	ScheduledTick<T> takeDrained(int id) {
		this.idSnapshotValid = false;
		return this.take(id);
	}

	/** Re-reads head/size/modCount after a native drain refreshed the report buffer. */
	void afterDrain() {
		this.readReport();
	}

	/** Distinct type handles held by this container. */
	int typeHandles() {
		return this.typeCount;
	}

	/** Allocated native entries (queued ticks), for retention checks. */
	int liveEntries() {
		return this.checked(this.call(LIVE_ENTRIES));
	}

	private static int checked(int result) {
		if (result == BUSY) {
			throw new ConcurrentModificationException("Concurrent native chunk tick access");
		}
		return result;
	}

	/** Reads the report buffer the last native mutator filled. */
	private void readReport() {
		this.headId = this.report.getAtIndex(ValueLayout.JAVA_INT, 0);
		this.size = this.report.getAtIndex(ValueLayout.JAVA_INT, 1);
		this.modCount = this.report.getAtIndex(ValueLayout.JAVA_INT, 2);
		this.idSnapshotValid = false;
	}

	private void refreshIdSnapshot() {
		try {
			while (true) {
				int capacity = (int)(this.idSnapshot.byteSize() / 4);
				int n = checked((int)COPY_IDS.invokeExact(this.address, this.idSnapshot.address(), capacity));
				if (n <= capacity) {
					this.idSnapshotSize = n;
					this.idSnapshotValid = true;
					return;
				}
				this.idSnapshot = Arena.ofAuto().allocate(ValueLayout.JAVA_INT, Math.max(n, capacity * 2));
			}
		} catch (Throwable t) {
			throw failure(t);
		} finally {
			Reference.reachabilityFence(this);
		}
	}

	private void store(int id, ScheduledTick<T> tick) {
		if (id >= this.ticks.length) {
			this.ticks = java.util.Arrays.copyOf(this.ticks, Math.max(id + 1, this.ticks.length * 2));
		}
		this.ticks[id] = tick;
	}

	@SuppressWarnings("unchecked")
	private ScheduledTick<T> tick(int id) {
		return (ScheduledTick<T>)this.ticks[id];
	}

	private ScheduledTick<T> take(int id) {
		ScheduledTick<T> tick = this.tick(id);
		this.ticks[id] = null;
		return tick;
	}

	/** {@code ticksPerPosition.add} then {@code tickQueue.add}; false when deduplicated. */
	boolean schedule(ScheduledTick<T> tick, boolean unchecked) {
		BlockPos pos = tick.pos();
		try {
			long packed = (long)SCHEDULE.invokeExact(this.address, tick.triggerTick(), tick.priority().ordinal(), tick.subTickOrder(),
				pos.getX(), pos.getY(), pos.getZ(), this.handleOf(tick.type()), unchecked ? 1 : 0);
			int id = checked((int)packed);
			if (id < 0) {
				return false;
			}
			// A successful offer: one more element, one modCount step (as PriorityQueue).
			this.headId = (int)(packed >> 32);
			this.size++;
			this.modCount++;
			this.idSnapshotValid = false;
			this.store(id, tick);
			return true;
		} catch (Throwable t) {
			throw failure(t);
		} finally {
			Reference.reachabilityFence(this);
		}
	}

	/** {@code ticksPerPosition.add(probe)}. */
	boolean addKey(T type, BlockPos pos) {
		try {
			return checked((int)ADD_KEY.invokeExact(this.address, pos.getX(), pos.getY(), pos.getZ(), this.handleOf(type))) != 0;
		} catch (Throwable t) {
			throw failure(t);
		} finally {
			Reference.reachabilityFence(this);
		}
	}

	boolean containsKey(T type, BlockPos pos) {
		try {
			return checked((int)CONTAINS_KEY.invokeExact(this.address, pos.getX(), pos.getY(), pos.getZ(), this.handleOf(type))) != 0;
		} catch (Throwable t) {
			throw failure(t);
		} finally {
			Reference.reachabilityFence(this);
		}
	}

	/** The head as last reported by the native queue; the same object every time. */
	@Nullable
	ScheduledTick<T> peek() {
		return this.headId < 0 ? null : this.tick(this.headId);
	}

	/** Polls the queue head and its dedup key. */
	@Nullable
	ScheduledTick<T> poll() {
		try {
			long packed = (long)POLL.invokeExact(this.address);
			int id = checked((int)packed);
			if (id < 0) {
				return null;
			}
			// A successful poll: one fewer element, one modCount step (as PriorityQueue).
			this.headId = (int)(packed >> 32);
			this.size--;
			this.modCount++;
			this.idSnapshotValid = false;
			return this.take(id);
		} catch (Throwable t) {
			throw failure(t);
		} finally {
			Reference.reachabilityFence(this);
		}
	}

	int size() {
		return this.size;
	}

	private int modCount() {
		return this.modCount;
	}

	/** The element at an array index, or null past the end (like an empty array slot). */
	@Nullable
	private ScheduledTick<T> at(int index) {
		if (!this.idSnapshotValid) {
			this.refreshIdSnapshot();
		}
		if (index < 0 || index >= this.idSnapshotSize) {
			return null;
		}
		int id = this.idSnapshot.getAtIndex(ValueLayout.JAVA_INT, index);
		return id < 0 ? null : this.tick(id);
	}

	private int call(MethodHandle handle) {
		try {
			return (int)handle.invokeExact(this.address);
		} catch (Throwable t) {
			throw failure(t);
		} finally {
			Reference.reachabilityFence(this);
		}
	}

	/** {@code PriorityQueue.removeAt} plus key removal; returns the element moved before the cursor, if any. */
	@Nullable
	private ScheduledTick<T> removeAt(int index) {
		try {
			int removed = checked((int)REMOVE_AT.invokeExact(this.address, index, this.reportAddress));
			this.readReport();
			if (removed < 0) {
				throw new IllegalStateException("Native chunk tick index out of range: " + index);
			}
			this.ticks[removed] = null;
			int movedId = this.report.getAtIndex(ValueLayout.JAVA_INT, 3);
			return movedId < 0 ? null : this.tick(movedId);
		} catch (Throwable t) {
			throw failure(t);
		} finally {
			Reference.reachabilityFence(this);
		}
	}

	/** {@code PriorityQueue.removeEq}: identity scan, then removeAt. */
	private void removeEq(ScheduledTick<T> tick) {
		for (int i = 0, n = this.size(); i < n; i++) {
			if (this.at(i) == tick) {
				this.removeAt(i);
				return;
			}
		}
	}

	/** Scratch for removed ids; sized to the queue before each box removal. */
	private MemorySegment removedIds = Arena.ofAuto().allocate(I, 16);

	/**
	 * {@code removeIf(tick -> box.isInside(tick.pos()))} for LevelTicks.clearArea only,
	 * run natively through the same PriorityQueue iterator-removal sequence (inclusive
	 * full-int bounds). Generic predicates keep using {@link #iterator()}.
	 */
	void removeInBox(net.minecraft.world.level.levelgen.structure.BoundingBox box) {
		if (this.size == 0) {
			return;
		}
		if (this.removedIds.byteSize() / 4 < this.size) {
			this.removedIds = Arena.ofAuto().allocate(I, Math.max(this.size, this.removedIds.byteSize() / 4 * 2));
		}
		try {
			int count = checked((int)REMOVE_IN_BOX.invokeExact(this.address, box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ(),
				this.removedIds.address(), (int)(this.removedIds.byteSize() / 4), this.reportAddress));
			if (count < 0) {
				throw new IllegalStateException("Native box removal failed: " + count);
			}
			this.readReport();
			for (int k = 0; k < count; k++) {
				this.ticks[this.removedIds.getAtIndex(I, k)] = null;
			}
		} catch (Throwable t) {
			throw failure(t);
		} finally {
			Reference.reachabilityFence(this);
		}
	}

	/** {@code PriorityQueue.iterator()}: array order, with the original removal semantics. */
	Iterator<ScheduledTick<T>> iterator() {
		return new Itr();
	}

	/** Port of {@code PriorityQueue.Itr}. */
	private final class Itr implements Iterator<ScheduledTick<T>> {
		private int cursor;
		private int lastRet = -1;
		@Nullable
		private ArrayDeque<ScheduledTick<T>> forgetMeNot;
		@Nullable
		private ScheduledTick<T> lastRetElt;
		private int expectedModCount = NativeChunkTicks.this.modCount();

		@Override
		public boolean hasNext() {
			return this.cursor < NativeChunkTicks.this.size() || this.forgetMeNot != null && !this.forgetMeNot.isEmpty();
		}

		@Override
		public ScheduledTick<T> next() {
			if (this.expectedModCount != NativeChunkTicks.this.modCount()) {
				throw new ConcurrentModificationException();
			}
			if (this.cursor < NativeChunkTicks.this.size()) {
				return NativeChunkTicks.this.at(this.lastRet = this.cursor++);
			}
			if (this.forgetMeNot != null) {
				this.lastRet = -1;
				this.lastRetElt = this.forgetMeNot.poll();
				if (this.lastRetElt != null) {
					return this.lastRetElt;
				}
			}
			throw new NoSuchElementException();
		}

		@Override
		public void remove() {
			if (this.expectedModCount != NativeChunkTicks.this.modCount()) {
				throw new ConcurrentModificationException();
			}
			if (this.lastRet != -1) {
				ScheduledTick<T> moved = NativeChunkTicks.this.removeAt(this.lastRet);
				this.lastRet = -1;
				if (moved == null) {
					this.cursor--;
				} else {
					if (this.forgetMeNot == null) {
						this.forgetMeNot = new ArrayDeque<>();
					}
					this.forgetMeNot.add(moved);
				}
			} else if (this.lastRetElt != null) {
				NativeChunkTicks.this.removeEq(this.lastRetElt);
				this.lastRetElt = null;
			} else {
				throw new IllegalStateException();
			}
			this.expectedModCount = NativeChunkTicks.this.modCount();
		}
	}

	/** Port of {@code PriorityQueue.PriorityQueueSpliterator}: array order, late-binding fence. */
	Spliterator<ScheduledTick<T>> spliterator() {
		return new Split(0, -1, 0);
	}

	private final class Split implements Spliterator<ScheduledTick<T>> {
		private int index;
		private int fence;
		private int expectedModCount;

		Split(int origin, int fence, int expectedModCount) {
			this.index = origin;
			this.fence = fence;
			this.expectedModCount = expectedModCount;
		}

		private int getFence() {
			int hi = this.fence;
			if (hi < 0) {
				this.expectedModCount = NativeChunkTicks.this.modCount();
				hi = this.fence = NativeChunkTicks.this.size();
			}
			return hi;
		}

		@Nullable
		@Override
		public Spliterator<ScheduledTick<T>> trySplit() {
			int hi = this.getFence(), lo = this.index, mid = lo + hi >>> 1;
			return lo >= mid ? null : new Split(lo, this.index = mid, this.expectedModCount);
		}

		@Override
		public void forEachRemaining(Consumer<? super ScheduledTick<T>> action) {
			if (action == null) throw new NullPointerException();
			if (this.fence < 0) {
				this.fence = NativeChunkTicks.this.size();
				this.expectedModCount = NativeChunkTicks.this.modCount();
			}
			int hi = this.fence;
			int i = this.index;
			this.index = hi;
			for (; i < hi; i++) {
				ScheduledTick<T> e = NativeChunkTicks.this.at(i);
				if (e == null) break;
				action.accept(e);
			}
			if (NativeChunkTicks.this.modCount() != this.expectedModCount) {
				throw new ConcurrentModificationException();
			}
		}

		@Override
		public boolean tryAdvance(Consumer<? super ScheduledTick<T>> action) {
			if (action == null) throw new NullPointerException();
			if (this.fence < 0) {
				this.fence = NativeChunkTicks.this.size();
				this.expectedModCount = NativeChunkTicks.this.modCount();
			}
			int i;
			if ((i = this.index) < this.fence) {
				this.index = i + 1;
				ScheduledTick<T> e = NativeChunkTicks.this.at(i);
				if (e == null || NativeChunkTicks.this.modCount() != this.expectedModCount) {
					throw new ConcurrentModificationException();
				}
				action.accept(e);
				return true;
			}
			return false;
		}

		@Override
		public long estimateSize() {
			return this.getFence() - this.index;
		}

		@Override
		public int characteristics() {
			return Spliterator.SIZED | Spliterator.SUBSIZED | Spliterator.NONNULL;
		}
	}

	/**
	 * The LevelTicks collect phase over native queues: the containersToTick
	 * PriorityQueue (INTRA_TICK_DRAIN_ORDER on heads, JDK heap rules), the drain loop
	 * and leftover rescheduling run in one native call. Buffers are owned by the
	 * LevelTicks thread and grow on demand.
	 */
	/** {@code nextTickForContainer.put(chunkKey, triggerTick)}. */
	@FunctionalInterface
	interface ScheduleUpdate {
		void put(long chunkKey, long triggerTick);
	}

	/**
	 * Per-LevelTicks native container registry plus the persistent containersToTick
	 * heap. Handles are registry-owned bindings (one container may be bound under
	 * several keys or registries). {@code byHandle} keeps each bound Java container,
	 * and with it the report buffer native writes to, alive until native frees the
	 * handle; freed handles are quarantined natively until the next collect, so a
	 * reused handle never meets a stale output. One thread at a time.
	 */
	static final class Registry {
		private static final MethodHandle REGISTRY_CREATE = lifecycle("registry_create", FunctionDescriptor.of(ValueLayout.ADDRESS));
		private static final MethodHandle REGISTRY_RELEASE = lifecycle("registry_release", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
		private static final MethodHandle REGISTRY_LIVE = lifecycle("registry_live", FunctionDescriptor.of(J));
		private static final MethodHandle REGISTER = handle("register", FunctionDescriptor.of(I, J, J, J));
		private static final MethodHandle UNREGISTER = handle("unregister", FunctionDescriptor.of(I, J, I));
		private static final MethodHandle COLLECT = handle("collect", FunctionDescriptor.of(I, J, J, I, I, J, J, J, I, J, I, J, J, I, J));
		private static final int STALE = -5, CAPACITY = -4, EMPTY = -3;

		private final MemorySegment owner;
		private final long address;
		private Object[] byHandle = new Object[64];
		private MemorySegment eligible = Arena.ofAuto().allocate(I, 64);
		private int eligibleCount;
		private MemorySegment ticks = Arena.ofAuto().allocate(I, 2 * 256);
		private MemorySegment updates = Arena.ofAuto().allocate(J, 2 * 64);
		private MemorySegment touched = Arena.ofAuto().allocate(I, 64);
		private MemorySegment freed = Arena.ofAuto().allocate(I, 64);
		private final MemorySegment counts = Arena.ofAuto().allocate(I, 5);
		/**
		 * True while a collect's outputs are being consumed. Native releases quarantined
		 * handles only when the NEXT collect starts, so a collect (or any registry call
		 * that could reuse a handle) must never begin before consumption finishes.
		 */
		private boolean consuming;

		Registry() {
			try {
				MemorySegment pointer = (MemorySegment)REGISTRY_CREATE.invokeExact();
				if (pointer.address() == 0L) {
					throw new IllegalStateException("Native scheduled tick registry allocation failed");
				}
				boolean attached = false;
				try {
					// The cleaner captures only the static release handle, never Java mirrors.
					this.owner = pointer.reinterpret(1, Arena.ofAuto(), Registry::release);
					attached = true;
				} finally {
					if (!attached) release(pointer);
				}
				this.address = this.owner.address();
			} catch (Throwable t) {
				throw failure(t);
			}
		}

		private static void release(MemorySegment pointer) {
			try {
				REGISTRY_RELEASE.invokeExact(pointer);
			} catch (Throwable t) {
				throw failure(t);
			}
		}

		static long liveRegistries() {
			try {
				return (long)REGISTRY_LIVE.invokeExact();
			} catch (Throwable t) {
				throw failure(t);
			}
		}

		private static void status(int result) {
			switch (result) {
				case BUSY -> throw new ConcurrentModificationException("Concurrent native scheduled tick registry access");
				case STALE -> throw new IllegalStateException("Stale scheduled tick container handle");
				default -> {}
			}
		}

		/** Binds a container; returns the registry handle. */
		int register(LevelChunkTicks<?> container) {
			this.checkNotConsuming();
			NativeChunkTicks<?> ticks = container.nativeTicks();
			try {
				int handle = (int)REGISTER.invokeExact(this.address, ticks.address(), ticks.reportAddress());
				status(handle);
				if (handle >= this.byHandle.length) {
					this.byHandle = java.util.Arrays.copyOf(this.byHandle, Math.max(handle + 1, this.byHandle.length * 2));
				}
				this.byHandle[handle] = container;
				return handle;
			} catch (Throwable t) {
				throw failure(t);
			} finally {
				Reference.reachabilityFence(container);
				Reference.reachabilityFence(this);
			}
		}

		/** Unbinds; the container stays alive while a retained heap slot holds it. */
		void unregister(int handle) {
			this.checkNotConsuming();
			try {
				int result = (int)UNREGISTER.invokeExact(this.address, handle);
				status(result);
				if (result == 1) {
					this.byHandle[handle] = null;
				}
			} catch (Throwable t) {
				throw failure(t);
			} finally {
				Reference.reachabilityFence(this);
			}
		}

		/** Appends an eligible container handle in scan order for the next collect. */
		void addEligible(int handle) {
			if (this.eligibleCount == this.eligible.byteSize() / 4) {
				MemorySegment grown = Arena.ofAuto().allocate(I, this.eligibleCount * 2L);
				MemorySegment.copy(this.eligible, 0, grown, 0, this.eligible.byteSize());
				this.eligible = grown;
			}
			this.eligible.setAtIndex(I, this.eligibleCount++, handle);
		}

		int eligibleCount() {
			return this.eligibleCount;
		}

		private static MemorySegment grow(MemorySegment segment, ValueLayout layout, long elements) {
			return segment.byteSize() >= elements * layout.byteSize() ? segment : Arena.ofAuto().allocate(layout, Math.max(elements, segment.byteSize() / layout.byteSize() * 2));
		}

		/**
		 * drainContainers + rescheduleLeftoverContainers natively over the persistent
		 * heap (cleared first when the previous tick cleaned up) and this tick's
		 * eligible handles. Applies drained ticks and map puts in native order and
		 * returns the retained heap size. On the original-equivalent NPE the completed
		 * prefix is applied first, then the NPE is thrown.
		 */
		@SuppressWarnings("unchecked")
		<T> int collect(boolean cleanedUp, long now, long budget, java.util.function.Consumer<ScheduledTick<T>> scheduleForThisTick, ScheduleUpdate updateScheduling) {
			this.checkNotConsuming();
			int result;
			try {
				while (true) {
					result = (int)COLLECT.invokeExact(this.address, this.eligible.address(), this.eligibleCount, cleanedUp ? 1 : 0, now, budget,
						this.ticks.address(), (int)(this.ticks.byteSize() / 8), this.updates.address(), (int)(this.updates.byteSize() / 16),
						this.touched.address(), this.freed.address(), (int)Math.min(this.touched.byteSize(), this.freed.byteSize()) / 4, this.counts.address());
					if (result != CAPACITY) {
						break;
					}
					// Nothing changed natively; grow and retry.
					this.ticks = grow(this.ticks, I, 2L * Math.max(1, this.counts.getAtIndex(I, 0)));
					this.updates = grow(this.updates, J, 2L * Math.max(1, this.counts.getAtIndex(I, 1)));
					int handles = Math.max(1, this.counts.getAtIndex(I, 2));
					this.touched = grow(this.touched, I, handles);
					this.freed = grow(this.freed, I, handles);
				}
			} catch (Throwable t) {
				throw failure(t);
			} finally {
				this.eligibleCount = 0;
				Reference.reachabilityFence(this);
			}
			status(result);
			this.consuming = true;
			try {
				return this.consume(result, scheduleForThisTick, updateScheduling);
			} finally {
				// Also on the NPE path: the outputs were consumed up to the throw point, and
				// the remaining freed handles stay quarantined natively until the next collect.
				this.consuming = false;
			}
		}

		private void checkNotConsuming() {
			if (this.consuming) {
				throw new IllegalStateException("Scheduled tick registry re-entered while consuming collect outputs");
			}
		}

		@SuppressWarnings("unchecked")
		private <T> int consume(int result, java.util.function.Consumer<ScheduledTick<T>> scheduleForThisTick, ScheduleUpdate updateScheduling) {
			int drained = this.counts.getAtIndex(I, 0), updated = this.counts.getAtIndex(I, 1), touchedCount = this.counts.getAtIndex(I, 2),
				freedCount = this.counts.getAtIndex(I, 3), heapSize = this.counts.getAtIndex(I, 4);
			for (int k = 0; k < touchedCount; k++) {
				((LevelChunkTicks<?>)this.byHandle[this.touched.getAtIndex(I, k)]).nativeTicks().afterDrain();
			}
			for (int k = 0; k < drained; k++) {
				NativeChunkTicks<T> source = (NativeChunkTicks<T>)((LevelChunkTicks<?>)this.byHandle[this.ticks.getAtIndex(I, 2L * k)]).nativeTicks();
				scheduleForThisTick.accept(source.takeDrained(this.ticks.getAtIndex(I, 2L * k + 1)));
			}
			for (int k = 0; k < updated; k++) {
				updateScheduling.put(this.updates.getAtIndex(J, 2L * k), this.updates.getAtIndex(J, 2L * k + 1));
			}
			// Freed last: every output naming these handles has been consumed.
			for (int k = 0; k < freedCount; k++) {
				this.byHandle[this.freed.getAtIndex(I, k)] = null;
			}
			if (result == EMPTY) {
				// The original NPEs at the same point (a container drained empty but still
				// queued, e.g. a duplicate retained after a throwing callback). Its completed
				// prefix was applied above, without rollback, as in the original.
				throw new NullPointerException("Scheduled tick container queued without a head");
			}
			return heapSize;
		}
	}
}
