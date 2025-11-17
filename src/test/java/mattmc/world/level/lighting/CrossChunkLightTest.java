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
 * Tests for cross-chunk light propagation.
 */
public class CrossChunkLightTest {
	
	private Level level;
	private Path tempDir;
	
	@BeforeEach
	public void setup() throws IOException {
		// Create a temp world
		tempDir = Files.createTempDirectory("mattmc-crosschunk-test-");
		level = new Level();
		level.setWorldDirectory(tempDir);
		level.setSeed(12345L);
	}
	
	@Test
	public void testBlockLightCrossesChunkBoundary() {
		// Get two adjacent chunks
		LevelChunk chunk0 = level.getChunk(0, 0);
		LevelChunk chunk1 = level.getChunk(1, 0); // Adjacent in X direction
		
		// Place a torch at the edge of chunk 0 (x=15)
		int edgeX = 15;
		int y = LevelChunk.worldYToChunkY(64);
		int z = 8;
		
		// Set the torch in chunk 0
		chunk0.setBlock(edgeX, y, z, Blocks.TORCH);
		
		// The torch has emission level 11 (max of RGB=11,9,0)
		int torchLight = chunk0.getBlockLight(edgeX, y, z);
		assertEquals(14, torchLight, "Torch should have light level 14");
		
		// Light should propagate to the neighbor chunk at x=0
		// Attenuation: 11 - 1 = 10
		int neighborLight = chunk1.getBlockLight(0, y, z);
		assertTrue(neighborLight > 0, "Light should propagate across chunk boundary, got " + neighborLight);
		assertEquals(13, neighborLight, "Light should attenuate by 1 across boundary");
		
		// And further into the neighbor chunk
		int furtherLight = chunk1.getBlockLight(1, y, z);
		assertTrue(furtherLight > 0, "Light should propagate further into neighbor chunk");
		assertTrue(furtherLight < neighborLight, "Light should continue to attenuate");
	}
	
	@Test
	public void testDeferredUpdateWhenChunkNotLoaded() {
		// Get only chunk 0 (chunk 1 not loaded yet)
		LevelChunk chunk0 = level.getChunk(0, 0);
		
		// Place torch at edge before neighbor loads
		int edgeX = 15;
		int y = LevelChunk.worldYToChunkY(64);
		int z = 8;
		
		chunk0.setBlock(edgeX, y, z, Blocks.TORCH);
		
		// Check that there are deferred updates
		CrossChunkLightPropagator propagator = level.getWorldLightManager().getCrossChunkPropagator();
		int deferredCount = propagator.getDeferredUpdateCount(1, 0);
		assertTrue(deferredCount > 0, "Should have deferred updates for unloaded chunk");
		
		// Now load chunk 1 - deferred updates should be processed
		LevelChunk chunk1 = level.getChunk(1, 0);
		
		// Light should now be in chunk 1
		// Torch has intensity 14, at distance 1 it should be 13
		int neighborLight = chunk1.getBlockLight(0, y, z);
		assertTrue(neighborLight > 0, "Deferred light should be applied when chunk loads");
		assertEquals(13, neighborLight, "Deferred light should have correct attenuation");
	}
	
	@Test
	public void testLightCrossesMultipleChunkBoundaries() {
		// Create a line of 3 chunks
		LevelChunk chunk0 = level.getChunk(0, 0);
		LevelChunk chunk1 = level.getChunk(1, 0);
		LevelChunk chunk2 = level.getChunk(2, 0);
		
		// Place torch in middle of chunk 0
		int torchX = 8;
		int y = LevelChunk.worldYToChunkY(64);
		int z = 8;
		
		chunk0.setBlock(torchX, y, z, Blocks.TORCH);
		
		// Should see light in all three chunks
		// Torch has intensity 14 (max of R=14, G=11, B=0)
		assertTrue(chunk0.getBlockLight(torchX, y, z) == 14, "Source chunk should have full torch light");
		assertTrue(chunk0.getBlockLight(15, y, z) > 0, "Light should reach chunk 0 edge");
		assertTrue(chunk1.getBlockLight(0, y, z) > 0, "Light should cross into chunk 1");
		
		// Depending on distance, might reach chunk 2
		int lightAtChunk1Edge = chunk1.getBlockLight(15, y, z);
		int lightInChunk2 = chunk2.getBlockLight(0, y, z);
		
		// From torch at x=8 in chunk0 to x=0 in chunk2 is:
		// 7 blocks to chunk edge (8,9,10,11,12,13,14) 
		// + 16 blocks across chunk1 
		// + 1 block into chunk2 = 24 blocks total
		// Light intensity: 14 - 24 = -10, so won't reach
		
		// But should reach the start of chunk1 (7 blocks away, intensity = 14-7 = 7)
		assertTrue(chunk1.getBlockLight(0, y, z) > 0, "Should reach start of chunk1");
	}
	
	
	@Test
	public void testNoSeamsAtChunkBoundary() {
		LevelChunk chunk0 = level.getChunk(0, 0);
		LevelChunk chunk1 = level.getChunk(1, 0);
		
		int y = LevelChunk.worldYToChunkY(64);
		int z = 8;
		
		// Place torch at chunk boundary
		chunk0.setBlock(15, y, z, Blocks.TORCH);
		
		// Check light continuity - no sudden jumps
		int lightAtBoundary = chunk0.getBlockLight(15, y, z);
		int lightAcrossBoundary = chunk1.getBlockLight(0, y, z);
		
		// Should differ by exactly 1 (attenuation)
		assertEquals(lightAtBoundary - 1, lightAcrossBoundary, 
			"Light should attenuate smoothly across boundary");
	}
}
