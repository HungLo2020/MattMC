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
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import it.unimi.dsi.fastutil.longs.LongIterator;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.core.SectionPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.TicketStorage;
import org.mockito.Mockito;

/**
 * Tick selection on any classpath: a real DistanceManager with a real TicketStorage,
 * fed seeded ticket churn, player presence (headless stub players, not a client),
 * simulation distance changes and simulation runs. Each step records, through public
 * DistanceManager methods only: the block-ticking walk (forEachEntityTickingChunk) in
 * order; the spawn-candidate walk in order, then Util.shuffle of it with a seeded
 * RandomSource and that source's final state; the spawn chunk count; point reads over a
 * key grid. Some walks reenter (ticket changes and a simulation run inside the
 * consumer) or throw at a chosen entry, recording the visited prefix and any iterator
 * exception. Prints one digest. Usage: VerifyTickSelection <label> <trace> [benchmark rounds warmups]
 */
public final class VerifyTickSelection {
	static Field field(Class<?> type, String name) throws NoSuchFieldException {
		Field f = type.getDeclaredField(name);
		f.setAccessible(true);
		return f;
	}

	static Method method(Class<?> type, String name) throws NoSuchMethodException {
		for (Class<?> c = type; c != null; c = c.getSuperclass()) {
			try {
				Method m = c.getDeclaredMethod(name);
				m.setAccessible(true);
				return m;
			} catch (NoSuchMethodException ignored) {
			}
		}
		throw new NoSuchMethodException(name);
	}

	static boolean benchmark;
	/** Benchmark only the hot point reads (entity-tick rate), not the walks. */
	static boolean pointsOnly;

	static String run(PrintWriter trace, int steps, long seed) throws Exception {
		RandomSource r = RandomSource.create(seed);
		Executor direct = Runnable::run;
		TicketStorage tickets = new TicketStorage();
		DistanceManager manager = new DistanceManager(tickets, direct, direct) {
			@Override
			protected boolean isChunkToRemove(long l) {
				return false;
			}

			@Override
			protected ChunkHolder getChunk(long l) {
				return null;
			}

			@Override
			protected ChunkHolder updateChunkScheduling(long l, int i, ChunkHolder chunkHolder, int j) {
				return null;
			}
		};
		Object simulation = field(DistanceManager.class, "simulationChunkTracker").get(manager);
		Method runSimulation = method(simulation.getClass(), "runAllUpdates");

		// Chunks around the origin (including chunk 0,0, the map's zero key) and a far
		// cluster whose churn grows and shrinks the views.
		long[] keys = new long[48];
		for (int i = 0; i < 32; i++) keys[i] = ChunkPos.asLong(r.nextInt(21) - 10, r.nextInt(21) - 10);
		for (int i = 32; i < keys.length; i++) keys[i] = ChunkPos.asLong(400 + r.nextInt(12), -300 + r.nextInt(12));
		keys[0] = 0;
		long[] probes = new long[64];
		for (int i = 0; i < probes.length; i++) probes[i] = i < 48 ? keys[i] : ChunkPos.asLong(r.nextInt(41) - 20, r.nextInt(41) - 20);
		List<Ticket> held = new ArrayList<>();
		List<Long> heldKeys = new ArrayList<>();
		List<ServerPlayer> players = new ArrayList<>();
		List<SectionPos> playerSections = new ArrayList<>();
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		StringBuilder row = new StringBuilder();

		// Off-thread point reads (as off-thread callers of the public API could make).
		AtomicBoolean stop = new AtomicBoolean();
		AtomicLong reads = new AtomicLong(), bad = new AtomicLong();
		boolean nativeViews = SimulationChunkTracker.class.getDeclaredMethods().length > 0
			&& java.util.Arrays.stream(SimulationChunkTracker.class.getDeclaredMethods()).anyMatch(m -> m.getName().equals("publishedView"));
		Thread reader = new Thread(() -> {
			while (!stop.get()) {
				for (long k : probes) {
					try {
						manager.inEntityTickingRange(k);
						manager.inBlockTickingRange(k);
						int level = manager.getChunkLevel(k, true);
						if (level < 0 || level > 33) bad.incrementAndGet();
					} catch (RuntimeException e) {
						bad.incrementAndGet();
					}
					reads.incrementAndGet();
				}
			}
		}, "tick-selection-reader");
		reader.setDaemon(true);
		// The original views are plain hash maps: reading them off-thread can fail
		// during a rehash, so only the native views are read concurrently.
		if (!benchmark && nativeViews) reader.start();

		try {
			for (int step = 0; step < steps; step++) {
				long key = keys[r.nextInt(keys.length)];
				int op = r.nextInt(12);
				try {
					switch (op) {
						case 0, 1, 2 -> {
							// Simulating tickets: forced, and player simulation at varied levels.
							Ticket t = new Ticket(r.nextBoolean() ? TicketType.FORCED : TicketType.PLAYER_SIMULATION, 28 + r.nextInt(8));
							held.add(t);
							heldKeys.add(key);
							row.append(" add").append(tickets.addTicket(key, t));
						}
						case 3, 4 -> {
							if (!held.isEmpty()) {
								int i = r.nextInt(held.size());
								row.append(" rem").append(tickets.removeTicket(heldKeys.remove(i), held.remove(i)));
							}
						}
						case 5 -> {
							// A player enters a chunk (headless stub player).
							ServerPlayer player = Mockito.mock(ServerPlayer.class, Mockito.withSettings().stubOnly());
							ChunkPos pos = new ChunkPos(key);
							SectionPos section = SectionPos.of(pos, 4);
							manager.addPlayer(section, player);
							players.add(player);
							playerSections.add(section);
							row.append(" join").append(key);
						}
						case 6 -> {
							if (!players.isEmpty()) {
								int i = r.nextInt(players.size());
								manager.removePlayer(playerSections.remove(i), players.remove(i));
								row.append(" leave");
							}
						}
						case 7 -> {
							int distance = 2 + r.nextInt(10);
							manager.updateSimulationDistance(distance);
							row.append(" simdist").append(distance);
						}
						default -> {
							runSimulation.invoke(simulation);
							row.append(" run");
						}
					}
				} catch (java.lang.reflect.InvocationTargetException e) {
					row.append(" EX ").append(e.getCause().getClass().getName());
				} catch (RuntimeException e) {
					row.append(" EX ").append(e.getClass().getName());
				}
				if (benchmark) {
					// The selection a ticking server makes each tick, plus entity-tick point reads.
					runSimulation.invoke(simulation);
					long[] sum = {0};
					if (pointsOnly) {
						for (int i = 0; i < 16; i++) for (long k : probes) {
							if (manager.inEntityTickingRange(k)) sum[0]++;
							if (manager.inBlockTickingRange(k)) sum[0]++;
						}
						row.setLength(0);
						continue;
					}
					manager.forEachEntityTickingChunk(l -> sum[0] += l);
					for (LongIterator it = manager.getSpawnCandidateChunks(); it.hasNext(); ) sum[0] ^= it.nextLong();
					sum[0] += manager.getNaturalSpawnChunkCount();
					for (int i = 0; i < 4; i++) for (long k : probes) if (manager.inEntityTickingRange(k)) sum[0]++;
					row.setLength(0);
					continue;
				}
				// Every step: the selection walks as a ticking server makes them.
				runSimulation.invoke(simulation);
				int mode = r.nextInt(6);
				int at = r.nextInt(40);
				int[] index = {0};
				row.append(" | tick");
				try {
					manager.forEachEntityTickingChunk(l -> {
						row.append(' ').append(l);
						if (index[0]++ == at) {
							if (mode == 0) {
								// Reentry: ticket changes and a simulation run inside the walk.
								for (int i = 0; i < 1 + r.nextInt(30); i++) {
									long k = keys[r.nextInt(keys.length)];
									Ticket t = new Ticket(TicketType.FORCED, 28 + r.nextInt(8));
									held.add(t);
									heldKeys.add(k);
									tickets.addTicket(k, t);
								}
								while (r.nextBoolean() && !held.isEmpty()) {
									int i = r.nextInt(held.size());
									tickets.removeTicket(heldKeys.remove(i), held.remove(i));
								}
								try {
									runSimulation.invoke(simulation);
								} catch (ReflectiveOperationException e) {
									throw new IllegalStateException(e);
								}
								row.append(" [reentered]");
							} else if (mode == 1) {
								throw new IllegalStateException("tick failed");
							}
						}
					});
					row.append(" end");
				} catch (RuntimeException e) {
					row.append(" EX ").append(e.getClass().getName()).append(": ").append(e.getMessage());
				}
				row.append(" | spawn");
				List<Long> candidates = new ArrayList<>();
				for (LongIterator it = manager.getSpawnCandidateChunks(); it.hasNext(); ) candidates.add(it.nextLong());
				for (long l : candidates) row.append(' ').append(l);
				RandomSource shuffleRandom = RandomSource.create(seed * 31 + step);
				Util.shuffle(candidates, shuffleRandom);
				row.append(" | shuffled");
				for (long l : candidates) row.append(' ').append(l);
				row.append(" rng ").append(shuffleRandom.nextLong());
				row.append(" count ").append(manager.getNaturalSpawnChunkCount());
				row.append(" | probe");
				for (long k : probes) {
					row.append(' ').append(manager.getChunkLevel(k, true)).append(manager.inEntityTickingRange(k) ? 'E' : '-')
						.append(manager.inBlockTickingRange(k) ? 'B' : '-').append(manager.hasPlayersNearby(k).toString().charAt(0));
				}
				String line = step + row.toString();
				digest.update(line.getBytes(StandardCharsets.UTF_8));
				if (trace != null) trace.println(line);
				row.setLength(0);
			}
		} finally {
			stop.set(true);
			reader.join();
		}
		if (!benchmark) System.out.println("READER seed " + seed + " reads " + reads.get() + " inconsistent " + bad.get());
		return HexFormat.of().formatHex(digest.digest());
	}

	public static void main(String[] args) throws Exception {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		if (args.length > 2 && args[2].startsWith("benchmark")) {
			benchmark = true;
			pointsOnly = args[2].equals("benchmark-points");
			int rounds = Integer.parseInt(args[3]), warmups = Integer.parseInt(args[4]);
			for (int round = 0; round < rounds; round++) {
				long start = System.nanoTime();
				for (long seed = 1; seed <= 4; seed++) run(null, 3000, seed);
				long ns = System.nanoTime() - start;
				if (round >= warmups) System.out.println("TIMING " + round + " " + ns);
			}
			return;
		}
		int steps = Integer.getInteger("selection.steps", 3000), seeds = Integer.getInteger("selection.seeds", 8);
		StringBuilder digests = new StringBuilder();
		try (var trace = new PrintWriter(Files.newBufferedWriter(Path.of(args[1])))) {
			for (long seed = 1; seed <= seeds; seed++) digests.append(run(trace, steps, seed)).append(' ');
		}
		System.out.println("TRACE " + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(digests.toString().getBytes(StandardCharsets.UTF_8))) + " " + steps * seeds);
	}
}
