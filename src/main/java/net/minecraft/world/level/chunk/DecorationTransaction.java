package net.minecraft.world.level.chunk;

import it.unimi.dsi.fastutil.shorts.ShortList;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

/**
 * A FEATURES step as one transaction over the chunks it may write. Native
 * sections save themselves at their first write (Rust's copy-on-write). Each
 * chunk's Java state is captured, and its native sections armed, at the
 * chunk's first touch: the centre at {@link #begin}, a neighbour when
 * {@code WorldGenRegion} hands it out, when Rust is about to write it
 * ({@code OP_TOUCH}) or when Java installs native heightmaps or sky sources
 * into it. Every route that can change a chunk passes one of these first, so
 * a capture always precedes the chunk's first change. A failed step rolls
 * every touched chunk back (untouched chunks never changed), so neither saving
 * nor a neighbour's generation can observe a partial decoration; a successful
 * one commits and drops the saved sections.
 */
public final class DecorationTransaction {
	private static final MethodHandle TX_ROLLBACK = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_live_section_tx_rollback",
		FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
			ValueLayout.ADDRESS));
	private static final MethodHandle TX = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_live_section_tx",
		FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG));
	private static final int BEGIN = 0, COMMIT = 1, ROLLBACK = 2;
	/** Verification of the verifier: {@code false} leaves a failed step's writes in place. */
	private static final boolean SNAPSHOT = !"false".equals(System.getProperty("mattmc.worldgen.decorationTransaction"));
	private static final boolean RESTORE = !"false".equals(System.getProperty("mattmc.worldgen.decorationRollback"));
	/** Comparison runs: capture every writable chunk at begin, as before lazy capture. */
	private static final boolean EAGER = Boolean.getBoolean("mattmc.worldgen.decorationEagerCapture");
	/** The step running on this thread, which chunk accessors and native touches join. */
	private static final ThreadLocal<DecorationTransaction> CURRENT = new ThreadLocal<>();
	private static final java.util.concurrent.atomic.AtomicLong IDS = new java.util.concurrent.atomic.AtomicLong();
	/** Each touched chunk's open transaction (chunk identity, not position). */
	private static final java.util.concurrent.ConcurrentHashMap<ChunkAccess, DecorationTransaction> OWNERS = new java.util.concurrent.ConcurrentHashMap<>();
	/** Touches of a chunk another open transaction already owned, and native owner conflicts. */
	public static final java.util.concurrent.atomic.LongAdder OVERLAPS = new java.util.concurrent.atomic.LongAdder(), CONFLICTS = new java.util.concurrent.atomic.LongAdder();

	private final long id = IDS.incrementAndGet();
	/** Java effects the step queues for later (POI updates, light tasks): run in order at commit, dropped on rollback. */
	private final List<Runnable> deferred = new ArrayList<>();
	/** The ownership locks this transaction holds, in acquisition order. */
	private final List<java.util.concurrent.locks.ReentrantLock> locks = new ArrayList<>();
	/** Steps that waited for another transaction's chunks. */
	public static final java.util.concurrent.atomic.LongAdder WAITS = new java.util.concurrent.atomic.LongAdder();

	/**
	 * Verification only: called with "begin" before capture, "rollback" after restoring (ownership
	 * still held), then "rollback-failed" if any rollback stage failed and "released" once ownership
	 * is released, before the step's failure is rethrown.
	 */
	@Nullable public static volatile java.util.function.BiConsumer<String, List<ChunkAccess>> TEST_HOOK;

	private final java.util.IdentityHashMap<ChunkAccess, ChunkState> chunks = new java.util.IdentityHashMap<>();
	private final List<ChunkAccess> captured;
	private final List<NativeLiveBlockSection> live = new ArrayList<>();
	/** Each armed owner's chunk and section index, parallel to {@link #live}. */
	private final List<ChunkAccess> liveChunks = new ArrayList<>();
	private final List<Integer> liveSections = new ArrayList<>();
	private final HolderLookup.Provider registries;
	@Nullable private final DecorationTransaction previous;
	private final Arena arena = Arena.ofConfined();
	private boolean open = true;

	private DecorationTransaction(List<ChunkAccess> chunks, HolderLookup.Provider registries, @Nullable DecorationTransaction previous) {
		this.captured = chunks;
		this.registries = registries;
		this.previous = previous;
	}

	/** Opens the step's transaction over {@code chunks}, capturing {@code center} now and the rest at first touch. */
	public static DecorationTransaction begin(List<ChunkAccess> chunks, ChunkAccess center, HolderLookup.Provider registries) {
		var hook = TEST_HOOK;
		if (hook != null) hook.accept("begin", chunks);
		// Steps never nest: a transaction already open on this thread is an invariant violation.
		if (CURRENT.get() != null) throw new IllegalStateException("FEATURES transaction opened inside another");
		var tx = new DecorationTransaction(chunks, registries, null);
		// Measurement only: no snapshot, so a failure would publish its partial writes.
		if (!SNAPSHOT) return tx;
		tx.acquire();
		CURRENT.set(tx);
		// The step writes the centre directly (heightmap priming, border ticks): capture it up front.
		tx.touch(center);
		if (EAGER) for (ChunkAccess chunk : chunks) tx.touch(chunk);
		return tx;
	}

	/**
	 * Takes ownership of every writable chunk, in the global chunk order, before
	 * anything is read or written; held through commit or rollback. A step whose
	 * region overlaps an open transaction's waits for it. The lock-held path never
	 * waits on executor work: WorldGenRegion hands out present chunks only, and the
	 * step's POI and light effects are queued, not awaited.
	 */
	private void acquire() {
		List<ChunkAccess> ordered = new ArrayList<>();
		for (ChunkAccess chunk : this.captured) {
			boolean seen = false;
			for (ChunkAccess other : ordered) seen |= other == chunk;
			if (!seen) ordered.add(chunk);
		}
		ordered.sort(java.util.Comparator.comparingLong(chunk -> chunk.nativeTxOrder));
		try {
			for (ChunkAccess chunk : ordered) {
				var lock = chunk.nativeTxLock;
				if (lock.isHeldByCurrentThread()) throw new IllegalStateException("FEATURES chunk lock already held on entry");
				if (!lock.tryLock()) {
					WAITS.increment();
					lock.lockInterruptibly();
				}
				this.locks.add(lock);
			}
		} catch (InterruptedException interrupted) {
			this.releaseLocks();
			Thread.currentThread().interrupt();
			throw new java.util.concurrent.CancellationException("FEATURES step interrupted while waiting for chunk ownership");
		} catch (RuntimeException | Error failure) {
			this.releaseLocks();
			throw failure;
		}
	}

	private void releaseLocks() {
		for (int i = this.locks.size() - 1; i >= 0; i--) this.locks.get(i).unlock();
		this.locks.clear();
	}

	/** Queues {@code effect} until the running step commits; false (run it now) outside a transaction. */
	public static boolean defer(Runnable effect) {
		var tx = CURRENT.get();
		if (tx == null || !tx.open) return false;
		tx.deferred.add(effect);
		return true;
	}

	/** The running step's first-touch capture of {@code chunk}, when it is one of the step's writable chunks. */
	public static void touchCurrent(ChunkAccess chunk) {
		var tx = CURRENT.get();
		if (tx != null) tx.touch(chunk);
	}

	private void touch(ChunkAccess chunk) {
		if (!this.open || this.chunks.containsKey(chunk)) return;
		boolean writable = false;
		for (ChunkAccess candidate : this.captured) writable |= candidate == chunk;
		if (!writable) return;
		var owner = OWNERS.putIfAbsent(chunk, this);
		if (owner != null && owner != this) {
			// Ownership locks make this impossible: a genuine invariant violation.
			OVERLAPS.increment();
			throw new IllegalStateException("FEATURES chunk " + chunk.getPos() + " touched by two open transactions");
		}
		int first = this.live.size();
		this.chunks.put(chunk, new ChunkState(chunk, this));
		int armed = this.live.size() - first;
		if (armed == 0) return;
		var owners = this.arena.allocate(ValueLayout.ADDRESS, armed);
		for (int i = 0; i < armed; i++) owners.setAtIndex(ValueLayout.ADDRESS, i, this.live.get(first + i).stageOwner());
		call(owners, armed, BEGIN);
	}

	private int call(MemorySegment owners, int count, int op) {
		try {
			int result = (int)TX.invokeExact(owners, count, op, this.id);
			if (result == -2) {
				// Another transaction owns a native section of a chunk this one holds: invariant violation.
				CONFLICTS.increment();
				throw new IllegalStateException("Native section owned by another FEATURES transaction");
			}
			if (result < 0) throw new IllegalStateException("Invalid section transaction");
			return result;
		} catch (RuntimeException | Error failure) {
			throw failure;
		} catch (Throwable failure) {
			throw new IllegalStateException("Cannot run section transaction", failure);
		}
	}

	/** Every armed owner, for one commit or rollback call. */
	private MemorySegment allOwners() {
		var owners = this.arena.allocate(ValueLayout.ADDRESS, Math.max(1, this.live.size()));
		for (int i = 0; i < this.live.size(); i++) owners.setAtIndex(ValueLayout.ADDRESS, i, this.live.get(i).stageOwner());
		return owners;
	}

	/** The step succeeded: keep everything it wrote. */
	public void commit() {
		if (!this.open) return;
		this.open = false;
		Throwable failure = null;
		try {
			try {
				if (!this.live.isEmpty()) call(this.allOwners(), this.live.size(), COMMIT);
			} catch (RuntimeException | Error thrown) {
				// Only an invariant violation (another transaction's section): the writes are final
				// either way, so their effects still follow before the failure surfaces.
				failure = thrown;
			}
			// The committed step's queued effects in their original order, while this step still owns
			// its chunks: an overlapping step can only queue its own effects after these.
			for (Runnable effect : this.deferred) {
				try {
					effect.run();
				} catch (RuntimeException | Error thrown) {
					failure = first(failure, thrown);
				}
			}
			this.deferred.clear();
		} finally {
			this.close();
		}
		rethrow(failure);
	}

	/** The step failed: every touched chunk returns to its state at its first touch, before any change. */
	public void rollback() {
		if (!this.open) return;
		this.open = false;
		// Every stage runs even if an earlier one fails, so a reportable failure (an invariant
		// violation) never leaves Java-side state tentative behind a closed transaction.
		Throwable failure = null;
		try {
			if (!RESTORE) {
				try {
					if (!this.live.isEmpty()) call(this.allOwners(), this.live.size(), COMMIT);
				} catch (RuntimeException | Error thrown) {
					failure = thrown;
				}
			} else {
				// Lighting may already have read the step's tentative blocks (a light task queued by
				// an earlier step runs on the light thread). Undo's light side-effects, as setBlockState
				// would queue them, converge lighting on the restored blocks.
				List<Relight> relight = new ArrayList<>();
				for (ChunkState state : this.chunks.values()) {
					try {
						state.recordTentative(relight);
					} catch (RuntimeException | Error thrown) {
						failure = first(failure, thrown);
					}
				}
				try {
					// The native call restores every owner of this transaction before reporting a conflict.
					if (!this.live.isEmpty()) this.rollbackNative(relight);
				} catch (RuntimeException | Error thrown) {
					failure = first(failure, thrown);
				}
				for (var section : this.live) {
					try {
						section.reloadViewAfterNativeWrite();
					} catch (RuntimeException | Error thrown) {
						failure = first(failure, thrown);
					}
				}
				for (ChunkState chunk : this.chunks.values()) {
					try {
						chunk.restore(this.registries);
					} catch (RuntimeException | Error thrown) {
						failure = first(failure, thrown);
					}
				}
				for (ChunkState chunk : this.chunks.values()) {
					try {
						chunk.relightSections();
					} catch (RuntimeException | Error thrown) {
						failure = first(failure, thrown);
					}
				}
				for (Relight change : relight) {
					try {
						change.apply();
					} catch (RuntimeException | Error thrown) {
						failure = first(failure, thrown);
					}
				}
			}
			var hook = TEST_HOOK;
			if (hook != null) {
				try {
					hook.accept("rollback", this.captured);
				} catch (RuntimeException | Error thrown) {
					failure = first(failure, thrown);
				}
			}
		} finally {
			// A failed step's POI and light effects never reach the level.
			this.deferred.clear();
			this.close();
		}
		var hook = TEST_HOOK;
		if (hook != null) {
			// Verification: ownership is released, and the failure has not left the step yet.
			if (failure != null) hook.accept("rollback-failed", this.captured);
			hook.accept("released", this.captured);
		}
		rethrow(failure);
	}

	private static Throwable first(@Nullable Throwable failure, Throwable thrown) {
		if (failure == null) return thrown;
		failure.addSuppressed(thrown);
		return failure;
	}

	private static void rethrow(@Nullable Throwable failure) {
		if (failure instanceof RuntimeException runtime) throw runtime;
		if (failure instanceof Error error) throw error;
	}

	/** A block a rollback changed in a lit chunk, with its state before the rollback. */
	private record Relight(ProtoChunk chunk, net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState tentative) {
		void apply() {
			var restored = this.chunk.getBlockState(this.pos);
			if (net.minecraft.world.level.lighting.LightEngine.hasDifferentLightProperties(this.tentative, restored)) {
				this.chunk.nativeLightEngine().checkBlock(this.pos);
			}
		}
	}

	/** Whether lighting holds data for {@code chunk}: ProtoChunk.setBlockState's own light condition. */
	@Nullable
	private static ProtoChunk lit(ChunkAccess chunk) {
		return chunk instanceof ProtoChunk proto && proto.nativeLightEngine() != null
			&& proto.getPersistedStatus().isOrAfter(net.minecraft.world.level.chunk.status.ChunkStatus.INITIALIZE_LIGHT) ? proto : null;
	}

	/** Rolls the native sections back, recording each changed block of a lit chunk. */
	private void rollbackNative(List<Relight> relight) {
		var owners = this.allOwners();
		// Every entry of every owner can change: one call restores and reports them all.
		int capacity = this.live.size() * 4096;
		{
			var changes = this.arena.allocate(ValueLayout.JAVA_INT, capacity * 2L);
			var total = this.arena.allocate(ValueLayout.JAVA_INT);
			int result;
			try {
				result = (int)TX_ROLLBACK.invokeExact(owners, this.live.size(), this.id, changes, capacity, total);
			} catch (Throwable failure) {
				throw new IllegalStateException("Cannot roll back section transaction", failure);
			}
			if (result < 0 && result != -2) throw new IllegalStateException("Invalid section transaction");
			int count = total.get(ValueLayout.JAVA_INT, 0);
			if (count > capacity) throw new IllegalStateException("Rollback reported " + count + " changes for " + capacity + " entries");
			for (int i = 0; i < count; i++) {
				int key = changes.getAtIndex(ValueLayout.JAVA_INT, i * 2L);
				int owner = key >>> 12, index = key & 4095;
				ProtoChunk proto = lit(this.liveChunks.get(owner));
				if (proto == null) continue;
				int section = this.liveSections.get(owner);
				var origin = proto.getPos();
				var pos = new net.minecraft.core.BlockPos(origin.getMinBlockX() + (index & 15),
					net.minecraft.core.SectionPos.sectionToBlockCoord(proto.getSectionYFromSectionIndex(section)) + (index >> 8), origin.getMinBlockZ() + ((index >> 4) & 15));
				relight.add(new Relight(proto, pos, net.minecraft.world.level.block.Block.stateById(changes.getAtIndex(ValueLayout.JAVA_INT, i * 2L + 1))));
			}
			if (result == -2) {
				// Every owner of this transaction was restored (and its changes recorded) first.
				CONFLICTS.increment();
				throw new IllegalStateException("Native section owned by another FEATURES transaction");
			}
		}
	}

	private void close() {
		for (ChunkAccess chunk : this.chunks.keySet()) OWNERS.remove(chunk, this);
		this.releaseLocks();
		if (CURRENT.get() == this) {
			if (this.previous != null) CURRENT.set(this.previous);
			else CURRENT.remove();
		}
		this.arena.close();
	}

	/** One chunk's state that a FEATURES step can change outside its native sections. */
	private static final class ChunkState {
		final ChunkAccess chunk;
		final long[] counters;
		/** Sections without a native owner: copies to put back. */
		final LevelChunkSection[] javaSections;
		final Map<Heightmap.Types, long[]> heightmaps = new EnumMap<>(Heightmap.Types.class);
		@Nullable final long[] skySources;
		final int[] postProcessing;
		final Map<BlockPos, CompoundTag> pending;
		final Map<BlockPos, BlockEntity> blockEntities;
		/** Block entities present before the step, as saved, in case the step changed them. */
		final Map<BlockPos, CompoundTag> blockEntityData = new HashMap<>();
		final int blockTicks, fluidTicks, entities;

		ChunkState(ChunkAccess chunk, DecorationTransaction tx) {
			this.chunk = chunk;
			var sections = chunk.getSectionsForRead();
			this.counters = new long[sections.length];
			this.javaSections = new LevelChunkSection[sections.length];
			for (int i = 0; i < sections.length; i++) {
				var section = sections[i];
				this.counters[i] = section.packedSectionCounts();
				var owner = section.nativeGenerationInput();
				if (owner != null) {
					tx.live.add(owner);
					tx.liveChunks.add(chunk);
					tx.liveSections.add(i);
				}
				else this.javaSections[i] = section.copy();
			}
			chunk.heightmaps.forEach((type, heightmap) -> this.heightmaps.put(type, heightmap.getRawData().clone()));
			var sky = chunk.getSkyLightSources();
			this.skySources = sky == null ? null : sky.nativeRaw().clone();
			this.postProcessing = new int[chunk.postProcessing.length];
			for (int i = 0; i < chunk.postProcessing.length; i++) {
				this.postProcessing[i] = chunk.postProcessing[i] == null ? -1 : chunk.postProcessing[i].size();
			}
			this.pending = new HashMap<>(chunk.pendingBlockEntities);
			this.blockEntities = new HashMap<>(chunk.blockEntities);
			for (var entry : chunk.blockEntities.entrySet()) {
				this.blockEntityData.put(entry.getKey(), entry.getValue().saveWithFullMetadata(tx.registries));
			}
			if (chunk instanceof ProtoChunk proto) {
				this.blockTicks = proto.protoBlockTicks().count();
				this.fluidTicks = proto.protoFluidTicks().count();
				this.entities = proto.getEntities().size();
			} else {
				this.blockTicks = this.fluidTicks = this.entities = -1;
			}
		}

		/** Sections' emptiness before the rollback, for lit chunks. */
		@Nullable private boolean[] tentativeEmpty;

		/** Before a rollback: each lit section's emptiness, and every changed block of a Java-owned section. */
		void recordTentative(List<Relight> relight) {
			ProtoChunk proto = lit(this.chunk);
			if (proto == null) return;
			var sections = this.chunk.getSectionsForRead();
			this.tentativeEmpty = new boolean[sections.length];
			for (int i = 0; i < sections.length; i++) {
				this.tentativeEmpty[i] = sections[i].hasOnlyAir();
				var saved = this.javaSections[i];
				if (saved == null) continue;
				var origin = proto.getPos();
				int baseY = net.minecraft.core.SectionPos.sectionToBlockCoord(proto.getSectionYFromSectionIndex(i));
				for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
					var tentative = sections[i].getBlockState(x, y, z);
					if (tentative != saved.getBlockState(x, y, z)) {
						relight.add(new Relight(proto, new net.minecraft.core.BlockPos(origin.getMinBlockX() + x, baseY + y, origin.getMinBlockZ() + z), tentative));
					}
				}
			}
		}

		/** After a rollback: the section-status light task for every lit section whose emptiness changed. */
		void relightSections() {
			ProtoChunk proto = lit(this.chunk);
			if (proto == null || this.tentativeEmpty == null) return;
			var sections = this.chunk.getSectionsForRead();
			for (int i = 0; i < sections.length; i++) {
				boolean empty = sections[i].hasOnlyAir();
				if (empty != this.tentativeEmpty[i]) {
					var pos = net.minecraft.core.SectionPos.of(proto.getPos(), proto.getSectionYFromSectionIndex(i)).origin();
					proto.nativeLightEngine().updateSectionStatus(pos, empty);
				}
			}
		}

		void restore(HolderLookup.Provider registries) {
			var sections = this.chunk.getSections();
			for (int i = 0; i < sections.length; i++) {
				if (this.javaSections[i] != null) sections[i] = this.javaSections[i];
				else sections[i].restorePackedCounts(this.counters[i]);
			}
			this.chunk.heightmaps.keySet().removeIf(type -> !this.heightmaps.containsKey(type));
			this.heightmaps.forEach((type, data) -> {
				long[] raw = this.chunk.heightmaps.get(type).getRawData();
				System.arraycopy(data, 0, raw, 0, data.length);
			});
			if (this.skySources != null) {
				long[] raw = this.chunk.getSkyLightSources().nativeRaw();
				System.arraycopy(this.skySources, 0, raw, 0, this.skySources.length);
			}
			for (int i = 0; i < this.postProcessing.length; i++) {
				if (this.postProcessing[i] < 0) this.chunk.postProcessing[i] = null;
				else if (this.chunk.postProcessing[i] instanceof it.unimi.dsi.fastutil.shorts.ShortArrayList list) list.size(this.postProcessing[i]);
				else {
					ShortList list = this.chunk.postProcessing[i];
					while (list.size() > this.postProcessing[i]) list.removeShort(list.size() - 1);
				}
			}
			this.chunk.pendingBlockEntities.clear();
			this.chunk.pendingBlockEntities.putAll(this.pending);
			this.chunk.blockEntities.clear();
			for (var entry : this.blockEntities.entrySet()) {
				var saved = this.blockEntityData.get(entry.getKey());
				var current = entry.getValue();
				// Reload from the saved form when the step changed what the block entity holds.
				if (saved != null && !saved.equals(current.saveWithFullMetadata(registries))) {
					var reloaded = BlockEntity.loadStatic(entry.getKey(), current.getBlockState(), saved, registries);
					if (reloaded != null) {
						reloaded.setLevel(current.getLevel());
						current = reloaded;
					}
				}
				this.chunk.blockEntities.put(entry.getKey(), current);
			}
			if (this.chunk instanceof ProtoChunk proto) {
				proto.protoBlockTicks().truncate(this.blockTicks);
				proto.protoFluidTicks().truncate(this.fluidTicks);
				var entities = proto.getEntities();
				while (entities.size() > this.entities) entities.removeLast();
			}
		}
	}
}
