package mattmc.world.level.lighting;

import mattmc.world.level.block.Blocks;
import mattmc.world.level.chunk.LevelChunk;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for SkylightInitializer.
 * Verifies heightmap computation and skylight initialization.
 */
public class SkylightInitializerTest {
	
	@Test
	public void testFlatTerrainSkylight() {
		LevelChunk chunk = new LevelChunk(0, 0);
		
		// Create flat terrain at y=64
		int surfaceChunkY = LevelChunk.worldYToChunkY(64);
		for (int x = 0; x < LevelChunk.WIDTH; x++) {
			for (int z = 0; z < LevelChunk.DEPTH; z++) {
				chunk.setBlock(x, surfaceChunkY, z, Blocks.GRASS_BLOCK);
			}
		}
		
		// Initialize skylight
		SkylightInitializer.initializeChunkSkylight(chunk);
		
		// Verify heightmap
		for (int x = 0; x < LevelChunk.WIDTH; x++) {
			for (int z = 0; z < LevelChunk.DEPTH; z++) {
				assertEquals(64, chunk.getHeightmap().getHeight(x, z), 
					"Heightmap should be 64 at (" + x + "," + z + ")");
			}
		}
		
		// Verify skylight above surface (should be 15)
		for (int x = 0; x < LevelChunk.WIDTH; x++) {
			for (int z = 0; z < LevelChunk.DEPTH; z++) {
				int aboveSurfaceY = LevelChunk.worldYToChunkY(65);
				assertEquals(15, chunk.getSkyLight(x, aboveSurfaceY, z),
					"Skylight above surface should be 15 at (" + x + "," + aboveSurfaceY + "," + z + ")");
				
				int highY = LevelChunk.worldYToChunkY(100);
				assertEquals(15, chunk.getSkyLight(x, highY, z),
					"Skylight at high altitude should be 15 at (" + x + "," + highY + "," + z + ")");
			}
		}
		
		// Verify skylight at and below surface (should be 0)
		for (int x = 0; x < LevelChunk.WIDTH; x++) {
			for (int z = 0; z < LevelChunk.DEPTH; z++) {
				int surfaceY = LevelChunk.worldYToChunkY(64);
				assertEquals(0, chunk.getSkyLight(x, surfaceY, z),
					"Skylight at surface should be 0 at (" + x + "," + surfaceY + "," + z + ")");
				
				int belowY = LevelChunk.worldYToChunkY(63);
				assertEquals(0, chunk.getSkyLight(x, belowY, z),
					"Skylight below surface should be 0 at (" + x + "," + belowY + "," + z + ")");
			}
		}
	}
	
	@Test
	public void testVariedHeightSkylight() {
		LevelChunk chunk = new LevelChunk(0, 0);
		
		// Create varied terrain: column at (5, 5) has height 100, others have height 64
		for (int x = 0; x < LevelChunk.WIDTH; x++) {
			for (int z = 0; z < LevelChunk.DEPTH; z++) {
				int height = (x == 5 && z == 5) ? 100 : 64;
				int chunkY = LevelChunk.worldYToChunkY(height);
				chunk.setBlock(x, chunkY, z, Blocks.STONE);
			}
		}
		
		// Initialize skylight
		SkylightInitializer.initializeChunkSkylight(chunk);
		
		// Verify heightmap for column (5, 5)
		assertEquals(100, chunk.getHeightmap().getHeight(5, 5));
		
		// Verify heightmap for other columns
		assertEquals(64, chunk.getHeightmap().getHeight(0, 0));
		assertEquals(64, chunk.getHeightmap().getHeight(10, 10));
		
		// Verify skylight at column (5, 5)
		int y101 = LevelChunk.worldYToChunkY(101);
		assertEquals(15, chunk.getSkyLight(5, y101, 5), "Above tall column should have skylight");
		
		int y100 = LevelChunk.worldYToChunkY(100);
		assertEquals(0, chunk.getSkyLight(5, y100, 5), "At tall column surface should have no skylight");
		
		int y99 = LevelChunk.worldYToChunkY(99);
		assertEquals(0, chunk.getSkyLight(5, y99, 5), "Below tall column surface should have no skylight");
		
		// Verify skylight at other columns (between the two heights)
		int y65 = LevelChunk.worldYToChunkY(65);
		assertEquals(15, chunk.getSkyLight(0, y65, 0), "Above normal height should have skylight");
		
		int y80 = LevelChunk.worldYToChunkY(80);
		assertEquals(15, chunk.getSkyLight(0, y80, 0), "Between heights should have skylight for lower columns");
	}
	
	@Test
	public void testEmptyChunkSkylight() {
		LevelChunk chunk = new LevelChunk(0, 0);
		// No blocks placed - all air
		
		// Initialize skylight
		SkylightInitializer.initializeChunkSkylight(chunk);
		
		// Heightmap should be MIN_Y for all columns (no opaque blocks)
		for (int x = 0; x < LevelChunk.WIDTH; x++) {
			for (int z = 0; z < LevelChunk.DEPTH; z++) {
				assertEquals(LevelChunk.MIN_Y, chunk.getHeightmap().getHeight(x, z));
			}
		}
		
		// All positions should have skylight=15 (no opaque blocks to block it)
		for (int x = 0; x < LevelChunk.WIDTH; x++) {
			for (int y = 0; y < LevelChunk.HEIGHT; y++) {
				for (int z = 0; z < LevelChunk.DEPTH; z++) {
					assertEquals(15, chunk.getSkyLight(x, y, z),
						"Empty chunk should have full skylight everywhere at (" + x + "," + y + "," + z + ")");
				}
			}
		}
	}
	
	@Test
	public void testRecomputeHeightmap() {
		LevelChunk chunk = new LevelChunk(0, 0);
		
		// Place blocks at y=50
		int y50 = LevelChunk.worldYToChunkY(50);
		for (int x = 0; x < LevelChunk.WIDTH; x++) {
			for (int z = 0; z < LevelChunk.DEPTH; z++) {
				chunk.setBlock(x, y50, z, Blocks.STONE);
			}
		}
		
		// Recompute heightmap only (don't update skylight)
		SkylightInitializer.recomputeHeightmap(chunk);
		
		// Verify heightmap was updated
		for (int x = 0; x < LevelChunk.WIDTH; x++) {
			for (int z = 0; z < LevelChunk.DEPTH; z++) {
				assertEquals(50, chunk.getHeightmap().getHeight(x, z));
			}
		}
		
		// Skylight should still be default (15 everywhere) since we didn't call initializeChunkSkylight
		int y100 = LevelChunk.worldYToChunkY(100);
		assertEquals(15, chunk.getSkyLight(0, y100, 0));
	}
}
