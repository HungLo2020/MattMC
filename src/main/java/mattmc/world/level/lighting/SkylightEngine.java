package mattmc.world.level.lighting;

import mattmc.world.level.block.Block;
import mattmc.world.level.chunk.ChunkUtils;
import mattmc.world.level.chunk.LevelChunk;

import java.util.ArrayDeque;
import java.util.Queue;

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
 * - Cross-chunk propagation: light propagates seamlessly across chunk boundaries
 */
public class SkylightEngine {
	
	/**
	 * Represents a skylight node in the BFS queue.
	 */
	private static class SkyNode {
		final LevelChunk chunk; // Added to support cross-chunk propagation
		final int x, y, z;
		final int lightLevel;
		
		SkyNode(LevelChunk chunk, int x, int y, int z, int lightLevel) {
			this.chunk = chunk;
			this.x = x;
			this.y = y;
			this.z = z;
			this.lightLevel = lightLevel;
		}
		
		// Convenience constructor for single-chunk use
		SkyNode(int x, int y, int z, int lightLevel) {
			this(null, x, y, z, lightLevel);
		}
	}
	
	// BFS queues
	private final Queue<SkyNode> addQueue = new ArrayDeque<>();
	private final Queue<SkyNode> removeQueue = new ArrayDeque<>();
	
	// Cross-chunk propagator for handling neighbor chunks
	private CrossChunkLightPropagator crossChunkPropagator;
	
	/**
	 * Set the cross-chunk propagator for handling neighbor chunks.
	 */
	public void setCrossChunkPropagator(CrossChunkLightPropagator propagator) {
		this.crossChunkPropagator = propagator;
	}
	
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
	 * Initialize skylight for a single column (vertical fill above heightmap).
	 */
	private void initializeColumnSkylight(LevelChunk chunk, int x, int z) {
		// Find the topmost opaque block
		int heightmapY = findTopmostOpaqueBlock(chunk, x, z);
		
		// Update the heightmap
		chunk.getHeightmap().setHeight(x, z, heightmapY);
		
		// Set skylight values based on heightmap
		for (int y = 0; y < LevelChunk.HEIGHT; y++) {
			int worldY = ChunkUtils.localToWorldY(y);
			
			if (heightmapY == ChunkUtils.MIN_Y || worldY > heightmapY) {
				// Above the heightmap OR no opaque blocks - full skylight from direct sky access
				// Note: For open columns (no opaque blocks), propagateSkylightBelowHeightmap
				// will re-process with attenuation where needed
				chunk.setSkyLight(x, y, z, 15);
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
				if (heightmapY == ChunkUtils.MIN_Y) {
					for (int y = 0; y < LevelChunk.HEIGHT; y++) {
						int light = chunk.getSkyLight(x, y, z);
						if (light > 0) {
							addQueue.offer(new SkyNode(chunk, x, y, z, light));
						}
					}
				}
			}
		}
		
		// BFS propagation to neighbors (horizontal and vertical) with attenuation
		SkyNode node;
		while ((node = addQueue.poll()) != null) {
			
			if (node.lightLevel <= 0) {
				continue;
			}
			
			// Use the chunk from the node to support cross-chunk propagation
			LevelChunk nodeChunk = node.chunk != null ? node.chunk : chunk;
			
			// Propagate to neighbors with attenuation
			int newLight = node.lightLevel - 1;
			if (newLight > 0) {
				propagateSkyToNeighbor(nodeChunk, node.x - 1, node.y, node.z, newLight);
				propagateSkyToNeighbor(nodeChunk, node.x + 1, node.y, node.z, newLight);
				propagateSkyToNeighbor(nodeChunk, node.x, node.y - 1, node.z, newLight);
				propagateSkyToNeighbor(nodeChunk, node.x, node.y + 1, node.z, newLight);
				propagateSkyToNeighbor(nodeChunk, node.x, node.y, node.z - 1, newLight);
				propagateSkyToNeighbor(nodeChunk, node.x, node.y, node.z + 1, newLight);
			}
		}
	}
	
	/**
	 * Propagate skylight to a neighbor position.
	 * Now supports cross-chunk propagation.
	 */
	private void propagateSkyToNeighbor(LevelChunk chunk, int x, int y, int z, int newLight) {
		// Check if crossing chunk boundaries (X or Z out of bounds)
		if (x < 0 || x >= LevelChunk.WIDTH || z < 0 || z >= LevelChunk.DEPTH) {
			// Crossing chunk boundary - use cross-chunk propagator if available
			if (crossChunkPropagator != null) {
				crossChunkPropagator.propagateSkylightCross(chunk, x, y, z, newLight + 1);
			}
			return;
		}
		
		// Check Y bounds (no chunk crossing for Y)
		if (y < 0 || y >= LevelChunk.HEIGHT) {
			return; // Out of world bounds
		}
		
		// Check if block is opaque
		Block block = chunk.getBlock(x, y, z);
		if (block == null || block.getOpacity() >= 15) {
			return; // Opaque block stops light or null block
		}
		
		int currentLight = chunk.getSkyLight(x, y, z);
		if (newLight > currentLight) {
			chunk.setSkyLight(x, y, z, newLight);  // Set the light value before adding to queue
			addQueue.offer(new SkyNode(chunk, x, y, z, newLight));
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
		int worldY = ChunkUtils.localToWorldY(y);
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
		if (newOpacity < 15 && oldOpacity >= 15) {
			// Block became transparent - add skylight
			addSkylightAt(chunk, x, y, z);
		} else if (newOpacity >= 15 && oldOpacity < 15) {
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
			// We need to do a batch removal to properly propagate darkness
			removeQueue.clear();
			Queue<SkyNode> boundaryQueue = new ArrayDeque<>();
			
			// First, clear all skylight in the column and seed the removal queue
			for (int y = 0; y < LevelChunk.HEIGHT; y++) {
				int worldY = ChunkUtils.localToWorldY(y);
				if (worldY > oldHeightmapY && worldY <= newHeightmapY) {
					// This area lost sky access
					int removedLight = chunk.getSkyLight(x, y, z);
					if (removedLight > 0) {
						chunk.setSkyLight(x, y, z, 0);
						removeQueue.offer(new SkyNode(chunk, x, y, z, removedLight));
					}
				}
			}
			
			// BFS removal to propagate darkness to all affected blocks
			SkyNode node;
			while ((node = removeQueue.poll()) != null) {
				LevelChunk nodeChunk = node.chunk != null ? node.chunk : chunk;
				
				checkNeighborForRemoval(nodeChunk, node.x - 1, node.y, node.z, node.lightLevel, boundaryQueue);
				checkNeighborForRemoval(nodeChunk, node.x + 1, node.y, node.z, node.lightLevel, boundaryQueue);
				checkNeighborForRemoval(nodeChunk, node.x, node.y - 1, node.z, node.lightLevel, boundaryQueue);
				checkNeighborForRemoval(nodeChunk, node.x, node.y + 1, node.z, node.lightLevel, boundaryQueue);
				checkNeighborForRemoval(nodeChunk, node.x, node.y, node.z - 1, node.lightLevel, boundaryQueue);
				checkNeighborForRemoval(nodeChunk, node.x, node.y, node.z + 1, node.lightLevel, boundaryQueue);
			}
			
			// Re-propagate from boundary (blocks that had light from other sources)
			addQueue.clear();
			addQueue.addAll(boundaryQueue);
			
			SkyNode node3;
			while ((node3 = addQueue.poll()) != null) {
				LevelChunk nodeChunk = node3.chunk != null ? node3.chunk : chunk;
				int currentLight = nodeChunk.getSkyLight(node3.x, node3.y, node3.z);
				int nextLight = currentLight - 1;
				
				if (nextLight > 0) {
					propagateSkyToNeighbor(nodeChunk, node3.x - 1, node3.y, node3.z, nextLight);
					propagateSkyToNeighbor(nodeChunk, node3.x + 1, node3.y, node3.z, nextLight);
					propagateSkyToNeighbor(nodeChunk, node3.x, node3.y - 1, node3.z, nextLight);
					propagateSkyToNeighbor(nodeChunk, node3.x, node3.y + 1, node3.z, nextLight);
					propagateSkyToNeighbor(nodeChunk, node3.x, node3.y, node3.z - 1, nextLight);
					propagateSkyToNeighbor(nodeChunk, node3.x, node3.y, node3.z + 1, nextLight);
				}
			}
		} else {
			// Heightmap decreased (block removed) - add skylight
			addQueue.clear();
			
			// First pass: set skylight to 15 for all positions that gained sky access
			for (int y = 0; y < LevelChunk.HEIGHT; y++) {
				int worldY = ChunkUtils.localToWorldY(y);
				if (worldY > newHeightmapY && worldY <= oldHeightmapY) {
					// This area gained sky access - set to full skylight
					chunk.setSkyLight(x, y, z, 15);
					// Add to queue for propagation
					addQueue.offer(new SkyNode(chunk, x, y, z, 15));
				}
			}
			
			// Second pass: BFS propagation to neighbors
			SkyNode node;
			while ((node = addQueue.poll()) != null) {
				LevelChunk nodeChunk = node.chunk != null ? node.chunk : chunk;
				int nextLight = node.lightLevel - 1;
				if (nextLight > 0) {
					propagateSkyToNeighbor(nodeChunk, node.x - 1, node.y, node.z, nextLight);
					propagateSkyToNeighbor(nodeChunk, node.x + 1, node.y, node.z, nextLight);
					propagateSkyToNeighbor(nodeChunk, node.x, node.y - 1, node.z, nextLight);
					propagateSkyToNeighbor(nodeChunk, node.x, node.y + 1, node.z, nextLight);
					propagateSkyToNeighbor(nodeChunk, node.x, node.y, node.z - 1, nextLight);
					propagateSkyToNeighbor(nodeChunk, node.x, node.y, node.z + 1, nextLight);
				}
			}
		}
	}
	
	/**
	 * Add skylight at a position and propagate.
	 */
	private void addSkylightAt(LevelChunk chunk, int x, int y, int z) {
		// Check what skylight should be here based on neighbors
		// This also needs to check cross-chunk neighbors for proper propagation
		int maxNeighborLight = 0;
		
		// Check all 6 neighbors (within same chunk for now, cross-chunk done via propagation)
		maxNeighborLight = Math.max(maxNeighborLight, getSkyLightSafe(chunk, x - 1, y, z));
		maxNeighborLight = Math.max(maxNeighborLight, getSkyLightSafe(chunk, x + 1, y, z));
		maxNeighborLight = Math.max(maxNeighborLight, getSkyLightSafe(chunk, x, y - 1, z));
		maxNeighborLight = Math.max(maxNeighborLight, getSkyLightSafe(chunk, x, y + 1, z));
		maxNeighborLight = Math.max(maxNeighborLight, getSkyLightSafe(chunk, x, y, z - 1));
		maxNeighborLight = Math.max(maxNeighborLight, getSkyLightSafe(chunk, x, y, z + 1));
		
		// Also check cross-chunk neighbors if we're at chunk edge
		if (crossChunkPropagator != null) {
			maxNeighborLight = Math.max(maxNeighborLight, getSkyLightAcrossChunks(chunk, x - 1, y, z));
			maxNeighborLight = Math.max(maxNeighborLight, getSkyLightAcrossChunks(chunk, x + 1, y, z));
			maxNeighborLight = Math.max(maxNeighborLight, getSkyLightAcrossChunks(chunk, x, y, z - 1));
			maxNeighborLight = Math.max(maxNeighborLight, getSkyLightAcrossChunks(chunk, x, y, z + 1));
		}
		
		if (maxNeighborLight > 1) {
			int newLight = maxNeighborLight - 1;
			chunk.setSkyLight(x, y, z, newLight);
			
			// Propagate to neighbors
			addQueue.clear();
			addQueue.offer(new SkyNode(chunk, x, y, z, newLight));
			
			SkyNode node2;
			while ((node2 = addQueue.poll()) != null) {
				LevelChunk nodeChunk = node2.chunk != null ? node2.chunk : chunk;
				int nextLight = node2.lightLevel - 1;
				if (nextLight > 0) {
					propagateSkyToNeighbor(nodeChunk, node2.x - 1, node2.y, node2.z, nextLight);
					propagateSkyToNeighbor(nodeChunk, node2.x + 1, node2.y, node2.z, nextLight);
					propagateSkyToNeighbor(nodeChunk, node2.x, node2.y - 1, node2.z, nextLight);
					propagateSkyToNeighbor(nodeChunk, node2.x, node2.y + 1, node2.z, nextLight);
					propagateSkyToNeighbor(nodeChunk, node2.x, node2.y, node2.z - 1, nextLight);
					propagateSkyToNeighbor(nodeChunk, node2.x, node2.y, node2.z + 1, nextLight);
				}
			}
		}
	}
	
	/**
	 * Get skylight from a neighbor that might be in a different chunk.
	 */
	private int getSkyLightAcrossChunks(LevelChunk chunk, int x, int y, int z) {
		if (y < 0 || y >= LevelChunk.HEIGHT) {
			return 0;
		}
		
		// If within current chunk, use normal method
		if (x >= 0 && x < LevelChunk.WIDTH && z >= 0 && z < LevelChunk.DEPTH) {
			return chunk.getSkyLight(x, y, z);
		}
		
		// Need to check neighbor chunk
		if (crossChunkPropagator == null) {
			return 0;
		}
		
		int targetChunkX = chunk.chunkX();
		int targetChunkZ = chunk.chunkZ();
		int targetLocalX = x;
		int targetLocalZ = z;
		
		if (x < 0) {
			targetChunkX--;
			targetLocalX = LevelChunk.WIDTH + x;
		} else if (x >= LevelChunk.WIDTH) {
			targetChunkX++;
			targetLocalX = x - LevelChunk.WIDTH;
		}
		
		if (z < 0) {
			targetChunkZ--;
			targetLocalZ = LevelChunk.DEPTH + z;
		} else if (z >= LevelChunk.DEPTH) {
			targetChunkZ++;
			targetLocalZ = z - LevelChunk.DEPTH;
		}
		
		LevelChunk neighborChunk = crossChunkPropagator.getNeighborChunk(targetChunkX, targetChunkZ);
		if (neighborChunk == null) {
			return 0;
		}
		
		return neighborChunk.getSkyLight(targetLocalX, y, targetLocalZ);
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
		removeQueue.offer(new SkyNode(chunk, x, y, z, removedLight));
		
		Queue<SkyNode> boundaryQueue = new ArrayDeque<>();
		
		SkyNode node;
		while ((node = removeQueue.poll()) != null) {
			LevelChunk nodeChunk = node.chunk != null ? node.chunk : chunk;
			
			checkNeighborForRemoval(nodeChunk, node.x - 1, node.y, node.z, node.lightLevel, boundaryQueue);
			checkNeighborForRemoval(nodeChunk, node.x + 1, node.y, node.z, node.lightLevel, boundaryQueue);
			checkNeighborForRemoval(nodeChunk, node.x, node.y - 1, node.z, node.lightLevel, boundaryQueue);
			checkNeighborForRemoval(nodeChunk, node.x, node.y + 1, node.z, node.lightLevel, boundaryQueue);
			checkNeighborForRemoval(nodeChunk, node.x, node.y, node.z - 1, node.lightLevel, boundaryQueue);
			checkNeighborForRemoval(nodeChunk, node.x, node.y, node.z + 1, node.lightLevel, boundaryQueue);
		}
		
		// Re-propagate from boundary
		addQueue.clear();
		addQueue.addAll(boundaryQueue);
		
		SkyNode node3;
		while ((node3 = addQueue.poll()) != null) {
			LevelChunk nodeChunk = node3.chunk != null ? node3.chunk : chunk;
			int currentLight = nodeChunk.getSkyLight(node3.x, node3.y, node3.z);
			int nextLight = currentLight - 1;
			
			if (nextLight > 0) {
				propagateSkyToNeighbor(nodeChunk, node3.x - 1, node3.y, node3.z, nextLight);
				propagateSkyToNeighbor(nodeChunk, node3.x + 1, node3.y, node3.z, nextLight);
				propagateSkyToNeighbor(nodeChunk, node3.x, node3.y - 1, node3.z, nextLight);
				propagateSkyToNeighbor(nodeChunk, node3.x, node3.y + 1, node3.z, nextLight);
				propagateSkyToNeighbor(nodeChunk, node3.x, node3.y, node3.z - 1, nextLight);
				propagateSkyToNeighbor(nodeChunk, node3.x, node3.y, node3.z + 1, nextLight);
			}
		}
	}
	
	/**
	 * Check a neighbor for removal during skylight removal BFS.
	 * Now supports cross-chunk removal.
	 */
	private void checkNeighborForRemoval(LevelChunk chunk, int x, int y, int z,
	                                      int sourceLight, Queue<SkyNode> boundaryQueue) {
		LevelChunk targetChunk = chunk;
		int targetX = x;
		int targetZ = z;
		
		// Handle cross-chunk boundaries
		if (x < 0 || x >= LevelChunk.WIDTH || z < 0 || z >= LevelChunk.DEPTH) {
			if (crossChunkPropagator == null) {
				return; // No cross-chunk support available
			}
			
			// Calculate target chunk coordinates
			int chunkX = chunk.chunkX();
			int chunkZ = chunk.chunkZ();
			
			if (x < 0) {
				chunkX--;
				targetX = LevelChunk.WIDTH + x;
			} else if (x >= LevelChunk.WIDTH) {
				chunkX++;
				targetX = x - LevelChunk.WIDTH;
			}
			
			if (z < 0) {
				chunkZ--;
				targetZ = LevelChunk.DEPTH + z;
			} else if (z >= LevelChunk.DEPTH) {
				chunkZ++;
				targetZ = z - LevelChunk.DEPTH;
			}
			
			// Get the neighbor chunk
			targetChunk = crossChunkPropagator.getNeighborChunk(chunkX, chunkZ);
			if (targetChunk == null) {
				return; // Neighbor chunk not loaded
			}
		}
		
		// Check Y bounds (no cross-chunk for vertical)
		if (y < 0 || y >= LevelChunk.HEIGHT) {
			return;
		}
		
		int neighborLight = targetChunk.getSkyLight(targetX, y, targetZ);
		if (neighborLight == 0) {
			return;
		}
		
		// Check if the neighbor has DIRECT sky access (above its column's heightmap).
		// If it does, its light comes from the sky, not from propagation, so we should
		// NOT remove it - instead treat it as a boundary for re-propagation.
		int neighborWorldY = ChunkUtils.localToWorldY(y);
		int neighborHeightmap = targetChunk.getHeightmap().getHeight(targetX, targetZ);
		
		if (neighborWorldY > neighborHeightmap || neighborHeightmap == ChunkUtils.MIN_Y) {
			// This block has direct sky access - its light comes from the sky, not from
			// the block being removed. Add it to boundary queue for re-propagation.
			boundaryQueue.offer(new SkyNode(targetChunk, targetX, y, targetZ, neighborLight));
			return;
		}
		
		// Neighbor is below its heightmap - check if its light came from the removed source
		if (neighborLight <= sourceLight) {
			// This light came from the removed source (or equal, meaning same step in BFS)
			targetChunk.setSkyLight(targetX, y, targetZ, 0);
			removeQueue.offer(new SkyNode(targetChunk, targetX, y, targetZ, neighborLight));
		} else {
			// This light is from a brighter source - boundary for re-propagation
			boundaryQueue.offer(new SkyNode(targetChunk, targetX, y, targetZ, neighborLight));
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
			if (block != null && block.getOpacity() >= 15) {
				return ChunkUtils.localToWorldY(y);
			}
		}
		return ChunkUtils.MIN_Y;
	}
}
