package mattmc.world.level.lighting;

import mattmc.world.level.block.Blocks;
import mattmc.world.level.chunk.LevelChunk;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Manual test for the exact scenario described in the bug report:
 * "if i build a completely enclosed room and place down a torch it will properly light up the room
 * and when i break it the room will go dark again. although SOMETIMES breaking the torch does not 
 * result in the light going away."
 * 
 * This test verifies the fix works for this scenario.
 */
public class EnclosedRoomTorchTest {
	
	@Test
	public void testTorchInEnclosedRoom() {
		LevelChunk chunk = new LevelChunk(0, 0);
		WorldLightManager worldLightManager = new WorldLightManager();
		chunk.setWorldLightManager(worldLightManager);
		int y = LevelChunk.worldYToChunkY(64);
		
		// Build a completely enclosed room (5x5x5)
		// Walls, floor, ceiling
		for (int x = 5; x <= 9; x++) {
			for (int yy = y; yy <= y + 4; yy++) {
				for (int z = 5; z <= 9; z++) {
					// Only walls, floor, ceiling - hollow inside
					if (x == 5 || x == 9 || z == 5 || z == 9 || yy == y || yy == y + 4) {
						chunk.setBlock(x, yy, z, Blocks.STONE);
					}
				}
			}
		}
		
		// Inside the room (7,y+2,7) should be dark initially
		assertEquals(0, chunk.getBlockLight(7, y + 2, 7), "Room should start dark");
		
		// Place a torch inside the room at (7, y+1, 7)
		chunk.setBlock(7, y + 1, 7, Blocks.TORCH);
		
		// The room should now be lit
		int lightAtTorch = chunk.getBlockLight(7, y + 1, 7);
		assertTrue(lightAtTorch > 0, "Torch should emit light");
		
		// Light should propagate inside the room
		int lightNearby = chunk.getBlockLight(7, y + 2, 7);
		assertTrue(lightNearby > 0, "Light should fill the room");
		
		// Break the torch
		chunk.setBlock(7, y + 1, 7, Blocks.AIR);
		
		// The room should go dark again
		assertEquals(0, chunk.getBlockLight(7, y + 1, 7), "Light should be removed at torch position");
		assertEquals(0, chunk.getBlockLight(7, y + 2, 7), "Light should be removed throughout room");
		assertEquals(0, chunk.getBlockLight(6, y + 1, 7), "Light should be removed in all directions");
		assertEquals(0, chunk.getBlockLight(8, y + 1, 7), "Light should be removed in all directions");
		assertEquals(0, chunk.getBlockLight(7, y + 1, 6), "Light should be removed in all directions");
		assertEquals(0, chunk.getBlockLight(7, y + 1, 8), "Light should be removed in all directions");
	}
	
	@Test
	public void testMultipleIterationsInEnclosedRoom() {
		LevelChunk chunk = new LevelChunk(0, 0);
		WorldLightManager worldLightManager = new WorldLightManager();
		chunk.setWorldLightManager(worldLightManager);
		int y = LevelChunk.worldYToChunkY(64);
		
		// Build enclosed room
		for (int x = 5; x <= 9; x++) {
			for (int yy = y; yy <= y + 4; yy++) {
				for (int z = 5; z <= 9; z++) {
					if (x == 5 || x == 9 || z == 5 || z == 9 || yy == y || yy == y + 4) {
						chunk.setBlock(x, yy, z, Blocks.STONE);
					}
				}
			}
		}
		
		// Test placing and removing torch multiple times
		// This catches the "SOMETIMES" part of the bug report
		for (int iteration = 0; iteration < 10; iteration++) {
			// Place torch
			chunk.setBlock(7, y + 1, 7, Blocks.TORCH);
			
			// Verify light exists
			assertTrue(chunk.getBlockLight(7, y + 1, 7) > 0, 
				"Iteration " + iteration + ": Light should exist after placing torch");
			assertTrue(chunk.getBlockLight(7, y + 2, 7) > 0, 
				"Iteration " + iteration + ": Light should propagate");
			
			// Break torch
			chunk.setBlock(7, y + 1, 7, Blocks.AIR);
			
			// Verify light is completely removed
			assertEquals(0, chunk.getBlockLight(7, y + 1, 7), 
				"Iteration " + iteration + ": Light should be removed at torch");
			assertEquals(0, chunk.getBlockLight(7, y + 2, 7), 
				"Iteration " + iteration + ": Light should be removed everywhere");
			assertEquals(0, chunk.getBlockLight(6, y + 1, 7), 
				"Iteration " + iteration + ": No light should remain");
			assertEquals(0, chunk.getBlockLight(8, y + 1, 7), 
				"Iteration " + iteration + ": No light should remain");
		}
	}
}
