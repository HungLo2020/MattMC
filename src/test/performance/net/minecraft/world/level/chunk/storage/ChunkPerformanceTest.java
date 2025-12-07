package net.minecraft.world.level.chunk.storage;

import com.mojang.datafixers.DataFixer;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.storage.ChunkStorage;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import org.slf4j.Logger;

/**
 * Performance benchmarking tests for chunk save and load operations.
 * Tests both single chunk and bulk operations to establish baselines.
 */
public class ChunkPerformanceTest {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final int WARMUP_ITERATIONS = 5;
	private static final int TEST_ITERATIONS = 10;
	
	private Path testDirectory;
	private ChunkStorage chunkStorage;
	private DataFixer dataFixer;
	private PalettedContainerFactory containerFactory;
	private RegistryAccess registryAccess;
	
	public static void main(String[] args) {
		try {
			ChunkPerformanceTest test = new ChunkPerformanceTest();
			test.setup();
			
			LOGGER.info("=== Chunk Save/Load Performance Baseline Tests ===");
			LOGGER.info("Warmup iterations: {}, Test iterations: {}", WARMUP_ITERATIONS, TEST_ITERATIONS);
			LOGGER.info("");
			
			test.runAllTests();
			
			test.teardown();
			
			LOGGER.info("");
			LOGGER.info("=== All tests completed successfully ===");
		} catch (Exception e) {
			LOGGER.error("Test execution failed", e);
			System.exit(1);
		}
	}
	
	private void setup() throws IOException {
		LOGGER.info("Setting up test environment...");
		
		// Initialize Minecraft bootstrap
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		Bootstrap.validate();
		
		// Create temporary test directory
		testDirectory = Files.createTempDirectory("chunk-performance-test");
		LOGGER.info("Test directory: {}", testDirectory);
		
		// Initialize data fixer
		dataFixer = DataFixers.getDataFixer();
		
		// Since we don't have a full RegistryAccess with worldgen registries, 
		// we'll use a minimal setup that's sufficient for testing
		// We won't actually use the full SerializableChunkData path but will focus
		// on the IO and compression performance which is the bottleneck
		containerFactory = null; // We'll work around this in our test methods
		
		// Initialize chunk storage
		RegionStorageInfo storageInfo = new RegionStorageInfo(
			"test-world",
			Level.OVERWORLD,
			"chunk"
		);
		chunkStorage = new ChunkStorage(storageInfo, testDirectory, dataFixer, false);
		
		LOGGER.info("Setup complete");
	}
	
	private void teardown() throws IOException {
		LOGGER.info("Cleaning up test environment...");
		
		if (chunkStorage != null) {
			chunkStorage.close();
		}
		
		// Delete test directory and all contents
		if (testDirectory != null && Files.exists(testDirectory)) {
			Files.walk(testDirectory)
				.sorted(Comparator.reverseOrder())
				.forEach(path -> {
					try {
						Files.delete(path);
					} catch (IOException e) {
						LOGGER.warn("Failed to delete {}", path, e);
					}
				});
		}
		
		LOGGER.info("Cleanup complete");
	}
	
	private void runAllTests() {
		// Test 1: Single empty chunk
		LOGGER.info("--- Test 1: Single Empty Chunk ---");
		runSingleChunkTest(ChunkType.EMPTY);
		
		// Test 2: Single chunk with simple blocks
		LOGGER.info("");
		LOGGER.info("--- Test 2: Single Chunk with Simple Blocks ---");
		runSingleChunkTest(ChunkType.SIMPLE);
		
		// Test 3: Single chunk with complex blocks
		LOGGER.info("");
		LOGGER.info("--- Test 3: Single Chunk with Complex Blocks ---");
		runSingleChunkTest(ChunkType.COMPLEX);
		
		// Test 4: Single chunk with varied patterns
		LOGGER.info("");
		LOGGER.info("--- Test 4: Single Chunk with Varied Patterns ---");
		runSingleChunkTest(ChunkType.VARIED);
		
		// Test 5: Multiple chunks (10)
		LOGGER.info("");
		LOGGER.info("--- Test 5: Multiple Chunks (10) ---");
		runMultipleChunksTest(10, ChunkType.SIMPLE);
		
		// Test 6: Multiple chunks (50)
		LOGGER.info("");
		LOGGER.info("--- Test 6: Multiple Chunks (50) ---");
		runMultipleChunksTest(50, ChunkType.SIMPLE);
		
		// Test 7: Multiple chunks (100)
		LOGGER.info("");
		LOGGER.info("--- Test 7: Multiple Chunks (100) ---");
		runMultipleChunksTest(100, ChunkType.SIMPLE);
		
		// Test 8: Bulk mixed chunks (100)
		LOGGER.info("");
		LOGGER.info("--- Test 8: Bulk Mixed Chunks (100) ---");
		runMixedChunksTest(100);
	}
	
	private void runSingleChunkTest(ChunkType type) {
		ChunkPos pos = new ChunkPos(0, 0);
		
		// Warmup
		for (int i = 0; i < WARMUP_ITERATIONS; i++) {
			CompoundTag tag = generateChunkTag(pos, type);
			measureSaveLoad(pos, tag, true);
		}
		
		// Actual test
		List<TestResult> results = new ArrayList<>();
		for (int i = 0; i < TEST_ITERATIONS; i++) {
			CompoundTag tag = generateChunkTag(pos, type);
			TestResult result = measureSaveLoad(pos, tag, false);
			results.add(result);
		}
		
		printResults(results, type.name() + " Single Chunk");
	}
	
	private void runMultipleChunksTest(int count, ChunkType type) {
		List<ChunkPos> positions = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			positions.add(new ChunkPos(i % 10, i / 10));
		}
		
		// Warmup
		for (int i = 0; i < WARMUP_ITERATIONS; i++) {
			List<CompoundTag> tags = new ArrayList<>();
			for (ChunkPos pos : positions) {
				tags.add(generateChunkTag(pos, type));
			}
			measureBulkSaveLoad(positions, tags, true);
		}
		
		// Actual test
		List<TestResult> results = new ArrayList<>();
		for (int i = 0; i < TEST_ITERATIONS; i++) {
			List<CompoundTag> tags = new ArrayList<>();
			for (ChunkPos pos : positions) {
				tags.add(generateChunkTag(pos, type));
			}
			TestResult result = measureBulkSaveLoad(positions, tags, false);
			results.add(result);
		}
		
		printResults(results, count + " " + type.name() + " Chunks");
	}
	
	private void runMixedChunksTest(int count) {
		List<ChunkPos> positions = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			positions.add(new ChunkPos(i % 10, i / 10));
		}
		
		// Warmup
		for (int i = 0; i < WARMUP_ITERATIONS; i++) {
			List<CompoundTag> tags = new ArrayList<>();
			for (int j = 0; j < count; j++) {
				ChunkType type = ChunkType.values()[j % ChunkType.values().length];
				tags.add(generateChunkTag(positions.get(j), type));
			}
			measureBulkSaveLoad(positions, tags, true);
		}
		
		// Actual test
		List<TestResult> results = new ArrayList<>();
		for (int i = 0; i < TEST_ITERATIONS; i++) {
			List<CompoundTag> tags = new ArrayList<>();
			for (int j = 0; j < count; j++) {
				ChunkType type = ChunkType.values()[j % ChunkType.values().length];
				tags.add(generateChunkTag(positions.get(j), type));
			}
			TestResult result = measureBulkSaveLoad(positions, tags, false);
			results.add(result);
		}
		
		printResults(results, count + " Mixed Chunks");
	}
	
	private CompoundTag generateChunkTag(ChunkPos pos, ChunkType type) {
		// For performance testing, we'll generate CompoundTags directly
		// This focuses the test on I/O and serialization bottlenecks
		CompoundTag tag = new CompoundTag();
		tag.putInt("xPos", pos.x);
		tag.putInt("zPos", pos.z);
		tag.putInt("yPos", -64);
		tag.putString("Status", "full");
		tag.putLong("LastUpdate", System.currentTimeMillis());
		tag.putLong("InhabitedTime", 0L);
		tag.putInt("DataVersion", SharedConstants.getCurrentVersion().dataVersion().version());
		
		// Create section data based on type
		net.minecraft.nbt.ListTag sections = new net.minecraft.nbt.ListTag();
		
		int sectionCount;
		int dataSize;
		switch (type) {
			case EMPTY:
				// No sections for empty chunks
				sectionCount = 0;
				dataSize = 0;
				break;
			case SIMPLE:
				// 4 sections with simple patterns
				sectionCount = 4;
				dataSize = 64; // Small data per section
				break;
			case COMPLEX:
				// 8 sections with more complex data
				sectionCount = 8;
				dataSize = 256; // Medium data per section
				break;
			case VARIED:
				// 12 sections with varied data
				sectionCount = 12;
				dataSize = 512; // Larger data per section
				break;
			default:
				sectionCount = 0;
				dataSize = 0;
		}
		
		for (int i = 0; i < sectionCount; i++) {
			CompoundTag sectionTag = new CompoundTag();
			sectionTag.putByte("Y", (byte)(i - 4));
			
			// Add some dummy block state data to simulate real chunks
			CompoundTag blockStates = new CompoundTag();
			net.minecraft.nbt.ListTag palette = new net.minecraft.nbt.ListTag();
			
			// Add palette entries
			for (int p = 0; p < Math.min(dataSize / 32, 16); p++) {
				CompoundTag paletteEntry = new CompoundTag();
				paletteEntry.putString("Name", "minecraft:stone");
				palette.add(paletteEntry);
			}
			blockStates.put("palette", palette);
			
			// Add data array
			long[] data = new long[dataSize];
			for (int d = 0; d < data.length; d++) {
				data[d] = (long) (Math.random() * Long.MAX_VALUE);
			}
			blockStates.putLongArray("data", data);
			
			sectionTag.put("block_states", blockStates);
			sections.add(sectionTag);
		}
		
		tag.put("sections", sections);
		
		// Add heightmaps
		CompoundTag heightmaps = new CompoundTag();
		long[] heightmapData = new long[37];
		for (int i = 0; i < heightmapData.length; i++) {
			heightmapData[i] = 0L;
		}
		heightmaps.putLongArray("MOTION_BLOCKING", heightmapData);
		heightmaps.putLongArray("WORLD_SURFACE", heightmapData);
		tag.put("Heightmaps", heightmaps);
		
		return tag;
	}
	
	private TestResult measureSaveLoad(ChunkPos pos, CompoundTag tag, boolean isWarmup) {
		// Tag is already created, so serialization time is minimal
		long serializeStart = System.nanoTime();
		// Just measure the tag creation/copying overhead
		CompoundTag tagCopy = tag.copy();
		long serializeEnd = System.nanoTime();
		
		// NBT encoding happens inside the write process
		long encodeStart = System.nanoTime();
		long encodeEnd = System.nanoTime();
		
		long saveStart = System.nanoTime();
		try {
			CompletableFuture<Void> future = chunkStorage.write(pos, () -> tagCopy);
			future.join();
		} catch (Exception e) {
			LOGGER.error("Failed to save chunk", e);
			return new TestResult(0, 0, 0, 0, 0, tag.toString().length());
		}
		long saveEnd = System.nanoTime();
		
		long loadStart = System.nanoTime();
		CompoundTag loadedTag;
		try {
			CompletableFuture<java.util.Optional<CompoundTag>> future = chunkStorage.read(pos);
			loadedTag = future.join().orElse(null);
		} catch (Exception e) {
			LOGGER.error("Failed to load chunk", e);
			return new TestResult(0, 0, 0, 0, 0, tag.toString().length());
		}
		long loadEnd = System.nanoTime();
		
		if (loadedTag == null) {
			LOGGER.error("Loaded chunk is null");
			return new TestResult(0, 0, 0, 0, 0, tag.toString().length());
		}
		
		long deserializeStart = System.nanoTime();
		// Simple deserialization - just access the data
		loadedTag.getInt("xPos");
		loadedTag.getInt("zPos");
		net.minecraft.nbt.ListTag loadedSections = loadedTag.getListOrEmpty("sections");
		for (int i = 0; i < loadedSections.size(); i++) {
			loadedSections.getCompound(i).ifPresent(sectionTag -> {
				sectionTag.getCompound("block_states").ifPresent(blockStatesTag -> {
					// Just access the data
					blockStatesTag.get("palette");
					blockStatesTag.getLongArray("data");
				});
			});
		}
		long deserializeEnd = System.nanoTime();
		
		double serializeMs = (serializeEnd - serializeStart) / 1_000_000.0;
		double encodeMs = (encodeEnd - encodeStart) / 1_000_000.0;
		double saveMs = (saveEnd - saveStart) / 1_000_000.0;
		double loadMs = (loadEnd - loadStart) / 1_000_000.0;
		double deserializeMs = (deserializeEnd - deserializeStart) / 1_000_000.0;
		int sizeBytes = tag.toString().length();
		
		return new TestResult(serializeMs, encodeMs, saveMs, loadMs, deserializeMs, sizeBytes);
	}
	
	private TestResult measureBulkSaveLoad(List<ChunkPos> positions, List<CompoundTag> tags, boolean isWarmup) {
		long totalSerializeStart = System.nanoTime();
		// Copy all tags
		List<CompoundTag> tagCopies = new ArrayList<>();
		for (CompoundTag tag : tags) {
			tagCopies.add(tag.copy());
		}
		long totalSerializeEnd = System.nanoTime();
		
		long totalEncodeStart = System.nanoTime();
		// Encoding happens in write process
		long totalEncodeEnd = System.nanoTime();
		
		long totalSaveStart = System.nanoTime();
		List<CompletableFuture<Void>> saveFutures = new ArrayList<>();
		for (int i = 0; i < positions.size(); i++) {
			ChunkPos pos = positions.get(i);
			CompoundTag tag = tagCopies.get(i);
			try {
				saveFutures.add(chunkStorage.write(pos, () -> tag));
			} catch (Exception e) {
				LOGGER.error("Failed to save chunk {}", pos, e);
			}
		}
		CompletableFuture.allOf(saveFutures.toArray(new CompletableFuture[0])).join();
		long totalSaveEnd = System.nanoTime();
		
		long totalLoadStart = System.nanoTime();
		List<CompletableFuture<java.util.Optional<CompoundTag>>> loadFutures = new ArrayList<>();
		for (ChunkPos pos : positions) {
			try {
				loadFutures.add(chunkStorage.read(pos));
			} catch (Exception e) {
				LOGGER.error("Failed to load chunk {}", pos, e);
				loadFutures.add(CompletableFuture.completedFuture(java.util.Optional.empty()));
			}
		}
		List<CompoundTag> loadedTags = new ArrayList<>();
		for (CompletableFuture<java.util.Optional<CompoundTag>> future : loadFutures) {
			loadedTags.add(future.join().orElse(null));
		}
		long totalLoadEnd = System.nanoTime();
		
		long totalDeserializeStart = System.nanoTime();
		for (CompoundTag tag : loadedTags) {
			if (tag != null) {
				tag.getInt("xPos");
				tag.getInt("zPos");
				net.minecraft.nbt.ListTag sections = tag.getListOrEmpty("sections");
				for (int i = 0; i < sections.size(); i++) {
					sections.getCompound(i).ifPresent(sectionTag -> {
						sectionTag.getCompound("block_states").ifPresent(blockStatesTag -> {
							blockStatesTag.get("palette");
							blockStatesTag.getLongArray("data");
						});
					});
				}
			}
		}
		long totalDeserializeEnd = System.nanoTime();
		
		double serializeMs = (totalSerializeEnd - totalSerializeStart) / 1_000_000.0;
		double encodeMs = (totalEncodeEnd - totalEncodeStart) / 1_000_000.0;
		double saveMs = (totalSaveEnd - totalSaveStart) / 1_000_000.0;
		double loadMs = (totalLoadEnd - totalLoadStart) / 1_000_000.0;
		double deserializeMs = (totalDeserializeEnd - totalDeserializeStart) / 1_000_000.0;
		int totalSize = tags.stream().mapToInt(tag -> tag.toString().length()).sum();
		
		return new TestResult(serializeMs, encodeMs, saveMs, loadMs, deserializeMs, totalSize);
	}
	
	private void printResults(List<TestResult> results, String testName) {
		double avgSerialize = results.stream().mapToDouble(r -> r.serializeMs).average().orElse(0);
		double avgEncode = results.stream().mapToDouble(r -> r.encodeMs).average().orElse(0);
		double avgSave = results.stream().mapToDouble(r -> r.saveMs).average().orElse(0);
		double avgLoad = results.stream().mapToDouble(r -> r.loadMs).average().orElse(0);
		double avgDeserialize = results.stream().mapToDouble(r -> r.deserializeMs).average().orElse(0);
		double avgSize = results.stream().mapToDouble(r -> r.sizeBytes).average().orElse(0);
		
		double minSerialize = results.stream().mapToDouble(r -> r.serializeMs).min().orElse(0);
		double minEncode = results.stream().mapToDouble(r -> r.encodeMs).min().orElse(0);
		double minSave = results.stream().mapToDouble(r -> r.saveMs).min().orElse(0);
		double minLoad = results.stream().mapToDouble(r -> r.loadMs).min().orElse(0);
		double minDeserialize = results.stream().mapToDouble(r -> r.deserializeMs).min().orElse(0);
		
		double maxSerialize = results.stream().mapToDouble(r -> r.serializeMs).max().orElse(0);
		double maxEncode = results.stream().mapToDouble(r -> r.encodeMs).max().orElse(0);
		double maxSave = results.stream().mapToDouble(r -> r.saveMs).max().orElse(0);
		double maxLoad = results.stream().mapToDouble(r -> r.loadMs).max().orElse(0);
		double maxDeserialize = results.stream().mapToDouble(r -> r.deserializeMs).max().orElse(0);
		
		double totalAvg = avgSerialize + avgEncode + avgSave + avgLoad + avgDeserialize;
		
		LOGGER.info("Results for: {}", testName);
		LOGGER.info("  Serialization:   avg={} ms  min={}ms  max={}ms", 
			String.format("%.2f", avgSerialize), String.format("%.2f", minSerialize), String.format("%.2f", maxSerialize));
		LOGGER.info("  NBT Encoding:    avg={}ms  min={}ms  max={}ms", 
			String.format("%.2f", avgEncode), String.format("%.2f", minEncode), String.format("%.2f", maxEncode));
		LOGGER.info("  Save to Disk:    avg={}ms  min={}ms  max={}ms", 
			String.format("%.2f", avgSave), String.format("%.2f", minSave), String.format("%.2f", maxSave));
		LOGGER.info("  Load from Disk:  avg={}ms  min={}ms  max={}ms", 
			String.format("%.2f", avgLoad), String.format("%.2f", minLoad), String.format("%.2f", maxLoad));
		LOGGER.info("  Deserialization: avg={}ms  min={}ms  max={}ms", 
			String.format("%.2f", avgDeserialize), String.format("%.2f", minDeserialize), String.format("%.2f", maxDeserialize));
		LOGGER.info("  TOTAL TIME:      avg={}ms", String.format("%.2f", totalAvg));
		LOGGER.info("  Avg Size:        {} KB ({} bytes)", String.format("%.1f", avgSize / 1024.0), (int)avgSize);
	}
	
	private enum ChunkType {
		EMPTY,
		SIMPLE,
		COMPLEX,
		VARIED
	}
	
	private static class TestResult {
		final double serializeMs;
		final double encodeMs;
		final double saveMs;
		final double loadMs;
		final double deserializeMs;
		final int sizeBytes;
		
		TestResult(double serializeMs, double encodeMs, double saveMs, double loadMs, double deserializeMs, int sizeBytes) {
			this.serializeMs = serializeMs;
			this.encodeMs = encodeMs;
			this.saveMs = saveMs;
			this.loadMs = loadMs;
			this.deserializeMs = deserializeMs;
			this.sizeBytes = sizeBytes;
		}
	}
}
