package mattmc.world.level.lighting;

import mattmc.world.level.block.Blocks;
import mattmc.world.level.chunk.LevelChunk;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to reproduce the user's reported issue:
 * "the problem still persists. i also notice when digging down into a hole 
 * and then covering up the only opening sometimes the light doesnt completely go away in the hole"
 */
public class DiggingHoleLightTest {
	
	@Test
	public void testDigHoleAndCoverItUp() {
		LevelChunk chunk = new LevelChunk(0, 0);
		int surfaceY = LevelChunk.worldYToChunkY(64);
		
		System.out.println("surfaceY (chunk-local): " + surfaceY);
		
		// First fill everything below surface with stone to simulate underground
		for (int x = 5; x <= 10; x++) {
			for (int z = 5; z <= 10; z++) {
				for (int y = 0; y < surfaceY; y++) {  // Below surface
					chunk.setBlock(x, y, z, Blocks.STONE);
				}
			}
		}
		
		// Create a cave/hole underground (air pocket)
		for (int y = surfaceY - 5; y < surfaceY; y++) {
			chunk.setBlock(7, y, 7, Blocks.AIR);
		}
		
		System.out.println("Initial heightmap: " + chunk.getHeightmap().getHeight(7, 7));
		System.out.println("Skylight in cave initially: " + chunk.getSkyLight(7, surfaceY - 3, 7));
		
		// Now "dig down" by removing the surface block, exposing the cave
		chunk.setBlock(7, surfaceY, 7, Blocks.AIR);
		
		System.out.println("After opening: heightmap=" + chunk.getHeightmap().getHeight(7, 7));
		System.out.println("Skylight at surface: " + chunk.getSkyLight(7, surfaceY, 7));
		System.out.println("Skylight in cave after opening: " + chunk.getSkyLight(7, surfaceY - 3, 7));
		
		// Skylight should now reach into the cave
		int lightInCave = chunk.getSkyLight(7, surfaceY - 3, 7);
		assertTrue(lightInCave > 0, "Cave should have skylight after opening, got: " + lightInCave);
		
		// Now "cover up" by placing the surface block back
		chunk.setBlock(7, surfaceY, 7, Blocks.STONE);
		
		System.out.println("After covering: heightmap=" + chunk.getHeightmap().getHeight(7, 7));
		System.out.println("Skylight in cave after covering: " + chunk.getSkyLight(7, surfaceY - 3, 7));
		
		// After covering, the cave should be dark
		int lightAfterCover = chunk.getSkyLight(7, surfaceY - 3, 7);
		assertEquals(0, lightAfterCover, "Covered cave should be dark, but got: " + lightAfterCover);
	}
	
	@Test
	public void testTorchInHoleThenCover() {
		LevelChunk chunk = new LevelChunk(0, 0);
		int surfaceY = LevelChunk.worldYToChunkY(64);
		
		// Create underground (filled with stone below surface)
		for (int x = 5; x <= 10; x++) {
			for (int z = 5; z <= 10; z++) {
				for (int y = 0; y < surfaceY; y++) {
					chunk.setBlock(x, y, z, Blocks.STONE);
				}
			}
		}
		
		// Create a cave underground
		for (int y = surfaceY - 5; y < surfaceY; y++) {
			chunk.setBlock(7, y, 7, Blocks.AIR);
		}
		
		// Place a torch in the cave
		chunk.setBlock(7, surfaceY - 3, 7, Blocks.TORCH);
		
		System.out.println("Torch placed, blocklight: " + chunk.getBlockLight(7, surfaceY - 3, 7));
		
		// Cave should be lit by torch
		int lightFromTorch = chunk.getBlockLight(7, surfaceY - 3, 7);
		assertTrue(lightFromTorch > 0, "Torch should light the cave, got: " + lightFromTorch);
		
		// Also should have propagated light nearby
		int lightNearby = chunk.getBlockLight(7, surfaceY - 2, 7);
		System.out.println("Light nearby: " + lightNearby);
		assertTrue(lightNearby > 0, "Light should propagate from torch, got: " + lightNearby);
		
		// Remove the torch - this is where the bug happens
		chunk.setBlock(7, surfaceY - 3, 7, Blocks.AIR);
		
		System.out.println("After removing torch, blocklight at source: " + chunk.getBlockLight(7, surfaceY - 3, 7));
		System.out.println("After removing torch, blocklight nearby: " + chunk.getBlockLight(7, surfaceY - 2, 7));
		
		// Light should be completely gone
		assertEquals(0, chunk.getBlockLight(7, surfaceY - 3, 7), "Torch light should be removed");
		assertEquals(0, chunk.getBlockLight(7, surfaceY - 2, 7), "Propagated light should be removed");
		assertEquals(0, chunk.getBlockLight(7, surfaceY - 4, 7), "Propagated light should be removed");
		assertEquals(0, chunk.getBlockLight(6, surfaceY - 3, 7), "Propagated light should be removed");
		assertEquals(0, chunk.getBlockLight(8, surfaceY - 3, 7), "Propagated light should be removed");
	}
}
