package net.minecraft.world.level.chunk.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.DetectedVersion;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NativeNbtRegionAccess;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.ticks.SavedTick;
import net.minecraft.world.ticks.TickPriority;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class NativeChunkTickStorageTest {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final int CURRENT_DATA_VERSION = 4556;
	private static final RegionStorageInfo STORAGE_INFO = new RegionStorageInfo("test", null, "chunk-tick-test");

	@BeforeAll
	static void bootstrapMinecraft() {
		SharedConstants.setVersion(DetectedVersion.BUILT_IN);
		Bootstrap.bootStrap();
	}

	@Test
	void generatedScheduledTicksMatchJavaCodecs(@TempDir Path tempDir) throws IOException {
		Path regionPath = tempDir.resolve("r.0.0.mca");
		ChunkPos pos = new ChunkPos(-1, 2);
		CompoundTag root = chunkRoot(pos);
		ListTag blockTicks = new ListTag();
		blockTicks.add(tick("minecraft:stone", -16, 64, 32, 7, TickPriority.EXTREMELY_HIGH));
		blockTicks.add(tick("minecraft:dirt", -1, 65, 47, -2, TickPriority.EXTREMELY_LOW));
		blockTicks.add(tick("minecraft:grass_block", 0, 66, 32, 9, TickPriority.NORMAL));
		root.put("block_ticks", blockTicks);
		root.put("fluid_ticks", ticks("minecraft:water", -15, -12, 40, 1, TickPriority.VERY_LOW));
		writeJavaRegion(regionPath, tempDir, RegionFileVersion.VERSION_DEFLATE, pos, root);

		try (NativeChunkTickStorage storage = NativeChunkTickStorage.open(regionPath)) {
			NativeChunkTickStorage.DecodeResult decoded = storage.decodeChunk(pos.x, pos.z);

			assertEquals(NativeChunkTickStorage.OK, decoded.result().status());
			assertTrue(decoded.result().present());
			assertFalse(decoded.result().requiresDfu());
			assertEquals(CURRENT_DATA_VERSION, decoded.result().dataVersion());
			assertEquals(2, decoded.result().blockTickCount());
			assertEquals(1, decoded.result().fluidTickCount());
			assertTickRecords(expectedBlockTicks(root, pos), NativeChunkTickStorage.resolveBlockTicks(decoded.ticks()));
			assertTickRecords(expectedFluidTicks(root, pos), NativeChunkTickStorage.resolveFluidTicks(decoded.ticks()));
			assertEquals(
				List.of("minecraft:stone", "minecraft:dirt"),
				decoded.ticks().blockTicks().stream().map(NativeChunkTickStorage.TickRecord::id).toList()
			);
		}
	}

	@Test
	void broadGeneratedScheduledTickFixturesPreserveCodecSemantics(@TempDir Path tempDir) throws IOException {
		List<TickFixture> fixtures = generatedTickFixtures();
		int index = 0;
		for (TickFixture fixture : fixtures) {
			Path regionPath = tempDir.resolve("fixture-" + index++ + ".mca");
			writeJavaRegion(regionPath, tempDir, RegionFileVersion.VERSION_DEFLATE, fixture.pos(), fixture.root());

			try (NativeChunkTickStorage storage = NativeChunkTickStorage.open(regionPath)) {
				NativeChunkTickStorage.DecodeResult decoded = storage.decodeChunk(fixture.pos().x, fixture.pos().z);

				assertEquals(NativeChunkTickStorage.OK, decoded.result().status(), fixture.name());
				assertTrue(decoded.result().present(), fixture.name());
				assertFalse(decoded.result().requiresDfu(), fixture.name());
				assertTickRecords(expectedBlockTicks(fixture.root(), fixture.pos()), NativeChunkTickStorage.resolveBlockTicks(decoded.ticks()));
				assertTickRecords(expectedFluidTicks(fixture.root(), fixture.pos()), NativeChunkTickStorage.resolveFluidTicks(decoded.ticks()));
			}
		}
	}

	@Test
	void unifiedChunkSectionDecodeCarriesScheduledTickTape(@TempDir Path tempDir) throws IOException {
		Path regionPath = tempDir.resolve("r.0.0.mca");
		ChunkPos pos = new ChunkPos(0, 0);
		CompoundTag root = chunkRoot(pos);
		root.put("block_ticks", ticks("minecraft:stone", 0, 64, 0, 3, TickPriority.HIGH));
		root.put("fluid_ticks", ticks("minecraft:water", 1, 64, 1, 4, TickPriority.LOW));
		writeJavaRegion(regionPath, tempDir, RegionFileVersion.VERSION_DEFLATE, pos, root);

		try (NativeChunkSectionStorage storage = NativeChunkSectionStorage.open(regionPath)) {
			NativeChunkSectionStorage.DecodeResult decoded = storage.decodeChunk(pos.x, pos.z);

			assertEquals(NativeChunkSectionStorage.OK, decoded.result().status());
			assertTrue(decoded.chunk().scheduledTicks().isPresent());
			assertEquals(1, decoded.result().blockTickCount());
			assertEquals(1, decoded.result().fluidTickCount());
			assertTickRecords(expectedBlockTicks(root, pos), NativeChunkTickStorage.resolveBlockTicks(decoded.chunk().scheduledTicks()));
			assertTickRecords(expectedFluidTicks(root, pos), NativeChunkTickStorage.resolveFluidTicks(decoded.chunk().scheduledTicks()));
		}
	}

	@Test
	void rustScheduledTickWriteReopensThroughJavaCodec(@TempDir Path tempDir) throws IOException {
		Path regionPath = tempDir.resolve("rust-ticks.mca");
		ChunkPos pos = new ChunkPos(2, -3);
		CompoundTag residual = chunkRoot(pos);
		residual.put("block_ticks", ticks("minecraft:air", 32, 64, -48, 1, TickPriority.NORMAL));
		residual.put("fluid_ticks", new ListTag());
		residual.put("mattmc:custom", StringTag.valueOf("preserved"));
		NativeChunkTickStorage.TickData ticks = new NativeChunkTickStorage.TickData(
			CURRENT_DATA_VERSION,
			pos.x,
			pos.z,
			List.of(
				new NativeChunkTickStorage.TickRecord("minecraft:stone", 33, 70, -47, 5, TickPriority.HIGH.getValue()),
				new NativeChunkTickStorage.TickRecord("minecraft:oak_sapling", 47, -20, -33, 0, TickPriority.LOW.getValue())
			),
			List.of(new NativeChunkTickStorage.TickRecord("minecraft:water", 34, -5, -33, 6, TickPriority.VERY_LOW.getValue()))
		);

		try (NativeChunkTickStorage storage = NativeChunkTickStorage.open(regionPath)) {
			NativeChunkTickStorage.WriteResult result = storage.writeChunk(pos.x, pos.z, RegionFileVersion.VERSION_DEFLATE.getId(), residual, ticks);
			assertEquals(NativeChunkTickStorage.OK, result.status());
			assertTrue(result.present());
			assertTrue(result.tickTapeLength() > 0L);
			assertTrue(result.residualTapeLength() > 0L);
		}

		CompoundTag read = readJavaRegion(regionPath, pos);
		assertEquals("preserved", read.getStringOr("mattmc:custom", ""));
		assertTickRecords(NativeChunkTickStorage.resolveBlockTicks(ticks), expectedBlockTicks(read, pos));
		assertTickRecords(NativeChunkTickStorage.resolveFluidTicks(ticks), expectedFluidTicks(read, pos));
	}

	@Test
	void oldVersionsRequireDfuAndMalformedTicksFailSafely(@TempDir Path tempDir) throws IOException {
		Path oldRegion = tempDir.resolve("old.mca");
		ChunkPos pos = new ChunkPos(0, 0);
		CompoundTag old = chunkRoot(pos);
		old.putInt("DataVersion", CURRENT_DATA_VERSION - 1);
		writeJavaRegion(oldRegion, tempDir, RegionFileVersion.VERSION_DEFLATE, pos, old);
		try (NativeChunkTickStorage storage = NativeChunkTickStorage.open(oldRegion)) {
			NativeChunkTickStorage.DecodeResult decoded = storage.decodeChunk(pos.x, pos.z);
			assertEquals(NativeChunkTickStorage.OK, decoded.result().status());
			assertTrue(decoded.result().requiresDfu());
			assertTrue(decoded.ticks().blockTicks().isEmpty());
		}

		Path malformedRegion = tempDir.resolve("malformed.mca");
		CompoundTag malformed = chunkRoot(pos);
		ListTag blockTicks = new ListTag();
		CompoundTag tick = new CompoundTag();
		tick.putString("i", "minecraft:stone");
		tick.putInt("x", 0);
		tick.putInt("y", 64);
		tick.putInt("z", 0);
		tick.putInt("t", 1);
		tick.putString("p", "normal");
		blockTicks.add(tick);
		malformed.put("block_ticks", blockTicks);
		writeJavaRegion(malformedRegion, tempDir, RegionFileVersion.VERSION_DEFLATE, pos, malformed);
		try (NativeChunkTickStorage storage = NativeChunkTickStorage.open(malformedRegion)) {
			assertThrows(NativeChunkTickStorage.DecodeException.class, () -> storage.decodeChunk(pos.x, pos.z));
		}

		Path missingFieldRegion = tempDir.resolve("missing-field.mca");
		CompoundTag missingField = chunkRoot(pos);
		ListTag missingFieldTicks = new ListTag();
		CompoundTag missing = new CompoundTag();
		missing.putString("i", "minecraft:stone");
		missing.putInt("x", 0);
		missing.putInt("y", 64);
		missing.putInt("z", 0);
		missing.putInt("t", 1);
		missingFieldTicks.add(missing);
		missingField.put("block_ticks", missingFieldTicks);
		writeJavaRegion(missingFieldRegion, tempDir, RegionFileVersion.VERSION_DEFLATE, pos, missingField);
		try (NativeChunkTickStorage storage = NativeChunkTickStorage.open(missingFieldRegion)) {
			assertThrows(NativeChunkTickStorage.DecodeException.class, () -> storage.decodeChunk(pos.x, pos.z));
		}
	}

	@Test
	void unknownIdsAreRejectedByJavaResolutionWithoutFailingChunkDecode(@TempDir Path tempDir) throws IOException {
		Path regionPath = tempDir.resolve("unknown.mca");
		ChunkPos pos = new ChunkPos(0, 0);
		CompoundTag root = chunkRoot(pos);
		root.put("block_ticks", ticks("mattmc:missing_block", 0, 64, 0, 1, TickPriority.NORMAL));
		writeJavaRegion(regionPath, tempDir, RegionFileVersion.VERSION_DEFLATE, pos, root);

		try (NativeChunkTickStorage storage = NativeChunkTickStorage.open(regionPath)) {
			NativeChunkTickStorage.DecodeResult decoded = storage.decodeChunk(pos.x, pos.z);
			assertEquals(NativeChunkTickStorage.OK, decoded.result().status());
			assertThrows(IOException.class, () -> NativeChunkTickStorage.resolveBlockTicks(decoded.ticks()));
		}
	}

	@Test
	void copiedOriginChunkScheduledTicksMatchJavaWhenAvailable(@TempDir Path tempDir) throws IOException {
		Path regionDir = Path.of("run", "saves", "Origin", "region");
		assumeTrue(Files.isDirectory(regionDir), "Origin region directory is not available");
		RegionChunk sourceChunk = firstPresentOriginChunk(regionDir);
		assumeTrue(sourceChunk != null, "Origin region files have no populated chunks");
		Path copy = tempDir.resolve("r.0.0.mca");
		Files.copy(sourceChunk.regionPath(), copy);
		ChunkPos pos = new ChunkPos(sourceChunk.regionX() * 32 + sourceChunk.localX(), sourceChunk.regionZ() * 32 + sourceChunk.localZ());
		CompoundTag javaRoot = readJavaRegion(copy, pos);
		assumeTrue(javaRoot.getInt("DataVersion").orElse(0) == CURRENT_DATA_VERSION, "Origin chunk remains Java DFU-owned");

		try (NativeChunkTickStorage storage = NativeChunkTickStorage.open(copy)) {
			NativeChunkTickStorage.DecodeResult decoded = storage.decodeChunk(pos.x, pos.z);

			assertEquals(NativeChunkTickStorage.OK, decoded.result().status());
			assertTrue(decoded.result().present());
			assertFalse(decoded.result().requiresDfu());
			assertTickRecords(expectedBlockTicks(javaRoot, pos), NativeChunkTickStorage.resolveBlockTicks(decoded.ticks()));
			assertTickRecords(expectedFluidTicks(javaRoot, pos), NativeChunkTickStorage.resolveFluidTicks(decoded.ticks()));
		}
	}

	@Test
	void copiedOriginChunkWithNaturalScheduledTicksMatchesJavaWhenAvailable(@TempDir Path tempDir) throws IOException {
		Path regionDir = Path.of("run", "saves", "Origin", "region");
		assumeTrue(Files.isDirectory(regionDir), "Origin region directory is not available");
		Optional<RegionChunk> maybeSourceChunk = firstOriginChunkWithScheduledTicks(regionDir);
		assumeTrue(maybeSourceChunk.isPresent(), "Origin has no naturally persisted scheduled ticks");
		RegionChunk sourceChunk = maybeSourceChunk.get();
		Path copy = tempDir.resolve(sourceChunk.regionPath().getFileName().toString());
		Files.copy(sourceChunk.regionPath(), copy);
		ChunkPos pos = new ChunkPos(sourceChunk.regionX() * 32 + sourceChunk.localX(), sourceChunk.regionZ() * 32 + sourceChunk.localZ());
		CompoundTag javaRoot = readJavaRegion(copy, pos);

		try (NativeChunkTickStorage storage = NativeChunkTickStorage.open(copy)) {
			NativeChunkTickStorage.DecodeResult decoded = storage.decodeChunk(pos.x, pos.z);

			assertEquals(NativeChunkTickStorage.OK, decoded.result().status());
			assertTickRecords(expectedBlockTicks(javaRoot, pos), NativeChunkTickStorage.resolveBlockTicks(decoded.ticks()));
			assertTickRecords(expectedFluidTicks(javaRoot, pos), NativeChunkTickStorage.resolveFluidTicks(decoded.ticks()));
		}
	}

	@Test
	void benchmarkScheduledTickPaths(@TempDir Path tempDir) throws Exception {
		assumeTrue(Boolean.getBoolean("mattmc.runChunkTickBenchmark"), "chunk tick benchmark is opt-in");
		List<TickFixture> fixtures = new ArrayList<>(generatedTickFixtures());
		firstOriginChunkWithScheduledTicks(Path.of("run", "saves", "Origin", "region")).ifPresentOrElse(
			sourceChunk -> {
				try {
					Path copy = tempDir.resolve("origin-" + sourceChunk.regionPath().getFileName());
					Files.copy(sourceChunk.regionPath(), copy);
					ChunkPos pos = new ChunkPos(sourceChunk.regionX() * 32 + sourceChunk.localX(), sourceChunk.regionZ() * 32 + sourceChunk.localZ());
					CompoundTag root = readJavaRegion(copy, pos);
					fixtures.add(new TickFixture("origin-natural", pos, root, copy));
				} catch (IOException exception) {
					throw new RuntimeException(exception);
				}
			},
			() -> {
			}
		);
		for (int i = 0; i < fixtures.size(); i++) {
			TickFixture fixture = fixtures.get(i);
			if (fixture.regionPath() == null) {
				Path regionPath = tempDir.resolve("benchmark-" + i + ".mca");
				writeJavaRegion(regionPath, tempDir, RegionFileVersion.VERSION_DEFLATE, fixture.pos(), fixture.root());
				fixtures.set(i, fixture.withRegionPath(regionPath));
			}
		}

		int warmup = Integer.getInteger("mattmc.chunkTickBenchmark.warmup", 8);
		int measure = Integer.getInteger("mattmc.chunkTickBenchmark.measure", 24);
		Path output = Path.of(System.getProperty("mattmc.chunkTickBenchmark.output", "build/chunk-tick-benchmark/chunk_tick_benchmark.json"));
		JsonObject report = new JsonObject();
		report.addProperty("schema", "mattmc-chunk-tick-benchmark-v1");
		report.addProperty("fixtures", fixtures.size());
		report.addProperty("warmupIterations", warmup);
		report.addProperty("measureIterations", measure);
		report.add("rustNative", rustNativeIdentity());
		JsonArray samples = new JsonArray();
		samples.add(runBenchmarkIteration("cold", 0, fixtures, tempDir));
		for (int i = 0; i < warmup; i++) {
			samples.add(runBenchmarkIteration("warmup", i, fixtures, tempDir));
		}
		for (int i = 0; i < measure; i++) {
			samples.add(runBenchmarkIteration("measure", i, fixtures, tempDir));
		}
		report.add("samples", samples);
		report.add("measureSummary", summarizeMeasures(samples));
		Files.createDirectories(output.toAbsolutePath().getParent());
		Files.writeString(output, GSON.toJson(report));
		System.out.println("chunk tick benchmark: " + output.toAbsolutePath());
	}

	private static List<SavedTick<Block>> expectedBlockTicks(CompoundTag root, ChunkPos pos) {
		return SavedTick.filterTickListForChunk(root.read("block_ticks", SavedTick.codec(BuiltInRegistries.BLOCK.byNameCodec()).listOf()).orElse(List.of()), pos);
	}

	private static List<SavedTick<Fluid>> expectedFluidTicks(CompoundTag root, ChunkPos pos) {
		return SavedTick.filterTickListForChunk(root.read("fluid_ticks", SavedTick.codec(BuiltInRegistries.FLUID.byNameCodec()).listOf()).orElse(List.of()), pos);
	}

	private static JsonObject runBenchmarkIteration(String phase, int index, List<TickFixture> fixtures, Path tempDir) throws Exception {
		JsonObject sample = new JsonObject();
		sample.addProperty("phase", phase);
		sample.addProperty("index", index);
		Metric javaDecode = new Metric();
		Metric javaEncode = new Metric();
		Metric rustDecodeTape = new Metric();
		Metric rustRegistryResolve = new Metric();
		Metric rustResidualTapeEncode = new Metric();
		Metric rustTickTapeEncode = new Metric();
		Metric rustWrite = new Metric();
		Metric javaTotalRead = new Metric();
		Metric rustTotalRead = new Metric();
		Metric javaTotalWrite = new Metric();
		Metric rustTotalWrite = new Metric();
		Map<Path, NativeChunkTickStorage> open = new HashMap<>();
		try {
			int fixtureIndex = 0;
			for (TickFixture fixture : fixtures) {
				long started = System.nanoTime();
				ChunkAccess.PackedTicks javaTicks = SerializableChunkData.parseJavaPackedTicks(fixture.root(), fixture.pos());
				long javaDecodeNanos = System.nanoTime() - started;
				int tickCount = javaTicks.blocks().size() + javaTicks.fluids().size();
				javaDecode.add(javaDecodeNanos, tickCount);
				javaTotalRead.add(javaDecodeNanos, tickCount);

				started = System.nanoTime();
				CompoundTag javaWritten = fixture.root().copy();
				javaWritten.store("block_ticks", SavedTick.codec(BuiltInRegistries.BLOCK.byNameCodec()).listOf(), javaTicks.blocks());
				javaWritten.store("fluid_ticks", SavedTick.codec(BuiltInRegistries.FLUID.byNameCodec()).listOf(), javaTicks.fluids());
				long javaEncodeNanos = System.nanoTime() - started;
				javaEncode.add(javaEncodeNanos, tickCount);
				javaTotalWrite.add(javaEncodeNanos, tickCount);

				NativeChunkTickStorage storage = open.computeIfAbsent(fixture.regionPath(), path -> {
					try {
						return NativeChunkTickStorage.open(path);
					} catch (IOException exception) {
						throw new RuntimeException(exception);
					}
				});
				started = System.nanoTime();
				NativeChunkTickStorage.DecodeResult decoded = storage.decodeChunk(fixture.pos().x, fixture.pos().z);
				long rustDecodeNanos = System.nanoTime() - started;
				rustDecodeTape.add(rustDecodeNanos, decoded.result().compressedLength(), decoded.result().outputLength());

				started = System.nanoTime();
				List<SavedTick<Block>> rustBlocks = NativeChunkTickStorage.resolveBlockTicks(decoded.ticks());
				List<SavedTick<Fluid>> rustFluids = NativeChunkTickStorage.resolveFluidTicks(decoded.ticks());
				long rustResolveNanos = System.nanoTime() - started;
				rustRegistryResolve.add(rustResolveNanos, tickCount);
				rustTotalRead.add(rustDecodeNanos + rustResolveNanos, decoded.result().compressedLength(), decoded.result().outputLength());
				assertTickRecords(javaTicks.blocks(), rustBlocks);
				assertTickRecords(javaTicks.fluids(), rustFluids);

				NativeChunkTickStorage.TickData tickData = NativeChunkTickStorage.fromSavedTicks(CURRENT_DATA_VERSION, fixture.pos(), javaTicks.blocks(), javaTicks.fluids());
				started = System.nanoTime();
				byte[] residualTape = NativeNbtRegionAccess.writeTape(fixture.root());
				long rustResidualTapeNanos = System.nanoTime() - started;
				rustResidualTapeEncode.add(rustResidualTapeNanos, estimatedTickInputBytes(tickCount), residualTape.length);
				started = System.nanoTime();
				byte[] tickTape = NativeChunkTickStorage.encodeTickTape(tickData);
				long rustTickTapeNanos = System.nanoTime() - started;
				rustTickTapeEncode.add(rustTickTapeNanos, tickCount, tickTape.length);

				Path writeRegion = tempDir.resolve("benchmark-write-" + phase + "-" + index + "-" + fixtureIndex++ + ".mca");
				started = System.nanoTime();
				try (NativeChunkTickStorage writer = NativeChunkTickStorage.open(writeRegion)) {
					NativeChunkTickStorage.WriteResult result = writer.writeChunkTapes(
						fixture.pos().x,
						fixture.pos().z,
						RegionFileVersion.VERSION_DEFLATE.getId(),
						residualTape,
						tickTape
					);
					rustWrite.add(System.nanoTime() - started, result.residualTapeLength(), result.tickTapeLength());
					rustTotalWrite.add(rustResidualTapeNanos + rustTickTapeNanos + rustWrite.nanosSinceLastAdd(), result.residualTapeLength(), result.tickTapeLength());
				}
				CompoundTag rustWritten = readJavaRegion(writeRegion, fixture.pos());
				assertTickRecords(javaTicks.blocks(), expectedBlockTicks(rustWritten, fixture.pos()));
				assertTickRecords(javaTicks.fluids(), expectedFluidTicks(rustWritten, fixture.pos()));
			}
		} finally {
			for (NativeChunkTickStorage storage : open.values()) {
				storage.close();
			}
		}
		JsonObject metrics = new JsonObject();
		metrics.add("java_tick_codec_decode", javaDecode.toJson());
		metrics.add("java_tick_codec_encode", javaEncode.toJson());
		metrics.add("rust_tick_decode_tape_wall", rustDecodeTape.toJson());
		metrics.add("rust_tick_registry_resolution", rustRegistryResolve.toJson());
		metrics.add("rust_residual_tape_encode", rustResidualTapeEncode.toJson());
		metrics.add("rust_tick_tape_encode", rustTickTapeEncode.toJson());
		metrics.add("rust_tick_region_write_wall", rustWrite.toJson());
		metrics.add("java_total_tick_read", javaTotalRead.toJson());
		metrics.add("rust_total_tick_read", rustTotalRead.toJson());
		metrics.add("java_total_tick_write", javaTotalWrite.toJson());
		metrics.add("rust_total_tick_write", rustTotalWrite.toJson());
		sample.add("metrics", metrics);
		return sample;
	}

	private static JsonObject summarizeMeasures(JsonArray samples) {
		Map<String, Metric> totals = new HashMap<>();
		for (int i = 0; i < samples.size(); i++) {
			JsonObject sample = samples.get(i).getAsJsonObject();
			if (!"measure".equals(sample.get("phase").getAsString())) {
				continue;
			}
			JsonObject metrics = sample.getAsJsonObject("metrics");
			for (String name : metrics.keySet()) {
				JsonObject metric = metrics.getAsJsonObject(name);
				totals.computeIfAbsent(name, ignored -> new Metric())
					.add(metric.get("nanos").getAsLong(), metric.get("inputBytes").getAsLong(), metric.get("copiedBytes").getAsLong());
			}
		}
		JsonObject summary = new JsonObject();
		totals.keySet().stream().sorted().forEach(key -> summary.add(key, totals.get(key).toJson()));
		if (totals.containsKey("java_total_tick_read") && totals.containsKey("rust_total_tick_read")) {
			double javaNanos = totals.get("java_total_tick_read").nanos;
			double rustNanos = totals.get("rust_total_tick_read").nanos;
			summary.addProperty("rustToJavaReadRatio", javaNanos == 0.0 ? 0.0 : rustNanos / javaNanos);
		}
		if (totals.containsKey("java_total_tick_write") && totals.containsKey("rust_total_tick_write")) {
			double javaNanos = totals.get("java_total_tick_write").nanos;
			double rustNanos = totals.get("rust_total_tick_write").nanos;
			summary.addProperty("rustToJavaWriteRatio", javaNanos == 0.0 ? 0.0 : rustNanos / javaNanos);
		}
		return summary;
	}

	private static List<TickFixture> generatedTickFixtures() {
		List<TickFixture> fixtures = new ArrayList<>();
		ChunkPos empty = new ChunkPos(0, 0);
		CompoundTag emptyRoot = chunkRoot(empty);
		emptyRoot.put("block_ticks", new ListTag());
		emptyRoot.put("fluid_ticks", new ListTag());
		fixtures.add(new TickFixture("empty-full", empty, emptyRoot, null));

		ChunkPos many = new ChunkPos(-2, -1);
		CompoundTag manyRoot = chunkRoot(many);
		manyRoot.put("block_ticks", tickList(
			tick("minecraft:stone", -32, -64, -16, 0, TickPriority.EXTREMELY_HIGH),
			tick("minecraft:dirt", -31, 0, -15, 1, TickPriority.VERY_HIGH),
			tick("minecraft:grass_block", -30, 64, -14, 2, TickPriority.HIGH),
			tick("minecraft:oak_sapling", -29, 255, -13, Integer.MAX_VALUE, TickPriority.NORMAL),
			tick("minecraft:water", -28, -32, -12, 4, TickPriority.LOW),
			tick("minecraft:lava", -27, 12, -11, 5, TickPriority.VERY_LOW),
			tick("minecraft:sand", -26, 320, -10, 6, TickPriority.EXTREMELY_LOW),
			tick("minecraft:stone", -32, -64, -16, 7, TickPriority.LOW),
			tick("minecraft:stone", -17, 384, -1, 8, TickPriority.HIGH),
			tick("minecraft:stone", -16, 64, -16, 9, TickPriority.NORMAL)
		));
		manyRoot.put("fluid_ticks", tickList(
			tick("minecraft:water", -32, -64, -16, 10, TickPriority.EXTREMELY_HIGH),
			tick("minecraft:flowing_water", -25, 63, -9, 11, TickPriority.NORMAL),
			tick("minecraft:lava", -17, 384, -1, 12, TickPriority.EXTREMELY_LOW),
			tick("minecraft:water", -33, 70, -16, 13, TickPriority.LOW)
		));
		fixtures.add(new TickFixture("many-priorities-boundaries-filtered", many, manyRoot, null));

		ChunkPos proto = new ChunkPos(1, -3);
		CompoundTag protoRoot = chunkRoot(proto);
		protoRoot.putString("Status", "minecraft:features");
		protoRoot.put("block_ticks", tickList(
			tick("minecraft:oak_sapling", 16, 70, -48, 2000000000, TickPriority.LOW),
			tick("minecraft:oak_sapling", 16, 70, -48, 2000000001, TickPriority.HIGH)
		));
		protoRoot.put("fluid_ticks", tickList(tick("minecraft:flowing_water", 31, 70, -33, 3, TickPriority.VERY_LOW)));
		fixtures.add(new TickFixture("proto-duplicate-position-order", proto, protoRoot, null));
		return List.copyOf(fixtures);
	}

	private static void assertTickRecords(List<? extends SavedTick<?>> expected, List<? extends SavedTick<?>> actual) {
		assertEquals(expected.size(), actual.size());
		for (int i = 0; i < expected.size(); i++) {
			SavedTick<?> left = expected.get(i);
			SavedTick<?> right = actual.get(i);
			assertEquals(left.type(), right.type(), "type at " + i);
			assertEquals(left.pos(), right.pos(), "position at " + i);
			assertEquals(left.delay(), right.delay(), "delay at " + i);
			assertEquals(left.priority(), right.priority(), "priority at " + i);
		}
	}

	private static CompoundTag chunkRoot(ChunkPos pos) {
		CompoundTag root = new CompoundTag();
		root.putInt("DataVersion", CURRENT_DATA_VERSION);
		root.putInt("xPos", pos.x);
		root.putInt("zPos", pos.z);
		root.putInt("yPos", -4);
		root.putString("Status", "minecraft:full");
		root.putBoolean("isLightOn", true);
		root.putLong("LastUpdate", 123L);
		root.putLong("InhabitedTime", 456L);
		root.put("sections", new ListTag());
		root.put("Heightmaps", new CompoundTag());
		root.put("block_entities", new ListTag());
		root.put("structures", new CompoundTag());
		return root;
	}

	private static ListTag ticks(String id, int x, int y, int z, int delay, TickPriority priority) {
		ListTag ticks = new ListTag();
		ticks.add(tick(id, x, y, z, delay, priority));
		return ticks;
	}

	private static ListTag tickList(CompoundTag... values) {
		ListTag ticks = new ListTag();
		for (CompoundTag value : values) {
			ticks.add(value);
		}
		return ticks;
	}

	private static CompoundTag tick(String id, int x, int y, int z, int delay, TickPriority priority) {
		CompoundTag tick = new CompoundTag();
		tick.putString("i", id);
		tick.putInt("x", x);
		tick.putInt("y", y);
		tick.putInt("z", z);
		tick.putInt("t", delay);
		tick.putInt("p", priority.getValue());
		return tick;
	}

	private static void writeJavaRegion(Path regionPath, Path dir, RegionFileVersion version, ChunkPos pos, CompoundTag tag) throws IOException {
		try (RegionFile regionFile = new RegionFile(STORAGE_INFO, regionPath, dir, version, false);
			DataOutputStream output = regionFile.getChunkDataOutputStream(pos)) {
			NbtIo.write(tag, output);
		}
	}

	private static CompoundTag readJavaRegion(Path regionPath, ChunkPos pos) throws IOException {
		try (RegionFile regionFile = new RegionFile(STORAGE_INFO, regionPath, regionPath.getParent(), false);
			InputStream input = regionFile.getChunkDataInputStream(pos)) {
			assertNotNull(input);
			return NbtIo.read(new java.io.DataInputStream(input));
		}
	}

	private static RegionChunk firstPresentOriginChunk(Path regionDir) throws IOException {
		try (Stream<Path> paths = Files.list(regionDir)) {
			for (Path path : paths.filter(path -> path.getFileName().toString().endsWith(".mca")).sorted(Comparator.naturalOrder()).toList()) {
				ChunkPos pos = firstPresentChunk(path);
				if (pos != null) {
					String[] parts = path.getFileName().toString().split("\\.");
					int regionX = Integer.parseInt(parts[1]);
					int regionZ = Integer.parseInt(parts[2]);
					return new RegionChunk(path, regionX, regionZ, pos.x, pos.z);
				}
			}
		}
		return null;
	}

	private static ChunkPos firstPresentChunk(Path regionPath) throws IOException {
		byte[] bytes = Files.readAllBytes(regionPath);
		for (int i = 0; i < 1024 && i * 4 + 3 < bytes.length; i++) {
			if (readInt(bytes, i * 4) != 0) {
				return new ChunkPos(i & 31, i >> 5);
			}
		}
		return null;
	}

	private static int readInt(byte[] bytes, int offset) {
		return (bytes[offset] & 0xFF) << 24
			| (bytes[offset + 1] & 0xFF) << 16
			| (bytes[offset + 2] & 0xFF) << 8
			| bytes[offset + 3] & 0xFF;
	}

	private static Optional<RegionChunk> firstOriginChunkWithScheduledTicks(Path regionDir) throws IOException {
		if (!Files.isDirectory(regionDir)) {
			return Optional.empty();
		}
		try (Stream<Path> paths = Files.list(regionDir)) {
			for (Path path : paths.filter(path -> path.getFileName().toString().endsWith(".mca")).sorted(Comparator.naturalOrder()).toList()) {
				String[] parts = path.getFileName().toString().split("\\.");
				int regionX = Integer.parseInt(parts[1]);
				int regionZ = Integer.parseInt(parts[2]);
				byte[] bytes = Files.readAllBytes(path);
				for (int i = 0; i < 1024 && i * 4 + 3 < bytes.length; i++) {
					if (readInt(bytes, i * 4) == 0) {
						continue;
					}
					ChunkPos pos = new ChunkPos(regionX * 32 + (i & 31), regionZ * 32 + (i >> 5));
					CompoundTag root = readJavaRegion(path, pos);
					if (root.getInt("DataVersion").orElse(0) == CURRENT_DATA_VERSION
						&& (!expectedBlockTicks(root, pos).isEmpty() || !expectedFluidTicks(root, pos).isEmpty())) {
						return Optional.of(new RegionChunk(path, regionX, regionZ, i & 31, i >> 5));
					}
				}
			}
		}
		return Optional.empty();
	}

	private static JsonObject rustNativeIdentity() throws IOException, NoSuchAlgorithmException {
		JsonObject object = new JsonObject();
		String nativesDir = System.getProperty("mattmc.rust.natives.dir", "natives");
		Path dll = Path.of(nativesDir).resolve(NativeLibraryLoader.platformLibraryFileName("mattmc_rust")).toAbsolutePath().normalize();
		object.addProperty("path", dll.toString());
		object.addProperty("sha256", Files.isRegularFile(dll) ? sha256(dll) : "");
		object.addProperty("size", Files.isRegularFile(dll) ? Files.size(dll) : 0L);
		object.addProperty("profileProperty", System.getProperty("mattmcRustProfile", ""));
		return object;
	}

	private static String sha256(Path path) throws IOException, NoSuchAlgorithmException {
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		digest.update(Files.readAllBytes(path));
		return HexFormat.of().formatHex(digest.digest());
	}

	private static long estimatedTickInputBytes(int tickCount) {
		return Math.max(1, tickCount) * 32L;
	}

	private static final class Metric {
		private long nanos;
		private long inputBytes;
		private long copiedBytes;
		private long lastAddedNanos;

		void add(long nanos, long inputBytes) {
			this.add(nanos, inputBytes, 0L);
		}

		void add(long nanos, long inputBytes, long copiedBytes) {
			this.nanos += nanos;
			this.inputBytes += inputBytes;
			this.copiedBytes += copiedBytes;
			this.lastAddedNanos = nanos;
		}

		long nanosSinceLastAdd() {
			return this.lastAddedNanos;
		}

		JsonObject toJson() {
			JsonObject object = new JsonObject();
			object.addProperty("nanos", this.nanos);
			object.addProperty("millis", this.nanos / 1_000_000.0);
			object.addProperty("inputBytes", this.inputBytes);
			object.addProperty("copiedBytes", this.copiedBytes);
			return object;
		}
	}

	private record RegionChunk(Path regionPath, int regionX, int regionZ, int localX, int localZ) {
	}

	private record TickFixture(String name, ChunkPos pos, CompoundTag root, Path regionPath) {
		private TickFixture withRegionPath(Path regionPath) {
			return new TickFixture(this.name, this.pos, this.root, regionPath);
		}
	}
}
