package mattmc.world.level.lighting;

import mattmc.world.level.block.Block;
import mattmc.world.level.block.Blocks;
import mattmc.world.level.chunk.LevelChunk;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for LightPropagator.
 * Verifies blockLight propagation with add/remove queues.
 */
public class LightPropagatorTest {
	
	@Test
	public void testAddBlockLightFromTorch() {
		LevelChunk chunk = new LevelChunk(0, 0);
		LightPropagator propagator = new LightPropagator();
		
		// Place a torch at (8, 64, 8) - emits light level 14 (vanilla torch level)
		int torchY = LevelChunk.worldYToChunkY(64);
		int emission = Blocks.TORCH.getLightEmission();
		assertEquals(14, emission, "Torch should emit light level 14");
		
		// Add light from torch
		propagator.addBlockLight(chunk, 8, torchY, 8, emission);
		
		// Verify light at source
		assertEquals(14, chunk.getBlockLight(8, torchY, 8), "Source should have full emission");
		
		// Verify light propagates to neighbors with attenuation
		assertEquals(13, chunk.getBlockLight(9, torchY, 8), "Neighbor +X should have 13");
		assertEquals(13, chunk.getBlockLight(7, torchY, 8), "Neighbor -X should have 13");
		assertEquals(13, chunk.getBlockLight(8, torchY, 9), "Neighbor +Z should have 13");
		assertEquals(13, chunk.getBlockLight(8, torchY, 7), "Neighbor -Z should have 13");
		
		// Verify light propagates to distance 2
		assertEquals(12, chunk.getBlockLight(10, torchY, 8), "Distance 2 should have 12");
		assertEquals(12, chunk.getBlockLight(6, torchY, 8), "Distance 2 should have 12");
		
		// Verify light propagates diagonally
		assertEquals(12, chunk.getBlockLight(9, torchY, 9), "Diagonal neighbor should have 12");
	}
	
	@Test
	public void testLightBlockedByOpaqueBlock() {
		LevelChunk chunk = new LevelChunk(0, 0);
		
		int y = LevelChunk.worldYToChunkY(64);
		
		// Create a complete enclosure around position (10, y, 8)
		// This ensures light cannot reach it from any direction
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				for (int dz = -1; dz <= 1; dz++) {
					if (dx == 0 && dy == 0 && dz == 0) continue; // Skip the center
					chunk.setBlock(10 + dx, y + dy, 8 + dz, Blocks.STONE);
				}
			}
		}
		
		// Clear all light
		for (int x = 0; x < LevelChunk.WIDTH; x++) {
			for (int yy = 0; yy < LevelChunk.HEIGHT; yy++) {
				for (int z = 0; z < LevelChunk.DEPTH; z++) {
					chunk.setBlockLight(x, yy, z, 0);
				}
			}
		}
		
		LightPropagator propagator = new LightPropagator();
		
		// Add light from a torch
		propagator.addBlockLight(chunk, 8, y, 8, 14);
		
		// Light at source
		assertEquals(14, chunk.getBlockLight(8, y, 8), "Source should have full light");
		
		// The enclosed position should have no light
		assertEquals(0, chunk.getBlockLight(10, y, 8), "Fully enclosed position should have no light");
		
		// Light should still propagate in open directions
		assertEquals(13, chunk.getBlockLight(7, y, 8), "Light should propagate backwards");
		assertEquals(13, chunk.getBlockLight(8, y + 1, 8), "Light should propagate up");
	}
	
	@Test
	public void testRemoveBlockLight() {
		LevelChunk chunk = new LevelChunk(0, 0);
		LightPropagator propagator = new LightPropagator();
		
		int y = LevelChunk.worldYToChunkY(64);
		
		// Add light from torch
		propagator.addBlockLight(chunk, 8, y, 8, 14);
		
		// Verify light is present
		assertEquals(14, chunk.getBlockLight(8, y, 8));
		assertEquals(13, chunk.getBlockLight(9, y, 8));
		assertEquals(12, chunk.getBlockLight(10, y, 8));
		
		// Remove the light source
		propagator.removeBlockLight(chunk, 8, y, 8);
		
		// All light should be removed
		assertEquals(0, chunk.getBlockLight(8, y, 8), "Source should have no light");
		assertEquals(0, chunk.getBlockLight(9, y, 8), "Neighbor should have no light");
		assertEquals(0, chunk.getBlockLight(10, y, 8), "Distance 2 should have no light");
	}
	
	@Test
	public void testMultipleLightSources() {
		LevelChunk chunk = new LevelChunk(0, 0);
		LightPropagator propagator = new LightPropagator();
		
		int y = LevelChunk.worldYToChunkY(64);
		
		// Place two torches 10 blocks apart
		propagator.addBlockLight(chunk, 5, y, 8, 14);
		propagator.addBlockLight(chunk, 15, y, 8, 14);
		
		// Verify both sources have light
		assertEquals(14, chunk.getBlockLight(5, y, 8));
		assertEquals(14, chunk.getBlockLight(15, y, 8));
		
		// In the middle, light should come from whichever source is brighter
		// At position 10, it's 5 blocks from each source
		int midLight = chunk.getBlockLight(10, y, 8);
		assertTrue(midLight >= 9, "Middle position should have light from both sources, got " + midLight);
	}
	
	@Test
	public void testRemoveOneOfMultipleSources() {
		LevelChunk chunk = new LevelChunk(0, 0);
		LightPropagator propagator = new LightPropagator();
		
		int y = LevelChunk.worldYToChunkY(64);
		
		// Place two torches close together
		propagator.addBlockLight(chunk, 8, y, 8, 14);
		propagator.addBlockLight(chunk, 10, y, 8, 14);
		
		// Position 9 should have light from both sources
		int lightBefore = chunk.getBlockLight(9, y, 8);
		assertEquals(13, lightBefore, "Position between sources should have 13");
		
		// Remove one torch
		propagator.removeBlockLight(chunk, 8, y, 8);
		
		// Position 9 should still have light from the remaining torch
		int lightAfter = chunk.getBlockLight(9, y, 8);
		assertEquals(13, lightAfter, "Light should remain from the other source");
		
		// But position 7 (on the other side of removed torch) should be darker
		int light7 = chunk.getBlockLight(7, y, 8);
		assertTrue(light7 <= 12, "Far side should be darker without the closer source, got " + light7);
	}
	
	@Test
	public void testUpdateBlockLight() {
		LevelChunk chunk = new LevelChunk(0, 0);
		
		int y = LevelChunk.worldYToChunkY(64);
		
		// Initially place air
		chunk.setBlock(8, y, 8, Blocks.AIR);
		assertEquals(0, chunk.getBlockLight(8, y, 8));
		
		// Place a torch (should automatically propagate light via setBlock hook)
		chunk.setBlock(8, y, 8, Blocks.TORCH);
		
		// Light should be added - torch emits RGB=(14, 11, 0) so intensity = 14
		assertEquals(14, chunk.getBlockLight(8, y, 8), "Torch should emit light");
		assertEquals(13, chunk.getBlockLight(9, y, 8), "Light should propagate");
		
		// Remove the torch
		chunk.setBlock(8, y, 8, Blocks.AIR);
		
		// Light should be removed
		assertEquals(0, chunk.getBlockLight(8, y, 8), "Light should be removed");
		assertEquals(0, chunk.getBlockLight(9, y, 8), "Propagated light should be removed");
	}
	
	@Test
	public void testLightAttenuation() {
		LevelChunk chunk = new LevelChunk(0, 0);
		LightPropagator propagator = new LightPropagator();
		
		int y = LevelChunk.worldYToChunkY(64);
		
		// Place torch
		propagator.addBlockLight(chunk, 5, y, 8, 14);
		
		// Verify attenuation along a line
		assertEquals(14, chunk.getBlockLight(5, y, 8), "Distance 0: 14");
		assertEquals(13, chunk.getBlockLight(6, y, 8), "Distance 1: 13");
		assertEquals(12, chunk.getBlockLight(7, y, 8), "Distance 2: 12");
		assertEquals(11, chunk.getBlockLight(8, y, 8), "Distance 3: 11");
		assertEquals(10, chunk.getBlockLight(9, y, 8), "Distance 4: 10");
		assertEquals(9, chunk.getBlockLight(10, y, 8), "Distance 5: 9");
		
		// Light should fade out after 14 blocks
		int farLight = chunk.getBlockLight(15, y, 8);
		assertTrue(farLight < 5, "Light should be very dim at distance 10, got " + farLight);
	}
	
	@Test
	public void testVerticalPropagation() {
		LevelChunk chunk = new LevelChunk(0, 0);
		LightPropagator propagator = new LightPropagator();
		
		int y = LevelChunk.worldYToChunkY(64);
		
		// Place torch
		propagator.addBlockLight(chunk, 8, y, 8, 14);
		
		// Light should propagate vertically
		assertEquals(13, chunk.getBlockLight(8, y + 1, 8), "Light should propagate up");
		assertEquals(13, chunk.getBlockLight(8, y - 1, 8), "Light should propagate down");
		assertEquals(12, chunk.getBlockLight(8, y + 2, 8), "Light at distance 2 up");
		assertEquals(12, chunk.getBlockLight(8, y - 2, 8), "Light at distance 2 down");
	}
}
