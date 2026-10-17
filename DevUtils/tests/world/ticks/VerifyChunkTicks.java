package net.minecraft.world.ticks;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Actual LevelTicks/LevelChunkTicks on any classpath: many chunk containers, a small
 * per-tick budget, chunk eligibility toggling, callback rescheduling and clearArea
 * during a run, copyArea, and unload/reload through pack/unpack. Prints a digest of
 * the run order and state. Usage: VerifyChunkTicks <label> <trace> [benchmark warmups samples]
 */
public final class VerifyChunkTicks {
	private static final int CHUNKS = 8;
	/** The grid starts at chunk -OFFSET, so block coordinates go negative too. */
	private static final int OFFSET = 2;
	/** Benchmark phase timers (ns): scheduling loop, LevelTicks.tick, everything else. */
	static long scheduleNanos, tickNanos;
	/** Benchmark variant without clearArea/copyArea (those run only from gametests and /clone). */
	static boolean gameplayOnly;

	static String run(PrintWriter trace, int ticks, long seed) throws Exception {
		RandomSource random = RandomSource.create(seed);
		boolean[] eligible = new boolean[CHUNKS * CHUNKS];
		java.util.Arrays.fill(eligible, true);
		LevelTicks<Block>[] holder = new LevelTicks[1];
		long aliasKey = ChunkPos.asLong(1000, 1000);
		LevelTicks<Block> level = new LevelTicks<>(key -> {
			if (key == aliasKey) return true;
			int x = ChunkPos.getX(key) + OFFSET, z = ChunkPos.getZ(key) + OFFSET;
			return x >= 0 && z >= 0 && x < CHUNKS && z < CHUNKS && eligible[x * CHUNKS + z];
		});
		// A second LevelTicks sharing one container (bound in both registries).
		LevelTicks<Block> other = new LevelTicks<>(key -> true);
		holder[0] = level;
		LevelChunkTicks<Block>[] containers = new LevelChunkTicks[CHUNKS * CHUNKS];
		for (int x = 0; x < CHUNKS; x++) {
			for (int z = 0; z < CHUNKS; z++) {
				containers[x * CHUNKS + z] = new LevelChunkTicks<>();
				level.addContainer(new ChunkPos(x - OFFSET, z - OFFSET), containers[x * CHUNKS + z]);
			}
		}
		Block[] types = {Blocks.STONE, Blocks.REDSTONE_WIRE, Blocks.OBSERVER, Blocks.REPEATER};
		long[] sub = {0};
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		StringBuilder row = new StringBuilder();
		long[] gameTime = {0};
		for (int tick = 0; tick < ticks; tick++) {
			gameTime[0]++;
			long scheduleStart = System.nanoTime();
			for (int i = random.nextInt(60); i > 0; i--) {
				BlockPos pos = new BlockPos(random.nextInt(CHUNKS * 16) - OFFSET * 16, random.nextInt(4) - 60, random.nextInt(CHUNKS * 16) - OFFSET * 16);
				// Some ticks share priority and subTickOrder across chunks: exact container-comparator ties.
				long order = random.nextInt(6) == 0 ? sub[0] : sub[0]++;
				level.schedule(new ScheduledTick<>(types[random.nextInt(types.length)], pos, gameTime[0] + random.nextInt(8),
					random.nextInt(6) == 0 ? TickPriority.NORMAL : TickPriority.values()[random.nextInt(TickPriority.values().length)], order));
			}
			scheduleNanos += System.nanoTime() - scheduleStart;
			if (random.nextInt(10) == 0) eligible[random.nextInt(eligible.length)] ^= true;
			if (random.nextInt(50) == 0) {
				// Unload and reload a chunk through its saved form.
				int index = random.nextInt(containers.length);
				ChunkPos chunk = new ChunkPos(index / CHUNKS - OFFSET, index % CHUNKS - OFFSET);
				List<SavedTick<Block>> saved = containers[index].pack(gameTime[0]);
				level.removeContainer(chunk);
				containers[index] = new LevelChunkTicks<>(new ArrayList<>(saved));
				containers[index].unpack(gameTime[0]);
				level.addContainer(chunk, containers[index]);
				if (trace != null) row.append(" reload").append(index).append(':').append(saved.size());
			}
			if (random.nextInt(70) == 0) {
				// Rebind a key to a reloaded container without removeContainer first.
				int index = random.nextInt(containers.length);
				ChunkPos chunk = new ChunkPos(index / CHUNKS - OFFSET, index % CHUNKS - OFFSET);
				List<SavedTick<Block>> saved = containers[index].pack(gameTime[0]);
				containers[index] = new LevelChunkTicks<>(new ArrayList<>(saved));
				containers[index].unpack(gameTime[0]);
				level.addContainer(chunk, containers[index]);
				if (trace != null) row.append(" rebind").append(index);
			}
			if (tick % 997 == 500 && !gameplayOnly) {
				// Full-i32 y bounds over the whole (negative-offset) grid. x/z stay bounded:
				// the original clearArea iterates every chunk coordinate in its box.
				level.clearArea(new BoundingBox(-OFFSET * 16, Integer.MIN_VALUE, -OFFSET * 16, (CHUNKS - OFFSET) * 16 - 1, Integer.MAX_VALUE, (CHUNKS - OFFSET) * 16 - 1));
				if (trace != null) row.append(" clearall ").append(level.count());
			}
			if (tick % 400 == 100) {
				// The same container bound under a second key, later removed again.
				level.addContainer(new ChunkPos(1000, 1000), containers[9]);
				if (trace != null) row.append(" alias");
			} else if (tick % 400 == 300) {
				level.removeContainer(new ChunkPos(1000, 1000));
				if (trace != null) row.append(" unalias");
			}
			if (tick == 50) other.addContainer(new ChunkPos(1 - OFFSET, 1 - OFFSET), containers[CHUNKS + 1]);
			if (tick > 50 && tick % 10 == 0) {
				int otherIndex = CHUNKS + 1;
				other.tick(gameTime[0], 5, (pos, type) -> { if (trace != null) row.append(" o").append(pos.getX()).append(',').append(pos.getZ()); });
				if (trace != null) row.append(" ocount ").append(other.count());
			}
			if (random.nextInt(80) == 0 && !gameplayOnly) {
				int x = random.nextInt(CHUNKS * 16 - 20) - OFFSET * 16, z = random.nextInt(CHUNKS * 16 - 20) - OFFSET * 16;
				level.copyAreaFrom(level, new BoundingBox(x, -64, z, x + 15, -55, z + 15), new Vec3i(random.nextInt(9) - 4, 0, random.nextInt(9) - 4));
			}
			int budget = switch (random.nextInt(8)) {
				case 0 -> 0;
				case 1 -> 1;
				case 2, 3 -> 7;
				default -> 200;
			};
			// Seeds 1-5 throw from callbacks (the original then crashes after retaining
			// state); seeds 6-10 never throw, so they cover long runs.
			int throwAt = seed <= 5 && random.nextInt(25) == 0 ? random.nextInt(5) : -1;
			int[] calls = {0};
			RandomSource inner = RandomSource.create(seed * 31 + tick);
			long tickStart = System.nanoTime();
			try {
			level.tick(gameTime[0], budget, (pos, type) -> {
				if (calls[0]++ == throwAt) {
					// A throwing callback skips cleanup: run queue and leftover containers are retained.
					throw new IllegalStateException("callback");
				}
				if (trace != null) row.append(' ').append(pos.getX()).append(',').append(pos.getY()).append(',').append(pos.getZ()).append(type == Blocks.STONE ? 's' : type == Blocks.REDSTONE_WIRE ? 'r' : type == Blocks.OBSERVER ? 'o' : 'p');
				int roll = inner.nextInt(20);
				if (roll == 0) {
					// Callback reschedules the same position and type.
					level.schedule(new ScheduledTick<>(type, pos, gameTime[0] + inner.nextInt(3), TickPriority.NORMAL, sub[0]++));
				} else if (roll == 1 && !gameplayOnly) {
					// Callback clears an area that may hold already-collected ticks.
					level.clearArea(new BoundingBox(pos.getX() - 3, -64, pos.getZ() - 3, pos.getX() + 3, -55, pos.getZ() + 3));
				}
				if (trace != null) row.append(level.willTickThisTick(pos, type) ? '+' : '-').append(level.hasScheduledTick(pos, type) ? 'h' : 'n');
			});
			} catch (IllegalStateException e) {
				tickNanos += System.nanoTime() - tickStart;
				if (!"callback".equals(e.getMessage())) throw e;
				if (trace != null) row.append(" threw");
			} catch (NullPointerException e) {
				// The original crashes here (a server would stop): record it and end this seed.
				if (trace != null) {
					String line = tick + row.toString() + " crashed NullPointerException";
					digest.update(line.getBytes(StandardCharsets.UTF_8));
					trace.println(line);
				}
				return HexFormat.of().formatHex(digest.digest());
			}
			tickNanos += System.nanoTime() - tickStart;
			if (trace != null) {
				row.append(" | count ").append(level.count());
				String line = tick + row.toString();
				digest.update(line.getBytes(StandardCharsets.UTF_8));
				trace.println(line);
				row.setLength(0);
			}
		}
		StringBuilder tail = new StringBuilder("saved");
		for (LevelChunkTicks<Block> c : containers) {
			for (SavedTick<Block> s : c.pack(gameTime[0])) tail.append(' ').append(s.pos().asLong()).append(':').append(s.delay()).append(':').append(s.priority().getValue());
		}
		if (trace != null) {
			digest.update(tail.toString().getBytes(StandardCharsets.UTF_8));
			trace.println(tail);
		}
		return HexFormat.of().formatHex(digest.digest());
	}

	public static void main(String[] args) throws Exception {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		boolean nativeTicks;
		try {
			Class.forName("net.minecraft.world.ticks.NativeChunkTicks");
			nativeTicks = true;
		} catch (ClassNotFoundException e) {
			nativeTicks = false;
		}
		// The control overlay contains the original LevelChunkTicks, which never loads NativeChunkTicks.
		System.out.println("NATIVE_CLASS_PRESENT " + nativeTicks);
		try (var in = ClassLoader.getSystemResourceAsStream("net/minecraft/world/ticks/LevelChunkTicks.class")) {
			System.out.println("CLASS LevelChunkTicks " + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes())));
		}
		int ticks = 4000;
		StringBuilder digests = new StringBuilder();
		try (var trace = new PrintWriter(Files.newBufferedWriter(Path.of(args[1])))) {
			for (long seed = 1; seed <= 10; seed++) digests.append(run(trace, ticks, seed)).append(' ');
		}
		System.out.println("TRACE " + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(digests.toString().getBytes(StandardCharsets.UTF_8))) + " " + ticks * 10);
		if (args.length > 2 && args[2].equals("benchmark")) {
			int warmups = Integer.parseInt(args[3]), samples = Integer.parseInt(args[4]);
			gameplayOnly = args.length > 5 && args[5].equals("gameplay");
			for (int i = 0; i < warmups; i++) run(null, ticks, 7);
			for (int i = 0; i < samples; i++) {
				scheduleNanos = 0;
				tickNanos = 0;
				long start = System.nanoTime();
				run(null, ticks, 7);
				long total = System.nanoTime() - start;
				System.out.println("TIMING " + i + " " + total);
				System.out.println("PHASES " + i + " schedule " + scheduleNanos + " tick " + tickNanos + " other " + (total - scheduleNanos - tickNanos));
			}
		}
	}
}
