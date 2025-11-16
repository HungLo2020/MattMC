package mattmc.world.level.lighting;

import mattmc.client.renderer.block.BlockFaceCollector;
import mattmc.client.renderer.chunk.MeshBuilder;
import mattmc.world.level.Level;
import mattmc.world.level.block.Blocks;
import mattmc.world.level.chunk.LevelChunk;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test to verify that interior corners between blocks are not too dark.
 * 
 * This tests the fix for the issue where interior corners in caves would be
 * very dark even when nearby torches or other light sources existed, because
 * the vertex light sampling was averaging zeros from solid blocks.
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
		WorldLightManager.getInstance().updateBlockLight(chunk, 8, y, 10, Blocks.TORCH, Blocks.AIR);
		
		// Now create a FaceData for the top face of block at (8, 64, 8)
		// which is at the interior corner
		BlockFaceCollector.FaceData cornerFace = new BlockFaceCollector.FaceData(
			8, 64, 8, // world position
			0xFFFFFF, // white color
			1.0f, // brightness
			1.0f, // colorBrightness
			Blocks.STONE, // block
			"top", // face type
			null, // renderer (not needed for this test)
			null, // blockState
			chunk, // chunk reference
			8, y, 8 // chunk-local coordinates
		);
		
		// Create a MeshBuilder to test vertex light sampling
		MeshBuilder meshBuilder = new MeshBuilder(null);
		
		// Set up a simple light accessor that just uses the chunk
		meshBuilder.setLightAccessor(new MeshBuilder.ChunkLightAccessor() {
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
					return chunk.getBlockLight(x, y, z);
				}
				return 0; // Default no blocklight
			}
		});
		
		// Sample light at the corner vertex using reflection to access the private method
		// We're testing that the corner is not too dark
		try {
			java.lang.reflect.Method sampleMethod = MeshBuilder.class.getDeclaredMethod(
				"sampleVertexLight", 
				BlockFaceCollector.FaceData.class, 
				int.class, 
				int.class
			);
			sampleMethod.setAccessible(true);
			
			// Sample the corner vertex (normalIndex=0 for top face, cornerIndex=0 for first corner)
			float[] lightData = (float[]) sampleMethod.invoke(meshBuilder, cornerFace, 0, 0);
			
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
			
		} catch (Exception e) {
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
			8, 64, 8, 0xFFFFFF, 1.0f, 1.0f, Blocks.STONE, "top", null, null, chunk, 8, y, 8
		);
		
		MeshBuilder meshBuilder = new MeshBuilder(null);
		meshBuilder.setLightAccessor(new MeshBuilder.ChunkLightAccessor() {
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
					return chunk.getBlockLight(x, y, z);
				}
				return 0;
			}
		});
		
		try {
			java.lang.reflect.Method sampleMethod = MeshBuilder.class.getDeclaredMethod(
				"sampleVertexLight", 
				BlockFaceCollector.FaceData.class, 
				int.class, 
				int.class
			);
			sampleMethod.setAccessible(true);
			
			// Sample corner 0 of the top face
			float[] lightData = (float[]) sampleMethod.invoke(meshBuilder, testFace, 0, 0);
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
			
		} catch (Exception e) {
			throw new RuntimeException("Failed to test vertex light sampling", e);
		}
	}
}
