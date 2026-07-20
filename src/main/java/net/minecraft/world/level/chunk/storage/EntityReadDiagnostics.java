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
	private static final int MAX_EVENTS = 128;
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
	private static long rustWriteCompressedBytes;
	private static long rustWriteDecompressedBytes;
	private static long rustWriteTapeBytes;
	private static long rustWriteSaveTraversalNanos;
	private static long rustWriteTapeConstructionNanos;
	private static long rustWriteCodecSubtreeNanos;
	private static long rustWriteNativeNanos;
	private static long rustWriteCompleteNanos;
	private static long rustWriteShadowValidationNanos;
	private static long rustWriteCodecSubtreeMaterializations;
	private static long rustCompleteNanos;
	private static long rustAsyncFutureNanos;
	private static long rustWorkerQueueWaitNanos;
	private static long rustWorkerExecutionNanos;
	private static long rustAsyncUnattributedNanos;
	private static long rustNativeFfiNanos;
	private static long rustFfiTotalNanos;
	private static long rustNativeReadDecodeNanos;
	private static long rustRegionPayloadReadNanos;
	private static long rustRegionHandleLookupNanos;
	private static long rustRegionLockWaitNanos;
	private static long rustRegionLockHoldNanos;
	private static long rustDecompressionNanos;
	private static long rustNbtParseNanos;
	private static long rustEnvelopeTraversalNanos;
	private static long rustTapeCreationNanos;
	private static long rustOutputCopyNanos;
	private static long javaNativeCallNanos;
	private static long javaArenaNanos;
	private static long javaOutputAllocationNanos;
	private static long javaResultAllocationNanos;
	private static long javaResultParseNanos;
	private static long javaWrapperOtherNanos;
	private static long javaAllocatedBytes;
	private static long javaClearedBytes;
	private static long rustFfiCopyNanos;
	private static long rustJavaEnvelopeDecodeNanos;
	private static long tapeIndexAndLoadNanos;
	private static long nativeCalls;
	private static long nativeRetries;
	private static long copiedBytes;
	private static long javaCompleteNanos;
	private static long javaReadNanos;
	private static long javaBaselineLoadNanos;
	private static long reSaveValidationNanos;
	private static boolean worldReady;
	private static boolean saveRequested;
	private static boolean shutdownRequested;
	private static boolean stopped;
	private static String status = "running";
	private static String error = "";
	private static final ListBackedEvents EVENTS = new ListBackedEvents();

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
		NativeEntityStorage.Result result = decodeResult.result();
		NativeEntityStorage.Metrics metrics = decodeResult.metrics();
		synchronized (LOCK) {
			rustCurrentVersionReads++;
			rustEntityCount += loadedEntities;
			rustCompleteNanos += metrics.workerExecutionNanos() + constructNanos;
			rustAsyncFutureNanos += readDecodeNanos;
			rustWorkerQueueWaitNanos += metrics.workerQueueWaitNanos();
			rustWorkerExecutionNanos += metrics.workerExecutionNanos();
			rustAsyncUnattributedNanos += Math.max(0L, readDecodeNanos - metrics.workerQueueWaitNanos() - metrics.workerExecutionNanos());
			rustNativeReadDecodeNanos += metrics.workerExecutionNanos();
			rustFfiTotalNanos += result.rustFfiTotalNanos();
			rustRegionPayloadReadNanos += result.regionReadNanos();
			rustRegionHandleLookupNanos += result.regionHandleLookupNanos();
			rustRegionLockWaitNanos += result.regionLockWaitNanos();
			rustRegionLockHoldNanos += result.regionLockHoldNanos();
			rustDecompressionNanos += result.decompressionNanos();
			rustNbtParseNanos += result.nbtParseNanos();
			rustEnvelopeTraversalNanos += result.envelopeTraversalNanos();
			rustTapeCreationNanos += result.tapeCreationNanos();
			rustOutputCopyNanos += result.rustOutputCopyNanos();
			javaNativeCallNanos += metrics.javaNativeCallNanos();
			javaArenaNanos += metrics.javaArenaNanos();
			javaOutputAllocationNanos += metrics.javaOutputAllocationNanos();
			javaResultAllocationNanos += metrics.javaResultAllocationNanos();
			javaResultParseNanos += metrics.javaResultParseNanos();
			javaWrapperOtherNanos += metrics.javaWrapperOtherNanos();
			javaAllocatedBytes += metrics.javaAllocatedBytes();
			javaClearedBytes += metrics.javaClearedBytes();
			rustNativeFfiNanos += metrics.javaFfiInvokeNanos();
			rustFfiCopyNanos += metrics.copyNanos();
			rustJavaEnvelopeDecodeNanos += metrics.javaEnvelopeDecodeNanos();
			tapeIndexAndLoadNanos += constructNanos;
			nativeCalls += metrics.nativeCalls();
			nativeRetries += metrics.retries();
			copiedBytes += metrics.copiedBytes();
			JsonObject event = event("rustEntityRead", chunkPos);
			event.addProperty("dataVersion", result.dataVersion());
			event.addProperty("compressionId", result.compressionId());
			event.addProperty("external", result.external());
			event.addProperty("rootEntities", result.entityCount());
			event.addProperty("loadedEntities", loadedEntities);
			event.addProperty("compressedBytes", result.compressedLength());
			event.addProperty("decompressedBytes", result.decompressedLength());
			event.addProperty("nativeCalls", metrics.nativeCalls());
			event.addProperty("nativeRetries", metrics.retries());
			event.addProperty("copiedBytes", metrics.copiedBytes());
			event.addProperty("futureElapsedNanos", readDecodeNanos);
			event.addProperty("workerQueueWaitNanos", metrics.workerQueueWaitNanos());
			event.addProperty("workerExecutionNanos", metrics.workerExecutionNanos());
			event.addProperty("javaNativeCallNanos", metrics.javaNativeCallNanos());
			event.addProperty("javaArenaNanos", metrics.javaArenaNanos());
			event.addProperty("javaOutputAllocationNanos", metrics.javaOutputAllocationNanos());
			event.addProperty("javaResultAllocationNanos", metrics.javaResultAllocationNanos());
			event.addProperty("javaFfiInvokeNanos", metrics.javaFfiInvokeNanos());
			event.addProperty("javaResultParseNanos", metrics.javaResultParseNanos());
			event.addProperty("javaWrapperOtherNanos", metrics.javaWrapperOtherNanos());
			event.addProperty("regionPayloadReadNanos", result.regionReadNanos());
			event.addProperty("regionHandleLookupNanos", result.regionHandleLookupNanos());
			event.addProperty("regionLockWaitNanos", result.regionLockWaitNanos());
			event.addProperty("regionLockHoldNanos", result.regionLockHoldNanos());
			event.addProperty("decompressionNanos", result.decompressionNanos());
			event.addProperty("nbtParseNanos", result.nbtParseNanos());
			event.addProperty("envelopeTraversalNanos", result.envelopeTraversalNanos());
			event.addProperty("tapeCreationNanos", result.tapeCreationNanos());
			event.addProperty("rustOutputCopyNanos", result.rustOutputCopyNanos());
			event.addProperty("rustFfiTotalNanos", result.rustFfiTotalNanos());
		}
		writeStatus();
	}

	static void absent(ChunkPos chunkPos) {
		if (!ENABLED) {
			return;
		}
		synchronized (LOCK) {
			absentChunks++;
			event("absentEntityChunk", chunkPos);
		}
		writeStatus();
	}

	static void oldVersionFallback(ChunkPos chunkPos, int dataVersion) {
		if (!ENABLED) {
			return;
		}
		synchronized (LOCK) {
			oldVersionJavaFallbacks++;
			event("oldVersionJavaFallback", chunkPos).addProperty("dataVersion", dataVersion);
		}
		writeStatus();
	}

	static void pendingWriteFallback(ChunkPos chunkPos) {
		if (!ENABLED) {
			return;
		}
		synchronized (LOCK) {
			pendingWriteJavaFallbacks++;
			event("pendingWriteJavaFallback", chunkPos);
		}
		writeStatus();
	}

	static void nativeError(ChunkPos chunkPos, Throwable throwable) {
		if (!ENABLED) {
			return;
		}
		synchronized (LOCK) {
			nativeErrors++;
			JsonObject event = event("nativeEntityReadError", chunkPos);
			event.addProperty("exception", throwable.getClass().getName());
			event.addProperty("message", throwable.getMessage());
		}
		writeStatus();
	}

	static void writeFallback(ChunkPos chunkPos, String reason) {
		if (!ENABLED) {
			return;
		}
		synchronized (LOCK) {
			rustWriteFallbacks++;
			event("rustEntityWriteFallback", chunkPos).addProperty("reason", reason);
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
			rustWriteShadowValidationNanos += validationNanos;
			JsonObject event = event("rustEntityWriteShadowMatch", chunkPos);
			event.addProperty("validationNanos", validationNanos);
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
			rustWriteShadowValidationNanos += validationNanos;
			JsonObject event = event("rustEntityWriteShadowMismatch", chunkPos);
			event.addProperty("message", message);
			event.addProperty("validationNanos", validationNanos);
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
			rustWriteCompressedBytes += result.compressedLength();
			rustWriteDecompressedBytes += result.decompressedLength();
			rustWriteTapeBytes += request.tapeBytes();
			rustWriteSaveTraversalNanos += request.saveTraversalNanos();
			rustWriteTapeConstructionNanos += request.tapeConstructionNanos();
			rustWriteCodecSubtreeNanos += request.codecSubtreeNanos();
			rustWriteCodecSubtreeMaterializations += request.codecSubtreeMaterializations();
			rustWriteShadowValidationNanos += request.shadowValidationNanos();
			rustWriteNativeNanos += nativeNanos;
			rustWriteCompleteNanos += request.saveTraversalNanos() + request.tapeConstructionNanos() + nativeNanos;
			JsonObject event = event("rustEntityWrite", chunkPos);
			event.addProperty("entities", request.entityCount());
			event.addProperty("compressionId", result.compressionId());
			event.addProperty("external", result.external());
			event.addProperty("compressedBytes", result.compressedLength());
			event.addProperty("decompressedBytes", result.decompressedLength());
			event.addProperty("tapeBytes", request.tapeBytes());
			event.addProperty("saveTraversalNanos", request.saveTraversalNanos());
			event.addProperty("tapeConstructionNanos", request.tapeConstructionNanos());
			event.addProperty("codecSubtreeNanos", request.codecSubtreeNanos());
			event.addProperty("codecSubtreeMaterializations", request.codecSubtreeMaterializations());
			event.addProperty("nativeWriteNanos", nativeNanos);
		}
		writeStatus();
	}

	static void writeFailure(ChunkPos chunkPos, Throwable throwable) {
		if (!ENABLED) {
			return;
		}
		synchronized (LOCK) {
			nativeWriteErrors++;
			JsonObject event = event("nativeEntityWriteError", chunkPos);
			event.addProperty("exception", throwable.getClass().getName());
			event.addProperty("message", throwable.getMessage());
		}
		writeStatus();
	}

	static void malformed(ChunkPos chunkPos, Throwable throwable) {
		if (!ENABLED) {
			return;
		}
		synchronized (LOCK) {
			malformedInputs++;
			JsonObject event = event("malformedEntityInput", chunkPos);
			event.addProperty("exception", throwable.getClass().getName());
			event.addProperty("message", throwable.getMessage());
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
			javaReadNanos += readNanos;
			javaBaselineLoadNanos += loadNanos;
			javaCompleteNanos += readNanos + loadNanos;
			reSaveValidationNanos += validationNanos;
			JsonObject event = event("entityParityMatch", chunkPos);
			event.addProperty("javaEntities", javaEntities);
			event.addProperty("rustEntities", rustEntities);
			event.addProperty("javaReadNanos", readNanos);
			event.addProperty("javaLoadNanos", loadNanos);
			event.addProperty("reSaveValidationNanos", validationNanos);
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
			javaReadNanos += readNanos;
			javaBaselineLoadNanos += loadNanos;
			javaCompleteNanos += readNanos + loadNanos;
			reSaveValidationNanos += validationNanos;
			JsonObject event = event("entityParityMismatch", chunkPos);
			event.addProperty("message", message);
			event.addProperty("javaReadNanos", readNanos);
			event.addProperty("javaLoadNanos", loadNanos);
			event.addProperty("reSaveValidationNanos", validationNanos);
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
			JsonObject event = event("generatedEntityBehaviorMatch", null);
			event.addProperty("case", caseName);
			event.addProperty("javaEntities", javaEntities);
			event.addProperty("rustEntities", rustEntities);
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
			JsonObject event = event("generatedEntityBehaviorMismatch", null);
			event.addProperty("case", caseName);
			event.addProperty("message", message);
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
			refreshTerminalStatusLocked();
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
			root.addProperty("rustWriteCompressedBytes", rustWriteCompressedBytes);
			root.addProperty("rustWriteDecompressedBytes", rustWriteDecompressedBytes);
			root.addProperty("rustWriteTapeBytes", rustWriteTapeBytes);
			root.addProperty("rustWriteSaveTraversalNanos", rustWriteSaveTraversalNanos);
			root.addProperty("rustWriteTapeConstructionNanos", rustWriteTapeConstructionNanos);
			root.addProperty("rustWriteCodecSubtreeNanos", rustWriteCodecSubtreeNanos);
			root.addProperty("rustWriteCodecSubtreeMaterializations", rustWriteCodecSubtreeMaterializations);
			root.addProperty("rustWriteNativeNanos", rustWriteNativeNanos);
			root.addProperty("rustWriteCompleteNanos", rustWriteCompleteNanos);
			root.addProperty("rustWriteShadowValidationNanos", rustWriteShadowValidationNanos);
			root.addProperty("rustCompleteNanos", rustCompleteNanos);
			root.addProperty("rustAsyncFutureNanos", rustAsyncFutureNanos);
			root.addProperty("rustWorkerQueueWaitNanos", rustWorkerQueueWaitNanos);
			root.addProperty("rustWorkerExecutionNanos", rustWorkerExecutionNanos);
			root.addProperty("rustAsyncUnattributedNanos", rustAsyncUnattributedNanos);
			root.addProperty("rustNativeReadDecodeNanos", rustNativeReadDecodeNanos);
			root.addProperty("rustFfiTotalNanos", rustFfiTotalNanos);
			root.addProperty("rustRegionPayloadReadNanos", rustRegionPayloadReadNanos);
			root.addProperty("rustRegionHandleLookupNanos", rustRegionHandleLookupNanos);
			root.addProperty("rustRegionLockWaitNanos", rustRegionLockWaitNanos);
			root.addProperty("rustRegionLockHoldNanos", rustRegionLockHoldNanos);
			root.addProperty("rustDecompressionNanos", rustDecompressionNanos);
			root.addProperty("rustNbtParseNanos", rustNbtParseNanos);
			root.addProperty("rustEnvelopeTraversalNanos", rustEnvelopeTraversalNanos);
			root.addProperty("rustTapeCreationNanos", rustTapeCreationNanos);
			root.addProperty("rustOutputCopyNanos", rustOutputCopyNanos);
			root.addProperty("rustNativeFfiNanos", rustNativeFfiNanos);
			root.addProperty("javaNativeCallNanos", javaNativeCallNanos);
			root.addProperty("javaArenaNanos", javaArenaNanos);
			root.addProperty("javaOutputAllocationNanos", javaOutputAllocationNanos);
			root.addProperty("javaResultAllocationNanos", javaResultAllocationNanos);
			root.addProperty("javaResultParseNanos", javaResultParseNanos);
			root.addProperty("javaWrapperOtherNanos", javaWrapperOtherNanos);
			root.addProperty("rustFfiCopyNanos", rustFfiCopyNanos);
			root.addProperty("rustJavaEnvelopeDecodeNanos", rustJavaEnvelopeDecodeNanos);
			root.addProperty("javaAllocatedBytes", javaAllocatedBytes);
			root.addProperty("javaClearedBytes", javaClearedBytes);
			root.addProperty("tapeIndexAndEntityLoadNanos", tapeIndexAndLoadNanos);
			root.addProperty("nativeCalls", nativeCalls);
			root.addProperty("nativeRetries", nativeRetries);
			root.addProperty("copiedBytes", copiedBytes);
			root.addProperty("javaCompleteNanos", javaCompleteNanos);
			root.addProperty("javaRegionNbtReadNanos", javaReadNanos);
			root.addProperty("javaBaselineLoadNanos", javaBaselineLoadNanos);
			root.addProperty("reSaveValidationNanos", reSaveValidationNanos);
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
		String property = System.getProperty("mattmc.dev.entityValidation.status");
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
