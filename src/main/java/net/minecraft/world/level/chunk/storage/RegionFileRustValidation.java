package net.minecraft.world.level.chunk.storage;

import net.minecraft.world.level.ChunkPos;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class RegionFileRustValidation {
	private static final Path STATUS_PATH = statusPath();
	private static final int MAX_EVENTS = Math.max(0, Integer.getInteger("mattmc.dev.regionFilesValidationMaxEvents", 2048));
	private static final int STATUS_WRITE_INTERVAL = Math.max(1, Integer.getInteger("mattmc.dev.regionFilesValidationWriteInterval", 128));
	private static final Set<String> REGIONS = new LinkedHashSet<>();
	private static final List<Event> EVENTS = new ArrayList<>();
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
		if (enabled()) {
			Runtime.getRuntime().addShutdownHook(new Thread(RegionFileRustValidation::writeStatus, "MattMC Rust region validation writer"));
		}
	}

	private RegionFileRustValidation() {
	}

	static boolean enabled() {
		return STATUS_PATH != null;
	}

	static synchronized void regionOpened(Path path) {
		if (!enabled()) {
			return;
		}

		REGIONS.add(normalize(path));
		maybeWriteStatus();
	}

	static synchronized void recordRead(
		Path path,
		ChunkPos pos,
		int compressionId,
		boolean external,
		long timestamp,
		int compressedBytes,
		String payloadHash,
		long nbtFingerprint
	) {
		if (!enabled()) {
			return;
		}

		chunksRead++;
		recordPayload(compressionId, external);
		addEvent("read", path, pos, compressionId, external, timestamp, compressedBytes, payloadHash, nbtFingerprint, "");
		maybeWriteStatus();
	}

	static synchronized void recordWrite(
		Path path,
		ChunkPos pos,
		int compressionId,
		boolean external,
		long timestamp,
		long compressedBytes,
		String payloadHash,
		long nbtFingerprint
	) {
		if (!enabled()) {
			return;
		}

		chunksWritten++;
		recordPayload(compressionId, external);
		addEvent("write", path, pos, compressionId, external, timestamp, compressedBytes, payloadHash, nbtFingerprint, "");
		maybeWriteStatus();
	}

	static synchronized void recordDelete(Path path, ChunkPos pos, long timestamp) {
		if (!enabled()) {
			return;
		}

		chunksDeleted++;
		addEvent("delete", path, pos, 0, false, timestamp, 0L, "", 0L, "");
		maybeWriteStatus();
	}

	static synchronized void recordFlush(Path path) {
		if (!enabled()) {
			return;
		}

		flushCalls++;
		addEvent("flush", path, null, 0, false, 0L, 0L, "", 0L, "");
		maybeWriteStatus();
	}

	static synchronized void recordRustError(Path path, ChunkPos pos, String operation, String message) {
		if (!enabled()) {
			return;
		}

		rustErrors++;
		addEvent(operation + "-error", path, pos, 0, false, 0L, 0L, "", 0L, message);
		writeStatus();
	}

	static synchronized void recordUnreadableChunk(Path path, ChunkPos pos, String message) {
		if (!enabled()) {
			return;
		}

		unreadableChunks++;
		addEvent("unreadable", path, pos, 0, false, 0L, 0L, "", 0L, message);
		writeStatus();
	}

	static synchronized void recordMalformedNbt(Path path, ChunkPos pos, String message) {
		if (!enabled()) {
			return;
		}

		malformedNbt++;
		addEvent("malformed-nbt", path, pos, 0, false, 0L, 0L, "", 0L, message);
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

	private static void addEvent(
		String operation,
		Path path,
		ChunkPos pos,
		int compressionId,
		boolean external,
		long timestamp,
		long compressedBytes,
		String payloadHash,
		long nbtFingerprint,
		String message
	) {
		if (EVENTS.size() >= MAX_EVENTS) {
			return;
		}

		EVENTS.add(new Event(
			operation,
			normalize(path),
			pos == null ? 0 : pos.x,
			pos == null ? 0 : pos.z,
			pos != null,
			compressionId,
			external,
			timestamp,
			compressedBytes,
			payloadHash,
			nbtFingerprint,
			message
		));
	}

	private static void writeStatus() {
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
		StringBuilder builder = new StringBuilder(4096);
		builder.append("{\n");
		builder.append("  \"status\": \"complete\",\n");
		builder.append("  \"counters\": {\n");
		appendCounter(builder, "regionsOpened", REGIONS.size(), true);
		appendCounter(builder, "chunksRead", chunksRead, true);
		appendCounter(builder, "chunksWritten", chunksWritten, true);
		appendCounter(builder, "chunksDeleted", chunksDeleted, true);
		appendCounter(builder, "flushCalls", flushCalls, true);
		appendCounter(builder, "rustErrors", rustErrors, true);
		appendCounter(builder, "unreadableChunks", unreadableChunks, true);
		appendCounter(builder, "malformedNbt", malformedNbt, true);
		appendCounter(builder, "internalPayloads", internalPayloads, true);
		appendCounter(builder, "externalPayloads", externalPayloads, false);
		builder.append("  },\n");
		builder.append("  \"compressionIds\": {");
		boolean first = true;
		for (Map.Entry<Integer, Long> entry : COMPRESSION_IDS.entrySet()) {
			if (!first) {
				builder.append(", ");
			}
			first = false;
			builder.append('"').append(entry.getKey()).append("\": ").append(entry.getValue());
		}
		builder.append("},\n");
		builder.append("  \"regions\": [");
		first = true;
		for (String region : REGIONS) {
			if (!first) {
				builder.append(", ");
			}
			first = false;
			appendJsonString(builder, region);
		}
		builder.append("],\n");
		builder.append("  \"events\": [\n");
		for (int i = 0; i < EVENTS.size(); i++) {
			if (i > 0) {
				builder.append(",\n");
			}
			EVENTS.get(i).appendJson(builder);
		}
		builder.append("\n  ]\n");
		builder.append("}\n");
		return builder.toString();
	}

	private static void appendCounter(StringBuilder builder, String name, long value, boolean comma) {
		builder.append("    \"").append(name).append("\": ").append(value);
		if (comma) {
			builder.append(',');
		}
		builder.append('\n');
	}

	private static String normalize(Path path) {
		return path.toAbsolutePath().normalize().toString();
	}

	private static Path statusPath() {
		String raw = System.getProperty("mattmc.dev.regionFilesValidationStatus", "").trim();
		return raw.isEmpty() ? null : Path.of(raw);
	}

	private static void appendJsonString(StringBuilder builder, String value) {
		builder.append('"');
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			switch (c) {
				case '\\' -> builder.append("\\\\");
				case '"' -> builder.append("\\\"");
				case '\n' -> builder.append("\\n");
				case '\r' -> builder.append("\\r");
				case '\t' -> builder.append("\\t");
				default -> {
					if (c < 0x20) {
						builder.append(String.format("\\u%04x", (int)c));
					} else {
						builder.append(c);
					}
				}
			}
		}
		builder.append('"');
	}

	private record Event(
		String operation,
		String region,
		int chunkX,
		int chunkZ,
		boolean hasChunk,
		int compressionId,
		boolean external,
		long timestamp,
		long compressedBytes,
		String payloadHash,
		long nbtFingerprint,
		String message
	) {
		void appendJson(StringBuilder builder) {
			builder.append("    {");
			builder.append("\"operation\": ");
			appendJsonString(builder, this.operation);
			builder.append(", \"region\": ");
			appendJsonString(builder, this.region);
			if (this.hasChunk) {
				builder.append(", \"chunkX\": ").append(this.chunkX);
				builder.append(", \"chunkZ\": ").append(this.chunkZ);
			}
			builder.append(", \"compressionId\": ").append(this.compressionId);
			builder.append(", \"external\": ").append(this.external);
			builder.append(", \"timestamp\": ").append(this.timestamp);
			builder.append(", \"compressedBytes\": ").append(this.compressedBytes);
			if (!this.payloadHash.isEmpty()) {
				builder.append(", \"payloadSha256\": ");
				appendJsonString(builder, this.payloadHash);
			}
			if (this.nbtFingerprint != 0L) {
				builder.append(", \"nbtFingerprint\": ");
				appendJsonString(builder, Long.toUnsignedString(this.nbtFingerprint));
			}
			if (!this.message.isEmpty()) {
				builder.append(", \"message\": ");
				appendJsonString(builder, this.message);
			}
			builder.append('}');
		}
	}
}
