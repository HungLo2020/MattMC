package net.minecraft.world.level.chunk.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.world.level.ChunkPos;

/**
 * Development-only counters for the Rust entity-read path.
 *
 * <p>Current-version entity chunks are read and written through Rust by default. This class
 * only records diagnostics when {@code -Dmattmc.dev.entityValidation=true} or a
 * status output path is explicitly set. Old entity chunks remain Java/DFU-owned
 * and pending Java writes are not read from Rust until the I/O worker has
 * flushed them to the region file.
 */
public final class EntityReadDiagnostics {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final boolean RUST_READS_ENABLED = true;
	private static final boolean RUST_WRITES_ENABLED = true;
	private static final boolean WRITE_SHADOW_ENABLED = Boolean.getBoolean("mattmc.dev.rustEntityWritesShadow") || Boolean.getBoolean("mattmc.dev.entityValidation");
	private static final boolean VALIDATION_ENABLED = Boolean.getBoolean("mattmc.dev.entityValidation");
	private static final Path STATUS_PATH = statusPath();
	private static final boolean ENABLED = VALIDATION_ENABLED || STATUS_PATH != null;
	private static final Object LOCK = new Object();
	private static long rustCurrentVersionReads;
	private static long absentChunks;
	private static long oldVersionJavaFallbacks;
	private static long pendingWriteJavaFallbacks;
	private static long nativeErrors;
	private static long nativeWriteErrors;
	private static long malformedInputs;
	private static long parityChecks;
	private static long parityMatches;
	private static long parityMismatches;
	private static long generatedBehaviorChecks;
	private static long generatedBehaviorMatches;
	private static long generatedBehaviorMismatches;
	private static long rustEntityCount;
	private static long javaEntityCount;
	private static long rustCurrentVersionWrites;
	private static long rustWriteEntityCount;
	private static long rustWriteFallbacks;
	private static long rustWriteShadowChecks;
	private static long rustWriteShadowMatches;
	private static long rustWriteShadowMismatches;
	private static boolean worldReady;
	private static boolean saveRequested;
	private static boolean shutdownRequested;
	private static boolean stopped;
	private static String status = "running";
	private static String error = "";

	static {
		if (ENABLED && STATUS_PATH != null) {
			Runtime.getRuntime().addShutdownHook(new Thread(EntityReadDiagnostics::writeStatus, "MattMC entity diagnostics"));
		}
	}

	private EntityReadDiagnostics() {
	}

	static boolean validationEnabled() {
		return VALIDATION_ENABLED;
	}

	public static boolean validationAwaitingShutdown() {
		if (!VALIDATION_ENABLED) {
			return false;
		}
		synchronized (LOCK) {
			return !stopped && status.equals("running");
		}
	}

	static boolean writeShadowEnabled() {
		return WRITE_SHADOW_ENABLED;
	}

	static long now() {
		return System.nanoTime();
	}

	static long elapsed(long started) {
		return System.nanoTime() - started;
	}

	static void rustDecoded(ChunkPos chunkPos, NativeEntityStorage.DecodeResult decodeResult, int loadedEntities, long readDecodeNanos, long constructNanos) {
		if (!ENABLED) {
			return;
		}
		synchronized (LOCK) {
			rustCurrentVersionReads++;
			rustEntityCount += loadedEntities;
		}
		writeStatus();
	}

	static void absent(ChunkPos chunkPos) {
		if (!ENABLED) {
			return;
		}
		synchronized (LOCK) {
			absentChunks++;
		}
		writeStatus();
	}

	static void oldVersionFallback(ChunkPos chunkPos, int dataVersion) {
		if (!ENABLED) {
			return;
		}
		synchronized (LOCK) {
			oldVersionJavaFallbacks++;
		}
		writeStatus();
	}

	static void pendingWriteFallback(ChunkPos chunkPos) {
		if (!ENABLED) {
			return;
		}
		synchronized (LOCK) {
			pendingWriteJavaFallbacks++;
		}
		writeStatus();
	}

	static void nativeError(ChunkPos chunkPos, Throwable throwable) {
		if (!ENABLED) {
			return;
		}
		synchronized (LOCK) {
			nativeErrors++;
			rememberFirstError("Native entity read failed for " + chunkPos + ": " + throwable.getMessage());
		}
		writeStatus();
	}

	static void writeFallback(ChunkPos chunkPos, String reason) {
		if (!ENABLED) {
			return;
		}
		synchronized (LOCK) {
			rustWriteFallbacks++;
		}
		writeStatus();
	}

	static void writeShadowMatch(ChunkPos chunkPos, long validationNanos) {
		if (!ENABLED) {
			return;
		}
		synchronized (LOCK) {
			rustWriteShadowChecks++;
			rustWriteShadowMatches++;
		}
		writeStatus();
	}

	static void writeShadowMismatch(ChunkPos chunkPos, String message, long validationNanos) {
		if (!ENABLED) {
			return;
		}
		synchronized (LOCK) {
			rustWriteShadowChecks++;
			rustWriteShadowMismatches++;
			rememberFirstError("Entity write shadow mismatch for " + chunkPos + ": " + message);
		}
		writeStatus();
	}

	static void rustWritten(ChunkPos chunkPos, NativeEntityStorage.WriteResult result, NativeEntityStorage.WriteRequest request, long nativeNanos) {
		if (!ENABLED) {
			return;
		}
		synchronized (LOCK) {
			rustCurrentVersionWrites++;
			rustWriteEntityCount += request.entityCount();
		}
		writeStatus();
	}

	static void writeFailure(ChunkPos chunkPos, Throwable throwable) {
		if (!ENABLED) {
			return;
		}
		synchronized (LOCK) {
			nativeWriteErrors++;
			rememberFirstError("Native entity write failed for " + chunkPos + ": " + throwable.getMessage());
		}
		writeStatus();
	}

	static void malformed(ChunkPos chunkPos, Throwable throwable) {
		if (!ENABLED) {
			return;
		}
		synchronized (LOCK) {
			malformedInputs++;
			rememberFirstError("Malformed entity input for " + chunkPos + ": " + throwable.getMessage());
		}
		writeStatus();
	}

	static void parityMatch(ChunkPos chunkPos, int javaEntities, int rustEntities, long readNanos, long loadNanos, long validationNanos) {
		if (!ENABLED) {
			return;
		}
		synchronized (LOCK) {
			parityChecks++;
			parityMatches++;
			javaEntityCount += javaEntities;
		}
		writeStatus();
	}

	static void parityMismatch(ChunkPos chunkPos, String message, long readNanos, long loadNanos, long validationNanos) {
		if (!ENABLED) {
			return;
		}
		synchronized (LOCK) {
			parityChecks++;
			parityMismatches++;
			rememberFirstError("Entity parity mismatch for " + chunkPos + ": " + message);
		}
		writeStatus();
	}

	public static void generatedBehaviorMatch(String caseName, int javaEntities, int rustEntities) {
		if (!ENABLED) {
			return;
		}
		synchronized (LOCK) {
			generatedBehaviorChecks++;
			generatedBehaviorMatches++;
		}
		writeStatus();
	}

	public static void generatedBehaviorMismatch(String caseName, String message) {
		if (!ENABLED) {
			return;
		}
		synchronized (LOCK) {
			generatedBehaviorChecks++;
			generatedBehaviorMismatches++;
			rememberFirstError("Generated entity behavior mismatch for " + caseName + ": " + message);
		}
		writeStatus();
	}

	public static void recordWorldReady() {
		if (!ENABLED) {
			return;
		}
		synchronized (LOCK) {
			worldReady = true;
		}
		writeStatus();
	}

	public static void recordSaveRequested() {
		if (!ENABLED) {
			return;
		}
		synchronized (LOCK) {
			saveRequested = true;
		}
		writeStatus();
	}

	public static void recordShutdownRequested() {
		if (!ENABLED) {
			return;
		}
		synchronized (LOCK) {
			shutdownRequested = true;
		}
		writeStatus();
	}

	public static void recordStopped() {
		if (!ENABLED) {
			return;
		}
		synchronized (LOCK) {
			stopped = true;
			refreshTerminalStatusLocked();
		}
		writeStatus();
	}

	public static void recordFailure(String message) {
		if (!ENABLED) {
			return;
		}
		synchronized (LOCK) {
			status = "failed";
			error = message;
		}
		writeStatus();
	}

	public static void writeStatus() {
		if (!ENABLED || STATUS_PATH == null) {
			return;
		}
		JsonObject root = new JsonObject();
		synchronized (LOCK) {
			refreshTerminalStatusLocked();
			root.addProperty("status", status);
			root.addProperty("error", error);
			root.addProperty("rustEntityReadsEnabled", RUST_READS_ENABLED);
			root.addProperty("rustEntityWritesEnabled", RUST_WRITES_ENABLED);
			root.addProperty("rustEntityWriteShadowEnabled", WRITE_SHADOW_ENABLED);
			root.addProperty("entityValidation", VALIDATION_ENABLED);
			root.addProperty("worldReady", worldReady);
			root.addProperty("saveRequested", saveRequested);
			root.addProperty("shutdownRequested", shutdownRequested);
			root.addProperty("stopped", stopped);
			root.addProperty("currentVersionRustReads", rustCurrentVersionReads);
			root.addProperty("absentChunks", absentChunks);
			root.addProperty("oldVersionJavaFallbacks", oldVersionJavaFallbacks);
			root.addProperty("pendingWriteJavaFallbacks", pendingWriteJavaFallbacks);
			root.addProperty("nativeErrors", nativeErrors);
			root.addProperty("nativeWriteErrors", nativeWriteErrors);
			root.addProperty("malformedInputs", malformedInputs);
			root.addProperty("parityChecks", parityChecks);
			root.addProperty("parityMatches", parityMatches);
			root.addProperty("parityMismatches", parityMismatches);
			root.addProperty("generatedBehaviorChecks", generatedBehaviorChecks);
			root.addProperty("generatedBehaviorMatches", generatedBehaviorMatches);
			root.addProperty("generatedBehaviorMismatches", generatedBehaviorMismatches);
			root.addProperty("rustEntityCount", rustEntityCount);
			root.addProperty("javaEntityCount", javaEntityCount);
			root.addProperty("currentVersionRustWrites", rustCurrentVersionWrites);
			root.addProperty("rustWriteEntityCount", rustWriteEntityCount);
			root.addProperty("rustWriteFallbacks", rustWriteFallbacks);
			root.addProperty("rustWriteShadowChecks", rustWriteShadowChecks);
			root.addProperty("rustWriteShadowMatches", rustWriteShadowMatches);
			root.addProperty("rustWriteShadowMismatches", rustWriteShadowMismatches);
		}
		try {
			Path normalizedPath = STATUS_PATH.toAbsolutePath().normalize();
			Path parent = normalizedPath.getParent();
			if (parent != null) {
				Files.createDirectories(parent);
			}
			Files.writeString(normalizedPath, GSON.toJson(root));
		} catch (IOException ignored) {
		}
	}

	private static void refreshTerminalStatusLocked() {
		if (!stopped) {
			return;
		}
		if (
			nativeErrors == 0L
				&& malformedInputs == 0L
				&& nativeWriteErrors == 0L
				&& parityMismatches == 0L
				&& generatedBehaviorMismatches == 0L
				&& rustWriteShadowMismatches == 0L
				&& rustCurrentVersionReads > 0L
				&& rustCurrentVersionWrites > 0L
		) {
			status = "complete";
			error = "";
		} else {
			status = "failed";
			if (error.isEmpty()) {
				error = rustCurrentVersionReads <= 0L
					? "No current-version entity chunks were read through Rust"
					: rustCurrentVersionWrites <= 0L
						? "No current-version entity chunks were written through Rust"
						: "Entity validation recorded errors";
			}
		}
	}

	private static Path statusPath() {
		String property = System.getProperty("mattmc.dev.entityValidation.status");
		return property == null || property.isBlank() ? null : Path.of(property);
	}

	private static void rememberFirstError(String message) {
		if (error.isEmpty()) {
			error = message;
		}
	}
}
