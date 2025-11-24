package mattmc.world.level.lighting;

import mattmc.world.level.block.Block;
import mattmc.world.level.chunk.ChunkUtils;
import mattmc.world.level.chunk.LevelChunk;

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
		// Find the topmost opaque block in this column
		int heightmapY = findTopmostOpaqueBlock(chunk, x, z);
		
		// Update the heightmap
		chunk.getHeightmap().setHeight(x, z, heightmapY);
		
		// Set skylight values based on heightmap
		for (int y = 0; y < LevelChunk.HEIGHT; y++) {
			int worldY = ChunkUtils.localToWorldY(y);
			
			// If heightmap is MIN_Y, there are no opaque blocks, so all blocks get full skylight
			// Otherwise, blocks above the heightmap get full skylight, blocks at/below get none
			if (heightmapY == ChunkUtils.MIN_Y || worldY > heightmapY) {
				// Above the heightmap (or no opaque blocks) - full skylight
				chunk.setSkyLight(x, y, z, 15);
			} else {
				// At or below the heightmap - no skylight
				chunk.setSkyLight(x, y, z, 0);
			}
		}
	}
	
	/**
	 * Find the topmost opaque block in a column.
	 * 
	 * Scans from top to bottom looking for the first opaque block
	 * (opacity > 0). Returns the world Y coordinate of that block,
	 * or MIN_Y if no opaque blocks are found.
	 * 
	 * @param chunk The chunk
	 * @param x Column X coordinate (0-15)
	 * @param z Column Z coordinate (0-15)
	 * @return World Y coordinate of topmost opaque block
	 */
	private static int findTopmostOpaqueBlock(LevelChunk chunk, int x, int z) {
		// Scan from top to bottom
		for (int y = LevelChunk.HEIGHT - 1; y >= 0; y--) {
			Block block = chunk.getBlock(x, y, z);
			
			// Check if this block is opaque (blocks light)
			if (block.getOpacity() > 0) {
				// Found the topmost opaque block - return its world Y
				return ChunkUtils.localToWorldY(y);
			}
		}
		
		// No opaque blocks found in this column - return minimum Y
		return ChunkUtils.MIN_Y;
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
