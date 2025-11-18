package mattmc.world.level.lighting;

import mattmc.world.level.block.Blocks;
import mattmc.world.level.chunk.LevelChunk;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for BUG-002: Missing Re-propagation in LightPropagator.updateBlockLight()
 * 
 * This test verifies that when an opaque block is placed in a lit area,
 * light is properly re-propagated from neighbors after clearing the light
 * at the block position.
 */
public class OpaqueBlockLightRepropagationTest {
	
	@Test
	public void testLightRepropagationAfterOpaqueBlockPlaced() {
		LevelChunk chunk = new LevelChunk(0, 0);
		WorldLightManager worldLightManager = new WorldLightManager();
		chunk.setWorldLightManager(worldLightManager);
		
		int y = LevelChunk.worldYToChunkY(64);
		
		// Step 1: Place a torch to light an area
		chunk.setBlock(5, y, 8, Blocks.TORCH);
		
		// Verify torch emission
		int emission = Math.max(Blocks.TORCH.getLightEmissionR(), 
		                        Math.max(Blocks.TORCH.getLightEmissionG(), 
		                                 Blocks.TORCH.getLightEmissionB()));
		assertEquals(14, emission, "Torch should emit light level 14");
		
		// Verify initial light propagation
		assertEquals(14, chunk.getBlockLightI(5, y, 8), "Torch position should have full light");
		assertEquals(13, chunk.getBlockLightI(6, y, 8), "1 block away should have 13");
		assertEquals(12, chunk.getBlockLightI(7, y, 8), "2 blocks away should have 12");
		assertEquals(11, chunk.getBlockLightI(8, y, 8), "3 blocks away should have 11");
		
		// Step 2: Place an opaque (stone) block at position (7, y, 8)
		// This position currently has light level 12 from the torch
		chunk.setBlock(7, y, 8, Blocks.STONE);
		
		// Step 3: Verify that light at the opaque block position is cleared
		assertEquals(0, chunk.getBlockLightI(7, y, 8), 
		             "Opaque block position should have no light");
		
		// Step 4: Verify that light still propagates from torch to positions around the opaque block
		// The torch is at (5, y, 8), so position (6, y, 8) should still have light
		assertEquals(13, chunk.getBlockLightI(6, y, 8), 
		             "Position before opaque block should still have light from torch");
		
		// Position (8, y, 8) is beyond the opaque block, so it should not receive
		// light from the torch anymore (blocked by stone at 7, y, 8)
		// However, it might still receive light from other directions
		
		// Positions adjacent to the opaque block (but not in line with torch) should have light
		// For example, (7, y, 7) and (7, y, 9) should receive light from the torch
		int lightAtSide1 = chunk.getBlockLightI(7, y, 7);
		int lightAtSide2 = chunk.getBlockLightI(7, y, 9);
		
		// Distance from torch (5, y, 8) to (7, y, 7) is sqrt((7-5)^2 + (7-8)^2) = sqrt(5) ≈ 2.2 blocks
		// Distance from torch (5, y, 8) to (7, y, 9) is sqrt((7-5)^2 + (9-8)^2) = sqrt(5) ≈ 2.2 blocks
		// With Manhattan distance propagation, light should reach these positions
		assertTrue(lightAtSide1 >= 11, 
		           "Adjacent position perpendicular to line should have light from torch, got " + lightAtSide1);
		assertTrue(lightAtSide2 >= 11, 
		           "Adjacent position perpendicular to line should have light from torch, got " + lightAtSide2);
	}
	
	@Test
	public void testOpaqueBlockBetweenTwoTorches() {
		LevelChunk chunk = new LevelChunk(0, 0);
		WorldLightManager worldLightManager = new WorldLightManager();
		chunk.setWorldLightManager(worldLightManager);
		
		int y = LevelChunk.worldYToChunkY(64);
		
		// Place two torches with space between them
		chunk.setBlock(5, y, 8, Blocks.TORCH);
		chunk.setBlock(11, y, 8, Blocks.TORCH);
		
		// Verify middle position gets light from both sides
		int middleLight = chunk.getBlockLightI(8, y, 8);
		assertTrue(middleLight >= 11, 
		           "Middle position should have light from both torches, got " + middleLight);
		
		// Place opaque block at middle position
		chunk.setBlock(8, y, 8, Blocks.STONE);
		
		// Verify light is cleared at the opaque block
		assertEquals(0, chunk.getBlockLightI(8, y, 8), 
		             "Opaque block should have no light");
		
		// Verify light from left torch still reaches positions before the block
		assertEquals(13, chunk.getBlockLightI(6, y, 8), 
		             "Position near left torch should still have light");
		assertEquals(12, chunk.getBlockLightI(7, y, 8), 
		             "Position near left torch should still have light");
		
		// Verify light from right torch still reaches positions after the block
		assertEquals(13, chunk.getBlockLightI(10, y, 8), 
		             "Position near right torch should still have light");
		assertEquals(12, chunk.getBlockLightI(9, y, 8), 
		             "Position near right torch should still have light");
	}
	
	@Test
	public void testRemoveOpaqueBlockRestoresLight() {
		LevelChunk chunk = new LevelChunk(0, 0);
		WorldLightManager worldLightManager = new WorldLightManager();
		chunk.setWorldLightManager(worldLightManager);
		
		int y = LevelChunk.worldYToChunkY(64);
		
		// Place a torch
		chunk.setBlock(5, y, 8, Blocks.TORCH);
		
		// Record initial light at a position
		int initialLight = chunk.getBlockLightI(7, y, 8);
		assertEquals(12, initialLight, "Initial light should be 12");
		
		// Place opaque block at that position
		chunk.setBlock(7, y, 8, Blocks.STONE);
		
		// Verify light is cleared
		assertEquals(0, chunk.getBlockLightI(7, y, 8), 
		             "Opaque block should block light");
		
		// Remove the opaque block (replace with air)
		chunk.setBlock(7, y, 8, Blocks.AIR);
		
		// Light should be restored from neighbors
		int restoredLight = chunk.getBlockLightI(7, y, 8);
		assertEquals(initialLight, restoredLight, 
		             "Light should be restored to original level when opaque block is removed");
	}
	
	@Test
	public void testOpaqueBlockDoesNotAffectOrthogonalLight() {
		LevelChunk chunk = new LevelChunk(0, 0);
		WorldLightManager worldLightManager = new WorldLightManager();
		chunk.setWorldLightManager(worldLightManager);
		
		int y = LevelChunk.worldYToChunkY(64);
		
		// Place a torch
		chunk.setBlock(8, y, 8, Blocks.TORCH);
		
		// Verify light in all directions
		assertEquals(13, chunk.getBlockLightI(9, y, 8), "Light should propagate +X");
		assertEquals(13, chunk.getBlockLightI(7, y, 8), "Light should propagate -X");
		assertEquals(13, chunk.getBlockLightI(8, y, 9), "Light should propagate +Z");
		assertEquals(13, chunk.getBlockLightI(8, y, 7), "Light should propagate -Z");
		
		// Place opaque block in +X direction
		chunk.setBlock(9, y, 8, Blocks.STONE);
		
		// Verify the opaque block has no light
		assertEquals(0, chunk.getBlockLightI(9, y, 8), 
		             "Opaque block should block light");
		
		// Verify light in other directions is unaffected
		assertEquals(13, chunk.getBlockLightI(7, y, 8), 
		             "Light in opposite direction should be unaffected");
		assertEquals(13, chunk.getBlockLightI(8, y, 9), 
		             "Light in perpendicular direction should be unaffected");
		assertEquals(13, chunk.getBlockLightI(8, y, 7), 
		             "Light in perpendicular direction should be unaffected");
	}
}
