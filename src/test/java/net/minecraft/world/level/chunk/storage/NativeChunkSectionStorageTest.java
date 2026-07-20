package net.minecraft.world.level.chunk.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.Lifecycle;
import it.unimi.dsi.fastutil.shorts.ShortList;
import net.minecraft.DetectedVersion;
import net.minecraft.SharedConstants;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class NativeChunkSectionStorageTest {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final int CURRENT_DATA_VERSION = 4556;
	private static final RegionStorageInfo STORAGE_INFO = new RegionStorageInfo("test", null, "chunk-section-test");
	private static final LevelHeightAccessor VANILLA_HEIGHT = LevelHeightAccessor.create(-64, 384);

	@BeforeAll
	static void bootstrapMinecraft() {
		SharedConstants.setVersion(DetectedVersion.BUILT_IN);
		Bootstrap.bootStrap();
	}

	@Test
	void generatedCurrentChunkSectionsMatchJavaParser(@TempDir Path tempDir) throws IOException {
		Path regionPath = tempDir.resolve("r.0.0.mca");
		ChunkPos pos = new ChunkPos(2, 3);
		CompoundTag root = chunkRoot(
			pos,
			section(
				0,
				blockStates(List.of(blockState("minecraft:air"), blockState("minecraft:stone")), new long[256]),
				biomes(List.of("minecraft:plains", "minecraft:forest"), new long[1]),
				bytes(2048, 1),
				bytes(2048, 2)
			),
			section(
				-4,
				blockStates(List.of(blockState("minecraft:air")), null),
				biomes(List.of("minecraft:plains"), null),
				null,
				null
			)
		);
		root.put("Heightmaps", heightmaps("MOTION_BLOCKING", longs(37, 7L)));
		writeJavaRegion(regionPath, tempDir, RegionFileVersion.VERSION_DEFLATE, pos, root);

		SerializableChunkData parsed = SerializableChunkData.parse(VANILLA_HEIGHT, palettedContainerFactory(root), root);
		assertNotNull(parsed);

		try (NativeChunkSectionStorage storage = NativeChunkSectionStorage.open(regionPath)) {
			NativeChunkSectionStorage.DecodeResult rust = storage.decodeChunk(pos.x, pos.z);

			assertEquals(NativeChunkSectionStorage.OK, rust.result().status());
			assertTrue(rust.result().present());
			assertFalse(rust.result().requiresDfu());
			assertEquals(CURRENT_DATA_VERSION, rust.result().dataVersion());
			assertEquals(pos.x, rust.result().chunkX());
			assertEquals(pos.z, rust.result().chunkZ());
			assertEquals("minecraft:full", rust.chunk().status());
			assertTrue(rust.chunk().lightOn());
			assertEquals(parsed.sectionData().stream().map(SerializableChunkData.SectionData::y).toList(), rust.chunk().sections().stream().map(NativeChunkSectionStorage.Section::sectionY).toList());
			assertEquals(2, rust.chunk().sections().size());
			assertSectionMatchesRoot(root, rust.chunk().sections().get(0), 0);
			assertSectionMatchesRoot(root, rust.chunk().sections().get(1), 1);
			assertEquals(1, rust.chunk().heightmaps().size());
			assertEquals("MOTION_BLOCKING", rust.chunk().heightmaps().getFirst().name());
			assertArrayEquals(longs(37, 7L), rust.chunk().heightmaps().getFirst().data());
		}
	}

	@Test
	void typedRustSectionsCanBackSerializableChunkData(@TempDir Path tempDir) throws IOException {
		Path regionPath = tempDir.resolve("r.0.0.mca");
		ChunkPos pos = new ChunkPos(2, 3);
		CompoundTag root = chunkRoot(
			pos,
			section(
				0,
				blockStates(List.of(blockState("minecraft:air"), blockState("minecraft:stone")), new long[256]),
				biomes(List.of("minecraft:plains", "minecraft:forest"), new long[1]),
				bytes(2048, 3),
				bytes(2048, 4)
			)
		);
		root.put("Heightmaps", heightmaps("MOTION_BLOCKING", longs(37, 9L)));
		writeJavaRegion(regionPath, tempDir, RegionFileVersion.VERSION_DEFLATE, pos, root);
		RegistryAccess registryAccess = registryAccess(root);
		PalettedContainerFactory factory = PalettedContainerFactory.create(registryAccess);

		try (NativeChunkSectionStorage storage = NativeChunkSectionStorage.open(regionPath)) {
			NativeChunkSectionStorage.DecodeResult rust = storage.decodeChunk(pos.x, pos.z);
			SerializableChunkData parsed = SerializableChunkData.parseWithRustSections(
				registryAccess,
				VANILLA_HEIGHT,
				factory,
				root,
				Optional.of(rust),
				pos
			);

			assertNotNull(parsed);
			assertEquals(1, parsed.sectionData().size());
			SerializableChunkData.SectionData section = parsed.sectionData().getFirst();
			assertEquals(0, section.y());
			assertNotNull(section.chunkSection());
			assertArrayEquals(bytes(2048, 3), section.blockLight().getData());
			assertArrayEquals(bytes(2048, 4), section.skyLight().getData());
			assertArrayEquals(longs(37, 9L), parsed.heightmaps().get(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING));
		}
	}

	@Test
	void absentChunkReportsNotPresent(@TempDir Path tempDir) throws IOException {
		Path regionPath = tempDir.resolve("r.0.0.mca");
		ChunkPos pos = new ChunkPos(0, 0);
		writeJavaRegion(regionPath, tempDir, RegionFileVersion.VERSION_NONE, pos, chunkRoot(pos));

		try (NativeChunkSectionStorage storage = NativeChunkSectionStorage.open(regionPath)) {
			NativeChunkSectionStorage.DecodeResult rust = storage.decodeChunk(1, 0);

			assertEquals(NativeChunkSectionStorage.OK, rust.result().status());
			assertFalse(rust.result().present());
			assertTrue(rust.chunk().sections().isEmpty());
		}
	}

	@Test
	void oldAndMissingDataVersionsRequireDfu(@TempDir Path tempDir) throws IOException {
		Path oldRegion = tempDir.resolve("old.mca");
		ChunkPos oldPos = new ChunkPos(0, 0);
		CompoundTag oldRoot = chunkRoot(oldPos);
		oldRoot.putInt("DataVersion", CURRENT_DATA_VERSION - 1);
		writeJavaRegion(oldRegion, tempDir, RegionFileVersion.VERSION_NONE, oldPos, oldRoot);

		try (NativeChunkSectionStorage storage = NativeChunkSectionStorage.open(oldRegion)) {
			NativeChunkSectionStorage.DecodeResult rust = storage.decodeChunk(oldPos.x, oldPos.z);

			assertEquals(NativeChunkSectionStorage.OK, rust.result().status());
			assertTrue(rust.result().requiresDfu());
			assertEquals(CURRENT_DATA_VERSION - 1, rust.result().dataVersion());
			assertTrue(rust.chunk().sections().isEmpty());
		}

		Path missingRegion = tempDir.resolve("missing.mca");
		ChunkPos missingPos = new ChunkPos(1, 0);
		CompoundTag missingRoot = chunkRoot(missingPos);
		missingRoot.remove("DataVersion");
		writeJavaRegion(missingRegion, tempDir, RegionFileVersion.VERSION_NONE, missingPos, missingRoot);

		try (NativeChunkSectionStorage storage = NativeChunkSectionStorage.open(missingRegion)) {
			NativeChunkSectionStorage.DecodeResult rust = storage.decodeChunk(missingPos.x, missingPos.z);

			assertEquals(NativeChunkSectionStorage.OK, rust.result().status());
			assertTrue(rust.result().requiresDfu());
		}
	}

	@Test
	void malformedSectionPayloadsFailSafely(@TempDir Path tempDir) throws IOException {
		Path badPacked = tempDir.resolve("bad-packed.mca");
		ChunkPos pos = new ChunkPos(0, 0);
		CompoundTag root = chunkRoot(
			pos,
			section(
				0,
				blockStates(List.of(blockState("minecraft:air"), blockState("minecraft:stone")), new long[2]),
				biomes(List.of("minecraft:plains"), null),
				null,
				null
			)
		);
		writeJavaRegion(badPacked, tempDir, RegionFileVersion.VERSION_NONE, pos, root);

		try (NativeChunkSectionStorage storage = NativeChunkSectionStorage.open(badPacked)) {
			assertThrows(NativeChunkSectionStorage.DecodeException.class, () -> storage.decodeChunk(pos.x, pos.z));
		}

		Path badLight = tempDir.resolve("bad-light.mca");
		CompoundTag lightRoot = chunkRoot(
			pos,
			section(
				0,
				blockStates(List.of(blockState("minecraft:air")), null),
				biomes(List.of("minecraft:plains"), null),
				new byte[7],
				null
			)
		);
		writeJavaRegion(badLight, tempDir, RegionFileVersion.VERSION_NONE, pos, lightRoot);

		try (NativeChunkSectionStorage storage = NativeChunkSectionStorage.open(badLight)) {
			assertThrows(NativeChunkSectionStorage.DecodeException.class, () -> storage.decodeChunk(pos.x, pos.z));
		}
	}

	@Test
	void coordinateMismatchFailsSafely(@TempDir Path tempDir) throws IOException {
		Path regionPath = tempDir.resolve("r.0.0.mca");
		ChunkPos storedAt = new ChunkPos(4, 5);
		CompoundTag root = chunkRoot(new ChunkPos(40, 50));
		writeJavaRegion(regionPath, tempDir, RegionFileVersion.VERSION_NONE, storedAt, root);

		try (NativeChunkSectionStorage storage = NativeChunkSectionStorage.open(regionPath)) {
			assertThrows(NativeChunkSectionStorage.DecodeException.class, () -> storage.decodeChunk(storedAt.x, storedAt.z));
		}
	}

	@Test
	void sectionsOutsideBuildHeightFailSafely(@TempDir Path tempDir) throws IOException {
		Path regionPath = tempDir.resolve("r.0.0.mca");
		ChunkPos pos = new ChunkPos(0, 0);
		CompoundTag root = chunkRoot(
			pos,
			section(
				20,
				blockStates(List.of(blockState("minecraft:air")), null),
				biomes(List.of("minecraft:plains"), null),
				null,
				null
			)
		);
		writeJavaRegion(regionPath, tempDir, RegionFileVersion.VERSION_NONE, pos, root);

		try (NativeChunkSectionStorage storage = NativeChunkSectionStorage.open(regionPath)) {
			assertThrows(NativeChunkSectionStorage.DecodeException.class, () -> storage.decodeChunk(pos.x, pos.z));
		}
	}

	@Test
	void copiedOriginRegionChunkMatchesJavaSectionNbtWhenAvailable(@TempDir Path tempDir) throws IOException {
		Path regionDir = Path.of("run", "saves", "Origin", "region");
		assumeTrue(Files.isDirectory(regionDir), "Origin region directory is not available");
		RegionChunk sourceChunk = firstPresentOriginChunk(regionDir);
		assumeTrue(sourceChunk != null, "Origin region files have no populated chunks");
		Path copy = tempDir.resolve("r.0.0.mca");
		Files.copy(sourceChunk.regionPath(), copy);
		ChunkPos pos = new ChunkPos(sourceChunk.regionX() * 32 + sourceChunk.localX(), sourceChunk.regionZ() * 32 + sourceChunk.localZ());
		CompoundTag javaRoot = readJavaRegion(copy, pos);
		assumeTrue(javaRoot.getInt("DataVersion").orElse(0) == CURRENT_DATA_VERSION, "Origin chunk remains Java DFU-owned");
		SerializableChunkData parsed = SerializableChunkData.parse(VANILLA_HEIGHT, palettedContainerFactory(javaRoot), javaRoot);
		assertNotNull(parsed);

		try (NativeChunkSectionStorage storage = NativeChunkSectionStorage.open(copy)) {
			NativeChunkSectionStorage.DecodeResult rust = storage.decodeChunk(pos.x, pos.z);

			assertEquals(NativeChunkSectionStorage.OK, rust.result().status());
			assertTrue(rust.result().present());
			assertFalse(rust.result().requiresDfu());
			assertEquals(parsed.sectionData().stream().map(SerializableChunkData.SectionData::y).toList(), rust.chunk().sections().stream().map(NativeChunkSectionStorage.Section::sectionY).toList());
			for (int i = 0; i < rust.chunk().sections().size(); i++) {
				assertSectionMatchesRoot(javaRoot, rust.chunk().sections().get(i), i);
			}
			for (NativeChunkSectionStorage.Heightmap heightmap : rust.chunk().heightmaps()) {
				assertArrayEquals(javaRoot.getCompoundOrEmpty("Heightmaps").getLongArray(heightmap.name()).orElseThrow(), heightmap.data(), heightmap.name());
			}
		}
	}

	@Test
	void benchmarkCurrentVersionSectionReadPaths(@TempDir Path tempDir) throws Exception {
		assumeTrue(Boolean.getBoolean("mattmc.runChunkSectionBenchmark"), "chunk-section benchmark is opt-in");
		Path regionDir = Path.of("run", "saves", "Origin", "region");
		assumeTrue(Files.isDirectory(regionDir), "Origin region directory is not available");
		List<BenchmarkChunk> chunks = benchmarkChunks(regionDir, tempDir, Integer.getInteger("mattmc.chunkSectionBenchmark.chunks", 24));
		assumeTrue(!chunks.isEmpty(), "Origin has no current-version benchmark chunks");
		int warmup = Integer.getInteger("mattmc.chunkSectionBenchmark.warmup", 6);
		int measure = Integer.getInteger("mattmc.chunkSectionBenchmark.measure", 18);
		Path output = Path.of(System.getProperty("mattmc.chunkSectionBenchmark.output", "build/chunk-section-benchmark/chunk_section_benchmark.json"));
		JsonObject report = new JsonObject();
		report.addProperty("schema", "mattmc-chunk-section-read-benchmark-v1");
		report.addProperty("chunks", chunks.size());
		report.addProperty("warmupIterations", warmup);
		report.addProperty("measureIterations", measure);
		report.add("rustNative", rustNativeIdentity());
		JsonArray fixtures = new JsonArray();
		for (BenchmarkChunk chunk : chunks) {
			JsonObject fixture = new JsonObject();
			fixture.addProperty("region", chunk.regionPath().getFileName().toString());
			fixture.addProperty("chunkX", chunk.pos().x);
			fixture.addProperty("chunkZ", chunk.pos().z);
			fixture.addProperty("sections", chunk.root().getListOrEmpty("sections").size());
			fixtures.add(fixture);
		}
		report.add("fixtures", fixtures);
		JsonArray samples = new JsonArray();
		samples.add(runBenchmarkIteration("cold", 0, chunks));
		for (int i = 0; i < warmup; i++) {
			samples.add(runBenchmarkIteration("warmup", i, chunks));
		}
		for (int i = 0; i < measure; i++) {
			samples.add(runBenchmarkIteration("measure", i, chunks));
		}
		report.add("samples", samples);
		report.add("measureSummary", summarizeMeasures(samples));
		Files.createDirectories(output.toAbsolutePath().getParent());
		Files.writeString(output, GSON.toJson(report));
		System.out.println("chunk-section benchmark: " + output.toAbsolutePath());
	}

	private static void assertSectionMatchesRoot(CompoundTag root, NativeChunkSectionStorage.Section rust, int sectionIndex) throws IOException {
		CompoundTag section = root.getListOrEmpty("sections").get(sectionIndex).asCompound().orElseThrow();
		assertEquals(section.getByte("Y").orElseThrow().intValue(), rust.sectionY());
		CompoundTag blockStates = section.getCompoundOrEmpty("block_states");
		List<CompoundTag> javaBlockPalette = blockStates.getListOrEmpty("palette").compoundStream().toList();
		assertEquals(javaBlockPalette.size(), rust.blockPalette().size());
		for (int i = 0; i < javaBlockPalette.size(); i++) {
			assertEquals(javaBlockPalette.get(i), rust.blockPalette().get(i).asTag());
		}
		assertArrayEquals(blockStates.getLongArray("data").orElse(new long[0]), rust.blockData());

		CompoundTag biomes = section.getCompoundOrEmpty("biomes");
		List<String> javaBiomePalette = biomes.getListOrEmpty("palette").stream().map(tag -> tag.asString().orElseThrow()).toList();
		assertEquals(javaBiomePalette, rust.biomePalette().stream().map(NativeChunkSectionStorage.BiomePaletteEntry::resourceId).toList());
		assertArrayEquals(biomes.getLongArray("data").orElse(new long[0]), rust.biomeData());

		assertArrayEquals(section.getByteArray("BlockLight").orElse(new byte[0]), rust.blockLight());
		assertArrayEquals(section.getByteArray("SkyLight").orElse(new byte[0]), rust.skyLight());
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

	private static CompoundTag chunkRoot(ChunkPos pos, Tag... sections) {
		CompoundTag root = new CompoundTag();
		root.putInt("DataVersion", CURRENT_DATA_VERSION);
		root.putInt("xPos", pos.x);
		root.putInt("zPos", pos.z);
		root.putInt("yPos", -4);
		root.putString("Status", "minecraft:full");
		root.putBoolean("isLightOn", true);
		root.putLong("LastUpdate", 123L);
		root.putLong("InhabitedTime", 456L);
		ListTag sectionList = new ListTag();
		for (Tag section : sections) {
			sectionList.add(section);
		}
		root.put("sections", sectionList);
		root.put("Heightmaps", new CompoundTag());
		root.put("block_entities", new ListTag());
		root.put("structures", new CompoundTag());
		return root;
	}

	private static CompoundTag section(int y, CompoundTag blockStates, CompoundTag biomes, byte[] blockLight, byte[] skyLight) {
		CompoundTag section = new CompoundTag();
		section.putByte("Y", (byte)y);
		section.put("block_states", blockStates);
		section.put("biomes", biomes);
		if (blockLight != null) {
			section.putByteArray("BlockLight", blockLight);
		}
		if (skyLight != null) {
			section.putByteArray("SkyLight", skyLight);
		}
		return section;
	}

	private static CompoundTag blockStates(List<CompoundTag> palette, long[] data) {
		CompoundTag blockStates = new CompoundTag();
		ListTag paletteTag = new ListTag();
		for (CompoundTag entry : palette) {
			paletteTag.add(entry);
		}
		blockStates.put("palette", paletteTag);
		if (data != null) {
			blockStates.putLongArray("data", data);
		}
		return blockStates;
	}

	private static CompoundTag blockState(String name) {
		CompoundTag blockState = new CompoundTag();
		blockState.putString("Name", name);
		return blockState;
	}

	private static CompoundTag biomes(List<String> palette, long[] data) {
		CompoundTag biomes = new CompoundTag();
		ListTag paletteTag = new ListTag();
		for (String value : palette) {
			paletteTag.add(StringTag.valueOf(value));
		}
		biomes.put("palette", paletteTag);
		if (data != null) {
			biomes.putLongArray("data", data);
		}
		return biomes;
	}

	private static CompoundTag heightmaps(String name, long[] data) {
		CompoundTag heightmaps = new CompoundTag();
		heightmaps.put(name, new LongArrayTag(data));
		return heightmaps;
	}

	private static byte[] bytes(int length, int value) {
		byte[] bytes = new byte[length];
		java.util.Arrays.fill(bytes, (byte)value);
		return bytes;
	}

	private static long[] longs(int length, long value) {
		long[] values = new long[length];
		java.util.Arrays.fill(values, value);
		return values;
	}

	private static PalettedContainerFactory palettedContainerFactory(CompoundTag root) {
		return PalettedContainerFactory.create(registryAccess(root));
	}

	private static RegistryAccess registryAccess(CompoundTag root) {
		MappedRegistry<Biome> biomes = new MappedRegistry<>(Registries.BIOME, Lifecycle.stable());
		registerBiome(biomes, Biomes.PLAINS.location());
		for (String id : biomeIds(root)) {
			registerBiome(biomes, ResourceLocation.parse(id));
		}
		Registry<Biome> frozen = biomes.freeze();
		return new RegistryAccess.ImmutableRegistryAccess(List.of(frozen)).freeze();
	}

	private static void registerBiome(MappedRegistry<Biome> registry, ResourceLocation id) {
		ResourceKey<Biome> key = ResourceKey.create(Registries.BIOME, id);
		if (registry.get(key).isPresent()) {
			return;
		}
		registry.register(key, dummyBiome(), RegistrationInfo.BUILT_IN);
	}

	private static Biome dummyBiome() {
		return new Biome.BiomeBuilder()
			.hasPrecipitation(true)
			.temperature(0.8F)
			.downfall(0.4F)
			.specialEffects(new BiomeSpecialEffects.Builder().fogColor(0).waterColor(0).waterFogColor(0).skyColor(0).build())
			.mobSpawnSettings(MobSpawnSettings.EMPTY)
			.generationSettings(BiomeGenerationSettings.EMPTY)
			.build();
	}

	private static List<String> biomeIds(CompoundTag root) {
		return root.getListOrEmpty("sections")
			.compoundStream()
			.flatMap(section -> section.getCompoundOrEmpty("biomes").getListOrEmpty("palette").stream())
			.map(tag -> tag.asString().orElse("minecraft:plains"))
			.distinct()
			.toList();
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

	private static List<BenchmarkChunk> benchmarkChunks(Path regionDir, Path tempDir, int maxChunks) throws IOException {
		List<BenchmarkChunk> chunks = new java.util.ArrayList<>();
		Map<Path, Path> copies = new HashMap<>();
		try (Stream<Path> paths = Files.list(regionDir)) {
			for (Path source : paths.filter(path -> path.getFileName().toString().endsWith(".mca")).sorted(Comparator.naturalOrder()).toList()) {
				if (chunks.size() >= maxChunks) {
					break;
				}
				String[] parts = source.getFileName().toString().split("\\.");
				int regionX = Integer.parseInt(parts[1]);
				int regionZ = Integer.parseInt(parts[2]);
				byte[] bytes = Files.readAllBytes(source);
				for (int i = 0; i < 1024 && chunks.size() < maxChunks && i * 4 + 3 < bytes.length; i++) {
					if (readInt(bytes, i * 4) == 0) {
						continue;
					}
					Path copy = copies.computeIfAbsent(source, path -> {
						try {
							Path target = tempDir.resolve(path.getFileName().toString());
							Files.copy(path, target);
							return target;
						} catch (IOException exception) {
							throw new RuntimeException(exception);
						}
					});
					ChunkPos pos = new ChunkPos(regionX * 32 + (i & 31), regionZ * 32 + (i >> 5));
					CompoundTag root = readJavaRegion(copy, pos);
					if (root.getInt("DataVersion").orElse(0) == CURRENT_DATA_VERSION && !root.getString("Status").isEmpty()) {
						RegistryAccess registryAccess = registryAccess(root);
						chunks.add(new BenchmarkChunk(copy, pos, root, registryAccess, PalettedContainerFactory.create(registryAccess)));
					}
				}
			}
		} catch (RuntimeException exception) {
			if (exception.getCause() instanceof IOException ioException) {
				throw ioException;
			}
			throw exception;
		}
		return List.copyOf(chunks);
	}

	private static JsonObject runBenchmarkIteration(String phase, int index, List<BenchmarkChunk> chunks) throws Exception {
		JsonObject sample = new JsonObject();
		sample.addProperty("phase", phase);
		sample.addProperty("index", index);
		Metric regionNbtRead = new Metric();
		Metric javaSectionParse = new Metric();
		Metric rustDecode = new Metric();
		Metric rustRegionRead = new Metric();
		Metric rustDecompress = new Metric();
		Metric rustNbtParse = new Metric();
		Metric rustChunkDecode = new Metric();
		Metric rustTapeCreate = new Metric();
		Metric rustOutputCopy = new Metric();
		Metric javaPaletteAndSection = new Metric();
		Metric totalJava = new Metric();
		Metric totalRust = new Metric();
		Map<Path, NativeChunkSectionStorage> open = new HashMap<>();
		try {
			for (BenchmarkChunk chunk : chunks) {
				long started = System.nanoTime();
				CompoundTag reread = readJavaRegion(chunk.regionPath(), chunk.pos());
				regionNbtRead.add(System.nanoTime() - started, estimatedChunkBytes(reread));

				started = System.nanoTime();
				ChunkStatus status = reread.read("Status", ChunkStatus.CODEC).orElse(ChunkStatus.EMPTY);
				SerializableChunkData.NativeSectionBuild javaSections = SerializableChunkData.parseJavaSectionData(VANILLA_HEIGHT, chunk.factory(), reread, status);
				long javaSectionNanos = System.nanoTime() - started;
				javaSectionParse.add(javaSectionNanos, estimatedChunkBytes(reread));
				totalJava.add(javaSectionNanos, estimatedChunkBytes(reread));

				NativeChunkSectionStorage storage = open.computeIfAbsent(chunk.regionPath(), path -> {
					try {
						return NativeChunkSectionStorage.open(path);
					} catch (IOException exception) {
						throw new RuntimeException(exception);
					}
				});
				started = System.nanoTime();
				NativeChunkSectionStorage.DecodeResult rust = storage.decodeChunk(chunk.pos().x, chunk.pos().z);
				long rustDecodeNanos = System.nanoTime() - started;
				rustDecode.add(rustDecodeNanos, rust.result().compressedLength(), rust.result().outputLength());
				rustRegionRead.add(rust.result().regionReadNanos(), rust.result().compressedLength());
				rustDecompress.add(rust.result().decompressionNanos(), rust.result().decompressedLength());
				rustNbtParse.add(rust.result().nbtParseNanos(), rust.result().decompressedLength());
				rustChunkDecode.add(rust.result().chunkDecodeNanos(), rust.result().decompressedLength());
				rustTapeCreate.add(rust.result().tapeCreationNanos(), rust.result().outputLength(), rust.result().outputLength());
				rustOutputCopy.add(rust.result().rustOutputCopyNanos(), rust.result().outputLength(), rust.result().outputLength());

				started = System.nanoTime();
				SerializableChunkData.NativeSectionBuild rustSections = SerializableChunkData.buildNativeSections(chunk.registryAccess(), chunk.factory(), rust.chunk());
				long javaPaletteNanos = System.nanoTime() - started;
				javaPaletteAndSection.add(javaPaletteNanos, rust.result().outputLength());
				totalRust.add(rustDecodeNanos + javaPaletteNanos, rust.result().compressedLength(), rust.result().outputLength());

				String mismatch = SerializableChunkData.compareNativeSections(
					new SerializableChunkData(
						chunk.factory(),
						chunk.pos(),
						VANILLA_HEIGHT.getMinSectionY(),
						0L,
						0L,
						status,
						null,
						null,
						UpgradeData.EMPTY,
						null,
						javaSections.heightmaps(),
						new ChunkAccess.PackedTicks(List.of(), List.of()),
						new ShortList[0],
						reread.getBooleanOr("isLightOn", false),
						javaSections.sections(),
						List.of(),
						List.of(),
						new CompoundTag()
					),
					rustSections,
					chunk.factory(),
					rust.chunk()
				);
				if (mismatch != null) {
					throw new IOException("Chunk-section benchmark parity mismatch at " + chunk.pos() + ": " + mismatch);
				}
			}
		} finally {
			for (NativeChunkSectionStorage storage : open.values()) {
				storage.close();
			}
		}

		JsonObject metrics = new JsonObject();
		metrics.add("region_nbt_read", regionNbtRead.toJson());
		metrics.add("java_section_parse_palette_light_heightmap", javaSectionParse.toJson());
		metrics.add("rust_decode_tape_wall", rustDecode.toJson());
		metrics.add("rust_region_read", rustRegionRead.toJson());
		metrics.add("rust_decompress", rustDecompress.toJson());
		metrics.add("rust_nbt_parse", rustNbtParse.toJson());
		metrics.add("rust_chunk_decode", rustChunkDecode.toJson());
		metrics.add("rust_tape_create", rustTapeCreate.toJson());
		metrics.add("rust_output_copy", rustOutputCopy.toJson());
		metrics.add("java_palette_section_light_heightmap_from_rust", javaPaletteAndSection.toJson());
		metrics.add("java_total_section_path", totalJava.toJson());
		metrics.add("rust_total_section_path", totalRust.toJson());
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
		if (totals.containsKey("java_total_section_path") && totals.containsKey("rust_total_section_path")) {
			double javaNanos = totals.get("java_total_section_path").nanos;
			double rustNanos = totals.get("rust_total_section_path").nanos;
			summary.addProperty("rustToJavaTotalRatio", javaNanos == 0.0 ? 0.0 : rustNanos / javaNanos);
		}
		return summary;
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

	private static long estimatedChunkBytes(CompoundTag root) {
		return root.getListOrEmpty("sections").size() * 4096L;
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

	private static final class Metric {
		private long nanos;
		private long inputBytes;
		private long copiedBytes;

		void add(long nanos, long inputBytes) {
			this.add(nanos, inputBytes, 0L);
		}

		void add(long nanos, long inputBytes, long copiedBytes) {
			this.nanos += nanos;
			this.inputBytes += inputBytes;
			this.copiedBytes += copiedBytes;
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

	private record BenchmarkChunk(Path regionPath, ChunkPos pos, CompoundTag root, RegistryAccess registryAccess, PalettedContainerFactory factory) {
	}

	private record RegionChunk(Path regionPath, int regionX, int regionZ, int localX, int localZ) {
	}
}
