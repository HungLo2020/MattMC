package net.minecraft.world.level.chunk.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.nbt.CollectionTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NativeNbtRegionAccess;
import net.minecraft.nbt.Tag;
import net.minecraft.util.profiling.storage.StoragePerfDiagnostics;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.Nullable;

/**
 * Dev-only observation hooks for {@link RegionFile}.
 *
 * <p>The region facade owns file operations. This class owns diagnostics,
 * fingerprints, hashes, storage-perf counters, and validation-only shadow
 * comparisons so the production path stays a thin Rust handle facade.
 */
final class RegionFileDiagnostics {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path STATUS_PATH = statusPath();
	private static final int STATUS_WRITE_INTERVAL = Math.max(1, Integer.getInteger("mattmc.dev.regionFilesValidationWriteInterval", 128));
	private static final Set<String> REGIONS = new LinkedHashSet<>();
	private static final Map<Integer, Long> COMPRESSION_IDS = new LinkedHashMap<>();
	private static int eventsSinceWrite;
	private static long chunksRead;
	private static long chunksWritten;
	private static long chunksDeleted;
	private static long flushCalls;
	private static long rustErrors;
	private static long unreadableChunks;
	private static long malformedNbt;
	private static long internalPayloads;
	private static long externalPayloads;

	static {
		if (validationEnabled()) {
			Runtime.getRuntime().addShutdownHook(new Thread(RegionFileDiagnostics::writeStatus, "MattMC region diagnostics"));
		}
	}

	private RegionFileDiagnostics() {
	}

	static void opened(java.nio.file.Path path) {
		regionOpened(path);
		StoragePerfDiagnostics.recordRegionOpen(path);
	}

	static long start() {
		return StoragePerfDiagnostics.start();
	}

	static long elapsed(long started) {
		return StoragePerfDiagnostics.elapsed(started);
	}

	static void recordRead(
		java.nio.file.Path path,
		ChunkPos chunkPos,
		boolean present,
		int compressionId,
		boolean external,
		long compressedBytes,
		long elapsedNanos
	) {
		StoragePerfDiagnostics.recordRegionRead(path, chunkPos.x, chunkPos.z, present, compressionId, external, compressedBytes, elapsedNanos);
	}

	static void recordWrite(
		java.nio.file.Path path,
		ChunkPos chunkPos,
		int compressionId,
		boolean external,
		long compressedBytes,
		long elapsedNanos
	) {
		StoragePerfDiagnostics.recordRegionWrite(path, chunkPos.x, chunkPos.z, compressionId, external, compressedBytes, elapsedNanos);
	}

	static void recordDelete(java.nio.file.Path path, ChunkPos chunkPos, long elapsedNanos, long timestamp) {
		StoragePerfDiagnostics.recordRegionDelete(path, chunkPos.x, chunkPos.z, elapsedNanos);
		validationDelete(path, chunkPos, timestamp);
	}

	static void recordFlush(java.nio.file.Path path, long elapsedNanos) {
		StoragePerfDiagnostics.recordRegionFlush(path, elapsedNanos);
		validationFlush(path);
	}

	static void recordClose(java.nio.file.Path path) {
		StoragePerfDiagnostics.recordRegionClose(path);
	}

	static void recordError(java.nio.file.Path path, @Nullable ChunkPos chunkPos, String perfOperation, String validationOperation, IOException exception) {
		StoragePerfDiagnostics.recordError(perfOperation, exception);
		validationRustError(path, chunkPos, validationOperation, exception.getMessage());
	}

	static void recordNbtRead(
		java.nio.file.Path path,
		long handle,
		ChunkPos chunkPos,
		NativeRegionFileBridge.TapeMetadata result
	) throws IOException {
		if (!validationEnabled()) {
			return;
		}
		validationRead(
			path,
			chunkPos,
			result.compressionId(),
			result.external(),
			result.timestamp(),
			Math.toIntExact(result.compressedLength()),
			result.fingerprint()
		);
	}

	static void recordPayloadRead(
		java.nio.file.Path path,
		long handle,
		ChunkPos chunkPos,
		NativeRegionFileBridge.Result result,
		byte[] payload
	) throws IOException {
		if (!validationEnabled()) {
			return;
		}
		validationRead(
			path,
			chunkPos,
			result.compressionId(),
			result.external(),
			result.timestamp(),
			payload.length,
			readNativeNbtFingerprint(path, handle, chunkPos, "read")
		);
	}

	static void recordNbtWrite(
		java.nio.file.Path path,
		long handle,
		ChunkPos chunkPos,
		NativeRegionFileBridge.WriteResult result,
		String operation
	) throws IOException {
		if (!validationEnabled()) {
			return;
		}
		validationWrite(
			path,
			chunkPos,
			result.compressionId(),
			result.external(),
			result.timestamp(),
			result.payloadLength(),
			readNativeNbtFingerprint(path, handle, chunkPos, operation)
		);
	}

	static void recordChunkSectionWrite(
		java.nio.file.Path path,
		long handle,
		ChunkPos chunkPos,
		NativeChunkSectionStorage.WriteResult result
	) throws IOException {
		if (!validationEnabled()) {
			return;
		}
		validationWrite(
			path,
			chunkPos,
			result.compressionId(),
			result.external(),
			result.timestamp(),
			result.compressedLength(),
			readNativeNbtFingerprint(path, handle, chunkPos, "write-chunk-sections")
		);
	}

	static void recordEntityWrite(
		java.nio.file.Path path,
		ChunkPos chunkPos,
		NativeEntityStorage.WriteResult result
	) {
		if (!validationEnabled()) {
			return;
		}
		validationWrite(
			path,
			chunkPos,
			result.compressionId(),
			result.external(),
			result.timestamp(),
			result.compressedLength(),
			result.fingerprint()
		);
	}

	static void recordPayloadWrite(
		java.nio.file.Path path,
		long handle,
		ChunkPos chunkPos,
		NativeRegionFileBridge.WriteResult result,
		byte[] payload
	) throws IOException {
		if (!validationEnabled()) {
			return;
		}
		validationWrite(
			path,
			chunkPos,
			result.compressionId(),
			result.external(),
			result.timestamp(),
			result.payloadLength(),
			readNativeNbtFingerprint(path, handle, chunkPos, "write")
		);
	}

	static void recordUnreadable(java.nio.file.Path path, ChunkPos chunkPos, String message) {
		validationUnreadableChunk(path, chunkPos, message);
	}

	static boolean chunkSectionWriteShadowEnabled() {
		return ChunkSectionReadDiagnostics.writeShadowValidationEnabled();
	}

	static void validateChunkSectionWriteShadow(RegionFile regionFile, ChunkPos chunkPos, SerializableChunkData data) throws IOException {
		long started = ChunkSectionReadDiagnostics.now();
		CompoundTag javaRoot = data.writeWithRustSectionResidual();
		CompoundTag rustRoot = regionFile.readChunk(chunkPos);
		long compareStarted = ChunkSectionReadDiagnostics.now();
		String javaFingerprint = NativeNbtRegionAccess.objectFingerprint(javaRoot);
		String rustFingerprint = rustRoot == null ? "missing" : NativeNbtRegionAccess.objectFingerprint(rustRoot);
		if (rustRoot == null || !javaRoot.equals(rustRoot)) {
			ChunkSectionReadDiagnostics.writeShadowMismatch(
				chunkPos,
				javaFingerprint,
				rustFingerprint,
				rustRoot == null ? "missing rust chunk" : firstDifference("$", javaRoot, rustRoot),
				ChunkSectionReadDiagnostics.elapsed(started),
				ChunkSectionReadDiagnostics.elapsed(compareStarted)
			);
		} else {
			ChunkSectionReadDiagnostics.writeShadowMatch(
				chunkPos,
				javaFingerprint,
				ChunkSectionReadDiagnostics.elapsed(started),
				ChunkSectionReadDiagnostics.elapsed(compareStarted)
			);
		}
	}

	private static long readNativeNbtFingerprint(java.nio.file.Path path, long handle, ChunkPos chunkPos, String operation) throws IOException {
		try {
			NativeRegionFileBridge.NbtResult result = NativeRegionFileBridge.readNbtFingerprint(handle, chunkPos.x, chunkPos.z);
			if (!result.present()) {
				validationUnreadableChunk(path, chunkPos, "NBT fingerprint missing after " + operation);
				throw new IOException("Rust region NBT fingerprint missing for " + chunkPos + " after " + operation);
			}

			return result.fingerprint();
		} catch (IOException exception) {
			validationMalformedNbt(path, chunkPos, exception.getMessage());
			throw exception;
		}
	}

	private static boolean validationEnabled() {
		return STATUS_PATH != null;
	}

	private static synchronized void regionOpened(Path path) {
		if (!validationEnabled()) {
			return;
		}

		REGIONS.add(normalize(path));
		maybeWriteStatus();
	}

	private static synchronized void validationRead(
		Path path,
		ChunkPos pos,
		int compressionId,
		boolean external,
		long timestamp,
		int compressedBytes,
		long nbtFingerprint
	) {
		if (!validationEnabled()) {
			return;
		}

		chunksRead++;
		recordPayload(compressionId, external);
		maybeWriteStatus();
	}

	private static synchronized void validationWrite(
		Path path,
		ChunkPos pos,
		int compressionId,
		boolean external,
		long timestamp,
		long compressedBytes,
		long nbtFingerprint
	) {
		if (!validationEnabled()) {
			return;
		}

		chunksWritten++;
		recordPayload(compressionId, external);
		maybeWriteStatus();
	}

	private static synchronized void validationDelete(Path path, ChunkPos pos, long timestamp) {
		if (!validationEnabled()) {
			return;
		}

		chunksDeleted++;
		maybeWriteStatus();
	}

	private static synchronized void validationFlush(Path path) {
		if (!validationEnabled()) {
			return;
		}

		flushCalls++;
		maybeWriteStatus();
	}

	private static synchronized void validationRustError(Path path, ChunkPos pos, String operation, String message) {
		if (!validationEnabled()) {
			return;
		}

		rustErrors++;
		writeStatus();
	}

	private static synchronized void validationUnreadableChunk(Path path, ChunkPos pos, String message) {
		if (!validationEnabled()) {
			return;
		}

		unreadableChunks++;
		writeStatus();
	}

	private static synchronized void validationMalformedNbt(Path path, ChunkPos pos, String message) {
		if (!validationEnabled()) {
			return;
		}

		malformedNbt++;
		writeStatus();
	}

	private static void recordPayload(int compressionId, boolean external) {
		COMPRESSION_IDS.merge(compressionId, 1L, Long::sum);
		if (external) {
			externalPayloads++;
		} else {
			internalPayloads++;
		}
	}

	private static synchronized void writeStatus() {
		try {
			Path parent = STATUS_PATH.getParent();
			if (parent != null) {
				Files.createDirectories(parent);
			}
			Files.writeString(STATUS_PATH, toJson());
			eventsSinceWrite = 0;
		} catch (IOException ignored) {
		}
	}

	private static void maybeWriteStatus() {
		eventsSinceWrite++;
		if (eventsSinceWrite >= STATUS_WRITE_INTERVAL) {
			writeStatus();
		}
	}

	private static String toJson() {
		JsonObject root = new JsonObject();
		root.addProperty("status", "complete");
		JsonObject counters = new JsonObject();
		counters.addProperty("regionsOpened", REGIONS.size());
		counters.addProperty("chunksRead", chunksRead);
		counters.addProperty("chunksWritten", chunksWritten);
		counters.addProperty("chunksDeleted", chunksDeleted);
		counters.addProperty("flushCalls", flushCalls);
		counters.addProperty("rustErrors", rustErrors);
		counters.addProperty("unreadableChunks", unreadableChunks);
		counters.addProperty("malformedNbt", malformedNbt);
		counters.addProperty("internalPayloads", internalPayloads);
		counters.addProperty("externalPayloads", externalPayloads);
		root.add("counters", counters);
		JsonObject compressionIds = new JsonObject();
		for (Map.Entry<Integer, Long> entry : COMPRESSION_IDS.entrySet()) {
			compressionIds.addProperty(entry.getKey().toString(), entry.getValue());
		}
		root.add("compressionIds", compressionIds);
		JsonArray regions = new JsonArray();
		for (String region : REGIONS) {
			regions.add(region);
		}
		root.add("regions", regions);
		return GSON.toJson(root);
	}

	private static String normalize(Path path) {
		return path.toAbsolutePath().normalize().toString();
	}

	private static Path statusPath() {
		String raw = System.getProperty("mattmc.dev.regionFilesValidationStatus", "").trim();
		return raw.isEmpty() ? null : Path.of(raw);
	}

	private static String firstDifference(String path, Tag expected, Tag actual) {
		if (expected.getId() != actual.getId()) {
			return path + " type expected=" + expected.getType().getName() + " actual=" + actual.getType().getName();
		}
		if (expected instanceof CompoundTag expectedCompound && actual instanceof CompoundTag actualCompound) {
			for (String key : expectedCompound.keySet()) {
				Tag expectedChild = expectedCompound.get(key);
				Tag actualChild = actualCompound.get(key);
				if (actualChild == null) {
					return path + "." + key + " missing in actual";
				}
				if (!expectedChild.equals(actualChild)) {
					return firstDifference(path + "." + key, expectedChild, actualChild);
				}
			}
			for (String key : actualCompound.keySet()) {
				if (!expectedCompound.contains(key)) {
					return path + "." + key + " extra in actual";
				}
			}
			return path + " compound differs";
		}
		if (expected instanceof CollectionTag expectedCollection && actual instanceof CollectionTag actualCollection) {
			if (expectedCollection.size() != actualCollection.size()) {
				return path + " size expected=" + expectedCollection.size() + " actual=" + actualCollection.size();
			}
			for (int i = 0; i < expectedCollection.size(); i++) {
				Tag expectedChild = expectedCollection.get(i);
				Tag actualChild = actualCollection.get(i);
				if (!expectedChild.equals(actualChild)) {
					return firstDifference(path + "[" + i + "]", expectedChild, actualChild);
				}
			}
			return path + " collection differs";
		}
		return truncateDifference(path + " expected=" + expected + " actual=" + actual);
	}

	private static String truncateDifference(String difference) {
		int limit = 512;
		return difference.length() <= limit ? difference : difference.substring(0, limit) + "...";
	}

}
