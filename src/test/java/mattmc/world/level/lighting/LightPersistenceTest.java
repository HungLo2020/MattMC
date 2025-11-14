package mattmc.world.level.lighting;

import mattmc.world.level.Level;
import mattmc.world.level.block.Block;
import mattmc.world.level.block.Blocks;
import mattmc.world.level.chunk.LevelChunk;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Manual test for light data persistence.
 * This test creates a world, sets some light values, saves it,
 * then reloads and verifies the light values persisted.
 * 
 * Run with: ./gradlew runDebugTest
 * or: java -cp build/classes/java/test:build/classes/java/main mattmc.world.level.lighting.LightPersistenceTest
 */
public class LightPersistenceTest {
	
	public static void main(String[] args) throws IOException {
		System.out.println("=== Light Data Persistence Test ===\n");
		
		// Create a temporary directory for the test world
		Path worldDir = Files.createTempDirectory("mattmc-light-test-");
		System.out.println("Test world directory: " + worldDir);
		
		try {
			// Phase 1: Create and save a world with custom light values
			System.out.println("\n--- Phase 1: Creating and saving world ---");
			createAndSaveWorld(worldDir);
			
			// Phase 2: Load the world and verify light values
			System.out.println("\n--- Phase 2: Loading and verifying world ---");
			loadAndVerifyWorld(worldDir);
			
			System.out.println("\n=== TEST PASSED ===");
			System.out.println("All light values persisted correctly!");
			
		} finally {
			// Cleanup
			deleteRecursively(worldDir);
			System.out.println("\nCleaned up test world directory.");
		}
	}
	
	private static void createAndSaveWorld(Path worldDir) throws IOException {
		Level level = new Level();
		level.setWorldDirectory(worldDir);
		level.setSeed(12345L);
		
		// Get a chunk and set some blocks with custom light values
		LevelChunk chunk = level.getChunk(0, 0);
		
		// Set blocks
		chunk.setBlock(5, 64, 5, Blocks.STONE);
		chunk.setBlock(10, 100, 10, Blocks.DIRT);
		chunk.setBlock(3, 150, 7, Blocks.TORCH); // Emits light
		
		// Set custom light values
		System.out.println("Setting light values:");
		
		// Location 1: (5, 64, 5)
		chunk.setSkyLight(5, 64, 5, 12);
		chunk.setBlockLight(5, 64, 5, 3);
		System.out.println("  (5, 64, 5): sky=12, block=3");
		
		// Location 2: (10, 100, 10)
		chunk.setSkyLight(10, 100, 10, 7);
		chunk.setBlockLight(10, 100, 10, 9);
		System.out.println("  (10, 100, 10): sky=7, block=9");
		
		// Location 3: (3, 150, 7) - torch location
		chunk.setSkyLight(3, 150, 7, 2);
		chunk.setBlockLight(3, 150, 7, 14); // Bright block light from torch
		System.out.println("  (3, 150, 7): sky=2, block=14 (torch)");
		
		// Location 4: Default values (15, 180, 8)
		System.out.println("  (15, 180, 8): sky=" + chunk.getSkyLight(15, 180, 8) + 
		                   ", block=" + chunk.getBlockLight(15, 180, 8) + " (defaults)");
		
		// Trigger save by unloading chunks
		level.updateChunksAroundPlayer(1000, 1000);
		level.shutdown();
		
		System.out.println("World saved successfully.");
	}
	
	private static void loadAndVerifyWorld(Path worldDir) throws IOException {
		Level level = new Level();
		level.setWorldDirectory(worldDir);
		level.setSeed(12345L);
		
		// Load the chunk
		LevelChunk chunk = level.getChunk(0, 0);
		
		System.out.println("Verifying light values:");
		
		// Verify blocks
		Block block1 = chunk.getBlock(5, 64, 5);
		Block block2 = chunk.getBlock(10, 100, 10);
		Block block3 = chunk.getBlock(3, 150, 7);
		
		System.out.println("  Block at (5, 64, 5): " + block1.getIdentifier());
		System.out.println("  Block at (10, 100, 10): " + block2.getIdentifier());
		System.out.println("  Block at (3, 150, 7): " + block3.getIdentifier());
		
		// Verify light values
		int sky1 = chunk.getSkyLight(5, 64, 5);
		int block1Light = chunk.getBlockLight(5, 64, 5);
		System.out.println("  (5, 64, 5): sky=" + sky1 + ", block=" + block1Light);
		verify(sky1 == 12, "Sky light at (5,64,5) should be 12");
		verify(block1Light == 3, "Block light at (5,64,5) should be 3");
		
		int sky2 = chunk.getSkyLight(10, 100, 10);
		int block2Light = chunk.getBlockLight(10, 100, 10);
		System.out.println("  (10, 100, 10): sky=" + sky2 + ", block=" + block2Light);
		verify(sky2 == 7, "Sky light at (10,100,10) should be 7");
		verify(block2Light == 9, "Block light at (10,100,10) should be 9");
		
		int sky3 = chunk.getSkyLight(3, 150, 7);
		int block3Light = chunk.getBlockLight(3, 150, 7);
		System.out.println("  (3, 150, 7): sky=" + sky3 + ", block=" + block3Light);
		verify(sky3 == 2, "Sky light at (3,150,7) should be 2");
		verify(block3Light == 14, "Block light at (3,150,7) should be 14");
		
		// Verify default values
		int skyDefault = chunk.getSkyLight(15, 180, 8);
		int blockDefault = chunk.getBlockLight(15, 180, 8);
		System.out.println("  (15, 180, 8): sky=" + skyDefault + ", block=" + blockDefault + " (defaults)");
		verify(skyDefault == 15, "Default sky light should be 15");
		verify(blockDefault == 0, "Default block light should be 0");
		
		// Test Block API
		System.out.println("\nVerifying Block API:");
		System.out.println("  STONE emission: " + Blocks.STONE.getLightEmission());
		System.out.println("  STONE opacity: " + Blocks.STONE.getOpacity());
		System.out.println("  TORCH emission: " + Blocks.TORCH.getLightEmission());
		System.out.println("  TORCH opacity: " + Blocks.TORCH.getOpacity());
		System.out.println("  AIR opacity: " + Blocks.AIR.getOpacity());
		
		level.shutdown();
	}
	
	private static void verify(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError("VERIFICATION FAILED: " + message);
		}
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
