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
import java.util.IdentityHashMap;
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
 * The real ChunkMap holder scheduling, promotion, unload and eager-save pipeline on
 * any classpath. ChunkMap is built without its constructor (the #818 test pattern):
 * real TicketStorage, real ChunkMap.DistanceManager and loading tracker, real holders
 * and ChunkMap.save; chunk writes return futures the driver completes or fails, and
 * Util.getMillis is the driver's clock. Everything is observed through ChunkMap's own
 * methods, so implementations may move the state. Prints one digest of the trace.
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

	/** Benchmark mode: no tracing, observations or off-thread reader. */
	static boolean benchmark;

	static String run(PrintWriter trace, int steps, long seed, Path dir) throws Exception {
		RandomSource r = RandomSource.create(seed);
		StringBuilder row = new StringBuilder();
		long[] clock = {1_000_000L};
		IdentityHashMap<ChunkHolder, Integer> ids = new IdentityHashMap<>();
		List<CompletableFuture<Void>> writes = new ArrayList<>();
		List<CompletableFuture<Void>> saveFutures = new ArrayList<>();

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

		TicketStorage tickets = new TicketStorage();
		var managerType = Class.forName("net.minecraft.server.level.ChunkMap$DistanceManager");
		var constructor = managerType.getDeclaredConstructor(ChunkMap.class, TicketStorage.class, Executor.class, Executor.class);
		constructor.setAccessible(true);
		Executor direct = Runnable::run;
		DistanceManager manager = (DistanceManager)constructor.newInstance(map, tickets, direct, direct);
		set(ChunkMap.class, map, "distanceManager", manager);
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
		Field futures = field(GenerationChunkHolder.class, "futures");
		Field startedWork = field(GenerationChunkHolder.class, "startedWork");
		Field accessible = field(ChunkHolder.class, "wasAccessibleSinceLastSave");
		@SuppressWarnings("unchecked")
		java.util.Set<ChunkHolder> toUpdate = (java.util.Set<ChunkHolder>)get(DistanceManager.class, manager, "chunksToUpdateFutures");

		long[] keys = new long[40];
		for (int i = 0; i < keys.length; i++) keys[i] = ChunkPos.asLong(r.nextInt(16) - 8, r.nextInt(16) - 8);
		// Two keys at the coordinate bound: holders beyond it throw on creation.
		int edge = ChunkPos.MAX_COORDINATE_VALUE;
		keys[0] = ChunkPos.asLong(edge - 1, 0);
		keys[1] = ChunkPos.asLong(0, -edge + 2);
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

			for (int step = 0; step < steps; step++) {
				long key = keys[r.nextInt(keys.length)];
				int op = r.nextInt(15);
				try {
					switch (op) {
						case 0, 1, 2 -> {
							// Tickets around the area: loading levels 38..45 (45 is not loaded; radius <= 6).
							Ticket t = new Ticket(r.nextInt(5) == 0 ? TicketType.FORCED : TicketType.UNKNOWN, 38 + r.nextInt(8));
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
							// Loading graph run: the real holder scheduling, then promotion.
							int processed = Integer.MAX_VALUE - (int)runLoading.invoke(loading, Integer.MAX_VALUE);
							toUpdate.clear();
							row.append(" run").append(processed).append(" promote").append(promote.invoke(map));
						}
						case 8 -> {
							// Give a holder an unsaved chunk at its latest status.
							ChunkHolder holder = map.getUpdatingChunkIfPresent(key);
							if (holder != null) {
								LevelChunk chunk = chunk(key);
								@SuppressWarnings("unchecked")
								var array = (AtomicReferenceArray<CompletableFuture<ChunkResult<ChunkAccess>>>)futures.get(holder);
								array.set(ChunkStatus.FULL.getIndex(), CompletableFuture.completedFuture(ChunkResult.of(chunk)));
								((AtomicReference<ChunkStatus>)startedWork.get(holder)).set(ChunkStatus.FULL);
								accessible.set(holder, true);
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
								accessible.set(holder, true);
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
				stream.forEach(h -> row.append(' ').append(ids.computeIfAbsent(h, x -> ids.size())).append('@').append(h.getPos().toLong())
					.append(':').append(h.getTicketLevel()));
				row.append(" upd");
				for (long k : keys) {
					ChunkHolder h = map.getUpdatingChunkIfPresent(k);
					row.append(' ').append(h == null ? -1 : ids.computeIfAbsent(h, x -> ids.size()));
					ChunkHolder v = map.getVisibleChunkIfPresent(k);
					row.append('/').append(v == null ? -1 : ids.computeIfAbsent(v, x -> ids.size()));
				}
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
		return HexFormat.of().formatHex(digest.digest());
	}

	public static void main(String[] args) throws Exception {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
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
		StringBuilder digests = new StringBuilder();
		int steps = 2000;
		try (var trace = new PrintWriter(Files.newBufferedWriter(Path.of(args[1])))) {
			for (long seed = 1; seed <= 8; seed++) digests.append(run(trace, steps, seed, Files.createDirectory(base.resolve("s" + seed)))).append(' ');
		}
		System.out.println("TRACE " + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(digests.toString().getBytes(StandardCharsets.UTF_8))) + " " + steps * 8);
	}
}
