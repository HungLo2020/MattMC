package net.minecraft.server.level;

import it.unimi.dsi.fastutil.longs.LongConsumer;
import it.unimi.dsi.fastutil.longs.LongIterator;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Reference;
import java.util.NoSuchElementException;
import net.minecraft.util.NativeLibraryLoader;

/**
 * A native-owned published level view of one distance field
 * ({@code world/level/chunk_distance/level_view.rs}): the original tracker's
 * {@code Long2ByteOpenHashMap chunks}, as an exact fastutil port filled from its
 * graph's ordered {@code setLevel} changes without a Java replay. Walks are fastutil
 * {@code MapIterator}s over the live table: callbacks between steps may change the
 * view exactly as they could change the Java map, including the original's
 * {@code ArrayIndexOutOfBoundsException} and {@code NullPointerException} when it
 * shrinks or grows under a walk. No native lock is held while Java runs; a walk keeps
 * its view alive.
 */
final class NativeLevelView {
	private static final ValueLayout.OfInt I = ValueLayout.JAVA_INT;
	private static final ValueLayout.OfLong J = ValueLayout.JAVA_LONG;
	private static MethodHandle handle(String name, FunctionDescriptor descriptor, Linker.Option... options) {
		return NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_level_view_" + name, descriptor, options);
	}
	private static final MethodHandle CREATE = handle("create", FunctionDescriptor.of(J, I, I));
	private static final MethodHandle RELEASE = handle("release", FunctionDescriptor.ofVoid(J));
	private static final MethodHandle TAKE_TICKET = handle("take_ticket", FunctionDescriptor.of(J, J, J), Linker.Option.critical(false));
	private static final MethodHandle TAKE_PLAYER = handle("take_player", FunctionDescriptor.of(J, J, J, I), Linker.Option.critical(false));
	private static final MethodHandle SET = handle("set", FunctionDescriptor.of(J, J, J, I), Linker.Option.critical(false));
	private static final MethodHandle GET = handle("get", FunctionDescriptor.of(I, J, J), Linker.Option.critical(false));
	private static final MethodHandle SIZE = handle("size", FunctionDescriptor.of(I, J), Linker.Option.critical(false));
	private static final MethodHandle CURSOR = handle("cursor", FunctionDescriptor.of(J, J));
	private static final MethodHandle CURSOR_RELEASE = handle("cursor_release", FunctionDescriptor.ofVoid(J));
	private static final MethodHandle CURSOR_NEXT = handle("cursor_next", FunctionDescriptor.of(I, J, I, ValueLayout.ADDRESS), Linker.Option.critical(true));
	private static final MethodHandle CURSOR_REMOVE = handle("cursor_remove", FunctionDescriptor.of(I, J, ValueLayout.ADDRESS), Linker.Option.critical(true));

	private final MemorySegment owner;
	private final long id;

	/** {@code absent}: the map's default return value; levels at or above {@code removeFrom} are removed. */
	NativeLevelView(int absent, int removeFrom) {
		long created;
		try { created = (long)CREATE.invokeExact(absent, removeFrom); }
		catch (Throwable t) { throw failure(t); }
		if (created == 0) throw new OutOfMemoryError("Native level view allocation failed");
		MemorySegment pointer = MemorySegment.ofAddress(created);
		try {
			this.id = created;
			this.owner = pointer.reinterpret(1, Arena.ofAuto(), NativeLevelView::release);
		} catch (Throwable t) {
			release(pointer);
			throw t;
		}
	}

	private static void release(MemorySegment pointer) {
		try { RELEASE.invokeExact(pointer.address()); }
		catch (Throwable t) { throw new IllegalStateException("Cannot release native level view", t); }
	}

	private static RuntimeException failure(Throwable t) {
		if (t instanceof RuntimeException r) return r;
		if (t instanceof Error e) throw e;
		return new IllegalStateException("Native level view call failed", t);
	}

	private static void check(long status) {
		if (status == -3) throw new OutOfMemoryError("Native level view allocation failed");
		if (status < 0) throw new IllegalStateException("Native level view fault: " + status);
	}

	/** Applies the ticket graph's changes from its last {@code run}. */
	void takeTicket(TicketChunkDistance distance) {
		long status;
		try { status = (long)TAKE_TICKET.invokeExact(this.id, distance.handleId()); }
		catch (Throwable t) { throw failure(t); }
		finally {
			Reference.reachabilityFence(this.owner);
			distance.fence();
		}
		check(status);
	}

	/** Applies a player-distance field's changes from its last run; the caller holds that graph's lock. */
	void takePlayer(long distances, int field) {
		long status;
		try { status = (long)TAKE_PLAYER.invokeExact(this.id, distances, field); }
		catch (Throwable t) { throw failure(t); }
		finally { Reference.reachabilityFence(this.owner); }
		check(status);
	}

	/** One {@code setLevel(key, level)} outside a graph run. */
	void set(long key, int level) {
		long status;
		try { status = (long)SET.invokeExact(this.id, key, level); }
		catch (Throwable t) { throw failure(t); }
		finally { Reference.reachabilityFence(this.owner); }
		check(status);
	}

	/** {@code chunks.get(key)}. */
	int get(long key) {
		try { return (int)GET.invokeExact(this.id, key); }
		catch (Throwable t) { throw failure(t); }
		finally { Reference.reachabilityFence(this.owner); }
	}

	/** {@code chunks.size()}. */
	int size() {
		try { return (int)SIZE.invokeExact(this.id); }
		catch (Throwable t) { throw failure(t); }
		finally { Reference.reachabilityFence(this.owner); }
	}

	/** The original {@code for (Entry e : Long2ByteMaps.fastIterable(chunks)) if (level(e) <= maxLevel) consumer.accept(key)}. */
	void forEachAtMost(int maxLevel, LongConsumer consumer) {
		Walk walk = new Walk();
		while (walk.advance(maxLevel)) {
			consumer.accept(walk.key);
		}
	}

	/** {@code chunks.keySet().iterator()}. */
	LongIterator keys() {
		return new Walk();
	}

	/** One walk: the native cursor holds the view alive; its arena frees it after the last use. */
	private final class Walk implements LongIterator {
		private final MemorySegment cursor;
		private final long cursorId;
		/** [key or index, level or length, remaining count]. */
		private final long[] out = new long[3];
		/** fastutil's {@code c}: the native walk's remaining count after its last step,
		 * including a step that threw (the original counts down before it can throw). */
		private int left;
		private long key;

		Walk() {
			long created;
			try { created = (long)CURSOR.invokeExact(NativeLevelView.this.id); }
			catch (Throwable t) { throw failure(t); }
			finally { Reference.reachabilityFence(NativeLevelView.this.owner); }
			if (created == 0) throw new OutOfMemoryError("Native level view cursor allocation failed");
			MemorySegment pointer = MemorySegment.ofAddress(created);
			try {
				this.cursorId = created;
				this.cursor = pointer.reinterpret(1, Arena.ofAuto(), Walk::release);
			} catch (Throwable t) {
				release(pointer);
				throw t;
			}
			this.left = NativeLevelView.this.size();
		}

		private static void release(MemorySegment pointer) {
			try { CURSOR_RELEASE.invokeExact(pointer.address()); }
			catch (Throwable t) { throw new IllegalStateException("Cannot release native level view cursor", t); }
		}

		/** The next entry at or below {@code maxLevel} into {@link #key}; false at the end. */
		boolean advance(int maxLevel) {
			int status;
			try { status = (int)CURSOR_NEXT.invokeExact(this.cursorId, maxLevel, MemorySegment.ofArray(this.out)); }
			catch (Throwable t) { throw failure(t); }
			finally { Reference.reachabilityFence(this.cursor); }
			if (status >= 0) {
				this.left = (int)this.out[2];
			}
			switch (status) {
				case 1 -> {
					this.key = this.out[0];
					return true;
				}
				case 0 -> {
					return false;
				}
				case 2 -> throw new ArrayIndexOutOfBoundsException("Index " + this.out[0] + " out of bounds for length " + this.out[1]);
				case 3, 5 -> throw wrappedFailure(status);
				default -> {
					check(status);
					throw new IllegalStateException("Native level view cursor status " + status);
				}
			}
		}

		/** The original iterator's failure reading its wrapped-key list: null (never
		 * created; JDK helpful-NPE text) or an index past it (fastutil's message). */
		private RuntimeException wrappedFailure(int status) {
			if (status == 3) {
				return new NullPointerException("Cannot invoke \"it.unimi.dsi.fastutil.longs.LongArrayList.getLong(int)\" because \"this.wrapped\" is null");
			}
			return new IndexOutOfBoundsException("Index (" + this.out[0] + ") is greater than or equal to list size (" + this.out[1] + ")");
		}

		@Override
		public boolean hasNext() {
			return this.left != 0;
		}

		/** fastutil {@code MapIterator.remove()}: removes the entry last returned from the view. */
		@Override
		public void remove() {
			int status;
			try { status = (int)CURSOR_REMOVE.invokeExact(this.cursorId, MemorySegment.ofArray(this.out)); }
			catch (Throwable t) { throw failure(t); }
			finally { Reference.reachabilityFence(this.cursor); }
			if (status >= 0) {
				this.left = (int)this.out[2];
			}
			switch (status) {
				case 0 -> {
				}
				case 4 -> throw new IllegalStateException();
				case 2 -> throw new ArrayIndexOutOfBoundsException("Index " + this.out[0] + " out of bounds for length " + this.out[1]);
				case 3, 5 -> throw wrappedFailure(status);
				default -> {
					check(status);
					throw new IllegalStateException("Native level view cursor status " + status);
				}
			}
		}

		@Override
		public long nextLong() {
			if (!this.hasNext()) throw new NoSuchElementException();
			if (!this.advance(Integer.MAX_VALUE)) throw new NoSuchElementException();
			return this.key;
		}
	}
}
