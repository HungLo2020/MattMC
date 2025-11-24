package mattmc.world.level.lighting;

import mattmc.client.renderer.block.BlockFaceCollector;
import mattmc.client.renderer.chunk.VertexLightSampler;
import mattmc.world.level.Level;
import mattmc.world.level.block.Blocks;
import mattmc.world.level.chunk.LevelChunk;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test to verify that interior corners between blocks are not too dark
 * and that ceiling (bottom face) lighting works correctly.
 * 
 * This tests the fixes for:
 * 1. Interior corners in caves being very dark even with nearby light sources
 * 2. Ceiling blocks being perfectly dark when they should receive light from below
 */
public class InteriorCornerLightingTest {
	
	@Test
	public void testInteriorCornerWithNearbyTorch() {
		// Create a test world
		Level level = new Level();
		LevelChunk chunk = level.getChunk(0, 0);
		
		// Create a simple interior corner scenario:
		// Place blocks in an L-shape to create an interior corner at (8, 64, 8)
		int y = LevelChunk.worldYToChunkY(64);
		
		// Block at (8, 64, 9) - south
		chunk.setBlock(8, y, 9, Blocks.STONE);
		// Block at (9, 64, 8) - east
		chunk.setBlock(9, y, 8, Blocks.STONE);
		
		// Place a torch nearby at (8, 64, 10) to light the corner
		chunk.setBlock(8, y, 10, Blocks.TORCH);
		
		// Trigger light propagation
		level.getWorldLightManager().updateBlockLight(chunk, 8, y, 10, Blocks.TORCH, Blocks.AIR);
		
		// Now create a FaceData for the top face of block at (8, 64, 8)
		// which is at the interior corner
		BlockFaceCollector.FaceData cornerFace = new BlockFaceCollector.FaceData(
			8f, 64f, 8f, // world position
			0xFFFFFF, // white color
			1.0f, // brightness
			1.0f, // colorBrightness
			Blocks.STONE, // block
			"top", // face type
			null, // blockState
			chunk, // chunk reference
			8, y, 8 // chunk-local coordinates
		);
		
		// Create a VertexLightSampler to test vertex light sampling
		VertexLightSampler lightSampler = new VertexLightSampler();
		
		// Set up a simple light accessor that just uses the chunk
		lightSampler.setLightAccessor(new VertexLightSampler.ChunkLightAccessor() {
			@Override
			public int getSkyLightAcrossChunks(LevelChunk chunk, int x, int y, int z) {
				if (x >= 0 && x < LevelChunk.WIDTH && z >= 0 && z < LevelChunk.DEPTH) {
					return chunk.getSkyLight(x, y, z);
				}
				return 15; // Default skylight
			}
			
			@Override
			public int getBlockLightAcrossChunks(LevelChunk chunk, int x, int y, int z) {
				if (x >= 0 && x < LevelChunk.WIDTH && z >= 0 && z < LevelChunk.DEPTH) {
					return chunk.getBlockLightI(x, y, z);
				}
				return 0; // Default no blocklight
			}
		});
		
		// Sample light at the corner vertex using the public API
		// We're testing that the corner is not too dark
		try {
			// Sample the corner vertex (normalIndex=0 for top face, cornerIndex=0 for first corner)
			float[] lightData = lightSampler.sampleVertexLight(cornerFace, 0, 0);
			
			// lightData = [skyLight, blockLightR, blockLightG, blockLightB, ao]
			float skyLight = lightData[0];
			float blockLightR = lightData[1];
			float blockLightG = lightData[2];
			float blockLightB = lightData[3];
			
			// With the fix, the corner should have reasonable lighting because:
			// 1. Only non-zero samples are averaged
			// 2. The torch nearby provides blocklight
			// 3. Even if some samples are blocked, non-zero ones should dominate
			
			// The blocklight should be > 0 because the torch is nearby
			float totalBlockLight = blockLightR + blockLightG + blockLightB;
			
			// We expect at least SOME blocklight from the torch
			// (The exact value depends on propagation, but should be > 0)
			assertTrue(totalBlockLight > 0, 
				"Interior corner should have blocklight from nearby torch, but got 0");
			
			System.out.println("Interior Corner Light Test:");
			System.out.println("  SkyLight: " + skyLight);
			System.out.println("  BlockLight RGB: (" + blockLightR + ", " + blockLightG + ", " + blockLightB + ")");
			System.out.println("  Total BlockLight: " + totalBlockLight);
			System.out.println("  ✓ Corner is properly lit (not too dark)");
			
		} catch (RuntimeException e) {
			throw new RuntimeException("Failed to test vertex light sampling", e);
		}
	}
	
	@Test
	public void testNonZeroLightSamplesAveraged() {
		// This test verifies that only non-zero samples are averaged
		// Create a scenario where some samples would be 0 (solid blocks)
		// and some would be non-zero (air with light)
		
		Level level = new Level();
		LevelChunk chunk = level.getChunk(0, 0);
		
		int y = LevelChunk.worldYToChunkY(64);
		
		// Place blocks to create a corner scenario
		// Block at (8, 65, 8) - directly above our test position
		chunk.setBlock(8, y + 1, 8, Blocks.STONE);
		// Block at (7, 65, 8) - west above
		chunk.setBlock(7, y + 1, 8, Blocks.STONE);
		
		// Set some skylight in air blocks
		chunk.setSkyLight(8, y + 1, 7, 15); // North above - air with full skylight
		chunk.setSkyLight(7, y + 1, 7, 15); // Northwest diagonal - air with full skylight
		
		// Create a face for testing
		BlockFaceCollector.FaceData testFace = new BlockFaceCollector.FaceData(
			8f, 64f, 8f, 0xFFFFFF, 1.0f, 1.0f, Blocks.STONE, "top", null, chunk, 8, y, 8
		);
		
		VertexLightSampler lightSampler = new VertexLightSampler();
		lightSampler.setLightAccessor(new VertexLightSampler.ChunkLightAccessor() {
			@Override
			public int getSkyLightAcrossChunks(LevelChunk chunk, int x, int y, int z) {
				if (x >= 0 && x < LevelChunk.WIDTH && z >= 0 && z < LevelChunk.DEPTH) {
					return chunk.getSkyLight(x, y, z);
				}
				return 15;
			}
			
			@Override
			public int getBlockLightAcrossChunks(LevelChunk chunk, int x, int y, int z) {
				if (x >= 0 && x < LevelChunk.WIDTH && z >= 0 && z < LevelChunk.DEPTH) {
					return chunk.getBlockLightI(x, y, z);
				}
				return 0;
			}
		});
		
		try {
			// Sample corner 0 of the top face
			float[] lightData = lightSampler.sampleVertexLight(testFace, 0, 0);
			float skyLight = lightData[0];
			
			// With the fix, skylight should be averaged from non-zero samples only
			// In this test: 2 samples are in solid blocks (0), 2 samples are in air (15)
			// Old behavior: (0 + 0 + 15 + 15) / 4 = 7.5
			// New behavior: (15 + 15) / 2 = 15.0
			
			// We expect skylight to be higher than 10 (closer to 15 than 7.5)
			assertTrue(skyLight >= 10.0f, 
				"Skylight should average only non-zero samples, expected >= 10, got " + skyLight);
			
			System.out.println("Non-Zero Sample Averaging Test:");
			System.out.println("  SkyLight: " + skyLight);
			System.out.println("  ✓ Only non-zero samples are averaged");
			
		} catch (RuntimeException e) {
			throw new RuntimeException("Failed to test vertex light sampling", e);
		}
	}
	
	@Test
	public void testCeilingBottomFaceLighting() {
		// This test verifies that ceiling (bottom face) blocks receive light from the air below
		// Previously, bottom faces sampled at Y=0 (inside the block) instead of Y=-1 (below)
		
		Level level = new Level();
		LevelChunk chunk = level.getChunk(0, 0);
		
		int y = LevelChunk.worldYToChunkY(65);
		
		// Create a horizontal shaft scenario:
		// Air at Y=64 with skylight
		// Block at Y=65 (ceiling of the shaft)
		
		// Set skylight in the air below the ceiling block
		chunk.setSkyLight(8, y - 1, 8, 15); // Air directly below - full skylight
		chunk.setSkyLight(7, y - 1, 8, 15); // Air to the west below
		chunk.setSkyLight(8, y - 1, 7, 15); // Air to the north below
		chunk.setSkyLight(7, y - 1, 7, 15); // Air to the northwest below
		
		// Place a ceiling block at Y=65
		chunk.setBlock(8, y, 8, Blocks.STONE);
		
		// Create a face for the bottom (ceiling) of this block
		BlockFaceCollector.FaceData ceilingFace = new BlockFaceCollector.FaceData(
			8f, 65f, 8f, 0xFFFFFF, 1.0f, 0.5f, Blocks.STONE, "bottom", null, chunk, 8, y, 8
		);
		
		VertexLightSampler lightSampler = new VertexLightSampler();
		lightSampler.setLightAccessor(new VertexLightSampler.ChunkLightAccessor() {
			@Override
			public int getSkyLightAcrossChunks(LevelChunk chunk, int x, int y, int z) {
				if (x >= 0 && x < LevelChunk.WIDTH && z >= 0 && z < LevelChunk.DEPTH && 
				    y >= 0 && y < LevelChunk.HEIGHT) {
					return chunk.getSkyLight(x, y, z);
				}
				return 15;
			}
			
			@Override
			public int getBlockLightAcrossChunks(LevelChunk chunk, int x, int y, int z) {
				if (x >= 0 && x < LevelChunk.WIDTH && z >= 0 && z < LevelChunk.DEPTH && 
				    y >= 0 && y < LevelChunk.HEIGHT) {
					return chunk.getBlockLightI(x, y, z);
				}
				return 0;
			}
		});
		
		try {
			// Sample the bottom face (normalIndex=1 for bottom, cornerIndex=0 for first corner)
			float[] lightData = lightSampler.sampleVertexLight(ceilingFace, 1, 0);
			float skyLight = lightData[0];
			
			// With the fix, the ceiling should have skylight because it samples from Y-1 (the air below)
			// All 4 samples should be from air with skylight=15
			// Expected: 15.0
			
			assertTrue(skyLight >= 10.0f, 
				"Ceiling should receive skylight from air below, expected >= 10, got " + skyLight);
			
			System.out.println("Ceiling Bottom Face Lighting Test:");
			System.out.println("  Ceiling SkyLight: " + skyLight);
			System.out.println("  ✓ Ceiling receives light from air space below");
			
		} catch (RuntimeException e) {
			throw new RuntimeException("Failed to test ceiling lighting", e);
		}
	}
}
