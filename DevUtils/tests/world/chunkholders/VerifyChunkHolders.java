package net.minecraft.server.level;

import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceArray;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.util.thread.BlockableEventLoop;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.TicketStorage;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.storage.IOWorker;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.minecraft.world.level.Level;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

/**
 * The real ChunkMap holder scheduling, status transitions, promotion, unload and
 * eager-save pipeline on any classpath. ChunkMap is built without its constructor (the
 * #818 test pattern): real TicketStorage, real ChunkMap.DistanceManager.runAllUpdates
 * with its loading tracker and chunksToUpdateFutures passes, real holders and
 * ChunkMap.save. The prepare*Chunk futures, chunk writes and full-status listener are
 * the driver's: futures complete, fail or throw when it says so, listeners reenter
 * (ticket changes and loading runs inside a pass), and Util.getMillis is its clock.
 * Everything is observed through public holder and ChunkMap methods, so implementations
 * may move the state. Prints one digest of the trace.
 *
 * chunksToUpdateFutures is an identity-hash set. Exact mode runs with -XX:hashCode=2
 * (every identity hash equal) so its traversal, including resizes and additions during
 * reentrant passes, is the same on every JVM and the trace stays exactly ordered.
 * Hash mode ({@code hash} argument) runs with ordinary identity hashes, and prepares
 * complete at once (pending futures and their unload-time completion are exact mode's): the set is
 * replaced by a subclass that records each add with its identity hash, and an
 * independent model of fastutil's open-addressing table replays them to predict every
 * pass's traversal, which is checked against the order the holders' listeners
 * actually ran. Stub decisions there depend on the chunk, not on call order, nothing
 * reenters or throws inside a pass, and per-pass records are sorted, so the digest
 * compares across JVMs while the oracle checks the order within each.
 * Usage: VerifyChunkHolders <label> <trace>
 */
public final class VerifyChunkHolders {
	static Field field(Class<?> type, String name) {
		try {
			Field f = type.getDeclaredField(name);
			f.setAccessible(true);
			return f;
		} catch (NoSuchFieldException e) {
			return null;
		}
	}

	static void set(Class<?> type, Object target, String name, Object value) throws Exception {
		Field f = field(type, name);
		if (f != null) f.set(target, value);
	}

	static Object get(Class<?> type, Object target, String name) throws Exception {
		return field(type, name).get(target);
	}

	/** Setup adapter only: original reflective writes on Frozen/pre, equivalent
	 * native owner operations on Current. Public observations below are unchanged. */
	static final class FullFixtureState {
		final Field futures = field(GenerationChunkHolder.class, "futures");
		final Field startedWork = field(GenerationChunkHolder.class, "startedWork");
		final Field generation = futures == null ? field(GenerationChunkHolder.class, "generation") : null;

		@SuppressWarnings("unchecked")
		CompletableFuture<ChunkResult<ChunkAccess>> put(GenerationChunkHolder holder, LevelChunk chunk) throws Exception {
			var future = CompletableFuture.<ChunkResult<ChunkAccess>>completedFuture(ChunkResult.of(chunk));
			if (futures != null) {
				var array = (AtomicReferenceArray<CompletableFuture<ChunkResult<ChunkAccess>>>)futures.get(holder);
				array.set(ChunkStatus.FULL.getIndex(), future);
				((AtomicReference<ChunkStatus>)startedWork.get(holder)).set(ChunkStatus.FULL);
			} else {
				Object owner = generation.get(holder);
				Method exchange = owner.getClass().getDeclaredMethod("compareFuture", int.class, CompletableFuture.class, CompletableFuture.class);
				Method bump = owner.getClass().getDeclaredMethod("bump", ChunkStatus.class, ChunkStatus.class);
				exchange.setAccessible(true);
				bump.setAccessible(true);
				var previous = holder.getAllFutures().getLast().getSecond();
				if (exchange.invoke(owner, ChunkStatus.FULL.getIndex(), previous, future) != previous)
					throw new AssertionError("fixture future changed concurrently");
				// Only raise to FULL. Existing lower slots and their identities stay
				// untouched, exactly as the original single FULL-slot write above.
				for (ChunkStatus status : ChunkStatus.getStatusList()) bump.invoke(owner, status == ChunkStatus.EMPTY ? null : status.getParent(), status);
			}
			return future;
		}
	}

	static void verifyFixtureSetup() throws Exception {
		GenerationChunkHolder holder = new GenerationChunkHolder(new ChunkPos(1, 2)) {
			public int getTicketLevel() { return 33; }
			public int getQueueLevel() { return 33; }
			protected void addSaveDependency(CompletableFuture<?> future) {}
		};
		if (holder.getLatestStatus() != null || holder.getAllFutures().stream().anyMatch(pair -> pair.getSecond() != null))
			throw new AssertionError("fixture initial state differs");
		FullFixtureState setup = new FullFixtureState();
		for (int pass = 0; pass < 2; pass++) {
			LevelChunk chunk = chunk(holder.getPos().toLong());
			var future = setup.put(holder, chunk);
			var slots = holder.getAllFutures();
			if (slots.getLast().getSecond() != future || holder.getLatestChunk() != chunk || holder.getLatestStatus() != ChunkStatus.FULL)
				throw new AssertionError("fixture FULL injection differs");
			for (int i = 0; i < slots.size() - 1; i++) if (slots.get(i).getSecond() != null)
				throw new AssertionError("fixture changed a lower future slot");
		}
		System.out.println("SETUP_CONTROL passed adapter=" + (setup.futures == null ? "native-generation-owner" : "original-reflection"));
	}

	/** ChunkMap fields the constructor would create; a native implementation instead
	 * provides {@code initializeVerification}. Missing fields are skipped. */
	static void initialize(ChunkMap map) throws Exception {
		set(ChunkMap.class, map, "updatingChunkMap", new it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap<ChunkHolder>());
		Object updating = field(ChunkMap.class, "updatingChunkMap") == null ? null : get(ChunkMap.class, map, "updatingChunkMap");
		if (updating != null) set(ChunkMap.class, map, "visibleChunkMap", ((it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap<?>)updating).clone());
		set(ChunkMap.class, map, "pendingUnloads", new it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap<ChunkHolder>());
		set(ChunkMap.class, map, "toDrop", new it.unimi.dsi.fastutil.longs.LongOpenHashSet());
		set(ChunkMap.class, map, "chunksToEagerlySave", new it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet());
		set(ChunkMap.class, map, "nextChunkSaveTime", new it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap());
		set(ChunkMap.class, map, "unloadQueue", new java.util.concurrent.ConcurrentLinkedQueue<Runnable>());
		set(ChunkMap.class, map, "activeChunkWrites", new AtomicInteger());
		set(ChunkMap.class, map, "chunkTypeCache", new it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap());
		try {
			Method init = ChunkMap.class.getDeclaredMethod("initializeVerification");
			init.setAccessible(true);
			init.invoke(map);
		} catch (NoSuchMethodException ignored) {
		}
		Field pending = field(ChunkMap.class, "pendingGenerationTasks");
		if (pending != null && pending.get(map) == null && pending.getType().getName().equals("net.minecraft.server.level.NativeChunkGeneration$Pending")) {
			var constructor = pending.getType().getDeclaredConstructor();
			constructor.setAccessible(true);
			pending.set(map, constructor.newInstance());
		}
	}

	/** A full chunk whose saved flag behaves as ChunkAccess's: tryMarkSaved clears it once. */
	static LevelChunk chunk(long key) {
		LevelChunk chunk = Mockito.mock(LevelChunk.class, Mockito.withSettings().stubOnly());
		boolean[] unsaved = {true};
		Mockito.when(chunk.getPos()).thenReturn(new ChunkPos(key));
		Mockito.when(chunk.getPersistedStatus()).thenReturn(ChunkStatus.FULL);
		Mockito.when(chunk.isUnsaved()).thenAnswer(a -> unsaved[0]);
		Mockito.when(chunk.tryMarkSaved()).thenAnswer(a -> {
			boolean was = unsaved[0];
			unsaved[0] = false;
			return was;
		});
		Mockito.doAnswer(a -> {
			unsaved[0] = true;
			return null;
		}).when(chunk).markUnsaved();
		return chunk;
	}

	/** A driver future with an id, so traces name futures without identity hashing. */
	static final class Tagged extends CompletableFuture<ChunkResult<LevelChunk>> {
		final String id;

		Tagged(String id) {
			this.id = id;
		}
	}

	/** Benchmark mode: no tracing, observations or off-thread reader. */
	static boolean benchmark;
	/** Hash mode: ordinary identity hashes, replay oracle (see the class comment). */
	static boolean hashMode;
	static long oraclePasses, oracleHolders, oracleMismatches;

	/** fastutil's ReferenceOpenHashSet table (8.5): mix(identityHashCode) & mask, linear
	 * probing, growth at maxFill by reinserting from the top index down, clear keeping
	 * the capacity, iteration from the top index down. Fed recorded hashes only. */
	static final class TableModel {
		Object[] key = new Object[33];
		int[] hash = new int[33];
		int n = 32, mask = 31, size, maxFill = 24;

		static int mix(int x) {
			int h = x * -1640531527;
			return h ^ (h >>> 16);
		}

		static int arraySize(int expected, float f) {
			long s = Math.max(2L, Long.highestOneBit((long)Math.ceil(expected / f) - 1) << 1);
			return (int)s;
		}

		void add(Object k, int h) {
			int pos = mix(h) & this.mask;
			while (this.key[pos] != null) {
				if (this.key[pos] == k) return;
				pos = pos + 1 & this.mask;
			}
			this.key[pos] = k;
			this.hash[pos] = h;
			if (this.size++ >= this.maxFill) this.rehash(arraySize(this.size + 1, 0.75F));
		}

		void rehash(int newN) {
			Object[] newKey = new Object[newN + 1];
			int[] newHash = new int[newN + 1];
			int newMask = newN - 1, i = this.n;
			for (int j = this.size; j-- != 0; ) {
				while (this.key[--i] == null);
				int pos = mix(this.hash[i]) & newMask;
				while (newKey[pos] != null) pos = pos + 1 & newMask;
				newKey[pos] = this.key[i];
				newHash[pos] = this.hash[i];
			}
			this.n = newN;
			this.mask = newMask;
			this.maxFill = Math.min((int)Math.ceil(newN * 0.75F), newN - 1);
			this.key = newKey;
			this.hash = newHash;
		}

		void clear() {
			if (this.size == 0) return;
			this.size = 0;
			java.util.Arrays.fill(this.key, null);
		}

		List<Object> order() {
			List<Object> out = new ArrayList<>();
			for (int pos = this.n; pos-- != 0; ) if (this.key[pos] != null) out.add(this.key[pos]);
			return out;
		}
	}

	/** chunksToUpdateFutures, recording adds (with identity hashes) into the model. The
	 * real traversal is the superclass's. */
	static final class RecordingSet extends it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet<ChunkHolder> {
		final TableModel model = new TableModel();
		java.util.function.Consumer<ChunkHolder> onAdd = h -> {};

		@Override
		public boolean add(ChunkHolder h) {
			this.onAdd.accept(h);
			this.model.add(h, System.identityHashCode(h));
			return super.add(h);
		}

		/** The model's traversal when the set was last cleared (the passes' order). */
		List<Object> cleared;

		@Override
		public void clear() {
			if (!this.isEmpty()) this.cleared = this.model.order();
			this.model.clear();
			super.clear();
		}
	}

	/**
	 * The real runAllUpdates. In hash mode its listener records are sorted, and the order
	 * the holders' queue listeners ran (pass 2) is checked against the model's traversal:
	 * equal, or a prefix when the pass threw.
	 */
	static void runAll(DistanceManager manager, ChunkMap map, RecordingSet toUpdate, boolean[] inPass, List<String> passRecords,
		List<Long> passOrder, StringBuilder row, List<CompletableFuture<ChunkResult<LevelChunk>>> prepared) {
		passRecords.clear();
		passOrder.clear();
		toUpdate.cleared = null;
		inPass[0] = true;
		boolean completed = false;
		try {
			row.append(manager.runAllUpdates(map));
			completed = true;
		} finally {
			inPass[0] = false;
			if (hashMode) {
				passRecords.sort(null);
				for (String text : passRecords) row.append(text);
				prepared.sort(java.util.Comparator.comparing(f -> ((Tagged)f).id));
				if (!passOrder.isEmpty()) {
					List<Object> expected = toUpdate.cleared != null ? toUpdate.cleared : toUpdate.model.order();
					boolean ok = completed ? expected.size() == passOrder.size() : expected.size() >= passOrder.size();
					for (int i = 0; ok && i < passOrder.size(); i++) ok = ((ChunkHolder)expected.get(i)).getPos().toLong() == passOrder.get(i);
					oraclePasses++;
					oracleHolders += passOrder.size();
					if (!ok) oracleMismatches++;
				}
			}
		}
	}

	/** A holder's observable state: level, accessibility, queue level, status, futures. */
	static String state(ChunkHolder h) {
		StringBuilder out = new StringBuilder().append(h.getTicketLevel()).append(h.wasAccessibleSinceLastSave() ? 'a' : '-')
			.append(h.getQueueLevel()).append(h.getFullStatus().name().charAt(0));
		for (var f : List.of(h.getFullChunkFuture(), h.getTickingChunkFuture(), h.getEntityTickingChunkFuture())) {
			out.append(',').append(f instanceof Tagged t ? t.id : f.isDone() ? "U" : "?");
		}
		return out.toString();
	}

	/** The candidate's native id for a holder, or -1 (other trees have none). */
	static int nativeId(ChunkMap map, ChunkHolder h) throws Exception {
		Field registry = field(ChunkMap.class, "holders");
		if (registry == null || h == null) return -1;
		Object holders = registry.get(map);
		Field ids = field(holders.getClass(), "ids");
		return ids == null ? -1 : ((it.unimi.dsi.fastutil.objects.Reference2IntMap<?>)ids.get(holders)).getInt(h);
	}

	/**
	 * Deterministic (exact mode, every tree): a pass throws after the update set holds
	 * holder A, which is dropping; A's unload completes while the set keeps it; new
	 * holders take the freed ids; the retry pass then runs A's pending transition.
	 * Traced: A's effects and state before and after, every replacement holder's state
	 * around C before and after the retry. On a native registry it also checks that A's
	 * id was reused and that the holder owning it was not changed by A's update.
	 */
	static void retainedHolderScenario(StringBuilder row, MessageDigest digest, PrintWriter trace, ChunkMap map, DistanceManager manager,
		TicketStorage tickets, RecordingSet toUpdate, boolean[] scenario, boolean[] inPass, List<String> passRecords, List<Long> passOrder,
		List<CompletableFuture<ChunkResult<LevelChunk>>> prepared, Method promote, java.util.function.ToIntFunction<ChunkHolder> ids) throws Exception {
		long a = ChunkPos.asLong(100, 100), c = ChunkPos.asLong(100, 103);
		scenario[0] = true;
		Ticket ta = new Ticket(TicketType.FORCED, 31);
		row.append("scenario addA ").append(tickets.addTicket(a, ta)).append(" run");
		runAll(manager, map, toUpdate, inPass, passRecords, passOrder, row, prepared);
		row.append(" promote").append(promote.invoke(map));
		ChunkHolder holderA = map.getUpdatingChunkIfPresent(a);
		row.append(" A ").append(ids.applyAsInt(holderA)).append(' ').append(state(holderA));
		int oldId = nativeId(map, holderA);
		// Release A (it drops) and fail the pass at its first queue listener: the set keeps A.
		row.append(" remA ").append(tickets.removeTicket(a, ta)).append(" run");
		scenario[1] = true;
		try {
			runAll(manager, map, toUpdate, inPass, passRecords, passOrder, row, prepared);
		} catch (RuntimeException e) {
			row.append(" EX ").append(e.getClass().getName());
		}
		scenario[1] = false;
		row.append(" keptA ").append(toUpdate.contains(holderA)).append(" pend").append(toUpdate.size()).append(" A ").append(state(holderA));
		row.append(" promote").append(promote.invoke(map));
		// The unload completes while the set still holds A.
		for (int i = 0; i < 4; i++) map.tick(() -> true);
		row.append(" promote").append(promote.invoke(map));
		row.append(" unloadedA ").append(map.getUpdatingChunkIfPresent(a) == null).append(map.getVisibleChunkIfPresent(a) == null)
			.append(" keptA ").append(toUpdate.contains(holderA));
		// New holders around C take the freed ids, then the retry pass runs A's transition.
		Ticket tc = new Ticket(TicketType.FORCED, 31);
		row.append(" addC ").append(tickets.addTicket(c, tc));
		Object loading = get(DistanceManager.class, manager, "loadingChunkTracker");
		Method distance = loading.getClass().getDeclaredMethod("runDistanceUpdates", int.class);
		distance.setAccessible(true);
		distance.invoke(loading, Integer.MAX_VALUE);
		ChunkHolder reuse = null;
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				ChunkHolder h = map.getUpdatingChunkIfPresent(ChunkPos.asLong(100 + dx, 103 + dz));
				if (h != null && oldId >= 0 && nativeId(map, h) == oldId) reuse = h;
			}
		}
		if (oldId >= 0 && reuse == null) {
			// A's id went to a holder outside the window: find it among the set.
			for (ChunkHolder h : toUpdate) if (h != holderA && nativeId(map, h) == oldId) reuse = h;
		}
		String reuseBefore = reuse == null ? null : state(reuse);
		row.append(" retry");
		runAll(manager, map, toUpdate, inPass, passRecords, passOrder, row, prepared);
		row.append(" A ").append(state(holderA)).append(" C");
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				ChunkHolder h = map.getUpdatingChunkIfPresent(ChunkPos.asLong(100 + dx, 103 + dz));
				row.append(' ').append(h == null ? "-" : state(h));
			}
		}
		if (oldId >= 0) {
			// Native only, outside the digest: the id was reused, and A's update did not
			// touch the replacement's state (its own transition is the only change).
			boolean reused = reuse != null;
			System.out.println("SCENARIO reused " + reused + " replacement " + (reused ? reuse.getPos().toLong() : 0)
				+ " before " + reuseBefore + " after " + (reused ? state(reuse) : null) + " A " + state(holderA));
		}
		// Clean up so the random steps start from a quiet area.
		row.append(" remC ").append(tickets.removeTicket(c, tc)).append(" run");
		runAll(manager, map, toUpdate, inPass, passRecords, passOrder, row, prepared);
		row.append(" promote").append(promote.invoke(map));
		for (int i = 0; i < 4; i++) map.tick(() -> true);
		row.append(" promote").append(promote.invoke(map)).append(" size").append(map.size());
		scenario[0] = false;
		String line = "-1 " + row;
		digest.update(line.getBytes(StandardCharsets.UTF_8));
		if (trace != null) trace.println(line);
		row.setLength(0);
	}

	/** Updating holders' levels around a chunk, for the state a throwing replay left. */
	static void grid(StringBuilder row, ChunkMap map, long center, int radius) {
		ChunkPos c = new ChunkPos(center);
		row.append(" grid").append(center).append(':');
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				ChunkHolder h = map.getUpdatingChunkIfPresent(ChunkPos.asLong(c.x + dx, c.z + dz));
				row.append(h == null ? '.' : (char)('0' + h.getTicketLevel()));
			}
		}
	}

	/** A stub decision for a chunk's n-th call, independent of call order. */
	static int roll(long seed, long key, int n, int bound) {
		long z = seed * 0x9E3779B97F4A7C15L + key * 0xBF58476D1CE4E5B9L + n * 0x94D049BB133111EBL;
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return (int)Long.remainderUnsigned(z ^ (z >>> 31), bound);
	}

	static String run(PrintWriter trace, int steps, long seed, Path dir) throws Exception {
		RandomSource r = RandomSource.create(seed);
		StringBuilder row = new StringBuilder();
		long[] clock = {1_000_000L};
		// Driver ids by first sighting, without identity hashing (all identity hashes are
		// equal under -XX:hashCode=2): holders by position, then by reference.
		java.util.HashMap<Long, List<Object[]>> seen = new java.util.HashMap<>();
		int[] nextId = {0};
		java.util.function.ToIntFunction<ChunkHolder> ids = h -> {
			List<Object[]> at = seen.computeIfAbsent(h.getPos().toLong(), x -> new ArrayList<>());
			for (Object[] e : at) if (e[0] == h) return (int)e[1];
			at.add(new Object[]{h, nextId[0]});
			return nextId[0]++;
		};
		List<CompletableFuture<Void>> writes = new ArrayList<>();
		List<CompletableFuture<Void>> saveFutures = new ArrayList<>();
		List<CompletableFuture<ChunkResult<LevelChunk>>> prepared = new ArrayList<>();
		int[] nextFuture = {0};
		long[] stale = {0};
		// The retained-holder scenario: prepares complete at once without consuming the
		// driver's RNG, and the first queue listener of a marked pass throws.
		boolean[] scenario = {false, false};
		java.util.HashMap<Long, Integer> calls = new java.util.HashMap<>();
		// Hash mode: records made inside runAllUpdates, sorted when it returns; the
		// chunks whose queue listener ran, in order, for the oracle.
		List<String> passRecords = new ArrayList<>();
		List<Long> passOrder = new ArrayList<>();
		boolean[] inPass = {false};
		java.util.function.Consumer<String> record = text -> {
			if (hashMode && inPass[0]) passRecords.add(text);
			else row.append(text);
		};

		ServerLevel level = Mockito.mock(ServerLevel.class, Mockito.withSettings().stubOnly());
		MinecraftServer server = Mockito.mock(MinecraftServer.class, Mockito.withSettings().stubOnly());
		Mockito.when(level.getServer()).thenReturn(server);
		Mockito.when(level.noSave()).thenReturn(false);
		Mockito.doAnswer(a -> {
			row.append(" FAIL").append(((ChunkPos)a.getArgument(2)).toLong());
			return null;
		}).when(server).reportChunkSaveFailure(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any());
		ThreadedLevelLightEngine light = Mockito.mock(ThreadedLevelLightEngine.class, Mockito.withSettings().stubOnly());
		Mockito.doAnswer(a -> {
			row.append(" UNL").append(((ChunkPos)a.getArgument(0)).toLong());
			return null;
		}).when(light).updateChunkStatus(ArgumentMatchers.any());
		Mockito.doAnswer(a -> {
			row.append(" ULV").append(((LevelChunk)a.getArgument(0)).getPos().toLong());
			return null;
		}).when(level).unload(ArgumentMatchers.any());
		@SuppressWarnings("unchecked")
		BlockableEventLoop<Runnable> loop = Mockito.mock(BlockableEventLoop.class, Mockito.withSettings().stubOnly());

		Mockito.doAnswer(a -> {
			java.util.function.BooleanSupplier ready = a.getArgument(0);
			// The server thread would run tasks until saves settle: complete them in order.
			while (!ready.getAsBoolean() && !saveFutures.isEmpty()) saveFutures.remove(0).complete(null);
			while (!ready.getAsBoolean() && !prepared.isEmpty()) prepared.remove(0).complete(ChunkHolder.UNLOADED_LEVEL_CHUNK);
			return null;
		}).when(loop).managedBlock(ArgumentMatchers.any());
		ChunkMap map = Mockito.mock(ChunkMap.class, Mockito.withSettings().stubOnly().defaultAnswer(Mockito.CALLS_REAL_METHODS));
		initialize(map);
		set(ChunkMap.class, map, "level", level);
		set(ChunkMap.class, map, "lightEngine", light);
		set(ChunkMap.class, map, "poiManager", Mockito.mock(PoiManager.class, Mockito.withSettings().stubOnly()));
		set(ChunkMap.class, map, "mainThreadExecutor", loop);
		var workerConstructor = IOWorker.class.getDeclaredConstructor(RegionStorageInfo.class, Path.class, boolean.class);
		workerConstructor.setAccessible(true);
		IOWorker worker = workerConstructor.newInstance(new RegionStorageInfo("chunk-holders", Level.OVERWORLD, "chunk"), dir, false);
		set(net.minecraft.world.level.chunk.storage.ChunkStorage.class, map, "worker", worker);
		// Writes complete or fail when the driver says so.
		// save() writes through writeEncoded (current) or write (Frozen); stub that one.
		Method write = null;
		for (String name : new String[]{"writeEncoded", "write"}) {
			try {
				write = net.minecraft.world.level.chunk.storage.ChunkStorage.class.getDeclaredMethod(name, ChunkPos.class, java.util.function.Supplier.class);
				break;
			} catch (NoSuchMethodException ignored) {
			}
		}
		write.setAccessible(true);
		write.invoke(Mockito.doAnswer(a -> {
			CompletableFuture<Void> f = new CompletableFuture<>();
			writes.add(f);
			return f;
		}).when(map), ArgumentMatchers.any(), ArgumentMatchers.any());

		// Status transitions: the driver's prepare futures (sometimes already complete,
		// sometimes throwing), the full-status listener (which may reenter) and the
		// worldgen/light queue-level listeners, answered synchronously.
		Object[] reenter = new Object[3];
		for (String kind : new String[]{"Accessible", "Ticking", "EntityTicking"}) {
			Method prepare = ChunkMap.class.getDeclaredMethod("prepare" + kind + "Chunk", ChunkHolder.class);
			prepare.invoke(Mockito.doAnswer(a -> {
				ChunkHolder holder = a.getArgument(0);
				long key = holder.getPos().toLong();
				record.accept(" P" + kind.charAt(0) + key);
				if (scenario[0]) {
					Tagged done = new Tagged("s" + key);
					done.complete(ChunkResult.of(chunk(key)));
					return done;
				}
				int call = calls.merge(key, 1, Integer::sum);
				// Rarely throws (exact mode only): a throwing pass keeps the whole set. In hash
				// mode every prepare completes at once: a pending one would be completed by an
				// unloading pass, releasing save dependencies (and queuing unloads) in pass order.
				int roll = hashMode ? 1 : r.nextInt(120);
				if (roll == 0) throw new IllegalStateException("prepare failed");
				Tagged f = new Tagged(hashMode ? key + "#" + call : Integer.toString(nextFuture[0]++));
				if (roll < 30) f.complete(ChunkResult.of(chunk(key)));
				else prepared.add(f);
				return f;
			}).when(map), ArgumentMatchers.any(ChunkHolder.class));
		}
		Mockito.doAnswer(a -> {
			record.accept(" FS" + ((ChunkPos)a.getArgument(0)).toLong() + ':' + a.getArgument(1).toString().charAt(0));
			int roll = hashMode || scenario[0] ? 1 : r.nextInt(8);
			if (roll == 0 && reenter[0] != null) {
				// Reentry: a ticket change and a loading run inside the pass.
				long k = ((long[])reenter[2])[r.nextInt(((long[])reenter[2]).length)];
				row.append(" radd").append(((TicketStorage)reenter[0]).addTicket(k, new Ticket(TicketType.UNKNOWN, 33 + r.nextInt(6))));
				row.append(" rrun").append(Integer.MAX_VALUE - (int)((Method)reenter[1]).invoke(get(DistanceManager.class, get(ChunkMap.class, map, "distanceManager"), "loadingChunkTracker"), Integer.MAX_VALUE));
			}
			return null;
		}).when(map).onFullChunkStatusChange(ArgumentMatchers.any(), ArgumentMatchers.any());
		for (String name : new String[]{"worldgenTaskDispatcher", "lightTaskDispatcher"}) {
			ChunkTaskDispatcher dispatcher = Mockito.mock(ChunkTaskDispatcher.class, Mockito.withSettings().stubOnly());
			boolean first = name.startsWith("w");
			Mockito.doAnswer(a -> {
				java.util.function.IntSupplier old = a.getArgument(1);
				int next = a.getArgument(2);
				long at = ((ChunkPos)a.getArgument(0)).toLong();
				record.accept((first ? " Q" : " q") + at + ':' + old.getAsInt() + '>' + next);
				if (first && inPass[0]) passOrder.add(at);
				if (first && scenario[1]) {
					scenario[1] = false;
					throw new IllegalStateException("queue listener failed");
				}
				((java.util.function.IntConsumer)a.getArgument(3)).accept(next);
				return null;
			}).when(dispatcher).onLevelChange(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.anyInt(), ArgumentMatchers.any());
			set(ChunkMap.class, map, name, dispatcher);
		}

		TicketStorage tickets = new TicketStorage();
		var managerType = Class.forName("net.minecraft.server.level.ChunkMap$DistanceManager");
		var constructor = managerType.getDeclaredConstructor(ChunkMap.class, TicketStorage.class, Executor.class, Executor.class);
		constructor.setAccessible(true);
		Executor direct = Runnable::run;
		DistanceManager manager = (DistanceManager)constructor.newInstance(map, tickets, direct, direct);
		set(ChunkMap.class, map, "distanceManager", manager);
		// chunksToUpdateFutures, recording its adds (the subclass keeps the traversal).
		RecordingSet toUpdate = new RecordingSet();
		toUpdate.onAdd = h -> ids.applyAsInt(h);
		set(DistanceManager.class, manager, "chunksToUpdateFutures", toUpdate);
		Object loading = get(DistanceManager.class, manager, "loadingChunkTracker");
		Method runLoading = loading.getClass().getDeclaredMethod("runDistanceUpdates", int.class);
		runLoading.setAccessible(true);
		Method promote = ChunkMap.class.getDeclaredMethod("promoteChunkMap");
		promote.setAccessible(true);
		Method saveAll = ChunkMap.class.getDeclaredMethod("saveAllChunks", boolean.class);
		saveAll.setAccessible(true);
		Method visible = ChunkMap.class.getDeclaredMethod("allChunksWithAtLeastStatus", ChunkStatus.class);
		visible.setAccessible(true);
		Method eager = ChunkMap.class.getDeclaredMethod("setChunkUnsaved", ChunkPos.class);
		eager.setAccessible(true);
		FullFixtureState fixtureState = new FullFixtureState();

		long[] keys = new long[40];
		for (int i = 0; i < keys.length; i++) keys[i] = ChunkPos.asLong(r.nextInt(16) - 8, r.nextInt(16) - 8);
		// Two keys at the coordinate bound: holders beyond it throw on creation.
		int edge = ChunkPos.MAX_COORDINATE_VALUE;
		keys[0] = ChunkPos.asLong(edge - 1, 0);
		keys[1] = ChunkPos.asLong(0, -edge + 2);
		reenter[0] = tickets;
		reenter[1] = runLoading;
		reenter[2] = keys;
		List<Ticket> held = new ArrayList<>();
		List<Long> heldKeys = new ArrayList<>();
		MessageDigest digest = MessageDigest.getInstance("SHA-256");

		// An off-thread reader, as the light engine and DH read: summarized outside the digest.
		java.util.concurrent.atomic.AtomicBoolean stop = new java.util.concurrent.atomic.AtomicBoolean();
		java.util.concurrent.atomic.AtomicLong reads = new java.util.concurrent.atomic.AtomicLong(), bad = new java.util.concurrent.atomic.AtomicLong();
		// The original's updating map is a plain hash map: reading it off-thread can spin
		// during a rehash, so only a native registry's updating lookups are exercised.
		boolean nativeRegistry;
		try {
			ChunkMap.class.getDeclaredMethod("initializeVerification");
			nativeRegistry = true;
		} catch (NoSuchMethodException e) {
			nativeRegistry = false;
		}
		boolean readUpdating = nativeRegistry;
		Thread reader = new Thread(() -> {
			while (!stop.get()) {
				for (long k : keys) {
					try {
						ChunkHolder v = map.getVisibleChunkIfPresent(k);
						ChunkHolder u = readUpdating ? map.getUpdatingChunkIfPresent(k) : null;
						if (v != null && v.getPos().toLong() != k || u != null && u.getPos().toLong() != k) bad.incrementAndGet();
						// The level and status read off-thread by worldgen and the light engine.
						if (v != null && (v.getTicketLevel() < 0 || v.getFullStatus() == null)) bad.incrementAndGet();
					} catch (RuntimeException e) {
						bad.incrementAndGet();
					}
					reads.incrementAndGet();
				}
			}
		}, "holder-reader");
		reader.setDaemon(true);
		if (!benchmark) reader.start();
		try (var millis = Mockito.mockStatic(Util.class, Mockito.CALLS_REAL_METHODS);
			 var snapshots = Mockito.mockStatic(SerializableChunkData.class);
			 var dh = Mockito.mockStatic(com.seibel.distanthorizons.core.api.internal.SharedApi.class)) {
			millis.when(Util::getMillis).thenAnswer(a -> clock[0]);
			int[] copies = {0};
			snapshots.when(() -> SerializableChunkData.copyOf(ArgumentMatchers.any(), ArgumentMatchers.any())).thenAnswer(a -> {
				row.append(" SAVE").append(((ChunkAccess)a.getArgument(1)).getPos().toLong());
				// A snapshot that fails synchronously: save's catch reports it and returns false.
				if (++copies[0] % 9 == 0) throw new IllegalStateException("snapshot failed");
				return Mockito.mock(SerializableChunkData.class, Mockito.withSettings().stubOnly());
			});
			dh.when(() -> com.seibel.distanthorizons.core.api.internal.SharedApi.isChunkAtChunkPosAlreadyUpdating(
				ArgumentMatchers.anyInt(), ArgumentMatchers.anyInt())).thenReturn(true);

			if (!hashMode && !benchmark) {
				retainedHolderScenario(row, digest, trace, map, manager, tickets, toUpdate, scenario, inPass, passRecords, passOrder,
					prepared, promote, ids);
			}

			for (int step = 0; step < steps; step++) {
				long key = keys[r.nextInt(keys.length)];
				int op = r.nextInt(17);
				try {
					switch (op) {
						case 0, 1, 2 -> {
							// Tickets around the area: loading levels 38..45 (45 is not loaded; radius <= 6),
							// and some at 30..33 (entity ticking to full) so holders change status.
							Ticket t = new Ticket(r.nextInt(5) == 0 ? TicketType.FORCED : TicketType.UNKNOWN, r.nextInt(4) == 0 ? 30 + r.nextInt(4) : 38 + r.nextInt(8));
							if (t.getType() == TicketType.FORCED) {
								held.add(t);
								heldKeys.add(key);
							}
							row.append(" add").append(tickets.addTicket(key, t));
						}
						case 3, 4 -> {
							if (!held.isEmpty()) {
								int i = r.nextInt(held.size());
								row.append(" rem").append(tickets.removeTicket(heldKeys.remove(i), held.remove(i)));
							}
						}
						case 5 -> {
							// UNKNOWN tickets expire; holders become droppable.
							for (int i = 0; i < 2; i++) tickets.purgeStaleTickets(map);
							row.append(" purge");
						}
						case 6, 7 -> {
							// The real runAllUpdates: loading graph and holder scheduling, then both
							// chunksToUpdateFutures passes (a throw keeps the set), then promotion.
							row.append(" run");
							try {
								runAll(manager, map, toUpdate, inPass, passRecords, passOrder, row, prepared);
							} catch (RuntimeException e) {
								// What the throwing run left (applied and discarded level changes,
								// the pending set), then a retry and what it leaves.
								row.append(" EX ").append(e.getClass().getName()).append(" pend").append(toUpdate.size());
								grid(row, map, keys[0], 15);
								grid(row, map, keys[1], 15);
								row.append(" retry");
								try {
									runAll(manager, map, toUpdate, inPass, passRecords, passOrder, row, prepared);
								} catch (RuntimeException again) {
									row.append(" EX ").append(again.getClass().getName());
								}
								row.append(" pend").append(toUpdate.size());
								grid(row, map, keys[0], 15);
								grid(row, map, keys[1], 15);
							}
							row.append(" promote").append(promote.invoke(map));
						}
						case 8 -> {
							// Give a holder an unsaved chunk at its latest status.
							ChunkHolder holder = map.getUpdatingChunkIfPresent(key);
							if (holder != null) {
								LevelChunk chunk = chunk(key);
								fixtureState.put(holder, chunk);
								if (r.nextBoolean()) eager.invoke(map, holder.getPos());
								row.append(" chunk").append(key);
							}
						}
						case 9 -> {
							// A pending save dependency: unload waits for it, and its replacement is rechecked.
							ChunkHolder holder = map.getUpdatingChunkIfPresent(key);
							if (holder != null) {
								CompletableFuture<Void> f = new CompletableFuture<>();
								saveFutures.add(f);
								holder.addSaveDependency(f);
								row.append(" dep").append(key);
							}
						}
						case 13 -> {
							ChunkHolder holder = map.getUpdatingChunkIfPresent(key);
							ChunkAccess c = holder == null ? null : holder.getLatestChunk();
							if (c != null) {
								c.markUnsaved();
								row.append(" dirty").append(key);
							}
						}
						case 10 -> {
							if (!saveFutures.isEmpty()) {
								saveFutures.remove(r.nextInt(saveFutures.size())).complete(null);
								row.append(" depdone");
							}
						}
						case 11 -> {
							// Unload queue and eager saves under a tick budget.
							int[] budget = {r.nextInt(4)};
							map.tick(() -> budget[0]-- > 0);
							row.append(" tick");
						}
						case 14, 15 -> {
							// A prepared future completes: with a chunk, unloaded, or exceptionally.
							if (!prepared.isEmpty()) {
								var f = prepared.remove(r.nextInt(prepared.size()));
								int how = r.nextInt(4);
								if (how == 0) f.complete(ChunkHolder.UNLOADED_LEVEL_CHUNK);
								else if (how == 1) f.completeExceptionally(new IllegalStateException("generation failed"));
								else f.complete(ChunkResult.of(chunk(key)));
								row.append(" ready").append(how);
							}
						}
						case 12 -> {
							// Writes finish or fail, in a random order.
							if (!writes.isEmpty()) {
								var w = writes.remove(r.nextInt(writes.size()));
								if (r.nextInt(4) == 0) w.completeExceptionally(new java.io.IOException("write failed"));
								else w.complete(null);
								row.append(" wrote");
							}
						}
						default -> {
							clock[0] += r.nextInt(3) == 0 ? 11_000L : 1_000L;
							if (r.nextInt(6) == 0) {
								boolean flush = r.nextInt(4) == 0;
								saveAll.invoke(map, flush);
								row.append(flush ? " saveallflush" : " saveall");
							}
						}
					}
				} catch (java.lang.reflect.InvocationTargetException e) {
					row.append(" EX ").append(e.getCause().getClass().getName());
				} catch (RuntimeException e) {
					row.append(" EX ").append(e.getClass().getName());
				}
				if (benchmark) {
					// The lookups a ticking server makes, without recording them.
					for (long k : keys) {
						map.getUpdatingChunkIfPresent(k);
						map.getVisibleChunkIfPresent(k);
					}
					row.setLength(0);
					continue;
				}
				row.append(" | vis");
				@SuppressWarnings("unchecked")
				var stream = (java.util.stream.Stream<ChunkHolder>)visible.invoke(map, ChunkStatus.EMPTY);
				stream.forEach(h -> row.append(' ').append(ids.applyAsInt(h)).append('@').append(h.getPos().toLong())
					.append(':').append(h.getTicketLevel()));
				row.append(" upd");
				for (long k : keys) {
					ChunkHolder h = map.getUpdatingChunkIfPresent(k);
					row.append(' ').append(h == null ? -1 : ids.applyAsInt(h));
					ChunkHolder v = map.getVisibleChunkIfPresent(k);
					row.append('/').append(v == null ? -1 : ids.applyAsInt(v));
				}
				// Holder state and transition results through public methods; the pending
				// chunksToUpdateFutures in traversal order (kept by a throwing pass).
				row.append(" st");
				for (long k : keys) {
					ChunkHolder h = map.getUpdatingChunkIfPresent(k);
					if (h == null) continue;
					row.append(' ').append(h.getTicketLevel()).append(h.wasAccessibleSinceLastSave() ? 'a' : '-').append(h.getQueueLevel())
						.append(h.getFullStatus().name().charAt(0));
					for (var f : List.of(h.getFullChunkFuture(), h.getTickingChunkFuture(), h.getEntityTickingChunkFuture())) {
						row.append(',').append(f instanceof Tagged t ? t.id : f.isDone() ? "U" : "?");
					}
				}
				row.append(" pend");
				List<Integer> pending = new ArrayList<>();
				for (ChunkHolder h : toUpdate) {
					pending.add(ids.applyAsInt(h));
					// Coverage: a kept holder that left the updating map (unloading or unloaded).
					if (map.getUpdatingChunkIfPresent(h.getPos().toLong()) != h) stale[0]++;
				}
				if (hashMode) pending.sort(null);
				for (int id : pending) row.append(' ').append(id);
				row.append(" size").append(map.size()).append(" writes").append(((AtomicInteger)get(ChunkMap.class, map, "activeChunkWrites")).get());
				String line = step + row.toString();
				digest.update(line.getBytes(StandardCharsets.UTF_8));
				if (trace != null) trace.println(line);
				row.setLength(0);
			}
		} finally {
			stop.set(true);
			if (!benchmark) reader.join();
			worker.close();
		}
		if (!benchmark) System.out.println("READER seed " + seed + " reads " + reads.get() + " inconsistent " + bad.get());
		System.out.println("COVERAGE seed " + seed + " kept-holders-outside-updating " + stale[0]);
		if (hashMode) System.out.println("ORACLE seed " + seed + " passes " + oraclePasses + " holders " + oracleHolders + " mismatches " + oracleMismatches);
		return HexFormat.of().formatHex(digest.digest());
	}

	public static void main(String[] args) throws Exception {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		verifyFixtureSetup();
		Path base = Files.createTempDirectory("chunk-holders");
		if (args.length > 2 && args[2].equals("benchmark")) {
			// Rounds of the same seeded churn; the first `warmups` are discarded.
			benchmark = true;
			int rounds = Integer.parseInt(args[3]), warmups = Integer.parseInt(args[4]);
			for (int round = 0; round < rounds; round++) {
				long start = System.nanoTime();
				for (long seed = 1; seed <= 4; seed++) run(null, 2000, seed, Files.createDirectory(base.resolve("b" + round + "-" + seed)));
				long ns = System.nanoTime() - start;
				if (round >= warmups) System.out.println("TIMING " + round + " " + ns);
			}
			return;
		}
		hashMode = args.length > 2 && args[2].equals("hash");
		StringBuilder digests = new StringBuilder();
		// -Dholders.steps / -Dholders.seeds shorten a local check; the gate uses the defaults.
		int steps = Integer.getInteger("holders.steps", 2000), seeds = Integer.getInteger("holders.seeds", 8);
		try (var trace = new PrintWriter(Files.newBufferedWriter(Path.of(args[1])))) {
			for (long seed = 1; seed <= seeds; seed++) digests.append(run(trace, steps, seed, Files.createDirectory(base.resolve("s" + seed)))).append(' ');
		}
		System.out.println("TRACE " + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(digests.toString().getBytes(StandardCharsets.UTF_8))) + " " + steps * seeds);
	}
}
