package mattmc.world.level.lighting;

import mattmc.world.level.block.Blocks;
import mattmc.world.level.chunk.LevelChunk;
import org.junit.jupiter.api.Test;

public class DebugAttenuationTest {
	@Test
	public void debugAttenuation() {
		LevelChunk chunk = new LevelChunk(0, 0);
		SkylightEngine engine = new SkylightEngine();
		
		int surfaceY = LevelChunk.worldYToChunkY(70);
		
		// Fill surface with grass, except for the shaft opening
		for (int x = 0; x < LevelChunk.WIDTH; x++) {
			for (int z = 0; z < LevelChunk.DEPTH; z++) {
				if (x != 8 || z != 8) {
					chunk.setBlock(x, surfaceY, z, Blocks.GRASS_BLOCK);
				}
			}
		}
		
		// Clear a shaft at (8, 8)
		for (int y = surfaceY; y >= surfaceY - 10; y--) {
			chunk.setBlock(8, y, 8, Blocks.AIR);
		}
		
		System.out.println("Before init:");
		System.out.println("  Heightmap at (8,8): " + chunk.getHeightmap().getHeight(8, 8));
		
		// Initialize skylight
		engine.initializeChunkSkylight(chunk);
		
		System.out.println("\nAfter init:");
		System.out.println("  Heightmap at (8,8): " + chunk.getHeightmap().getHeight(8, 8));
		
		for (int y = surfaceY; y >= surfaceY - 10; y--) {
			int worldY = LevelChunk.chunkYToWorldY(y);
			System.out.println("  Light at (8," + y + ",8) [world Y=" + worldY + "]: " + chunk.getSkyLight(8, y, 8));
		}
	}
}
