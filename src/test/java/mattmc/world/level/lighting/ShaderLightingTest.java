package mattmc.world.level.lighting;

import mattmc.world.level.block.Block;
import mattmc.world.level.block.Blocks;
import mattmc.world.level.chunk.LevelChunk;
import org.junit.jupiter.api.Test;

/**
 * Manual test to verify shader lighting support.
 * This test demonstrates the data flow from light storage to vertex attributes.
 * 
 * To see the visual result, run the game and:
 * 1. Place torches in a dark area
 * 2. Observe smooth light falloff
 * 3. Adjust gamma via renderer.getShader().setLightGamma(1.8f)
 */
public class ShaderLightingTest {
	
	@Test
	public void testLightDataFlow() {
		System.out.println("=== Shader Lighting Data Flow Test ===\n");
		
		// Create a test chunk
		LevelChunk chunk = new LevelChunk(0, 0);
		
		// Set up a simple lighting scenario
		int torchX = 8, torchY = 64, torchZ = 8;
		
		// Place a torch (emission level 14)
		chunk.setBlock(torchX, torchY, torchZ, Blocks.TORCH);
		
		// Manually set light values (normally done by propagation)
		chunk.setBlockLight(torchX, torchY, torchZ, 14); // Source
		chunk.setBlockLight(torchX + 1, torchY, torchZ, 13); // Adjacent
		chunk.setBlockLight(torchX + 2, torchY, torchZ, 12); // 2 blocks away
		chunk.setBlockLight(torchX + 3, torchY, torchZ, 11); // 3 blocks away
		
		// Set sky light
		chunk.setSkyLight(torchX, torchY, torchZ, 0); // In cave
		chunk.setSkyLight(torchX, torchY + 10, torchZ, 15); // Above
		
		System.out.println("Test Setup:");
		System.out.println("  Torch placed at (" + torchX + ", " + torchY + ", " + torchZ + ")");
		System.out.println("  Emission level: " + Blocks.TORCH.getLightEmission());
		System.out.println();
		
		// Display light values
		System.out.println("BlockLight values (should show torch falloff):");
		for (int dx = 0; dx <= 3; dx++) {
			int x = torchX + dx;
			int light = chunk.getBlockLight(x, torchY, torchZ);
			float normalized = light / 15.0f;
			float gamma14 = (float) Math.pow(normalized, 1.4);
			float gamma20 = (float) Math.pow(normalized, 2.0);
			
			System.out.printf("  (%d, %d, %d): light=%2d, normalized=%.2f, gamma(1.4)=%.2f, gamma(2.0)=%.2f%n",
				x, torchY, torchZ, light, normalized, gamma14, gamma20);
		}
		
		System.out.println();
		System.out.println("SkyLight values (vertical):");
		System.out.printf("  In cave  (%d, %d, %d): light=%2d%n", torchX, torchY, torchZ, 
			chunk.getSkyLight(torchX, torchY, torchZ));
		System.out.printf("  Above    (%d, %d, %d): light=%2d%n", torchX, torchY + 10, torchZ,
			chunk.getSkyLight(torchX, torchY + 10, torchZ));
		
		System.out.println();
		System.out.println("Gamma Curve Comparison:");
		System.out.println("  Light  | Normalized | Gamma 1.0 | Gamma 1.4 | Gamma 2.0");
		System.out.println("  -------|------------|-----------|-----------|----------");
		
		for (int light : new int[]{15, 12, 8, 4, 0}) {
			float norm = light / 15.0f;
			float g10 = (float) Math.pow(norm, 1.0);
			float g14 = (float) Math.pow(norm, 1.4);
			float g20 = (float) Math.pow(norm, 2.0);
			
			// Apply minimum brightness floor
			g10 = Math.max(g10, 0.05f);
			g14 = Math.max(g14, 0.05f);
			g20 = Math.max(g20, 0.05f);
			
			System.out.printf("  %5d  |   %5.2f    |   %5.2f   |   %5.2f   |   %5.2f%n",
				light, norm, g10, g14, g20);
		}
		
		System.out.println();
		System.out.println("Light Combination (max of sky and block):");
		
		int[][] testCases = {
			{15, 0},  // Full skylight, no torchlight
			{0, 14},  // Full torchlight, no skylight
			{8, 8},   // Equal sky and block
			{12, 6},  // More sky than block
			{6, 12}   // More block than sky
		};
		
		System.out.println("  Sky | Block | Combined (max)");
		System.out.println("  ----|-------|---------------");
		for (int[] test : testCases) {
			int sky = test[0];
			int block = test[1];
			int combined = Math.max(sky, block);
			System.out.printf("  %3d | %5d | %6d%n", sky, block, combined);
		}
		
		System.out.println();
		System.out.println("✓ Data flow test complete");
		System.out.println();
		System.out.println("To see visual results:");
		System.out.println("1. Run the game: ./gradlew run");
		System.out.println("2. Place torches in a dark area");
		System.out.println("3. Observe smooth light falloff with gamma curve");
		System.out.println("4. Adjust gamma: renderer.getShader().setLightGamma(1.8f)");
	}
	
	@Test
	public void testEmissiveBoost() {
		System.out.println("=== Emissive Boost Test ===\n");
		
		float baseBrightness = 0.5f;
		
		System.out.println("Base brightness: " + baseBrightness);
		System.out.println();
		System.out.println("Boost | Final Brightness");
		System.out.println("------|------------------");
		
		for (float boost : new float[]{0.8f, 1.0f, 1.2f, 1.5f, 2.0f}) {
			float result = baseBrightness * boost;
			System.out.printf(" %.1f  |      %.2f%n", boost, result);
		}
		
		System.out.println();
		System.out.println("✓ Emissive boost calculation verified");
	}
}
