package mattmc.world.level.lighting;

import mattmc.world.level.block.Block;
import mattmc.world.level.block.Blocks;
import mattmc.world.level.chunk.LevelChunk;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for SkylightEngine.
 * Verifies BFS-based skylight propagation below heightmap.
 */
public class SkylightEngineTest {
	
	@Test
	public void testBasicSkylightPropagation() {
		LevelChunk chunk = new LevelChunk(0, 0);
		SkylightEngine engine = new SkylightEngine();
		
		// Create a simple flat surface at y=64
		int surfaceY = LevelChunk.worldYToChunkY(64);
		chunk.setBlock(8, surfaceY, 8, Blocks.GRASS_BLOCK);
		
		// Initialize skylight
		engine.initializeChunkSkylight(chunk);
		
		// Above the surface should have full skylight
		int aboveY = LevelChunk.worldYToChunkY(65);
		assertEquals(15, chunk.getSkyLight(8, aboveY, 8), "Above surface should have full skylight");
		
		// At the surface (opaque block) should have no skylight
		assertEquals(0, chunk.getSkyLight(8, surfaceY, 8), "Opaque surface block should have no skylight");
	}
	
	@Test
	public void testSkylightAttenuation() {
		LevelChunk chunk = new LevelChunk(0, 0);
		SkylightEngine engine = new SkylightEngine();
		
		// Create a vertical shaft: surface at y=70, air shaft going down
		int surfaceY = LevelChunk.worldYToChunkY(70);
		for (int x = 0; x < LevelChunk.WIDTH; x++) {
			for (int z = 0; z < LevelChunk.DEPTH; z++) {
				chunk.setBlock(x, surfaceY, z, Blocks.GRASS_BLOCK);
			}
		}
		
		// Clear a shaft at (8, 8)
		for (int y = surfaceY - 1; y >= surfaceY - 10; y--) {
			chunk.setBlock(8, y, 8, Blocks.AIR);
		}
		
		// Initialize skylight
		engine.initializeChunkSkylight(chunk);
		
		// Check attenuation down the shaft
		// At the opening, should have full or near-full skylight
		int openingY = LevelChunk.worldYToChunkY(69);
		int openingLight = chunk.getSkyLight(8, openingY, 8);
		assertTrue(openingLight >= 14, "Opening should have near-full skylight, got " + openingLight);
		
		// Further down should have some attenuation
		int deepY = LevelChunk.worldYToChunkY(65);
		int deepLight = chunk.getSkyLight(8, deepY, 8);
		assertTrue(deepLight > 0, "Deep in shaft should have some skylight, got " + deepLight);
		assertTrue(deepLight < openingLight, "Deep should be darker than opening");
	}
	
	@Test
	public void testOpaqueBlockStopsPropagation() {
		LevelChunk chunk = new LevelChunk(0, 0);
		SkylightEngine engine = new SkylightEngine();
		
		// Create a surface with a cavity, then block it with stone
		int surfaceY = LevelChunk.worldYToChunkY(64);
		for (int x = 0; x < LevelChunk.WIDTH; x++) {
			for (int z = 0; z < LevelChunk.DEPTH; z++) {
				chunk.setBlock(x, surfaceY, z, Blocks.GRASS_BLOCK);
			}
		}
		
		// Create cavity with stone floor
		chunk.setBlock(8, surfaceY, 8, Blocks.AIR);
		chunk.setBlock(8, surfaceY - 1, 8, Blocks.STONE);
		chunk.setBlock(8, surfaceY - 2, 8, Blocks.AIR);
		
		// Initialize skylight
		engine.initializeChunkSkylight(chunk);
		
		// Cavity should have skylight
		int cavityLight = chunk.getSkyLight(8, surfaceY, 8);
		assertTrue(cavityLight > 0, "Cavity should have skylight");
		
		// Stone floor should block skylight
		int floorLight = chunk.getSkyLight(8, surfaceY - 1, 8);
		assertEquals(0, floorLight, "Opaque stone floor should have no internal skylight");
		
		// Below stone floor should have no skylight
		int belowFloorLight = chunk.getSkyLight(8, surfaceY - 2, 8);
		assertEquals(0, belowFloorLight, "Below opaque floor should have no skylight");
	}
	
	@Test
	public void testHeightmapUpdate() {
		LevelChunk chunk = new LevelChunk(0, 0);
		SkylightEngine engine = new SkylightEngine();
		
		// Initial surface at y=64
		int surfaceY = LevelChunk.worldYToChunkY(64);
		chunk.setBlock(8, surfaceY, 8, Blocks.GRASS_BLOCK);
		
		// Initialize
		engine.initializeChunkSkylight(chunk);
		
		// Check initial heightmap
		int initialHeightmap = chunk.getHeightmap().getHeight(8, 8);
		assertEquals(64, initialHeightmap, "Initial heightmap should be 64");
		
		// Dig down - remove the surface block
		chunk.setBlock(8, surfaceY, 8, Blocks.AIR);
		
		// Manually trigger heightmap update (normally done by setBlock hook)
		engine.updateColumnSkylight(chunk, 8, surfaceY, 8, Blocks.AIR, Blocks.GRASS_BLOCK);
		
		// Heightmap should have decreased
		int newHeightmap = chunk.getHeightmap().getHeight(8, 8);
		assertTrue(newHeightmap < initialHeightmap, 
			"Heightmap should decrease after removing block, was " + initialHeightmap + " now " + newHeightmap);
		
		// Skylight at the dug position should now be 15
		assertEquals(15, chunk.getSkyLight(8, surfaceY, 8), 
			"Dug position should have full skylight");
	}
	
	@Test
	public void testDigVerticalShaft() {
		LevelChunk chunk = new LevelChunk(0, 0);
		SkylightEngine engine = new SkylightEngine();
		
		// Create solid ground from y=50 to y=70
		for (int y = LevelChunk.worldYToChunkY(50); y <= LevelChunk.worldYToChunkY(70); y++) {
			for (int x = 0; x < LevelChunk.WIDTH; x++) {
				for (int z = 0; z < LevelChunk.DEPTH; z++) {
					chunk.setBlock(x, y, z, Blocks.STONE);
				}
			}
		}
		
		// Initialize skylight
		engine.initializeChunkSkylight(chunk);
		
		// Verify no skylight below surface initially
		int undergroundY = LevelChunk.worldYToChunkY(60);
		assertEquals(0, chunk.getSkyLight(8, undergroundY, 8), 
			"Underground should have no skylight initially");
		
		// Dig a shaft from y=70 down to y=60
		for (int worldY = 70; worldY >= 60; worldY--) {
			int chunkY = LevelChunk.worldYToChunkY(worldY);
			Block oldBlock = chunk.getBlock(8, chunkY, 8);
			chunk.setBlock(8, chunkY, 8, Blocks.AIR);
			engine.updateColumnSkylight(chunk, 8, chunkY, 8, Blocks.AIR, oldBlock);
		}
		
		// Now the shaft should have skylight
		int shaftLight = chunk.getSkyLight(8, undergroundY, 8);
		assertTrue(shaftLight > 0, "Dug shaft should have skylight, got " + shaftLight);
	}
}
