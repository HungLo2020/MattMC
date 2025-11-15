package mattmc.world.level.lighting;

import mattmc.world.level.block.Block;
import mattmc.world.level.chunk.LevelChunk;

import java.util.*;

/**
 * Handles light propagation across chunk boundaries.
 * 
 * Features:
 * - Cross-chunk propagation for both skyLight and blockLight
 * - Deferred update queue for edges adjacent to unloaded chunks
 * - Retry mechanism triggered when neighbor chunks load
 * - No light seams at chunk borders
 */
public class CrossChunkLightPropagator {
	
	/**
	 * Represents a light update that crosses chunk boundaries.
	 */
	public static class CrossChunkUpdate {
		public final int chunkX, chunkZ;
		public final int localX, localY, localZ;
		public final int lightLevel;
		public final boolean isSkylight; // true = skylight, false = blocklight
		
		public CrossChunkUpdate(int chunkX, int chunkZ, int localX, int localY, int localZ, 
		                        int lightLevel, boolean isSkylight) {
			this.chunkX = chunkX;
			this.chunkZ = chunkZ;
			this.localX = localX;
			this.localY = localY;
			this.localZ = localZ;
			this.lightLevel = lightLevel;
			this.isSkylight = isSkylight;
		}
	}
	
	/**
	 * Interface for accessing neighbor chunks.
	 */
	public interface NeighborChunkAccessor {
		/**
		 * Get a chunk at the given chunk coordinates.
		 * Returns null if the chunk is not loaded.
		 */
		LevelChunk getChunkIfLoaded(int chunkX, int chunkZ);
	}
	
	// Queue for deferred updates (updates waiting for chunks to load)
	private final Map<Long, List<CrossChunkUpdate>> deferredUpdates = new HashMap<>();
	
	// Neighbor accessor
	private NeighborChunkAccessor neighborAccessor;
	private LightPropagator lightPropagator;  // Reference back to propagator for continuing BFS
	
	/**
	 * Set the neighbor chunk accessor.
	 */
	public void setNeighborAccessor(NeighborChunkAccessor accessor) {
		this.neighborAccessor = accessor;
	}
	
	/**
	 * Set the light propagator for continuing BFS across chunks.
	 */
	public void setLightPropagator(LightPropagator propagator) {
		this.lightPropagator = propagator;
	}
	
	/**
	 * Propagate blockLight across chunk boundaries.
	 * 
	 * @param sourceChunk The chunk where light originates
	 * @param x Chunk-local X (can be outside 0-15 if crossing boundary)
	 * @param y Chunk-local Y (0-383)
	 * @param z Chunk-local Z (can be outside 0-15 if crossing boundary)
	 * @param lightLevel Light level to propagate
	 */
	public void propagateBlockLightCross(LevelChunk sourceChunk, int x, int y, int z, int lightLevel) {
		if (lightLevel <= 1) {
			return; // Too weak to propagate
		}
		
		int newLight = lightLevel - 1; // Attenuation
		
		// IMPORTANT: Before propagating FROM this position, we need to verify
		// that this position can actually hold light (i.e., it's not opaque).
		// We need to check the block at the target position across chunk boundaries.
		
		// Calculate which chunk and local coordinates we're checking
		int targetChunkX = sourceChunk.chunkX();
		int targetChunkZ = sourceChunk.chunkZ();
		int targetLocalX = x;
		int targetLocalZ = z;
		
		// Handle X boundary crossing
		if (x < 0) {
			targetChunkX--;
			targetLocalX = LevelChunk.WIDTH - 1;
		} else if (x >= LevelChunk.WIDTH) {
			targetChunkX++;
			targetLocalX = 0;
		}
		
		// Handle Z boundary crossing
		if (z < 0) {
			targetChunkZ--;
			targetLocalZ = LevelChunk.DEPTH - 1;
		} else if (z >= LevelChunk.DEPTH) {
			targetChunkZ++;
			targetLocalZ = 0;
		}
		
		// Handle Y bounds
		if (y < 0 || y >= LevelChunk.HEIGHT) {
			return; // Out of world bounds
		}
		
		// Get the target chunk to check the block
		boolean crossingChunk = (targetChunkX != sourceChunk.chunkX() || 
		                         targetChunkZ != sourceChunk.chunkZ());
		
		LevelChunk targetChunk;
		if (crossingChunk) {
			if (neighborAccessor == null) {
				return; // No accessor available
			}
			targetChunk = neighborAccessor.getChunkIfLoaded(targetChunkX, targetChunkZ);
			if (targetChunk == null) {
				// Chunk not loaded - defer the check and potential propagation
				// We'll check opacity when the chunk loads
				deferUpdate(targetChunkX, targetChunkZ, targetLocalX, y, targetLocalZ, 
				           newLight, false);
				return;
			}
		} else {
			targetChunk = sourceChunk;
		}
		
		// Check if the block at this position is opaque
		Block block = targetChunk.getBlock(targetLocalX, y, targetLocalZ);
		if (block.getOpacity() >= 15) {
			return; // Fully opaque block - don't propagate FROM this position
		}
		
		// Check all 6 neighbors - propagate to neighbor chunks if needed
		tryPropagateToNeighbor(sourceChunk, x - 1, y, z, newLight, false);
		tryPropagateToNeighbor(sourceChunk, x + 1, y, z, newLight, false);
		tryPropagateToNeighbor(sourceChunk, x, y - 1, z, newLight, false);
		tryPropagateToNeighbor(sourceChunk, x, y + 1, z, newLight, false);
		tryPropagateToNeighbor(sourceChunk, x, y, z - 1, newLight, false);
		tryPropagateToNeighbor(sourceChunk, x, y, z + 1, newLight, false);
	}
	
	/**
	 * Propagate blockLight RGB across chunk boundaries.
	 * 
	 * @param sourceChunk The chunk where light originates
	 * @param x Chunk-local X (can be outside 0-15 if crossing boundary)
	 * @param y Chunk-local Y (0-383)
	 * @param z Chunk-local Z (can be outside 0-15 if crossing boundary)
	 * @param r Red light level to propagate (0-15)
	 * @param g Green light level to propagate (0-15)
	 * @param b Blue light level to propagate (0-15)
	 */
	public void propagateBlockLightRGBCross(LevelChunk sourceChunk, int x, int y, int z, int r, int g, int b) {
		// Check if light is strong enough to propagate
		if (r <= 0 && g <= 0 && b <= 0) {
			return; // No light to propagate
		}
		
		// Calculate which chunk and local coordinates we're targeting
		int targetChunkX = sourceChunk.chunkX();
		int targetChunkZ = sourceChunk.chunkZ();
		int targetLocalX = x;
		int targetLocalZ = z;
		
		// Handle X boundary crossing
		if (x < 0) {
			targetChunkX--;
			targetLocalX = LevelChunk.WIDTH + x; // x is negative
		} else if (x >= LevelChunk.WIDTH) {
			targetChunkX++;
			targetLocalX = x - LevelChunk.WIDTH;
		}
		
		// Handle Z boundary crossing
		if (z < 0) {
			targetChunkZ--;
			targetLocalZ = LevelChunk.DEPTH + z; // z is negative
		} else if (z >= LevelChunk.DEPTH) {
			targetChunkZ++;
			targetLocalZ = z - LevelChunk.DEPTH;
		}
		
		// Handle Y bounds
		if (y < 0 || y >= LevelChunk.HEIGHT) {
			return; // Out of world bounds
		}
		
		// Get the target chunk
		boolean crossingChunk = (targetChunkX != sourceChunk.chunkX() || 
		                         targetChunkZ != sourceChunk.chunkZ());
		
		if (!crossingChunk) {
			// Not actually crossing a chunk boundary - this shouldn't happen
			// but handle it gracefully
			return;
		}
		
		if (neighborAccessor == null) {
			return; // No accessor available
		}
		
		LevelChunk targetChunk = neighborAccessor.getChunkIfLoaded(targetChunkX, targetChunkZ);
		if (targetChunk == null) {
			// Chunk not loaded - defer the update
			// TODO: Store RGB values in deferred updates (for now, just skip)
			return;
		}
		
		// Check if the block at this position is opaque
		Block block = targetChunk.getBlock(targetLocalX, y, targetLocalZ);
		if (block.getOpacity() >= 15) {
			return; // Fully opaque block - don't set light here
		}
		
		// Get current light at target position
		int currentR = targetChunk.getBlockLightR(targetLocalX, y, targetLocalZ);
		int currentG = targetChunk.getBlockLightG(targetLocalX, y, targetLocalZ);
		int currentB = targetChunk.getBlockLightB(targetLocalX, y, targetLocalZ);
		
		// Only update if new light is brighter in at least one channel
		if (r > currentR || g > currentG || b > currentB) {
			// Take maximum of each channel
			int finalR = Math.max(r, currentR);
			int finalG = Math.max(g, currentG);
			int finalB = Math.max(b, currentB);
			
			// Set the light in the target chunk
			targetChunk.setBlockLightRGB(targetLocalX, y, targetLocalZ, finalR, finalG, finalB);
			
			// Continue propagation from this position using the light propagator
			// This is critical for cross-chunk light to continue spreading!
			if (lightPropagator != null) {
				// Note: We pass the attenuated values (finalR-1, etc.) as the propagator
				// expects the light value TO BE PROPAGATED, not the current value
				int propR = Math.max(0, finalR - 1);
				int propG = Math.max(0, finalG - 1);
				int propB = Math.max(0, finalB - 1);
				
				if (propR > 0 || propG > 0 || propB > 0) {
					// Propagate to neighbors of this position in the target chunk
					// We need to propagate within the target chunk, not add to source chunk queue
					// Actually, we should call propagateRGBToNeighbor for each neighbor
					// But that would require exposing it or having a helper method
					// For now, use a simpler approach: recursively call cross-chunk propagator
					// for all 6 neighbors of the position we just set
					
					// Propagate in all 6 directions from the position in target chunk
					propagateBlockLightRGBCross(targetChunk, targetLocalX - 1, y, targetLocalZ, propR + 1, propG + 1, propB + 1);
					propagateBlockLightRGBCross(targetChunk, targetLocalX + 1, y, targetLocalZ, propR + 1, propG + 1, propB + 1);
					propagateBlockLightRGBCross(targetChunk, targetLocalX, y - 1, targetLocalZ, propR + 1, propG + 1, propB + 1);
					propagateBlockLightRGBCross(targetChunk, targetLocalX, y + 1, targetLocalZ, propR + 1, propG + 1, propB + 1);
					propagateBlockLightRGBCross(targetChunk, targetLocalX, y, targetLocalZ - 1, propR + 1, propG + 1, propB + 1);
					propagateBlockLightRGBCross(targetChunk, targetLocalX, y, targetLocalZ + 1, propR + 1, propG + 1, propB + 1);
				}
			}
		}
	}
	
	/**
	 * Propagate skyLight across chunk boundaries.
	 * 
	 * @param sourceChunk The chunk where light originates
	 * @param x Chunk-local X (can be outside 0-15 if crossing boundary)
	 * @param y Chunk-local Y (0-383)
	 * @param z Chunk-local Z (can be outside 0-15 if crossing boundary)
	 * @param lightLevel Light level to propagate
	 */
	public void propagateSkylightCross(LevelChunk sourceChunk, int x, int y, int z, int lightLevel) {
		if (lightLevel <= 1) {
			return; // Too weak to propagate
		}
		
		int newLight = lightLevel - 1; // Attenuation
		
		// IMPORTANT: Before propagating FROM this position, we need to verify
		// that this position can actually hold light (i.e., it's not opaque).
		// We need to check the block at the target position across chunk boundaries.
		
		// Calculate which chunk and local coordinates we're checking
		int targetChunkX = sourceChunk.chunkX();
		int targetChunkZ = sourceChunk.chunkZ();
		int targetLocalX = x;
		int targetLocalZ = z;
		
		// Handle X boundary crossing
		if (x < 0) {
			targetChunkX--;
			targetLocalX = LevelChunk.WIDTH - 1;
		} else if (x >= LevelChunk.WIDTH) {
			targetChunkX++;
			targetLocalX = 0;
		}
		
		// Handle Z boundary crossing
		if (z < 0) {
			targetChunkZ--;
			targetLocalZ = LevelChunk.DEPTH - 1;
		} else if (z >= LevelChunk.DEPTH) {
			targetChunkZ++;
			targetLocalZ = 0;
		}
		
		// Handle Y bounds
		if (y < 0 || y >= LevelChunk.HEIGHT) {
			return; // Out of world bounds
		}
		
		// Get the target chunk to check the block
		boolean crossingChunk = (targetChunkX != sourceChunk.chunkX() || 
		                         targetChunkZ != sourceChunk.chunkZ());
		
		LevelChunk targetChunk;
		if (crossingChunk) {
			if (neighborAccessor == null) {
				return; // No accessor available
			}
			targetChunk = neighborAccessor.getChunkIfLoaded(targetChunkX, targetChunkZ);
			if (targetChunk == null) {
				// Chunk not loaded - defer the check and potential propagation
				deferUpdate(targetChunkX, targetChunkZ, targetLocalX, y, targetLocalZ, 
				           newLight, true);
				return;
			}
		} else {
			targetChunk = sourceChunk;
		}
		
		// Check if the block at this position is opaque
		Block block = targetChunk.getBlock(targetLocalX, y, targetLocalZ);
		if (block.getOpacity() >= 15) {
			return; // Fully opaque block - don't propagate FROM this position
		}
		
		// Check all 6 neighbors - propagate to neighbor chunks if needed
		tryPropagateToNeighbor(sourceChunk, x - 1, y, z, newLight, true);
		tryPropagateToNeighbor(sourceChunk, x + 1, y, z, newLight, true);
		tryPropagateToNeighbor(sourceChunk, x, y - 1, z, newLight, true);
		tryPropagateToNeighbor(sourceChunk, x, y + 1, z, newLight, true);
		tryPropagateToNeighbor(sourceChunk, x, y, z - 1, newLight, true);
		tryPropagateToNeighbor(sourceChunk, x, y, z + 1, newLight, true);
	}
	
	/**
	 * Try to propagate light to a neighbor position.
	 * Handles cross-chunk propagation and deferred updates.
	 */
	private void tryPropagateToNeighbor(LevelChunk sourceChunk, int x, int y, int z, 
	                                     int newLight, boolean isSkylight) {
		// Calculate target chunk coordinates
		int targetChunkX = sourceChunk.chunkX();
		int targetChunkZ = sourceChunk.chunkZ();
		int targetLocalX = x;
		int targetLocalZ = z;
		
		// Handle X boundary crossing
		if (x < 0) {
			targetChunkX--;
			targetLocalX = LevelChunk.WIDTH - 1;
		} else if (x >= LevelChunk.WIDTH) {
			targetChunkX++;
			targetLocalX = 0;
		}
		
		// Handle Z boundary crossing
		if (z < 0) {
			targetChunkZ--;
			targetLocalZ = LevelChunk.DEPTH - 1;
		} else if (z >= LevelChunk.DEPTH) {
			targetChunkZ++;
			targetLocalZ = 0;
		}
		
		// Handle Y bounds (no chunk crossing, just validate)
		if (y < 0 || y >= LevelChunk.HEIGHT) {
			return; // Out of world bounds
		}
		
		// Check if we're crossing chunk boundaries
		boolean crossingChunk = (targetChunkX != sourceChunk.chunkX() || 
		                         targetChunkZ != sourceChunk.chunkZ());
		
		if (!crossingChunk) {
			// Same chunk - propagate directly
			propagateWithinChunk(sourceChunk, targetLocalX, y, targetLocalZ, newLight, isSkylight);
		} else {
			// Different chunk - need neighbor access
			if (neighborAccessor == null) {
				return; // No accessor available
			}
			
			LevelChunk targetChunk = neighborAccessor.getChunkIfLoaded(targetChunkX, targetChunkZ);
			
			if (targetChunk != null) {
				// Chunk is loaded - propagate immediately
				propagateWithinChunk(targetChunk, targetLocalX, y, targetLocalZ, newLight, isSkylight);
			} else {
				// Chunk not loaded - defer the update
				deferUpdate(targetChunkX, targetChunkZ, targetLocalX, y, targetLocalZ, 
				           newLight, isSkylight);
			}
		}
	}
	
	/**
	 * Propagate light within a single chunk.
	 */
	private void propagateWithinChunk(LevelChunk chunk, int x, int y, int z, 
	                                  int newLight, boolean isSkylight) {
		// Check if block is opaque (blocks light)
		Block block = chunk.getBlock(x, y, z);
		if (block.getOpacity() >= 15) {
			return; // Fully opaque block stops light
		}
		
		// Get current light at position
		int currentLight = isSkylight ? chunk.getSkyLight(x, y, z) : chunk.getBlockLight(x, y, z);
		
		// Only update if new light is brighter
		if (newLight > currentLight) {
			if (isSkylight) {
				chunk.setSkyLight(x, y, z, newLight);
			} else {
				chunk.setBlockLight(x, y, z, newLight);
			}
			
			// Continue propagation from this position
			if (isSkylight) {
				propagateSkylightCross(chunk, x, y, z, newLight);
			} else {
				propagateBlockLightCross(chunk, x, y, z, newLight);
			}
		}
	}
	
	/**
	 * Defer an update for a chunk that isn't loaded yet.
	 */
	private void deferUpdate(int chunkX, int chunkZ, int localX, int localY, int localZ, 
	                        int lightLevel, boolean isSkylight) {
		long chunkKey = chunkKey(chunkX, chunkZ);
		CrossChunkUpdate update = new CrossChunkUpdate(chunkX, chunkZ, localX, localY, localZ, 
		                                               lightLevel, isSkylight);
		
		deferredUpdates.computeIfAbsent(chunkKey, k -> new ArrayList<>()).add(update);
	}
	
	/**
	 * Process deferred updates for a chunk that just loaded.
	 * Call this when a chunk is loaded to apply any pending light updates.
	 * 
	 * @param chunk The chunk that just loaded
	 */
	public void processDeferredUpdates(LevelChunk chunk) {
		long chunkKey = chunkKey(chunk.chunkX(), chunk.chunkZ());
		List<CrossChunkUpdate> updates = deferredUpdates.remove(chunkKey);
		
		if (updates == null || updates.isEmpty()) {
			return; // No deferred updates for this chunk
		}
		
		// Apply all deferred updates
		for (CrossChunkUpdate update : updates) {
			propagateWithinChunk(chunk, update.localX, update.localY, update.localZ, 
			                    update.lightLevel, update.isSkylight);
		}
	}
	
	/**
	 * Get the number of deferred updates waiting for a specific chunk.
	 */
	public int getDeferredUpdateCount(int chunkX, int chunkZ) {
		List<CrossChunkUpdate> updates = deferredUpdates.get(chunkKey(chunkX, chunkZ));
		return updates != null ? updates.size() : 0;
	}
	
	/**
	 * Get total number of deferred updates across all chunks.
	 */
	public int getTotalDeferredUpdateCount() {
		return deferredUpdates.values().stream().mapToInt(List::size).sum();
	}
	
	/**
	 * Clear all deferred updates.
	 */
	public void clearDeferredUpdates() {
		deferredUpdates.clear();
	}
	
	/**
	 * Get a neighbor chunk if it's loaded, null otherwise.
	 * This is a helper method for light removal that needs to access neighbor chunks.
	 */
	public LevelChunk getNeighborChunk(int chunkX, int chunkZ) {
		if (neighborAccessor == null) {
			return null;
		}
		return neighborAccessor.getChunkIfLoaded(chunkX, chunkZ);
	}
	
	/**
	 * Generate a unique key for chunk coordinates.
	 */
	private long chunkKey(int chunkX, int chunkZ) {
		return ((long)chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
	}
}
