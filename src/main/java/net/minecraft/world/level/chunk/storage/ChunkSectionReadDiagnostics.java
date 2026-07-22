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
 * <p>Current-version chunk sections are Rust-owned in normal reads. These
 * counters are enabled only by {@code -Dmattmc.dev.chunkSectionValidation=true}
 * or an explicit status output path. Old chunks, pending in-memory writes, and
 * malformed typed data fall back before a partial chunk is published.
 */
public final class ChunkSectionReadDiagnostics {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final boolean SHADOW_VALIDATION_ENABLED = Boolean.getBoolean("mattmc.dev.chunkSectionValidation");
	private static final boolean WRITE_VALIDATION_ENABLED = Boolean.getBoolean("mattmc.dev.chunkSectionWriteValidation");
	private static final boolean WRITE_SHADOW_VALIDATION_ENABLED = Boolean.getBoolean("mattmc.dev.rustChunkSectionWriteShadow");
	private static final Path STATUS_PATH = statusPath();
	private static final boolean ENABLED = SHADOW_VALIDATION_ENABLED || WRITE_VALIDATION_ENABLED || WRITE_SHADOW_VALIDATION_ENABLED || STATUS_PATH != null;
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
	private static long rustCurrentVersionTickReads;
	private static long blockTickCount;
	private static long fluidTickCount;
	private static long rustTickResolveNanos;
	private static long rustDecodeTapeNanos;
	private static long javaPaletteResolveNanos;
	private static long javaSectionCompareNanos;
	private static long javaBaselineParseNanos;
	private static long rustCurrentVersionSectionWrites;
	private static long rustCurrentVersionTickWrites;
	private static long writeFallbacks;
	private static long nativeWriteErrors;
	private static long writeShadowChecks;
	private static long writeShadowMatches;
	private static long writeShadowMismatches;
	private static long writeCompressedBytes;
	private static long writeDecompressedBytes;
	private static long writeTapeBytes;
	private static long writeTapeCreationBytes;
	private static long writeTapeCreationNanos;
	private static long writeMergeNanos;
	private static long writeNbtEncodeNanos;
	private static long writeCompressionNanos;
	private static long writeRegionWriteNanos;
	private static long writeRustFfiTotalNanos;
	private static long writeTickTapeBytes;
	private static long writeTickTapeCreationNanos;
	private static long writeShadowJavaNanos;
	private static long writeShadowCompareNanos;
	private static long forcedValidationChunks;
	private static long generatedCustomRootFieldsInjected;
	private static long generatedCustomRootFieldsObserved;
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

	public static boolean shadowValidationEnabled() {
		return SHADOW_VALIDATION_ENABLED || STATUS_PATH != null;
	}

	public static boolean writeValidationEnabled() {
		return WRITE_VALIDATION_ENABLED;
	}

	public static boolean validationAwaitingShutdown() {
		if (!SHADOW_VALIDATION_ENABLED && !WRITE_VALIDATION_ENABLED) {
			return false;
		}
		synchronized (LOCK) {
			return !stopped && status.equals("running");
		}
	}

	public static boolean writeShadowValidationEnabled() {
		return WRITE_SHADOW_VALIDATION_ENABLED || STATUS_PATH != null && WRITE_VALIDATION_ENABLED;
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

	public static void rustTicksDecoded(
		ChunkPos chunkPos,
		NativeChunkSectionStorage.Result result,
		net.minecraft.world.level.chunk.ChunkAccess.PackedTicks ticks,
		long resolveNanos
	) {
		if (!ENABLED) {
			return;
		}
		synchronized (LOCK) {
			rustCurrentVersionTickReads++;
			blockTickCount += ticks.blocks().size();
			fluidTickCount += ticks.fluids().size();
			rustTickResolveNanos += resolveNanos;
			JsonObject event = event("rustChunkTicks", chunkPos);
			event.addProperty("dataVersion", result.dataVersion());
			event.addProperty("blockTicks", ticks.blocks().size());
			event.addProperty("fluidTicks", ticks.fluids().size());
			event.addProperty("nativeBlockTicks", result.blockTickCount());
			event.addProperty("nativeFluidTicks", result.fluidTickCount());
			event.addProperty("resolveNanos", resolveNanos);
		}
		writeStatus();
	}

	public static void rustTickWriteTapeCreated(long nanos, long tapeBytes) {
		if (!ENABLED) {
			return;
		}
		synchronized (LOCK) {
			writeTickTapeCreationNanos += nanos;
			writeTickTapeBytes += tapeBytes;
		}
	}

	public static void rustWriteTapeCreated(long nanos, long tapeBytes) {
		if (!ENABLED) {
			return;
		}
		synchronized (LOCK) {
			writeTapeCreationNanos += nanos;
			writeTapeCreationBytes += tapeBytes;
		}
	}

	public static void rustWritten(ChunkPos chunkPos, NativeChunkSectionStorage.WriteResult result, long totalNanos) {
		if (!ENABLED) {
			return;
		}
		synchronized (LOCK) {
			rustCurrentVersionSectionWrites++;
			rustCurrentVersionTickWrites++;
			writeCompressedBytes += result.compressedLength();
			writeDecompressedBytes += result.decompressedLength();
			writeTapeBytes += result.tapeLength();
			writeMergeNanos += result.mergeNanos();
			writeNbtEncodeNanos += result.nbtEncodeNanos();
			writeCompressionNanos += result.compressionNanos();
			writeRegionWriteNanos += result.regionWriteNanos();
			writeRustFfiTotalNanos += result.rustFfiTotalNanos();
			JsonObject event = event("rustChunkSectionWrite", chunkPos);
			event.addProperty("compressionId", result.compressionId());
			event.addProperty("external", result.external());
			event.addProperty("compressedBytes", result.compressedLength());
			event.addProperty("decompressedBytes", result.decompressedLength());
			event.addProperty("tapeBytes", result.tapeLength());
			event.addProperty("totalNanos", totalNanos);
			event.addProperty("mergeNanos", result.mergeNanos());
			event.addProperty("nbtEncodeNanos", result.nbtEncodeNanos());
			event.addProperty("compressionNanos", result.compressionNanos());
			event.addProperty("regionWriteNanos", result.regionWriteNanos());
			event.addProperty("rustFfiTotalNanos", result.rustFfiTotalNanos());
		}
		writeStatus();
	}

	public static void writeFallback(ChunkPos chunkPos, Throwable throwable) {
		if (!ENABLED) {
			return;
		}
		synchronized (LOCK) {
			writeFallbacks++;
			nativeWriteErrors++;
			JsonObject event = event("rustChunkSectionWriteFallback", chunkPos);
			event.addProperty("exception", throwable.getClass().getName());
			event.addProperty("message", throwable.getMessage());
		}
		writeStatus();
	}

	public static void writeShadowMatch(ChunkPos chunkPos, String fingerprint, long javaNanos, long compareNanos) {
		if (!ENABLED) {
			return;
		}
		synchronized (LOCK) {
			writeShadowChecks++;
			writeShadowMatches++;
			writeShadowJavaNanos += javaNanos;
			writeShadowCompareNanos += compareNanos;
			JsonObject event = event("rustChunkSectionWriteShadowMatch", chunkPos);
			event.addProperty("fingerprint", fingerprint);
			event.addProperty("javaNanos", javaNanos);
			event.addProperty("compareNanos", compareNanos);
		}
		writeStatus();
	}

	public static void writeShadowMismatch(
		ChunkPos chunkPos,
		String javaFingerprint,
		String rustFingerprint,
		String mismatch,
		long javaNanos,
		long compareNanos
	) {
		if (!ENABLED) {
			return;
		}
		synchronized (LOCK) {
			writeShadowChecks++;
			writeShadowMismatches++;
			writeShadowJavaNanos += javaNanos;
			writeShadowCompareNanos += compareNanos;
			JsonObject event = event("rustChunkSectionWriteShadowMismatch", chunkPos);
			event.addProperty("javaFingerprint", javaFingerprint);
			event.addProperty("rustFingerprint", rustFingerprint);
			event.addProperty("mismatch", mismatch);
			event.addProperty("javaNanos", javaNanos);
			event.addProperty("compareNanos", compareNanos);
		}
		writeStatus();
	}

	public static void forcedValidationChunk(boolean customInjected, boolean customObserved) {
		if (!ENABLED) {
			return;
		}
		synchronized (LOCK) {
			forcedValidationChunks++;
			if (customInjected) {
				generatedCustomRootFieldsInjected++;
			}
			if (customObserved) {
				generatedCustomRootFieldsObserved++;
			}
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
			root.addProperty("rustEnabled", true);
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
			root.addProperty("rustChunkTicksOwned", NativeChunkSectionStorage.rustChunkTicksOwned());
			root.addProperty("rustCurrentVersionTickReads", rustCurrentVersionTickReads);
			root.addProperty("blockTickCount", blockTickCount);
			root.addProperty("fluidTickCount", fluidTickCount);
			root.addProperty("rustTickResolveNanos", rustTickResolveNanos);
			root.addProperty("rustDecodeTapeNanos", rustDecodeTapeNanos);
			root.addProperty("javaBaselineParseNanos", javaBaselineParseNanos);
			root.addProperty("javaPaletteResolveNanos", javaPaletteResolveNanos);
			root.addProperty("javaSectionCompareNanos", javaSectionCompareNanos);
			root.addProperty("rustSectionWritesEnabled", true);
			root.addProperty("chunkSectionWriteValidationEnabled", WRITE_VALIDATION_ENABLED);
			root.addProperty("writeShadowValidationEnabled", writeShadowValidationEnabled());
			root.addProperty("rustCurrentVersionSectionWrites", rustCurrentVersionSectionWrites);
			root.addProperty("rustCurrentVersionTickWrites", rustCurrentVersionTickWrites);
			root.addProperty("writeFallbacks", writeFallbacks);
			root.addProperty("nativeWriteErrors", nativeWriteErrors);
			root.addProperty("writeShadowChecks", writeShadowChecks);
			root.addProperty("writeShadowMatches", writeShadowMatches);
			root.addProperty("writeShadowMismatches", writeShadowMismatches);
			root.addProperty("writeCompressedBytes", writeCompressedBytes);
			root.addProperty("writeDecompressedBytes", writeDecompressedBytes);
			root.addProperty("writeTapeBytes", writeTapeBytes);
			root.addProperty("writeTapeCreationBytes", writeTapeCreationBytes);
			root.addProperty("writeTapeCreationNanos", writeTapeCreationNanos);
			root.addProperty("writeMergeNanos", writeMergeNanos);
			root.addProperty("writeNbtEncodeNanos", writeNbtEncodeNanos);
			root.addProperty("writeCompressionNanos", writeCompressionNanos);
			root.addProperty("writeRegionWriteNanos", writeRegionWriteNanos);
			root.addProperty("writeRustFfiTotalNanos", writeRustFfiTotalNanos);
			root.addProperty("writeTickTapeBytes", writeTickTapeBytes);
			root.addProperty("writeTickTapeCreationNanos", writeTickTapeCreationNanos);
			root.addProperty("writeShadowJavaNanos", writeShadowJavaNanos);
			root.addProperty("writeShadowCompareNanos", writeShadowCompareNanos);
			root.addProperty("forcedValidationChunks", forcedValidationChunks);
			root.addProperty("generatedCustomRootFieldsInjected", generatedCustomRootFieldsInjected);
			root.addProperty("generatedCustomRootFieldsObserved", generatedCustomRootFieldsObserved);
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
