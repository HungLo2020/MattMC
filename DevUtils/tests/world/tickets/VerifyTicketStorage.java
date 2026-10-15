package net.minecraft.world.level;

import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.SimulationChunkTracker;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelHeightAccessor;

/**
 * The real TicketStorage under scripted operations, on any classpath. A stub ChunkMap
 * supplies holders whose save futures the test controls; a listener completes some of
 * them mid-walk to check when readiness is observed. Seeds 9-12 attach the real
 * SimulationChunkTracker (natively notified where the storage supports it) next to a
 * Java loading listener and trace its levels; seeds 13-14 hold 700 tickets so Java
 * predicate walks cross batch boundaries. Prints one digest of the trace.
 * The benchmark drives a real DistanceManager's trackers with production listeners.
 * Usage: VerifyTicketStorage <label> <trace> [benchmark rounds warmups]
 */
public final class VerifyTicketStorage {
	static Field field(Class<?> type, String name) throws Exception {
		Field f = type.getDeclaredField(name);
		f.setAccessible(true);
		return f;
	}

	@SuppressWarnings("unchecked")
	static <T> T allocate(Class<T> type) throws Exception {
		var unsafe = Class.forName("sun.misc.Unsafe");
		return (T)unsafe.getMethod("allocateInstance", Class.class).invoke(field(unsafe, "theUnsafe").get(null), type);
	}

	/** Loaded after bootstrap (TicketType registers on class init). */
	static final class Types {
		static final TicketType[] ALL = {
			TicketType.PLAYER_SPAWN, TicketType.SPAWN_SEARCH, TicketType.DRAGON, TicketType.PLAYER_LOADING,
			TicketType.PLAYER_SIMULATION, TicketType.FORCED, TicketType.PORTAL, TicketType.ENDER_PEARL, TicketType.UNKNOWN
		};
	}

	static String run(PrintWriter trace, int steps, long seed, boolean tracker, boolean big) throws Exception {
		RandomSource r = RandomSource.create(seed);
		TicketStorage storage = new TicketStorage();
		StringBuilder row = new StringBuilder();
		// Stub ChunkMap: holders with controllable save futures.
		ChunkMap chunkMap = allocate(ChunkMap.class);
		Long2ObjectLinkedOpenHashMap<ChunkHolder> holders = new Long2ObjectLinkedOpenHashMap<>();
		field(ChunkMap.class, "updatingChunkMap").set(chunkMap, holders);
		Field saveSync = field(ChunkHolder.class, "saveSync");
		Map<Long, CompletableFuture<?>> futures = new java.util.HashMap<>();
		long[] keys = new long[24];
		for (int i = 0; i < keys.length; i++) {
			keys[i] = i == 0 ? 0L : ChunkPos.asLong(r.nextInt(64) - 32, r.nextInt(64) - 32);
			if (r.nextInt(3) > 0) {
				ChunkHolder holder = allocate(ChunkHolder.class);
				CompletableFuture<?> future = r.nextBoolean() ? CompletableFuture.completedFuture(null) : new CompletableFuture<>();
				saveSync.set(holder, future);
				holders.put(keys[i], holder);
				futures.put(keys[i], future);
			}
		}
		int[] completeOnNextNotify = {-1};
		// One storage mutation from inside the next loading notification (walks, activation).
		Runnable[] mutateOnNextNotify = {null};
		TicketStorage.ChunkUpdated loading = (pos, level, decrease) -> {
			// Notification-time read of the storage, as the real trackers do.
			row.append(" L").append(pos).append(':').append(level).append(decrease ? '-' : '+').append('=').append(storage.getTicketLevelAt(pos, false));
			if (mutateOnNextNotify[0] != null) {
				Runnable mutation = mutateOnNextNotify[0];
				mutateOnNextNotify[0] = null;
				mutation.run();
			}
			if (completeOnNextNotify[0] >= 0) {
				CompletableFuture<?> f = futures.get(keys[completeOnNextNotify[0]]);
				if (f != null) f.complete(null);
				row.append(" done").append(completeOnNextNotify[0]);
				completeOnNextNotify[0] = -1;
			}
		};
		TicketStorage.ChunkUpdated simulation = (pos, level, decrease) ->
			row.append(" S").append(pos).append(':').append(level).append(decrease ? '-' : '+').append('=').append(storage.getTicketLevelAt(pos, true));
		storage.setLoadingChunkUpdatedListener(loading);
		SimulationChunkTracker simulationTracker = tracker ? new SimulationChunkTracker(storage) : null;
		if (!tracker) storage.setSimulationChunkUpdatedListener(simulation);
		IdentityHashMap<Ticket, Integer> tracked = new IdentityHashMap<>();
		List<Ticket> shared = new ArrayList<>();
		// A second storage holding some of the same Ticket objects.
		TicketStorage other = new TicketStorage();
		// Retained getTickets views (key, view), and forced sets replaced since.
		List<long[]> retainedKeys = new ArrayList<>();
		List<List<Ticket>> retained = new ArrayList<>();
		List<LongSet> staleForced = new ArrayList<>();
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		LongSet lastForced = storage.getForceLoadedChunks();
		if (big) {
			// Hundreds of tickets so a predicate walk crosses batch boundaries (256).
			for (int i = 0; i < 700; i++) {
				Ticket t = new Ticket(Types.ALL[r.nextInt(Types.ALL.length)], 31 + r.nextInt(4));
				tracked.putIfAbsent(t, tracked.size());
				if (r.nextInt(8) == 0) shared.add(t);
				storage.addTicket(ChunkPos.asLong(r.nextInt(200) - 100, r.nextInt(200) - 100), t);
			}
			row.setLength(0);
		}
		int[] predicateCalls = {0};
		// A Java removeTicketIf predicate that logs every call, changes later shared
		// tickets (also held by the other storage) without touching lists, sometimes
		// mutates the lists, and sometimes throws.
		TicketStorage.TicketPredicate predicate = (t, l) -> {
			int call = predicateCalls[0]++;
			row.append(" P").append(tracked.getOrDefault(t, -1)).append('@').append(l).append(':').append(t);
			int roll = r.nextInt(big ? 400 : 40);
			if (roll < 4 && !shared.isEmpty()) {
				Ticket later = shared.get(r.nextInt(shared.size()));
				if (roll < 2) later.decreaseTicksLeft(); else later.resetTicksLeft();
			} else if (roll == 4) {
				row.append(" padd").append(storage.addTicket(keys[r.nextInt(keys.length)], new Ticket(Types.ALL[r.nextInt(Types.ALL.length)], 31 + r.nextInt(4))));
			} else if (roll == 5) {
				row.append(" prem").append(storage.removeTicket(keys[r.nextInt(keys.length)], new Ticket(Types.ALL[r.nextInt(Types.ALL.length)], 31 + r.nextInt(4))));
			} else if (roll == 6 && !tracker) {
				// Direct list writes skip notifications, which the tracker mirror (like the
				// original's lazy reads) is not specified for.
				List<Ticket> view = storage.getTickets(keys[r.nextInt(keys.length)]);
				if (!view.isEmpty()) row.append(" pview").append(tracked.getOrDefault(view.remove(0), -1));
			} else if (roll == 7) {
				throw new IllegalStateException("predicate " + call);
			}
			return t.getType().hasTimeout() ? t.isTimedOut() || r.nextInt(big ? 60 : 6) == 0 : r.nextInt(big ? 90 : 9) == 0;
		};
		for (int step = 0; step < steps; step++) {
			long key = keys[r.nextInt(keys.length)];
			int op = r.nextInt(big ? 25 : 24);
			if (!tracker && r.nextInt(10) == 0) {
				// Mutate the storage from the next loading notification; not with the
				// real tracker, whose mirror (like the original's lazy reads) assumes
				// notifications describe every change.
				long mutationKey = keys[r.nextInt(keys.length)];
				int kind = r.nextInt(4);
				Ticket mutationTicket = new Ticket(Types.ALL[r.nextInt(Types.ALL.length)], 31 + r.nextInt(4));
				mutateOnNextNotify[0] = () -> {
					row.append(" mut").append(kind);
					switch (kind) {
						case 0 -> row.append(storage.addTicket(mutationKey, mutationTicket));
						case 1 -> row.append(storage.removeTicket(mutationKey, mutationTicket));
						case 2 -> storage.deactivateTicketsOnClosing();
						default -> storage.activateAllDeactivatedTickets();
					}
				};
			}
			try {
			switch (op) {
				case 0, 1, 2 -> {
					Ticket t = new Ticket(Types.ALL[r.nextInt(Types.ALL.length)], 31 + r.nextInt(4));
					tracked.putIfAbsent(t, tracked.size());
					if (r.nextInt(4) == 0) shared.add(t);
					row.append(" add").append(storage.addTicket(key, t));
				}
				case 3 -> {
					if (!shared.isEmpty()) {
						// One instance stored under another chunk: shared ticksLeft.
						Ticket t = shared.get(r.nextInt(shared.size()));
						row.append(" addShared").append(storage.addTicket(key, t));
					}
				}
				case 4, 5 -> row.append(" rem").append(storage.removeTicket(key, new Ticket(Types.ALL[r.nextInt(Types.ALL.length)], 31 + r.nextInt(4))));
				case 6 -> storage.addTicketWithRadius(Types.ALL[r.nextInt(Types.ALL.length)], new ChunkPos(key), r.nextInt(3));
				case 7 -> storage.removeTicketWithRadius(Types.ALL[r.nextInt(Types.ALL.length)], new ChunkPos(key), r.nextInt(3));
				case 8 -> row.append(" forced").append(storage.updateChunkForced(new ChunkPos(key), r.nextBoolean()));
				case 9, 10, 11 -> {
					if (r.nextInt(3) == 0) completeOnNextNotify[0] = r.nextInt(keys.length);
					storage.purgeStaleTickets(chunkMap);
					row.append(" purge");
				}
				case 12 -> {
					if (r.nextInt(8) == 0) {
						storage.deactivateTicketsOnClosing();
						row.append(" close");
						storage.activateAllDeactivatedTickets();
						row.append(" reopen");
					}
				}
				case 13 -> {
					if (r.nextInt(6) == 0) {
						storage.replaceTicketLevelOfType(31 + r.nextInt(4), Types.ALL[r.nextInt(Types.ALL.length)]);
						row.append(" replace");
					}
				}
				case 14 -> {
					// Save/load round trip of the persisted (PERSIST) tickets.
					var json = TicketStorage.CODEC.encodeStart(JsonOps.INSTANCE, storage).getOrThrow();
					row.append(" save").append(json);
					TicketStorage loaded = TicketStorage.CODEC.parse(new Dynamic<>(JsonOps.INSTANCE, json)).getOrThrow();
					loaded.activateAllDeactivatedTickets();
					row.append(" loaded").append(TicketStorage.CODEC.encodeStart(JsonOps.INSTANCE, loaded).getOrThrow());
				}
				case 16 -> {
					List<Ticket> view = storage.getTickets(key);
					if (retained.size() < 6) {
						retained.add(view);
						retainedKeys.add(new long[]{key});
					} else {
						int i = r.nextInt(retained.size());
						retained.set(i, view);
						retainedKeys.set(i, new long[]{key});
					}
				}
				case 17 -> {
					if (!tracker && !retained.isEmpty()) {
						// Mutation through a retained list, as a caller of the original could.
						List<Ticket> view = retained.get(r.nextInt(retained.size()));
						Ticket t = new Ticket(Types.ALL[r.nextInt(Types.ALL.length)], 31 + r.nextInt(4));
						tracked.putIfAbsent(t, tracked.size());
						int size = view.size();
						switch (r.nextInt(3)) {
							case 0 -> {
								view.add(r.nextInt(size + 1), t);
								row.append(" vadd");
							}
							case 1 -> {
								if (size > 0) row.append(" vset").append(tracked.getOrDefault(view.set(r.nextInt(size), t), -1));
							}
							default -> {
								if (size > 0) row.append(" vrem").append(tracked.getOrDefault(view.remove(r.nextInt(size)), -1));
							}
						}
					}
				}
				case 18 -> {
					if (!shared.isEmpty()) row.append(" oadd").append(other.addTicket(key, shared.get(r.nextInt(shared.size()))));
				}
				case 19 -> {
					other.purgeStaleTickets(chunkMap);
					row.append(" opurge");
				}
				case 20 -> row.append(" orem").append(other.removeTicket(key, new Ticket(Types.ALL[r.nextInt(Types.ALL.length)], 31 + r.nextInt(4))));
				case 21, 22, 24 -> {
					it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<List<Ticket>> removed = r.nextBoolean() ? new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>() : null;
					row.append(" pwalk");
					storage.removeTicketIf(predicate, removed);
					if (removed != null) {
						for (var e : removed.long2ObjectEntrySet()) {
							row.append(' ').append(e.getLongKey()).append('[');
							for (Ticket t : e.getValue()) row.append(tracked.getOrDefault(t, -1)).append(',');
							row.append(']');
						}
					}
				}
				default -> {
					var f = futures.get(key);
					if (f != null && !f.isDone() && r.nextBoolean()) { f.complete(null); row.append(" ready"); }
				}
			}
			} catch (RuntimeException e) {
				// Callback-driven mutation can make the original throw mid-walk; the
				// class and the state it leaves behind must match.
				row.append(" EX ").append(e.getClass().getName());
			}
			mutateOnNextNotify[0] = null;
			// Observations.
			row.append(" |");
			for (long k : keys) {
				List<Ticket> list = storage.getTickets(k);
				if (!list.isEmpty()) {
					row.append(' ').append(k).append('[');
					for (Ticket t : list) row.append(t).append('#').append(tracked.getOrDefault(t, -1)).append(',');
					row.append(']').append(storage.getTicketDebugString(k, true)).append('/').append(storage.getTicketDebugString(k, false));
				}
			}
			LongSet forced = storage.getForceLoadedChunks();
			row.append(" forced").append(forced == lastForced ? "=" : "*").append(forced);
			if (forced != lastForced) {
				staleForced.add(lastForced);
				if (staleForced.size() > 3) staleForced.remove(0);
			}
			lastForced = forced;
			// Replaced sets must stay as they were.
			for (LongSet stale : staleForced) row.append(" stale").append(stale);
			row.append(" views");
			for (int i = 0; i < retained.size(); i++) {
				List<Ticket> view = retained.get(i);
				row.append(' ').append(storage.getTickets(retainedKeys.get(i)[0]) == view ? '=' : '*').append('[');
				for (Ticket t : view) row.append(t).append('#').append(tracked.getOrDefault(t, -1)).append(',');
				row.append(']');
			}
			row.append(" other");
			for (long k : keys) {
				List<Ticket> list = other.getTickets(k);
				if (!list.isEmpty()) {
					row.append(' ').append(k).append('[');
					for (Ticket t : list) row.append(t).append('#').append(tracked.getOrDefault(t, -1)).append(',');
					row.append(']');
				}
			}
			row.append(other.getForceLoadedChunks());
			row.append(" keep").append(storage.shouldKeepDimensionActive()).append(" has").append(storage.hasTickets());
			row.append(" shared");
			for (Ticket t : shared) row.append(' ').append(t);
			if (simulationTracker != null) {
				simulationTracker.runAllUpdates();
				row.append(" sim");
				for (long k : keys) {
					ChunkPos pos = new ChunkPos(k);
					for (int dx = -2; dx <= 2; dx += 2) row.append(' ').append(simulationTracker.getLevel(new ChunkPos(pos.x + dx, pos.z)));
				}
			}
			String line = step + row.toString();
			digest.update(line.getBytes(StandardCharsets.UTF_8));
			if (trace != null) trace.println(line);
			row.setLength(0);
		}
		return HexFormat.of().formatHex(digest.digest());
	}

	public static void main(String[] args) throws Exception {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		try (var in = ClassLoader.getSystemResourceAsStream("net/minecraft/world/level/TicketStorage.class")) {
			System.out.println("CLASS TicketStorage " + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes())));
		}
		if (args.length > 2 && args[2].equals("benchmark")) {
			benchmark(Integer.parseInt(args[3]), Integer.parseInt(args[4]));
			return;
		}
		if (args.length > 2 && args[2].equals("churn")) {
			churn(Integer.parseInt(args[3]), Integer.parseInt(args[4]));
			return;
		}
		StringBuilder digests = new StringBuilder();
		int steps = 3000;
		try (var trace = new PrintWriter(Files.newBufferedWriter(Path.of(args[1])))) {
			for (long seed = 1; seed <= 14; seed++) digests.append(run(trace, seed > 12 ? 600 : steps, seed, seed > 8 && seed <= 12, seed > 12)).append(' ');
		}
		System.out.println("TRACE " + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(digests.toString().getBytes(StandardCharsets.UTF_8))) + " " + (steps * 12 + 1200));
	}

	/**
	 * The S0006 baseline churn (custom-listener path): Java listeners counting
	 * notifications and a Java removeTicketIf predicate, so every notification
	 * and predicate is a Java call. 2000 ticks of 40 forced/unknown adds,
	 * 100-ticket removal batches and the predicate walk.
	 */
	static void churn(int rounds, int warmups) {
		long sink = 0;
		for (int round = 0; round < rounds; round++) {
			long start = System.nanoTime();
			TicketStorage storage = new TicketStorage();
			int[] calls = {0};
			storage.setLoadingChunkUpdatedListener((pos, level, decrease) -> calls[0]++);
			storage.setSimulationChunkUpdatedListener((pos, level, decrease) -> calls[0]++);
			List<Ticket> held = new ArrayList<>();
			for (int tick = 0; tick < 2000; tick++) {
				for (int i = 0; i < 40; i++) {
					long key = ChunkPos.asLong((tick * 7 + i * 13) % 64 - 32, (tick * 3 + i * 5) % 64 - 32);
					Ticket t = new Ticket(i % 4 == 0 ? TicketType.FORCED : TicketType.UNKNOWN, 31 + i % 3);
					storage.addTicket(key, t);
					if (i % 4 == 0) held.add(t);
				}
				if (held.size() > 400) {
					for (int k = 0; k < 100; k++) {
						Ticket t = held.remove(0);
						storage.removeTicket(ChunkPos.asLong(0, 0), t);
					}
				}
				storage.removeTicketIf((t, l) -> { t.decreaseTicksLeft(); return t.isTimedOut(); }, null);
			}
			long ns = System.nanoTime() - start;
			sink += calls[0];
			if (round >= warmups) System.out.println("TIMING " + round + " " + ns);
		}
		System.out.println("CHECKSUM " + sink);
	}

	/** ChunkMap's holder bookkeeping, as NativeLoadingChunkTrackerTest's manager keeps it. */
	static final class Manager extends DistanceManager {
		static final LevelHeightAccessor HEIGHT = LevelHeightAccessor.create(0, 16);
		final Long2ObjectLinkedOpenHashMap<ChunkHolder> holders = new Long2ObjectLinkedOpenHashMap<>();
		final Long2ObjectLinkedOpenHashMap<ChunkHolder> pendingUnloads = new Long2ObjectLinkedOpenHashMap<>();
		final it.unimi.dsi.fastutil.longs.LongOpenHashSet toDrop = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
		long scheduled;

		Manager(TicketStorage storage) {
			super(storage, Runnable::run, Runnable::run);
		}

		@Override protected boolean isChunkToRemove(long l) { return this.toDrop.contains(l); }

		@Override protected ChunkHolder getChunk(long l) { return this.holders.get(l); }

		@Override
		protected ChunkHolder updateChunkScheduling(long l, int i, ChunkHolder chunkHolder, int j) {
			this.scheduled += i;
			if (!ChunkLevel.isLoaded(j) && !ChunkLevel.isLoaded(i)) return chunkHolder;
			if (chunkHolder != null) chunkHolder.setTicketLevel(i);
			if (chunkHolder != null) {
				if (!ChunkLevel.isLoaded(i)) this.toDrop.add(l); else this.toDrop.remove(l);
			}
			if (ChunkLevel.isLoaded(i) && chunkHolder == null) {
				chunkHolder = this.pendingUnloads.remove(l);
				if (chunkHolder != null) chunkHolder.setTicketLevel(i);
				else chunkHolder = new ChunkHolder(new ChunkPos(l), i, HEIGHT, null, (pos, level, setter, nextLevel) -> {}, null);
				this.holders.put(l, chunkHolder);
			}
			return chunkHolder;
		}

		void processUnloads() {
			for (long l : this.toDrop.toLongArray()) {
				ChunkHolder chunkHolder = this.holders.remove(l);
				if (chunkHolder != null) this.pendingUnloads.put(l, chunkHolder);
			}
			this.toDrop.clear();
		}
	}

	/**
	 * Server-shaped ticket churn per tick: portal, ender pearl, spawn-search, forced,
	 * dragon and unknown tickets at random chunks; removals of untimed tickets; the
	 * purge (with holder readiness reads) and both trackers' updates, as
	 * ServerChunkCache.tick runs them. Prints per-round nanoseconds and a checksum.
	 */
	static void benchmark(int rounds, int warmups) throws Exception {
		Field loadingField = field(DistanceManager.class, "loadingChunkTracker");
		Field simulationField = field(DistanceManager.class, "simulationChunkTracker");
		long checksum = 0;
		for (int round = 0; round < rounds; round++) {
			RandomSource r = RandomSource.create(round % 4);
			TicketStorage storage = new TicketStorage();
			Manager manager = new Manager(storage);
			ChunkMap chunkMap = allocate(ChunkMap.class);
			field(ChunkMap.class, "updatingChunkMap").set(chunkMap, manager.holders);
			Object loadingTracker = loadingField.get(manager);
			var runLoading = loadingTracker.getClass().getDeclaredMethod("runDistanceUpdates", int.class);
			runLoading.setAccessible(true);
			SimulationChunkTracker simulationTracker = (SimulationChunkTracker)simulationField.get(manager);
			List<long[]> untimed = new ArrayList<>();
			long start = System.nanoTime();
			for (int tick = 0; tick < 1500; tick++) {
				for (int i = 0; i < 12; i++) {
					long key = ChunkPos.asLong(r.nextInt(96) - 48, r.nextInt(96) - 48);
					switch (r.nextInt(6)) {
						case 0 -> storage.addTicketWithRadius(TicketType.PORTAL, new ChunkPos(key), 3);
						case 1 -> storage.addTicketWithRadius(TicketType.ENDER_PEARL, new ChunkPos(key), 2);
						case 2 -> storage.addTicketWithRadius(TicketType.SPAWN_SEARCH, new ChunkPos(key), 1);
						case 3 -> {
							storage.updateChunkForced(new ChunkPos(key), true);
							untimed.add(new long[]{key, 0});
						}
						case 4 -> {
							storage.addTicketWithRadius(TicketType.DRAGON, new ChunkPos(key), 2);
							untimed.add(new long[]{key, 1});
						}
						default -> storage.addTicket(key, new Ticket(TicketType.UNKNOWN, 31 + r.nextInt(3)));
					}
				}
				while (untimed.size() > 200) {
					long[] t = untimed.remove(r.nextInt(untimed.size()));
					if (t[1] == 0) storage.updateChunkForced(new ChunkPos(t[0]), false);
					else storage.removeTicketWithRadius(TicketType.DRAGON, new ChunkPos(t[0]), 2);
				}
				storage.purgeStaleTickets(chunkMap);
				simulationTracker.runAllUpdates();
				checksum += Integer.MAX_VALUE - (int)runLoading.invoke(loadingTracker, Integer.MAX_VALUE);
				manager.processUnloads();
			}
			long ns = System.nanoTime() - start;
			checksum += manager.scheduled + manager.holders.size() + storage.getForceLoadedChunks().size();
			if (round >= warmups) System.out.println("TIMING " + round + " " + ns);
		}
		System.out.println("CHECKSUM " + checksum);
	}
}
