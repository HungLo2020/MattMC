package mattmc.world.level.lighting;

import mattmc.world.level.block.Blocks;
import mattmc.world.level.chunk.LevelChunk;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test cross-chunk light removal to ensure torches near chunk boundaries
 * properly remove light from neighboring chunks.
 */
public class CrossChunkLightRemovalTest {
	
	@Test
	public void testTorchAtChunkBoundaryRemoval() {
		// Create two adjacent chunks
		LevelChunk chunk0 = new LevelChunk(0, 0);
		LevelChunk chunk1 = new LevelChunk(1, 0);
		
		// Set up cross-chunk propagator with a simple accessor
		CrossChunkLightPropagator crossProp = new CrossChunkLightPropagator();
		crossProp.setNeighborAccessor((chunkX, chunkZ) -> {
			if (chunkX == 0 && chunkZ == 0) return chunk0;
			if (chunkX == 1 && chunkZ == 0) return chunk1;
			return null;
		});
		
		// Wire up the light propagator with cross-chunk support
		LightPropagator propagator = new LightPropagator();
		propagator.setCrossChunkPropagator(crossProp);
		
		int y = LevelChunk.worldYToChunkY(64);
		
		// Fill the area with stone to block skylight
		for (int x = 0; x < LevelChunk.WIDTH; x++) {
			for (int z = 0; z < LevelChunk.DEPTH; z++) {
				for (int yy = 0; yy <= y + 1; yy++) {
					chunk0.setBlock(x, yy, z, Blocks.STONE);
					chunk1.setBlock(x, yy, z, Blocks.STONE);
				}
			}
		}
		
		// Create a room at y
		for (int x = 10; x < LevelChunk.WIDTH; x++) {
			for (int z = 6; z <= 10; z++) {
				chunk0.setBlock(x, y, z, Blocks.AIR);
			}
		}
		for (int x = 0; x < 6; x++) {
			for (int z = 6; z <= 10; z++) {
				chunk1.setBlock(x, y, z, Blocks.AIR);
			}
		}
		
		System.out.println("Skylight at chunk0[15]: " + chunk0.getSkyLight(15, y, 8));
		System.out.println("Blocklight before torch at chunk0[15]: " + chunk0.getBlockLight(15, y, 8));
		
		// Place a torch at the edge of chunk 0 (will propagate into chunk 1)
		int edgeX = 15; // Last block in chunk 0
		
		// Use the propagator to add light (simulating what setBlock would do)
		propagator.addBlockLightRGB(chunk0, edgeX, y, 8, 11, 9, 0); // Torch RGB values
		
		// Verify light in source chunk
		assertTrue(chunk0.getBlockLightR(edgeX, y, 8) > 0, "Torch should emit light in chunk 0");
		
		// Verify light propagated to chunk 1
		int lightInChunk1 = chunk1.getBlockLightR(0, y, 8); // First block in chunk 1
		assertTrue(lightInChunk1 > 0, "Light should propagate to chunk 1, got: " + lightInChunk1);
		
		System.out.println("Light at chunk0[15]: R=" + chunk0.getBlockLightR(edgeX, y, 8) + 
			" G=" + chunk0.getBlockLightG(edgeX, y, 8) + " B=" + chunk0.getBlockLightB(edgeX, y, 8));
		System.out.println("Light at chunk1[0]: R=" + chunk1.getBlockLightR(0, y, 8) +
			" G=" + chunk1.getBlockLightG(0, y, 8) + " B=" + chunk1.getBlockLightB(0, y, 8));
		
		// Now remove the torch
		propagator.removeBlockLight(chunk0, edgeX, y, 8);
		
		System.out.println("After removal - chunk0[15]: R=" + chunk0.getBlockLightR(edgeX, y, 8) +
			" G=" + chunk0.getBlockLightG(edgeX, y, 8) + " B=" + chunk0.getBlockLightB(edgeX, y, 8));
		System.out.println("After removal - chunk1[0]: R=" + chunk1.getBlockLightR(0, y, 8) +
			" G=" + chunk1.getBlockLightG(0, y, 8) + " B=" + chunk1.getBlockLightB(0, y, 8));
		
		// Verify light removed from source chunk
		assertEquals(0, chunk0.getBlockLightR(edgeX, y, 8), "Light should be removed from chunk 0");
		assertEquals(0, chunk0.getBlockLightG(edgeX, y, 8), "Light should be removed from chunk 0");
		assertEquals(0, chunk0.getBlockLightB(edgeX, y, 8), "Light should be removed from chunk 0");
		
		// Verify light removed from neighbor chunk (this is the critical test)
		assertEquals(0, chunk1.getBlockLightR(0, y, 8), "Light should be removed from chunk 1");
		assertEquals(0, chunk1.getBlockLightG(0, y, 8), "Light should be removed from chunk 1");
		assertEquals(0, chunk1.getBlockLightB(0, y, 8), "Light should be removed from chunk 1");
	}
	
	@Test
	public void testTorchJustInsideChunkBoundary() {
		// Create two adjacent chunks
		LevelChunk chunk0 = new LevelChunk(0, 0);
		LevelChunk chunk1 = new LevelChunk(1, 0);
		
		// Set up cross-chunk propagator
		CrossChunkLightPropagator crossProp = new CrossChunkLightPropagator();
		crossProp.setNeighborAccessor((chunkX, chunkZ) -> {
			if (chunkX == 0 && chunkZ == 0) return chunk0;
			if (chunkX == 1 && chunkZ == 0) return chunk1;
			return null;
		});
		
		LightPropagator propagator = new LightPropagator();
		propagator.setCrossChunkPropagator(crossProp);
		
		int y = LevelChunk.worldYToChunkY(64);
		
		// Place torch one block inside chunk boundary
		int nearEdgeX = 14;
		
		propagator.addBlockLightRGB(chunk0, nearEdgeX, y, 8, 14, 11, 0);
		
		// Light should reach chunk 1 (distance 2: 14->15->0)
		assertTrue(chunk1.getBlockLightR(0, y, 8) > 0, "Light should cross chunk boundary");
		assertTrue(chunk1.getBlockLightR(1, y, 8) > 0, "Light should propagate further into chunk 1");
		
		// Remove the torch
		propagator.removeBlockLight(chunk0, nearEdgeX, y, 8);
		
		// All light should be gone
		assertEquals(0, chunk0.getBlockLightR(nearEdgeX, y, 8), "Source should be dark");
		assertEquals(0, chunk0.getBlockLightR(15, y, 8), "Edge of chunk 0 should be dark");
		assertEquals(0, chunk1.getBlockLightR(0, y, 8), "Start of chunk 1 should be dark");
		assertEquals(0, chunk1.getBlockLightR(1, y, 8), "Interior of chunk 1 should be dark");
	}
}
