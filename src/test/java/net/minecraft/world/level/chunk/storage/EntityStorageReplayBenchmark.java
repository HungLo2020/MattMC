package net.minecraft.world.level.chunk.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DynamicOps;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.DetectedVersion;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NbtBenchmarkAccess;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Headless entity-region replay benchmark for Java versus Rust entity-read paths.
 *
 * <p>This class intentionally lives in test sources. It needs Mockito to supply
 * a minimal controlled {@link Level}, and it is not part of production entity
 * loading. The Rust path is reached reflectively so the same source can compile
 * in the frozen Java performance repository, where the Rust entity wrappers do
 * not exist.
 */
public final class EntityStorageReplayBenchmark {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final int CURRENT_DATA_VERSION = 4556;
	private static final RegionStorageInfo STORAGE_INFO = new RegionStorageInfo("entity-replay-benchmark", null, "entities");
	private static final int DEFAULT_REAL_CHUNKS = 32;
	private static final int DEFAULT_WARMUP = 8;
	private static final int DEFAULT_MEASURE = 24;
	private static final int MAX_FIELD_DIAGNOSTICS_PER_FIXTURE = 512;

	private EntityStorageReplayBenchmark() {
	}

	public static void main(String[] args) throws Exception {
		System.setProperty("net.bytebuddy.experimental", "true");
		SharedConstants.setVersion(DetectedVersion.BUILT_IN);
		Bootstrap.bootStrap();
		if (Boolean.getBoolean("mattmc.entityReplay.generateTrace")) {
			generateTrace();
			return;
		}
		runBenchmark();
	}

	private static void generateTrace() throws Exception {
		Path worldPath = requiredPath("mattmc.entityReplay.world");
		Path tracePath = requiredPath("mattmc.entityReplay.trace");
		int maxRealChunks = intProperty("mattmc.entityReplay.maxRealChunks", DEFAULT_REAL_CHUNKS);
		TraceBuilder builder = new TraceBuilder(tracePath, worldPath);
		builder.addRealChunks(maxRealChunks);
		builder.addGeneratedFixtures();
		builder.write();
	}

	private static void runBenchmark() throws Exception {
		Path tracePath = requiredPath("mattmc.entityReplay.trace");
		Path outputPath = requiredPath("mattmc.entityReplay.output");
		Path scratchPath = requiredPath("mattmc.entityReplay.scratch");
		String implementation = System.getProperty("mattmc.entityReplay.implementation", "java");
		int warmup = intProperty("mattmc.entityReplay.warmup", DEFAULT_WARMUP);
		int measure = intProperty("mattmc.entityReplay.measure", DEFAULT_MEASURE);
		Trace trace = Trace.load(tracePath);
		Benchmark benchmark = new Benchmark(trace, scratchPath, implementation);

		JsonObject output = new JsonObject();
		output.addProperty("implementation", implementation);
		output.addProperty("trace", tracePath.toAbsolutePath().toString());
		output.addProperty("scratch", scratchPath.toAbsolutePath().toString());
		output.addProperty("warmupIterations", warmup);
		output.addProperty("measureIterations", measure);
		output.addProperty("supportsRustEntityPath", RustEntityBridge.available());
		output.add("fixtures", trace.fixturesJson());
		JsonArray samples = new JsonArray();

		benchmark.prepareScratchRegions();
		samples.add(benchmark.runIteration("cold", 0, true));
		for (int i = 0; i < warmup; i++) {
			samples.add(benchmark.runIteration("warmup", i, true));
		}
		for (int i = 0; i < measure; i++) {
			samples.add(benchmark.runIteration("measure", i, true));
		}

		output.add("samples", samples);
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

	private static RegistryAccess.Frozen registryAccess() {
		return RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
	}

	private static Level controlledLevel() {
		Level level = mock(Level.class);
		when(level.registryAccess()).thenReturn(registryAccess());
		when(level.enabledFeatures()).thenReturn(FeatureFlags.DEFAULT_FLAGS);
		when(level.isClientSide()).thenReturn(false);
		return level;
	}

	private static final class Benchmark {
		private final Trace trace;
		private final Path scratchPath;
		private final String implementation;

		private Benchmark(Trace trace, Path scratchPath, String implementation) {
			this.trace = trace;
			this.scratchPath = scratchPath;
			this.implementation = implementation;
		}

		void prepareScratchRegions() throws IOException {
			if (Files.exists(this.scratchPath)) {
				try (Stream<Path> paths = Files.walk(this.scratchPath)) {
					for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
						Files.delete(path);
					}
				}
			}
			Files.createDirectories(this.scratchPath);
			Map<String, Long> regions = new HashMap<>();
			try {
				for (Fixture fixture : this.trace.fixtures) {
					long region = regions.computeIfAbsent(fixture.region, key -> {
						try {
							Path path = this.scratchPath.resolve(key);
							Files.createDirectories(path.getParent());
							return NativeRegionFileBridge.open(path, false);
						} catch (IOException exception) {
							throw new RegionUncheckedIOException(exception);
						}
					});
					writeEncodedPayload(region, fixture.chunkX, fixture.chunkZ, fixture.encodedPayload);
				}
			} catch (RegionUncheckedIOException exception) {
				throw exception.unwrap();
			} finally {
				for (long region : regions.values()) {
					NativeRegionFileBridge.close(region);
				}
			}
		}

		JsonObject runIteration(String phase, int index, boolean validate) throws Exception {
			Map<String, Metric> metrics = new HashMap<>();
			JsonArray fixtureResults = new JsonArray();
			long started = System.nanoTime();
			for (Fixture fixture : this.trace.fixtures) {
				fixtureResults.add(this.runFixture(fixture, metrics, validate));
			}
			metrics.computeIfAbsent("complete.iteration_with_validation", key -> new Metric()).add(System.nanoTime() - started, this.trace.totalPayloadBytes());

			JsonObject sample = new JsonObject();
			sample.addProperty("phase", phase);
			sample.addProperty("index", index);
			JsonObject metricsJson = new JsonObject();
			metrics.keySet().stream().sorted().forEach(key -> metricsJson.add(key, metrics.get(key).toJson()));
			sample.add("metrics", metricsJson);
			if (validate) {
				sample.add("fixtures", fixtureResults);
			}
			return sample;
		}

		private JsonObject runFixture(Fixture fixture, Map<String, Metric> metrics, boolean validate) throws Exception {
			if (this.implementation.equals("current-rust")) {
				return this.runRustFixture(fixture, metrics, validate);
			}
			return this.runJavaFixture(fixture, metrics, validate);
		}

		private JsonObject runJavaFixture(Fixture fixture, Map<String, Metric> metrics, boolean validate) throws Exception {
			long completeStarted = System.nanoTime();
			BenchmarkPayload payload = this.readRegionPayload(fixture, metrics);
			CompoundTag root = Metric.time(metrics, "java.decompress_nbt_parse", payload.encodedPayload().length, () -> readPayloadObject(payload.encodedPayload()));
			List<CompoundTag> entityTags = Metric.time(metrics, "java.entity_tag_list", payload.encodedPayload().length, () -> entityTags(root));
			ValueInput.ValueInputList inputs = Metric.time(
				metrics,
				"java.tag_value_input",
				payload.encodedPayload().length,
				() -> TagValueInput.create(ProblemReporter.DISCARDING, registryAccess(), entityTags)
			);
			LoadedEntities loaded = Metric.time(metrics, "java.entity_load", payload.encodedPayload().length, () -> loadEntities(inputs));
			metrics.computeIfAbsent("complete.entity_load_path", key -> new Metric()).add(System.nanoTime() - completeStarted, payload.encodedPayload().length);
			this.runJavaWriteFixture(fixture, loaded, metrics);
			String fingerprint = Metric.time(metrics, "java.resave_fingerprint", payload.encodedPayload().length, () -> savedFingerprint(loaded.saved()));
			return fixtureResult(fixture, loaded, fingerprint, validate, null);
		}

		private JsonObject runRustFixture(Fixture fixture, Map<String, Metric> metrics, boolean validate) throws Exception {
			if (!RustEntityBridge.available()) {
				throw new IOException("Rust entity path is not available in this repository");
			}
			long completeStarted = System.nanoTime();
			RustEntityBridge.Decoded decoded = Metric.time(metrics, "rust.complete_native_read_decode", fixture.encodedPayload.length, () -> RustEntityBridge.decode(this.scratchPath.resolve(fixture.region), fixture.chunkX, fixture.chunkZ));
			decoded.addNativeMetrics(metrics);
			ValueInput.ValueInputList inputs = Metric.time(metrics, "rust.tape_indexing", decoded.outputBytes, decoded.copiedBytes, decoded::createInputList);
			LoadedEntities loaded = Metric.time(metrics, "rust.entity_load", fixture.encodedPayload.length, () -> loadEntities(inputs));
			metrics.computeIfAbsent("complete.entity_load_path", key -> new Metric()).add(System.nanoTime() - completeStarted, fixture.encodedPayload.length);
			this.runRustWriteFixture(fixture, loaded, metrics);
			String fingerprint = Metric.time(metrics, "rust.resave_fingerprint", fixture.encodedPayload.length, () -> savedFingerprint(loaded.saved()));
			ValidationResult validationResult = null;
			if (validate) {
				validationResult = this.validateAgainstJava(fixture, loaded, fingerprint, metrics);
			}
			return fixtureResult(fixture, loaded, fingerprint, validate, validationResult);
		}

		private void runJavaWriteFixture(Fixture fixture, LoadedEntities loaded, Map<String, Metric> metrics) throws Exception {
			long completeStarted = System.nanoTime();
			List<CompoundTag> saved = Metric.time(metrics, "java.entity_save_compound", fixture.encodedPayload.length, () -> saveWithTagValueOutput(loaded.entities()));
			Metric.time(metrics, "java.entity_chunk_encode_write", fixture.encodedPayload.length, () -> {
				Path regionPath = this.scratchPath.resolve("java-writes").resolve(fixture.region);
				Files.createDirectories(regionPath.getParent());
				try (RegionFile region = new RegionFile(STORAGE_INFO, regionPath, regionPath.getParent(), RegionFileVersion.VERSION_DEFLATE, false)) {
					try (DataOutputStream output = region.getChunkDataOutputStream(new ChunkPos(fixture.chunkX, fixture.chunkZ))) {
						NbtIo.write(entityRoot(new ChunkPos(fixture.chunkX, fixture.chunkZ), saved.toArray(CompoundTag[]::new)), output);
					}
				}
				return null;
			});
			metrics.computeIfAbsent("complete.entity_save_write_path", key -> new Metric()).add(System.nanoTime() - completeStarted, fixture.encodedPayload.length);
		}

		private void runRustWriteFixture(Fixture fixture, LoadedEntities loaded, Map<String, Metric> metrics) throws Exception {
			long completeStarted = System.nanoTime();
			List<byte[]> tapes = Metric.time(metrics, "rust.entity_save_tape", fixture.encodedPayload.length, () -> RustEntityBridge.saveNativeTapes(loaded.entities()));
			Path regionPath = this.scratchPath.resolve("rust-writes").resolve(fixture.region);
			Metric.time(metrics, "rust.entity_chunk_encode_write", fixture.encodedPayload.length, () -> RustEntityBridge.writeNativeEntityChunk(regionPath, fixture.chunkX, fixture.chunkZ, RegionFileVersion.VERSION_DEFLATE.getId(), tapes));
			metrics.computeIfAbsent("complete.entity_save_write_path", key -> new Metric()).add(System.nanoTime() - completeStarted, fixture.encodedPayload.length);
			if (!tapes.isEmpty()) {
				RustEntityBridge.Decoded decoded = Metric.time(metrics, "rust.entity_write_reopen_read", fixture.encodedPayload.length, () -> RustEntityBridge.decode(regionPath, fixture.chunkX, fixture.chunkZ));
				List<CompoundTag> actual = decoded.readTags();
				if (!savedFingerprint(loaded.saved()).equals(savedFingerprint(actual))) {
					throw new IOException("Rust entity write/read parity mismatch for " + fixture.id);
				}
			}
		}

		private ValidationResult validateAgainstJava(Fixture fixture, LoadedEntities rustLoaded, String rustFingerprint, Map<String, Metric> metrics) throws Exception {
			long completeStarted = System.nanoTime();
			CompoundTag root = Metric.time(metrics, "validation.java_decompress_nbt_parse", fixture.encodedPayload.length, () -> readPayloadObject(fixture.encodedPayload));
			List<CompoundTag> entityTags = Metric.time(metrics, "validation.java_entity_tag_list", fixture.encodedPayload.length, () -> entityTags(root));
			ValueInput.ValueInputList inputs = Metric.time(
				metrics,
				"validation.java_tag_value_input",
				fixture.encodedPayload.length,
				() -> TagValueInput.create(ProblemReporter.DISCARDING, registryAccess(), entityTags)
			);
			LoadedEntities javaLoaded = Metric.time(metrics, "validation.java_entity_load", fixture.encodedPayload.length, () -> loadEntities(inputs));
			long completeNanos = System.nanoTime() - completeStarted;
			metrics.computeIfAbsent("validation.java_entity_load_path", key -> new Metric()).add(completeNanos, fixture.encodedPayload.length);
			metrics.computeIfAbsent("validation.java_complete_load", key -> new Metric()).add(completeNanos, fixture.encodedPayload.length);
			String javaFingerprint = Metric.time(metrics, "validation.java_resave_fingerprint", fixture.encodedPayload.length, () -> savedFingerprint(javaLoaded.saved()));
			String mismatch = compareLoaded(javaLoaded, rustLoaded, javaFingerprint, rustFingerprint);
			if (mismatch != null) {
				throw new IOException("Entity replay parity mismatch for " + fixture.id + ": " + mismatch);
			}
			return new ValidationResult(javaLoaded, javaFingerprint);
		}

		private BenchmarkPayload readRegionPayload(Fixture fixture, Map<String, Metric> metrics) throws Exception {
			long region = Metric.time(metrics, "region.open", 0, () -> {
				Path path = this.scratchPath.resolve(fixture.region);
				return NativeRegionFileBridge.open(path, false);
			});
			try {
				return Metric.time(metrics, "region.read_payload", fixture.encodedPayload.length, () -> {
					BenchmarkPayload payload = readEncodedPayload(region, fixture.chunkX, fixture.chunkZ);
					if (!payload.present()) {
						throw new IOException("Benchmark fixture chunk missing: " + fixture.id);
					}
					if (!sha256Hex(payload.encodedPayload()).equals(fixture.payloadSha256)) {
						throw new IOException("Benchmark fixture payload hash mismatch: " + fixture.id);
					}
					return payload;
				});
			} finally {
				NativeRegionFileBridge.close(region);
			}
		}
	}

	private static LoadedEntities loadEntities(ValueInput.ValueInputList inputs) throws IOException {
		Level level = controlledLevel();
		List<Entity> entities = EntityType.loadEntitiesRecursive(inputs, level, EntitySpawnReason.LOAD).toList();
		return new LoadedEntities(entities, saveWithTagValueOutput(entities));
	}

	private static List<CompoundTag> saveWithTagValueOutput(List<Entity> entities) throws IOException {
		List<CompoundTag> saved = new ArrayList<>(entities.size());
		for (Entity entity : entities) {
			TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, entity.registryAccess());
			if (!entity.saveAsPassenger(output)) {
				throw new IOException("Entity refused to save for benchmark parity: " + EntityType.getKey(entity.getType()));
			}
			saved.add(output.buildResult());
		}
		return List.copyOf(saved);
	}

	private static String compareLoaded(LoadedEntities expected, LoadedEntities actual, String expectedFingerprint, String actualFingerprint) {
		if (expected.entities.size() != actual.entities.size()) {
			return "entity count mismatch java=" + expected.entities.size() + " rust=" + actual.entities.size();
		}
		if (!expectedFingerprint.equals(actualFingerprint)) {
			return "saved fingerprint mismatch java=" + expectedFingerprint + " rust=" + actualFingerprint;
		}
		for (int i = 0; i < expected.entities.size(); i++) {
			Entity expectedEntity = expected.entities.get(i);
			Entity actualEntity = actual.entities.get(i);
			if (!EntityType.getKey(expectedEntity.getType()).equals(EntityType.getKey(actualEntity.getType()))) {
				return "entity " + i + " type mismatch";
			}
			if (!expectedEntity.getUUID().equals(actualEntity.getUUID())) {
				return "entity " + i + " UUID mismatch";
			}
			if (Double.compare(expectedEntity.getX(), actualEntity.getX()) != 0
				|| Double.compare(expectedEntity.getY(), actualEntity.getY()) != 0
				|| Double.compare(expectedEntity.getZ(), actualEntity.getZ()) != 0) {
				return "entity " + i + " position mismatch";
			}
			if (Float.compare(expectedEntity.getYRot(), actualEntity.getYRot()) != 0 || Float.compare(expectedEntity.getXRot(), actualEntity.getXRot()) != 0) {
				return "entity " + i + " rotation mismatch";
			}
			if (expectedEntity.getPassengers().size() != actualEntity.getPassengers().size()) {
				return "entity " + i + " passenger count mismatch";
			}
		}
		return null;
	}

	private static JsonObject fixtureResult(Fixture fixture, LoadedEntities loaded, String fingerprint, boolean validate, ValidationResult validationResult) throws IOException {
		JsonObject json = new JsonObject();
		json.addProperty("id", fixture.id);
		json.addProperty("kind", fixture.kind);
		json.addProperty("chunkX", fixture.chunkX);
		json.addProperty("chunkZ", fixture.chunkZ);
		json.addProperty("rootEntities", fixture.rootEntities);
		json.addProperty("loadedEntities", loaded.entities.size());
		json.addProperty("savedFingerprint", fingerprint);
		json.addProperty("stateFingerprint", entityStateFingerprint(loaded.entities));
		json.addProperty("validated", validate);
		if (validate) {
			json.add("savedEntityDiagnostics", savedEntityDiagnostics(loaded));
		}
		if (validationResult != null) {
			json.addProperty("currentJavaSavedFingerprint", validationResult.savedFingerprint());
			json.addProperty("currentJavaStateFingerprint", entityStateFingerprint(validationResult.loaded().entities()));
			json.add("currentJavaSavedEntityDiagnostics", savedEntityDiagnostics(validationResult.loaded()));
		}
		return json;
	}

	private static String entityStateFingerprint(List<Entity> entities) throws IOException {
		MessageDigest digest = sha256();
		for (Entity entity : entities) {
			writeEntityState(digest, entity);
			digest.update((byte)0);
		}
		return HexFormat.of().formatHex(digest.digest());
	}

	private static void writeEntityState(MessageDigest digest, Entity entity) {
		updateString(digest, EntityType.getKey(entity.getType()).toString());
		updateString(digest, entity.getUUID().toString());
		updateLong(digest, Double.doubleToRawLongBits(entity.getX()));
		updateLong(digest, Double.doubleToRawLongBits(entity.getY()));
		updateLong(digest, Double.doubleToRawLongBits(entity.getZ()));
		updateInt(digest, Float.floatToRawIntBits(entity.getYRot()));
		updateInt(digest, Float.floatToRawIntBits(entity.getXRot()));
		updateInt(digest, entity.getPassengers().size());
		for (Entity passenger : entity.getPassengers()) {
			writeEntityState(digest, passenger);
		}
	}

	private static JsonArray savedEntityDiagnostics(LoadedEntities loaded) {
		JsonArray array = new JsonArray();
		for (int i = 0; i < loaded.saved().size(); i++) {
			CompoundTag saved = loaded.saved().get(i);
			JsonObject entity = new JsonObject();
			entity.addProperty("rootIndex", i);
			if (i < loaded.entities().size()) {
				Entity liveEntity = loaded.entities().get(i);
				entity.addProperty("type", EntityType.getKey(liveEntity.getType()).toString());
				entity.addProperty("uuid", liveEntity.getUUID().toString());
				entity.addProperty("x", liveEntity.getX());
				entity.addProperty("y", liveEntity.getY());
				entity.addProperty("z", liveEntity.getZ());
				entity.addProperty("yRot", liveEntity.getYRot());
				entity.addProperty("xRot", liveEntity.getXRot());
				entity.addProperty("directPassengers", liveEntity.getPassengers().size());
				entity.addProperty("totalPassengers", (int)liveEntity.getPassengersAndSelf().count() - 1);
			}
			FieldCollector collector = new FieldCollector(MAX_FIELD_DIAGNOSTICS_PER_FIXTURE);
			flattenTag("$[" + i + "]", saved, collector);
			entity.add("fields", collector.fields);
			entity.addProperty("fieldsTruncated", collector.truncated);
			array.add(entity);
		}
		return array;
	}

	private static void flattenTag(String path, Tag tag, FieldCollector collector) {
		if (collector.truncated) {
			return;
		}
		switch (tag.getId()) {
			case 10 -> {
				CompoundTag compound = (CompoundTag)tag;
				for (String key : compound.keySet().stream().sorted().toList()) {
					Tag child = compound.get(key);
					if (child != null) {
						flattenTag(path + "." + key, child, collector);
					}
				}
			}
			case 9 -> {
				ListTag list = (ListTag)tag;
				if (list.isEmpty()) {
					collector.add(path, "list[0]");
					return;
				}
				for (int i = 0; i < list.size(); i++) {
					flattenTag(path + "[" + i + "]", list.get(i), collector);
				}
			}
			default -> collector.add(path, describeTag(tag));
		}
	}

	private static String describeTag(Tag tag) {
		return switch (tag.getId()) {
			case 1 -> "byte:" + ((NumericTag)tag).byteValue();
			case 2 -> "short:" + ((NumericTag)tag).shortValue();
			case 3 -> "int:" + ((NumericTag)tag).intValue();
			case 4 -> "long:" + ((NumericTag)tag).longValue();
			case 5 -> "floatBits:" + Integer.toUnsignedString(Float.floatToRawIntBits(((NumericTag)tag).floatValue()));
			case 6 -> "doubleBits:" + Long.toUnsignedString(Double.doubleToRawLongBits(((NumericTag)tag).doubleValue()));
			case 7 -> arrayDescription("byteArray", ((ByteArrayTag)tag).getAsByteArray());
			case 8 -> "string:" + tag.asString().orElse("");
			case 11 -> arrayDescription("intArray", ((IntArrayTag)tag).getAsIntArray());
			case 12 -> arrayDescription("longArray", ((LongArrayTag)tag).getAsLongArray());
			default -> "tag" + tag.getId();
		};
	}

	private static String arrayDescription(String type, byte[] values) {
		return values.length <= 16 ? type + ":" + Arrays.toString(values) : type + "[len=" + values.length + ",sha256=" + sha256Hex(values) + "]";
	}

	private static String arrayDescription(String type, int[] values) {
		return values.length <= 16 ? type + ":" + Arrays.toString(values) : type + "[len=" + values.length + ",hash=" + Arrays.hashCode(values) + "]";
	}

	private static String arrayDescription(String type, long[] values) {
		return values.length <= 16 ? type + ":" + Arrays.toString(values) : type + "[len=" + values.length + ",hash=" + Arrays.hashCode(values) + "]";
	}

	private static void updateString(MessageDigest digest, String value) {
		byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
		updateInt(digest, bytes.length);
		digest.update(bytes);
	}

	private static void updateInt(MessageDigest digest, int value) {
		digest.update((byte)(value >>> 24));
		digest.update((byte)(value >>> 16));
		digest.update((byte)(value >>> 8));
		digest.update((byte)value);
	}

	private static void updateLong(MessageDigest digest, long value) {
		for (int shift = 56; shift >= 0; shift -= 8) {
			digest.update((byte)(value >>> shift));
		}
	}

	private static CompoundTag readPayloadObject(byte[] encodedPayload) throws IOException {
		if (encodedPayload.length < 5) {
			throw new IOException("Encoded region payload is too small");
		}
		int declaredLength = (encodedPayload[0] & 0xFF) << 24 | (encodedPayload[1] & 0xFF) << 16 | (encodedPayload[2] & 0xFF) << 8 | encodedPayload[3] & 0xFF;
		if (declaredLength != encodedPayload.length - 4) {
			throw new IOException("Encoded region payload length mismatch");
		}
		int compression = encodedPayload[4] & 0x7F;
		RegionFileVersion version = RegionFileVersion.fromId(compression);
		if (version == null) {
			throw new IOException("Unsupported region compression id " + compression);
		}
		return NbtIo.read(new DataInputStream(version.wrap(new ByteArrayInputStream(encodedPayload, 5, encodedPayload.length - 5))));
	}

	private static BenchmarkPayload readEncodedPayload(long region, int chunkX, int chunkZ) throws IOException {
		NativeRegionFileBridge.PayloadResult payload = NativeRegionFileBridge.readPayload(region, chunkX, chunkZ);
		NativeRegionFileBridge.Result result = payload.result();
		if (!result.present()) {
			return BenchmarkPayload.missing();
		}

		return new BenchmarkPayload(true, result.compressionId(), result.external(), result.timestamp(), encodeRegionPayload(payload.bytes(), result.compressionId()));
	}

	private static void writeEncodedPayload(long region, int chunkX, int chunkZ, byte[] encodedPayload) throws IOException {
		if (encodedPayload.length < 5) {
			throw new IOException("Encoded region payload is too small: " + encodedPayload.length);
		}
		ByteBuffer encoded = ByteBuffer.wrap(encodedPayload);
		int declaredLength = encoded.getInt();
		int payloadLength = declaredLength - 1;
		if (payloadLength < 0 || payloadLength != encoded.remaining() - 1) {
			throw new IOException("Encoded region payload length " + declaredLength + " does not match buffer size " + encodedPayload.length);
		}
		int compressionId = Byte.toUnsignedInt(encoded.get());
		byte[] payload = new byte[payloadLength];
		encoded.get(payload);
		NativeRegionFileBridge.writePayload(region, chunkX, chunkZ, compressionId, payload);
	}

	private static byte[] encodeRegionPayload(byte[] payload, int compressionId) {
		ByteBuffer encoded = ByteBuffer.allocate(5 + payload.length);
		encoded.putInt(payload.length + 1);
		encoded.put((byte)compressionId);
		encoded.put(payload);
		return encoded.array();
	}

	private record BenchmarkPayload(boolean present, int compressionId, boolean external, long timestamp, byte[] encodedPayload) {
		static BenchmarkPayload missing() {
			return new BenchmarkPayload(false, -1, false, 0L, new byte[0]);
		}
	}

	private static List<CompoundTag> entityTags(CompoundTag root) {
		Optional<ListTag> entities = root.getList("Entities");
		if (entities.isEmpty()) {
			return List.of();
		}
		return entities.orElseThrow().compoundStream().toList();
	}

	private static String savedFingerprint(List<CompoundTag> saved) throws IOException {
		MessageDigest digest = sha256();
		for (CompoundTag tag : saved) {
			digest.update(canonicalBytes(tag));
			digest.update((byte)0);
		}
		return HexFormat.of().formatHex(digest.digest());
	}

	private static byte[] canonicalBytes(Tag tag) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (DataOutputStream output = new DataOutputStream(bytes)) {
			writeCanonicalTag(output, tag);
		}
		return bytes.toByteArray();
	}

	private static void writeCanonicalTag(DataOutputStream output, Tag tag) throws IOException {
		output.writeByte(tag.getId());
		switch (tag.getId()) {
			case 1 -> output.writeByte(((NumericTag)tag).byteValue());
			case 2 -> output.writeShort(((NumericTag)tag).shortValue());
			case 3 -> output.writeInt(((NumericTag)tag).intValue());
			case 4 -> output.writeLong(((NumericTag)tag).longValue());
			case 5 -> output.writeInt(Float.floatToRawIntBits(((NumericTag)tag).floatValue()));
			case 6 -> output.writeLong(Double.doubleToRawLongBits(((NumericTag)tag).doubleValue()));
			case 7 -> {
				byte[] values = ((ByteArrayTag)tag).getAsByteArray();
				output.writeInt(values.length);
				output.write(values);
			}
			case 8 -> output.writeUTF(tag.asString().orElse(""));
			case 9 -> {
				ListTag list = (ListTag)tag;
				output.writeInt(list.size());
				for (int i = 0; i < list.size(); i++) {
					writeCanonicalTag(output, list.get(i));
				}
			}
			case 10 -> {
				CompoundTag compound = (CompoundTag)tag;
				List<String> keys = compound.keySet().stream().sorted().toList();
				output.writeInt(keys.size());
				for (String key : keys) {
					output.writeUTF(key);
					writeCanonicalTag(output, compound.get(key));
				}
			}
			case 11 -> {
				int[] values = ((IntArrayTag)tag).getAsIntArray();
				output.writeInt(values.length);
				for (int value : values) {
					output.writeInt(value);
				}
			}
			case 12 -> {
				long[] values = ((LongArrayTag)tag).getAsLongArray();
				output.writeInt(values.length);
				for (long value : values) {
					output.writeLong(value);
				}
			}
			default -> throw new IOException("Unsupported canonical NBT tag id " + tag.getId());
		}
	}

	private static final class TraceBuilder {
		private final Path tracePath;
		private final Path worldPath;
		private final Path blobDir;
		private final List<JsonObject> fixtures = new ArrayList<>();
		private int fixtureIndex;

		private TraceBuilder(Path tracePath, Path worldPath) throws IOException {
			this.tracePath = tracePath;
			this.worldPath = worldPath;
			this.blobDir = tracePath.getParent().resolve("entity_blobs");
			Files.createDirectories(this.blobDir);
		}

		void addRealChunks(int maxChunks) throws IOException {
			Path entities = this.worldPath.resolve("entities");
			if (!Files.isDirectory(entities)) {
				return;
			}
			try (Stream<Path> paths = Files.list(entities)) {
				for (Path regionPath : paths.filter(path -> path.getFileName().toString().endsWith(".mca")).sorted().toList()) {
					if (this.realFixtureCount() >= maxChunks) {
						return;
					}
					int[] coords = parseRegionCoords(regionPath.getFileName().toString());
					long region = NativeRegionFileBridge.open(regionPath, false);
					try {
						for (int index = 0; index < 1024 && this.realFixtureCount() < maxChunks; index++) {
							if (!headerHasChunk(regionPath, index)) {
								continue;
							}
							int chunkX = coords[0] * 32 + (index & 31);
							int chunkZ = coords[1] * 32 + (index >> 5);
							BenchmarkPayload payload = readEncodedPayload(region, chunkX, chunkZ);
							if (!payload.present()) {
								continue;
							}
							CompoundTag root = readPayloadObject(payload.encodedPayload());
							if (root.getInt("DataVersion").orElse(0) != CURRENT_DATA_VERSION) {
								continue;
							}
							this.addPayload(
								"real-" + this.realFixtureCount(),
								"real",
								"entities/" + regionPath.getFileName(),
								chunkX,
								chunkZ,
								payload.encodedPayload(),
								root
							);
						}
					} finally {
						NativeRegionFileBridge.close(region);
					}
				}
			}
		}

		void addGeneratedFixtures() throws IOException {
			Path generatedDir = this.tracePath.getParent().resolve("generated-world").resolve("entities");
			Files.createDirectories(generatedDir);
			Path regionPath = generatedDir.resolve("r.0.0.mca");
			long region = NativeRegionFileBridge.open(regionPath, false);
			try {
				this.writeGenerated(region, "generated-empty", new ChunkPos(0, 0), entityRoot(new ChunkPos(0, 0)));
				this.writeGenerated(region, "generated-ordinary", new ChunkPos(1, 0), entityRoot(new ChunkPos(1, 0), entity("minecraft:pig")));
				this.writeGenerated(
					region,
					"generated-multiple",
					new ChunkPos(2, 0),
					entityRoot(new ChunkPos(2, 0), entity("minecraft:armor_stand"), itemEntity())
				);
				this.writeGenerated(
					region,
					"generated-passengers",
					new ChunkPos(3, 0),
					entityRoot(new ChunkPos(3, 0), entity("minecraft:area_effect_cloud", entity("minecraft:armor_stand", entity("minecraft:pig"))))
				);
				this.writeGenerated(region, "generated-unknown-id", new ChunkPos(4, 0), entityRoot(new ChunkPos(4, 0), entity("mattmc:not_registered"), entity("minecraft:armor_stand")));
			} finally {
				NativeRegionFileBridge.close(region);
			}
		}

		void write() throws IOException {
			JsonObject root = new JsonObject();
			root.addProperty("version", 1);
			root.addProperty("world", this.worldPath.toAbsolutePath().toString());
			root.addProperty("currentDataVersion", CURRENT_DATA_VERSION);
			JsonArray array = new JsonArray();
			for (JsonObject fixture : this.fixtures) {
				array.add(fixture);
			}
			root.add("fixtures", array);
			Files.createDirectories(this.tracePath.getParent());
			Files.writeString(this.tracePath, GSON.toJson(root));
		}

		private void writeGenerated(long region, String id, ChunkPos pos, CompoundTag root) throws IOException {
			byte[] encodedPayload = encodeRegionPayload(NbtBenchmarkAccess.writeObject(root, NbtBenchmarkAccess.FORMAT_ZLIB), RegionFileVersion.VERSION_DEFLATE.getId());
			writeEncodedPayload(region, pos.x, pos.z, encodedPayload);
			BenchmarkPayload payload = readEncodedPayload(region, pos.x, pos.z);
			this.addPayload(id, "generated", "entities/r.0.0.mca", pos.x, pos.z, payload.encodedPayload(), root);
		}

		private void addPayload(String id, String kind, String region, int chunkX, int chunkZ, byte[] encodedPayload, CompoundTag root) throws IOException {
			String blobName = "%03d-%s.bin".formatted(this.fixtureIndex++, id);
			Files.write(this.blobDir.resolve(blobName), encodedPayload);
			JsonObject json = new JsonObject();
			json.addProperty("id", id);
			json.addProperty("kind", kind);
			json.addProperty("region", region);
			json.addProperty("chunkX", chunkX);
			json.addProperty("chunkZ", chunkZ);
			json.addProperty("compressionId", encodedPayload[4] & 0x7F);
			json.addProperty("payloadBlob", "entity_blobs/" + blobName);
			json.addProperty("payloadBytes", encodedPayload.length);
			json.addProperty("payloadSha256", sha256Hex(encodedPayload));
			json.addProperty("rootEntities", entityTags(root).size());
			json.addProperty("rootFingerprint", NbtBenchmarkAccess.objectFingerprint(root));
			this.fixtures.add(json);
		}

		private int realFixtureCount() {
			int count = 0;
			for (JsonObject fixture : this.fixtures) {
				if (fixture.get("kind").getAsString().equals("real")) {
					count++;
				}
			}
			return count;
		}
	}

	private static boolean headerHasChunk(Path regionPath, int index) throws IOException {
		byte[] header = Files.readAllBytes(regionPath);
		if (header.length < 4096 || index < 0 || index >= 1024) {
			return false;
		}
		int offset = index * 4;
		return header[offset] != 0 || header[offset + 1] != 0 || header[offset + 2] != 0 || header[offset + 3] != 0;
	}

	private static int[] parseRegionCoords(String name) {
		String[] parts = name.split("\\.");
		if (parts.length < 4) {
			throw new IllegalArgumentException("Invalid region filename: " + name);
		}
		return new int[]{Integer.parseInt(parts[1]), Integer.parseInt(parts[2])};
	}

	private static CompoundTag entityRoot(ChunkPos pos, CompoundTag... entities) {
		CompoundTag root = new CompoundTag();
		root.putInt("DataVersion", CURRENT_DATA_VERSION);
		root.putIntArray("Position", new int[]{pos.x, pos.z});
		ListTag list = new ListTag();
		for (CompoundTag entity : entities) {
			list.add(entity);
		}
		root.put("Entities", list);
		return root;
	}

	private static CompoundTag entity(String id, CompoundTag... passengers) {
		CompoundTag tag = new CompoundTag();
		tag.putString("id", id);
		UUID uuid = UUID.nameUUIDFromBytes(id.getBytes(java.nio.charset.StandardCharsets.UTF_8));
		tag.putIntArray("UUID", UUIDUtil.uuidToIntArray(uuid));
		tag.put("Pos", doubleList(1.25, 64.0, -2.5));
		tag.put("Motion", doubleList(0.0625, 0.0, -0.125));
		tag.put("Rotation", floatList(90.0F, 12.5F));
		tag.putDouble("fall_distance", 1.5);
		tag.putShort("Fire", (short)20);
		tag.putInt("Air", 250);
		tag.putBoolean("OnGround", true);
		tag.putBoolean("Invulnerable", false);
		tag.putInt("PortalCooldown", 5);
		tag.putBoolean("Silent", true);
		tag.putBoolean("NoGravity", false);
		tag.putBoolean("Glowing", true);
		tag.putInt("TicksFrozen", 3);
		tag.putBoolean("HasVisualFire", true);
		if (id.equals("minecraft:area_effect_cloud")) {
			tag.putInt("Age", 12);
			tag.putInt("Duration", 200);
			tag.putInt("WaitTime", 5);
			tag.putFloat("Radius", 2.5F);
			tag.putFloat("RadiusOnUse", -0.25F);
			tag.putFloat("RadiusPerTick", -0.01F);
		} else if (id.equals("minecraft:armor_stand")) {
			tag.putBoolean("Invisible", true);
			tag.putBoolean("Small", true);
			tag.putBoolean("ShowArms", true);
			tag.putBoolean("NoBasePlate", true);
			tag.putInt("DisabledSlots", 7);
			EntityEquipment equipment = new EntityEquipment();
			equipment.set(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
			equipment.set(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
			DynamicOps<Tag> ops = registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
			tag.store("equipment", EntityEquipment.CODEC, ops, equipment);
		}
		ListTag tags = new ListTag();
		tags.add(StringTag.valueOf("entity-replay"));
		tags.add(StringTag.valueOf("benchmark"));
		tag.put("Tags", tags);
		if (passengers.length > 0) {
			ListTag list = new ListTag();
			for (CompoundTag passenger : passengers) {
				list.add(passenger);
			}
			tag.put("Passengers", list);
		}
		return tag;
	}

	private static CompoundTag itemEntity() {
		CompoundTag tag = entity("minecraft:item");
		tag.putShort("Health", (short)5);
		tag.putShort("Age", (short)10);
		tag.putShort("PickupDelay", (short)2);
		DynamicOps<Tag> ops = registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
		tag.store("Item", ItemStack.CODEC, ops, new ItemStack(Items.DIAMOND_SWORD));
		return tag;
	}

	private static ListTag doubleList(double... values) {
		ListTag list = new ListTag();
		for (double value : values) {
			list.add(DoubleTag.valueOf(value));
		}
		return list;
	}

	private static ListTag floatList(float... values) {
		ListTag list = new ListTag();
		for (float value : values) {
			list.add(FloatTag.valueOf(value));
		}
		return list;
	}

	private static final class Trace {
		private final List<Fixture> fixtures;

		private Trace(List<Fixture> fixtures) {
			this.fixtures = fixtures;
		}

		static Trace load(Path tracePath) throws IOException {
			try (Reader reader = Files.newBufferedReader(tracePath)) {
				JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
				Path base = tracePath.getParent();
				List<Fixture> fixtures = new ArrayList<>();
				for (JsonElement element : root.getAsJsonArray("fixtures")) {
					fixtures.add(Fixture.fromJson(base, element.getAsJsonObject()));
				}
				return new Trace(fixtures);
			}
		}

		JsonArray fixturesJson() {
			JsonArray array = new JsonArray();
			for (Fixture fixture : this.fixtures) {
				JsonObject json = new JsonObject();
				json.addProperty("id", fixture.id);
				json.addProperty("kind", fixture.kind);
				json.addProperty("region", fixture.region);
				json.addProperty("chunkX", fixture.chunkX);
				json.addProperty("chunkZ", fixture.chunkZ);
				json.addProperty("compressionId", fixture.compressionId);
				json.addProperty("payloadBytes", fixture.encodedPayload.length);
				json.addProperty("rootEntities", fixture.rootEntities);
				array.add(json);
			}
			return array;
		}

		long totalPayloadBytes() {
			long total = 0L;
			for (Fixture fixture : this.fixtures) {
				total += fixture.encodedPayload.length;
			}
			return total;
		}
	}

	private record Fixture(
		String id,
		String kind,
		String region,
		int chunkX,
		int chunkZ,
		int compressionId,
		byte[] encodedPayload,
		String payloadSha256,
		int rootEntities
	) {
		static Fixture fromJson(Path base, JsonObject json) throws IOException {
			byte[] payload = Files.readAllBytes(base.resolve(json.get("payloadBlob").getAsString()));
			return new Fixture(
				json.get("id").getAsString(),
				json.get("kind").getAsString(),
				json.get("region").getAsString(),
				json.get("chunkX").getAsInt(),
				json.get("chunkZ").getAsInt(),
				json.get("compressionId").getAsInt(),
				payload,
				json.get("payloadSha256").getAsString(),
				json.get("rootEntities").getAsInt()
			);
		}
	}

	private static final class RustEntityBridge {
		private static final Class<?> STORAGE_CLASS = find("net.minecraft.world.level.chunk.storage.NativeEntityStorage");
		private static final Class<?> VALUE_INPUT_CLASS = find("net.minecraft.world.level.storage.NativeEntityValueInput");
		private static final Class<?> VALUE_OUTPUT_CLASS = find("net.minecraft.world.level.storage.NativeEntityValueOutput");
		private static final Method OPEN = method(STORAGE_CLASS, "open", Path.class);
		private static final Method DECODE_CHUNK = method(STORAGE_CLASS, "decodeChunk", int.class, int.class);
		private static final Method WRITE_CHUNK = method(STORAGE_CLASS, "writeChunk", int.class, int.class, int.class, List.class);
		private static final Method CLOSE = method(STORAGE_CLASS, "close");
		private static final Method CREATE_INPUT_LIST = method(VALUE_INPUT_CLASS, "createListFromSlices", ProblemReporter.class, net.minecraft.core.HolderLookup.Provider.class, List.class);
		private static final Method CREATE_OUTPUT = method(VALUE_OUTPUT_CLASS, "createWithContext", ProblemReporter.class, net.minecraft.core.HolderLookup.Provider.class);
		private static final Method BUILD_TAPE = method(VALUE_OUTPUT_CLASS, "buildTape");

		static boolean available() {
			return STORAGE_CLASS != null && VALUE_INPUT_CLASS != null && VALUE_OUTPUT_CLASS != null;
		}

		static Decoded decode(Path regionPath, int chunkX, int chunkZ) throws Exception {
			Object storage = OPEN.invoke(null, regionPath);
			try {
				Object result = DECODE_CHUNK.invoke(storage, chunkX, chunkZ);
				return Decoded.from(result);
			} finally {
				CLOSE.invoke(storage);
			}
		}

		static List<byte[]> saveNativeTapes(List<Entity> entities) throws Exception {
			List<byte[]> tapes = new ArrayList<>(entities.size());
			for (Entity entity : entities) {
				Object output = CREATE_OUTPUT.invoke(null, ProblemReporter.DISCARDING, entity.registryAccess());
				if (!entity.saveAsPassenger((ValueOutput)output)) {
					throw new IOException("Entity refused to save for native output benchmark: " + EntityType.getKey(entity.getType()));
				}
				tapes.add((byte[])BUILD_TAPE.invoke(output));
			}
			return List.copyOf(tapes);
		}

		static void writeNativeEntityChunk(Path regionPath, int chunkX, int chunkZ, int compressionId, List<byte[]> tapes) throws Exception {
			Files.createDirectories(regionPath.getParent());
			Object storage = OPEN.invoke(null, regionPath);
			try {
				WRITE_CHUNK.invoke(storage, chunkX, chunkZ, compressionId, tapes);
			} finally {
				CLOSE.invoke(storage);
			}
		}

		private static Class<?> find(String name) {
			try {
				return Class.forName(name);
			} catch (ClassNotFoundException exception) {
				return null;
			}
		}

		private static Method method(Class<?> owner, String name, Class<?>... params) {
			if (owner == null) {
				return null;
			}
			try {
				return owner.getMethod(name, params);
			} catch (NoSuchMethodException exception) {
				throw new ExceptionInInitializerError(exception);
			}
		}

		private record Decoded(Object result, Object metrics, List<?> records, List<?> slices, long outputBytes, long copiedBytes) {
			static Decoded from(Object decodeResult) throws Exception {
				Object result = decodeResult.getClass().getMethod("result").invoke(decodeResult);
				Object metrics = decodeResult.getClass().getMethod("metrics").invoke(decodeResult);
				@SuppressWarnings("unchecked")
				List<?> records = (List<?>)decodeResult.getClass().getMethod("entities").invoke(decodeResult);
				@SuppressWarnings("unchecked")
				List<?> slices = (List<?>)decodeResult.getClass().getMethod("entityTapeSlices").invoke(decodeResult);
				long outputBytes = ((Number)result.getClass().getMethod("outputLength").invoke(result)).longValue();
				long copiedBytes = ((Number)metrics.getClass().getMethod("copiedBytes").invoke(metrics)).longValue();
				return new Decoded(result, metrics, records, slices, outputBytes, copiedBytes);
			}

			ValueInput.ValueInputList createInputList() throws Exception {
				return (ValueInput.ValueInputList)CREATE_INPUT_LIST.invoke(null, ProblemReporter.DISCARDING, registryAccess(), this.slices);
			}

			List<CompoundTag> readTags() throws Exception {
				List<CompoundTag> tags = new ArrayList<>(this.records.size());
				for (Object record : this.records) {
					tags.add((CompoundTag)record.getClass().getMethod("readTapeAsTag").invoke(record));
				}
				return List.copyOf(tags);
			}

			void addNativeMetrics(Map<String, Metric> metrics) throws Exception {
				add(metrics, "rust.stage.region_read", number(this.result, "regionReadNanos"), number(this.result, "compressedLength"));
				add(metrics, "rust.stage.decompression", number(this.result, "decompressionNanos"), number(this.result, "compressedLength"));
				add(metrics, "rust.stage.nbt_parse", number(this.result, "nbtParseNanos"), number(this.result, "decompressedLength"));
				add(metrics, "rust.stage.envelope_traversal", number(this.result, "envelopeTraversalNanos"), number(this.result, "decompressedLength"));
				add(metrics, "rust.stage.tape_creation", number(this.result, "tapeCreationNanos"), this.outputBytes);
				add(metrics, "rust.stage.output_copy", number(this.result, "rustOutputCopyNanos"), this.outputBytes);
				add(metrics, "rust.stage.ffi_total", number(this.result, "rustFfiTotalNanos"), this.outputBytes);
				add(metrics, "rust.java_arena", number(this.metrics, "javaArenaNanos"), 0L);
				add(metrics, "rust.java_output_allocation", number(this.metrics, "javaOutputAllocationNanos"), this.outputBytes);
				add(metrics, "rust.java_result_allocation", number(this.metrics, "javaResultAllocationNanos"), 0L);
				add(metrics, "rust.java_result_parse", number(this.metrics, "javaResultParseNanos"), 0L);
				add(metrics, "rust.java_wrapper_other", number(this.metrics, "javaWrapperOtherNanos"), 0L);
				metrics.computeIfAbsent("rust.copied_bytes", key -> new Metric()).add(0L, this.copiedBytes, this.copiedBytes, 0L, false);
				metrics.computeIfAbsent("rust.allocated_bytes", key -> new Metric()).add(0L, number(this.metrics, "javaAllocatedBytes"), 0L, 0L, false);
			}

			private static void add(Map<String, Metric> metrics, String name, long nanos, long bytes) {
				metrics.computeIfAbsent(name, key -> new Metric()).add(nanos, bytes);
			}

			private static long number(Object target, String method) throws Exception {
				return ((Number)target.getClass().getMethod(method).invoke(target)).longValue();
			}
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

		static <T> T time(Map<String, Metric> metrics, String name, long bytes, ThrowingSupplier<T> supplier) throws Exception {
			return time(metrics, name, bytes, bytes, supplier);
		}

		static void time(Map<String, Metric> metrics, String name, long bytes, ThrowingRunnable runnable) throws Exception {
			time(metrics, name, bytes, bytes, () -> {
				runnable.run();
				return null;
			});
		}

		static <T> T time(Map<String, Metric> metrics, String name, long bytes, long copiedBytes, ThrowingSupplier<T> supplier) throws Exception {
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
			java.lang.management.ThreadMXBean bean = java.lang.management.ManagementFactory.getThreadMXBean();
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
		T get() throws Exception;
	}

	@FunctionalInterface
	private interface ThrowingRunnable {
		void run() throws Exception;
	}

	private record LoadedEntities(List<Entity> entities, List<CompoundTag> saved) {
	}

	private record ValidationResult(LoadedEntities loaded, String savedFingerprint) {
	}

	private static final class FieldCollector {
		private final int limit;
		private final JsonArray fields = new JsonArray();
		private boolean truncated;

		private FieldCollector(int limit) {
			this.limit = limit;
		}

		void add(String path, String value) {
			if (this.fields.size() >= this.limit) {
				this.truncated = true;
				return;
			}
			JsonObject json = new JsonObject();
			json.addProperty("path", path);
			json.addProperty("value", value);
			this.fields.add(json);
		}
	}

	private static final class RegionUncheckedIOException extends RuntimeException {
		private final IOException exception;

		RegionUncheckedIOException(IOException exception) {
			this.exception = exception;
		}

		IOException unwrap() {
			return this.exception;
		}
	}

	private static String sha256Hex(byte[] bytes) {
		return HexFormat.of().formatHex(sha256().digest(bytes));
	}

	private static MessageDigest sha256() {
		try {
			return MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException(exception);
		}
	}
}
