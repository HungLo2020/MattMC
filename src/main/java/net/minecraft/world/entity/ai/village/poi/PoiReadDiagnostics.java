package net.minecraft.world.entity.ai.village.poi;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;

/**
 * Development-only diagnostics for the Rust-owned current-version POI path.
 *
 * <p>Production always uses Rust for current-version POI chunks. The only
 * compatibility escape hatch is old-schema data that Rust rejects before
 * object construction so Java can run DFU and the existing codec path for that
 * specific chunk.
 */
public final class PoiReadDiagnostics {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final boolean VALIDATION_ENABLED = Boolean.getBoolean("mattmc.dev.poiValidation");
	private static final Path STATUS_PATH = statusPath();
	private static final boolean ENABLED = VALIDATION_ENABLED || STATUS_PATH != null;
	private static final Object LOCK = new Object();
	private static final int MAX_EVENTS = 128;
	private static long rustDecodedChunks;
	private static long javaCompatibilityReads;
	private static long oldVersionChunks;
	private static long unknownTypes;
	private static long malformedInputs;
	private static long rustWrittenChunks;
	private static long javaCompatibilityWrites;
	private static long writeFailures;
	private static long rustDecodeNanos;
	private static long javaObjectNanos;
	private static long rustWriteNanos;
	private static boolean worldReady;
	private static boolean saveRequested;
	private static boolean shutdownRequested;
	private static boolean stopped;
	private static String status = "running";
	private static String error = "";
	private static final ListBackedEvents EVENTS = new ListBackedEvents();

	static {
		if (ENABLED && STATUS_PATH != null) {
			Runtime.getRuntime().addShutdownHook(new Thread(PoiReadDiagnostics::writeStatus, "MattMC POI diagnostics"));
		}
	}

	private PoiReadDiagnostics() {
	}

	public static boolean validationEnabled() {
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

	static long now() {
		return System.nanoTime();
	}

	static void rustDecoded(ChunkPos chunkPos, NativePoiStorage.Result result, long decodeNanos, long constructNanos) {
		if (!ENABLED) {
			return;
		}
		synchronized (LOCK) {
			rustDecodedChunks++;
			rustDecodeNanos += decodeNanos;
			javaObjectNanos += constructNanos;
			JsonObject event = event("rustDecoded", chunkPos);
			event.addProperty("compressionId", result.compressionId());
			event.addProperty("external", result.external());
			event.addProperty("sections", result.sectionCount());
			event.addProperty("records", result.recordCount());
			event.addProperty("compressedBytes", result.compressedLength());
			event.addProperty("decompressedBytes", result.decompressedLength());
		}
		writeStatus();
	}

	static void javaCompatibilityRead(ChunkPos chunkPos, String reason) {
		if (!ENABLED) {
			return;
		}
		synchronized (LOCK) {
			javaCompatibilityReads++;
			event("javaCompatibilityRead", chunkPos).addProperty("reason", reason);
		}
		writeStatus();
	}

	static void oldVersion(ChunkPos chunkPos) {
		if (!ENABLED) {
			return;
		}
		synchronized (LOCK) {
			oldVersionChunks++;
			event("oldVersion", chunkPos);
		}
		writeStatus();
	}

	static void unknownType(ChunkPos chunkPos, ResourceLocation type) {
		if (!ENABLED) {
			return;
		}
		synchronized (LOCK) {
			unknownTypes++;
			event("unknownPoiType", chunkPos).addProperty("type", type.toString());
		}
		writeStatus();
	}

	static void malformed(ChunkPos chunkPos, Throwable throwable) {
		if (!ENABLED) {
			return;
		}
		synchronized (LOCK) {
			malformedInputs++;
			JsonObject event = event("malformedPoiData", chunkPos);
			event.addProperty("exception", throwable.getClass().getName());
			event.addProperty("message", throwable.getMessage());
		}
		writeStatus();
	}

	static void rustWritten(ChunkPos chunkPos, NativePoiStorage.WriteResult result, long writeNanos) {
		if (!ENABLED) {
			return;
		}
		synchronized (LOCK) {
			rustWrittenChunks++;
			rustWriteNanos += writeNanos;
			JsonObject event = event("rustWritten", chunkPos);
			event.addProperty("compressionId", result.compressionId());
			event.addProperty("external", result.external());
			event.addProperty("sections", result.sectionCount());
			event.addProperty("records", result.recordCount());
			event.addProperty("compressedBytes", result.compressedLength());
			event.addProperty("decompressedBytes", result.decompressedLength());
			event.addProperty("fingerprint", Long.toUnsignedString(result.fingerprint()));
		}
		writeStatus();
	}

	static void javaCompatibilityWrite(ChunkPos chunkPos, String reason) {
		if (!ENABLED) {
			return;
		}
		synchronized (LOCK) {
			javaCompatibilityWrites++;
			event("javaCompatibilityWrite", chunkPos).addProperty("reason", reason);
		}
		writeStatus();
	}

	static void writeFailure(ChunkPos chunkPos, Throwable throwable) {
		if (!ENABLED) {
			return;
		}
		synchronized (LOCK) {
			writeFailures++;
			JsonObject event = event("rustWriteFailure", chunkPos);
			event.addProperty("exception", throwable.getClass().getName());
			event.addProperty("message", throwable.getMessage());
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
			status = "complete";
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
			error = message;
			event("failure", null).addProperty("message", message);
		}
		writeStatus();
	}

	public static void writeStatus() {
		if (!ENABLED || STATUS_PATH == null) {
			return;
		}
		JsonObject root = new JsonObject();
		synchronized (LOCK) {
			root.addProperty("status", status);
			root.addProperty("error", error);
			root.addProperty("poiValidation", VALIDATION_ENABLED);
			root.addProperty("worldReady", worldReady);
			root.addProperty("saveRequested", saveRequested);
			root.addProperty("shutdownRequested", shutdownRequested);
			root.addProperty("stopped", stopped);
			root.addProperty("currentVersionRustAuthoritative", true);
			root.addProperty("oldSchemaFallback", "java-dfu-codec-per-chunk");
			root.addProperty("rustDecodedChunks", rustDecodedChunks);
			root.addProperty("javaCompatibilityReads", javaCompatibilityReads);
			root.addProperty("oldVersionChunks", oldVersionChunks);
			root.addProperty("unknownTypes", unknownTypes);
			root.addProperty("malformedInputs", malformedInputs);
			root.addProperty("rustWrittenChunks", rustWrittenChunks);
			root.addProperty("javaCompatibilityWrites", javaCompatibilityWrites);
			root.addProperty("writeFailures", writeFailures);
			root.addProperty("rustDecodeNanos", rustDecodeNanos);
			root.addProperty("javaObjectConstructionNanos", javaObjectNanos);
			root.addProperty("rustWriteNanos", rustWriteNanos);
			JsonArray events = new JsonArray();
			for (JsonObject event : EVENTS.events) {
				events.add(event.deepCopy());
			}
			root.add("events", events);
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

	private static JsonObject event(String type, ChunkPos chunkPos) {
		JsonObject event = new JsonObject();
		event.addProperty("type", type);
		if (chunkPos != null) {
			event.addProperty("chunkX", chunkPos.x);
			event.addProperty("chunkZ", chunkPos.z);
		}
		EVENTS.add(event);
		return event;
	}

	private static Path statusPath() {
		String property = System.getProperty("mattmc.dev.poiValidation.status");
		return property == null || property.isBlank() ? null : Path.of(property);
	}

	private static final class ListBackedEvents {
		private final java.util.List<JsonObject> events = new java.util.ArrayList<>();

		private void add(JsonObject event) {
			if (this.events.size() < MAX_EVENTS) {
				this.events.add(event);
			}
		}
	}
}
