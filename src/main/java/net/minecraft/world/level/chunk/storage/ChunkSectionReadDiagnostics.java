package net.minecraft.world.level.chunk.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.world.level.ChunkPos;

/**
 * Development-only counters for the Rust typed chunk-section read path.
 *
 * <p>Enabled by {@code -Dmattmc.dev.rustChunkSections=true}. Production chunk
 * reads remain Java-owned unless that explicit property is present. Old chunks,
 * pending in-memory writes, malformed typed data, and parity mismatches fall
 * back to the existing Java {@link SerializableChunkData} section path.
 */
public final class ChunkSectionReadDiagnostics {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final boolean RUST_ENABLED = Boolean.getBoolean("mattmc.dev.rustChunkSections");
	private static final boolean SHADOW_VALIDATION_ENABLED = Boolean.getBoolean("mattmc.dev.chunkSectionValidation");
	private static final Path STATUS_PATH = statusPath();
	private static final boolean ENABLED = RUST_ENABLED || STATUS_PATH != null;
	private static final Object LOCK = new Object();
	private static final int MAX_EVENTS = 128;
	private static long rustCurrentVersionSectionReads;
	private static long absentChunks;
	private static long javaFallbacks;
	private static long oldVersionJavaFallbacks;
	private static long nativeErrors;
	private static long malformedChunks;
	private static long parityChecks;
	private static long parityMatches;
	private static long parityMismatches;
	private static long compressedBytes;
	private static long decompressedBytes;
	private static long sectionCount;
	private static long heightmapCount;
	private static long rustDecodeTapeNanos;
	private static long javaPaletteResolveNanos;
	private static long javaSectionCompareNanos;
	private static long javaBaselineParseNanos;
	private static boolean worldReady;
	private static boolean saveRequested;
	private static boolean shutdownRequested;
	private static boolean stopped;
	private static String status = "running";
	private static String error = "";
	private static final JsonArray EVENTS = new JsonArray();

	static {
		if (ENABLED && STATUS_PATH != null) {
			Runtime.getRuntime().addShutdownHook(new Thread(ChunkSectionReadDiagnostics::writeStatus, "MattMC chunk section diagnostics"));
		}
	}

	private ChunkSectionReadDiagnostics() {
	}

	public static boolean rustEnabled() {
		return RUST_ENABLED;
	}

	public static boolean shadowValidationEnabled() {
		return SHADOW_VALIDATION_ENABLED || STATUS_PATH != null;
	}

	public static long now() {
		return System.nanoTime();
	}

	public static long elapsed(long started) {
		return System.nanoTime() - started;
	}

	public static void rustDecoded(
		ChunkPos chunkPos,
		NativeChunkSectionStorage.Result result,
		long javaParseNanos,
		long resolveNanos,
		long compareNanos
	) {
		if (!ENABLED) {
			return;
		}
		synchronized (LOCK) {
			rustCurrentVersionSectionReads++;
			if (compareNanos > 0L || shadowValidationEnabled()) {
				parityChecks++;
				parityMatches++;
			}
			compressedBytes += result.compressedLength();
			decompressedBytes += result.decompressedLength();
			sectionCount += result.sectionCount();
			heightmapCount += result.heightmapCount();
			rustDecodeTapeNanos += result.rustFfiTotalNanos();
			javaBaselineParseNanos += javaParseNanos;
			javaPaletteResolveNanos += resolveNanos;
			javaSectionCompareNanos += compareNanos;
			JsonObject event = event("rustChunkSections", chunkPos);
			event.addProperty("dataVersion", result.dataVersion());
			event.addProperty("compressionId", result.compressionId());
			event.addProperty("external", result.external());
			event.addProperty("sections", result.sectionCount());
			event.addProperty("heightmaps", result.heightmapCount());
			event.addProperty("compressedBytes", result.compressedLength());
			event.addProperty("decompressedBytes", result.decompressedLength());
			event.addProperty("rustFfiTotalNanos", result.rustFfiTotalNanos());
			event.addProperty("javaBaselineParseNanos", javaParseNanos);
			event.addProperty("javaPaletteResolveNanos", resolveNanos);
			event.addProperty("javaSectionCompareNanos", compareNanos);
		}
		writeStatus();
	}

	public static void absent(ChunkPos chunkPos) {
		if (!ENABLED) {
			return;
		}
		synchronized (LOCK) {
			absentChunks++;
			event("absentChunk", chunkPos);
		}
		writeStatus();
	}

	public static void javaFallback(ChunkPos chunkPos, String reason) {
		if (!ENABLED) {
			return;
		}
		synchronized (LOCK) {
			javaFallbacks++;
			event("javaFallback", chunkPos).addProperty("reason", reason);
		}
		writeStatus();
	}

	public static void oldVersionFallback(ChunkPos chunkPos, int dataVersion) {
		if (!ENABLED) {
			return;
		}
		synchronized (LOCK) {
			oldVersionJavaFallbacks++;
			event("oldVersionJavaFallback", chunkPos).addProperty("dataVersion", dataVersion);
		}
		writeStatus();
	}

	public static void nativeError(ChunkPos chunkPos, Throwable throwable) {
		if (!ENABLED) {
			return;
		}
		synchronized (LOCK) {
			nativeErrors++;
			JsonObject event = event("nativeError", chunkPos);
			event.addProperty("exception", throwable.getClass().getName());
			event.addProperty("message", throwable.getMessage());
		}
		writeStatus();
	}

	public static void malformed(ChunkPos chunkPos, Throwable throwable) {
		if (!ENABLED) {
			return;
		}
		synchronized (LOCK) {
			malformedChunks++;
			JsonObject event = event("malformedTypedSections", chunkPos);
			event.addProperty("exception", throwable.getClass().getName());
			event.addProperty("message", throwable.getMessage());
		}
		writeStatus();
	}

	public static void parityMismatch(
		ChunkPos chunkPos,
		String mismatch,
		NativeChunkSectionStorage.Result result,
		long javaParseNanos,
		long resolveNanos,
		long compareNanos
	) {
		if (!ENABLED) {
			return;
		}
		synchronized (LOCK) {
			parityChecks++;
			parityMismatches++;
			JsonObject event = event("parityMismatch", chunkPos);
			event.addProperty("mismatch", mismatch);
			event.addProperty("dataVersion", result.dataVersion());
			event.addProperty("sections", result.sectionCount());
			event.addProperty("javaBaselineParseNanos", javaParseNanos);
			event.addProperty("javaPaletteResolveNanos", resolveNanos);
			event.addProperty("javaSectionCompareNanos", compareNanos);
		}
		writeStatus();
	}

	public static void recordWorldReady() {
		if (!ENABLED) {
			return;
		}
		synchronized (LOCK) {
			worldReady = true;
			event("worldReady", null);
		}
		writeStatus();
	}

	public static void recordSaveRequested() {
		if (!ENABLED) {
			return;
		}
		synchronized (LOCK) {
			saveRequested = true;
			event("saveRequested", null);
		}
		writeStatus();
	}

	public static void recordShutdownRequested() {
		if (!ENABLED) {
			return;
		}
		synchronized (LOCK) {
			shutdownRequested = true;
			event("shutdownRequested", null);
		}
		writeStatus();
	}

	public static void recordStopped() {
		if (!ENABLED) {
			return;
		}
		synchronized (LOCK) {
			stopped = true;
			if (error.isEmpty()) {
				status = "complete";
			}
			event("stopped", null);
		}
		writeStatus();
	}

	public static void recordFailure(String message) {
		if (!ENABLED) {
			return;
		}
		synchronized (LOCK) {
			status = "failed";
			error = message == null ? "" : message;
			event("failure", null).addProperty("message", error);
		}
		writeStatus();
	}

	public static void writeStatus() {
		if (STATUS_PATH == null) {
			return;
		}
		JsonObject root;
		synchronized (LOCK) {
			root = new JsonObject();
			root.addProperty("status", status);
			root.addProperty("error", error);
			root.addProperty("rustEnabled", RUST_ENABLED);
			root.addProperty("worldReady", worldReady);
			root.addProperty("saveRequested", saveRequested);
			root.addProperty("shutdownRequested", shutdownRequested);
			root.addProperty("stopped", stopped);
			root.addProperty("rustCurrentVersionSectionReads", rustCurrentVersionSectionReads);
			root.addProperty("absentChunks", absentChunks);
			root.addProperty("javaFallbacks", javaFallbacks);
			root.addProperty("oldVersionJavaFallbacks", oldVersionJavaFallbacks);
			root.addProperty("nativeErrors", nativeErrors);
			root.addProperty("malformedChunks", malformedChunks);
			root.addProperty("parityChecks", parityChecks);
			root.addProperty("parityMatches", parityMatches);
			root.addProperty("parityMismatches", parityMismatches);
			root.addProperty("compressedBytes", compressedBytes);
			root.addProperty("decompressedBytes", decompressedBytes);
			root.addProperty("sectionCount", sectionCount);
			root.addProperty("heightmapCount", heightmapCount);
			root.addProperty("rustDecodeTapeNanos", rustDecodeTapeNanos);
			root.addProperty("javaBaselineParseNanos", javaBaselineParseNanos);
			root.addProperty("javaPaletteResolveNanos", javaPaletteResolveNanos);
			root.addProperty("javaSectionCompareNanos", javaSectionCompareNanos);
			root.add("events", EVENTS.deepCopy());
		}
		try {
			Files.createDirectories(STATUS_PATH.getParent());
			Files.writeString(STATUS_PATH, GSON.toJson(root));
		} catch (IOException ignored) {
		}
	}

	private static JsonObject event(String type, ChunkPos chunkPos) {
		JsonObject event = new JsonObject();
		event.addProperty("type", type);
		if (chunkPos != null) {
			event.addProperty("chunkX", chunkPos.x);
			event.addProperty("chunkZ", chunkPos.z);
		}
		EVENTS.add(event);
		while (EVENTS.size() > MAX_EVENTS) {
			EVENTS.remove(0);
		}
		return event;
	}

	private static Path statusPath() {
		String path = System.getProperty("mattmc.dev.chunkSectionValidation.status", "");
		return path.isBlank() ? null : Path.of(path);
	}
}
