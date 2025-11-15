package mattmc.world.level.lighting;

import mattmc.world.level.block.Blocks;
import mattmc.world.level.chunk.LevelChunk;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to reproduce the user's reported issue:
 * "if i dig a hole in the ground and then block the entrance so its completely enclosed, 
 * place a torch or other block light it will properly propagate and fill the room. 
 * however if i then break a block in the wall to expand the room (while still keeping it enclosed) 
 * light does not propagate into that empty space"
 */
public class EnclosedRoomExpansionTest {
	
	@Test
	public void testExpandingEnclosedRoomWithTorch() {
		LevelChunk chunk = new LevelChunk(0, 0);
		int y = LevelChunk.worldYToChunkY(64);
		
		// Suppress light updates during terrain setup to avoid skylight infinite loop
		chunk.setSuppressLightUpdates(true);
		
		// Create an enclosed 3x3x3 room underground
		// Fill surrounding area with stone
		for (int x = 4; x <= 10; x++) {
			for (int z = 4; z <= 10; z++) {
				for (int dy = -1; dy <= 1; dy++) {
					chunk.setBlock(x, y + dy, z, Blocks.STONE);
				}
			}
		}
		
		// Hollow out the center to create a 3x3x3 room (x: 5-7, y: 64, z: 5-7)
		for (int x = 5; x <= 7; x++) {
			for (int z = 5; z <= 7; z++) {
				for (int dy = -1; dy <= 1; dy++) {
					chunk.setBlock(x, y + dy, z, Blocks.AIR);
				}
			}
		}
		
		// Re-enable light updates for the actual test
		chunk.setSuppressLightUpdates(false);
		
		System.out.println("Created 3x3x3 enclosed room at (5-7, 64, 5-7)");
		
		// Place a torch in the center of the room
		chunk.setBlock(6, y, 6, Blocks.TORCH);
		
		System.out.println("Placed torch at center (6, 64, 6)");
		System.out.println("Light at torch: " + chunk.getBlockLight(6, y, 6));
		System.out.println("Light at (5, 64, 6): " + chunk.getBlockLight(5, y, 6));
		System.out.println("Light at (7, 64, 6): " + chunk.getBlockLight(7, y, 6));
		
		// Verify light propagated throughout the room
		int lightAtCorner = chunk.getBlockLight(5, y, 5);
		System.out.println("Light at corner (5, 64, 5): " + lightAtCorner);
		assertTrue(lightAtCorner > 0, "Initial room should be lit by torch");
		
		// Now expand the room by breaking a wall block (while keeping it enclosed)
		// Break the block at (8, 64, 6) - this is the wall on the +X side
		System.out.println("\n--- Expanding room by breaking wall at (8, 64, 6) ---");
		chunk.setBlock(8, y, 6, Blocks.AIR);
		
		System.out.println("Light at torch (6, 64, 6): " + chunk.getBlockLight(6, y, 6));
		System.out.println("Light at (7, 64, 6): " + chunk.getBlockLight(7, y, 6));
		System.out.println("Light at newly opened (8, 64, 6): " + chunk.getBlockLight(8, y, 6));
		
		// THIS IS THE BUG: Light should propagate into the newly opened space
		int lightInNewSpace = chunk.getBlockLight(8, y, 6);
		assertTrue(lightInNewSpace > 0, 
			"Light should propagate into newly opened space at (8, 64, 6), but got: " + lightInNewSpace);
	}
	
	@Test
	public void testExpandingRoomMultipleDirections() {
		LevelChunk chunk = new LevelChunk(0, 0);
		int y = LevelChunk.worldYToChunkY(64);
		
		// Suppress light updates during terrain setup
		chunk.setSuppressLightUpdates(true);
		
		// Create enclosed room with torch
		for (int x = 4; x <= 8; x++) {
			for (int z = 4; z <= 8; z++) {
				chunk.setBlock(x, y, z, Blocks.STONE);
			}
		}
		
		// Hollow out center (5-7)
		for (int x = 5; x <= 7; x++) {
			for (int z = 5; z <= 7; z++) {
				chunk.setBlock(x, y, z, Blocks.AIR);
			}
		}
		
		// Re-enable light updates
		chunk.setSuppressLightUpdates(false);
		
		// Place torch
		chunk.setBlock(6, y, 6, Blocks.TORCH);
		
		// Expand in +X direction
		chunk.setBlock(8, y, 6, Blocks.AIR);
		int lightXPlus = chunk.getBlockLight(8, y, 6);
		assertTrue(lightXPlus > 0, "Light should propagate in +X direction, got: " + lightXPlus);
		
		// Expand in -X direction
		chunk.setBlock(4, y, 6, Blocks.AIR);
		int lightXMinus = chunk.getBlockLight(4, y, 6);
		assertTrue(lightXMinus > 0, "Light should propagate in -X direction, got: " + lightXMinus);
		
		// Expand in +Z direction
		chunk.setBlock(6, y, 8, Blocks.AIR);
		int lightZPlus = chunk.getBlockLight(6, y, 8);
		assertTrue(lightZPlus > 0, "Light should propagate in +Z direction, got: " + lightZPlus);
		
		// Expand in -Z direction
		chunk.setBlock(6, y, 4, Blocks.AIR);
		int lightZMinus = chunk.getBlockLight(6, y, 4);
		assertTrue(lightZMinus > 0, "Light should propagate in -Z direction, got: " + lightZMinus);
	}
}
