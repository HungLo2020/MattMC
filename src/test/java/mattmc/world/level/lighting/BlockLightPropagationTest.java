package mattmc.world.level.lighting;

import mattmc.world.level.Level;
import mattmc.registries.Blocks;
import mattmc.world.level.chunk.LevelChunk;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Manual test for blockLight propagation.
 * 
 * This test demonstrates placing and removing emissive blocks (torches)
 * and shows how blockLight propagates and is removed using BFS queues.
 * 
 * Run with: ./gradlew runDebugTest
 */
public class BlockLightPropagationTest {
	
	public static void main(String[] args) throws IOException {
		System.out.println("=== BlockLight Propagation Test ===\n");
		
		// Create a temporary directory for the test world
		Path worldDir = Files.createTempDirectory("mattmc-blocklight-test-");
		System.out.println("Test world directory: " + worldDir);
		
		try {
			testBlockLightPropagation(worldDir);
			System.out.println("\n=== TEST PASSED ===");
			System.out.println("BlockLight propagation working correctly!");
			
		} finally {
			// Cleanup
			deleteRecursively(worldDir);
			System.out.println("\nCleaned up test world directory.");
		}
	}
	
	private static void testBlockLightPropagation(Path worldDir) throws IOException {
		Level level = new Level();
		level.setWorldDirectory(worldDir);
		level.setSeed(12345L);
		
		System.out.println("\n--- Test 1: Place a Torch ---");
		
		// Get a chunk
		LevelChunk chunk = level.getChunk(0, 0);
		
		int torchY = LevelChunk.worldYToChunkY(64);
		
		// Place a torch at (8, 64, 8)
		System.out.println("Placing torch at (8, 64, 8)...");
		chunk.setBlock(8, torchY, 8, Blocks.TORCH);
		
		// Verify torch emission
		int emission = Math.max(Blocks.TORCH.getLightEmissionR(), Math.max(Blocks.TORCH.getLightEmissionG(), Blocks.TORCH.getLightEmissionB()));
		System.out.println("Torch light emission: " + emission);
		
		// Check blockLight propagation
		System.out.println("\nBlockLight values around torch:");
		printBlockLightAt(chunk, 8, torchY, 8, "Source (8,64,8)");
		printBlockLightAt(chunk, 9, torchY, 8, "1 block away +X");
		printBlockLightAt(chunk, 7, torchY, 8, "1 block away -X");
		printBlockLightAt(chunk, 10, torchY, 8, "2 blocks away +X");
		printBlockLightAt(chunk, 6, torchY, 8, "2 blocks away -X");
		printBlockLightAt(chunk, 8, torchY + 1, 8, "1 block up");
		printBlockLightAt(chunk, 8, torchY - 1, 8, "1 block down");
		printBlockLightAt(chunk, 9, torchY, 9, "Diagonal");
		
		// Verify expected values
		int sourceLight = chunk.getBlockLightI(8, torchY, 8);
		int neighborLight = chunk.getBlockLightI(9, torchY, 8);
		int distance2Light = chunk.getBlockLightI(10, torchY, 8);
		
		System.out.println("\nVerification:");
		System.out.println("  Source light: " + sourceLight + " (expected: " + emission + ")");
		verify(sourceLight == emission, "Source should have full emission");
		
		System.out.println("  Neighbor light: " + neighborLight + " (expected: " + (emission - 1) + ")");
		verify(neighborLight == emission - 1, "Neighbor should have emission - 1");
		
		System.out.println("  Distance 2 light: " + distance2Light + " (expected: " + (emission - 2) + ")");
		verify(distance2Light == emission - 2, "Distance 2 should have emission - 2");
		
		System.out.println("\n--- Test 2: Remove the Torch ---");
		
		// Remove the torch
		System.out.println("Removing torch...");
		chunk.setBlock(8, torchY, 8, Blocks.AIR);
		
		// Check that blockLight is removed
		System.out.println("\nBlockLight values after removal:");
		printBlockLightAt(chunk, 8, torchY, 8, "Former source");
		printBlockLightAt(chunk, 9, torchY, 8, "Former neighbor");
		printBlockLightAt(chunk, 10, torchY, 8, "Former distance 2");
		
		int removedSourceLight = chunk.getBlockLightI(8, torchY, 8);
		int removedNeighborLight = chunk.getBlockLightI(9, torchY, 8);
		
		System.out.println("\nVerification:");
		System.out.println("  Former source light: " + removedSourceLight + " (expected: 0)");
		verify(removedSourceLight == 0, "Light should be removed from source");
		
		System.out.println("  Former neighbor light: " + removedNeighborLight + " (expected: 0)");
		verify(removedNeighborLight == 0, "Light should be removed from neighbors");
		
		System.out.println("\n--- Test 3: Multiple Torches ---");
		
		// Place two torches
		System.out.println("Placing two torches 6 blocks apart...");
		chunk.setBlock(5, torchY, 8, Blocks.TORCH);
		chunk.setBlock(11, torchY, 8, Blocks.TORCH);
		
		System.out.println("\nBlockLight values:");
		printBlockLightAt(chunk, 5, torchY, 8, "Torch 1 at (5,64,8)");
		printBlockLightAt(chunk, 11, torchY, 8, "Torch 2 at (11,64,8)");
		printBlockLightAt(chunk, 8, torchY, 8, "Middle (8,64,8)");
		
		int midLight = chunk.getBlockLightI(8, torchY, 8);
		System.out.println("\nMiddle position light: " + midLight);
		System.out.println("  (Should be lit from both sources)");
		verify(midLight > 0, "Middle should have light from both sources");
		
		// Remove one torch
		System.out.println("\nRemoving torch 1...");
		chunk.setBlock(5, torchY, 8, Blocks.AIR);
		
		System.out.println("\nBlockLight values after removing one torch:");
		printBlockLightAt(chunk, 5, torchY, 8, "Former torch 1");
		printBlockLightAt(chunk, 11, torchY, 8, "Torch 2 (still there)");
		printBlockLightAt(chunk, 8, torchY, 8, "Middle");
		
		int midLightAfter = chunk.getBlockLightI(8, torchY, 8);
		System.out.println("\nMiddle position light after: " + midLightAfter);
		System.out.println("  (Should still have light from torch 2)");
		verify(midLightAfter > 0, "Middle should still have light from remaining torch");
		
		// Save and reload
		System.out.println("\n--- Test 4: Persistence ---");
		level.updateChunksAroundPlayer(1000, 1000);
		level.shutdown();
		
		Level level2 = new Level();
		level2.setWorldDirectory(worldDir);
		level2.setSeed(12345L);
		
		LevelChunk reloadedChunk = level2.getChunk(0, 0);
		
		System.out.println("Reloaded chunk. Verifying blockLight:");
		printBlockLightAt(reloadedChunk, 11, torchY, 8, "Torch 2 (should persist)");
		printBlockLightAt(reloadedChunk, 8, torchY, 8, "Middle (should persist)");
		
		int persistedLight = reloadedChunk.getBlockLightI(11, torchY, 8);
		verify(persistedLight == emission, "BlockLight should persist after save/load");
		
		level2.shutdown();
	}
	
	private static void printBlockLightAt(LevelChunk chunk, int x, int y, int z, String label) {
		int light = chunk.getBlockLightI(x, y, z);
		System.out.println("  " + label + ": " + light);
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
