package mattmc.world.level.lighting;

import mattmc.registries.Blocks;
import mattmc.world.level.chunk.LevelChunk;
import org.junit.jupiter.api.Test;

public class DebugLightTest {
	@Test
	public void debugVerticalShaft() {
		LevelChunk chunk = new LevelChunk(0, 0);
		int surfaceY = LevelChunk.worldYToChunkY(64);
		
		// Fill underground
		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				for (int y = 0; y < surfaceY; y++) {
					chunk.setBlock(x, y, z, Blocks.STONE);
				}
			}
		}
		
		System.out.println("Initial state:");
		System.out.println("  Heightmap at (8,8): " + chunk.getHeightmap().getHeight(8, 8));
		System.out.println("  Skylight at surface (8," + surfaceY + ",8): " + chunk.getSkyLight(8, surfaceY, 8));
		
		// Dig shaft
		System.out.println("\nDigging vertical shaft...");
		for (int y = surfaceY; y >= surfaceY - 5; y--) {
			System.out.println("  Removing block at (8," + y + ",8)");
			chunk.setBlock(8, y, 8, Blocks.AIR);
			System.out.println("    Heightmap now: " + chunk.getHeightmap().getHeight(8, 8));
			System.out.println("    Skylight at this Y: " + chunk.getSkyLight(8, y, 8));
		}
		
		int bottomY = surfaceY - 5;
		System.out.println("\nAfter digging shaft:");
		System.out.println("  Skylight at bottom (" + bottomY + "): " + chunk.getSkyLight(8, bottomY, 8));
		
		// Close shaft
		System.out.println("\nClosing shaft at surface...");
		chunk.setBlock(8, surfaceY, 8, Blocks.STONE);
		System.out.println("  Heightmap now: " + chunk.getHeightmap().getHeight(8, 8));
		System.out.println("  Skylight at surface: " + chunk.getSkyLight(8, surfaceY, 8));
		System.out.println("  Skylight at bottom: " + chunk.getSkyLight(8, bottomY, 8));
		
		// Reopen shaft
		System.out.println("\nReopening shaft at surface...");
		chunk.setBlock(8, surfaceY, 8, Blocks.AIR);
		System.out.println("  Heightmap now: " + chunk.getHeightmap().getHeight(8, 8));
		System.out.println("  Skylight at surface: " + chunk.getSkyLight(8, surfaceY, 8));
		for (int y = surfaceY; y >= bottomY; y--) {
			System.out.println("  Skylight at y=" + y + ": " + chunk.getSkyLight(8, y, 8));
		}
	}
}
