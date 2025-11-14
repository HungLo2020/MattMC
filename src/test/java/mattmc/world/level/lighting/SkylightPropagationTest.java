package mattmc.world.level.lighting;

import mattmc.world.level.Level;
import mattmc.world.level.block.Block;
import mattmc.world.level.block.Blocks;
import mattmc.world.level.chunk.LevelChunk;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Manual test for skylight propagation below heightmap.
 * 
 * Demonstrates digging a vertical shaft and seeing skylight "pour" in,
 * then filling it and seeing skylight removed.
 * 
 * Run with: ./gradlew runDebugTest
 */
public class SkylightPropagationTest {
	
	public static void main(String[] args) throws IOException {
		System.out.println("=== Skylight Propagation Test ===\n");
		
		Path worldDir = Files.createTempDirectory("mattmc-skylight-propagation-");
		System.out.println("Test world directory: " + worldDir);
		
		try {
			testSkylightPropagation(worldDir);
			System.out.println("\n=== TEST PASSED ===");
			System.out.println("Skylight propagation working correctly!");
			
		} finally {
			deleteRecursively(worldDir);
			System.out.println("\nCleaned up test world directory.");
		}
	}
	
	private static void testSkylightPropagation(Path worldDir) throws IOException {
		Level level = new Level();
		level.setWorldDirectory(worldDir);
		level.setSeed(12345L);
		
		LevelChunk chunk = level.getChunk(0, 0);
		
		System.out.println("\n--- Initial Setup: Solid Ground ---");
		
		// Create solid ground from y=50 to y=70
		for (int worldY = 50; worldY <= 70; worldY++) {
			int chunkY = LevelChunk.worldYToChunkY(worldY);
			chunk.setBlock(8, chunkY, 8, Blocks.STONE);
		}
		
		// Get initial skylight values
		System.out.println("\nSkylight before digging:");
		printSkylightColumn(chunk, 8, 8, 70, 60);
		
		System.out.println("\n--- Test 1: Dig Vertical Shaft ---");
		System.out.println("Digging from y=70 down to y=60...");
		
		// Dig the shaft
		for (int worldY = 70; worldY >= 60; worldY--) {
			int chunkY = LevelChunk.worldYToChunkY(worldY);
			chunk.setBlock(8, chunkY, 8, Blocks.AIR);
		}
		
		System.out.println("\nSkylight after digging shaft:");
		printSkylightColumn(chunk, 8, 8, 75, 55);
		
		// Verify skylight "poured" into the shaft
		int topOfShaft = LevelChunk.worldYToChunkY(70);
		int midShaft = LevelChunk.worldYToChunkY(65);
		int bottomOfShaft = LevelChunk.worldYToChunkY(60);
		
		int topLight = chunk.getSkyLight(8, topOfShaft, 8);
		int midLight = chunk.getSkyLight(8, midShaft, 8);
		int bottomLight = chunk.getSkyLight(8, bottomOfShaft, 8);
		
		System.out.println("\nVerification:");
		System.out.println("  Top of shaft (y=70): " + topLight);
		verify(topLight >= 14, "Top should have near-full skylight");
		
		System.out.println("  Mid shaft (y=65): " + midLight);
		verify(midLight > 0, "Middle should have some skylight");
		verify(midLight < topLight, "Middle should be darker than top");
		
		System.out.println("  Bottom of shaft (y=60): " + bottomLight);
		verify(bottomLight > 0, "Bottom should have some skylight");
		verify(bottomLight < midLight, "Bottom should be darker than middle");
		
		// Check that light hasn't gone through the floor
		int belowFloor = LevelChunk.worldYToChunkY(59);
		int belowLight = chunk.getSkyLight(8, belowFloor, 8);
		System.out.println("  Below floor (y=59): " + belowLight);
		verify(belowLight == 0, "Below floor should have no skylight");
		
		System.out.println("\n--- Test 2: Fill the Shaft ---");
		System.out.println("Filling from y=60 back up to y=70...");
		
		// Fill the shaft back in
		for (int worldY = 60; worldY <= 70; worldY++) {
			int chunkY = LevelChunk.worldYToChunkY(worldY);
			chunk.setBlock(8, chunkY, 8, Blocks.STONE);
		}
		
		System.out.println("\nSkylight after filling shaft:");
		printSkylightColumn(chunk, 8, 8, 75, 55);
		
		// Verify skylight was removed
		topLight = chunk.getSkyLight(8, topOfShaft, 8);
		midLight = chunk.getSkyLight(8, midShaft, 8);
		bottomLight = chunk.getSkyLight(8, bottomOfShaft, 8);
		
		System.out.println("\nVerification:");
		System.out.println("  Former top (y=70): " + topLight);
		System.out.println("  Former middle (y=65): " + midLight);
		System.out.println("  Former bottom (y=60): " + bottomLight);
		
		verify(topLight == 0, "Filled shaft should have no skylight");
		verify(midLight == 0, "Filled shaft should have no skylight");
		verify(bottomLight == 0, "Filled shaft should have no skylight");
		
		System.out.println("\n--- Test 3: Heightmap Updates ---");
		
		// Check heightmap
		int heightmapBefore = chunk.getHeightmap().getHeight(8, 8);
		System.out.println("Heightmap before: " + heightmapBefore);
		
		// Remove top block
		chunk.setBlock(8, LevelChunk.worldYToChunkY(70), 8, Blocks.AIR);
		
		int heightmapAfter = chunk.getHeightmap().getHeight(8, 8);
		System.out.println("Heightmap after removing top: " + heightmapAfter);
		verify(heightmapAfter < heightmapBefore, "Heightmap should decrease");
		
		// Add block on top
		chunk.setBlock(8, LevelChunk.worldYToChunkY(71), 8, Blocks.STONE);
		
		int heightmapFinal = chunk.getHeightmap().getHeight(8, 8);
		System.out.println("Heightmap after placing higher: " + heightmapFinal);
		verify(heightmapFinal > heightmapAfter, "Heightmap should increase");
		
		level.shutdown();
	}
	
	private static void printSkylightColumn(LevelChunk chunk, int x, int z, int startY, int endY) {
		System.out.println("  Y    Block           Skylight");
		System.out.println("  ---  --------------  --------");
		for (int worldY = startY; worldY >= endY; worldY--) {
			int chunkY = LevelChunk.worldYToChunkY(worldY);
			if (chunkY < 0 || chunkY >= LevelChunk.HEIGHT) continue;
			
			Block block = chunk.getBlock(x, chunkY, z);
			int skylight = chunk.getSkyLight(x, chunkY, z);
			String blockName = block.getIdentifier();
			if (blockName == null) blockName = "null";
			blockName = blockName.replace("mattmc:", "");
			
			System.out.printf("  %-4d %-15s %d%n", worldY, blockName, skylight);
		}
	}
	
	private static void verify(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError("VERIFICATION FAILED: " + message);
		}
		System.out.println("  ✓ " + message);
	}
	
	private static void deleteRecursively(Path path) throws IOException {
		if (Files.isDirectory(path)) {
			Files.list(path).forEach(child -> {
				try {
					deleteRecursively(child);
				} catch (IOException e) {
					e.printStackTrace();
				}
			});
		}
		Files.deleteIfExists(path);
	}
}
