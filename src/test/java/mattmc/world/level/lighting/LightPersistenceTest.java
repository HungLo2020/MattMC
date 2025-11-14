package mattmc.world.level.lighting;

import mattmc.world.level.Level;
import mattmc.world.level.block.Block;
import mattmc.world.level.block.Blocks;
import mattmc.world.level.chunk.LevelChunk;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Manual test for heightmap and skylight initialization.
 * 
 * This test creates a world, generates terrain, and prints skylight values
 * to verify that skylight is properly initialized based on the heightmap.
 * 
 * Run with: ./gradlew runDebugTest
 */
public class LightPersistenceTest {
	
	public static void main(String[] args) throws IOException {
		System.out.println("=== Heightmap + Skylight Initialization Test ===\n");
		
		// Create a temporary directory for the test world
		Path worldDir = Files.createTempDirectory("mattmc-skylight-test-");
		System.out.println("Test world directory: " + worldDir);
		
		try {
			testSkylightInitialization(worldDir);
			System.out.println("\n=== TEST PASSED ===");
			System.out.println("Skylight values are correctly initialized!");
			
		} finally {
			// Cleanup
			deleteRecursively(worldDir);
			System.out.println("\nCleaned up test world directory.");
		}
	}
	
	private static void testSkylightInitialization(Path worldDir) throws IOException {
		Level level = new Level();
		level.setWorldDirectory(worldDir);
		level.setSeed(12345L);
		
		System.out.println("\n--- Generating Chunks ---");
		
		// Generate a few chunks (flat world generation will create terrain)
		LevelChunk chunk1 = level.getChunk(0, 0);
		LevelChunk chunk2 = level.getChunk(1, 0);
		LevelChunk chunk3 = level.getChunk(0, 1);
		
		System.out.println("Generated 3 chunks");
		
		// Inspect heightmap and skylight for several columns in chunk (0, 0)
		System.out.println("\n--- Chunk (0, 0) Column Analysis ---");
		inspectColumn(chunk1, 0, 0);
		inspectColumn(chunk1, 8, 8);
		inspectColumn(chunk1, 15, 15);
		
		// Save the world
		System.out.println("\n--- Saving World ---");
		level.updateChunksAroundPlayer(1000, 1000);
		level.shutdown();
		System.out.println("World saved");
		
		// Reload the world
		System.out.println("\n--- Reloading World ---");
		Level level2 = new Level();
		level2.setWorldDirectory(worldDir);
		level2.setSeed(12345L);
		
		LevelChunk reloadedChunk = level2.getChunk(0, 0);
		System.out.println("Reloaded chunk (0, 0)");
		
		// Verify heightmap and skylight persisted
		System.out.println("\n--- Reloaded Chunk (0, 0) Verification ---");
		inspectColumn(reloadedChunk, 0, 0);
		inspectColumn(reloadedChunk, 8, 8);
		inspectColumn(reloadedChunk, 15, 15);
		
		level2.shutdown();
	}
	
	/**
	 * Inspect and print heightmap and skylight values for a column.
	 */
	private static void inspectColumn(LevelChunk chunk, int x, int z) {
		int heightmapY = chunk.getHeightmap().getHeight(x, z);
		System.out.println("\nColumn (" + x + ", " + z + "):");
		System.out.println("  Heightmap: " + heightmapY);
		
		// Find the surface block
		int surfaceChunkY = LevelChunk.worldYToChunkY(heightmapY);
		Block surfaceBlock = chunk.getBlock(x, surfaceChunkY, z);
		System.out.println("  Surface block: " + surfaceBlock.getIdentifier());
		
		// Check skylight above, at, and below surface
		int aboveY = Math.min(heightmapY + 5, LevelChunk.MAX_Y);
		int aboveChunkY = LevelChunk.worldYToChunkY(aboveY);
		int skyAbove = chunk.getSkyLight(x, aboveChunkY, z);
		System.out.println("  Skylight 5 above surface (Y=" + aboveY + "): " + skyAbove);
		
		int skyAtSurface = chunk.getSkyLight(x, surfaceChunkY, z);
		System.out.println("  Skylight at surface (Y=" + heightmapY + "): " + skyAtSurface);
		
		int belowY = Math.max(heightmapY - 5, LevelChunk.MIN_Y);
		int belowChunkY = LevelChunk.worldYToChunkY(belowY);
		int skyBelow = chunk.getSkyLight(x, belowChunkY, z);
		System.out.println("  Skylight 5 below surface (Y=" + belowY + "): " + skyBelow);
		
		// Verify expectations
		if (skyAbove != 15) {
			throw new AssertionError("Skylight above surface should be 15, got " + skyAbove);
		}
		if (skyAtSurface != 0) {
			throw new AssertionError("Skylight at surface should be 0, got " + skyAtSurface);
		}
		if (skyBelow != 0) {
			throw new AssertionError("Skylight below surface should be 0, got " + skyBelow);
		}
		
		System.out.println("  ✓ Skylight values correct!");
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
