package mattmc.world.level.lighting;

import mattmc.world.level.block.Block;
import mattmc.world.level.chunk.LevelChunk;

import static mattmc.world.level.lighting.LightingConstants.FULL_OPACITY;

/**
 * Static skylight initializer for chunks.
 * 
 * Computes the heightmap (topmost opaque block per column) and initializes
 * skylight values: from world top to first opaque block gets skyLight=15,
 * then 0 below. No propagation - just a static fill based on heightmap.
 */
public class SkylightInitializer {
	
	/**
	 * Initialize skylight for a chunk based on its heightmap.
	 * 
	 * This performs a static initialization:
	 * 1. Computes the heightmap (topmost opaque block per column)
	 * 2. Sets skyLight=15 from world top down to the heightmap
	 * 3. Sets skyLight=0 below the heightmap
	 * 
	 * No horizontal propagation is performed - this is just vertical fill.
	 * 
	 * @param chunk The chunk to initialize skylight for
	 */
	public static void initializeChunkSkylight(LevelChunk chunk) {
		// Process each column in the chunk
		for (int x = 0; x < LevelChunk.WIDTH; x++) {
			for (int z = 0; z < LevelChunk.DEPTH; z++) {
				initializeColumnSkylight(chunk, x, z);
			}
		}
	}
	
	/**
	 * Initialize skylight for a single column.
	 * 
	 * @param chunk The chunk
	 * @param x Column X coordinate (0-15)
	 * @param z Column Z coordinate (0-15)
	 */
	private static void initializeColumnSkylight(LevelChunk chunk, int x, int z) {
		// Find the topmost fully opaque block in this column
		int heightmapY = findTopmostOpaqueBlock(chunk, x, z);
		
		// Update the heightmap
		chunk.getHeightmap().setHeight(x, z, heightmapY);
		
		// Set skylight values with vertical attenuation for semi-transparent blocks
		// Air blocks (opacity 0) maintain full skylight in a vertical column
		// Semi-transparent blocks (opacity 1-14) attenuate light
		
		int currentLight = 15; // Start with full sky light at top
		
		for (int y = LevelChunk.HEIGHT - 1; y >= 0; y--) {
			int worldY = LevelChunk.chunkYToWorldY(y);
			
			if (heightmapY == LevelChunk.MIN_Y || worldY > heightmapY) {
				// Above the heightmap OR no fully opaque blocks
				
				// Set light at this position
				chunk.setSkyLight(x, y, z, currentLight);
				
				// Calculate light for next block down based on THIS block's opacity
				Block block = chunk.getBlock(x, y, z);
				int blockOpacity = (block != null) ? block.getOpacity() : 0;
				
				if (blockOpacity > 0) {
					// Semi-transparent block - apply attenuation
					int decrement = Math.max(1, blockOpacity);
					currentLight = Math.max(0, currentLight - decrement);
				}
				// For air (opacity 0), maintain current light level (no attenuation in vertical column)
			} else {
				// At or below the heightmap - no skylight
				chunk.setSkyLight(x, y, z, 0);
			}
		}
	}
	
	/**
	 * Find the topmost opaque block in a column.
	 * 
	 * Scans from top to bottom looking for the first fully opaque block
	 * (opacity >= FULL_OPACITY). Returns the world Y coordinate of that block,
	 * or MIN_Y if no fully opaque blocks are found.
	 * 
	 * Semi-transparent blocks (opacity 1-14) are NOT treated as blockers
	 * for the heightmap, allowing skylight to propagate through them with
	 * appropriate attenuation.
	 * 
	 * @param chunk The chunk
	 * @param x Column X coordinate (0-15)
	 * @param z Column Z coordinate (0-15)
	 * @return World Y coordinate of topmost fully opaque block
	 */
	private static int findTopmostOpaqueBlock(LevelChunk chunk, int x, int z) {
		// Scan from top to bottom
		for (int y = LevelChunk.HEIGHT - 1; y >= 0; y--) {
			Block block = chunk.getBlock(x, y, z);
			
			// Check if this block is fully opaque (hard blocker)
			if (block.getOpacity() >= FULL_OPACITY) {
				// Found the topmost fully opaque block - return its world Y
				return LevelChunk.chunkYToWorldY(y);
			}
		}
		
		// No fully opaque blocks found in this column - return minimum Y
		return LevelChunk.MIN_Y;
	}
	
	/**
	 * Recompute heightmap for a chunk without updating skylight.
	 * Useful for when blocks have changed but skylight will be updated separately.
	 * 
	 * @param chunk The chunk to update heightmap for
	 */
	public static void recomputeHeightmap(LevelChunk chunk) {
		for (int x = 0; x < LevelChunk.WIDTH; x++) {
			for (int z = 0; z < LevelChunk.DEPTH; z++) {
				int heightmapY = findTopmostOpaqueBlock(chunk, x, z);
				chunk.getHeightmap().setHeight(x, z, heightmapY);
			}
		}
	}
}
