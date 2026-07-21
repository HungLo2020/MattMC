package net.minecraft.world.level.chunk.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtBenchmarkAccess;
import net.minecraft.server.Bootstrap;

public final class StorageReplayBenchmark {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private StorageReplayBenchmark() {
	}

	public static void main(String[] args) throws Exception {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		Path tracePath = requiredPath("mattmc.storageReplay.trace");
		Path worldPath = requiredPath("mattmc.storageReplay.world");
		Path outputPath = requiredPath("mattmc.storageReplay.output");
		int warmup = intProperty("mattmc.storageReplay.warmup", 4);
		int measure = intProperty("mattmc.storageReplay.measure", 12);
		String implementation = System.getProperty("mattmc.storageReplay.implementation", "unknown");

		Trace trace = Trace.load(tracePath);
		Benchmark benchmark = new Benchmark(trace, worldPath);
		JsonObject output = new JsonObject();
		output.addProperty("implementation", implementation);
		output.addProperty("trace", tracePath.toAbsolutePath().toString());
		output.addProperty("world", worldPath.toAbsolutePath().toString());
		output.addProperty("warmupIterations", warmup);
		output.addProperty("measureIterations", measure);
		output.addProperty("supportsNativeTape", NbtBenchmarkAccess.supportsNativeTape());
		JsonArray samples = new JsonArray();

		samples.add(benchmark.runIteration("cold", 0, true));
		for (int i = 0; i < warmup; i++) {
			samples.add(benchmark.runIteration("warmup", i, true));
		}
		for (int i = 0; i < measure; i++) {
			samples.add(benchmark.runIteration("measure", i, true));
		}

		output.add("samples", samples);
		output.add("finalWorld", trace.fingerprintWorld(worldPath));
		Files.createDirectories(outputPath.getParent());
		Files.writeString(outputPath, GSON.toJson(output));
	}

	private static Path requiredPath(String property) {
		String value = System.getProperty(property, "");
		if (value.isBlank()) {
			throw new IllegalArgumentException("Missing required property " + property);
		}
		return Path.of(value);
	}

	private static int intProperty(String property, int fallback) {
		String value = System.getProperty(property);
		return value == null || value.isBlank() ? fallback : Integer.parseInt(value);
	}

	private static final class Benchmark {
		private final Trace trace;
		private final Path worldPath;

		private Benchmark(Trace trace, Path worldPath) {
			this.trace = trace;
			this.worldPath = worldPath;
		}

		JsonObject runIteration(String phase, int index, boolean validate) throws Exception {
			JsonObject sample = new JsonObject();
			sample.addProperty("phase", phase);
			sample.addProperty("index", index);
			Map<String, Metric> metrics = new HashMap<>();
			this.runNbt(metrics, validate);
			this.runRegion(metrics, validate);
			JsonObject metricsJson = new JsonObject();
			metrics.keySet().stream().sorted().forEach(key -> metricsJson.add(key, metrics.get(key).toJson()));
			sample.add("metrics", metricsJson);
			return sample;
		}

		private void runNbt(Map<String, Metric> metrics, boolean validate) throws IOException {
			for (NbtDocument document : this.trace.nbtDocuments) {
				byte[] encoded = document.encoded;
				int compression = document.compression;

				Metric.time(metrics, "nbt.decompress", encoded.length, () -> NbtBenchmarkAccess.decodeToRawBytes(encoded, compression));
				Metric.time(metrics, "nbt.compress", document.rawLength, () -> NbtBenchmarkAccess.encodeRawBytes(document.raw, compression));

				long implementationFingerprint = Metric.time(metrics, "nbt.implementation_fingerprint", encoded.length, () -> NbtBenchmarkAccess.implementationFingerprint(encoded, compression));
				CompoundTag tag = Metric.time(metrics, "nbt.decode_to_object", encoded.length, () -> NbtBenchmarkAccess.readObject(encoded, compression));
				String objectFingerprint = Metric.time(metrics, "nbt.object_fingerprint", document.rawLength, () -> NbtBenchmarkAccess.objectFingerprint(tag));
				byte[] reencoded = Metric.time(metrics, "nbt.object_to_encoded", document.rawLength, () -> NbtBenchmarkAccess.writeObject(tag, compression));
				Metric.time(metrics, "nbt.complete_object_roundtrip", encoded.length, () -> NbtBenchmarkAccess.writeObject(NbtBenchmarkAccess.readObject(encoded, compression), compression));

				if (NbtBenchmarkAccess.supportsNativeTape()) {
					byte[] tape = Metric.time(
						metrics,
						"nbt.decode.rust_decompress_parse_to_tape",
						encoded.length,
						encoded.length,
						() -> NbtBenchmarkAccess.decodeToTape(encoded, compression)
					);
					Metric.time(metrics, "nbt.decode.java_tape_to_object", tape.length, tape.length, () -> NbtBenchmarkAccess.readTapeObject(tape));
					Metric.time(metrics, "nbt.decode_to_tape", encoded.length, encoded.length, () -> NbtBenchmarkAccess.decodeToTape(encoded, compression));
					byte[] writtenTape = Metric.time(
						metrics,
						"nbt.encode.java_object_to_tape",
						document.rawLength,
						document.rawLength,
						() -> NbtBenchmarkAccess.writeTapeObject(tag)
					);
					Metric.time(
						metrics,
						"nbt.encode.rust_tape_to_encoded",
						writtenTape.length,
						writtenTape.length,
						() -> NbtBenchmarkAccess.encodeFromTape(writtenTape, compression)
					);
					Metric.time(metrics, "nbt.encode_from_tape", tape.length, tape.length, () -> NbtBenchmarkAccess.encodeFromTape(tape, compression));
				}

				if (validate) {
					if (!document.rawSha256.equals(NbtBenchmarkAccess.sha256Hex(NbtBenchmarkAccess.decodeToRawBytes(encoded, compression)))) {
						throw new IOException("Raw NBT hash mismatch for " + document.id);
					}
					if (!document.objectFingerprint.equals(objectFingerprint)) {
						throw new IOException("NBT object fingerprint mismatch for " + document.id + ": expected " + document.objectFingerprint + " got " + objectFingerprint);
					}
					if (!document.objectFingerprint.equals(NbtBenchmarkAccess.objectFingerprint(NbtBenchmarkAccess.readObject(reencoded, compression)))) {
						throw new IOException("NBT round-trip fingerprint mismatch for " + document.id);
					}
					if (implementationFingerprint == 0L) {
						throw new IOException("Zero implementation fingerprint for " + document.id);
					}
				}
			}
		}

		private void runRegion(Map<String, Metric> metrics, boolean validate) throws Exception {
			RegionSession session = new RegionSession(this.worldPath, metrics);
			long started = System.nanoTime();
			try {
				for (RegionOperation operation : this.trace.regionOperations) {
					switch (operation.op) {
						case "read" -> {
							BenchmarkPayload payload = Metric.time(
								metrics,
								"region.read_payload",
								Math.max(0, operation.payloadBytes),
								() -> session.readPayload(operation)
							);
							if (validate) {
								operation.validateRead(payload);
							}
						}
						case "write" -> {
							byte[] payload = operation.payload;
							Metric.time(metrics, "region.write_payload", payload.length, () -> {
								session.writePayload(operation, payload);
								return null;
							});
						}
						case "delete" -> Metric.time(metrics, "region.delete", 0, () -> {
							NativeRegionFileBridge.deleteChunk(session.region(operation), operation.chunkX, operation.chunkZ);
							return null;
						});
						case "flush" -> Metric.time(metrics, "region.flush", 0, () -> {
							NativeRegionFileBridge.flush(session.region(operation));
							return null;
						});
						case "reopen" -> Metric.time(metrics, "region.reopen", 0, () -> {
							session.reopen(operation);
							return null;
						});
						default -> throw new IOException("Unknown region operation: " + operation.op);
					}
				}
			} finally {
				session.close();
			}
			metrics.computeIfAbsent("region.complete_replay", key -> new Metric()).add(System.nanoTime() - started, this.trace.regionPayloadBytes);
		}
	}

	private static final class RegionSession implements AutoCloseable {
		private final Path worldPath;
		private final Map<String, Metric> metrics;
		private final Map<String, Long> open = new HashMap<>();

		private RegionSession(Path worldPath, Map<String, Metric> metrics) {
			this.worldPath = worldPath;
			this.metrics = metrics;
		}

		long region(RegionOperation operation) throws IOException {
			String key = operation.region;
			Long existing = this.open.get(key);
			if (existing != null) {
				return existing;
			}
			Path path = this.worldPath.resolve(operation.region);
			Files.createDirectories(path.getParent());
			long created = Metric.time(
				this.metrics,
				"region.open",
				0,
				() -> NativeRegionFileBridge.open(path, false)
			);
			this.open.put(key, created);
			return created;
		}

		BenchmarkPayload readPayload(RegionOperation operation) throws IOException {
			NativeRegionFileBridge.PayloadResult payload = NativeRegionFileBridge.readPayload(this.region(operation), operation.chunkX, operation.chunkZ);
			NativeRegionFileBridge.Result result = payload.result();
			if (!result.present()) {
				return BenchmarkPayload.missing();
			}

			ByteBuffer encoded = ByteBuffer.allocate(5 + payload.bytes().length);
			encoded.putInt(payload.bytes().length + 1);
			encoded.put((byte)result.compressionId());
			encoded.put(payload.bytes());
			return new BenchmarkPayload(true, result.compressionId(), result.external(), result.timestamp(), encoded.array());
		}

		void writePayload(RegionOperation operation, byte[] encodedPayload) throws IOException {
			ByteBuffer encoded = ByteBuffer.wrap(encodedPayload);
			if (encoded.remaining() < 5) {
				throw new IOException("Encoded region payload is too small: " + encodedPayload.length);
			}
			int declaredLength = encoded.getInt();
			int payloadLength = declaredLength - 1;
			if (payloadLength < 0 || payloadLength != encoded.remaining() - 1) {
				throw new IOException("Encoded region payload length " + declaredLength + " does not match buffer size " + encodedPayload.length);
			}
			int compressionId = Byte.toUnsignedInt(encoded.get());
			byte[] payload = new byte[payloadLength];
			encoded.get(payload);
			NativeRegionFileBridge.writePayload(this.region(operation), operation.chunkX, operation.chunkZ, compressionId, payload);
		}

		void reopen(RegionOperation operation) throws IOException {
			Long existing = this.open.remove(operation.region);
			if (existing != null) {
				this.closeRegion(existing);
			}
			this.region(operation);
		}

		@Override
		public void close() throws IOException {
			IOException failure = null;
			for (long region : this.open.values()) {
				try {
					this.closeRegion(region);
				} catch (IOException exception) {
					if (failure == null) {
						failure = exception;
					} else {
						failure.addSuppressed(exception);
					}
				}
			}
			this.open.clear();
			if (failure != null) {
				throw failure;
			}
		}

		private void closeRegion(long region) throws IOException {
			Metric.time(this.metrics, "region.close", 0, () -> {
				NativeRegionFileBridge.close(region);
				return null;
			});
		}
	}

	private static final class Trace {
		private final Path tracePath;
		private final List<NbtDocument> nbtDocuments;
		private final List<RegionOperation> regionOperations;
		private final long regionPayloadBytes;

		private Trace(Path tracePath, List<NbtDocument> nbtDocuments, List<RegionOperation> regionOperations) {
			this.tracePath = tracePath;
			this.nbtDocuments = nbtDocuments;
			this.regionOperations = regionOperations;
			long total = 0L;
			for (RegionOperation operation : regionOperations) {
				total += Math.max(0, operation.payloadBytes);
			}
			this.regionPayloadBytes = total;
		}

		static Trace load(Path tracePath) throws IOException {
			try (Reader reader = Files.newBufferedReader(tracePath)) {
				JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
				Path base = tracePath.getParent();
				List<NbtDocument> nbtDocuments = new ArrayList<>();
				for (JsonElement element : root.getAsJsonArray("nbtDocuments")) {
					nbtDocuments.add(NbtDocument.fromJson(base, element.getAsJsonObject()));
				}
				List<RegionOperation> regionOperations = new ArrayList<>();
				for (JsonElement element : root.getAsJsonArray("regionOperations")) {
					regionOperations.add(RegionOperation.fromJson(base, element.getAsJsonObject()));
				}
				return new Trace(tracePath, nbtDocuments, regionOperations);
			}
		}

		JsonObject fingerprintWorld(Path worldPath) throws IOException {
			JsonObject output = new JsonObject();
			output.addProperty("path", worldPath.toAbsolutePath().toString());
			output.addProperty("hash", hashTree(worldPath));
			output.addProperty("trace", this.tracePath.toAbsolutePath().toString());
			return output;
		}
	}

	private record NbtDocument(String id, int compression, byte[] encoded, byte[] raw, int rawLength, String rawSha256, String objectFingerprint) {
		static NbtDocument fromJson(Path base, JsonObject json) throws IOException {
			byte[] encoded = Files.readAllBytes(base.resolve(json.get("encodedBlob").getAsString()));
			byte[] raw = Files.readAllBytes(base.resolve(json.get("rawBlob").getAsString()));
			return new NbtDocument(
				json.get("id").getAsString(),
				json.get("compression").getAsInt(),
				encoded,
				raw,
				raw.length,
				json.get("rawSha256").getAsString(),
				json.get("objectFingerprint").getAsString()
			);
		}
	}

	private static final class RegionOperation {
		private final String op;
		private final String storageType;
		private final String region;
		private final int chunkX;
		private final int chunkZ;
		private final boolean expectedPresent;
		private final int compression;
		private final boolean external;
		private final int payloadBytes;
		private final String payloadSha256;
		private final byte[] payload;

		private RegionOperation(
			String op,
			String storageType,
			String region,
			int chunkX,
			int chunkZ,
			boolean expectedPresent,
			int compression,
			boolean external,
			int payloadBytes,
			String payloadSha256,
			byte[] payload
		) {
			this.op = op;
			this.storageType = storageType;
			this.region = region;
			this.chunkX = chunkX;
			this.chunkZ = chunkZ;
			this.expectedPresent = expectedPresent;
			this.compression = compression;
			this.external = external;
			this.payloadBytes = payloadBytes;
			this.payloadSha256 = payloadSha256;
			this.payload = payload;
		}

		static RegionOperation fromJson(Path base, JsonObject json) throws IOException {
			String blob = json.has("payloadBlob") ? json.get("payloadBlob").getAsString() : "";
			byte[] payload = blob.isBlank() ? new byte[0] : Files.readAllBytes(base.resolve(blob));
			return new RegionOperation(
				json.get("op").getAsString(),
				json.get("storageType").getAsString(),
				json.get("region").getAsString(),
				json.has("chunkX") ? json.get("chunkX").getAsInt() : 0,
				json.has("chunkZ") ? json.get("chunkZ").getAsInt() : 0,
				json.has("expectedPresent") && json.get("expectedPresent").getAsBoolean(),
				json.has("compression") ? json.get("compression").getAsInt() : -1,
				json.has("external") && json.get("external").getAsBoolean(),
				json.has("payloadBytes") ? json.get("payloadBytes").getAsInt() : payload.length,
				json.has("payloadSha256") ? json.get("payloadSha256").getAsString() : "",
				payload
			);
		}

		void validateRead(BenchmarkPayload payload) throws IOException {
			if (payload.present() != this.expectedPresent) {
				throw new IOException("Region presence mismatch for " + this.region + " " + this.chunkX + "," + this.chunkZ);
			}
			if (!this.expectedPresent) {
				return;
			}
			if (payload.compressionId() != this.compression || payload.external() != this.external) {
				throw new IOException("Region metadata mismatch for " + this.region + " " + this.chunkX + "," + this.chunkZ);
			}
			if (payload.encodedPayload().length != this.payloadBytes) {
				throw new IOException("Region payload length mismatch for " + this.region + " " + this.chunkX + "," + this.chunkZ);
			}
			String actualHash = sha256Hex(payload.encodedPayload());
			if (!actualHash.equals(this.payloadSha256)) {
				throw new IOException("Region payload hash mismatch for " + this.region + " " + this.chunkX + "," + this.chunkZ);
			}
		}
	}

	private record BenchmarkPayload(boolean present, int compressionId, boolean external, long timestamp, byte[] encodedPayload) {
		static BenchmarkPayload missing() {
			return new BenchmarkPayload(false, -1, false, 0L, new byte[0]);
		}
	}

	private static final class Metric {
		private static final com.sun.management.ThreadMXBean ALLOCATION_BEAN = allocationBean();
		private long nanos;
		private long operations;
		private long bytes;
		private long copiedBytes;
		private long allocatedBytes;
		private long allocationSamples;

		static <T> T time(Map<String, Metric> metrics, String name, long bytes, ThrowingSupplier<T> supplier) throws IOException {
			return time(metrics, name, bytes, bytes, supplier);
		}

		static <T> T time(Map<String, Metric> metrics, String name, long bytes, long copiedBytes, ThrowingSupplier<T> supplier) throws IOException {
			long allocatedBefore = threadAllocatedBytes();
			long started = System.nanoTime();
			try {
				return supplier.get();
			} finally {
				long allocatedAfter = threadAllocatedBytes();
				long allocated = allocatedBefore >= 0L && allocatedAfter >= allocatedBefore ? allocatedAfter - allocatedBefore : 0L;
				metrics.computeIfAbsent(name, key -> new Metric()).add(System.nanoTime() - started, bytes, copiedBytes, allocated, allocatedBefore >= 0L);
			}
		}

		void add(long nanos, long bytes) {
			this.add(nanos, bytes, bytes, 0L, false);
		}

		void add(long nanos, long bytes, long copiedBytes, long allocatedBytes, boolean allocationMeasured) {
			this.nanos += nanos;
			this.operations++;
			this.bytes += bytes;
			this.copiedBytes += copiedBytes;
			if (allocationMeasured) {
				this.allocatedBytes += allocatedBytes;
				this.allocationSamples++;
			}
		}

		JsonObject toJson() {
			JsonObject json = new JsonObject();
			json.addProperty("nanos", this.nanos);
			json.addProperty("operations", this.operations);
			json.addProperty("bytes", this.bytes);
			json.addProperty("copiedBytes", this.copiedBytes);
			json.addProperty("allocatedBytes", this.allocatedBytes);
			json.addProperty("allocationSamples", this.allocationSamples);
			json.addProperty("allocationCountAvailable", false);
			return json;
		}

		private static com.sun.management.ThreadMXBean allocationBean() {
			java.lang.management.ThreadMXBean bean = ManagementFactory.getThreadMXBean();
			if (bean instanceof com.sun.management.ThreadMXBean threadBean && threadBean.isThreadAllocatedMemorySupported()) {
				threadBean.setThreadAllocatedMemoryEnabled(true);
				return threadBean;
			}
			return null;
		}

		private static long threadAllocatedBytes() {
			if (ALLOCATION_BEAN == null || !ALLOCATION_BEAN.isThreadAllocatedMemoryEnabled()) {
				return -1L;
			}
			return ALLOCATION_BEAN.getThreadAllocatedBytes(Thread.currentThread().getId());
		}
	}

	@FunctionalInterface
	private interface ThrowingSupplier<T> {
		T get() throws IOException;
	}

	private static String hashTree(Path root) throws IOException {
		MessageDigest digest = sha256();
		if (!Files.exists(root)) {
			return "";
		}
		try (var paths = Files.walk(root)) {
			for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
				String relative = root.relativize(path).toString().replace('\\', '/');
				digest.update(relative.getBytes(java.nio.charset.StandardCharsets.UTF_8));
				digest.update((byte)0);
				digest.update(Files.readAllBytes(path));
				digest.update((byte)0);
			}
		}
		return HexFormat.of().formatHex(digest.digest());
	}

	private static MessageDigest sha256() {
		try {
			return MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException(exception);
		}
	}

	private static String sha256Hex(byte[] bytes) {
		return HexFormat.of().formatHex(sha256().digest(bytes));
	}
}
