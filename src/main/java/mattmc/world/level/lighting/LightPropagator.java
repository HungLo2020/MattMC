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
 * 
 * This is a chunk-local propagator - it only propagates within a single chunk.
 * Cross-chunk propagation would require access to neighboring chunks.
 */
public class LightPropagator {
	
	/**
	 * Represents a light node in the BFS queue.
	 */
	private static class LightNode {
		final int x, y, z;
		final int lightLevel;
		
		LightNode(int x, int y, int z, int lightLevel) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.lightLevel = lightLevel;
		}
	}
	
	// BFS queues
	private final Queue<LightNode> addQueue = new ArrayDeque<>();
	private final Queue<LightNode> removeQueue = new ArrayDeque<>();
	
	/**
	 * Add blockLight from an emissive block at the given position.
	 * This propagates light outward using BFS with attenuation.
	 * 
	 * @param chunk The chunk containing the block
	 * @param x Chunk-local X coordinate (0-15)
	 * @param y Chunk-local Y coordinate (0-383)
	 * @param z Chunk-local Z coordinate (0-15)
	 * @param emission Light emission level of the block (0-15)
	 */
	public void addBlockLight(LevelChunk chunk, int x, int y, int z, int emission) {
		if (emission <= 0) {
			return; // No light to add
		}
		
		// Set the light at the source position
		chunk.setBlockLight(x, y, z, emission);
		
		// Enqueue the source for propagation
		addQueue.clear();
		addQueue.offer(new LightNode(x, y, z, emission));
		
		// BFS propagation
		while (!addQueue.isEmpty()) {
			LightNode node = addQueue.poll();
			int currentLight = node.lightLevel;
			
			if (currentLight <= 1) {
				continue; // Light too weak to propagate
			}
			
			int newLight = currentLight - 1; // Attenuation
			
			// Propagate to all 6 neighbors
			propagateToNeighbor(chunk, node.x - 1, node.y, node.z, newLight);
			propagateToNeighbor(chunk, node.x + 1, node.y, node.z, newLight);
			propagateToNeighbor(chunk, node.x, node.y - 1, node.z, newLight);
			propagateToNeighbor(chunk, node.x, node.y + 1, node.z, newLight);
			propagateToNeighbor(chunk, node.x, node.y, node.z - 1, newLight);
			propagateToNeighbor(chunk, node.x, node.y, node.z + 1, newLight);
		}
	}
	
	/**
	 * Propagate light to a neighbor position.
	 */
	private void propagateToNeighbor(LevelChunk chunk, int x, int y, int z, int newLight) {
		// Check bounds
		if (x < 0 || x >= LevelChunk.WIDTH || y < 0 || y >= LevelChunk.HEIGHT || 
		    z < 0 || z >= LevelChunk.DEPTH) {
			return; // Out of chunk bounds
		}
		
		// Check if block is opaque (blocks light)
		Block block = chunk.getBlock(x, y, z);
		if (block.getOpacity() >= 15) {
			return; // Fully opaque block stops light
		}
		
		// Get current light at neighbor
		int currentLight = chunk.getBlockLight(x, y, z);
		
		// Only update if new light is brighter
		if (newLight > currentLight) {
			chunk.setBlockLight(x, y, z, newLight);
			addQueue.offer(new LightNode(x, y, z, newLight));
		}
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
		int removedLight = chunk.getBlockLight(x, y, z);
		if (removedLight <= 0) {
			return; // No light to remove
		}
		
		// Clear light at source
		chunk.setBlockLight(x, y, z, 0);
		
		// BFS to remove light and collect boundary nodes for re-propagation
		removeQueue.clear();
		removeQueue.offer(new LightNode(x, y, z, removedLight));
		
		Queue<LightNode> boundaryQueue = new ArrayDeque<>();
		
		while (!removeQueue.isEmpty()) {
			LightNode node = removeQueue.poll();
			
			// Check all 6 neighbors
			checkNeighborForRemoval(chunk, node.x - 1, node.y, node.z, node.lightLevel, boundaryQueue);
			checkNeighborForRemoval(chunk, node.x + 1, node.y, node.z, node.lightLevel, boundaryQueue);
			checkNeighborForRemoval(chunk, node.x, node.y - 1, node.z, node.lightLevel, boundaryQueue);
			checkNeighborForRemoval(chunk, node.x, node.y + 1, node.z, node.lightLevel, boundaryQueue);
			checkNeighborForRemoval(chunk, node.x, node.y, node.z - 1, node.lightLevel, boundaryQueue);
			checkNeighborForRemoval(chunk, node.x, node.y, node.z + 1, node.lightLevel, boundaryQueue);
		}
		
		// Re-propagate light from boundary nodes
		addQueue.clear();
		while (!boundaryQueue.isEmpty()) {
			LightNode node = boundaryQueue.poll();
			int light = chunk.getBlockLight(node.x, node.y, node.z);
			if (light > 0) {
				addQueue.offer(node);
			}
		}
		
		// BFS re-propagation from boundary
		while (!addQueue.isEmpty()) {
			LightNode node = addQueue.poll();
			int currentLight = chunk.getBlockLight(node.x, node.y, node.z);
			
			if (currentLight <= 1) {
				continue;
			}
			
			int newLight = currentLight - 1;
			
			repropagateToNeighbor(chunk, node.x - 1, node.y, node.z, newLight);
			repropagateToNeighbor(chunk, node.x + 1, node.y, node.z, newLight);
			repropagateToNeighbor(chunk, node.x, node.y - 1, node.z, newLight);
			repropagateToNeighbor(chunk, node.x, node.y + 1, node.z, newLight);
			repropagateToNeighbor(chunk, node.x, node.y, node.z - 1, newLight);
			repropagateToNeighbor(chunk, node.x, node.y, node.z + 1, newLight);
		}
	}
	
	/**
	 * Check a neighbor for removal during light removal BFS.
	 */
	private void checkNeighborForRemoval(LevelChunk chunk, int x, int y, int z, 
	                                      int sourceLight, Queue<LightNode> boundaryQueue) {
		// Check bounds
		if (x < 0 || x >= LevelChunk.WIDTH || y < 0 || y >= LevelChunk.HEIGHT || 
		    z < 0 || z >= LevelChunk.DEPTH) {
			return;
		}
		
		int neighborLight = chunk.getBlockLight(x, y, z);
		
		if (neighborLight == 0) {
			return; // Already dark
		}
		
		// Check if this light came from the removed source
		if (neighborLight < sourceLight) {
			// This light was from the removed source - remove it
			chunk.setBlockLight(x, y, z, 0);
			removeQueue.offer(new LightNode(x, y, z, neighborLight));
		} else {
			// This light is from another source - add to boundary for re-propagation
			Block block = chunk.getBlock(x, y, z);
			// Check if it's an emissive block (light source)
			if (block.getLightEmission() > 0 || neighborLight >= sourceLight) {
				boundaryQueue.offer(new LightNode(x, y, z, neighborLight));
			}
		}
	}
	
	/**
	 * Re-propagate light to a neighbor during re-propagation phase.
	 */
	private void repropagateToNeighbor(LevelChunk chunk, int x, int y, int z, int newLight) {
		// Check bounds
		if (x < 0 || x >= LevelChunk.WIDTH || y < 0 || y >= LevelChunk.HEIGHT || 
		    z < 0 || z >= LevelChunk.DEPTH) {
			return;
		}
		
		// Check if block is opaque
		Block block = chunk.getBlock(x, y, z);
		if (block.getOpacity() >= 15) {
			return;
		}
		
		int currentLight = chunk.getBlockLight(x, y, z);
		
		// Only update if new light is brighter
		if (newLight > currentLight) {
			chunk.setBlockLight(x, y, z, newLight);
			addQueue.offer(new LightNode(x, y, z, newLight));
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
		int oldEmission = oldBlock.getLightEmission();
		int newEmission = newBlock.getLightEmission();
		
		// If old block was emissive, remove its light
		if (oldEmission > 0) {
			removeBlockLight(chunk, x, y, z);
		}
		
		// If new block is emissive, add its light
		if (newEmission > 0) {
			addBlockLight(chunk, x, y, z, newEmission);
		} else if (newBlock.getOpacity() >= 15) {
			// New block is opaque and non-emissive - it blocks light
			// Remove any light that was at this position
			int currentLight = chunk.getBlockLight(x, y, z);
			if (currentLight > 0) {
				chunk.setBlockLight(x, y, z, 0);
				// TODO: This should trigger re-propagation from neighbors
				// For now, we just clear the light at this position
			}
		}
	}
}
