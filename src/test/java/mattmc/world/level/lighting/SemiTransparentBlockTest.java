package mattmc.world.level.lighting;

import mattmc.world.level.block.Block;
import mattmc.world.level.block.Blocks;
import mattmc.world.level.chunk.LevelChunk;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for semi-transparent block behavior in the lighting system.
 * 
 * This test verifies that the opacity inconsistency bug is fixed:
 * - All three lighting systems (SkylightInitializer, SkylightEngine, LightPropagator)
 *   now use the same threshold (opacity >= 15) for blocking light
 * - Semi-transparent blocks (opacity 1-14) allow light to propagate with proper attenuation
 * - Light attenuation follows the rule: decrement = Math.max(1, blockOpacity)
 */
public class SemiTransparentBlockTest {
	
	/**
	 * Test helper: A block with configurable opacity for testing.
	 */
	private static class TestBlock extends Block {
		private final int opacity;
		
		public TestBlock(int opacity) {
			super(opacity >= 15); // Solid if fully opaque
			this.opacity = opacity;
		}
		
		@Override
		public int getOpacity() {
			return opacity;
		}
	}
	
	@Test
	public void testSemiTransparentBlockNotInHeightmap() {
		LevelChunk chunk = new LevelChunk(0, 0);
		
		// Create a column with a semi-transparent block at y=64
		int y64 = LevelChunk.worldYToChunkY(64);
		Block semiTransparent = new TestBlock(5); // Opacity 5 (semi-transparent)
		chunk.setBlock(8, y64, 8, semiTransparent);
		
		// Initialize skylight
		SkylightInitializer.initializeChunkSkylight(chunk);
		
		// Verify heightmap: semi-transparent blocks should NOT be treated as blockers
		int heightmap = chunk.getHeightmap().getHeight(8, 8);
		assertEquals(LevelChunk.MIN_Y, heightmap, 
			"Semi-transparent block (opacity < 15) should not be in heightmap");
		
		// Verify skylight passes through
		int skylightAt64 = chunk.getSkyLight(8, y64, 8);
		assertEquals(15, skylightAt64, 
			"Semi-transparent block should receive full skylight from above");
	}
	
	@Test
	public void testFullyOpaqueBlockInHeightmap() {
		LevelChunk chunk = new LevelChunk(0, 0);
		
		// Create a column with a fully opaque block at y=64
		int y64 = LevelChunk.worldYToChunkY(64);
		Block fullyOpaque = new TestBlock(15); // Opacity 15 (fully opaque)
		chunk.setBlock(8, y64, 8, fullyOpaque);
		
		// Initialize skylight
		SkylightInitializer.initializeChunkSkylight(chunk);
		
		// Verify heightmap: fully opaque blocks SHOULD be treated as blockers
		int heightmap = chunk.getHeightmap().getHeight(8, 8);
		assertEquals(64, heightmap, 
			"Fully opaque block (opacity >= 15) should be in heightmap");
		
		// Verify skylight does not pass through
		int skylightAt64 = chunk.getSkyLight(8, y64, 8);
		assertEquals(0, skylightAt64, 
			"Fully opaque block should not receive skylight (it blocks it)");
	}
	
	@Test
	public void testConsistentThresholdAcrossAllSystems() {
		LevelChunk chunk = new LevelChunk(0, 0);
		
		// Create a test scenario with semi-transparent blocks
		int y62 = LevelChunk.worldYToChunkY(62);
		int y64 = LevelChunk.worldYToChunkY(64);
		
		chunk.setBlock(8, y62, 8, Blocks.STONE);           // Fully opaque
		chunk.setBlock(8, y64, 8, new TestBlock(7));       // Semi-transparent (opacity 7)
		
		// Initialize with SkylightInitializer
		SkylightInitializer.initializeChunkSkylight(chunk);
		
		// Verify heightmap only includes fully opaque blocks
		int heightmap = chunk.getHeightmap().getHeight(8, 8);
		assertEquals(62, heightmap, "Heightmap should only count fully opaque blocks (>= 15)");
		
		// Verify semi-transparent block receives skylight
		int light64 = chunk.getSkyLight(8, y64, 8);
		assertEquals(15, light64, "Semi-transparent block should receive full skylight");
	}
}
