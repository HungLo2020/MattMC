package mattmc.world.level.lighting;

import mattmc.world.level.block.Blocks;
import mattmc.world.level.chunk.LevelChunk;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite to verify RGB light removal works correctly.
 * This tests the fix for the bug where colored light (like torches)
 * would sometimes not be removed properly when the light source was broken.
 */
public class RGBLightRemovalTest {
	
	@Test
	public void testRemoveRGBBlockLight() {
		LevelChunk chunk = new LevelChunk(0, 0);
		LightPropagator propagator = new LightPropagator();
		
		int y = LevelChunk.worldYToChunkY(64);
		
		// Add RGB light (like a torch with R=11, G=9, B=0)
		int r = 11, g = 9, b = 0;
		propagator.addBlockLightRGB(chunk, 8, y, 8, r, g, b);
		
		// Verify light is present
		assertEquals(11, chunk.getBlockLightR(8, y, 8), "Source should have R=11");
		assertEquals(9, chunk.getBlockLightG(8, y, 8), "Source should have G=9");
		assertEquals(0, chunk.getBlockLightB(8, y, 8), "Source should have B=0");
		assertEquals(11, chunk.getBlockLight(8, y, 8), "Intensity should be max(R,G,B)=11");
		
		// Check propagated light
		assertTrue(chunk.getBlockLightR(9, y, 8) > 0, "Light should propagate in R channel");
		assertTrue(chunk.getBlockLightG(9, y, 8) > 0, "Light should propagate in G channel");
		assertEquals(0, chunk.getBlockLightB(9, y, 8), "B channel should stay 0");
		
		// Remove the light source
		propagator.removeBlockLight(chunk, 8, y, 8);
		
		// All RGB channels should be removed at source
		assertEquals(0, chunk.getBlockLightR(8, y, 8), "R should be removed");
		assertEquals(0, chunk.getBlockLightG(8, y, 8), "G should be removed");
		assertEquals(0, chunk.getBlockLightB(8, y, 8), "B should be removed");
		assertEquals(0, chunk.getBlockLight(8, y, 8), "Intensity should be 0");
		
		// Propagated light should also be removed
		assertEquals(0, chunk.getBlockLightR(9, y, 8), "Propagated R should be removed");
		assertEquals(0, chunk.getBlockLightG(9, y, 8), "Propagated G should be removed");
		assertEquals(0, chunk.getBlockLightB(9, y, 8), "Propagated B should be removed");
		assertEquals(0, chunk.getBlockLight(9, y, 8), "Propagated intensity should be 0");
	}
	
	@Test
	public void testTorchPlaceAndRemove() {
		LevelChunk chunk = new LevelChunk(0, 0);
		int y = LevelChunk.worldYToChunkY(64);
		
		// Place a torch (RGB=11,9,0)
		chunk.setBlock(8, y, 8, Blocks.TORCH);
		
		// Verify torch light is present
		int rAtSource = chunk.getBlockLightR(8, y, 8);
		int gAtSource = chunk.getBlockLightG(8, y, 8);
		int bAtSource = chunk.getBlockLightB(8, y, 8);
		
		assertTrue(rAtSource > 0, "Torch should emit red light");
		assertTrue(gAtSource > 0, "Torch should emit green light");
		assertTrue(bAtSource == 0, "Torch should not emit blue light (orange color)");
		
		// Verify light propagates
		int rNeighbor = chunk.getBlockLightR(9, y, 8);
		int gNeighbor = chunk.getBlockLightG(9, y, 8);
		
		assertTrue(rNeighbor > 0, "Red light should propagate");
		assertTrue(gNeighbor > 0, "Green light should propagate");
		assertTrue(rNeighbor < rAtSource, "Light should attenuate");
		assertTrue(gNeighbor < gAtSource, "Light should attenuate");
		
		// Remove the torch
		chunk.setBlock(8, y, 8, Blocks.AIR);
		
		// All light should be removed
		assertEquals(0, chunk.getBlockLightR(8, y, 8), "R should be removed at source");
		assertEquals(0, chunk.getBlockLightG(8, y, 8), "G should be removed at source");
		assertEquals(0, chunk.getBlockLightB(8, y, 8), "B should be removed at source");
		assertEquals(0, chunk.getBlockLightR(9, y, 8), "R should be removed at neighbor");
		assertEquals(0, chunk.getBlockLightG(9, y, 8), "G should be removed at neighbor");
		assertEquals(0, chunk.getBlockLightB(9, y, 8), "B should be removed at neighbor");
	}
	
	@Test
	public void testMultipleTorchRemoval() {
		LevelChunk chunk = new LevelChunk(0, 0);
		int y = LevelChunk.worldYToChunkY(64);
		
		// Place two torches far enough apart (13 blocks ensures no overlap)
		chunk.setBlock(2, y, 8, Blocks.TORCH);
		chunk.setBlock(15, y, 8, Blocks.TORCH);
		
		// Both should have light
		assertTrue(chunk.getBlockLightR(2, y, 8) > 0, "First torch has light");
		assertTrue(chunk.getBlockLightR(15, y, 8) > 0, "Second torch has light");
		
		// Remove first torch
		chunk.setBlock(2, y, 8, Blocks.AIR);
		
		// First torch area should be dark (no light from second torch reaches here)
		assertEquals(0, chunk.getBlockLightR(2, y, 8), "First torch removed");
		
		// Second torch should still have light
		assertTrue(chunk.getBlockLightR(15, y, 8) > 0, "Second torch still has light");
		
		// Remove second torch
		chunk.setBlock(15, y, 8, Blocks.AIR);
		
		// Both should be dark
		assertEquals(0, chunk.getBlockLightR(2, y, 8), "First position dark");
		assertEquals(0, chunk.getBlockLightR(15, y, 8), "Second position dark");
	}
	
	@Test
	public void testRepeatedPlaceAndRemove() {
		LevelChunk chunk = new LevelChunk(0, 0);
		int y = LevelChunk.worldYToChunkY(64);
		
		// Test the same position multiple times to catch any state issues
		for (int i = 0; i < 5; i++) {
			// Place torch
			chunk.setBlock(8, y, 8, Blocks.TORCH);
			
			// Verify light exists
			assertTrue(chunk.getBlockLightR(8, y, 8) > 0, 
				"Iteration " + i + ": Torch should emit light");
			assertTrue(chunk.getBlockLightR(9, y, 8) > 0, 
				"Iteration " + i + ": Light should propagate");
			
			// Remove torch
			chunk.setBlock(8, y, 8, Blocks.AIR);
			
			// Verify light is removed
			assertEquals(0, chunk.getBlockLightR(8, y, 8), 
				"Iteration " + i + ": Light should be removed at source");
			assertEquals(0, chunk.getBlockLightR(9, y, 8), 
				"Iteration " + i + ": Light should be removed at neighbor");
		}
	}
}
