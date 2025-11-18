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
	public void testSemiTransparentBlockInHeightmap() {
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
			"Semi-transparent block should not be in heightmap (opacity < 15)");
		
		// Verify skylight passes through
		int skylightAt64 = chunk.getSkyLight(8, y64, 8);
		assertTrue(skylightAt64 > 0, 
			"Semi-transparent block should allow skylight to pass through");
		
		// Skylight above should be full
		int y65 = LevelChunk.worldYToChunkY(65);
		int skylightAt65 = chunk.getSkyLight(8, y65, 8);
		assertEquals(15, skylightAt65, "Skylight above semi-transparent block should be full");
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
			"Fully opaque block should be in heightmap (opacity >= 15)");
		
		// Verify skylight does not pass through
		int skylightAt64 = chunk.getSkyLight(8, y64, 8);
		assertEquals(0, skylightAt64, 
			"Fully opaque block should block skylight");
		
		// Skylight above should be full
		int y65 = LevelChunk.worldYToChunkY(65);
		int skylightAt65 = chunk.getSkyLight(8, y65, 8);
		assertEquals(15, skylightAt65, "Skylight above opaque block should be full");
	}
	
	@Test
	public void testOpacityBasedAttenuationSkylight() {
		LevelChunk chunk = new LevelChunk(0, 0);
		
		// Create a vertical column:
		// y=70: air (skylight = 15)
		// y=69: semi-transparent block (opacity 3)
		// y=68: air
		// y=67: fully opaque block (opacity 15)
		
		int y67 = LevelChunk.worldYToChunkY(67);
		int y69 = LevelChunk.worldYToChunkY(69);
		
		chunk.setBlock(8, y67, 8, new TestBlock(15)); // Fully opaque floor
		chunk.setBlock(8, y69, 8, new TestBlock(3));  // Semi-transparent (opacity 3)
		
		// Use SkylightEngine for proper propagation
		SkylightEngine engine = new SkylightEngine();
		engine.initializeChunkSkylight(chunk);
		
		// Verify skylight at y=70 is full (direct sky access)
		int y70 = LevelChunk.worldYToChunkY(70);
		int light70 = chunk.getSkyLight(8, y70, 8);
		assertEquals(15, light70, "Sky should have full light");
		
		// Verify skylight at y=69 (the semi-transparent block)
		// The semi-transparent block itself receives full light (15) from above
		int light69 = chunk.getSkyLight(8, y69, 8);
		assertEquals(15, light69, 
			"Semi-transparent block itself should receive full skylight from above");
		
		// Verify skylight at y=68 (below semi-transparent block)
		// Light from y=69 (15) passes through the semi-transparent block (opacity 3)
		// with attenuation: 15 - max(1, 3) = 12
		int y68 = LevelChunk.worldYToChunkY(68);
		int light68 = chunk.getSkyLight(8, y68, 8);
		assertEquals(12, light68, 
			"Air below semi-transparent block (opacity 3) should have light = 15 - max(1,3) = 12, got " + light68);
		
		// Verify skylight at y=67 (fully opaque block) is 0
		int light67 = chunk.getSkyLight(8, y67, 8);
		assertEquals(0, light67, "Fully opaque block should have no light inside");
	}
	
	@Test
	public void testConsistentBehaviorAcrossAllSystems() {
		LevelChunk chunk = new LevelChunk(0, 0);
		
		// Create a test scenario:
		// y=65: air
		// y=64: semi-transparent (opacity 7)
		// y=63: air
		// y=62: opaque block
		
		int y62 = LevelChunk.worldYToChunkY(62);
		int y63 = LevelChunk.worldYToChunkY(63);
		int y64 = LevelChunk.worldYToChunkY(64);
		int y65 = LevelChunk.worldYToChunkY(65);
		
		chunk.setBlock(8, y62, 8, Blocks.STONE);           // Fully opaque
		chunk.setBlock(8, y64, 8, new TestBlock(7));       // Semi-transparent (opacity 7)
		
		// Initialize with SkylightEngine (uses BFS propagation)
		SkylightEngine engine = new SkylightEngine();
		engine.initializeChunkSkylight(chunk);
		
		// Verify all three systems now agree:
		// 1. Heightmap (from SkylightInitializer) should NOT include the semi-transparent block
		int heightmap = chunk.getHeightmap().getHeight(8, 8);
		assertEquals(62, heightmap, "Heightmap should only count fully opaque blocks (>= 15)");
		
		// 2. Skylight should propagate through the semi-transparent block
		int light64 = chunk.getSkyLight(8, y64, 8);
		assertTrue(light64 > 0, "SkylightEngine should propagate through semi-transparent block");
		assertEquals(15, light64, "Semi-transparent block itself should have full skylight");
		
		// 3. Air below semi-transparent block should receive attenuated light
		int light63 = chunk.getSkyLight(8, y63, 8);
		assertTrue(light63 > 0, "Air below semi-transparent block should receive light");
		
		// 4. Light should be attenuated correctly
		// At y=64 (semi-transparent, opacity 7): light = 15
		// At y=63 (air, below the semi-transparent): light = 15 - max(1,7) = 8
		assertEquals(8, light63, "Light below semi-transparent (opacity 7) should be 15 - max(1,7) = 8, got " + light63);
	}
	
	@Test
	public void testRegressionScenario() {
		// This is the regression test for the bug described in the issue:
		// "a column with top air, a semi-transparent block with opacity X (1..14),
		// then air, then a fully opaque block"
		
		LevelChunk chunk = new LevelChunk(0, 0);
		
		// Build the column from bottom to top:
		// y=60: fully opaque block (stone)
		// y=61: air
		// y=62: air
		// y=63: semi-transparent block (opacity 8)
		// y=64+: air (open to sky)
		
		int y60 = LevelChunk.worldYToChunkY(60);
		int y61 = LevelChunk.worldYToChunkY(61);
		int y62 = LevelChunk.worldYToChunkY(62);
		int y63 = LevelChunk.worldYToChunkY(63);
		
		chunk.setBlock(8, y60, 8, Blocks.STONE);       // Fully opaque floor
		chunk.setBlock(8, y63, 8, new TestBlock(8));   // Semi-transparent (opacity 8)
		
		// Initialize with SkylightEngine
		SkylightEngine engine = new SkylightEngine();
		engine.initializeChunkSkylight(chunk);
		
		// The bug was that air below the semi-transparent block received zero light
		// because the heightmap incorrectly treated it as blocking.
		// Now it should receive non-zero light.
		
		int light61 = chunk.getSkyLight(8, y61, 8);
		int light62 = chunk.getSkyLight(8, y62, 8);
		
		assertTrue(light61 > 0, 
			"Air below semi-transparent block should receive non-zero skylight (bug fix verification)");
		assertTrue(light62 > 0, 
			"Air below semi-transparent block should receive non-zero skylight (bug fix verification)");
		
		// Verify the light follows the attenuation rule
		// At y=63 (semi-transparent, opacity 8): light = 15
		// Below y=63, at y=62 (air): light = 15 - max(1, 8) = 7
		int light63 = chunk.getSkyLight(8, y63, 8);
		assertEquals(15, light63, 
			"Light at semi-transparent block should be 15 (full skylight from above)");
		
		// light62 was already read above, so just verify its value
		assertEquals(7, light62, 
			"Light below semi-transparent (opacity 8) should be 15 - max(1, 8) = 7, got " + light62);
		
		// Verify heightmap only includes the fully opaque block
		int heightmap = chunk.getHeightmap().getHeight(8, 8);
		assertEquals(60, heightmap, 
			"Heightmap should only include fully opaque block at y=60");
	}
}
