package mattmc.world.level.lighting;

import mattmc.world.level.Level;
import mattmc.world.level.block.Blocks;
import mattmc.world.level.chunk.LevelChunk;

/**
 * Manual test for ambient occlusion calculation.
 * Demonstrates AO in various scenarios: concave corners, caves, and edges.
 */
public class AOTest {
	
	public static void main(String[] args) {
		System.out.println("=== Ambient Occlusion Test ===\n");
		
		// Create test level
		Level level = new Level(12345L);
		LevelChunk chunk = level.getOrCreateChunk(0, 0);
		
		testConcaveCorner(chunk);
		testCave(chunk);
		testFlat(chunk);
		
		System.out.println("\n=== Tests Complete ===");
		System.out.println("\nTo see AO visually:");
		System.out.println("1. Build: ./gradlew installDist");
		System.out.println("2. Run: ./build/install/MattMC/bin/MattMC");
		System.out.println("3. Build L-shaped structures and caves");
		System.out.println("4. Notice darkening at inside corners");
		System.out.println("\nTo toggle AO strength:");
		System.out.println("  shader.setAOStrength(0.0f); // Off");
		System.out.println("  shader.setAOStrength(0.8f); // Default");
		System.out.println("  shader.setAOStrength(1.0f); // Maximum");
	}
	
	private static void testConcaveCorner(LevelChunk chunk) {
		System.out.println("--- Test 1: Concave Corner (Inside) ---");
		System.out.println("Build an L-shaped structure:");
		System.out.println();
		
		// Clear area
		for (int x = 0; x < 16; x++) {
			for (int y = 60; y < 70; y++) {
				for (int z = 0; z < 16; z++) {
					chunk.setBlock(x, y, z, Blocks.AIR);
				}
			}
		}
		
		// Build L-shape (inside corner at 8,64,8)
		// Horizontal wall (along X)
		for (int x = 6; x <= 10; x++) {
			chunk.setBlock(x, 64, 8, Blocks.STONE);
		}
		// Vertical wall (along Z)
		for (int z = 6; z <= 10; z++) {
			chunk.setBlock(8, 64, z, Blocks.STONE);
		}
		
		System.out.println("L-shape built:");
		System.out.println("  Horizontal wall: X=6-10, Y=64, Z=8");
		System.out.println("  Vertical wall: X=8, Y=64, Z=6-10");
		System.out.println("  Inside corner at (8, 64, 8)");
		System.out.println();
		System.out.println("Expected AO:");
		System.out.println("  - Inside corner vertex: Strong AO (both sides solid)");
		System.out.println("  - Adjacent vertices: Medium AO (one side + corner)");
		System.out.println("  - Outside vertices: Weak/No AO");
		System.out.println();
	}
	
	private static void testCave(LevelChunk chunk) {
		System.out.println("--- Test 2: Cave (Underground Room) ---");
		System.out.println("Create 5x5x5 underground room:");
		System.out.println();
		
		// Fill with stone
		for (int x = 0; x < 16; x++) {
			for (int y = 50; y < 60; y++) {
				for (int z = 0; z < 16; z++) {
					chunk.setBlock(x, y, z, Blocks.STONE);
				}
			}
		}
		
		// Carve out cave
		for (int x = 6; x <= 10; x++) {
			for (int y = 54; y <= 58; y++) {
				for (int z = 6; z <= 10; z++) {
					chunk.setBlock(x, y, z, Blocks.AIR);
				}
			}
		}
		
		System.out.println("Cave carved:");
		System.out.println("  Dimensions: 5x5x5");
		System.out.println("  Position: X=6-10, Y=54-58, Z=6-10");
		System.out.println();
		System.out.println("Expected AO:");
		System.out.println("  - Floor/ceiling edges: Strong AO (corner meets 2 walls)");
		System.out.println("  - Wall corners: Medium to strong AO");
		System.out.println("  - Center of floor/ceiling: Minimal AO");
		System.out.println();
	}
	
	private static void testFlat(LevelChunk chunk) {
		System.out.println("--- Test 3: Flat Surface (Reference) ---");
		System.out.println("Create flat ground:");
		System.out.println();
		
		// Build flat ground
		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				chunk.setBlock(x, 40, z, Blocks.GRASS_BLOCK);
			}
		}
		
		// Clear above
		for (int x = 0; x < 16; x++) {
			for (int y = 41; y < 50; y++) {
				for (int z = 0; z < 16; z++) {
					chunk.setBlock(x, y, z, Blocks.AIR);
				}
			}
		}
		
		System.out.println("Flat ground built:");
		System.out.println("  Ground level: Y=40");
		System.out.println("  Air above: Y=41-49");
		System.out.println();
		System.out.println("Expected AO:");
		System.out.println("  - Top face: No AO (all neighbors are air)");
		System.out.println("  - Side faces: Minimal AO (only bottom neighbor)");
		System.out.println();
	}
}
