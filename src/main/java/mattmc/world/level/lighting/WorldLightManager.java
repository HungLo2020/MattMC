package mattmc.world.level.lighting;

import mattmc.world.level.block.Block;
import mattmc.world.level.chunk.LevelChunk;

/**
 * World light manager that coordinates light propagation across the entire world.
 * Provides a centralized point for accessing cross-chunk light propagation.
 * 
 * Each Level instance owns its own WorldLightManager for proper encapsulation
 * and improved testability.
 */
public class WorldLightManager {
	
	/**
	 * Minimum light intensity required to propagate to the next block.
	 * Light attenuates by 1 per block, so intensity 1 cannot propagate further.
	 */
	private static final int MIN_LIGHT_FOR_PROPAGATION = 2;
	
	private CrossChunkLightPropagator crossChunkPropagator;
	private LightPropagator blockLightPropagator;
	private SkylightEngine skylightEngine;
	
	public WorldLightManager() {
		this.crossChunkPropagator = new CrossChunkLightPropagator();
		this.blockLightPropagator = new LightPropagator();
		this.skylightEngine = new SkylightEngine();
		
		// Wire up the propagators
		this.blockLightPropagator.setCrossChunkPropagator(crossChunkPropagator);
		this.crossChunkPropagator.setLightPropagator(blockLightPropagator);
		// Wire up skylight engine for cross-chunk propagation
		this.skylightEngine.setCrossChunkPropagator(crossChunkPropagator);
	}
	
	/**
	 * Set the neighbor chunk accessor.
	 */
	public void setNeighborAccessor(CrossChunkLightPropagator.NeighborChunkAccessor accessor) {
		crossChunkPropagator.setNeighborAccessor(accessor);
	}
	
	/**
	 * Get the cross-chunk propagator.
	 */
	public CrossChunkLightPropagator getCrossChunkPropagator() {
		return crossChunkPropagator;
	}
	
	/**
	 * Update block light when a block changes.
	 */
	public void updateBlockLight(LevelChunk chunk, int x, int y, int z, Block newBlock, Block oldBlock) {
		blockLightPropagator.updateBlockLight(chunk, x, y, z, newBlock, oldBlock);
	}
	
	/**
	 * Update skylight when a block changes (column update).
	 */
	public void updateColumnSkylight(LevelChunk chunk, int x, int y, int z, Block newBlock, Block oldBlock) {
		skylightEngine.updateColumnSkylight(chunk, x, y, z, newBlock, oldBlock);
	}
	
	/**
	 * Initialize skylight for a chunk.
	 */
	public void initializeChunkSkylight(LevelChunk chunk) {
		skylightEngine.initializeChunkSkylight(chunk);
	}
	
	/**
	 * Process deferred light updates for a chunk that just loaded.
	 */
	public void processDeferredUpdates(LevelChunk chunk) {
		crossChunkPropagator.processDeferredUpdates(chunk);
	}
	
	/**
	 * Propagate light from neighboring chunks into a newly loaded chunk.
	 * This should be called after a chunk is loaded from disk to ensure
	 * cross-chunk light propagation works correctly even after world reload.
	 * 
	 * When a world is saved and reloaded, the deferred update system loses
	 * any pending cross-chunk propagation. This method scans the boundary
	 * edges of the newly loaded chunk and propagates light from loaded neighbors.
	 * 
	 * @param chunk The newly loaded chunk
	 */
	public void propagateLightFromNeighbors(LevelChunk chunk) {
		// Check all 4 neighbor chunks (north, south, east, west)
		// For each loaded neighbor, propagate their edge light into this chunk
		propagateFromNeighborEdge(chunk, -1, 0);  // West neighbor (chunk at -X)
		propagateFromNeighborEdge(chunk, 1, 0);   // East neighbor (chunk at +X)
		propagateFromNeighborEdge(chunk, 0, -1);  // North neighbor (chunk at -Z)
		propagateFromNeighborEdge(chunk, 0, 1);   // South neighbor (chunk at +Z)
	}
	
	/**
	 * Propagate light from a specific neighbor chunk's edge into this chunk.
	 */
	private void propagateFromNeighborEdge(LevelChunk chunk, int neighborDX, int neighborDZ) {
		int neighborChunkX = chunk.chunkX() + neighborDX;
		int neighborChunkZ = chunk.chunkZ() + neighborDZ;
		
		LevelChunk neighbor = crossChunkPropagator.getNeighborChunk(neighborChunkX, neighborChunkZ);
		if (neighbor == null) {
			return; // Neighbor not loaded
		}
		
		// Determine which edge of the neighbor faces this chunk
		// and propagate light from that edge
		if (neighborDX == -1) {
			// West neighbor: their x=15 edge faces our x=0 edge
			propagateEdgeLight(neighbor, 15, chunk, 0, true);
		} else if (neighborDX == 1) {
			// East neighbor: their x=0 edge faces our x=15 edge
			propagateEdgeLight(neighbor, 0, chunk, 15, true);
		} else if (neighborDZ == -1) {
			// North neighbor: their z=15 edge faces our z=0 edge
			propagateEdgeLight(neighbor, 15, chunk, 0, false);
		} else if (neighborDZ == 1) {
			// South neighbor: their z=0 edge faces our z=15 edge
			propagateEdgeLight(neighbor, 0, chunk, 15, false);
		}
	}
	
	/**
	 * Propagate light from one chunk's edge to another chunk's edge.
	 * 
	 * @param fromChunk Source chunk
	 * @param fromEdge Edge coordinate in source (0 or 15)
	 * @param toChunk Target chunk
	 * @param toEdge Edge coordinate in target (0 or 15)
	 * @param isXEdge True if this is X edge, false if Z edge
	 */
	private void propagateEdgeLight(LevelChunk fromChunk, int fromEdge, 
	                                 LevelChunk toChunk, int toEdge, boolean isXEdge) {
		// Scan the entire edge
		for (int y = 0; y < LevelChunk.HEIGHT; y++) {
			for (int other = 0; other < 16; other++) {
				int fromX = isXEdge ? fromEdge : other;
				int fromZ = isXEdge ? other : fromEdge;
				int toX = isXEdge ? toEdge : other;
				int toZ = isXEdge ? other : toEdge;
				
				// Get light at source edge
				int sourceR = fromChunk.getBlockLightR(fromX, y, fromZ);
				int sourceG = fromChunk.getBlockLightG(fromX, y, fromZ);
				int sourceB = fromChunk.getBlockLightB(fromX, y, fromZ);
				int sourceI = fromChunk.getBlockLightI(fromX, y, fromZ);
				
				// Check if source has light that should propagate
				if (sourceI >= MIN_LIGHT_FOR_PROPAGATION) {
					int newI = sourceI - 1;
					int currentI = toChunk.getBlockLightI(toX, y, toZ);
					
					// Only propagate if it would increase the light
					if (newI > currentI) {
						// Check if target block can receive light (not opaque)
						mattmc.world.level.block.Block block = toChunk.getBlock(toX, y, toZ);
						if (block != null && block.getOpacity() < 15) {
							// Set the light and continue propagation
							toChunk.setBlockLightRGBI(toX, y, toZ, sourceR, sourceG, sourceB, newI);
							// Continue propagation within the target chunk
							blockLightPropagator.addBlockLightRGB(toChunk, toX, y, toZ, sourceR, sourceG, sourceB);
						}
					}
				}
				
				// Also check skylight
				int sourceSky = fromChunk.getSkyLight(fromX, y, fromZ);
				if (sourceSky >= MIN_LIGHT_FOR_PROPAGATION) {
					int newSky = sourceSky - 1;
					int currentSky = toChunk.getSkyLight(toX, y, toZ);
					
					if (newSky > currentSky) {
						mattmc.world.level.block.Block block = toChunk.getBlock(toX, y, toZ);
						if (block != null && block.getOpacity() < 15) {
							toChunk.setSkyLight(toX, y, toZ, newSky);
							// Note: skylight propagation is handled by SkylightEngine
						}
					}
				}
			}
		}
	}
	
	/**
	 * Get the number of deferred updates waiting.
	 */
	public int getTotalDeferredUpdateCount() {
		return crossChunkPropagator.getTotalDeferredUpdateCount();
	}
	
	/**
	 * Add block light at a position with RGB values.
	 */
	public void addBlockLightRGB(LevelChunk chunk, int x, int y, int z, int r, int g, int b) {
		blockLightPropagator.addBlockLightRGB(chunk, x, y, z, r, g, b);
	}
	
	/**
	 * Remove block light at a position.
	 */
	public void removeBlockLight(LevelChunk chunk, int x, int y, int z) {
		blockLightPropagator.removeBlockLight(chunk, x, y, z);
	}
}
