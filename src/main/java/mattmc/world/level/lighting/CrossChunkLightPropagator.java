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
		
		// RGBI values for colored block light (only used when isSkylight = false)
		public final int r, g, b, i;
		public final boolean isRGBI; // true = use RGBI values, false = use legacy lightLevel
		
		// Legacy constructor for non-RGBI updates (skylight or legacy blocklight)
		public CrossChunkUpdate(int chunkX, int chunkZ, int localX, int localY, int localZ, 
		                        int lightLevel, boolean isSkylight) {
			this.chunkX = chunkX;
			this.chunkZ = chunkZ;
			this.localX = localX;
			this.localY = localY;
			this.localZ = localZ;
			this.lightLevel = lightLevel;
			this.isSkylight = isSkylight;
			this.r = 0;
			this.g = 0;
			this.b = 0;
			this.i = 0;
			this.isRGBI = false;
		}
		
		// RGBI constructor for colored block light
		public CrossChunkUpdate(int chunkX, int chunkZ, int localX, int localY, int localZ, 
		                        int r, int g, int b, int i) {
			this.chunkX = chunkX;
			this.chunkZ = chunkZ;
			this.localX = localX;
			this.localY = localY;
			this.localZ = localZ;
			this.lightLevel = 0; // Not used for RGBI
			this.isSkylight = false; // RGBI is only for block light
			this.r = r;
			this.g = g;
			this.b = b;
			this.i = i;
			this.isRGBI = true;
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
	 * Propagate blockLight RGBI across chunk boundaries.
	 * Color (RGB) remains constant; only intensity decrements with distance.
	 * 
	 * @param sourceChunk The chunk where light originates
	 * @param x Chunk-local X (can be outside 0-15 if crossing boundary)
	 * @param y Chunk-local Y (0-383)
	 * @param z Chunk-local Z (can be outside 0-15 if crossing boundary)
	 * @param r Red light level (0-15) - color component, constant
	 * @param g Green light level (0-15) - color component, constant
	 * @param b Blue light level (0-15) - color component, constant
	 * @param intensity Intensity level to propagate (0-15) - decrements with distance
	 */
	public void propagateBlockLightRGBICross(LevelChunk sourceChunk, int x, int y, int z, int r, int g, int b, int intensity) {
		// Check if intensity is strong enough to propagate
		if (intensity <= 0) {
			return; // No intensity to propagate
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
		
		// Check if we're actually crossing a chunk boundary
		boolean crossingChunk = (targetChunkX != sourceChunk.chunkX() || 
		                         targetChunkZ != sourceChunk.chunkZ());
		
		LevelChunk targetChunk;
		if (crossingChunk) {
			// Crossing chunk boundary - get the neighbor chunk
			if (neighborAccessor == null) {
				return; // No accessor available
			}
			
			targetChunk = neighborAccessor.getChunkIfLoaded(targetChunkX, targetChunkZ);
			if (targetChunk == null) {
				// Chunk not loaded - defer the RGBI update
				deferRGBIUpdate(targetChunkX, targetChunkZ, targetLocalX, y, targetLocalZ, r, g, b, intensity);
				return;
			}
		} else {
			// Not crossing chunk boundary - use source chunk
			targetChunk = sourceChunk;
		}
		
		// Check if the block at this position is opaque
		Block block = targetChunk.getBlock(targetLocalX, y, targetLocalZ);
		if (block.getOpacity() >= 15) {
			return; // Fully opaque block - don't set light here
		}
		
		// Get current light at target position
		int currentI = targetChunk.getBlockLightI(targetLocalX, y, targetLocalZ);
		
		// Only update if new intensity is brighter
		if (intensity > currentI) {
			// Set the light in the target chunk with same color but current intensity
			targetChunk.setBlockLightRGBI(targetLocalX, y, targetLocalZ, r, g, b, intensity);
			
			// Continue propagation from this position with reduced intensity
			int nextI = intensity - 1;
			if (nextI > 0) {
				// Propagate in all 6 directions from the position in target chunk
				propagateBlockLightRGBICross(targetChunk, targetLocalX - 1, y, targetLocalZ, r, g, b, nextI);
				propagateBlockLightRGBICross(targetChunk, targetLocalX + 1, y, targetLocalZ, r, g, b, nextI);
				propagateBlockLightRGBICross(targetChunk, targetLocalX, y - 1, targetLocalZ, r, g, b, nextI);
				propagateBlockLightRGBICross(targetChunk, targetLocalX, y + 1, targetLocalZ, r, g, b, nextI);
				propagateBlockLightRGBICross(targetChunk, targetLocalX, y, targetLocalZ - 1, r, g, b, nextI);
				propagateBlockLightRGBICross(targetChunk, targetLocalX, y, targetLocalZ + 1, r, g, b, nextI);
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
	 * Propagate RGBI block light within a single chunk.
	 * Color (RGB) remains constant; only intensity decrements with distance.
	 */
	private void propagateWithinChunkRGBI(LevelChunk chunk, int x, int y, int z, 
	                                      int r, int g, int b, int intensity) {
		// Check if block is opaque (blocks light)
		Block block = chunk.getBlock(x, y, z);
		if (block.getOpacity() >= 15) {
			return; // Fully opaque block stops light
		}
		
		// Get current light intensity at position
		int currentI = chunk.getBlockLightI(x, y, z);
		
		// Only update if new intensity is brighter
		if (intensity > currentI) {
			chunk.setBlockLightRGBI(x, y, z, r, g, b, intensity);
			
			// Continue propagation from this position with the same color but reduced intensity
			propagateBlockLightRGBICross(chunk, x, y, z, r, g, b, intensity);
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
	 * Defer an RGBI update for a chunk that isn't loaded yet.
	 */
	private void deferRGBIUpdate(int chunkX, int chunkZ, int localX, int localY, int localZ, 
	                             int r, int g, int b, int i) {
		long chunkKey = chunkKey(chunkX, chunkZ);
		CrossChunkUpdate update = new CrossChunkUpdate(chunkX, chunkZ, localX, localY, localZ, r, g, b, i);
		
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
			if (update.isRGBI) {
				// RGBI update - propagate with color
				propagateWithinChunkRGBI(chunk, update.localX, update.localY, update.localZ, 
				                        update.r, update.g, update.b, update.i);
			} else {
				// Legacy update (skylight or non-RGBI blocklight)
				propagateWithinChunk(chunk, update.localX, update.localY, update.localZ, 
				                    update.lightLevel, update.isSkylight);
			}
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
