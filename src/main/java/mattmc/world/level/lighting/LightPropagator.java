package mattmc.world.level.lighting;

import mattmc.world.level.block.Block;
import mattmc.world.level.chunk.LevelChunk;

import java.util.ArrayDeque;
import java.util.Queue;

/**
 * Handles blockLight propagation using BFS queues.
 * 
 * Implements flood-fill light propagation with:
 * - Add queue: spreads light from emissive blocks (attenuation: value-1 per step)
 * - Remove queue: removes light when emissive blocks are removed
 * - Opacity blocking: blocks with opacity >= 15 stop light propagation
 * - Cross-chunk propagation: extends light across chunk boundaries when neighbors are loaded
 */
public class LightPropagator {
	
	/**
	 * Represents a light node in the BFS queue for RGB propagation.
	 */
	private static class LightNode {
		final LevelChunk chunk; // Added to support cross-chunk propagation
		final int x, y, z;
		final int r, g, b;  // RGB light levels (0-15)
		
		LightNode(LevelChunk chunk, int x, int y, int z, int r, int g, int b) {
			this.chunk = chunk;
			this.x = x;
			this.y = y;
			this.z = z;
			this.r = r;
			this.g = g;
			this.b = b;
		}
		
		// Legacy constructor for backward compatibility (converts single value to white RGB)
		LightNode(LevelChunk chunk, int x, int y, int z, int lightLevel) {
			this(chunk, x, y, z, lightLevel, lightLevel, lightLevel);
		}
	}
	
	// BFS queues
	private final Queue<LightNode> addQueue = new ArrayDeque<>();
	private final Queue<LightNode> removeQueue = new ArrayDeque<>();
	
	// Cross-chunk propagator for handling neighbor chunks
	private CrossChunkLightPropagator crossChunkPropagator;
	
	/**
	 * Set the cross-chunk propagator for handling neighbor chunks.
	 */
	public void setCrossChunkPropagator(CrossChunkLightPropagator propagator) {
		this.crossChunkPropagator = propagator;
	}
	
	/**
	 * Add blockLight RGB from an emissive block at the given position.
	 * This propagates light outward using BFS with attenuation.
	 * 
	 * @param chunk The chunk containing the block
	 * @param x Chunk-local X coordinate (0-15)
	 * @param y Chunk-local Y coordinate (0-383)
	 * @param z Chunk-local Z coordinate (0-15)
	 * @param r Red light emission level (0-15)
	 * @param g Green light emission level (0-15)
	 * @param b Blue light emission level (0-15)
	 */
	public void addBlockLightRGB(LevelChunk chunk, int x, int y, int z, int r, int g, int b) {
		if (r <= 0 && g <= 0 && b <= 0) {
			return; // No light to add
		}
		
		// Set the light at the source position
		chunk.setBlockLightRGB(x, y, z, r, g, b);
		
		// Enqueue the source for propagation
		addQueue.clear();
		addQueue.offer(new LightNode(chunk, x, y, z, r, g, b));
		
		// BFS propagation
		LightNode node;
		while ((node = addQueue.poll()) != null) {
			int currentR = node.r;
			int currentG = node.g;
			int currentB = node.b;
			
			// Only propagate if at least one channel is strong enough
			if (currentR <= 1 && currentG <= 1 && currentB <= 1) {
				continue; // Light too weak to propagate
			}
			
			// Attenuation (reduce each channel by 1, min 0)
			int newR = Math.max(0, currentR - 1);
			int newG = Math.max(0, currentG - 1);
			int newB = Math.max(0, currentB - 1);
			
			// Propagate to all 6 neighbors
			propagateRGBToNeighbor(node.chunk, node.x - 1, node.y, node.z, newR, newG, newB);
			propagateRGBToNeighbor(node.chunk, node.x + 1, node.y, node.z, newR, newG, newB);
			propagateRGBToNeighbor(node.chunk, node.x, node.y - 1, node.z, newR, newG, newB);
			propagateRGBToNeighbor(node.chunk, node.x, node.y + 1, node.z, newR, newG, newB);
			propagateRGBToNeighbor(node.chunk, node.x, node.y, node.z - 1, newR, newG, newB);
			propagateRGBToNeighbor(node.chunk, node.x, node.y, node.z + 1, newR, newG, newB);
		}
	}
	
	/**
	 * Add blockLight from an emissive block (legacy method, converts to white RGB).
	 * 
	 * @param chunk The chunk containing the block
	 * @param x Chunk-local X coordinate (0-15)
	 * @param y Chunk-local Y coordinate (0-383)
	 * @param z Chunk-local Z coordinate (0-15)
	 * @param emission Light emission level of the block (0-15)
	 * @deprecated Use addBlockLightRGB for RGB values
	 */
	@Deprecated
	public void addBlockLight(LevelChunk chunk, int x, int y, int z, int emission) {
		addBlockLightRGB(chunk, x, y, z, emission, emission, emission);
	}
	
	/**
	 * Propagate RGB light to a neighbor position.
	 * Now supports cross-chunk propagation.
	 */
	private void propagateRGBToNeighbor(LevelChunk chunk, int x, int y, int z, int newR, int newG, int newB) {
		// Check if crossing chunk boundaries
		if (x < 0 || x >= LevelChunk.WIDTH || z < 0 || z >= LevelChunk.DEPTH) {
			// Crossing chunk boundary - use cross-chunk propagator if available
			if (crossChunkPropagator != null) {
				crossChunkPropagator.propagateBlockLightRGBCross(chunk, x, y, z, newR + 1, newG + 1, newB + 1);
			}
			return;
		}
		
		// Check Y bounds (no chunk crossing)
		if (y < 0 || y >= LevelChunk.HEIGHT) {
			return; // Out of world bounds
		}
		
		// Within same chunk - propagate directly
		// Check if block is opaque (blocks light)
		Block block = chunk.getBlock(x, y, z);
		if (block == null || block.getOpacity() >= 15) {
			return; // Fully opaque block stops light or null
		}
		
		// Get current light at neighbor
		int currentR = chunk.getBlockLightR(x, y, z);
		int currentG = chunk.getBlockLightG(x, y, z);
		int currentB = chunk.getBlockLightB(x, y, z);
		
		// Only update if new light is brighter in at least one channel
		if (newR > currentR || newG > currentG || newB > currentB) {
			// Take maximum of each channel
			int finalR = Math.max(newR, currentR);
			int finalG = Math.max(newG, currentG);
			int finalB = Math.max(newB, currentB);
			chunk.setBlockLightRGB(x, y, z, finalR, finalG, finalB);
			addQueue.offer(new LightNode(chunk, x, y, z, finalR, finalG, finalB));
		}
	}
	
	/**
	 * Propagate light to a neighbor position (legacy method).
	 * Now supports cross-chunk propagation.
	 * @deprecated Use propagateRGBToNeighbor for RGB values
	 */
	@Deprecated
	private void propagateToNeighbor(LevelChunk chunk, int x, int y, int z, int newLight) {
		propagateRGBToNeighbor(chunk, x, y, z, newLight, newLight, newLight);
	}
	
	/**
	 * Remove blockLight from a position (when an emissive block is removed).
	 * This removes the light and then re-propagates from remaining light sources.
	 * 
	 * @param chunk The chunk containing the block
	 * @param x Chunk-local X coordinate (0-15)
	 * @param y Chunk-local Y coordinate (0-383)
	 * @param z Chunk-local Z coordinate (0-15)
	 */
	public void removeBlockLight(LevelChunk chunk, int x, int y, int z) {
		// Read actual RGB values from the chunk
		int removedR = chunk.getBlockLightR(x, y, z);
		int removedG = chunk.getBlockLightG(x, y, z);
		int removedB = chunk.getBlockLightB(x, y, z);
		
		// Check if there's any light to remove
		if (removedR <= 0 && removedG <= 0 && removedB <= 0) {
			return; // No light to remove
		}
		
		// Clear light at source
		chunk.setBlockLightRGB(x, y, z, 0, 0, 0);
		
		// BFS to remove light and collect boundary nodes for re-propagation
		removeQueue.clear();
		removeQueue.offer(new LightNode(chunk, x, y, z, removedR, removedG, removedB));
		
		Queue<LightNode> boundaryQueue = new ArrayDeque<>();
		
		LightNode node2;
		while ((node2 = removeQueue.poll()) != null) {
			// Use max of RGB as the light level for removal
			int sourceLight = Math.max(node2.r, Math.max(node2.g, node2.b));
			
			// Check all 6 neighbors
			checkNeighborForRemoval(node2.chunk, node2.x - 1, node2.y, node2.z, sourceLight, boundaryQueue);
			checkNeighborForRemoval(node2.chunk, node2.x + 1, node2.y, node2.z, sourceLight, boundaryQueue);
			checkNeighborForRemoval(node2.chunk, node2.x, node2.y - 1, node2.z, sourceLight, boundaryQueue);
			checkNeighborForRemoval(node2.chunk, node2.x, node2.y + 1, node2.z, sourceLight, boundaryQueue);
			checkNeighborForRemoval(node2.chunk, node2.x, node2.y, node2.z - 1, sourceLight, boundaryQueue);
			checkNeighborForRemoval(node2.chunk, node2.x, node2.y, node2.z + 1, sourceLight, boundaryQueue);
		}
		
		// Re-propagate light from boundary nodes
		addQueue.clear();
		LightNode node3;
		while ((node3 = boundaryQueue.poll()) != null) {
			// Get current RGB values (should match what's in the node since we just read them)
			int r = node3.chunk.getBlockLightR(node3.x, node3.y, node3.z);
			int g = node3.chunk.getBlockLightG(node3.x, node3.y, node3.z);
			int b = node3.chunk.getBlockLightB(node3.x, node3.y, node3.z);
			
			// Only re-propagate if there's still light here
			if (r > 0 || g > 0 || b > 0) {
				addQueue.offer(new LightNode(node3.chunk, node3.x, node3.y, node3.z, r, g, b));
			}
		}
		
		// BFS re-propagation from boundary
		LightNode node4;
		while ((node4 = addQueue.poll()) != null) {
			// Get current RGB values from the node
			int currentR = node4.r;
			int currentG = node4.g;
			int currentB = node4.b;
			
			// Only propagate if at least one channel is strong enough
			if (currentR <= 1 && currentG <= 1 && currentB <= 1) {
				continue;
			}
			
			// Attenuation (reduce each channel by 1, min 0)
			int newR = Math.max(0, currentR - 1);
			int newG = Math.max(0, currentG - 1);
			int newB = Math.max(0, currentB - 1);
			
			// Use the RGB propagation method
			propagateRGBToNeighbor(node4.chunk, node4.x - 1, node4.y, node4.z, newR, newG, newB);
			propagateRGBToNeighbor(node4.chunk, node4.x + 1, node4.y, node4.z, newR, newG, newB);
			propagateRGBToNeighbor(node4.chunk, node4.x, node4.y - 1, node4.z, newR, newG, newB);
			propagateRGBToNeighbor(node4.chunk, node4.x, node4.y + 1, node4.z, newR, newG, newB);
			propagateRGBToNeighbor(node4.chunk, node4.x, node4.y, node4.z - 1, newR, newG, newB);
			propagateRGBToNeighbor(node4.chunk, node4.x, node4.y, node4.z + 1, newR, newG, newB);
		}
	}
	
	/**
	 * Check a neighbor for removal during light removal BFS.
	 * Now properly supports cross-chunk boundaries.
	 */
	private void checkNeighborForRemoval(LevelChunk chunk, int x, int y, int z, 
	                                      int sourceLight, Queue<LightNode> boundaryQueue) {
		LevelChunk targetChunk = chunk;
		int targetX = x;
		int targetZ = z;
		
		// Handle cross-chunk boundaries
		if (x < 0 || x >= LevelChunk.WIDTH || z < 0 || z >= LevelChunk.DEPTH) {
			if (crossChunkPropagator == null) {
				return; // No cross-chunk support
			}
			
			// Calculate target chunk coordinates
			int chunkX = chunk.chunkX();
			int chunkZ = chunk.chunkZ();
			
			if (x < 0) {
				chunkX--;
				targetX = x + LevelChunk.WIDTH;
			} else if (x >= LevelChunk.WIDTH) {
				chunkX++;
				targetX = x - LevelChunk.WIDTH;
			}
			
			if (z < 0) {
				chunkZ--;
				targetZ = z + LevelChunk.DEPTH;
			} else if (z >= LevelChunk.DEPTH) {
				chunkZ++;
				targetZ = z - LevelChunk.DEPTH;
			}
			
			// Try to get the neighbor chunk
			targetChunk = crossChunkPropagator.getNeighborChunk(chunkX, chunkZ);
			if (targetChunk == null) {
				return; // Neighbor chunk not loaded, can't remove light there
			}
		}
		
		// Check Y bounds (no cross-chunk for vertical)
		if (y < 0 || y >= LevelChunk.HEIGHT) {
			return;
		}
		
		// Read actual RGB values from neighbor
		int neighborR = targetChunk.getBlockLightR(targetX, y, targetZ);
		int neighborG = targetChunk.getBlockLightG(targetX, y, targetZ);
		int neighborB = targetChunk.getBlockLightB(targetX, y, targetZ);
		
		// Calculate max for comparison
		int neighborLight = Math.max(neighborR, Math.max(neighborG, neighborB));
		
		if (neighborLight == 0) {
			return; // Already dark
		}
		
		// Check if this light came from the removed source
		if (neighborLight < sourceLight) {
			// This light was from the removed source - remove it
			targetChunk.setBlockLightRGB(targetX, y, targetZ, 0, 0, 0);
			removeQueue.offer(new LightNode(targetChunk, targetX, y, targetZ, neighborR, neighborG, neighborB));
		} else {
			// This light is from another source - add to boundary for re-propagation
			Block block = targetChunk.getBlock(targetX, y, targetZ);
			// Check if it's an emissive block (light source)
			if (block.getLightEmission() > 0 || neighborLight >= sourceLight) {
				boundaryQueue.offer(new LightNode(targetChunk, targetX, y, targetZ, neighborR, neighborG, neighborB));
			}
		}
	}
	
	/**
	 * Re-propagate light to a neighbor during re-propagation phase.
	 * Now supports cross-chunk propagation.
	 */
	private void repropagateToNeighbor(LevelChunk chunk, int x, int y, int z, int newLight) {
		// Check if crossing chunk boundaries
		if (x < 0 || x >= LevelChunk.WIDTH || z < 0 || z >= LevelChunk.DEPTH) {
			// Crossing chunk boundary - use cross-chunk propagator if available
			if (crossChunkPropagator != null) {
				crossChunkPropagator.propagateBlockLightCross(chunk, x, y, z, newLight + 1);
			}
			return;
		}
		
		// Check Y bounds
		if (y < 0 || y >= LevelChunk.HEIGHT) {
			return;
		}
		
		// Check if block is opaque
		Block block = chunk.getBlock(x, y, z);
		if (block == null || block.getOpacity() >= 15) {
			return; // Opaque block or null
		}
		
		int currentLight = chunk.getBlockLight(x, y, z);
		
		// Only update if new light is brighter
		if (newLight > currentLight) {
			chunk.setBlockLight(x, y, z, newLight);
			addQueue.offer(new LightNode(chunk, x, y, z, newLight));
		}
	}
	
	/**
	 * Update blockLight when a block is placed.
	 * Handles both emissive blocks (add light) and opaque blocks (may need to remove light).
	 * 
	 * @param chunk The chunk
	 * @param x Chunk-local X coordinate
	 * @param y Chunk-local Y coordinate
	 * @param z Chunk-local Z coordinate
	 * @param newBlock The block being placed
	 * @param oldBlock The block being replaced
	 */
	public void updateBlockLight(LevelChunk chunk, int x, int y, int z, Block newBlock, Block oldBlock) {
		int oldEmissionR = oldBlock.getLightEmissionR();
		int oldEmissionG = oldBlock.getLightEmissionG();
		int oldEmissionB = oldBlock.getLightEmissionB();
		int newEmissionR = newBlock.getLightEmissionR();
		int newEmissionG = newBlock.getLightEmissionG();
		int newEmissionB = newBlock.getLightEmissionB();
		
		// If old block was emissive, remove its light
		if (oldEmissionR > 0 || oldEmissionG > 0 || oldEmissionB > 0) {
			removeBlockLight(chunk, x, y, z);
		}
		
		// If new block is emissive, add its light
		if (newEmissionR > 0 || newEmissionG > 0 || newEmissionB > 0) {
			addBlockLightRGB(chunk, x, y, z, newEmissionR, newEmissionG, newEmissionB);
		} else if (newBlock.getOpacity() >= 15) {
			// New block is opaque and non-emissive - it blocks light
			// Remove any light that was at this position
			int currentR = chunk.getBlockLightR(x, y, z);
			int currentG = chunk.getBlockLightG(x, y, z);
			int currentB = chunk.getBlockLightB(x, y, z);
			if (currentR > 0 || currentG > 0 || currentB > 0) {
				chunk.setBlockLightRGB(x, y, z, 0, 0, 0);
				// TODO: This should trigger re-propagation from neighbors
				// For now, we just clear the light at this position
			}
		} else if (oldBlock.getOpacity() >= 15 && newBlock.getOpacity() < 15) {
			// Old block was opaque, new block is transparent
			// Light from neighbors should propagate into this newly opened space
			propagateLightFromNeighbors(chunk, x, y, z);
		}
	}
	
	/**
	 * Propagate light from neighboring blocks into the specified position.
	 * This is used when a transparent block replaces an opaque block.
	 */
	private void propagateLightFromNeighbors(LevelChunk chunk, int x, int y, int z) {
		// Clear the add queue before starting
		addQueue.clear();
		
		// Check all 6 neighbors and propagate their light
		propagateLightFromNeighbor(chunk, x - 1, y, z, x, y, z);
		propagateLightFromNeighbor(chunk, x + 1, y, z, x, y, z);
		propagateLightFromNeighbor(chunk, x, y - 1, z, x, y, z);
		propagateLightFromNeighbor(chunk, x, y + 1, z, x, y, z);
		propagateLightFromNeighbor(chunk, x, y, z - 1, x, y, z);
		propagateLightFromNeighbor(chunk, x, y, z + 1, x, y, z);
		
		// Process the queue to continue propagation
		processAddQueue();
	}
	
	/**
	 * Propagate light from a neighbor position to the target position.
	 * @param chunk The chunk
	 * @param fromX Source X coordinate
	 * @param fromY Source Y coordinate
	 * @param fromZ Source Z coordinate
	 * @param toX Target X coordinate
	 * @param toY Target Y coordinate
	 * @param toZ Target Z coordinate
	 */
	private void propagateLightFromNeighbor(LevelChunk chunk, int fromX, int fromY, int fromZ, 
	                                        int toX, int toY, int toZ) {
		// Check if source is out of bounds
		if (fromX < 0 || fromX >= LevelChunk.WIDTH || 
		    fromY < 0 || fromY >= LevelChunk.HEIGHT || 
		    fromZ < 0 || fromZ >= LevelChunk.DEPTH) {
			// TODO: Handle cross-chunk propagation
			return;
		}
		
		// Get light at source
		int sourceR = chunk.getBlockLightR(fromX, fromY, fromZ);
		int sourceG = chunk.getBlockLightG(fromX, fromY, fromZ);
		int sourceB = chunk.getBlockLightB(fromX, fromY, fromZ);
		
		// If source has no light, nothing to propagate
		if (sourceR <= 0 && sourceG <= 0 && sourceB <= 0) {
			return;
		}
		
		// Attenuate light (reduce by 1 per block)
		int newR = Math.max(0, sourceR - 1);
		int newG = Math.max(0, sourceG - 1);
		int newB = Math.max(0, sourceB - 1);
		
		// Get current light at target
		int currentR = chunk.getBlockLightR(toX, toY, toZ);
		int currentG = chunk.getBlockLightG(toX, toY, toZ);
		int currentB = chunk.getBlockLightB(toX, toY, toZ);
		
		// Only propagate if new light is brighter in at least one channel
		if (newR > currentR || newG > currentG || newB > currentB) {
			// Take maximum of each channel
			int finalR = Math.max(newR, currentR);
			int finalG = Math.max(newG, currentG);
			int finalB = Math.max(newB, currentB);
			
			// Set the new light value
			chunk.setBlockLightRGB(toX, toY, toZ, finalR, finalG, finalB);
			
			// Use the existing propagation queue to continue spreading this light
			addQueue.offer(new LightNode(chunk, toX, toY, toZ, finalR, finalG, finalB));
		}
	}
	
	/**
	 * Process the add queue to continue light propagation.
	 * This should be called after propagateLightFromNeighbors to complete the propagation.
	 */
	private void processAddQueue() {
		LightNode node;
		while ((node = addQueue.poll()) != null) {
			int currentR = node.r;
			int currentG = node.g;
			int currentB = node.b;
			
			// Only propagate if at least one channel is strong enough
			if (currentR <= 1 && currentG <= 1 && currentB <= 1) {
				continue; // Light too weak to propagate
			}
			
			// Attenuation (reduce each channel by 1, min 0)
			int newR = Math.max(0, currentR - 1);
			int newG = Math.max(0, currentG - 1);
			int newB = Math.max(0, currentB - 1);
			
			// Propagate to all 6 neighbors
			propagateRGBToNeighbor(node.chunk, node.x - 1, node.y, node.z, newR, newG, newB);
			propagateRGBToNeighbor(node.chunk, node.x + 1, node.y, node.z, newR, newG, newB);
			propagateRGBToNeighbor(node.chunk, node.x, node.y - 1, node.z, newR, newG, newB);
			propagateRGBToNeighbor(node.chunk, node.x, node.y + 1, node.z, newR, newG, newB);
			propagateRGBToNeighbor(node.chunk, node.x, node.y, node.z - 1, newR, newG, newB);
			propagateRGBToNeighbor(node.chunk, node.x, node.y, node.z + 1, newR, newG, newB);
		}
	}
}
