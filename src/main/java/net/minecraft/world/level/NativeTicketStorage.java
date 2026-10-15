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
	private static final MethodHandle ADD = handle("storage_add", FunctionDescriptor.of(J, J, J, I, I, I, J, I, J));
	private static final MethodHandle ADD_DEACTIVATED = handle("storage_add_deactivated", FunctionDescriptor.of(J, J, J, I, I, I, J, I, J));
	private static final MethodHandle REMOVE = heap("storage_remove", FunctionDescriptor.of(J, J, J, I, I, I, ValueLayout.ADDRESS));
	private static final MethodHandle NOTIFY = handle("storage_notify", FunctionDescriptor.of(J, J, I, J, I, I));
	private static final MethodHandle LIST_LEVEL = handle("storage_list_level", FunctionDescriptor.of(I, J, J, I));
	private static final MethodHandle LEVEL = handle("storage_level", FunctionDescriptor.of(I, J, J, I));
	private static final MethodHandle LIST = handle("storage_list", FunctionDescriptor.of(J, J, J));
	private static final MethodHandle LIST_SIZE = handle("storage_list_size", FunctionDescriptor.of(I, J, J));
	private static final MethodHandle LIST_GET = handle("storage_list_get", FunctionDescriptor.of(I, J, J, I));
	private static final MethodHandle LIST_PUT = heap("storage_list_put", FunctionDescriptor.of(J, J, J, I, I, I, I, I, J, I, J, ValueLayout.ADDRESS));
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
	private static final MethodHandle CELLS_FREE = handle("cells_free", FunctionDescriptor.of(I, I));

	static final int LOADING = 0;
	static final int SIMULATION = 1;
	static final int PURGE = 0;
	static final int DEACTIVATE = 1;
	static final int JAVA_PREDICATE = 2;
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

	private final MemorySegment owner;
	private final long id;
	/** Out-values of remove and walk steps. */
	final long[] io = new long[4];
	/**
	 * Most tickets per Java-predicate batch. {@code -Dmattmc.ticketStorage.batch=N}
	 * fixes the size instead (1 is one ticket per call), for parity tests.
	 */
	private static final int BATCH_LIMIT = 256;
	private static final int FIXED_BATCH = Math.min(BATCH_LIMIT, Integer.getInteger("mattmc.ticketStorage.batch", 0));
	/** {@code (key, cell)} pairs of a Java-predicate batch. */
	final long[] batch = new long[2 * BATCH_LIMIT];
	/** Next batch size: grows while predicates keep returning false. */
	private int batchSize = FIXED_BATCH > 0 ? FIXED_BATCH : 16;
	/**
	 * Counts calls that may change the lists, so a predicate batch knows whether
	 * its remaining tickets are still the ones the original would visit.
	 */
	long modifications;
	private long[] export = new long[64];
	/** One view per list arena index, so an unchanged list keeps its identity. */
	private TicketList[] views = new TicketList[16];

	NativeTicketStorage() {
		long created;
		try { created = (long)CREATE.invokeExact(TicketStorage.ABSENT_LEVEL); }
		catch (Throwable t) { throw failure(t); }
		if (created == 0) throw new OutOfMemoryError("Native ticket storage allocation failed");
		MemorySegment pointer = MemorySegment.ofAddress(created);
		try {
			this.id = created;
			this.owner = pointer.reinterpret(1, Arena.ofAuto(), NativeTicketStorage::release);
		} catch (Throwable t) {
			release(pointer);
			throw t;
		}
	}

	private static void release(MemorySegment pointer) {
		try { RELEASE.invokeExact(pointer.address()); }
		catch (Throwable t) { throw new IllegalStateException("Cannot release native ticket storage", t); }
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
		long status;
		try {
			status = (long)ADD.invokeExact(this.id, key, type, ticket.getTicketLevel(), Types.flags(type), ticket.getType().timeout(), cell,
				cell < 0 ? ticket.unboundTicksLeft() : 0L);
		}
		catch (Throwable t) { throw failure(t); }
		finally {
			Reference.reachabilityFence(this.owner);
			// A bound cell is retained natively; its ticket's cleaner must not free it meanwhile.
			Reference.reachabilityFence(ticket);
		}
		Cells.bindNew(ticket, status);
		if (cell >= 0 && (status & RESULT) != 0) Cells.pin(ticket);
		return check(status);
	}

	void addDeactivated(long key, Ticket ticket) {
		this.modifications++;
		int type = Types.id(ticket.getType());
		int cell = ticket.boundCell();
		long status;
		try {
			status = (long)ADD_DEACTIVATED.invokeExact(this.id, key, type, ticket.getTicketLevel(), Types.flags(type), ticket.getType().timeout(), cell,
				cell < 0 ? ticket.unboundTicksLeft() : 0L);
		}
		catch (Throwable t) { throw failure(t); }
		finally {
			Reference.reachabilityFence(this.owner);
			// A bound cell is retained natively; its ticket's cleaner must not free it meanwhile.
			Reference.reachabilityFence(ticket);
		}
		Cells.bindNew(ticket, status);
		if (cell >= 0 && (status & 255) == 0) Cells.pin(ticket);
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
	void checkBatchValue(long walk, int index) {
		long status;
		try { status = (long)BATCH_VALUE.invokeExact(this.id, walk, index); }
		catch (Throwable t) { throw failure(t); }
		finally { Reference.reachabilityFence(this.owner); }
		check(status);
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

	/** {@code getTickets(key)}: a live view of the active list, or the shared empty list. */
	java.util.List<Ticket> tickets(long key) {
		long list;
		try { list = (long)LIST.invokeExact(this.id, key); }
		catch (Throwable t) { throw failure(t); }
		finally { Reference.reachabilityFence(this.owner); }
		if (list == -1L) return java.util.List.of();
		int index = (int)list;
		if (index >= this.views.length) {
			this.views = java.util.Arrays.copyOf(this.views, Math.max(index + 1, this.views.length * 2));
		}
		TicketList view = this.views[index];
		if (view == null || view.list != list) {
			view = new TicketList(this, list);
			this.views[index] = view;
		}
		return view;
	}

	/**
	 * One stored list as Java saw its {@code ObjectArrayList}: reads and writes
	 * go to the native list (writes, like the original's, bypass listeners and
	 * the forced set). A list leaves the storage only when emptied; from then on
	 * the view is that detached empty list, which no longer affects the storage.
	 */
	private static final class TicketList extends AbstractList<Ticket> implements RandomAccess {
		private final NativeTicketStorage storage;
		final long list;
		@org.jetbrains.annotations.Nullable
		private java.util.List<Ticket> detached;

		TicketList(NativeTicketStorage storage, long list) {
			this.storage = storage;
			this.list = list;
		}

		/** The detached list once the native one has left the storage, else null. */
		@org.jetbrains.annotations.Nullable
		private java.util.List<Ticket> detached() {
			if (this.detached == null && this.nativeSize() < 0) {
				this.detached = new it.unimi.dsi.fastutil.objects.ObjectArrayList<>();
			}
			return this.detached;
		}

		private int nativeSize() {
			try { return (int)LIST_SIZE.invokeExact(this.storage.id, this.list); }
			catch (Throwable t) { throw failure(t); }
			finally { Reference.reachabilityFence(this.storage.owner); }
		}

		@Override
		public int size() {
			java.util.List<Ticket> detached = this.detached;
			if (detached != null) return detached.size();
			int size = this.nativeSize();
			return size >= 0 ? size : this.detached().size();
		}

		@Override
		public Ticket get(int index) {
			java.util.List<Ticket> detached = this.detached();
			if (detached != null) return detached.get(index);
			int cell;
			try { cell = (int)LIST_GET.invokeExact(this.storage.id, this.list, index); }
			catch (Throwable t) { throw failure(t); }
			finally { Reference.reachabilityFence(this.storage.owner); }
			if (index < 0 || cell < 0) throw new IndexOutOfBoundsException(index);
			return Cells.ticket(cell);
		}

		@Override
		public void add(int index, Ticket ticket) {
			java.util.List<Ticket> detached = this.detached();
			if (detached != null) {
				detached.add(index, ticket);
				return;
			}
			this.storage.modifications++;
			check(this.put(0, index, ticket));
		}

		@Override
		public Ticket set(int index, Ticket ticket) {
			java.util.List<Ticket> detached = this.detached();
			if (detached != null) return detached.set(index, ticket);
			this.storage.modifications++;
			long status = this.put(1, index, ticket);
			// Read the replaced ticket while a release still leaves it pinned.
			Ticket old = (status & 255) == 0 ? Cells.ticket((int)this.storage.io[0]) : null;
			check(status);
			return old;
		}

		@Override
		public Ticket remove(int index) {
			java.util.List<Ticket> detached = this.detached();
			if (detached != null) return detached.remove(index);
			this.storage.modifications++;
			long status;
			try { status = (long)LIST_REMOVE.invokeExact(this.storage.id, this.list, index, MemorySegment.ofArray(this.storage.io)); }
			catch (Throwable t) { throw failure(t); }
			finally { Reference.reachabilityFence(this.storage.owner); }
			// Read the ticket while a release still leaves it pinned.
			Ticket removed = (status & 255) == 0 ? Cells.ticket((int)this.storage.io[0]) : null;
			check(status);
			return removed;
		}

		/** Stores {@code ticket} (null throws, as reading it back would have); the unchecked status. */
		private long put(int mode, int index, Ticket ticket) {
			java.util.Objects.requireNonNull(ticket);
			int type = Types.id(ticket.getType());
			int cell = ticket.boundCell();
			long status;
			try {
				status = (long)LIST_PUT.invokeExact(this.storage.id, this.list, mode, index, type, ticket.getTicketLevel(), Types.flags(type),
					ticket.getType().timeout(), cell, cell < 0 ? ticket.unboundTicksLeft() : 0L, MemorySegment.ofArray(this.storage.io));
			}
			catch (Throwable t) { throw failure(t); }
			finally {
				Reference.reachabilityFence(this.storage.owner);
				Reference.reachabilityFence(ticket);
			}
			Cells.bindNew(ticket, status);
			if (cell >= 0 && (status & 255) == 0) Cells.pin(ticket);
			return status;
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
	 * Tickets by native cell. A ticket binds to a cell when first stored and
	 * keeps it for life: its cleaner frees the cell once the ticket is
	 * unreachable. While storage entries refer to the cell, {@code strong}
	 * keeps the ticket alive (the storage held it, as its lists did); when the
	 * entries are gone a drain unpins it, leaving only {@code weak}. Pinning,
	 * unpinning, binding and freeing all hold {@link #LOCK}, and a drain
	 * unpins only cells the native side reports unreferenced inside that lock,
	 * so a concurrent re-store re-pins after it. Any thread may do any of these.
	 */
	static final class Cells {
		private static final Object LOCK = new Object();
		private static final java.lang.ref.Cleaner CLEANER = java.lang.ref.Cleaner.create();
		private static volatile Ticket[] strong = new Ticket[64];
		private static volatile java.lang.ref.WeakReference<?>[] weak = new java.lang.ref.WeakReference<?>[64];
		private static long[] pages = new long[0];
		private static long[] drained = new long[64];

		/** The ticket bound to {@code cell}; stored cells are always pinned. */
		static Ticket ticket(int cell) {
			Ticket ticket = strong[cell];
			if (ticket != null) return ticket;
			java.lang.ref.WeakReference<?> reference = weak[cell];
			return reference == null ? null : (Ticket)reference.get();
		}

		private static void ensure(int cell) {
			if (cell >= strong.length) {
				int length = Math.max(cell + 1, strong.length * 2);
				strong = java.util.Arrays.copyOf(strong, length);
				weak = java.util.Arrays.copyOf(weak, length);
			}
		}

		/** Binds {@code ticket} to the cell a store reported creating for it. */
		static void bindNew(Ticket ticket, long status) {
			int cell = (int)(status >>> 32) - 1;
			if (cell < 0) return;
			synchronized (LOCK) {
				ensure(cell);
				strong[cell] = ticket;
				weak[cell] = new java.lang.ref.WeakReference<>(ticket);
				ticket.bindCell(cell, address(cell));
			}
			CLEANER.register(ticket, new Free(cell));
		}

		/** Re-pins a bound ticket a store retained again. */
		static void pin(Ticket ticket) {
			int cell = ticket.boundCell();
			if (cell < 0) return;
			synchronized (LOCK) {
				strong[cell] = ticket;
			}
		}

		private static long address(int cell) {
			int page = cell >>> 12;
			if (page >= pages.length) pages = java.util.Arrays.copyOf(pages, Math.max(page + 1, pages.length * 2));
			if (pages[page] == 0) {
				try { pages[page] = (long)CELLS_PAGE.invokeExact(page); }
				catch (Throwable t) { throw failure(t); }
			}
			return pages[page] + (long)(cell & 4095) * 16;
		}

		/** Unpins every queued cell that no storage entry refers to now. */
		static void drain() {
			synchronized (LOCK) {
				int count;
				try {
					count = (int)CELLS_DRAIN.invokeExact(MemorySegment.ofArray(drained), drained.length);
					if (count < 0) {
						drained = new long[Math.max(-count, drained.length * 2)];
						count = (int)CELLS_DRAIN.invokeExact(MemorySegment.ofArray(drained), drained.length);
					}
				}
				catch (Throwable t) { throw failure(t); }
				Ticket[] tickets = strong;
				for (int i = 0; i < count; i++) {
					tickets[(int)drained[i]] = null;
				}
			}
		}

		/** A ticket's cleaner: its cell is reused only once the ticket is unreachable. */
		private record Free(int cell) implements Runnable {
			@Override
			public void run() {
				synchronized (LOCK) {
					weak[this.cell] = null;
					try { int freed = (int)CELLS_FREE.invokeExact(this.cell); }
					catch (Throwable t) { throw failure(t); }
				}
			}
		}
	}
}
