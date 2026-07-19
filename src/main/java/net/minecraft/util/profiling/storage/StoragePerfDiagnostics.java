package net.minecraft.util.profiling.storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.jetbrains.annotations.Nullable;

public final class StoragePerfDiagnostics {
	private static final boolean ENABLED = Boolean.getBoolean("mattmc.dev.storagePerf");
	private static final int MAX_EVENTS = Math.max(0, Integer.getInteger("mattmc.dev.storagePerf.maxEvents", 2048));
	private static final int WRITE_INTERVAL = Math.max(1, Integer.getInteger("mattmc.dev.storagePerf.writeInterval", 256));
	private static final Path STATUS_PATH = statusPath();
	private static final String IMPLEMENTATION = System.getProperty("mattmc.dev.storagePerf.implementation", "unknown");
	private static final String RUN_ID = System.getProperty("mattmc.dev.storagePerf.runId", "unknown");
	private static final String WORLD = System.getProperty("mattmc.dev.storagePerf.world", "unknown");

	private static final Object LOCK = new Object();
	private static final long STARTED_AT_MS = System.currentTimeMillis();
	private static long worldReadyAtMs;
	private static long saveRequestedAtMs;
	private static long shutdownRequestedAtMs;
	private static long stoppedAtMs;
	private static boolean worldReady;
	private static boolean saveRequested;
	private static boolean shutdownRequested;
	private static boolean stopped;
	private static int runErrors;
	private static int regionsOpened;
	private static int regionReads;
	private static int regionReadMisses;
	private static int regionWrites;
	private static int regionDeletes;
	private static int regionFlushes;
	private static int regionCloses;
	private static int regionErrors;
	private static int nbtErrors;
	private static int otherErrors;
	private static int internalPayloads;
	private static int externalPayloads;
	private static int nbtReads;
	private static int nbtWrites;
	private static int rustNbtReads;
	private static int rustNbtWrites;
	private static int javaNbtReads;
	private static int javaNbtWrites;
	private static long regionReadNanos;
	private static long regionWriteNanos;
	private static long regionDeleteNanos;
	private static long regionFlushNanos;
	private static long nbtReadNanos;
	private static long nbtWriteNanos;
	private static long compressedBytesRead;
	private static long compressedBytesWritten;
	private static long nbtBytesRead;
	private static long nbtBytesWritten;
	private static long eventsSinceWrite;
	private static final Map<Integer, Integer> COMPRESSION_IDS = new LinkedHashMap<>();
	private static final TreeSet<String> FILES = new TreeSet<>();
	private static final List<String> EVENTS = new ArrayList<>();
	private static final List<String> ERRORS = new ArrayList<>();

	static {
		if (enabled()) {
			Runtime.getRuntime().addShutdownHook(new Thread(StoragePerfDiagnostics::writeStatus, "MattMC storage perf diagnostics"));
		}
	}

	private StoragePerfDiagnostics() {
	}

	private static Path statusPath() {
		String value = System.getProperty("mattmc.dev.storagePerf.status", "").trim();
		return value.isEmpty() ? null : Path.of(value);
	}

	public static boolean enabled() {
		return ENABLED || STATUS_PATH != null;
	}

	public static long start() {
		return enabled() ? System.nanoTime() : 0L;
	}

	public static long elapsed(long started) {
		return started == 0L ? 0L : System.nanoTime() - started;
	}

	public static void recordWorldReady() {
		if (!enabled()) {
			return;
		}
		synchronized (LOCK) {
			if (!worldReady) {
				worldReady = true;
				worldReadyAtMs = System.currentTimeMillis();
				event("world-ready");
			}
		}
	}

	public static void recordSaveRequested() {
		if (!enabled()) {
			return;
		}
		synchronized (LOCK) {
			if (!saveRequested) {
				saveRequested = true;
				saveRequestedAtMs = System.currentTimeMillis();
				event("save-requested");
			}
		}
	}

	public static void recordShutdownRequested() {
		if (!enabled()) {
			return;
		}
		synchronized (LOCK) {
			if (!shutdownRequested) {
				shutdownRequested = true;
				shutdownRequestedAtMs = System.currentTimeMillis();
				event("shutdown-requested");
			}
		}
	}

	public static void recordStopped() {
		if (!enabled()) {
			return;
		}
		synchronized (LOCK) {
			if (!stopped) {
				stopped = true;
				stoppedAtMs = System.currentTimeMillis();
				event("stopped");
			}
		}
	}

	public static void recordRunError(String message) {
		if (!enabled()) {
			return;
		}
		synchronized (LOCK) {
			runErrors++;
			error("run", message);
		}
	}

	public static void recordRegionOpen(Path path) {
		if (!enabled()) {
			return;
		}
		synchronized (LOCK) {
			regionsOpened++;
			file(path);
			event("region-open path=" + normalize(path));
		}
	}

	public static void recordRegionRead(
		Path path,
		int chunkX,
		int chunkZ,
		boolean present,
		int compressionId,
		boolean external,
		long payloadBytes,
		long nanos
	) {
		if (!enabled()) {
			return;
		}
		synchronized (LOCK) {
			regionReads++;
			if (!present) {
				regionReadMisses++;
			}
			regionReadNanos += nanos;
			compressedBytesRead += Math.max(0L, payloadBytes);
			if (present) {
				compression(compressionId);
				if (external) {
					externalPayloads++;
				} else {
					internalPayloads++;
				}
			}
			file(path);
			event("region-read path=" + normalize(path) + " chunk=" + chunkX + "," + chunkZ + " present=" + present + " bytes=" + payloadBytes);
		}
	}

	public static void recordRegionWrite(Path path, int chunkX, int chunkZ, int compressionId, boolean external, long payloadBytes, long nanos) {
		if (!enabled()) {
			return;
		}
		synchronized (LOCK) {
			regionWrites++;
			regionWriteNanos += nanos;
			compressedBytesWritten += Math.max(0L, payloadBytes);
			compression(compressionId);
			if (external) {
				externalPayloads++;
			} else {
				internalPayloads++;
			}
			file(path);
			event("region-write path=" + normalize(path) + " chunk=" + chunkX + "," + chunkZ + " bytes=" + payloadBytes);
		}
	}

	public static void recordRegionDelete(Path path, int chunkX, int chunkZ, long nanos) {
		if (!enabled()) {
			return;
		}
		synchronized (LOCK) {
			regionDeletes++;
			regionDeleteNanos += nanos;
			file(path);
			event("region-delete path=" + normalize(path) + " chunk=" + chunkX + "," + chunkZ);
		}
	}

	public static void recordRegionFlush(Path path, long nanos) {
		if (!enabled()) {
			return;
		}
		synchronized (LOCK) {
			regionFlushes++;
			regionFlushNanos += nanos;
			file(path);
			event("region-flush path=" + normalize(path));
		}
	}

	public static void recordRegionClose(Path path) {
		if (!enabled()) {
			return;
		}
		synchronized (LOCK) {
			regionCloses++;
			file(path);
			event("region-close path=" + normalize(path));
		}
	}

	public static void recordNbtRead(String owner, int compression, long inputBytes, long outputBytes, long nanos) {
		if (!enabled()) {
			return;
		}
		synchronized (LOCK) {
			nbtReads++;
			if ("rust".equals(owner)) {
				rustNbtReads++;
			} else {
				javaNbtReads++;
			}
			nbtReadNanos += nanos;
			nbtBytesRead += Math.max(0L, inputBytes);
			compression(compression);
			event("nbt-read owner=" + owner + " compression=" + compression + " in=" + inputBytes + " out=" + outputBytes);
		}
	}

	public static void recordNbtWrite(String owner, int compression, long inputBytes, long outputBytes, long nanos) {
		if (!enabled()) {
			return;
		}
		synchronized (LOCK) {
			nbtWrites++;
			if ("rust".equals(owner)) {
				rustNbtWrites++;
			} else {
				javaNbtWrites++;
			}
			nbtWriteNanos += nanos;
			nbtBytesWritten += Math.max(0L, outputBytes);
			compression(compression);
			event("nbt-write owner=" + owner + " compression=" + compression + " in=" + inputBytes + " out=" + outputBytes);
		}
	}

	public static void recordError(String category, Throwable throwable) {
		if (!enabled()) {
			return;
		}
		synchronized (LOCK) {
			error(category, throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
		}
	}

	public static void writeStatus() {
		if (!enabled() || STATUS_PATH == null) {
			return;
		}
		String json;
		synchronized (LOCK) {
			json = toJson();
		}
		try {
			Path parent = STATUS_PATH.getParent();
			if (parent != null) {
				Files.createDirectories(parent);
			}
			Files.writeString(STATUS_PATH, json, StandardCharsets.UTF_8);
		} catch (IOException ignored) {
		}
	}

	private static void compression(int id) {
		if (id >= 0) {
			COMPRESSION_IDS.merge(id, 1, Integer::sum);
		}
	}

	private static void file(@Nullable Path path) {
		if (path != null) {
			FILES.add(normalize(path));
		}
	}

	private static void event(String event) {
		eventsSinceWrite++;
		if (EVENTS.size() < MAX_EVENTS) {
			EVENTS.add(System.currentTimeMillis() + " " + event);
		}
		if (eventsSinceWrite >= WRITE_INTERVAL) {
			eventsSinceWrite = 0;
			writeStatus();
		}
	}

	private static void error(String category, String message) {
		if (category.startsWith("region")) {
			regionErrors++;
		} else if (category.startsWith("nbt")) {
			nbtErrors++;
		} else {
			otherErrors++;
		}
		ERRORS.add(category + ": " + message);
		event("error category=" + category + " message=" + message);
	}

	private static String normalize(Path path) {
		return path.toString().replace('\\', '/');
	}

	private static String toJson() {
		return "{\n"
			+ "  \"schemaVersion\": 1,\n"
			+ "  \"runId\": " + quote(RUN_ID) + ",\n"
			+ "  \"implementation\": " + quote(IMPLEMENTATION) + ",\n"
			+ "  \"world\": " + quote(WORLD) + ",\n"
			+ "  \"startedAtMs\": " + STARTED_AT_MS + ",\n"
			+ "  \"worldReady\": " + worldReady + ",\n"
			+ "  \"worldReadyAtMs\": " + worldReadyAtMs + ",\n"
			+ "  \"saveRequested\": " + saveRequested + ",\n"
			+ "  \"saveRequestedAtMs\": " + saveRequestedAtMs + ",\n"
			+ "  \"shutdownRequested\": " + shutdownRequested + ",\n"
			+ "  \"shutdownRequestedAtMs\": " + shutdownRequestedAtMs + ",\n"
			+ "  \"stopped\": " + stopped + ",\n"
			+ "  \"stoppedAtMs\": " + stoppedAtMs + ",\n"
			+ "  \"runErrors\": " + runErrors + ",\n"
			+ "  \"regionsOpened\": " + regionsOpened + ",\n"
			+ "  \"regionReads\": " + regionReads + ",\n"
			+ "  \"regionReadMisses\": " + regionReadMisses + ",\n"
			+ "  \"regionWrites\": " + regionWrites + ",\n"
			+ "  \"regionDeletes\": " + regionDeletes + ",\n"
			+ "  \"regionFlushes\": " + regionFlushes + ",\n"
			+ "  \"regionCloses\": " + regionCloses + ",\n"
			+ "  \"regionErrors\": " + regionErrors + ",\n"
			+ "  \"nbtErrors\": " + nbtErrors + ",\n"
			+ "  \"otherErrors\": " + otherErrors + ",\n"
			+ "  \"internalPayloads\": " + internalPayloads + ",\n"
			+ "  \"externalPayloads\": " + externalPayloads + ",\n"
			+ "  \"nbtReads\": " + nbtReads + ",\n"
			+ "  \"nbtWrites\": " + nbtWrites + ",\n"
			+ "  \"rustNbtReads\": " + rustNbtReads + ",\n"
			+ "  \"rustNbtWrites\": " + rustNbtWrites + ",\n"
			+ "  \"javaNbtReads\": " + javaNbtReads + ",\n"
			+ "  \"javaNbtWrites\": " + javaNbtWrites + ",\n"
			+ "  \"compressedBytesRead\": " + compressedBytesRead + ",\n"
			+ "  \"compressedBytesWritten\": " + compressedBytesWritten + ",\n"
			+ "  \"nbtBytesRead\": " + nbtBytesRead + ",\n"
			+ "  \"nbtBytesWritten\": " + nbtBytesWritten + ",\n"
			+ "  \"timingsNanos\": {\n"
			+ "    \"regionRead\": " + regionReadNanos + ",\n"
			+ "    \"regionWrite\": " + regionWriteNanos + ",\n"
			+ "    \"regionDelete\": " + regionDeleteNanos + ",\n"
			+ "    \"regionFlush\": " + regionFlushNanos + ",\n"
			+ "    \"nbtRead\": " + nbtReadNanos + ",\n"
			+ "    \"nbtWrite\": " + nbtWriteNanos + "\n"
			+ "  },\n"
			+ "  \"compressionIds\": " + mapJson(COMPRESSION_IDS) + ",\n"
			+ "  \"files\": " + arrayJson(FILES) + ",\n"
			+ "  \"errors\": " + arrayJson(ERRORS) + ",\n"
			+ "  \"events\": " + arrayJson(EVENTS) + "\n"
			+ "}\n";
	}

	private static String mapJson(Map<Integer, Integer> map) {
		StringBuilder builder = new StringBuilder("{");
		boolean first = true;
		for (Map.Entry<Integer, Integer> entry : map.entrySet()) {
			if (!first) {
				builder.append(", ");
			}
			builder.append(quote(Integer.toString(entry.getKey()))).append(": ").append(entry.getValue());
			first = false;
		}
		return builder.append('}').toString();
	}

	private static String arrayJson(Iterable<String> values) {
		StringBuilder builder = new StringBuilder("[");
		boolean first = true;
		for (String value : values) {
			if (!first) {
				builder.append(", ");
			}
			builder.append(quote(value));
			first = false;
		}
		return builder.append(']').toString();
	}

	private static String quote(String value) {
		return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + '"';
	}
}
