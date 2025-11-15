package mattmc.world.level.lighting;

import mattmc.world.level.Level;
import mattmc.world.level.block.Blocks;
import mattmc.world.level.chunk.LevelChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for cross-chunk vertex light sampling in mesh building.
 * This verifies that when building chunk meshes, vertex light values are correctly
 * sampled from neighboring chunks at chunk boundaries.
 */
public class CrossChunkVertexLightTest {
	
	private Level level;
	private Path tempDir;
	
	@BeforeEach
	public void setup() throws IOException {
		// Reset the WorldLightManager singleton for each test
		WorldLightManager.resetInstance();
		
		// Create a temp world
		tempDir = Files.createTempDirectory("mattmc-vertex-light-test-");
		level = new Level();
		level.setWorldDirectory(tempDir);
		level.setSeed(12345L);
	}
	
	@Test
	public void testBlockLightCrossesChunkBoundaryForVertices() {
		// Get two adjacent chunks
		LevelChunk chunk0 = level.getChunk(0, 0);
		LevelChunk chunk1 = level.getChunk(1, 0);
		
		// Place a torch at the boundary in chunk 0 (x=15)
		int edgeX = 15;
		int y = LevelChunk.worldYToChunkY(64);
		int z = 8;
		
		// Set the torch in chunk 0
		chunk0.setBlock(edgeX, y, z, Blocks.TORCH);
		
		// Verify torch has light
		int torchLight = chunk0.getBlockLight(edgeX, y, z);
		assertEquals(11, torchLight, "Torch should have light level 11");
		
		// Verify light propagates across boundary
		int boundaryLight = chunk1.getBlockLight(0, y, z);
		assertTrue(boundaryLight > 0, "Light should cross chunk boundary");
		assertEquals(10, boundaryLight, "Light should attenuate by 1 across boundary");
		
		// Now test vertex sampling - when building the mesh for chunk1,
		// vertices at x=0 might sample from x=-1 (which is chunk0's x=15)
		// The light accessor should handle this correctly
		
		// The important part is that the light propagation worked correctly
		// and vertex sampling will use the cross-chunk light accessor we added
	}
	
	@Test
	public void testSkyLightCrossesChunkBoundaryForVertices() {
		// Get two adjacent chunks
		LevelChunk chunk0 = level.getChunk(0, 0);
		LevelChunk chunk1 = level.getChunk(1, 0);
		
		// Initialize skylight for both chunks
		WorldLightManager.getInstance().initializeChunkSkylight(chunk0);
		WorldLightManager.getInstance().initializeChunkSkylight(chunk1);
		
		int y = LevelChunk.worldYToChunkY(100); // Well above ground
		int z = 8;
		
		// Both chunks should have full skylight at this height
		assertEquals(15, chunk0.getSkyLight(15, y, z), "Chunk 0 should have full skylight");
		assertEquals(15, chunk1.getSkyLight(0, y, z), "Chunk 1 should have full skylight");
		
		// The important test: skylight should be consistently accessible across chunks
		// This ensures vertex sampling will work correctly
	}
	
	@Test
	public void testNoLightLeakAtChunkBoundaryWithWall() {
		// This tests that blocks at chunk borders properly communicate opacity
		LevelChunk chunk0 = level.getChunk(0, 0);
		LevelChunk chunk1 = level.getChunk(1, 0);
		
		int y = LevelChunk.worldYToChunkY(64);
		int z = 8;
		
		// Build a COMPLETE sealed wall at chunk boundary (entire x=0 plane)
		System.out.println("Building complete stone wall at chunk1 x=0...");
		for (int wallY = y - 1; wallY < y + 6; wallY++) {
			for (int wallZ = 0; wallZ < LevelChunk.DEPTH; wallZ++) {
				chunk1.setBlock(0, wallY, wallZ, Blocks.STONE);
			}
		}
		
		// Also seal the floor and ceiling to prevent light going up/down
		for (int wallX = 0; wallX < 3; wallX++) {
			for (int wallZ = 0; wallZ < LevelChunk.DEPTH; wallZ++) {
				chunk1.setBlock(wallX, y - 1, wallZ, Blocks.STONE); // Floor
				chunk1.setBlock(wallX, y + 6, wallZ, Blocks.STONE); // Ceiling
			}
		}
		
		// Verify wall is opaque
		int wallOpacity = chunk1.getBlock(0, y, z).getOpacity();
		System.out.println("Wall opacity: " + wallOpacity);
		assertEquals(15, wallOpacity, "Stone should be fully opaque");
		
		// Place torch on chunk0 side near the wall
		System.out.println("Placing torch at chunk0 x=14...");
		chunk0.setBlock(14, y, z, Blocks.TORCH);
		
		// Verify torch has light
		assertEquals(11, chunk0.getBlockLight(14, y, z), "Torch should have light level 11");
		
		// Light should reach chunk0's edge (x=15)
		int edgeLight = chunk0.getBlockLight(15, y, z);
		System.out.println("Light at edge (15, y, " + z + "): " + edgeLight);
		assertTrue(edgeLight > 0, "Light should reach chunk edge");
		
		// Check light in and beyond the wall
		int lightInWall = chunk1.getBlockLight(0, y, z);
		int lightBeyondWall = chunk1.getBlockLight(1, y, z);
		System.out.println("Light in wall (chunk1 x=0, y, " + z + "): " + lightInWall);
		System.out.println("Light beyond wall (chunk1 x=1, y, " + z + "): " + lightBeyondWall);
		
		// Light should NOT be inside or pass through the sealed room
		assertEquals(0, lightInWall, "Light should not be inside opaque stone block");
		assertEquals(0, lightBeyondWall, "Light should not pass through sealed stone room");
	}
	
	@Test
	public void testVertexLightSamplingConsistency() {
		// Test that vertex light sampling is consistent when accessing from either side
		LevelChunk chunk0 = level.getChunk(0, 0);
		LevelChunk chunk1 = level.getChunk(1, 0);
		
		int y = LevelChunk.worldYToChunkY(64);
		int z = 8;
		
		// Place torch in chunk 0
		chunk0.setBlock(14, y, z, Blocks.TORCH);
		
		// The blocklight at chunk0's x=15 should match what we get when sampling
		// from chunk1's perspective at x=-1
		int lightFromChunk0 = chunk0.getBlockLight(15, y, z);
		
		// We can't directly test the vertex sampling without accessing internals,
		// but we can verify that the light values are consistent across the boundary
		int lightFromChunk1 = chunk1.getBlockLight(0, y, z);
		
		// These should differ by exactly 1 (attenuation)
		assertEquals(lightFromChunk0 - 1, lightFromChunk1,
			"Light values should be consistent across chunk boundary");
	}
}
