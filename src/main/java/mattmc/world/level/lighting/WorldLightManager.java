package mattmc.world.level.lighting;

import mattmc.world.level.block.Block;
import mattmc.world.level.chunk.LevelChunk;

/**
 * Global light manager that coordinates light propagation across the entire world.
 * Provides a centralized point for accessing cross-chunk light propagation.
 * 
 * This is a singleton pattern to avoid passing the cross-chunk propagator
 * through every method call.
 */
public class WorldLightManager {
	
	private static WorldLightManager instance = new WorldLightManager();
	
	private CrossChunkLightPropagator crossChunkPropagator;
	private LightPropagator blockLightPropagator;
	private SkylightEngine skylightEngine;
	
	private WorldLightManager() {
		this.crossChunkPropagator = new CrossChunkLightPropagator();
		this.blockLightPropagator = new LightPropagator();
		this.skylightEngine = new SkylightEngine();
		
		// Wire up the propagators
		this.blockLightPropagator.setCrossChunkPropagator(crossChunkPropagator);
		this.crossChunkPropagator.setLightPropagator(blockLightPropagator);
		// SkylightEngine will also need cross-chunk support (to be added)
	}
	
	/**
	 * Get the singleton instance.
	 */
	public static WorldLightManager getInstance() {
		return instance;
	}
	
	/**
	 * Reset the singleton (for testing).
	 */
	public static void resetInstance() {
		instance = new WorldLightManager();
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
