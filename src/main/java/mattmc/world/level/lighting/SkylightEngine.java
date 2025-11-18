package mattmc.world.level.lighting;

import mattmc.world.level.block.Block;
import mattmc.world.level.chunk.LevelChunk;

import java.util.ArrayDeque;
import java.util.Queue;

import static mattmc.world.level.lighting.LightingConstants.FULL_OPACITY;

/**
 * Handles skylight propagation using BFS queues.
 * 
 * Unlike the static SkylightInitializer, this engine propagates skylight
 * below the heightmap into cavities using BFS with attenuation.
 * 
 * Features:
 * - BFS propagation below heightmap with attenuation (-1 per step)
 * - Add/remove queues for dynamic updates when terrain changes
 * - Heightmap updates when columns change
 * - Opacity blocking: fully opaque blocks (opacity >= 15) stop propagation
 */
public class SkylightEngine {
	
	/**
	 * Represents a skylight node in the BFS queue.
	 */
	private static class SkyNode {
		final int x, y, z;
		final int lightLevel;
		
		SkyNode(int x, int y, int z, int lightLevel) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.lightLevel = lightLevel;
		}
	}
	
	// BFS queues
	private final Queue<SkyNode> addQueue = new ArrayDeque<>();
	private final Queue<SkyNode> removeQueue = new ArrayDeque<>();
	
	/**
	 * Initialize skylight for entire chunk with BFS propagation.
	 * This replaces the static initialization with full propagation.
	 * 
	 * @param chunk The chunk to initialize
	 */
	public void initializeChunkSkylight(LevelChunk chunk) {
		// First, compute heightmap and do initial vertical fill
		for (int x = 0; x < LevelChunk.WIDTH; x++) {
			for (int z = 0; z < LevelChunk.DEPTH; z++) {
				initializeColumnSkylight(chunk, x, z);
			}
		}
		
		// Now propagate skylight into cavities below heightmap
		propagateSkylightBelowHeightmap(chunk);
	}
	
	/**
	 * Initialize skylight for a single column (vertical fill above heightmap with attenuation).
	 */
	private void initializeColumnSkylight(LevelChunk chunk, int x, int z) {
		// Find the topmost fully opaque block
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
				// At or below the heightmap - no skylight (will be propagated if cavity exists)
				chunk.setSkyLight(x, y, z, 0);
			}
		}
	}
	
	/**
	 * Propagate skylight below the heightmap into cavities.
	 * This uses BFS to spread skylight downward and sideways with attenuation.
	 * 
	 * For columns that have opaque blocks (heightmap > MIN_Y), this propagates
	 * light horizontally into any air cavities below the heightmap.
	 * 
	 * For open columns (heightmap = MIN_Y), the flat light=15 from initializeColumnSkylight
	 * is kept - representing direct unobstructed skylight.
	 * 
	 * Light does NOT pass through opaque blocks.
	 */
	private void propagateSkylightBelowHeightmap(LevelChunk chunk) {
		addQueue.clear();
		
		// Seed from blocks that have light and are adjacent to blocks that need light
		// This handles horizontal propagation into cavities
		for (int x = 0; x < LevelChunk.WIDTH; x++) {
			for (int z = 0; z < LevelChunk.DEPTH; z++) {
				int heightmapY = chunk.getHeightmap().getHeight(x, z);
				
				// For open columns (heightmap = MIN_Y), add all lit blocks to seed
				// horizontal propagation to neighboring columns/cavities
				if (heightmapY == LevelChunk.MIN_Y) {
					for (int y = 0; y < LevelChunk.HEIGHT; y++) {
						int light = chunk.getSkyLight(x, y, z);
						if (light > 0) {
							addQueue.offer(new SkyNode(x, y, z, light));
						}
					}
				}
			}
		}
		
		// BFS propagation to neighbors (horizontal and vertical) with opacity-based attenuation
		SkyNode node;
		while ((node = addQueue.poll()) != null) {
			
			if (node.lightLevel <= 0) {
				continue;
			}
			
			// Propagate to neighbors with source light level
			// Attenuation will be calculated based on target block opacity
			propagateSkyToNeighbor(chunk, node.x - 1, node.y, node.z, node.lightLevel);
			propagateSkyToNeighbor(chunk, node.x + 1, node.y, node.z, node.lightLevel);
			propagateSkyToNeighbor(chunk, node.x, node.y - 1, node.z, node.lightLevel);
			propagateSkyToNeighbor(chunk, node.x, node.y + 1, node.z, node.lightLevel);
			propagateSkyToNeighbor(chunk, node.x, node.y, node.z - 1, node.lightLevel);
			propagateSkyToNeighbor(chunk, node.x, node.y, node.z + 1, node.lightLevel);
		}
	}
	
	/**
	 * Propagate skylight to a neighbor position with opacity-based attenuation.
	 * 
	 * Attenuation rule:
	 * - If block opacity >= FULL_OPACITY (15): hard blocker, no propagation
	 * - Otherwise: decrement = Math.max(1, blockOpacity)
	 *   This ensures light always decreases by at least 1 per step,
	 *   but semi-transparent blocks cause additional attenuation.
	 */
	private void propagateSkyToNeighbor(LevelChunk chunk, int x, int y, int z, int sourceLight) {
		// Check bounds
		if (x < 0 || x >= LevelChunk.WIDTH || y < 0 || y >= LevelChunk.HEIGHT || 
		    z < 0 || z >= LevelChunk.DEPTH) {
			return;
		}
		
		// Check if block is fully opaque
		Block block = chunk.getBlock(x, y, z);
		if (block == null) {
			return; // Null block
		}
		
		int blockOpacity = block.getOpacity();
		if (blockOpacity >= FULL_OPACITY) {
			return; // Fully opaque block stops light
		}
		
		// Apply opacity-based attenuation
		// Decrement is at least 1 (for air/transparent) or more for semi-transparent blocks
		int decrement = Math.max(1, blockOpacity);
		int newLight = Math.max(0, sourceLight - decrement);
		
		int currentLight = chunk.getSkyLight(x, y, z);
		if (newLight > currentLight) {
			chunk.setSkyLight(x, y, z, newLight);  // Set the light value before adding to queue
			addQueue.offer(new SkyNode(x, y, z, newLight));
		}
	}
	
	/**
	 * Update skylight when a block changes in a column.
	 * This updates the heightmap and re-propagates skylight as needed.
	 * 
	 * @param chunk The chunk
	 * @param x Chunk-local X coordinate
	 * @param y Chunk-local Y coordinate
	 * @param z Chunk-local Z coordinate
	 * @param newBlock The new block
	 * @param oldBlock The old block
	 */
	public void updateColumnSkylight(LevelChunk chunk, int x, int y, int z, Block newBlock, Block oldBlock) {
		int worldY = LevelChunk.chunkYToWorldY(y);
		int oldHeightmapY = chunk.getHeightmap().getHeight(x, z);
		
		// Check if opacity changed
		int oldOpacity = oldBlock.getOpacity();
		int newOpacity = newBlock.getOpacity();
		
		if (oldOpacity == newOpacity) {
			return; // No opacity change, no skylight update needed
		}
		
		// Recompute heightmap for this column
		int newHeightmapY = findTopmostOpaqueBlock(chunk, x, z);
		chunk.getHeightmap().setHeight(x, z, newHeightmapY);
		
		// If heightmap changed, update skylight for the entire column
		if (oldHeightmapY != newHeightmapY) {
			updateColumnSkylightAfterHeightmapChange(chunk, x, z, oldHeightmapY, newHeightmapY);
			return; // Heightmap change handles all skylight updates for this column
		}
		
		// Heightmap didn't change, but opacity changed at this specific position
		if (newOpacity < FULL_OPACITY && oldOpacity >= FULL_OPACITY) {
			// Block became transparent - add skylight
			addSkylightAt(chunk, x, y, z);
		} else if (newOpacity >= FULL_OPACITY && oldOpacity < FULL_OPACITY) {
			// Block became opaque - remove skylight
			removeSkylightAt(chunk, x, y, z);
		}
	}
	
	/**
	 * Update column skylight after heightmap change.
	 */
	private void updateColumnSkylightAfterHeightmapChange(LevelChunk chunk, int x, int z, 
	                                                       int oldHeightmapY, int newHeightmapY) {
		if (newHeightmapY > oldHeightmapY) {
			// Heightmap increased (block placed above) - remove skylight below
			for (int y = 0; y < LevelChunk.HEIGHT; y++) {
				int worldY = LevelChunk.chunkYToWorldY(y);
				if (worldY > oldHeightmapY && worldY <= newHeightmapY) {
					// This area lost sky access
					removeSkylightAt(chunk, x, y, z);
				}
			}
		} else {
			// Heightmap decreased (block removed) - add skylight
			addQueue.clear();
			
			// First pass: set skylight to 15 for all positions that gained sky access
			for (int y = 0; y < LevelChunk.HEIGHT; y++) {
				int worldY = LevelChunk.chunkYToWorldY(y);
				if (worldY > newHeightmapY && worldY <= oldHeightmapY) {
					// This area gained sky access - set to full skylight
					chunk.setSkyLight(x, y, z, 15);
					// Add to queue for propagation
					addQueue.offer(new SkyNode(x, y, z, 15));
				}
			}
			
			// Second pass: BFS propagation to neighbors
			SkyNode node;
			while ((node = addQueue.poll()) != null) {
				if (node.lightLevel > 0) {
					propagateSkyToNeighbor(chunk, node.x - 1, node.y, node.z, node.lightLevel);
					propagateSkyToNeighbor(chunk, node.x + 1, node.y, node.z, node.lightLevel);
					propagateSkyToNeighbor(chunk, node.x, node.y - 1, node.z, node.lightLevel);
					propagateSkyToNeighbor(chunk, node.x, node.y + 1, node.z, node.lightLevel);
					propagateSkyToNeighbor(chunk, node.x, node.y, node.z - 1, node.lightLevel);
					propagateSkyToNeighbor(chunk, node.x, node.y, node.z + 1, node.lightLevel);
				}
			}
		}
	}
	
	/**
	 * Add skylight at a position and propagate.
	 */
	private void addSkylightAt(LevelChunk chunk, int x, int y, int z) {
		// Check what skylight should be here based on neighbors
		int maxNeighborLight = 0;
		
		// Check all 6 neighbors
		maxNeighborLight = Math.max(maxNeighborLight, getSkyLightSafe(chunk, x - 1, y, z));
		maxNeighborLight = Math.max(maxNeighborLight, getSkyLightSafe(chunk, x + 1, y, z));
		maxNeighborLight = Math.max(maxNeighborLight, getSkyLightSafe(chunk, x, y - 1, z));
		maxNeighborLight = Math.max(maxNeighborLight, getSkyLightSafe(chunk, x, y + 1, z));
		maxNeighborLight = Math.max(maxNeighborLight, getSkyLightSafe(chunk, x, y, z - 1));
		maxNeighborLight = Math.max(maxNeighborLight, getSkyLightSafe(chunk, x, y, z + 1));
		
		if (maxNeighborLight > 1) {
			int newLight = maxNeighborLight - 1;
			chunk.setSkyLight(x, y, z, newLight);
			
			// Propagate to neighbors
			addQueue.clear();
			addQueue.offer(new SkyNode(x, y, z, newLight));
			
			SkyNode node2;
			while ((node2 = addQueue.poll()) != null) {
				if (node2.lightLevel > 0) {
					propagateSkyToNeighbor(chunk, node2.x - 1, node2.y, node2.z, node2.lightLevel);
					propagateSkyToNeighbor(chunk, node2.x + 1, node2.y, node2.z, node2.lightLevel);
					propagateSkyToNeighbor(chunk, node2.x, node2.y - 1, node2.z, node2.lightLevel);
					propagateSkyToNeighbor(chunk, node2.x, node2.y + 1, node2.z, node2.lightLevel);
					propagateSkyToNeighbor(chunk, node2.x, node2.y, node2.z - 1, node2.lightLevel);
					propagateSkyToNeighbor(chunk, node2.x, node2.y, node2.z + 1, node2.lightLevel);
				}
			}
		}
	}
	
	/**
	 * Remove skylight at a position and re-propagate from remaining sources.
	 */
	private void removeSkylightAt(LevelChunk chunk, int x, int y, int z) {
		int removedLight = chunk.getSkyLight(x, y, z);
		if (removedLight <= 0) {
			return;
		}
		
		chunk.setSkyLight(x, y, z, 0);
		
		// BFS removal similar to blockLight
		removeQueue.clear();
		removeQueue.offer(new SkyNode(x, y, z, removedLight));
		
		Queue<SkyNode> boundaryQueue = new ArrayDeque<>();
		
		SkyNode node;
		while ((node = removeQueue.poll()) != null) {
			
			checkNeighborForRemoval(chunk, node.x - 1, node.y, node.z, node.lightLevel, boundaryQueue);
			checkNeighborForRemoval(chunk, node.x + 1, node.y, node.z, node.lightLevel, boundaryQueue);
			checkNeighborForRemoval(chunk, node.x, node.y - 1, node.z, node.lightLevel, boundaryQueue);
			checkNeighborForRemoval(chunk, node.x, node.y + 1, node.z, node.lightLevel, boundaryQueue);
			checkNeighborForRemoval(chunk, node.x, node.y, node.z - 1, node.lightLevel, boundaryQueue);
			checkNeighborForRemoval(chunk, node.x, node.y, node.z + 1, node.lightLevel, boundaryQueue);
		}
		
		// Re-propagate from boundary
		addQueue.clear();
		addQueue.addAll(boundaryQueue);
		
		SkyNode node3;
		while ((node3 = addQueue.poll()) != null) {
			int currentLight = chunk.getSkyLight(node3.x, node3.y, node3.z);
			
			if (currentLight > 0) {
				propagateSkyToNeighbor(chunk, node3.x - 1, node3.y, node3.z, currentLight);
				propagateSkyToNeighbor(chunk, node3.x + 1, node3.y, node3.z, currentLight);
				propagateSkyToNeighbor(chunk, node3.x, node3.y - 1, node3.z, currentLight);
				propagateSkyToNeighbor(chunk, node3.x, node3.y + 1, node3.z, currentLight);
				propagateSkyToNeighbor(chunk, node3.x, node3.y, node3.z - 1, currentLight);
				propagateSkyToNeighbor(chunk, node3.x, node3.y, node3.z + 1, currentLight);
			}
		}
	}
	
	/**
	 * Check a neighbor for removal during skylight removal BFS.
	 */
	private void checkNeighborForRemoval(LevelChunk chunk, int x, int y, int z,
	                                      int sourceLight, Queue<SkyNode> boundaryQueue) {
		if (x < 0 || x >= LevelChunk.WIDTH || y < 0 || y >= LevelChunk.HEIGHT || 
		    z < 0 || z >= LevelChunk.DEPTH) {
			return;
		}
		
		int neighborLight = chunk.getSkyLight(x, y, z);
		if (neighborLight == 0) {
			return;
		}
		
		if (neighborLight < sourceLight) {
			// This light came from the removed source
			chunk.setSkyLight(x, y, z, 0);
			removeQueue.offer(new SkyNode(x, y, z, neighborLight));
		} else {
			// This light is from another source - boundary for re-propagation
			boundaryQueue.offer(new SkyNode(x, y, z, neighborLight));
		}
	}
	
	/**
	 * Get skylight safely (returns 0 if out of bounds).
	 */
	private int getSkyLightSafe(LevelChunk chunk, int x, int y, int z) {
		if (x < 0 || x >= LevelChunk.WIDTH || y < 0 || y >= LevelChunk.HEIGHT || 
		    z < 0 || z >= LevelChunk.DEPTH) {
			return 0;
		}
		return chunk.getSkyLight(x, y, z);
	}
	
	/**
	 * Find the topmost opaque block in a column.
	 */
	private int findTopmostOpaqueBlock(LevelChunk chunk, int x, int z) {
		for (int y = LevelChunk.HEIGHT - 1; y >= 0; y--) {
			Block block = chunk.getBlock(x, y, z);
			if (block != null && block.getOpacity() >= FULL_OPACITY) {
				return LevelChunk.chunkYToWorldY(y);
			}
		}
		return LevelChunk.MIN_Y;
	}
}
