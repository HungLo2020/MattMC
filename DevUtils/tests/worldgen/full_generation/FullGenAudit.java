package fullgeneration;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;
import java.util.zip.GZIPOutputStream;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;

/**
 * Generates fixed regions to FULL and fingerprints each chunk's serialized save
 * form plus its generation entities. Uses only APIs present in Frozen and Current.
 */
public final class FullGenAudit {
	private static final AtomicBoolean STARTED = new AtomicBoolean();
	private static final Path OUTPUT = Path.of(System.getProperty("audit.output")).toAbsolutePath();
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final ThreadLocal<ArrayDeque<Long>> STARTS = ThreadLocal.withInitial(ArrayDeque::new);
	private static final Map<String, LongAdder> TASK_NANOS = new ConcurrentHashMap<>();
	private static final Map<String, LongAdder> TASK_CALLS = new ConcurrentHashMap<>();
	private static final boolean SERIAL = Boolean.getBoolean("audit.serial");
	private static final boolean HASH = Boolean.getBoolean("audit.hash");
	/** Interrupted generation: carve each chunk, save and unload, then finish to FULL from disk. */
	private static final boolean RESUME = Boolean.getBoolean("audit.resume");

	public static void enter() {
		STARTS.get().push(System.nanoTime());
	}

	public static void exit(String task) {
		ArrayDeque<Long> starts = STARTS.get();
		if (starts.isEmpty()) {
			return;
		}
		long elapsed = System.nanoTime() - starts.pop();
		TASK_NANOS.computeIfAbsent(task, ignored -> new LongAdder()).add(elapsed);
		TASK_CALLS.computeIfAbsent(task, ignored -> new LongAdder()).increment();
	}

	public static void capture(Object server) {
		if (STARTED.compareAndSet(false, true)) {
			new Thread(() -> run((MinecraftServer) server), "full-generation-audit").start();
		}
	}

	static String canonical(Tag tag) {
		StringBuilder out = new StringBuilder();
		canonical(tag, out);
		return out.toString();
	}

	static void canonical(Tag tag, StringBuilder out) {
		if (tag instanceof CompoundTag compound) {
			out.append('{');
			List<String> keys = new ArrayList<>(compound.keySet());
			keys.sort(null);
			for (String key : keys) {
				out.append(StringTag.quoteAndEscape(key)).append(':');
				canonical(compound.get(key), out);
				out.append(',');
			}
			out.append('}');
		} else if (tag instanceof ListTag list) {
			out.append('[');
			for (Tag element : list) {
				canonical(element, out);
				out.append(',');
			}
			out.append(']');
		} else {
			out.append(tag.toString());
		}
	}

	static String sha(String value) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))).substring(0, 24);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/** Splits the save form into separately compared fields; sections compare per Y and per field. */
	static Map<String, String> fingerprint(CompoundTag data, List<String> entities) {
		Map<String, String> parts = new TreeMap<>();
		for (String key : data.keySet()) {
			if (key.equals("LastUpdate") || key.equals("sections")) {
				continue;
			}
			parts.put(key, sha(canonical(data.get(key))));
		}
		if (data.get("sections") instanceof ListTag sections) {
			for (Tag element : sections) {
				CompoundTag section = (CompoundTag) element;
				String y = section.get("Y").toString();
				for (String key : section.keySet()) {
					if (!key.equals("Y")) {
						parts.put("sections/" + y + "/" + key, sha(canonical(section.get(key))));
					}
				}
			}
		}
		parts.put("generation_entities", sha(String.join("\n", entities)));
		return parts;
	}

	static List<String> entities(ServerLevel level, ChunkPos pos) {
		List<String> rows = new ArrayList<>();
		for (Entity entity : level.getAllEntities()) {
			if (entity.chunkPosition().equals(pos)) {
				rows.add(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()) + " " + entity.getX() + " " + entity.getY() + " " + entity.getZ()
					+ " " + entity.getYRot() + " " + entity.getXRot());
			}
		}
		rows.sort(null);
		return rows;
	}

	static List<ChunkPos> positions(int centerX, int centerZ, int radius) {
		List<ChunkPos> positions = new ArrayList<>();
		for (int z = -radius; z <= radius; z++) {
			for (int x = -radius; x <= radius; x++) {
				positions.add(new ChunkPos(centerX + x, centerZ + z));
			}
		}
		return positions;
	}

	static long gcMillis() {
		return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(GarbageCollectorMXBean::getCollectionTime).sum();
	}

	static Map<String, Object> region(MinecraftServer server, ServerLevel level, String label, List<ChunkPos> positions, BufferedWriter dump) throws Exception {
		Map<String, Object> resume = RESUME ? carveSaveAndUnload(server, level, positions) : null;
		TASK_NANOS.clear();
		TASK_CALLS.clear();
		long cpu = ((com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean()).getProcessCpuTime();
		long gc = gcMillis();
		long start = System.nanoTime();
		if (!SERIAL) {
			server.submit(() -> positions.forEach(p -> level.getChunkSource().addTicketWithRadius(TicketType.PLAYER_LOADING, p, 0))).get();
		}
		List<CompletableFuture<ChunkResult<ChunkAccess>>> futures = new ArrayList<>();
		for (ChunkPos pos : positions) {
			if (SERIAL) {
				server.submit(() -> level.getChunkSource().addTicketWithRadius(TicketType.PLAYER_LOADING, pos, 0)).get();
			}
			futures.add(level.getChunkSource().getChunkFuture(pos.x, pos.z, ChunkStatus.FULL, true));
			if (SERIAL) {
				futures.getLast().get(15, TimeUnit.MINUTES);
			}
		}
		CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).get(30, TimeUnit.MINUTES);
		long elapsed = System.nanoTime() - start;
		long cpuEnd = ((com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean()).getProcessCpuTime();
		Map<String, Object> row = new LinkedHashMap<>();
		row.put("dimension", level.dimension().location().toString());
		row.put("label", label);
		row.put("chunks", positions.size());
		if (resume != null) row.put("resume", resume);
		row.put("wall_seconds", elapsed / 1e9);
		row.put("process_cpu_seconds", (cpuEnd - cpu) / 1e9);
		row.put("gc_ms", gcMillis() - gc);
		Map<String, Object> tasks = new TreeMap<>();
		TASK_NANOS.forEach((task, nanos) -> tasks.put(task, Map.of("ms", nanos.sum() / 1e6, "calls", TASK_CALLS.get(task).sum())));
		row.put("synchronous_task_ms", tasks);
		for (var future : futures) {
			ChunkAccess chunk = future.join().orElse(null);
			if (chunk == null || !chunk.getPersistedStatus().isOrAfter(ChunkStatus.FULL)) {
				throw new IllegalStateException("Chunk generation failed: " + future.join().getError());
			}
		}
		if (HASH) {
			Map<String, Map<String, String>> fingerprints = new TreeMap<>();
			server.submit(() -> {
				for (var future : futures) {
					ChunkAccess chunk = future.join().orElse(null);
					CompoundTag data = SerializableChunkData.copyOf(level, chunk).write();
					List<String> entities = entities(level, chunk.getPos());
					fingerprints.put(chunk.getPos().x + "," + chunk.getPos().z, fingerprint(data, entities));
					try {
						dump.write(level.dimension().location() + " " + label + " " + chunk.getPos().x + "," + chunk.getPos().z + "\n");
						data.remove("LastUpdate");
						dump.write(canonical(data));
						dump.write("\n");
						dump.write(String.join("\n", entities));
						dump.write("\n");
					} catch (Exception e) {
						throw new RuntimeException(e);
					}
				}
			}).get();
			row.put("fingerprints", fingerprints);
		}
		try {
			var stats = Class.forName("net.minecraft.world.level.levelgen.NativeDecoration").getMethod("statistics").invoke(null);
			row.put("native_decoration", stats);
		} catch (ReflectiveOperationException ignored) {
		}
		System.out.println("FULLGEN_REGION " + level.dimension().location() + " " + label + " " + positions.size() + " " + elapsed / 1e9);
		return row;
	}

	/** Requests positions to CARVERS, one at a time, under a ticket exactly at that level. */
	static void carve(MinecraftServer server, ServerLevel level, List<ChunkPos> positions) throws Exception {
		for (ChunkPos pos : positions) {
			server.submit(() -> level.getChunkSource().addTicketWithRadius(TicketType.PLAYER_LOADING, pos, CARVERS_RADIUS)).get();
			ChunkResult<ChunkAccess> result = level.getChunkSource().getChunkFuture(pos.x, pos.z, ChunkStatus.CARVERS, false).get(15, TimeUnit.MINUTES);
			ChunkAccess chunk = result.orElse(null);
			if (chunk == null || !chunk.getPersistedStatus().isOrAfter(ChunkStatus.CARVERS)) throw new IllegalStateException("Carving failed: " + result.getError());
		}
	}

	static final int CARVERS_RADIUS = 33 - net.minecraft.server.level.ChunkLevel.byStatus(ChunkStatus.CARVERS);

	/**
	 * Carves the region and a two-chunk margin, saves and unloads it, then loads
	 * it all back at CARVERS before the FULL requests: decoration then reads
	 * chunks restored from disk without waiting on IO, so its order matches a
	 * straight run.
	 */
	static Map<String, Object> carveSaveAndUnload(MinecraftServer server, ServerLevel level, List<ChunkPos> positions) throws Exception {
		int before = server.submit(() -> level.getChunkSource().chunkMap.size()).get();
		int radius = CARVERS_RADIUS;
		List<ChunkPos> margin = expand(positions, 2);
		for (ChunkPos pos : margin) {
			server.submit(() -> level.getChunkSource().addTicketWithRadius(TicketType.PLAYER_LOADING, pos, radius)).get();
			ChunkResult<ChunkAccess> result = level.getChunkSource().getChunkFuture(pos.x, pos.z, ChunkStatus.CARVERS, false).get(15, TimeUnit.MINUTES);
			ChunkAccess chunk = result.orElse(null);
			if (chunk == null || !chunk.getPersistedStatus().isOrAfter(ChunkStatus.CARVERS)) throw new IllegalStateException("Carving failed: " + result.getError());
		}
		int carved = server.submit(() -> level.getChunkSource().chunkMap.size()).get();
		server.submit(() -> margin.forEach(pos -> level.getChunkSource().removeTicketWithRadius(TicketType.PLAYER_LOADING, pos, radius))).get();
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(90);
		int size;
		do {
			server.submit(() -> level.save(null, true, false)).get();
			Thread.sleep(500);
			size = server.submit(() -> level.getChunkSource().chunkMap.size()).get();
		} while (size > before && System.nanoTime() < deadline);
		if (size > before) throw new IllegalStateException("Carved chunks did not unload: " + size + " > " + before);
		carve(server, level, margin);
		RESUMED.put(level, margin);
		Map<String, Object> row = new LinkedHashMap<>();
		row.put("restored_chunks", margin.size());
		row.put("holders_before", before);
		row.put("holders_carved", carved);
		row.put("holders_after_unload", size);
		return row;
	}

	static final Map<ServerLevel, List<ChunkPos>> RESUMED = new ConcurrentHashMap<>();

	static List<ChunkPos> expand(List<ChunkPos> positions, int margin) {
		int minX = positions.stream().mapToInt(p -> p.x).min().orElseThrow(), maxX = positions.stream().mapToInt(p -> p.x).max().orElseThrow();
		int minZ = positions.stream().mapToInt(p -> p.z).min().orElseThrow(), maxZ = positions.stream().mapToInt(p -> p.z).max().orElseThrow();
		List<ChunkPos> result = new ArrayList<>();
		for (int z = minZ - margin; z <= maxZ + margin; z++) {
			for (int x = minX - margin; x <= maxX + margin; x++) {
				result.add(new ChunkPos(x, z));
			}
		}
		return result;
	}

	static void release(MinecraftServer server, ServerLevel level, List<ChunkPos> positions) throws Exception {
		server.submit(() -> positions.forEach(p -> level.getChunkSource().removeTicketWithRadius(TicketType.PLAYER_LOADING, p, 0))).get();
		List<ChunkPos> restored = RESUMED.remove(level);
		if (restored != null) server.submit(() -> restored.forEach(p -> level.getChunkSource().removeTicketWithRadius(TicketType.PLAYER_LOADING, p, CARVERS_RADIUS))).get();
		server.submit(() -> level.save(null, true, false)).get();
		Thread.sleep(1500);
	}

	static void run(MinecraftServer server) {
		try {
			Files.createDirectories(OUTPUT);
			int radius = Integer.getInteger("audit.radius", 4);
			int rounds = Integer.getInteger("audit.rounds", 1);
			int warmups = Integer.getInteger("audit.warmups", 0);
			int warmRadius = Integer.getInteger("audit.warmRadius", 3);
			String selected = System.getProperty("audit.dimensions", "");
			List<ServerLevel> levels = new ArrayList<>();
			server.getAllLevels().forEach(levels::add);
			levels.sort(Comparator.comparing(level -> level.dimension().location().toString()));
			System.out.println("FULLGEN_LEVELS " + levels.stream().map(level -> level.dimension().location().toString()).toList());
			if (!selected.isBlank()) {
				List<String> names = List.of(selected.split(","));
				levels.removeIf(level -> !names.contains(level.dimension().location().toString()));
			}
			if (levels.isEmpty()) {
				throw new IllegalArgumentException("No matching dimensions");
			}
			List<Map<String, Object>> rows = new ArrayList<>();
			try (BufferedWriter dump = new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(Files.newOutputStream(OUTPUT.resolve("chunks.txt.gz"))), StandardCharsets.UTF_8))) {
				for (ServerLevel level : levels) {
					for (int w = 0; w < warmups; w++) {
						List<ChunkPos> positions = positions(2048 + w * 128, 2048, warmRadius);
						Map<String, Object> row = region(server, level, "warmup-" + w, positions, dump);
						rows.add(row);
						release(server, level, positions);
					}
					for (int i = 0; i < rounds; i++) {
						// Round 0 includes the origin region, where spawn-adjacent structures cluster.
						List<ChunkPos> positions = positions(i == 0 ? 0 : 64 * i, i == 0 ? 0 : -48 * i, radius);
						rows.add(region(server, level, "measure-" + i, positions, dump));
						Files.writeString(OUTPUT.resolve("regions.json"), GSON.toJson(rows));
						release(server, level, positions);
					}
				}
			}
			Files.writeString(OUTPUT.resolve("regions.json"), GSON.toJson(rows));
			Files.writeString(OUTPUT.resolve("complete.json"), GSON.toJson(Map.of("success", true, "regions", rows.size())));
			System.out.println("FULLGEN_COMPLETE");
		} catch (Throwable t) {
			t.printStackTrace();
			try {
				Files.writeString(OUTPUT.resolve("failed.txt"), t.toString());
			} catch (Exception ignored) {
			}
		}
		server.halt(false);
	}
}
